/*
 * Copyright (c) 2020, 2025, Oracle and/or its affiliates. All rights reserved.
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
package jdk.internal.vm;

import java.io.BufferedWriter;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;

/**
 * Thread dump support.
 *
 * This class defines static methods to support the Thread.dump_to_file diagnostic command
 * and the HotSpotDiagnosticMXBean.dumpThreads API. It defines methods to generate a
 * thread dump to a file or byte array in plain text or JSON format.
 */
public class ThreadDumper {
    private ThreadDumper() { }

    // the maximum byte array to return when generating the thread dump to a byte array
    private static final int MAX_BYTE_ARRAY_SIZE = 16_000;

    /**
     * Generate a thread dump in plain text format to a file or byte array, UTF-8 encoded.
     * This method is invoked by the VM for the Thread.dump_to_file diagnostic command.
     *
     * @param file the file path to the file, null or "-" to return a byte array
     * @param okayToOverwrite true to overwrite an existing file
     * @return the UTF-8 encoded thread dump or message to return to the tool user
     */
    public static byte[] dumpThreads(String file, boolean okayToOverwrite) {
        if (file == null || file.equals("-")) {
            return dumpThreadsToByteArray(false, MAX_BYTE_ARRAY_SIZE);
        } else {
            return dumpThreadsToFile(file, okayToOverwrite, false);
        }
    }

    /**
     * Generate a thread dump in JSON format to a file or byte array, UTF-8 encoded.
     * This method is invoked by the VM for the Thread.dump_to_file diagnostic command.
     *
     * @param file the file path to the file, null or "-" to return a byte array
     * @param okayToOverwrite true to overwrite an existing file
     * @return the UTF-8 encoded thread dump or message to return to the tool user
     */
    public static byte[] dumpThreadsToJson(String file, boolean okayToOverwrite) {
        if (file == null || file.equals("-")) {
            return dumpThreadsToByteArray(true, MAX_BYTE_ARRAY_SIZE);
        } else {
            return dumpThreadsToFile(file, okayToOverwrite, true);
        }
    }

    /**
     * Generate a thread dump in plain text or JSON format to a byte array, UTF-8 encoded.
     * This method is the implementation of the Thread.dump_to_file diagnostic command
     * when a file path is not specified. It returns the thread dump and/or message to
     * send to the tool user.
     */
    private static byte[] dumpThreadsToByteArray(boolean json, int maxSize) {
        var out = new BoundedByteArrayOutputStream(maxSize);
        try (out; var writer = new TextWriter(out)) {
            if (json) {
                dumpThreadsToJson(writer);
            } else {
                dumpThreads(writer);
            }
        } catch (Exception ex) {
            if (ex instanceof UncheckedIOException ioe) {
                ex = ioe.getCause();
            }
            String reply = String.format("Failed: %s%n", ex);
            return reply.getBytes(StandardCharsets.UTF_8);
        }
        return out.toByteArray();
    }

    /**
     * Generate a thread dump in plain text or JSON format to the given file, UTF-8 encoded.
     * This method is the implementation of the Thread.dump_to_file diagnostic command.
     * It returns the thread dump and/or message to send to the tool user.
     */
    private static byte[] dumpThreadsToFile(String file, boolean okayToOverwrite, boolean json) {
        Path path = Path.of(file).toAbsolutePath();
        OpenOption[] options = (okayToOverwrite)
                ? new OpenOption[0]
                : new OpenOption[] { StandardOpenOption.CREATE_NEW };
        String reply;
        try (OutputStream out = Files.newOutputStream(path, options)) {
            try (var writer = new TextWriter(out)) {
                if (json) {
                    dumpThreadsToJson(writer);
                } else {
                    dumpThreads(writer);
                }
                reply = String.format("Created %s%n", path);
            } catch (UncheckedIOException e) {
                reply = String.format("Failed: %s%n", e.getCause());
            }
        } catch (FileAlreadyExistsException _) {
            reply = String.format("%s exists, use -overwrite to overwrite%n", path);
        } catch (Exception ex) {
            reply = String.format("Failed: %s%n", ex);
        }
        return reply.getBytes(StandardCharsets.UTF_8);
    }

    /**
     * Generate a thread dump in plain text format to the given output stream, UTF-8
     * encoded. This method is invoked by HotSpotDiagnosticMXBean.dumpThreads.
     * @throws IOException if an I/O error occurs
     */
    public static void dumpThreads(OutputStream out) throws IOException {
        var writer = new TextWriter(out);
        try {
            dumpThreads(writer);
            writer.flush();
        } catch (UncheckedIOException e) {
            IOException ioe = e.getCause();
            throw ioe;
        }
    }

