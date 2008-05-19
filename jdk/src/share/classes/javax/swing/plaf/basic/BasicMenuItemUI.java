/*
 * Copyright 1997-2006 Sun Microsystems, Inc.  All Rights Reserved.
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

package javax.swing.plaf.basic;

import sun.swing.MenuItemCheckIconFactory;
import sun.swing.SwingUtilities2;
import static sun.swing.SwingUtilities2.BASICMENUITEMUI_MAX_TEXT_OFFSET;
import java.awt.*;
import java.awt.event.*;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;

import javax.swing.*;
import javax.swing.event.*;
import javax.swing.border.*;
import javax.swing.plaf.*;
import javax.swing.text.View;

import sun.swing.UIAction;
import sun.swing.StringUIClientPropertyKey;

/**
 * BasicMenuItem implementation
 *
 * @author Georges Saab
 * @author David Karlton
 * @author Arnaud Weber
 * @author Fredrik Lagerblad
 */
public class BasicMenuItemUI extends MenuItemUI
{
    protected JMenuItem menuItem = null;
    protected Color selectionBackground;
    protected Color selectionForeground;
    protected Color disabledForeground;
    protected Color acceleratorForeground;
    protected Color acceleratorSelectionForeground;
    private   String acceleratorDelimiter;

    protected int defaultTextIconGap;
    protected Font acceleratorFont;

    protected MouseInputListener mouseInputListener;
    protected MenuDragMouseListener menuDragMouseListener;
    protected MenuKeyListener menuKeyListener;
    /**
     * <code>PropertyChangeListener</code> returned from
     * <code>createPropertyChangeListener</code>. You should not
     * need to access this field, rather if you want to customize the
     * <code>PropertyChangeListener</code> override
     * <code>createPropertyChangeListener</code>.
     *
     * @since 1.6
     * @see #createPropertyChangeListener
     */
    protected PropertyChangeListener propertyChangeListener;
    // BasicMenuUI also uses this.
    Handler handler;

    protected Icon arrowIcon = null;
    protected Icon checkIcon = null;

    protected boolean oldBorderPainted;

    /* diagnostic aids -- should be false for production builds. */
    private static final boolean TRACE =   false; // trace creates and disposes

    private static final boolean VERBOSE = false; // show reuse hits/misses
    private static final boolean DEBUG =   false;  // show bad params, misc.

    // Allows to reuse layoutInfo object.
    // Shouldn't be used directly. Use getLayoutInfo() instead.
    private final transient LayoutInfo layoutInfo = new LayoutInfo();

    /* Client Property keys for calculation of maximal widths */
    static final StringUIClientPropertyKey MAX_ARROW_WIDTH =
                        new StringUIClientPropertyKey("maxArrowWidth");
    static final StringUIClientPropertyKey MAX_CHECK_WIDTH =
                        new StringUIClientPropertyKey("maxCheckWidth");
    static final StringUIClientPropertyKey MAX_ICON_WIDTH =
                        new StringUIClientPropertyKey("maxIconWidth");
    static final StringUIClientPropertyKey MAX_TEXT_WIDTH =
                        new StringUIClientPropertyKey("maxTextWidth");
    static final StringUIClientPropertyKey MAX_ACC_WIDTH =
                        new StringUIClientPropertyKey("maxAccWidth");
    static final StringUIClientPropertyKey MAX_LABEL_WIDTH =
                        new StringUIClientPropertyKey("maxLabelWidth");

    static void loadActionMap(LazyActionMap map) {
        // NOTE: BasicMenuUI also calls into this method.
        map.put(new Actions(Actions.CLICK));
        BasicLookAndFeel.installAudioActionMap(map);
    }

    public static ComponentUI createUI(JComponent c) {
        return new BasicMenuItemUI();
    }

    public void installUI(JComponent c) {
        menuItem = (JMenuItem) c;

        installDefaults();
        installComponents(menuItem);
        installListeners();
        installKeyboardActions();
    }


    protected void installDefaults() {
        String prefix = getPropertyPrefix();

        acceleratorFont = UIManager.getFont("MenuItem.acceleratorFont");

        Object opaque = UIManager.get(getPropertyPrefix() + ".opaque");
        if (opaque != null) {
            LookAndFeel.installProperty(menuItem, "opaque", opaque);
        }
        else {
            LookAndFeel.installProperty(menuItem, "opaque", Boolean.TRUE);
        }
        if(menuItem.getMargin() == null ||
           (menuItem.getMargin() instanceof UIResource)) {
            menuItem.setMargin(UIManager.getInsets(prefix + ".margin"));
        }

        LookAndFeel.installProperty(menuItem, "iconTextGap", Integer.valueOf(4));
        defaultTextIconGap = menuItem.getIconTextGap();

        LookAndFeel.installBorder(menuItem, prefix + ".border");
        oldBorderPainted = menuItem.isBorderPainted();
        LookAndFeel.installProperty(menuItem, "borderPainted",
                                    UIManager.get(prefix + ".borderPainted"));
        LookAndFeel.installColorsAndFont(menuItem,
                                         prefix + ".background",
                                         prefix + ".foreground",
                                         prefix + ".font");

        // MenuItem specific defaults
        if (selectionBackground == null ||
            selectionBackground instanceof UIResource) {
            selectionBackground =
                UIManager.getColor(prefix + ".selectionBackground");
        }
        if (selectionForeground == null ||
            selectionForeground instanceof UIResource) {
            selectionForeground =
                UIManager.getColor(prefix + ".selectionForeground");
        }
        if (disabledForeground == null ||
            disabledForeground instanceof UIResource) {
            disabledForeground =
                UIManager.getColor(prefix + ".disabledForeground");
        }
        if (acceleratorForeground == null ||
            acceleratorForeground instanceof UIResource) {
            acceleratorForeground =
                UIManager.getColor(prefix + ".acceleratorForeground");
        }
        if (acceleratorSelectionForeground == null ||
            acceleratorSelectionForeground instanceof UIResource) {
            acceleratorSelectionForeground =
                UIManager.getColor(prefix + ".acceleratorSelectionForeground");
        }
        // Get accelerator delimiter
        acceleratorDelimiter =
            UIManager.getString("MenuItem.acceleratorDelimiter");
        if (acceleratorDelimiter == null) { acceleratorDelimiter = "+"; }
        // Icons
        if (arrowIcon == null ||
            arrowIcon instanceof UIResource) {
            arrowIcon = UIManager.getIcon(prefix + ".arrowIcon");
        }
        if (checkIcon == null ||
            checkIcon instanceof UIResource) {
            checkIcon = UIManager.getIcon(prefix + ".checkIcon");
            //In case of column layout, .checkIconFactory is defined for this UI,
            //the icon is compatible with it and useCheckAndArrow() is true,
            //then the icon is handled by the checkIcon.
            boolean isColumnLayout = LayoutInfo.isColumnLayout(
                    BasicGraphicsUtils.isLeftToRight(menuItem), menuItem);
            if (isColumnLayout) {
                MenuItemCheckIconFactory iconFactory =
                    (MenuItemCheckIconFactory) UIManager.get(prefix
                        + ".checkIconFactory");
                if (iconFactory != null && useCheckAndArrow()
                        && iconFactory.isCompatible(checkIcon, prefix)) {
                    checkIcon = iconFactory.getIcon(menuItem);
                }
            }
        }
    }

    /**
     * @since 1.3
     */
    protected void installComponents(JMenuItem menuItem){
        BasicHTML.updateRenderer(menuItem, menuItem.getText());
    }

    protected String getPropertyPrefix() {
        return "MenuItem";
    }

    protected void installListeners() {
        if ((mouseInputListener = createMouseInputListener(menuItem)) != null) {
            menuItem.addMouseListener(mouseInputListener);
            menuItem.addMouseMotionListener(mouseInputListener);
        }
        if ((menuDragMouseListener = createMenuDragMouseListener(menuItem)) != null) {
            menuItem.addMenuDragMouseListener(menuDragMouseListener);
        }
        if ((menuKeyListener = createMenuKeyListener(menuItem)) != null) {
            menuItem.addMenuKeyListener(menuKeyListener);
        }
        if ((propertyChangeListener = createPropertyChangeListener(menuItem)) != null) {
            menuItem.addPropertyChangeListener(propertyChangeListener);
        }
    }

