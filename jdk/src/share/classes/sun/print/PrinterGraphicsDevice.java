/*
 * Copyright 2004 Sun Microsystems, Inc.  All Rights Reserved.
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


package sun.print;

import java.awt.GraphicsConfiguration;
import java.awt.GraphicsDevice;
import java.awt.Window;

public final class PrinterGraphicsDevice extends GraphicsDevice {

    String printerID;
    GraphicsConfiguration graphicsConf;

    protected PrinterGraphicsDevice(GraphicsConfiguration conf, String id) {
        printerID = id;
        graphicsConf = conf;
    }

    public int getType() {
        return TYPE_PRINTER;
    }

    public String getIDstring() {
        return printerID;
    }

    public GraphicsConfiguration[] getConfigurations() {
        GraphicsConfiguration[] confs = new GraphicsConfiguration[1];
        confs[0] = graphicsConf;
        return confs;
    }

    public GraphicsConfiguration getDefaultConfiguration() {
        return graphicsConf;
    }

    public void setFullScreenWindow(Window w) {
        // Do nothing
    }

    public Window getFullScreenWindow() {
        return null;
    }
}
