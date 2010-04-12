/*
 * Copyright 2010 Sun Microsystems, Inc.  All Rights Reserved.
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
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 */

/**
 * @test
 * @bug 6930108
 * @summary IllegalArgumentException in AbstractDiagnosticFormatter for tools/javac/api/TestJavacTaskScanner.java
 * @library ./lib
 * @build ToolTester
 * @run main TestResolveError
 */

import java.io.*;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import javax.tools.*;

import com.sun.tools.javac.api.JavacTaskImpl;

/*
 * This is a cut down version of TestJavacTaskScanner, which as originally written
 * caused an IllegalArgumentException in AbstractDiagnosticFormatter as a result
 * of calling task.parseType with a name whose resolution depended on the setting
 * of the bootclasspath.
 * This test has the same call, task.parseType("List<String>", clazz), but checks
 * that the error is handled in a reasonable way by javac.
 */
public class TestResolveError extends ToolTester {
    public static void main(String... args) throws Exception {
        new TestResolveError().run();
    }

    void run() throws Exception {
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        File file = new File(test_src, "TestResolveError.java");
        final Iterable<? extends JavaFileObject> compilationUnits =
            fm.getJavaFileObjects(new File[] {file});
        task = (JavacTaskImpl)tool.getTask(pw, fm, null, null, null, compilationUnits);
        elements = task.getElements();
        types = task.getTypes();

        Iterable<? extends TypeElement> toplevels;
        try {
            toplevels = task.enter(task.parse());
        } catch (IOException ex) {
            throw new AssertionError(ex);
        }

        for (TypeElement clazz : toplevels) {
            System.out.format("Testing %s:%n%n", clazz.getSimpleName());
            // this should not cause any exception from the compiler,
            // such as IllegalArgumentException
            testParseType(clazz);
        }

        pw.close();

        String out = sw.toString();
        System.out.println(out);

        if (out.contains("com.sun.tools.javac.util"))
            throw new Exception("Unexpected output from compiler");
    }

    void testParseType(TypeElement clazz) {
        DeclaredType type = (DeclaredType)task.parseType("List<String>", clazz);
        for (Element member : elements.getAllMembers((TypeElement)type.asElement())) {
            TypeMirror mt = types.asMemberOf(type, member);
            System.out.format("%s : %s -> %s%n", member.getSimpleName(), member.asType(), mt);
        }
    }

    JavacTaskImpl task;
    Elements elements;
    Types types;
}
