/*
 * Copyright (c) 2016, 2025, Oracle and/or its affiliates. All rights reserved.
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
import java.io.PrintWriter;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.SequencedSet;
import java.util.StringJoiner;

import jdk.jfr.Contextual;
import jdk.jfr.DataAmount;
import jdk.jfr.EventType;
import jdk.jfr.Frequency;
import jdk.jfr.MemoryAddress;
import jdk.jfr.Percentage;
import jdk.jfr.ValueDescriptor;
import jdk.jfr.consumer.RecordedClass;
import jdk.jfr.consumer.RecordedClassLoader;
import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.consumer.RecordedFrame;
import jdk.jfr.consumer.RecordedMethod;
import jdk.jfr.consumer.RecordedObject;
import jdk.jfr.consumer.RecordedStackTrace;
import jdk.jfr.consumer.RecordedThread;
import jdk.jfr.consumer.RecordingFile;
import jdk.jfr.internal.util.ValueFormatter;

/**
 * Print events in a human-readable format.
 *
 * This class is also used by {@link RecordedObject#toString()}
 */
public final class PrettyWriter extends EventPrintWriter {
    private static record Timestamp(RecordedEvent event, long seconds, int nanosCompare, boolean contextual) implements Comparable<Timestamp> {
        // If the start timestamp from a contextual event has the same start timestamp
        // as an ordinary instant event, the contextual event should be processed first
        // One way to ensure this is to multiply the nanos value and add 1 ns to the end
        // timestamp so the context event always comes first in a comparison.
        // This also prevents a contextual start time to be processed after a contextual
        // end time, if the event is instantaneous.
        public static Timestamp createStart(RecordedEvent event, boolean contextual) {
            Instant time = event.getStartTime(); // Method allocates, so store seconds and nanos
            return new Timestamp(event, time.getEpochSecond(), 2 * time.getNano(), contextual);
        }

        public static Timestamp createEnd(RecordedEvent event, boolean contextual) {
            Instant time = event.getEndTime(); // Method allocates, so store seconds and nanos
            return new Timestamp(event, time.getEpochSecond(), 2 * time.getNano() + 1, contextual);
        }

        public boolean start() {
            return (nanosCompare & 1L) == 0;
        }

        @Override
        public int compareTo(Timestamp that) {
            // This is taken from Instant::compareTo
            int cmp = Long.compare(seconds, that.seconds);
            if (cmp != 0) {
                return cmp;
            }
            return nanosCompare - that.nanosCompare;
        }
    }

    private static record TypeInformation(Long id, List<ValueDescriptor> contextualFields, boolean contextual, String simpleName) {
    }

    private static final SequencedSet<RecordedEvent> EMPTY_SET = new LinkedHashSet<>();
    private static final String TYPE_OLD_OBJECT = "jdk.types.OldObject";
    private static final DateTimeFormatter TIME_FORMAT_EXACT = DateTimeFormatter.ofPattern("HH:mm:ss.SSSSSSSSS (yyyy-MM-dd)");
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss.SSS (yyyy-MM-dd)");
    private static final Long ZERO = 0L;
    // Rationale for using one million events in the window.
    // Events in JFR arrive in batches. The commit time (end time) of an
    // event in batch N typically doesn't come before any events in batch N - 1,
    // but it can't be ruled out completely. Data is also partitioned into chunks,
    // typically 16 MB each. Within a chunk, there must be at least one batch.
    // The size of an event is typically more than 16 bytes, so an
    // EVENT_WINDOW_SIZE of 1 000 000 events will likely cover more than one batch.
    // Having at least two batches in a window avoids boundary issues.
    // At the same time, a too large window, means it will take more time
    // before the first event is printed and the tool will feel unresponsive.
    private static final int EVENT_WINDOW_SIZE = 1_000_000;
    private final boolean showExact;
    private RecordedEvent currentEvent;
    private PriorityQueue<Timestamp> timeline;
    private Map<Long, TypeInformation> typeInformation;
    private Map<Long, SequencedSet<RecordedEvent>> contexts;

    public PrettyWriter(PrintWriter destination, boolean showExact) {
        super(destination);
        this.showExact = showExact;
    }

    public PrettyWriter(PrintWriter destination) {
        this(destination, false);
    }

