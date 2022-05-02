/*
 * Copyright (c) 1998, 2021, Oracle and/or its affiliates. All rights reserved.
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

package javax.swing.plaf.metal;

import sun.swing.SwingUtilities2;
import java.awt.*;
import java.awt.event.*;
import javax.swing.*;
import javax.swing.plaf.*;
import javax.swing.plaf.basic.BasicToolTipUI;
import javax.swing.plaf.basic.BasicHTML;
import javax.swing.text.View;


/**
 * A Metal L&amp;F extension of BasicToolTipUI.
 * <p>
 * <strong>Warning:</strong>
 * Serialized objects of this class will not be compatible with
 * future Swing releases. The current serialization support is
 * appropriate for short term storage or RMI between applications running
 * the same version of Swing.  As of 1.4, support for long term storage
 * of all JavaBeans
 * has been added to the <code>java.beans</code> package.
 * Please see {@link java.beans.XMLEncoder}.
 *
 * @author Steve Wilson
 */
@SuppressWarnings("serial") // Same-version serialization only
public class MetalToolTipUI extends BasicToolTipUI {

    static MetalToolTipUI sharedInstance = new MetalToolTipUI();
    private Font smallFont;
    // Refer to note in getAcceleratorString about this field.
    private JToolTip tip;

    /**
     * The space between strings.
     */
    public static final int padSpaceBetweenStrings = 12;

    /**
     * Constructs an instance of the {@code MetalToolTipUI}.
     */
    public MetalToolTipUI() {
        super();
    }

    /**
     * Returns an instance of the {@code MetalToolTipUI}.
     *
     * @param c a component
     * @return an instance of the {@code MetalToolTipUI}.
     */
    public static ComponentUI createUI(JComponent c) {
        return sharedInstance;
    }

    public void installUI(JComponent c) {
        super.installUI(c);
        tip = (JToolTip)c;
        Font f = c.getFont();
        smallFont = new Font( f.getName(), f.getStyle(), f.getSize() - 2 );
    }

    public void uninstallUI(JComponent c) {
        super.uninstallUI(c);
        tip = null;
    }

    public void paint(Graphics g, JComponent c) {
        JToolTip tip = (JToolTip)c;
        Font font = c.getFont();
        FontMetrics metrics = SwingUtilities2.getFontMetrics(c, g, font);
        Dimension size = c.getSize();
        int accelBL;

        g.setColor(c.getForeground());
        // fix for bug 4153892
        String tipText = tip.getTipText();
        if (tipText == null) {
            tipText = "";
        }

        String accelString = getAcceleratorString(tip);
        FontMetrics accelMetrics = SwingUtilities2.getFontMetrics(c, g, smallFont);
        int accelSpacing = calcAcceleratorSpacing(c, accelMetrics, accelString);

        Insets insets = tip.getInsets();
        Rectangle paintTextR = new Rectangle(
            insets.left + 3,
            insets.top,
            size.width - (insets.left + insets.right) - 6 - accelSpacing,
            size.height - (insets.top + insets.bottom));

        if (paintTextR.width <= 0 || paintTextR.height <= 0) {
            return;
        }

        View v = (View) c.getClientProperty(BasicHTML.propertyKey);
        if (v != null) {
            v.paint(g, paintTextR);
            accelBL = BasicHTML.getHTMLBaseline(v, paintTextR.width,
                                                  paintTextR.height);
        } else {
            g.setFont(font);
            SwingUtilities2.drawString(tip, g, tipText, paintTextR.x,
                                  paintTextR.y + metrics.getAscent());
            accelBL = metrics.getAscent();
        }

        if (!accelString.isEmpty()) {
            g.setFont(smallFont);
            g.setColor( MetalLookAndFeel.getPrimaryControlDarkShadow() );
            SwingUtilities2.drawString(tip, g, accelString,
                                       tip.getWidth() - 1 - insets.right
                                           - accelSpacing
                                           + padSpaceBetweenStrings
                                           - 3,
                                       paintTextR.y + accelBL);
        }
    }

    public Dimension getPreferredSize(JComponent c) {
        Dimension d = super.getPreferredSize(c);
        return d;
    }
}
