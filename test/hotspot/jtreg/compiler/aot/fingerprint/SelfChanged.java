/*
 * Copyright (c) 2016, 2019, Oracle and/or its affiliates. All rights reserved.
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
 * @summary AOT methods should be swept if a super class has changed.
 * @library /test/lib /
 * @modules java.base/jdk.internal.misc
 *          java.management
 * @requires vm.aot
 * @build compiler.aot.fingerprint.SelfChanged
 *        compiler.aot.AotCompiler
 *
 * @run main
 *      compiler.aot.fingerprint.SelfChanged WRITE-UNMODIFIED-CLASS
 * @run driver compiler.aot.AotCompiler -libname libSelfChanged.so
 *      -class compiler.aot.fingerprint.Blah
 *
 * @run main/othervm
 *      compiler.aot.fingerprint.SelfChanged TEST-UNMODIFIED
 * @run main/othervm -XX:+UnlockExperimentalVMOptions -XX:+UseAOT -XX:+PrintAOT
 *      -XX:AOTLibrary=./libSelfChanged.so
 *      -Xlog:aot+class+fingerprint=trace -Xlog:aot+class+load=trace
 *      compiler.aot.fingerprint.SelfChanged TEST-UNMODIFIED
 *
 * @run main
 *      compiler.aot.fingerprint.SelfChanged WRITE-MODIFIED-CLASS
 * @run main
 *      compiler.aot.fingerprint.SelfChanged TEST-MODIFIED
 * @run main/othervm -XX:+UnlockExperimentalVMOptions -XX:+UseAOT -XX:+PrintAOT
 *      -XX:AOTLibrary=./libSelfChanged.so
 *      -Xlog:aot+class+fingerprint=trace -Xlog:aot+class+load=trace
 *      compiler.aot.fingerprint.SelfChanged TEST-MODIFIED
 */

package compiler.aot.fingerprint;

import jdk.test.lib.Asserts;
import jdk.test.lib.compiler.InMemoryJavaCompiler;

import java.io.*;

class Blah {
    volatile int z;
    int getX() {
        for (z = 0; z < 10000; z++) {
            if (z % 7 == 1) {
                z += 2;
            }
        }
        return 0;
    }
}

public class SelfChanged {
    public static void main(String args[]) throws Throwable {
        Blah f = new Blah();
        System.out.println("f.getX = " + f.getX());
        switch (args[0]) {
        case "WRITE-UNMODIFIED-CLASS":
            compileClass(false);
            break;
        case "WRITE-MODIFIED-CLASS":
            compileClass(true);
            break;
        case "TEST-UNMODIFIED":
            Asserts.assertTrue(f.getX() == 0, "getX from unmodified Blah class should return 0");
            break;
        case "TEST-MODIFIED":
            Asserts.assertTrue(f.getX() == 1, "getX from modified Blah class should return 1");
            break;
        default:
            throw new RuntimeException("unexpected option: " + args[0]);
        }
    }

    static void compileClass(boolean isModified) throws Throwable {
        String src =
               "package compiler.aot.fingerprint;"
             + "public class Blah {"
             + "    volatile int z;"
             + "    int getX() {"
             + "        for (z = 0; z < 10000; z++) {"
             + "            if (z % 7 == 1) {"
             + "                z += 2;"
             + "            }"
             + "        }"
             + "        return " + ((isModified) ? "1" : "0") + ";"
             + "    }"
             + "    int getY() {return 255;}"

            // The following is for the SelfChangedCDS.java test case. We always load an unmodified
            // version of Blah from the CDS archive. However, we would load an AOT library that
            // was compiled using a modified version of Blah. The getX method in this AOT library should
            // not be used.

            + "    public static void main(String args[]) {"
             + "        Blah b = new Blah();"
             + "        int n = b.getX();"
             + "        if (n != 0) {"
             + "            throw new RuntimeException(args[0] +  \" : \" + n);"
             + "        }"
             + "        System.out.println(\"PASSED\");"
             + "    }"
             + "}";

        String filename = System.getProperty("test.classes") + "/compiler/aot/fingerprint/Blah.class";
        FileOutputStream fos = new FileOutputStream(filename);
        fos.write(InMemoryJavaCompiler.compile("compiler.aot.fingerprint.Blah", src));
        fos.close();
    }
}
