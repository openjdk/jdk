/*
 * Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8181171
 * @summary Test deleting static method pointing to by a jmethod
 * @library /test/lib
 * @modules java.base/jdk.internal.misc
 * @modules java.compiler
 *          java.instrument
 *          jdk.jartool/sun.tools.jar
 * @run main RedefineClassHelper
 * @run main/native/othervm -javaagent:redefineagent.jar -XX:+AllowRedefinitionToAddDeleteMethods -Xlog:redefine+class*=trace RedefineDeleteJmethod
 */

class B {
    private static int deleteMe() { System.out.println("deleteMe called"); return 5; }
    public static int callDeleteMe() { return deleteMe(); }
}

public class RedefineDeleteJmethod {

    public static String newB =
        "class B {" +
            "public static int callDeleteMe() { return 6; }" +
        "}";

    public static String newerB =
        "class B {" +
            "private static int deleteMe() { System.out.println(\"deleteMe (2) called\"); return 7; }" +
            "public static int callDeleteMe() { return deleteMe(); }" +
        "}";


    static {
        System.loadLibrary("RedefineDeleteJmethod");
    }

    static native int jniCallDeleteMe();

    static void test(int expected, boolean nsme_expected) throws Exception {
        // Call through static method
        int res = B.callDeleteMe();
        System.out.println("Result = " + res);
        if (res != expected) {
            throw new Error("returned " + res + " expected " + expected);
        }

        // Call through jmethodID, saved from first call.
        try {
            res = jniCallDeleteMe();
            if (nsme_expected) {
                throw new RuntimeException("Failed, NoSuchMethodError expected");
            }
            if (res != expected) {
                throw new Error("returned " + res + " expected " + expected);
            }
        } catch (NoSuchMethodError ex) {
            if (!nsme_expected) {
                throw new RuntimeException("Failed, NoSuchMethodError not expected");
            }
            System.out.println("Passed, NoSuchMethodError expected");
        }
    }

    public static void main(String[] args) throws Exception {
        test(5, false);
        RedefineClassHelper.redefineClass(B.class, newB);
        test(6, true);
        RedefineClassHelper.redefineClass(B.class, newerB);
        test(7, true);
    }
}