    protected void installKeyboardActions() {
        installLazyActionMap();
        updateAcceleratorBinding();
    }

    void installLazyActionMap() {
        LazyActionMap.installLazyActionMap(menuItem, BasicMenuItemUI.class,
                                           getPropertyPrefix() + ".actionMap");
    }

    public void uninstallUI(JComponent c) {
        menuItem = (JMenuItem)c;
        uninstallDefaults();
        uninstallComponents(menuItem);
        uninstallListeners();
        uninstallKeyboardActions();


        // Remove values from the parent's Client Properties.
        JComponent p = getMenuItemParent(menuItem);
        if(p != null) {
            p.putClientProperty(BasicMenuItemUI.MAX_ARROW_WIDTH, null );
            p.putClientProperty(BasicMenuItemUI.MAX_CHECK_WIDTH, null );
            p.putClientProperty(BasicMenuItemUI.MAX_ACC_WIDTH, null );
            p.putClientProperty(BasicMenuItemUI.MAX_TEXT_WIDTH, null );
            p.putClientProperty(BasicMenuItemUI.MAX_ICON_WIDTH, null );
            p.putClientProperty(BasicMenuItemUI.MAX_LABEL_WIDTH, null );
            p.putClientProperty(BASICMENUITEMUI_MAX_TEXT_OFFSET, null );
        }

        menuItem = null;
    }


    protected void uninstallDefaults() {
        LookAndFeel.uninstallBorder(menuItem);
        LookAndFeel.installProperty(menuItem, "borderPainted", oldBorderPainted);
        if (menuItem.getMargin() instanceof UIResource)
            menuItem.setMargin(null);
        if (arrowIcon instanceof UIResource)
            arrowIcon = null;
        if (checkIcon instanceof UIResource)
            checkIcon = null;
    }

    /**
     * @since 1.3
     */
    protected void uninstallComponents(JMenuItem menuItem){
        BasicHTML.updateRenderer(menuItem, "");
    }

    protected void uninstallListeners() {
        if (mouseInputListener != null) {
            menuItem.removeMouseListener(mouseInputListener);
            menuItem.removeMouseMotionListener(mouseInputListener);
        }
        if (menuDragMouseListener != null) {
            menuItem.removeMenuDragMouseListener(menuDragMouseListener);
        }
        if (menuKeyListener != null) {
            menuItem.removeMenuKeyListener(menuKeyListener);
        }
        if (propertyChangeListener != null) {
            menuItem.removePropertyChangeListener(propertyChangeListener);
        }

        mouseInputListener = null;
        menuDragMouseListener = null;
        menuKeyListener = null;
        propertyChangeListener = null;
        handler = null;
    }

    protected void uninstallKeyboardActions() {
        SwingUtilities.replaceUIActionMap(menuItem, null);
        SwingUtilities.replaceUIInputMap(menuItem, JComponent.
                                         WHEN_IN_FOCUSED_WINDOW, null);
    }

    protected MouseInputListener createMouseInputListener(JComponent c) {
        return getHandler();
    }

    protected MenuDragMouseListener createMenuDragMouseListener(JComponent c) {
        return getHandler();
    }

    protected MenuKeyListener createMenuKeyListener(JComponent c) {
        return null;
    }

    /**
     * Creates a <code>PropertyChangeListener</code> which will be added to
     * the menu item.
     * If this method returns null then it will not be added to the menu item.
     *
     * @return an instance of a <code>PropertyChangeListener</code> or null
     * @since 1.6
     */
    protected PropertyChangeListener
                                  createPropertyChangeListener(JComponent c) {
        return getHandler();
    }

    Handler getHandler() {
        if (handler == null) {
            handler = new Handler();
        }
        return handler;
    }

    InputMap createInputMap(int condition) {
        if (condition == JComponent.WHEN_IN_FOCUSED_WINDOW) {
            return new ComponentInputMapUIResource(menuItem);
        }
        return null;
    }

    void updateAcceleratorBinding() {
        KeyStroke accelerator = menuItem.getAccelerator();
        InputMap windowInputMap = SwingUtilities.getUIInputMap(
                       menuItem, JComponent.WHEN_IN_FOCUSED_WINDOW);

        if (windowInputMap != null) {
            windowInputMap.clear();
        }
        if (accelerator != null) {
            if (windowInputMap == null) {
                windowInputMap = createInputMap(JComponent.
                                                WHEN_IN_FOCUSED_WINDOW);
                SwingUtilities.replaceUIInputMap(menuItem,
                           JComponent.WHEN_IN_FOCUSED_WINDOW, windowInputMap);
            }
            windowInputMap.put(accelerator, "doClick");
        }
    }

    public Dimension getMinimumSize(JComponent c) {
        Dimension d = null;
        View v = (View) c.getClientProperty(BasicHTML.propertyKey);
        if (v != null) {
            d = getPreferredSize(c);
            d.width -= v.getPreferredSpan(View.X_AXIS) - v.getMinimumSpan(View.X_AXIS);
        }
        return d;
    }

    public Dimension getPreferredSize(JComponent c) {
        return getPreferredMenuItemSize(c,
                                        checkIcon,
                                        arrowIcon,
                                        defaultTextIconGap);
    }

    public Dimension getMaximumSize(JComponent c) {
        Dimension d = null;
        View v = (View) c.getClientProperty(BasicHTML.propertyKey);
        if (v != null) {
            d = getPreferredSize(c);
            d.width += v.getMaximumSpan(View.X_AXIS) - v.getPreferredSpan(View.X_AXIS);
        }
        return d;
    }

    // Returns parent of this component if it is not a top-level menu
    // Otherwise returns null
    private static JComponent getMenuItemParent(JMenuItem mi) {
        Container parent = mi.getParent();
        if ((parent instanceof JComponent) &&
             (!(mi instanceof JMenu) ||
               !((JMenu)mi).isTopLevelMenu())) {
            return (JComponent) parent;
        } else {
            return null;
        }
    }

    protected Dimension getPreferredMenuItemSize(JComponent c,
                                                 Icon checkIcon,
                                                 Icon arrowIcon,
                                                 int defaultTextIconGap) {

        // The method also determines the preferred width of the
        // parent popup menu (through DefaultMenuLayout class).
        // The menu width equals to the maximal width
        // among child menu items.

        // Menu item width will be a sum of the widest check icon, label,
        // arrow icon and accelerator text among neighbor menu items.
        // For the latest menu item we will know the maximal widths exactly.
        // It will be the widest menu item and it will determine
        // the width of the parent popup menu.

        // !!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!
        // There is a conceptual problem: if user sets preferred size manually
        // for a menu item, this method won't be called for it
        // (see JComponent.getPreferredSize()),
        // maximal widths won't be calculated, other menu items won't be able
        // to take them into account and will be layouted in such a way,
        // as there is no the item with manual preferred size.
        // But after the first paint() method call, all maximal widths
        // will be correctly calculated and layout of some menu items
        // can be changed. For example, it can cause a shift of
        // the icon and text when user points a menu item by mouse.

        JMenuItem mi = (JMenuItem) c;
        LayoutInfo li = getLayoutInfo(mi, checkIcon, arrowIcon,
                createMaxViewRect(), defaultTextIconGap, acceleratorDelimiter,
                BasicGraphicsUtils.isLeftToRight(mi), acceleratorFont,
                useCheckAndArrow(), getPropertyPrefix());

        Dimension result = new Dimension();

        // Calculate the result width
        result.width = li.leadingGap;
        addWidth(li.maxCheckWidth, li.afterCheckIconGap, result);
        // Take into account mimimal text offset.
        if ((!li.isTopLevelMenu)
                && (li.minTextOffset > 0)
                && (result.width < li.minTextOffset)) {
            result.width = li.minTextOffset;
        }
        addWidth(li.maxLabelWidth, li.gap, result);
        addWidth(li.maxAccWidth, li.gap, result);
        addWidth(li.maxArrowWidth, li.gap, result);

        // Calculate the result height
        result.height = max(li.checkRect.height, li.labelRect.height,
                            li.accRect.height, li.arrowRect.height);

        // Take into account menu item insets
        Insets insets = li.mi.getInsets();
        if(insets != null) {
            result.width += insets.left + insets.right;
            result.height += insets.top + insets.bottom;
        }

        // if the width is even, bump it up one. This is critical
        // for the focus dash line to draw properly
        if(result.width%2 == 0) {
            result.width++;
        }

        // if the height is even, bump it up one. This is critical
        // for the text to center properly
        if(result.height%2 == 0
                && Boolean.TRUE !=
                    UIManager.get(getPropertyPrefix() + ".evenHeight")) {
            result.height++;
        }

        li.clear();
        return result;
    }

