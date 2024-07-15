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

package compiler.c2;

/*
 * @test
 * @bug 8318446 8335392
 * @summary Test merging of consecutive stores, and more specifically the MemPointer.
 * @modules java.base/jdk.internal.misc
 * @library /test/lib /
 * @run driver compiler.c2.TestMergeStoresFuzzer
 */

import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaCompiler.CompilationTask;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.ToolProvider;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.InvocationTargetException;
import java.net.URI;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.List;

import compiler.lib.ir_framework.*;

public class TestMergeStoresFuzzer {
  public static void main(String args[]) throws IOException {
    JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
    DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<JavaFileObject>();

    StringWriter writer = new StringWriter();
    PrintWriter out = new PrintWriter(writer);
    out.println("import compiler.lib.ir_framework.*;"); // TODO make this work
    out.println("");
    out.println("public class HelloWorld {");
    out.println("    public static void main(String args[]) {");
    out.println("        System.out.println(\"This is in another java file\");");
    out.println("        ");
    out.println("        ");
    out.println("        ");
    out.println("        //TestFramework.run(HelloWorld.class);");
    out.println("        System.out.println(\"Done with IR framework.\");");
    out.println("    }");
    out.println("");
    out.println("    //@Test");
    out.println("    static void test() {");
    out.println("        throw new RuntimeException(\"xyz\");");
    out.println("    }");
    out.println("}");
    out.close();
    JavaFileObject file = new JavaSourceFromString("HelloWorld", writer.toString());

    Iterable<? extends JavaFileObject> compilationUnits = Arrays.asList(file);
    List<String> optionList = new ArrayList<String>();
    optionList.add("-classpath");
    //optionList.add(System.getProperty("test.classes"));
    optionList.add(System.getProperty("java.class.path"));
    CompilationTask task = compiler.getTask(null, null, diagnostics, optionList, null, compilationUnits);

    System.out.println("classpath: " + System.getProperty("java.class.path"));

    boolean success = task.call();
    for (Diagnostic diagnostic : diagnostics.getDiagnostics()) {
      System.out.println(diagnostic.getCode());
      System.out.println(diagnostic.getKind());
      System.out.println(diagnostic.getPosition());
      System.out.println(diagnostic.getStartPosition());
      System.out.println(diagnostic.getEndPosition());
      System.out.println(diagnostic.getSource());
      System.out.println(diagnostic.getMessage(null));
    }

    if (success) {
      System.out.println("Compilation successfull, invoking test...");
      try {
          ClassLoader sysLoader = ClassLoader.getSystemClassLoader();
          // Classpath for all included classes (e.g. IR Framework).
          URL[] urls = new URL[] { new File("").toURI().toURL(),
                                   new File(System.getProperty("java.class.path")).toURI().toURL()};
          URLClassLoader classLoader = URLClassLoader.newInstance(urls, sysLoader);
          Class.forName("HelloWorld", true, classLoader).getDeclaredMethod("main", new Class[] { String[].class }).invoke(null, new Object[] { null });

      } catch (ClassNotFoundException e) {
        throw new RuntimeException("Class not found:", e);
      } catch (NoSuchMethodException e) {
        throw new RuntimeException("No such method:", e);
      } catch (IllegalAccessException e) {
        throw new RuntimeException("Illegal access:", e);
      } catch (InvocationTargetException e) {
        throw new RuntimeException("Invocation target:", e);
      }

      System.out.println("Invocation successful.");
    } else {
      System.out.println("Compilation failed.");
      throw new RuntimeException("Compilation failed.");
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
