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

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Shape;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.Collections;
import java.util.List;

/**
 * ControlPoint
 *
 * @author Created by Jasper Potts (May 24, 2007)
 */
public class ControlPoint extends SimpleShape {
    protected Color fillColor;
    protected Color lineColor;
    protected DoubleBean x, y;

    public ControlPoint() {
        this(new DoubleBean(), new DoubleBean());
    }

    public ControlPoint(Color fillColor, Color lineColor) {
        this(new DoubleBean(), new DoubleBean(), fillColor, lineColor);
    }

    public ControlPoint(double x, double y) {
        this(new DoubleBean(x), new DoubleBean(y));
    }

    public ControlPoint(DoubleBean x, DoubleBean y) {
        this(x, y, GraphicsHelper.CONTROL_POINT_FILL, GraphicsHelper.CONTROL_POINT_LINE);
    }

    public ControlPoint(DoubleBean x, DoubleBean y, Color fillColor, Color lineColor) {
        this.x = x;
        this.y = y;
        this.fillColor = fillColor;
        this.lineColor = lineColor;
        x.addPropertyChangeListener(new PropertyChangeListener() {
            public void propertyChange(PropertyChangeEvent evt) {
                firePropertyChange("position",
                        new Point2D.Double((Double) evt.getOldValue(), getY()),
                        getPosition());
            }
        });
        y.addPropertyChangeListener(new PropertyChangeListener() {
            public void propertyChange(PropertyChangeEvent evt) {
                firePropertyChange("position",
                        new Point2D.Double(getX(), (Double) evt.getOldValue()),
                        getPosition());
            }
        });
    }

    public double getX() {
        return x.getValue();
    }

    public double getY() {
        return y.getValue();
    }

    public void setX(double x) {
        this.x.setValue(x);
    }

    public void setY(double y) {
        this.y.setValue(y);
    }

    public void setPosition(Point2D position) {
        x.setValue(position.getX());
        y.setValue(position.getY());
    }

    public void setPosition(double x, double y) {
        setPosition(new Point2D.Double(x, y));
    }

    public Point2D getPosition() {
        return new Point2D.Double(getX(), getY());
    }

    public Rectangle2D getBounds(double pixelSize) {
        double size = pixelSize * 4d;
        return new Rectangle2D.Double(getX() - size, getY() - size,
                size * 2, size * 2);
    }

    public boolean isHit(Point2D p, double pixelSize) {
        return getBounds(pixelSize).contains(p);
    }


    public Shape getShape() {
        return getBounds(0);
    }

    public void paint(Graphics2D g2, double pixelSize) {
    }

    public void paintControls(Graphics2D g2, double pixelSize, boolean paintControlLines) {
        g2.setStroke(new BasicStroke((float) pixelSize));
        Shape s = getBounds(pixelSize);
        g2.setColor(fillColor);
        g2.fill(s);
        g2.setColor(lineColor);
        g2.draw(s);
    }

    public List<ControlPoint> getControlPoints() {
        return Collections.emptyList();
    }


    public void move(double moveX, double moveY, boolean snapPixels) {
        if (snapPixels) {
            setPosition(
                    Math.round(x.getValue() + moveX),
                    Math.round(y.getValue() + moveY));
        } else {
            setPosition(x.getValue() + moveX, y.getValue() + moveY);
        }
    }


}
