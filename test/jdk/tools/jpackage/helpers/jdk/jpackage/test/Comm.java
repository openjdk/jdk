/*
 * Copyright (c) 2024, 2025, Oracle and/or its affiliates. All rights reserved.
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
package jdk.jpackage.test;

import java.util.HashSet;
import java.util.Set;

public record Comm<T>(Set<T> common, Set<T> unique1, Set<T> unique2) {

    public static <T> Comm<T> compare(Set<T> a, Set<T> b) {
        Set<T> common = new HashSet<>(a);
        common.retainAll(b);
        Set<T> unique1 = new HashSet<>(a);
        unique1.removeAll(common);
        Set<T> unique2 = new HashSet<>(b);
        unique2.removeAll(common);
        return new Comm<>(common, unique1, unique2);
    }

    public boolean uniqueEmpty() {
        return unique1.isEmpty() && unique2.isEmpty();
    }
}
