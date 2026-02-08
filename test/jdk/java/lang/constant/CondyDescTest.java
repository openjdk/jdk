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

import java.lang.Enum.EnumDesc;
import java.lang.constant.MethodTypeDesc;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.lang.invoke.VarHandle.VarHandleDesc;
import java.lang.constant.ClassDesc;
import java.lang.constant.ConstantDesc;
import java.lang.constant.ConstantDescs;
import java.lang.constant.DirectMethodHandleDesc;
import java.lang.constant.DynamicConstantDesc;
import java.lang.constant.MethodHandleDesc;

import static java.lang.constant.ConstantDescs.CD_MethodHandle;
import static java.lang.constant.ConstantDescs.CD_Object;
import static java.lang.constant.ConstantDescs.CD_String;
import static java.lang.constant.ConstantDescs.CD_VarHandle;
import static java.lang.constant.ConstantDescs.CD_int;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

/*
 * @test
 * @compile CondyDescTest.java
 * @run junit CondyDescTest
 * @summary unit tests for java.lang.constant.CondyDescTest
 */
public class CondyDescTest extends SymbolicDescTest {
    private final static ConstantDesc[] EMPTY_ARGS = new ConstantDesc[0];
    private final static ClassDesc CD_ConstantBootstraps = ClassDesc.of("java.lang.invoke.ConstantBootstraps");

    private static<T> void testDCR(DynamicConstantDesc<T> r, T c) throws ReflectiveOperationException {
        assertEquals(DynamicConstantDesc.ofNamed(r.bootstrapMethod(), r.constantName(), r.constantType(), r.bootstrapArgs()), r);
        assertEquals(c, r.resolveConstantDesc(LOOKUP));
    }

    private void testVarHandleDesc(DynamicConstantDesc<VarHandle> r, VarHandle vh) throws ReflectiveOperationException  {
        testSymbolicDesc(r);
        assertEquals(r, vh.describeConstable().orElseThrow());
    }

    private static<E extends Enum<E>> void testEnumDesc(EnumDesc<E> r, E e) throws ReflectiveOperationException {
        testSymbolicDesc(r);

        assertEquals(EnumDesc.of(r.constantType(), r.constantName()), r);
        assertEquals(e, r.resolveConstantDesc(LOOKUP));
    }

    @Test
    public void testNullConstant() throws ReflectiveOperationException {
        DynamicConstantDesc<?> r = (DynamicConstantDesc<?>) ConstantDescs.NULL;
        assertEquals(DynamicConstantDesc.ofNamed(r.bootstrapMethod(), r.constantName(), r.constantType(), r.bootstrapArgs()), r);
        assertNull(r.resolveConstantDesc(LOOKUP));
    }

    static String concatBSM(MethodHandles.Lookup lookup, String name, Class<?> type, String a, String b) {
        return a + b;
    }

    @Test
    public void testDynamicConstant() throws ReflectiveOperationException {
        DirectMethodHandleDesc bsmDesc = ConstantDescs.ofConstantBootstrap(ClassDesc.of("CondyDescTest"), "concatBSM",
                                                                            CD_String, CD_String, CD_String);
        DynamicConstantDesc<String> r = DynamicConstantDesc.of(bsmDesc, "foo", "bar");
        testDCR(r, "foobar");
    }

    @Test
    public void testNested() throws Throwable {
        DirectMethodHandleDesc invoker = ConstantDescs.ofConstantBootstrap(CD_ConstantBootstraps, "invoke", CD_Object, CD_MethodHandle, CD_Object.arrayType());
        DirectMethodHandleDesc format = MethodHandleDesc.ofMethod(DirectMethodHandleDesc.Kind.STATIC, CD_String, "format",
                                                                  MethodTypeDesc.of(CD_String, CD_String, CD_Object.arrayType()));

        String s = (String) invoker.resolveConstantDesc(LOOKUP)
                                   .invoke(LOOKUP, "", String.class,
                                           format.resolveConstantDesc(LOOKUP), "%s%s", "moo", "cow");
        assertEquals("moocow", s);

        DynamicConstantDesc<String> desc = DynamicConstantDesc.of(invoker, format, "%s%s", "moo", "cow");
        testDCR(desc, "moocow");

        DynamicConstantDesc<String> desc2 = DynamicConstantDesc.of(invoker, format, "%s%s", desc, "cow");
        testDCR(desc2, "moocowcow");
    }

    enum MyEnum { A, B, C }

