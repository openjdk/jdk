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
import java.awt.*;
import java.awt.event.*;
import java.awt.datatransfer.*;
import java.awt.dnd.*;
import java.beans.*;
import java.io.*;
import java.util.*;
import javax.swing.plaf.*;
import javax.swing.plaf.basic.*;
import javax.swing.tree.*;
import javax.swing.text.Position;
import sun.swing.plaf.synth.*;

/**
 * Skinnable TreeUI.
 *
 * @author Scott Violet
 */
class SynthTreeUI extends BasicTreeUI implements PropertyChangeListener,
                               SynthUI {
    private SynthStyle style;
    private SynthStyle cellStyle;

    private SynthContext paintContext;

    private boolean drawHorizontalLines;
    private boolean drawVerticalLines;

    private Object linesStyle;

    private int leadRow;

    private int padding;

    private boolean useTreeColors;

    private Icon expandedIconWrapper;

    public static ComponentUI createUI(JComponent x) {
        return new SynthTreeUI();
    }

    SynthTreeUI() {
        expandedIconWrapper = new ExpandedIconWrapper();
    }

    public Icon getExpandedIcon() {
        return expandedIconWrapper;
    }

    protected void installDefaults() {
        updateStyle(tree);
    }

    private void updateStyle(JTree tree) {
        SynthContext context = getContext(tree, ENABLED);
        SynthStyle oldStyle = style;

        style = SynthLookAndFeel.updateStyle(context, this);
        if (style != oldStyle) {
            Object value;

            setExpandedIcon(style.getIcon(context, "Tree.expandedIcon"));
            setCollapsedIcon(style.getIcon(context, "Tree.collapsedIcon"));

            setLeftChildIndent(style.getInt(context, "Tree.leftChildIndent",
                                            0));
            setRightChildIndent(style.getInt(context, "Tree.rightChildIndent",
                                             0));

            drawHorizontalLines = style.getBoolean(
                          context, "Tree.drawHorizontalLines",true);
            drawVerticalLines = style.getBoolean(
                        context, "Tree.drawVerticalLines", true);
            linesStyle = style.get(context, "Tree.linesStyle");

                value = style.get(context, "Tree.rowHeight");
                if (value != null) {
                    LookAndFeel.installProperty(tree, "rowHeight", value);
                }

                value = style.get(context, "Tree.scrollsOnExpand");
                LookAndFeel.installProperty(tree, "scrollsOnExpand",
                                                    value != null? value : Boolean.TRUE);

            padding = style.getInt(context, "Tree.padding", 0);

            largeModel = (tree.isLargeModel() && tree.getRowHeight() > 0);

            useTreeColors = style.getBoolean(context,
                                  "Tree.rendererUseTreeColors", true);

            Boolean showsRootHandles = style.getBoolean(
                    context, "Tree.showsRootHandles", Boolean.TRUE);
            LookAndFeel.installProperty(
                    tree, JTree.SHOWS_ROOT_HANDLES_PROPERTY, showsRootHandles);

            if (oldStyle != null) {
                uninstallKeyboardActions();
                installKeyboardActions();
            }
        }
        context.dispose();

        context = getContext(tree, Region.TREE_CELL, ENABLED);
        cellStyle = SynthLookAndFeel.updateStyle(context, this);
        context.dispose();
    }

    protected void installListeners() {
        super.installListeners();
        tree.addPropertyChangeListener(this);
    }

    public SynthContext getContext(JComponent c) {
        return getContext(c, getComponentState(c));
    }

    private SynthContext getContext(JComponent c, int state) {
        return SynthContext.getContext(SynthContext.class, c,
                    SynthLookAndFeel.getRegion(c), style, state);
    }

    private Region getRegion(JTree c) {
        return SynthLookAndFeel.getRegion(c);
    }

    private int getComponentState(JComponent c) {
        return SynthLookAndFeel.getComponentState(c);
    }

    private SynthContext getContext(JComponent c, Region region) {
        return getContext(c, region, getComponentState(c, region));
    }

    private SynthContext getContext(JComponent c, Region region, int state) {
        return SynthContext.getContext(SynthContext.class, c,
                                       region, cellStyle, state);
    }

    private int getComponentState(JComponent c, Region region) {
        // Always treat the cell as selected, will be adjusted appropriately
        // when painted.
        return ENABLED | SELECTED;
    }

    protected TreeCellEditor createDefaultCellEditor() {
        TreeCellRenderer renderer = tree.getCellRenderer();
        DefaultTreeCellEditor editor;

        if(renderer != null && (renderer instanceof DefaultTreeCellRenderer)) {
            editor = new SynthTreeCellEditor(tree, (DefaultTreeCellRenderer)
                                             renderer);
        }
        else {
            editor = new SynthTreeCellEditor(tree, null);
        }
        return editor;
    }

    protected TreeCellRenderer createDefaultCellRenderer() {
        return new SynthTreeCellRenderer();
    }

    protected void uninstallDefaults() {
        SynthContext context = getContext(tree, ENABLED);

        style.uninstallDefaults(context);
        context.dispose();
        style = null;

        context = getContext(tree, Region.TREE_CELL, ENABLED);
        cellStyle.uninstallDefaults(context);
        context.dispose();
        cellStyle = null;


        if (tree.getTransferHandler() instanceof UIResource) {
            tree.setTransferHandler(null);
        }
    }

    protected void uninstallListeners() {
        super.uninstallListeners();
        tree.removePropertyChangeListener(this);
    }

    public void update(Graphics g, JComponent c) {
        SynthContext context = getContext(c);

        SynthLookAndFeel.update(context, g);
        context.getPainter().paintTreeBackground(context,
                          g, 0, 0, c.getWidth(), c.getHeight());
        paint(context, g);
        context.dispose();
    }

    public void paintBorder(SynthContext context, Graphics g, int x,
                            int y, int w, int h) {
        context.getPainter().paintTreeBorder(context, g, x, y, w, h);
    }

    public void paint(Graphics g, JComponent c) {
        SynthContext context = getContext(c);

        paint(context, g);
        context.dispose();
    }

    private void updateLeadRow() {
        leadRow = getRowForPath(tree, tree.getLeadSelectionPath());
    }

    protected void paint(SynthContext context, Graphics g) {
        paintContext = context;

        updateLeadRow();

        Rectangle paintBounds = g.getClipBounds();
        Insets insets = tree.getInsets();
        TreePath initialPath = getClosestPathForLocation(tree, 0,
                                                         paintBounds.y);
        Enumeration paintingEnumerator = treeState.getVisiblePathsFrom
                                              (initialPath);
        int row = treeState.getRowForPath(initialPath);
        int endY = paintBounds.y + paintBounds.height;
        TreeModel treeModel = tree.getModel();
        SynthContext cellContext = getContext(tree, Region.TREE_CELL);

        drawingCache.clear();

        setHashColor(context.getStyle().getColor(context,
                                                ColorType.FOREGROUND));

        if (paintingEnumerator != null) {
            // First pass, draw the rows

            boolean done = false;
            boolean isExpanded;
            boolean hasBeenExpanded;
            boolean isLeaf;
            Rectangle boundsBuffer = new Rectangle();
            Rectangle rowBounds = new Rectangle(0, 0, tree.getWidth(),0);
            Rectangle bounds;
            TreePath path;
            TreeCellRenderer renderer = tree.getCellRenderer();
            DefaultTreeCellRenderer dtcr = (renderer instanceof
                       DefaultTreeCellRenderer) ? (DefaultTreeCellRenderer)
                       renderer : null;

            configureRenderer(cellContext);
            while (!done && paintingEnumerator.hasMoreElements()) {
                path = (TreePath)paintingEnumerator.nextElement();
                if (path != null) {
                    isLeaf = treeModel.isLeaf(path.getLastPathComponent());
                    if (isLeaf) {
                        isExpanded = hasBeenExpanded = false;
                    }
                    else {
                        isExpanded = treeState.getExpandedState(path);
                        hasBeenExpanded = tree.hasBeenExpanded(path);
                    }
                    bounds = getPathBounds(tree, path);
                    rowBounds.y = bounds.y;
                    rowBounds.height = bounds.height;
                    paintRow(renderer, dtcr, context, cellContext, g,
                             paintBounds, insets, bounds, rowBounds, path,
                             row, isExpanded, hasBeenExpanded, isLeaf);
                    if ((bounds.y + bounds.height) >= endY) {
                        done = true;
                    }
                }
                else {
                    done = true;
                }
                row++;
            }

            // Draw the connecting lines and controls.
            // Find each parent and have them draw a line to their last child
            boolean rootVisible = tree.isRootVisible();
            TreePath parentPath = initialPath;
            parentPath = parentPath.getParentPath();
            while (parentPath != null) {
                paintVerticalPartOfLeg(g, paintBounds, insets, parentPath);
                drawingCache.put(parentPath, Boolean.TRUE);
                parentPath = parentPath.getParentPath();
            }
            done = false;
            paintingEnumerator = treeState.getVisiblePathsFrom(initialPath);
            while (!done && paintingEnumerator.hasMoreElements()) {
                path = (TreePath)paintingEnumerator.nextElement();
                if (path != null) {
                    isLeaf = treeModel.isLeaf(path.getLastPathComponent());
                    if (isLeaf) {
                        isExpanded = hasBeenExpanded = false;
                    }
                    else {
                        isExpanded = treeState.getExpandedState(path);
                        hasBeenExpanded = tree.hasBeenExpanded(path);
                    }
                    bounds = getPathBounds(tree, path);
                    // See if the vertical line to the parent has been drawn.
                    parentPath = path.getParentPath();
                    if (parentPath != null) {
                        if (drawingCache.get(parentPath) == null) {
                            paintVerticalPartOfLeg(g, paintBounds, insets,
                                                   parentPath);
                            drawingCache.put(parentPath, Boolean.TRUE);
                        }
                        paintHorizontalPartOfLeg(g,
                                                 paintBounds, insets, bounds,
                                                 path, row, isExpanded,
                                                 hasBeenExpanded, isLeaf);
                    }
                    else if (rootVisible && row == 0) {
                        paintHorizontalPartOfLeg(g,
                                                 paintBounds, insets, bounds,
                                                 path, row, isExpanded,
                                                 hasBeenExpanded, isLeaf);
                    }
                    if (shouldPaintExpandControl(path, row, isExpanded,
                                                 hasBeenExpanded, isLeaf)) {
                        paintExpandControl(g, paintBounds,
                                           insets, bounds, path, row,
                                           isExpanded, hasBeenExpanded,isLeaf);
                    }
                    if ((bounds.y + bounds.height) >= endY) {
                        done = true;
                    }
                }
                else {
                    done = true;
                }
                row++;
            }
        }
        cellContext.dispose();

        paintDropLine(g);

        // Empty out the renderer pane, allowing renderers to be gc'ed.
        rendererPane.removeAll();
    }

    private boolean isDropLine(JTree.DropLocation loc) {
        return loc != null && loc.getPath() != null && loc.getChildIndex() != -1;
    }

    private void paintDropLine(Graphics g) {
        JTree.DropLocation loc = tree.getDropLocation();
        if (!isDropLine(loc)) {
            return;
        }

        Color c = (Color)style.get(paintContext, "Tree.dropLineColor");
        if (c != null) {
            g.setColor(c);
            Rectangle rect = getDropLineRect(loc);
            g.fillRect(rect.x, rect.y, rect.width, rect.height);
        }
    }

    private Rectangle getDropLineRect(JTree.DropLocation loc) {
        Rectangle rect;
        TreePath path = loc.getPath();
        int index = loc.getChildIndex();
        boolean ltr = tree.getComponentOrientation().isLeftToRight();

        Insets insets = tree.getInsets();

        if (tree.getRowCount() == 0) {
            rect = new Rectangle(insets.left,
                                 insets.top,
                                 tree.getWidth() - insets.left - insets.right,
                                 0);
        } else {
            int row = tree.getRowForPath(path);
            TreeModel model = getModel();
            Object root = model.getRoot();

            if (path.getLastPathComponent() == root
                    && index >= model.getChildCount(root)) {

                rect = tree.getRowBounds(tree.getRowCount() - 1);
                rect.y = rect.y + rect.height;
                Rectangle xRect;

                if (!tree.isRootVisible()) {
                    xRect = tree.getRowBounds(0);
                } else if (model.getChildCount(root) == 0){
                    xRect = tree.getRowBounds(0);
                    xRect.x += totalChildIndent;
                    xRect.width -= totalChildIndent + totalChildIndent;
                } else {
                    TreePath lastChildPath = path.pathByAddingChild(
                        model.getChild(root, model.getChildCount(root) - 1));
                    xRect = tree.getPathBounds(lastChildPath);
                }

                rect.x = xRect.x;
                rect.width = xRect.width;
            } else {
                rect = tree.getPathBounds(path.pathByAddingChild(
                    model.getChild(path.getLastPathComponent(), index)));
            }
        }

        if (rect.y != 0) {
            rect.y--;
        }

        if (!ltr) {
            rect.x = rect.x + rect.width - 100;
        }

        rect.width = 100;
        rect.height = 2;

        return rect;
    }

    private void configureRenderer(SynthContext context) {
        TreeCellRenderer renderer = tree.getCellRenderer();

        if (renderer instanceof DefaultTreeCellRenderer) {
            DefaultTreeCellRenderer r = (DefaultTreeCellRenderer)renderer;
            SynthStyle style = context.getStyle();

            context.setComponentState(ENABLED | SELECTED);
            Color color = r.getTextSelectionColor();
            if (color == null || (color instanceof UIResource)) {
                r.setTextSelectionColor(style.getColor(
                                     context, ColorType.TEXT_FOREGROUND));
            }
            color = r.getBackgroundSelectionColor();
            if (color == null || (color instanceof UIResource)) {
                r.setBackgroundSelectionColor(style.getColor(
                                        context, ColorType.TEXT_BACKGROUND));
            }

            context.setComponentState(ENABLED);
            color = r.getTextNonSelectionColor();
            if (color == null || color instanceof UIResource) {
                r.setTextNonSelectionColor(style.getColorForState(
                                        context, ColorType.TEXT_FOREGROUND));
            }
            color = r.getBackgroundNonSelectionColor();
            if (color == null || color instanceof UIResource) {
                r.setBackgroundNonSelectionColor(style.getColorForState(
                                  context, ColorType.TEXT_BACKGROUND));
            }
        }
    }

    protected void paintHorizontalPartOfLeg(Graphics g, Rectangle clipBounds,
                                            Insets insets, Rectangle bounds,
                                            TreePath path, int row,
                                            boolean isExpanded,
                                            boolean hasBeenExpanded, boolean
                                            isLeaf) {
        if (drawHorizontalLines) {
            super.paintHorizontalPartOfLeg(g, clipBounds, insets, bounds,
                                           path, row, isExpanded,
                                           hasBeenExpanded, isLeaf);
        }
    }

    protected void paintHorizontalLine(Graphics g, JComponent c, int y,
                                      int left, int right) {
        paintContext.getStyle().getGraphicsUtils(paintContext).drawLine(
            paintContext, "Tree.horizontalLine", g, left, y, right, y, linesStyle);
    }

    protected void paintVerticalPartOfLeg(Graphics g,
                                          Rectangle clipBounds, Insets insets,
                                          TreePath path) {
        if (drawVerticalLines) {
            super.paintVerticalPartOfLeg(g, clipBounds, insets, path);
        }
    }

    protected void paintVerticalLine(Graphics g, JComponent c, int x, int top,
                                    int bottom) {
        paintContext.getStyle().getGraphicsUtils(paintContext).drawLine(
            paintContext, "Tree.verticalLine", g, x, top, x, bottom, linesStyle);
    }

    protected void paintRow(TreeCellRenderer renderer,
               DefaultTreeCellRenderer dtcr, SynthContext treeContext,
               SynthContext cellContext, Graphics g, Rectangle clipBounds,
               Insets insets, Rectangle bounds, Rectangle rowBounds,
               TreePath path, int row, boolean isExpanded,
               boolean hasBeenExpanded, boolean isLeaf) {
        // Don't paint the renderer if editing this row.
        boolean selected = tree.isRowSelected(row);

        JTree.DropLocation dropLocation = tree.getDropLocation();
        boolean isDrop = dropLocation != null
                         && dropLocation.getChildIndex() == -1
                         && path == dropLocation.getPath();

        if (selected || isDrop) {
            cellContext.setComponentState(ENABLED | SELECTED);
        }
        else {
            cellContext.setComponentState(ENABLED);
        }
        if (dtcr != null && (dtcr.getBorderSelectionColor() instanceof
                             UIResource)) {
            dtcr.setBorderSelectionColor(style.getColor(
                                             cellContext, ColorType.FOCUS));
        }
        SynthLookAndFeel.updateSubregion(cellContext, g, rowBounds);
        cellContext.getPainter().paintTreeCellBackground(cellContext, g,
                    rowBounds.x, rowBounds.y, rowBounds.width,
                    rowBounds.height);
        cellContext.getPainter().paintTreeCellBorder(cellContext, g,
                    rowBounds.x, rowBounds.y, rowBounds.width,
                    rowBounds.height);
        if (editingComponent != null && editingRow == row) {
            return;
        }

        int leadIndex;

        if (tree.hasFocus()) {
            leadIndex = leadRow;
        }
        else {
            leadIndex = -1;
        }

        Component component = renderer.getTreeCellRendererComponent(
                         tree, path.getLastPathComponent(),
                         selected, isExpanded, isLeaf, row,
                         (leadIndex == row));

        rendererPane.paintComponent(g, component, tree, bounds.x, bounds.y,
                                    bounds.width, bounds.height, true);
    }

    private int findCenteredX(int x, int iconWidth) {
        return tree.getComponentOrientation().isLeftToRight()
               ? x - (int)Math.ceil(iconWidth / 2.0)
               : x - (int)Math.floor(iconWidth / 2.0);
    }

    protected void drawCentered(Component c, Graphics graphics, Icon icon,
                                int x, int y) {
        int w = SynthIcon.getIconWidth(icon, paintContext);
        int h = SynthIcon.getIconHeight(icon, paintContext);

        SynthIcon.paintIcon(icon, paintContext, graphics,
                            findCenteredX(x, w),
                            y - h/2, w, h);
    }

    public void propertyChange(PropertyChangeEvent event) {
        if (SynthLookAndFeel.shouldUpdateStyle(event)) {
            updateStyle((JTree)event.getSource());
        }

        if ("dropLocation" == event.getPropertyName()) {
            JTree.DropLocation oldValue = (JTree.DropLocation)event.getOldValue();
            repaintDropLocation(oldValue);
            repaintDropLocation(tree.getDropLocation());
        }
    }

    private void repaintDropLocation(JTree.DropLocation loc) {
        if (loc == null) {
            return;
        }

        Rectangle r;

        if (isDropLine(loc)) {
            r = getDropLineRect(loc);
        } else {
            r = tree.getPathBounds(loc.getPath());
            if (r != null) {
                r.x = 0;
                r.width = tree.getWidth();
            }
        }

        if (r != null) {
            tree.repaint(r);
        }
    }

    protected int getRowX(int row, int depth) {
        return super.getRowX(row, depth) + padding;
    }


    private class SynthTreeCellRenderer extends DefaultTreeCellRenderer
                               implements UIResource {
        SynthTreeCellRenderer() {
        }
        public String getName() {
            return "Tree.cellRenderer";
        }
        public Component getTreeCellRendererComponent(JTree tree, Object value,
                                                      boolean sel,
                                                      boolean expanded,
                                                      boolean leaf, int row,
                                                      boolean hasFocus) {
            if (!useTreeColors && (sel || hasFocus)) {
                SynthLookAndFeel.setSelectedUI((SynthLabelUI)SynthLookAndFeel.
                             getUIOfType(getUI(), SynthLabelUI.class),
                                   sel, hasFocus, tree.isEnabled(), false);
            }
            else {
                SynthLookAndFeel.resetSelectedUI();
            }
            return super.getTreeCellRendererComponent(tree, value, sel,
                                                      expanded, leaf, row, hasFocus);
        }
        public void paint(Graphics g) {
            paintComponent(g);
            if (hasFocus) {
                SynthContext context = getContext(tree, Region.TREE_CELL);

                if (context.getStyle() == null) {
                    assert false: "SynthTreeCellRenderer is being used " +
                        "outside of UI that created it";
                    return;
                }
                int imageOffset = 0;
                Icon currentI = getIcon();

                if(currentI != null && getText() != null) {
                    imageOffset = currentI.getIconWidth() +
                                          Math.max(0, getIconTextGap() - 1);
                }
                if (selected) {
                    context.setComponentState(ENABLED | SELECTED);
                }
                else {
                    context.setComponentState(ENABLED);
                }
                if(getComponentOrientation().isLeftToRight()) {
                    context.getPainter().paintTreeCellFocus(context, g,
                            imageOffset, 0, getWidth() - imageOffset,
                            getHeight());
                }
                else {
                    context.getPainter().paintTreeCellFocus(context, g,
                            0, 0, getWidth() - imageOffset, getHeight());
                }
                context.dispose();
            }
            SynthLookAndFeel.resetSelectedUI();
        }
    }


    private static class SynthTreeCellEditor extends DefaultTreeCellEditor {
        public SynthTreeCellEditor(JTree tree,
                                   DefaultTreeCellRenderer renderer) {
            super(tree, renderer);
            setBorderSelectionColor(null);
        }

        protected TreeCellEditor createTreeCellEditor() {
            JTextField tf = new JTextField() {
                public String getName() {
                    return "Tree.cellEditor";
                }
            };
            DefaultCellEditor editor = new DefaultCellEditor(tf);

            // One click to edit.
            editor.setClickCountToStart(1);
            return editor;
        }
    }


    //
    // BasicTreeUI directly uses expandIcon outside of the Synth methods.
    // To get the correct context we return an instance of this that fetches
    // the SynthContext as needed.
    //
    private class ExpandedIconWrapper extends SynthIcon {
        public void paintIcon(SynthContext context, Graphics g, int x,
                              int y, int w, int h) {
            if (context == null) {
                context = getContext(tree);
                SynthIcon.paintIcon(expandedIcon, context, g, x, y, w, h);
                context.dispose();
            }
            else {
                SynthIcon.paintIcon(expandedIcon, context, g, x, y, w, h);
            }
        }

        public int getIconWidth(SynthContext context) {
            int width;
            if (context == null) {
                context = getContext(tree);
                width = SynthIcon.getIconWidth(expandedIcon, context);
                context.dispose();
            }
            else {
                width = SynthIcon.getIconWidth(expandedIcon, context);
            }
            return width;
        }

        public int getIconHeight(SynthContext context) {
            int height;
            if (context == null) {
                context = getContext(tree);
                height = SynthIcon.getIconHeight(expandedIcon, context);
                context.dispose();
            }
            else {
                height = SynthIcon.getIconHeight(expandedIcon, context);
            }
            return height;
        }
    }
}
