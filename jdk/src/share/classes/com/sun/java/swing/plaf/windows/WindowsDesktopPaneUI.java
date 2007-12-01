/*
 * Copyright 1997-2003 Sun Microsystems, Inc.  All Rights Reserved.
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

package com.sun.java.swing.plaf.windows;

import javax.swing.*;
import javax.swing.plaf.basic.*;
import javax.swing.plaf.ComponentUI;
import java.awt.event.*;

/**
 * Windows desktop pane.
 * <p>
 * <strong>Warning:</strong>
 * Serialized objects of this class will not be compatible with
 * future Swing releases.  The current serialization support is appropriate
 * for short term storage or RMI between applications running the same
 * version of Swing.  A future release of Swing will provide support for
 * long term persistence.
 *
 * @author David Kloba
 */
public class WindowsDesktopPaneUI extends BasicDesktopPaneUI
{
    public static ComponentUI createUI(JComponent c) {
        return new WindowsDesktopPaneUI();
    }

    protected void installDesktopManager() {
        desktopManager = desktop.getDesktopManager();
        if(desktopManager == null) {
            desktopManager = new WindowsDesktopManager();
            desktop.setDesktopManager(desktopManager);
        }
    }

    protected void installDefaults() {
        super.installDefaults();
    }

    protected void installKeyboardActions() {
        super.installKeyboardActions();

        // Request focus if it isn't set.
        if(!desktop.requestDefaultFocus()) {
            desktop.requestFocus();
        }
    }
}