    private Rectangle createMaxViewRect() {
        return new Rectangle(0,0,Short.MAX_VALUE, Short.MAX_VALUE);
    }

    private void addWidth(int width, int gap, Dimension result) {
        if (width > 0) {
            result.width += width + gap;
        }
    }

    private static int max(int... values) {
        int maxValue = Integer.MIN_VALUE;
        for (int i : values) {
            if (i > maxValue) {
                maxValue = i;
            }
        }
        return maxValue;
    }

    // LayoutInfo helps to calculate preferred size and to paint a menu item
    private static class LayoutInfo {
        JMenuItem mi;
        JComponent miParent;

        FontMetrics fm;
        FontMetrics accFm;

        Icon icon;
        Icon checkIcon;
        Icon arrowIcon;
        String text;
        String accText;

        boolean isColumnLayout;
        boolean useCheckAndArrow;
        boolean isLeftToRight;
        boolean isTopLevelMenu;
        View htmlView;

        int verticalAlignment;
        int horizontalAlignment;
        int verticalTextPosition;
        int horizontalTextPosition;
        int gap;
        int leadingGap;
        int afterCheckIconGap;
        int minTextOffset;

        Rectangle viewRect;
        Rectangle iconRect;
        Rectangle textRect;
        Rectangle accRect;
        Rectangle checkRect;
        Rectangle arrowRect;
        Rectangle labelRect;

        int origIconWidth;
        int origTextWidth;
        int origAccWidth;
        int origCheckWidth;
        int origArrowWidth;

        int maxIconWidth;
        int maxTextWidth;
        int maxAccWidth;
        int maxCheckWidth;
        int maxArrowWidth;
        int maxLabelWidth;

        // Empty constructor helps to create "final" LayoutInfo object
        public LayoutInfo() {
        }

        public LayoutInfo(JMenuItem mi, Icon checkIcon, Icon arrowIcon,
                          Rectangle viewRect, int gap, String accDelimiter,
                          boolean isLeftToRight, Font acceleratorFont,
                          boolean useCheckAndArrow, String propertyPrefix) {
            reset(mi, checkIcon, arrowIcon, viewRect, gap, accDelimiter,
                  isLeftToRight, acceleratorFont, useCheckAndArrow,
                  propertyPrefix);
        }

        // Allows to reuse a LayoutInfo object
        public void reset(JMenuItem mi, Icon checkIcon, Icon arrowIcon,
                          Rectangle viewRect, int gap, String accDelimiter,
                          boolean isLeftToRight, Font acceleratorFont,
                          boolean useCheckAndArrow, String propertyPrefix) {
            this.mi = mi;
            this.miParent = getMenuItemParent(mi);
            this.accText = getAccText(accDelimiter);
            this.verticalAlignment = mi.getVerticalAlignment();
            this.horizontalAlignment = mi.getHorizontalAlignment();
            this.verticalTextPosition = mi.getVerticalTextPosition();
            this.horizontalTextPosition = mi.getHorizontalTextPosition();
            this.useCheckAndArrow = useCheckAndArrow;
            this.fm = mi.getFontMetrics(mi.getFont());
            this.accFm = mi.getFontMetrics(acceleratorFont);
            this.isLeftToRight = isLeftToRight;
            this.isColumnLayout = isColumnLayout();
            this.isTopLevelMenu = (this.miParent == null)? true : false;
            this.checkIcon = checkIcon;
            this.icon = getIcon(propertyPrefix);
            this.arrowIcon = arrowIcon;
            this.text = mi.getText();
            this.gap = gap;
            this.afterCheckIconGap = getAfterCheckIconGap(propertyPrefix);
            this.minTextOffset = getMinTextOffset(propertyPrefix);
            this.htmlView = (View) mi.getClientProperty(BasicHTML.propertyKey);

            this.viewRect = viewRect;
            this.iconRect = new Rectangle();
            this.textRect = new Rectangle();
            this.accRect = new Rectangle();
            this.checkRect = new Rectangle();
            this.arrowRect = new Rectangle();
            this.labelRect = new Rectangle();

            calcWidthsAndHeights();
            this.origIconWidth = iconRect.width;
            this.origTextWidth = textRect.width;
            this.origAccWidth = accRect.width;
            this.origCheckWidth = checkRect.width;
            this.origArrowWidth = arrowRect.width;

            calcMaxWidths();
            this.leadingGap = getLeadingGap(propertyPrefix);
            calcMaxTextOffset();
        }

        // Clears fields to remove all links to other objects
        // to prevent memory leaks
        public void clear() {
            mi = null;
            miParent = null;
            fm = null;
            accFm = null;
            icon = null;
            checkIcon = null;
            arrowIcon = null;
            text = null;
            accText = null;
            htmlView = null;
            viewRect = null;
            iconRect = null;
            textRect = null;
            accRect = null;
            checkRect = null;
            arrowRect = null;
            labelRect = null;
        }

        private String getAccText(String acceleratorDelimiter) {
            String accText = "";
            KeyStroke accelerator = mi.getAccelerator();
            if (accelerator != null) {
                int modifiers = accelerator.getModifiers();
                if (modifiers > 0) {
                    accText = KeyEvent.getKeyModifiersText(modifiers);
                    accText += acceleratorDelimiter;
                }
                int keyCode = accelerator.getKeyCode();
                if (keyCode != 0) {
                    accText += KeyEvent.getKeyText(keyCode);
                } else {
                    accText += accelerator.getKeyChar();
                }
            }
            return accText;
        }

        // In case of column layout, .checkIconFactory is defined for this UI,
        // the icon is compatible with it and useCheckAndArrow() is true,
        // then the icon is handled by the checkIcon.
        private Icon getIcon(String propertyPrefix) {
            Icon icon = null;
            MenuItemCheckIconFactory iconFactory =
                (MenuItemCheckIconFactory) UIManager.get(propertyPrefix
                    + ".checkIconFactory");
            if (!isColumnLayout || !useCheckAndArrow || iconFactory == null
                    || !iconFactory.isCompatible(checkIcon, propertyPrefix)) {
               icon = mi.getIcon();
            }
            return icon;
        }

        private int getMinTextOffset(String propertyPrefix) {
            int minimumTextOffset = 0;
            Object minimumTextOffsetObject =
                    UIManager.get(propertyPrefix + ".minimumTextOffset");
            if (minimumTextOffsetObject instanceof Integer) {
                minimumTextOffset = (Integer) minimumTextOffsetObject;
            }
            return minimumTextOffset;
        }

        private int getAfterCheckIconGap(String propertyPrefix) {
            int afterCheckIconGap = gap;
            Object afterCheckIconGapObject =
                    UIManager.get(propertyPrefix + ".afterCheckIconGap");
            if (afterCheckIconGapObject instanceof Integer) {
                afterCheckIconGap = (Integer) afterCheckIconGapObject;
            }
            return afterCheckIconGap;
        }

        private int getLeadingGap(String propertyPrefix) {
            if (maxCheckWidth > 0) {
                return getCheckOffset(propertyPrefix);
            } else {
                return gap; // There is no any check icon
            }
        }

        private int getCheckOffset(String propertyPrefix) {
            int checkIconOffset = gap;
            Object checkIconOffsetObject =
                    UIManager.get(propertyPrefix + ".checkIconOffset");
            if (checkIconOffsetObject instanceof Integer) {
                checkIconOffset = (Integer) checkIconOffsetObject;
            }
            return checkIconOffset;
        }

