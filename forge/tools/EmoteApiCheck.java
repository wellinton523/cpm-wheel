import com.welli.cpmwheel.EmoteApi;
import java.io.File;
import java.net.URL;
import java.net.URLClassLoader;

/** 1.8.1: resolves the Emotecraft API handles used by EmoteBridge against REAL jars (no game needed).
 *  java -cp <classes> EmoteApiCheck <expected flavor 2.4|2.2> <emotecraft.jar> <playerAnimator.jar> [minecraft.jar ...]
 *  Without arguments: also checks that a missing Emotecraft gives ClassNotFoundException (= "not installed", no crash). */
public class EmoteApiCheck {
    static int fails = 0, n = 0;
    static void ok(String what, boolean v) { n++; if (!v) { fails++; System.out.println("FAIL " + what); } else System.out.println("OK   " + what); }

    public static void main(String[] a) throws Exception {
        try { EmoteApi.resolve(new URLClassLoader(new URL[0], null)); ok("absent mod: ClassNotFoundException", false); }
        catch (ClassNotFoundException e) { ok("absent mod: ClassNotFoundException (bridge stays off, no crash)", true); }
        if (a.length >= 3) {
            URL[] u = new URL[a.length - 1];
            for (int i = 1; i < a.length; i++) u[i - 1] = new File(a[i]).toURI().toURL();
            EmoteApi api = EmoteApi.resolve(new URLClassLoader(u, EmoteApiCheck.class.getClassLoader()));
            ok("flavor " + a[0] + " (got " + api.flavor + ")", a[0].equals(api.flavor));
            ok("play(KeyframeAnimation) static", java.lang.reflect.Modifier.isStatic(api.play.getModifiers()) && api.play.getReturnType() == boolean.class);
            ok("stop() static: " + api.stop.getDeclaringClass().getSimpleName() + "." + api.stop.getName(), java.lang.reflect.Modifier.isStatic(api.stop.getModifiers()));
            ok("player state: isPlayingEmote / " + api.getEmote.getName() + " / getData", api.isPlaying != null && api.getEmote != null && api.getData != null);
            ok("emote list + name + uuid", api.list != null && api.name != null && api.emote != null && api.holderUuid != null && api.kaUuid != null);
            ok("name text: " + api.name.getType().getSimpleName() + (api.textString != null ? " (Emotecraft Text.getString)" : ""),
                    "2.2".equals(api.flavor) ? api.textString != null && api.text.isAssignableFrom(api.name.getType()) : api.name.getType().getSimpleName().equals("Component"));
            ok("extraData (name fallback)", api.extra != null);
        }
        System.out.println(fails == 0 ? "ALL OK " + n + "/" + n : fails + " FAILED of " + n);
        if (fails > 0) System.exit(1);
    }
}
