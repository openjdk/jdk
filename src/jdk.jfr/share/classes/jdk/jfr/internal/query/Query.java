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
package jdk.jfr.internal.query;

import java.text.ParseException;
import java.util.List;
import java.util.Optional;
import java.util.StringJoiner;
import java.util.function.Consumer;

final class Query {
    enum SortOrder {
        NONE, ASCENDING, DESCENDING
    }

    record Source(String name, Optional<String> alias) {
    }

    record Condition(String field, String value) {
    }

    record Where(List<Condition> conditions) {
    }

    record Property(String name, Consumer<Field> style) {
    }

    record Formatter(List<Property> properties) {
    }

    record Expression(String name, Optional<String> alias, Aggregator aggregator) {
    }

    record Grouper(String field) {
    }

    record OrderElement(String name, SortOrder order) {
    }

    final List<String> column;
    final List<Formatter> format;
    final List<Expression> select;
    final List<Source> from;
    final List<Condition> where;
    final List<Grouper> groupBy;
    final List<OrderElement> orderBy;
    final int limit;

    public Query(String text) throws ParseException {
        try (QueryParser qp = new QueryParser(text)) {
            column = qp.column();
            format = qp.format();
            select = qp.select();
            from = qp.from();
            where = qp.where();
            groupBy = qp.groupBy();
            orderBy = qp.orderBy();
            limit = qp.limit();
        }
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        if (!column.isEmpty()) {
            StringJoiner sj = new StringJoiner(", ");
            for (String c : column) {
                sj.add("'" + c + "'");
            }
            sb.append("COLUMN ").append(sj).append(" ");
        }
        if (!format.isEmpty()) {
            StringJoiner sj = new StringJoiner(", ");
            for (Formatter f : format) {
                StringJoiner t = new StringJoiner(";");
                for (Property p : f.properties()) {
                    t.add(p.name());
                }
                sj.add(t.toString());
            }
            sb.append("FORMAT ").append(sj).append(" ");
        }
        StringJoiner t = new StringJoiner(", ");
        for (Expression e : select) {
            StringBuilder w = new StringBuilder();
            if (e.aggregator() != Aggregator.MISSING) {
                w.append(e.aggregator().name());
                w.append("(");
            }
            w.append(e.name());
            if (e.aggregator() != Aggregator.MISSING) {
                w.append(")");
            }
            if (e.alias().isPresent()) {
                w.append(" AS ");
                w.append(e.alias().get());
            }
            t.add(w.toString());
        }
        sb.append("SELECT ")
          .append(select.isEmpty() ? "*" : t.toString());
        StringJoiner u = new StringJoiner(", ");
        for (Source e : from) {
            String s = e.name();
            if (e.alias().isPresent()) {
                s += " AS " + e.alias().get();
            }
            u.add(s);
        }
        sb.append(" FROM ").append(u);
        if (!where.isEmpty()) {
            StringJoiner sj = new StringJoiner(" AND");
            for (Condition c : where) {
                sj.add(" " + c.field() + " = '" + c.value() + "'");
            }
            sb.append(" WHERE").append(sj);
        }

        if (!groupBy.isEmpty()) {
            StringJoiner sj = new StringJoiner(", ");
            for (Grouper g : groupBy) {
                sj.add(g.field());
            }
            sb.append(" GROUP BY ").append(sj);
        }
        if (!orderBy.isEmpty()) {
            StringJoiner sj = new StringJoiner(", ");
            for (OrderElement e : orderBy) {
                String name = e.name();
                if (e.order() == SortOrder.ASCENDING) {
                    name += " ASC";
                }
                if (e.order() == SortOrder.DESCENDING) {
                    name += " DESC";
                }
                sj.add(name);
            }
            sb.append(" ORDER BY ").append(sj);
        }
        if (limit != Integer.MAX_VALUE) {
            sb.append(" LIMIT " + limit);
        }
        return sb.toString();
    }
}
