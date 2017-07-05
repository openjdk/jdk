/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
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
 * @test TestCompilerDirectivesCompatibilityFlag
 * @bug 8137167
 * @library /testlibrary /test/lib /
 * @modules java.base/jdk.internal.misc
 *          java.compiler
 *          java.management
 * @build jdk.test.lib.*
 *        jdk.test.lib.dcmd.*
 *        sun.hotspot.WhiteBox
 *        compiler.testlibrary.CompilerUtils
 * @run driver ClassFileInstaller sun.hotspot.WhiteBox
 *                                sun.hotspot.WhiteBox$WhiteBoxPermission
 * @run testng/othervm -Xbootclasspath/a:. -Xmixed -XX:+UnlockDiagnosticVMOptions
 *      -XX:+PrintAssembly -XX:+WhiteBoxAPI TestCompilerDirectivesCompatibilityFlag
 * @summary Test compiler control compatibility with compile command
 */

import jdk.test.lib.dcmd.CommandExecutor;
import jdk.test.lib.dcmd.JMXExecutor;

import org.testng.annotations.Test;
import org.testng.Assert;

import sun.hotspot.WhiteBox;

import java.io.BufferedReader;
import java.io.File;
import java.io.StringReader;
import java.lang.reflect.Method;
import java.util.Objects;

public class TestCompilerDirectivesCompatibilityFlag extends TestCompilerDirectivesCompatibilityBase {

    public void testCompatibility(CommandExecutor executor, int comp_level) throws Exception {

        // Call all validation twice to catch error when overwriting a directive
        // Flag is default on
        expect(WB.getBooleanVMFlag("PrintAssembly"));
        expect(WB.shouldPrintAssembly(method, comp_level));
        expect(WB.shouldPrintAssembly(nomatch, comp_level));
        expect(WB.shouldPrintAssembly(method, comp_level));
        expect(WB.shouldPrintAssembly(nomatch, comp_level));

        // load directives that turn it off
        executor.execute("Compiler.directives_add " + control_off);
        expect(!WB.shouldPrintAssembly(method, comp_level));
        expect(WB.shouldPrintAssembly(nomatch, comp_level));
        expect(!WB.shouldPrintAssembly(method, comp_level));
        expect(WB.shouldPrintAssembly(nomatch, comp_level));

        // remove and see that it is true again
        executor.execute("Compiler.directives_remove");
        expect(WB.shouldPrintAssembly(method, comp_level));
        expect(WB.shouldPrintAssembly(nomatch, comp_level));
        expect(WB.shouldPrintAssembly(method, comp_level));
        expect(WB.shouldPrintAssembly(nomatch, comp_level));
    }
}
