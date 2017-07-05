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
import java.awt.Graphics2D;
import java.awt.Shape;
import java.awt.geom.Ellipse2D;
import java.awt.geom.GeneralPath;
import java.awt.geom.Line2D;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * BezierControlPoint
 *
 * @author Created by Jasper Potts (May 29, 2007)
 */
public class BezierControlPoint extends ControlPoint {
    private HandleControlPoint cp1 = new HandleControlPoint();
    private HandleControlPoint cp2 = new HandleControlPoint();
    private transient boolean makingChange = false;
    private transient PropertyChangeListener cpListener = new PropertyChangeListener() {
        public void propertyChange(PropertyChangeEvent evt) {
//            if (!makingChange) {
//                makingChange = true;
//                if (evt.getSource() == cp1) {
//                    double angle = Math.tan((cp1.getY() - getY())/(cp1.getX() - getX()));
//                    double cp2len = Math.sqrt(
//                            Math.pow(cp2.getX() - getX(),2) +
//                            Math.pow(cp2.getY() - getY(),2)
//                    );
//                    double offsetX = cp2len * Math.sin(angle);
//                    double offsetY = cp2len * Math.cos(angle);
//                    cp2.setPosition(getX() - offsetX, getY() - offsetY);
//                } else {
//                    double angle = Math.tan((cp2.getY() - getY())/(cp2.getX() - getX()));
//                    double cp1len = Math.sqrt(
//                            Math.pow(cp1.getX() - getX(),2) +
//                            Math.pow(cp1.getY() - getY(),2)
//                    );
//                    double offsetX = cp1len * Math.sin(angle);
//                    double offsetY = cp1len * Math.cos(angle);
//                    cp1.setPosition(getX() - offsetX, getY() - offsetY);
//                }
////                if (evt.getSource() == cp1) {
////                    double offsetX = cp1.getX() - getX();
////                    double offsetY = cp1.getY() - getY();
////                    cp2.setPosition(getX() - offsetX, getY() - offsetY);
////                } else {
////                    double offsetX = cp2.getX() - getX();
////                    double offsetY = cp2.getY() - getY();
////                    cp1.setPosition(getX() - offsetX, getY() - offsetY);
////                }
//                makingChange = false;
//                firePropertyChange("cp1", null, cp1);
//                firePropertyChange("cp2", null, cp1);
//            }
            firePropertyChange("shape",null,getShape());
        }
    };

    public BezierControlPoint() {
        cp1.addPropertyChangeListener(cpListener);
        cp2.addPropertyChangeListener(cpListener);
    }

    public BezierControlPoint(double x, double y) {
        super(x, y);
        cp1.addPropertyChangeListener(cpListener);
        cp2.addPropertyChangeListener(cpListener);
        cp1.setPosition(x, y);
        cp2.setPosition(x, y);
    }

    public boolean isSharpCorner() {
        return
                (cp1.getX() == x.getValue()) &&
                        (cp1.getY() == y.getValue()) &&
                        (cp2.getX() == x.getValue()) &&
                        (cp2.getY() == y.getValue());
    }

    public void flip(int width, int height){
        makingChange = true;
        if (width > 0){
            x.setValue(width - x.getValue());
            cp1.x.setValue(width - cp1.x.getValue());
            cp2.x.setValue(width - cp2.x.getValue());
        }
        if (height > 0){
            y.setValue(height - y.getValue());
            cp1.y.setValue(height - cp1.y.getValue());
            cp2.y.setValue(height - cp2.y.getValue());
        }
        makingChange = false;
    }

    public void convertToSharpCorner() {
        cp1.setPosition(x.getValue(), y.getValue());
        cp2.setPosition(x.getValue(), y.getValue());
    }

    public List<ControlPoint> getControlPoints() {
        if (isSharpCorner()) {
            return Collections.emptyList();
        } else {
            List<ControlPoint> points = new ArrayList<ControlPoint>();
            points.add(cp1);
            points.add(cp2);
            return points;
        }
    }

