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
 *          NPE when compared against each other.
 * @run junit DFSymbolsNullEqualityTest
 */

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.text.DecimalFormatSymbols;
import java.util.Arrays;
import java.util.Currency;
import java.util.Locale;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

public class DFSymbolsNullEqualityTest {

    // Two DFS instances with null(able) variables should be able to be
    // equality checked without throwing NPE
    @ParameterizedTest
    @MethodSource("setters")
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
    @MethodSource("setters")
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

    // All public setter methods that do not throw NPE AND do not take primitives
    private static Stream<Method> setters() {
        var dfs = new DecimalFormatSymbols();
        return Arrays.stream(DecimalFormatSymbols.class.getDeclaredMethods())
                .filter(m -> Modifier.isPublic(m.getModifiers()))
                .filter(m -> m.getName().startsWith("set"))
                .filter(m -> {
                    for (Class<?> c : m.getParameterTypes()) {
                        if (c.isPrimitive()) return false;
                    }
                    return true;
                })
                .filter(m -> {
                    try {
                        m.invoke(dfs, (Object) null);
                    } catch (InvocationTargetException e) {
                        if (e.getCause() instanceof NullPointerException) {
                            return false;
                        } else {
                            throw new RuntimeException(String.format(
                                    "Test init failed, unexpected exception: %s thrown for method: %s", e.getCause(), m));
                        }
                    } catch (IllegalAccessException e) {
                        throw new RuntimeException(String.format(
                                "Test init failed, could not access method: %s", m));
                    }
                    return true;
                });
    }
}