        private void calcWidthsAndHeights()
        {
            // iconRect
            if (icon != null) {
                iconRect.width = icon.getIconWidth();
                iconRect.height = icon.getIconHeight();
            }

            // accRect
            if (!accText.equals("")) {
                accRect.width = SwingUtilities2.stringWidth(
                        mi, accFm, accText);
                accRect.height = accFm.getHeight();
            }

            // textRect
            if (text == null) {
                text = "";
            } else if (!text.equals("")) {
                if (htmlView != null) {
                    // Text is HTML
                    textRect.width =
                            (int) htmlView.getPreferredSpan(View.X_AXIS);
                    textRect.height =
                            (int) htmlView.getPreferredSpan(View.Y_AXIS);
                } else {
                    // Text isn't HTML
                    textRect.width =
                            SwingUtilities2.stringWidth(mi, fm, text);
                    textRect.height = fm.getHeight();
                }
            }

            if (useCheckAndArrow) {
                // checkIcon
                if (checkIcon != null) {
                    checkRect.width = checkIcon.getIconWidth();
                    checkRect.height = checkIcon.getIconHeight();
                }
                // arrowRect
                if (arrowIcon != null) {
                    arrowRect.width = arrowIcon.getIconWidth();
                    arrowRect.height = arrowIcon.getIconHeight();
                }
            }

            // labelRect
            if (isColumnLayout) {
                labelRect.width = iconRect.width + textRect.width + gap;
                labelRect.height = max(checkRect.height, iconRect.height,
                        textRect.height, accRect.height, arrowRect.height);
            } else {
                textRect = new Rectangle();
                iconRect = new Rectangle();
                SwingUtilities.layoutCompoundLabel(mi, fm, text, icon,
                        verticalAlignment, horizontalAlignment,
                        verticalTextPosition, horizontalTextPosition,
                        viewRect, iconRect, textRect, gap);
                 labelRect = iconRect.union(textRect);
            }
        }

        private void calcMaxWidths() {
            maxCheckWidth = calcMaxValue(BasicMenuItemUI.MAX_CHECK_WIDTH,
                    checkRect.width);
            maxArrowWidth = calcMaxValue(BasicMenuItemUI.MAX_ARROW_WIDTH,
                    arrowRect.width);
            maxAccWidth = calcMaxValue(BasicMenuItemUI.MAX_ACC_WIDTH,
                    accRect.width);

            if (isColumnLayout) {
                maxIconWidth = calcMaxValue(BasicMenuItemUI.MAX_ICON_WIDTH,
                        iconRect.width);
                maxTextWidth = calcMaxValue(BasicMenuItemUI.MAX_TEXT_WIDTH,
                        textRect.width);
                int curGap = gap;
                if ((maxIconWidth == 0) || (maxTextWidth == 0)) {
                    curGap = 0;
                }
                maxLabelWidth =
                        calcMaxValue(BasicMenuItemUI.MAX_LABEL_WIDTH,
                        maxIconWidth + maxTextWidth + curGap);
            } else {
                // We shouldn't use current icon and text widths
                // in maximal widths calculation for complex layout.
                maxIconWidth = getParentIntProperty(BasicMenuItemUI.MAX_ICON_WIDTH);
                maxLabelWidth = calcMaxValue(BasicMenuItemUI.MAX_LABEL_WIDTH,
                        labelRect.width);
                // If maxLabelWidth is wider
                // than the widest icon + the widest text + gap,
                // we should update the maximal text witdh
                int candidateTextWidth = maxLabelWidth - maxIconWidth;
                if (maxIconWidth > 0) {
                    candidateTextWidth -= gap;
                }
                maxTextWidth = calcMaxValue(BasicMenuItemUI.MAX_TEXT_WIDTH,
                        candidateTextWidth);
            }
        }

        // Calculates and returns maximal value
        // through specified parent component client property.
        private int calcMaxValue(Object propertyName, int value) {
            // Get maximal value from parent client property
            int maxValue = getParentIntProperty(propertyName);
            // Store new maximal width in parent client property
            if (value > maxValue) {
                if (miParent != null) {
                    miParent.putClientProperty(propertyName, value);
                }
                return value;
            } else {
                return maxValue;
            }
        }

        // Returns parent client property as int
        private int getParentIntProperty(Object propertyName) {
            Object value = null;
            if (miParent != null) {
                value = miParent.getClientProperty(propertyName);
            }
            if ((value == null) || !(value instanceof Integer)){
                value = 0;
            }
            return (Integer)value;
        }

        private boolean isColumnLayout() {
            return isColumnLayout(isLeftToRight, horizontalAlignment,
                    horizontalTextPosition, verticalTextPosition);
        }

        public static boolean isColumnLayout(boolean isLeftToRight,
                                             JMenuItem mi) {
            assert(mi != null);
            return isColumnLayout(isLeftToRight, mi.getHorizontalAlignment(),
                    mi.getHorizontalTextPosition(), mi.getVerticalTextPosition());
        }

        // Answers should we do column layout for a menu item or not.
        // We do it when a user doesn't set any alignments
        // and text positions manually, except the vertical alignment.
        public static boolean isColumnLayout( boolean isLeftToRight,
                int horizontalAlignment, int horizontalTextPosition,
                int verticalTextPosition) {
            if (verticalTextPosition != SwingConstants.CENTER) {
                return false;
            }
            if (isLeftToRight) {
                if (horizontalAlignment != SwingConstants.LEADING
                 && horizontalAlignment != SwingConstants.LEFT) {
                    return false;
                }
                if (horizontalTextPosition != SwingConstants.TRAILING
                 && horizontalTextPosition != SwingConstants.RIGHT) {
                    return false;
                }
            } else {
                if (horizontalAlignment != SwingConstants.LEADING
                 && horizontalAlignment != SwingConstants.RIGHT) {
                    return false;
                }
                if (horizontalTextPosition != SwingConstants.TRAILING
                 && horizontalTextPosition != SwingConstants.LEFT) {
                    return false;
                }
            }
            return true;
        }

        // Calculates maximal text offset.
        // It is required for some L&Fs (ex: Vista L&F).
        // The offset is meaningful only for L2R column layout.
        private void calcMaxTextOffset() {
            if (!isColumnLayout || !isLeftToRight) {
                return;
            }

            // Calculate the current text offset
            int offset = viewRect.x + leadingGap + maxCheckWidth
                     + afterCheckIconGap + maxIconWidth + gap;
            if (maxCheckWidth == 0) {
                offset -= afterCheckIconGap;
            }
            if (maxIconWidth == 0) {
                offset -= gap;
            }

            // maximal text offset shouldn't be less than minimal text offset;
            if (offset < minTextOffset) {
                offset = minTextOffset;
            }

            // Calculate and store the maximal text offset
            calcMaxValue(BASICMENUITEMUI_MAX_TEXT_OFFSET, offset);
        }

