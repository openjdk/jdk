/*
 * Copyright (c) 2002, 2026, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.java.swing;

import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.Rectangle;
import java.awt.Stroke;
import java.awt.Window;
import java.awt.geom.AffineTransform;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

import javax.swing.ButtonModel;
import javax.swing.Icon;
import javax.swing.JComponent;
import javax.swing.JMenu;
import javax.swing.RepaintManager;

import sun.awt.SunToolkit;
import sun.swing.MenuItemLayoutHelper;
import sun.swing.SwingUtilities2;

import static sun.java2d.pipe.Region.clipRound;

/**
 * A collection of utility methods for Swing.
 * <p>
 * <b>WARNING:</b> While this class is public, it should not be treated as
 * public API and its API may change in incompatible ways between dot dot
 * releases and even patch releases. You should not rely on this class even
 * existing.
 *
 * This is a second part of sun.swing.SwingUtilities2.
 */
public class SwingUtilities3 {
    /**
     * The {@code clientProperty} key for delegate {@code RepaintManager}
     */
    private static final Object DELEGATE_REPAINT_MANAGER_KEY =
        new StringBuilder("DelegateRepaintManagerKey");

    private static volatile boolean repaintDelegateSet;

    /**
      * Registers delegate RepaintManager for {@code JComponent}.
      */
    public static void setDelegateRepaintManager(JComponent component,
                                                RepaintManager repaintManager) {
        /* setting up flag to speed up lookups in case
         * there are no delegate RepaintManagers used.
         */
        repaintDelegateSet = true;

        component.putClientProperty(DELEGATE_REPAINT_MANAGER_KEY,
                                    repaintManager);
    }

    private static final Map<Container, Boolean> vsyncedMap =
        Collections.synchronizedMap(new WeakHashMap<Container, Boolean>());

    /**
     * Sets vsyncRequested state for the {@code rootContainer}.  If
     * {@code isRequested} is {@code true} then vsynced
     * {@code BufferStrategy} is enabled for this {@code rootContainer}.
     *
     * Note: requesting vsynced painting does not guarantee one. The outcome
     * depends on current RepaintManager's RepaintManager.PaintManager
     * and on the capabilities of the graphics hardware/software and what not.
     *
     * @param rootContainer topmost container. Should be {@code Window}
     * @param isRequested the value to set vsyncRequested state to
     */
    public static void setVsyncRequested(Container rootContainer,
                                         boolean isRequested) {
        assert (rootContainer instanceof Window);
        if (isRequested) {
            vsyncedMap.put(rootContainer, Boolean.TRUE);
        } else {
            vsyncedMap.remove(rootContainer);
        }
    }

    /**
     * Checks if vsync painting is requested for {@code rootContainer}
     *
     * @param rootContainer topmost container. Should be Window
     * @return {@code true} if vsync painting is requested for {@code rootContainer}
     */
    public static boolean isVsyncRequested(Container rootContainer) {
        assert (rootContainer instanceof Window);
        return Boolean.TRUE == vsyncedMap.get(rootContainer);
    }

    /**
     * Returns delegate {@code RepaintManager} for {@code component} hierarchy.
     */
    public static RepaintManager getDelegateRepaintManager(Component
                                                            component) {
        RepaintManager delegate = null;
        if (repaintDelegateSet) {
            while (delegate == null && component != null) {
                while (component != null
                         && ! (component instanceof JComponent)) {
                    component = component.getParent();
                }
                if (component != null) {
                    delegate = (RepaintManager)
                        ((JComponent) component)
                          .getClientProperty(DELEGATE_REPAINT_MANAGER_KEY);
                    component = component.getParent();
                }

            }
        }
        return delegate;
    }

    public static void applyInsets(Rectangle rect, Insets insets) {
        applyInsets(rect, insets, true);
    }

    public static void applyInsets(Rectangle rect, Insets insets, boolean leftToRight) {
        if (insets != null) {
            rect.x += leftToRight ? insets.left : insets.right;
            rect.y += insets.top;
            rect.width -= (insets.left + insets.right);
            rect.height -= (insets.top + insets.bottom);
        }
    }

