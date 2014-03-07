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
import javax.xml.bind.annotation.XmlElement;

class UIProperty extends UIDefault<String> {
    public static enum PropertyType {
        BOOLEAN, INT, FLOAT, DOUBLE, STRING, FONT, COLOR, INSETS, DIMENSION, BORDER
    }
    @XmlAttribute private PropertyType type;

    @XmlElement private Border border;
    @XmlElement private Dimension dimension;
    @XmlElement private Insets insets;
    @XmlElement private Matte matte;
    @XmlElement private Typeface typeface;

    @XmlAttribute
    @Override public void setValue(String value) {
        super.setValue(value);
    }

    public String write(String prefix) {
        switch (type) {
            case BOOLEAN:
                return String.format("        d.put(\"%s%s\", Boolean.%s);\n",
                                     prefix, getName(), getValue().toUpperCase());  ///autobox
            case STRING:
                return String.format("        d.put(\"%s%s\", \"%s\");\n",
                                     prefix, getName(), getValue());
            case INT:
                return String.format("        d.put(\"%s%s\", new Integer(%s));\n",
                                     prefix, getName(), getValue());
            case FLOAT:
                return String.format("        d.put(\"%s%s\", new Float(%sf));\n",
                                     prefix, getName(), getValue());
            case DOUBLE:
                return String.format("        d.put(\"%s%s\", new Double(%s));\n",
                                     prefix, getName(), getValue());
            case COLOR:
                return String.format("        addColor(d, \"%s%s\", %s);\n",
                                     prefix, getName(), matte.write());
            case FONT:
                return String.format("        d.put(\"%s%s\", %s);\n",
                                     prefix, getName(), typeface.write());
            case INSETS:
                return String.format("        d.put(\"%s%s\", %s);\n",
                                     prefix, getName(), insets.write(true));
            case DIMENSION:
                return String.format("        d.put(\"%s%s\", new DimensionUIResource(%d, %d));\n",
                                     prefix, getName(), dimension.width, dimension.height);
            case BORDER:
                return String.format("        d.put(\"%s%s\", new BorderUIResource(%s));\n",
                                     prefix, getName(), border.write());
            default:
                return "###  Look, something's wrong with UIProperty.write()  $$$";
        }
    }
}
