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
package org.jdesktop.swingx.designer;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.ArrayList;
import java.util.List;

/**
 * RectangleShape
 *
 * @author Created by Jasper Potts (May 22, 2007)
 */
public class RectangleShape extends PaintedShape {

    private DoubleBean x1 = new DoubleBean();
    private DoubleBean x2 = new DoubleBean();
    private DoubleBean y1 = new DoubleBean();
    private DoubleBean y2 = new DoubleBean();
    private ControlPoint tl = new ControlPoint(x1, y1);
    private ControlPoint tr = new ControlPoint(x2, y1);
    private ControlPoint bl = new ControlPoint(x1, y2);
    private ControlPoint br = new ControlPoint(x2, y2);
    private DoubleBean roundingX = new DoubleBean() {
        public void setValue(double value) {
            // contrain y = y1 and x is between x1+1 and (x2-x1)/2
            boolean x1isLess = x1.getValue() < x2.getValue();
            double min = x1isLess ? x1.getValue() + 1 : x1.getValue() - 1;
            double max = x1isLess ? x1.getValue() + ((x2.getValue() - x1.getValue()) / 2) :
                    x2.getValue() + ((x1.getValue() - x2.getValue()) / 2);
            double newX = value;
            if (newX < min) newX = min;
            if (newX > max) newX = max;
            super.setValue(newX);
        }
    };
    private ControlPoint rounding = new ControlPoint(roundingX, y1) {
        public void paintControls(Graphics2D g2, double pixelSize, boolean paintControlLines) {
            double size = pixelSize * 3d;
            Shape s = new Ellipse2D.Double(getX() - size, getY() - size,
                    size * 2, size * 2);
            g2.setColor(GraphicsHelper.BEZIER_CONTROL_POINT_FILL);
            g2.fill(s);
            g2.setColor(GraphicsHelper.BEZIER_CONTROL_POINT_LINE);
            g2.draw(s);
        }

        public void setPosition(Point2D position) {
            // only alow X to change
            x.setValue(position.getX());
        }

    };

    // =================================================================================================================
    // Constructors

    /** private noargs constructor for JIBX */
    private RectangleShape() {
        this(null);
    }

    public RectangleShape(UIDefaults canvasUiDefaults) {
        super(canvasUiDefaults);
        x1.addPropertyChangeListener(new PropertyChangeListener() {
            public void propertyChange(PropertyChangeEvent evt) {
                // keep rounding point in sync
                roundingX.setValue(roundingX.getValue() +
                        ((Double) evt.getNewValue() - (Double) evt.getOldValue()));
                firePropertyChange("bounds", null, getBounds(0));
            }
        });
        x2.addPropertyChangeListener(new PropertyChangeListener() {
            public void propertyChange(PropertyChangeEvent evt) {
                // keep rounding point in sync
                double distanceFromX1 = Math.abs(roundingX.getValue() - x1.getValue());
                roundingX.setValue(
                        (x1.getValue() < x2.getValue()) ? x1.getValue() + distanceFromX1 :
                                x1.getValue() - distanceFromX1
                );
                firePropertyChange("bounds", null, getBounds(0));
            }
        });
        PropertyChangeListener listener = new PropertyChangeListener() {
            public void propertyChange(PropertyChangeEvent evt) {
                firePropertyChange("bounds", null, getBounds(0));
            }
        };
        y1.addPropertyChangeListener(listener);
        y2.addPropertyChangeListener(listener);
        rounding.addPropertyChangeListener(listener);
    }

    public RectangleShape(double x, double y, double w, double h) {
        this();
        x1.setValue(x);
        y1.setValue(y);
        x2.setValue(x + w);
        y2.setValue(y + h);
    }

    public Shape getShape() {
        double rounding = getRounding();
        double left = Math.min(x1.getValue(), x2.getValue());
        double right = Math.max(x1.getValue(), x2.getValue());
        double top = Math.min(y1.getValue(), y2.getValue());
        double bottom = Math.max(y1.getValue(), y2.getValue());
        if (rounding > 0) {
            return new RoundRectangle2D.Double(
                    left, top, right - left, bottom - top, rounding, rounding
            );
        } else {
            return new Rectangle2D.Double(left, top, right - left, bottom - top);
        }
    }

