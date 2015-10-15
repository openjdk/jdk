/*
 * Copyright (c) 1997, 2014, Oracle and/or its affiliates. All rights reserved.
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

import javax.swing.*;

import javax.swing.plaf.UIResource;

import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Polygon;

import java.io.Serializable;

/**
 * Icon factory for the CDE/Motif Look and Feel
 * <p>
 * <strong>Warning:</strong>
 * Serialized objects of this class will not be compatible with
 * future Swing releases.  The current serialization support is appropriate
 * for short term storage or RMI between applications running the same
 * version of Swing.  A future release of Swing will provide support for
 * long term persistence.
 *
 * 1.20 04/27/99
 * @author Georges Saab
 */
@SuppressWarnings("serial") // Same-version serialization only
public class MotifIconFactory implements Serializable
{
    private static Icon checkBoxIcon;
    private static Icon radioButtonIcon;
    private static Icon menuItemCheckIcon;
    private static Icon menuItemArrowIcon;
    private static Icon menuArrowIcon;

    public static Icon getMenuItemCheckIcon() {
        return null;
    }

    public static Icon getMenuItemArrowIcon() {
        if (menuItemArrowIcon == null) {
            menuItemArrowIcon = new MenuItemArrowIcon();
        }
        return menuItemArrowIcon;
    }

    public static Icon getMenuArrowIcon() {
        if (menuArrowIcon == null) {
            menuArrowIcon = new MenuArrowIcon();
        }
        return menuArrowIcon;
    }

    public static Icon getCheckBoxIcon() {
        if (checkBoxIcon == null) {
            checkBoxIcon = new CheckBoxIcon();
        }
        return checkBoxIcon;
    }

    public static Icon getRadioButtonIcon() {
        if (radioButtonIcon == null) {
            radioButtonIcon = new RadioButtonIcon();
        }
        return radioButtonIcon;
    }

    @SuppressWarnings("serial") // Same-version serialization only
    private static class CheckBoxIcon implements Icon, UIResource, Serializable  {
        static final int csize = 13;

        private Color control = UIManager.getColor("control");
        private Color foreground = UIManager.getColor("CheckBox.foreground");
        private Color shadow = UIManager.getColor("controlShadow");
        private Color highlight = UIManager.getColor("controlHighlight");
        private Color lightShadow = UIManager.getColor("controlLightShadow");

        public void paintIcon(Component c, Graphics g, int x, int y) {
            AbstractButton b = (AbstractButton) c;
            ButtonModel model = b.getModel();

            boolean flat = false;

            if(b instanceof JCheckBox) {
                flat = ((JCheckBox)b).isBorderPaintedFlat();
            }

            boolean isPressed = model.isPressed();
            boolean isArmed = model.isArmed();
            boolean isEnabled = model.isEnabled();
            boolean isSelected = model.isSelected();

            // There are 4 "looks" to the Motif CheckBox:
            //  drawCheckBezelOut  -  default unchecked state
            //  drawBezel          -  when we uncheck in toggled state
            //  drawCheckBezel     -  when we check in toggle state
            //  drawCheckBezelIn   -  selected, mouseReleased
            boolean checkToggleIn = ((isPressed &&
                                      !isArmed   &&
                                      isSelected) ||
                                     (isPressed &&
                                      isArmed   &&
                                      !isSelected));
            boolean uncheckToggleOut = ((isPressed &&
                                         !isArmed &&
                                         !isSelected) ||
                                        (isPressed &&
                                         isArmed &&
                                         isSelected));

            boolean checkIn = (!isPressed  &&
                               isArmed    &&
                               isSelected  ||
                               (!isPressed &&
                                !isArmed  &&
                                isSelected));


            if(flat) {
                g.setColor(shadow);
                g.drawRect(x+2,y,csize-1,csize-1);
                if(uncheckToggleOut || checkToggleIn) {
                    g.setColor(control);
                    g.fillRect(x+3,y+1,csize-2,csize-2);
                }
            }

            if (checkToggleIn) {
                // toggled from unchecked to checked
                drawCheckBezel(g,x,y,csize,true,false,false,flat);
            } else if (uncheckToggleOut) {
                // MotifBorderFactory.drawBezel(g,x,y,csize,csize,false,false);
                drawCheckBezel(g,x,y,csize,true,true,false,flat);
            } else if (checkIn) {
                // show checked, unpressed state
                drawCheckBezel(g,x,y,csize,false,false,true,flat);
            } else if(!flat) {
                //  show unchecked state
                drawCheckBezelOut(g,x,y,csize);
            }
        }

