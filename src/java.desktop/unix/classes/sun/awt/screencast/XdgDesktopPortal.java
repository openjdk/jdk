/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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

import sun.awt.SunToolkit;
import sun.awt.UNIXToolkit;

import java.awt.Toolkit;

public class XdgDesktopPortal {
    private static final String METHOD_X11 = "x11";
    private static final String METHOD_SCREENCAST = "dbusScreencast";
    private static final String METHOD_REMOTE_DESKTOP = "dbusRemoteDesktop";

    private static final String method;
    private static final boolean isRemoteDesktop;
    private static final boolean isScreencast;

    private XdgDesktopPortal() {}

    static {
        boolean isOnWayland = false;

        if (Toolkit.getDefaultToolkit() instanceof SunToolkit sunToolkit) {
            isOnWayland = sunToolkit.isRunningOnWayland();
        }

        String defaultMethod = METHOD_X11;
        if (isOnWayland) {
            Integer gnomeShellVersion = null;

            UNIXToolkit toolkit = (UNIXToolkit) Toolkit.getDefaultToolkit();
            if ("gnome".equals(toolkit.getDesktop())) {
                gnomeShellVersion = toolkit.getGnomeShellMajorVersion();
            }

            defaultMethod = (gnomeShellVersion != null && gnomeShellVersion >= 47)
                    ? METHOD_REMOTE_DESKTOP
                    : METHOD_SCREENCAST;
        }

        String m = System.getProperty("awt.robot.screenshotMethod", defaultMethod);

        if (!METHOD_REMOTE_DESKTOP.equals(m)
                && !METHOD_SCREENCAST.equals(m)
                && !METHOD_X11.equals(m)) {
            m = defaultMethod;
        }

        isRemoteDesktop = METHOD_REMOTE_DESKTOP.equals(m);
        isScreencast = METHOD_SCREENCAST.equals(m);
        method = m;

    }

    public static String getMethod() {
        return method;
    }

    public static boolean isRemoteDesktop() {
        return isRemoteDesktop;
    }

    public static boolean isScreencast() {
        return isScreencast;
    }
}
