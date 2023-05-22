/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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
import sun.security.action.GetPropertyAction;

import java.awt.GraphicsEnvironment;
import java.awt.Rectangle;
import java.awt.Toolkit;
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

        if (!(Toolkit.getDefaultToolkit()
                instanceof UNIXToolkit tk && tk.loadGTK())
                || !TokenStorage.initSuccessful()
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
