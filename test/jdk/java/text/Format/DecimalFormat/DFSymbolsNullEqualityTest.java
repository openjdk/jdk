/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8341445
 * @summary Ensure that DFS with null instance variables do not throw
 *          NPE when compared against each other. Also provides a white list
 *          that forces new setter methods to be explicitly added if throwing NPE.
 * @run junit DFSymbolsNullEqualityTest
 */

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.text.DecimalFormatSymbols;
import java.util.Arrays;
import java.util.Currency;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

public class DFSymbolsNullEqualityTest {

    // This is a white list of DFS setter methods that are allowed to throw NPE
    private static final List<Method> NPE_SETTERS;
    private static final List<Method> NON_NPE_SETTERS;

    static {
        try {
            NPE_SETTERS = List.of(
                    // New NPE throwing setters MUST be added here
                    DecimalFormatSymbols.class.getMethod("setCurrency", Currency.class),
                    DecimalFormatSymbols.class.getMethod("setExponentSeparator", String.class)
            );
            NON_NPE_SETTERS = Arrays.stream(DecimalFormatSymbols.class.getDeclaredMethods())
                    .filter(m -> Modifier.isPublic(m.getModifiers()))
                    .filter(m -> m.getName().startsWith("set"))
                    .filter(m -> Stream.of(m.getParameterTypes()).noneMatch(Class::isPrimitive))
                    .filter(m -> NPE_SETTERS.stream().noneMatch(x -> x.equals(m))).toList();
        } catch (NoSuchMethodException e) {
            throw new RuntimeException("Unexpected test init failure");
        }
    }

    // If a future (NPE) setter method were to be added without being whitelisted,
    // this test would fail. This ensures that the implications of adding a new
    // setter method are fully understood in relation to the equals method
    @Test
    public void setterThrowsNPETest() {
        var dfs = new DecimalFormatSymbols();
        nonNPEThrowingSetters().forEach(m -> {
            try {
                m.invoke(dfs, (Object) null);
            } catch (InvocationTargetException e) {
                if (e.getCause() instanceof NullPointerException) {
                    throw new RuntimeException(String.format(
                            "DFS method: %s threw NPE, but was not added to whitelist", m));
                } else {
                    throw new RuntimeException(String.format(
                            "Unexpected exception: %s thrown for method: %s", e.getCause(), m));
                }
            } catch (IllegalAccessException e) {
                throw new RuntimeException(String.format(
                        "Test init failed, could not access method: %s", m));
            }
        });
    }

    // Two DFS instances with null(able) variables should be able to be
    // equality checked without throwing NPE
    @ParameterizedTest
    @MethodSource("nonNPEThrowingSetters")
    public void isEqualTest(Method m)
            throws InvocationTargetException, IllegalAccessException {
        var dfs = new DecimalFormatSymbols();
        var other = new DecimalFormatSymbols();
        m.invoke(dfs, (Object) null);
        m.invoke(other, (Object) null);
        assertEquals(dfs, other,
                "dfs and other should have compared as equal");
    }

    // Same as previous, but don't compare equal
    @ParameterizedTest
    @MethodSource("nonNPEThrowingSetters")
    public void nonEqualTest(Method m)
            throws InvocationTargetException, IllegalAccessException {
        var dfs = new DecimalFormatSymbols();
        var other = new DecimalFormatSymbols();
        // Use some arbitrary valid value of the expected param type
        var param = m.getParameterTypes()[0];
        if (param == String.class) {
            m.invoke(other, "foo");
        } else if (param == Currency.class) {
            m.invoke(other, Currency.getInstance(Locale.US));
        } else {
            throw new RuntimeException(
                    String.format("Unexpected param type: %s in method: %s.", param, m));
        }
        m.invoke(dfs, (Object) null);
        assertNotEquals(dfs, other,
                "dfs and other should not have compared as equal");
    }

    // All public setter methods that do not throw NPE and do not take primitives
    private static List<Method> nonNPEThrowingSetters() {
        return NON_NPE_SETTERS;
    }
}
