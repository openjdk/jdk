/*
 * Copyright (c) 2019, 2026, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8235369 8235550 8247444 8320575
 * @summary reflection test for records
 * @build R10
 * @compile RecordReflectionTest.java
 * @run junit/othervm RecordReflectionTest
 */

import java.lang.annotation.*;
import java.lang.reflect.*;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

public class RecordReflectionTest {

    class NoRecord {}

    record R1() {}

    record R2(int i, int j) {}

    record R3(List<String> ls) {}

    record R4(R1 r1, R2 r2, R3 r3) {}

    record R5(String... args) {}

    record R6(long l, String... args) implements java.io.Serializable {}

    record R7(String s1, String s2, String... args) {}

    record R8<A, B>(A a, B b) implements java.io.Serializable { }

    record R9(List<String> ls) {
        R9 {} // compact constructor, will contain a mandated parameter
    }

    /* record R10 is defined in an accompaning jcod file, defined as:
    record R10(List<String> ls) { // in this case there wasn't be any compact constructor and thus no mandated param
    }
    */

    record R11(int i, List<String> ls) {
        R11 {} // compact constructor, will contain mandated parameters
    }

    record R12(List<String> ls, int i) {
        R12 {} // compact constructor, will contain mandated parameters
    }

    record R13(List<String> ls1, int i, List<String> ls2) {
        R13 {} // compact constructor, will contain mandated parameters
    }

    public static Object[][] recordClassData() {
        return List.of(R1.class,
                       R2.class,
                       R3.class,
                       R4.class,
                       R5.class,
                       R6.class,
                       R7.class,
                       R8.class,
                       R9.class,
                       R10.class,
                       R11.class,
                       R12.class,
                       R13.class
        ).stream().map(c -> new Object[] {c}).toArray(Object[][]::new);
    }

    @ParameterizedTest
    @MethodSource("recordClassData")
    public void testIsRecord(Class<?> cls) {
        String message = cls.toGenericString();
        assertTrue(cls.isRecord());
        assertSame(Record.class, cls.getSuperclass());
        assertNotNull(cls.getRecordComponents());
        assertTrue(message.contains("record"), message);
    }

    public static List<Class<?>> notRecordClasses() {
        return List.of(NoRecord.class,
                       NoRecord[].class,
                       Record.class,  // java.lang.Record is not itself a record class
                       Record[].class,
                       byte.class,
                       byte[].class,
                       int.class,
                       int[].class,
                       long.class,
                       long[].class);
    }

    @ParameterizedTest
    @MethodSource("notRecordClasses")
    public void testNotARecordClass(Class<?> cls) {
        assertFalse(cls.isRecord());
        assertNotSame(Record.class, cls.getSuperclass());
        assertNull(cls.getRecordComponents());
    }

    public static Object[][] reflectionData() {
        return new Object[][] {
            new Object[] { new R1(),
                           0,
                           null,
                           null,
                           null },
            new Object[] { new R2(1, 2),
                           2,
                           new Object[]{ 1, 2 },
                           new String[]{ "i", "j" },
                           new String[]{ "int", "int"} },
            new Object[] { new R3(List.of("1")),
                           1,
                           new Object[]{ List.of("1") },
                           new String[]{ "ls" },
                           new String[]{ "java.util.List<java.lang.String>"} },
            new Object[] { new R4(new R1(), new R2(6, 7), new R3(List.of("s"))),
                           3,
                           new Object[]{ new R1(), new R2(6, 7), new R3(List.of("s")) },
                           new String[]{ "r1", "r2", "r3" },
                           new String[]{ R1.class.toString(), R2.class.toString(), R3.class.toString()} },
            new Object[] { new R9(List.of("1")),
                        1,
                        new Object[]{ List.of("1") },
                        new String[]{ "ls" },
                        new String[]{ "java.util.List<java.lang.String>"} },
            /* R10 has exactly the same definition as R9 but the parameter of the compact constructor doesn't have
             * the mandated flag, nevertheless we should be able to load the same generic information
             */
            new Object[] { new R10(List.of("1")),
                        1,
                        new Object[]{ List.of("1") },
                        new String[]{ "ls" },
                        new String[]{ "java.util.List<java.lang.String>"} },
            new Object[] { new R11(1, List.of("1")),
                        2,
                        new Object[]{ 1, List.of("1") },
                        new String[]{ "i", "ls" },
                        new String[]{ "int", "java.util.List<java.lang.String>"} },
            new Object[] { new R12(List.of("1"), 1),
                        2,
                        new Object[]{ List.of("1"), 1 },
                        new String[]{ "ls", "i" },
                        new String[]{ "java.util.List<java.lang.String>", "int"} },
            new Object[] { new R13(List.of("1"), 1, List.of("2")),
                        3,
                        new Object[]{ List.of("1"), 1, List.of("2") },
                        new String[]{ "ls1", "i", "ls2" },
                        new String[]{ "java.util.List<java.lang.String>", "int", "java.util.List<java.lang.String>"} },
        };
    }

