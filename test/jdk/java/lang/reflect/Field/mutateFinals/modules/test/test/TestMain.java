/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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
package test;

import java.lang.reflect.Field;
import java.util.ServiceLoader;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import test.spi.Mutator;
import test.fieldholders.PublicFields;
import test.fieldholders.PrivateFields;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Test mutating final fields from different modules.
 */
class TestMain {
    static Map<Boolean, List<Mutator>> exportedToMutators;
    static Map<Boolean, List<Mutator>> openToMutators;

    // test module and the name of package with the fieldholder classes
    static Module testModule;
    static String fieldHoldersPackage;

    @BeforeAll
    static void setup() throws Exception {
        testModule = TestMain.class.getModule();
        fieldHoldersPackage = PublicFields.class.getPackageName();

        List<Mutator> allMutators = ServiceLoader.load(Mutator.class)
                .stream()
                .map(ServiceLoader.Provider::get)
                .toList();

        // mutators that test.fieldholders is exported to
        exportedToMutators = allMutators.stream()
                .collect(Collectors.partitioningBy(m -> testModule.isExported(fieldHoldersPackage,
                                                                              m.getClass().getModule()),
                        Collectors.toList()));

        // mutators that test.fieldholders is open to
        openToMutators = allMutators.stream()
                .collect(Collectors.partitioningBy(m -> testModule.isOpen(fieldHoldersPackage,
                                                                          m.getClass().getModule()),
                        Collectors.toList()));


        // exported to at least test, m1 and m2
        assertTrue(exportedToMutators.get(Boolean.TRUE).size() >= 3);

        // open to at least test and m1
        assertTrue(openToMutators.get(Boolean.TRUE).size() >= 2);
    }

    /**
     * Returns a stream of mutators that test.fieldholders is exported to.
     */
    static Stream<Mutator> exportedToMutators() {
        return exportedToMutators.get(Boolean.TRUE).stream();
    }

    /**
     * Returns a stream of mutators that test.fieldholders is open to.
     */
    static Stream<Mutator> openToMutators() {
        return openToMutators.get(Boolean.TRUE).stream();
    }

    /**
     * Returns a stream of mutators that test.fieldholders is not exported to.
     */
    static Stream<Mutator> notExportedToMutators() {
        List<Mutator> mutators = exportedToMutators.get(Boolean.FALSE);
        if (mutators.isEmpty()) {
            // can't return an empty stream at this time
            return Stream.of(Mutator.throwing());
        } else {
            return mutators.stream();
        }
    }

    /**
     * Returns a stream of mutators that test.fieldholders is not open to.
     */
    static Stream<Mutator> notOpenToMutators() {
        List<Mutator> mutators = openToMutators.get(Boolean.FALSE);
        if (mutators.isEmpty()) {
            // can't return an empty stream at this time
            return Stream.of(Mutator.throwing());
        } else {
            return mutators.stream();
        }
    }

    // public field, public class in package exported to mutator

    @ParameterizedTest()
    @MethodSource("exportedToMutators")
    void testFieldSetExportedPackage(Mutator mutator) throws Exception {
        Field f = PublicFields.objectField();
        var obj = new PublicFields();
        Object oldValue = obj.objectValue();
        Object newValue = new Object();

        f.setAccessible(false);
        assertThrows(IllegalAccessException.class, () -> mutator.set(f, obj, newValue));
        assertTrue(obj.objectValue() == oldValue);

        f.setAccessible(true);
        mutator.set(f, obj, newValue);
        assertTrue(obj.objectValue() == newValue);
    }

    @ParameterizedTest()
    @MethodSource("exportedToMutators")
    void testFieldSetBooleanExportedPackage(Mutator mutator) throws Exception {
        Field f = PublicFields.booleanField();
        var obj = new PublicFields();
        boolean oldValue = obj.booleanValue();
        boolean newValue = true;

        f.setAccessible(false);
        assertThrows(IllegalAccessException.class, () -> mutator.setBoolean(f, obj, newValue));
        assertTrue(obj.booleanValue() == oldValue);

        f.setAccessible(true);
        mutator.setBoolean(f, obj, newValue);
        assertTrue(obj.booleanValue() == newValue);
    }

