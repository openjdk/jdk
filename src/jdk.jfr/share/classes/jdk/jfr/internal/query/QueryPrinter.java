/*
 * Copyright (c) 2023, 2025, Oracle and/or its affiliates. All rights reserved.
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
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import jdk.jfr.AnnotationElement;
import jdk.jfr.EventType;
import jdk.jfr.Experimental;
import jdk.jfr.Relational;
import jdk.jfr.ValueDescriptor;
import jdk.jfr.consumer.EventStream;
import jdk.jfr.internal.util.Utils;
import jdk.jfr.internal.util.Columnizer;
import jdk.jfr.internal.util.Output;
import jdk.jfr.internal.util.StopWatch;
import jdk.jfr.internal.util.Tokenizer;
import jdk.jfr.internal.util.UserDataException;
import jdk.jfr.internal.util.UserSyntaxException;
import jdk.jfr.internal.util.ValueFormatter;

/**
 * Class responsible for executing and displaying the contents of a query.
 * <p>
 * Used by 'jcmd JFR.query' and 'jfr query'.
 */
public final class QueryPrinter {
    private final EventStream stream;
    private final Configuration configuration;
    private final Output out;
    private final StopWatch stopWatch;

    /**
     * Constructs a query printer.
     *
     * @param configuration display configuration
     * @param stream a non-started stream from where data should be fetched.
     */
    public QueryPrinter(Configuration configuration, EventStream stream) {
        this.configuration = configuration;
        this.out = configuration.output;
        this.stopWatch = new StopWatch();
        this.stream = stream;
    }

    /**
     * Prints the query.
     *
     * @see getGrammarText().
     *
     * @param query the query text
     *
     * @throws UserDataException   if the stream associated with the printer lacks
     *                             event or event metadata
     * @throws UserSyntaxException if the syntax of the query is incorrect.
     */
    public void execute(String query) throws UserDataException, UserSyntaxException {
        if (showEvents(query) || showFields(query)) {
            return;
        }
        showQuery(query);
    }

    private void showQuery(String query) throws UserDataException, UserSyntaxException {
        try {
            stopWatch.beginQueryValidation();
            Query q = new Query(query);
            QueryExecutor executor = new QueryExecutor(stream, q);
            stopWatch.beginAggregation();
            QueryRun task = executor.run().getFirst();
            if (!task.getSyntaxErrors().isEmpty()) {
                throw new UserSyntaxException(task.getSyntaxErrors().getFirst());
            }
            if (!task.getMetadataErrors().isEmpty()) {
                throw new UserDataException(task.getMetadataErrors().getFirst());
            }
            Table table = task.getTable();
            if (configuration.verboseTitle) {
                FilteredType type = table.getFields().getFirst().type;
                configuration.title = type.getLabel();
                if (type.isExperimental()) {
                    configuration.title += " (Experimental)";
                }
            }
            stopWatch.beginFormatting();
            TableRenderer renderer = new TableRenderer(configuration, table, q);
            renderer.render();
            stopWatch.finish();
            if (configuration.verbose) {
                out.println();
                out.println("Execution: " + stopWatch.toString());
            }
            if (configuration.verboseTimespan) {
                String s = ValueFormatter.formatTimestamp(configuration.startTime);
                String e = ValueFormatter.formatTimestamp(configuration.endTime);
                out.println();
                out.println("Timespan: " + s + " - " + e);
            }
        } catch (ParseException pe) {
            throw new UserSyntaxException(pe.getMessage());
        }
    }

    private boolean showFields(String text) {
        String eventType = null;
        try (Tokenizer t = new Tokenizer(text)) {
            t.expect("SHOW");
            t.expect("FIELDS");
            eventType = t.next();
        } catch (ParseException pe) {
            return false;
        }
        Map<Long, EventType> eventTypes = new HashMap<>();
        stream.onMetadata(e -> {
            for (EventType t : e.getAddedEventTypes()) {
                eventTypes.put(t.getId(), t);
            }
        });
        stream.start();

        List<EventType> types = new ArrayList<>(eventTypes.values());
        Collections.sort(types, Comparator.comparing(EventType::getName));
        for (EventType type : types) {
            String qualifiedName = type.getName();
            String name = Utils.makeSimpleName(qualifiedName);
            if (qualifiedName.equals(eventType) || name.equals(eventType)) {
                printFields(type, types);
                return true;
            }
        }
        return false;
    }

    private void printFields(EventType type, List<EventType> allTypes) {
        out.println();
        out.println("" + type.getName() + ":");
        out.println();
        for (ValueDescriptor f : type.getFields()) {
            String typeName = Utils.makeSimpleName(f.getTypeName());
            out.println(" " + typeName + " " + f.getName());
        }
        List<String> related = new ArrayList<>();
        for (String s : relations(type)) {
            out.println();
            String simpleName = Utils.makeSimpleName(s);
            out.println("Event types with a " + simpleName + " relation:");
            out.println();
            for (EventType et : allTypes) {
                if (et != type) {
                    List<String> r = relations(et);
                    if (r.contains(s)) {
                        related.add(et.getName());
                    }
                }
            }
            out.println(new Columnizer(related, 2).toString());
        }
        out.println();
    }

