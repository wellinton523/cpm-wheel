import com.welli.cpmwheel.ChainLogic;
import com.welli.cpmwheel.ProfileLogic;
import java.util.Map;

/** 1.8.0 offline check: chained animations + automatic profiles per model.
 *  javac -d /tmp/f18 src/main/java/com/welli/cpmwheel/ChainLogic.java src/main/java/com/welli/cpmwheel/ProfileLogic.java tools/Feature180Check.java && java -cp /tmp/f18 Feature180Check */
public class Feature180Check {
    static int fails = 0, n = 0;
    static void ok(String what, boolean v) { n++; if (!v) { fails++; System.out.println("FAIL " + what); } else System.out.println("OK   " + what); }

    static ChainLogic.Rule rule(String when, int delay) { ChainLogic.Rule r = new ChainLogic.Rule(); r.from = "a"; r.to = "b"; r.when = when; r.delayMs = delay; return r; }

    public static void main(String[] args) {
        // ---- chained animations ----
        ChainLogic.Rule r = rule("end", 0); ChainLogic.State s = new ChainLogic.State();
        ok("first look never fires (already playing at load)", !ChainLogic.step(r, s, 1, 0));
        ok("still playing: no fire", !ChainLogic.step(r, s, 1, 50));
        ok("ends: fires at once (delay 0)", ChainLogic.step(r, s, 0, 100));
        ok("stays stopped: no repeat", !ChainLogic.step(r, s, 0, 150) && !ChainLogic.step(r, s, 0, 2000));
        ok("-1 (unknown) counts as stopped", !ChainLogic.step(r, s, -1, 2050));
        r = rule("end", 1000); s = new ChainLogic.State();
        ChainLogic.step(r, s, 0, 0); ChainLogic.step(r, s, 2, 50);
        ok("end + 1 s: not yet at the end", !ChainLogic.step(r, s, 0, 100));
        ok("end + 1 s: not at 0.9 s", !ChainLogic.step(r, s, 0, 1000));
        ok("end + 1 s: fires at 1.1 s", ChainLogic.step(r, s, 0, 1100));
        r = rule("start", 500); s = new ChainLogic.State();
        ChainLogic.step(r, s, 0, 0);
        ok("start: the start itself doesn't fire with a delay", !ChainLogic.step(r, s, 1, 50));
        ok("start + 0.5 s fires even if A already ended", !ChainLogic.step(r, s, 0, 300) && ChainLogic.step(r, s, 0, 600));
        r = rule("start", 0); s = new ChainLogic.State();
        ChainLogic.step(r, s, -1, 0);
        ok("start from unknown (-1) fires", ChainLogic.step(r, s, 1, 50));
        // loop guard
        r = rule("end", 0); s = new ChainLogic.State();
        ChainLogic.step(r, s, 1, 0); boolean f1 = ChainLogic.step(r, s, 0, 50); ChainLogic.step(r, s, 1, 100); boolean f2 = ChainLogic.step(r, s, 0, 150);
        ok("fast loop: second fire within 250 ms is skipped", f1 && !f2);
        ChainLogic.step(r, s, 1, 400); ok("after the gap it fires again", ChainLogic.step(r, s, 0, 450));
        // disabled / incomplete rules
        ChainLogic.Rule off = rule("end", 0); off.on = false; s = new ChainLogic.State();
        ChainLogic.step(off, s, 1, 0); ok("rule off never fires", !ChainLogic.step(off, s, 0, 50));
        ChainLogic.Rule empty = rule("end", 0); empty.to = ""; s = new ChainLogic.State();
        ChainLogic.step(empty, s, 1, 0); ok("rule without target never fires", !ChainLogic.step(empty, s, 0, 50));
        ok("new rules are on, ATIVAR, at the end, no delay", new ChainLogic.Rule().on && "on".equals(new ChainLogic.Rule().op) && "end".equals(new ChainLogic.Rule().when) && new ChainLogic.Rule().delayMs == 0);
        ok("target: on / off / toggle", ChainLogic.target("on", 0, 5) == 5 && ChainLogic.target("off", 5, 5) == 0
                && ChainLogic.target("toggle", 3, 5) == 0 && ChainLogic.target("toggle", 0, 5) == 5 && ChainLogic.target("toggle", -1, 1) == 1);

        // ---- automatic profiles ----
        Map<String, String> links = Map.of("modelA", "Demonio", "modelB", "Demonio", "modelC", "../../evil", "modelD", "  ");
        ok("profiles off: always the model's own key", "modelA".equals(ProfileLogic.resolve(false, links, "modelA")));
        ok("profiles on: linked models share one profile", "@Demonio".equals(ProfileLogic.resolve(true, links, "modelA")) && "@Demonio".equals(ProfileLogic.resolve(true, links, "modelB")));
        ok("profiles on: unlinked model keeps its own config", "modelX".equals(ProfileLogic.resolve(true, links, "modelX")));
        ok("blank link = not linked", "modelD".equals(ProfileLogic.resolve(true, links, "modelD")));
        ok("unsafe names are cleaned (no path escape)", ProfileLogic.resolve(true, links, "modelC").startsWith("@") && !ProfileLogic.resolve(true, links, "modelC").startsWith("@.") && !ProfileLogic.clean("../../evil").contains("/"));
        ok("null / blank model = default", "default".equals(ProfileLogic.resolve(true, links, null)) && "default".equals(ProfileLogic.resolve(false, null, " ")));
        ok("isProfile", ProfileLogic.isProfile("@x") && !ProfileLogic.isProfile("x") && !ProfileLogic.isProfile(null));
        ok("long names cut to 32", ProfileLogic.clean("x".repeat(50)).length() == 32);

        System.out.println((fails == 0 ? "ALL OK " : "FAILED ") + (n - fails) + "/" + n);
        if (fails > 0) System.exit(1);
    }
}
