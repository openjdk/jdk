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

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

import jdk.jfr.consumer.EventStream;
import jdk.jfr.consumer.MetadataEvent;
import jdk.jfr.internal.query.QueryResolver.QueryException;
import jdk.jfr.internal.query.QueryResolver.QuerySyntaxException;

final class QueryRun {
    private final Histogram histogram = new Histogram();
    private final Table table = new Table();
    private final List<String> syntaxErrors = new ArrayList<>();
    private final List<String> metadataErrors = new ArrayList<>();
    private final Query query;
    private final EventStream stream;

    public QueryRun(EventStream stream, Query query) {
        this.stream = stream;
        this.query = query;
    }

    void onMetadata(MetadataEvent e) {
        if (table.getFields().isEmpty()) {
            // Only use first metadata event for now
            try {
                QueryResolver resolver = new QueryResolver(query, e.getEventTypes());
                List<Field> fields = resolver.resolve();
                table.addFields(fields);
                histogram.addFields(fields);
                addEventListeners();
            } catch (QuerySyntaxException qe) {
                syntaxErrors.add(qe.getMessage());
            } catch (QueryException qe) {
                metadataErrors.add(qe.getMessage());
            }
        }
    }

    public void complete() {
        if (!query.groupBy.isEmpty()) {
            table.addRows(histogram.toRows());
        }
    }

    private void addEventListeners() {
        for (var entry : groupByTypeDescriptor().entrySet()) {
            FilteredType type = entry.getKey();
            List<Field> sourceFields = entry.getValue();
            stream.onEvent(type.getName(), e -> {
                for (var filter : type.getFilters()) {
                    Object object = filter.field().valueGetter.apply(e);
                    String text = FieldFormatter.format(filter.field(), object);
                    if (!text.equals(filter.value())) {
                        return;
                    }
                }
                if (query.groupBy.isEmpty()) {
                    table.add(e, sourceFields);
                } else {
                    histogram.add(e, type, sourceFields);
                }
            });
        }
    }

    private LinkedHashMap<FilteredType, List<Field>> groupByTypeDescriptor() {
        var multiMap = new LinkedHashMap<FilteredType, List<Field>>();
        for (Field field : table.getFields()) {
            for (Field sourceFields : field.sourceFields) {
                multiMap.computeIfAbsent(sourceFields.type, k -> new ArrayList<>()).add(field);
            }
        }
        return multiMap;
    }

    public List<String> getSyntaxErrors() {
        return syntaxErrors;
    }

    public List<String> getMetadataErrors() {
        return metadataErrors;
    }

    public Query getQuery() {
        return query;
    }

    public Table getTable() {
        return table;
    }
}
