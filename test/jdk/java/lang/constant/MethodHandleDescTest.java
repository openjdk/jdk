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

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandleInfo;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.invoke.WrongMethodTypeException;
import java.lang.constant.ClassDesc;
import java.lang.constant.ConstantDescs;
import java.lang.constant.DirectMethodHandleDesc;
import java.lang.constant.MethodHandleDesc;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.constant.MethodTypeDesc;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;


import static java.lang.constant.ConstantDescs.CD_Void;
import static java.lang.constant.ConstantDescs.CD_boolean;
import static java.lang.constant.DirectMethodHandleDesc.*;
import static java.lang.constant.DirectMethodHandleDesc.Kind.GETTER;
import static java.lang.constant.DirectMethodHandleDesc.Kind.SETTER;
import static java.lang.constant.DirectMethodHandleDesc.Kind.STATIC_GETTER;
import static java.lang.constant.DirectMethodHandleDesc.Kind.STATIC_SETTER;
import static java.lang.constant.DirectMethodHandleDesc.Kind.VIRTUAL;
import static java.lang.constant.ConstantDescs.CD_Integer;
import static java.lang.constant.ConstantDescs.CD_List;
import static java.lang.constant.ConstantDescs.CD_Object;
import static java.lang.constant.ConstantDescs.CD_String;
import static java.lang.constant.ConstantDescs.CD_int;
import static java.lang.constant.ConstantDescs.CD_void;
import static java.lang.invoke.MethodHandleInfo.*;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/*
 * @test
 * @compile MethodHandleDescTest.java
 * @run junit MethodHandleDescTest
 * @summary unit tests for java.lang.constant.MethodHandleDesc
 */
public class MethodHandleDescTest extends SymbolicDescTest {
    private static ClassDesc helperHolderClass = ClassDesc.of("TestHelpers");
    private static ClassDesc testClass = helperHolderClass.nested("TestClass");
    private static ClassDesc testInterface = helperHolderClass.nested("TestInterface");
    private static ClassDesc testSuperclass = helperHolderClass.nested("TestSuperclass");


    private static void assertMHEquals(MethodHandle expected, MethodHandle actual) {
        MethodHandleInfo expectedInfo = LOOKUP.revealDirect(expected);
        MethodHandleInfo actualInfo = LOOKUP.revealDirect(actual);
        assertEquals(expectedInfo.getDeclaringClass(), actualInfo.getDeclaringClass());
        assertEquals(expectedInfo.getName(), actualInfo.getName());
        assertEquals(expectedInfo.getMethodType(), actualInfo.getMethodType());
        assertEquals(expectedInfo.getReferenceKind(), actualInfo.getReferenceKind());
    }

    private void testMethodHandleDesc(MethodHandleDesc r) throws ReflectiveOperationException {
        if (r instanceof DirectMethodHandleDesc) {
            testSymbolicDesc(r);

            DirectMethodHandleDesc rr = (DirectMethodHandleDesc) r;
            assertEquals(MethodHandleDesc.of(rr.kind(), rr.owner(), rr.methodName(), rr.lookupDescriptor()), r);
            assertEquals(r.resolveConstantDesc(LOOKUP).type(), r.invocationType().resolveConstantDesc(LOOKUP));
        }
        else {
            testSymbolicDescForwardOnly(r);
        }
    }

    private String lookupDescriptor(DirectMethodHandleDesc rr) {
        switch (rr.kind()) {
            case VIRTUAL:
            case SPECIAL:
            case INTERFACE_VIRTUAL:
            case INTERFACE_SPECIAL:
                return rr.invocationType().dropParameterTypes(0, 1).descriptorString();
            case CONSTRUCTOR:
                return rr.invocationType().changeReturnType(CD_void).descriptorString();
            default:
                return rr.invocationType().descriptorString();
        }
    }

    private void testMethodHandleDesc(MethodHandleDesc r, MethodHandle mh) throws ReflectiveOperationException {
        testMethodHandleDesc(r);

        assertMHEquals(mh, r.resolveConstantDesc(LOOKUP));
        assertEquals(r, mh.describeConstable().orElseThrow());

        // compare extractable properties: refKind, owner, name, type
        MethodHandleInfo mhi = LOOKUP.revealDirect(mh);
        DirectMethodHandleDesc rr = (DirectMethodHandleDesc) r;
        assertEquals(rr.owner().descriptorString(), mhi.getDeclaringClass().descriptorString());
        assertEquals(rr.methodName(), mhi.getName());
        assertEquals(rr.kind().refKind, mhi.getReferenceKind());
        MethodType type = mhi.getMethodType();
        assertEquals(lookupDescriptor(rr), type.toMethodDescriptorString());
    }

