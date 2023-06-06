/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 9999999
 * @summary Tests for unnamed variables
 * @library /tools/lib
 * @modules jdk.compiler/com.sun.tools.javac.api
 *          jdk.compiler/com.sun.tools.javac.main
 *          jdk.jshell
 * @build Compiler KullaTesting TestingInputStream ExpectedDiagnostic
 * @run testng UnnamedTest
 */

import java.util.function.Consumer;
import jdk.jshell.VarSnippet;
import org.testng.Assert;
import org.testng.annotations.Test;

import jdk.jshell.JShell;

public class UnnamedTest extends KullaTesting {

    @Test
    public void unnamed() {
        VarSnippet sn1 = varKey(assertEval("int _ = 0;"));
        VarSnippet sn2 = varKey(assertEval("String _ = \"x\";"));
        Assert.assertEquals(getState().varValue(sn1), "0");
        Assert.assertEquals(getState().varValue(sn2), "\"x\"");
    }

    @Override
    public void setUp(Consumer<JShell.Builder> bc) {
        super.setUp(bc.andThen(b -> b.compilerOptions("--enable-preview", "--source", System.getProperty("java.specification.version"))));
    }
}
