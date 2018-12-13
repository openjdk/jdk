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

package jdk.jfr.internal;

import static java.util.concurrent.TimeUnit.MICROSECONDS;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.NANOSECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;

import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.RandomAccessFile;
import java.lang.annotation.Annotation;
import java.lang.annotation.Repeatable;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import jdk.internal.org.objectweb.asm.ClassReader;
import jdk.internal.org.objectweb.asm.util.CheckClassAdapter;
import jdk.jfr.Event;
import jdk.jfr.FlightRecorderPermission;
import jdk.jfr.Recording;
import jdk.jfr.RecordingState;
import jdk.jfr.internal.handlers.EventHandler;
import jdk.jfr.internal.settings.PeriodSetting;
import jdk.jfr.internal.settings.StackTraceSetting;
import jdk.jfr.internal.settings.ThresholdSetting;

public final class Utils {

    private static final String INFINITY = "infinity";

    private static Boolean SAVE_GENERATED;

    public static final String EVENTS_PACKAGE_NAME = "jdk.jfr.events";
    public static final String INSTRUMENT_PACKAGE_NAME = "jdk.jfr.internal.instrument";
    public static final String HANDLERS_PACKAGE_NAME = "jdk.jfr.internal.handlers";
    public static final String REGISTER_EVENT = "registerEvent";
    public static final String ACCESS_FLIGHT_RECORDER = "accessFlightRecorder";

    private final static String LEGACY_EVENT_NAME_PREFIX = "com.oracle.jdk.";

    public static void checkAccessFlightRecorder() throws SecurityException {
        SecurityManager sm = System.getSecurityManager();
        if (sm != null) {
            sm.checkPermission(new FlightRecorderPermission(ACCESS_FLIGHT_RECORDER));
        }
    }

    public static void checkRegisterPermission() throws SecurityException {
        SecurityManager sm = System.getSecurityManager();
        if (sm != null) {
            sm.checkPermission(new FlightRecorderPermission(REGISTER_EVENT));
        }
    }

    private static enum TimespanUnit {
        NANOSECONDS("ns", 1000), MICROSECONDS("us", 1000), MILLISECONDS("ms", 1000), SECONDS("s", 60), MINUTES("m", 60), HOURS("h", 24), DAYS("d", 7);

        final String text;
        final long amount;

        TimespanUnit(String unit, long amount) {
            this.text = unit;
            this.amount = amount;
        }
    }

    // Tjis method can't handle Long.MIN_VALUE because absolute value is negative
    private static String formatDataAmount(String formatter, long amount) {
        int exp = (int) (Math.log(Math.abs(amount)) / Math.log(1024));
        char unitPrefix = "kMGTPE".charAt(exp - 1);
        return String.format(formatter, amount / Math.pow(1024, exp), unitPrefix);
    }

    public static String formatBytesCompact(long bytes) {
        if (bytes < 1024) {
            return String.valueOf(bytes);
        }
        return formatDataAmount("%.1f%cB", bytes);
    }

    public static String formatBits(long bits) {
        if (bits == 1 || bits == -1) {
            return bits + " bit";
        }
        if (bits < 1024 && bits > -1024) {
            return bits + " bits";
        }
        return formatDataAmount("%.1f %cbit", bits);
    }

    public static String formatBytes(long bytes) {
        if (bytes == 1 || bytes == -1) {
            return bytes + " byte";
        }
        if (bytes < 1024 && bytes > -1024) {
            return bytes + " bytes";
        }
        return formatDataAmount("%.1f %cB", bytes);
    }

    public static String formatBytesPerSecond(long bytes) {
        if (bytes < 1024 && bytes > -1024) {
            return bytes + " byte/s";
        }
        return formatDataAmount("%.1f %cB/s", bytes);
    }

