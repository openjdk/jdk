/*
 * Copyright (c) 2017, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8046171
 * @summary Setup nestmate calls to private methods then use
 *          modified jcod classes to introduce errors. Test with
 *          and without verification enabled
 * @compile TestInvokeErrors.java
 * @compile MissingMethod.jcod
 *          MissingMethodWithSuper.jcod
 *          MissingNestHost.jcod
 * @run main TestInvokeErrors true
 * @run main/othervm -Xverify:none TestInvokeErrors false
 */

public class TestInvokeErrors {

    static class Nested {
        private void priv_invoke() {
            System.out.println("Nested::priv_invoke");
        }
    }

    static class MissingMethod {
        // jcod version will rename this method to not_priv_invoke
        private void priv_invoke() {
            System.out.println("MissingMethod::priv_invoke");
        }
    }

    static class MissingMethodWithSuper extends Nested {
        // jcod version will rename this method to not_priv_invoke
        private void priv_invoke() {
            System.out.println("MissingMethodWithSuper::priv_invoke");
        }
    }

    static class MissingNestHost {
        // jcod version will change NestHost to a non-existent class
        private void priv_invoke() {
            System.out.println("MissingNestHost::priv_invoke");
        }
    }

    // Helper class adds a level of indirection to avoid the main class
    // failing verification if these tests are written directly in main.
    // That can only happen if using invokespecial for nestmate invocation.
    static class Helper {
        static void doTest() {
            try {
                MissingNestHost m = new MissingNestHost();
                m.priv_invoke();
                throw new Error("Unexpected success invoking MissingNestHost.priv_invoke");
            }
            catch (NoClassDefFoundError ncdfe) {
                System.out.println("Got expected exception:" + ncdfe);
            }
        }
    }

    public static void main(String[] args) throws Throwable {
        // some errors change depending on whether they are caught by the
        // verifier first
        boolean verifying = Boolean.parseBoolean(args[0]);
        System.out.println("Verification is " +
                           (verifying ? "enabled" : "disabled"));

        try {
            MissingMethod m = new MissingMethod();
            m.priv_invoke();
            throw new Error("Unexpected success invoking MissingMethod.priv_invoke");
        }
        catch (NoSuchMethodError nsme) {
            System.out.println("Got expected exception:" + nsme);
        }

        try {
            MissingMethodWithSuper m = new MissingMethodWithSuper();
            m.priv_invoke();
            throw new Error("Unexpected success invoking MissingMethodWithSuper.priv_invoke");
        }
        catch (NoSuchMethodError nsme) {
            System.out.println("Got expected exception:" + nsme);
        }

        // Verification of Helper will trigger the nestmate access check failure
        try {
            Helper.doTest();
        }
        catch (NoClassDefFoundError ncdfe) {
            if (verifying)
                System.out.println("Got expected exception:" + ncdfe);
            else
                throw new Error("Unexpected error loading Helper class with verification disabled");
        }
    }
}
