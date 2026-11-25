package com.welli.cpmwheel;

import java.util.List;

/**
 * 1.9.2 "Movimento real no mundo": pure math (no Minecraft classes; checked offline by tools/WorldMathCheck.java).
 *
 * Every chain hangs from a point of the body (its anchor: the pivot of the CPM bone its first layer moves, or a default
 * point of the head / body / arm / leg). Each tick the anchor's real velocity in the world is split into
 *   A = the player's own motion (walking, jumping, falling, knockback, riding, elytra…),
 *   B = the anchor being carried around when the body turns,
 *   H = the head's own rotation carrying the anchor (only for anchors on the head),
 *   R = the rest: the model pose / animations moving that part (CPM animations, walking arm / leg swing, sneaking…),
 * all in the body frame {forward, right, up} in the same units as the speed inputs (10° per block/s).
 * A Live2D-style inertia is then added to the chain: like a pendulum whose pivot moves, accelerating the anchor swings the
 * links (a steady motion settles back), the tips whip after the first link. Nothing the chain already reads is counted twice:
 * a link with a speed input on an axis gets only B + H + R on it (its input already gives the player's motion), a link with
 * body / head turn inputs doesn't get B (resp. H).
 */
public final class WorldMath {
    private WorldMath() {}

    /** anchor parts (index = CPM PlayerModelParts order) */
    public static final String[] PARTS = {"head", "body", "larm", "rarm", "lleg", "rleg"};
    public static final int HEAD = 0, BODY = 1, LARM = 2, RARM = 3, LLEG = 4, RLEG = 5;
    /** chain setting: "auto" + the parts */
    public static final String[] PART_CHOICES = {"auto", "head", "body", "larm", "rarm", "lleg", "rleg"};
    public static int partIndex(String p) { for (int i = 0; i < PARTS.length; i++) if (PARTS[i].equals(p)) return i; return -1; }
    /** left <-> right for "Espelhar" */
    public static String mirrorPart(String p) { return Live2dMath.mirrorPart(p); }

    /** angle inputs give a softer feel per unit than speed inputs */
    public static final float ANGLE_K = 0.5f;
    /** velocity smoothing (s), the largest speed and change per tick used (blocks/s, above = clamped), pose speed limit */
    public static final float TAU = 0.06f, MAX_SPEED = 60f, MAX_DV = 14f, MAX_POSE_SPEED = 10f;

    // ---------- link axes ----------
    /**
     * How strongly a link reacts on each body axis {forward, right, up} (signed), read from its own inputs:
     * an axis with speed inputs (fwd / walk / side / vert / fall / land / wind) uses their sum 1:1 (same sign and size as the
     * input's own swing); otherwise the strongest angle input on it (head / body turn, head pitch, gravity) ×ANGLE_K.
     * "prev"-only links return 0 (they follow the link above, which already carries the inertia).
     */
    public static float[] axes(Live2dMath.Link l) {
        float[] sp = new float[3], an = new float[3];
        boolean[] has = new boolean[3];
        if (l == null || l.inputs == null) return sp;
        for (Live2dMath.Input in : l.inputs) {
            if (in == null || in.src == null) continue;
            float w = Live2dMath.clamp(in.weight, 0, 300) / 100f * (in.invert ? -1 : 1);
            int ax = -1; float f = 1; boolean speed = true;
            switch (in.src) {
                case "fwd": case "walk": case "windF": ax = 0; break;
                case "side": case "windS": ax = 1; break;
                case "vert": case "land": ax = 2; break;
                case "fall": ax = 2; f = -1; break;
                case "headPitch": ax = 0; speed = false; break;
                case "grav": ax = 0; f = -1; speed = false; break;
                case "headYaw": case "bodyYaw": case "headRel": ax = 1; speed = false; break;
                default: break;
            }
            if (ax < 0) continue;
            if (speed) { sp[ax] += w * f; has[ax] = true; }
            else if (Math.abs(w) > Math.abs(an[ax])) an[ax] = w * f;
        }
        for (int k = 0; k < 3; k++) if (!has[k]) sp[k] = an[k] * ANGLE_K;
        return sp;
    }
    /** bit flags of what a link already reads: 1/2/4 = player speed on forward / right / up, 8 = body turn, 16 = head rotation */
    public static int covered(Live2dMath.Link l) {
        int m = 0;
        if (l == null || l.inputs == null) return 0;
        for (Live2dMath.Input in : l.inputs) {
            if (in == null || in.src == null || in.weight <= 0) continue;
            switch (in.src) {
                case "fwd": case "walk": m |= 1; break;
                case "side": m |= 2; break;
                case "vert": case "fall": case "land": m |= 4; break;
                case "bodyYaw": m |= 8; break;
                case "headYaw": m |= 8 | 16; break;   // head yaw in the world = body turn + head turn
                case "headRel": case "headPitch": case "grav": m |= 16; break;
                default: break;
            }
        }
        return m;
    }

