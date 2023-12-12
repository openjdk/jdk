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
 * @summary Verify function of LocalExecutionControlProvider createExecutionControl() method override
 * @library /tools/lib
 * @modules
 *      jdk.compiler/com.sun.tools.javac.api
 *      jdk.compiler/com.sun.tools.javac.main
 * @build toolbox.ToolBox toolbox.JavacTask LocalExecutionTestSupport
 * @run testng/othervm LocalExecutionContextLoaderParentTest
 */

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Locale;
import java.util.Map;
import java.nio.file.Files;

import jdk.jshell.execution.LocalExecutionControl;
import jdk.jshell.execution.LocalExecutionControlProvider;
import jdk.jshell.spi.ExecutionControl;
import jdk.jshell.spi.ExecutionControlProvider;
import jdk.jshell.spi.ExecutionEnv;

import org.testng.annotations.Test;
import org.testng.annotations.BeforeTest;

public class LocalExecutionContextLoaderParentTest extends LocalExecutionTestSupport {

    @BeforeTest
    public void installParentTestProvider() throws IOException {
        Path dir = createSubdir(classesDir, "META-INF/services");
        Files.write(dir.resolve(ExecutionControlProvider.class.getName()),
          Arrays.asList(ParentTestExecutionControlProvider.class.getName()));
    }

    @Override
    public void test(Locale locale, boolean defaultStartUp, String[] args, String startMsg, ReplTest... tests) {

        // Make test.MyClass visible to the context class loader
        final URL classesDirURL;
        try {
            classesDirURL = this.classesDir.toUri().toURL();
        } catch (MalformedURLException e) {
            throw new RuntimeException(e);
        }
        Thread.currentThread().setContextClassLoader(new URLClassLoader(new URL[] { classesDirURL }));

        // Set local execution with context class loader as parent loader
        args = this.prependArgs(args, "--execution", "parentTest");

        // Verify the execution engine can find MyClass (we don't care whether the compiler can find it in this test)
        super.test(locale, defaultStartUp, args, startMsg, tests);
    }

    @Test
    public void verifyMyClassFoundByExecutionEngine() {
        test(new String[] { "--no-startup" },
            a -> assertCommand(a, "Class.forName(\"test.MyClass\").getField(\"FOO\").get(null)", "$1 ==> \"bar\"")
        );
    }

// ParentTestExecutionControlProvider

    public static class ParentTestExecutionControlProvider extends LocalExecutionControlProvider {

        @Override
        public String name() {
            return "parentTest";
        }

        @Override
        public ExecutionControl createExecutionControl(ExecutionEnv env, Map<String, String> parameters) {
            return new LocalExecutionControl(Thread.currentThread().getContextClassLoader());
        }
    }
}
