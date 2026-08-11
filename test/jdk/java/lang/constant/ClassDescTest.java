/*
 * Copyright (c) 2018, 2026, Oracle and/or its affiliates. All rights reserved.
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

import java.lang.invoke.MethodHandles;
import java.lang.constant.ClassDesc;
import java.lang.constant.ConstantDescs;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/*
 * @test
 * @bug 8215510 8283075 8338544
 * @compile ClassDescTest.java
 * @run junit ClassDescTest
 * @summary unit tests for java.lang.constant.ClassDesc
 */
public class ClassDescTest extends SymbolicDescTest {

    private void testClassDesc(ClassDesc r) throws ReflectiveOperationException {
        testSymbolicDesc(r);

        // Test descriptor accessor, factory, equals
        assertEquals(r, ClassDesc.ofDescriptor(r.descriptorString()));

        if (!r.descriptorString().equals("V")) {
            assertEquals(r, r.arrayType().componentType());
            // Commutativity: array -> resolve -> componentType -> toSymbolic
            assertEquals(r, r.arrayType().resolveConstantDesc(LOOKUP).getComponentType().describeConstable().orElseThrow());
            // Commutativity: resolve -> array -> toSymbolic -> component type
            assertEquals(r, Array.newInstance(r.resolveConstantDesc(LOOKUP), 0).getClass().describeConstable().orElseThrow().componentType());
        }

        if (r.isArray()) {
            assertEquals(r, r.componentType().arrayType());
            assertEquals(r, r.resolveConstantDesc(LOOKUP).getComponentType().describeConstable().orElseThrow().arrayType());
            assertEquals(r, Array.newInstance(r.componentType().resolveConstantDesc(LOOKUP), 0).getClass().describeConstable().orElseThrow());
        } else {
            assertNull(r.componentType());
        }

        if (!r.isClassOrInterface()) {
            assertEquals("", r.packageName());
        }
    }

    private static String classDisplayName(Class<?> c) {
        int arrayLevel = 0;
        while (c.isArray()) {
            arrayLevel++;
            c = c.componentType();
        }
        String name = c.getName();
        String simpleClassName;
        if (c.isPrimitive()) {
            simpleClassName = name;
        } else {
            int lastDot = name.lastIndexOf('.');
            simpleClassName = lastDot == -1 ? name : name.substring(lastDot + 1);
        }
        return simpleClassName + "[]".repeat(arrayLevel);
    }

    private void testClassDesc(ClassDesc r, Class<?> c) throws ReflectiveOperationException {
        testClassDesc(r);

        assertEquals(c, r.resolveConstantDesc(LOOKUP));
        assertEquals(r, c.describeConstable().orElseThrow());
        assertEquals(r, ClassDesc.ofDescriptor(c.descriptorString()));
        if (r.isArray()) {
            testClassDesc(r.componentType(), c.componentType());
        }
        if (r.isClassOrInterface()) {
            assertEquals(c.getPackageName(), r.packageName());
        }
        assertEquals(classDisplayName(c), r.displayName());
    }

    @Test
    public void testSymbolicDescsConstants() throws ReflectiveOperationException {
        int tested = 0;
        Field[] fields = ConstantDescs.class.getDeclaredFields();
        for (Field f : fields) {
            try {
                if (f.getType().equals(ClassDesc.class)
                    && ((f.getModifiers() & Modifier.STATIC) != 0)
                    && ((f.getModifiers() & Modifier.PUBLIC) != 0)) {
                    ClassDesc cr = (ClassDesc) f.get(null);
                    Class<?> c = cr.resolveConstantDesc(MethodHandles.lookup());
                    testClassDesc(cr, c);
                    ++tested;
                }
            }
            catch (Throwable e) {
                System.out.println(e.getMessage());
                fail("Error testing field " + f.getName(), e);
            }
        }

        assertTrue(tested > 0);
    }

    @Test
    public void testPrimitiveClassDesc() throws ReflectiveOperationException {
        for (Primitives p : Primitives.values()) {
            List<ClassDesc> descs = List.of(ClassDesc.ofDescriptor(p.descriptor),
                                           p.classDesc,
                                           p.clazz.describeConstable().orElseThrow());
            for (ClassDesc c : descs) {
                testClassDesc(c, p.clazz);
                assertTrue(c.isPrimitive());
                assertEquals(c.descriptorString(), p.descriptor);
                assertEquals(c.displayName(), p.name);
                descs.forEach(cc -> assertEquals(cc, c));
                if (p != Primitives.VOID) {
                    testClassDesc(c.arrayType(), p.arrayClass);
                    assertEquals(p.arrayClass.describeConstable().orElseThrow().componentType(), c);
                    assertEquals(p.classDesc.arrayType().componentType(), c);
                }
            }

            for (Primitives other : Primitives.values()) {
                ClassDesc otherDescr = ClassDesc.ofDescriptor(other.descriptor);
                if (p != other)
                    descs.forEach(c -> assertNotEquals(otherDescr, c));
                else
                    descs.forEach(c -> assertEquals(otherDescr, c));
            }
        }
    }