    @Test
    public void testSimpleMHs() throws ReflectiveOperationException {
        MethodHandle MH_String_isEmpty = LOOKUP.findVirtual(String.class, "isEmpty", MethodType.fromMethodDescriptorString("()Z", null));
        testMethodHandleDesc(MethodHandleDesc.of(Kind.VIRTUAL, CD_String, "isEmpty", "()Z"), MH_String_isEmpty);
        testMethodHandleDesc(MethodHandleDesc.ofMethod(Kind.VIRTUAL, CD_String, "isEmpty", MethodTypeDesc.of(CD_boolean)), MH_String_isEmpty);

        MethodHandle MH_List_isEmpty = LOOKUP.findVirtual(List.class, "isEmpty", MethodType.fromMethodDescriptorString("()Z", null));
        testMethodHandleDesc(MethodHandleDesc.of(Kind.INTERFACE_VIRTUAL, CD_List, "isEmpty", "()Z"), MH_List_isEmpty);
        testMethodHandleDesc(MethodHandleDesc.ofMethod(Kind.INTERFACE_VIRTUAL, CD_List, "isEmpty", MethodTypeDesc.of(CD_boolean)), MH_List_isEmpty);

        MethodHandle MH_String_format = LOOKUP.findStatic(String.class, "format", MethodType.methodType(String.class, String.class, Object[].class));
        testMethodHandleDesc(MethodHandleDesc.of(Kind.STATIC, CD_String, "format", MethodType.methodType(String.class, String.class, Object[].class).descriptorString()),
                             MH_String_format);
        testMethodHandleDesc(MethodHandleDesc.ofMethod(Kind.STATIC, CD_String, "format", MethodTypeDesc.of(CD_String, CD_String, CD_Object.arrayType())),
                             MH_String_format);

        MethodHandle MH_ArrayList_new = LOOKUP.findConstructor(ArrayList.class, MethodType.methodType(void.class));
        testMethodHandleDesc(MethodHandleDesc.ofMethod(Kind.CONSTRUCTOR, ClassDesc.of("java.util.ArrayList"), "<init>", MethodTypeDesc.of(CD_void)),
                             MH_ArrayList_new);
        testMethodHandleDesc(MethodHandleDesc.ofConstructor(ClassDesc.of("java.util.ArrayList")), MH_ArrayList_new);

        assertThrows(IllegalArgumentException.class, () -> MethodHandleDesc.of(Kind.CONSTRUCTOR, ClassDesc.of("java.util.ArrayList"), "<init>", "()I"),
                "bad constructor non void return type");
        assertThrows(NullPointerException.class, () -> MethodHandleDesc.ofConstructor(ClassDesc.of("java.util.ArrayList", null)),
                "null list of parameters");

        assertThrows(NullPointerException.class, () -> MethodHandleDesc.ofConstructor(ClassDesc.of("java.util.ArrayList"), new ClassDesc[] { null }),
                "null elements in list of parameters");
    }

    @Test
    public void testAsType() throws Throwable {
        MethodHandleDesc mhr = MethodHandleDesc.ofMethod(Kind.STATIC, ClassDesc.of("java.lang.Integer"), "valueOf",
                                                         MethodTypeDesc.of(CD_Integer, CD_int));
        MethodHandleDesc takesInteger = mhr.asType(MethodTypeDesc.of(CD_Integer, CD_Integer));
        testMethodHandleDesc(takesInteger);
        MethodHandle mh1 = takesInteger.resolveConstantDesc(LOOKUP);
        assertEquals((Integer) 3, (Integer) mh1.invokeExact((Integer) 3));
        assertEquals("MethodHandleDesc[STATIC/Integer::valueOf(int)Integer].asType(Integer)Integer", takesInteger.toString());

        assertThrows(WrongMethodTypeException.class, () -> {
            Integer _ = (Integer) mh1.invokeExact(3);
        });

        MethodHandleDesc takesInt = takesInteger.asType(MethodTypeDesc.of(CD_Integer, CD_int));
        testMethodHandleDesc(takesInt);
        MethodHandle mh2 = takesInt.resolveConstantDesc(LOOKUP);
        assertEquals((Integer) 3, (Integer) mh2.invokeExact(3));

        assertThrows(WrongMethodTypeException.class, () -> {
            Integer _ = (Integer) mh2.invokeExact((Integer) 3);
        });

        // Short circuit optimization
        MethodHandleDesc same = mhr.asType(mhr.invocationType());
        assertSame(mhr, same);

        assertThrows(NullPointerException.class, () -> mhr.asType(null));

        // @@@ Test varargs adaptation
        // @@@ Test bad adaptations and assert runtime error on resolution
        // @@@ Test intrinsification of adapted MH
    }

