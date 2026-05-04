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
 * @summary DFS setters should throw NPE. This ensures that NPE is not thrown
 *          by equals().
 * @run junit SettersShouldThrowNPETest
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

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class SettersShouldThrowNPETest {

    // The public setter methods that should throw NPE
    private static final List<Method> NPE_SETTERS =
            Arrays.stream(DecimalFormatSymbols.class.getDeclaredMethods())
            .filter(m -> Modifier.isPublic(m.getModifiers())
                    && m.getName().startsWith("set")
                    && Stream.of(m.getParameterTypes()).noneMatch(Class::isPrimitive))
            .toList();

    // Non-primitive setters should throw NPE
    @ParameterizedTest
    @MethodSource("setters")
    public void settersThrowNPETest(Method m) {
        var dfs = new DecimalFormatSymbols();
        InvocationTargetException e =
                assertThrows(InvocationTargetException.class, () -> m.invoke(dfs, (Object) null));
        if (!(e.getCause() instanceof NullPointerException)) {
            throw new RuntimeException(e.getCause() + " was thrown instead of NPE by : " + m);
        }
    }

    // Currency fields are lazy and can be null
    // Ensure when exposed to users, they are never null
    @ParameterizedTest
    @MethodSource("locales")
    public void lazyCurrencyFieldsTest(Locale locale) {
        var dfs = new DecimalFormatSymbols(locale);
        assertDoesNotThrow(() -> dfs.equals(new DecimalFormatSymbols()));
        assertNotNull(dfs.getCurrency());
        assertNotNull(dfs.getInternationalCurrencySymbol());
        assertNotNull(dfs.getCurrencySymbol());
    }

    // Prior to 8341445, if the international currency symbol was invalid,
    // the currency attribute was set to null. However, we should not have null
    // currency fields post initializeCurrency() call. Ensure invalid code
    // does not update the other fields.
    @Test
    public void setInternationalCurrencySymbolFallbackTest() {
        var code = "fooBarBazQux";
        // initialize() should provide null for all currency related fields
        var dfs = new DecimalFormatSymbols(Locale.ROOT);
        // Load the fallbacks via initCurrency() since the loc is Locale.ROOT
        dfs.setInternationalCurrencySymbol(code); // set invalid code
        // Ensure our values are the expected fallbacks, minus the updated intl code
        assertEquals(Currency.getInstance("XXX"), dfs.getCurrency());
        assertEquals("\u00A4", dfs.getCurrencySymbol());
        assertEquals("fooBarBazQux", dfs.getInternationalCurrencySymbol());
    }

    private static List<Method> setters() {
        return NPE_SETTERS;
    }

    private static List<Locale> locales() {
        return List.of(Locale.ROOT, Locale.US, Locale.forLanguageTag("XXX"));
    }
}