    /** the anchor velocity one link reacts to on each axis (comp = {A, B, H, R} × {fwd, right, up}) */
    public static float[] linkVel(float[][] comp, int cov) {
        float[] v = new float[3];
        for (int k = 0; k < 3; k++) {
            float x = comp[3][k];
            if ((cov & (1 << k)) == 0) x += comp[0][k];
            if ((cov & 8) == 0) x += comp[1][k];
            if ((cov & 16) == 0) x += comp[2][k];
            v[k] = x;
        }
        return v;
    }

    // ---------- inertia sub-chain ----------
    /**
     * Steps the inertia of a chain (superposed on the normal chain: same springs, links, gain and output mode).
     * vel[i] = anchor velocity link i reacts to (blocks/s, body frame), k = overall strength (1 = 100 %).
     * Link drive: x = gain·(10·k·axes·vel + prevW·a_prev); output = a − x ("atraso") or a − baseline ("segue"),
     * so a steady motion settles back to 0 and accelerations swing the chain. out[i] gets the output angle (before invert).
     */
    public static void stepInertia(List<Live2dMath.Link> links, Live2dMath.State[] st, float[][] vel, float k, float dt, float[] out) {
        float prevA = 0, prevB = 0;
        for (int i = 0; i < links.size(); i++) {
            Live2dMath.Link l = links.get(i);
            float g = Live2dMath.clamp(l.gain, 0, 300) / 100f;
            float[] ax = axes(l);
            float own = 0;
            if (vel != null && i < vel.length && vel[i] != null) own = 10f * k * (ax[0] * vel[i][0] + ax[1] * vel[i][1] + ax[2] * vel[i][2]);
            float pw = i == 0 ? 0 : prevWeight(l);
            float x = g * (own + pw * prevA);
            float b = g * (own + pw * prevB);
            Live2dMath.State s = st[i];
            Live2dMath.step(s, x, l, dt);
            float o = l.lag ? s.a - x : s.a - b;
            if (Float.isNaN(o) || Float.isInfinite(o)) { s.a = x; s.v = 0; o = 0; }
            o = Live2dMath.clamp(o, -120, 120);
            out[i] = l.invert ? -o : o;
            prevA = s.a; prevB = b;
        }
    }
    /** signed weight of the link's "prev" inputs */
    public static float prevWeight(Live2dMath.Link l) {
        float w = 0;
        if (l.inputs != null) for (Live2dMath.Input in : l.inputs) if (in != null && "prev".equals(in.src)) w += Live2dMath.clamp(in.weight, 0, 300) / 100f * (in.invert ? -1 : 1);
        return w;
    }

