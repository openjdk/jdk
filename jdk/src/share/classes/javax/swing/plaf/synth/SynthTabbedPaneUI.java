/*
 * Copyright 2002-2007 Sun Microsystems, Inc.  All Rights Reserved.
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
import javax.swing.text.View;

import java.awt.*;
import java.awt.event.*;
import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeEvent;
import java.util.Vector;
import java.util.Hashtable;
import sun.swing.plaf.synth.SynthUI;
import sun.swing.SwingUtilities2;

/**
 * A Synth L&F implementation of TabbedPaneUI.
 *
 * @author Scott Violet
 */
/**
 * Looks up 'selectedTabPadInsets' from the Style, which will be additional
 * insets for the selected tab.
 */
class SynthTabbedPaneUI extends BasicTabbedPaneUI implements SynthUI, PropertyChangeListener  {
    private SynthContext tabAreaContext;
    private SynthContext tabContext;
    private SynthContext tabContentContext;

    private SynthStyle style;
    private SynthStyle tabStyle;
    private SynthStyle tabAreaStyle;
    private SynthStyle tabContentStyle;

    private Rectangle textRect;
    private Rectangle iconRect;

    private Rectangle tabAreaBounds = new Rectangle();

    public static ComponentUI createUI(JComponent c) {
        return new SynthTabbedPaneUI();
    }

    SynthTabbedPaneUI() {
        textRect = new Rectangle();
        iconRect = new Rectangle();
    }

    private boolean scrollableTabLayoutEnabled() {
        return (tabPane.getTabLayoutPolicy() == JTabbedPane.SCROLL_TAB_LAYOUT);
    }

    protected void installDefaults() {
        updateStyle(tabPane);
    }

    private void updateStyle(JTabbedPane c) {
        SynthContext context = getContext(c, ENABLED);
        SynthStyle oldStyle = style;
        style = SynthLookAndFeel.updateStyle(context, this);
        // Add properties other than JComponent colors, Borders and
        // opacity settings here:
        if (style != oldStyle) {
            tabRunOverlay =
                style.getInt(context, "TabbedPane.tabRunOverlay", 0);
            textIconGap = style.getInt(context, "TabbedPane.textIconGap", 0);
            selectedTabPadInsets = (Insets)style.get(context,
                "TabbedPane.selectedTabPadInsets");
            if (selectedTabPadInsets == null) {
                selectedTabPadInsets = new Insets(0, 0, 0, 0);
            }
            if (oldStyle != null) {
                uninstallKeyboardActions();
                installKeyboardActions();
            }
        }
        context.dispose();

        if (tabContext != null) {
            tabContext.dispose();
        }
        tabContext = getContext(c, Region.TABBED_PANE_TAB, ENABLED);
        this.tabStyle = SynthLookAndFeel.updateStyle(tabContext, this);
        tabInsets = tabStyle.getInsets(tabContext, null);


        if (tabAreaContext != null) {
            tabAreaContext.dispose();
        }
        tabAreaContext = getContext(c, Region.TABBED_PANE_TAB_AREA, ENABLED);
        this.tabAreaStyle = SynthLookAndFeel.updateStyle(tabAreaContext, this);
        tabAreaInsets = tabAreaStyle.getInsets(tabAreaContext, null);


        if (tabContentContext != null) {
            tabContentContext.dispose();
        }
        tabContentContext = getContext(c, Region.TABBED_PANE_CONTENT, ENABLED);
        this.tabContentStyle = SynthLookAndFeel.updateStyle(tabContentContext,
                                                            this);
        contentBorderInsets =
            tabContentStyle.getInsets(tabContentContext, null);
    }

    protected void installListeners() {
        super.installListeners();
        tabPane.addPropertyChangeListener(this);
    }

    protected void uninstallListeners() {
        super.uninstallListeners();
        tabPane.removePropertyChangeListener(this);
    }

    protected void uninstallDefaults() {
        SynthContext context = getContext(tabPane, ENABLED);
        style.uninstallDefaults(context);
        context.dispose();
        style = null;

        tabStyle.uninstallDefaults(tabContext);
        tabContext.dispose();
        tabContext = null;
        tabStyle = null;

        tabAreaStyle.uninstallDefaults(tabAreaContext);
        tabAreaContext.dispose();
        tabAreaContext = null;
        tabAreaStyle = null;

        tabContentStyle.uninstallDefaults(tabContentContext);
        tabContentContext.dispose();
        tabContentContext = null;
        tabContentStyle = null;
    }

