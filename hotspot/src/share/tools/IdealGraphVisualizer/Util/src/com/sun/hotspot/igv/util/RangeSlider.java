/*
 * Copyright (c) 1998, 2007, Oracle and/or its affiliates. All rights reserved.
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
package com.sun.hotspot.igv.util;

import com.sun.hotspot.igv.data.ChangedListener;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import java.util.List;
import javax.swing.JComponent;

/**
 *
 * @author Thomas Wuerthinger
 */
public class RangeSlider extends JComponent implements ChangedListener<RangeSliderModel>, MouseListener, MouseMotionListener {

    public static final int HEIGHT = 40;
    public static final int BAR_HEIGHT = 22;
    public static final int BAR_SELECTION_ENDING_HEIGHT = 16;
    public static final int BAR_SELECTION_HEIGHT = 10;
    public static final int BAR_THICKNESS = 2;
    public static final int BAR_CIRCLE_SIZE = 9;
    public static final int MOUSE_ENDING_OFFSET = 3;
    public static final Color BACKGROUND_COLOR = Color.white;
    public static final Color BAR_COLOR = Color.black;
    public static final Color BAR_SELECTION_COLOR = new Color(255, 0, 0, 120);
    public static final Color BAR_SELECTION_COLOR_ROLLOVER = new Color(255, 0, 255, 120);
    public static final Color BAR_SELECTION_COLOR_DRAG = new Color(0, 0, 255, 120);
    private RangeSliderModel model;
    private State state;
    private Point startPoint;
    private RangeSliderModel tempModel;
    private boolean isOverBar;

    private enum State {

        Initial,
        DragBar,
        DragFirstPosition,
        DragSecondPosition
    }

    public RangeSlider() {
        state = State.Initial;
        this.addMouseMotionListener(this);
        this.addMouseListener(this);
    }

    public void setModel(RangeSliderModel newModel) {
        if (model != null) {
            model.getChangedEvent().removeListener(this);
            model.getColorChangedEvent().removeListener(this);
        }
        if (newModel != null) {
            newModel.getChangedEvent().addListener(this);
            newModel.getColorChangedEvent().addListener(this);
        }
        this.model = newModel;
        update();
    }

    private RangeSliderModel getPaintingModel() {
        if (tempModel != null) {
            return tempModel;
        }
        return model;
    }

    @Override
    public Dimension getPreferredSize() {
        Dimension d = super.getPreferredSize();
        d.height = HEIGHT;
        return d;
    }

    public void changed(RangeSliderModel source) {
        update();
    }

    private void update() {
        this.repaint();
    }

    private int getXPosition(int index) {
        assert index >= 0 && index < getPaintingModel().getPositions().size();
        return getXOffset() * (index + 1);
    }

    private int getXOffset() {
        int size = getPaintingModel().getPositions().size();
        int width = getWidth();
        return (width / (size + 1));
    }

    private int getEndXPosition(int index) {
        return getXPosition(index) + getXOffset() / 2;
    }

    private int getStartXPosition(int index) {
        return getXPosition(index) - getXOffset() / 2;
    }

    @Override
    public void paint(Graphics g) {
        super.paint(g);
        Graphics2D g2 = (Graphics2D) g;
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                RenderingHints.VALUE_ANTIALIAS_ON);
        int width = getWidth();
        int height = getHeight();

        g2.setColor(BACKGROUND_COLOR);
        g2.fillRect(0, 0, width, height);

        // Nothing to paint?
        if (getPaintingModel() == null || getPaintingModel().getPositions().size() == 0) {
            return;
        }

        int firstPos = getPaintingModel().getFirstPosition();
        int secondPos = getPaintingModel().getSecondPosition();

