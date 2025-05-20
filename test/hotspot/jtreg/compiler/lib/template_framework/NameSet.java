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

    interface Predicate {
        boolean check(Name type);
    }

    NameSet(NameSet parent) {
        this.parent = parent;
    }

    private long weight(Predicate predicate) {
        long w = names.stream().filter(n -> predicate.check(n)).mapToInt(Name::weight).sum();
        if (parent != null) { w += parent.weight(predicate); }
        return w;
    }

    public int count(Predicate predicate) {
        int c = (int)names.stream().filter(n -> predicate.check(n)).count();
        if (parent != null) { c += parent.count(predicate); }
        return c;
    }

    public boolean hasAny(Predicate predicate) {
        return names.stream().anyMatch(n -> predicate.check(n)) ||
               (parent != null && parent.hasAny(predicate));
    }

    public List<Name> toList(Predicate predicate) {
        List<Name> list = (parent != null) ? parent.toList(predicate)
                                           : new ArrayList<>();
        list.addAll(names.stream().filter(n -> predicate.check(n)).toList());
        return list;
    }

    /**
     * Randomly sample a name from this set or a parent set, restricted to the predicate.
     */
    public Name sample(Predicate predicate) {
        long w = weight(predicate);
        if (w <= 0) {
            return null;
        }

        long r = RANDOM.nextLong(w);
        return sample(predicate, r);
    }

    private Name sample(Predicate predicate, long r) {
        for (var name : names) {
            if (predicate.check(name)) {
                r -= name.weight();
                if (r < 0) { return name; }
            }
        }
        return parent.sample(predicate, r);
    }

    /**
     * Add a variable of a specified type to the set.
     */
    public void add(Name name) {
        // TODO: verification!
        names.add(name);
    }
}
