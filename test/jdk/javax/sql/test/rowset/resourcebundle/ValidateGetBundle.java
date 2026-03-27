/*
 * Copyright (c) 2022, 2026, Oracle and/or its affiliates. All rights reserved.
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
package test.rowset.resourcebundle;

import com.sun.rowset.JdbcRowSetResourceBundle;

import java.util.Locale;
import java.util.MissingResourceException;
import java.util.ResourceBundle;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.Arguments;

/**
 * @test
 * @bug 8294989
 * @summary Check JDBC RowSet resource bundle access
 */
public class ValidateGetBundle{

    // Data provider for testResourceBundleAccess
    private static Stream<Arguments> bundleProvider() {
        return Stream.of(
                // The resource bundle should be found with the fully qualified class name
                Arguments.of("com.sun.rowset.RowSetResourceBundle", true),
                // The resource bundle will not be found when the path is specified
                Arguments.of("com/sun/rowset/RowSetResourceBundle", false)
        );
    }

    /**
     * Test to validate whether the JDBC RowSet Resource bundle can be found
     * @param bundleName the base name of the resource bundle
     * @param expectBundle indicates whether the resource bundle should be found
     */
    @ParameterizedTest
    @MethodSource("bundleProvider")
    void testResourceBundleAccess(String bundleName, boolean expectBundle) {
        try {
            var bundle = ResourceBundle.getBundle(bundleName,
                    Locale.US, JdbcRowSetResourceBundle.class.getModule());
            if (!expectBundle) {
                throw new RuntimeException(
                        String.format("$$$ Error: '%s' shouldn't have loaded!%n", bundleName));
            }
            System.out.printf("$$$ %s was found as expected!%n", bundleName);
        } catch (MissingResourceException mr) {
            if (expectBundle) {
                throw new RuntimeException(
                        String.format("$$$ Error: '%s' should have loaded!", bundleName), mr);
            }
            System.out.printf("$$$ %s was not found as expected!", bundleName);
        }
    }
}