        paintSelected(g2, firstPos, secondPos);
        paintBar(g2);

    }

    private int getBarStartY() {
        return getHeight() - BAR_HEIGHT;
    }

    private void paintBar(Graphics2D g) {
        List<String> list = getPaintingModel().getPositions();
        int barStartY = getBarStartY();

        g.setColor(BAR_COLOR);
        g.fillRect(getXPosition(0), barStartY + BAR_HEIGHT / 2 - BAR_THICKNESS / 2, getXPosition(list.size() - 1) - getXPosition(0), BAR_THICKNESS);

        int circleCenterY = barStartY + BAR_HEIGHT / 2;
        for (int i = 0; i < list.size(); i++) {
            int curX = getXPosition(i);
            g.setColor(getPaintingModel().getColors().get(i));
            g.fillOval(curX - BAR_CIRCLE_SIZE / 2, circleCenterY - BAR_CIRCLE_SIZE / 2, BAR_CIRCLE_SIZE, BAR_CIRCLE_SIZE);
            g.setColor(Color.black);
            g.drawOval(curX - BAR_CIRCLE_SIZE / 2, circleCenterY - BAR_CIRCLE_SIZE / 2, BAR_CIRCLE_SIZE, BAR_CIRCLE_SIZE);


            String curS = list.get(i);
            if (curS != null && curS.length() > 0) {
                int startX = getStartXPosition(i);
                int endX = getEndXPosition(i);
                FontMetrics metrics = g.getFontMetrics();
                Rectangle bounds = metrics.getStringBounds(curS, g).getBounds();
                if (bounds.width < endX - startX && bounds.height < barStartY) {
                    g.setColor(Color.black);
                    g.drawString(curS, startX + (endX - startX) / 2 - bounds.width / 2, barStartY / 2 + bounds.height / 2);
                }
            }
        }

    }

    private void paintSelected(Graphics2D g, int start, int end) {

        int startX = getStartXPosition(start);
        int endX = getEndXPosition(end);
        int barStartY = getBarStartY();
        int barSelectionEndingStartY = barStartY + BAR_HEIGHT / 2 - BAR_SELECTION_ENDING_HEIGHT / 2;
        paintSelectedEnding(g, startX, barSelectionEndingStartY);
        paintSelectedEnding(g, endX, barSelectionEndingStartY);

        g.setColor(BAR_SELECTION_COLOR);
        if (state == State.DragBar) {
            g.setColor(BAR_SELECTION_COLOR_DRAG);
        } else if (isOverBar) {
            g.setColor(BAR_SELECTION_COLOR_ROLLOVER);
        }
        g.fillRect(startX, barStartY + BAR_HEIGHT / 2 - BAR_SELECTION_HEIGHT / 2, endX - startX, BAR_SELECTION_HEIGHT);
    }

    private void paintSelectedEnding(Graphics g, int x, int y) {
        g.setColor(BAR_COLOR);
        g.fillRect(x - BAR_THICKNESS / 2, y, BAR_THICKNESS, BAR_SELECTION_ENDING_HEIGHT);
    }

    private boolean isOverSecondPosition(Point p) {
        if (p.y >= getBarStartY()) {
            int destX = getEndXPosition(getPaintingModel().getSecondPosition());
            int off = Math.abs(destX - p.x);
            return off <= MOUSE_ENDING_OFFSET;
        }
        return false;
    }

    private boolean isOverFirstPosition(Point p) {
        if (p.y >= getBarStartY()) {
            int destX = getStartXPosition(getPaintingModel().getFirstPosition());
            int off = Math.abs(destX - p.x);
            return off <= MOUSE_ENDING_OFFSET;
        }
        return false;
    }

    private boolean isOverSelection(Point p) {
        if (p.y >= getBarStartY() && !isOverFirstPosition(p) && !isOverSecondPosition(p)) {
            return p.x > getStartXPosition(getPaintingModel().getFirstPosition()) && p.x < getEndXPosition(getPaintingModel().getSecondPosition());
        }
        return false;
    }

    public void mouseDragged(MouseEvent e) {
        if (state == State.DragBar) {
            int firstX = this.getStartXPosition(model.getFirstPosition());
            int newFirstX = firstX + e.getPoint().x - startPoint.x;
            int newIndex = getIndexFromPosition(newFirstX) + 1;
            if (newIndex + model.getSecondPosition() - model.getFirstPosition() >= model.getPositions().size()) {
                newIndex = model.getPositions().size() - (model.getSecondPosition() - model.getFirstPosition()) - 1;
            }
            int secondPosition = newIndex + model.getSecondPosition() - model.getFirstPosition();
            tempModel.setPositions(newIndex, secondPosition);
            update();
        } else if (state == State.DragFirstPosition) {
            int firstPosition = getIndexFromPosition(e.getPoint().x) + 1;
            int secondPosition = model.getSecondPosition();
            if (firstPosition > secondPosition) {
                firstPosition--;
            }
            tempModel.setPositions(firstPosition, secondPosition);
            update();
        } else if (state == State.DragSecondPosition) {
            int firstPosition = model.getFirstPosition();
            int secondPosition = getIndexFromPosition(e.getPoint().x);
            if (secondPosition < firstPosition) {
                secondPosition++;
            }
            tempModel.setPositions(firstPosition, secondPosition);
            update();
        }
    }

    private int getIndexFromPosition(int x) {
        if (x < getXPosition(0)) {
            return -1;
        }
        for (int i = 0; i < getPaintingModel().getPositions().size() - 1; i++) {
            int startX = getXPosition(i);
            int endX = getXPosition(i + 1);
            if (x >= startX && x <= endX) {
                return i;
            }
        }
        return getPaintingModel().getPositions().size() - 1;
    }

    private int getCircleIndexFromPosition(int x) {
        int result = 0;
        for (int i = 1; i < getPaintingModel().getPositions().size() - 1; i++) {
            if (x > getStartXPosition(i)) {
                result = i;
            }
        }
        return result;
    }

    public void mouseMoved(MouseEvent e) {
        isOverBar = false;
        if (model == null) {
            return;
        }


        Point p = e.getPoint();
        if (isOverFirstPosition(p) || isOverSecondPosition(p)) {
            setCursor(Cursor.getPredefinedCursor(Cursor.E_RESIZE_CURSOR));
        } else if (isOverSelection(p)) {
            isOverBar = true;
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        } else {
            this.setCursor(Cursor.getDefaultCursor());
        }
        repaint();
    }

    public void mouseClicked(MouseEvent e) {
        if (e.getClickCount() > 1) {
            // Double click
            int index = getCircleIndexFromPosition(e.getPoint().x);
            model.setPositions(index, index);
        }
    }

    public void mousePressed(MouseEvent e) {
        if (model == null) {
            return;
        }

        Point p = e.getPoint();
        if (isOverFirstPosition(p)) {
            state = State.DragFirstPosition;
        } else if (isOverSecondPosition(p)) {
            state = State.DragSecondPosition;
        } else if (isOverSelection(p)) {
            state = State.DragBar;
        } else {
            return;
        }

        startPoint = e.getPoint();
        tempModel = model.copy();
    }

    public void mouseReleased(MouseEvent e) {
        if (model == null || tempModel == null) {
            return;
        }
        state = State.Initial;
        model.setPositions(tempModel.getFirstPosition(), tempModel.getSecondPosition());
        tempModel = null;
    }

    public void mouseEntered(MouseEvent e) {
    }

    public void mouseExited(MouseEvent e) {
        isOverBar = false;
        repaint();
    }
}