    @Test
    public void testMethodHandleDesc() throws Throwable {
        MethodHandleDesc ctorDesc = MethodHandleDesc.of(Kind.CONSTRUCTOR, testClass, "<ignored!>", "()V");
        MethodHandleDesc staticMethodDesc = MethodHandleDesc.of(Kind.STATIC, testClass, "sm", "(I)I");
        MethodHandleDesc staticIMethodDesc = MethodHandleDesc.of(Kind.INTERFACE_STATIC, testInterface, "sm", "(I)I");
        MethodHandleDesc instanceMethodDesc = MethodHandleDesc.of(Kind.VIRTUAL, testClass, "m", "(I)I");
        MethodHandleDesc instanceIMethodDesc = MethodHandleDesc.of(Kind.INTERFACE_VIRTUAL, testInterface, "m", "(I)I");
        MethodHandleDesc superMethodDesc = MethodHandleDesc.of(Kind.SPECIAL, testSuperclass, "m", "(I)I");
        MethodHandleDesc superIMethodDesc = MethodHandleDesc.of(Kind.INTERFACE_SPECIAL, testInterface, "m", "(I)I");
        MethodHandleDesc privateMethodDesc = MethodHandleDesc.of(Kind.SPECIAL, testClass, "pm", "(I)I");
        MethodHandleDesc privateIMethodDesc = MethodHandleDesc.of(Kind.INTERFACE_SPECIAL, testInterface, "pm", "(I)I");
        MethodHandleDesc privateStaticMethodDesc = MethodHandleDesc.of(Kind.STATIC, testClass, "psm", "(I)I");
        MethodHandleDesc privateStaticIMethodDesc = MethodHandleDesc.of(Kind.INTERFACE_STATIC, testInterface, "psm", "(I)I");

        assertEquals(MethodTypeDesc.of(testClass), ctorDesc.invocationType());
        assertEquals("()V", ((DirectMethodHandleDesc) ctorDesc).lookupDescriptor());

        assertEquals("(I)I", staticMethodDesc.invocationType().descriptorString());
        assertEquals("(I)I", ((DirectMethodHandleDesc) staticMethodDesc).lookupDescriptor());

        assertEquals("(" + testClass.descriptorString() + "I)I", instanceMethodDesc.invocationType().descriptorString());
        assertEquals("(I)I", ((DirectMethodHandleDesc) instanceMethodDesc).lookupDescriptor());

        for (MethodHandleDesc r : List.of(ctorDesc, staticMethodDesc, staticIMethodDesc, instanceMethodDesc, instanceIMethodDesc))
            testMethodHandleDesc(r);

        TestHelpers.TestClass instance = (TestHelpers.TestClass) ctorDesc.resolveConstantDesc(LOOKUP).invokeExact();
        TestHelpers.TestClass instance2 = (TestHelpers.TestClass) ctorDesc.resolveConstantDesc(TestHelpers.TestClass.LOOKUP).invokeExact();
        TestHelpers.TestInterface instanceI = instance;

        assertNotSame(instance, instance2);

        assertEquals(5, (int) staticMethodDesc.resolveConstantDesc(LOOKUP).invokeExact(5));
        assertEquals(5, (int) staticMethodDesc.resolveConstantDesc(TestHelpers.TestClass.LOOKUP).invokeExact(5));
        assertEquals(0, (int) staticIMethodDesc.resolveConstantDesc(LOOKUP).invokeExact(5));
        assertEquals(0, (int) staticIMethodDesc.resolveConstantDesc(TestHelpers.TestClass.LOOKUP).invokeExact(5));

        assertEquals(5, (int) instanceMethodDesc.resolveConstantDesc(LOOKUP).invokeExact(instance, 5));
        assertEquals(5, (int) instanceMethodDesc.resolveConstantDesc(TestHelpers.TestClass.LOOKUP).invokeExact(instance, 5));
        assertEquals(5, (int) instanceIMethodDesc.resolveConstantDesc(LOOKUP).invokeExact(instanceI, 5));
        assertEquals(5, (int) instanceIMethodDesc.resolveConstantDesc(TestHelpers.TestClass.LOOKUP).invokeExact(instanceI, 5));

        assertThrows(IllegalAccessException.class, () -> superMethodDesc.resolveConstantDesc(LOOKUP));
        assertEquals(-1, (int) superMethodDesc.resolveConstantDesc(TestHelpers.TestClass.LOOKUP).invokeExact(instance, 5));

        assertThrows(IllegalAccessException.class, () -> superIMethodDesc.resolveConstantDesc(LOOKUP));
        assertEquals(0, (int) superIMethodDesc.resolveConstantDesc(TestHelpers.TestClass.LOOKUP).invokeExact(instance, 5));

        assertThrows(IllegalAccessException.class, () -> privateMethodDesc.resolveConstantDesc(LOOKUP));
        assertEquals(5, (int) privateMethodDesc.resolveConstantDesc(TestHelpers.TestClass.LOOKUP).invokeExact(instance, 5));

        assertThrows(IllegalAccessException.class, () -> privateIMethodDesc.resolveConstantDesc(LOOKUP));
        assertEquals(0, (int) privateIMethodDesc.resolveConstantDesc(TestHelpers.TestInterface.LOOKUP).invokeExact(instanceI, 5));
        assertEquals(0, (int) privateIMethodDesc.resolveConstantDesc(TestHelpers.TestClass.LOOKUP).invoke(instanceI, 5));

        assertThrows(IllegalAccessException.class, () -> privateStaticMethodDesc.resolveConstantDesc(LOOKUP));
        assertEquals(5, (int) privateStaticMethodDesc.resolveConstantDesc(TestHelpers.TestClass.LOOKUP).invokeExact(5));

        assertThrows(IllegalAccessException.class, () -> privateStaticIMethodDesc.resolveConstantDesc(LOOKUP));
        assertEquals(0, (int) privateStaticIMethodDesc.resolveConstantDesc(TestHelpers.TestInterface.LOOKUP).invokeExact(5));
        assertEquals(0, (int) privateStaticIMethodDesc.resolveConstantDesc(TestHelpers.TestClass.LOOKUP).invokeExact(5));

        MethodHandleDesc staticSetterDesc = MethodHandleDesc.ofField(STATIC_SETTER, testClass, "sf", CD_int);
        MethodHandleDesc staticGetterDesc = MethodHandleDesc.ofField(STATIC_GETTER, testClass, "sf", CD_int);
        MethodHandleDesc staticGetterIDesc = MethodHandleDesc.ofField(STATIC_GETTER, testInterface, "sf", CD_int);
        MethodHandleDesc setterDesc = MethodHandleDesc.ofField(SETTER, testClass, "f", CD_int);
        MethodHandleDesc getterDesc = MethodHandleDesc.ofField(GETTER, testClass, "f", CD_int);

        for (MethodHandleDesc r : List.of(staticSetterDesc, staticGetterDesc, staticGetterIDesc, setterDesc, getterDesc))
            testMethodHandleDesc(r);

        staticSetterDesc.resolveConstantDesc(LOOKUP).invokeExact(6); assertEquals(6, TestHelpers.TestClass.sf);
        assertEquals(6, (int) staticGetterDesc.resolveConstantDesc(LOOKUP).invokeExact());
        assertEquals(6, (int) staticGetterDesc.resolveConstantDesc(TestHelpers.TestClass.LOOKUP).invokeExact());
        staticSetterDesc.resolveConstantDesc(TestHelpers.TestClass.LOOKUP).invokeExact(7); assertEquals(7, TestHelpers.TestClass.sf);
        assertEquals(7, (int) staticGetterDesc.resolveConstantDesc(LOOKUP).invokeExact());
        assertEquals(7, (int) staticGetterDesc.resolveConstantDesc(TestHelpers.TestClass.LOOKUP).invokeExact());

        assertEquals(3, (int) staticGetterIDesc.resolveConstantDesc(LOOKUP).invokeExact());
        assertEquals(3, (int) staticGetterIDesc.resolveConstantDesc(TestHelpers.TestClass.LOOKUP).invokeExact());

        setterDesc.resolveConstantDesc(LOOKUP).invokeExact(instance, 6); assertEquals(6, instance.f);
        assertEquals(6, (int) getterDesc.resolveConstantDesc(LOOKUP).invokeExact(instance));
        assertEquals(6, (int) getterDesc.resolveConstantDesc(TestHelpers.TestClass.LOOKUP).invokeExact(instance));
        setterDesc.resolveConstantDesc(TestHelpers.TestClass.LOOKUP).invokeExact(instance, 7); assertEquals(7, instance.f);
        assertEquals(7, (int) getterDesc.resolveConstantDesc(LOOKUP).invokeExact(instance));
        assertEquals(7, (int) getterDesc.resolveConstantDesc(TestHelpers.TestClass.LOOKUP).invokeExact(instance));
    }

