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
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.ArrayList;
import java.util.List;

/**
 * EllipseShape
 *
 * @author Created by Jasper Potts (May 22, 2007)
 */
public class EllipseShape extends PaintedShape {

    private DoubleBean x1 = new DoubleBean();
    private DoubleBean x2 = new DoubleBean();
    private DoubleBean y1 = new DoubleBean();
    private DoubleBean y2 = new DoubleBean();
    private ControlPoint tl = new ControlPoint(x1, y1);
    private ControlPoint tr = new ControlPoint(x2, y1);
    private ControlPoint bl = new ControlPoint(x1, y2);
    private ControlPoint br = new ControlPoint(x2, y2);

    // =================================================================================================================
    // Constructors

    /** private noargs constructor for JIBX */
    private EllipseShape() {
        this(null);
    }

    public EllipseShape(UIDefaults canvasUiDefaults) {
        super(canvasUiDefaults);
        PropertyChangeListener listener = new PropertyChangeListener() {
            public void propertyChange(PropertyChangeEvent evt) {
                firePropertyChange("bounds", null, getBounds(0));
            }
        };
        x1.addPropertyChangeListener(listener);
        y1.addPropertyChangeListener(listener);
        x2.addPropertyChangeListener(listener);
        y2.addPropertyChangeListener(listener);
    }

    public EllipseShape(double x, double y, double w, double h) {
        this();
        x1.setValue(x);
        y1.setValue(y);
        x2.setValue(x + w);
        y2.setValue(y + h);
    }

    public Rectangle2D getBounds(double pixelSize) {
        double left = Math.min(x1.getValue(), x2.getValue());
        double right = Math.max(x1.getValue(), x2.getValue());
        double top = Math.min(y1.getValue(), y2.getValue());
        double bottom = Math.max(y1.getValue(), y2.getValue());
        return new Rectangle2D.Double(left, top, right - left, bottom - top);
    }

    public Ellipse2D getShape() {
        double left = Math.min(x1.getValue(), x2.getValue());
        double right = Math.max(x1.getValue(), x2.getValue());
        double top = Math.min(y1.getValue(), y2.getValue());
        double bottom = Math.max(y1.getValue(), y2.getValue());
        return new Ellipse2D.Double(left, top, right - left, bottom - top);
    }

    public boolean isHit(Point2D p, double pixelSize) {
        return getBounds(pixelSize).contains(p);
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
        return "ELLIPSE { x=" +  bounds.getX() + ", y=" + bounds.getY() + ", w=" + bounds.getWidth() + ", h=" + bounds.getHeight() + " }";
    }

    public List<ControlPoint> getControlPoints() {
        List<ControlPoint> points = new ArrayList<ControlPoint>();
        points.addAll(super.getControlPoints());
        points.add(tl);
        points.add(tr);
        points.add(bl);
        points.add(br);
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
//        super.paintControls(g2, pixelSize, paintControlLines);
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

}