    /**
     * Generate a thread dump in plain text format to the given text stream.
     * @throws UncheckedIOException if an I/O error occurs
     */
    private static void dumpThreads(TextWriter writer) {
        writer.println(processId());
        writer.println(Instant.now());
        writer.println(Runtime.version());
        writer.println();
        dumpThreads(ThreadContainers.root(), writer);
    }

    private static void dumpThreads(ThreadContainer container, TextWriter writer) {
        container.threads().forEach(t -> dumpThread(t, writer));
        container.children().forEach(c -> dumpThreads(c, writer));
    }

    private static boolean dumpThread(Thread thread, TextWriter writer) {
        ThreadSnapshot snapshot = ThreadSnapshot.of(thread);
        if (snapshot == null) {
            return false; // thread terminated
        }
        Instant now = Instant.now();
        Thread.State state = snapshot.threadState();
        writer.println("#" + thread.threadId() + " \"" + snapshot.threadName()
                + "\" " + (thread.isVirtual() ? "virtual " : "") + state + " " + now);

        StackTraceElement[] stackTrace = snapshot.stackTrace();
        int depth = 0;
        while (depth < stackTrace.length) {
            writer.print("    at ");
            writer.println(stackTrace[depth]);
            snapshot.ownedMonitorsAt(depth).forEach(o -> {
                if (o != null) {
                    writer.println("    - locked " + decorateObject(o));
                } else {
                    writer.println("    - lock is eliminated");
                }
            });

            // if parkBlocker set, or blocked/waiting on monitor, then print after top frame
            if (depth == 0) {
                // park blocker
                Object parkBlocker = snapshot.parkBlocker();
                if (parkBlocker != null) {
                    writer.println("    - parking to wait for " + decorateObject(parkBlocker));
                }

                // blocked on monitor enter or Object.wait
                if (state == Thread.State.BLOCKED && snapshot.blockedOn() instanceof Object obj) {
                    writer.println("    - waiting to lock " + decorateObject(obj));
                } else if ((state == Thread.State.WAITING || state == Thread.State.TIMED_WAITING)
                        && snapshot.waitingOn() instanceof Object obj) {
                    writer.println("    - waiting on " + decorateObject(obj));
                }
            }

            depth++;
        }
        writer.println();
        return true;
    }

    /**
     * Returns the identity string for the given object in a form suitable for the plain
     * text format thread dump.
     */
    private static String decorateObject(Object obj) {
        return "<" + Objects.toIdentityString(obj) + ">";
    }

    /**
     * Generate a thread dump in JSON format to the given output stream, UTF-8 encoded.
     * This method is invoked by HotSpotDiagnosticMXBean.dumpThreads.
     * @throws IOException if an I/O error occurs
     */
    public static void dumpThreadsToJson(OutputStream out) throws IOException {
        var writer = new TextWriter(out);
        try {
            dumpThreadsToJson(writer);
            writer.flush();
        } catch (UncheckedIOException e) {
            IOException ioe = e.getCause();
            throw ioe;
        }
    }

    /**
     * Generate a thread dump to the given text stream in JSON format.
     * @throws UncheckedIOException if an I/O error occurs
     */
    private static void dumpThreadsToJson(TextWriter textWriter) {
        var jsonWriter = new JsonWriter(textWriter);

        jsonWriter.startObject();  // top-level object

        jsonWriter.startObject("threadDump");

        jsonWriter.writeProperty("processId", processId());
        jsonWriter.writeProperty("time", Instant.now());
        jsonWriter.writeProperty("runtimeVersion", Runtime.version());

        jsonWriter.startArray("threadContainers");
        dumpThreads(ThreadContainers.root(), jsonWriter);
        jsonWriter.endArray();

        jsonWriter.endObject();  // threadDump

        jsonWriter.endObject();  // end of top-level object
    }

    /**
     * Write a thread container to the given JSON writer.
     * @throws UncheckedIOException if an I/O error occurs
     */
    private static void dumpThreads(ThreadContainer container, JsonWriter jsonWriter) {
        jsonWriter.startObject();
        jsonWriter.writeProperty("container", container);
        jsonWriter.writeProperty("parent", container.parent());

        Thread owner = container.owner();
        jsonWriter.writeProperty("owner", (owner != null) ? owner.threadId() : null);

        long threadCount = 0;
        jsonWriter.startArray("threads");
        Iterator<Thread> threads = container.threads().iterator();
        while (threads.hasNext()) {
            Thread thread = threads.next();
            if (dumpThread(thread, jsonWriter)) {
                threadCount++;
            }
        }
        jsonWriter.endArray(); // threads

        // thread count
        if (!ThreadContainers.trackAllThreads()) {
            threadCount = Long.max(threadCount, container.threadCount());
        }
        jsonWriter.writeProperty("threadCount", threadCount);

        jsonWriter.endObject();

        // the children of the thread container follow
        container.children().forEach(c -> dumpThreads(c, jsonWriter));
    }