    @ParameterizedTest()
    @MethodSource("exportedToMutators")
    void testFieldSetByteExportedPackage(Mutator mutator) throws Exception {
        Field f = PublicFields.byteField();
        var obj = new PublicFields();
        byte oldValue = obj.byteValue();
        byte newValue = 10;

        f.setAccessible(false);
        assertThrows(IllegalAccessException.class, () -> mutator.setByte(f, obj, newValue));
        assertTrue(obj.byteValue() == oldValue);

        f.setAccessible(true);
        mutator.setByte(f, obj, newValue);
        assertTrue(obj.byteValue() == newValue);
    }

    @ParameterizedTest()
    @MethodSource("exportedToMutators")
    void testFieldSetCharExportedPackage(Mutator mutator) throws Exception {
        Field f = PublicFields.charField();
        var obj = new PublicFields();
        char oldValue = obj.charValue();
        char newValue = 'Z';

        f.setAccessible(false);
        assertThrows(IllegalAccessException.class, () -> mutator.setChar(f, obj, newValue));
        assertTrue(obj.charValue() == oldValue);

        f.setAccessible(true);
        mutator.setChar(f, obj, newValue);
        assertTrue(obj.charValue() == newValue);
    }

    @ParameterizedTest()
    @MethodSource("exportedToMutators")
    void testFieldSetShortExportedPackage(Mutator mutator) throws Exception {
        Field f = PublicFields.shortField();
        var obj = new PublicFields();
        short oldValue = obj.shortValue();
        short newValue = 99;

        f.setAccessible(false);
        assertThrows(IllegalAccessException.class, () -> mutator.setShort(f, obj, newValue));
        assertTrue(obj.shortValue() == oldValue);

        f.setAccessible(true);
        mutator.setShort(f, obj, newValue);
        assertTrue(obj.shortValue() == newValue);
    }

    @ParameterizedTest()
    @MethodSource("exportedToMutators")
    void testFieldSetIntExportedPackage(Mutator mutator) throws Exception {
        Field f = PublicFields.intField();
        var obj = new PublicFields();
        int oldValue = obj.intValue();
        int newValue = 999;

        f.setAccessible(false);
        assertThrows(IllegalAccessException.class, () -> mutator.setInt(f, obj, newValue));
        assertTrue(obj.intValue() == oldValue);

        f.setAccessible(true);
        mutator.setInt(f, obj, newValue);
        assertTrue(obj.intValue() == newValue);
    }

    @ParameterizedTest()
    @MethodSource("exportedToMutators")
    void testFieldSetLongExportedPackage(Mutator mutator) throws Exception {
        Field f = PublicFields.longField();
        var obj = new PublicFields();
        long oldValue = obj.longValue();
        long newValue = 9999;

        f.setAccessible(false);
        assertThrows(IllegalAccessException.class, () -> mutator.setLong(f, obj, newValue));
        assertTrue(obj.longValue() == oldValue);

        f.setAccessible(true);
        mutator.setLong(f, obj, newValue);
        assertTrue(obj.longValue() == newValue);
    }

    @ParameterizedTest()
    @MethodSource("exportedToMutators")
    void testFieldSetFloatExportedPackage(Mutator mutator) throws Exception {
        Field f = PublicFields.floatField();
        var obj = new PublicFields();
        float oldValue = obj.floatValue();
        float newValue = 9.9f;

        f.setAccessible(false);
        assertThrows(IllegalAccessException.class, () -> mutator.setFloat(f, obj, newValue));
        assertTrue(obj.floatValue() == oldValue);

        f.setAccessible(true);
        mutator.setFloat(f, obj, newValue);
        assertTrue(obj.floatValue() == newValue);
    }

