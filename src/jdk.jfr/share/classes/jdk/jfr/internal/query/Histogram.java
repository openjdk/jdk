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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import jdk.jfr.consumer.RecordedClass;
import jdk.jfr.consumer.RecordedClassLoader;
import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.consumer.RecordedFrame;
import jdk.jfr.consumer.RecordedMethod;
import jdk.jfr.consumer.RecordedObject;
import jdk.jfr.consumer.RecordedStackTrace;
import jdk.jfr.consumer.RecordedThread;
import jdk.jfr.consumer.RecordedThreadGroup;
import jdk.jfr.internal.query.Function.LastBatch;

/**
 * Class responsible for aggregating values
 */
final class Histogram {
    private static final class LookupKey {
        private Object keys;

        @SuppressWarnings({ "unchecked", "rawtypes" })
        public void add(Object o) {
            // One key, fast path
            if (keys == null) {
                keys = o;
                return;
            }
            // Three or more keys
            if (keys instanceof Set set) {
                set.add(o);
                return;
            }
            // Two keys
            Set<Object> set = HashSet.newHashSet(2);
            set.add(keys);
            set.add(o);
            keys = set;
        }

        @Override
        public int hashCode() {
            return Objects.hashCode(keys);
        }

        @Override
        public boolean equals(Object object) {
            if (object instanceof LookupKey that) {
                return Objects.deepEquals(that.keys, this.keys);
            }
            return false;
        }
    }

    private static final class MethodEquality {
        private final String methodName;
        private final String descriptor;
        private final long classId;

        public MethodEquality(RecordedMethod method) {
            methodName = method.getName();
            descriptor = method.getDescriptor();
            classId = method.getType().getId();
        }

        @Override
        public int hashCode() {
            int hash1 = Long.hashCode(classId);
            int hash2 = methodName.hashCode();
            int hash3 = descriptor.hashCode();
            int result = 31 + hash1;
            result += 31 * result + hash2;
            result += 31 * result + hash3;
            return result;
        }

        @Override
        public boolean equals(Object object) {
            if (object instanceof MethodEquality that) {
                if ((this.classId != that.classId) || !Objects.equals(this.methodName, that.methodName)) {
                    return false;
                }
                return Objects.equals(this.descriptor, that.descriptor);
            }
            return false;
        }
    }

    private final Map<LookupKey, Function[]> keyFunctionsMap = new HashMap<>();
    private final List<Field> fields = new ArrayList<>();

    public void addFields(List<Field> fields) {
        this.fields.addAll(fields);
    }

    public void add(RecordedEvent e, FilteredType type, List<Field> sourceFields) {
        LookupKey lk = new LookupKey();
        final Object[] values = new Object[sourceFields.size()];
        for (int i = 0; i < values.length; i++) {
            Field field = sourceFields.get(i);
            Object value = field.valueGetter.apply(e);
            values[i] = value;
            if (field.grouper != null) {
                lk.add(makeKey(value));
            }
        }

        Function[] fs = keyFunctionsMap.computeIfAbsent(lk, k -> createFunctions());
        for (int i = 0; i < values.length; i++) {
            Function function = fs[sourceFields.get(i).index];
            function.add(values[i]);
            if (function instanceof LastBatch l) {
                l.setTime(e.getEndTime());
            }
        }
    }

    public List<Row> toRows() {
        List<Row> rows = new ArrayList<>(keyFunctionsMap.size());
        for (Function[] functions : keyFunctionsMap.values()) {
            Row row = new Row(fields.size());
            boolean valid = true;
            int index = 0;
            for (Function f : functions) {
                if (f instanceof LastBatch last && !last.valid()) {
                    valid = false;
                }
                row.putValue(index++, f.result());
            }
            if (valid) {
                rows.add(row);
            }
        }
        return rows;
    }

    private Function[] createFunctions() {
        Function[] functions = new Function[fields.size()];
        for (int i = 0; i < functions.length; i++) {
            functions[i] = Function.create(fields.get(i));
        }
        return functions;
    }

    private static Object makeKey(Object object) {
        if (!(object instanceof RecordedObject)) {
            return object;
        }
        if (object instanceof RecordedMethod method) {
            return new MethodEquality(method);
        }
        if (object instanceof RecordedThread thread) {
            return thread.getId();
        }
        if (object instanceof RecordedClass clazz) {
            return clazz.getId();
        }
        if (object instanceof RecordedFrame frame) {
            if (frame.isJavaFrame()) {
                return makeKey(frame.getMethod());
            }
            return null;
        }

        if (object instanceof RecordedStackTrace stackTrace) {
            List<RecordedFrame> recordedFrames = stackTrace.getFrames();
            List<Object> frames = new ArrayList<>(recordedFrames.size());
            for (RecordedFrame frame : recordedFrames) {
                frames.add(makeKey(frame));
            }
            return frames;
        }
        if (object instanceof RecordedClassLoader classLoader) {
            return classLoader.getId();
        }
        if (object instanceof RecordedThreadGroup group) {
            String name = group.getName();
            String parentName = group.getParent() != null ? group.getParent().getName() : null;
            return name + ":" + parentName;
        }
        return object;
    }
}
