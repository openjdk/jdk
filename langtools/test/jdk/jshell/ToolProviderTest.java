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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.ServiceLoader;
import java.util.function.Consumer;
import javax.tools.Tool;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

/*
 * @test
 * @bug 8170044 8171343
 * @summary Test ServiceLoader launching of jshell tool
 * @modules jdk.compiler/com.sun.tools.javac.api
 *          jdk.compiler/com.sun.tools.javac.main
 *          jdk.jdeps/com.sun.tools.javap
 *          jdk.jshell/jdk.internal.jshell.tool
 * @library /tools/lib
 * @build Compiler toolbox.ToolBox
 * @run testng ToolProviderTest
 */
@Test
public class ToolProviderTest extends StartOptionTest {

    private ByteArrayOutputStream cmdout;
    private ByteArrayOutputStream cmderr;
    private InputStream cmdInStream;

    @BeforeMethod
    @Override
    public void setUp() {
        cmdout = new ByteArrayOutputStream();
        cmderr = new ByteArrayOutputStream();
        cmdInStream = new ByteArrayInputStream("/exit\n".getBytes());
    }

    @Override
    protected void start(Consumer<String> checkCmdOutput,
            Consumer<String> checkUserOutput, Consumer<String> checkError,
            String... args) throws Exception {
        if (runShellServiceLoader(args) != 0) {
            fail("Repl tool failed");
        }
        check(cmdout, checkCmdOutput, "cmdout");
        check(cmderr, checkError, "cmderr");
    }

    private int runShellServiceLoader(String... args) {
        ServiceLoader<Tool> sl = ServiceLoader.load(Tool.class);
        for (Tool provider : sl) {
            if (provider.name().equals("jshell")) {
                return provider.run(cmdInStream, cmdout, cmderr, args);
            }
        }
        throw new AssertionError("Repl tool not found by ServiceLoader: " + sl);
    }

    @Override
    public void testCommandFile() throws Exception {
        String fn = writeToFile("String str = \"Hello \"\n/list\nSystem.out.println(str + str)\n/exit\n");
        start("1 : String str = \"Hello \";" + "\n" + "Hello Hello", "", "--no-startup", fn, "-s");
    }

    @Override
    public void testShowVersion() throws Exception {
        start(
                s -> {
                    assertTrue(s.startsWith("jshell "), "unexpected version: " + s);
                    assertTrue(s.contains("Welcome"), "Expected start (but got no welcome): " + s);
                    assertTrue(s.trim().contains("jshell>"), "Expected prompt, got: " + s);
                },
                null, null,
                "--show-version");
    }
}
