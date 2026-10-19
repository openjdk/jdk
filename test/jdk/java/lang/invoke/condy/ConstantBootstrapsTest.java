/*
 * Copyright (c) 2017, 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8186046 8195694 8241100 8364751
 * @summary Test dynamic constant bootstraps
 * @library /java/lang/invoke/common
 * @build test.java.lang.invoke.lib.InstructionHelper
 * @run junit ConstantBootstrapsTest
 * @run junit/othervm -XX:+UnlockDiagnosticVMOptions -XX:UseBootstrapCallInfo=3 ConstantBootstrapsTest
 */

import java.lang.constant.ConstantDescs;
import java.lang.constant.DirectMethodHandleDesc;
import java.lang.constant.MethodHandleDesc;
import java.lang.invoke.ConstantBootstraps;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.invoke.VarHandle;
import java.lang.invoke.WrongMethodTypeException;
import java.math.BigInteger;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import test.java.lang.invoke.lib.InstructionHelper;

import static org.junit.jupiter.api.Assertions.*;

public class ConstantBootstrapsTest {
    static final MethodHandles.Lookup L = MethodHandles.lookup();

    static MethodType lookupMT(Class<?> ret, Class<?>... params) {
        return MethodType.methodType(ret, MethodHandles.Lookup.class, String.class, Class.class).
                appendParameterTypes(params);
    }

    @Test
    public void testNullConstant() throws Throwable {
        var handle = InstructionHelper.ldcDynamicConstant(L, "_", Object.class,
                ConstantBootstraps.class, "nullConstant", lookupMT(Object.class));
        assertNull(handle.invoke());

        handle = InstructionHelper.ldcDynamicConstant(L, "_", MethodType.class,
                ConstantBootstraps.class, "nullConstant", lookupMT(Object.class));
        assertNull(handle.invoke());
    }

    @Test
    public void testNullConstantPrimitiveClass() {
        assertThrows(IllegalArgumentException.class, () -> ConstantBootstraps.nullConstant(MethodHandles.lookup(), null, int.class));
    }


    @Test
    public void testPrimitiveClass() throws Throwable {
        var pm = Map.of(
                "I", int.class,
                "J", long.class,
                "S", short.class,
                "B", byte.class,
                "C", char.class,
                "F", float.class,
                "D", double.class,
                "Z", boolean.class,
                "V", void.class
        );

        for (var desc : pm.keySet()) {
            var handle = InstructionHelper.ldcDynamicConstant(L, desc, Class.class,
                    ConstantBootstraps.class, "primitiveClass", lookupMT(Class.class));
            assertEquals(pm.get(desc), handle.invoke());
        }
    }

    @Test
    public void testPrimitiveClassNullName() {
        assertThrows(NullPointerException.class, () -> ConstantBootstraps.primitiveClass(MethodHandles.lookup(), null, Class.class));
    }

    @Test
    public void testPrimitiveClassNullType() {
        assertThrows(NullPointerException.class, () -> ConstantBootstraps.primitiveClass(MethodHandles.lookup(), "I", null));
    }

    @Test
    public void testPrimitiveClassEmptyName() {
        assertThrows(IllegalArgumentException.class, () -> ConstantBootstraps.primitiveClass(MethodHandles.lookup(), "", Class.class));
    }

    @Test
    public void testPrimitiveClassWrongNameChar() {
        assertThrows(IllegalArgumentException.class, () -> ConstantBootstraps.primitiveClass(MethodHandles.lookup(), "L", Class.class));
    }

    @Test
    public void testPrimitiveClassWrongNameString() {
        assertThrows(IllegalArgumentException.class, () -> ConstantBootstraps.primitiveClass(MethodHandles.lookup(), "Ljava/lang/Object;", Class.class));
    }


    @Test
    public void testEnumConstant() throws Throwable {
        for (var v : StackWalker.Option.values()) {
            var handle = InstructionHelper.ldcDynamicConstant(L, v.name(), StackWalker.Option.class,
                    ConstantBootstraps.class, "enumConstant", lookupMT(Enum.class));
            assertEquals(v, handle.invoke());
        }
    }

    @Test
    public void testEnumConstantUnknown() {
        assertThrows(IllegalArgumentException.class, () -> ConstantBootstraps.enumConstant(MethodHandles.lookup(), "DOES_NOT_EXIST", StackWalker.Option.class));
    }


    @Test
    public void testGetStaticDecl() throws Throwable {
        var handle = InstructionHelper.ldcDynamicConstant(L, "TYPE", Class.class,
                ConstantBootstraps.class, "getStaticFinal", lookupMT(Object.class, Class.class),
                InstructionHelper.classDesc(Integer.class));
        assertEquals(int.class, handle.invoke());
    }

    @Test
    public void testGetStaticSelf() throws Throwable {
        var handle = InstructionHelper.ldcDynamicConstant(L, "MAX_VALUE", int.class,
                ConstantBootstraps.class, "getStaticFinal", lookupMT(Object.class));
        assertEquals(Integer.MAX_VALUE, handle.invoke());


        handle = InstructionHelper.ldcDynamicConstant(L, "ZERO", BigInteger.class,
                ConstantBootstraps.class, "getStaticFinal", lookupMT(Object.class));
        assertEquals(BigInteger.ZERO, handle.invoke());
    }


