/*
 * Copyright (c) 2004, 2023, Oracle and/or its affiliates. All rights reserved.
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

package sun.awt.windows;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Insets;
import java.awt.Point;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import static com.sun.java.swing.plaf.windows.TMSchema.Part;

/**
 * Implements Theme Support for Windows XP.
 *
 * @author Sergey Salishev
 * @author Bino George
 * @author Igor Kushnirskiy
 */
public final class ThemeReader {

    private static final int defaultDPI = 96;

    /**
     * List of widgets for which we need to get the part size for the current DPI.
     */
    private static final  List<String> partSizeWidgets =
            List.of("MENU", "BUTTON");

    /**
     * List of widget parts for which we need to get the part size for the current DPI.
     */
    private static final List<Integer> partSizeWidgetParts =
            List.of(Part.BP_RADIOBUTTON.getValue(),
                    Part.BP_CHECKBOX.getValue(),
                    Part.MP_POPUPCHECK.getValue());

    private static final Map<Integer, Map<String, Long>> dpiAwareWidgetToTheme
            = new HashMap<>();

    // lock for the cache
    // reading should be done with readLock
    // writing with writeLock
    private static final ReadWriteLock readWriteLock =
        new ReentrantReadWriteLock();
    private static final Lock readLock = readWriteLock.readLock();
    private static final Lock writeLock = readWriteLock.writeLock();
    private static volatile boolean valid;
    private static volatile boolean isThemed;

    static volatile boolean xpStyleEnabled;

    static void flush() {
        // Could be called on Toolkit thread, so do not try to acquire locks
        // to avoid deadlock with theme initialization
        valid = false;
    }

    private static native boolean initThemes();

    public static boolean isThemed() {
        writeLock.lock();
        try {
            isThemed = initThemes();
            return isThemed;
        } finally {
            writeLock.unlock();
        }
    }

    public static boolean isXPStyleEnabled() {
        return xpStyleEnabled;
    }

    private static Long openThemeImpl(String widget, int dpi) {
       Long theme;
       int i = widget.indexOf("::");
       if (i > 0) {
           // We're using the syntax "subAppName::controlName" here, as used by msstyles.
           // See documentation for SetWindowTheme on MSDN.
           setWindowTheme(widget.substring(0, i));
           theme = openTheme(widget.substring(i + 2), dpi);
           setWindowTheme(null);
       } else {
           theme = openTheme(widget, dpi);
       }
       return theme;
    }

    // this should be called only with writeLock held
    private static Long getThemeImpl(String widget, int dpi) {
       return dpiAwareWidgetToTheme.computeIfAbsent(dpi, key -> new HashMap<>())
                .computeIfAbsent(widget, w -> openThemeImpl(widget, dpi));
    }

    // returns theme value
    // this method should be invoked with readLock locked
    private static Long getTheme(String widget, int dpi) {
        if (!isThemed) {
            throw new IllegalStateException("Themes are not loaded");
        }
        if (!valid) {
            readLock.unlock();
            writeLock.lock();
            try {
                if (!valid) {
                    // Close old themes.
                    for (Map<String, Long> dpiVal : dpiAwareWidgetToTheme.values()) {
                        for (Long value : dpiVal.values()) {
                            closeTheme(value);
                        }
                    }
                    dpiAwareWidgetToTheme.clear();
                    valid = true;
                }
            } finally {
                readLock.lock();
                writeLock.unlock();
            }
        }

        Long theme = null;

        Map<String, Long> widgetToTheme = dpiAwareWidgetToTheme.get(dpi);

        if (widgetToTheme != null) {
            theme = widgetToTheme.get(widget);
        }

        // mostly copied from the javadoc for ReentrantReadWriteLock
        if (theme == null) {
            readLock.unlock();
            writeLock.lock();
            try {
                theme = getThemeImpl(widget, dpi);
            } finally {
                readLock.lock();
                writeLock.unlock();// Unlock write, still hold read
            }
        }
        return theme;
    }

    private static native void paintBackground(int[] buffer, long theme,
                                               int part, int state,
                                               int rectRight, int rectBottom,
                                               int w, int h, int stride);