    @ParameterizedTest
    @MethodSource("reflectionData")
    public void testRecordReflection(Object recordOb,
                                     int numberOfComponents,
                                     Object[] values,
                                     String[] names,
                                     String[] signatures)
        throws ReflectiveOperationException
    {
        Class<?> recordClass = recordOb.getClass();
        assertTrue(recordClass.isRecord());
        RecordComponent[] recordComponents = recordClass.getRecordComponents();
        assertEquals(numberOfComponents, recordComponents.length);
        int i = 0;
        for (RecordComponent rc : recordComponents) {
            assertEquals(names[i], rc.getName());
            assertEquals(rc.getAccessor().getReturnType(), rc.getType());
            assertEquals(values[i], rc.getAccessor().invoke(recordOb));
            assertEquals(signatures[i], rc.getAccessor().getGenericReturnType().toString(),
                         String.format("signature of method \"%s\" different from expected signature \"%s\"",
                                 rc.getAccessor().getGenericReturnType(), signatures[i]));
            i++;
        }
        // now let's check constructors
        var constructor = recordClass.getDeclaredConstructors()[0];
        i = 0;
        for (var p: constructor.getParameters()) {
            assertEquals(signatures[i], p.getParameterizedType().toString(),
                    String.format("signature of method \"%s\" different from expected signature \"%s\"",
                            p.getType().toString(), signatures[i]));
            i++;
        }
        // similar as above but testing another API
        i = 0;
        for (var p : constructor.getGenericParameterTypes()) {
            assertEquals(signatures[i], p.toString(),
                    String.format("signature of method \"%s\" different from expected signature \"%s\"",
                            p.toString(), signatures[i]));
            i++;
        }
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target({ ElementType.RECORD_COMPONENT, ElementType.FIELD })
    @interface RCA {}

    record AnnotatedRec(@RCA int i) {}

    @Test
    public void testDeclAnnotationsInRecordComp() throws Throwable {
        Class<?> recordClass = AnnotatedRec.class;
        RecordComponent rc = recordClass.getRecordComponents()[0];
        Annotation[] annos = rc.getAnnotations();
        assertEquals(1, annos.length);
        assertEquals("@RecordReflectionTest.RCA()", annos[0].toString());

        Field f = recordClass.getDeclaredField("i");
        assertEquals(1, f.getAnnotations().length);
        assertEquals(annos[0].toString(), f.getAnnotations()[0].toString());
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.TYPE_USE})
    @interface TYPE_USE {}

    record TypeAnnotatedRec(@TYPE_USE int i) {}

    @Test
    public void testTypeAnnotationsInRecordComp() throws Throwable {
        Class<?> recordClass = TypeAnnotatedRec.class;
        RecordComponent rc = recordClass.getRecordComponents()[0];
        AnnotatedType at = rc.getAnnotatedType();
        Annotation[] annos = at.getAnnotations();
        assertEquals(1, annos.length);
        assertEquals("@RecordReflectionTest.TYPE_USE()", annos[0].toString());

        Field f = recordClass.getDeclaredField("i");
        assertEquals(1, f.getAnnotatedType().getAnnotations().length);
        assertEquals(annos[0].toString(), f.getAnnotatedType().getAnnotations()[0].toString());
    }

    @Test
    public void testReadOnlyFieldInRecord() throws Throwable {
        R2 o = new R2(1, 2);
        Class<?> recordClass = R2.class;
        String fieldName = "i";
        Field f = recordClass.getDeclaredField(fieldName);
        assertTrue(f.trySetAccessible());
        assertNotNull(f.get(o));
        assertThrows(IllegalAccessException.class, () -> f.set(o, null));
    }
}
