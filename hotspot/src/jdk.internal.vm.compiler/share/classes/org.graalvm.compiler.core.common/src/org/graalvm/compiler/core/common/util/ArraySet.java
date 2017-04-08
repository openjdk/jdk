/*
 * Copyright (c) 2013, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.core.common.util;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

/**
 * Mimic a set implementation with an ArrayList. Beneficial for small sets (compared to
 * {@link HashSet}).
 */
public class ArraySet<E> extends ArrayList<E> implements Set<E> {
    private static final long serialVersionUID = 4476957522387436654L;

    public ArraySet() {
        super();
    }

    public ArraySet(int i) {
        super(i);
    }

    public ArraySet(Collection<? extends E> c) {
        super(c);
    }

    @Override
    public boolean add(E e) {
        // avoid duplicated entries
        if (contains(e)) {
            return false;
        }
        return super.add(e);
    }
}