    public SynthContext getContext(JComponent c) {
        return getContext(c, getComponentState(c));
    }

    public SynthContext getContext(JComponent c, int state) {
        return SynthContext.getContext(SynthContext.class, c,
                    SynthLookAndFeel.getRegion(c),style, state);
    }

    public SynthContext getContext(JComponent c, Region subregion) {
        return getContext(c, subregion, getComponentState(c));
    }

    private SynthContext getContext(JComponent c, Region subregion, int state){
        SynthStyle style = null;
        Class klass = SynthContext.class;

        if (subregion == Region.TABBED_PANE_TAB) {
            style = tabStyle;
        }
        else if (subregion == Region.TABBED_PANE_TAB_AREA) {
            style = tabAreaStyle;
        }
        else if (subregion == Region.TABBED_PANE_CONTENT) {
            style = tabContentStyle;
        }
        return SynthContext.getContext(klass, c, subregion, style, state);
    }

    private Region getRegion(JComponent c) {
        return SynthLookAndFeel.getRegion(c);
    }

    private int getComponentState(JComponent c) {
        return SynthLookAndFeel.getComponentState(c);
    }

    protected JButton createScrollButton(int direction) {
        return new SynthScrollableTabButton(direction);
    }

    public void propertyChange(PropertyChangeEvent e) {
        if (SynthLookAndFeel.shouldUpdateStyle(e)) {
            updateStyle(tabPane);
        }
    }

    public void update(Graphics g, JComponent c) {
        SynthContext context = getContext(c);

        SynthLookAndFeel.update(context, g);
        context.getPainter().paintTabbedPaneBackground(context,
                          g, 0, 0, c.getWidth(), c.getHeight());
        paint(context, g);
        context.dispose();
    }

    protected int getBaseline(int tab) {
        if (tabPane.getTabComponentAt(tab) != null ||
                getTextViewForTab(tab) != null) {
            return super.getBaseline(tab);
        }
        String title = tabPane.getTitleAt(tab);
        Font font = tabContext.getStyle().getFont(tabContext);
        FontMetrics metrics = getFontMetrics(font);
        Icon icon = getIconForTab(tab);
        textRect.setBounds(0, 0, 0, 0);
        iconRect.setBounds(0, 0, 0, 0);
        calcRect.setBounds(0, 0, Short.MAX_VALUE, maxTabHeight);
        tabContext.getStyle().getGraphicsUtils(tabContext).layoutText(
                tabContext, metrics, title, icon, SwingUtilities.CENTER,
                SwingUtilities.CENTER, SwingUtilities.LEADING,
                SwingUtilities.CENTER, calcRect,
                iconRect, textRect, textIconGap);
        return textRect.y + metrics.getAscent() + getBaselineOffset();
    }

    public void paintBorder(SynthContext context, Graphics g, int x,
                            int y, int w, int h) {
        context.getPainter().paintTabbedPaneBorder(context, g, x, y, w, h);
    }

    public void paint(Graphics g, JComponent c) {
        SynthContext context = getContext(c);

        paint(context, g);
        context.dispose();
    }

