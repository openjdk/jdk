/*
 * Copyright 2002-2006 Sun Microsystems, Inc.  All Rights Reserved.
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

package javax.swing.plaf.synth;

import javax.swing.*;
import javax.swing.event.*;
import javax.swing.plaf.*;
import javax.swing.plaf.basic.*;
import javax.swing.border.*;

import java.applet.Applet;

import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.KeyboardFocusManager;
import java.awt.Window;
import java.awt.event.*;
import java.awt.AWTEvent;
import java.awt.Toolkit;

import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeEvent;

import java.util.*;
import sun.swing.plaf.synth.SynthUI;

/**
 * Synth's PopupMenuUI.
 *
 * @author Georges Saab
 * @author David Karlton
 * @author Arnaud Weber
 */
class SynthPopupMenuUI extends BasicPopupMenuUI implements
                PropertyChangeListener, SynthUI {
    /**
     * Maximum size of the text portion of the children menu items.
     */
    private int maxTextWidth;

    /**
     * Maximum size of the icon portion of the children menu items.
     */
    private int maxIconWidth;

    /**
     * Maximum size of the spacing between the text and accelerator
     * portions of the children menu items.
     */
    private int maxAccelSpacingWidth;

    /**
     * Maximum size of the text for the accelerator portion of the children
     * menu items.
     */
    private int maxAcceleratorWidth;

    /*
     * Maximum icon and text offsets of the children menu items.
     */
    private int maxTextOffset;
    private int maxIconOffset;

    private SynthStyle style;

    public static ComponentUI createUI(JComponent x) {
        return new SynthPopupMenuUI();
    }

    public void installDefaults() {
        if (popupMenu.getLayout() == null ||
            popupMenu.getLayout() instanceof UIResource) {
            popupMenu.setLayout(new DefaultMenuLayout(
                                    popupMenu, BoxLayout.Y_AXIS));
        }
        updateStyle(popupMenu);
    }

    private void updateStyle(JComponent c) {
        SynthContext context = getContext(c, ENABLED);
        SynthStyle oldStyle = style;
        style = SynthLookAndFeel.updateStyle(context, this);
        if (style != oldStyle) {
            if (oldStyle != null) {
                uninstallKeyboardActions();
                installKeyboardActions();
            }
        }
        context.dispose();
    }

    protected void installListeners() {
        super.installListeners();
        popupMenu.addPropertyChangeListener(this);
    }

    protected void uninstallDefaults() {
        SynthContext context = getContext(popupMenu, ENABLED);

        style.uninstallDefaults(context);
        context.dispose();
        style = null;

        if (popupMenu.getLayout() instanceof UIResource) {
            popupMenu.setLayout(null);
        }
    }

    protected void uninstallListeners() {
        super.uninstallListeners();
        popupMenu.removePropertyChangeListener(this);
    }

    public SynthContext getContext(JComponent c) {
        return getContext(c, getComponentState(c));
    }

    private SynthContext getContext(JComponent c, int state) {
        return SynthContext.getContext(SynthContext.class, c,
                    SynthLookAndFeel.getRegion(c), style, state);
    }

    private Region getRegion(JComponent c) {
        return SynthLookAndFeel.getRegion(c);
    }

    private int getComponentState(JComponent c) {
        return SynthLookAndFeel.getComponentState(c);
    }

    /**
     * Resets the max text and accerator widths,
     * text and icon offsets.
     */
    void resetAlignmentHints() {
        maxTextWidth = maxIconWidth
                     = maxAccelSpacingWidth = maxAcceleratorWidth
                     = maxTextOffset = maxIconOffset = 0;
    }

    /**
     * Adjusts the width needed to display the maximum menu item string.
     *
     * @param width Text width.
     * @return max width
     */
    int adjustTextWidth(int width) {
        maxTextWidth = Math.max(maxTextWidth, width);
        return maxTextWidth;
    }

    /**
     * Adjusts the width needed to display the maximum menu item icon.
     *
     * @param width Icon width.
     * @return max width
     */
    int adjustIconWidth(int width) {
        maxIconWidth = Math.max(maxIconWidth, width);
        return maxIconWidth;
    }

    /**
     * Adjusts the width needed to pad between the maximum menu item
     * text and accelerator.
     *
     * @param width Spacing width.
     * @return max width
     */
    int adjustAccelSpacingWidth(int width) {
        maxAccelSpacingWidth = Math.max(maxAccelSpacingWidth, width);
        return maxAccelSpacingWidth;
    }

    /**
     * Adjusts the width needed to display the maximum accelerator.
     *
     * @param width Text width.
     * @return max width
     */
    int adjustAcceleratorWidth(int width) {
        maxAcceleratorWidth = Math.max(maxAcceleratorWidth, width);
        return maxAcceleratorWidth;
    }

    /**
     * Maximum size needed to display accelerators of children menu items.
     */
    int getMaxAcceleratorWidth() {
        return maxAcceleratorWidth;
    }

    /**
     * Adjusts the text offset needed to align text horizontally.
     *
     * @param offset Text offset
     * @return max offset
     */
    int adjustTextOffset(int offset) {
        maxTextOffset = Math.max(maxTextOffset, offset);
        return maxTextOffset;
    }

   /**
    * Adjusts the icon offset needed to align icons horizontally
    *
    * @param offset Icon offset
    * @return max offset
    */
    int adjustIconOffset(int offset) {
        maxIconOffset = Math.max(maxIconOffset, offset);
        return maxIconOffset;
    }

    public void update(Graphics g, JComponent c) {
        SynthContext context = getContext(c);

        SynthLookAndFeel.update(context, g);
        context.getPainter().paintPopupMenuBackground(context,
                          g, 0, 0, c.getWidth(), c.getHeight());
        paint(context, g);
        context.dispose();
    }

    public void paint(Graphics g, JComponent c) {
        SynthContext context = getContext(c);

        paint(context, g);
        context.dispose();
    }

    protected void paint(SynthContext context, Graphics g) {
    }

    public void paintBorder(SynthContext context, Graphics g, int x,
                            int y, int w, int h) {
        context.getPainter().paintPopupMenuBorder(context, g, x, y, w, h);
    }

    public void propertyChange(PropertyChangeEvent e) {
        if (SynthLookAndFeel.shouldUpdateStyle(e)) {
            updateStyle(popupMenu);
        }
    }
}