    private List<String> relations(EventType type) {
        List<String> relations = new ArrayList<>();
        for (ValueDescriptor field : type.getFields()) {
            for (AnnotationElement annotation : field.getAnnotationElements()) {
                Relational relation = annotation.getAnnotation(Relational.class);
                if (relation != null) {
                    relations.add(annotation.getTypeName());
                }
            }
        }
        return relations;
    }

    private boolean showEvents(String queryText) {
        try (Tokenizer t = new Tokenizer(queryText)) {
            t.expect("SHOW");
            t.expect("EVENTS");
        } catch (ParseException pe) {
            return false;
            // Ignore
        }
        out.println("Event Types (number of events):");
        out.println();
        Map<Long, Long> eventCount = new HashMap<>();
        Map<Long, EventType> eventTypes = new HashMap<>();
        stream.onMetadata(e -> {
            for (EventType t : e.getAddedEventTypes()) {
                eventTypes.put(t.getId(), t);
            }
        });
        stream.onEvent(event -> {
            eventCount.merge(event.getEventType().getId(), 1L, Long::sum);
        });
        stream.start();
        List<String> types = new ArrayList<>();
        for (EventType type : eventTypes.values()) {
            if (!isExperimental(type)) {
                String name = Utils.makeSimpleName(type);
                Long count = eventCount.get(type.getId());
                String countText = count == null ? "" : " (" + count + ")";
                types.add(name + countText);
            }
        }
        out.println(new Columnizer(types, 2).toString());
        return true;
    }

    private boolean isExperimental(EventType t) {
        return t.getAnnotation(Experimental.class) != null;
    }

    public static String getGrammarText() {
        return """
                Grammar:

                 query       ::= [column] [format] select from [where] [groupBy] [orderBy] [limit]
                 column      ::= "COLUMN" text ("," text)*
                 format      ::= "FORMAT" formatter ("," formatter)*
                 formatter   ::= property (";" property)*
                 select      ::= "SELECT" "*" | expression ("," expression)*
                 expression  ::= (aggregator | field) [alias]
                 aggregator  ::= function "(" (field | "*") ")"
                 alias       ::= "AS" symbol
                 from        ::= "FROM" source ("," source)*
                 source      ::= type [alias]
                 where       ::= condition ("AND" condition)*
                 condition   ::= field "=" text
                 groupBy     ::= "GROUP BY" field ("," field)*
                 orderBy     ::= "ORDER BY" orderField ("," orderField)*
                 orderField  ::= field [sortOrder]
                 sortOrder   ::= "ASC" | "DESC"
                 limit       ::= "LIMIT" <integer>

                 - text, characters surrounded by single quotes
                 - symbol, alphabetic characters
                 - type, the event type name, for example SystemGC. To avoid ambiguity,
                   the name may be qualified, for example jdk.SystemGC
                 - field, the event field name, for example stackTrace.
                   To avoid ambiguity, the name may be qualified, for example
                   jdk.SystemGC.stackTrace. A type alias declared in a FROM clause
                   can be used instead of the type, for example S.eventThread
                 - function, determines how fields are aggregated when using GROUP BY.
                   Aggregate functions are:
                    AVG: The numeric average
                    COUNT: The number of values
                    DIFF: The numeric difference between the last and first value
                    FIRST: The first value
                    LAST: The last value
                    LAST_BATCH: The last set of values with the same end timestamp
                    LIST: All values in a comma-separated list
                    MAX: The numeric maximum
                    MEDIAN: The numeric median
                    MIN: The numeric minimum
                    P90, P95, P99, P999: The numeric percentile, 90%, 95%, 99% or 99.9%
                    STDEV: The numeric standard deviation
                    SUM: The numeric sum
                    UNIQUE: The unique number of occurrences of a value
                   Null values are included, but ignored for numeric functions. If no
                   aggregator function is specified, the first non-null value is used.
                 - property, any of the following:
                    cell-height:<integer> Maximum height of a table cell
                    missing:whitespace Replace missing values (N/A) with blank space
                    normalized Normalize values between 0 and 1.0 for the column
                    truncate-beginning if value can't fit a table cell, remove the first characters
                    truncate-end if value can't fit a table cell, remove the last characters

                 If no value exist, or a numeric value can't be aggregated, the result is 'N/A',
                 unless missing:whitespace is used. The top frame of a stack trace can be referred'
                 to as stackTrace.topFrame. When multiple event types are specified in a FROM clause,
                 the union of the event types are used (not the cartesian product)

                 To see all available events, use the query '"SHOW EVENTS"'. To see all fields for
                 a particular event type, use the query '"SHOW FIELDS <type>"'.""";
    }
}