    protected void paint(SynthContext context, Graphics g) {
        int selectedIndex = tabPane.getSelectedIndex();
        int tabPlacement = tabPane.getTabPlacement();

        ensureCurrentLayout();

        // Paint tab area
        // If scrollable tabs are enabled, the tab area will be
        // painted by the scrollable tab panel instead.
        //
        if (!scrollableTabLayoutEnabled()) { // WRAP_TAB_LAYOUT
            Insets insets = tabPane.getInsets();
            int x = insets.left;
            int y = insets.top;
            int width = tabPane.getWidth() - insets.left - insets.right;
            int height = tabPane.getHeight() - insets.top - insets.bottom;
            int size;
            switch(tabPlacement) {
            case LEFT:
                width = calculateTabAreaWidth(tabPlacement, runCount,
                                              maxTabWidth);
                break;
            case RIGHT:
                size = calculateTabAreaWidth(tabPlacement, runCount,
                                             maxTabWidth);
                x = x + width - size;
                width = size;
                break;
            case BOTTOM:
                size = calculateTabAreaHeight(tabPlacement, runCount,
                                              maxTabHeight);
                y = y + height - size;
                height = size;
                break;
            case TOP:
            default:
                height = calculateTabAreaHeight(tabPlacement, runCount,
                                                maxTabHeight);
            }

            tabAreaBounds.setBounds(x, y, width, height);

            if (g.getClipBounds().intersects(tabAreaBounds)) {
                paintTabArea(tabAreaContext, g, tabPlacement,
                         selectedIndex, tabAreaBounds);
            }
        }

        // Paint content border
        paintContentBorder(tabContentContext, g, tabPlacement, selectedIndex);
    }


    protected void paintTabArea(Graphics g, int tabPlacement,
                                int selectedIndex) {
        // This can be invoked from ScrollabeTabPanel
        Insets insets = tabPane.getInsets();
        int x = insets.left;
        int y = insets.top;
        int width = tabPane.getWidth() - insets.left - insets.right;
        int height = tabPane.getHeight() - insets.top - insets.bottom;

        paintTabArea(tabAreaContext, g, tabPlacement, selectedIndex,
                     new Rectangle(x, y, width, height));
    }

    protected void paintTabArea(SynthContext ss, Graphics g,
                                int tabPlacement, int selectedIndex,
                                Rectangle tabAreaBounds) {
        Rectangle clipRect = g.getClipBounds();

        // Paint the tab area.
        SynthLookAndFeel.updateSubregion(ss, g, tabAreaBounds);
        ss.getPainter().paintTabbedPaneTabAreaBackground(ss, g,
             tabAreaBounds.x, tabAreaBounds.y, tabAreaBounds.width,
             tabAreaBounds.height, tabPlacement);
        ss.getPainter().paintTabbedPaneTabAreaBorder(ss, g, tabAreaBounds.x,
             tabAreaBounds.y, tabAreaBounds.width, tabAreaBounds.height,
             tabPlacement);

        int tabCount = tabPane.getTabCount();

        iconRect.setBounds(0, 0, 0, 0);
        textRect.setBounds(0, 0, 0, 0);

        // Paint tabRuns of tabs from back to front
        for (int i = runCount - 1; i >= 0; i--) {
            int start = tabRuns[i];
            int next = tabRuns[(i == runCount - 1)? 0 : i + 1];
            int end = (next != 0? next - 1: tabCount - 1);
            for (int j = start; j <= end; j++) {
                if (rects[j].intersects(clipRect) && selectedIndex != j) {
                    paintTab(tabContext, g, tabPlacement, rects, j, iconRect,
                             textRect);
                }
            }
        }

        if (selectedIndex >= 0) {
            if (rects[selectedIndex].intersects(clipRect)) {
                paintTab(tabContext, g, tabPlacement, rects, selectedIndex,
                         iconRect, textRect);
            }
        }
    }

    protected void setRolloverTab(int index) {
        int oldRolloverTab = getRolloverTab();
        super.setRolloverTab(index);

        Rectangle r = null;

        if ((oldRolloverTab >= 0) && (oldRolloverTab < tabPane.getTabCount())) {
            r = getTabBounds(tabPane, oldRolloverTab);
            if (r != null) {
                tabPane.repaint(r);
            }
        }

        if (index >= 0) {
            r = getTabBounds(tabPane, index);
            if (r != null) {
                tabPane.repaint(r);
            }
        }
    }

