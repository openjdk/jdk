/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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
/**
 * @test
 * @bug 8276186
 * @summary Checks whether getAvailableLocales() returns at least Locale.ROOT and
 *      Locale.US instances.
 * @run testng/othervm -Djava.locale.providers=COMPAT RequiredAvailableLocalesTest
 * @run testng/othervm -Djava.locale.providers=CLDR RequiredAvailableLocalesTest
 */

import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.text.*;
import java.time.format.DecimalStyle;
import java.util.*;

import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import static org.testng.Assert.assertTrue;

@Test
public class RequiredAvailableLocalesTest {

    private static final Set<Locale> REQUIRED_LOCALES = Set.of(Locale.ROOT, Locale.US);
    private static final MethodType ARRAY_RETURN_TYPE = MethodType.methodType(Locale.class.arrayType());
    private static final MethodType SET_RETURN_TYPE = MethodType.methodType(Set.class);

    @DataProvider
    public Object[][] availableLocalesClasses() {
        return new Object[][] {
            {BreakIterator.class, ARRAY_RETURN_TYPE},
            {Calendar.class, ARRAY_RETURN_TYPE},
            {Collator.class, ARRAY_RETURN_TYPE},
            {DateFormat.class, ARRAY_RETURN_TYPE},
            {DateFormatSymbols.class, ARRAY_RETURN_TYPE},
            {DecimalFormatSymbols.class, ARRAY_RETURN_TYPE},
            {DecimalStyle.class, SET_RETURN_TYPE},
            {Locale.class, ARRAY_RETURN_TYPE},
            {NumberFormat.class, ARRAY_RETURN_TYPE},
        };
    }

    @Test (dataProvider = "availableLocalesClasses")
    public void checkRequiredLocales(Class<?> c, MethodType mt) throws Throwable {
        var ret = MethodHandles.lookup().findStatic(c, "getAvailableLocales", mt).invoke();

        if (ret instanceof Locale[] a) {
            assertTrue(Arrays.asList(a).containsAll(REQUIRED_LOCALES));
        } else if (ret instanceof Set<?> s) {
            assertTrue(s.containsAll(REQUIRED_LOCALES));
        } else {
            throw new RuntimeException("return type mismatch");
        }
    }
}
