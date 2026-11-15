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
        System.out.println(fails == 0 ? "ALL OK " + n + "/" + n : fails + " FAILED of " + n);
        if (fails > 0) System.exit(1);
    }
}