        public int getIconWidth() {
            return csize;
        }

        public int getIconHeight() {
            return csize;
        }

        public void drawCheckBezelOut(Graphics g, int x, int y, int csize){
            Color controlShadow = UIManager.getColor("controlShadow");

            int w = csize;
            int h = csize;
            Color oldColor = g.getColor();

            g.translate(x,y);
            g.setColor(highlight);    // inner 3D border
            g.drawLine(0, 0, 0, h-1);
            g.drawLine(1, 0, w-1, 0);

            g.setColor(shadow);         // black drop shadow  __|
            g.drawLine(1, h-1, w-1, h-1);
            g.drawLine(w-1, h-1, w-1, 1);
            g.translate(-x,-y);
            g.setColor(oldColor);
        }

        public void drawCheckBezel(Graphics g, int x, int y, int csize,
                                   boolean shade, boolean out, boolean check, boolean flat)
            {


                Color oldColor = g.getColor();
                g.translate(x, y);


                //bottom
                if(!flat) {
                    if (out) {
                        g.setColor(control);
                        g.fillRect(1,1,csize-2,csize-2);
                        g.setColor(shadow);
                    } else {
                        g.setColor(lightShadow);
                        g.fillRect(0,0,csize,csize);
                        g.setColor(highlight);
                    }

                    g.drawLine(1,csize-1,csize-2,csize-1);
                    if (shade) {
                        g.drawLine(2,csize-2,csize-3,csize-2);
                        g.drawLine(csize-2,2,csize-2 ,csize-1);
                        if (out) {
                            g.setColor(highlight);
                        } else {
                            g.setColor(shadow);
                        }
                        g.drawLine(1,2,1,csize-2);
                        g.drawLine(1,1,csize-3,1);
                        if (out) {
                            g.setColor(shadow);
                        } else {
                            g.setColor(highlight);
                        }
                    }
                    //right
                    g.drawLine(csize-1,1,csize-1,csize-1);

                    //left
                    if (out) {
                        g.setColor(highlight);
                    } else {
                        g.setColor(shadow);
                    }
                    g.drawLine(0,1,0,csize-1);

                    //top
                    g.drawLine(0,0,csize-1,0);
                }

                if (check) {
                    // draw check
                    g.setColor(foreground);
                    g.drawLine(csize-2,1,csize-2,2);
                    g.drawLine(csize-3,2,csize-3,3);
                    g.drawLine(csize-4,3,csize-4,4);
                    g.drawLine(csize-5,4,csize-5,6);
                    g.drawLine(csize-6,5,csize-6,8);
                    g.drawLine(csize-7,6,csize-7,10);
                    g.drawLine(csize-8,7,csize-8,10);
                    g.drawLine(csize-9,6,csize-9,9);
                    g.drawLine(csize-10,5,csize-10,8);
                    g.drawLine(csize-11,5,csize-11,7);
                    g.drawLine(csize-12,6,csize-12,6);
                }
                g.translate(-x, -y);
                g.setColor(oldColor);
            }
    } // end class CheckBoxIcon

    @SuppressWarnings("serial") // Same-version serialization only
    private static class RadioButtonIcon implements Icon, UIResource, Serializable {
        private Color dot = UIManager.getColor("activeCaptionBorder");
        private Color highlight = UIManager.getColor("controlHighlight");
        private Color shadow = UIManager.getColor("controlShadow");

