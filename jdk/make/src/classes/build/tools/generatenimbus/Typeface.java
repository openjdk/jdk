/*
 * Copyright (c) 2002, 2013, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

package build.tools.generatenimbus;

import java.awt.Font;

import javax.xml.bind.annotation.XmlAttribute;

class Typeface {
    public enum DeriveStyle {
        Default, Off, On;

        @Override public String toString() {
            switch (this) {
                default:  return "null";
                case On:  return "true";
                case Off: return "false";
            }
        }
    }

    @XmlAttribute private String uiDefaultParentName;
    @XmlAttribute(name="family") private String name;
    @XmlAttribute private int size;
    @XmlAttribute private DeriveStyle bold = DeriveStyle.Default;
    @XmlAttribute private DeriveStyle italic = DeriveStyle.Default;
    @XmlAttribute private float sizeOffset = 1f;

    public boolean isAbsolute() {
        return uiDefaultParentName == null;
    }

    public String write() {
        if (isAbsolute()) {
            int style = Font.PLAIN;
            if (bold == DeriveStyle.On) {
                style = style | Font.BOLD;
            }
            if (italic == DeriveStyle.On) {
                style = style | Font.ITALIC;
            }

            return String.format(
                    "new javax.swing.plaf.FontUIResource(\"%s\", %d, %d)",
                    name, style, size);
        } else {
            return String.format(
                    "new DerivedFont(\"%s\", %sf, %s, %s)",
                    uiDefaultParentName, String.valueOf(sizeOffset), bold, italic);
        }
    }
}
