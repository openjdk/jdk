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
 * The {@link TemplateFrame} keeps track of the nested hashtag replacements available
 * inside the {@link Template}, as well as the unique id of the {@link Template} use,
 * and how much fuel is available for recursive {@link Template} calls. The name of
 * the {@link TemplateFrame} indicates that it corresponds to the structure of the
 * {@link Template}, whereas the {@link CodeFrame} corresponds to the structure of
 * the generated code.
 *
 * <p>
 * The unique id is used to deconflict names using {@link Template#$}.
 *
 * <p>
 * A {@link Template} can have multiple {@link TemplateFrame}s, if there are nested
 * scopes. The outermost {@link TemplateFrame} determines the id of the {@link Template}
 * use and performs the subtraction of fuel from the outer {@link Template}. Inner
 * {@link TemplateFrame}s ensure the correct availability of hashtag replacement and
 * {@link Template#setFuelCost} definitions, so that they are local to their scope and
 * nested scopes, and only escape if the scope is transparent.
 *
 * <p>
 * The hashtag replacements are a set of key-value pairs from the template arguments
 * and queries such as {@link Template#let} definitions. Each {@link TemplateFrame}
 * has such a set of hashtag replacements, and implicitly provides access to the
 * hashtag replacements of the outer {@link TemplateFrame}s, up to the outermost
 * of the current {@link Template}. If a hashtag replacement is added in a scope,
 * we have to traverse to outer scopes until we find one that is not transparent
 * for hashtags (at most it is the frame of the Template), and insert it there.
 * The hashtag replacent is local to that frame, and accessible for any frames nested
 * inside it, but not inside other Templates. The hashtag replacement disappears once
 * the corresponding scope is exited, i.e. the frame removed.
 *
 * <p>
 * The {@link #parent} relationship provides a trace for the use chain of templates and
 * their inner scopes. The {@link #fuel} is reduced over this chain to give a heuristic
 * on how deeply nested the code is at a given point, correlating to the runtime that
 * would be spent if the code was executed. The idea is that once the fuel is depleated,
 * we do not want to nest more deeply, so that there is a reasonable chance that the
 * execution of the generated code can terminate.
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
        if (isTransparentForFuel) {
            parent.setFuelCost(fuelCost);
        }
    }
}