    protected void paintTab(SynthContext ss, Graphics g,
                            int tabPlacement, Rectangle[] rects, int tabIndex,
                            Rectangle iconRect, Rectangle textRect) {
        Rectangle tabRect = rects[tabIndex];
        int selectedIndex = tabPane.getSelectedIndex();
        boolean isSelected = selectedIndex == tabIndex;
        updateTabContext(tabIndex, isSelected,
                          (getRolloverTab() == tabIndex),
                          (getFocusIndex() == tabIndex));

        SynthLookAndFeel.updateSubregion(ss, g, tabRect);
        tabContext.getPainter().paintTabbedPaneTabBackground(tabContext,
                                g, tabRect.x, tabRect.y, tabRect.width,
                                tabRect.height, tabIndex,
                                tabPane.getTabPlacement());
        tabContext.getPainter().paintTabbedPaneTabBorder(tabContext, g,
                   tabRect.x, tabRect.y, tabRect.width, tabRect.height,
                   tabIndex, tabPane.getTabPlacement());

        if (tabPane.getTabComponentAt(tabIndex) == null) {
            String title = tabPane.getTitleAt(tabIndex);
            Font font = ss.getStyle().getFont(ss);
            FontMetrics metrics = SwingUtilities2.getFontMetrics(tabPane, g, font);
            Icon icon = getIconForTab(tabIndex);

            layoutLabel(ss, tabPlacement, metrics, tabIndex, title, icon,
                    tabRect, iconRect, textRect, isSelected);

            paintText(ss, g, tabPlacement, font, metrics,
                    tabIndex, title, textRect, isSelected);

            paintIcon(g, tabPlacement, tabIndex, icon, iconRect, isSelected);
        }
    }

    protected void layoutLabel(SynthContext ss, int tabPlacement,
                               FontMetrics metrics, int tabIndex,
                               String title, Icon icon,
                               Rectangle tabRect, Rectangle iconRect,
                               Rectangle textRect, boolean isSelected ) {
        View v = getTextViewForTab(tabIndex);
        if (v != null) {
            tabPane.putClientProperty("html", v);
        }

        textRect.x = textRect.y = iconRect.x = iconRect.y = 0;

        ss.getStyle().getGraphicsUtils(ss).layoutText(ss, metrics, title,
                         icon, SwingUtilities.CENTER, SwingUtilities.CENTER,
                         SwingUtilities.LEADING, SwingUtilities.CENTER,
                         tabRect, iconRect, textRect, textIconGap);

        tabPane.putClientProperty("html", null);

        int xNudge = getTabLabelShiftX(tabPlacement, tabIndex, isSelected);
        int yNudge = getTabLabelShiftY(tabPlacement, tabIndex, isSelected);
        iconRect.x += xNudge;
        iconRect.y += yNudge;
        textRect.x += xNudge;
        textRect.y += yNudge;
    }

    protected void paintText(SynthContext ss,
                             Graphics g, int tabPlacement,
                             Font font, FontMetrics metrics, int tabIndex,
                             String title, Rectangle textRect,
                             boolean isSelected) {
        g.setFont(font);

        View v = getTextViewForTab(tabIndex);
        if (v != null) {
            // html
            v.paint(g, textRect);
        } else {
            // plain text
            int mnemIndex = tabPane.getDisplayedMnemonicIndexAt(tabIndex);

            g.setColor(ss.getStyle().getColor(ss, ColorType.TEXT_FOREGROUND));
            ss.getStyle().getGraphicsUtils(ss).paintText(ss, g, title,
                                  textRect, mnemIndex);
        }
    }


    protected void paintContentBorder(SynthContext ss, Graphics g,
                                      int tabPlacement, int selectedIndex) {
        int width = tabPane.getWidth();
        int height = tabPane.getHeight();
        Insets insets = tabPane.getInsets();

        int x = insets.left;
        int y = insets.top;
        int w = width - insets.right - insets.left;
        int h = height - insets.top - insets.bottom;

        switch(tabPlacement) {
          case LEFT:
              x += calculateTabAreaWidth(tabPlacement, runCount, maxTabWidth);
              w -= (x - insets.left);
              break;
          case RIGHT:
              w -= calculateTabAreaWidth(tabPlacement, runCount, maxTabWidth);
              break;
          case BOTTOM:
              h -= calculateTabAreaHeight(tabPlacement, runCount, maxTabHeight);
              break;
          case TOP:
          default:
              y += calculateTabAreaHeight(tabPlacement, runCount, maxTabHeight);
              h -= (y - insets.top);
        }
        SynthLookAndFeel.updateSubregion(ss, g, new Rectangle(x, y, w, h));
        ss.getPainter().paintTabbedPaneContentBackground(ss, g, x, y,
                                                           w, h);
        ss.getPainter().paintTabbedPaneContentBorder(ss, g, x, y, w, h);
    }

