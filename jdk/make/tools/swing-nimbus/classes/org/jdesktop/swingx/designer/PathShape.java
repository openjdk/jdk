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
import java.awt.geom.GeneralPath;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.ArrayList;
import java.util.List;

/**
 * PathShape
 *
 * @author Created by Jasper Potts (May 29, 2007)
 */
public class PathShape extends PaintedShape {

    private Shape cachedShape = null;
    private List<BezierControlPoint> controlPoints = new ArrayList<BezierControlPoint>();
    private PropertyChangeListener cpListener = new PropertyChangeListener() {
        public void propertyChange(PropertyChangeEvent evt) {
            rebuildShape();
        }
    };

    // =================================================================================================================
    // Constructors

    /** private noargs constructor for JIBX */
    private PathShape() {
        this(null);
    }

    public PathShape(UIDefaults canvasUiDefaults) {
        super(canvasUiDefaults);
    }

    public BezierControlPoint addPoint(double x, double y) {
        BezierControlPoint cp = new BezierControlPoint(x, y);
        controlPoints.add(cp);
        cp.addPropertyChangeListener(cpListener);
        // update shape
        rebuildShape();
        // return new control point
        return cp;
    }

    public Shape getShape() {
        if (cachedShape == null) {
            rebuildShape();
        }
        return cachedShape;
    }

    private void rebuildShape() {
        GeneralPath path = new GeneralPath();
        BezierControlPoint first, last;
        first = last = controlPoints.get(0);
        path.moveTo((float) first.getX(), (float) first.getY());
        for (int i = 0; i < controlPoints.size(); i++) {
            BezierControlPoint controlPoint = controlPoints.get(i);
            if (last.getCp2().isSharp() && controlPoint.getCp1().isSharp()) {
                path.lineTo(controlPoint.getX(), controlPoint.getY());
            } else {
                path.curveTo(
                        (float) last.getCp2().getX(), (float) last.getCp2().getY(),
                        (float) controlPoint.getCp1().getX(), (float) controlPoint.getCp1().getY(),
                        (float) controlPoint.getX(), (float) controlPoint.getY()
                );
            }
            last = controlPoint;
        }
        // close path
        if (last.getCp2().isSharp() && first.getCp1().isSharp()) {
            path.lineTo(first.getX(), first.getY());
        } else {
            path.curveTo(
                    (float) last.getCp2().getX(), (float) last.getCp2().getY(),
                    (float) first.getCp1().getX(), (float) first.getCp1().getY(),
                    (float) first.getX(), (float) first.getY()
            );
        }
        path.closePath();
        // fire change
        cachedShape = path;
        firePropertyChange("shape", null, cachedShape);
    }

    @Override
    public String toString() {
        String p = "PATH {\n";
        BezierControlPoint first, last;
        first = last = controlPoints.get(0);
        p += "   path.moveTo(" + first.getX() + "," + first.getY() + ");";
        for (int i = 0; i < controlPoints.size(); i++) {
            BezierControlPoint controlPoint = controlPoints.get(i);
            p += "   path.curveTo(" +
                    (float) last.getCp2().getX() + "," + (float) last.getCp2().getY() + "," +
                    (float) controlPoint.getCp1().getX() + "," + (float) controlPoint.getCp1().getY() + "," +
                    (float) controlPoint.getX() + "," + (float) controlPoint.getY() +
                    ");\n";
            last = controlPoint;
        }
        // close path
        p += "   path.curveTo(" +
                (float) last.getCp2().getX() + "," + (float) last.getCp2().getY() + "," +
                (float) first.getCp1().getX() + "," + (float) first.getCp1().getY() + "," +
                (float) first.getX() + "," + (float) first.getY() +
                ");\n";
        p += "}\n";
        return p;
    }

    // =================================================================================================================
    // Shape Methods

    public Rectangle2D getBounds(double pixelSize) {
        return getShape().getBounds2D();
    }

    public List<? extends ControlPoint> getControlPoints() {
        List<ControlPoint> pts = new ArrayList<ControlPoint>();
        for (BezierControlPoint controlPoint : controlPoints) {
            pts.add(controlPoint);
        }
        for (ControlPoint controlPoint : super.getControlPoints()) {
            pts.add(controlPoint);
        }
        return pts;
    }

    public void setControlPoints(List<BezierControlPoint> controlPoints) {
        List<BezierControlPoint> old = this.controlPoints;
        for (BezierControlPoint cp : old) {
            cp.removePropertyChangeListener(cpListener);
        }
        this.controlPoints = controlPoints;
        for (BezierControlPoint cp : this.controlPoints) {
            cp.addPropertyChangeListener(cpListener);
        }
        // update shape
        rebuildShape();
    }

    public boolean isHit(Point2D p, double pixelSize) {
        return getShape().contains(p);
    }

    public void paint(Graphics2D g2, double pixelSize) {
        g2.setPaint(getPaint());
        g2.fill(getShape());
    }

    public void paintControls(Graphics2D g2, double pixelSize, boolean paintControlLines) {
        if (paintControlLines) {
            g2.setStroke(new BasicStroke((float) pixelSize));
            g2.setColor(GraphicsHelper.CONTROL_LINE);
            g2.draw(getShape());
        }
        for (BezierControlPoint controlPoint : controlPoints) {
            if (!controlPoint.isSharpCorner()) controlPoint.paintControls(g2, pixelSize, true);
        }
    }

    public List<BezierControlPoint> getBezierControlPoints() {
        return controlPoints;
    }
}
