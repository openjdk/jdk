/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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
 */

package compiler.lib.template_framework;

import java.util.HashMap;
import java.util.Map;

/**
 * The {@link TemplateFrame} is the frame for a {@link Template}, i.e. the corresponding
 * {@link TemplateToken}. It ensures that each template use has its own unique {@link #id}
 * used to deconflict names using {@link Template#$}. It also has a set of hashtag
 * replacements, which combine the key-value pairs from the template argument and the
 * {@link Template#let} definitions. The {@link #parent} relationship provides a trace
 * for the use chain of templates. The {@link #fuel} is reduced over this chain, to give
 * a heuristic on how much time is spent on the code from the template corresponding to
 * the frame, and to give a termination criterion to avoid nesting templates too deeply.
 *
 * <p>
 * See also {@link CodeFrame} for more explanations about the frames.
 */
class TemplateFrame {
    final TemplateFrame parent;
    private final int id;
    private final Map<String, String> hashtagReplacements = new HashMap<>();
    final float fuel;
    private float fuelCost;

    public static TemplateFrame makeBase(int id, float fuel) {
        return new TemplateFrame(null, id, fuel, 0.0f);
    }

    public static TemplateFrame make(TemplateFrame parent, int id) {
        return new TemplateFrame(parent, id, parent.fuel - parent.fuelCost, Template.DEFAULT_FUEL_COST);
    }

    private TemplateFrame(TemplateFrame parent, int id, float fuel, float fuelCost) {
        this.parent = parent;
        this.id = id;
        this.fuel = fuel;
        this.fuelCost = fuelCost;
    }

    public String $(String name) {
        if (name == null) {
            throw new RendererException("A '$' name should not be null.");
        }
        if (!Renderer.isValidHashtagOrDollarName(name)) {
            throw new RendererException("Is not a valid '$' name: '" + name + "'.");
        }
        return name + "_" + id;
    }

    void addHashtagReplacement(String key, String value) {
        if (key == null) {
            throw new RendererException("A hashtag replacement should not be null.");
        }
        if (!Renderer.isValidHashtagOrDollarName(key)) {
            throw new RendererException("Is not a valid hashtag replacement name: '" + key + "'.");
        }
        if (hashtagReplacements.putIfAbsent(key, value) != null) {
            throw new RendererException("Duplicate hashtag replacement for #" + key);
        }
    }

    String getHashtagReplacement(String key) {
        if (!Renderer.isValidHashtagOrDollarName(key)) {
            throw new RendererException("Is not a valid hashtag replacement name: '" + key + "'.");
        }
        if (hashtagReplacements.containsKey(key)) {
            return hashtagReplacements.get(key);
        }
        throw new RendererException("Missing hashtag replacement for #" + key);
    }

    void setFuelCost(float fuelCost) {
        this.fuelCost = fuelCost;
    }
}