    void print(Path source) throws IOException {
        timeline = new PriorityQueue<>(EVENT_WINDOW_SIZE + 4);
        typeInformation = new HashMap<>();
        contexts = new HashMap<>();
        printBegin();
        int counter = 0;
        try (RecordingFile file = new RecordingFile(source)) {
            while (file.hasMoreEvents()) {
                RecordedEvent event = file.readEvent();
                if (typeInformation(event).contextual()) {
                    timeline.add(Timestamp.createStart(event, true));
                    timeline.add(Timestamp.createEnd(event, true));
                }
                if (acceptEvent(event)) {
                    timeline.add(Timestamp.createEnd(event, false));
                }
                // There should not be a limit on the size of the recording files that
                // the 'jfr' tool can process. To avoid OutOfMemoryError and time complexity
                // issues on large recordings, a window size must be set when sorting
                // and processing contextual events.
                while (timeline.size() > EVENT_WINDOW_SIZE) {
                    print(timeline.remove());
                    flush(false);
                }
                if ((++counter % EVENT_WINDOW_SIZE) == 0) {
                    contexts.entrySet().removeIf(c -> c.getValue().isEmpty());
                }
            }
            while (!timeline.isEmpty()) {
                print(timeline.remove());
            }
        }
        printEnd();
        flush(true);
    }

    private TypeInformation typeInformation(RecordedEvent event) {
        long id = event.getEventType().getId();
        TypeInformation ti = typeInformation.get(id);
        if (ti == null) {
            ti = createTypeInformation(event.getEventType());
            typeInformation.put(ti.id(), ti);
        }
        return ti;
    }

    private TypeInformation createTypeInformation(EventType eventType) {
        ArrayList<ValueDescriptor> contextualFields = new ArrayList<>();
        for (ValueDescriptor v : eventType.getFields()) {
            if (v.getAnnotation(Contextual.class) != null) {
                contextualFields.add(v);
            }
        }
        contextualFields.trimToSize();
        String name = eventType.getName();
        String simpleName = name.substring(name.lastIndexOf(".") + 1);
        boolean contextual = contextualFields.size() > 0;
        return new TypeInformation(eventType.getId(), contextualFields, contextual, simpleName);
    }

    private void print(Timestamp t) {
        RecordedEvent event = t.event();
        RecordedThread rt = event.getThread();
        if (rt != null) {
            processThreadedTimestamp(rt, t);
        } else {
            if (!t.contextual()) {
                print(event);
            }
        }
    }

    public void processThreadedTimestamp(RecordedThread thread, Timestamp t) {
        RecordedEvent event = t.event();
        var contextEvents = contexts.computeIfAbsent(thread.getId(), k -> new LinkedHashSet<>(1));
        if (t.contextual) {
            if (t.start()) {
                contextEvents.add(event);
            } else {
                contextEvents.remove(event);
            }
            return;
        }
        if (typeInformation(event).contextual()) {
            print(event);
        } else {
            print(event, contextEvents);
        }
    }

    @Override
    protected void print(List<RecordedEvent> events) {
        throw new InternalError("Should not reach here!");
    }

    public void print(RecordedEvent event) {
        print(event, EMPTY_SET);
    }

    public void print(RecordedEvent event, SequencedSet<RecordedEvent> context) {
        currentEvent = event;
        print(event.getEventType().getName(), " ");
        println("{");
        indent();
        if (!context.isEmpty()) {
            printContexts(context);
        }
        for (ValueDescriptor v : event.getFields()) {
            String name = v.getName();
            if (!isZeroDuration(event, name) && !isLateField(name)) {
                printFieldValue(event, v);
            }
        }
        if (event.getThread() != null) {
            printIndent();
            print(EVENT_THREAD_FIELD + " = ");
            printThread(event.getThread(), "");
        }
        if (event.getStackTrace() != null) {
            printIndent();
            print(STACK_TRACE_FIELD + " = ");
            printStackTrace(event.getStackTrace());
        }
        retract();
        printIndent();
        println("}");
        println();
    }

    private void printContexts(SequencedSet<RecordedEvent> contextEvents) {
        for (RecordedEvent e : contextEvents) {
            printContextFields(e);
        }
    }

    private void printContextFields(RecordedEvent contextEvent) {
        TypeInformation ti = typeInformation(contextEvent);
        for (ValueDescriptor v : ti.contextualFields()) {
            printIndent();
            String name = "Context: " + ti.simpleName() + "." + v.getName();
            print(name, " = ");
            printValue(getValue(contextEvent, v), v, "");
        }
    }

    private boolean isZeroDuration(RecordedEvent event, String name) {
        return name.equals("duration") && ZERO.equals(event.getValue("duration"));
    }

    private void printStackTrace(RecordedStackTrace stackTrace) {
        println("[");
        List<RecordedFrame> frames = stackTrace.getFrames();
        indent();
        int i = 0;
        int depth = 0;
        while (i < frames.size() && depth < getStackDepth()) {
            RecordedFrame frame = frames.get(i);
            if (frame.isJavaFrame() && !frame.getMethod().isHidden()) {
                printIndent();
                printValue(frame, null, "");
                println();
                depth++;
            }
            i++;
        }
        if (stackTrace.isTruncated() || i == getStackDepth()) {
            printIndent();
            println("...");
        }
        retract();
        printIndent();
        println("]");
    }

