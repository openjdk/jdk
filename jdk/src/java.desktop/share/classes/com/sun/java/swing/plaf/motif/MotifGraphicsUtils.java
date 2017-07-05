/*
 * Copyright (c) 1997, 2008, Oracle and/or its affiliates. All rights reserved.
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

import sun.swing.SwingUtilities2;

import javax.swing.*;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Rectangle;
import java.awt.Component;
import java.awt.Insets;
import java.awt.event.KeyEvent;
import java.awt.Container;

import javax.swing.plaf.basic.*;
import javax.swing.text.View;

/*
 * @author Jeff Dinkins
 * @author Dave Kloba
 */

public class MotifGraphicsUtils implements SwingConstants
{
    /* Client Property keys for text and accelerator text widths */
    private static final String MAX_ACC_WIDTH  =  "maxAccWidth";

    /**
     * Draws the point (<b>x</b>, <b>y</b>) in the current color.
     */
    static void drawPoint(Graphics g, int x, int y) {
        g.drawLine(x, y, x, y);
    }

    /*
     * Convenience method for drawing a grooved line
     *
     */
    public static void drawGroove(Graphics g, int x, int y, int w, int h,
                                  Color shadow, Color highlight)
    {
        Color oldColor = g.getColor();  // Make no net change to g
        g.translate(x, y);

        g.setColor(shadow);
        g.drawRect(0, 0, w-2, h-2);

        g.setColor(highlight);
        g.drawLine(1, h-3, 1, 1);
        g.drawLine(1, 1, w-3, 1);

        g.drawLine(0, h-1, w-1, h-1);
        g.drawLine(w-1, h-1, w-1, 0);

        g.translate(-x, -y);
        g.setColor(oldColor);
    }

    /** Draws <b>aString</b> in the rectangle defined by
      * (<b>x</b>, <b>y</b>, <b>width</b>, <b>height</b>).
      * <b>justification</b> specifies the text's justification, one of
      * LEFT, CENTER, or RIGHT.
      * <b>drawStringInRect()</b> does not clip to the rectangle, but instead
      * uses this rectangle and the desired justification to compute the point
      * at which to begin drawing the text.
      * @see #drawString
      */
    public static void drawStringInRect(Graphics g, String aString, int x, int y,
                                 int width, int height, int justification) {
        drawStringInRect(null, g, aString, x, y, width, height, justification);
    }

    static void drawStringInRect(JComponent c, Graphics g, String aString,
                                 int x, int y, int width, int height,
                                 int justification) {
        FontMetrics  fontMetrics;
        int          drawWidth, startX, startY, delta;

        if (g.getFont() == null) {
//            throw new InconsistencyException("No font set");
            return;
        }
        fontMetrics = SwingUtilities2.getFontMetrics(c, g);
        if (fontMetrics == null) {
//            throw new InconsistencyException("No metrics for Font " + font());
            return;
        }

        if (justification == CENTER) {
            drawWidth = SwingUtilities2.stringWidth(c, fontMetrics, aString);
            if (drawWidth > width) {
                drawWidth = width;
            }
            startX = x + (width - drawWidth) / 2;
        } else if (justification == RIGHT) {
            drawWidth = SwingUtilities2.stringWidth(c, fontMetrics, aString);
            if (drawWidth > width) {
                drawWidth = width;
            }
            startX = x + width - drawWidth;
        } else {
            startX = x;
        }

        delta = (height - fontMetrics.getAscent() - fontMetrics.getDescent()) / 2;
        if (delta < 0) {
            delta = 0;
        }

        startY = y + height - delta - fontMetrics.getDescent();

        SwingUtilities2.drawString(c, g, aString, startX, startY);
    }