        public void paintIcon(Component c, Graphics g, int x, int y) {
            // fill interior
            AbstractButton b = (AbstractButton) c;
            ButtonModel model = b.getModel();

            int w = getIconWidth();
            int h = getIconHeight();

            boolean isPressed = model.isPressed();
            boolean isArmed = model.isArmed();
            boolean isEnabled = model.isEnabled();
            boolean isSelected = model.isSelected();

            boolean checkIn = ((isPressed &&
                                !isArmed   &&
                                isSelected) ||
                               (isPressed &&
                                isArmed   &&
                                !isSelected)
                               ||
                               (!isPressed  &&
                                isArmed    &&
                                isSelected  ||
                                (!isPressed &&
                                 !isArmed  &&
                                 isSelected)));

            if (checkIn){
                g.setColor(shadow);
                g.drawLine(x+5,y+0,x+8,y+0);
                g.drawLine(x+3,y+1,x+4,y+1);
                g.drawLine(x+9,y+1,x+9,y+1);
                g.drawLine(x+2,y+2,x+2,y+2);
                g.drawLine(x+1,y+3,x+1,y+3);
                g.drawLine(x,y+4,x,y+9);
                g.drawLine(x+1,y+10,x+1,y+10);
                g.drawLine(x+2,y+11,x+2,y+11);
                g.setColor(highlight);
                g.drawLine(x+3,y+12,x+4,y+12);
                g.drawLine(x+5,y+13,x+8,y+13);
                g.drawLine(x+9,y+12,x+10,y+12);
                g.drawLine(x+11,y+11,x+11,y+11);
                g.drawLine(x+12,y+10,x+12,y+10);
                g.drawLine(x+13,y+9,x+13,y+4);
                g.drawLine(x+12,y+3,x+12,y+3);
                g.drawLine(x+11,y+2,x+11,y+2);
                g.drawLine(x+10,y+1,x+10,y+1);
                g.setColor(dot);
                g.fillRect(x+4,y+5,6,4);
                g.drawLine(x+5,y+4,x+8,y+4);
                g.drawLine(x+5,y+9,x+8,y+9);
            }
            else {
                g.setColor(highlight);
                g.drawLine(x+5,y+0,x+8,y+0);
                g.drawLine(x+3,y+1,x+4,y+1);
                g.drawLine(x+9,y+1,x+9,y+1);
                g.drawLine(x+2,y+2,x+2,y+2);
                g.drawLine(x+1,y+3,x+1,y+3);
                g.drawLine(x,y+4,x,y+9);
                g.drawLine(x+1,y+10,x+1,y+10);
                g.drawLine(x+2,y+11,x+2,y+11);

                g.setColor(shadow);
                g.drawLine(x+3,y+12,x+4,y+12);
                g.drawLine(x+5,y+13,x+8,y+13);
                g.drawLine(x+9,y+12,x+10,y+12);
                g.drawLine(x+11,y+11,x+11,y+11);
                g.drawLine(x+12,y+10,x+12,y+10);
                g.drawLine(x+13,y+9,x+13,y+4);
                g.drawLine(x+12,y+3,x+12,y+3);
                g.drawLine(x+11,y+2,x+11,y+2);
                g.drawLine(x+10,y+1,x+10,y+1);

            }
        }

        public int getIconWidth() {
            return 14;
        }

        public int getIconHeight() {
            return 14;
        }
    } // end class RadioButtonIcon

    @SuppressWarnings("serial") // Same-version serialization only
    private static class MenuItemCheckIcon implements Icon, UIResource, Serializable
    {
        public void paintIcon(Component c,Graphics g, int x, int y)
            {
            }
        public int getIconWidth() { return 0; }
        public int getIconHeight() { return 0; }
    }  // end class MenuItemCheckIcon


    @SuppressWarnings("serial") // Same-version serialization only
    private static class MenuItemArrowIcon implements Icon, UIResource, Serializable
    {
        public void paintIcon(Component c,Graphics g, int x, int y)
            {
            }
        public int getIconWidth() { return 0; }
        public int getIconHeight() { return 0; }
    }  // end class MenuItemArrowIcon

