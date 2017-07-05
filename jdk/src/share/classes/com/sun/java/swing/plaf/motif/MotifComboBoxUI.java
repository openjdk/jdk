/*
 * Copyright (c) 1997, 2006, Oracle and/or its affiliates. All rights reserved.
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
package com.sun.java.swing.plaf.motif;

import java.awt.*;
import javax.swing.*;
import javax.swing.plaf.*;
import javax.swing.border.*;
import javax.swing.plaf.basic.*;
import java.io.Serializable;
import java.awt.event.*;
import java.beans.*;

/**
 * ComboBox motif look and feel
 * <p> * <strong>Warning:</strong>
 * Serialized objects of this class will not be compatible with
 * future Swing releases.  The current serialization support is appropriate
 * for short term storage or RMI between applications running the same
 * version of Swing.  A future release of Swing will provide support for
 * long term persistence.
 *
 * @author Arnaud Weber
 */
public class MotifComboBoxUI extends BasicComboBoxUI implements Serializable {
    Icon arrowIcon;
    static final int HORIZ_MARGIN = 3;

    public static ComponentUI createUI(JComponent c) {
        return new MotifComboBoxUI();
    }

    public void installUI(JComponent c) {
        super.installUI(c);
        arrowIcon = new MotifComboBoxArrowIcon(UIManager.getColor("controlHighlight"),
                                               UIManager.getColor("controlShadow"),
                                               UIManager.getColor("control"));

        Runnable initCode = new Runnable() {
            public void run(){
                if ( motifGetEditor() != null ) {
                    motifGetEditor().setBackground( UIManager.getColor( "text" ) );
                }
            }
        };

        SwingUtilities.invokeLater( initCode );
    }

    public Dimension getMinimumSize( JComponent c ) {
        if ( !isMinimumSizeDirty ) {
            return new Dimension( cachedMinimumSize );
        }
        Dimension size;
        Insets insets = getInsets();
        size = getDisplaySize();
        size.height += insets.top + insets.bottom;
        int buttonSize = iconAreaWidth();
        size.width +=  insets.left + insets.right + buttonSize;

        cachedMinimumSize.setSize( size.width, size.height );
        isMinimumSizeDirty = false;

        return size;
    }

    protected ComboPopup createPopup() {
        return new MotifComboPopup( comboBox );
    }

    /**
     * Overriden to empty the MouseMotionListener.
     */
    protected class MotifComboPopup extends BasicComboPopup {

        public MotifComboPopup( JComboBox comboBox ) {
            super( comboBox );
        }

        /**
         * Motif combo popup should not track the mouse in the list.
         */
        public MouseMotionListener createListMouseMotionListener() {
           return new MouseMotionAdapter() {};
        }

        public KeyListener createKeyListener() {
            return super.createKeyListener();
        }

        protected class InvocationKeyHandler extends BasicComboPopup.InvocationKeyHandler {
            protected InvocationKeyHandler() {
                MotifComboPopup.this.super();
            }
        }
    }

    protected void installComponents() {
        if ( comboBox.isEditable() ) {
            addEditor();
        }

        comboBox.add( currentValuePane );
    }

    protected void uninstallComponents() {
        removeEditor();
        comboBox.removeAll();
    }

    public void paint(Graphics g, JComponent c) {
        boolean hasFocus = comboBox.hasFocus();
        Rectangle r;

        if (comboBox.isEnabled()) {
            g.setColor(comboBox.getBackground());
        } else {
            g.setColor(UIManager.getColor("ComboBox.disabledBackground"));
        }
        g.fillRect(0,0,c.getWidth(),c.getHeight());

        if ( !comboBox.isEditable() ) {
            r = rectangleForCurrentValue();
            paintCurrentValue(g,r,hasFocus);
        }
        r = rectangleForArrowIcon();
        arrowIcon.paintIcon(c,g,r.x,r.y);
        if ( !comboBox.isEditable() ) {
            Border border = comboBox.getBorder();
            Insets in;
            if ( border != null ) {
                in = border.getBorderInsets(comboBox);
            }
            else {
                in = new Insets( 0, 0, 0, 0 );
            }
            // Draw the separation
            if(MotifGraphicsUtils.isLeftToRight(comboBox)) {
                r.x -= (HORIZ_MARGIN + 2);
            }
            else {
                r.x += r.width + HORIZ_MARGIN + 1;
            }
            r.y = in.top;
            r.width = 1;
            r.height = comboBox.getBounds().height - in.bottom - in.top;
            g.setColor(UIManager.getColor("controlShadow"));
            g.fillRect(r.x,r.y,r.width,r.height);
            r.x++;
            g.setColor(UIManager.getColor("controlHighlight"));
            g.fillRect(r.x,r.y,r.width,r.height);
        }
    }

    public void paintCurrentValue(Graphics g,Rectangle bounds,boolean hasFocus) {
        ListCellRenderer renderer = comboBox.getRenderer();
        Component c;
        Dimension d;
        c = renderer.getListCellRendererComponent(listBox, comboBox.getSelectedItem(), -1, false, false);
        c.setFont(comboBox.getFont());
        if ( comboBox.isEnabled() ) {
            c.setForeground(comboBox.getForeground());
            c.setBackground(comboBox.getBackground());
        }
        else {
            c.setForeground(UIManager.getColor("ComboBox.disabledForeground"));
            c.setBackground(UIManager.getColor("ComboBox.disabledBackground"));
        }
        d  = c.getPreferredSize();
        currentValuePane.paintComponent(g,c,comboBox,bounds.x,bounds.y,
                                        bounds.width,d.height);
    }