        public String toString() {
            StringBuilder result = new StringBuilder();
            result.append(super.toString()).append("\n");
            result.append("accFm = ").append(accFm).append("\n");
            result.append("accRect = ").append(accRect).append("\n");
            result.append("accText = ").append(accText).append("\n");
            result.append("afterCheckIconGap = ").append(afterCheckIconGap)
                    .append("\n");
            result.append("arrowIcon = ").append(arrowIcon).append("\n");
            result.append("arrowRect = ").append(arrowRect).append("\n");
            result.append("checkIcon = ").append(checkIcon).append("\n");
            result.append("checkRect = ").append(checkRect).append("\n");
            result.append("fm = ").append(fm).append("\n");
            result.append("gap = ").append(gap).append("\n");
            result.append("horizontalAlignment = ").append(horizontalAlignment)
                    .append("\n");
            result.append("horizontalTextPosition = ")
                    .append(horizontalTextPosition).append("\n");
            result.append("htmlView = ").append(htmlView).append("\n");
            result.append("icon = ").append(icon).append("\n");
            result.append("iconRect = ").append(iconRect).append("\n");
            result.append("isColumnLayout = ").append(isColumnLayout).append("\n");
            result.append("isLeftToRight = ").append(isLeftToRight).append("\n");
            result.append("isTopLevelMenu = ").append(isTopLevelMenu).append("\n");
            result.append("labelRect = ").append(labelRect).append("\n");
            result.append("leadingGap = ").append(leadingGap).append("\n");
            result.append("maxAccWidth = ").append(maxAccWidth).append("\n");
            result.append("maxArrowWidth = ").append(maxArrowWidth).append("\n");
            result.append("maxCheckWidth = ").append(maxCheckWidth).append("\n");
            result.append("maxIconWidth = ").append(maxIconWidth).append("\n");
            result.append("maxLabelWidth = ").append(maxLabelWidth).append("\n");
            result.append("maxTextWidth = ").append(maxTextWidth).append("\n");
            result.append("maxTextOffset = ")
                    .append(getParentIntProperty(BASICMENUITEMUI_MAX_TEXT_OFFSET))
                    .append("\n");
            result.append("mi = ").append(mi).append("\n");
            result.append("minTextOffset = ").append(minTextOffset).append("\n");
            result.append("miParent = ").append(miParent).append("\n");
            result.append("origAccWidth = ").append(origAccWidth).append("\n");
            result.append("origArrowWidth = ").append(origArrowWidth).append("\n");
            result.append("origCheckWidth = ").append(origCheckWidth).append("\n");
            result.append("origIconWidth = ").append(origIconWidth).append("\n");
            result.append("origTextWidth = ").append(origTextWidth).append("\n");
            result.append("text = ").append(text).append("\n");
            result.append("textRect = ").append(textRect).append("\n");
            result.append("useCheckAndArrow = ").append(useCheckAndArrow)
                    .append("\n");
            result.append("verticalAlignment = ").append(verticalAlignment)
                    .append("\n");
            result.append("verticalTextPosition = ")
                    .append(verticalTextPosition).append("\n");
            result.append("viewRect = ").append(viewRect).append("\n");
            return result.toString();
        }
    } // End of LayoutInfo

    // Reuses layoutInfo object to reduce the amount of produced garbage
    private LayoutInfo getLayoutInfo(JMenuItem mi, Icon checkIcon, Icon arrowIcon,
                             Rectangle viewRect, int gap, String accDelimiter,
                             boolean isLeftToRight, Font acceleratorFont,
                             boolean useCheckAndArrow, String propertyPrefix) {
        // layoutInfo is final and always not null
        layoutInfo.reset(mi, checkIcon, arrowIcon, viewRect,
                gap, accDelimiter, isLeftToRight, acceleratorFont,
                useCheckAndArrow, propertyPrefix);
        return layoutInfo;
    }

    /**
     * We draw the background in paintMenuItem()
     * so override update (which fills the background of opaque
     * components by default) to just call paint().
     *
     */
    public void update(Graphics g, JComponent c) {
        paint(g, c);
    }

    public void paint(Graphics g, JComponent c) {
        paintMenuItem(g, c, checkIcon, arrowIcon,
                      selectionBackground, selectionForeground,
                      defaultTextIconGap);
    }

    protected void paintMenuItem(Graphics g, JComponent c,
                                     Icon checkIcon, Icon arrowIcon,
                                     Color background, Color foreground,
                                     int defaultTextIconGap) {
        // Save original graphics font and color
        Font holdf = g.getFont();
        Color holdc = g.getColor();

        JMenuItem mi = (JMenuItem) c;
        g.setFont(mi.getFont());

        Rectangle viewRect = new Rectangle(0, 0, mi.getWidth(), mi.getHeight());
        applyInsets(viewRect, mi.getInsets());

        LayoutInfo li = getLayoutInfo(mi, checkIcon, arrowIcon,
                viewRect, defaultTextIconGap, acceleratorDelimiter,
                BasicGraphicsUtils.isLeftToRight(mi), acceleratorFont,
                useCheckAndArrow(), getPropertyPrefix());
        layoutMenuItem(li);

        paintBackground(g, mi, background);
        paintCheckIcon(g, li, holdc, foreground);
        paintIcon(g, li, holdc);
        paintText(g, li);
        paintAccText(g, li);
        paintArrowIcon(g, li, foreground);

        // Restore original graphics font and color
        g.setColor(holdc);
        g.setFont(holdf);

        li.clear();
    }

    private void paintIcon(Graphics g, LayoutInfo li, Color holdc) {
        if (li.icon != null) {
            Icon icon;
            ButtonModel model = li.mi.getModel();
            if (!model.isEnabled()) {
                icon = (Icon) li.mi.getDisabledIcon();
            } else if (model.isPressed() && model.isArmed()) {
                icon = (Icon) li.mi.getPressedIcon();
                if (icon == null) {
                    // Use default icon
                    icon = (Icon) li.mi.getIcon();
                }
            } else {
                icon = (Icon) li.mi.getIcon();
            }

            if (icon != null) {
                icon.paintIcon(li.mi, g, li.iconRect.x, li.iconRect.y);
                g.setColor(holdc);
            }
        }
    }

    private void paintCheckIcon(Graphics g, LayoutInfo li,
                                Color holdc, Color foreground) {
        if (li.checkIcon != null) {
            ButtonModel model = li.mi.getModel();
            if (model.isArmed()
                    || (li.mi instanceof JMenu && model.isSelected())) {
                g.setColor(foreground);
            } else {
                g.setColor(holdc);
            }
            if (li.useCheckAndArrow) {
                li.checkIcon.paintIcon(li.mi, g, li.checkRect.x,
                                       li.checkRect.y);
            }
            g.setColor(holdc);
        }
    }

    private void paintAccText(Graphics g, LayoutInfo li) {
        if (!li.accText.equals("")) {
            ButtonModel model = li.mi.getModel();
            g.setFont(acceleratorFont);
            if (!model.isEnabled()) {
                // *** paint the accText disabled
                if (disabledForeground != null) {
                    g.setColor(disabledForeground);
                    SwingUtilities2.drawString(li.mi, g, li.accText,
                                 li.accRect.x,
                                 li.accRect.y + li.accFm.getAscent());
                } else {
                    g.setColor(li.mi.getBackground().brighter());
                    SwingUtilities2.drawString(li.mi, g, li.accText, li.accRect.x,
                                 li.accRect.y + li.accFm.getAscent());
                    g.setColor(li.mi.getBackground().darker());
                    SwingUtilities2.drawString(li.mi, g, li.accText,
                            li.accRect.x - 1,
                            li.accRect.y + li.accFm.getAscent() - 1);
                }
            } else {
                // *** paint the accText normally
                if (model.isArmed() ||
                        (li.mi instanceof JMenu && model.isSelected())) {
                    g.setColor(acceleratorSelectionForeground);
                } else {
                    g.setColor(acceleratorForeground);
                }
                SwingUtilities2.drawString(li.mi, g, li.accText, li.accRect.x,
                        li.accRect.y + li.accFm.getAscent());
            }
        }
    }

    private void paintText(Graphics g, LayoutInfo li) {
        if (!li.text.equals("")) {
            if (li.htmlView != null) {
                // Text is HTML
                li.htmlView.paint(g, li.textRect);
            } else {
                // Text isn't HTML
                paintText(g, li.mi, li.textRect, li.text);
            }
        }
    }

    private void paintArrowIcon(Graphics g, LayoutInfo li, Color foreground) {
        if (li.arrowIcon != null) {
            ButtonModel model = li.mi.getModel();
            if (model.isArmed()
                    || (li.mi instanceof JMenu && model.isSelected())) {
                g.setColor(foreground);
            }
            if (li.useCheckAndArrow) {
                li.arrowIcon.paintIcon(li.mi, g, li.arrowRect.x, li.arrowRect.y);
            }
        }
    }

    private void applyInsets(Rectangle rect, Insets insets) {
        if(insets != null) {
            rect.x += insets.left;
            rect.y += insets.top;
            rect.width -= (insets.right + rect.x);
            rect.height -= (insets.bottom + rect.y);
        }
    }

