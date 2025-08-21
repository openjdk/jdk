/*
 * Copyright (c) 1997, 2025, Oracle and/or its affiliates. All rights reserved.
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

import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Insets;
import java.awt.Rectangle;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.Enumeration;

import javax.swing.AbstractButton;
import javax.swing.ButtonGroup;
import javax.swing.ButtonModel;
import javax.swing.DefaultButtonModel;
import javax.swing.Icon;
import javax.swing.JComponent;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.UIManager;
import javax.swing.plaf.ComponentUI;
import javax.swing.plaf.UIResource;
import javax.swing.plaf.basic.BasicMenuItemUI;

import com.sun.java.swing.SwingUtilities3;
import com.sun.java.swing.plaf.windows.TMSchema.Part;
import com.sun.java.swing.plaf.windows.TMSchema.State;
import com.sun.java.swing.plaf.windows.XPStyle.Skin;
import sun.swing.MenuItemCheckIconFactory;
import sun.swing.MenuItemLayoutHelper;
import sun.swing.MnemonicHandler;
import sun.swing.SwingUtilities2;

/**
 * Windows rendition of the component.
 *
 * @author Igor Kushnirskiy
 */
public final class WindowsMenuItemUI extends BasicMenuItemUI {
    /**
     * The instance of {@code PropertyChangeListener}.
     */
    private PropertyChangeListener changeListener;
    private static Color disabledForeground;
    private static Color acceleratorSelectionForeground;
    private static Color acceleratorForeground;

    final WindowsMenuItemUIAccessor accessor =
        new  WindowsMenuItemUIAccessor() {

            @Override
            public JMenuItem getMenuItem() {
                return menuItem;
            }

            public State getState(JMenuItem menuItem) {
                return WindowsMenuItemUI.getState(this, menuItem);
            }

            @Override
            public Part getPart(JMenuItem menuItem) {
                return WindowsMenuItemUI.getPart(this, menuItem);
            }
    };
    public static ComponentUI createUI(JComponent c) {
        return new WindowsMenuItemUI();
    }

