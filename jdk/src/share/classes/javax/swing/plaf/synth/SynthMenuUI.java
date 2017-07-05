/*
 * Copyright 2002-2008 Sun Microsystems, Inc.  All Rights Reserved.
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

import java.awt.*;
import java.beans.*;
import javax.swing.*;
import javax.swing.plaf.*;
import javax.swing.plaf.basic.*;
import sun.swing.MenuItemLayoutHelper;

/**
 * Provides the Synth L&F UI delegate for
 * {@link javax.swing.JMenu}.
 *
 * @author Georges Saab
 * @author David Karlton
 * @author Arnaud Weber
 * @since 1.7
 */
public class SynthMenuUI extends BasicMenuUI
                         implements PropertyChangeListener, SynthUI {
    private SynthStyle style;
    private SynthStyle accStyle;

    /**
     * Creates a new UI object for the given component.
     *
     * @param x component to create UI object for
     * @return the UI object
     */
    public static ComponentUI createUI(JComponent x) {
        return new SynthMenuUI();
    }

    /**
     * @inheritDoc
     */
    @Override
    protected void installDefaults() {
        updateStyle(menuItem);
    }

    /**
     * @inheritDoc
     */
    @Override
    protected void installListeners() {
        super.installListeners();
        menuItem.addPropertyChangeListener(this);
    }

    private void updateStyle(JMenuItem mi) {
        SynthStyle oldStyle = style;
        SynthContext context = getContext(mi, ENABLED);

        style = SynthLookAndFeel.updateStyle(context, this);
        if (oldStyle != style) {
            String prefix = getPropertyPrefix();
            defaultTextIconGap = style.getInt(
                           context, prefix + ".textIconGap", 4);
            if (menuItem.getMargin() == null ||
                         (menuItem.getMargin() instanceof UIResource)) {
                Insets insets = (Insets)style.get(context, prefix + ".margin");

                if (insets == null) {
                    // Some places assume margins are non-null.
                    insets = SynthLookAndFeel.EMPTY_UIRESOURCE_INSETS;
                }
                menuItem.setMargin(insets);
            }
            acceleratorDelimiter = style.getString(context, prefix +
                                            ".acceleratorDelimiter", "+");

            if (MenuItemLayoutHelper.useCheckAndArrow(menuItem)) {
                checkIcon = style.getIcon(context, prefix + ".checkIcon");
                arrowIcon = style.getIcon(context, prefix + ".arrowIcon");
            } else {
                // Not needed in this case
                checkIcon = null;
                arrowIcon = null;
            }

            ((JMenu)menuItem).setDelay(style.getInt(context, prefix +
                                                    ".delay", 200));
            if (oldStyle != null) {
                uninstallKeyboardActions();
                installKeyboardActions();
            }
        }
        context.dispose();

        SynthContext accContext = getContext(mi, Region.MENU_ITEM_ACCELERATOR,
                                             ENABLED);

        accStyle = SynthLookAndFeel.updateStyle(accContext, this);
        accContext.dispose();
    }

    /**
     * @inheritDoc
     */
    @Override
    public void uninstallUI(JComponent c) {
        super.uninstallUI(c);
        // Remove values from the parent's Client Properties.
        JComponent p = MenuItemLayoutHelper.getMenuItemParent((JMenuItem) c);
        if (p != null) {
            p.putClientProperty(
                    SynthMenuItemLayoutHelper.MAX_ACC_OR_ARROW_WIDTH, null);
        }
    }

    /**
     * @inheritDoc
     */
    @Override
    protected void uninstallDefaults() {
        SynthContext context = getContext(menuItem, ENABLED);
        style.uninstallDefaults(context);
        context.dispose();
        style = null;

        SynthContext accContext = getContext(menuItem,
                                     Region.MENU_ITEM_ACCELERATOR, ENABLED);
        accStyle.uninstallDefaults(accContext);
        accContext.dispose();
        accStyle = null;

        super.uninstallDefaults();
    }

    /**
     * @inheritDoc
     */
    @Override
    protected void uninstallListeners() {
        super.uninstallListeners();
        menuItem.removePropertyChangeListener(this);
    }

    /**
     * @inheritDoc
     */
    @Override
    public SynthContext getContext(JComponent c) {
        return getContext(c, getComponentState(c));
    }

    SynthContext getContext(JComponent c, int state) {
        Region region = SynthLookAndFeel.getRegion(c);
        return SynthContext.getContext(SynthContext.class, c, region,
                                       style, state);
    }

    SynthContext getContext(JComponent c, Region region) {
        return getContext(c, region, getComponentState(c, region));
    }

    private SynthContext getContext(JComponent c, Region region, int state) {
        return SynthContext.getContext(SynthContext.class, c,
                                       region, accStyle, state);
    }

    private int getComponentState(JComponent c) {
        int state;

        if (!c.isEnabled()) {
            return DISABLED;
        }
        if (menuItem.isArmed()) {
            state = MOUSE_OVER;
        }
        else {
            state = SynthLookAndFeel.getComponentState(c);
        }
        if (menuItem.isSelected()) {
            state |= SELECTED;
        }
        return state;
    }

    private int getComponentState(JComponent c, Region region) {
        return getComponentState(c);
    }

    /**
     * @inheritDoc
     */
    @Override
    protected Dimension getPreferredMenuItemSize(JComponent c,
                                                     Icon checkIcon,
                                                     Icon arrowIcon,
                                                     int defaultTextIconGap) {
        SynthContext context = getContext(c);
        SynthContext accContext = getContext(c, Region.MENU_ITEM_ACCELERATOR);
        Dimension value = SynthGraphicsUtils.getPreferredMenuItemSize(
                context, accContext, c, checkIcon, arrowIcon,
                defaultTextIconGap, acceleratorDelimiter,
                MenuItemLayoutHelper.useCheckAndArrow(menuItem),
                getPropertyPrefix());
        context.dispose();
        accContext.dispose();
        return value;
    }

    /**
     * @inheritDoc
     */
    @Override
    public void update(Graphics g, JComponent c) {
        SynthContext context = getContext(c);

        SynthLookAndFeel.update(context, g);
        context.getPainter().paintMenuBackground(context,
                          g, 0, 0, c.getWidth(), c.getHeight());
        paint(context, g);
        context.dispose();
    }

    /**
     * @inheritDoc
     */
    @Override
    public void paint(Graphics g, JComponent c) {
        SynthContext context = getContext(c);

        paint(context, g);
        context.dispose();
    }

    /**
     * Paints the specified component. This implementation does nothing.
     *
     * @param context context for the component being painted
     * @param g {@code Graphics} object used for painting
     */
    protected void paint(SynthContext context, Graphics g) {
        SynthContext accContext = getContext(menuItem,
                                             Region.MENU_ITEM_ACCELERATOR);
        // Refetch the appropriate check indicator for the current state
        String prefix = getPropertyPrefix();
        Icon checkIcon = style.getIcon(context, prefix + ".checkIcon");
        Icon arrowIcon = style.getIcon(context, prefix + ".arrowIcon");
        SynthGraphicsUtils.paint(context, accContext, g, checkIcon, arrowIcon,
              acceleratorDelimiter, defaultTextIconGap, getPropertyPrefix());
        accContext.dispose();
    }

    /**
     * @inheritDoc
     */
    @Override
    public void paintBorder(SynthContext context, Graphics g, int x,
                            int y, int w, int h) {
        context.getPainter().paintMenuBorder(context, g, x, y, w, h);
    }

    /**
     * @inheritDoc
     */
    @Override
    public void propertyChange(PropertyChangeEvent e) {
        if (SynthLookAndFeel.shouldUpdateStyle(e)) {
            updateStyle((JMenu)e.getSource());
        }
    }
}
