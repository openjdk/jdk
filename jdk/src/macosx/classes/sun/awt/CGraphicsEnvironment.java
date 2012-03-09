/*
 * Copyright (c) 2011, Oracle and/or its affiliates. All rights reserved.
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

import java.awt.*;
import java.awt.print.*;
import java.util.*;

import sun.java2d.*;

/**
 * This is an implementation of a GraphicsEnvironment object for the default local GraphicsEnvironment used by the Java
 * Runtime Environment for Mac OS X GUI environments.
 *
 * @see GraphicsDevice
 * @see GraphicsConfiguration
 */
public class CGraphicsEnvironment extends SunGraphicsEnvironment {
    // Global initialization of the Cocoa runtime.
    private static native void initCocoa();

    /**
     * Fetch an array of all valid CoreGraphics display identifiers.
     */
    private static native int[] getDisplayIDs();

    /**
     * Fetch the CoreGraphics display ID for the 'main' display.
     */
    private static native int getMainDisplayID();

    /**
     * Noop function that just acts as an entry point for someone to force a static initialization of this class.
     */
    public static void init() { }

    static {
        java.security.AccessController.doPrivileged(new sun.security.action.LoadLibraryAction("awt"));
        java.security.AccessController.doPrivileged(new java.security.PrivilegedAction<Object>() {
            public Object run() {
                if (isHeadless()) return null;
                initCocoa();
                return null;
            }
        });

        // Install the correct surface manager factory.
        SurfaceManagerFactory.setInstance(new MacosxSurfaceManagerFactory());
    }

    /**
     * Register the instance with CGDisplayRegisterReconfigurationCallback()
     * The registration uses a weak global reference -- if our instance is garbage collected, the reference will be dropped.
     *
     * @return Return the registration context (a pointer).
     */
    private native long registerDisplayReconfiguration();

    /**
     * Remove the instance's registration with CGDisplayRemoveReconfigurationCallback()
     */
    private native void deregisterDisplayReconfiguration(long context);

    /** Available CoreGraphics displays. */
    private final Map<Integer, CGraphicsDevice> devices = new HashMap<Integer, CGraphicsDevice>();

    /** Reference to the display reconfiguration callback context. */
    private final long displayReconfigContext;

    /**
     * Construct a new instance.
     */
    public CGraphicsEnvironment() {
        if (isHeadless()) {
            displayReconfigContext = 0L;
            return;
        }

        /* Populate the device table */
        initDevices();

        /* Register our display reconfiguration listener */
        displayReconfigContext = registerDisplayReconfiguration();
        if (displayReconfigContext == 0L) {
            throw new RuntimeException("Could not register CoreGraphics display reconfiguration callback");
        }
    }

    /**
     * Called by the CoreGraphics Display Reconfiguration Callback.
     *
     * @param displayId
     *            CoreGraphics displayId
     */
    void _displayReconfiguration(long displayId) {
        displayChanged();
    }

    @Override
    protected void finalize() throws Throwable {
        try {
            super.finalize();
        } finally {
            deregisterDisplayReconfiguration(displayReconfigContext);
        }
    }

    /**
     * (Re)create all CGraphicsDevices
     *
     * @return
     */
    private synchronized void initDevices() {
        devices.clear();

        int mainID = getMainDisplayID();

        // initialization of the graphics device may change
        // list of displays on hybrid systems via an activation
        // of discrete video.
        // So, we initialize the main display first, and then
        // retrieve actual list of displays.
        CGraphicsDevice mainDevice = new CGraphicsDevice(mainID);

        final int[] displayIDs = getDisplayIDs();

        for (int displayID : displayIDs) {
            if (displayID != mainID) {
                devices.put(displayID, new CGraphicsDevice(displayID));
            } else {
                devices.put(mainID, mainDevice);
            }
        }
    }

    @Override
    public synchronized GraphicsDevice getDefaultScreenDevice() throws HeadlessException {
        final int mainDisplayID = getMainDisplayID();
        CGraphicsDevice d = devices.get(mainDisplayID);
        if (d == null) {
            // we do not exepct that this may happen, the only responce
            // is to re-initialize the list of devices
            initDevices();

            d = devices.get(mainDisplayID);
        }
        return d;
    }

    @Override
    public synchronized GraphicsDevice[] getScreenDevices() throws HeadlessException {
        return devices.values().toArray(new CGraphicsDevice[devices.values().size()]);
    }

    @Override
    protected synchronized int getNumScreens() {
        return devices.size();
    }

    @Override
    protected GraphicsDevice makeScreenDevice(int screennum) {
        throw new UnsupportedOperationException("This method is unused and should not be called in this implementation");
    }

    @Override
    public boolean isDisplayLocal() {
       return true;
    }

    private Font[] allFontsWithLogical;
    static String[] sLogicalFonts = { "Serif", "SansSerif", "Monospaced", "Dialog", "DialogInput" };

    @Override
    public Font[] getAllFonts() {
        if (allFontsWithLogical == null)
        {
            Font[] newFonts;
            Font[] superFonts = super.getAllFonts();

            int numLogical = sLogicalFonts.length;
            int numOtherFonts = superFonts.length;

            newFonts = new Font[numOtherFonts + numLogical];
            System.arraycopy(superFonts,0,newFonts,numLogical,numOtherFonts);

            for (int i = 0; i < numLogical; i++)
            {
                newFonts[i] = new Font(sLogicalFonts[i], Font.PLAIN, 1);
            }
            allFontsWithLogical = newFonts;
        }
        return java.util.Arrays.copyOf(allFontsWithLogical, allFontsWithLogical.length);
    }

}
