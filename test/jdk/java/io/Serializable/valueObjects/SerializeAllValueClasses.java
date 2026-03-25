/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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

/*
 * @test
 * @modules java.base/jdk.internal java.base/jdk.internal.misc
 * @run junit/othervm --enable-preview SerializeAllValueClasses
 * @run junit/othervm SerializeAllValueClasses
 */

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.Month;
import java.time.ZonedDateTime;
import java.time.chrono.HijrahDate;
import java.time.chrono.JapaneseDate;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAccessor;
import java.time.temporal.TemporalUnit;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import jdk.internal.misc.PreviewFeatures;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Scans all classes in the JDK for those recognized as value classes
 * or with the annotation jdk.internal.misc.ValueBasedClass.
 *
 * Scanning is done over the jrt: filesystem. Classes are matched using the
 * following criteria:
 *
 *  - serializable
 *  - is a public or protected class
 *  - has public or protected constructor
 *
 * This returns a list of class', which is convenient for the caller.
 */

public class SerializeAllValueClasses {
    // Cache of instances of known classes suitable as arguments to constructors
    // or factory methods.
    private static final Map<Class<?>, Object> argumentForType = initInstances();

    private static Map<Class<?>, Object> initInstances() {
        Map<Class<?>, Object> map = new HashMap<>();
        map.put(Integer.class, 12); map.put(int.class, 12);
        map.put(Short.class, (short)3); map.put(short.class, (short)3);
        map.put(Byte.class, (byte)4); map.put(byte.class, (byte)4);
        map.put(Long.class, 5L); map.put(long.class, 5L);
        map.put(Character.class, 'C'); map.put(char.class, 'C');
        map.put(Float.class, 1.0f); map.put(float.class, 1.0f);
        map.put(Double.class, 2.0d); map.put(double.class, 2.0d);
        map.put(Duration.class, Duration.ofHours(1));
        map.put(TemporalUnit.class, ChronoUnit.SECONDS);
        map.put(LocalTime.class, LocalTime.of(12, 1));
        map.put(LocalDate.class, LocalDate.of(2024, 1, 1));
        map.put(LocalDateTime.class, LocalDateTime.of(2024, 2, 1, 12, 2));
        map.put(TemporalAccessor.class, ZonedDateTime.now());
        map.put(ZonedDateTime.class, ZonedDateTime.now());
        map.put(Clock.class, Clock.systemUTC());
        map.put(Month.class, Month.JANUARY);
        map.put(Instant.class, Instant.now());
        map.put(JapaneseDate.class, JapaneseDate.now());
        map.put(HijrahDate.class, HijrahDate.now());
        return map;
    }


    // Stream the value classes to the test
    private static Stream<Arguments> classProvider() throws IOException, URISyntaxException {
        return findAll().stream().map(c -> Arguments.of(c));
    }

    @Test
    void info() {
        var info = (PreviewFeatures.isEnabled()) ? "  Checking preview classes declared as `value class`" :
            "  Checking identity classes with annotation `jdk.internal.ValueBased.class`";
        System.err.println(info);
    }

    @ParameterizedTest
    @MethodSource("classProvider")
    void testValueClass(Class<?> clazz) {
        boolean atLeastOne = false;

        Object expected = argumentForType.get(clazz);
        if (expected != null) {
            serializeDeserialize(expected);
            atLeastOne = true;
        }
        var cons = clazz.getConstructors();
        for (Constructor<?> c : cons) {
            Object[] args = makeArgs(c.getParameterTypes(), clazz);
            if (args != null) {
                try {
                    expected = c.newInstance(args);
                    serializeDeserialize(expected);
                    atLeastOne = true;
                    break;  // one is enough
                } catch (InvocationTargetException | InstantiationException |
                         IllegalAccessException e) {
                    // Ignore
                    System.err.printf("""
                                      Ignoring constructor: %s
                                        Generated arguments are invalid: %s
                                        %s
                                    """,
                            c, Arrays.toString(args), e.getCause());
                }
            }
        }

        // Scan for suitable factory methods
        for (Method m : clazz.getMethods()) {
            if (Modifier.isStatic(m.getModifiers()) &&
                m.getReturnType().equals(clazz)) {
                // static method returning itself
                Object[] args = makeArgs(m.getParameterTypes(), clazz);
                if (args != null) {
                    try {
                        expected = m.invoke(null, args);
                        serializeDeserialize(expected);
                        atLeastOne = true;
                        break;  // one is enough
                    } catch (IllegalAccessException | InvocationTargetException e) {
                        // Ignore
                        System.err.printf("""
                                          Ignoring factory: %s
                                            Generated arguments are invalid: %s
                                            %s
                                        """,
                                m, Arrays.toString(args), e.getCause());
                    }
                }
            }
        }
        assertTrue(atLeastOne, "No constructor or factory found for " + clazz);
    }

    /**
     * {@return an array of instances matching the parameter types, or null}
     *
     * @param paramTypes an array of parameter types
     * @param forClazz the owner class for which the parameters are being generated
     */
    private Object[] makeArgs(Class<?>[] paramTypes, Class<?> forClazz) {
        Object[] args = Arrays.stream(paramTypes)
                .map(t -> makeArg(t, forClazz))
                .toArray();
        for (Object arg : args) {
            if (arg == null)
                return null;
        }
        return args;
    }