    private void updateCheckIcon() {
        String prefix = getPropertyPrefix();

        if (checkIcon == null ||
                checkIcon instanceof UIResource) {
            checkIcon = UIManager.getIcon(prefix + ".checkIcon");
            //In case of column layout, .checkIconFactory is defined for this UI,
            //the icon is compatible with it and useCheckAndArrow() is true,
            //then the icon is handled by the checkIcon.
            boolean isColumnLayout = MenuItemLayoutHelper.isColumnLayout(
                    menuItem.getComponentOrientation().isLeftToRight(), menuItem);
            if (isColumnLayout) {
                MenuItemCheckIconFactory iconFactory =
                        (MenuItemCheckIconFactory) UIManager.get(prefix
                                + ".checkIconFactory");
                if (iconFactory != null
                        && MenuItemLayoutHelper.useCheckAndArrow(menuItem)
                        && iconFactory.isCompatible(checkIcon, prefix)) {
                    checkIcon = iconFactory.getIcon(menuItem);
                }
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void installListeners() {
        super.installListeners();
        changeListener = new PropertyChangeListener() {
            @Override
            public void propertyChange(PropertyChangeEvent e) {
                String name = e.getPropertyName();
                if (name == "horizontalTextPosition") {
                    updateCheckIcon();
                }
            }
        };
        menuItem.addPropertyChangeListener(changeListener);
    }

    protected void installDefaults() {
        super.installDefaults();
        String prefix = getPropertyPrefix();

        if (acceleratorSelectionForeground == null ||
                acceleratorSelectionForeground instanceof UIResource) {
            acceleratorSelectionForeground =
                    UIManager.getColor(prefix + ".acceleratorSelectionForeground");
        }
        if (acceleratorForeground == null ||
                acceleratorForeground instanceof UIResource) {
            acceleratorForeground =
                    UIManager.getColor(prefix + ".acceleratorForeground");
        }
        if (disabledForeground == null ||
                disabledForeground instanceof UIResource) {
            disabledForeground =
                    UIManager.getColor(prefix + ".disabledForeground");
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void uninstallListeners() {
        super.uninstallListeners();
        if (changeListener != null) {
            menuItem.removePropertyChangeListener(changeListener);
        }
        changeListener = null;
    }

    private static void applyInsets(Rectangle rect, Insets insets) {
        SwingUtilities3.applyInsets(rect, insets);
    }

    private static void paintCheckIcon(Graphics g, MenuItemLayoutHelper lh,
                                MenuItemLayoutHelper.LayoutResult lr,
                                Color holdc, Color foreground) {
        SwingUtilities3.paintCheckIcon(g, lh, lr, holdc, foreground);
    }

    private static void paintIcon(Graphics g, MenuItemLayoutHelper lh,
                           MenuItemLayoutHelper.LayoutResult lr, Color holdc) {
        SwingUtilities3.paintIcon(g, lh, lr, holdc);
    }

    private static void paintAccText(Graphics g, MenuItemLayoutHelper lh,
                              MenuItemLayoutHelper.LayoutResult lr) {
        SwingUtilities3.setDisabledForeground(disabledForeground);
        SwingUtilities3.setAcceleratorSelectionForeground(
                        acceleratorSelectionForeground);
        SwingUtilities3.setAcceleratorForeground(acceleratorForeground);
        SwingUtilities3.paintAccText(g, lh, lr);
    }

    private static void paintArrowIcon(Graphics g, MenuItemLayoutHelper lh,
                                MenuItemLayoutHelper.LayoutResult lr,
                                Color foreground) {
        SwingUtilities3.paintArrowIcon(g, lh, lr, foreground);
    }

    protected void paintMenuItem(Graphics g, JComponent c,
                                 Icon checkIcon, Icon arrowIcon,
                                 Color background, Color foreground,
                                 int defaultTextIconGap) {
        if (WindowsMenuItemUI.isVistaPainting()) {
            WindowsMenuItemUI.paintMenuItem(accessor, g, c, checkIcon,
                                            arrowIcon, background, foreground,
                                            defaultTextIconGap, menuItem,
                                            getPropertyPrefix());
            return;
        }
        super.paintMenuItem(g, c, checkIcon, arrowIcon, background,
                foreground, defaultTextIconGap);
    }

    static void paintMenuItem(WindowsMenuItemUIAccessor accessor, Graphics g,
                              JComponent c, Icon checkIcon, Icon arrowIcon,
                              Color background, Color foreground,
                              int defaultTextIconGap, JMenuItem menuItem, String prefix) {
        // Save original graphics font and color
        Font holdf = g.getFont();
        Color holdc = g.getColor();

        JMenuItem mi = (JMenuItem) c;
        g.setFont(mi.getFont());

        Rectangle viewRect = new Rectangle(0, 0, mi.getWidth(), mi.getHeight());
        applyInsets(viewRect, mi.getInsets());

        String acceleratorDelimiter =
                UIManager.getString("MenuItem.acceleratorDelimiter");
        if (acceleratorDelimiter == null) { acceleratorDelimiter = "+"; }
        Font acceleratorFont = UIManager.getFont("MenuItem.acceleratorFont");
        if (acceleratorFont == null) {
            acceleratorFont = UIManager.getFont("MenuItem.font");
        }

        MenuItemLayoutHelper lh = new MenuItemLayoutHelper(mi, checkIcon,
                arrowIcon, viewRect, defaultTextIconGap, acceleratorDelimiter,
                mi.getComponentOrientation().isLeftToRight(), mi.getFont(),
                acceleratorFont, MenuItemLayoutHelper.useCheckAndArrow(menuItem),
                prefix);
        MenuItemLayoutHelper.LayoutResult lr = lh.layoutMenuItem();

        paintBackground(accessor, g, mi, background);
        paintCheckIcon(g, lh, lr, holdc, foreground);
        paintIcon(g, lh, lr, holdc);

        if (lh.getCheckIcon() != null && lh.useCheckAndArrow()) {
            Rectangle rect = lr.getTextRect();

            rect.x += lh.getAfterCheckIconGap();

            lr.setTextRect(rect);
        }
        if (!lh.getText().isEmpty()) {
            if (lh.getHtmlView() != null) {
                // Text is HTML
                lh.getHtmlView().paint(g, lr.getTextRect());
            } else {
                // Text isn't HTML
                paintText(accessor, g, lh.getMenuItem(),
                          lr.getTextRect(), lh.getText());
            }
        }
        if (lh.getCheckIcon() != null && lh.useCheckAndArrow()) {
            Rectangle rect = lr.getAccRect();
            rect.x += lh.getAfterCheckIconGap();
            lr.setAccRect(rect);
        }
        paintAccText(g, lh, lr);
        paintArrowIcon(g, lh, lr, foreground);

        // Restore original graphics font and color
        g.setColor(holdc);
        g.setFont(holdf);
    }

    /**
     * Method which renders the text of the current menu item.
     *
     * @param g Graphics context
     * @param menuItem Current menu item to render
     * @param textRect Bounding rectangle to render the text.
     * @param text String to render
     */
    @Override
    protected void paintText(Graphics g, JMenuItem menuItem,
                             Rectangle textRect, String text) {
        if (WindowsMenuItemUI.isVistaPainting()) {
            WindowsMenuItemUI.paintText(accessor, g, menuItem, textRect, text);
            return;
        }
        ButtonModel model = menuItem.getModel();
        Color oldColor = g.getColor();

        if(model.isEnabled() &&
            (model.isArmed() || (menuItem instanceof JMenu &&
             model.isSelected()))) {
            g.setColor(selectionForeground); // Uses protected field.
        }

        WindowsGraphicsUtils.paintText(g, menuItem, textRect, text, 0);

        g.setColor(oldColor);
    }

    @Override
    protected void paintBackground(Graphics g, JMenuItem menuItem,
            Color bgColor) {
        if (WindowsMenuItemUI.isVistaPainting()) {
            WindowsMenuItemUI.paintBackground(accessor, g, menuItem, bgColor);
            return;
        }
        super.paintBackground(g, menuItem, bgColor);
    }

    static void paintBackground(WindowsMenuItemUIAccessor menuItemUI,
            Graphics g, JMenuItem menuItem, Color bgColor) {
        XPStyle xp = XPStyle.getXP();
        assert isVistaPainting(xp);
        if (isVistaPainting(xp)) {
            int menuWidth = menuItem.getWidth();
            int menuHeight = menuItem.getHeight();
            if (menuItem.isOpaque()) {
                Color oldColor = g.getColor();
                g.setColor(menuItem.getBackground());
                g.fillRect(0,0, menuWidth, menuHeight);
                g.setColor(oldColor);
            }
            Part part = menuItemUI.getPart(menuItem);
            Skin skin = xp.getSkin(menuItem, part);
            skin.paintSkin(g, 0 , 0,
                menuWidth,
                menuHeight,
                menuItemUI.getState(menuItem));
        }
    }

    static void paintText(WindowsMenuItemUIAccessor menuItemUI, Graphics g,
                                JMenuItem menuItem, Rectangle textRect,
                                String text) {
        assert isVistaPainting();
        if (isVistaPainting()) {
            State state = menuItemUI.getState(menuItem);

            /* part of it copied from WindowsGraphicsUtils.java */
            FontMetrics fm = SwingUtilities2.getFontMetrics(menuItem, g);
            int mnemIndex = menuItem.getDisplayedMnemonicIndex();
            // W2K Feature: Check to see if the Underscore should be rendered.
            if (MnemonicHandler.isMnemonicHidden()) {
                mnemIndex = -1;
            }
            WindowsGraphicsUtils.paintXPText(menuItem,
                menuItemUI.getPart(menuItem), state,
                g, textRect.x,
                textRect.y + fm.getAscent(),
                text, mnemIndex);
        }
    }

    static State getState(WindowsMenuItemUIAccessor menuItemUI, JMenuItem menuItem) {
        State state;
        ButtonModel model = menuItem.getModel();
        if (model.isArmed()) {
            state = (model.isEnabled()) ? State.HOT : State.DISABLEDHOT;
        } else {
            state = (model.isEnabled()) ? State.NORMAL : State.DISABLED;
        }
        return state;
    }

    static Part getPart(WindowsMenuItemUIAccessor menuItemUI, JMenuItem menuItem) {
        return Part.MP_POPUPITEM;
    }

    /*
     * TODO idk can we use XPStyle.isVista?
     * is it possible that in some theme some Vista parts are not defined while
     * others are?
     */
    static boolean isVistaPainting(final XPStyle xp) {
        return xp != null && xp.isSkinDefined(null, Part.MP_POPUPITEM);
    }

    static boolean isVistaPainting() {
        return isVistaPainting(XPStyle.getXP());
    }
}
