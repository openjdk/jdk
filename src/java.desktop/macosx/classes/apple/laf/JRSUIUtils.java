/*
 * Copyright (c) 2011, 2022, Oracle and/or its affiliates. All rights reserved.
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

package apple.laf;

import java.security.AccessController;

import sun.security.action.GetPropertyAction;

/**
 * Utility class for JRSUI.
 */
public final class JRSUIUtils {

    /**
     * True if OSX is 10.5 exactly (ignoring patch versions).
     */
    public static final boolean isLeopard;

    /**
     * True if OSX is 10.6 or below.
     */
    public static final boolean isSnowLeopardOrBelow;

    /**
     * True if macOS is 10.16 or higher.
     */
    public static final boolean isBigSurOrAbove;

    static {
        // split the "x.y.z" version number
        @SuppressWarnings("removal")
        String osVersion = AccessController.doPrivileged(new GetPropertyAction("os.version"));
        String[] fragments = osVersion.split("\\.");

        if (fragments.length < 2) {
            isLeopard = false;
            isSnowLeopardOrBelow = false;
            isBigSurOrAbove = false;
        } else {
            boolean isBigSurOrAbove1;
            boolean isSnowLeopardOrBelow1;
            boolean isLeopard1;

            try {
                int majorVersion = Integer.parseInt(fragments[0]);
                int minorVersion = Integer.parseInt(fragments[1]);

                isLeopard1 = majorVersion == 10 && minorVersion == 5; // exactly OSX 10.5
                isSnowLeopardOrBelow1 = majorVersion < 10 || majorVersion == 10 && minorVersion <= 6; // OSX 10.6 or below
                isBigSurOrAbove1 = majorVersion > 10 || majorVersion == 10 && minorVersion >= 16; // OSX 10.16 or above
            } catch (NumberFormatException e) {
                // was not an integer
                isLeopard1 = false;
                isSnowLeopardOrBelow1 = false;
                isBigSurOrAbove1 = false;
            }

            isBigSurOrAbove = isBigSurOrAbove1;
            isSnowLeopardOrBelow = isSnowLeopardOrBelow1;
            isLeopard = isLeopard1;
        }
    }

    // DENY
    private JRSUIUtils() {}

    /**
     * Returns {@code JRSUIControlShouldScrollToClick();} from native code.
     */
    public static native boolean shouldUseScrollToClick();
}
