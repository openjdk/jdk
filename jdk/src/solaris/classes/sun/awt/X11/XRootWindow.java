/*
 * Copyright 2003-2005 Sun Microsystems, Inc.  All Rights Reserved.
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

package sun.awt.X11;

/**
 * This class represents AWT application root window functionality.
 * Object of this class is singleton, all window reference it to have
 * common logical ancestor
 */
class XRootWindow extends XBaseWindow {
    private static XRootWindow xawtRootWindow = null;
    static XRootWindow getInstance() {
        XToolkit.awtLock();
        try {
            if (xawtRootWindow == null) {
                xawtRootWindow = new XRootWindow();
                xawtRootWindow.init(xawtRootWindow.getDelayedParams().delete(DELAYED));
            }
            return xawtRootWindow;
        } finally {
            XToolkit.awtUnlock();
        }
    }

    private XRootWindow() {
        super(new XCreateWindowParams(new Object[] {DELAYED, Boolean.TRUE}));
    }

    public void postInit(XCreateWindowParams params){
        super.postInit(params);
        setWMClass(getWMClass());
    }

    protected String getWMName() {
        return XToolkit.getAWTAppClassName();
    }
    protected String[] getWMClass() {
        return new String[] {XToolkit.getAWTAppClassName(), XToolkit.getAWTAppClassName()};
    }

  /* Fix 4976517.  Return awt_root_shell to XToolkit.c */
    private static long getXRootWindow() {
        return getXAWTRootWindow().getWindow();
    }
}
