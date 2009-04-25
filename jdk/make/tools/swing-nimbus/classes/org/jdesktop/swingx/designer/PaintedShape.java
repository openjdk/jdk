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

import org.jdesktop.swingx.designer.paint.Matte;
import org.jdesktop.swingx.designer.paint.PaintModel;

import javax.swing.UIDefaults;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.LinearGradientPaint;
import java.awt.Paint;
import java.awt.RadialGradientPaint;
import java.awt.Shape;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * PaintedShape
 *
 * @author Created by Jasper Potts (May 22, 2007)
 */
public abstract class PaintedShape extends SimpleShape {

    private PaintModel paint;
    // control points for paint control types
    private DoubleBean px1 = new DoubleBean(0.25);
    private DoubleBean px2 = new DoubleBean(0.75);
    private DoubleBean py1 = new DoubleBean(0);
    private DoubleBean py2 = new DoubleBean(1);
    private ControlPoint ptl = new PaintControlPoint(px1, py1);
    private ControlPoint ptr = new PaintControlPoint(px2, py1);
    private ControlPoint pbl = new PaintControlPoint(px1, py2);
    private ControlPoint pbr = new PaintControlPoint(px2, py2);
    private PropertyChangeListener paintListener = new PropertyChangeListener() {
        public void propertyChange(PropertyChangeEvent evt) {
            firePropertyChange("paint." + evt.getPropertyName(), evt.getOldValue(), evt.getNewValue());
        }
    };

    protected PaintedShape() {
        px1.addPropertyChangeListener(new PropertyChangeListener() {
            public void propertyChange(PropertyChangeEvent evt) {
                firePropertyChange("x1", evt.getOldValue(), evt.getNewValue());
            }
        });
        py1.addPropertyChangeListener(new PropertyChangeListener() {
            public void propertyChange(PropertyChangeEvent evt) {
                firePropertyChange("y1", evt.getOldValue(), evt.getNewValue());
            }
        });
        px2.addPropertyChangeListener(new PropertyChangeListener() {
            public void propertyChange(PropertyChangeEvent evt) {
                firePropertyChange("x2", evt.getOldValue(), evt.getNewValue());
            }
        });
        py2.addPropertyChangeListener(new PropertyChangeListener() {
            public void propertyChange(PropertyChangeEvent evt) {
                firePropertyChange("y2", evt.getOldValue(), evt.getNewValue());
            }
        });
    }

    protected PaintedShape(UIDefaults canvasUiDefaults) {
        this();
        setPaintModel(new Matte(Color.ORANGE, canvasUiDefaults));
    }

    public PaintModel getPaintModel() {
        return paint;
    }

    public void setPaintModel(PaintModel paint) {
        PaintModel old = getPaintModel();
        if (old != null) old.removePropertyChangeListener(paintListener);
        this.paint = paint;
        this.paint.addPropertyChangeListener(paintListener);
        firePropertyChange("paintModel", old, getPaintModel());
    }

    public Paint getPaint() {
        Paint p = getPaintModel().getPaint();
        if (p instanceof Color) {
            return p;
        }
        //resize p as necessary to fit the bounds of this PaintedShape
        Rectangle2D bounds = getBounds(0);
        if (p instanceof LinearGradientPaint) {
            LinearGradientPaint lgp = (LinearGradientPaint) p;
            return new LinearGradientPaint(
                    convertLocalPoint(ptl.getPosition(), bounds),
                    convertLocalPoint(pbr.getPosition(), bounds),
                    lgp.getFractions(),
                    lgp.getColors());
        } else if (p instanceof RadialGradientPaint) {
            RadialGradientPaint rgp = (RadialGradientPaint) p;
            Point2D outer = convertLocalPoint(ptl.getPosition(), bounds);
            Point2D center = convertLocalPoint(pbr.getPosition(), bounds);
            double deltaX = Math.abs(center.getX() - outer.getX());
            double deltaY = Math.abs(center.getY() - outer.getY());
            float radius = (float) Math.sqrt((deltaX * deltaX) + (deltaY * deltaY));
            return new RadialGradientPaint(
                    center,
                    radius,
                    rgp.getFractions(),
                    rgp.getColors());
        } else {
            return p;
        }
    }

    public List<? extends ControlPoint> getControlPoints() {
        switch (paint.getPaintControlType()) {
            case control_line:
                return Arrays.asList(ptl, pbr);
            case control_rect:
                return Arrays.asList(ptl, ptr, pbl, pbr);
            default:
                return Collections.emptyList();
        }
    }

