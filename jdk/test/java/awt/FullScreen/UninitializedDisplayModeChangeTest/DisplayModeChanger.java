/*
 * Copyright 2006-2008 Sun Microsystems, Inc.  All Rights Reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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

import java.awt.DisplayMode;
import java.awt.EventQueue;
import java.awt.Frame;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.lang.reflect.InvocationTargetException;

/**
 * Used by the UninitializedDisplayModeChangeTest to change the
 * display mode.
 */
public class DisplayModeChanger {

    public static void main(String[] args)
        throws InterruptedException, InvocationTargetException
    {
        final GraphicsDevice gd =
            GraphicsEnvironment.getLocalGraphicsEnvironment().
                getDefaultScreenDevice();

        EventQueue.invokeAndWait(new Runnable() {
            public void run() {
                Frame f = null;
                if (gd.isFullScreenSupported()) {
                    try {
                        f = new Frame("DisplayChanger Frame");
                        gd.setFullScreenWindow(f);
                        if (gd.isDisplayChangeSupported()) {
                            DisplayMode dm = findDisplayMode(gd);
                            if (gd != null) {
                                gd.setDisplayMode(dm);
                            }
                        }
                        try {
                            Thread.sleep(1000);
                        } catch (InterruptedException ex) {
                            ex.printStackTrace();
                        }
                        gd.setFullScreenWindow(null);
                    } finally {
                        if (f != null) {
                            f.dispose();
                        }
                    }
                }
            }
        });
    }

    /**
     * Finds a display mode that is different from the current display
     * mode and is likely to cause a display change event.
     */
    private static DisplayMode findDisplayMode(GraphicsDevice gd) {
        DisplayMode dms[] = gd.getDisplayModes();
        DisplayMode currentDM = gd.getDisplayMode();
        for (DisplayMode dm : dms) {
            if (!dm.equals(currentDM) &&
                 dm.getRefreshRate() == currentDM.getRefreshRate())
            {
                // different from the current dm and refresh rate is the same
                // means that something else is different => more likely to
                // cause a DM change event
                return dm;
            }
        }
        return null;
    }

}
