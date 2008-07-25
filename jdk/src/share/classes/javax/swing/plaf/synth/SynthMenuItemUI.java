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

import javax.swing.plaf.basic.BasicHTML;
import java.awt.*;
import java.awt.event.*;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;

import javax.swing.*;
import javax.swing.event.*;
import javax.swing.border.*;
import javax.swing.plaf.*;
import javax.swing.plaf.basic.*;
import javax.swing.text.View;
import sun.swing.plaf.synth.*;
import sun.swing.SwingUtilities2;


/**
 * Synth's MenuItemUI.
 *
 * @author Georges Saab
 * @author David Karlton
 * @author Arnaud Weber
 * @author Fredrik Lagerblad
 */
class SynthMenuItemUI extends BasicMenuItemUI implements
                                   PropertyChangeListener, SynthUI {
    private SynthStyle style;
    private SynthStyle accStyle;

    private String acceleratorDelimiter;

    public static ComponentUI createUI(JComponent c) {
        return new SynthMenuItemUI();
    }

    //
    // The next handful of static methods are used by both SynthMenuUI
    // and SynthMenuItemUI. This is necessitated by SynthMenuUI not
    // extending SynthMenuItemUI.
    //

    /*
     * All JMenuItems (and JMenus) include enough space for the insets
     * plus one or more elements.  When we say "icon(s)" below, we mean
     * "check/radio indicator and/or user icon."  If both are defined for
     * a given menu item, then in a LTR orientation the check/radio indicator
     * is on the left side followed by the user icon to the right; it is
     * just the opposite in a RTL orientation.
     *
     * Cases to consider for SynthMenuItemUI (visualized here in a
     * LTR orientation; the RTL case would be reversed):
     *                text
     *      icon(s) + text
     *      icon(s) + text + accelerator
     *                text + accelerator
     *
     * Cases to consider for SynthMenuUI (again visualized here in a
     * LTR orientation):
     *                text       + arrow
     *   (user)icon + text       + arrow
     *
     * Note that in the above scenarios, accelerator and arrow icon are
     * mutually exclusive.  This means that if a popup menu contains a mix
     * of JMenus and JMenuItems, we only need to allow enough space for
     * max(maxAccelerator, maxArrow), and both accelerators and arrow icons
     * can occupy the same "column" of space in the menu.
     *
     * A quick note about how preferred sizes are calculated... Generally
     * speaking, SynthPopupMenuUI will run through the list of its children
     * (from top to bottom) and ask each for its preferred size.  Each menu
     * item will add up the max width of each element (icons, text,
     * accelerator spacing, accelerator text or arrow icon) encountered thus
     * far, so by the time all menu items have been calculated, we will
     * know the maximum (preferred) menu item size for that popup menu.
     * Later when it comes time to paint each menu item, we can use those
     * same accumulated max element sizes in order to layout the item.
     */
    static Dimension getPreferredMenuItemSize(SynthContext context,
           SynthContext accContext, JComponent c,
           Icon checkIcon, Icon arrowIcon, int defaultTextIconGap,
           String acceleratorDelimiter) {
        JMenuItem b = (JMenuItem) c;
        Icon icon = b.getIcon();
        String text = b.getText();
        KeyStroke accelerator =  b.getAccelerator();
        String acceleratorText = "";

        if (accelerator != null) {
            int modifiers = accelerator.getModifiers();
            if (modifiers > 0) {
                acceleratorText = KeyEvent.getKeyModifiersText(modifiers);
                acceleratorText += acceleratorDelimiter;
            }
            int keyCode = accelerator.getKeyCode();
            if (keyCode != 0) {
                acceleratorText += KeyEvent.getKeyText(keyCode);
            } else {
                acceleratorText += accelerator.getKeyChar();
            }
        }

        Font font = context.getStyle().getFont(context);
        FontMetrics fm = b.getFontMetrics(font);
        FontMetrics fmAccel = b.getFontMetrics(accContext.getStyle().
                                               getFont(accContext));

        resetRects();

        layoutMenuItem(
                  context, fm, accContext, text, fmAccel, acceleratorText,
                  icon, checkIcon, arrowIcon, b.getVerticalAlignment(),
                  b.getHorizontalAlignment(), b.getVerticalTextPosition(),
                  b.getHorizontalTextPosition(), viewRect, iconRect, textRect,
                  acceleratorRect, checkIconRect, arrowIconRect,
                  text == null ? 0 : defaultTextIconGap, defaultTextIconGap);

        r.setBounds(textRect);

        int totalIconWidth = 0;
        int maxIconHeight = 0;
        if (icon != null) {
            // Add in the user icon
            totalIconWidth += iconRect.width;
            if (textRect.width > 0) {
                // Allow for some room between the user icon and the text
                totalIconWidth += defaultTextIconGap;
            }
            maxIconHeight = Math.max(iconRect.height, maxIconHeight);
        }
        if (checkIcon != null) {
            // Add in the checkIcon
            totalIconWidth += checkIconRect.width;
            if (textRect.width > 0 || icon != null) {
                // Allow for some room between the check/radio indicator
                // and the text (or user icon, if both are specified)
                totalIconWidth += defaultTextIconGap;
            }
            maxIconHeight = Math.max(checkIconRect.height, maxIconHeight);
        }

        int arrowWidth = 0;
        if (arrowIcon != null) {
            // Add in the arrowIcon
            arrowWidth += defaultTextIconGap;
            arrowWidth += arrowIconRect.width;
            maxIconHeight = Math.max(arrowIconRect.height, maxIconHeight);
        }

        int accelSpacing = 0;
        if (acceleratorRect.width > 0) {
            // Allow for some room between the text and the accelerator
            accelSpacing += 4*defaultTextIconGap;
        }

        // Take text and all icons into account when determining height
        r.height = Math.max(r.height, maxIconHeight);

        // To make the accelerator texts appear in a column,
        // find the widest MenuItem text and the widest accelerator text.

        // Get the parent, which stores the information.
        Container parent = b.getParent();

        if (parent instanceof JPopupMenu) {
            SynthPopupMenuUI popupUI = (SynthPopupMenuUI)SynthLookAndFeel.
                             getUIOfType(((JPopupMenu)parent).getUI(),
                                         SynthPopupMenuUI.class);

            if (popupUI != null) {
                // This gives us the widest MenuItem text encountered thus
                // far in the parent JPopupMenu
                r.width = popupUI.adjustTextWidth(r.width);

                // Add in the widest icon (includes both user and
                // check/radio icons) encountered thus far
                r.width += popupUI.adjustIconWidth(totalIconWidth);

                // Add in the widest text/accelerator spacing
                // encountered thus far
                r.width += popupUI.adjustAccelSpacingWidth(accelSpacing);

                // Add in the widest accelerator text (or arrow)
                // encountered thus far (at least one of these values
                // will always be zero, so we combine them here to
                // avoid double counting)
                int totalAccelOrArrow = acceleratorRect.width + arrowWidth;
                r.width += popupUI.adjustAcceleratorWidth(totalAccelOrArrow);
            }
        }
        else if (parent != null && !(b instanceof JMenu &&
                                     ((JMenu)b).isTopLevelMenu())) {
            r.width +=
                totalIconWidth + accelSpacing +
                acceleratorRect.width + arrowWidth;
        }

        Insets insets = b.getInsets();
        if(insets != null) {
            r.width += insets.left + insets.right;
            r.height += insets.top + insets.bottom;
        }

        // if the width is even, bump it up one. This is critical
        // for the focus dash line to draw properly
        if(r.width%2 == 0) {
            r.width++;
        }

        // if the height is even, bump it up one. This is critical
        // for the text to center properly
        if(r.height%2 == 0) {
            r.height++;
        }
        return r.getSize();
    }

    static void paint(SynthContext context, SynthContext accContext,
                      Graphics g, Icon checkIcon, Icon arrowIcon,
                      String acceleratorDelimiter,
                      int defaultTextIconGap) {
        JComponent c = context.getComponent();
        JMenuItem b = (JMenuItem)c;
        ButtonModel model = b.getModel();
        Insets i = b.getInsets();

        resetRects();

        viewRect.setBounds(0, 0, b.getWidth(), b.getHeight());

        viewRect.x += i.left;
        viewRect.y += i.top;
        viewRect.width -= (i.right + viewRect.x);
        viewRect.height -= (i.bottom + viewRect.y);

        SynthStyle style = context.getStyle();
        Font f = style.getFont(context);
        g.setFont(f);
        FontMetrics fm = SwingUtilities2.getFontMetrics(c, g, f);
        FontMetrics accFM = SwingUtilities2.getFontMetrics(c, g,
                                 accContext.getStyle().
                                             getFont(accContext));

        // get Accelerator text
        KeyStroke accelerator =  b.getAccelerator();
        String acceleratorText = "";
        if (accelerator != null) {
            int modifiers = accelerator.getModifiers();
            if (modifiers > 0) {
                acceleratorText = KeyEvent.getKeyModifiersText(modifiers);
                acceleratorText += acceleratorDelimiter;
            }

            int keyCode = accelerator.getKeyCode();
            if (keyCode != 0) {
                acceleratorText += KeyEvent.getKeyText(keyCode);
            } else {
                acceleratorText += accelerator.getKeyChar();
            }
        }

        // Layout the text and icon
        String text = layoutMenuItem(context, fm, accContext,
            b.getText(), accFM, acceleratorText, b.getIcon(),
            checkIcon, arrowIcon,
            b.getVerticalAlignment(), b.getHorizontalAlignment(),
            b.getVerticalTextPosition(), b.getHorizontalTextPosition(),
            viewRect, iconRect, textRect, acceleratorRect,
            checkIconRect, arrowIconRect,
            b.getText() == null ? 0 : defaultTextIconGap,
            defaultTextIconGap
        );

        // Paint the Check
        if (checkIcon != null) {
            SynthIcon.paintIcon(checkIcon, context, g, checkIconRect.x,
                    checkIconRect.y, checkIconRect.width, checkIconRect.height);
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
                SynthIcon.paintIcon(icon, context, g, iconRect.x,
                    iconRect.y, iconRect.width, iconRect.height);
            }
        }

        // Draw the Text
        if(text != null) {
            View v = (View) c.getClientProperty(BasicHTML.propertyKey);
            if (v != null) {
                v.paint(g, textRect);
            } else {
                g.setColor(style.getColor(context, ColorType.TEXT_FOREGROUND));
                g.setFont(style.getFont(context));
                style.getGraphicsUtils(context).paintText(context, g, text,
                        textRect.x, textRect.y, b.getDisplayedMnemonicIndex());
            }
        }

        // Draw the Accelerator Text
        if(acceleratorText != null && !acceleratorText.equals("")) {
            // Get the maxAccWidth from the parent to calculate the offset.
            int accOffset = 0;
            Container parent = b.getParent();
            if (parent != null && parent instanceof JPopupMenu) {
                SynthPopupMenuUI popupUI = (SynthPopupMenuUI)
                                       ((JPopupMenu)parent).getUI();

                // Note that we can only get here for SynthMenuItemUI
                // (not SynthMenuUI) since acceleratorText is defined,
                // so this cast should be safe
                SynthMenuItemUI miUI = (SynthMenuItemUI)
                    SynthLookAndFeel.getUIOfType(b.getUI(),
                                                 SynthMenuItemUI.class);

                if (popupUI != null && miUI != null) {
                    String prop =
                        miUI.getPropertyPrefix() + ".alignAcceleratorText";
                    boolean align = style.getBoolean(context, prop, true);

                    // Calculate the offset, with which the accelerator texts
                    // will be drawn.
                    if (align) {
                        // When align==true and we're in the LTR case,
                        // we add an offset here so that all accelerators
                        // will be left-justified in their own column.
                        int max = popupUI.getMaxAcceleratorWidth();
                        if (max > 0) {
                            accOffset = max - acceleratorRect.width;
                            if (!SynthLookAndFeel.isLeftToRight(c)) {
                                // In the RTL, flip the sign so that all
                                // accelerators will be right-justified.
                                accOffset = -accOffset;
                            }
                        }
                    } //else {
                        // Don't need to do anything special here; in the
                        // LTR case, the accelerator is already justified
                        // against the right edge of the menu (and against
                        // the left edge in the RTL case).
                    //}
                }
            }

            SynthStyle accStyle = accContext.getStyle();

            g.setColor(accStyle.getColor(accContext,
                                         ColorType.TEXT_FOREGROUND));
            g.setFont(accStyle.getFont(accContext));
            accStyle.getGraphicsUtils(accContext).paintText(
                     accContext, g, acceleratorText, acceleratorRect.x -
                     accOffset, acceleratorRect.y, -1);
        }

        // Paint the Arrow
        if (arrowIcon != null) {
            SynthIcon.paintIcon(arrowIcon, context, g, arrowIconRect.x,
                    arrowIconRect.y, arrowIconRect.width, arrowIconRect.height);
        }
    }

    /**
     * Compute and return the location of the icons origin, the
     * location of origin of the text baseline, and a possibly clipped
     * version of the compound labels string.  Locations are computed
     * relative to the viewRect rectangle.
     */

    private static String layoutMenuItem(
        SynthContext context,
        FontMetrics fm,
        SynthContext accContext,
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
        Rectangle viewRect,
        Rectangle iconRect,
        Rectangle textRect,
        Rectangle acceleratorRect,
        Rectangle checkIconRect,
        Rectangle arrowIconRect,
        int textIconGap,
        int menuItemGap
        )
    {
        // If parent is JPopupMenu, get and store it's UI
        SynthPopupMenuUI popupUI = null;
        JComponent b = context.getComponent();
        Container parent = b.getParent();
        if(parent instanceof JPopupMenu) {
            popupUI = (SynthPopupMenuUI)SynthLookAndFeel.
                             getUIOfType(((JPopupMenu)parent).getUI(),
                                         SynthPopupMenuUI.class);
        }

        context.getStyle().getGraphicsUtils(context).layoutText(
                context, fm, text, icon,horizontalAlignment, verticalAlignment,
                horizontalTextPosition, verticalTextPosition, viewRect,
                iconRect, textRect, textIconGap);

        /* Initialize the acceleratorText bounds rectangle textRect.  If a null
         * or and empty String was specified we substitute "" here
         * and use 0,0,0,0 for acceleratorTextRect.
         */
        if( (acceleratorText == null) || acceleratorText.equals("") ) {
            acceleratorRect.width = acceleratorRect.height = 0;
            acceleratorText = "";
        }
        else {
            SynthStyle style = accContext.getStyle();
            acceleratorRect.width = style.getGraphicsUtils(accContext).
                    computeStringWidth(accContext, fmAccel.getFont(), fmAccel,
                                       acceleratorText);
            acceleratorRect.height = fmAccel.getHeight();
        }

        // Initialize the checkIcon bounds rectangle width & height.
        if (checkIcon != null) {
            checkIconRect.width = SynthIcon.getIconWidth(checkIcon,
                                                         context);
            checkIconRect.height = SynthIcon.getIconHeight(checkIcon,
                                                           context);
        }
        else {
            checkIconRect.width = checkIconRect.height = 0;
        }

        // Initialize the arrowIcon bounds rectangle width & height.
        if (arrowIcon != null) {
            arrowIconRect.width = SynthIcon.getIconWidth(arrowIcon,
                                                         context);
            arrowIconRect.height = SynthIcon.getIconHeight(arrowIcon,
                                                           context);
        } else {
            arrowIconRect.width = arrowIconRect.height = 0;
        }

        // Note: layoutText() has already left room for
        // the user icon, so no need to adjust textRect below
        // to account for the user icon.  However, we do have to
        // reposition textRect when the check icon is visible.

        Rectangle labelRect = iconRect.union(textRect);
        if( SynthLookAndFeel.isLeftToRight(context.getComponent()) ) {
            // Position the check and user icons
            iconRect.x = viewRect.x;
            if (checkIcon != null) {
                checkIconRect.x = viewRect.x;
                iconRect.x += menuItemGap + checkIconRect.width;
                textRect.x += menuItemGap + checkIconRect.width;
            }

            // Position the arrow icon
            arrowIconRect.x =
                viewRect.x + viewRect.width - arrowIconRect.width;

            // Position the accelerator text rect
            acceleratorRect.x =
                viewRect.x + viewRect.width - acceleratorRect.width;

            /* Align icons and text horizontally */
            if(popupUI != null) {
                int thisTextOffset = popupUI.adjustTextOffset(textRect.x
                                                              - viewRect.x);
                textRect.x = thisTextOffset + viewRect.x;

                if(icon != null) {
                    // REMIND: The following code currently assumes the
                    // default (TRAILING) horizontalTextPosition, which means
                    // it will always place the icon to the left of the text.
                    // Other values of horizontalTextPosition aren't very
                    // useful for menu items, so we ignore them for now, but
                    // someday we might want to fix this situation.
                    int thisIconOffset =
                        popupUI.adjustIconOffset(iconRect.x - viewRect.x);
                    iconRect.x = thisIconOffset + viewRect.x;
                }
            }
        } else {
            // Position the accelerator text rect
            acceleratorRect.x = viewRect.x;

            // Position the arrow icon
            arrowIconRect.x = viewRect.x;

            // Position the check and user icons
            iconRect.x =
                viewRect.x + viewRect.width - iconRect.width;
            if (checkIcon != null) {
                checkIconRect.x =
                    viewRect.x + viewRect.width - checkIconRect.width;
                textRect.x -= menuItemGap + checkIconRect.width;
                iconRect.x -= menuItemGap + checkIconRect.width;
            }

            /* Align icons and text horizontally */
            if(popupUI != null) {
                int thisTextOffset = viewRect.x + viewRect.width
                                     - textRect.x - textRect.width;
                thisTextOffset = popupUI.adjustTextOffset(thisTextOffset);
                textRect.x = viewRect.x + viewRect.width
                             - thisTextOffset - textRect.width;
                if(icon != null) {
                    // REMIND: The following code currently assumes the
                    // default (TRAILING) horizontalTextPosition, which means
                    // it will always place the icon to the right of the text.
                    // Other values of horizontalTextPosition aren't very
                    // useful for menu items, so we ignore them for now, but
                    // someday we might want to fix this situation.
                    int thisIconOffset = viewRect.x + viewRect.width
                                         - iconRect.x - iconRect.width;
                    thisIconOffset =
                        popupUI.adjustIconOffset(thisIconOffset);
                    iconRect.x = viewRect.x + viewRect.width
                                 - thisIconOffset - iconRect.width;
                }
            }
        }

        // Align the accelerator text and all icons vertically
        // with the center of the label rect.
        int midY = labelRect.y + (labelRect.height/2);
        iconRect.y        = midY - (iconRect.height/2);
        acceleratorRect.y = midY - (acceleratorRect.height/2);
        arrowIconRect.y   = midY - (arrowIconRect.height/2);
        checkIconRect.y   = midY - (checkIconRect.height/2);

        return text;
    }

    // these rects are used for painting and preferredsize calculations.
    // they used to be regenerated constantly.  Now they are reused.
    static Rectangle iconRect = new Rectangle();
    static Rectangle textRect = new Rectangle();
    static Rectangle acceleratorRect = new Rectangle();
    static Rectangle checkIconRect = new Rectangle();
    static Rectangle arrowIconRect = new Rectangle();
    static Rectangle viewRect = new Rectangle(Short.MAX_VALUE,Short.MAX_VALUE);
    static Rectangle r = new Rectangle();

    private static void resetRects() {
        iconRect.setBounds(0, 0, 0, 0);
        textRect.setBounds(0, 0, 0, 0);
        acceleratorRect.setBounds(0, 0, 0, 0);
        checkIconRect.setBounds(0, 0, 0, 0);
        arrowIconRect.setBounds(0, 0, 0, 0);
        viewRect.setBounds(0,0,Short.MAX_VALUE, Short.MAX_VALUE);
        r.setBounds(0, 0, 0, 0);
    }


    protected void installDefaults() {
        updateStyle(menuItem);
    }

    protected void installListeners() {
        super.installListeners();
        menuItem.addPropertyChangeListener(this);
    }

    private void updateStyle(JMenuItem mi) {
        SynthContext context = getContext(mi, ENABLED);
        SynthStyle oldStyle = style;

        style = SynthLookAndFeel.updateStyle(context, this);
        if (oldStyle != style) {
            String prefix = getPropertyPrefix();

            Object value = style.get(context, prefix + ".textIconGap");
            if (value != null) {
                LookAndFeel.installProperty(mi, "iconTextGap", value);
            }
            defaultTextIconGap = mi.getIconTextGap();

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

            arrowIcon = style.getIcon(context, prefix + ".arrowIcon");

            checkIcon = style.getIcon(context, prefix + ".checkIcon");
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

    protected void uninstallListeners() {
        super.uninstallListeners();
        menuItem.removePropertyChangeListener(this);
    }

    public SynthContext getContext(JComponent c) {
        return getContext(c, getComponentState(c));
    }

    SynthContext getContext(JComponent c, int state) {
        return SynthContext.getContext(SynthContext.class, c,
                    SynthLookAndFeel.getRegion(c), style, state);
    }

    public SynthContext getContext(JComponent c, Region region) {
        return getContext(c, region, getComponentState(c, region));
    }

    private SynthContext getContext(JComponent c, Region region, int state) {
        return SynthContext.getContext(SynthContext.class, c,
                                       region, accStyle, state);
    }

    private Region getRegion(JComponent c) {
        return SynthLookAndFeel.getRegion(c);
    }

    private int getComponentState(JComponent c) {
        int state;

        if (!c.isEnabled()) {
            state = DISABLED;
        }
        else if (menuItem.isArmed()) {
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

    protected Dimension getPreferredMenuItemSize(JComponent c,
                                                     Icon checkIcon,
                                                     Icon arrowIcon,
                                                     int defaultTextIconGap) {
        SynthContext context = getContext(c);
        SynthContext accContext = getContext(c, Region.MENU_ITEM_ACCELERATOR);
        Dimension value = getPreferredMenuItemSize(context, accContext,
                  c, checkIcon, arrowIcon, defaultTextIconGap,
                  acceleratorDelimiter);
        context.dispose();
        accContext.dispose();
        return value;
    }


    public void update(Graphics g, JComponent c) {
        SynthContext context = getContext(c);

        SynthLookAndFeel.update(context, g);
        paintBackground(context, g, c);
        paint(context, g);
        context.dispose();
    }

    public void paint(Graphics g, JComponent c) {
        SynthContext context = getContext(c);

        paint(context, g);
        context.dispose();
    }

    protected void paint(SynthContext context, Graphics g) {
        SynthContext accContext = getContext(menuItem,
                                             Region.MENU_ITEM_ACCELERATOR);

        // Refetch the appropriate check indicator for the current state
        String prefix = getPropertyPrefix();
        Icon checkIcon = style.getIcon(context, prefix + ".checkIcon");
        Icon arrowIcon = style.getIcon(context, prefix + ".arrowIcon");
        paint(context, accContext, g, checkIcon, arrowIcon,
              acceleratorDelimiter, defaultTextIconGap);
        accContext.dispose();
    }

    void paintBackground(SynthContext context, Graphics g, JComponent c) {
        context.getPainter().paintMenuItemBackground(context, g, 0, 0,
                                                c.getWidth(), c.getHeight());
    }

    public void paintBorder(SynthContext context, Graphics g, int x,
                            int y, int w, int h) {
        context.getPainter().paintMenuItemBorder(context, g, x, y, w, h);
    }

    public void propertyChange(PropertyChangeEvent e) {
        if (SynthLookAndFeel.shouldUpdateStyle(e)) {
            updateStyle((JMenuItem)e.getSource());
        }
    }
}
