/*
 * Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8081800
 * @summary Redefine private and default interface methods
 * @library /test/lib
 * @modules java.base/jdk.internal.misc
 * @modules java.compiler
 *          java.instrument
 *          jdk.jartool/sun.tools.jar
 * @run main RedefineClassHelper
 * @run main/othervm -javaagent:redefineagent.jar -Xlog:redefine+class*=trace RedefineInterfaceMethods
 */
public class RedefineInterfaceMethods {

    static final int RET = -2;

    static interface B {
        int ORIGINAL_RETURN = 1;
        int NEW_RETURN = 2;
        private int privateMethod() {
            return ORIGINAL_RETURN;
        }
        public default int defaultMethod() {
            return privateMethod();
        }
    }

    public static String redefinedPrivateMethod =
        "interface RedefineInterfaceMethods$B {" +
        "    int ORIGINAL_RETURN = 1;" +
        "    int NEW_RETURN = 2;" +
        "    private int privateMethod() {" +
        "        return NEW_RETURN;" +
        "    }" +
        "    public default int defaultMethod() {" +
        "       return privateMethod();" +
        "    }" +
        "}";

    public static String redefinedDefaultMethod =
        "interface RedefineInterfaceMethods$B {" +
        "    int ORIGINAL_RETURN = 1;" +
        "    int NEW_RETURN = 2;" +
        "    private int privateMethod() {" +
        "        return ORIGINAL_RETURN;" +
        "    }" +
        "    public default int defaultMethod() {" +
        "       return RedefineInterfaceMethods.RET;" +
        "    }" +
        "}";

    static class Impl implements B {
    }


    public static void main(String[] args) throws Exception {

        Impl impl = new Impl();

        int res = impl.defaultMethod();
        if (res != B.ORIGINAL_RETURN)
            throw new Error("defaultMethod returned " + res +
                            " expected " + B.ORIGINAL_RETURN);

        RedefineClassHelper.redefineClass(B.class, redefinedPrivateMethod);

        res = impl.defaultMethod();
        if (res != B.NEW_RETURN)
            throw new Error("defaultMethod returned " + res +
                            " expected " + B.NEW_RETURN);

        System.gc();

        RedefineClassHelper.redefineClass(B.class, redefinedDefaultMethod);

        res = impl.defaultMethod();
        if (res != RET)
            throw new Error("defaultMethod returned " + res +
                            " expected " + RET);
    }
}
