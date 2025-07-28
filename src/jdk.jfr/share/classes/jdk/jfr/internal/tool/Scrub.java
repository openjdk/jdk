/*
 * Copyright (c) 2022, 2025, Oracle and/or its affiliates. All rights reserved.
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

import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.function.Predicate;

import jdk.jfr.EventType;
import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.consumer.RecordingFile;
import jdk.jfr.internal.consumer.JdkJfrConsumer;
import jdk.jfr.internal.consumer.filter.ChunkWriter.RemovedEvents;
import jdk.jfr.internal.util.UserDataException;
import jdk.jfr.internal.util.UserSyntaxException;

final class Scrub extends Command {

    @Override
    public String getName() {
        return "scrub";
    }

    @Override
    public List<String> getOptionSyntax() {
        List<String> list = new ArrayList<>();
        list.add("[--include-events <filter>]");
        list.add("[--exclude-events <filter>]");
        list.add("[--include-categories <filter>]");
        list.add("[--exclude-categories <filter>]");
        list.add("[--include-threads <filter>]");
        list.add("[--exclude-threads <filter>]");
        list.add("<input-file>");
        list.add("[<output-file>]");
        return list;
    }

    @Override
    protected String getTitle() {
        return "Scrub contents of a recording file";
    }

    @Override
    public String getDescription() {
        return getTitle() + ". See 'jfr help scrub' for details.";
    }

    @Override
    public void displayOptionUsage(PrintStream stream) {
        // 01234567890123456789012345678901234567890123467890123456789012345678901234567890
        stream.println("  --include-events <filter>       Select events matching an event name");
        stream.println();
        stream.println("  --exclude-events <filter>       Exclude events matching an event name");
        stream.println();
        stream.println("  --include-categories <filter>   Select events matching a category name");
        stream.println();
        stream.println("  --exclude-categories <filter>   Exclude events matching a category name");
        stream.println();
        stream.println("  --include-threads <filter>      Select events matching a thread name");
        stream.println();
        stream.println("  --exclude-threads <filter>      Exclude events matching a thread name");
        stream.println();
        stream.println("  <input-file>                    The input file to read events from");
        stream.println();
        stream.println("  <output-file>                   The output file to write filter events to. ");
        stream.println("                                  If no file is specified, it will be written to");
        stream.println("                                  the same  path as the input file, but with");
        stream.println("                                  \"-scrubbed\" appended to the filename");
        stream.println();
        stream.println("  The filter is a comma-separated list of names, simple and/or qualified,");
        stream.println("  and/or quoted glob patterns. If multiple filters are used, they ");
        stream.println("  are applied in the specified order");
        stream.println();
        stream.println("Example usage:");
        stream.println();
        stream.println(" jfr scrub --include-events 'jdk.Socket*' recording.jfr socket-only.jfr");
        stream.println();
        stream.println(" jfr scrub --exclude-events InitialEnvironmentVariable recording.jfr no-psw.jfr");
        stream.println();
        stream.println(" jfr scrub --include-threads main recording.jfr");
        stream.println();
        stream.println(" jfr scrub --exclude-threads 'Foo*' recording.jfr");
        stream.println();
        stream.println(" jfr scrub --include-categories 'My App' recording.jfr");
        stream.println();
        stream.println(" jfr scrub --exclude-categories JVM,OS recording.jfr");
    }

    @Override
    public void execute(Deque<String> options) throws UserSyntaxException, UserDataException {
        ensureMinArgumentCount(options, 1);

        Path last = Path.of(options.pollLast());
        ensureFileExtension(last, ".jfr");
        Path output = null;
        Path input = null;
        String peek = options.peekLast();
        if (peek != null && peek.endsWith(".jfr")) {
            // Both source and destination specified
            input =  Path.of(options.pollLast());
            output = last;
        } else {
            // Only source file specified
            Path file = last.getFileName();
            Path dir = last.getParent();
            String filename = file.toString();
            int index = filename.lastIndexOf(".");
            String s = filename.substring(0, index);
            String t = s + "-scrubbed.jfr";
            input = last;
            output = dir == null ? Path.of(t) : dir.resolve(t);
        }
        ensureUsableOutput(input, output);

        try (RecordingFile rf = new RecordingFile(input)) {
            List<EventType> types = rf.readEventTypes();
            Predicate<RecordedEvent> filter = createFilter(options, types);
            List<RemovedEvents> result = JdkJfrConsumer.instance().write(rf, output, filter);
            println("Scrubbed recording file written to:");
            println(output.toRealPath().toString());
            if (result.isEmpty()) {
                println("No events removed.");
                return;
            }
            int maxName = 0;
            int maxShare = 0;
            for (RemovedEvents re : result) {
                maxName = Math.max(maxName, re.getName().length());
                maxShare = Math.max(maxShare, re.share().length());
            }
            println("Removed events:");
            for (RemovedEvents re : result) {
                printf("%-" + maxName + "s %" + maxShare + "s\n", re.getName(), re.share());
            }
        } catch (IOException ioe) {
            couldNotReadError(input, ioe);
        }
    }

    private Predicate<RecordedEvent> createFilter(Deque<String> options, List<EventType> types) throws UserSyntaxException, UserDataException {
        List<Predicate<RecordedEvent>> filters = new ArrayList<>();
        int optionCount = options.size();
        while (optionCount > 0) {
            if (acceptFilterOption(options, "--include-events")) {
                String filter = options.remove();
                warnForWildcardExpansion("--include-events", filter);
                var f = Filters.createEventTypeFilter(filter, types);
                filters.add(Filters.fromEventType(f));
            }
            if (acceptFilterOption(options, "--exclude-events")) {
                String filter = options.remove();
                warnForWildcardExpansion("--exclude-events", filter);
                var f = Filters.createEventTypeFilter(filter, types);
                filters.add(Filters.fromEventType(f.negate()));
            }
            if (acceptFilterOption(options, "--include-categories")) {
                String filter = options.remove();
                warnForWildcardExpansion("--include-categories", filter);
                var f = Filters.createCategoryFilter(filter, types);
                filters.add(Filters.fromEventType(f));
            }
            if (acceptFilterOption(options, "--exclude-categories")) {
                String filter = options.remove();
                warnForWildcardExpansion("--exclude-categories", filter);
                var f = Filters.createCategoryFilter(filter, types);
                filters.add(Filters.fromEventType(f.negate()));
            }
            if (acceptFilterOption(options, "--include-threads")) {
                String filter = options.remove();
                warnForWildcardExpansion("--include-threads", filter);
                var f = Filters.createThreadFilter(filter);
                filters.add(Filters.fromRecordedThread(f));
            }
            if (acceptFilterOption(options, "--exclude-threads")) {
                String filter = options.remove();
                warnForWildcardExpansion("--exclude-threads", filter);
                var f = Filters.createThreadFilter(filter);
                filters.add(Filters.fromRecordedThread(f).negate());
            }
            if (optionCount == options.size()) {
                // No progress made
                checkCommonError(options, "--include-event", "--include-events");
                checkCommonError(options, "--include-category", "--include-categories");
                checkCommonError(options, "--include-thread", "--include-threads");
                throw new UserSyntaxException("unknown option " + options.peek());
            }
            optionCount = options.size();
        }
        return Filters.matchAny(filters);
    }

    private void ensureUsableOutput(Path input, Path output) throws UserSyntaxException, UserDataException {
        if (!Files.exists(output)) {
            return;
        }
        if (!Files.exists(input)) {
            return; // Will fail later when reading file
        }
        try {
            if (Files.isSameFile(input, output)) {
                throw new UserSyntaxException("output file can't be same as input file " + input.toAbsolutePath());
            }
        } catch (IOException e) {
            throw new UserDataException("could not access " + input.toAbsolutePath() + " or " + output.toAbsolutePath() + ". " + e.getMessage());
        }
        try {
            Files.delete(output);
        } catch (IOException e) {
            throw new UserDataException("could not delete existing output file " + output.toAbsolutePath() + ". " + e.getMessage());
        }
    }
}