    private void assertBadArgs(Supplier<MethodHandleDesc> supplier, String s) {
        assertThrows(IllegalArgumentException.class, supplier::get, s);
    }

    @Test
    public void testBadFieldMHs() {
        List<String> badGetterDescs = List.of("()V", "(Ljava/lang/Object;)V", "(I)I", "(Ljava/lang/Object;I)I");
        List<String> badStaticGetterDescs = List.of("()V", "(Ljava/lang/Object;)I", "(I)I", "(Ljava/lang/Object;I)I");
        List<String> badSetterDescs = List.of("()V", "(I)V", "(Ljava/lang/Object;)V", "(Ljava/lang/Object;I)I", "(Ljava/lang/Object;II)V");
        List<String> badStaticSetterDescs = List.of("()V", "(II)V", "()I");

        badGetterDescs.forEach(s -> assertBadArgs(() -> MethodHandleDesc.of(GETTER, helperHolderClass, "x", s), s));
        badSetterDescs.forEach(s -> assertBadArgs(() -> MethodHandleDesc.of(SETTER, helperHolderClass, "x", s), s));
        badStaticGetterDescs.forEach(s -> assertBadArgs(() -> MethodHandleDesc.of(STATIC_GETTER, helperHolderClass, "x", s), s));
        badStaticSetterDescs.forEach(s -> assertBadArgs(() -> MethodHandleDesc.of(STATIC_SETTER, helperHolderClass, "x", s), s));
    }

