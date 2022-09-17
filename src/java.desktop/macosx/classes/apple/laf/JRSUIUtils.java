/*
 * Copyright (c) 2011, 2021, Oracle and/or its affiliates. All rights reserved.
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
    public static boolean isLeopard = currentMacOSXVersionMatchesGivenVersionRange(10, 5,
            true, false, false);

    /**
     * True if OSX is 10.6 or below.
     */
    public static boolean isSnowLeopardOrBelow = currentMacOSXVersionMatchesGivenVersionRange(10, 6,
            true, true, false);

    /**
     * True if macOS is 10.16 or higher.
     */
    public static boolean isBigSurOrAbove = currentMacOSXVersionMatchesGivenVersionRange(10, 16,
            true, false, true);

    // DENY
    private JRSUIUtils() {}

    /**
     * Method for checking macOS version compatibility (ignores the patch).
     *
     * @param majorVersion major release (e.g. 10 for OSX)
     * @param minorVersion minor release
     * @param inclusive set to true to return true for an equal version
     * @param matchBelow set to true to return true when the version is smaller
     * @param matchAbove set to true to return true when the version is greater
     * @return true if the running version matches
     */
    static boolean currentMacOSXVersionMatchesGivenVersionRange(
            final int majorVersion, final int minorVersion, final boolean inclusive,
            final boolean matchBelow, final boolean matchAbove) {
        // split the "x.y.z" version number
        @SuppressWarnings("removal")
        String osVersion = AccessController.doPrivileged(new GetPropertyAction("os.version"));
        String[] fragments = osVersion.split("\\.");

        if (fragments.length < 2) return false;

        // check if os.version matches the given version using the given match method
        try {
            int majorVers = Integer.parseInt(fragments[0]);
            int minorVers = Integer.parseInt(fragments[1]);

            if (inclusive && majorVers == majorVersion && minorVers == minorVersion) return true;
            if (matchBelow &&
                    (majorVers < majorVersion ||
                            (majorVers == majorVersion && minorVers < minorVersion))) return true;
            if (matchAbove &&
                    (majorVers > majorVersion ||
                            (majorVers == majorVersion && minorVers > minorVersion))) return true;

        } catch (NumberFormatException e) {
            // was not an integer
        }
        return false;
    }

    /**
     * Returns {@code JRSUIControlShouldScrollToClick();} from native code.
     */
    public static native boolean shouldUseScrollToClick();
}
