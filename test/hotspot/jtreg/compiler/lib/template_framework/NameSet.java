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

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import jdk.test.lib.Utils;

/**
 * The {@link NameSet} defines a set of {@link Name}s (e.g. fields or variable names). They extend the
 * set of the {@code 'parent'} set.
 */
class NameSet {
    static final Random RANDOM = Utils.getRandomInstance();

    private final NameSet parent;
    private final List<Name> names = new ArrayList<>();

    NameSet(NameSet parent) {
        this.parent = parent;
    }

    private long localWeight(Name.Type type, boolean onlyMutable) {
        long sum = 0;
        for (var name : names) {
            if (name.type().isSubtypeOf(type) && (name.mutable() || !onlyMutable)) {
                sum += name.weight();
            }
        }
        return sum;
    }

    public long weight(Name.Type type, boolean onlyMutable) {
        long w = localWeight(type, onlyMutable);
        if (parent != null) { w += parent.weight(type, onlyMutable); }
        return w;
    }

    /**
     * Randomly sample a name from this set or a parent set, restricted to the specified type.
     */
    public Name sample(Name.Type type, boolean onlyMutable) {
        long w = weight(type, onlyMutable);
        if (w <= 0) {
            throw new RendererException("No variable of type '" + type.toString() + "'.");
        }

        long r = RANDOM.nextLong(w);
        return sample(type, onlyMutable, r);
    }

    private Name sample(Name.Type type, boolean onlyMutable, long r) {
        for (var name : names) {
            if (name.type().isSubtypeOf(type) && (name.mutable() || !onlyMutable)) {
                r -= name.weight();
                if (r < 0) { return name; }
            }
        }
        return parent.sample(type, onlyMutable, r);
    }

    /**
     * Add a variable of a specified type to the set.
     */
    public void add(Name name) {
        names.add(name);
    }
}
