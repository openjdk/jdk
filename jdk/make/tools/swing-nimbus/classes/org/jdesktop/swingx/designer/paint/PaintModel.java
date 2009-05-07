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

import java.awt.Paint;

/**
 * I'd have just called it Paint, but sadly, that name was already taken, and would have been too confusing.
 * <p/>
 * Whenever size or position values are required (for example with Texture or Gradient), they are specified in the unit
 * square: that is, between 0 and 1 inclusive. They can then later be scaled as necessary by any painting code.
 *
 * @author rbair
 */
public abstract class PaintModel extends AbstractBean implements Cloneable {
    public static enum PaintControlType {
        none, control_line, control_rect
    }

    protected PaintModel() { }

    /**
     * @return an instance of Paint that is represented by this PaintModel. This is often not a reversable operation,
     *         and hence there is no "setPaint" method. Rather, tweaking the exposed properties of the PaintModel fires,
     *         when necessary, property change events for the "paint" property, and results in different values returned
     *         from this method.
     */
    public abstract Paint getPaint();

    /**
     * Get the type of controls for this paint model
     *
     * @return The type of paint controls, one of PaintControlType.none, PaintControlType.control_line or
     *         PaintControlType.control_rect
     */
    public abstract PaintControlType getPaintControlType();


    public abstract PaintModel clone();
}
