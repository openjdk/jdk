/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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

package jdk.internal.net.http.qpack;

public sealed interface HeadersTable permits StaticTable, DynamicTable {

    /**
     * Add an entry to the table.
     *
     * @param name  header name
     * @param value header value
     * @return unique index of entry added to the table.
     * If element cannot be added {@code -1} is returned.
     */
    long insert(String name, String value);

    /**
     * Get a table entry with specified unique index.
     *
     * @param index an entry unique index
     * @return table entry
     */
    HeaderField get(long index);

    /**
     * Returns an index for name:value pair, or just name in a headers table.
     * The contract for return values is the following:
     * - a positive integer {@code i} where {@code i - 1} is an index of an
     * entry with a header (n, v), where {@code n.equals(name) && v.equals(value)}.
     * <p>
     * - a negative integer {@code j} where {@code -j - 1} is an index of an entry with
     * a header (n, v), where {@code n.equals(name)}.
     * <p>
     * - {@code 0} if there's no entry 'e' found such that {@code e.getName().equals(name)}
     *
     * @param name  a name to search for
     * @param value a value to search for
     * @return a non-zero value if a matching entry is found, 0 otherwise
     */
    long search(String name, String value);
}
