/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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
package jdk.jpackage.internal.summary;

/**
 * Accumulator of items for summary.
 */
public interface SummaryAccumulator {

    default void putIfAbsent(SummaryItem k, Object... valueFormatArgs) {
        putIfAbsent(k, k.formatValue(valueFormatArgs));
    }

    default void put(SummaryItem k, Object... valueFormatArgs) {
        put(k, k.formatValue(valueFormatArgs));
    }

    default void putMultiValue(Warning k, Iterable<String> items, Object... valueFormatArgs) {
        putMultiValue(k, k.formatValue(valueFormatArgs), items);
    }

    void put(SummaryItem k, String value);

    void putIfAbsent(SummaryItem k, String value);

    void putMultiValue(Warning k, String header, Iterable<String> items);
}