    public static void paintBackground(int[] buffer, String widget,
           int part, int state, int x, int y, int w, int h, int stride, int dpi) {
        readLock.lock();
        try {
            /* For widgets and parts in the lists, we get the part size
            for the current screen DPI to scale them better. */
            Dimension d = (partSizeWidgets.contains(widget)
                          && partSizeWidgetParts.contains(Integer.valueOf(part)))
                          ? getPartSize(getTheme(widget, dpi), part, state)
                          : new Dimension(w, h);

            paintBackground(buffer, getTheme(widget, dpi), part, state,
                            d.width, d.height, w, h, stride);
        } finally {
            readLock.unlock();
        }
    }

    private static native Insets getThemeMargins(long theme, int part,
                                                 int state, int marginType);

    public static Insets getThemeMargins(String widget, int part, int state, int marginType) {
        readLock.lock();
        try {
            return getThemeMargins(getTheme(widget, defaultDPI), part, state, marginType);
        } finally {
            readLock.unlock();
        }
    }

    private static native boolean isThemePartDefined(long theme, int part, int state);

    public static boolean isThemePartDefined(String widget, int part, int state) {
        readLock.lock();
        try {
            return isThemePartDefined(getTheme(widget, defaultDPI), part, state);
        } finally {
            readLock.unlock();
        }
    }

    private static native Color getColor(long theme, int part, int state,
                                         int property);

    public static Color getColor(String widget, int part, int state, int property) {
        readLock.lock();
        try {
            return getColor(getTheme(widget, defaultDPI), part, state, property);
        } finally {
            readLock.unlock();
        }
    }

    private static native int getInt(long theme, int part, int state,
                                     int property);

    public static int getInt(String widget, int part, int state, int property) {
        readLock.lock();
        try {
            return getInt(getTheme(widget, defaultDPI), part, state, property);
        } finally {
            readLock.unlock();
        }
    }

    private static native int getEnum(long theme, int part, int state,
                                      int property);

    public static int getEnum(String widget, int part, int state, int property) {
        readLock.lock();
        try {
            return getEnum(getTheme(widget, defaultDPI), part, state, property);
        } finally {
            readLock.unlock();
        }
    }

    private static native boolean getBoolean(long theme, int part, int state,
                                             int property);

    public static boolean getBoolean(String widget, int part, int state,
                                     int property) {
        readLock.lock();
        try {
            return getBoolean(getTheme(widget, defaultDPI), part, state, property);
        } finally {
            readLock.unlock();
        }
    }

    private static native boolean getSysBoolean(long theme, int property);

    public static boolean getSysBoolean(String widget, int property) {
        readLock.lock();
        try {
            return getSysBoolean(getTheme(widget, defaultDPI), property);
        } finally {
            readLock.unlock();
        }
    }

    private static native Point getPoint(long theme, int part, int state,
                                         int property);

    public static Point getPoint(String widget, int part, int state, int property) {
        readLock.lock();
        try {
            return getPoint(getTheme(widget, defaultDPI), part, state, property);
        } finally {
            readLock.unlock();
        }
    }

    private static native Dimension getPosition(long theme, int part, int state,
                                                int property);

    public static Dimension getPosition(String widget, int part, int state,
                                        int property) {
        readLock.lock();
        try {
            return getPosition(getTheme(widget, defaultDPI), part,state,property);
        } finally {
            readLock.unlock();
        }
    }

    private static native Dimension getPartSize(long theme, int part,
                                                int state);

    public static Dimension getPartSize(String widget, int part, int state) {
        readLock.lock();
        try {
            return getPartSize(getTheme(widget, defaultDPI), part, state);
        } finally {
            readLock.unlock();
        }
    }

    private static native long openTheme(String widget, int dpi);

    private static native void closeTheme(long theme);

    private static native void setWindowTheme(String subAppName);

    private static native long getThemeTransitionDuration(long theme, int part,
                                        int stateFrom, int stateTo, int propId);

    public static long getThemeTransitionDuration(String widget, int part,
                                       int stateFrom, int stateTo, int propId) {
        readLock.lock();
        try {
            return getThemeTransitionDuration(getTheme(widget, defaultDPI),
                                              part, stateFrom, stateTo,
                                              propId);
        } finally {
            readLock.unlock();
        }
    }

    private static native Insets getThemeBackgroundContentMargins(long theme,
                     int part, int state, int boundingWidth, int boundingHeight);

    public static Insets getThemeBackgroundContentMargins(String widget,
                    int part, int state, int boundingWidth, int boundingHeight) {
        readLock.lock();
        try {
            return getThemeBackgroundContentMargins(getTheme(widget, defaultDPI),
                                                    part, state,
                                                    boundingWidth, boundingHeight);
        } finally {
            readLock.unlock();
        }
    }
}
