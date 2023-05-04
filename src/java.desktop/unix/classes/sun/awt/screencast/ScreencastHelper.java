package sun.awt.screencast;

import sun.security.action.GetPropertyAction;

import java.security.AccessController;
import java.util.prefs.Preferences;

@SuppressWarnings("removal")
public class ScreencastHelper {

    private static final boolean screencastDebug;
    private static final boolean isNativeLoaded;

    private static final Preferences TOKEN_STORE =
            Preferences.userNodeForPackage(ScreencastHelper.class);
    private static final String TOKEN_KEY = "restoreToken";

    private ScreencastHelper() {}

    static {
        screencastDebug = Boolean.parseBoolean(
                AccessController.doPrivileged(
                        new GetPropertyAction(
                                "awt.robot.screencastDebug",
                                "false"
                        )
                ));

        isNativeLoaded = loadPipewire(screencastDebug);
        if (!isNativeLoaded) {
            System.err.println(
                    "Could not load native libraries for ScreencastHelper"
            );
        }
    }

    public static boolean isAvailable() {
        return isNativeLoaded;
    }

    private static native boolean loadPipewire(boolean screencastDebug);

    public static void resetToken() {
        TOKEN_STORE.remove(TOKEN_KEY);
    }

    // called from native
    private static void storeToken(String token) {
        TOKEN_STORE.put(TOKEN_KEY, token);
    }

    private static String getToken() {
        return TOKEN_STORE.get(TOKEN_KEY, null);
    }

    private static final int DENIED = -11; //defined in native code

    private static synchronized native int getRGBPixelsImpl(
            int x, int y, int width, int height, int[] pixelArray, String token
    );

    public static synchronized void getRGBPixels(
            int x, int y, int width, int height, int[] pixelArray
    ) {
        if (isNativeLoaded
                && (DENIED
                == getRGBPixelsImpl(x, y, width, height, pixelArray, getToken()))
        ) {
            throw new SecurityException(
                    "User denied the screen data capture"
            );
        }
    }
}
