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

import java.io.Closeable;
import java.io.IOException;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.StringJoiner;

import jdk.jfr.EventType;
import jdk.jfr.consumer.EventStream;
import jdk.jfr.internal.query.QueryResolver.QueryException;
import jdk.jfr.internal.query.ViewFile.ViewConfiguration;
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
 * Used by 'jcmd JFR.view' and 'jfr view'.
 * <p>
 * Views are defined in jdk/jfr/internal/query/view.ini
 */
public final class ViewPrinter {
    private final Configuration configuration;
    private final EventStream stream;
    private final Output out;
    private final StopWatch stopWatch;

    /**
     * Constructs a view printer object.
     *
     * @param configuration display configuration
     * @param stream a non-started stream from where data should be fetched.
     */
    public ViewPrinter(Configuration configuration, EventStream stream) {
        this.configuration = configuration;
        this.out = configuration.output;
        this.stopWatch = new StopWatch();
        this.stream = stream;
    }

    /**
     * Prints the view.
     *
     * @param text the view or event type to display
     *
     * @throws UserDataException   if the stream associated with the printer lacks
     *                             event or event metadata
     * @throws UserSyntaxException if the syntax of the query is incorrect.
     */
    public void execute(String text) throws UserDataException, UserSyntaxException {
        try {
            if (showViews(text) || showEventType(text)) {
                return;
            }
        } catch (ParseException pe) {
            throw new InternalError("Internal error, view.ini file is invalid", pe);
        }
        throw new UserDataException("Could not find a view or an event type named " + text);
    }

    private boolean showEventType(String eventType) {
        try {
            QueryPrinter printer = new QueryPrinter(configuration, stream);
            configuration.verboseTitle = true;
            printer.execute("SELECT * FROM " + eventType);
            return true;
        } catch (UserDataException | UserSyntaxException e) {
            return false;
        }
    }

    private boolean showViews(String text) throws UserDataException, ParseException, UserSyntaxException {
        if (configuration.verbose) {
            configuration.verboseHeaders = true;
        }
        if (text.equalsIgnoreCase("all-events")) {
            QueryExecutor executor = new QueryExecutor(stream);
            stopWatch.beginAggregation();
            List<QueryRun> runs = executor.run();
            stopWatch.beginFormatting();
            for (QueryRun task : runs) {
                Table table = task.getTable();
                FilteredType type = table.getFields().getFirst().type;
                configuration.title = type.getLabel();
                TableRenderer renderer = new TableRenderer(configuration, table , task.getQuery());
                renderer.render();
                out.println();
            }
            stopWatch.finish();
            if (configuration.verbose) {
                out.println();
                out.println("Execution: " + stopWatch.toString());
            }
            printTimespan();
            return true;
        }
        if (text.equals("types")) {
            QueryPrinter qp = new QueryPrinter(configuration, stream);
            qp.execute("SHOW EVENTS");
            return true;
        }
        List<ViewConfiguration> views = ViewFile.getDefault().getViewConfigurations();
        if (text.equalsIgnoreCase("all-views")) {
            stopWatch.beginQueryValidation();
            List<Query> queries = new ArrayList<>();
            for (ViewConfiguration view : views) {
                queries.add(new Query(view.query()));
            }
            QueryExecutor executor = new QueryExecutor(stream, queries);
            int index = 0;
            stopWatch.beginAggregation();
            List<QueryRun> runs = executor.run();
            stopWatch.beginFormatting();
            for (QueryRun run : runs) {
                printView(views.get(index++), run);
            }
            stopWatch.finish();
            if (configuration.verbose) {
                out.println();
                out.println("Execution: " + stopWatch.toString());
            }
            printTimespan();
            printViewTypeRelation(views, executor.getEventTypes());
            return true;
        }
        for (ViewConfiguration view : views) {
            if (view.name().equalsIgnoreCase(text)) {
                stopWatch.beginQueryValidation();
                Query q = new Query(view.query());
                QueryExecutor executor = new QueryExecutor(stream, q);
                stopWatch.beginAggregation();
                QueryRun run = executor.run().getFirst();
                stopWatch.beginFormatting();
                printView(view, run);
                stopWatch.finish();
                if (configuration.verbose) {
                    out.println();
                    out.println("Execution: " + stopWatch.toString());
                    out.println();
                }
                printTimespan();
                return true;
            }
        }
        return false;
    }

