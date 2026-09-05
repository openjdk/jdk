/*
 * Copyright (c) 2017, 2026, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8186046
 * @summary Test bootstrap methods returning the wrong type
 * @library /java/lang/invoke/common
 * @build test.java.lang.invoke.lib.InstructionHelper
 * @run junit CondyWrongType
 * @run junit/othervm -XX:+UnlockDiagnosticVMOptions -XX:UseBootstrapCallInfo=3 CondyWrongType
 */

import test.java.lang.invoke.lib.InstructionHelper;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.invoke.WrongMethodTypeException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static java.lang.invoke.MethodType.methodType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import static org.junit.jupiter.api.Assertions.*;

public class CondyWrongType {

    public static Object[][] primitivesProvider() throws Exception {
        Map<String, Class<?>> typeMap = Map.of(
                "B", byte.class,
                "C", char.class,
                "D", double.class,
                "F", float.class,
                "I", int.class,
                "J", long.class,
                "S", short.class,
                "Z", boolean.class
        );

        List<Object[]> cases = new ArrayList<>();
        for (String name : typeMap.keySet()) {
            MethodHandle zero = MethodHandles.zero(typeMap.get(name));
            for (String type : typeMap.keySet()) {
                // Use asType transformation to detect if primitive conversion
                // is supported from the BSM value type to the dynamic constant type
                boolean pass = true;
                try {
                    zero.asType(MethodType.methodType(typeMap.get(type)));
                } catch (WrongMethodTypeException e) {
                    pass = false;
                }
                cases.add(new Object[]{name, type, pass});
            }
        }

        return cases.stream().toArray(Object[][]::new);
    }

    @ParameterizedTest
    @MethodSource("primitivesProvider")
    public void testPrimitives(String name, String type, boolean pass) {
        test(name, type, pass);
    }

    @Test
    public void testReferences() {
        test("String", "Ljava/math/BigDecimal;", false);
        test("BigDecimal", "Ljava/lang/String;", false);
    }

    @Test
    public void testReferenceAndPrimitives() {
        test("String", "B", false);
        test("String", "C", false);
        test("String", "D", false);
        test("String", "F", false);
        test("String", "I", false);
        test("String", "J", false);
        test("String", "S", false);
        test("String", "Z", false);
    }

    static void test(String name, String type, boolean pass) {
        MethodHandle mh = caster(name, type);
        if (pass) {
            assertDoesNotThrow(() -> mh.invoke());
        } else {
            Throwable caught = assertThrows(BootstrapMethodError.class, () -> mh.invoke());
            assertInstanceOf(ClassCastException.class, caught.getCause());
        }
    }

    static Object bsm(MethodHandles.Lookup l, String name, Class<?> type) {
        switch (name) {
            case "B":
                return (byte) 1;
            case "C":
                return 'A';
            case "D":
                return 1.0;
            case "F":
                return 1.0f;
            case "I":
                return 1;
            case "J":
                return 1L;
            case "S":
                return (short) 1;
            case "Z":
                return true;
            case "String":
                return "string";
            case "BigDecimal":
                return BigDecimal.ONE;
            default:
                throw new UnsupportedOperationException();
        }
    }

    static MethodHandle caster(String name, String type) {
        try {
            return InstructionHelper.ldcDynamicConstant(
                    MethodHandles.lookup(),
                    name, type,
                    "bsm",
                    methodType(Object.class, MethodHandles.Lookup.class, String.class, Class.class).descriptorString());
        } catch (Exception e) {
            throw new Error(e);
        }
    }
}