    public static void paintCheckIcon(Graphics g, MenuItemLayoutHelper lh,
                               MenuItemLayoutHelper.LayoutResult lr,
                               Color holdc, Color foreground) {
        if (lh.getCheckIcon() != null) {
            ButtonModel model = lh.getMenuItem().getModel();
            if (model.isArmed() || (lh.getMenuItem() instanceof JMenu
                    && model.isSelected())) {
                g.setColor(foreground);
            } else {
                g.setColor(holdc);
            }
            if (lh.useCheckAndArrow()) {
                lh.getCheckIcon().paintIcon(lh.getMenuItem(), g,
                        lr.getCheckRect().x, lr.getCheckRect().y);
            }
            g.setColor(holdc);
        }
    }

    public static void paintIcon(Graphics g, MenuItemLayoutHelper lh,
                          MenuItemLayoutHelper.LayoutResult lr, Color holdc) {
        if (lh.getIcon() != null) {
            Icon icon;
            ButtonModel model = lh.getMenuItem().getModel();
            if (!model.isEnabled()) {
                icon = lh.getMenuItem().getDisabledIcon();
            } else if (model.isPressed() && model.isArmed()) {
                icon = lh.getMenuItem().getPressedIcon();
                if (icon == null) {
                    // Use default icon
                    icon = lh.getMenuItem().getIcon();
                }
            } else {
                icon = lh.getMenuItem().getIcon();
            }

            if (icon != null) {
                icon.paintIcon(lh.getMenuItem(), g, lr.getIconRect().x,
                        lr.getIconRect().y);
                g.setColor(holdc);
            }
        }
    }


    public static void paintAccText(Graphics g, MenuItemLayoutHelper lh,
                             MenuItemLayoutHelper.LayoutResult lr,
                             Color disabledForeground,
                             Color acceleratorSelectionForeground,
                             Color acceleratorForeground) {
        if (!lh.getAccText().isEmpty()) {
            ButtonModel model = lh.getMenuItem().getModel();
            g.setFont(lh.getAccFontMetrics().getFont());
            if (!model.isEnabled()) {

                // paint the accText disabled
                if (disabledForeground != null) {
                    g.setColor(disabledForeground);
                    SwingUtilities2.drawString(lh.getMenuItem(), g,
                            lh.getAccText(), lr.getAccRect().x,
                            lr.getAccRect().y + lh.getAccFontMetrics().getAscent());
                } else {
                    g.setColor(lh.getMenuItem().getBackground().brighter());
                    SwingUtilities2.drawString(lh.getMenuItem(), g,
                            lh.getAccText(), lr.getAccRect().x,
                            lr.getAccRect().y + lh.getAccFontMetrics().getAscent());
                    g.setColor(lh.getMenuItem().getBackground().darker());
                    SwingUtilities2.drawString(lh.getMenuItem(), g,
                            lh.getAccText(), lr.getAccRect().x - 1,
                            lr.getAccRect().y + lh.getFontMetrics().getAscent() - 1);
                }
            } else {

                // paint the accText normally
                if (model.isArmed()
                        || (lh.getMenuItem() instanceof JMenu
                        && model.isSelected())) {
                    g.setColor(acceleratorSelectionForeground);
                } else {
                    g.setColor(acceleratorForeground);
                }
                SwingUtilities2.drawString(lh.getMenuItem(), g, lh.getAccText(),
                        lr.getAccRect().x, lr.getAccRect().y +
                                lh.getAccFontMetrics().getAscent());
            }
        }
    }

    public static void paintArrowIcon(Graphics g, MenuItemLayoutHelper lh,
                               MenuItemLayoutHelper.LayoutResult lr,
                               Color foreground) {
        if (lh.getArrowIcon() != null) {
            ButtonModel model = lh.getMenuItem().getModel();
            if (model.isArmed() || (lh.getMenuItem() instanceof JMenu
                    && model.isSelected())) {
                g.setColor(foreground);
            }
            if (lh.useCheckAndArrow()) {
                lh.getArrowIcon().paintIcon(lh.getMenuItem(), g,
                        lr.getArrowRect().x, lr.getArrowRect().y);
            }
        }
    }

