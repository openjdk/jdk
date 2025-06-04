/*
 * Copyright (c) 2018, 2022, Oracle and/or its affiliates. All rights reserved.
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

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintWriter;
import java.math.BigInteger;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

import jdk.jfr.EventType;
import jdk.jfr.Timespan;
import jdk.jfr.Timestamp;
import jdk.jfr.Unsigned;
import jdk.jfr.ValueDescriptor;
import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.consumer.RecordedObject;
import jdk.jfr.consumer.RecordingFile;
import jdk.jfr.internal.consumer.JdkJfrConsumer;

abstract class EventPrintWriter extends StructuredWriter {

    enum ValueType {
        TIMESPAN, TIMESTAMP, UNSIGNED, OTHER
    }

    protected static final String STACK_TRACE_FIELD = "stackTrace";
    protected static final String EVENT_THREAD_FIELD = "eventThread";
    private static final JdkJfrConsumer PRIVATE_ACCESS = JdkJfrConsumer.instance();

    private Predicate<EventType> eventFilter = x -> true;
    private int stackDepth;

    // cache that will speed up annotation lookup
    private Map<ValueDescriptor, ValueType> typeOfValues = new HashMap<>();

    EventPrintWriter(PrintWriter p) {
        super(p);
    }

    protected abstract void print(List<RecordedEvent> events);

    void print(Path source) throws FileNotFoundException, IOException {
        List<RecordedEvent> events = new ArrayList<>(500_000);
        printBegin();
        try (RecordingFile file = new RecordingFile(source)) {
            while (file.hasMoreEvents()) {
                RecordedEvent event = file.readEvent();
                if (acceptEvent(event)) {
                    events.add(event);
                }
                if (PRIVATE_ACCESS.isLastEventInChunk(file)) {
                    events.sort(PRIVATE_ACCESS.eventComparator());
                    print(events);
                    events.clear();
                }
            }
        }
        printEnd();
        flush(true);
    }

    protected void printEnd() {
    }

    protected void printBegin() {
    }

    public final void setEventFilter(Predicate<EventType> eventFilter) {
        this.eventFilter = eventFilter;
    }

    protected final boolean acceptEvent(RecordedEvent event) {
        return eventFilter.test(event.getEventType());
    }

    protected final int getStackDepth() {
        return stackDepth;
    }

    protected final boolean isLateField(String name) {
        return name.equals(EVENT_THREAD_FIELD) || name.equals(STACK_TRACE_FIELD);
    }

    public void setStackDepth(int stackDepth) {
        this.stackDepth = stackDepth;
    }

    protected Object getValue(RecordedObject object, ValueDescriptor v) {
        ValueType valueType = typeOfValues.get(v);
        if (valueType == null) {
            valueType = determineValueType(v);
            typeOfValues.put(v, valueType);
        }
        String name = v.getName();
        return switch (valueType) {
            case TIMESPAN -> object.getDuration(name);
            case TIMESTAMP -> PRIVATE_ACCESS.getOffsetDataTime(object, name);
            case UNSIGNED -> getUnsigned(object, name);
            case OTHER -> object.getValue(name);
        };
    }

    private Object getUnsigned(RecordedObject object, String name) {
        // RecordedObject::getLong handles unsigned byte, short, int
        long value = object.getLong(name);
        // If unsigned long value exceeds 2^63, return (upper << 32) + lower
        if (value < 0) {
            int upper = (int) (value >>> 32);
            int lower = (int) value;
            BigInteger u = BigInteger.valueOf(Integer.toUnsignedLong(upper));
            u = u.shiftLeft(32);
            BigInteger l = BigInteger.valueOf(Integer.toUnsignedLong(lower));
            return u.add(l);
        }
        return Long.valueOf(value);
    }

    // Somewhat expensive operation
    private ValueType determineValueType(ValueDescriptor v) {
        if (v.getAnnotation(Timespan.class) != null) {
            return ValueType.TIMESPAN;
        }
        if (v.getAnnotation(Timestamp.class) != null) {
            return ValueType.TIMESTAMP;
        }
        if (v.getAnnotation(Unsigned.class) != null) {
            return switch(v.getTypeName()) {
                case "byte", "short", "int", "long" -> ValueType.UNSIGNED;
                default -> ValueType.OTHER;
            };
        }
        return ValueType.OTHER;
    }
}
