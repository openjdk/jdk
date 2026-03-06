/*
 * Copyright (c) 2025, 2026, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8353487
 * @summary Verify local execution engine supports decorating the snippet execution
 * @library /tools/lib
 * @modules
 *      jdk.compiler/com.sun.tools.javac.api
 *      jdk.compiler/com.sun.tools.javac.main
 * @build toolbox.ToolBox toolbox.JavacTask LocalExecutionTestSupport
 * @run testng/othervm LocalDecorateExecutionTest
 */

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
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

import java.util.Locale;

import org.testng.annotations.Test;
import org.testng.annotations.BeforeTest;

public class LocalDecorateExecutionTest extends LocalExecutionTestSupport {

    @BeforeTest
    public void installExecutionControlProvider() throws Exception {
        Path dir = createSubdir(classesDir, "META-INF/services");
        Files.write(dir.resolve(ExecutionControlProvider.class.getName()),
          Arrays.asList(LocalDecorateExecutionControlProvider.class.getName()));
    }

    @Override
    public void test(Locale locale, boolean defaultStartUp, String[] args, String startMsg, ReplTest... tests) {

        // Make test classes visible to the context class loader
        final URL classesDirURL;
        try {
            classesDirURL = classesDir.toUri().toURL();
        } catch (MalformedURLException e) {
            throw new RuntimeException(e);
        }
        Thread.currentThread().setContextClassLoader(new URLClassLoader(new URL[] { classesDirURL }));

        // Proceed
        super.test(locale, defaultStartUp, args, startMsg, tests);
    }

    @Test
    public void verifyDecoration() throws Exception {

        // Configure an execution control that put "foobar" in context during snippet execution
        String spec = String.format("%s:%s(%s)",
          LocalDecorateExecutionControlProvider.NAME, LocalDecorateExecutionControlProvider.CONTEXT_PARAM, "foobar");

        // Verify the snippet can access that thread-local context
        test(new String[] { "--no-startup", "--execution", spec },
          a -> assertCommand(a,
            "ThreadLocal.class.getMethod(\"get\").invoke(Class.forName(\"LocalDecorateExecutionTest$LocalDecorateExecutionControlProvider\").getField(\"CONTEXT_VALUE\").get(null))",
            "$1 ==> \"foobar\"")
        );
    }

// LocalDecorateExecutionControlProvider

    public static class LocalDecorateExecutionControlProvider extends LocalExecutionControlProvider {

        public static final String NAME = "localDecorate";
        public static final String CONTEXT_PARAM = "context";
        public static final ThreadLocal<String> CONTEXT_VALUE = new ThreadLocal<>();

        @Override
        public Map<String, String> defaultParameters() {
            return Map.of(CONTEXT_PARAM, "");
        }

        @Override
        public String name() {
            return NAME;
        }

        @Override
        public ExecutionControl createExecutionControl(ExecutionEnv env, Map<String, String> parameters) {
            final String contextString = parameters.get(CONTEXT_PARAM);
            return new LocalExecutionControl(Thread.currentThread().getContextClassLoader()) {
                @Override
                protected Object doInvoke(Method method) throws IllegalAccessException, InvocationTargetException {
                    CONTEXT_VALUE.set(contextString);
                    try {
                        return method.invoke(null);
                    } finally {
                        CONTEXT_VALUE.set(null);
                    }
                }
            };
        }
    }
}
