/*
 * Copyright (c) 2010, 2021, Oracle and/or its affiliates. All rights reserved.
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
 * @key randomness
 * @modules java.base/jdk.internal.misc
 *
 * @summary converted from VM Testbase vm/mlvm/anonloader/func/classNameInStackTrace.
 * VM Testbase keywords: [feature_mlvm]
 * VM Testbase readme:
 * DESCRIPTION
 *     An exception is thrown by a hidden class.  Verify that the exception's
 *     stack trace contains name of the current test class (i.e., verify
 *     that the stack trace is not broken)
 *
 * @library /vmTestbase
 *          /test/lib
 *
 * @comment build test class and indify classes
 * @build vm.mlvm.anonloader.func.classNameInStackTrace.Test
 * @run driver vm.mlvm.share.IndifiedClassesBuilder
 *
 * @run main/othervm vm.mlvm.anonloader.func.classNameInStackTrace.Test
 */

package vm.mlvm.anonloader.func.classNameInStackTrace;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodHandles.Lookup;

import jdk.test.lib.compiler.InMemoryJavaCompiler;

import vm.mlvm.share.MlvmTest;

public class Test extends MlvmTest {

    public boolean run() throws Exception {
        String newClass =
            "package vm.mlvm.anonloader.func.classNameInStackTrace; " +
            "public class HashThrow { " +
            "    @Override " +
            "    public int hashCode() { " +
            "        throw new RuntimeException(\"Making fun of errors\"); " +
            "    } }";
        byte[] classBytes = InMemoryJavaCompiler.compile(
            "vm.mlvm.anonloader.func.classNameInStackTrace.HashThrow", newClass);
        Lookup lookup = MethodHandles.lookup();
        Class<?> cls = lookup.defineHiddenClass(classBytes, true).lookupClass();
        try {
            // hashCode() throws an exception
            cls.newInstance().hashCode();
            return false;

        } catch ( RuntimeException e ) {
            ByteArrayOutputStream byteOS = new ByteArrayOutputStream();
            PrintStream printStream = new PrintStream(byteOS);
            e.printStackTrace(printStream);
            printStream.close();
            String stackTrace = byteOS.toString("ASCII");
            getLog().display("Caught exception stack trace: " + stackTrace);
            return stackTrace.contains(Test.class.getName());
        }
    }

    public static void main(String[] args) { MlvmTest.launch(args); }
}
