/*
 * Copyright (c) 2016, 2020, Oracle and/or its affiliates. All rights reserved.
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

package jdk.jfr.internal.tool;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Predicate;

import jdk.jfr.EventType;

final class Print extends Command {
    @Override
    public String getName() {
        return "print";
    }

    @Override
    public List<String> getOptionSyntax() {
        List<String> list = new ArrayList<>();
        list.add("[--xml|--json]");
        list.add("[--categories <filter>]");
        list.add("[--events <filter>]");
        list.add("[--stack-depth <depth>]");
        list.add("<file>");
        return list;
    }

    @Override
    protected String getTitle() {
        return "Print contents of a recording file";
    }

    @Override
    public String getDescription() {
        return getTitle() + ". See 'jfr help print' for details.";
    }

    @Override
    public void displayOptionUsage(PrintStream stream) {
        stream.println("  --xml                   Print recording in XML format");
        stream.println();
        stream.println("  --json                  Print recording in JSON format");
        stream.println();
        stream.println("  --categories <filter>   Select events matching a category name.");
        stream.println("                          The filter is a comma-separated list of names,");
        stream.println("                          simple and/or qualified, and/or quoted glob patterns");
        stream.println();
        stream.println("  --events <filter>       Select events matching an event name.");
        stream.println("                          The filter is a comma-separated list of names,");
        stream.println("                          simple and/or qualified, and/or quoted glob patterns");
        stream.println();
        stream.println("  --stack-depth <depth>   Number of frames in stack traces, by default 5");
        stream.println();
        stream.println("  <file>                  Location of the recording file (.jfr)");
        stream.println();
        stream.println();
        stream.println("Example usage:");
        stream.println();
        stream.println(" jfr print --events OldObjectSample recording.jfr");
        stream.println();
        stream.println(" jfr print --events CPULoad,GarbageCollection recording.jfr");
        stream.println();
        char q = quoteCharacter();
        stream.println(" jfr print --categories " + q + "GC,JVM,Java*" + q + " recording.jfr");
        stream.println();
        stream.println(" jfr print --events "+ q + "jdk.*" + q +" --stack-depth 64 recording.jfr");
        stream.println();
        stream.println(" jfr print --json --events CPULoad recording.jfr");
    }

    @Override
    public void execute(Deque<String> options) throws UserSyntaxException, UserDataException {
        Path file = getJFRInputFile(options);
        PrintWriter pw = new PrintWriter(System.out, false, Charset.forName("UTF-8"));
        Predicate<EventType> eventFilter = null;
        int stackDepth = 5;
        EventPrintWriter eventWriter = null;
        int optionCount = options.size();
        boolean foundEventFilter = false;
        boolean foundCategoryFilter = false;
        while (optionCount > 0) {
            if (acceptFilterOption(options, "--events")) {
                if (foundEventFilter) {
                    throw new UserSyntaxException("use --events event1,event2,event3 to include multiple events");
                }
                foundEventFilter = true;
                String filter = options.remove();
                warnForWildcardExpansion("--events", filter);
                eventFilter = addEventFilter(filter, eventFilter);
            }
            if (acceptFilterOption(options, "--categories")) {
                if (foundCategoryFilter) {
                    throw new UserSyntaxException("use --categories category1,category2 to include multiple categories");
                }
                foundCategoryFilter = true;
                String filter = options.remove();
                warnForWildcardExpansion("--categories", filter);
                eventFilter = addCategoryFilter(filter, eventFilter);
            }
            if (acceptOption(options, "--stack-depth")) {
                String value = options.pop();
                try {
                    stackDepth = Integer.parseInt(value);
                    if (stackDepth < 0) {
                        throw new UserSyntaxException("stack depth must be zero or a positive integer.");
                    }
                } catch (NumberFormatException nfe) {
                    throw new UserSyntaxException("not a valid value for --stack-depth");
                }
            }
            if (acceptFormatterOption(options, eventWriter, "--json")) {
                eventWriter = new JSONWriter(pw);
            }
            if (acceptFormatterOption(options, eventWriter, "--xml")) {
                eventWriter = new XMLWriter(pw);
            }
            if (optionCount == options.size()) {
                // No progress made
                checkCommonError(options, "--event", "--events");
                checkCommonError(options, "--category", "--categories");
                throw new UserSyntaxException("unknown option " + options.peek());
            }
            optionCount = options.size();
        }
        if (eventWriter == null) {
            eventWriter = new PrettyWriter(pw); // default to pretty printer
        }
        eventWriter.setStackDepth(stackDepth);
        if (eventFilter != null) {
            eventFilter = addCache(eventFilter, eventType -> eventType.getId());
            eventWriter.setEventFilter(eventFilter);
        }
        try {
            eventWriter.print(file);
        } catch (IOException ioe) {
            couldNotReadError(file, ioe);
        }
        pw.flush();
    }

    private void checkCommonError(Deque<String> options, String typo, String correct) throws UserSyntaxException {
       if (typo.equals(options.peek())) {
           throw new UserSyntaxException("unknown option " + typo + ", did you mean " + correct + "?");
       }
    }

    private static boolean acceptFormatterOption(Deque<String> options, EventPrintWriter eventWriter, String expected) throws UserSyntaxException {
        if (expected.equals(options.peek())) {
            if (eventWriter != null) {
                throw new UserSyntaxException("only one format can be specified at a time");
            }
            options.remove();
            return true;
        }
        return false;
    }

    private static <T, X> Predicate<T> addCache(final Predicate<T> filter, Function<T, X> cacheFunction) {
        Map<X, Boolean> cache = new HashMap<>();
        return t -> cache.computeIfAbsent(cacheFunction.apply(t), x -> filter.test(t));
    }

    private static <T> Predicate<T> recurseIfPossible(Predicate<T> filter) {
        return x -> filter != null && filter.test(x);
    }

    private static Predicate<EventType> addCategoryFilter(String filterText, Predicate<EventType> eventFilter) throws UserSyntaxException {
        List<String> filters = explodeFilter(filterText);
        Predicate<EventType> newFilter = recurseIfPossible(eventType -> {
            for (String category : eventType.getCategoryNames()) {
                for (String filter : filters) {
                    if (match(category, filter)) {
                        return true;
                    }
                    if (category.contains(" ") && acronomify(category).equals(filter)) {
                        return true;
                    }
                }
            }
            return false;
        });
        return eventFilter == null ? newFilter : eventFilter.or(newFilter);
    }

    private static String acronomify(String multipleWords) {
        boolean newWord = true;
        String acronym = "";
        for (char c : multipleWords.toCharArray()) {
            if (newWord) {
                if (Character.isAlphabetic(c) && Character.isUpperCase(c)) {
                    acronym += c;
                }
            }
            newWord = Character.isWhitespace(c);
        }
        return acronym;
    }

    private static Predicate<EventType> addEventFilter(String filterText, final Predicate<EventType> eventFilter) throws UserSyntaxException {
        List<String> filters = explodeFilter(filterText);
        Predicate<EventType> newFilter = recurseIfPossible(eventType -> {
            for (String filter : filters) {
                String fullEventName = eventType.getName();
                if (match(fullEventName, filter)) {
                    return true;
                }
                String eventName = fullEventName.substring(fullEventName.lastIndexOf(".") + 1);
                if (match(eventName, filter)) {
                    return true;
                }
            }
            return false;
        });
        return eventFilter == null ? newFilter : eventFilter.or(newFilter);
    }

    private static boolean match(String text, String filter) {
        if (filter.length() == 0) {
            // empty filter string matches if string is empty
            return text.length() == 0;
        }
        if (filter.charAt(0) == '*') { // recursive check
            filter = filter.substring(1);
            for (int n = 0; n <= text.length(); n++) {
                if (match(text.substring(n), filter))
                    return true;
            }
        } else if (text.length() == 0) {
            // empty string and non-empty filter does not match
            return false;
        } else if (filter.charAt(0) == '?') {
            // eat any char and move on
            return match(text.substring(1), filter.substring(1));
        } else if (filter.charAt(0) == text.charAt(0)) {
            // eat chars and move on
            return match(text.substring(1), filter.substring(1));
        }
        return false;
    }

    private static List<String> explodeFilter(String filter) throws UserSyntaxException {
        List<String> list = new ArrayList<>();
        for (String s : filter.split(",")) {
            s = s.trim();
            if (!s.isEmpty()) {
                list.add(s);
            }
        }
        return list;
    }

    static char quoteCharacter() {
        return File.pathSeparatorChar == ';' ? '"' : '\'';
    }
}
