/*
 * Copyright (c) 2019, 2021, Oracle and/or its affiliates. All rights reserved.
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

package sun.awt;

import java.io.File;
import java.awt.GraphicsEnvironment;
import java.awt.Toolkit;
import java.security.AccessController;
import java.security.PrivilegedAction;

public class PlatformGraphicsInfo {

    public static GraphicsEnvironment createGE() {
        return new X11GraphicsEnvironment();
    }

    public static Toolkit createToolkit() {
        return new sun.awt.X11.XToolkit();
    }

    /**
      * Called from java.awt.GraphicsEnvironment when
      * to check if on this platform, the JDK should default to
      * headless mode, in the case the application did not specify
      * a value for the java.awt.headless system property.
      */
    @SuppressWarnings("removal")
    public static boolean getDefaultHeadlessProperty() {
        boolean noDisplay =
            AccessController.doPrivileged((PrivilegedAction<Boolean>) () -> {

               final String display = System.getenv("DISPLAY");
               return display == null || display.trim().isEmpty();
            });
        if (noDisplay) {
            return true;
        }
        /*
         * If we positively identify a separate headless library support being present
         * but no corresponding headful library, then we can support headless but
         * not headful, so report that back to the caller.
         * This does require duplication of knowing the name of the libraries
         * also in libawt's OnLoad() but we need to make sure that the Java
         * code is also set up as headless from the start - it is not so easy
         * to try headful and then unwind that and then retry as headless.
         */
        boolean headless =
            AccessController.doPrivileged((PrivilegedAction<Boolean>) () -> {
                String[] libDirs = System.getProperty("sun.boot.library.path", "").split(":");
                for (String libDir : libDirs) {
                    File headlessLib = new File(libDir, "libawt_headless.so");
                    File xawtLib = new File(libDir, "libawt_xawt.so");
                    if (headlessLib.exists() && !xawtLib.exists()) {
                        return true;
                    }
                }
                return false;
            });
        return headless;
    }

    /**
      * Called from java.awt.GraphicsEnvironment when
      * getDefaultHeadlessProperty() has returned true, and
      * the application has called an API that requires headful.
      */
    public static String getDefaultHeadlessMessage() {
        return
            """

            No X11 DISPLAY variable was set,
            or no headful library support was found,
            but this program performed an operation which requires it,
            """;
    }
}
