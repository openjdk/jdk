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
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import jdk.test.lib.Utils;

/**
 * The {@link NameSet} defines a set of names (e.g. fields or variable names). They extend the
 * set of the {@code 'parent'} set.
 */
class NameSet {
    static final Random RANDOM = Utils.getRandomInstance();

    private final NameSet parent;
    private final Map<Object,List<String>> names = new HashMap<>();

    NameSet(NameSet parent) {
        this.parent = parent;
    }

    public int countLocal(Object type) {
        List<String> locals = names.get(type);
        return (locals == null) ? 0 : locals.size();
    }

    public int count(Object type) {
        int c = countLocal(type);
        if (parent != null) { c += parent.count(type); }
        return c;
    }

    /**
     * Randomly sample a name from this set or a parent set, restricted to the specified type.
     */
    public String sample(Object type) {
        int c = count(type);
        if (c == 0) {
            throw new RendererException("No variable of type '" + type.toString() + "'.");
        }

        // Maybe sample from parent.
        if (parent != null) {
            int pc = parent.count(type);
            int r = RANDOM.nextInt(c);
            if (r < pc) {
                return parent.sample(type);
            }
        }

        List<String> locals = names.get(type);
        int r = RANDOM.nextInt(locals.size());
        return locals.get(r);
    }

    /**
     * Add a variable of a specified type to the set.
     */
    public void add(String name, Object type) {
        // Fetch list of variables - if non-existant create a new one.
        List<String> locals = names.get(type);
        if (locals == null) {
            locals = new ArrayList<String>();
            names.put(type, locals);
        }
        locals.add(name);
    }
}
