/*
 * Copyright (c) 2008, 2024, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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
 *
 */
package com.sun.hotspot.igv.data;

import java.awt.Color;
import java.util.Objects;

/**
 *
 * @author Thomas Wuerthinger
 */
public class InputNode extends Properties.Entity {

    private int id;

    public InputNode(InputNode n) {
        super(n);
        setId(n.id);
    }

    public InputNode(int id) {
        setId(id);
    }

    public void setId(int id) {
        this.id = id;
    }

    public int getId() {
        return id;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }
        InputNode other = (InputNode) obj;
        return id == other.id &&
                Objects.equals(getProperties(), other.getProperties());
    }

    @Override
    public String toString() {
        return "Node " + id + " " + getProperties().toString();
    }

    public void setCustomColor(Color color) {
        if (color != null) {
            String hexColor = String.format("#%08X", color.getRGB());
            getProperties().setProperty("color", hexColor);
        } else {
            getProperties().setProperty("color", null);
        }
    }

    public Color getCustomColor() {
        String hexColor = getProperties().get("color");
        if (hexColor != null) {
            try {
                String hex = hexColor.startsWith("#") ? hexColor.substring(1) : hexColor;
                int argb = (int) Long.parseLong(hex, 16);
                return new Color(argb, true);
            } catch (Exception ignored) {
                return null;
            }
        }
        return null;
    }
}