    /**
     * Draws the background of the menu item.
     *
     * @param g the paint graphics
     * @param menuItem menu item to be painted
     * @param bgColor selection background color
     * @since 1.4
     */
    protected void paintBackground(Graphics g, JMenuItem menuItem, Color bgColor) {
        ButtonModel model = menuItem.getModel();
        Color oldColor = g.getColor();
        int menuWidth = menuItem.getWidth();
        int menuHeight = menuItem.getHeight();

        if(menuItem.isOpaque()) {
            if (model.isArmed()|| (menuItem instanceof JMenu && model.isSelected())) {
                g.setColor(bgColor);
                g.fillRect(0,0, menuWidth, menuHeight);
            } else {
                g.setColor(menuItem.getBackground());
                g.fillRect(0,0, menuWidth, menuHeight);
            }
            g.setColor(oldColor);
        }
        else if (model.isArmed() || (menuItem instanceof JMenu &&
                                     model.isSelected())) {
            g.setColor(bgColor);
            g.fillRect(0,0, menuWidth, menuHeight);
            g.setColor(oldColor);
        }
    }

    /**
     * Renders the text of the current menu item.
     * <p>
     * @param g graphics context
     * @param menuItem menu item to render
     * @param textRect bounding rectangle for rendering the text
     * @param text string to render
     * @since 1.4
     */
    protected void paintText(Graphics g, JMenuItem menuItem, Rectangle textRect, String text) {
        ButtonModel model = menuItem.getModel();
        FontMetrics fm = SwingUtilities2.getFontMetrics(menuItem, g);
        int mnemIndex = menuItem.getDisplayedMnemonicIndex();

        if(!model.isEnabled()) {
            // *** paint the text disabled
            if ( UIManager.get("MenuItem.disabledForeground") instanceof Color ) {
                g.setColor( UIManager.getColor("MenuItem.disabledForeground") );
                SwingUtilities2.drawStringUnderlineCharAt(menuItem, g,text,
                          mnemIndex, textRect.x,  textRect.y + fm.getAscent());
            } else {
                g.setColor(menuItem.getBackground().brighter());
                SwingUtilities2.drawStringUnderlineCharAt(menuItem, g, text,
                           mnemIndex, textRect.x, textRect.y + fm.getAscent());
                g.setColor(menuItem.getBackground().darker());
                SwingUtilities2.drawStringUnderlineCharAt(menuItem, g,text,
                           mnemIndex,  textRect.x - 1, textRect.y +
                           fm.getAscent() - 1);
            }
        } else {
            // *** paint the text normally
            if (model.isArmed()|| (menuItem instanceof JMenu && model.isSelected())) {
                g.setColor(selectionForeground); // Uses protected field.
            }
            SwingUtilities2.drawStringUnderlineCharAt(menuItem, g,text,
                           mnemIndex, textRect.x, textRect.y + fm.getAscent());
        }
    }


    /**
     * Layout icon, text, check icon, accelerator text and arrow icon
     * in the viewRect and return their positions.
     *
     * If horizontalAlignment, verticalTextPosition and horizontalTextPosition
     * are default (user doesn't set any manually) the layouting algorithm is:
     * Elements are layouted in the five columns:
     * check icon + icon + text + accelerator text + arrow icon
     *
     * In the other case elements are layouted in the four columns:
     * check icon + label + accelerator text + arrow icon
     * Label is icon and text rectangles union.
     *
     * The order of columns can be reversed.
     * It depends on the menu item orientation.
     */
    private void layoutMenuItem(LayoutInfo li)
    {
        li.checkRect.width = li.maxCheckWidth;
        li.accRect.width = li.maxAccWidth;
        li.arrowRect.width = li.maxArrowWidth;

        if (li.isColumnLayout) {
            if (li.isLeftToRight) {
                doLTRColumnLayout(li);
            } else {
                doRTLColumnLayout(li);
            }
        } else {
            if (li.isLeftToRight) {
                doLTRComplexLayout(li);
            } else {
                doRTLComplexLayout(li);
            }
        }

        alignAccCheckAndArrowVertically(li);
    }

    // Aligns the accelertor text and the check and arrow icons vertically
    // with the center of the label rect.
    private void alignAccCheckAndArrowVertically(LayoutInfo li) {
        li.accRect.y = (int)(li.labelRect.y + (float)li.labelRect.height/2
                - (float)li.accRect.height/2);
        fixVerticalAlignment(li, li.accRect);
        if (li.useCheckAndArrow) {
            li.arrowRect.y = (int)(li.labelRect.y + (float)li.labelRect.height/2
                    - (float)li.arrowRect.height/2);
            li.checkRect.y = (int)(li.labelRect.y + (float)li.labelRect.height/2
                    - (float)li.checkRect.height/2);
            fixVerticalAlignment(li, li.arrowRect);
            fixVerticalAlignment(li, li.checkRect);
        }
    }

    // Fixes vertical alignment of all menu item elements if a rect.y
    // or (rect.y + rect.height) is out of viewRect bounds
    private void fixVerticalAlignment(LayoutInfo li, Rectangle r) {
        int delta = 0;
        if (r.y < li.viewRect.y) {
            delta = li.viewRect.y - r.y;
        } else if (r.y + r.height > li.viewRect.y + li.viewRect.height) {
            delta = li.viewRect.y + li.viewRect.height - r.y - r.height;
        }
        if (delta != 0) {
            li.checkRect.y += delta;
            li.iconRect.y += delta;
            li.textRect.y += delta;
            li.accRect.y += delta;
            li.arrowRect.y += delta;
        }
    }

    private void doLTRColumnLayout(LayoutInfo li) {
        // Set maximal width for all the five basic rects
        // (three other ones are already maximal)
        li.iconRect.width = li.maxIconWidth;
        li.textRect.width = li.maxTextWidth;

        // Set X coordinates
        // All rects will be aligned at the left side
        calcXPositionsL2R(li.viewRect.x, li.leadingGap, li.gap, li.checkRect,
                li.iconRect, li.textRect);

        // Tune afterCheckIconGap
        if (li.checkRect.width > 0) { // there is the afterCheckIconGap
            li.iconRect.x += li.afterCheckIconGap - li.gap;
            li.textRect.x += li.afterCheckIconGap - li.gap;
        }

        calcXPositionsR2L(li.viewRect.x + li.viewRect.width, li.gap,
                li.arrowRect, li.accRect);

        // Take into account minimal text offset
        int textOffset = li.textRect.x - li.viewRect.x;
        if (!li.isTopLevelMenu && (textOffset < li.minTextOffset)) {
            li.textRect.x += li.minTextOffset - textOffset;
        }

        // Take into account the left side bearings for text and accelerator text.
        fixTextRects(li);

        // Set Y coordinate for text and icon.
        // Y coordinates for other rects
        // will be calculated later in layoutMenuItem.
        calcTextAndIconYPositions(li);

        // Calculate valid X and Y coordinates for labelRect
        li.labelRect = li.textRect.union(li.iconRect);
    }

    private void doLTRComplexLayout(LayoutInfo li) {
        li.labelRect.width = li.maxLabelWidth;

        // Set X coordinates
        calcXPositionsL2R(li.viewRect.x, li.leadingGap, li.gap, li.checkRect,
                li.labelRect);

        // Tune afterCheckIconGap
        if (li.checkRect.width > 0) { // there is the afterCheckIconGap
            li.labelRect.x += li.afterCheckIconGap - li.gap;
        }

        calcXPositionsR2L(li.viewRect.x + li.viewRect.width, li.gap,
                li.arrowRect, li.accRect);

        // Take into account minimal text offset
        int labelOffset = li.labelRect.x - li.viewRect.x;
        if (!li.isTopLevelMenu && (labelOffset < li.minTextOffset)) {
            li.labelRect.x += li.minTextOffset - labelOffset;
        }

        // Take into account the left side bearing for accelerator text.
        // The LSB for text is taken into account in layoutCompoundLabel() below.
        fixAccTextRect(li);

        // Layout icon and text with SwingUtilities.layoutCompoundLabel()
        // within the labelRect
        li.textRect = new Rectangle();
        li.iconRect = new Rectangle();
        SwingUtilities.layoutCompoundLabel(
                            li.mi, li.fm, li.text, li.icon, li.verticalAlignment,
                            li.horizontalAlignment, li.verticalTextPosition,
                            li.horizontalTextPosition, li.labelRect,
                            li.iconRect, li.textRect, li.gap);
    }

