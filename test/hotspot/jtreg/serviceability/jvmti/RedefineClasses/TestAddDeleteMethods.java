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
 * @bug 8192936
 * @summary RI does not follow the JVMTI RedefineClasses spec; need to disallow adding and deleting methods
 * @library /test/lib
 * @modules java.base/jdk.internal.misc
 * @modules java.compiler
 *          java.instrument
 *          jdk.jartool/sun.tools.jar
 * @run main RedefineClassHelper
 * @run main/othervm -javaagent:redefineagent.jar TestAddDeleteMethods
 */

import static jdk.test.lib.Asserts.assertEquals;

// package access top-level class to avoid problem with RedefineClassHelper
// and nested types.
class A {
    private static void foo()       { System.out.println("OLD foo called"); }
    private final  void finalFoo()  { System.out.println("OLD finalFoo called"); }
    public         void publicFoo() { foo(); finalFoo(); }
}

public class TestAddDeleteMethods {
    static A a;

    public static String newA =
        "class A {" +
            "private static void foo()       { System.out.println(\"NEW foo called\"); }" +
            "private final  void finalFoo()  { System.out.println(\"NEW finalFoo called\"); }" +
            "public         void publicFoo() { foo(); finalFoo(); }" +
        "}";

    public static String newAddBar =
        "class A {" +
            "private        void bar()       { System.out.println(\"NEW bar called\"); }" +
            "private static void foo()       { System.out.println(\"NEW foo called\"); }" +
            "private final  void finalFoo()  { System.out.println(\"NEW finalFoo called\"); }" +
            "public         void publicFoo() { foo(); bar(); finalFoo(); }" +
        "}";

    public static String newAddFinalBar =
        "class A {" +
            "private final  void bar()       { System.out.println(\"NEW bar called\"); }" +
            "private static void foo()       { System.out.println(\"NEW foo called\"); }" +
            "private final  void finalFoo()  { System.out.println(\"NEW finalFoo called\"); }" +
            "public         void publicFoo() { foo(); bar(); finalFoo(); }" +
        "}";

    public static String newAddPublicBar =
        "class A {" +
            "public         void bar()       { System.out.println(\"NEW public bar called\"); }" +
            "private static void foo()       { System.out.println(\"NEW foo called\"); }" +
            "private final  void finalFoo()  { System.out.println(\"NEW finalFoo called\"); }" +
            "public         void publicFoo() { foo(); bar(); finalFoo(); }" +
        "}";

    public static String newDeleteFoo =
        "class A {" +
            "private final  void finalFoo()  { System.out.println(\"NEW finalFoo called\"); }" +
            "public         void publicFoo() { finalFoo(); }" +
        "}";

    public static String newDeleteFinalFoo =
        "class A {" +
            "private static void foo()       { System.out.println(\"NEW foo called\"); }" +
            "public         void publicFoo() { foo(); }" +
        "}";

    public static String newDeletePublicFoo =
        "class A {" +
            "private static void foo()       { System.out.println(\"NEW foo called\"); }" +
            "private final  void finalFoo()  { System.out.println(\"NEW finalFoo called\"); }" +
        "}";

    static private final String ExpMsgPrefix = "attempted to ";
    static private final String ExpMsgPostfix = " a method";

    public static void test(String newBytes, String expSuffix) throws Exception {
        String expectedMessage = ExpMsgPrefix + expSuffix + ExpMsgPostfix;

        try {
            RedefineClassHelper.redefineClass(A.class, newBytes);
            a.publicFoo();
            throw new RuntimeException("Failed, expected UOE");
        } catch (UnsupportedOperationException uoe) {
            String message = uoe.getMessage();
            System.out.println("Got expected UOE " + message);
            if (!message.endsWith(expectedMessage)) {
                throw new RuntimeException("Expected UOE error message to end with: " + expectedMessage);
            }
        }
    }

    static {
        a = new A();
    }

    public static void main(String[] args) throws Exception {

        a.publicFoo();

        // Should pass because this only changes bytes of methods.
        RedefineClassHelper.redefineClass(A.class, newA);
        a.publicFoo();

        // Add private static bar
        test(newAddBar,          "add");
        test(newAddFinalBar,     "add");
        test(newAddPublicBar,    "add");
        test(newDeleteFoo,       "delete");
        test(newDeleteFinalFoo,  "delete");
        test(newDeletePublicFoo, "delete");
    }
}
