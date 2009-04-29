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

import java.awt.Font;
import org.jdesktop.swingx.designer.font.Typeface;
import java.util.Arrays;
import java.util.List;
import java.util.ArrayList;
import javax.swing.UIDefaults;

/**
 * Represents a single font entry in the UIDefaults table. Each UIFont takes a
 * list of Typefaces. These typefaces are listed by order of preference. Thus,
 * when putting a font into UIDefaults, the code can check whether each font
 * exists, and when it finds the first font that does, insert it.
 *
 * @author  Richard Bair
 * @author  Jasper Potts
 */
public class UIFont extends UIDefault<List<Typeface>> implements Cloneable {

    private void updateUIDefaults() {
        if (getUiDefaults() != null) {
            for (Typeface t : getFonts()) {
                if (t.isFontSupported()) {
                    getUiDefaults().put(getName(), t.getFont());
                    return;
                }
            }
        }

        //TODO must not have found any. Default to the Default platform font
        getUiDefaults().put(getName(), new Font("Arial", Font.PLAIN, 12));
    }

    public UIFont() {
        setValue(new ArrayList<Typeface>());
    }

    public UIFont(String id, List<Typeface> values, UIDefaults defaults) {
        super(id, values, defaults);
        updateUIDefaults();
    }

    public UIFont(String id, Font font, UIDefaults modelDefaults) {
        this(id, Arrays.asList(new Typeface(font, modelDefaults)), modelDefaults);
    }

    public List<Typeface> getFonts() {
        return super.getValue();
    }

    private void setFonts(List<Typeface> values) {
        super.setValue(values);
    }
}
