/*
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
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
  * @bug 8197785
  * @summary Tests if AccessibleBundle will reload the ResourceBundle for every
  * call to toDisplayString.
  * @run main AccessibilityBundleMemoryLeakTest
  */
import java.lang.reflect.Field;
import java.util.Hashtable;
import java.util.Locale;

import javax.accessibility.AccessibleRole;
import javax.accessibility.AccessibleBundle;

public class AccessibilityBundleMemoryLeakTest extends AccessibleRole {
    public AccessibilityBundleMemoryLeakTest() {
        super("");
    }

    public static void main(String... args) throws Exception {
        AccessibilityBundleMemoryLeakTest role = new AccessibilityBundleMemoryLeakTest();

        Field field = AccessibleBundle.class.getDeclaredField("table");
        field.setAccessible(true);

        final Hashtable table = (Hashtable)field.get(role);
        Locale locale = Locale.getDefault();

        role.toDisplayString();
        Object obj = table.get(locale);

        role.toDisplayString();
        Object obj1 = table.get(locale);

        if (obj != obj1) {
            throw new RuntimeException("Test case failed: AccessibleBundle allocates new value for existing key!");
        }

        System.out.println("Test passed.");

    }
}
