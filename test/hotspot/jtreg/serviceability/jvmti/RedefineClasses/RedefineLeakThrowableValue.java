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
 * @bug 8336829
 * @library /test/lib
 * @summary Test that redefinition of a value class containing Throwable refs does not leak constant pool
 * @enablePreview
 * @modules java.base/jdk.internal.misc
 * @modules java.instrument
 *          java.compiler
 * @run main RedefineClassHelper
 * @run main/othervm/timeout=6000 --enable-preview -Xlog:class+load,gc+metaspace+freelist+oom:rt.log -javaagent:redefineagent.jar -XX:MetaspaceSize=23m -XX:MaxMetaspaceSize=23m RedefineLeakThrowableValue
 */

import jdk.test.lib.compiler.InMemoryJavaCompiler;

value class Tester {
    void test() {
        try {
            int i = 42;
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }
}

public class RedefineLeakThrowableValue {

    static final String NEW_TESTER =
        "value class Tester {" +
        "   void test() {" +
        "        try {" +
        "            int i = 42;" +
        "        } catch (Throwable t) {" +
        "            t.printStackTrace();" +
        "        }" +
        "    }" +
        "}";


    public static void main(String argv[]) throws Exception {
        String java_version = System.getProperty("java.specification.version");
        // Load InMemoryJavaCompiler outside the redefinition loop to prevent random lambdas that load and fill up metaspace.
        byte[] bytecodes = InMemoryJavaCompiler.compile("Tester", NEW_TESTER,
                                                        "-source", java_version, "--enable-preview",
                                                        "--add-exports", "java.base/jdk.internal.vm.annotation=ALL-UNNAMED");
        for (int i = 0; i < 700; i++) {
            RedefineClassHelper.redefineClass(Tester.class, bytecodes);
        }
    }
}