    @Test
    public void testSimpleClassDesc() throws ReflectiveOperationException {

        List<ClassDesc> stringClassDescs = Arrays.asList(ClassDesc.ofDescriptor("Ljava/lang/String;"),
                                                        ClassDesc.ofInternalName("java/lang/String"),
                                                        ClassDesc.of("java.lang", "String"),
                                                        ClassDesc.of("java.lang.String"),
                                                        ClassDesc.of("java.lang.String").arrayType().componentType(),
                                                        String.class.describeConstable().orElseThrow());
        for (ClassDesc r : stringClassDescs) {
            testClassDesc(r, String.class);
            assertFalse(r.isPrimitive());
            assertEquals("Ljava/lang/String;", r.descriptorString());
            assertEquals("String", r.displayName());
            testClassDesc(r.arrayType(), String[].class);
            testClassDesc(r.arrayType(3), String[][][].class);
            stringClassDescs.forEach(rr -> assertEquals(rr, r));
        }

        testClassDesc(ClassDesc.of("java.lang.String").arrayType(), String[].class);
        testClassDesc(ClassDesc.of("java.util.Map").nested("Entry"), Map.Entry.class);

        assertEquals(ClassDesc.ofDescriptor("Ljava/lang/String;"), ClassDesc.of("java.lang.String"));
        assertEquals(ClassDesc.ofInternalName("java/lang/String"), ClassDesc.of("java.lang.String"));

        ClassDesc thisClassDesc = ClassDesc.ofDescriptor("LClassDescTest;");
        assertEquals(ClassDesc.of("", "ClassDescTest"), thisClassDesc);
        assertEquals(ClassDesc.of("ClassDescTest"), thisClassDesc);
        assertEquals("ClassDescTest", thisClassDesc.displayName());
        testClassDesc(thisClassDesc, ClassDescTest.class);
    }

    @Test
    public void testPackageName() {
        assertEquals("com.foo", ClassDesc.of("com.foo.Bar").packageName());
        assertEquals("com.foo", ClassDesc.of("com.foo.Bar").nested("Baz").packageName());
        assertEquals("", ClassDesc.of("Bar").packageName());
        assertEquals("", ClassDesc.of("Bar").nested("Baz").packageName());
        assertEquals("", ClassDesc.of("Bar").nested("Baz", "Foo").packageName());

        assertEquals("", ConstantDescs.CD_int.packageName());
        assertEquals("", ConstantDescs.CD_int.arrayType().packageName());
        assertEquals("", ConstantDescs.CD_String.arrayType().packageName());
        assertEquals("", ClassDesc.of("Bar").arrayType().packageName());
    }

    private void testBadArrayRank(ClassDesc cr) {
        assertThrows(IllegalArgumentException.class, () -> cr.arrayType(-1));
        assertThrows(IllegalArgumentException.class, () -> cr.arrayType(0));
    }

    private void testArrayRankOverflow() {
        ClassDesc TwoDArrayDesc =
            String.class.describeConstable().get().arrayType().arrayType();

        assertThrows(IllegalArgumentException.class, () -> TwoDArrayDesc.arrayType(Integer.MAX_VALUE));
    }


