package sun.awt.screencast;

import sun.security.action.GetPropertyAction;

import java.awt.GraphicsEnvironment;
import java.awt.Rectangle;
import java.security.AccessController;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.IntStream;

@SuppressWarnings("removal")
public class ScreencastHelper {

    static final boolean SCREENCAST_DEBUG;
    private static final boolean IS_NATIVE_LOADED;


    private static final int DENIED = -11;

    private ScreencastHelper() {
    }

    static {
        SCREENCAST_DEBUG = Boolean.parseBoolean(
                AccessController.doPrivileged(
                        new GetPropertyAction(
                                "awt.robot.screencastDebug",
                                "false"
                        )
                ));

        boolean loadFailed = false;

        if (!TokenStorage.initSuccessful()
                || !loadPipewire(SCREENCAST_DEBUG)) {

            System.err.println(
                    "Could not load native libraries for ScreencastHelper"
            );

            loadFailed = true;
        }

        IS_NATIVE_LOADED = !loadFailed;
    }

    public static boolean isAvailable() {
        return IS_NATIVE_LOADED;
    }

    private static native boolean loadPipewire(boolean screencastDebug);

    private static native int getRGBPixelsImpl(
            int x, int y, int width, int height,
            int[] pixelArray,
            int[] affectedScreensBoundsArray,
            String token
    );

    private static List<Rectangle> getSystemScreensBounds() {
        return Arrays
                .stream(GraphicsEnvironment
                        .getLocalGraphicsEnvironment()
                        .getScreenDevices())
                .map(graphicsDevice ->
                        graphicsDevice.getDefaultConfiguration().getBounds()
                ).toList();
    }

    public static synchronized void getRGBPixels(
            int x, int y, int width, int height, int[] pixelArray
    ) {
        if (!IS_NATIVE_LOADED) return;

        Rectangle captureArea = new Rectangle(x, y, width, height);

        List<Rectangle> affectedScreenBounds =  getSystemScreensBounds()
                .stream()
                .filter(captureArea::intersects)
                .toList();

        if (SCREENCAST_DEBUG) {
            System.out.println("// getRGBPixels affectedScreenBounds "
                    + affectedScreenBounds);
        }

        if (affectedScreenBounds.isEmpty()) {
            if (SCREENCAST_DEBUG) {
                System.out.println("// getRGBPixels - requested area "
                        + "outside of any screen");
            }
            return;
        }

        int retVal;
        Set<TokenItem> tokensForRectangle =
                TokenStorage.getTokens(affectedScreenBounds);

        int[] affectedScreenBoundsArray = affectedScreenBounds
                .stream()
                .filter(captureArea::intersects)
                .flatMapToInt(bounds -> IntStream.of(
                        bounds.x, bounds.y,
                        bounds.width, bounds.height
                ))
                .toArray();

        for (TokenItem tokenItem : tokensForRectangle) {
            retVal = getRGBPixelsImpl(
                    x, y, width, height,
                    pixelArray,
                    affectedScreenBoundsArray,
                    tokenItem.token
            );

            if (retVal >= 0) { // we have received a screen data
                return;
            }
        }

        // we do not have a saved token or it did not work,
        // try without the token to show the system's permission request window
        retVal = getRGBPixelsImpl(
                x, y, width, height,
                pixelArray,
                affectedScreenBoundsArray,
                null
        );

        if (retVal == DENIED) {
            throw new SecurityException(
                    "Screen Capture in the selected area was not allowed"
            );
        }
    }
}