    public void print(RecordedObject struct, String postFix) {
        println("{");
        indent();
        for (ValueDescriptor v : struct.getFields()) {
            printFieldValue(struct, v);
        }
        retract();
        printIndent();
        println("}" + postFix);
    }

    private void printFieldValue(RecordedObject struct, ValueDescriptor v) {
        printIndent();
        print(v.getName(), " = ");
        printValue(getValue(struct, v), v, "");
    }

    private void printArray(Object[] array) {
        println("[");
        indent();
        for (int i = 0; i < array.length; i++) {
            printIndent();
            printValue(array[i], null, i + 1 < array.length ? ", " : "");
        }
        retract();
        printIndent();
        println("]");
    }

    private void printValue(Object value, ValueDescriptor field, String postFix) {
        if (value == null) {
            println("N/A" + postFix);
            return;
        }
        if (value instanceof RecordedObject) {
            if (value instanceof RecordedThread rt) {
                printThread(rt, postFix);
                return;
            }
            if (value instanceof RecordedClass rc) {
                printClass(rc, postFix);
                return;
            }
            if (value instanceof RecordedClassLoader rcl) {
                printClassLoader(rcl, postFix);
                return;
            }
            if (value instanceof RecordedFrame frame) {
                if (frame.isJavaFrame()) {
                    printJavaFrame((RecordedFrame) value, postFix);
                    return;
                }
            }
            if (value instanceof RecordedMethod rm) {
                println(formatMethod(rm));
                return;
            }
            if (field.getTypeName().equals(TYPE_OLD_OBJECT)) {
                printOldObject((RecordedObject) value);
                return;
            }
             print((RecordedObject) value, postFix);
            return;
        }
        if (value.getClass().isArray()) {
            printArray((Object[]) value);
            return;
        }

        if (value instanceof Double d) {
            if (Double.isNaN(d) || d == Double.NEGATIVE_INFINITY) {
                println("N/A");
                return;
            }
        }
        if (value instanceof Float f) {
            if (Float.isNaN(f) || f == Float.NEGATIVE_INFINITY) {
                println("N/A");
                return;
            }
        }
        if (value instanceof Long l) {
            if (l == Long.MIN_VALUE) {
                println("N/A");
                return;
            }
        }
        if (value instanceof Integer i) {
            if (i == Integer.MIN_VALUE) {
                println("N/A");
                return;
            }
        }

        if (field.getContentType() != null) {
            if (printFormatted(field, value)) {
                return;
            }
        }

        String text = String.valueOf(value);
        if (value instanceof String) {
            text = "\"" + text + "\"";
        }
        println(text);
    }

    private void printOldObject(RecordedObject object) {
        println(" [");
        indent();
        printIndent();
        try {
            printReferenceChain(object);
        } catch (IllegalArgumentException iae) {
           // Could not find a field
           // Not possible to validate fields beforehand using RecordedObject#hasField
           // since nested objects, for example object.referrer.array.index, requires
           // an actual array object (which may be null).
        }
        retract();
        printIndent();
        println("]");
    }

    private void printReferenceChain(RecordedObject object) {
        printObject(object, currentEvent.getLong("arrayElements"));
        for (RecordedObject ref = object.getValue("referrer"); ref != null; ref = object.getValue("referrer")) {
            long skip = ref.getLong("skip");
            if (skip > 0) {
                printIndent();
                println("...");
            }
            String objectHolder = "";
            long size = Long.MIN_VALUE;
            RecordedObject array = ref.getValue("array");
            if (array != null) {
                long index = array.getLong("index");
                size = array.getLong("size");
                objectHolder = "[" + index + "]";
            }
            RecordedObject field = ref.getValue("field");
            if (field != null) {
                objectHolder = field.getString("name");
            }
            printIndent();
            print(objectHolder);
            print(" : ");
            object = ref.getValue("object");
            if (object != null) {
                printObject(object, size);
            }
        }
    }

    void printObject(RecordedObject object, long arraySize) {
        RecordedClass clazz = object.getClass("type");
        if (clazz != null) {
            String className = clazz.getName();
            if (className!= null && className.startsWith("[")) {
                className = ValueFormatter.decodeDescriptors(className, arraySize > 0 ? Long.toString(arraySize) : "").getFirst();
            }
            print(className);
            String description = object.getString("description");
            if (description != null) {
                print(" ");
                print(description);
            }
        }
        println();
    }

    private void printClassLoader(RecordedClassLoader cl, String postFix) {
        // Purposely not printing class loader name to avoid cluttered output
        RecordedClass clazz = cl.getType();
        if (clazz != null) {
            print(clazz.getName());
            print(" (");
            print("id = ");
            print(String.valueOf(cl.getId()));
            print(")");
        } else {
            print("null");
        }
        println(postFix);
    }

