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

package build.tools.generatenimbus;

import java.util.ArrayList;
import java.util.List;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;

public abstract class Paint {
}

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

class ComponentColor {
    private String propertyName;
    private String defaultColorVariableName;
    private float saturationOffset = 0,  brightnessOffset = 0;
    private int alphaOffset = 0;

    ComponentColor(String propertyName,
            String defaultColorVariableName,
            float saturationOffset,
            float brightnessOffset,
            int alphaOffset) {
        this.propertyName = propertyName;
        this.defaultColorVariableName = defaultColorVariableName;
        this.saturationOffset = saturationOffset;
        this.brightnessOffset = brightnessOffset;
        this.alphaOffset = alphaOffset;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        ComponentColor c = (ComponentColor) o;
        if (alphaOffset != c.alphaOffset) {
            return false;
        }
        if (Float.compare(saturationOffset, c.saturationOffset) != 0) {
            return false;
        }
        if (Float.compare(brightnessOffset, c.brightnessOffset) != 0) {
            return false;
        }
        if (defaultColorVariableName != null ? !defaultColorVariableName.equals(c.defaultColorVariableName) : c.defaultColorVariableName != null) {
            return false;
        }
        if (propertyName != null ? !propertyName.equals(c.propertyName) : c.propertyName != null) {
            return false;
        }
        return true;
    }

    @Override
    public int hashCode() {
        int hash = 5;
        hash = 61 * hash + (this.propertyName != null ? this.propertyName.hashCode() : 0);
        hash = 61 * hash + (this.defaultColorVariableName != null ? this.defaultColorVariableName.hashCode() : 0);
        hash = 61 * hash + Float.floatToIntBits(this.saturationOffset);
        hash = 61 * hash + Float.floatToIntBits(this.brightnessOffset);
        hash = 61 * hash + this.alphaOffset;
        return hash;
    }

    public void write(StringBuilder sb) {
        sb.append("                     getComponentColor(c, \"").
           append(propertyName).append("\", ").
           append(defaultColorVariableName).append(", ").
           append(saturationOffset).append("f, ").
           append(brightnessOffset).append("f, ").
           append(alphaOffset);
    }
}

class GradientStop {
    @XmlAttribute private float position;
    public float getPosition() { return position; }

    @XmlAttribute private float midpoint;
    public float getMidpoint() { return midpoint; }

    @XmlElement private Matte matte;
    public Matte getColor() { return matte; }
}

class AbstractGradient extends Paint {
    public static enum CycleMethod {
        NO_CYCLE, REFLECT, REPEAT
    }

    @XmlElement(name="stop") private ArrayList<GradientStop> stops;
    public List<GradientStop> getStops() { return stops; }
}

class Gradient extends AbstractGradient {
}

class RadialGradient extends AbstractGradient {
}
