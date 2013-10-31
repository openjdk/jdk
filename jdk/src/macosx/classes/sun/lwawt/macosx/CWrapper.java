/*
 * Copyright (c) 2011, 2013, Oracle and/or its affiliates. All rights reserved.
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

package sun.lwawt.macosx;

import java.awt.geom.Rectangle2D;

public final class CWrapper {
    private CWrapper() { }

    public static final class NSWindow {
        // NSWindowOrderingMode
        public static final int NSWindowAbove = 1;
        public static final int NSWindowBelow = -1;
        public static final int NSWindowOut = 0;

        // Window level constants
        // The number of supported levels: (we'll use more in the future)
        public static final int MAX_WINDOW_LEVELS = 2;
        // The levels: (these are NOT real constants, these are keys. See native code.)
        public static final int NSNormalWindowLevel = 0;
        public static final int NSFloatingWindowLevel = 1;

        // 'level' is one of the keys defined above
        public static native void setLevel(long window, int level);

        public static native void makeKeyAndOrderFront(long window);
        public static native void makeKeyWindow(long window);
        public static native void makeMainWindow(long window);
        public static native boolean canBecomeMainWindow(long window);
        public static native boolean isKeyWindow(long window);

        public static native void orderFront(long window);
        public static native void orderFrontRegardless(long window);
        public static native void orderWindow(long window, int ordered, long relativeTo);
        public static native void orderOut(long window);

        public static native void addChildWindow(long parent, long child, int ordered);
        public static native void removeChildWindow(long parent, long child);

        public static native void setFrame(long window, int x, int y, int w, int h, boolean display);

        public static native void setAlphaValue(long window, float alpha);
        public static native void setOpaque(long window, boolean opaque);
        public static native void setBackgroundColor(long window, long color);

        public static native void miniaturize(long window);
        public static native void deminiaturize(long window);
        public static native boolean isZoomed(long window);
        public static native void zoom(long window);

        public static native void makeFirstResponder(long window, long responder);
    }

    public static final class NSView {
        public static native void addSubview(long view, long subview);
        public static native void removeFromSuperview(long view);

        public static native void setFrame(long view, int x, int y, int w, int h);
        public static native Rectangle2D frame(long view);
        public static native long window(long view);

        public static native void setHidden(long view, boolean hidden);

        public static native void setToolTip(long view, String msg);
    }

    public static final class NSObject {
        public static native void release(long object);
    }

    public static final class NSColor {
        public static native long clearColor();
    }
}
