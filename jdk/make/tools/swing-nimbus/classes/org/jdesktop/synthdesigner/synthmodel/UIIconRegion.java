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

/**
 * A UIRegion subclass which is used for generating icons. For example, JRadioButton and JCheckBox represent themselves
 * mainly via their icons. However, from the designers perspective, the main design isn't an "icon", but just a region
 * on the button.
 * <p/>
 * That type of region is represented by a UIIconRegion. UIIconRegion contains a string which references the UIDefault
 * value associated with this icon. For example, RadioButton.icon.
 *
 * @author  Richard Bair
 * @author  Jasper Potts
 */
public class UIIconRegion extends UIRegion {
    /** The UiDefaults key which this icon should be stored for basic LaF to find it. This is absolute */
    private String basicKey = null;

    public UIIconRegion() {
        super();
    }

    public String getBasicKey() {
        return basicKey;
    }

    public void setBasicKey(String basicKey) {
        String old = getBasicKey();
        this.basicKey = basicKey;
        firePropertyChange("basicKey",old,getBasicKey());
    }
}
