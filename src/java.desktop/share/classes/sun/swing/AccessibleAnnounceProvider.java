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

package sun.swing;

import javax.accessibility.Accessible;
import javax.accessibility.AccessibleAnnouncer;
import java.lang.annotation.Native;

/**
  * This class provides the ability to speak a given string using screen readers.
 */
public class AccessibleAnnounceProvider {

    private AccessibleAnnounceProvider() {}

    /**
     * This method checks the parameters for announcing
     * and makes a native call to the announsing screen reader API.
     *
     * @param a      an accessible to which the announcing relates
     * @param str      string for announcing
     * @param priority priority for announcing
     */
    public static void announce(Accessible a, final String str, final int priority) throws Exception {
        if (str == null ||
        priority != AccessibleAnnouncer.ANNOUNCE_WITHOUT_INTERRUPTING_CURRENT_OUTPUT &&
        priority != AccessibleAnnouncer.ANNOUNCE_WITH_INTERRUPTING_CURRENT_OUTPUT) {
            throw new IllegalArgumentException("Invalid parameters passed for declaration");
        }

        nativeAnnounce(a, str, priority);
    }

    public static boolean isAnnounceExists() {
        try {
            return nativeIsAnnounceExists();
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        return false;
    }

    private static native void nativeAnnounce(Accessible a, final String str, final int priority);
    private static native boolean nativeIsAnnounceExists();
}
