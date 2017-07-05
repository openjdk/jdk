/*
 * Copyright 2005-2006 Sun Microsystems, Inc.  All Rights Reserved.
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
package org.jdesktop.synthdesigner.synthmodel;

import org.jdesktop.beans.AbstractBean;

/**
 * UIProperty
 *
 * @author  Richard Bair
 * @author  Jasper Potts
 */
public class UIProperty extends AbstractBean {
    public static enum PropertyType {
        BOOLEAN, INT, FLOAT, DOUBLE, STRING, FONT, COLOR, INSETS, DIMENSION, BORDER
    }

    private String name;
    private PropertyType type;
    private Object value;

    protected UIProperty() {
    }

    public UIProperty(String name, PropertyType type, Object value) {
        this.name = name;
        this.type = type;
        this.value = value;
    }

    // =================================================================================================================
    // Bean Methods

    public String getName() {
        return name;
    }

    public void setName(String name) {
        String old = getName();
        this.name = name;
        firePropertyChange("name", old, getName());
    }

    public PropertyType getType() {
        return type;
    }

    public void setType(PropertyType type) {
        PropertyType old = getType();
        this.type = type;
        firePropertyChange("type", old, getType());
    }

    public Object getValue() {
        return value;
    }

    public void setValue(Object value) {
        Object old = getValue();
        this.value = value;
        firePropertyChange("value", old, getValue());
    }
}