    /**
     * Write a thread to the given JSON writer.
     * @return true if the thread dump was written, false otherwise
     * @throws UncheckedIOException if an I/O error occurs
     */
    private static boolean dumpThread(Thread thread, JsonWriter jsonWriter) {
        Instant now = Instant.now();
        ThreadSnapshot snapshot = ThreadSnapshot.of(thread);
        if (snapshot == null) {
            return false; // thread terminated
        }
        Thread.State state = snapshot.threadState();
        StackTraceElement[] stackTrace = snapshot.stackTrace();

        jsonWriter.startObject();
        jsonWriter.writeProperty("tid", thread.threadId());
        jsonWriter.writeProperty("time", now);
        if (thread.isVirtual()) {
            jsonWriter.writeProperty("virtual", Boolean.TRUE);
        }
        jsonWriter.writeProperty("name", snapshot.threadName());
        jsonWriter.writeProperty("state", state);

        // park blocker
        Object parkBlocker = snapshot.parkBlocker();
        if (parkBlocker != null) {
            // parkBlocker is an object to allow for exclusiveOwnerThread in the future
            jsonWriter.startObject("parkBlocker");
            jsonWriter.writeProperty("object", Objects.toIdentityString(parkBlocker));
            jsonWriter.endObject();
        }

        // blocked on monitor enter or Object.wait
        if (state == Thread.State.BLOCKED && snapshot.blockedOn() instanceof Object obj) {
            jsonWriter.writeProperty("blockedOn", Objects.toIdentityString(obj));
        } else if ((state == Thread.State.WAITING || state == Thread.State.TIMED_WAITING)
                && snapshot.waitingOn() instanceof Object obj) {
            jsonWriter.writeProperty("waitingOn", Objects.toIdentityString(obj));
        }

        // stack trace
        jsonWriter.startArray("stack");
        Arrays.stream(stackTrace).forEach(jsonWriter::writeProperty);
        jsonWriter.endArray();

        // monitors owned, skip if none
        if (snapshot.ownsMonitors()) {
            jsonWriter.startArray("monitorsOwned");
            int depth = 0;
            while (depth < stackTrace.length) {
                List<Object> objs = snapshot.ownedMonitorsAt(depth).toList();
                if (!objs.isEmpty()) {
                    jsonWriter.startObject();
                    jsonWriter.writeProperty("depth", depth);
                    jsonWriter.startArray("locks");
                    snapshot.ownedMonitorsAt(depth)
                            .map(o -> (o != null) ? Objects.toIdentityString(o) : null)
                            .forEach(jsonWriter::writeProperty);
                    jsonWriter.endArray();
                    jsonWriter.endObject();
                }
                depth++;
            }
            jsonWriter.endArray();
        }

        // thread identifier of carrier, when mounted
        if (thread.isVirtual() && snapshot.carrierThread() instanceof Thread carrier) {
            jsonWriter.writeProperty("carrier", carrier.threadId());
        }

        jsonWriter.endObject();
        return true;
    }

    /**
     * Simple JSON writer to stream objects/arrays to a TextWriter with formatting.
     * This class is not intended to be a fully featured JSON writer.
     */
    private static class JsonWriter {
        private static class Node {
            final boolean isArray;
            int propertyCount;
            Node(boolean isArray) {
                this.isArray = isArray;
            }
            boolean isArray() {
                return isArray;
            }
            int propertyCount() {
                return propertyCount;
            }
            int getAndIncrementPropertyCount() {
                int old = propertyCount;
                propertyCount++;
                return old;
            }
        }
        private final Deque<Node> stack = new ArrayDeque<>();
        private final TextWriter writer;

        JsonWriter(TextWriter writer) {
            this.writer = writer;
        }

        private void indent() {
            int indent = stack.size() * 2;
            writer.print(" ".repeat(indent));
        }