    /**
     * A task which paints an <i>unscaled</i> border after {@code Graphics}
     * transforms are removed. It's used with the
     * {@link #paintBorder(Component, Graphics, int, int, int, int, UnscaledBorderPainter)
     * SwingUtilities3.paintBorder} which manages changing the transforms and calculating
     * the coordinates and size of the border.
     */
    @FunctionalInterface
    public interface UnscaledBorderPainter {
        /**
         * Paints the border for the specified component after the
         * {@code Graphics} transforms are removed.
         *
         * <p>
         * The <i>x</i> and <i>y</i> of the painted border are zero.
         *
         * @param c the component for which this border is being painted
         * @param g the paint graphics
         * @param w the width of the painted border, in physical pixels
         * @param h the height of the painted border, in physical pixels
         * @param scaleFactor the scale that was in the {@code Graphics}
         *
         * @see #paintBorder(Component, Graphics, int, int, int, int, UnscaledBorderPainter)
         * SwingUtilities3.paintBorder
         * @see javax.swing.border.Border#paintBorder(Component, Graphics, int, int, int, int)
         * Border.paintBorder
         */
        void paintUnscaledBorder(Component c, Graphics g,
                                 int w, int h,
                                 double scaleFactor);
    }

    /**
     * Paints the border for a component ensuring its sides have consistent
     * thickness at different scales.
     * <p>
     * It performs the following steps:
     * <ol>
     *     <li>Reset the scale transform on the {@code Graphics},</li>
     *     <li>Call {@code painter} to paint the border,</li>
     *     <li>Restores the transform.</li>
     * </ol>
     *
     * @param c the component for which this border is being painted
     * @param g the paint graphics
     * @param x the x position of the painted border
     * @param y the y position of the painted border
     * @param w the width of the painted border
     * @param h the height of the painted border
     * @param painter the painter object which paints the border after
     *                the transform on the {@code Graphics} is reset
     */
    public static void paintBorder(Component c, Graphics g,
                                   int x, int y,
                                   int w, int h,
                                   UnscaledBorderPainter painter) {

        // Step 1: Reset Transform
        AffineTransform at = null;
        Stroke oldStroke = null;
        boolean resetTransform = false;
        double scaleFactor = 1;

        int xtranslation = x;
        int ytranslation = y;
        int width = w;
        int height = h;

        if (g instanceof Graphics2D) {
            Graphics2D g2d = (Graphics2D) g;
            at = g2d.getTransform();
            oldStroke = g2d.getStroke();
            scaleFactor = Math.min(at.getScaleX(), at.getScaleY());

            // if m01 or m10 is non-zero, then there is a rotation or shear,
            // or if scale=1, skip resetting the transform in these cases.
            resetTransform = ((at.getShearX() == 0) && (at.getShearY() == 0))
                    && ((at.getScaleX() > 1) || (at.getScaleY() > 1));

            if (resetTransform) {
                /* Deactivate the HiDPI scaling transform,
                 * so we can do paint operations in the device
                 * pixel coordinate system instead of the logical coordinate system.
                 */
                g2d.setTransform(new AffineTransform());
                double xx = at.getScaleX() * x + at.getTranslateX();
                double yy = at.getScaleY() * y + at.getTranslateY();
                xtranslation = clipRound(xx);
                ytranslation = clipRound(yy);
                width = clipRound(at.getScaleX() * w + xx) - xtranslation;
                height = clipRound(at.getScaleY() * h + yy) - ytranslation;
            }
        }

        g.translate(xtranslation, ytranslation);

        // Step 2: Call respective paintBorder with transformed values
        painter.paintUnscaledBorder(c, g, width, height, scaleFactor);

        // Step 3: Restore previous stroke & transform
        g.translate(-xtranslation, -ytranslation);
        if (g instanceof Graphics2D) {
            Graphics2D g2d = (Graphics2D) g;
            g2d.setStroke(oldStroke);
            if (resetTransform) {
                g2d.setTransform(at);
            }
        }
    }
}
