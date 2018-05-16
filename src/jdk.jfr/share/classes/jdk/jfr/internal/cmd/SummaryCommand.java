/*
 * Copyright (c) 2016, 2018, Oracle and/or its affiliates. All rights reserved.
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

package jdk.jfr.internal.cmd;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;

import jdk.jfr.EventType;
import jdk.jfr.internal.MetadataDescriptor;
import jdk.jfr.internal.Type;
import jdk.jfr.internal.consumer.ChunkHeader;
import jdk.jfr.internal.consumer.RecordingInput;

final class SummaryCommand extends Command {

    private static class Statistics {
        Statistics(String name) {
            this.name = name;
        }

        String name;
        long count;
        long size;
    }

    @Override
    public String getOptionSyntax() {
        return "<file>";
    }

    @Override
    public void displayOptionUsage() {
        println("  <file>   Location of the recording file (.jfr) to display information about");
    }

    @Override
    public String getName() {
        return "summary";
    }

    @Override
    public String getDescription() {
        return "Display general information about a recording file (.jfr)";
    }

    @Override
    public void execute(Deque<String> options) {
        if (options.isEmpty()) {
            userFailed("Missing file");
        }
        ensureMaxArgumentCount(options, 1);
        Path p = Paths.get(options.remove());
        ensureFileExist(p);
        ensureJFRFile(p);
        try {
            printInformation(p);
        } catch (IOException e) {
            throw new IllegalStateException("Unexpected error. " + e.getMessage());
        }
    }

    private void printInformation(Path p) throws IOException {
        long totalSize = 0;
        long totalDuration = 0;
        long chunks = 0;

        try (RecordingInput input = new RecordingInput(p.toFile())) {
            ChunkHeader first = new ChunkHeader(input);
            ChunkHeader ch = first;
            String eventPrefix = Type.EVENT_NAME_PREFIX;
            if (first.getMajor() == 1) {
                eventPrefix = "com.oracle.jdk.";
            }
            HashMap<Long, Statistics> stats = new HashMap<>();
            stats.put(0L, new Statistics(eventPrefix + "Metadata"));
            stats.put(1L, new Statistics(eventPrefix + "CheckPoint"));
            int minWidth = 0;
            while (true) {
                long chunkEnd = ch.getEnd();
                MetadataDescriptor md = ch.readMetadata();

                for (EventType eventType : md.getEventTypes()) {
                    stats.computeIfAbsent(eventType.getId(), (e) -> new Statistics(eventType.getName()));
                    minWidth = Math.max(minWidth, eventType.getName().length());
                }

                totalSize += ch.getSize();
                totalDuration += ch.getDuration();
                chunks++;
                input.position(ch.getEventStart());
                while (input.position() < chunkEnd) {

                    long pos = input.position();
                    int size = input.readInt();
                    long eventTypeId = input.readLong();
                    Statistics s = stats.get(eventTypeId);

                    if (s != null) {
                        s.count++;
                        s.size += size;
                    }
                    input.position(pos + size);
                }
                if (ch.isLastChunk()) {
                    break;
                }
                ch = ch.nextHeader();
            }
            println();
            long epochSeconds = first.getStartNanos() / 1_000_000_000L;
            long adjustNanos = first.getStartNanos() - epochSeconds * 1_000_000_000L;
            println(" Version: " + first.getMajor() + "." + first.getMinor());
            println(" Chunks: " + chunks);
            println(" Size: " + totalSize + " bytes");
            println(" Start: " + Instant.ofEpochSecond(epochSeconds, adjustNanos));
            println(" Duration: " + Duration.ofNanos(totalDuration));
            println();
            println(" Start Ticks: " + first.getStartTicks());
            println(" Ticks / Second: " + first.getTicksPerSecond());

            List<Statistics> statsList = new ArrayList<>(stats.values());
            Collections.sort(statsList, (u, v) -> Long.compare(v.count, u.count));
            println();
            String header = "      Count  Size (bytes) ";
            String typeHeader = " Event Type";
            minWidth = Math.max(minWidth, typeHeader.length());
            println(typeHeader + pad(minWidth - typeHeader.length(), ' ') + header);
            println(pad(minWidth + header.length(), '='));
            for (Statistics s : statsList) {
                System.out.printf(" %-" + minWidth + "s%10d  %12d\n", s.name, s.count, s.size);
            }
        }
    }

    private String pad(int count, char c) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < count; i++) {
            sb.append(c);
        }
        return sb.toString();
    }
}
