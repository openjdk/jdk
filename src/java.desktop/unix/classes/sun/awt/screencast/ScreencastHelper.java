/*
 * Copyright (c) 2023, 2025, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

package sun.awt.screencast;

import sun.awt.UNIXToolkit;
import sun.java2d.pipe.Region;

import java.awt.GraphicsConfiguration;
import java.awt.GraphicsEnvironment;
import java.awt.Rectangle;
import java.awt.Toolkit;
import java.awt.geom.AffineTransform;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;
import java.util.function.Function;
import java.util.stream.IntStream;

/**
 * Helper class for grabbing pixels from the screen using the
 * <a href="https://flatpak.github.io/xdg-desktop-portal/#gdbus-org.freedesktop.portal.ScreenCast">
 * org.freedesktop.portal.ScreenCast API</a>
 */

public final class ScreencastHelper {

    static final boolean SCREENCAST_DEBUG;
    private static final boolean IS_NATIVE_LOADED;

    private static final int ERROR = -1;
    private static final int DENIED = -11;
    private static final int OUT_OF_BOUNDS = -12;
    private static final int NO_STREAMS = -13;

    private static final int XDG_METHOD_SCREENCAST = 0;
    private static final int XDG_METHOD_REMOTE_DESKTOP = 1;

    private static final int DELAY_BEFORE_SESSION_CLOSE = 2000;

    private static volatile TimerTask timerTask = null;
    private static final Timer timerCloseSession
            = new Timer("auto-close screencast session", true);


    private ScreencastHelper() {}

    static {
        SCREENCAST_DEBUG = Boolean.getBoolean("awt.robot.screenshotDebug");

        boolean loadFailed = false;

        boolean shouldLoadNative = XdgDesktopPortal.isRemoteDesktop()
                || XdgDesktopPortal.isScreencast();

        int methodId = XdgDesktopPortal.isScreencast()
                ? XDG_METHOD_SCREENCAST
                : XDG_METHOD_REMOTE_DESKTOP;

        if (!(Toolkit.getDefaultToolkit() instanceof UNIXToolkit tk
              && tk.loadGTK())
              || !(shouldLoadNative && loadPipewire(methodId, SCREENCAST_DEBUG))) {

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

    private static native boolean loadPipewire(int method, boolean isDebug);

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
                .map(graphicsDevice -> {
                    GraphicsConfiguration gc =
                            graphicsDevice.getDefaultConfiguration();
                    Rectangle screen = gc.getBounds();
                    AffineTransform tx = gc.getDefaultTransform();

                    return new Rectangle(
                            Region.clipRound(screen.x * tx.getScaleX()),
                            Region.clipRound(screen.y * tx.getScaleY()),
                            Region.clipRound(screen.width * tx.getScaleX()),
                            Region.clipRound(screen.height * tx.getScaleY())
                    );
                })
                .toList();
    }

    private static synchronized native void closeSession();

    private static void timerCloseSessionRestart() {
        if (timerTask != null) {
            timerTask.cancel();
        }

        timerTask = new TimerTask() {
            @Override
            public void run() {
                closeSession();
            }
        };

        timerCloseSession.schedule(timerTask, DELAY_BEFORE_SESSION_CLOSE);
    }

    public static synchronized void getRGBPixels(
            int x, int y, int width, int height, int[] pixelArray
    ) {
        if (!IS_NATIVE_LOADED) return;

        timerCloseSessionRestart();

        Rectangle captureArea = new Rectangle(x, y, width, height);

        List<Rectangle> affectedScreenBounds = getSystemScreensBounds()
                .stream()
                .filter(captureArea::intersects)
                .toList();

        if (SCREENCAST_DEBUG) {
            System.out.printf("// getRGBPixels in %s, affectedScreenBounds %s\n",
                    captureArea, affectedScreenBounds);
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
            } else if (!checkReturnValue(retVal, true)) {
                return;
            } // else, try other tokens
        }

        // we do not have a saved token or it did not work,
        // try without the token to show the system's permission request window
        retVal = getRGBPixelsImpl(
                x, y, width, height,
                pixelArray,
                affectedScreenBoundsArray,
                null
        );

        checkReturnValue(retVal, true);
    }

    private static boolean checkReturnValue(int retVal,
                                            boolean throwException) {
        if (retVal == DENIED) {
            if (SCREENCAST_DEBUG) {
                System.err.println("robot action: access denied by user.");
            }
            if (throwException) {
                // user explicitly denied the capture, no more tries.
                throw new SecurityException(
                        "Screen Capture in the selected area was not allowed"
                );
            }
        } else if (retVal == ERROR) {
            if (SCREENCAST_DEBUG) {
                System.err.println("robot action: failed.");
            }
        } else if (retVal == OUT_OF_BOUNDS) {
            if (SCREENCAST_DEBUG) {
                System.err.println(
                        "Token does not provide access to requested area.");
            }
        } else if (retVal == NO_STREAMS) {
            if (SCREENCAST_DEBUG) {
                System.err.println("robot action: no streams available");
            }
        }
        return retVal != ERROR;
    }

    private static void performWithToken(Function<String, Integer> func) {
        if (!XdgDesktopPortal.isRemoteDesktop() || !IS_NATIVE_LOADED) return;

        timerCloseSessionRestart();

        for (TokenItem tokenItem : TokenStorage.getTokens(getSystemScreensBounds())) {
            int retVal = func.apply(tokenItem.token);

            if (retVal >= 0 || !checkReturnValue(retVal, false)) {
                return;
            }
        }

        checkReturnValue(func.apply(null), false);
    }

    public static synchronized void remoteDesktopMouseMove(int x, int y) {
        performWithToken((token) -> remoteDesktopMouseMoveImpl(x, y, token));
    }

    public static synchronized void remoteDesktopMouseButton(boolean isPress, int buttons) {
        performWithToken((token) -> remoteDesktopMouseButtonImpl(isPress, buttons, token));
    }

    public static synchronized void remoteDesktopMouseWheel(int wheel) {
        performWithToken((token) -> remoteDesktopMouseWheelImpl(wheel, token));
    }

    public static synchronized void remoteDesktopKey(boolean isPress, int key) {
        performWithToken((token) -> remoteDesktopKeyImpl(isPress, key, token));
    }

    private static synchronized native int remoteDesktopMouseMoveImpl(int x, int y, String token);
    private static synchronized native int remoteDesktopMouseButtonImpl(boolean isPress, int buttons, String token);
    private static synchronized native int remoteDesktopMouseWheelImpl(int wheelAmt, String token);
    private static synchronized native int remoteDesktopKeyImpl(boolean isPress, int key, String token);
}
