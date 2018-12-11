/*
 * Copyright (c) 2017, 2018, Oracle and/or its affiliates. All rights reserved.
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

import java.util.AbstractList;
import java.util.List;
import java.util.RandomAccess;

/**
 * A {@code ReversedList} is a view on an other list with the elements in reverse order.
 *
 * This implementation is made for {@link RandomAccess} lists.
 */
public class ReversedList<T> extends AbstractList<T> implements RandomAccess {
    private final List<T> original;

    public ReversedList(List<T> original) {
        assert original instanceof RandomAccess;
        this.original = original;
    }

    @Override
    public T get(int index) {
        return original.get(original.size() - index - 1);
    }

    @Override
    public int size() {
        return original.size();
    }

    /**
     * Creates a list that is a view on {@code list} in reverse order.
     */
    public static <T> ReversedList<T> reversed(List<T> list) {
        return new ReversedList<>(list);
    }
}
