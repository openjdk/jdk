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
import java.lang.invoke.MethodType;
import java.lang.constant.ClassDesc;
import java.lang.constant.MethodTypeDesc;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.IntStream;
import java.util.stream.Stream;


import static java.lang.constant.ConstantDescs.*;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toList;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

/*
 * @test
 * @bug 8304932
 * @compile MethodTypeDescTest.java
 * @run junit MethodTypeDescTest
 * @summary unit tests for java.lang.constant.MethodTypeDesc
 */
public class MethodTypeDescTest extends SymbolicDescTest {

    private void testMethodTypeDesc(MethodTypeDesc r) throws ReflectiveOperationException {
        testSymbolicDesc(r);

        // Tests accessors (rType, pType, pCount, pList, pArray, descriptorString),
        // factories (ofDescriptor, of), equals
        if (r.parameterCount() == 0) {
            assertEquals(r, MethodTypeDesc.of(r.returnType()));
        }
        assertEquals(r, MethodTypeDesc.ofDescriptor(r.descriptorString()));
        assertEquals(r, MethodTypeDesc.of(r.returnType(), r.parameterArray()));
        assertEquals(r, MethodTypeDesc.of(r.returnType(), r.parameterList().toArray(new ClassDesc[0])));
        assertEquals(r, MethodTypeDesc.of(r.returnType(), r.parameterList().stream().toArray(ClassDesc[]::new)));
        assertEquals(r, MethodTypeDesc.of(r.returnType(), IntStream.range(0, r.parameterCount())
                                                                   .mapToObj(r::parameterType)
                                                                   .toArray(ClassDesc[]::new)));
        assertEquals(r, MethodTypeDesc.of(r.returnType(), r.parameterList()));
        assertEquals(r, MethodTypeDesc.of(r.returnType(), List.copyOf(r.parameterList())));
        assertEquals(r, MethodTypeDesc.of(r.returnType(), r.parameterList().stream().toList()));
        assertEquals(r, MethodTypeDesc.of(r.returnType(), IntStream.range(0, r.parameterCount())
                                                                   .mapToObj(r::parameterType)
                                                                   .toList()));
    }

    private void testMethodTypeDesc(MethodTypeDesc r, MethodType mt) throws ReflectiveOperationException {
        testMethodTypeDesc(r);

        assertEquals(mt, r.resolveConstantDesc(LOOKUP));
        assertEquals(r, mt.describeConstable().get());

        assertEquals(mt.toMethodDescriptorString(), r.descriptorString());
        assertEquals(mt.parameterCount(), r.parameterCount());
        assertEquals(mt.parameterList().stream().map(SymbolicDescTest::classToDesc).collect(toList()), r.parameterList());
        assertArrayEquals(Stream.of(mt.parameterArray()).map(SymbolicDescTest::classToDesc).toArray(ClassDesc[]::new), r.parameterArray());
        for (int i=0; i<r.parameterCount(); i++)
            assertEquals(classToDesc(mt.parameterType(i)), r.parameterType(i));
        assertEquals(classToDesc(mt.returnType()), r.returnType());
    }

