/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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

/* This test "covers"/verifies com.sun.tools.javac.model.JavacTypes#asMemberOf's calls
   to asSuper work*properly with primitive types.
*/

/**
 * @test
 * @bug 8244712
 * @summary Test API usage with reference projection types.
 * @ignore
 * @library ./lib
 * @modules jdk.compiler/com.sun.tools.javac.api
 *          jdk.compiler/com.sun.tools.javac.code
 * @build ToolTester
 */

import java.io.*;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.ElementFilter;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import javax.tools.*;

import com.sun.tools.javac.api.JavacTaskImpl;

public class TestApisWithProjections extends ToolTester {
    public static void main(String... args) throws Exception {
        try (TestApisWithProjections t = new TestApisWithProjections()) {
            t.run();
        }
    }

    void run() throws Exception {
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        File file = new File(test_src, "TestApisWithProjections.java");
        final Iterable<? extends JavaFileObject> compilationUnits =
            fm.getJavaFileObjects(new File[] {file});
        task = (JavacTaskImpl)tool.getTask(pw, fm, null, null, null, compilationUnits);
        elements = task.getElements();
        types = task.getTypes();

        Iterable<? extends TypeElement> toplevels;
        toplevels = ElementFilter.typesIn(task.enter(task.parse()));

        for (TypeElement clazz : toplevels) {
            System.out.format("Testing %s:%n%n", clazz.getSimpleName());
            testParseType(clazz);
        }

        pw.close();

        String out = sw.toString();
        System.out.println(out);

        if (out.contains("com.sun.tools.javac.util"))
            throw new Exception("Unexpected output from compiler");
    }

    void testParseType(TypeElement clazz) {
        DeclaredType type = (DeclaredType)task.parseType("PrimitiveClass<String>", clazz);
        for (Element member : elements.getAllMembers((TypeElement)type.asElement())) {
            TypeMirror mt = types.asMemberOf(type, member);
            System.out.format("%s : %s -> %s%n", member.getSimpleName(), member.asType(), mt);
        }
    }

    JavacTaskImpl task;
    Elements elements;
    Types types;
}

abstract class Base<T> {
    void foo(T t) {}
}

primitive class PrimitiveClass<T> extends Base<T> {
}