    private void doRTLColumnLayout(LayoutInfo li) {
        // Set maximal width for all the five basic rects
        // (three other ones are already maximal)
        li.iconRect.width = li.maxIconWidth;
        li.textRect.width = li.maxTextWidth;

        // Set X coordinates
        calcXPositionsR2L(li.viewRect.x + li.viewRect.width, li.leadingGap,
                li.gap, li.checkRect, li.iconRect, li.textRect);

        // Tune the gap after check icon
        if (li.checkRect.width > 0) { // there is the gap after check icon
            li.iconRect.x -= li.afterCheckIconGap - li.gap;
            li.textRect.x -= li.afterCheckIconGap - li.gap;
        }

        calcXPositionsL2R(li.viewRect.x, li.gap, li.arrowRect,
                li.accRect);

        // Take into account minimal text offset
        int textOffset = (li.viewRect.x + li.viewRect.width)
                       - (li.textRect.x + li.textRect.width);
        if (!li.isTopLevelMenu && (textOffset < li.minTextOffset)) {
            li.textRect.x -= li.minTextOffset - textOffset;
        }

        // Align icon, text, accelerator text, check icon and arrow icon
        // at the right side
        rightAlignAllRects(li);

        // Take into account the left side bearings for text and accelerator text.
        fixTextRects(li);

        // Set Y coordinates for text and icon.
        // Y coordinates for other rects
        // will be calculated later in layoutMenuItem.
        calcTextAndIconYPositions(li);

        // Calculate valid X and Y coordinate for labelRect
        li.labelRect = li.textRect.union(li.iconRect);
    }

    private void doRTLComplexLayout(LayoutInfo li) {
        li.labelRect.width = li.maxLabelWidth;

        // Set X coordinates
        calcXPositionsR2L(li.viewRect.x + li.viewRect.width, li.leadingGap,
                li.gap, li.checkRect, li.labelRect);

        // Tune the gap after check icon
        if (li.checkRect.width > 0) { // there is the gap after check icon
            li.labelRect.x -= li.afterCheckIconGap - li.gap;
        }

        calcXPositionsL2R(li.viewRect.x, li.gap, li.arrowRect,
                li.accRect);

        // Take into account minimal text offset
        int labelOffset = (li.viewRect.x + li.viewRect.width)
                        - (li.labelRect.x + li.labelRect.width);
        if (!li.isTopLevelMenu && (labelOffset < li.minTextOffset)) {
            li.labelRect.x -= li.minTextOffset - labelOffset;
        }

        // Align icon, text, accelerator text, check icon and arrow icon
        // at the right side
        rightAlignAllRects(li);

        // Take into account the left side bearing for accelerator text.
        // The LSB for text is taken into account in layoutCompoundLabel() below.
        fixAccTextRect(li);

        // Layout icon and text with SwingUtilities.layoutCompoundLabel()
        // within the labelRect
        li.textRect = new Rectangle();
        li.iconRect = new Rectangle();
        SwingUtilities.layoutCompoundLabel(
                            menuItem, li.fm, li.text, li.icon, li.verticalAlignment,
                            li.horizontalAlignment, li.verticalTextPosition,
                            li.horizontalTextPosition, li.labelRect,
                            li.iconRect, li.textRect, li.gap);
    }

    private void calcXPositionsL2R(int startXPos, int leadingGap,
                                   int gap, Rectangle... rects) {
        int curXPos = startXPos + leadingGap;
        for (Rectangle rect : rects) {
            rect.x = curXPos;
            if (rect.width > 0) {
                curXPos += rect.width + gap;
            }
        }
    }

    private void calcXPositionsL2R(int startXPos, int gap, Rectangle... rects) {
        calcXPositionsL2R(startXPos, gap, gap, rects);
    }

    private void calcXPositionsR2L(int startXPos, int leadingGap,
                                   int gap, Rectangle... rects) {
        int curXPos = startXPos - leadingGap;
        for (Rectangle rect : rects) {
            rect.x = curXPos - rect.width;
            if (rect.width > 0) {
                curXPos -= rect.width + gap;
            }
        }
    }

    private void calcXPositionsR2L(int startXPos, int gap, Rectangle... rects) {
        calcXPositionsR2L(startXPos, gap, gap, rects);
    }

    // Takes into account the left side bearings for text and accelerator text
    private void fixTextRects(LayoutInfo li) {
        if (li.htmlView == null) { // The text isn't a HTML
            int lsb = SwingUtilities2.getLeftSideBearing(li.mi, li.fm, li.text);
            if (lsb < 0) {
                li.textRect.x -= lsb;
            }
        }
        fixAccTextRect(li);
    }

    // Takes into account the left side bearing for accelerator text
    private void fixAccTextRect(LayoutInfo li) {
        int lsb = SwingUtilities2
                .getLeftSideBearing(li.mi, li.accFm, li.accText);
        if (lsb < 0) {
            li.accRect.x -= lsb;
        }
    }

    // Sets Y coordinates of text and icon
    // taking into account the vertical alignment
    private void calcTextAndIconYPositions(LayoutInfo li) {
        if (li.verticalAlignment == SwingUtilities.TOP) {
            li.textRect.y  = (int)(li.viewRect.y
                    + (float)li.labelRect.height/2
                    - (float)li.textRect.height/2);
            li.iconRect.y  = (int)(li.viewRect.y
                    + (float)li.labelRect.height/2
                    - (float)li.iconRect.height/2);
        } else if (li.verticalAlignment == SwingUtilities.CENTER) {
            li.textRect.y = (int)(li.viewRect.y
                    + (float)li.viewRect.height/2
                    - (float)li.textRect.height/2);
            li.iconRect.y = (int)(li.viewRect.y
                    + (float)li.viewRect.height/2
                    - (float)li.iconRect.height/2);
        }
        else if (li.verticalAlignment == SwingUtilities.BOTTOM) {
            li.textRect.y = (int)(li.viewRect.y + li.viewRect.height
                    - (float)li.labelRect.height/2
                    - (float)li.textRect.height/2);
            li.iconRect.y = (int)(li.viewRect.y + li.viewRect.height
                    - (float)li.labelRect.height/2
                    - (float)li.iconRect.height/2);
        }
    }

    // Aligns icon, text, accelerator text, check icon and arrow icon
    // at the right side
    private void rightAlignAllRects(LayoutInfo li) {
        li.iconRect.x = li.iconRect.x + li.iconRect.width - li.origIconWidth;
        li.iconRect.width = li.origIconWidth;
        li.textRect.x = li.textRect.x + li.textRect.width - li.origTextWidth;
        li.textRect.width = li.origTextWidth;
        li.accRect.x = li.accRect.x + li.accRect.width
                - li.origAccWidth;
        li.accRect.width = li.origAccWidth;
        li.checkRect.x = li.checkRect.x + li.checkRect.width
                - li.origCheckWidth;
        li.checkRect.width = li.origCheckWidth;
        li.arrowRect.x = li.arrowRect.x + li.arrowRect.width -
                li.origArrowWidth;
        li.arrowRect.width = li.origArrowWidth;
    }

    /*
     * Returns false if the component is a JMenu and it is a top
     * level menu (on the menubar).
     */
    private boolean useCheckAndArrow(){
        boolean b = true;
        if((menuItem instanceof JMenu) &&
           (((JMenu)menuItem).isTopLevelMenu())) {
            b = false;
        }
        return b;
    }

    public MenuElement[] getPath() {
        MenuSelectionManager m = MenuSelectionManager.defaultManager();
        MenuElement oldPath[] = m.getSelectedPath();
        MenuElement newPath[];
        int i = oldPath.length;
        if (i == 0)
            return new MenuElement[0];
        Component parent = menuItem.getParent();
        if (oldPath[i-1].getComponent() == parent) {
            // The parent popup menu is the last so far
            newPath = new MenuElement[i+1];
            System.arraycopy(oldPath, 0, newPath, 0, i);
            newPath[i] = menuItem;
        } else {
            // A sibling menuitem is the current selection
            //
            //  This probably needs to handle 'exit submenu into
            // a menu item.  Search backwards along the current
            // selection until you find the parent popup menu,
            // then copy up to that and add yourself...
            int j;
            for (j = oldPath.length-1; j >= 0; j--) {
                if (oldPath[j].getComponent() == parent)
                    break;
            }
            newPath = new MenuElement[j+2];
            System.arraycopy(oldPath, 0, newPath, 0, j+1);
            newPath[j+1] = menuItem;
            /*
            System.out.println("Sibling condition -- ");
            System.out.println("Old array : ");
            printMenuElementArray(oldPath, false);
            System.out.println("New array : ");
            printMenuElementArray(newPath, false);
            */
        }
        return newPath;
    }