    @Test
    public void testArrayClassDesc() throws ReflectiveOperationException {
        for (String d : basicDescs) {
            ClassDesc a0 = ClassDesc.ofDescriptor(d);
            ClassDesc a1 = a0.arrayType();
            ClassDesc a2 = a1.arrayType();

            testClassDesc(a0);
            testClassDesc(a1);
            testClassDesc(a2);
            assertFalse(a0.isArray());
            assertTrue(a1.isArray());
            assertTrue(a2.isArray());
            assertFalse(a1.isPrimitive());
            assertFalse(a2.isPrimitive());
            assertEquals(d, a0.descriptorString());
            assertEquals("[" + a0.descriptorString(), a1.descriptorString());
            assertEquals("[[" + a0.descriptorString(), a2.descriptorString());

            assertNull(a0.componentType());
            assertEquals(a1.componentType(), a0);
            assertEquals(a2.componentType(), a1);

            assertNotEquals(a1, a0);
            assertNotEquals(a2, a1);

            assertEquals(ClassDesc.ofDescriptor("[" + d), a1);
            assertEquals(ClassDesc.ofDescriptor("[[" + d), a2);
            assertEquals(a0.descriptorString(), classToDescriptor(a0.resolveConstantDesc(LOOKUP)));
            assertEquals(a1.descriptorString(), classToDescriptor(a1.resolveConstantDesc(LOOKUP)));
            assertEquals(a2.descriptorString(), classToDescriptor(a2.resolveConstantDesc(LOOKUP)));

            testBadArrayRank(ConstantDescs.CD_int);
            testBadArrayRank(ConstantDescs.CD_String);
            testBadArrayRank(ClassDesc.of("Bar"));
            testArrayRankOverflow();
        }
        assertThrows(IllegalArgumentException.class, () -> ConstantDescs.CD_void.arrayType());
    }

    @Test
    public void testBadClassDescs() {
        List<String> badDescriptors = List.of("II", "I;", "Q", "L", "",
                                              "java.lang.String", "[]", "Ljava/lang/String",
                                              "Ljava.lang.String;", "java/lang/String", "L;",
                                              "La//b;", "L/a;", "La/;");

        for (String d : badDescriptors) {
            assertThrows(IllegalArgumentException.class, () -> ClassDesc.ofDescriptor(d), d);
        }

        List<String> badBinaryNames = List.of("I;", "[]", "Ljava/lang/String",
                "Ljava.lang.String;", "java/lang/String", "");
        for (String d : badBinaryNames) {
            assertThrows(IllegalArgumentException.class, () -> ClassDesc.of(d), d);
        }

        List<String> badInternalNames = List.of("I;", "[]", "[Ljava/lang/String;",
                "Ljava.lang.String;", "java.lang.String", "");
        for (String d : badInternalNames) {
            assertThrows(IllegalArgumentException.class, () -> ClassDesc.ofInternalName(d), d);
        }

        for (Primitives p : Primitives.values()) {
            testBadNestedClasses(ClassDesc.ofDescriptor(p.descriptor), "any");
            testBadNestedClasses(ClassDesc.ofDescriptor(p.descriptor), "any", "other");
        }

        ClassDesc stringDesc = ClassDesc.ofDescriptor("Ljava/lang/String;");
        ClassDesc stringArrDesc = stringDesc.arrayType(255);
        assertThrows(IllegalStateException.class, () -> stringArrDesc.arrayType(),
                "can't create an array type descriptor with more than 255 dimensions");
        String descWith255ArrayDims = "[".repeat(255);
        assertThrows(IllegalArgumentException.class, () -> ClassDesc.ofDescriptor(descWith255ArrayDims + "[Ljava/lang/String;"),
                "can't create an array type descriptor with more than 255 dimensions");
        ClassDesc arrWith255Dims = ClassDesc.ofDescriptor(descWith255ArrayDims + "Ljava/lang/String;");
        assertThrows(IllegalArgumentException.class, () -> arrWith255Dims.arrayType(1),
                "can't create an array type descriptor with more than 255 dimensions");
    }

    private void testBadNestedClasses(ClassDesc cr, String firstNestedName, String... moreNestedNames) {
        assertThrows(IllegalStateException.class, () -> cr.nested(firstNestedName, moreNestedNames));
    }

    @Test
    public void testLangClasses() {
        Double d = 1.0;
        assertEquals(d, d.resolveConstantDesc(LOOKUP));
        assertEquals(d, d.describeConstable().get());

        Integer i = 1;
        assertEquals(i, i.resolveConstantDesc(LOOKUP));
        assertEquals(i, i.describeConstable().get());

        Float f = 1.0f;
        assertEquals(f, f.resolveConstantDesc(LOOKUP));
        assertEquals(f, f.describeConstable().get());

        Long l = 1L;
        assertEquals(l, l.resolveConstantDesc(LOOKUP));
        assertEquals(l, l.describeConstable().get());

        String s = "";
        assertEquals(s, s.resolveConstantDesc(LOOKUP));
        assertEquals(s, s.describeConstable().get());
    }

    @Test
    public void testNullNestedClasses() {
        ClassDesc cd = ClassDesc.of("Bar");
        assertThrows(NullPointerException.class, () -> cd.nested(null));
        assertThrows(NullPointerException.class, () -> cd.nested("good", null));
        assertThrows(NullPointerException.class, () -> cd.nested("good", "goodToo", null));
    }
}