    @Test
    public void testEnumDesc() throws ReflectiveOperationException {
        ClassDesc enumClass = ClassDesc.of("CondyDescTest").nested("MyEnum");

        testEnumDesc(EnumDesc.of(enumClass, "A"), MyEnum.A);
        testEnumDesc(EnumDesc.of(enumClass, "B"), MyEnum.B);
        testEnumDesc(EnumDesc.of(enumClass, "C"), MyEnum.C);

        DynamicConstantDesc<MyEnum> denum = DynamicConstantDesc.ofNamed(ConstantDescs.BSM_ENUM_CONSTANT, "A", enumClass, EMPTY_ARGS);
        assertEquals(MyEnum.A, denum.resolveConstantDesc(LOOKUP));

        EnumDesc<MyEnum> enumDesc = (EnumDesc<MyEnum>)DynamicConstantDesc.<MyEnum>ofCanonical(ConstantDescs.BSM_ENUM_CONSTANT, "A", enumClass, EMPTY_ARGS);
        assertEquals(MyEnum.A, enumDesc.resolveConstantDesc(LOOKUP));
    }

    static class MyClass {
        static int sf;
        int f;
    }

    @Test
    public void testVarHandles() throws ReflectiveOperationException {
        ClassDesc testClass = ClassDesc.of("CondyDescTest").nested("MyClass");
        MyClass instance = new MyClass();

        // static varHandle
        VarHandleDesc vhc = VarHandleDesc.ofStaticField(testClass, "sf", CD_int);
        VarHandle varHandle = LOOKUP.findStaticVarHandle(MyClass.class, "sf", int.class);
        testVarHandleDesc(vhc, varHandle);

        assertEquals(int.class, varHandle.varType());
        varHandle.set(8);
        assertEquals(8, (int) varHandle.get());
        assertEquals(8, MyClass.sf);

        // static varHandle
        vhc = VarHandleDesc.ofField(testClass, "f", CD_int);
        varHandle = LOOKUP.findVarHandle(MyClass.class, "f", int.class);
        testVarHandleDesc(vhc, varHandle);

        assertEquals(int.class, varHandle.varType());
        varHandle.set(instance, 9);
        assertEquals(9, (int) varHandle.get(instance));
        assertEquals(9, instance.f);

        vhc = VarHandleDesc.ofArray(CD_int.arrayType());
        varHandle = MethodHandles.arrayElementVarHandle(int[].class);
        testVarHandleDesc(vhc, varHandle);

        int[] ints = new int[3];
        varHandle.set(ints, 0, 1);
        varHandle.set(ints, 1, 2);
        varHandle.set(ints, 2, 3);

        assertEquals(1, varHandle.get(ints, 0));
        assertEquals(2, varHandle.get(ints, 1));
        assertEquals(3, varHandle.get(ints, 2));
        assertEquals(1, ints[0]);
        assertEquals(2, ints[1]);
        assertEquals(3, ints[2]);

        // static var handle obtained using the DynamicConstantDesc
        DynamicConstantDesc<VarHandle> dcd = DynamicConstantDesc.ofNamed(ConstantDescs.BSM_VARHANDLE_STATIC_FIELD, "sf", CD_VarHandle, new ConstantDesc[] {testClass, CD_int });
        VarHandle vh = dcd.resolveConstantDesc(LOOKUP);
        testVarHandleDesc(dcd, vh);

        VarHandleDesc vhd = (VarHandleDesc) DynamicConstantDesc.ofCanonical(ConstantDescs.BSM_VARHANDLE_STATIC_FIELD, "sf", CD_VarHandle, new ConstantDesc[] {testClass, CD_int });
        vh = vhd.resolveConstantDesc(LOOKUP);
        testVarHandleDesc(vhd, vh);

        dcd = DynamicConstantDesc.ofNamed(ConstantDescs.BSM_VARHANDLE_FIELD, "f", CD_VarHandle, new ConstantDesc[] {testClass, CD_int });
        vh = dcd.resolveConstantDesc(LOOKUP);
        testVarHandleDesc(dcd, vh);

        vhd = (VarHandleDesc) DynamicConstantDesc.ofCanonical(ConstantDescs.BSM_VARHANDLE_FIELD, "f", CD_VarHandle, new ConstantDesc[] {testClass, CD_int });
        vh = vhd.resolveConstantDesc(LOOKUP);
        testVarHandleDesc(vhd, vh);

        dcd = DynamicConstantDesc.ofNamed(ConstantDescs.BSM_VARHANDLE_ARRAY, "_", CD_VarHandle, new ConstantDesc[] {CD_int.arrayType() });
        vh = dcd.resolveConstantDesc(LOOKUP);
        testVarHandleDesc(dcd, vh);

        vhd = (VarHandleDesc)DynamicConstantDesc.ofCanonical(ConstantDescs.BSM_VARHANDLE_ARRAY, "_", CD_VarHandle, new ConstantDesc[] {CD_int.arrayType() });
        vh = vhd.resolveConstantDesc(LOOKUP);
        testVarHandleDesc(vhd, vh);
    }

