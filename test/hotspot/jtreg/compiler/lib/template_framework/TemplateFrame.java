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

// We need frames for
// - Variables:
//   - normal template borders are opaque, but not for intoHook
//   - hook scopes are also opaque
//
// - hashtag replacement:
//   - template border opaque
//   - hook scope transparent
//
// - Parent:
//   - via calls
//   - code / variable scopes

class TemplateFrame {
    public final TemplateFrame parent;
    final int id;

    enum Kind { INTERNAL, BORDER }
    private final Kind kind;

    final Map<String, String> hashtagReplacements = new HashMap<>();

    private TemplateFrame(TemplateFrame parent, int id, Kind kind) {
        this.parent = parent;
        this.id = id;
        this.kind = kind;
    }

    public static TemplateFrame makeBorder(TemplateFrame parent, int id) {
        return new TemplateFrame(parent, id, Kind.BORDER);
    }

    public static TemplateFrame makeInternal(TemplateFrame parent) {
        TemplateFrame frame = new TemplateFrame(parent, parent.id, Kind.INTERNAL);
        frame.hashtagReplacements.putAll(parent.hashtagReplacements);
        return frame;
    }

    public String $(String name) {
        return name + "_" + id;
    }

    void addHashtagReplacement(String key, String value) {
        if (!hashtagReplacements.containsKey(key)) {
            hashtagReplacements.put(key, value);
            return;
        }
        throw new RendererException("Duplicate hashtag replacement for #" + key);
    }

    String getHashtagReplacement(String key) {
        if (hashtagReplacements.containsKey(key)) {
            return hashtagReplacements.get(key);
        }
        throw new RendererException("Missing hashtag replacement for #" + key);
    }
}