    void printMenuElementArray(MenuElement path[], boolean dumpStack) {
        System.out.println("Path is(");
        int i, j;
        for(i=0,j=path.length; i<j ;i++){
            for (int k=0; k<=i; k++)
                System.out.print("  ");
            MenuElement me = (MenuElement) path[i];
            if(me instanceof JMenuItem)
                System.out.println(((JMenuItem)me).getText() + ", ");
            else if (me == null)
                System.out.println("NULL , ");
            else
                System.out.println("" + me + ", ");
        }
        System.out.println(")");

        if (dumpStack == true)
            Thread.dumpStack();
    }
    protected class MouseInputHandler implements MouseInputListener {
        // NOTE: This class exists only for backward compatability. All
        // its functionality has been moved into Handler. If you need to add
        // new functionality add it to the Handler, but make sure this
        // class calls into the Handler.

        public void mouseClicked(MouseEvent e) {
            getHandler().mouseClicked(e);
        }
        public void mousePressed(MouseEvent e) {
            getHandler().mousePressed(e);
        }
        public void mouseReleased(MouseEvent e) {
            getHandler().mouseReleased(e);
        }
        public void mouseEntered(MouseEvent e) {
            getHandler().mouseEntered(e);
        }
        public void mouseExited(MouseEvent e) {
            getHandler().mouseExited(e);
        }
        public void mouseDragged(MouseEvent e) {
            getHandler().mouseDragged(e);
        }
        public void mouseMoved(MouseEvent e) {
            getHandler().mouseMoved(e);
        }
    }


    private static class Actions extends UIAction {
        private static final String CLICK = "doClick";

        Actions(String key) {
            super(key);
        }

        public void actionPerformed(ActionEvent e) {
            JMenuItem mi = (JMenuItem)e.getSource();
            MenuSelectionManager.defaultManager().clearSelectedPath();
            mi.doClick();
        }
    }

    /**
     * Call this method when a menu item is to be activated.
     * This method handles some of the details of menu item activation
     * such as clearing the selected path and messaging the
     * JMenuItem's doClick() method.
     *
     * @param msm  A MenuSelectionManager. The visual feedback and
     *             internal bookkeeping tasks are delegated to
     *             this MenuSelectionManager. If <code>null</code> is
     *             passed as this argument, the
     *             <code>MenuSelectionManager.defaultManager</code> is
     *             used.
     * @see MenuSelectionManager
     * @see JMenuItem#doClick(int)
     * @since 1.4
     */
    protected void doClick(MenuSelectionManager msm) {
        // Auditory cue
        if (! isInternalFrameSystemMenu()) {
            BasicLookAndFeel.playSound(menuItem, getPropertyPrefix() +
                                       ".commandSound");
        }
        // Visual feedback
        if (msm == null) {
            msm = MenuSelectionManager.defaultManager();
        }
        msm.clearSelectedPath();
        menuItem.doClick(0);
    }

    /**
     * This is to see if the menu item in question is part of the
     * system menu on an internal frame.
     * The Strings that are being checked can be found in
     * MetalInternalFrameTitlePaneUI.java,
     * WindowsInternalFrameTitlePaneUI.java, and
     * MotifInternalFrameTitlePaneUI.java.
     *
     * @since 1.4
     */
    private boolean isInternalFrameSystemMenu() {
        String actionCommand = menuItem.getActionCommand();
        if ((actionCommand == "Close") ||
            (actionCommand == "Minimize") ||
            (actionCommand == "Restore") ||
            (actionCommand == "Maximize")) {
          return true;
        } else {
          return false;
        }
    }


    // BasicMenuUI subclasses this.
    class Handler implements MenuDragMouseListener,
                          MouseInputListener, PropertyChangeListener {
        //
        // MouseInputListener
        //
        public void mouseClicked(MouseEvent e) {}
        public void mousePressed(MouseEvent e) {
        }
        public void mouseReleased(MouseEvent e) {
            if (!menuItem.isEnabled()) {
                return;
            }
            MenuSelectionManager manager =
                MenuSelectionManager.defaultManager();
            Point p = e.getPoint();
            if(p.x >= 0 && p.x < menuItem.getWidth() &&
               p.y >= 0 && p.y < menuItem.getHeight()) {
                doClick(manager);
            } else {
                manager.processMouseEvent(e);
            }
        }
        public void mouseEntered(MouseEvent e) {
            MenuSelectionManager manager = MenuSelectionManager.defaultManager();
            int modifiers = e.getModifiers();
            // 4188027: drag enter/exit added in JDK 1.1.7A, JDK1.2
            if ((modifiers & (InputEvent.BUTTON1_MASK |
                              InputEvent.BUTTON2_MASK | InputEvent.BUTTON3_MASK)) !=0 ) {
                MenuSelectionManager.defaultManager().processMouseEvent(e);
            } else {
            manager.setSelectedPath(getPath());
             }
        }
        public void mouseExited(MouseEvent e) {
            MenuSelectionManager manager = MenuSelectionManager.defaultManager();

            int modifiers = e.getModifiers();
            // 4188027: drag enter/exit added in JDK 1.1.7A, JDK1.2
            if ((modifiers & (InputEvent.BUTTON1_MASK |
                              InputEvent.BUTTON2_MASK | InputEvent.BUTTON3_MASK)) !=0 ) {
                MenuSelectionManager.defaultManager().processMouseEvent(e);
            } else {

                MenuElement path[] = manager.getSelectedPath();
                if (path.length > 1 && path[path.length-1] == menuItem) {
                    MenuElement newPath[] = new MenuElement[path.length-1];
                    int i,c;
                    for(i=0,c=path.length-1;i<c;i++)
                        newPath[i] = path[i];
                    manager.setSelectedPath(newPath);
                }
                }
        }

        public void mouseDragged(MouseEvent e) {
            MenuSelectionManager.defaultManager().processMouseEvent(e);
        }
        public void mouseMoved(MouseEvent e) {
        }

        //
        // MenuDragListener
        //
        public void menuDragMouseEntered(MenuDragMouseEvent e) {
            MenuSelectionManager manager = e.getMenuSelectionManager();
            MenuElement path[] = e.getPath();
            manager.setSelectedPath(path);
        }
        public void menuDragMouseDragged(MenuDragMouseEvent e) {
            MenuSelectionManager manager = e.getMenuSelectionManager();
            MenuElement path[] = e.getPath();
            manager.setSelectedPath(path);
        }
        public void menuDragMouseExited(MenuDragMouseEvent e) {}
        public void menuDragMouseReleased(MenuDragMouseEvent e) {
            if (!menuItem.isEnabled()) {
                return;
            }
            MenuSelectionManager manager = e.getMenuSelectionManager();
            MenuElement path[] = e.getPath();
            Point p = e.getPoint();
            if (p.x >= 0 && p.x < menuItem.getWidth() &&
                    p.y >= 0 && p.y < menuItem.getHeight()) {
                doClick(manager);
            } else {
                manager.clearSelectedPath();
            }
        }


        //
        // PropertyChangeListener
        //
        public void propertyChange(PropertyChangeEvent e) {
            String name = e.getPropertyName();

            if (name == "labelFor" || name == "displayedMnemonic" ||
                name == "accelerator") {
                updateAcceleratorBinding();
            } else if (name == "text" || "font" == name ||
                       "foreground" == name) {
                // remove the old html view client property if one
                // existed, and install a new one if the text installed
                // into the JLabel is html source.
                JMenuItem lbl = ((JMenuItem) e.getSource());
                String text = lbl.getText();
                BasicHTML.updateRenderer(lbl, text);
            } else if (name  == "iconTextGap") {
                defaultTextIconGap = ((Number)e.getNewValue()).intValue();
            }
        }
    }
}
