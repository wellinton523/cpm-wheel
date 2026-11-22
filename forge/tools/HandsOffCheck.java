// javac -d /tmp/ho src/main/java/com/welli/cpmwheel/HandsOff.java tools/HandsOffCheck.java && java -cp /tmp/ho HandsOffCheck
import com.welli.cpmwheel.HandsOff;
import java.util.LinkedHashMap;
import java.util.Map;

public class HandsOffCheck {
    static int fails = 0, n = 0;
    static void ok(String what, boolean v) { n++; if (!v) { fails++; System.out.println("FAIL " + what); } else System.out.println("OK   " + what); }
    public static void main(String[] a) {
        Map<String, Integer> m = new LinkedHashMap<>();
        ok("empty: nothing off", HandsOff.mask(m, "tacz:gun", "tacz") == 0);
        HandsOff.set(m, "mod:tacz", HandsOff.BOTH);
        ok("mod off: both hands", HandsOff.off(HandsOff.mask(m, "tacz:gun", "tacz"), true) && HandsOff.off(HandsOff.mask(m, "tacz:gun", "tacz"), false));
        ok("other mods untouched", HandsOff.mask(m, "minecraft:stone", "minecraft") == 0);
        HandsOff.set(m, "tacz:knife", 0);
        ok("item forced on beats its mod", HandsOff.mask(m, "tacz:knife", "tacz") == 0);
        HandsOff.set(m, "tacz:knife", HandsOff.MAIN);
        ok("item: only main hand off", HandsOff.off(HandsOff.mask(m, "tacz:knife", "tacz"), true) && !HandsOff.off(HandsOff.mask(m, "tacz:knife", "tacz"), false));
        HandsOff.set(m, "tacz:knife", -1);
        ok("item inherit removes the entry", !m.containsKey("tacz:knife") && HandsOff.state(m, "tacz:knife") == -1);
        HandsOff.set(m, "mod:tacz", 0);
        ok("mod on removes the entry (default unchanged)", m.isEmpty() && HandsOff.state(m, "mod:x") == 0);
        int s = 0; StringBuilder sb = new StringBuilder(); for (int i = 0; i < 4; i++) { s = HandsOff.next(s, true); sb.append(s); }
        ok("mod cycle on>off>main>offhand>on (" + sb + ")", sb.toString().equals("3120"));
        s = -1; sb.setLength(0); for (int i = 0; i < 5; i++) { s = HandsOff.next(s, false); sb.append(s).append(','); }
        ok("item cycle inherit>on>off>main>offhand>inherit (" + sb + ")", sb.toString().equals("0,3,1,2,-1,"));
        ok("parse words", HandsOff.parse("on") == 0 && HandsOff.parse("OFF") == 3 && HandsOff.parse("principal") == 1 && HandsOff.parse("secundaria") == 2 && HandsOff.parse("herdar") == -1 && HandsOff.parse("x") == null);
        ok("valid keys", HandsOff.validKey("mod:tacz") && HandsOff.validKey("minecraft:diamond_sword") && !HandsOff.validKey("mod:") && !HandsOff.validKey("Bad Key") && !HandsOff.validKey("../x"));
        System.out.println((fails == 0 ? "ALL OK " : "FAILED ") + (n - fails) + "/" + n);
        if (fails > 0) System.exit(1);
    }
}
