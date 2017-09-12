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

import javax.xml.bind.annotation.XmlAttribute;

class Matte extends Paint {
    @XmlAttribute private int red;
    @XmlAttribute private int green;
    @XmlAttribute private int blue;
    @XmlAttribute private int alpha;

    @XmlAttribute private String uiDefaultParentName = null;
    @XmlAttribute private float hueOffset = 0;
    @XmlAttribute private float saturationOffset = 0;
    @XmlAttribute private float brightnessOffset = 0;
    @XmlAttribute private int alphaOffset = 0;

    @XmlAttribute private String componentPropertyName = null;
    public String getComponentPropertyName() { return componentPropertyName; }

    @XmlAttribute private boolean uiResource = true;

    public boolean isAbsolute() {
        return uiDefaultParentName == null;
    }

    public String getDeclaration() {
        if (isAbsolute()) {
            return String.format("new Color(%d, %d, %d, %d)",
                                 red, green, blue, alpha);
        } else {
            return String.format("decodeColor(\"%s\", %sf, %sf, %sf, %d)",
                    uiDefaultParentName, String.valueOf(hueOffset),
                    String.valueOf(saturationOffset),
                    String.valueOf(brightnessOffset), alphaOffset);
        }
    }

    public String write() {
        if (isAbsolute()) {
            return String.format("%s, %s, %s, %s", red, green, blue, alpha);
        } else {
            String s = String.format("\"%s\", %sf, %sf, %sf, %d",
                    uiDefaultParentName, String.valueOf(hueOffset),
                    String.valueOf(saturationOffset),
                    String.valueOf(brightnessOffset), alphaOffset);
            if (! uiResource) {
                s += ", false";
            }
            return s;
        }
    }

    public ComponentColor createComponentColor(String variableName) {
        return new ComponentColor(componentPropertyName, variableName,
                saturationOffset, brightnessOffset, alphaOffset);
    }
}