        /**
         * Start of object or array.
         */
        private void startObject(String name, boolean isArray) {
            if (!stack.isEmpty()) {
                Node node = stack.peek();
                if (node.getAndIncrementPropertyCount() > 0) {
                    writer.println(",");
                }
            }
            indent();
            if (name != null) {
                writer.print("\"" + name + "\": ");
            }
            writer.println(isArray ? "[" : "{");
            stack.push(new Node(isArray));
        }

        /**
         * End of object or array.
         */
        private void endObject(boolean isArray) {
            Node node = stack.pop();
            if (node.isArray() != isArray)
                throw new IllegalStateException();
            if (node.propertyCount() > 0) {
                writer.println();
            }
            indent();
            writer.print(isArray ? "]" : "}");
        }

        /**
         * Write a property.
         * @param name the property name, null for an unnamed property
         * @param obj the value or null
         */
        void writeProperty(String name, Object obj) {
            Node node = stack.peek();
            if (node.getAndIncrementPropertyCount() > 0) {
                writer.println(",");
            }
            indent();
            if (name != null) {
                writer.print("\"" + name + "\": ");
            }
            switch (obj) {
                // Long may be larger than safe range of JSON integer value
                case Long   _  -> writer.print("\"" + obj + "\"");
                case Number _  -> writer.print(obj);
                case Boolean _ -> writer.print(obj);
                case null      -> writer.print("null");
                default        -> writer.print("\"" + escape(obj.toString()) + "\"");
            }
        }

        /**
         * Write an unnamed property.
         */
        void writeProperty(Object obj) {
            writeProperty(null, obj);
        }

        /**
         * Start named object.
         */
        void startObject(String name) {
            startObject(name, false);
        }

        /**
         * Start unnamed object.
         */
        void startObject() {
            startObject(null);
        }

        /**
         * End of object.
         */
        void endObject() {
            endObject(false);
        }

        /**
         * Start named array.
         */
        void startArray(String name) {
            startObject(name, true);
        }

        /**
         * End of array.
         */
        void endArray() {
            endObject(true);
        }

        /**
         * Escape any characters that need to be escape in the JSON output.
         */
        private static String escape(String value) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < value.length(); i++) {
                char c = value.charAt(i);
                switch (c) {
                    case '"'  -> sb.append("\\\"");
                    case '\\' -> sb.append("\\\\");
                    case '/'  -> sb.append("\\/");
                    case '\b' -> sb.append("\\b");
                    case '\f' -> sb.append("\\f");
                    case '\n' -> sb.append("\\n");
                    case '\r' -> sb.append("\\r");
                    case '\t' -> sb.append("\\t");
                    default -> {
                        if (c <= 0x1f) {
                            sb.append(String.format("\\u%04x", c));
                        } else {
                            sb.append(c);
                        }
                    }
                }
            }
            return sb.toString();
        }
    }

    /**
     * A ByteArrayOutputStream of bounded size. Once the maximum number of bytes is
     * written the subsequent bytes are discarded.
     */
    private static class BoundedByteArrayOutputStream extends ByteArrayOutputStream {
        final int max;
        BoundedByteArrayOutputStream(int max) {
            this.max = max;
        }
        @Override
        public void write(int b) {
            if (max < count) {
                super.write(b);
            }
        }
        @Override
        public void write(byte[] b, int off, int len) {
            int remaining = max - count;
            if (remaining > 0) {
                super.write(b, off, Integer.min(len, remaining));
            }
        }
        @Override
        public void close() {
        }
    }

    /**
     * Simple Writer implementation for printing text. The print/println methods
     * throw UncheckedIOException if an I/O error occurs.
     */
    private static class TextWriter extends Writer {
        private final Writer delegate;

        TextWriter(OutputStream out) {
            delegate = new BufferedWriter(new OutputStreamWriter(out, StandardCharsets.UTF_8));
        }

        @Override
        public void write(char[] cbuf, int off, int len) throws IOException {
            delegate.write(cbuf, off, len);
        }

        void print(Object obj) {
            String s = String.valueOf(obj);
            try {
                write(s, 0, s.length());
            } catch (IOException ioe) {
                throw new UncheckedIOException(ioe);
            }
        }

        void println() {
            print(System.lineSeparator());
        }

        void println(String s) {
            print(s);
            println();
        }

        void println(Object obj) {
            print(obj);
            println();
        }

        @Override
        public void flush() throws IOException {
            delegate.flush();
        }

        @Override
        public void close() throws IOException {
            delegate.close();
        }
    }

    /**
     * Returns the process ID or -1 if not supported.
     */
    private static long processId() {
        try {
            return ProcessHandle.current().pid();
        } catch (UnsupportedOperationException e) {
            return -1L;
        }
    }
}
