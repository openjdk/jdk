/*
 * Copyright (c) 2002, 2013, Oracle and/or its affiliates. All rights reserved.
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
package sun.awt.X11;

import java.awt.*;
import java.awt.peer.*;

import sun.awt.SunToolkit;

import sun.awt.X11GraphicsConfig;
import sun.awt.X11GraphicsDevice;

class XCanvasPeer extends XComponentPeer implements CanvasPeer {

    private boolean eraseBackgroundDisabled;

    XCanvasPeer() {}

    XCanvasPeer(XCreateWindowParams params) {
        super(params);
    }

    XCanvasPeer(Component target) {
        super(target);
    }

    @Override
    void preInit(XCreateWindowParams params) {
        super.preInit(params);
        if (SunToolkit.getSunAwtNoerasebackground()) {
            disableBackgroundErase();
        }
    }

    /* Get a GraphicsConfig with the same visual on the new
     * screen, which should be easy in Xinerama mode.
     */
    @Override
    public GraphicsConfiguration getAppropriateGraphicsConfiguration(
                                    GraphicsConfiguration gc)
    {
        if (graphicsConfig == null || gc == null) {
            return gc;
        }

        final X11GraphicsDevice newDev = getSameScreenDevice(gc);
        final int visualToLookFor = graphicsConfig.getVisual();

        final GraphicsConfiguration[] configurations = newDev.getConfigurations();
        for (final GraphicsConfiguration config : configurations) {
            final X11GraphicsConfig x11gc = (X11GraphicsConfig) config;
            if (visualToLookFor == x11gc.getVisual()) {
                graphicsConfig = x11gc;
            }
        }

        return graphicsConfig;
    }

    private X11GraphicsDevice getSameScreenDevice(GraphicsConfiguration gc) {
        XToolkit.awtLock(); // so that the number of screens doesn't change during
        try {
            final int screenNum = ((X11GraphicsDevice) gc.getDevice()).getScreen();
            return (X11GraphicsDevice) GraphicsEnvironment.
                    getLocalGraphicsEnvironment().
                    getScreenDevices()[screenNum];
        } finally {
            XToolkit.awtUnlock();
        }
    }

    protected boolean shouldFocusOnClick() {
        // Canvas should always be able to be focused by mouse clicks.
        return true;
    }

    public void disableBackgroundErase() {
        eraseBackgroundDisabled = true;
    }
    @Override
    protected boolean doEraseBackground() {
        return !eraseBackgroundDisabled;
    }
}