    @ParameterizedTest()
    @MethodSource("exportedToMutators")
    void testFieldSetDoublExportedPackage(Mutator mutator) throws Exception {
        Field f = PublicFields.doubleField();
        var obj = new PublicFields();
        double oldValue = obj.doubleValue();
        double newValue = 99.9d;

        f.setAccessible(false);
        assertThrows(IllegalAccessException.class, () -> mutator.setDouble(f, obj, newValue));
        assertTrue(obj.doubleValue() == oldValue);

        f.setAccessible(true);
        mutator.setDouble(f, obj, newValue);
        assertTrue(obj.doubleValue() == newValue);
    }

    @ParameterizedTest
    @MethodSource("exportedToMutators")
    void testUnreflectSetterExportedPackage(Mutator mutator) throws Throwable {
        Field f = PublicFields.objectField();
        var obj = new PublicFields();
        Object oldValue = obj.objectValue();
        Object newValue = new Object();

        f.setAccessible(false);
        assertThrows(IllegalAccessException.class, () -> mutator.unreflectSetter(f));

        f.setAccessible(true);
        mutator.unreflectSetter(f).invokeExact(obj, newValue);
        assertTrue(obj.objectValue() == newValue);
    }

    // private field, class in package opened to mutator

    @ParameterizedTest()
    @MethodSource("openToMutators")
    void testFieldSetOpenPackage(Mutator mutator) throws Exception {
        Field f = PrivateFields.objectField();
        var obj = new PrivateFields();
        Object oldValue = obj.objectValue();
        Object newValue = new Object();

        f.setAccessible(false);
        assertThrows(IllegalAccessException.class, () -> mutator.set(f, obj, newValue));
        assertTrue(obj.objectValue() == oldValue);

        f.setAccessible(true);
        mutator.set(f, obj, newValue);
        assertTrue(obj.objectValue() == newValue);
    }

    @ParameterizedTest()
    @MethodSource("openToMutators")
    void testFieldSetBooleanOpenPackage(Mutator mutator) throws Exception {
        Field f = PrivateFields.booleanField();
        var obj = new PrivateFields();
        boolean oldValue = obj.booleanValue();
        boolean newValue = true;

        f.setAccessible(false);
        assertThrows(IllegalAccessException.class, () -> mutator.setBoolean(f, obj, newValue));
        assertTrue(obj.booleanValue() == oldValue);

        f.setAccessible(true);
        mutator.setBoolean(f, obj, newValue);
        assertTrue(obj.booleanValue() == newValue);
    }

    @ParameterizedTest()
    @MethodSource("openToMutators")
    void testFieldSetByteOpenPackage(Mutator mutator) throws Exception {
        Field f = PrivateFields.byteField();
        var obj = new PrivateFields();
        byte oldValue = obj.byteValue();
        byte newValue = 10;

        f.setAccessible(false);
        assertThrows(IllegalAccessException.class, () -> mutator.setByte(f, obj, newValue));
        assertTrue(obj.byteValue() == oldValue);

        f.setAccessible(true);
        mutator.setByte(f, obj, newValue);
        assertTrue(obj.byteValue() == newValue);
    }

    @ParameterizedTest()
    @MethodSource("openToMutators")
    void testFieldSetCharOpenPackage(Mutator mutator) throws Exception {
        Field f = PrivateFields.charField();
        var obj = new PrivateFields();
        char oldValue = obj.charValue();
        char newValue = 'Z';

        f.setAccessible(false);
        assertThrows(IllegalAccessException.class, () -> mutator.setChar(f, obj, newValue));
        assertTrue(obj.charValue() == oldValue);

        f.setAccessible(true);
        mutator.setChar(f, obj, newValue);
        assertTrue(obj.charValue() == newValue);
    }