  /**
   * This method is not being used to paint menu item since
   * 6.0 This code left for compatibility only. Do not use or
   * override it, this will not cause any visible effect.
   */
  public static void paintMenuItem(Graphics g, JComponent c,
                                   Icon checkIcon, Icon arrowIcon,
                                   Color background, Color foreground,
                                   int defaultTextIconGap)
    {

        JMenuItem b = (JMenuItem) c;
        ButtonModel model = b.getModel();

        Dimension size = b.getSize();
        Insets i = c.getInsets();

        Rectangle viewRect = new Rectangle(size);

        viewRect.x += i.left;
        viewRect.y += i.top;
        viewRect.width -= (i.right + viewRect.x);
        viewRect.height -= (i.bottom + viewRect.y);

        Rectangle iconRect = new Rectangle();
        Rectangle textRect = new Rectangle();
        Rectangle acceleratorRect = new Rectangle();
        Rectangle checkRect = new Rectangle();
        Rectangle arrowRect = new Rectangle();

        Font holdf = g.getFont();
        Font f = c.getFont();
        g.setFont(f);
        FontMetrics fm = SwingUtilities2.getFontMetrics(c, g, f);
        FontMetrics fmAccel = SwingUtilities2.getFontMetrics(
            c, g, UIManager.getFont("MenuItem.acceleratorFont"));

        if (c.isOpaque()) {
            if (model.isArmed()|| (c instanceof JMenu && model.isSelected())) {
                g.setColor(background);
            } else {
                g.setColor(c.getBackground());
            }
            g.fillRect(0,0, size.width, size.height);
        }

        // get Accelerator text
        KeyStroke accelerator =  b.getAccelerator();
        String acceleratorText = "";
        if (accelerator != null) {
            int modifiers = accelerator.getModifiers();
            if (modifiers > 0) {
                acceleratorText = KeyEvent.getKeyModifiersText(modifiers);
                acceleratorText += "+";
            }
            acceleratorText += KeyEvent.getKeyText(accelerator.getKeyCode());
        }

        // layout the text and icon
        String text = layoutMenuItem(c, fm, b.getText(), fmAccel,
                                     acceleratorText, b.getIcon(),
                                     checkIcon, arrowIcon,
                                     b.getVerticalAlignment(),
                                     b.getHorizontalAlignment(),
                                     b.getVerticalTextPosition(),
                                     b.getHorizontalTextPosition(),
                                     viewRect, iconRect,
                                     textRect, acceleratorRect,
                                     checkRect, arrowRect,
                                     b.getText() == null
                                     ? 0 : defaultTextIconGap,
                                     defaultTextIconGap
                                     );

        // Paint the Check
        Color holdc = g.getColor();
        if (checkIcon != null) {
            if(model.isArmed() || (c instanceof JMenu && model.isSelected()))
                g.setColor(foreground);
            checkIcon.paintIcon(c, g, checkRect.x, checkRect.y);
            g.setColor(holdc);
        }

        // Paint the Icon
        if(b.getIcon() != null) {
            Icon icon;
            if(!model.isEnabled()) {
                icon = b.getDisabledIcon();
            } else if(model.isPressed() && model.isArmed()) {
                icon = b.getPressedIcon();
                if(icon == null) {
                    // Use default icon
                    icon = b.getIcon();
                }
            } else {
                icon = b.getIcon();
            }

            if (icon!=null) {
                icon.paintIcon(c, g, iconRect.x, iconRect.y);
            }
        }

        // Draw the Text
        if(text != null && !text.equals("")) {
            // Once BasicHTML becomes public, use BasicHTML.propertyKey
            // instead of the hardcoded string below!
            View v = (View) c.getClientProperty("html");
            if (v != null) {
                v.paint(g, textRect);
            } else {
                int mnemIndex = b.getDisplayedMnemonicIndex();

                if(!model.isEnabled()) {
                    // *** paint the text disabled
                    g.setColor(b.getBackground().brighter());
                    SwingUtilities2.drawStringUnderlineCharAt(b, g,text,
                        mnemIndex,
                        textRect.x, textRect.y + fmAccel.getAscent());
                    g.setColor(b.getBackground().darker());
                    SwingUtilities2.drawStringUnderlineCharAt(b, g,text,
                        mnemIndex,
                        textRect.x - 1, textRect.y + fmAccel.getAscent() - 1);

                } else {
                    // *** paint the text normally
                    if (model.isArmed()|| (c instanceof JMenu && model.isSelected())) {
                        g.setColor(foreground);
                    } else {
                        g.setColor(b.getForeground());
                    }
                    SwingUtilities2.drawStringUnderlineCharAt(b, g,text,
                                                  mnemIndex,
                                                  textRect.x,
                                                  textRect.y + fm.getAscent());
                }
            }
        }

        // Draw the Accelerator Text
        if(acceleratorText != null && !acceleratorText.equals("")) {

            //Get the maxAccWidth from the parent to calculate the offset.
            int accOffset = 0;
            Container parent = b.getParent();
            if (parent != null && parent instanceof JComponent) {
                JComponent p = (JComponent) parent;
                Integer maxValueInt = (Integer) p.getClientProperty(MotifGraphicsUtils.MAX_ACC_WIDTH);
                int maxValue = maxValueInt != null ?
                    maxValueInt.intValue() : acceleratorRect.width;

                //Calculate the offset, with which the accelerator texts will be drawn with.
                accOffset = maxValue - acceleratorRect.width;
            }

            g.setFont( UIManager.getFont("MenuItem.acceleratorFont") );
            if(!model.isEnabled()) {
                // *** paint the acceleratorText disabled
                g.setColor(b.getBackground().brighter());
                SwingUtilities2.drawString(c, g,acceleratorText,
                                              acceleratorRect.x - accOffset, acceleratorRect.y + fm.getAscent());
                g.setColor(b.getBackground().darker());
                SwingUtilities2.drawString(c, g,acceleratorText,
                                              acceleratorRect.x - accOffset - 1, acceleratorRect.y + fm.getAscent() - 1);
            } else {
                // *** paint the acceleratorText normally
                if (model.isArmed()|| (c instanceof JMenu && model.isSelected()))
                    {
                        g.setColor(foreground);
                    } else {
                        g.setColor(b.getForeground());
                    }
                SwingUtilities2.drawString(c, g,acceleratorText,
                                              acceleratorRect.x - accOffset,
                                              acceleratorRect.y + fmAccel.getAscent());
            }
        }

        // Paint the Arrow
        if (arrowIcon != null) {
            if(model.isArmed() || (c instanceof JMenu && model.isSelected()))
                g.setColor(foreground);
            if( !(b.getParent() instanceof JMenuBar) )
                arrowIcon.paintIcon(c, g, arrowRect.x, arrowRect.y);
        }

        g.setColor(holdc);
        g.setFont(holdf);
    }


