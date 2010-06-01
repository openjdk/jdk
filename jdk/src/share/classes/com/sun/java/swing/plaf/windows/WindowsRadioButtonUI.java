/*
 * Copyright (c) 1997, 2003, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.java.swing.plaf.windows;

import sun.awt.AppContext;

import javax.swing.plaf.basic.*;
import javax.swing.*;
import javax.swing.plaf.*;

import java.awt.*;


/**
 * Windows rendition of the component.
 * <p>
 * <strong>Warning:</strong>
 * Serialized objects of this class will not be compatible with
 * future Swing releases.  The current serialization support is appropriate
 * for short term storage or RMI between applications running the same
 * version of Swing.  A future release of Swing will provide support for
 * long term persistence.
 */
public class WindowsRadioButtonUI extends BasicRadioButtonUI
{
    private static final Object WINDOWS_RADIO_BUTTON_UI_KEY = new Object();

    protected int dashedRectGapX;
    protected int dashedRectGapY;
    protected int dashedRectGapWidth;
    protected int dashedRectGapHeight;

    protected Color focusColor;

    private boolean initialized = false;

    // ********************************
    //          Create PLAF
    // ********************************
    public static ComponentUI createUI(JComponent c) {
        AppContext appContext = AppContext.getAppContext();
        WindowsRadioButtonUI windowsRadioButtonUI =
                (WindowsRadioButtonUI) appContext.get(WINDOWS_RADIO_BUTTON_UI_KEY);
        if (windowsRadioButtonUI == null) {
            windowsRadioButtonUI = new WindowsRadioButtonUI();
            appContext.put(WINDOWS_RADIO_BUTTON_UI_KEY, windowsRadioButtonUI);
        }
        return windowsRadioButtonUI;
    }

    // ********************************
    //           Defaults
    // ********************************
    public void installDefaults(AbstractButton b) {
        super.installDefaults(b);
        if(!initialized) {
            dashedRectGapX = ((Integer)UIManager.get("Button.dashedRectGapX")).intValue();
            dashedRectGapY = ((Integer)UIManager.get("Button.dashedRectGapY")).intValue();
            dashedRectGapWidth = ((Integer)UIManager.get("Button.dashedRectGapWidth")).intValue();
            dashedRectGapHeight = ((Integer)UIManager.get("Button.dashedRectGapHeight")).intValue();
            focusColor = UIManager.getColor(getPropertyPrefix() + "focus");
            initialized = true;
        }
        if (XPStyle.getXP() != null) {
            LookAndFeel.installProperty(b, "rolloverEnabled", Boolean.TRUE);
        }
    }

    protected Color getFocusColor() {
        return focusColor;
    }

    // ********************************
    //          Paint Methods
    // ********************************

    /**
     * Overridden method to render the text without the mnemonic
     */
    protected void paintText(Graphics g, AbstractButton b, Rectangle textRect, String text) {
        WindowsGraphicsUtils.paintText(g, b, textRect, text, getTextShiftOffset());
    }


    protected void paintFocus(Graphics g, Rectangle textRect, Dimension d){
        g.setColor(getFocusColor());
        BasicGraphicsUtils.drawDashedRect(g, textRect.x, textRect.y, textRect.width, textRect.height);
    }

    // ********************************
    //          Layout Methods
    // ********************************
    public Dimension getPreferredSize(JComponent c) {
        Dimension d = super.getPreferredSize(c);

        /* Ensure that the width and height of the button is odd,
         * to allow for the focus line if focus is painted
         */
        AbstractButton b = (AbstractButton)c;
        if (d != null && b.isFocusPainted()) {
            if(d.width % 2 == 0) { d.width += 1; }
            if(d.height % 2 == 0) { d.height += 1; }
        }
        return d;
    }

}
