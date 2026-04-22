/*
 * Copyright (c) 2024, 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8339296
 * @summary Verify that the UnknownType behaves as an erroneous type
 * @library /tools/lib/types
 * @modules jdk.compiler/com.sun.tools.javac.code
 *          jdk.compiler/com.sun.tools.javac.comp
 *          jdk.compiler/com.sun.tools.javac.file
 *          jdk.compiler/com.sun.tools.javac.util
 *          jdk.compiler/com.sun.tools.javac.main
 *          jdk.compiler/com.sun.tools.javac.tree
 * @build TypeHarness
 * @run main UnknownTypeTest
 */

import com.sun.tools.javac.code.Type;
import com.sun.tools.javac.code.Symtab;
import com.sun.tools.javac.code.Type.TypeVar;
import com.sun.tools.javac.code.Type.UnionClassType;
import com.sun.tools.javac.util.List;
import java.util.Objects;

public class UnknownTypeTest extends TypeHarness {

    private final Type error;
    private final Type unknown;
    private final Type[] testTypes;
    private final Operation[] testOperations;

    UnknownTypeTest() {
        Symtab syms = Symtab.instance(context);
        error = syms.errType;
        unknown = syms.unknownType;
        testTypes = new Type[] {
            syms.objectType,
            syms.errType,
            syms.unknownType,
            new TypeVar(syms.unknownSymbol, syms.objectType, syms.objectType),
            types.makeIntersectionType(List.of(syms.annotationType, syms.stringType)),
            new UnionClassType((Type.ClassType) syms.annotationType, List.of(syms.stringType)),
            syms.intType,
            types.makeArrayType(syms.stringType)
        };
        testOperations = new Operation[] {
            types::containedBy,
            types::containsType,
            types::isAssignable,
            types::isCastable,
            types::isConvertible,
            types::isSameType,
            types::isSubtype,
            types::isSuperType,
            types::isUnconditionallyExactValueBased,
            types::isUnconditionallyExactTypeBased,
            types::isUnconditionallyExactCombined,
            (t1, _) -> types.isArray(t1),
            (t1, _) -> types.isDerivedRaw(t1),
            (t1, _) -> types.isReifiable(t1),
            (t1, _) -> types.isUnbounded(t1),
            (t1, _) -> types.boxedTypeOrType(t1),
            (t1, _) -> types.unboxedType(t1),
            (t1, _) -> types.unboxedTypeOrType(t1),
        };
    }

    void test(Type[] testTypes, Operation[] testOperations) {
        for (int typeIndex = 0; typeIndex < testTypes.length ; typeIndex++) {
            for (int operationIndex = 0; operationIndex < testOperations.length ; operationIndex++) {
                Object expected;
                Object actual;
                expected = testOperations[operationIndex].run(error, testTypes[typeIndex]);
                actual = testOperations[operationIndex].run(unknown, testTypes[typeIndex]);
                checkEquals("Type index: " + typeIndex + ", operationIndex: " + operationIndex + ", unknown in the first position", expected, actual);
                expected = testOperations[operationIndex].run(testTypes[typeIndex], error);
                actual = testOperations[operationIndex].run(testTypes[typeIndex], unknown);
                checkEquals("Type index: " + typeIndex + ", operationIndex: " + operationIndex + ", unknown in the second position", expected, actual);
            }
        }
    }

    void checkEquals(String message, Object expected, Object actual) {
        boolean matches;

        if (expected instanceof Type t1 && actual instanceof Type t2) {
            matches = types.isSameType(t1, t2);
        } else {
            matches = Objects.equals(expected, actual);
        }

        if (!matches) {
            throw new AssertionError("Unexpected outcome: " + actual +
                                     ", expected: " + expected +
                                     ", for test: " + message);
        }
    }

    void runTests() {
        test(testTypes, testOperations);
    }

    public static void main(String[] args) {
        new UnknownTypeTest().runTests();
    }

    interface Operation {
        public Object run(Type t1, Type t2);
    }
}