    @Test
    public void testBadOwners() {
        assertThrows(IllegalArgumentException.class, () -> MethodHandleDesc.ofMethod(VIRTUAL, ClassDesc.ofDescriptor("I"), "x", MethodTypeDesc.ofDescriptor("()I")));
    }

    @Test
    public void testSymbolicDescsConstants() throws ReflectiveOperationException {
        int tested = 0;
        Field[] fields = ConstantDescs.class.getDeclaredFields();
        for (Field f : fields) {
            try {
                if (f.getType().equals(DirectMethodHandleDesc.class)
                    && ((f.getModifiers() & Modifier.STATIC) != 0)
                    && ((f.getModifiers() & Modifier.PUBLIC) != 0)) {
                    MethodHandleDesc r = (MethodHandleDesc) f.get(null);
                    MethodHandle m = r.resolveConstantDesc(MethodHandles.lookup());
                    testMethodHandleDesc(r, m);
                    ++tested;
                }
            }
            catch (Throwable e) {
                fail("Error testing field " + f.getName(), e);
            }
        }

        assertTrue(tested > 0);
    }

    @Test
    public void testKind() {
        for (Kind k : Kind.values()) {
            assertEquals(Kind.valueOf(k.refKind, k.refKind == MethodHandleInfo.REF_invokeInterface), Kind.valueOf(k.refKind));
            assertEquals(k, Kind.valueOf(k.refKind, k.isInterface));
        }
        // let's now verify those cases for which the value of the isInterface parameter is ignored
        int[] isInterfaceIgnored = new int[] {
                REF_getField,
                REF_getStatic,
                REF_putField,
                REF_putStatic,
                REF_newInvokeSpecial,
                REF_invokeInterface
        };
        for (int refKind : isInterfaceIgnored) {
            assertEquals(Kind.valueOf(refKind, true), Kind.valueOf(refKind, false));
        }

        // some explicit tests for REF_invokeStatic and REF_invokeSpecial
        assertNotEquals(Kind.valueOf(REF_invokeStatic, true), Kind.valueOf(REF_invokeStatic, false));
        assertNotEquals(Kind.valueOf(REF_invokeSpecial, true), Kind.valueOf(REF_invokeSpecial, false));
        assertEquals(Kind.STATIC, Kind.valueOf(REF_invokeStatic, false));
        assertEquals(Kind.INTERFACE_STATIC, Kind.valueOf(REF_invokeStatic, true));
        assertEquals(Kind.SPECIAL, Kind.valueOf(REF_invokeSpecial, false));
        assertEquals(Kind.INTERFACE_SPECIAL, Kind.valueOf(REF_invokeSpecial, true));
    }
}