    void printViewTypeRelation(List<ViewConfiguration> views, List<EventType> eventTypes) throws ParseException {
        if (!configuration.verbose) {
            return;
        }
        out.println();
        out.println("Event types and views");
        out.println();
        Map<String, Set<String>> viewMap = new HashMap<>();
        for (EventType type : eventTypes) {
            viewMap.put(type.getName(), new LinkedHashSet<>());
        }
        for (ViewConfiguration view : views) {
            Query query = new Query(view.query());
            if (query.from.getFirst().name().equals("*")) {
                continue;
            }
            QueryResolver resolver = new QueryResolver(query, eventTypes);
            try {
                resolver.resolve();
            } catch (QueryException e) {
               throw new InternalError(e);
            }
            for (FilteredType ft: resolver.getFromTypes()) {
                Set<String> list = viewMap.get(ft.getName());
                list.add(view.name());
            }
        }
        List<String> names = new ArrayList<>(viewMap.keySet());
        Collections.sort(names);
        for (String name : names) {
            Set<String> vs = viewMap.get(name);
            StringJoiner sj = new StringJoiner(", ");
            vs.stream().forEach(sj::add);
            out.println(String.format("%-35s %s", name, sj.toString()));
        }
    }

    private void printTimespan() {
        if (configuration.verboseTimespan) {
            String start = ValueFormatter.formatTimestamp(configuration.startTime);
            String end = ValueFormatter.formatTimestamp(configuration.endTime);
            out.println();
            out.println("Timespan: " + start + " - " + end);
        }
    }

    private void printView(ViewConfiguration section, QueryRun queryRun)
            throws UserDataException, ParseException, UserSyntaxException {
        if (!queryRun.getSyntaxErrors().isEmpty()) {
            throw new UserSyntaxException(queryRun.getSyntaxErrors().getFirst());
        }
        if (!queryRun.getMetadataErrors().isEmpty()) {
            // Recording doesn't have the event,
            out.println(queryRun.getMetadataErrors().getFirst());
            out.println("Missing event found for " + section.name());
            return;
        }
        Table table = queryRun.getTable();
        configuration.title = section.getLabel();
        long width = 0;
        if (section.getForm() != null) {
            FormRenderer renderer = new FormRenderer(configuration, table);
            renderer.render();
            width = renderer.getWidth();
        }
        if (section.getTable() != null) {
            Query query = queryRun.getQuery();
            TableRenderer renderer = new TableRenderer(configuration, table, query);
            renderer.render();
            width = renderer.getWidth();
        }
        if (width != 0 && configuration.verbose && !queryRun.getTable().isEmpty()) {
            out.println();
            Query query = queryRun.getQuery();
            printQuery(new LineBuilder(out, width), query.toString());
        }
    }

    private void printQuery(LineBuilder lb, String query) {
        char[] separators = {'=', ','};
        try (Tokenizer tokenizer = new Tokenizer(query, separators)) {
            while (tokenizer.hasNext()) {
                lb.append(nextText(tokenizer));
            }
            lb.out.println();
        } catch (ParseException pe) {
            throw new InternalError("Could not format already parsed query", pe);
        }
    }

    private String nextText(Tokenizer tokenizer) throws ParseException {
        if (tokenizer.peekChar() == '\'') {
            return "'" + tokenizer.next() + "'";
        } else {
            return tokenizer.next();
        }
    }

    // Helper class for line breaking
    private static class LineBuilder implements Closeable {
        private final Output out;
        private final long width;
        private int position;
        LineBuilder(Output out, long width) {
            this.out = out;
            this.width = width;
        }

        public void append(String text) {
            String original = text;
            if (!text.equals(",") && !text.equals(";") && position != 0) {
                text = " " + text;
            }
            if (text.length() > width) {
                print(text);
                return;
            }

            if (text.length() + position > width) {
                out.println();
                position = 0;
                text = original;
            }
            out.print(text);
            position += text.length();
        }

        private void print(String s) {
            for (int i = 0; i < s.length(); i++) {
                if (position % width == 0 && position != 0) {
                    out.println();
                }
                out.print(s.charAt(i));
                position++;
            }
        }

        @Override
        public void close() throws IOException {
            out.println();
        }
    }

    public static List<String> getAvailableViews() {
        List<String> list = new ArrayList<>();
        list.add("Java virtual machine views:");
        list.add(new Columnizer(getViewList("jvm"), 3).toString());
        list.add("");
        list.add("Environment views:");
        list.add(new Columnizer(getViewList("environment"), 3).toString());
        list.add("");
        list.add("Application views:");
        list.add(new Columnizer(getViewList("application"), 3).toString());
        list.add("");
        return list;
    }

    private static List<String> getViewList(String selection) {
        List<String> names = new ArrayList<>();
        for (var view : ViewFile.getDefault().getViewConfigurations()) {
            String category = view.category();
            if (selection.equals(category)) {
                names.add(view.name());
            }
        }
        return names;
    }
}
