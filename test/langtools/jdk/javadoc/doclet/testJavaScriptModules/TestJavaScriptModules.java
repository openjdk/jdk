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
 * @bug      8317621
 * @summary  --add-script should support JavaScript modules
 * @library /tools/lib ../../lib
 * @modules jdk.javadoc/jdk.javadoc.internal.tool
 * @build toolbox.ToolBox javadoc.tester.*
 * @run main TestJavaScriptModules
 */

import java.io.IOException;
import java.nio.file.Path;

import javadoc.tester.JavadocTester;
import toolbox.ToolBox;

public class TestJavaScriptModules extends JavadocTester {

    public static void main(String... args) throws Exception {
        var tester = new TestJavaScriptModules();
        tester.setup().runTests();
    }

    private final ToolBox tb = new ToolBox();
    Path src;

    TestJavaScriptModules setup() throws IOException {
        src = Path.of("src");
        tb.writeJavaFiles(src, """
                   /**
                    * Simple dummy class.
                    */
                   public class Test {}
                   """);
        tb.writeFile("module.mjs", """
                   var x = 1;
                   """);
        tb.writeFile("module1.js", """
                   const x = 1;
                   export class FooModule {}
                   """);
        tb.writeFile("module2.js", """
                   const x = 1;
                   export function f() {}
                   """);
        tb.writeFile("module3.js", """
                       const x = 1;
                       export async function a() {}
                   """);
        tb.writeFile("module4.js", """
                   // Another JS module
                   export const c = 3;
                   """);
        tb.writeFile("module5.js", """
                   export default class FooModule {}
                   """);
        tb.writeFile("module6.js", """
                   const x = 1;
                   export class FooModule {}
                   """);
        tb.writeFile("module7.js", """
                   function abc() {}
                   import * as foo from "module1.js";
                   """);
        tb.writeFile("module8.js", """
                   var z = false;
                   import { _A_, $b, C0 } from "abc.js";
                   """);
        tb.writeFile("script1.js", """
                   var z = false;
                   import(1, z);
                   """);
        tb.writeFile("script2.js", """
                   export("foo");
                   """);
        tb.writeFile("script3.js", """
                   var import = 1;
                   """);
        return this;
    }

    @Test
    public void test(Path base) {
        javadoc("-d", base.resolve("out").toString(),
                "--add-script", "module.mjs",
                "--add-script", "module1.js",
                "--add-script", "module2.js",
                "--add-script", "module3.js",
                "--add-script", "module4.js",
                "--add-script", "module5.js",
                "--add-script", "module6.js",
                "--add-script", "module7.js",
                "--add-script", "module8.js",
                "--add-script", "script1.js",
                "--add-script", "script2.js",
                "--add-script", "script3.js",
                src.resolve("Test.java").toString());
        checkExit(Exit.OK);

        checkOutput("Test.html", true,
                """
                    <script type="module" src="script-files/module.mjs"></script>""",
                """
                    <script type="module" src="script-files/module1.js"></script>""",
                """
                    <script type="module" src="script-files/module2.js"></script>""",
                """
                    <script type="module" src="script-files/module3.js"></script>""",
                """
                    <script type="module" src="script-files/module4.js"></script>""",
                """
                    <script type="module" src="script-files/module5.js"></script>""",
                """
                    <script type="module" src="script-files/module6.js"></script>""",
                """
                    <script type="module" src="script-files/module7.js"></script>""",
                """
                    <script type="module" src="script-files/module8.js"></script>""",
                """
                    <script type="text/javascript" src="script-files/script1.js"></script>""",
                """
                    <script type="text/javascript" src="script-files/script2.js"></script>""",
                """
                    <script type="text/javascript" src="script-files/script3.js"></script>""");
    }
}
