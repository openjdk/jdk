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
 * @bug 8314327
 * @summary Verify the "--class-path" flag works properly in local execution mode
 * @library /tools/lib
 * @modules
 *      jdk.compiler/com.sun.tools.javac.api
 *      jdk.compiler/com.sun.tools.javac.main
 * @build toolbox.ToolBox toolbox.JavacTask LocalExecutionTestSupport
 * @run testng/othervm LocalExecutionClassPathTest
 */

import java.util.Locale;
import org.testng.annotations.Test;

public class LocalExecutionClassPathTest extends LocalExecutionTestSupport {

    @Override
    public void test(Locale locale, boolean defaultStartUp, String[] args, String startMsg, ReplTest... tests) {

        // Set local execution with context class loader
        args = this.prependArgs(args,
          "--execution", "local",
          "--class-path", this.classesDir.toString());

        // Verify MyClass can be found by both the compiler and the execution engine
        super.test(locale, defaultStartUp, args, startMsg, tests);
    }

    @Test
    public void verifyMyClassFoundOnClassPath() {
        test(new String[] { "--no-startup" },
            a -> assertCommand(a, "test.MyClass.class", "$1 ==> class test.MyClass")
        );
    }
}
