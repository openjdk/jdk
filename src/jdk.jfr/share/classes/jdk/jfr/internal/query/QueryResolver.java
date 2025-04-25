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

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;
import java.util.function.Function;
import java.util.regex.Pattern;

import jdk.jfr.EventType;
import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.internal.util.Utils;
import jdk.jfr.internal.query.FilteredType.Filter;
import jdk.jfr.internal.query.Query.Condition;
import jdk.jfr.internal.query.Query.Expression;
import jdk.jfr.internal.query.Query.Formatter;
import jdk.jfr.internal.query.Query.Grouper;
import jdk.jfr.internal.query.Query.OrderElement;
import jdk.jfr.internal.query.Query.Source;
import jdk.jfr.internal.util.Matcher;
import jdk.jfr.internal.util.SpellChecker;

/**
 * Purpose of this class is to take a query and all available event types and
 * check that the query is valid, for example, to see if all fields and types
 * referenced in the query exists. The end result is a list of fields
 * suitable for grouping, sorting and rendering operations later.
 */
final class QueryResolver {
    @SuppressWarnings("serial")
    static class QueryException extends Exception {
        public QueryException(String message) {
            super(message);
        }
    }

    @SuppressWarnings("serial")
    static final class QuerySyntaxException extends QueryException {
        public QuerySyntaxException(String message) {
            super(message);
        }
    }

    private final List<EventType> eventTypes;
    private final List<FilteredType> fromTypes = new ArrayList<>();
    private final Map<String, FilteredType> typeAliases = new LinkedHashMap<>();
    private final Map<String, Field> fieldAliases = new LinkedHashMap<>();
    private final List<Field> resultFields = new ArrayList<>();

    // For readability take query apart
    private final List<String> column;
    private final List<Formatter> format;
    private final List<Expression> select;
    private final List<Source> from;
    private final List<Condition> where;
    private final List<OrderElement> orderBy;
    private final List<Grouper> groupBy;

    public QueryResolver(Query query, List<EventType> eventTypes) {
        this.eventTypes = eventTypes;
        this.column = query.column;
        this.format = query.format;
        this.select = query.select;
        this.from = query.from;
        this.where = query.where;
        this.orderBy = query.orderBy;
        this.groupBy = query.groupBy;
    }

    public List<Field> resolve() throws QueryException {
        resolveFrom();
        resolveSelect();
        resolveGroupBy();
        resolveOrderBy();
        resolveWhere();
        applyIndex();
        applyColumn();
        applyFormat();
        return resultFields;
    }

    private void applyIndex() {
        int index = 0;
        for (Field field : resultFields) {
            field.index = index++;
        }
    }

    private void resolveWhere() throws QuerySyntaxException {
        for (Condition condition : where) {
            List<Field> fields = new ArrayList<>();
            String fieldName = condition.field();
            Field aliasedField = fieldAliases.get(fieldName);
            if (aliasedField != null) {
                fields.add(aliasedField);
            } else {
                fields.addAll(resolveFields(fieldName, fromTypes));
            }
            for (Field field : fields) {
                field.type.addFilter(new Filter(field, condition.value()));
            }
        }
    }

    private void resolveFrom() throws QueryException {
        for (Source source : from) {
            List<EventType> eventTypes = resolveEventType(source.name());
            if (!source.alias().isEmpty() && eventTypes.size() > 1) {
                throw new QueryException("Alias can only refer to a single event type");
            }
            for (EventType eventType : eventTypes) {
                FilteredType type = new FilteredType(eventType);
                fromTypes.add(type);
                source.alias().ifPresent(alias -> typeAliases.put(alias, type));
            }
        }
    }

    private void resolveSelect() throws QueryException {
        if (select.isEmpty()) { // SELECT *
            resultFields.addAll(FieldBuilder.createWildcardFields(eventTypes, fromTypes));
            return;
        }
        for (Expression expression : select) {
            Field field = addField(expression.name(), fromTypes);
            field.visible = true;
            field.aggregator = expression.aggregator();
            FieldBuilder.configureAggregator(field);
            expression.alias().ifPresent(alias -> fieldAliases.put(alias, field));
            if (expression.name().equals("*") && field.aggregator != Aggregator.COUNT) {
                throw new QuerySyntaxException("Wildcard ('*') can only be used with aggregator function COUNT");
            }
        }
    }

    private void resolveGroupBy() throws QueryException {
        if (groupBy.isEmpty()) {
            // Queries on the form "SELECT SUM(a), b, c FROM D" should group all rows implicitly
            var f = select.stream().filter(e -> e.aggregator() != Aggregator.MISSING).findFirst();
            if (f.isPresent()) {
                Grouper grouper = new Grouper("startTime");
                for (var fr : fromTypes) {
                    Field implicit = addField("startTime", List.of(fr));
                    implicit.valueGetter = e -> 1;
                    implicit.grouper = grouper;
                }
                groupBy.add(grouper);
                return;
            }
        }

        for (Grouper grouper : groupBy) {
            for (FilteredType type : fromTypes) {
                String fieldName = grouper.field();
                // Check if alias exists, e.g. "SELECT field AS K FROM ... GROUP BY K"
                Field field= fieldAliases.get(fieldName);
                if (field != null) {
                    fieldName = field.name;
                    if (field.aggregator != Aggregator.MISSING) {
                        throw new QueryException("Aggregate funtion can't be used together with an alias");
                    }
                }
                field = addField(fieldName, List.of(type));
                field.grouper = grouper;
            }
        }
    }

    private void resolveOrderBy() throws QueryException {
        for (OrderElement orderer : orderBy) {
            Field field = fieldAliases.get(orderer.name());
            if (field == null) {
                field = addField(orderer.name(), fromTypes);
            }
            field.orderer = orderer;
        }
    }