    private void assertMethodType(ClassDesc returnType,
                                  ClassDesc... paramTypes) throws ReflectiveOperationException {
        String descriptor = Stream.of(paramTypes).map(ClassDesc::descriptorString).collect(joining("", "(", ")"))
                            + returnType.descriptorString();
        MethodTypeDesc mtDesc = MethodTypeDesc.of(returnType, paramTypes);

        // MTDesc accessors
        assertEquals(mtDesc.descriptorString(), descriptor);
        assertEquals(mtDesc.returnType(), returnType);
        assertArrayEquals(mtDesc.parameterArray(), paramTypes);
        assertEquals(mtDesc.parameterList(), Arrays.asList(paramTypes));
        assertEquals(mtDesc.parameterCount(), paramTypes.length);
        for (int i=0; i<paramTypes.length; i++)
            assertEquals(mtDesc.parameterType(i), paramTypes[i]);

        // Consistency between MT and MTDesc
        MethodType mt = MethodType.fromMethodDescriptorString(descriptor, null);
        testMethodTypeDesc(mtDesc, mt);

        // changeReturnType
        for (String r : returnDescs) {
            ClassDesc rc = ClassDesc.ofDescriptor(r);
            MethodTypeDesc newDesc = mtDesc.changeReturnType(rc);
            assertEquals(MethodTypeDesc.of(rc, paramTypes), newDesc);
            testMethodTypeDesc(newDesc, mt.changeReturnType(rc.resolveConstantDesc(LOOKUP)));
        }

        // try with null parameter
        assertThrows(NullPointerException.class, () -> mtDesc.changeReturnType(null));

        // changeParamType
        for (int i=0; i<paramTypes.length; i++) {
            for (String p : paramDescs) {
                ClassDesc pc = ClassDesc.ofDescriptor(p);
                ClassDesc[] ps = paramTypes.clone();
                ps[i] = pc;
                MethodTypeDesc newDesc = mtDesc.changeParameterType(i, pc);
                assertEquals(MethodTypeDesc.of(returnType, ps), newDesc);
                testMethodTypeDesc(newDesc, mt.changeParameterType(i, pc.resolveConstantDesc(LOOKUP)));
            }
        }

        // dropParamType
        for (int i=0; i<paramTypes.length; i++) {
            int k = i;
            ClassDesc[] ps = IntStream.range(0, paramTypes.length)
                                      .filter(j -> j != k)
                                      .mapToObj(j -> paramTypes[j])
                                      .toArray(ClassDesc[]::new);
            MethodTypeDesc newDesc = mtDesc.dropParameterTypes(i, i + 1);
            assertEquals(MethodTypeDesc.of(returnType, ps), newDesc);
            testMethodTypeDesc(newDesc, mt.dropParameterTypes(i, i+1));

            // drop multiple params
            for (int j = i; j < paramTypes.length; j++) {
                var t = new ArrayList<>(Arrays.asList(paramTypes));
                t.subList(i, j).clear();
                MethodTypeDesc multiDrop = mtDesc.dropParameterTypes(i, j);
                assertEquals(MethodTypeDesc.of(returnType, t.toArray(ClassDesc[]::new)), multiDrop);
                testMethodTypeDesc(multiDrop, mt.dropParameterTypes(i, j));
            }
        }

        badDropParametersTypes(CD_void, paramDescs);

        // addParam
        for (int i=0; i <= paramTypes.length; i++) {
            for (ClassDesc p : paramTypes) {
                int k = i;
                ClassDesc[] ps = IntStream.range(0, paramTypes.length + 1)
                                          .mapToObj(j -> (j < k) ? paramTypes[j] : (j == k) ? p : paramTypes[j-1])
                                          .toArray(ClassDesc[]::new);
                MethodTypeDesc newDesc = mtDesc.insertParameterTypes(i, p);
                assertEquals(MethodTypeDesc.of(returnType, ps), newDesc);
                testMethodTypeDesc(newDesc, mt.insertParameterTypes(i, p.resolveConstantDesc(LOOKUP)));
            }

            // add multiple params
            ClassDesc[] addition = {CD_int, CD_String};
            var a = new ArrayList<>(Arrays.asList(paramTypes));
            a.addAll(i, Arrays.asList(addition));

            MethodTypeDesc newDesc = mtDesc.insertParameterTypes(i, addition);
            assertEquals(MethodTypeDesc.of(returnType, a.toArray(ClassDesc[]::new)), newDesc);
            testMethodTypeDesc(newDesc, mt.insertParameterTypes(i, Arrays.stream(addition).map(d -> {
                try {
                    return (Class<?>) d.resolveConstantDesc(LOOKUP);
                } catch (ReflectiveOperationException ex) {
                    throw new RuntimeException(ex);
                }
            }).toArray(Class[]::new)));
        }

        badInsertParametersTypes(CD_void, paramDescs);
    }

    private void badInsertParametersTypes(ClassDesc returnType, String... paramDescTypes) {
        ClassDesc[] paramTypes =
                IntStream.rangeClosed(0, paramDescTypes.length - 1)
                        .mapToObj(i -> ClassDesc.ofDescriptor(paramDescTypes[i])).toArray(ClassDesc[]::new);
        MethodTypeDesc mtDesc = MethodTypeDesc.of(returnType, paramTypes);
        assertThrows(IndexOutOfBoundsException.class, () -> mtDesc.insertParameterTypes(-1, paramTypes));

        assertThrows(IndexOutOfBoundsException.class, () -> mtDesc.insertParameterTypes(paramTypes.length + 1, paramTypes));

        {
            ClassDesc[] newParamTypes = new ClassDesc[1];
            newParamTypes[0] = CD_void;
            MethodTypeDesc newDesc = MethodTypeDesc.of(returnType, CD_int);
            assertThrows(IllegalArgumentException.class, () -> newDesc.insertParameterTypes(0, newParamTypes));
        }

        {
            MethodTypeDesc newDesc = MethodTypeDesc.of(returnType, CD_int);
            assertThrows(NullPointerException.class, () -> newDesc.insertParameterTypes(0, null));
        }

        {
            ClassDesc[] newParamTypes = new ClassDesc[1];
            newParamTypes[0] = null;
            MethodTypeDesc newDesc = MethodTypeDesc.of(returnType, CD_int);
            assertThrows(NullPointerException.class, () -> newDesc.insertParameterTypes(0, newParamTypes));
        }
    }

