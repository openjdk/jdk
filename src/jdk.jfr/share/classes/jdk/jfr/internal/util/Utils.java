/*
 * Copyright (c) 2016, 2023, Oracle and/or its affiliates. All rights reserved.
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

package jdk.jfr.internal.util;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.lang.annotation.Annotation;
import java.lang.annotation.Repeatable;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import jdk.internal.module.Checks;
import jdk.jfr.Event;
import jdk.jfr.EventType;
import jdk.jfr.RecordingState;
import jdk.jfr.internal.LogLevel;
import jdk.jfr.internal.LogTag;
import jdk.jfr.internal.Logger;
import jdk.jfr.internal.SecuritySupport;
import jdk.jfr.internal.Type;
import jdk.jfr.internal.settings.PeriodSetting;
import jdk.jfr.internal.settings.StackTraceSetting;
import jdk.jfr.internal.settings.ThresholdSetting;

public final class Utils {
    public static final String FIELD_DURATION = "duration";
    public static final String FIELD_STACK_TRACE = "stackTrace";
    public static final String FIELD_START_TIME = "startTime";
    public static final String FIELD_EVENT_THREAD = "eventThread";

    private static final Object flushObject = new Object();
    private static final String LEGACY_EVENT_NAME_PREFIX = "com.oracle.jdk.";

    /**
     * Return all annotations as they are visible in the source code
     *
     * @param clazz class to return annotations from
     *
     * @return list of annotation
     *
     */
    public static List<Annotation> getAnnotations(Class<?> clazz) {
        List<Annotation> annos = new ArrayList<>();
        for (Annotation a : clazz.getAnnotations()) {
            annos.addAll(getAnnotation(a));
        }
        return annos;
    }

    private static List<? extends Annotation> getAnnotation(Annotation a) {
        Class<?> annotated = a.annotationType();
        Method valueMethod = getValueMethod(annotated);
        if (valueMethod != null) {
            Class<?> returnType = valueMethod.getReturnType();
            if (returnType.isArray()) {
                Class<?> candidate = returnType.getComponentType();
                Repeatable r = candidate.getAnnotation(Repeatable.class);
                if (r != null) {
                    Class<?> repeatClass = r.value();
                    if (annotated == repeatClass) {
                        return getAnnotationValues(a, valueMethod);
                    }
                }
            }
        }
        return List.of(a);
    }

    public static boolean isAfter(RecordingState stateToTest, RecordingState b) {
        return stateToTest.ordinal() > b.ordinal();
    }

    public static boolean isBefore(RecordingState stateToTest, RecordingState b) {
        return stateToTest.ordinal() < b.ordinal();
    }

    public static boolean isState(RecordingState stateToTest, RecordingState... states) {
        for (RecordingState s : states) {
            if (s == stateToTest) {
                return true;
            }
        }
        return false;
    }

    private static List<Annotation> getAnnotationValues(Annotation a, Method valueMethod) {
        try {
            return Arrays.asList((Annotation[]) valueMethod.invoke(a, new Object[0]));
        } catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
            return new ArrayList<>();
        }
    }

    private static Method getValueMethod(Class<?> annotated) {
        try {
            return annotated.getMethod("value", new Class<?>[0]);
        } catch (NoSuchMethodException e) {
            return null;
        }
    }

    public static void touch(Path dumpFile) throws IOException {
        RandomAccessFile raf = new RandomAccessFile(dumpFile.toFile(), "rw");
        raf.close();
    }

    public static Class<?> unboxType(Class<?> t) {
        if (t == Integer.class) {
            return int.class;
        }
        if (t == Long.class) {
            return long.class;
        }
        if (t == Float.class) {
            return float.class;
        }
        if (t == Double.class) {
            return double.class;
        }
        if (t == Byte.class) {
            return byte.class;
        }
        if (t == Short.class) {
            return short.class;
        }
        if (t == Boolean.class) {
            return boolean.class;
        }
        if (t == Character.class) {
            return char.class;
        }
        return t;
    }

    public static Map<String, String> sanitizeNullFreeStringMap(Map<String, String> settings) {
        HashMap<String, String> map = HashMap.newHashMap(settings.size());
        for (Map.Entry<String, String> e : settings.entrySet()) {
            String key = e.getKey();
            if (key == null) {
                throw new NullPointerException("Null key is not allowed in map");
            }
            String value = e.getValue();
            if (value == null) {
                throw new NullPointerException("Null value is not allowed in map");
            }
            map.put(key, value);
        }
        return map;
    }

    public static <T> List<T> sanitizeNullFreeList(List<T> elements, Class<T> clazz) {
        List<T> sanitized = new ArrayList<>(elements.size());
        for (T element : elements) {
            if (element == null) {
                throw new NullPointerException("Null is not an allowed element in list");
            }
            if (element.getClass() != clazz) {
                throw new ClassCastException();
            }
            sanitized.add(element);
        }
        return sanitized;
    }

    public static List<Field> getVisibleEventFields(Class<?> clazz) {
        Utils.ensureValidEventSubclass(clazz);
        List<Field> fields = new ArrayList<>();
        for (Class<?> c = clazz; c != jdk.internal.event.Event.class; c = c.getSuperclass()) {
            for (Field field : c.getDeclaredFields()) {
                // skip private field in base classes
                if (c == clazz || !Modifier.isPrivate(field.getModifiers())) {
                    fields.add(field);
                }
            }
        }
        return fields;
    }

    public static void ensureValidEventSubclass(Class<?> eventClass) {
        if (jdk.internal.event.Event.class.isAssignableFrom(eventClass) && Modifier.isAbstract(eventClass.getModifiers())) {
            throw new IllegalArgumentException("Abstract event classes are not allowed");
        }
        if (eventClass == Event.class || eventClass == jdk.internal.event.Event.class || !jdk.internal.event.Event.class.isAssignableFrom(eventClass)) {
            throw new IllegalArgumentException("Must be a subclass to " + Event.class.getName());
        }
    }

    public static void ensureInitialized(Class<? extends jdk.internal.event.Event> eventClass) {
        SecuritySupport.ensureClassIsInitialized(eventClass);
    }

    public static Object makePrimitiveArray(String typeName, List<Object> values) {
        int length = values.size();
        switch (typeName) {
        case "int":
            int[] ints = new int[length];
            for (int i = 0; i < length; i++) {
                ints[i] = (int) values.get(i);
            }
            return ints;
        case "long":
            long[] longs = new long[length];
            for (int i = 0; i < length; i++) {
                longs[i] = (long) values.get(i);
            }
            return longs;

        case "float":
            float[] floats = new float[length];
            for (int i = 0; i < length; i++) {
                floats[i] = (float) values.get(i);
            }
            return floats;

        case "double":
            double[] doubles = new double[length];
            for (int i = 0; i < length; i++) {
                doubles[i] = (double) values.get(i);
            }
            return doubles;

        case "short":
            short[] shorts = new short[length];
            for (int i = 0; i < length; i++) {
                shorts[i] = (short) values.get(i);
            }
            return shorts;
        case "char":
            char[] chars = new char[length];
            for (int i = 0; i < length; i++) {
                chars[i] = (char) values.get(i);
            }
            return chars;
        case "byte":
            byte[] bytes = new byte[length];
            for (int i = 0; i < length; i++) {
                bytes[i] = (byte) values.get(i);
            }
            return bytes;
        case "boolean":
            boolean[] booleans = new boolean[length];
            for (int i = 0; i < length; i++) {
                booleans[i] = (boolean) values.get(i);
            }
            return booleans;
        case "java.lang.String":
            String[] strings = new String[length];
            for (int i = 0; i < length; i++) {
                strings[i] = (String) values.get(i);
            }
            return strings;
        }
        return null;
    }

    public static boolean isSettingVisible(long typeId, boolean hasEventHook) {
        if (ThresholdSetting.isType(typeId)) {
            return !hasEventHook;
        }
        if (PeriodSetting.isType(typeId)) {
            return hasEventHook;
        }
        if (StackTraceSetting.isType(typeId)) {
            return !hasEventHook;
        }
        return true;
    }

    public static Type getValidType(Class<?> type, String name) {
        Objects.requireNonNull(type, "Null is not a valid type for value descriptor " + name);
        if (type.isArray()) {
            type = type.getComponentType();
            if (type != String.class && !type.isPrimitive()) {
                throw new IllegalArgumentException("Only arrays of primitives and Strings are allowed");
            }
        }

        Type knownType = Type.getKnownType(type);
        if (knownType == null || knownType == Type.STACK_TRACE) {
            throw new IllegalArgumentException("Only primitive types, java.lang.Thread, java.lang.String and java.lang.Class are allowed for value descriptors. " + type.getName());
        }
        return knownType;
    }

    public static String upgradeLegacyJDKEvent(String eventName) {
        if (eventName.length() <= LEGACY_EVENT_NAME_PREFIX.length()) {
            return eventName;
        }
        if (eventName.startsWith(LEGACY_EVENT_NAME_PREFIX)) {
            int index = eventName.lastIndexOf(".");
            if (index == LEGACY_EVENT_NAME_PREFIX.length() - 1) {
                return Type.EVENT_NAME_PREFIX + eventName.substring(index + 1);
            }
        }
        return eventName;
    }

    public static void verifyMirror(Class<?> mirror, Class<?> real) {
        Class<?> cMirror = Objects.requireNonNull(mirror);
        Class<?> cReal = Objects.requireNonNull(real);

        Map<String, Field> mirrorFields = new HashMap<>();
        while (cMirror != null) {
            for (Field f : cMirror.getDeclaredFields()) {
                if (isSupportedType(f.getType())) {
                    mirrorFields.put(f.getName(), f);
                }
            }
            cMirror = cMirror.getSuperclass();
        }
        while (cReal != null) {
            for (Field realField : cReal.getDeclaredFields()) {
                if (isSupportedType(realField.getType())) {
                    String fieldName = realField.getName();
                    Field mirrorField = mirrorFields.get(fieldName);
                    if (mirrorField == null) {
                        throw new InternalError("Missing mirror field for " + cReal.getName() + "#" + fieldName);
                    }
                    if (realField.getType() != mirrorField.getType()) {
                        throw new InternalError("Incorrect type for mirror field " + fieldName);
                    }
                    if (realField.getModifiers() != mirrorField.getModifiers()) {
                        throw new InternalError("Incorrect modifier for mirror field " + fieldName);
                    }
                    mirrorFields.remove(fieldName);
                }
            }
            cReal = cReal.getSuperclass();
        }

        if (!mirrorFields.isEmpty()) {
            throw new InternalError("Found additional fields in mirror class " + mirrorFields.keySet());
        }
    }

    private static boolean isSupportedType(Class<?> type) {
        if (Modifier.isTransient(type.getModifiers()) || Modifier.isStatic(type.getModifiers())) {
            return false;
        }
        return Type.isValidJavaFieldType(type.getName());
    }

    public static void takeNap(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            // ok
        }
    }

    public static void notifyFlush() {
        synchronized (flushObject) {
            flushObject.notifyAll();
        }
    }

    public static void waitFlush(long timeOut) {
        synchronized (flushObject) {
            try {
                flushObject.wait(timeOut);
            } catch (InterruptedException e) {
                // OK
            }
        }
    }

    public static Instant epochNanosToInstant(long epochNanos) {
        return Instant.ofEpochSecond(0, epochNanos);
    }

    public static long timeToNanos(Instant timestamp) {
        return timestamp.getEpochSecond() * 1_000_000_000L + timestamp.getNano();
    }

    public static String validTypeName(String typeName, String defaultTypeName) {
        if (Checks.isClassName(typeName)) {
            return typeName;
        } else {
            Logger.log(LogTag.JFR, LogLevel.WARN, "@Name ignored, not a valid Java type name.");
            return defaultTypeName;
        }
    }

    public static String validJavaIdentifier(String identifier, String defaultIdentifier) {
        if (Checks.isJavaIdentifier(identifier)) {
            return identifier;
        } else {
            Logger.log(LogTag.JFR, LogLevel.WARN, "@Name ignored, not a valid Java identifier.");
            return defaultIdentifier;
        }
    }

    public static void ensureJavaIdentifier(String name) {
        if (!Checks.isJavaIdentifier(name)) {
            throw new IllegalArgumentException("'" + name + "' is not a valid Java identifier");
        }
    }

    public static String makeSimpleName(EventType type) {
      return makeSimpleName(type.getName());
    }

    public static String makeSimpleName(String qualified) {
        return qualified.substring(qualified.lastIndexOf(".") + 1);
    }
}
