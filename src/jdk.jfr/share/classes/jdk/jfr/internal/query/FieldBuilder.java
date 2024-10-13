/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;

import jdk.jfr.DataAmount;
import jdk.jfr.EventType;
import jdk.jfr.Frequency;
import jdk.jfr.MemoryAddress;
import jdk.jfr.Percentage;
import jdk.jfr.Timespan;
import jdk.jfr.Timestamp;
import jdk.jfr.ValueDescriptor;
import jdk.jfr.consumer.RecordedClass;
import jdk.jfr.consumer.RecordedClassLoader;
import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.consumer.RecordedFrame;
import jdk.jfr.consumer.RecordedStackTrace;

/**
 * This is a helper class to QueryResolver. It handles the creation of fields
 * and their default configuration.
 * <p>
 * The class applies heuristics to decide how values should be formatted,
 * and labeled.
 */
final class FieldBuilder {
    private static final Set<String> KNOWN_TYPES = createKnownTypes();
    private final List<EventType> eventTypes;
    private final ValueDescriptor descriptor;
    private final Field field;
    private final String fieldName;

    public FieldBuilder(List<EventType> eventTypes, FilteredType type, String fieldName) {
        this.eventTypes = eventTypes;
        this.descriptor = type.getField(fieldName);
        this.field = new Field(type, fieldName);
        this.fieldName = fieldName;
    }

    public List<Field> build() {
        if (configureSyntheticFields()) {
            field.fixedWidth = false;
            return List.of(field);
        }

        if (descriptor != null) {
            field.fixedWidth = !descriptor.getTypeName().equals("java.lang.String");
            field.dataType = descriptor.getTypeName();
            field.label = makeLabel(descriptor, hasDuration());
            field.alignLeft = true;
            field.valueGetter = valueGetter(field.name);

            configureNumericTypes();
            configureTime();
            configurePercentage();
            configureDataAmount();
            configureFrequency();
            configureMemoryAddress();
            configureKnownType();
            return List.of(field);
        }
        return List.of();
    }

    private static Function<RecordedEvent, Object> valueGetter(String name) {
        return event -> {
            try {
                return event.getValue(name);
            } catch (NullPointerException e) {
                // This can happen when accessing a nested structure
                // that is null, for example root.referrer
                return null;
            }
        };
    }

    private boolean hasDuration() {
        return field.type.getField("duration") != null;
    }

    private boolean configureSyntheticFields() {
        if (fieldName.equals("stackTrace.topApplicationFrame")) {
            configureTopApplicationFrameField();
            return true;
        }
        if (fieldName.equals("stackTrace.notInit")) {
            configureNotInitFrameField();
            return true;
        }
        if (fieldName.equals("stackTrace.topFrame.class")) {
            configureTopFrameClassField();
            return true;
        }
        if (fieldName.equals("stackTrace.topFrame")) {
            configureTopFrameField();
            return true;
        }
        if (fieldName.equals("id") && field.type.getName().equals("jdk.ActiveSetting")) {
            configureEventTypeIdField();
            return true;
        }
        if (fieldName.equals("eventType.label")) {
            configureEventType(e -> e.getEventType().getLabel());
            return true;
        }
        if (fieldName.equals("eventType.name")) {
            configureEventType(e -> e.getEventType().getName());
            return true;
        }
        return false;
    }

    private void configureEventTypeIdField() {
        Map<Long, String> eventTypes = createEventTypeLookup();
        field.alignLeft = true;
        field.label = "Event Type";
        field.dataType = String.class.getName();
        field.valueGetter = event -> eventTypes.get(event.getLong("id"));
        field.lexicalSort = true;
        field.integralType = false;
    }

    private Map<Long, String> createEventTypeLookup() {
        Map<Long, String> map = new HashMap<>();
        for (EventType eventType : eventTypes) {
            String label = eventType.getLabel();
            if (label == null) {
                label = eventType.getName();
            }
            map.put(eventType.getId(), label);
        }
        return map;
    }

