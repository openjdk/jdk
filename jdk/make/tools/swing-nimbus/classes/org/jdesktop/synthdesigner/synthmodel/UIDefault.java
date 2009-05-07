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
import org.jdesktop.swingx.designer.utils.HasUIDefaults;
import org.jibx.runtime.IUnmarshallingContext;

import javax.swing.UIDefaults;

/**
 * Represents an entry in the UI defaults table.
 *
 * @author  Richard Bair
 * @author  Jasper Potts
 */
public class UIDefault<T> extends AbstractBean implements HasUIDefaults {
    private String name;
    private T value;
    /**
     * This is a local UIDefaults that contains all the UIDefaults in the synth model. It is kept uptodate by the
     * indervidual UIDefaults nodes
     */
    private transient UIDefaults modelDefaults = null;

    public UIDefault() {
    }

    public UIDefault(String name, T value) {
        this.name = name;
        this.value = value;
    }

    public UIDefault(String name, T value, UIDefaults modelDefaults) {
        this.name = name;
        this.value = value;
        this.modelDefaults = modelDefaults;
    }

    // =================================================================================================================
    // JIBX Methods

    /**
     * Called by JIBX after all fields have been set
     *
     * @param context The JIBX Unmarshalling Context
     */
    private void postSet(IUnmarshallingContext context) {
        // walk up till we get synth model
        for (int i = 0; i < context.getStackDepth(); i++) {
            if (context.getStackObject(i) instanceof HasUIDefaults) {
                modelDefaults = ((HasUIDefaults) context.getStackObject(i)).getUiDefaults();
                if (modelDefaults != null) break;
            }
        }
    }

    // =================================================================================================================
    // Bean Methods

    /**
     * Get the local UIDefaults that contains all the UIDefaults in the synth model. It is kept uptodate by the
     * indervidual UIDefaults nodes
     *
     * @return The UIDefaults for the synth model
     */
    public UIDefaults getUiDefaults() {
        return modelDefaults;
    }

    public void setValue(T t) {
        T old = this.value;
        this.value = t;
        firePropertyChange("value", old, getValue());
    }

    public T getValue() {
        return value;
    }

    public final String getName() {
        return name;
    }

    public void setName(String name) {
        String old = this.name;
        firePropertyChange("name", old, name);
        this.name = name;
        // update model defaults
        if (old != null) modelDefaults.remove(old);
        modelDefaults.put(getName(), getValue());
    }

}