    @Test
    public void testInvoke() throws Throwable {
        var handle = InstructionHelper.ldcDynamicConstant(
                L, "_", List.class,
                ConstantBootstraps.class, "invoke", lookupMT(Object.class, MethodHandle.class, Object[].class),
                MethodHandleDesc.of(DirectMethodHandleDesc.Kind.INTERFACE_STATIC, ConstantDescs.CD_List, "of",
                        MethodType.methodType(List.class, Object[].class).toMethodDescriptorString()),
                1, 2, 3, 4
        );
        assertEquals(List.of(1, 2, 3, 4), handle.invoke());
    }

    @Test
    public void testInvokeAsType() throws Throwable {
        var handle = InstructionHelper.ldcDynamicConstant(
                L, "_", int.class,
                ConstantBootstraps.class, "invoke", lookupMT(Object.class, MethodHandle.class, Object[].class),
                MethodHandleDesc.of(DirectMethodHandleDesc.Kind.STATIC, ConstantDescs.CD_Integer, "valueOf",
                        MethodType.methodType(Integer.class, String.class).toMethodDescriptorString()),
                "42"
        );
        assertEquals(42, handle.invoke());
    }

    @Test
    public void testInvokeAsTypeVariableArity() throws Throwable {
        // The constant type is Collection but the invoke return type is List
        var handle = InstructionHelper.ldcDynamicConstant(
                L, "_", Collection.class,
                ConstantBootstraps.class, "invoke", lookupMT(Object.class, MethodHandle.class, Object[].class),
                MethodHandleDesc.of(DirectMethodHandleDesc.Kind.INTERFACE_STATIC, ConstantDescs.CD_List, "of",
                        MethodType.methodType(List.class, Object[].class).toMethodDescriptorString()),
                1, 2, 3, 4
        );
        assertEquals(List.of(1, 2, 3, 4), handle.invoke());
    }

    @Test
    public void testInvokeAsTypeClassCast() throws Throwable {
        assertThrows(ClassCastException.class, () -> ConstantBootstraps.invoke(MethodHandles.lookup(), "_", String.class,
                MethodHandles.lookup().findStatic(Integer.class, "valueOf", MethodType.methodType(Integer.class, String.class)),
                "42"));
    }

    @Test
    public void testInvokeAsTypeWrongReturnType() throws Throwable {
        assertThrows(WrongMethodTypeException.class, () -> ConstantBootstraps.invoke(MethodHandles.lookup(), "_", short.class,
                MethodHandles.lookup().findStatic(Integer.class, "parseInt", MethodType.methodType(int.class, String.class)),
                "42"));
    }


    static class X {
        public String f;
        public static String sf;
    }

    @Test
    public void testVarHandleField() throws Throwable {
        var handle = InstructionHelper.ldcDynamicConstant(
                L, "f", VarHandle.class,
                ConstantBootstraps.class, "fieldVarHandle", lookupMT(VarHandle.class, Class.class, Class.class),
                InstructionHelper.classDesc(X.class),
                InstructionHelper.classDesc(String.class)
        );

        var vhandle = (VarHandle) handle.invoke();
        assertEquals(String.class, vhandle.varType());
        assertEquals(List.of(X.class), vhandle.coordinateTypes());
    }

    @Test
    public void testVarHandleStaticField() throws Throwable {
        var handle = InstructionHelper.ldcDynamicConstant(
                L, "sf", VarHandle.class,
                ConstantBootstraps.class, "staticFieldVarHandle", lookupMT(VarHandle.class, Class.class, Class.class),
                InstructionHelper.classDesc(X.class),
                InstructionHelper.classDesc(String.class)
        );

        var vhandle = (VarHandle) handle.invoke();
        assertEquals(String.class, vhandle.varType());
        assertEquals(List.of(), vhandle.coordinateTypes());
    }

    @Test
    public void testVarHandleArray() throws Throwable {
        var handle = InstructionHelper.ldcDynamicConstant(
                L, "_", VarHandle.class,
                ConstantBootstraps.class, "arrayVarHandle", lookupMT(VarHandle.class, Class.class),
                InstructionHelper.classDesc(String[].class)
        );

        var vhandle = (VarHandle) handle.invoke();
        assertEquals(String.class, vhandle.varType());
        assertEquals(List.of(String[].class, int.class), vhandle.coordinateTypes());
    }

    public static Object[][] cceCasts() {
        return new Object[][]{
                { void.class, null },
                { Integer.class, "a" },
                { int.class, BigInteger.ZERO },
        };
    }

    @ParameterizedTest
    @MethodSource("cceCasts")
    public void testBadCasts(Class<?> dstType, Object value) {
        assertThrows(ClassCastException.class, () -> ConstantBootstraps.explicitCast(null, null, dstType, value));
    }

    public static Object[][] validCasts() {
        Object o = new Object();
        return new Object[][]{
                { Object.class, null, null },
                { Object.class, o, o },
                { String.class, "abc", "abc" },
                { short.class, 10, (short) 10 },
                { int.class, (short) 10, 10 },
                { boolean.class, 1, true },
                { boolean.class, 2, false },
                { int.class, true, 1 },
                { int.class, false, 0 },
                { int.class, 10, 10 },
                { Integer.class, 10, 10 },
                { Object.class, 10, 10 },
                { Number.class, 10, 10 },
                { char.class, null, (char) 0 }
        };
    }

    @ParameterizedTest
    @MethodSource("validCasts")
    public void testSuccessfulCasts(Class<?> dstType, Object value, Object expected) {
        Object actual = ConstantBootstraps.explicitCast(null, null, dstType, value);
        assertEquals(expected, actual);
    }
}
