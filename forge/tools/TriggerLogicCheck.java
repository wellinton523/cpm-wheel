import com.welli.cpmwheel.TriggerLogic;
import com.welli.cpmwheel.TriggerLogic.Cond;
import com.welli.cpmwheel.TriggerLogic.Snapshot;
import java.util.List;

/** 1.6.0 offline check of trigger conditions and debounce: javac -d /tmp/tl src/main/java/com/welli/cpmwheel/TriggerLogic.java tools/TriggerLogicCheck.java && java -cp /tmp/tl TriggerLogicCheck */
public class TriggerLogicCheck {
    static int fails = 0, n = 0;
    static void ok(String what, boolean v) { n++; if (!v) { fails++; System.out.println("FAIL " + what); } else System.out.println("OK   " + what); }
    static Cond c(String t, String v, int num) { return new Cond(t, v, num); }
    static Cond not(Cond c) { c.not = true; return c; }

    public static void main(String[] a) {
        Snapshot s = new Snapshot();
        s.effects.put("minecraft:poison", 1);
        s.health = 5; s.msSinceHurt = 1500; s.dayTime = 18000; s.raining = true; s.thundering = false;
        s.underwater = false; s.sneaking = true; s.sprinting = false; s.dimension = "minecraft:the_nether";
        s.biome = v -> v.equals("desert") || v.equals("minecraft:desert") || v.equals("#minecraft:is_hot");
        s.hold = (v, h) -> v.equals("lantern") && h != 2;

        ok("effect present, amp II >= I", TriggerLogic.test(c("effect", "minecraft:poison", 0), s));
        ok("effect amp II >= II", TriggerLogic.test(c("effect", "minecraft:poison", 1), s));
        ok("effect amp II < III", !TriggerLogic.test(c("effect", "minecraft:poison", 2), s));
        ok("missing effect", !TriggerLogic.test(c("effect", "minecraft:speed", 0), s));
        ok("health 5 <= 6", TriggerLogic.test(c("health", "", 6), s));
        ok("health 5 > 4", !TriggerLogic.test(c("health", "", 4), s));
        ok("damage 1.5s within 3s", TriggerLogic.test(c("damage", "", 3), s));
        ok("damage 1.5s not within 1s", !TriggerLogic.test(c("damage", "", 1), s));
        ok("rain", TriggerLogic.test(c("rain", "", 0), s));
        ok("no thunder", !TriggerLogic.test(c("thunder", "", 0), s));
        ok("not underwater", TriggerLogic.test(not(c("water", "", 0)), s));
        ok("sneak", TriggerLogic.test(c("sneak", "", 0), s));
        ok("not sprinting", !TriggerLogic.test(c("sprint", "", 0), s));
        ok("biome short id", TriggerLogic.test(c("biome", "desert", 0), s));
        ok("biome tag", TriggerLogic.test(c("biome", "#minecraft:is_hot", 0), s));
        ok("other biome", !TriggerLogic.test(c("biome", "plains", 0), s));
        ok("empty biome never", !TriggerLogic.test(c("biome", "", 0), s));
        ok("dimension short", TriggerLogic.test(c("dimension", "the_nether", 0), s));
        ok("dimension full", TriggerLogic.test(c("dimension", "minecraft:the_nether", 0), s));
        ok("dimension other", !TriggerLogic.test(c("dimension", "the_end", 0), s));
        ok("dimMatches no partial", !TriggerLogic.dimMatches("nether", "minecraft:the_nether"));
        ok("hold any hand", TriggerLogic.test(c("hold", "lantern", 0), s));
        ok("hold off hand only", !TriggerLogic.test(c("hold", "lantern", 2), s));
        ok("unknown type false", !TriggerLogic.test(c("bogus", "", 0), s));

        // 1.7.2 animation / pose conditions
        s.anim = (v, min) -> v.equals("voar") ? 1 >= min : v.equals("fis_asas") && 128 >= min;
        s.flying = true; s.gliding = false; s.swimming = false; s.riding = true; s.sleeping = false;
        ok("anim playing (>=1)", TriggerLogic.test(c("anim", "voar", 1), s));
        ok("anim num 0 counts as >=1", TriggerLogic.test(c("anim", "voar", 0), s));
        ok("anim below minimum", !TriggerLogic.test(c("anim", "voar", 2), s));
        ok("value layer >= 100", TriggerLogic.test(c("anim", "fis_asas", 100), s));
        ok("other anim not playing", !TriggerLogic.test(c("anim", "andar", 1), s));
        ok("empty anim never", !TriggerLogic.test(c("anim", "", 1), s));
        ok("flying", TriggerLogic.test(c("fly", "", 0), s));
        ok("not gliding", !TriggerLogic.test(c("glide", "", 0), s));
        ok("riding", TriggerLogic.test(c("ride", "", 0), s));
        ok("not swimming / sleeping", !TriggerLogic.test(c("swim", "", 0), s) && !TriggerLogic.test(c("sleep", "", 0), s));
        ok("suspend list OR: voar or glide", TriggerLogic.eval(List.of(c("anim", "voar", 1), c("glide", "", 0)), "or", s));

        // night / day
        Snapshot d = new Snapshot();
        d.dayTime = 18000; ok("18000 night", TriggerLogic.night(d) && !TriggerLogic.test(c("day", "", 0), d));
        d.dayTime = 6000; ok("6000 day", !TriggerLogic.night(d) && TriggerLogic.test(c("day", "", 0), d));
        d.dayTime = 13000; ok("13000 night starts", TriggerLogic.night(d));
        d.dayTime = 23000; ok("23000 day again", !TriggerLogic.night(d));
        d.dayTime = 24000L * 5 + 15000; ok("day counter wraps", TriggerLogic.night(d));
        d.fixedTime = true; ok("nether: neither night nor day", !TriggerLogic.test(c("night", "", 0), d) && !TriggerLogic.test(c("day", "", 0), d));

        // AND / OR / NOT
        ok("and all true", TriggerLogic.eval(List.of(c("rain", "", 0), c("sneak", "", 0)), "and", s));
        ok("and one false", !TriggerLogic.eval(List.of(c("rain", "", 0), c("sprint", "", 0)), "and", s));
        ok("or one true", TriggerLogic.eval(List.of(c("sprint", "", 0), c("rain", "", 0)), "or", s));
        ok("or all false", !TriggerLogic.eval(List.of(c("sprint", "", 0), c("thunder", "", 0)), "or", s));
        ok("and with not", TriggerLogic.eval(List.of(c("rain", "", 0), not(c("sprint", "", 0))), "and", s));
        ok("empty list never", !TriggerLogic.eval(List.of(), "and", s) && !TriggerLogic.eval(List.of(), "or", s));
        ok("null list never", !TriggerLogic.eval(null, "and", s));

        // debounce: delayStart 1000, delayEnd 500
        TriggerLogic.Debounce db = new TriggerLogic.Debounce();
        ok("t0 want: nothing yet", db.update(true, 0, 1000, 500) == 0);
        ok("t999 still waiting", db.update(true, 999, 1000, 500) == 0);
        ok("t1000 start", db.update(true, 1000, 1000, 500) == 1);
        ok("t1050 no repeat start", db.update(true, 1050, 1000, 500) == 0);
        ok("t1100 false: wait end delay", db.update(false, 1100, 1000, 500) == 0);
        ok("t1400 true again: no end", db.update(true, 1400, 1000, 500) == 0);
        ok("t1500 false again (timer reset)", db.update(false, 1500, 1000, 500) == 0);
        ok("t1999 not yet", db.update(false, 1999, 1000, 500) == 0);
        ok("t2000 end", db.update(false, 2000, 1000, 500) == -1);
        // rapid toggling every 100 ms for 3 s never starts with a 1 s start delay
        TriggerLogic.Debounce fl = new TriggerLogic.Debounce();
        int events = 0;
        for (int t = 0; t < 3000; t += 50) events += Math.abs(fl.update((t / 100) % 2 == 0, t, 1000, 500));
        ok("rapid toggle: no flicker", events == 0 && !fl.active);
        // active rule with quick off/on blips keeps running
        TriggerLogic.Debounce bl = new TriggerLogic.Debounce();
        bl.update(true, 0, 0, 500);
        int ends = 0;
        for (int t = 50; t < 5000; t += 50) ends += bl.update(t % 1000 >= 800 ? false : true, t, 0, 500) < 0 ? 1 : 0;
        ok("short blips (<500ms) never end it", ends == 0 && bl.active);
        // zero delays: immediate
        TriggerLogic.Debounce z = new TriggerLogic.Debounce();
        ok("zero delay start", z.update(true, 5, 0, 0) == 1);
        ok("zero delay end", z.update(false, 6, 0, 0) == -1);

        System.out.println((fails == 0 ? "ALL OK " : "FAILED ") + (n - fails) + "/" + n);
        if (fails > 0) System.exit(1);
    }
}
