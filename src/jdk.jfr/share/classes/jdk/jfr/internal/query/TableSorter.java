/*
 * Copyright (c) 2023, 2024, Oracle and/or its affiliates. All rights reserved.
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
package jdk.jfr.internal.query;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.function.Predicate;

import jdk.jfr.internal.query.Query.OrderElement;
import jdk.jfr.internal.query.Query.SortOrder;
/**
 * Class responsible for sorting a table according to an ORDER BY statement or
 * a heuristics.
 */
final class TableSorter {
    @SuppressWarnings({ "rawtypes", "unchecked" })
    private static class ColumnComparator implements Comparator<Row> {
        private final int factor;
        private final int index;
        private final boolean lexical;

        public ColumnComparator(Field field, SortOrder order) {
            this.factor = sortOrderToFactor(determineSortOrder(field, order));
            this.index = field.index;
            this.lexical = field.lexicalSort;
        }

        private SortOrder determineSortOrder(Field field, SortOrder order) {
            if (order != SortOrder.NONE) {
                return order;
            }
            if (field.timespan || field.percentage) {
                return SortOrder.DESCENDING;
            }
            return SortOrder.ASCENDING;
        }

        int sortOrderToFactor(SortOrder order) {
            return order == SortOrder.DESCENDING ? -1 : 1;
        }

        @Override
        public int compare(Row rowA, Row rowB) {
            if (lexical) {
                return factor * compareObjects(rowA.getText(index), rowB.getText(index));
            } else {
                return factor * compareObjects(rowA.getValue(index), rowB.getValue(index));
            }
        }

        private static int compareObjects(Object a, Object b) {
            if (a == b) {
                return 0;
            }
            if (a == null) {
                return -1;
            }
            if (b == null) {
                return 1;
            }
            if (a instanceof String s1 && b instanceof String s2) {
                return s1.compareTo(s2);
            }

            if (a instanceof Number n1 && b instanceof Number n2) {
                if (isIntegralType(n1)) {
                    if (isIntegralType(n2)) {
                        return Long.compare(n1.longValue(), n2.longValue());
                    }
                    if (isFractionalType(n2)) {
                        return compare(n1.longValue(), n2.doubleValue());
                    }
                }
                if (isFractionalType(n1)) {
                    if (isFractionalType(n2)) {
                        return Double.compare(n1.doubleValue(), n2.doubleValue());
                    }
                    if (isIntegralType(n2)) {
                        return - compare(n2.longValue(), n1.doubleValue());
                    }
                }
            }
            if (a instanceof Number) {
                return 1;
            }
            if (b instanceof Number) {
                return -1;
            }
            // Comparison with the same class
            if (a.getClass() == b.getClass() && a instanceof Comparable c1) {
                return c1.compareTo((Comparable)b);
            }
            if (a instanceof Comparable) {
                return 1;
            }
            if (b instanceof Comparable) {
                return -1;
            }
            // Use something that is stable if it's not null, comparable or numeric
            return Integer.compare(System.identityHashCode(a), System.identityHashCode(b));
        }

        private static int compare(long integral, double fractional) {
            return BigDecimal.valueOf(integral).compareTo(BigDecimal.valueOf(fractional));
        }

        private static boolean isIntegralType(Number value) {
            if (value instanceof Long || value instanceof Integer) {
                return true;
            }
            if (value instanceof Short || value instanceof Byte) {
                return true;
            }
            return false;
        }

        private static boolean isFractionalType(Number number) {
            return number instanceof Float || number instanceof Double;
        }
    }

    private final Table table;
    private final Query query;

    public TableSorter(Table table, Query query) {
        this.table = table;
        this.query = query;
    }

    public void sort() {
        if (table.getFields().isEmpty()) {
            return;
        }
        if (query.orderBy.isEmpty()) {
            sortDefault();
            return;
        }
        sortOrderBy();
    }

    private void sortDefault() {
        if (sortAggregators()) {
            return;
        }
        if (sortGroupBy()) {
            return;
        }
        sortLeftMost();
    }

    private boolean sortAggregators() {
        return sortPredicate(field -> field.aggregator != Aggregator.MISSING);
    }

    private boolean sortGroupBy() {
        return sortPredicate(field -> query.groupBy.contains(field.grouper));
    }

    private void sortOrderBy() {
        for (OrderElement orderer : query.orderBy.reversed()) {
            sortPredicate(field -> field.orderer == orderer);
        }
    }

    private boolean sortPredicate(Predicate<Field> predicate) {
        boolean sorted = false;
        for (Field field : table.getFields()) {
            if (predicate.test(field)) {
                sort(field, determineSortOrder(field));
                sorted = true;
            }
        }
        return sorted;
    }

    private SortOrder determineSortOrder(Field field) {
        if (field.orderer == null) {
            return SortOrder.NONE;
        } else {
            return field.orderer.order();
        }
    }

    private void sortLeftMost() {
        sort(table.getFields().getFirst(), SortOrder.NONE);
    }

    private void sort(Field field, SortOrder order) {
        table.getRows().sort(new ColumnComparator(field, order));
    }
}
