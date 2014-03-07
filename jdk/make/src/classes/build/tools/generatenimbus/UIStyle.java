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

import java.util.ArrayList;
import java.util.List;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementWrapper;


class UIStyle {
    public static enum CacheMode {
        NO_CACHING, FIXED_SIZES, NINE_SQUARE_SCALE
    }

    @XmlElement private UIColor textForeground = null;
    @XmlElement(name="inherit-textForeground")
    private boolean textForegroundInherited = true;

    @XmlElement private UIColor textBackground = null;
    @XmlElement(name="inherit-textBackground")
    private boolean textBackgroundInherited = true;

    @XmlElement private UIColor background = null;
    @XmlElement(name="inherit-background")
    private boolean backgroundInherited = true;

    @XmlElement private boolean cacheSettingsInherited = true;
    @XmlElement CacheMode cacheMode = CacheMode.FIXED_SIZES;
    @XmlElement String maxHozCachedImgScaling = "1.0";
    @XmlElement String maxVertCachedImgScaling = "1.0";

    @XmlElement(name="uiProperty")
    @XmlElementWrapper(name="uiproperties")
    private List<UIProperty> uiProperties = new ArrayList<UIProperty>();

    private UIStyle parentStyle = null;
    public void setParentStyle(UIStyle parentStyle) {
        this.parentStyle = parentStyle;
    }

    public CacheMode getCacheMode() {
        if (cacheSettingsInherited) {
            return (parentStyle == null ?
                CacheMode.FIXED_SIZES : parentStyle.getCacheMode());
        } else {
            return cacheMode;
        }
    }

    public String getMaxHozCachedImgScaling() {
        if (cacheSettingsInherited) {
            return (parentStyle == null ?
                "1.0" : parentStyle.getMaxHozCachedImgScaling());
        } else {
            return maxHozCachedImgScaling;
        }
    }

    public String getMaxVertCachedImgScaling() {
        if (cacheSettingsInherited) {
            return (parentStyle == null ?
                "1.0" : parentStyle.getMaxVertCachedImgScaling());
        } else {
            return maxVertCachedImgScaling;
        }
    }

    public String write(String prefix) {
        StringBuilder sb = new StringBuilder();
        if (! textForegroundInherited) {
            sb.append(String.format("        addColor(d, \"%s%s\", %s);\n",
                    prefix, "textForeground", textForeground.getValue().write()));
        }
        if (! textBackgroundInherited) {
            sb.append(String.format("        addColor(d, \"%s%s\", %s);\n",
                    prefix, "textBackground", textBackground.getValue().write()));
        }
        if (! backgroundInherited) {
            sb.append(String.format("        addColor(d, \"%s%s\", %s);\n",
                    prefix, "background", background.getValue().write()));
        }
        for (UIProperty property : uiProperties) {
            sb.append(property.write(prefix));
        }
        return sb.toString();
    }
}