    private void configureTopFrameField() {
        field.alignLeft = true;
        field.label = "Method";
        field.dataType = "jdk.types.Method";
        field.valueGetter = e -> {
            RecordedStackTrace t = e.getStackTrace();
            return t != null ? t.getFrames().getFirst() : null;
        };
        field.lexicalSort = true;
    }

    private void configureTopFrameClassField() {
        field.alignLeft = true;
        field.label = "Class";
        field.dataType = "java.lang.Class";
        field.valueGetter = e -> {
            RecordedStackTrace t = e.getStackTrace();
            if (t == null) {
                return null;
            }
            return t.getFrames().getFirst().getMethod().getType();
        };
        field.lexicalSort = true;
    }

    private void configureCustomFrame(Predicate<RecordedFrame> condition) {
        field.alignLeft = true;
        field.dataType = "jdk.types.Frame";
        field.label = "Method";
        field.lexicalSort = true;
        field.valueGetter = e -> {
            RecordedStackTrace t = e.getStackTrace();
            if (t != null) {
                for (RecordedFrame f : t.getFrames()) {
                    if (f.isJavaFrame()) {
                        if (condition.test(f)) {
                            return f;
                        }
                    }
                }
            }
            return null;
        };
    }

    private void configureNotInitFrameField() {
        configureCustomFrame(frame -> {
            return !frame.getMethod().getName().equals("<init>");
        });
    }

    private void configureTopApplicationFrameField() {
        configureCustomFrame(frame -> {
            RecordedClass cl = frame.getMethod().getType();
            RecordedClassLoader classLoader = cl.getClassLoader();
            return classLoader != null && !"bootstrap".equals(classLoader.getName());
        });
    }

    private void configureEventType(Function<RecordedEvent, Object> retriever) {
        field.alignLeft = true;
        field.dataType = String.class.getName();
        field.label = "Event Type";
        field.valueGetter = retriever;
        field.lexicalSort = true;
    }

    private static String makeLabel(ValueDescriptor v, boolean hasDuration) {
        String label = v.getLabel();
        if (label == null) {
            return v.getName();
        }
        String name = v.getName();
        if (name.equals("gcId")) {
            return "GC ID";
        }
        if (name.equals("compilerId")) {
            return "Compiler ID";
        }
        if (name.equals("startTime") && !hasDuration) {
                return "Time";
        }
        return label;
    }

    private void configureTime() {
        Timestamp timestamp = descriptor.getAnnotation(Timestamp.class);
        if (timestamp != null) {
            field.alignLeft = true;
            field.dataType = Instant.class.getName();
            field.timestamp = true;
            field.valueGetter = e -> e.getInstant(fieldName);
        }
        Timespan timespan = descriptor.getAnnotation(Timespan.class);
        if (timespan != null) {
            field.alignLeft = false;
            field.dataType = Duration.class.getName();
            field.timespan = true;
            field.valueGetter = e -> e.getDuration(fieldName);
        }
    }

    private void configureNumericTypes() {
        switch (descriptor.getTypeName()) {
        case "int", "long", "short", "byte":
            field.integralType = true;
            field.alignLeft = false;
            break;
        case "float", "double":
            field.fractionalType = true;
            field.alignLeft = false;
            break;
        case "boolean":
            field.alignLeft = false;
            break;
        }
    }

    private void configureKnownType() {
        String type = descriptor.getTypeName();
        if (KNOWN_TYPES.contains(type)) {
            field.lexicalSort = true;
            field.fixedWidth = false;
        }
    }

    private void configureMemoryAddress() {
        MemoryAddress memoryAddress = descriptor.getAnnotation(MemoryAddress.class);
        if (memoryAddress != null) {
            field.memoryAddress = true;
            field.alignLeft = true;
        }
    }

    private void configureFrequency() {
        if (descriptor.getAnnotation(Frequency.class) != null) {
            field.frequency = true;
        }
    }

    private void configureDataAmount() {
        DataAmount dataAmount = descriptor.getAnnotation(DataAmount.class);
        if (dataAmount != null) {
            if (DataAmount.BITS.equals(dataAmount.value())) {
                field.bits = true;
            }
            if (DataAmount.BYTES.equals(dataAmount.value())) {
                field.bytes = true;
            }
        }
    }