    public static String formatBitsPerSecond(long bits) {
        if (bits < 1024 && bits > -1024) {
            return bits + " bps";
        }
        return formatDataAmount("%.1f %cbps", bits);
    }
    public static String formatTimespan(Duration dValue, String separation) {
        if (dValue == null) {
            return "0";
        }
        long value = dValue.toNanos();
        TimespanUnit result = TimespanUnit.NANOSECONDS;
        for (TimespanUnit unit : TimespanUnit.values()) {
            result = unit;
            long amount = unit.amount;
            if (result == TimespanUnit.DAYS || value < amount || value % amount != 0) {
                break;
            }
            value /= amount;
        }
        return String.format("%d%s%s", value, separation, result.text);
    }

    public static long parseTimespanWithInfinity(String s) {
        if (INFINITY.equals(s)) {
            return Long.MAX_VALUE;
        }
        return parseTimespan(s);
    }

    public static long parseTimespan(String s) {
        if (s.endsWith("ns")) {
            return Long.parseLong(s.substring(0, s.length() - 2).trim());
        }
        if (s.endsWith("us")) {
            return NANOSECONDS.convert(Long.parseLong(s.substring(0, s.length() - 2).trim()), MICROSECONDS);
        }
        if (s.endsWith("ms")) {
            return NANOSECONDS.convert(Long.parseLong(s.substring(0, s.length() - 2).trim()), MILLISECONDS);
        }
        if (s.endsWith("s")) {
            return NANOSECONDS.convert(Long.parseLong(s.substring(0, s.length() - 1).trim()), SECONDS);
        }
        if (s.endsWith("m")) {
            return 60 * NANOSECONDS.convert(Long.parseLong(s.substring(0, s.length() - 1).trim()), SECONDS);
        }
        if (s.endsWith("h")) {
            return 60 * 60 * NANOSECONDS.convert(Long.parseLong(s.substring(0, s.length() - 1).trim()), SECONDS);
        }
        if (s.endsWith("d")) {
            return 24 * 60 * 60 * NANOSECONDS.convert(Long.parseLong(s.substring(0, s.length() - 1).trim()), SECONDS);
        }

        try {
            Long.parseLong(s);
        } catch (NumberFormatException nfe) {
            throw new NumberFormatException("'" + s + "' is not a valid timespan. Shoule be numeric value followed by a unit, i.e. 20 ms. Valid units are ns, us, s, m, h and d.");
        }
        // Only accept values with units
        throw new NumberFormatException("Timespan + '" + s + "' is missing unit. Valid units are ns, us, s, m, h and d.");
    }

    /**
     * Return all annotations as they are visible in the source code
     *
     * @param clazz class to return annotations from
     *
     * @return list of annotation
     *
     */
    static List<Annotation> getAnnotations(Class<?> clazz) {
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
        List<Annotation> annos = new ArrayList<>();
        annos.add(a);
        return annos;
    }

    static boolean isAfter(RecordingState stateToTest, RecordingState b) {
        return stateToTest.ordinal() > b.ordinal();
    }

    static boolean isBefore(RecordingState stateToTest, RecordingState b) {
        return stateToTest.ordinal() < b.ordinal();
    }