    private void applyColumn() throws QueryException {
        if (column.isEmpty()) {
            return;
        }
        if (column.size() != select.size()) {
            throw new QuerySyntaxException("Number of fields in COLUMN clause doesn't match SELECT");
        }

        for (Field field : resultFields) {
            if (field.visible) {
                field.label = column.get(field.index);
            }
        }
    }

    private void applyFormat() throws QueryException {
        if (format.isEmpty()) {
            return;
        }
        if (format.size() != select.size()) {
            throw new QueryException("Number of fields in FORMAT doesn't match SELECT");
        }
        for (Field field : resultFields) {
            if (field.visible) {
                for (var formatter : format.get(field.index).properties()) {
                    formatter.style().accept(field);
                }
            }
        }
    }

    private Field addField(String name, List<FilteredType> types) throws QueryException {
        List<Field> fields = resolveFields(name, types);
        if (fields.isEmpty()) {
            throw new QueryException(unknownField(name, types));
        }

        Field primary = fields.getFirst();
        boolean mixedTypes = false;
        for (Field f : fields) {
            if (!f.dataType.equals(primary.dataType)) {
                mixedTypes = true;
            }
        }
        for (Field field: fields) {
            primary.sourceFields.add(field);
            // Convert to String if field data types mismatch
            if (mixedTypes) {
                final Function<RecordedEvent, Object> valueGetter = field.valueGetter;
                field.valueGetter = event -> {
                  return FieldFormatter.format(field, valueGetter.apply(event));
                };
                field.lexicalSort = true;
                field.dataType = String.class.getName();
                field.alignLeft = true;
            }
        }
        resultFields.add(primary);
        return primary;
    }

    private List<Field> resolveFields(String name, List<FilteredType> types) {
        List<Field> fields = new ArrayList<>();

        if (name.equals("*")) {
            // Used with COUNT(*)
            // All events should have a start time
            name = "startTime";
        }
        if (name.startsWith("[")) {
            int index = name.indexOf("]");
            if (index != -1) {
                String typeNames = name.substring(1, index);
                String suffix = name.substring(index + 1);
                for (String typeName : typeNames.split(Pattern.quote("|"))) {
                    fields.addAll(resolveFields(typeName + suffix, types));
                }
                return fields;
            }
        }

        // Match "namespace.Event.field" and "Event.field"
        if (name.contains(".")) {
            for (FilteredType et : types) {
                String fullEventType = et.getName() + ".";
                if (name.startsWith(fullEventType)) {
                    String fieldName = name.substring(fullEventType.length());
                    FieldBuilder fb = new FieldBuilder(eventTypes, et, fieldName);
                    fields.addAll(fb.build());
                }
                String eventType = et.getSimpleName() + ".";
                if (name.startsWith(eventType)) {
                    String fieldName = name.substring(eventType.length());
                    FieldBuilder fb = new FieldBuilder(eventTypes, et, fieldName);
                    fields.addAll(fb.build());
                }
            }
        }
        // Match "ALIAS.field" where ALIAS can be "namespace.Event" or "Event"
        for (var entry : typeAliases.entrySet()) {
            String alias = entry.getKey() + ".";
            FilteredType s = entry.getValue();
            if (name.startsWith(alias)) {
                int index = name.indexOf(".");
                String unaliased = s.getName() + "." + name.substring(index + 1);
                fields.addAll(resolveFields(unaliased, List.of(s)));
            }
        }

        // Match without namespace
        for (FilteredType eventType : types) {
            FieldBuilder fb = new FieldBuilder(eventTypes, eventType, name);
            fields.addAll(fb.build());
        }
        return fields;
    }

    private List<EventType> resolveEventType(String name) throws QueryException {
        List<EventType> types = new ArrayList<>();
        // Match fully qualified name first
        for (EventType eventType : eventTypes) {
            if (Matcher.match(eventType.getName(),name)) {
                types.add(eventType);
            }
        }
        // Match less qualified name
        for (EventType eventType : eventTypes) {
            if (eventType.getName().endsWith("." + name)) {
                types.add(eventType);
                break;
            }
        }
        if (types.isEmpty()) {
            throw new QueryException(unknownEventType(eventTypes, name));
        }
        return types;
    }

    private static String unknownField(String name, List<FilteredType> types) {
        List<String> alternatives = new ArrayList<>();
        StringJoiner sj = new StringJoiner(", ");
        for (FilteredType t : types) {
            for (var v : t.getFields()) {
                alternatives.add(v.getName());
                alternatives.add(t.getName() + "." + v.getName());
                alternatives.add(t.getSimpleName() + "." + v.getName());
            }
            sj.add(t.getName());
        }
        String message = "Can't find field named '" + name + "' in " + sj;
        String alternative = SpellChecker.check(name, alternatives);
        if (alternative != null) {
            return message + ".\nDid you mean '" + alternative + "'?";
        } else {
            return message + ".\nUse 'SHOW FIELDS " + types.getFirst().getSimpleName() + "' to list available fields.";
        }
    }

    private static String unknownEventType(List<EventType> eventTypes, String name) {
        List<String> alternatives = new ArrayList<>();
        for (EventType type : eventTypes) {
            alternatives.add(Utils.makeSimpleName(type));
        }
        String alternative = SpellChecker.check(name, alternatives);
        String message = "Can't find event type named '" + name + "'.";
        if (alternative != null) {
            return message + " Did you mean '" + alternative + "'?";
        } else {
            return message + " 'SHOW EVENTS' will list available event types.";
        }
    }

    public List<FilteredType> getFromTypes() {
        return fromTypes;
    }
}