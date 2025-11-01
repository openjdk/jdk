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
 * The {@link TemplateFrame} is the frame for a {@link Template} and its inner
 * {@link Template#scope}s. It ensures that each {@link Template} use has its own unique
 * {@link #id} used to deconflict names using {@link Template#$}. It also has a set of hashtag
 * replacements, which combine the key-value pairs from the template argument and the
 * {@link Template#let} definitions. Inner scopes of a {@link Template} have access to
 * the outer scope hashtag replacements, and any hashtag replacement defined inside an
 * inner scope is local and disapears once we leave the scope. The {@link #parent} relationship
 * provides a trace for the use chain of templates and their inner scopes. The {@link #fuel}
 * is reduced over this chain, to give a heuristic on how much time is spent on the code
 * from the template corresponding to the frame, and to give a termination criterion to avoid
 * nesting templates too deeply.
 *
 * <p>
 * The {@link TemplateFrame} thus implements the hashtag and {@link Template#setFuelCost}
 * non-transparency aspect of {@link ScopeToken}.
 *
 * <p>
 * See also {@link CodeFrame} for more explanations about the frames. Note, that while
 * {@link TemplateFrame} always nests inward, even with {@link Hook#insert}, the
 * {@link CodeFrame} can also jump to the {@link Hook#anchor} {@link CodeFrame} when
 * using {@link Hook#insert}.
 */
class TemplateFrame {
    final TemplateFrame parent;
    private final boolean isInnerScope;
    private final int id;
    private final Map<String, String> hashtagReplacements = new HashMap<>();
    final float fuel;
    private float fuelCost;
    private final boolean isTransparentForHashtag;
    private final boolean isTransparentForFuel;

    public static TemplateFrame makeBase(int id, float fuel) {
        return new TemplateFrame(null, false, id, fuel, 0.0f, false, false);
    }

    public static TemplateFrame make(TemplateFrame parent, int id) {
        float fuel = parent.fuel - parent.fuelCost;
        return new TemplateFrame(parent, false, id, fuel, Template.DEFAULT_FUEL_COST, false, false);
    }

    public static TemplateFrame makeInnerScope(TemplateFrame parent,
                                               boolean isTransparentForHashtag,
                                               boolean isTransparentForFuel) {
        // We keep the id of the parent, so that we have the same dollar replacements.
        // And we subtract no fuel, but forward the cost.
        return new TemplateFrame(parent, true, parent.id, parent.fuel, parent.fuelCost,
                                 isTransparentForHashtag, isTransparentForFuel);
    }

    private TemplateFrame(TemplateFrame parent,
                          boolean isInnerScope,
                          int id,
                          float fuel,
                          float fuelCost,
                          boolean isTransparentForHashtag,
                          boolean isTransparentForFuel) {
        this.parent = parent;
        this.isInnerScope = isInnerScope;
        this.id = id;
        this.fuel = fuel;
        this.fuelCost = fuelCost;
        this.isTransparentForHashtag = isTransparentForHashtag;
        this.isTransparentForFuel = isTransparentForFuel;
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
        String previous = findHashtagReplacementInScopes(key);
        if (previous != null) {
            throw new RendererException("Duplicate hashtag replacement for #" + key + ". " +
                                        "previous: " + previous + ", new: " + value);
        }
        if (isTransparentForHashtag) {
            parent.addHashtagReplacement(key, value);
        } else {
            hashtagReplacements.put(key, value);
        }
    }

    String getHashtagReplacement(String key) {
        if (!Renderer.isValidHashtagOrDollarName(key)) {
            throw new RendererException("Is not a valid hashtag replacement name: '" + key + "'.");
        }
        String value = findHashtagReplacementInScopes(key);
        if (value != null) {
            return value;
        }
        throw new RendererException("Missing hashtag replacement for #" + key);
    }

    private String findHashtagReplacementInScopes(String key) {
        if (hashtagReplacements.containsKey(key)) {
            return hashtagReplacements.get(key);
        }
        if (!isInnerScope) {
            return null;
        }
        return parent.findHashtagReplacementInScopes(key);
    }

    void setFuelCost(float fuelCost) {
        this.fuelCost = fuelCost;
        if (this.isTransparentForFuel) {
            this.parent.setFuelCost(fuelCost);
        }
    }
}
