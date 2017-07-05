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

import java.awt.Font;
import java.util.ArrayList;
import java.util.List;
import javax.xml.bind.annotation.*;


@XmlRootElement(name="synthModel")
public class SynthModel {
    @XmlElement private UIStyle style;

    @XmlElement(name="uiColor")
    @XmlElementWrapper(name="colors")
    private ArrayList<UIColor> colors;

    @XmlElement(name="uiFont")
    @XmlElementWrapper(name="fonts")
    private ArrayList<UIFont> fonts;

    @XmlElement(name="uiComponent")
    @XmlElementWrapper(name="components")
    private ArrayList<UIComponent> components;

    public void initStyles() {
        for (UIComponent c: components) {
            c.initStyles(this.style);
        }
    }

    public void write(StringBuilder defBuffer, StringBuilder styleBuffer, String packageName) {
        defBuffer.append("        //Color palette\n");
        for (UIColor c: colors) defBuffer.append(c.write());
        defBuffer.append('\n');

        defBuffer.append("        //Font palette\n");
        defBuffer.append("        d.put(\"defaultFont\", new FontUIResource(defaultFont));\n");
        for (UIFont f: fonts) defBuffer.append(f.write());
        defBuffer.append('\n');

        defBuffer.append("        //Border palette\n");
        defBuffer.append('\n');

        defBuffer.append("        //The global style definition\n");
        defBuffer.append(style.write(""));
        defBuffer.append('\n');

        for (UIComponent c: components) {
            String prefix = Utils.escape(c.getKey());
            defBuffer.append("        //Initialize ").append(prefix).append("\n");
            c.write(defBuffer, styleBuffer, c, prefix, packageName);
            defBuffer.append('\n');
        }
    }
}

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

class Border {
    enum BorderType {
        @XmlEnumValue("empty") EMPTY,
        @XmlEnumValue("painter") PAINTER
    }
    @XmlAttribute private BorderType type;
    @XmlAttribute private String painter;
    @XmlAttribute private int top;
    @XmlAttribute private int left;
    @XmlAttribute private int bottom;
    @XmlAttribute private int right;

    public String write() {
        switch (type) {
            case PAINTER:
                return String.format("new PainterBorder(\"%s\", new Insets(%d, %d, %d, %d))",
                                     painter, top, left, bottom, right);
            case EMPTY:
                return String.format("BorderFactory.createEmptyBorder(%d, %d, %d, %d)",
                                     top, left, bottom, right);
            default:
                return "### Look, here's an unknown border! $$$";
        }
    }
}

class Insets {
    @XmlAttribute int top;
    @XmlAttribute int left;
    @XmlAttribute int bottom;
    @XmlAttribute int right;

    public Insets() {
        this(0, 0, 0, 0);
    }

    public Insets(int top, int left, int bottom, int right) {
        this.top = top;
        this.left = left;
        this.bottom = bottom;
        this.right = right;
    }

    public String write(boolean uiResource) {
        String uiSuffix = (uiResource ? "UIResource" : "");
        return String.format("new Insets%s(%d, %d, %d, %d)",
                             uiSuffix, top, left, bottom, right);
    }
}

class Dimension {
    @XmlAttribute int width;
    @XmlAttribute int height;

    public String write(boolean uiResource) {
        String uiSuffix = (uiResource ? "UIResource" : "");
        return String.format("new Dimension%s(%d, %d)", uiSuffix, width, height);
    }
}

class Canvas {
    @XmlElement private Dimension size;
    public Dimension getSize() { return size; }

    @XmlElement(name="layer") private List<Layer> layers;
    public List<Layer> getLayers() { return layers; }

    @XmlElement private Insets stretchingInsets = null;
    public Insets getStretchingInsets() { return stretchingInsets; }

    public boolean isBlank() {
        return layers.size() == 0 || (layers.size() == 1 && layers.get(0).isEmpty());
    }
}

class Layer {
    /** List of shapes in this layer, first shape is painted on top */
    @XmlElements({
        @XmlElement(name = "ellipse", type = Ellipse.class),
        @XmlElement(name = "path", type = Path.class),
        @XmlElement(name = "rectangle", type = Rectangle.class)
    })
    @XmlElementWrapper(name="shapes")
    private List<Shape> shapes = new ArrayList<Shape>();
    public List<Shape> getShapes() { return shapes; }

    public boolean isEmpty() {
        return shapes.isEmpty();
    }
}
