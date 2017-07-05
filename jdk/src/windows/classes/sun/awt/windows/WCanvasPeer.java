/*
 * Copyright 1996-2009 Sun Microsystems, Inc.  All Rights Reserved.
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
package sun.awt.windows;

import java.awt.*;
import java.awt.peer.*;
import java.lang.ref.WeakReference;
import java.lang.reflect.Method;
import sun.awt.SunToolkit;
import sun.awt.Win32GraphicsDevice;
import sun.awt.PaintEventDispatcher;

class WCanvasPeer extends WComponentPeer implements CanvasPeer {

    private boolean eraseBackground;

    // Toolkit & peer internals

    WCanvasPeer(Component target) {
        super(target);
    }

    native void create(WComponentPeer parent);

    void initialize() {
        eraseBackground = !SunToolkit.getSunAwtNoerasebackground();
        boolean eraseBackgroundOnResize = SunToolkit.getSunAwtErasebackgroundonresize();
        // Optimization: the default value in the native code is true, so we
        // call setNativeBackgroundErase only when the value changes to false
        if (!PaintEventDispatcher.getPaintEventDispatcher().
                shouldDoNativeBackgroundErase((Component)target)) {
            eraseBackground = false;
        }
        setNativeBackgroundErase(eraseBackground, eraseBackgroundOnResize);
        super.initialize();
        Color bg = ((Component)target).getBackground();
        if (bg != null) {
            setBackground(bg);
        }
    }

    public void paint(Graphics g) {
        Dimension d = ((Component)target).getSize();
        if (g instanceof Graphics2D ||
            g instanceof sun.awt.Graphics2Delegate) {
            // background color is setup correctly, so just use clearRect
            g.clearRect(0, 0, d.width, d.height);
        } else {
            // emulate clearRect
            g.setColor(((Component)target).getBackground());
            g.fillRect(0, 0, d.width, d.height);
            g.setColor(((Component)target).getForeground());
        }
        super.paint(g);
    }

    public boolean shouldClearRectBeforePaint() {
        return eraseBackground;
    }

    /*
     * Disables background erasing for this canvas, both for resizing
     * and not-resizing repaints.
     */
    void disableBackgroundErase() {
        eraseBackground = false;
        setNativeBackgroundErase(false, false);
    }

    /*
     * Sets background erasing flags at the native level. If {@code
     * doErase} is set to {@code true}, canvas background is erased on
     * every repaint. If {@code doErase} is {@code false} and {@code
     * doEraseOnResize} is {@code true}, then background is only erased
     * on resizing repaints. If both {@code doErase} and {@code
     * doEraseOnResize} are false, then background is never erased.
     */
    private native void setNativeBackgroundErase(boolean doErase,
                                                 boolean doEraseOnResize);

    public GraphicsConfiguration getAppropriateGraphicsConfiguration(
            GraphicsConfiguration gc)
    {
        return gc;
    }
}