    // ---------- velocity tracking ----------
    /** smoothed velocity with spike rejection; returns false (and holds 0) when the sample was rejected */
    public static final class Vel {
        public final float[] v = new float[3];
        public boolean primed;
        public void reset() { v[0] = v[1] = v[2] = 0; primed = false; }
        /** new raw velocity (blocks/s) over dt seconds: smoothed; speeds above maxSpeed and changes above maxDv per sample are
         *  clamped (a lag spike never gives a huge impulse); NaN / infinite resets */
        public boolean add(float x, float y, float z, float dt, float maxSpeed, float maxDv) {
            if (!finite(x) || !finite(y) || !finite(z)) { reset(); return false; }
            float m2 = x * x + y * y + z * z;
            if (m2 > maxSpeed * maxSpeed) { float f = maxSpeed / (float) Math.sqrt(m2); x *= f; y *= f; z *= f; }
            if (!primed) { v[0] = x; v[1] = y; v[2] = z; primed = true; return true; }   // first sample: start at the real speed, no impulse
            float dx = x - v[0], dy = y - v[1], dz = z - v[2];
            if (dx * dx + dy * dy + dz * dz > maxDv * maxDv) {
                // a huge jump (lag spike, teleport-ish): clamp the change instead of a huge impulse
                float f = maxDv / (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
                x = v[0] + dx * f; y = v[1] + dy * f; z = v[2] + dz * f;
            }
            float k = 1 - (float) Math.exp(-Math.max(0, dt) / TAU);
            v[0] += (x - v[0]) * k; v[1] += (y - v[1]) * k; v[2] += (z - v[2]) * k;
            return true;
        }
    }
    static boolean finite(float f) { return !Float.isNaN(f) && !Float.isInfinite(f); }

    /** world vector (x, z horizontal, y up) -> body frame {forward, right, up} (Minecraft yaw: 0 = +Z, 90 = −X) */
    public static float[] toBody(float vx, float vy, float vz, float bodyYawDeg) {
        float[] r = Live2dMath.bodyRelative(vx, vz, bodyYawDeg);
        return new float[]{r[0], r[1], vy};
    }

    // ---------- model space ----------
    /** rotation of a point by Euler angles (radians) applied like a Minecraft ModelPart / CPM cube: R = Rz·Ry·Rx */
    public static float[] rotZYX(float[] p, float rx, float ry, float rz) {
        double x = p[0], y = p[1], z = p[2];
        double c = Math.cos(rx), s = Math.sin(rx);
        double y1 = y * c - z * s, z1 = y * s + z * c; y = y1; z = z1;
        c = Math.cos(ry); s = Math.sin(ry);
        double x1 = x * c + z * s; z1 = -x * s + z * c; x = x1; z = z1;
        c = Math.cos(rz); s = Math.sin(rz);
        x1 = x * c - y * s; y1 = x * s + y * c;
        return new float[]{(float) x1, (float) y1, (float) z};
    }
    /** point in a part's frame -> parent frame: pivot + R·p */
    public static float[] apply(float[] pose, float[] p) {
        float[] r = rotZYX(p, pose[3], pose[4], pose[5]);
        return new float[]{pose[0] + r[0], pose[1] + r[1], pose[2] + r[2]};
    }

    /** player model space (pixels, y down, model origin) -> world offset from the player's feet (blocks), like the player renderer */
    public static float[] modelToWorld(float[] p, float bodyYawDeg, boolean crouch) {
        // translate(0,-1.501,0) · scale(0.9375) · scale(-1,-1,1) · rotY(180 − bodyYaw)
        double x = p[0] / 16.0, y = p[1] / 16.0 - 1.501, z = p[2] / 16.0;
        x *= -0.9375; y *= -0.9375; z *= 0.9375;
        double t = Math.toRadians(180.0 - bodyYawDeg), c = Math.cos(t), s = Math.sin(t);
        double wx = x * c + z * s, wz = -x * s + z * c;
        return new float[]{(float) wx, (float) y + (crouch ? -0.125f : 0f), (float) wz};
    }

    /** default anchor (pixels, in the part's own frame): top of the head, lower back, near the hand, mid shin */
    public static float[] defaultOffset(int part) {
        switch (part) {
            case HEAD: return new float[]{0, -6, 0};
            case BODY: return new float[]{0, 9, 2};
            case LARM: return new float[]{1, 9, 0};
            case RARM: return new float[]{-1, 9, 0};
            case LLEG: case RLEG: return new float[]{0, 9, 0};
            default: return new float[]{0, 0, 0};
        }
    }

