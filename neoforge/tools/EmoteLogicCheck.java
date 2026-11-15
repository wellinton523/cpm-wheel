import com.welli.cpmwheel.ChainLogic;
import com.welli.cpmwheel.EmoteLogic;
import com.welli.cpmwheel.TriggerLogic;
import java.util.List;

/** 1.8.1 offline check: Emotecraft names / matching, trigger condition "emote", chained animations from an emote, start/stop edges, button clicks.
 *  javac -d /tmp/e181 src/main/java/com/welli/cpmwheel/{EmoteLogic,TriggerLogic,ChainLogic}.java tools/EmoteLogicCheck.java && java -cp /tmp/e181 EmoteLogicCheck */
public class EmoteLogicCheck {
    static int fails = 0, n = 0;
    static void ok(String what, boolean v) { n++; if (!v) { fails++; System.out.println("FAIL " + what); } else System.out.println("OK   " + what); }

    public static void main(String[] args) {
        // names
        ok("plain name kept", EmoteLogic.clean("Wave").equals("Wave"));
        ok("JSON string unquoted (2.2.x extraData)", EmoteLogic.clean("\"Wave hello\"").equals("Wave hello"));
        ok("JSON text object -> text", EmoteLogic.clean("{\"text\":\"Dance \\\"1\\\"\"}").equals("Dance \"1\""));
        ok("JSON translate object -> key", EmoteLogic.clean("{\"translate\":\"emote.wave\"}").equals("emote.wave"));
        ok("null -> empty", EmoteLogic.clean(null).isEmpty());
        ok("norm: case, spaces, colour codes", EmoteLogic.norm("  \u00A7aBig   Wave ").equals("big wave"));
        // matching
        ok("any matches while playing", EmoteLogic.matches("", true, "Wave") && EmoteLogic.matches("*", true, "x") && EmoteLogic.matches(null, true, ""));
        ok("nothing matches when not playing", !EmoteLogic.matches("", false, "Wave") && !EmoteLogic.matches("Wave", false, "Wave"));
        ok("named: case / spaces ignored", EmoteLogic.matches("big wave", true, "Big  Wave"));
        ok("named: other emote doesn't match", !EmoteLogic.matches("Wave", true, "Dance"));
        ok("named: playing emote without a name doesn't match a name", !EmoteLogic.matches("Wave", true, ""));
        // refs
        ok("ref any", EmoteLogic.ref(null).equals("ec:*") && EmoteLogic.ref(" ").equals("ec:*"));
        ok("ref name", EmoteLogic.ref(" Wave ").equals("ec:Wave") && EmoteLogic.refName("ec:Wave").equals("Wave"));
        ok("isRef only with prefix", EmoteLogic.isRef("ec:x") && !EmoteLogic.isRef("wave") && !EmoteLogic.isRef(null));
        ok("CPM animation names are not refs", EmoteLogic.refName("wave").isEmpty());
        // trigger condition
        TriggerLogic.Snapshot sn = new TriggerLogic.Snapshot();
        ok("emote is a condition type", java.util.Arrays.asList(TriggerLogic.TYPES).contains("emote"));
        TriggerLogic.Cond any = new TriggerLogic.Cond("emote", "", 0), wave = new TriggerLogic.Cond("emote", "Wave", 0);
        ok("default snapshot: no emote", !TriggerLogic.test(any, sn));
        final boolean[] pl = {true}; final String[] nm = {"Wave"};
        sn.emote = v -> EmoteLogic.matches(v, pl[0], nm[0]);
        ok("condition any while Wave plays", TriggerLogic.test(any, sn));
        ok("condition Wave while Wave plays", TriggerLogic.test(wave, sn));
        nm[0] = "Dance";
        ok("condition Wave while Dance plays: false", !TriggerLogic.test(wave, sn));
        wave.not = true;
        ok("NOT Wave while Dance plays: true", TriggerLogic.test(wave, sn));
        wave.not = false; pl[0] = false;
        ok("rules (AND) with emote + other cond", !TriggerLogic.eval(List.of(any, new TriggerLogic.Cond("sneak", "", 0)), "and", sn));
        pl[0] = true; sn.sneaking = true;
        ok("rules (AND) both true", TriggerLogic.eval(List.of(any, new TriggerLogic.Cond("sneak", "", 0)), "and", sn));
        // chained animation from an emote (ChainTriggers feeds 1 / 0)
        ChainLogic.Rule r = new ChainLogic.Rule(); r.from = EmoteLogic.ref("Wave"); r.to = "smile"; r.when = "end"; r.delayMs = 0;
        ChainLogic.State st = new ChainLogic.State();
        ChainLogic.step(r, st, 0, 0); ChainLogic.step(r, st, 1, 300);
        ok("chain: emote ends -> fires", ChainLogic.step(r, st, 0, 600));
        // edges for the physics kick
        EmoteLogic.Edge e = new EmoteLogic.Edge();
        ok("edge: idle", e.step(false, "") == 0);
        ok("edge: start", e.step(true, "Wave") == 1);
        ok("edge: still playing", e.step(true, "wave") == 0);
        ok("edge: switched emote = new start", e.step(true, "Dance") == 1);
        ok("edge: stop", e.step(false, "") == -1);
        ok("edge: stays stopped", e.step(false, "") == 0);
        // button click
        ok("toggle: not playing -> play", EmoteLogic.click("toggle", "Wave", false, "").equals("play"));
        ok("toggle: same emote playing -> stop", EmoteLogic.click("toggle", "Wave", true, "wave").equals("stop"));
        ok("toggle: other emote playing -> play this one", EmoteLogic.click("toggle", "Wave", true, "Dance").equals("play"));
        ok("play: always plays (restart)", EmoteLogic.click("play", "Wave", true, "Wave").equals("play"));
        ok("stop: stops any", EmoteLogic.click("stop", "", true, "Dance").equals("stop") && EmoteLogic.click("stop", "", false, "").equals("none"));
        ok("no emote chosen: toggle only stops", EmoteLogic.click("toggle", "", true, "x").equals("stop") && EmoteLogic.click("toggle", "", false, "").equals("none"));
        ok("bad action -> toggle; bad physics mode -> none", EmoteLogic.action("x").equals("toggle") && EmoteLogic.l2dMode("x").equals("none") && EmoteLogic.l2dMode("kick").equals("kick"));
        // 1.8.2 icon + details
        byte[] png = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 13};
        ok("PNG signature accepted", EmoteLogic.isPng(png));
        ok("not PNG / null / too short rejected", !EmoteLogic.isPng(new byte[]{1, 2, 3, 4, 5, 6, 7, 8, 9}) && !EmoteLogic.isPng(null) && !EmoteLogic.isPng(java.util.Arrays.copyOf(png, 8)));
        java.nio.ByteBuffer bb = java.nio.ByteBuffer.wrap(png); bb.position(5);
        byte[] got = EmoteLogic.bytes(bb);
        ok("ByteBuffer read from the start, whole icon", java.util.Arrays.equals(got, png));
        ok("original ByteBuffer position untouched", bb.position() == 5);
        java.nio.ByteBuffer direct = java.nio.ByteBuffer.allocateDirect(png.length); direct.put(png); direct.flip();
        ok("direct ByteBuffer works", java.util.Arrays.equals(EmoteLogic.bytes(direct), png) && direct.remaining() == png.length);
        byte[] copy = EmoteLogic.bytes(png); copy[0] = 0;
        ok("byte[] copied (source not changed)", png[0] == (byte) 0x89);
        ok("unknown type -> null", EmoteLogic.bytes("x") == null && EmoteLogic.bytes(null) == null);
        ok("seconds 40 -> 2 s, 50 -> 2,5 s, 0 -> empty", EmoteLogic.seconds(40).equals("2 s") && EmoteLogic.seconds(50).equals("2,5 s") && EmoteLogic.seconds(0).isEmpty());
        ok("length uses stopTick when set, else endTick", EmoteLogic.length(60, 80) == 80 && EmoteLogic.length(60, 0) == 60 && EmoteLogic.length(-1, -1) == 0);
        ok("loop span only when looping", EmoteLogic.loopSpan(true, 20, 60) == 40 && EmoteLogic.loopSpan(false, 20, 60) == 0 && EmoteLogic.loopSpan(true, 70, 60) == 0);
        ok("format key", EmoteLogic.formatKey("JSON_EMOTECRAFT").equals("json_emotecraft") && EmoteLogic.formatKey(null).equals("unknown"));
        ok("icon id", EmoteLogic.iconId("abc").equals("ec:abc") && EmoteLogic.iconId(" ").isEmpty() && EmoteLogic.isRef(EmoteLogic.iconId("abc")));
        System.out.println(fails == 0 ? "ALL OK " + n + "/" + n : fails + " FAILED of " + n);
        if (fails > 0) System.exit(1);
    }
}