    private void badDropParametersTypes(ClassDesc returnType, String... paramDescTypes) {
        ClassDesc[] paramTypes =
                IntStream.rangeClosed(0, paramDescTypes.length - 1)
                        .mapToObj(i -> ClassDesc.ofDescriptor(paramDescTypes[i])).toArray(ClassDesc[]::new);
        MethodTypeDesc mtDesc = MethodTypeDesc.of(returnType, paramTypes);

        assertThrows(IndexOutOfBoundsException.class, () -> mtDesc.dropParameterTypes(-1, 0));

        assertThrows(IndexOutOfBoundsException.class, () -> mtDesc.dropParameterTypes(paramTypes.length, 0));

        assertThrows(IndexOutOfBoundsException.class, () -> mtDesc.dropParameterTypes(paramTypes.length + 1, 0));

        assertThrows(IndexOutOfBoundsException.class, () -> mtDesc.dropParameterTypes(0, paramTypes.length + 1));

        assertThrows(IndexOutOfBoundsException.class, () -> mtDesc.dropParameterTypes(1, 0));
    }

    @Test
    public void testMethodTypeDesc() throws ReflectiveOperationException {
        for (String r : returnDescs) {
            assertMethodType(ClassDesc.ofDescriptor(r));
            for (String p1 : paramDescs) {
                assertMethodType(ClassDesc.ofDescriptor(r), ClassDesc.ofDescriptor(p1));
                for (String p2 : paramDescs) {
                    assertMethodType(ClassDesc.ofDescriptor(r), ClassDesc.ofDescriptor(p1), ClassDesc.ofDescriptor(p2));
                }
            }
        }
    }

    @Test
    public void testBadMethodTypeRefs() {
        // ofDescriptor
        List<String> badDescriptors = List.of("()II", "()I;", "(I;)", "(I)", "()L", "(V)V",
                                              "(java.lang.String)V", "()[]", "(Ljava/lang/String)V",
                                              "(Ljava.lang.String;)V", "(java/lang/String)V");

        for (String d : badDescriptors) {
            assertThrows(IllegalArgumentException.class, () -> MethodTypeDesc.ofDescriptor(d));
        }

        assertThrows(NullPointerException.class, () -> MethodTypeDesc.ofDescriptor(null));

        // of(ClassDesc)
        assertThrows(NullPointerException.class, () -> MethodTypeDesc.of(null));

        // of(ClassDesc, ClassDesc...)
        assertThrows(NullPointerException.class, () -> MethodTypeDesc.of(CD_int, (ClassDesc[]) null));
        assertThrows(NullPointerException.class, () -> MethodTypeDesc.of(CD_int, new ClassDesc[] {null}));
        assertThrows(IllegalArgumentException.class, () -> MethodTypeDesc.of(CD_int, CD_void));

        // of(ClassDesc, List<ClassDesc>)
        assertThrows(NullPointerException.class, () -> MethodTypeDesc.of(CD_int, (List<ClassDesc>) null));
        assertThrows(NullPointerException.class, () -> MethodTypeDesc.of(CD_int, Collections.singletonList(null)));
        assertThrows(IllegalArgumentException.class, () -> MethodTypeDesc.of(CD_int, List.of(CD_void)));
    }

    @Test
    public void testOfArrayImmutability() {
        ClassDesc[] args = {CD_Object, CD_int};
        var mtd = MethodTypeDesc.of(CD_void, args);

        args[1] = CD_void;
        assertEquals(MethodTypeDesc.of(CD_void, CD_Object, CD_int), mtd);

        mtd.parameterArray()[1] = CD_void;
        assertEquals(MethodTypeDesc.of(CD_void, CD_Object, CD_int), mtd);
    }

    @Test
    public void testOfListImmutability() {
        List<ClassDesc> args = Arrays.asList(CD_Object, CD_int);
        var mtd = MethodTypeDesc.of(CD_void, args);

        args.set(1, CD_void);
        assertEquals(MethodTypeDesc.of(CD_void, CD_Object, CD_int), mtd);

        assertThrows(UnsupportedOperationException.class, () ->
                mtd.parameterList().set(1, CD_void));
        assertEquals(MethodTypeDesc.of(CD_void, CD_Object, CD_int), mtd);
    }

    @Test
    public void testMissingClass() {
        var mtd = MTD_void.insertParameterTypes(0, ClassDesc.of("does.not.exist.DoesNotExist"));
        assertThrows(ReflectiveOperationException.class, () -> mtd.resolveConstantDesc(MethodHandles.publicLookup()));
    }
}
