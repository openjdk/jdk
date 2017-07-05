/*
 * Copyright 1995-2005 Sun Microsystems, Inc.  All Rights Reserved.
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
package sun.awt.motif;

import java.awt.*;
import java.awt.peer.*;
import sun.awt.DisplayChangedListener;
import sun.awt.X11GraphicsConfig;
import sun.awt.X11GraphicsDevice;
import sun.awt.X11GraphicsEnvironment;

class MCanvasPeer extends MComponentPeer implements CanvasPeer,
 DisplayChangedListener {

    native void create(MComponentPeer parent);
    private static native void initIDs();
    static {
        initIDs();
    }

    MCanvasPeer() {}

    MCanvasPeer(Component target) {
        super(target);
    }

    MCanvasPeer(Component target, Object arg) {
        super(target, arg);
    }

/* --- DisplayChangedListener Stuff --- */
    public void displayChanged() {}
    public void paletteChanged() {}
    native void resetTargetGC(Component target);

    /*
     * Called when the Window this
     * Canvas is on is moved onto another Xinerama screen.
     *
     * Canvases can be created with a non-defulat GraphicsConfiguration.  The
     * GraphicsConfiguration needs to be changed to one on the new screen,
     * preferably with the same visual ID.
     *
     * Up-called for other windows peer instances (WPanelPeer, WWindowPeer).
     *
     * Should only be called from the event thread.
     */
     public void displayChanged(int screenNum) {
        resetLocalGC(screenNum);
        resetTargetGC(target);  /* call Canvas.setGCFromPeer() via native */
    }

    /* Set graphicsConfig to a GraphicsConfig with the same visual on the new
     * screen, which should be easy in Xinerama mode.
     *
     * Should only be called from displayChanged(), and therefore only from
     * the event thread.
     */
    void resetLocalGC(int screenNum) {
        // Opt: Only need to do if we're not using the default GC
        if (graphicsConfig != null) {
            X11GraphicsConfig parentgc;
            // save vis id of current gc
            int visual = graphicsConfig.getVisual();

            X11GraphicsDevice newDev = (X11GraphicsDevice) GraphicsEnvironment.
                                                 getLocalGraphicsEnvironment().
                                                 getScreenDevices()[screenNum];

            for (int i = 0; i < newDev.getNumConfigs(screenNum); i++) {
                if (visual == newDev.getConfigVisualId(i, screenNum)) {
                    // use that
                    graphicsConfig = (X11GraphicsConfig)newDev.getConfigurations()[i];
                    break;
                }
            }
            // just in case...
            if (graphicsConfig == null) {
                graphicsConfig = (X11GraphicsConfig) GraphicsEnvironment.
                                           getLocalGraphicsEnvironment().
                                           getScreenDevices()[screenNum].
                                           getDefaultConfiguration();
            }
        }
    }

    protected boolean shouldFocusOnClick() {
        // Canvas should always be able to be focused by mouse clicks.
        return true;
    }
}