    @ParameterizedTest()
    @MethodSource("openToMutators")
    void testFieldSetShortOpenPackage(Mutator mutator) throws Exception {
        Field f = PrivateFields.shortField();
        var obj = new PrivateFields();
        short oldValue = obj.shortValue();
        short newValue = 'Z';

        f.setAccessible(false);
        assertThrows(IllegalAccessException.class, () -> mutator.setShort(f, obj, newValue));
        assertTrue(obj.shortValue() == oldValue);

        f.setAccessible(true);
        mutator.setShort(f, obj, newValue);
        assertTrue(obj.shortValue() == newValue);
    }

    @ParameterizedTest()
    @MethodSource("openToMutators")
    void testFieldSetIntOpenPackage(Mutator mutator) throws Exception {
        Field f = PrivateFields.intField();
        var obj = new PrivateFields();
        int oldValue = obj.intValue();
        int newValue = 99;

        f.setAccessible(false);
        assertThrows(IllegalAccessException.class, () -> mutator.setInt(f, obj, newValue));
        assertTrue(obj.intValue() == oldValue);

        f.setAccessible(true);
        mutator.setInt(f, obj, newValue);
        assertTrue(obj.intValue() == newValue);
    }

    @ParameterizedTest()
    @MethodSource("openToMutators")
    void testFieldSetLongOpenPackage(Mutator mutator) throws Exception {
        Field f = PrivateFields.longField();
        var obj = new PrivateFields();
        long oldValue = obj.longValue();
        long newValue = 999;

        f.setAccessible(false);
        assertThrows(IllegalAccessException.class, () -> mutator.setLong(f, obj, newValue));
        assertTrue(obj.longValue() == oldValue);

        f.setAccessible(true);
        mutator.setLong(f, obj, newValue);
        assertTrue(obj.longValue() == newValue);
    }

    @ParameterizedTest()
    @MethodSource("openToMutators")
    void testFieldSetFloatOpenPackage(Mutator mutator) throws Exception {
        Field f = PrivateFields.floatField();
        var obj = new PrivateFields();
        float oldValue = obj.floatValue();
        float newValue = 9.9f;

        f.setAccessible(false);
        assertThrows(IllegalAccessException.class, () -> mutator.setFloat(f, obj, newValue));
        assertTrue(obj.floatValue() == oldValue);

        f.setAccessible(true);
        mutator.setFloat(f, obj, newValue);
        assertTrue(obj.floatValue() == newValue);
    }

    @ParameterizedTest()
    @MethodSource("openToMutators")
    void testFieldSetDoubleOpenPackage(Mutator mutator) throws Exception {
        Field f = PrivateFields.doubleField();
        var obj = new PrivateFields();
        double oldValue = obj.doubleValue();
        double newValue = 99.9d;

        f.setAccessible(false);
        assertThrows(IllegalAccessException.class, () -> mutator.setDouble(f, obj, newValue));
        assertTrue(obj.doubleValue() == oldValue);

        f.setAccessible(true);
        mutator.setDouble(f, obj, newValue);
        assertTrue(obj.doubleValue() == newValue);
    }

    @ParameterizedTest
    @MethodSource("openToMutators")
    void testUnreflectSetterOpenPackage(Mutator mutator) throws Throwable {
        Field f = PrivateFields.objectField();
        var obj = new PrivateFields();
        Object oldValue = obj.objectValue();
        Object newValue = new Object();

        f.setAccessible(false);
        assertThrows(IllegalAccessException.class, () -> mutator.unreflectSetter(f));

        f.setAccessible(true);
        mutator.unreflectSetter(f).invokeExact(obj, newValue);
        assertTrue(obj.objectValue() == newValue);
    }

    // public field, public class in package not exported to mutator