    protected Rectangle rectangleForArrowIcon() {
        Rectangle b = comboBox.getBounds();
        Border border = comboBox.getBorder();
        Insets in;
        if ( border != null ) {
            in = border.getBorderInsets(comboBox);
        }
        else {
            in = new Insets( 0, 0, 0, 0 );
        }
        b.x = in.left;
        b.y = in.top;
        b.width -= (in.left + in.right);
        b.height -= (in.top + in.bottom);

        if(MotifGraphicsUtils.isLeftToRight(comboBox)) {
            b.x = b.x + b.width - HORIZ_MARGIN - arrowIcon.getIconWidth();
        }
        else {
            b.x += HORIZ_MARGIN;
        }
        b.y = b.y + (b.height - arrowIcon.getIconHeight()) / 2;
        b.width = arrowIcon.getIconWidth();
        b.height = arrowIcon.getIconHeight();
        return b;
    }

    protected Rectangle rectangleForCurrentValue() {
        int width = comboBox.getWidth();
        int height = comboBox.getHeight();
        Insets insets = getInsets();
        if(MotifGraphicsUtils.isLeftToRight(comboBox)) {
            return new Rectangle(insets.left, insets.top,
                                 (width - (insets.left + insets.right)) -
                                                        iconAreaWidth(),
                                 height - (insets.top + insets.bottom));
        }
        else {
            return new Rectangle(insets.left + iconAreaWidth(), insets.top,
                                 (width - (insets.left + insets.right)) -
                                                        iconAreaWidth(),
                                 height - (insets.top + insets.bottom));
        }
    }

    public int iconAreaWidth() {
        if ( comboBox.isEditable() )
            return arrowIcon.getIconWidth() + (2 * HORIZ_MARGIN);
        else
            return arrowIcon.getIconWidth() + (3 * HORIZ_MARGIN) + 2;
    }

    public void configureEditor() {
        super.configureEditor();
        editor.setBackground( UIManager.getColor( "text" ) );
    }

    protected LayoutManager createLayoutManager() {
        return new ComboBoxLayoutManager();
    }

    private Component motifGetEditor() {
        return editor;
    }

    /**
     * This inner class is marked &quot;public&quot; due to a compiler bug.
     * This class should be treated as a &quot;protected&quot; inner class.
     * Instantiate it only within subclasses of <FooUI>.
     */
    public class ComboBoxLayoutManager extends BasicComboBoxUI.ComboBoxLayoutManager {
        public ComboBoxLayoutManager() {
            MotifComboBoxUI.this.super();
        }
        public void layoutContainer(Container parent) {
            if ( motifGetEditor() != null ) {
                Rectangle cvb = rectangleForCurrentValue();
                cvb.x += 1;
                cvb.y += 1;
                cvb.width -= 1;
                cvb.height -= 2;
                motifGetEditor().setBounds(cvb);
            }
        }
    }

    static class MotifComboBoxArrowIcon implements Icon, Serializable {
        private Color lightShadow;
        private Color darkShadow;
        private Color fill;

        public MotifComboBoxArrowIcon(Color lightShadow, Color darkShadow, Color fill) {
            this.lightShadow = lightShadow;
            this.darkShadow = darkShadow;
            this.fill = fill;
        }


        public void paintIcon(Component c, Graphics g, int xo, int yo) {
            int w = getIconWidth();
            int h = getIconHeight();

            g.setColor(lightShadow);
            g.drawLine(xo, yo, xo+w-1, yo);
            g.drawLine(xo, yo+1, xo+w-3, yo+1);
            g.setColor(darkShadow);
            g.drawLine(xo+w-2, yo+1, xo+w-1, yo+1);

            for ( int x = xo+1, y = yo+2, dx = w-6; y+1 < yo+h; y += 2 ) {
                g.setColor(lightShadow);
                g.drawLine(x, y,   x+1, y);
                g.drawLine(x, y+1, x+1, y+1);
                if ( dx > 0 ) {
                    g.setColor(fill);
                    g.drawLine(x+2, y,   x+1+dx, y);
                    g.drawLine(x+2, y+1, x+1+dx, y+1);
                }
                g.setColor(darkShadow);
                g.drawLine(x+dx+2, y,   x+dx+3, y);
                g.drawLine(x+dx+2, y+1, x+dx+3, y+1);
                x += 1;
                dx -= 2;
            }

            g.setColor(darkShadow);
            g.drawLine(xo+(w/2), yo+h-1, xo+(w/2), yo+h-1);

        }

        public int getIconWidth() {
            return 11;
        }

        public int getIconHeight() {
            return 11;
        }
    }

    /**
     *{@inheritDoc}
     *
     * @since 1.6
     */
    protected PropertyChangeListener createPropertyChangeListener() {
        return new MotifPropertyChangeListener();
    }

    /**
     * This class should be made &quot;protected&quot; in future releases.
     */
    private class MotifPropertyChangeListener
            extends BasicComboBoxUI.PropertyChangeHandler {
        public void propertyChange(PropertyChangeEvent e) {
            super.propertyChange(e);
            String propertyName = e.getPropertyName();
            if (propertyName == "enabled") {
                if (comboBox.isEnabled()) {
                    Component editor = motifGetEditor();
                    if (editor != null) {
                        editor.setBackground(UIManager.getColor("text"));
                    }
                }
            }
        }
    }
}