    private<T> void assertLifted(ConstantDesc prototype,
                                 DynamicConstantDesc<T> nonCanonical,
                                 ConstantDesc canonical) {
        Class<?> clazz = prototype.getClass();

        assertNotSame(canonical, nonCanonical);
        assertTrue(clazz.isAssignableFrom(canonical.getClass()));
        assertFalse(clazz.isAssignableFrom(nonCanonical.getClass()));
        assertEquals(canonical, prototype);
        assertEquals(prototype, canonical);
        if (prototype instanceof DynamicConstantDesc) {
            assertEquals(nonCanonical, canonical);
            assertEquals(canonical, nonCanonical);
            assertEquals(nonCanonical, prototype);
            assertEquals(prototype, nonCanonical);
        }
    }

    @Test
    public void testLifting() {
        DynamicConstantDesc<Object> unliftedNull = DynamicConstantDesc.ofNamed(ConstantDescs.BSM_NULL_CONSTANT, "_", CD_Object, EMPTY_ARGS);
        assertEquals(ConstantDescs.NULL, unliftedNull);
        assertNotSame(ConstantDescs.NULL, unliftedNull);
        assertSame(ConstantDescs.NULL, DynamicConstantDesc.ofCanonical(ConstantDescs.BSM_NULL_CONSTANT, "_", CD_Object, EMPTY_ARGS));
        assertSame(ConstantDescs.NULL, DynamicConstantDesc.ofCanonical(ConstantDescs.BSM_NULL_CONSTANT, "_", CD_String, EMPTY_ARGS));
        assertSame(ConstantDescs.NULL, DynamicConstantDesc.ofCanonical(ConstantDescs.BSM_NULL_CONSTANT, "wahoo", CD_Object, EMPTY_ARGS));

        assertLifted(CD_int,
                     DynamicConstantDesc.ofNamed(ConstantDescs.BSM_PRIMITIVE_CLASS, "I", ConstantDescs.CD_Class, EMPTY_ARGS),
                     DynamicConstantDesc.ofCanonical(ConstantDescs.BSM_PRIMITIVE_CLASS, "I", ConstantDescs.CD_Class, EMPTY_ARGS));

        ClassDesc enumClass = ClassDesc.of("CondyDescTest").nested("MyEnum");
        assertLifted(EnumDesc.of(enumClass, "A"),
                     DynamicConstantDesc.ofNamed(ConstantDescs.BSM_ENUM_CONSTANT, "A", enumClass, EMPTY_ARGS),
                     DynamicConstantDesc.<MyEnum>ofCanonical(ConstantDescs.BSM_ENUM_CONSTANT, "A", enumClass, EMPTY_ARGS));


        ClassDesc testClass = ClassDesc.of("CondyDescTest").nested("MyClass");

        assertLifted(VarHandleDesc.ofStaticField(testClass, "sf", CD_int),
                     DynamicConstantDesc.ofNamed(ConstantDescs.BSM_VARHANDLE_STATIC_FIELD, "sf", CD_VarHandle, new ConstantDesc[] {testClass, CD_int }),
                     DynamicConstantDesc.ofCanonical(ConstantDescs.BSM_VARHANDLE_STATIC_FIELD, "sf", CD_VarHandle, new ConstantDesc[] {testClass, CD_int }));
        assertLifted(VarHandleDesc.ofField(testClass, "f", CD_int),
                     DynamicConstantDesc.ofNamed(ConstantDescs.BSM_VARHANDLE_FIELD, "f", CD_VarHandle, new ConstantDesc[] {testClass, CD_int }),
                     DynamicConstantDesc.ofCanonical(ConstantDescs.BSM_VARHANDLE_FIELD, "f", CD_VarHandle, new ConstantDesc[] {testClass, CD_int }));

        assertLifted(VarHandleDesc.ofArray(CD_int.arrayType()),
                     DynamicConstantDesc.ofNamed(ConstantDescs.BSM_VARHANDLE_ARRAY, "_", CD_VarHandle, new ConstantDesc[] {CD_int.arrayType() }),
                     DynamicConstantDesc.ofCanonical(ConstantDescs.BSM_VARHANDLE_ARRAY, "_", CD_VarHandle, new ConstantDesc[] {CD_int.arrayType() }));
    }
}