    private void configurePercentage() {
        Percentage percentage = descriptor.getAnnotation(Percentage.class);
        if (percentage != null) {
            field.percentage = true;
        }
    }

    // Fields created with "SELECT * FROM ..." queries
    public static List<Field> createWildcardFields(List<EventType> eventTypes, List<FilteredType> types) {
        List<Field> result = new ArrayList<>();
        for (FilteredType type : types) {
            result.addAll(createWildcardFields(eventTypes, type));
        }
        return result;
    }

    private static List<Field> createWildcardFields(List<EventType> eventTypes, FilteredType type) {
        record WildcardElement(String name, String label, ValueDescriptor field) {
        }

        var visited = new HashSet<ValueDescriptor>();
        var stack = new ArrayDeque<WildcardElement>();
        var wildcardElements = new ArrayList<WildcardElement>();

        for (ValueDescriptor field : type.getFields().reversed()) {
            stack.push(new WildcardElement(field.getName(), makeLabel(field, hasDuration(type)), field));
        }
        while (!stack.isEmpty()) {
            var we = stack.pop();
            if (!visited.contains(we.field)) {
                visited.add(we.field);
                var subFields = we.field().getFields().reversed();
                if (!subFields.isEmpty() && !KNOWN_TYPES.contains(we.field().getTypeName())) {
                    for (ValueDescriptor subField : subFields) {
                        String n = we.name + "." + subField.getName();
                        String l = we.label + " : " + makeLabel(subField, false);
                        if (stack.size() < 2) { // Limit depth to 2
                            stack.push(new WildcardElement(n, l, subField));
                        }
                    }
                } else {
                    wildcardElements.add(we);
                }
            }
        }
        List<Field> result = new ArrayList<>();
        for (WildcardElement we : wildcardElements) {
            FieldBuilder fb = new FieldBuilder(eventTypes, type, we.name());
            Field field = fb.build().getFirst();
            field.label = we.label;
            field.index = result.size();
            field.visible = true;
            field.sourceFields.add(field);
            result.add(field);
        }
        return result;
    }

    private static boolean hasDuration(FilteredType type) {
        return type.getField("duration") != null;
    }

    public static void configureAggregator(Field field) {
        Aggregator aggregator = field.aggregator;
        if (aggregator == Aggregator.COUNT || aggregator == Aggregator.UNIQUE) {
            field.integralType = true;
            field.timestamp = false;
            field.timespan = false;
            field.fractionalType = false;
            field.bytes = false;
            field.bits = false;
            field.frequency = false;
            field.memoryAddress = false;
            field.percentage = false;
            field.alignLeft = false;
            field.lexicalSort = false;
        }
        if (aggregator == Aggregator.LIST || aggregator == Aggregator.SET) {
            field.alignLeft = true;
            field.lexicalSort = true;
        }
        field.label = switch (aggregator) {
            case COUNT -> "Count";
            case AVERAGE -> "Avg. " + field.label;
            case FIRST, LAST, LAST_BATCH -> field.label;
            case MAXIMUM -> "Max. " + field.label;
            case MINIMUM -> "Min. " + field.label;
            case SUM -> "Total " + field.label;
            case UNIQUE -> "Unique Count " + field.label;
            case LIST -> field.label + "s";
            case SET -> field.label + "s";
            case MISSING -> field.label;
            case DIFFERENCE -> "Difference " + field.label;
            case MEDIAN -> "Median " + field.label;
            case P90 -> "P90 " + field.label;
            case P95 -> "P95 " + field.label;
            case P99 -> "P99 " + field.label;
            case P999 -> "P99.9 " + field.label;
            case STANDARD_DEVIATION -> "Std. Dev. " + field.label;
        };
    }

    private static Set<String> createKnownTypes() {
        Set<String> set = new HashSet<>();
        set.add(String.class.getName());
        set.add(Thread.class.getName());
        set.add(Class.class.getName());
        set.add("jdk.types.ThreadGroup");
        set.add("jdk.types.ClassLoader");
        set.add("jdk.types.Method");
        set.add("jdk.types.StackFrame");
        set.add("jdk.types.StackTrace");
        return set;
    }
}