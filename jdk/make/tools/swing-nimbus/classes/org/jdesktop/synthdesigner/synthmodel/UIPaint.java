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

import org.jdesktop.swingx.designer.paint.Matte;
import org.jdesktop.swingx.designer.paint.PaintModel;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;

/**
 * UIPaint
 *
 * @author  Richard Bair
 * @author  Jasper Potts
 */
public class UIPaint extends UIDefault<PaintModel> {

    /** Listener to keep model UiDefaults up to date for this UiPaint */
    private PropertyChangeListener matteListener = new PropertyChangeListener() {
        public void propertyChange(PropertyChangeEvent evt) {
            PaintModel paintModel = getValue();
            if (paintModel instanceof Matte) {
                getUiDefaults().put(getName(), ((Matte) paintModel).getColor());
            }
            // propogate the paint change up as PaintModel is a mutable object
            if (evt.getPropertyName().equals("paint")) {
                firePropertyChange("paint", null, getPaint());
                firePropertyChange("value", null, getPaint());
            }
        }
    };

    public UIPaint() {}

    public UIPaint(String id, PaintModel value) {
        super(id, value, (value instanceof Matte) ? ((Matte) value).getUiDefaults() : null);
        // update model defaults
        if (value instanceof Matte) {
            Matte matte = (Matte) value;
            if (getUiDefaults() != null) getUiDefaults().put(getName(), matte.getColor());
            matte.addPropertyChangeListener(matteListener);
        }
    }

    public PaintModel getPaint() {
        return super.getValue();
    }

    public void setPaint(PaintModel c) {
        PaintModel old = getPaint();
        if (old instanceof Matte) old.removePropertyChangeListener(matteListener);
        super.setValue(c);
        firePropertyChange("paint", old, c);
        // update model defaults
        if (c instanceof Matte) {
            Matte matte = (Matte) c;
            getUiDefaults().put(getName(), matte.getColor());
            matte.addPropertyChangeListener(matteListener);
        }
    }
}
