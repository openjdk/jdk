/*
 * Copyright (c) 1997, 1998, Oracle and/or its affiliates. All rights reserved.
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

package javax.swing.plaf;

import java.awt.event.KeyEvent;
import java.awt.FontMetrics;

import javax.swing.AbstractButton;
import javax.swing.JComponent;
import javax.swing.JToolTip;
import javax.swing.KeyStroke;
import javax.swing.UIManager;

import sun.swing.SwingUtilities2;

/**
 * Pluggable look and feel interface for JToolTip.
 *
 * @author Dave Moore
 */
public abstract class ToolTipUI extends ComponentUI {

    /**
     * Delimiter for Accelerator String.
     */
    private String acceleratorDelimiter;

    /**
     * The space between strings.
     */
    public static final int padSpaceBetweenStrings = 12;

    /**
     * Constructor for subclasses to call.
     */
    protected ToolTipUI() {}

    /**
     * If the accelerator is hidden, the method returns {@code true},
     * otherwise, returns {@code false}.
     *
     * @return {@code true} if the accelerator is hidden.
     */
    protected boolean isAcceleratorHidden() {
        Boolean b = (Boolean)UIManager.get("ToolTip.hideAccelerator");
        return b != null && b.booleanValue();
    }

    /**
     * Returns the accelerator string.
     *
     * @param tip ToolTip.
     * @return the accelerator string.
     */

    public String getAcceleratorString(JToolTip tip) {

        if (tip == null || isAcceleratorHidden()) {
            return "";
        }
        JComponent comp = tip.getComponent();
        if (!(comp instanceof AbstractButton)) {
            return "";
        }

        KeyStroke[] keys = comp.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).keys();
        if (keys == null) {
            return "";
        }

        String controlKeyStr = "";

        acceleratorDelimiter = UIManager.getString( "MenuItem.acceleratorDelimiter" );
        if ( acceleratorDelimiter == null ) { acceleratorDelimiter = "-"; }

        for (int i = 0; i < keys.length; i++) {
            int mod = keys[i].getModifiers();
            controlKeyStr = KeyEvent.getModifiersExText(mod) +
                    acceleratorDelimiter +
                    KeyEvent.getKeyText(keys[i].getKeyCode());
            break;
        }

        return controlKeyStr;
    }

    /**
     * Calculates the Accelerator Spacing Value.
     * @param c JComponent
     * @param fm FontMetrics
     * @param accel String
     * @return Accelerator Spacing.
     */
    protected int calcAcceleratorSpacing(JComponent c, FontMetrics fm, String accel) {
        return accel.isEmpty()
                ? 0
                : padSpaceBetweenStrings +
                SwingUtilities2.stringWidth(c, fm, accel);
    }
}
