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
 * @bug 8324651
 * @summary Support for derived record creation expression in JShell
 * @library /tools/lib
 * @modules jdk.compiler/com.sun.tools.javac.api
 *          jdk.compiler/com.sun.tools.javac.main
 *          jdk.jshell
 * @build Compiler KullaTesting TestingInputStream ExpectedDiagnostic
 * @run testng DerivedRecordCreationTest
 */

import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;


@Test
public class DerivedRecordCreationTest extends KullaTesting {

    public void derivedRecordInVarInit() {
        assertEval("record A(int i) {}");
        assertEval("var v1 = new A(0);");
        assertEval("var v2 = v1 with { i = -1; };");
        assertEval("v2", "A[i=-1]");
    }

    public void derivedRecordInClass() {
        assertEval("record A(int i) {}");
        assertEval("""
                   class Test {
                       public static A test(A arg) {
                           A a = arg with {
                               i = 32;
                           };
                           return a;
                       }
                   }
                   """);
    }

    @BeforeMethod
    public void setUp() {
        setUp(bc -> bc.compilerOptions("--enable-preview",
                                       "--source", System.getProperty("java.specification.version"))
                      .remoteVMOptions("--enable-preview"));
    }

}
