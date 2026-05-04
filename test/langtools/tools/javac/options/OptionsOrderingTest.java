/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8347474
 * @summary Verify -XDrawDiagnostics flag is picked up by JavacMessages singleton
 * @library /tools/lib
 * @modules
 *  jdk.compiler/com.sun.tools.javac.api
 *  jdk.compiler/com.sun.tools.javac.file
 *  jdk.compiler/com.sun.tools.javac.main
 *  jdk.compiler/com.sun.tools.javac.util:+open
 */

import java.io.PrintWriter;
import java.io.Writer;
import java.lang.reflect.Field;
import java.net.URI;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Objects;

import com.sun.tools.javac.file.JavacFileManager;
import com.sun.tools.javac.main.Main;
import com.sun.tools.javac.util.Context;
import com.sun.tools.javac.util.JavacMessages;
import com.sun.tools.javac.util.RawDiagnosticFormatter;

import toolbox.JavacTask;
import toolbox.TestRunner;
import toolbox.ToolBox;

public class OptionsOrderingTest extends TestRunner {

    protected final ToolBox tb;

    public OptionsOrderingTest() {
        super(System.err);
        tb = new ToolBox();
    }

    public void testJavacMessagesDiagFormatter() throws Exception {

        // Write source file
        Path dir = Paths.get(getClass().getSimpleName());
        tb.writeJavaFiles(dir, "class Test { }");

        // Run the compiler where we supply the Context
        Context context = new Context();
        JavacFileManager.preRegister(context);
        Main compiler = new Main("javac", new PrintWriter(Writer.nullWriter()));
        String[] args = new String[] {
          "-XDrawDiagnostics",
          tb.findJavaFiles(dir)[0].toString()
        };
        Main.Result result = compiler.compile(args, context);

        // Verify field JavacMessages.diagFormatter is a RawDiagnosticFormatter
        JavacMessages messages = JavacMessages.instance(context);
        Field diagFormatterField = messages.getClass().getDeclaredField("diagFormatter");
        diagFormatterField.setAccessible(true);
        Class<?> diagFormatterClass = diagFormatterField.get(messages).getClass();
        if (!Objects.equals(diagFormatterClass, RawDiagnosticFormatter.class)) {
            throw new AssertionError(String.format(
              "diagFormatter: expected %s but found %s",
              RawDiagnosticFormatter.class, diagFormatterClass));
        }
    }

    public static void main(String... args) throws Exception {
        new OptionsOrderingTest().testJavacMessagesDiagFormatter();
    }
}
