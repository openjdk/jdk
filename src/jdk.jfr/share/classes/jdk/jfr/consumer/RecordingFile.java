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

package jdk.jfr.consumer;

import java.io.Closeable;
import java.io.EOFException;
import java.io.File;
import java.io.IOException;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

import jdk.jfr.EventType;
import jdk.jfr.internal.MetadataDescriptor;
import jdk.jfr.internal.consumer.ChunkHeader;
import jdk.jfr.internal.consumer.RecordingInput;

/**
 * A recording file.
 * <p>
 * The following example shows how read and print all events in a recording file.
 *
 * <pre>
 * <code>
 * try (RecordingFile recordingFile = new RecordingFile(Paths.get("recording.jfr"))) {
 *   while (recordingFile.hasMoreEvents()) {
 *     RecordedEvent event = recordingFile.readEvent();
 *     System.out.println(event);
 *   }
 * }
 * </code>
 * </pre>
 *
 * @since 9
 */
public final class RecordingFile implements Closeable {

    private final File file;
    private RecordingInput input;
    private ChunkParser chunkParser;
    private RecordedEvent nextEvent;
    private boolean eof;

    /**
     * Creates a recording file.
     *
     * @param file the path of the file to open, not {@code null}
     * @throws IOException if it's not a valid recording file, or an I/O error
     *         occurred
     * @throws NoSuchFileException if the {@code file} can't be located
     *
     * @throws SecurityException if a security manager exists and its
     *         {@code checkRead} method denies read access to the file.
     */
    public RecordingFile(Path file) throws IOException {
        this.file = file.toFile();
        this.input = new RecordingInput(this.file);
        findNext();
    }

    /**
     * Reads the next event in the recording.
     *
     * @return the next event, not {@code null}
     *
     * @throws EOFException if no more events exist in the recording file
     * @throws IOException if an I/O error occurs.
     *
     * @see #hasMoreEvents()
     */
    public RecordedEvent readEvent() throws IOException {
        if (eof) {
            ensureOpen();
            throw new EOFException();
        }
        RecordedEvent event = nextEvent;
        nextEvent = chunkParser.readEvent();
        if (nextEvent == null) {
            findNext();
        }
        return event;
    }

    /**
     * Returns {@code true} if unread events exist in the recording file,
     * {@code false} otherwise.
     *
     * @return {@code true} if unread events exist in the recording, {@code false}
     *         otherwise.
     */
    public boolean hasMoreEvents() {
        return !eof;
    }

    /**
     * Returns a list of all event types in this recording.
     *
     * @return a list of event types, not {@code null}
     * @throws IOException if an I/O error occurred while reading from the file
     *
     * @see #hasMoreEvents()
     */
    public List<EventType> readEventTypes() throws IOException {
        ensureOpen();
        List<EventType> types = new ArrayList<>();
        HashSet<Long> foundIds = new HashSet<>();
        try (RecordingInput ri = new RecordingInput(file)) {
            ChunkHeader ch = new ChunkHeader(ri);
            aggregateTypeForChunk(ch, types, foundIds);
            while (!ch.isLastChunk()) {
                ch = ch.nextHeader();
                aggregateTypeForChunk(ch, types, foundIds);
            }
        }
        return types;
    }

    private static void aggregateTypeForChunk(ChunkHeader ch, List<EventType> types, HashSet<Long> foundIds) throws IOException {
        MetadataDescriptor m = ch.readMetadata();
        for (EventType t : m.getEventTypes()) {
            if (!foundIds.contains(t.getId())) {
                types.add(t);
                foundIds.add(t.getId());
            }
        }
    }

    /**
     * Closes this recording file and releases any system resources that are
     * associated with it.
     *
     * @throws IOException if an I/O error occurred
     */
    public void close() throws IOException {
        if (input != null) {
            eof = true;
            input.close();
            chunkParser = null;
            input = null;
            nextEvent = null;
        }
    }

    /**
     * Returns a list of all events in a file.
     * <p>
     * This method is intended for simple cases where it's convenient to read all
     * events in a single operation. It isn't intended for reading large files.
     *
     * @param path the path to the file, not {@code null}
     *
     * @return the events from the file as a {@code List} object; whether the
     *         {@code List} is modifiable or not is implementation dependent and
     *         therefore not specified, not {@code null}
     *
     * @throws IOException if an I/O error occurred, it's not a Flight Recorder
     *         file or a version of a JFR file that can't be parsed
     *
     * @throws SecurityException if a security manager exists and its
     *         {@code checkRead} method denies read access to the file.
     */
    public static List<RecordedEvent> readAllEvents(Path path) throws IOException {
        try (RecordingFile r = new RecordingFile(path)) {
            List<RecordedEvent> list = new ArrayList<>();
            while (r.hasMoreEvents()) {
                list.add(r.readEvent());
            }
            return list;
        }
    }

    // either sets next to an event or sets eof to true
    private void findNext() throws IOException {
        while (nextEvent == null) {
            if (chunkParser == null) {
                chunkParser = new ChunkParser(input);
            } else if (!chunkParser.isLastChunk()) {
                chunkParser = chunkParser.nextChunkParser();
            } else {
                eof = true;
                return;
            }
            nextEvent = chunkParser.readEvent();
        }
    }

    private void ensureOpen() throws IOException {
        if (input == null) {
            throw new IOException("Stream Closed");
        }
    }
}
