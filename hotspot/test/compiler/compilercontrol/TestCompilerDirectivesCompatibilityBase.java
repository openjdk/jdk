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
 * @test TestCompilerDirectivesCompatibilityBase
 * @bug 8137167
 * @library /testlibrary /../../test/lib
 * @modules java.base/sun.misc
 *          java.compiler
 *          java.management
 * @build jdk.test.lib.*
 * @build jdk.test.lib.dcmd.*
 * @build sun.hotspot.WhiteBox
 * @run main ClassFileInstaller sun.hotspot.WhiteBox
 *                              sun.hotspot.WhiteBox$WhiteBoxPermission
 * @run testng/othervm -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI TestCompilerDirectivesCompatibilityBase
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

public class TestCompilerDirectivesCompatibilityBase {

    public static final WhiteBox WB = WhiteBox.getWhiteBox();
    public static String control_on, control_off;
    Method method, nomatch;

    public void run(CommandExecutor executor) throws Exception {

        control_on = System.getProperty("test.src", ".") + File.separator + "control_on.txt";
        control_off = System.getProperty("test.src", ".") + File.separator + "control_off.txt";
        method  = getMethod(TestCompilerDirectivesCompatibilityBase.class, "helper");
        nomatch = getMethod(TestCompilerDirectivesCompatibilityBase.class, "another");

        testCompatibility(executor);
    }

    public void testCompatibility(CommandExecutor executor) throws Exception {

        // Call all validation twice to catch error when overwriting a directive
        // Flag is default off
        expect(!WB.getBooleanVMFlag("PrintAssembly"));
        expect(!WB.shouldPrintAssembly(method));
        expect(!WB.shouldPrintAssembly(nomatch));
        expect(!WB.shouldPrintAssembly(method));
        expect(!WB.shouldPrintAssembly(nomatch));

        // load directives that turn it on
        executor.execute("Compiler.directives_add " + control_on);
        expect(WB.shouldPrintAssembly(method));
        expect(!WB.shouldPrintAssembly(nomatch));
        expect(WB.shouldPrintAssembly(method));
        expect(!WB.shouldPrintAssembly(nomatch));

        // remove and see that it is true again
        executor.execute("Compiler.directives_remove");
        expect(!WB.shouldPrintAssembly(method));
        expect(!WB.shouldPrintAssembly(nomatch));
        expect(!WB.shouldPrintAssembly(method));
        expect(!WB.shouldPrintAssembly(nomatch));
    }

    public void expect(boolean test) throws Exception {
        if (!test) {
            throw new Exception("Test failed");
        }
    }

    public void expect(boolean test, String msg) throws Exception {
        if (!test) {
            throw new Exception(msg);
        }
    }

    @Test
    public void jmx() throws Exception {
        run(new JMXExecutor());
    }

    public void helper() {
    }

    public void another() {
    }

    public static Method getMethod(Class klass, String name, Class<?>... parameterTypes) {
        try {
            return klass.getDeclaredMethod(name, parameterTypes);
        } catch (NoSuchMethodException | SecurityException e) {
            throw new RuntimeException("exception on getting method Helper." + name, e);
        }
    }
}
