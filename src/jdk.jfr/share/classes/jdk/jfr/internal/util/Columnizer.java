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
package jdk.jfr.internal.util;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Class that creates a column-sorted list.
 * <p>
 * For example, the list: "Bison", "Dog", "Frog", Goldfish", "Kangaroo", "Ant",
 * "Jaguar", "Cat", "Elephant", "Ibex" becomes:
 * <pre>
 *  Ant   Elephant Jaguar
 *  Bison Frog     Kangaroo
 *  Cat   Goldfish
 *  Dog   Ibex"
 * </pre>
 */
public final class Columnizer {
    private static final class Column {
        int maxWidth;
        List<String> entries = new ArrayList<>();
        public void add(String text) {
            entries.add(text);
            maxWidth = Math.max(maxWidth, text.length());
        }
    }
    private final List<Column> columns = new ArrayList<>();

    public Columnizer(List<String> texts, int columnCount) {
        List<String> list = new ArrayList<>(texts);
        Collections.sort(list);
        int columnHeight = (list.size() + columnCount - 1) / columnCount;
        int index = 0;
        Column column = null;
        for (String text : list) {
            if (index % columnHeight == 0) {
                column = new Column();
                columns.add(column);
            }
            column.add(text);
            index++;
        }
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        int index = 0;
        while (true) {
            for (Column column : columns) {
                if (index == column.entries.size()) {
                    return sb.toString();
                }
                if (index != 0 && columns.getFirst() == column) {
                    sb.append(System.lineSeparator());
                }
                String text = column.entries.get(index);
                sb.append(" ");
                sb.append(text);
                sb.append(" ".repeat(column.maxWidth - text.length()));
            }
            index++;
        }
    }
}