    static boolean isState(RecordingState stateToTest, RecordingState... states) {
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

    static long nanosToTicks(long nanos) {
        return (long) (nanos * JVM.getJVM().getTimeConversionFactor());
    }

    static synchronized EventHandler getHandler(Class<? extends jdk.internal.event.Event> eventClass) {
        Utils.ensureValidEventSubclass(eventClass);
        try {
            Field f = eventClass.getDeclaredField(EventInstrumentation.FIELD_EVENT_HANDLER);
            SecuritySupport.setAccessible(f);
            return (EventHandler) f.get(null);
        } catch (NoSuchFieldException | IllegalArgumentException | IllegalAccessException e) {
            throw new InternalError("Could not access event handler");
        }
    }

    static synchronized void setHandler(Class<? extends jdk.internal.event.Event> eventClass, EventHandler handler) {
        Utils.ensureValidEventSubclass(eventClass);
        try {
            Field field = eventClass.getDeclaredField(EventInstrumentation.FIELD_EVENT_HANDLER);
            SecuritySupport.setAccessible(field);
            field.set(null, handler);
        } catch (NoSuchFieldException | IllegalArgumentException | IllegalAccessException e) {
            throw new InternalError("Could not access event handler");
        }
    }

    public static Map<String, String> sanitizeNullFreeStringMap(Map<String, String> settings) {
        HashMap<String, String> map = new HashMap<>(settings.size());
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

    static List<Field> getVisibleEventFields(Class<?> clazz) {
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

    public static void writeGeneratedASM(String className, byte[] bytes) {
        if (SAVE_GENERATED == null) {
            // We can't calculate value statically because it will force
            // initialization of SecuritySupport, which cause
            // UnsatisfiedLinkedError on JDK 8 or non-Oracle JDKs
            SAVE_GENERATED = SecuritySupport.getBooleanProperty("jfr.save.generated.asm");
        }
        if (SAVE_GENERATED) {
            try {
                try (FileOutputStream fos = new FileOutputStream(className + ".class")) {
                    fos.write(bytes);
                }

                try (FileWriter fw = new FileWriter(className + ".asm"); PrintWriter pw = new PrintWriter(fw)) {
                    ClassReader cr = new ClassReader(bytes);
                    CheckClassAdapter.verify(cr, true, pw);
                }
                Logger.log(LogTag.JFR_SYSTEM_BYTECODE, LogLevel.INFO, "Instrumented code saved to " + className + ".class and .asm");
            } catch (IOException e) {
                Logger.log(LogTag.JFR_SYSTEM_BYTECODE, LogLevel.INFO, "Could not save instrumented code, for " + className + ".class and .asm");
            }
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

    public static boolean isSettingVisible(Control c, boolean hasEventHook) {
        if (c instanceof ThresholdSetting) {
            return !hasEventHook;
        }
        if (c instanceof PeriodSetting) {
            return hasEventHook;
        }
        if (c instanceof StackTraceSetting) {
            return !hasEventHook;
        }
        return true;
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

    public static <T> List<T> smallUnmodifiable(List<T> list) {
        if (list.isEmpty()) {
            return Collections.emptyList();
        }
        if (list.size() == 1) {
            return Collections.singletonList(list.get(0));
        }
        return Collections.unmodifiableList(list);
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

        while (cReal != null) {
            Map<String, Field> mirrorFields = new HashMap<>();
            if (cMirror != null) {
                for (Field f : cMirror.getDeclaredFields()) {
                    if (isSupportedType(f.getType())) {
                        mirrorFields.put(f.getName(), f);
                    }
                }
            }
            for (Field realField : cReal.getDeclaredFields()) {
                if (isSupportedType(realField.getType())) {
                    String fieldName = realField.getName();
                    Field mirrorField = mirrorFields.get(fieldName);
                    if (mirrorField == null) {
                        throw new InternalError("Missing mirror field for " + cReal.getName() + "#" + fieldName);
                    }
                    if (realField.getModifiers() != mirrorField.getModifiers()) {
                        throw new InternalError("Incorrect modifier for mirror field "+ cMirror.getName() + "#" + fieldName);
                    }
                    mirrorFields.remove(fieldName);
                }
            }
            if (!mirrorFields.isEmpty()) {
                throw new InternalError(
                        "Found additional fields in mirror class " + cMirror.getName() + " " + mirrorFields.keySet());
            }
            if (cMirror != null) {
                cMirror = cMirror.getSuperclass();
            }
            cReal = cReal.getSuperclass();
        }
    }

    private static boolean isSupportedType(Class<?> type) {
        if (Modifier.isTransient(type.getModifiers()) || Modifier.isStatic(type.getModifiers())) {
            return false;
        }
        return Type.isValidJavaFieldType(type.getName());
    }

    public static String makeFilename(Recording recording) {
        String pid = JVM.getJVM().getPid();
        String date = Repository.REPO_DATE_FORMAT.format(LocalDateTime.now());
        String idText = recording == null ? "" :  "-id-" + Long.toString(recording.getId());
        return "hotspot-" + "pid-" + pid + idText + "-" + date + ".jfr";
    }
}