    /**
     * Compute and return the location of the icons origin, the
     * location of origin of the text baseline, and a possibly clipped
     * version of the compound labels string.  Locations are computed
     * relative to the viewR rectangle.
     */

    private static String layoutMenuItem(
        JComponent c,
        FontMetrics fm,
        String text,
        FontMetrics fmAccel,
        String acceleratorText,
        Icon icon,
        Icon checkIcon,
        Icon arrowIcon,
        int verticalAlignment,
        int horizontalAlignment,
        int verticalTextPosition,
        int horizontalTextPosition,
        Rectangle viewR,
        Rectangle iconR,
        Rectangle textR,
        Rectangle acceleratorR,
        Rectangle checkIconR,
        Rectangle arrowIconR,
        int textIconGap,
        int menuItemGap
        )
    {

        SwingUtilities.layoutCompoundLabel(c,
                                           fm,
                                           text,
                                           icon,
                                           verticalAlignment,
                                           horizontalAlignment,
                                           verticalTextPosition,
                                           horizontalTextPosition,
                                           viewR,
                                           iconR,
                                           textR,
                                           textIconGap);

        /* Initialize the acceelratorText bounds rectangle textR.  If a null
         * or and empty String was specified we substitute "" here
         * and use 0,0,0,0 for acceleratorTextR.
         */
        if( (acceleratorText == null) || acceleratorText.equals("") ) {
            acceleratorR.width = acceleratorR.height = 0;
            acceleratorText = "";
        }
        else {
            acceleratorR.width
                = SwingUtilities2.stringWidth(c, fmAccel, acceleratorText);
            acceleratorR.height = fmAccel.getHeight();
        }

        /* Initialize the checkIcon bounds rectangle checkIconR.
         */

        if (checkIcon != null) {
            checkIconR.width = checkIcon.getIconWidth();
            checkIconR.height = checkIcon.getIconHeight();
        }
        else {
            checkIconR.width = checkIconR.height = 0;
        }

        /* Initialize the arrowIcon bounds rectangle arrowIconR.
         */

        if (arrowIcon != null) {
            arrowIconR.width = arrowIcon.getIconWidth();
            arrowIconR.height = arrowIcon.getIconHeight();
        }
        else {
            arrowIconR.width = arrowIconR.height = 0;
        }


        Rectangle labelR = iconR.union(textR);
        if( MotifGraphicsUtils.isLeftToRight(c) ) {
            textR.x += checkIconR.width + menuItemGap;
            iconR.x += checkIconR.width + menuItemGap;

            // Position the Accelerator text rect
            acceleratorR.x = viewR.x + viewR.width - arrowIconR.width
                             - menuItemGap - acceleratorR.width;

            // Position the Check and Arrow Icons
            checkIconR.x = viewR.x;
            arrowIconR.x = viewR.x + viewR.width - menuItemGap
                           - arrowIconR.width;
        } else {
            textR.x -= (checkIconR.width + menuItemGap);
            iconR.x -= (checkIconR.width + menuItemGap);

            // Position the Accelerator text rect
            acceleratorR.x = viewR.x + arrowIconR.width + menuItemGap;

            // Position the Check and Arrow Icons
            checkIconR.x = viewR.x + viewR.width - checkIconR.width;
            arrowIconR.x = viewR.x + menuItemGap;
        }

        // Align the accelertor text and the check and arrow icons vertically
        // with the center of the label rect.
        acceleratorR.y = labelR.y + (labelR.height/2) - (acceleratorR.height/2);
        arrowIconR.y = labelR.y + (labelR.height/2) - (arrowIconR.height/2);
        checkIconR.y = labelR.y + (labelR.height/2) - (checkIconR.height/2);

        /*
          System.out.println("Layout: v=" +viewR+"  c="+checkIconR+" i="+
          iconR+" t="+textR+" acc="+acceleratorR+" a="+arrowIconR);
          */
        return text;
    }

  private static void drawMenuBezel(Graphics g, Color background,
                                    int x, int y,
                                    int width, int height)
    {
      // shadowed button region
      g.setColor(background);
      g.fillRect(x,y,width,height);

      g.setColor(background.brighter().brighter());
      g.drawLine(x+1,       y+height-1,  x+width-1, y+height-1);
      g.drawLine(x+width-1, y+height-2,  x+width-1, y+1);

      g.setColor(background.darker().darker());
      g.drawLine(x,   y,   x+width-2, y);
      g.drawLine(x,   y+1, x,         y+height-2);

    }

    /*
     * Convenience function for determining ComponentOrientation.  Helps us
     * avoid having Munge directives throughout the code.
     */
    static boolean isLeftToRight( Component c ) {
        return c.getComponentOrientation().isLeftToRight();
    }
}