    public void paintFillControls(Graphics2D g2, double pixelSize, boolean paintControlLines) {
        switch (paint.getPaintControlType()) {
            case control_line:
                Point2D p1 = convertLocalPoint(ptl.getPosition(), PaintedShape.this.getBounds(0));
                Point2D p2 = convertLocalPoint(pbr.getPosition(), PaintedShape.this.getBounds(0));
                g2.setStroke(new BasicStroke((float) pixelSize));
                g2.setColor(GraphicsHelper.FILL_LINE);
                g2.draw(new Line2D.Double(p1.getX(), p1.getY(), p2.getX(), p2.getY()));
                ptl.paintControls(g2, pixelSize, true);
                pbr.paintControls(g2, pixelSize, true);
                break;
            case control_rect:
                g2.setStroke(new BasicStroke((float) pixelSize));
                g2.setColor(GraphicsHelper.FILL_LINE);
                g2.draw(new Rectangle2D.Double(
                        px1.getValue(),
                        py1.getValue(),
                        px2.getValue() - px1.getValue(),
                        py2.getValue() - py1.getValue()));
                ptl.paintControls(g2, pixelSize, true);
                ptr.paintControls(g2, pixelSize, true);
                pbl.paintControls(g2, pixelSize, true);
                pbr.paintControls(g2, pixelSize, true);
                break;
        }
    }

    public void move(double moveX, double moveY, boolean snapPixels) {
        for (ControlPoint controlPoint : getControlPoints()) {
            if (!(controlPoint instanceof PaintControlPoint)) controlPoint.move(moveX, moveY, snapPixels);
        }
    }

    public double getPaintX1() {
        return px1.getValue();
    }

    public void setPaintX1(double x1) {
        this.px1.setValue(x1);
    }

    public double getPaintX2() {
        return px2.getValue();
    }

    public void setPaintX2(double x2) {
        this.px2.setValue(x2);
    }

    public double getPaintY1() {
        return py1.getValue();
    }

    public void setPaintY1(double y1) {
        this.py1.setValue(y1);
    }

    public double getPaintY2() {
        return py2.getValue();
    }

    public void setPaintY2(double y2) {
        this.py2.setValue(y2);
    }

    // =================================================================================================================
    // Private helper methods

    private Point2D convertLocalPoint(Point2D point, Rectangle2D bounds) {
        point.setLocation(
                bounds.getX() + (point.getX() * bounds.getWidth()),
                bounds.getY() + (point.getY() * bounds.getHeight())
        );
        return point;
    }

    private Point2D convertScreenPoint(Point2D point, Rectangle2D bounds) {
        return new Point2D.Double(
                (point.getX() - bounds.getX()) / bounds.getWidth(),
                (point.getY() - bounds.getY()) / bounds.getHeight()
        );
    }

    // =================================================================================================================
    // Gradient ControlPoint

    /**
     * A Special ControlPoint thats internal values are in coordinates relative to the shapes bounds. With 0,0 being the
     * top left of the shape and 1.0X == shape width and 1.0Y == shapes height.
     */
    public class PaintControlPoint extends ControlPoint {
        public PaintControlPoint() {
            super(GraphicsHelper.FILL_CP_FILL, GraphicsHelper.FILL_CP_LINE);
        }

        public PaintControlPoint(DoubleBean x, DoubleBean y) {
            super(x, y, GraphicsHelper.FILL_CP_FILL, GraphicsHelper.FILL_CP_LINE);
        }

        public void paintControls(Graphics2D g2, double pixelSize, boolean paintControlLines) {
            Point2D p = convertLocalPoint(getPosition(), PaintedShape.this.getBounds(0));
            g2.setStroke(new BasicStroke((float) pixelSize));
            double size = pixelSize * 4d;
            Shape s = new Ellipse2D.Double(p.getX() - size, p.getY() - size,
                    size * 2, size * 2);
            g2.setPaint(new GradientPaint(
                    (float) p.getX(), (float) (p.getY() - size), Color.CYAN,
                    (float) p.getX(), (float) (p.getY() + size), Color.WHITE
            ));
            g2.fill(s);
            g2.setColor(GraphicsHelper.FILL_CP_LINE);
            g2.draw(s);
        }

        public void move(double moveX, double moveY, boolean snapPixels) {
            Rectangle2D bounds = PaintedShape.this.getBounds(0);
            moveX = moveX / bounds.getWidth();
            moveY = moveY / bounds.getHeight();
            if (snapPixels) {
                // snap to neareast 0.5
                double newX = Math.round((x.getValue() + moveX) * 2d) / 2d;
                double newY = Math.round((y.getValue() + moveY) * 2d) / 2d;
                setPosition(newX, newY);
            } else {
                setPosition(x.getValue() + moveX, y.getValue() + moveY);
            }
        }

        public Rectangle2D getBounds(double pixelSize) {
            Point2D p = convertLocalPoint(getPosition(), PaintedShape.this.getBounds(0));
            double size = pixelSize * 4d;
            return new Rectangle2D.Double(p.getX() - size, p.getY() - size,
                    size * 2, size * 2);
        }
    }
}