    /** vanilla player pose state (what HumanoidModel.setupAnim reads) */
    public static final class PoseIn {
        public float headYawRel, headPitch;   // degrees
        public float limbPos, limbAmt;        // walk animation position / speed
        public boolean crouch, riding;
    }
    /**
     * Approximate pose of one part like vanilla HumanoidModel.setupAnim (head, walk swing, sneaking, sitting):
     * {x, y, z, xRot, yRot, zRot} (pixels / radians). noHead = without the head's own rotation.
     */
    public static float[] vanillaPose(int part, PoseIn s, boolean noHead) {
        float sw = s.limbPos * 0.6662f, a = s.limbAmt;
        float[] p = new float[6];
        switch (part) {
            case HEAD:
                p[1] = s.crouch ? 4.2f : 0;
                if (!noHead) { p[3] = (float) Math.toRadians(s.headPitch); p[4] = (float) Math.toRadians(s.headYawRel); }
                break;
            case BODY:
                p[1] = s.crouch ? 3.2f : 0; p[3] = s.crouch ? 0.5f : 0; break;
            case LARM: case RARM: {
                boolean right = part == RARM;
                p[0] = right ? -5 : 5; p[1] = s.crouch ? 5.2f : 2;
                p[3] = (float) Math.cos(sw + (right ? Math.PI : 0)) * a;
                if (s.riding) p[3] += (float) (-Math.PI / 5);
                if (s.crouch) p[3] += 0.4f;
                break;
            }
            default: {
                boolean right = part == RLEG;
                p[0] = right ? -1.9f : 1.9f; p[1] = s.crouch ? 12.2f : 12; p[2] = s.crouch ? 4 : 0;
                if (s.riding) { p[3] = -1.4137167f; p[4] = (float) (right ? Math.PI / 10 : -Math.PI / 10); p[5] = right ? 0.07853982f : -0.07853982f; }
                else p[3] = (float) Math.cos(sw + (right ? 0 : Math.PI)) * 1.4f * a;
            }
        }
        return p;
    }

    /** body-frame velocity of a world-space point when the body turns from yaw0 to yaw1 (degrees) in dt; q = model-space point */
    public static float[] turnVel(float[] q, float yaw0, float yaw1, boolean crouch, float dt) {
        float[] w0 = modelToWorld(q, yaw0, crouch), w1 = modelToWorld(q, yaw1, crouch);
        return toBody((w1[0] - w0[0]) / dt, 0, (w1[2] - w0[2]) / dt, yaw1);
    }

    /** words in the chain / layer names -> part (when CPM can't tell) */
    public static int guess(Live2dMath.Chain ch) {
        StringBuilder sb = new StringBuilder(ch.name == null ? "" : ch.name);
        for (Live2dMath.Link l : ch.links) if (l != null && l.anim != null) sb.append(' ').append(l.anim);
        String s = sb.toString().toLowerCase(java.util.Locale.ROOT);
        boolean left = s.matches(".*(left|esq|_l\\b|\\bl_).*"), right = s.matches(".*(right|dir|_r\\b|\\br_).*");
        if (s.matches(".*(hair|cabel|franja|fringe|bang|orelh|\\bear|ears|antena|antenn|ahoge|tran[cç]a|braid|chap[eé]u|\\bhat|head|cabe[cç]a|ponytail|rabo.de.cavalo|pigtail|maria.chiquinha).*")) return HEAD;
        if (s.matches(".*(sleeve|manga|\\barm|bra[cç]o|hand|m[aã]o).*")) return right && !left ? RARM : LARM;
        if (s.matches(".*(\\bleg|perna|boot|bota).*")) return right && !left ? RLEG : LLEG;
        if (s.matches(".*(tail|cauda|rabo|wing|\\basa|cape|capa|skirt|saia|body|corpo).*")) return BODY;
        for (Live2dMath.Link l : ch.links) if (l != null && l.inputs != null) for (Live2dMath.Input in : l.inputs)
            if (in != null && ("headYaw".equals(in.src) || "headPitch".equals(in.src) || "headRel".equals(in.src))) return HEAD;
        return BODY;
    }

}