    @ParameterizedTest
    @MethodSource("notExportedToMutators")
    void testFieldSetNotExportedPackage(Mutator mutator) throws Exception {
        Field f = PublicFields.objectField();
        var obj = new PublicFields();
        Object oldValue = obj.objectValue();
        Object newValue = new Object();

        f.setAccessible(true);
        var e1 = assertThrows(IllegalAccessException.class, () -> mutator.set(f, obj, newValue));
        Module mutatorModule = mutator.getClass().getModule();
        if (mutatorModule != testModule) {
            assertTrue(e1.getMessage().contains("module " + testModule.getName()
                    + " does not explicitly \"exports\" package "
                    + f.getDeclaringClass().getPackageName()
                    + " to module " + mutatorModule.getName()));
        }
        assertTrue(obj.objectValue() == oldValue);

        // export package to mutator module, should have no effect on set method
        testModule.addExports(fieldHoldersPackage, mutator.getClass().getModule());
        var e2 = assertThrows(IllegalAccessException.class, () -> mutator.set(f, obj, newValue));
        assertEquals(e1.getMessage(), e2.getMessage());
        assertTrue(obj.objectValue() == oldValue);

        // open package to mutator module, should have no effect on set method
        testModule.addOpens(fieldHoldersPackage, mutator.getClass().getModule());
        var e3 = assertThrows(IllegalAccessException.class, () -> mutator.set(f, obj, newValue));
        assertEquals(e1.getMessage(), e3.getMessage());
        assertTrue(obj.objectValue() == oldValue);
    }

    @ParameterizedTest
    @MethodSource("notExportedToMutators")
    void testUnreflectSetterNotExportedPackage(Mutator mutator) throws Exception {
        Field f = PublicFields.objectField();

        f.setAccessible(true);
        assertThrows(IllegalAccessException.class, () -> mutator.unreflectSetter(f));

        // export package to mutator module, should have no effect on unreflectSetter method
        testModule.addExports(fieldHoldersPackage, mutator.getClass().getModule());
        assertThrows(IllegalAccessException.class, () -> mutator.unreflectSetter(f));

        // open package to mutator module, should have no effect on unreflectSetter method
        testModule.addOpens(fieldHoldersPackage, mutator.getClass().getModule());
        assertThrows(IllegalAccessException.class, () -> mutator.unreflectSetter(f));
    }

    // private field, class in package not opened to mutator

    @ParameterizedTest
    @MethodSource("notOpenToMutators")
    void testFieldSetNotOpenPackage(Mutator mutator) throws Exception {
        Field f = PrivateFields.objectField();
        var obj = new PrivateFields();
        Object oldValue = obj.objectValue();
        Object newValue = new Object();

        f.setAccessible(true);
        var e1 = assertThrows(IllegalAccessException.class, () -> mutator.set(f, obj, newValue));
        Module mutatorModule = mutator.getClass().getModule();
        if (mutatorModule != testModule) {
            assertTrue(e1.getMessage().contains("module " + testModule.getName()
                    + " does not explicitly \"opens\" package "
                    + f.getDeclaringClass().getPackageName()
                    + " to module " + mutatorModule.getName()));
        }
        assertTrue(obj.objectValue() == oldValue);

        // open package to mutator module, should have no effect on set method
        testModule.addOpens(fieldHoldersPackage, mutator.getClass().getModule());
        var e2 = assertThrows(IllegalAccessException.class, () -> mutator.set(f, obj, newValue));
        assertEquals(e2.getMessage(), e2.getMessage());
        assertTrue(obj.objectValue() == oldValue);
    }

    @ParameterizedTest
    @MethodSource("notOpenToMutators")
    void testUnreflectSetterNotOpenPackage(Mutator mutator) throws Exception {
        Field f = PrivateFields.class.getDeclaredField("obj");

        f.setAccessible(true);
        assertThrows(IllegalAccessException.class, () -> mutator.unreflectSetter(f));

        // open package to mutator module, should have no effect on unreflectSetter method
        testModule.addOpens(fieldHoldersPackage, mutator.getClass().getModule());
        assertThrows(IllegalAccessException.class, () -> mutator.unreflectSetter(f));
    }
}