    public double getRounding() {
        double rounding = Math.abs(roundingX.getValue() - x1.getValue()) * 2;
        return rounding > 2 ? rounding : 0;
    }

    public void setRounding(double rounding) {
        if (rounding > 0 && rounding < 2) rounding = 0;
        roundingX.setValue((rounding / 2d) + x1.getValue());
    }

    public boolean isRounded() {
        return getRounding() > 0;
    }

    public double getX1() {
        return x1.getValue();
    }

    public void setX1(double x1) {
        this.x1.setValue(x1);
    }

    public double getX2() {
        return x2.getValue();
    }

    public void setX2(double x2) {
        this.x2.setValue(x2);
    }

    public double getY1() {
        return y1.getValue();
    }

    public void setY1(double y1) {
        this.y1.setValue(y1);
    }

    public double getY2() {
        return y2.getValue();
    }

    public void setY2(double y2) {
        this.y2.setValue(y2);
    }

    // =================================================================================================================
    // SimpleShape Methods

    public Rectangle2D getBounds(double pixelSize) {
        double left = Math.min(x1.getValue(), x2.getValue());
        double right = Math.max(x1.getValue(), x2.getValue());
        double top = Math.min(y1.getValue(), y2.getValue());
        double bottom = Math.max(y1.getValue(), y2.getValue());
        return new Rectangle2D.Double(left, top, right - left, bottom - top);
    }

    public boolean isHit(Point2D p, double pixelSize) {
        return getShape().contains(p);
    }

    public void paint(Graphics2D g2, double pixelSize) {
        g2.setPaint(getPaint());
        g2.fill(getShape());
    }

    public void setFrame(double x1, double y1, double x2, double y2) {
        this.x1.setValue(x1);
        this.y1.setValue(y1);
        this.x2.setValue(x2);
        this.y2.setValue(y2);
    }

    @Override
    public String toString() {
        Rectangle2D bounds = getBounds(0);
        if (isRounded()) {
            return "ROUND RECT { x=" +  bounds.getX() + ", y=" + bounds.getY() + ", w=" + bounds.getWidth() + ", h=" + bounds.getHeight() + ", rounding=" + getRounding() + " }";
        } else {
            return "ROUND RECT { x=" +  bounds.getX() + ", y=" + bounds.getY() + ", w=" + bounds.getWidth() + ", h=" + bounds.getHeight() + " }";
        }
    }

    public List<ControlPoint> getControlPoints() {
        List<ControlPoint> points = new ArrayList<ControlPoint>();
        points.addAll(super.getControlPoints());
        points.add(tl);
        points.add(tr);
        points.add(bl);
        points.add(br);
        points.add(rounding);
        return points;
    }

    public void paintControls(Graphics2D g2, double pixelSize, boolean paintControlLines) {
        if (paintControlLines) {
            g2.setStroke(new BasicStroke((float) pixelSize));
            g2.setColor(GraphicsHelper.CONTROL_LINE);
            g2.draw(getShape());
        }
        tl.paintControls(g2, pixelSize, true);
        tr.paintControls(g2, pixelSize, true);
        bl.paintControls(g2, pixelSize, true);
        br.paintControls(g2, pixelSize, true);
        rounding.paintControls(g2, pixelSize, true);
    }

    public void move(double moveX, double moveY, boolean snapPixels) {
        if (snapPixels) {
            x1.setValue(Math.round(x1.getValue() + moveX));
            x2.setValue(Math.round(x2.getValue() + moveX));
            y1.setValue(Math.round(y1.getValue() + moveY));
            y2.setValue(Math.round(y2.getValue() + moveY));
        } else {
            x1.setValue(x1.getValue() + moveX);
            x2.setValue(x2.getValue() + moveX);
            y1.setValue(y1.getValue() + moveY);
            y2.setValue(y2.getValue() + moveY);
        }
    }
}
