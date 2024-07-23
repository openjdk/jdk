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
 * @bug 8318446 8335392
 * @summary Test merging of consecutive stores, and more specifically the MemPointer.
 * @modules java.base/jdk.internal.misc
 * @library /test/lib /
 * @compile ../lib/ir_framework/TestFramework.java
 * @run driver TestMergeStoresFuzzer
 */

import compiler.lib.ir_framework.*;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.InvocationTargetException;
import java.net.URI;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler.CompilationTask;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.ToolProvider;

public class TestMergeStoresFuzzer {

    public static String generate() {
        StringWriter writer = new StringWriter();
        PrintWriter out = new PrintWriter(writer);
        out.println("import compiler.lib.ir_framework.*;");
        out.println("");
        out.println("public class XYZ {");
        out.println("    public static void main(String args[]) {");
        out.println("        System.out.println(\"This is in another java file\");");
        out.println("        TestFramework.run(XYZ.class);");
        out.println("        System.out.println(\"Done with IR framework.\");");
        out.println("    }");
        out.println("");
        out.println("    @Test");
        out.println("    static void test() {");
        out.println("        throw new RuntimeException(\"xyz\");");
        out.println("    }");
        out.println("}");
        out.close();
        return writer.toString();
    }

    public static void main(String args[]) throws IOException {
        String src = generate();
        JavaSourceFromString file = new JavaSourceFromString("XYZ", src);

        InMemoryCompiler comp = new InMemoryCompiler();
        comp.add(file);

        comp.compile();

        Class c = comp.getClass("XYZ");

        try {
            c.getDeclaredMethod("main", new Class[] { String[].class }).invoke(null, new Object[] { null });
        } catch (NoSuchMethodException e) {
            throw new RuntimeException("No such method:", e);
        } catch (IllegalAccessException e) {
            throw new RuntimeException("Illegal access:", e);
        } catch (InvocationTargetException e) {
            throw new RuntimeException("Invocation target:", e);
        }
    }
}

class InMemoryCompiler {
    private List<JavaSourceFromString> files = new ArrayList<JavaSourceFromString>();
    private URLClassLoader classLoader;

    public void add(JavaSourceFromString file) {
        files.add(file);
    }

    public void compile() throws IOException {
        if (classLoader != null) {
            throw new RuntimeException("Cannot compile twice!");
        }

        // Get compiler with diagnostics.
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<JavaFileObject>();

        // Set classpath and compilation destination for new class files.
        List<String> optionList = new ArrayList<String>();
        optionList.add("-classpath");
        optionList.add(System.getProperty("java.class.path"));
        optionList.add("-d");
        optionList.add(System.getProperty("test.classes"));

        for (JavaSourceFromString f : files) {
            System.out.println("File: " + f.getName());
            System.out.println(f.getCharContent(false).toString());
        }

        // Compile.
        CompilationTask task = compiler.getTask(null, null, diagnostics, optionList, null, files);
        boolean success = task.call();

        // Print diagnostics.
        for (Diagnostic diagnostic : diagnostics.getDiagnostics()) {
            System.out.println(diagnostic.getCode());
            System.out.println(diagnostic.getKind());
            System.out.println(diagnostic.getPosition());
            System.out.println(diagnostic.getStartPosition());
            System.out.println(diagnostic.getEndPosition());
            System.out.println(diagnostic.getSource());
            System.out.println(diagnostic.getMessage(null));
        }

        if (!success) {
            System.out.println("Compilation failed.");
            throw new RuntimeException("Compilation failed.");
        }

        System.out.println("Compilation successfull, creating ClassLoader...");

        ClassLoader sysLoader = ClassLoader.getSystemClassLoader();
        // Classpath for all included classes (e.g. IR Framework).
        URL[] urls = new URL[] { new File("").toURI().toURL(),
                                 new File(System.getProperty("test.classes")).toURI().toURL()};
        classLoader = URLClassLoader.newInstance(urls, sysLoader);
    }

    public Class getClass(String name) {
        try {
            return Class.forName(name, true, classLoader);
        } catch (ClassNotFoundException e) {
            throw new RuntimeException("Class not found:", e);
        }
    }
}

class JavaSourceFromString extends SimpleJavaFileObject {
    final String code;

    JavaSourceFromString(String name, String code) {
        super(URI.create("string:///" + name.replace('.','/') + Kind.SOURCE.extension), Kind.SOURCE);
        this.code = code;
    }

    @Override
    public CharSequence getCharContent(boolean ignoreEncodingErrors) {
        return code;
    }
}
