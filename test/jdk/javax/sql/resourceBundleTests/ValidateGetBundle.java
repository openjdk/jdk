/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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

import com.sun.rowset.JdbcRowSetResourceBundle;

import java.util.Locale;
import java.util.MissingResourceException;
import java.util.ResourceBundle;

/**
 * @test
 * @bug 8294989
 * @summary Check JDBC RowSet resource bundle access
 * @modules java.sql.rowset/com.sun.rowset
 * @run main/othervm --add-opens java.sql.rowset/com.sun.rowset=ALL-UNNAMED ValidateGetBundle
 */
public class ValidateGetBundle{

    // Resource bundle base name via a fully qualified class name
    private static final String FULLY_QUALIFIED_CLASS_NAME = "com.sun.rowset.RowSetResourceBundle";
    // Resource bundle base name via a path
    private static final String PATH_TO_BUNDLE = "com/sun/rowset/RowSetResourceBundle";

    public static void main(String[] args) {
        // The resource bundle should be found with the fully qualified class name
        testResourceBundleAccess(FULLY_QUALIFIED_CLASS_NAME, true);
        // The resource bundle will not be found when the path is specified
        testResourceBundleAccess(PATH_TO_BUNDLE, false);
    }

    /**
     * Test to validate whether the JDBC RowSet Resource bundle can be found
     * @param bundleName the base name of the resource bundle
     * @param expectBundle indicates whether the resource bundle should be found
     */
    private static void testResourceBundleAccess(String bundleName, boolean expectBundle) {
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
