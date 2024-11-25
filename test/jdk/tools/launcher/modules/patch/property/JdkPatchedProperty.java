/*
 * Copyright (c) 2024, Red Hat, Inc.
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

import java.lang.reflect.Constructor;

/*
 * @test id=unpatched
 * @summary Test property jdk.patched for unpatched runtime
 * @run main/othervm JdkPatchedProperty false
 */

/*
 * @test id=unpatched_override_cli
 * @summary Test CLI override of property jdk.patched for unpatched runtime
 * @run main/othervm -Djdk.patched=true JdkPatchedProperty false
 */

/*
 * @test id=patched
 * @summary Test property jdk.patched for patched runtime
 * @compile --patch-module java.base=${test.src}/patch/java/lang
 *          ${test.src}/patch/java/lang/TestInteger.java JdkPatchedProperty.java
 * @run main/othervm --patch-module=java.base=${test.classes} JdkPatchedProperty true
 */

/*
 * @test id=patched_override_cli
 * @summary Test CLI override of property jdk.patched for patched runtime
 * @compile --patch-module java.base=${test.src}/patch/java/lang
 *          ${test.src}/patch/java/lang/TestInteger.java JdkPatchedProperty.java
 * @run main/othervm --patch-module=java.base=${test.classes} -Djdk.patched=false JdkPatchedProperty true
 */
public class JdkPatchedProperty {

    private static final String PATCHED_PROPERTY_NAME = "jdk.patched";

    private final boolean expectPatched;

    public JdkPatchedProperty(boolean expectPatched) {
        this.expectPatched = expectPatched;
    }

    public void runTest() throws Exception {
        boolean actual = Boolean.getBoolean(PATCHED_PROPERTY_NAME);
        if (expectPatched) {
            // Verify we find the TestInteger class from the module patch
            Class<?> testInt = Class.forName("java.lang.TestInteger");
            Constructor<?> cons = testInt.getDeclaredConstructor();
            Object i = cons.newInstance();
            System.out.println("Found integer class from module patch: " + i.getClass());
        }
        assertEquals(actual, expectPatched);
    }

    private static void assertEquals(boolean actual, boolean expected) {
        if (actual != expected) {
            String msg = "Expected " + (expected ? "patched" : "unpatched") +
                         " runtime but detected " + (actual ? "patched" : "unpatched") +
                         " runtime via property " + PATCHED_PROPERTY_NAME;
            throw new AssertionError(msg);
        }
    }

    public void runTestCode() throws Exception {
        // The System class happily lets you change JDK properties
        System.setProperty(PATCHED_PROPERTY_NAME, Boolean.valueOf(!expectPatched).toString());
        boolean actual = Boolean.getBoolean(PATCHED_PROPERTY_NAME);
        assertEquals(actual, !expectPatched);

        // clear properties, which gets expected values from the VM
        System.setProperties(null);
        actual = Boolean.getBoolean(PATCHED_PROPERTY_NAME);
        assertEquals(actual, expectPatched);
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            throw new RuntimeException("Invalid test setup. Expected a single boolean argument");
        }
        boolean expectPatched = Boolean.parseBoolean(args[0]);
        JdkPatchedProperty t = new JdkPatchedProperty(expectPatched);
        t.runTest();
        t.runTestCode();
    }

}
