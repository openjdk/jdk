/*
 * Copyright (c) 2017, 2017, Oracle and/or its affiliates. All rights reserved.
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


package jdk.internal.vm.compiler.collections;

/**
 * Cursor to iterate over a map without changing its contents.
 *
 * @since 1.0
 */
public interface UnmodifiableMapCursor<K, V> {
    /**
     * Advances to the next entry.
     *
     * @return {@code true} if a next entry exists, {@code false} if there is no next entry.
     * @since 1.0
     */
    boolean advance();

    /**
     * The key of the current entry.
     *
     * @since 1.0
     */
    K getKey();

    /**
     * The value of the current entry.
     *
     * @since 1.0
     */
    V getValue();
}