    @SuppressWarnings("serial") // Same-version serialization only
    private static class MenuArrowIcon implements Icon, UIResource, Serializable
    {
        private Color focus = UIManager.getColor("windowBorder");
        private Color shadow = UIManager.getColor("controlShadow");
        private Color highlight = UIManager.getColor("controlHighlight");

        public void paintIcon(Component c, Graphics g, int x, int y) {
            AbstractButton b = (AbstractButton) c;
            ButtonModel model = b.getModel();

            // These variables are kind of pointless as the following code
            // assumes the icon will be 10 x 10 regardless of their value.
            int w = getIconWidth();
            int h = getIconHeight();

            Color oldColor = g.getColor();

            if (model.isSelected()){
                if( MotifGraphicsUtils.isLeftToRight(c) ){
                    g.setColor(shadow);
                    g.fillRect(x+1,y+1,2,h);
                    g.drawLine(x+4,y+2,x+4,y+2);
                    g.drawLine(x+6,y+3,x+6,y+3);
                    g.drawLine(x+8,y+4,x+8,y+5);
                    g.setColor(focus);
                    g.fillRect(x+2,y+2,2,h-2);
                    g.fillRect(x+4,y+3,2,h-4);
                    g.fillRect(x+6,y+4,2,h-6);
                    g.setColor(highlight);
                    g.drawLine(x+2,y+h,x+2,y+h);
                    g.drawLine(x+4,y+h-1,x+4,y+h-1);
                    g.drawLine(x+6,y+h-2,x+6,y+h-2);
                    g.drawLine(x+8,y+h-4,x+8,y+h-3);
                } else {
                    g.setColor(highlight);
                    g.fillRect(x+7,y+1,2,10);
                    g.drawLine(x+5,y+9,x+5,y+9);
                    g.drawLine(x+3,y+8,x+3,y+8);
                    g.drawLine(x+1,y+6,x+1,y+7);
                    g.setColor(focus);
                    g.fillRect(x+6,y+2,2,8);
                    g.fillRect(x+4,y+3,2,6);
                    g.fillRect(x+2,y+4,2,4);
                    g.setColor(shadow);
                    g.drawLine(x+1,y+4,x+1,y+5);
                    g.drawLine(x+3,y+3,x+3,y+3);
                    g.drawLine(x+5,y+2,x+5,y+2);
                    g.drawLine(x+7,y+1,x+7,y+1);
                }
            } else {
                if( MotifGraphicsUtils.isLeftToRight(c) ){
                    g.setColor(highlight);
                    g.drawLine(x+1,y+1,x+1,y+h);
                    g.drawLine(x+2,y+1,x+2,y+h-2);
                    g.fillRect(x+3,y+2,2,2);
                    g.fillRect(x+5,y+3,2,2);
                    g.fillRect(x+7,y+4,2,2);
                    g.setColor(shadow);
                    g.drawLine(x+2,y+h-1,x+2,y+h);
                    g.fillRect(x+3,y+h-2,2,2);
                    g.fillRect(x+5,y+h-3,2,2);
                    g.fillRect(x+7,y+h-4,2,2);
                    g.setColor(oldColor);
                } else {
                    g.setColor(highlight);
                    g.fillRect(x+1,y+4,2,2);
                    g.fillRect(x+3,y+3,2,2);
                    g.fillRect(x+5,y+2,2,2);
                    g.drawLine(x+7,y+1,x+7,y+2);
                    g.setColor(shadow);
                    g.fillRect(x+1,y+h-4,2,2);
                    g.fillRect(x+3,y+h-3,2,2);
                    g.fillRect(x+5,y+h-2,2,2);
                    g.drawLine(x+7,y+3,x+7,y+h);
                    g.drawLine(x+8,y+1,x+8,y+h);
                    g.setColor(oldColor);
                }
            }

        }
        public int getIconWidth() { return 10; }
        public int getIconHeight() { return 10; }
    } // End class MenuArrowIcon
}
