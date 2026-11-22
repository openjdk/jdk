/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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

import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.function.Predicate;

import jdk.incubator.vector.Float16;
import jdk.incubator.vector.Vector;
import jdk.incubator.vector.VectorMask;
import jdk.incubator.vector.VectorOperators;
import jdk.incubator.vector.VectorShape;
import jdk.incubator.vector.VectorSpecies;

import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

/*
 * @test
 * @bug 8389844 8391307
 * @modules jdk.incubator.vector
 * @run testng VectorLanewiseOpCompatibleWithTest
 */

public class VectorLanewiseOpCompatibleWithTest {
    private static final List<Class<?>> ELEMENT_TYPES = List.of(
            byte.class,
            short.class,
            int.class,
            long.class,
            Float16.class,
            float.class,
            double.class);

    private static final List<VectorOperators.Operator> OPERATORS = vectorOperators();

    private static List<VectorOperators.Operator> vectorOperators() {
        List<VectorOperators.Operator> operators = new ArrayList<>();
        for (var field : VectorOperators.class.getFields()) {
            if (Modifier.isStatic(field.getModifiers()) &&
                VectorOperators.Operator.class.isAssignableFrom(field.getType())) {
                try {
                    operators.add((VectorOperators.Operator) field.get(null));
                } catch (ReflectiveOperationException e) {
                    throw new AssertionError(e);
                }
            }
        }
        operators.sort(Comparator.comparing(VectorOperators.Operator::name));
        return List.copyOf(operators);
    }

    private static Object[][] operatorProvider(boolean compatible,
                                               Predicate<VectorOperators.Operator> opFilter) {
        return ELEMENT_TYPES.stream()
                .flatMap(elementType -> Arrays.stream(VectorShape.values())
                        .map(shape -> VectorSpecies.of(elementType, shape)))
                .flatMap(species -> OPERATORS.stream()
                        // These operators are more restrictive, exclude for now.
                        .filter(op -> op != VectorOperators.COMPRESS_BITS &&
                                      op != VectorOperators.EXPAND_BITS)
                        .filter(opFilter)
                        .filter(op -> op.compatibleWith(species.elementType()) == compatible)
                        .map(op -> new Object[] {species, op}))
                .toArray(Object[][]::new);
    }

    private static Object[][] operatorProvider(boolean compatible) {
        return operatorProvider(compatible,
                                op -> op instanceof VectorOperators.Unary ||
                                      op instanceof VectorOperators.Binary ||
                                      op instanceof VectorOperators.Ternary ||
                                      op instanceof VectorOperators.Test);
    }

    @DataProvider
    public Object[][] unsupportedOperatorProvider() {
        return operatorProvider(false);
    }

    @DataProvider
    public Object[][] supportedOperatorProvider() {
        return operatorProvider(true);
    }

    @Test(dataProvider = "unsupportedOperatorProvider")
    public <E> void testUnsupportedOperator(VectorSpecies<E> species,
                                            VectorOperators.Operator op) {
        Vector<E> vector = species.zero();
        VectorMask<E> mask = species.maskAll(false);

        switch (op) {
            case VectorOperators.Unary unary -> {
                Assert.assertThrows(UnsupportedOperationException.class,
                        () -> vector.lanewise(unary));
                Assert.assertThrows(UnsupportedOperationException.class,
                        () -> vector.lanewise(unary, mask));
            }
            case VectorOperators.Binary binary -> {
                Assert.assertThrows(UnsupportedOperationException.class,
                        () -> vector.lanewise(binary, vector));
                Assert.assertThrows(UnsupportedOperationException.class,
                        () -> vector.lanewise(binary, 0L));
                Assert.assertThrows(UnsupportedOperationException.class,
                        () -> vector.lanewise(binary, vector, mask));
                Assert.assertThrows(UnsupportedOperationException.class,
                        () -> vector.lanewise(binary, 0L, mask));
            }
            case VectorOperators.Ternary ternary -> {
                Assert.assertThrows(UnsupportedOperationException.class,
                        () -> vector.lanewise(ternary, vector, vector));
                Assert.assertThrows(UnsupportedOperationException.class,
                        () -> vector.lanewise(ternary, vector, vector, mask));
            }
            case VectorOperators.Test test -> {
                Assert.assertThrows(UnsupportedOperationException.class,
                        () -> vector.test(test));
                Assert.assertThrows(UnsupportedOperationException.class,
                        () -> vector.test(test, mask));
            }
            default -> throw new AssertionError("Not a lanewise operator: " + op);
        }
    }

    @Test(dataProvider = "supportedOperatorProvider")
    public <E> void testSupportedOperator(VectorSpecies<E> species,
                                          VectorOperators.Operator op) {
        Vector<E> vector = species.zero().broadcast(1L);
        VectorMask<E> mask = species.maskAll(true);

        switch (op) {
            case VectorOperators.Unary unary -> {
                vector.lanewise(unary);
                vector.lanewise(unary, mask);
            }
            case VectorOperators.Binary binary -> {
                vector.lanewise(binary, vector);
                vector.lanewise(binary, 1L);
                vector.lanewise(binary, vector, mask);
                vector.lanewise(binary, 1L, mask);
            }
            case VectorOperators.Ternary ternary -> {
                vector.lanewise(ternary, vector, vector);
                vector.lanewise(ternary, vector, vector, mask);
            }
            case VectorOperators.Test test -> {
                vector.test(test);
                vector.test(test, mask);
            }
            default -> throw new AssertionError("Not a lanewise operator: " + op);
        }
    }
}