    private void ensureCurrentLayout() {
        if (!tabPane.isValid()) {
            tabPane.validate();
        }
        /* If tabPane doesn't have a peer yet, the validate() call will
         * silently fail.  We handle that by forcing a layout if tabPane
         * is still invalid.  See bug 4237677.
         */
        if (!tabPane.isValid()) {
            TabbedPaneLayout layout = (TabbedPaneLayout)tabPane.getLayout();
            layout.calculateLayoutInfo();
        }
    }


    protected int calculateMaxTabHeight(int tabPlacement) {
        FontMetrics metrics = getFontMetrics(tabContext.getStyle().getFont(
                                             tabContext));
        int tabCount = tabPane.getTabCount();
        int result = 0;
        int fontHeight = metrics.getHeight();
        for(int i = 0; i < tabCount; i++) {
            result = Math.max(calculateTabHeight(tabPlacement, i, fontHeight), result);
        }
        return result;
    }

    protected int calculateTabWidth(int tabPlacement, int tabIndex,
                                    FontMetrics metrics) {
        Icon icon = getIconForTab(tabIndex);
        Insets tabInsets = getTabInsets(tabPlacement, tabIndex);
        int width = tabInsets.left + tabInsets.right;
        Component tabComponent = tabPane.getTabComponentAt(tabIndex);
        if (tabComponent != null) {
            width += tabComponent.getPreferredSize().width;
        } else {
            if (icon != null) {
                width += icon.getIconWidth() + textIconGap;
            }
            View v = getTextViewForTab(tabIndex);
            if (v != null) {
                // html
                width += (int) v.getPreferredSpan(View.X_AXIS);
            } else {
                // plain text
                String title = tabPane.getTitleAt(tabIndex);
                width += tabContext.getStyle().getGraphicsUtils(tabContext).
                        computeStringWidth(tabContext, metrics.getFont(),
                                metrics, title);
            }
        }
        return width;
    }

    protected int calculateMaxTabWidth(int tabPlacement) {
        FontMetrics metrics = getFontMetrics(tabContext.getStyle().getFont(
                                     tabContext));
        int tabCount = tabPane.getTabCount();
        int result = 0;
        for(int i = 0; i < tabCount; i++) {
            result = Math.max(calculateTabWidth(tabPlacement, i, metrics),
                              result);
        }
        return result;
    }

    protected Insets getTabInsets(int tabPlacement, int tabIndex) {
        updateTabContext(tabIndex, false, false,
                          (getFocusIndex() == tabIndex));
        return tabInsets;
    }

    protected FontMetrics getFontMetrics() {
        return getFontMetrics(tabContext.getStyle().getFont(tabContext));
    }

    protected FontMetrics getFontMetrics(Font font) {
        return tabPane.getFontMetrics(font);
    }

    private void updateTabContext(int index, boolean selected,
                                  boolean isMouseOver, boolean hasFocus) {
        int state = 0;
        if (!tabPane.isEnabled() || !tabPane.isEnabledAt(index)) {
            state |= SynthConstants.DISABLED;
            if (selected) {
                state |= SynthConstants.SELECTED;
            }
        }
        else if (selected) {
            state |= (SynthConstants.ENABLED | SynthConstants.SELECTED);
            if (isMouseOver && UIManager.getBoolean("TabbedPane.isTabRollover")) {
                state |= SynthConstants.MOUSE_OVER;
            }
        }
        else if (isMouseOver) {
            state |= (SynthConstants.ENABLED | SynthConstants.MOUSE_OVER);
        }
        else {
            state = SynthLookAndFeel.getComponentState(tabPane);
            state &= ~SynthConstants.FOCUSED; // don't use tabbedpane focus state
        }
        if (hasFocus && tabPane.hasFocus()) {
            state |= SynthConstants.FOCUSED; // individual tab has focus
        }
        tabContext.setComponentState(state);
    }

    private class SynthScrollableTabButton extends SynthArrowButton implements
            UIResource {
        public SynthScrollableTabButton(int direction) {
            super(direction);
        }
    }
}
