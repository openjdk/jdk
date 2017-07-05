/*
 * Copyright 2007 Sun Microsystems, Inc.  All Rights Reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Sun designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Sun in the LICENSE file that accompanied this code.
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
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 */

package sun.awt;

import java.awt.Window;

import java.lang.reflect.Field;

import sun.util.logging.PlatformLogger;

import java.security.AccessController;
import java.security.PrivilegedAction;

public class WindowAccessor {

    private static Class windowClass;
    private static Field fieldIsAutoRequestFocus;
    private static Field fieldIsTrayIconWindow;

    private static final PlatformLogger log = PlatformLogger.getLogger("sun.awt.WindowAccessor");

    private WindowAccessor() {
    }

    static {
        AccessController.doPrivileged( new PrivilegedAction() {
                public Object run() {
                    try {
                        windowClass = Class.forName("java.awt.Window");
                        fieldIsAutoRequestFocus = windowClass.getDeclaredField("autoRequestFocus");
                        fieldIsAutoRequestFocus.setAccessible(true);
                        fieldIsTrayIconWindow = windowClass.getDeclaredField("isTrayIconWindow");
                        fieldIsTrayIconWindow.setAccessible(true);

                    } catch (NoSuchFieldException e) {
                        log.fine("Unable to initialize WindowAccessor: ", e);
                    } catch (ClassNotFoundException e) {
                        log.fine("Unable to initialize WindowAccessor: ", e);
                    }
                    return null;
                }
            });
    }

    public static boolean isAutoRequestFocus(Window w) {
        try {
            return fieldIsAutoRequestFocus.getBoolean(w);

        } catch (IllegalAccessException e) {
            log.fine("Unable to access the Window object", e);
        }
        return true;
    }

    public static boolean isTrayIconWindow(Window w) {
        try {
            return fieldIsTrayIconWindow.getBoolean(w);

        } catch (IllegalAccessException e) {
            log.fine("Unable to access the Window object", e);
        }
        return false;
    }

    public static void setTrayIconWindow(Window w, boolean isTrayIconWindow) {
        try {
            fieldIsTrayIconWindow.set(w, isTrayIconWindow);

        } catch (IllegalAccessException e) {
            log.fine("Unable to access the Window object", e);
        }
    }
}