    /**
     * {@return an instance of the class, or null if not available}
     * String values are customized by the requesting owner.
     * For example, "true" is returned as a value when requested for "Boolean".
     * @param paramType the parameter type
     * @param forClass the owner class
     */
    private static Object makeArg(Class<?> paramType, Class<?> forClass) {
        return (paramType == String.class || paramType == CharSequence.class)
                ? makeStringArg(forClass)
                : argumentForType.get(paramType);
    }

    /**
     * {@return a string representation of an instance of class, or null}
     * Mostly special cased for core value classes.
     * @param forClass a Class
     */
    private static String makeStringArg(Class<?> forClass) {
        if (forClass == Integer.class || forClass == int.class ||
                forClass == Byte.class || forClass == byte.class ||
                forClass == Short.class || forClass == short.class ||
                forClass == Long.class || forClass == long.class) {
            return "0";
        } else if (forClass == Boolean.class || forClass == boolean.class) {
            return "true";
        } else if (forClass == Float.class || forClass == float.class ||
                forClass == Double.class || forClass == double.class) {
            return "1.0";
        } else if (forClass == Duration.class) {
            return "PT4H";
        } else if (forClass == LocalDate.class) {
            return LocalDate.of(2024, 1, 1).toString();
        } else if (forClass == LocalDateTime.class) {
            return LocalDateTime.of(2024, 1, 1, 12, 1).toString();
        } else if (forClass == LocalTime.class) {
            return LocalTime.of(12, 1).toString();
        } else if (forClass == Instant.class) {
            return Instant.ofEpochSecond(5_000_000, 1000).toString();
        } else {
            return null;
        }
    }

    static final ClassLoader LOADER = SerializeAllValueClasses.class.getClassLoader();

    private static Optional<Class<?>> findClass(String name) {
        try {
            Class<?> clazz = Class.forName(name, false, LOADER);
            return Optional.of(clazz);
        } catch (ClassNotFoundException | ExceptionInInitializerError |
                 NoClassDefFoundError | IllegalAccessError ex) {
            return Optional.empty();
        }
    }

    private static boolean isClass(Class<?> clazz) {
        return !(clazz.isEnum() || clazz.isInterface());
    }

    private static boolean isNonAbstract(Class<?> clazz) {
        return (clazz.getModifiers() & Modifier.ABSTRACT) == 0;
    }

    private static boolean isPublicOrProtected(Class<?> clazz) {
        return (clazz.getModifiers() & (Modifier.PUBLIC | Modifier.PROTECTED)) != 0;
    }

    @SuppressWarnings("preview")
    private static boolean isValueClass(Class<?> clazz) {
        if (PreviewFeatures.isEnabled())
            return clazz.isValue();
        var a = clazz.getAnnotation(jdk.internal.ValueBased.class);
        return a != null;
    }

    /**
     * Scans classes in the JDK and returns matching classes.
     *
     * @return list of matching class
     * @throws IOException if an unexpected exception occurs
     * @throws URISyntaxException if an unexpected exception occurs
     */
    public static List<Class<?>> findAll() throws IOException, URISyntaxException {
        FileSystem fs = FileSystems.getFileSystem(new URI("jrt:/"));
        Path dir = fs.getPath("/modules");
        try (final Stream<Path> paths = Files.walk(dir)) {
            // each path is in the form: /modules/<modname>/<pkg>/<pkg>/.../name.class
            return paths.filter((path) -> path.getNameCount() > 2)
                    .map((path) -> path.subpath(2, path.getNameCount()))
                    .map(Path::toString)
                    .filter((name) -> name.endsWith(".class"))
                    .map((name) -> name.replaceFirst("\\.class$", ""))
                    .filter((name) -> !name.equals("module-info"))
                    .map((name) -> name.replaceAll("/", "."))
                    .flatMap((java.lang.String name) -> findClass(name).stream())
                    .filter(Serializable.class::isAssignableFrom)
                    .filter(SerializeAllValueClasses::isClass)
                    .filter(SerializeAllValueClasses::isNonAbstract)
                    .filter((klass) -> !klass.isSealed())
                    .filter(SerializeAllValueClasses::isValueClass)
                    .filter(SerializeAllValueClasses::isPublicOrProtected)
                    .collect(Collectors.toList());
        }
    }

    private void serializeDeserialize(Object expected) {
        try {
            Object actual = deserialize(serialize(expected));
            assertEquals(expected, actual, "round trip compare fail");
        } catch (IOException  | ClassNotFoundException e) {
            fail("serialize/Deserialize", e);
        }
    }

    /**
     * Serialize an object into byte array.
     */
    private static byte[] serialize(Object obj) throws IOException {
        ByteArrayOutputStream bs = new ByteArrayOutputStream();
        try (ObjectOutputStream out = new ObjectOutputStream(bs)) {
            out.writeObject(obj);
        }
        return bs.toByteArray();
    }

    /**
     * Deserialize an object from byte array using the requested classloader.
     */
    private static Object deserialize(byte[] ba) throws IOException, ClassNotFoundException {
        try (ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(ba))) {
            return in.readObject();
        }
    }

}