    public HandleControlPoint getCp1() {
        return cp1;
    }

    public HandleControlPoint getCp2() {
        return cp2;
    }

    public void paintControls(Graphics2D g2, double pixelSize, boolean paintControlLines) {
        g2.setStroke(new BasicStroke((float) pixelSize));
        // paint control line
        g2.setColor(GraphicsHelper.BEZIER_CONTROL_LINE);
        g2.draw(new Line2D.Double(cp1.getX(), cp1.getY(), getX(), getY()));
        g2.draw(new Line2D.Double(getX(), getY(), cp2.getX(), cp2.getY()));
        // paint this control point
        Shape s;
        if (isSharpCorner()) {
            double size = pixelSize * 4d;
            GeneralPath path = new GeneralPath();
            path.moveTo(getX() - size, getY());
            path.lineTo(getX(), getY() + size);
            path.lineTo(getX() + size, getY());
            path.lineTo(getX(), getY() - size);
            path.closePath();
            s = path;
        } else {
            double size = pixelSize * 3d;
            s = new Ellipse2D.Double(getX() - size, getY() - size,
                    size * 2, size * 2);
        }
        g2.setColor(GraphicsHelper.BEZIER_CONTROL_POINT_FILL);
        g2.fill(s);
        g2.setColor(GraphicsHelper.BEZIER_CONTROL_POINT_LINE);
        g2.draw(s);
        // paint child control points
        if (!isSharpCorner()) {
            cp1.paintControls(g2, pixelSize, true);
            cp2.paintControls(g2, pixelSize, true);
        }
    }


    public void move(double moveX, double moveY, boolean snapPixels) {
        makingChange = true;
        super.move(moveX, moveY, snapPixels);
        cp1.move(moveX, moveY, snapPixels);
        cp2.move(moveX, moveY, snapPixels);
        makingChange = false;
    }

    public double getCp1X() {
        return cp1.getX();
    }

    public void setCp1X(double v) {
        cp1.setX(v);
    }

    public double getCp1Y() {
        return cp1.getY();
    }

    public void setCp1Y(double v) {
        cp1.setY(v);
    }

    public double getCp2X() {
        return cp2.getX();
    }

    public void setCp2X(double v) {
        cp2.setX(v);
    }

    public double getCp2Y() {
        return cp2.getY();
    }

    public void setCp2Y(double v) {
        cp2.setY(v);
    }

    // =================================================================================================================
    // Bezier handle control point

    public class HandleControlPoint extends ControlPoint {

        public void paintControls(Graphics2D g2, double pixelSize, boolean paintControlLines) {
            if (!isSharp()){
                double size = pixelSize * 3d;
                Shape s = new Ellipse2D.Double(getX() - size, getY() - size,
                        size * 2, size * 2);
                g2.setColor(GraphicsHelper.BEZIER_CONTROL_POINT_FILL);
                g2.fill(s);
                g2.setColor(GraphicsHelper.BEZIER_CONTROL_POINT_LINE);
                g2.draw(s);
                g2.draw(new Rectangle2D.Double(getX() - (pixelSize / 2), getY() - (pixelSize / 2), pixelSize, pixelSize));
            }
        }

        public boolean isHit(Point2D p, double pixelSize) {
            return !isSharp() && super.isHit(p, pixelSize);
        }

        /**
         * Is the line controled by this handle in or out of the parent BezierControlPoint sharp.
         *
         * @return <code>true</code> If this is the exact same point as the parent BezierControlPoint.
         */
        public boolean isSharp(){
            return x.getValue() == BezierControlPoint.this.x.getValue() &&
                y.getValue() == BezierControlPoint.this.y.getValue();
        }

        public void convertToSharp(){
            setPosition(BezierControlPoint.this.x.getValue(),BezierControlPoint.this.y.getValue());
        }

        public BezierControlPoint getParentControlPoint(){
            return BezierControlPoint.this;
        }
    }

}
