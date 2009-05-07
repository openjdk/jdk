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
package org.jdesktop.swingx.designer.paint;

import org.jdesktop.beans.AbstractBean;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;

/** Each stop is defined linearly, at positions between 0 and 1. */
public final class GradientStop extends AbstractBean implements Cloneable {
    private float position;
    private Matte color;
    private PropertyChangeListener matteListener = new PropertyChangeListener() {
        public void propertyChange(PropertyChangeEvent evt) {
            firePropertyChange("color", null, color);
        }
    };

    /**
     * The midpoint to the right of the stop. Must be 0 &lt;= midpoint &lt;= 1. The midpoint value of the last Stop is
     * ignored.
     */
    private float midpoint;

    public GradientStop() {}

    public GradientStop(float position, Matte color) {
        if (color == null) {
            throw new IllegalArgumentException("Color must not be null");
        }

        this.position = clamp(0, 1, position);
        this.color = color;
        this.midpoint = .5f;

        if (this.color != null) {
            this.color.addPropertyChangeListener("color", matteListener);
        }
    }


    public GradientStop clone() {
        GradientStop clone = new GradientStop(this.position, this.color.clone());
        clone.midpoint = midpoint;
        return clone;
    }

    public final float getPosition() {
        return position;
    }

    public final void setPosition(float position) {
        float old = this.position;
        this.position = clamp(0, 1, position);
        firePropertyChange("position", old, this.position);
    }

    public final Matte getColor() {
        return color;
    }

    public final void setColor(Matte c) {
        if (c == null) throw new IllegalArgumentException("Color must not be null");
        Matte old = this.color;
        if (old != null) old.removePropertyChangeListener(matteListener);
        this.color = c;
        if (this.color != null) this.color.addPropertyChangeListener(matteListener);
        firePropertyChange("color", old, c);
    }

    public final void setOpacity(int opacity) {
        int old = getOpacity();
        color.setAlpha(opacity);
        firePropertyChange("opacity", old, getOpacity());
    }

    public final int getOpacity() {
        return color.getAlpha();
    }

    public final float getMidpoint() {
        return midpoint;
    }

    public final void setMidpoint(float midpoint) {
        float old = this.midpoint;
        this.midpoint = clamp(0, 1, midpoint);
        firePropertyChange("midpoint", old, this.midpoint);
    }

    private float clamp(float lo, float hi, float value) {
        if (value < lo) {
            return lo;
        } else if (value > hi) {
            return hi;
        } else {
            return value;
        }
    }
}
