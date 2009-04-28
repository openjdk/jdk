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

import org.jdesktop.beans.AbstractBean;

import java.awt.Graphics2D;
import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.util.List;

/**
 * SimpleShape
 *
 * @author Created by Jasper Potts (May 22, 2007)
 */
public abstract class SimpleShape extends AbstractBean {

    protected AffineTransform transform = new AffineTransform();
    protected LayerContainer parent = null;

    public void applyTransform(AffineTransform t) {
        transform.concatenate(t);
    }

    public abstract Rectangle2D getBounds(double pixelSize);

    public abstract void paint(Graphics2D g2, double pixelSize);

    public abstract boolean isHit(Point2D p, double pixelSize);

    public boolean intersects(Rectangle2D rect, double pixelSize) {
        return getBounds(pixelSize).intersects(rect);
    }

    public abstract List<? extends ControlPoint> getControlPoints();

    public abstract void paintControls(Graphics2D g2, double pixelSize, boolean paintControlLines);

    public void move(double moveX, double moveY, boolean snapPixels) {
        for (ControlPoint controlPoint : getControlPoints()) {
            controlPoint.move(moveX, moveY, snapPixels);
        }
    }

    public LayerContainer getParent() {
        return parent;
    }

    public void setParent(LayerContainer parent) {
        LayerContainer old = getParent();
        this.parent = parent;
        firePropertyChange("parent", old, getParent());
    }

    public abstract Shape getShape();
}
