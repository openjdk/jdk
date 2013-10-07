/*
 * Copyright (c) 1998, 2004, Oracle and/or its affiliates. All rights reserved.
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

import java.awt.*;
import java.awt.event.*;
import javax.swing.plaf.basic.*;
import javax.swing.*;
import javax.swing.plaf.*;
import javax.swing.border.*;
import java.io.Serializable;

/**
 * JButton subclass to help out MetalComboBoxUI
 * <p>
 * <strong>Warning:</strong>
 * Serialized objects of this class will not be compatible with
 * future Swing releases. The current serialization support is
 * appropriate for short term storage or RMI between applications running
 * the same version of Swing.  As of 1.4, support for long term storage
 * of all JavaBeans&trade;
 * has been added to the <code>java.beans</code> package.
 * Please see {@link java.beans.XMLEncoder}.
 *
 * @see MetalComboBoxButton
 * @author Tom Santos
 */
public class MetalComboBoxButton extends JButton {
    protected JComboBox comboBox;
    protected JList listBox;
    protected CellRendererPane rendererPane;
    protected Icon comboIcon;
    protected boolean iconOnly = false;

    public final JComboBox getComboBox() { return comboBox;}
    public final void setComboBox( JComboBox cb ) { comboBox = cb;}

    public final Icon getComboIcon() { return comboIcon;}
    public final void setComboIcon( Icon i ) { comboIcon = i;}

    public final boolean isIconOnly() { return iconOnly;}
    public final void setIconOnly( boolean isIconOnly ) { iconOnly = isIconOnly;}

    MetalComboBoxButton() {
        super( "" );
        DefaultButtonModel model = new DefaultButtonModel() {
            public void setArmed( boolean armed ) {
                super.setArmed( isPressed() ? true : armed );
            }
        };
        setModel( model );
    }

    public MetalComboBoxButton( JComboBox cb, Icon i,
                                CellRendererPane pane, JList list ) {
        this();
        comboBox = cb;
        comboIcon = i;
        rendererPane = pane;
        listBox = list;
        setEnabled( comboBox.isEnabled() );
    }

    public MetalComboBoxButton( JComboBox cb, Icon i, boolean onlyIcon,
                                CellRendererPane pane, JList list ) {
        this( cb, i, pane, list );
        iconOnly = onlyIcon;
    }

    public boolean isFocusTraversable() {
        return false;
    }

    public void setEnabled(boolean enabled) {
        super.setEnabled(enabled);

        // Set the background and foreground to the combobox colors.
        if (enabled) {
            setBackground(comboBox.getBackground());
            setForeground(comboBox.getForeground());
        } else {
            setBackground(UIManager.getColor("ComboBox.disabledBackground"));
            setForeground(UIManager.getColor("ComboBox.disabledForeground"));
        }
    }

    public void paintComponent( Graphics g ) {
        boolean leftToRight = MetalUtils.isLeftToRight(comboBox);

        // Paint the button as usual
        super.paintComponent( g );

        Insets insets = getInsets();

        int width = getWidth() - (insets.left + insets.right);
        int height = getHeight() - (insets.top + insets.bottom);

        if ( height <= 0 || width <= 0 ) {
            return;
        }

        int left = insets.left;
        int top = insets.top;
        int right = left + (width - 1);
        int bottom = top + (height - 1);

        int iconWidth = 0;
        int iconLeft = (leftToRight) ? right : left;

        // Paint the icon
        if ( comboIcon != null ) {
            iconWidth = comboIcon.getIconWidth();
            int iconHeight = comboIcon.getIconHeight();
            int iconTop = 0;

            if ( iconOnly ) {
                iconLeft = (getWidth() / 2) - (iconWidth / 2);
                iconTop = (getHeight() / 2) - (iconHeight / 2);
            }
            else {
                if (leftToRight) {
                    iconLeft = (left + (width - 1)) - iconWidth;
                }
                else {
                    iconLeft = left;
                }
                iconTop = (top + ((bottom - top) / 2)) - (iconHeight / 2);
            }

            comboIcon.paintIcon( this, g, iconLeft, iconTop );

            // Paint the focus
            if ( comboBox.hasFocus() && (!MetalLookAndFeel.usingOcean() ||
                                         comboBox.isEditable())) {
                g.setColor( MetalLookAndFeel.getFocusColor() );
                g.drawRect( left - 1, top - 1, width + 3, height + 1 );
            }
        }

        if (MetalLookAndFeel.usingOcean()) {
            // With Ocean the button only paints the arrow, bail.
            return;
        }

        // Let the renderer paint
        if ( ! iconOnly && comboBox != null ) {
            ListCellRenderer renderer = comboBox.getRenderer();
            Component c;
            boolean renderPressed = getModel().isPressed();
            c = renderer.getListCellRendererComponent(listBox,
                                                      comboBox.getSelectedItem(),
                                                      -1,
                                                      renderPressed,
                                                      false);
            c.setFont(rendererPane.getFont());

            if ( model.isArmed() && model.isPressed() ) {
                if ( isOpaque() ) {
                    c.setBackground(UIManager.getColor("Button.select"));
                }
                c.setForeground(comboBox.getForeground());
            }
            else if ( !comboBox.isEnabled() ) {
                if ( isOpaque() ) {
                    c.setBackground(UIManager.getColor("ComboBox.disabledBackground"));
                }
                c.setForeground(UIManager.getColor("ComboBox.disabledForeground"));
            }
            else {
                c.setForeground(comboBox.getForeground());
                c.setBackground(comboBox.getBackground());
            }


            int cWidth = width - (insets.right + iconWidth);

            // Fix for 4238829: should lay out the JPanel.
            boolean shouldValidate = false;
            if (c instanceof JPanel)  {
                shouldValidate = true;
            }

            if (leftToRight) {
                rendererPane.paintComponent( g, c, this,
                                             left, top, cWidth, height, shouldValidate );
            }
            else {
                rendererPane.paintComponent( g, c, this,
                                             left + iconWidth, top, cWidth, height, shouldValidate );
            }
        }
    }

    public Dimension getMinimumSize() {
        Dimension ret = new Dimension();
        Insets insets = getInsets();
        ret.width = insets.left + getComboIcon().getIconWidth() + insets.right;
        ret.height = insets.bottom + getComboIcon().getIconHeight() + insets.top;
        return ret;
    }
}