    private void printJavaFrame(RecordedFrame f, String postFix) {
        print(formatMethod(f.getMethod()));
        int line = f.getLineNumber();
        if (line >= 0) {
            print(" line: " + line);
        }
        print(postFix);
    }

    private String formatMethod(RecordedMethod m) {
        StringBuilder sb = new StringBuilder();
        sb.append(m.getType().getName());
        sb.append(".");
        sb.append(m.getName());
        sb.append("(");
        StringJoiner sj = new StringJoiner(", ");
        String md = m.getDescriptor().replace("/", ".");
        String parameter = md.substring(1, md.lastIndexOf(")"));
        for (String qualifiedName : ValueFormatter.decodeDescriptors(parameter, "")) {
            String typeName = qualifiedName.substring(qualifiedName.lastIndexOf('.') + 1);
            sj.add(typeName);
        }
        sb.append(sj);
        sb.append(")");
        return sb.toString();
    }

    private void printClass(RecordedClass clazz, String postFix) {
        RecordedClassLoader classLoader = clazz.getClassLoader();
        String classLoaderName = "null";
        if (classLoader != null) {
            if (classLoader.getName() != null) {
                classLoaderName = classLoader.getName();
            } else {
                classLoaderName = classLoader.getType().getName();
            }
        }
        String className = clazz.getName();
        if (className.startsWith("[")) {
            className = ValueFormatter.decodeDescriptors(className, "").getFirst();
        }
        println(className + " (classLoader = " + classLoaderName + ")" + postFix);
    }

    private void printThread(RecordedThread thread, String postFix) {
        long javaThreadId = thread.getJavaThreadId();
        if (javaThreadId > 0) {
            String virtualText = thread.isVirtual() ? ", virtual" : "";
            println("\"" + thread.getJavaName() + "\" (javaThreadId = " + javaThreadId + virtualText + ")" + postFix);
        } else {
            println("\"" + thread.getOSName() + "\" (osThreadId = " + thread.getOSThreadId() + ")" + postFix);
        }
    }

    private boolean printFormatted(ValueDescriptor field, Object value) {
        if (value instanceof Duration d) {
            if (d.getSeconds() == Long.MIN_VALUE && d.getNano() == 0)  {
                println("N/A");
                return true;
            }
            if (d.equals(ChronoUnit.FOREVER.getDuration())) {
                println("Forever");
                return true;
            }
            if (showExact) {
                println(String.format("%.9f s", (double) d.toNanos() / 1_000_000_000));
            } else {
                println(ValueFormatter.formatDuration(d));
            }
            return true;
        }
        if (value instanceof OffsetDateTime odt) {
            if (odt.equals(OffsetDateTime.MIN))  {
                println("N/A");
                return true;
            }
            if (showExact) {
                println(TIME_FORMAT_EXACT.format(odt));
            } else {
                println(TIME_FORMAT.format(odt));
            }
            return true;
        }
        Percentage percentage = field.getAnnotation(Percentage.class);
        if (percentage != null) {
            if (value instanceof Number n) {
                double p = 100 * n.doubleValue();
                if (showExact) {
                    println(String.format("%.9f%%", p));
                } else {
                    println(String.format("%.2f%%", p));
                }
                return true;
            }
        }
        DataAmount dataAmount = field.getAnnotation(DataAmount.class);
        if (dataAmount != null && value instanceof Number number) {
            boolean frequency = field.getAnnotation(Frequency.class) != null;
            String unit = dataAmount.value();
            boolean bits = unit.equals(DataAmount.BITS);
            boolean bytes = unit.equals(DataAmount.BYTES);
            if (bits || bytes) {
                formatMemory(number.longValue(), bytes, frequency);
                return true;
            }
        }
        MemoryAddress memoryAddress = field.getAnnotation(MemoryAddress.class);
        if (memoryAddress != null) {
            if (value instanceof Number n) {
                long d = n.longValue();
                println(String.format("0x%08X", d));
                return true;
            }
        }
        Frequency frequency = field.getAnnotation(Frequency.class);
        if (frequency != null) {
            if (value instanceof Number) {
                println(value + " Hz");
                return true;
            }
        }

        return false;
    }

    private void formatMemory(long value, boolean bytesUnit, boolean frequency) {
        if (showExact) {
            StringBuilder sb = new StringBuilder();
            sb.append(value);
            sb.append(bytesUnit ? " byte" : " bit");
            if (value > 1) {
                sb.append("s");
            }
            if (frequency) {
                sb.append("/s");
            }
            println(sb.toString());
            return;
        }
        if (frequency) {
            if (bytesUnit) {
                println(ValueFormatter.formatBytesPerSecond(value));
            } else {
                println(ValueFormatter.formatBitsPerSecond(value));
            }
            return;
        }
        if (bytesUnit) {
            println(ValueFormatter.formatBytes(value));
        } else {
            println(ValueFormatter.formatBits(value));
        }
    }
}
