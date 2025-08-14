/*
 * Copyright (c) 2008, 2025, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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
 *
 */
package com.sun.hotspot.igv.view.widgets;

import com.sun.hotspot.igv.graph.Block;
import com.sun.hotspot.igv.data.InputBlock;
import com.sun.hotspot.igv.data.services.InputGraphProvider;
import com.sun.hotspot.igv.util.DoubleClickHandler;
import com.sun.hotspot.igv.util.LookupHistory;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.awt.geom.Rectangle2D;
import java.util.List;
import org.netbeans.api.visual.action.WidgetAction;
import org.netbeans.api.visual.widget.Scene;
import org.netbeans.api.visual.widget.Widget;
import org.openide.util.Utilities;

/**
 *
 * @author Thomas Wuerthinger
 */
public class BlockWidget extends Widget implements DoubleClickHandler {

    public static final Color BACKGROUND_COLOR = new Color(235, 235, 255);
    private static final Font TITLE_FONT = new Font("Arial", Font.BOLD, 14);
    public static final Color TITLE_COLOR = new Color(42, 42, 171);
    private final Block block;
    private static final Font LIVE_RANGE_FONT = new Font("Arial", Font.BOLD, 12);
    public static final Color LIVE_RANGE_COLOR = Color.BLACK;
    private int nodeWidth;
    private List<Integer> liveRangeIds;

    public BlockWidget(Scene scene, Block block) {
        super(scene);
        this.block = block;
        this.setBackground(BACKGROUND_COLOR);
        this.setOpaque(true);
        this.setCheckClipping(true);
    }

    public void setLiveRangeIds(List<Integer> liveRangeIds) {
        this.liveRangeIds = liveRangeIds;
    }

    public void setNodeWidth(int nodeWidth) {
        this.nodeWidth = nodeWidth;
    }

    @Override
    protected void paintWidget() {
        super.paintWidget();
        Graphics2D g = this.getGraphics();
        Stroke old = g.getStroke();
        g.setColor(Color.BLUE);
        Rectangle r = new Rectangle(this.getPreferredBounds());
        r.width--;
        r.height--;
        if (this.getBounds().width > 0 && this.getBounds().height > 0) {
            g.setStroke(new BasicStroke(2));
            g.drawRect(r.x, r.y, r.width, r.height);
        }

        g.setColor(TITLE_COLOR);
        g.setFont(TITLE_FONT);

        String s = "B" + getBlockNode().getName();
        Rectangle2D r1 = g.getFontMetrics().getStringBounds(s, g);
        g.drawString(s, r.x + 5, r.y + (int) r1.getHeight());

        g.setColor(LIVE_RANGE_COLOR);
        g.setFont(LIVE_RANGE_FONT);
        if (liveRangeIds != null) {
            int x = nodeWidth + block.getLiveRangeSeparation();
            for (int liveRangeId : liveRangeIds) {
                String ls = "L" + String.valueOf(liveRangeId);
                Rectangle2D lr = g.getFontMetrics().getStringBounds(ls, g);
                g.drawString(ls, r.x + x, r.y + (int) lr.getHeight() + 2);
                x += block.getLiveRangeSeparation();
            }
        }

        g.setStroke(old);
    }

    private void addToSelection(BlockWidget blockWidget, boolean additiveSelection) {
        InputGraphProvider graphProvider = LookupHistory.getLast(InputGraphProvider.class);
        if (graphProvider != null) {
            if (!additiveSelection) {
                graphProvider.clearSelectedElements();
            }
            graphProvider.addSelectedNodes(blockWidget.getBlockNode().getNodes(), false);
        }
    }

    public void updatePosition() {
        setPreferredLocation(block.getPosition());
    }

    public InputBlock getBlockNode() {
        return block.getInputBlock();
    }

    private int getModifierMask () {
        return Utilities.isMac() ? MouseEvent.META_DOWN_MASK : MouseEvent.CTRL_DOWN_MASK;
    }

    @Override
    public void handleDoubleClick(Widget widget, WidgetAction.WidgetMouseEvent event) {
        assert widget instanceof BlockWidget;
        BlockWidget blockWidget = (BlockWidget) widget;
        boolean additiveSelection = (event.getModifiersEx() & getModifierMask()) != 0;
        addToSelection(blockWidget, additiveSelection);
    }
}
