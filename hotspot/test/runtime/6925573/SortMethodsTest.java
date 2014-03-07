/*
 * Copyright (c) 2008, 2010, Oracle and/or its affiliates. All rights reserved.
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
 *
 */

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;

import java.lang.reflect.Method;
import java.net.URI;
import java.util.Arrays;
import java.util.Vector;

import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.FileObject;
import javax.tools.ForwardingJavaFileManager;
import javax.tools.JavaCompiler;
import javax.tools.JavaCompiler.CompilationTask;
import javax.tools.JavaFileManager;
import javax.tools.JavaFileObject;
import javax.tools.JavaFileObject.Kind;
import javax.tools.SimpleJavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;

/*
 * @ignore 6959423
 * @test SortMethodsTest
 * @bug 6925573
 * @summary verify that class loading does not need quadratic time with regard to the number of class
methods.
 * @run main SortMethodsTest
 * @author volker.simonis@gmail.com
*/

public class SortMethodsTest {

  static String createClass(String name, int nrOfMethods) {
    StringWriter sw = new StringWriter();
    PrintWriter pw = new PrintWriter(sw);
    pw.println("public class " + name + "{");
    for (int i = 0; i < nrOfMethods; i++) {
      pw.println("  public void m" + i + "() {}");
    }
    pw.println("  public static String sayHello() {");
    pw.println("    return \"Hello from class \" + " + name +
               ".class.getName() + \" with \" + " + name +
               ".class.getDeclaredMethods().length + \" methods\";");
    pw.println("  }");
    pw.println("}");
    pw.close();
    return sw.toString();
  }

  public static void main(String args[]) {

    JavaCompiler comp = ToolProvider.getSystemJavaCompiler();
    DiagnosticCollector<JavaFileObject> diags = new DiagnosticCollector<JavaFileObject>();
    final String cName = new String("ManyMethodsClass");
    Vector<Long> results = new Vector<Long>();

    for (int i = 6; i < 600000; i*=10) {
      String klass =  createClass(cName, i);
      JavaMemoryFileObject file = new JavaMemoryFileObject(cName, klass);
      MemoryFileManager mfm = new MemoryFileManager(comp.getStandardFileManager(diags, null, null), file);
      CompilationTask task = comp.getTask(null, mfm, diags, null, null, Arrays.asList(file));

      if (task.call()) {
        try {
          MemoryClassLoader mcl = new MemoryClassLoader(file);
          long start = System.nanoTime();
          Class<? extends Object> c = Class.forName(cName, true, mcl);
          long end = System.nanoTime();
          results.add(end - start);
          Method m = c.getDeclaredMethod("sayHello", new Class[0]);
          String ret = (String)m.invoke(null, new Object[0]);
          System.out.println(ret + " (loaded and resloved in " + (end - start) + "ns)");
        } catch (Exception e) {
          System.err.println(e);
        }
      }
      else {
        System.out.println(klass);
        System.out.println();
        for (Diagnostic diag : diags.getDiagnostics()) {
          System.out.println(diag.getCode() + "\n" + diag.getKind() + "\n" + diag.getPosition());
          System.out.println(diag.getSource() + "\n" + diag.getMessage(null));
        }
      }
    }

    long lastRatio = 0;
    for (int i = 2; i < results.size(); i++) {
      long normalized1 = Math.max(results.get(i-1) - results.get(0), 1);
      long normalized2 = Math.max(results.get(i) - results.get(0), 1);
      long ratio = normalized2/normalized1;
      lastRatio = ratio;
      System.out.println("10 x more methods requires " + ratio + " x more time");
    }
    // The following is just vague estimation but seems to work on current x86_64 and sparcv9 machines
    if (lastRatio > 80) {
      throw new RuntimeException("ATTENTION: it seems that class loading needs quadratic time with regard to the number of class methods!!!");
    }
  }
}

class JavaMemoryFileObject extends SimpleJavaFileObject {

  private final String code;
  private ByteArrayOutputStream byteCode;

  JavaMemoryFileObject(String name, String code) {
    super(URI.create("string:///" + name.replace('.','/') + Kind.SOURCE.extension), Kind.SOURCE);
    this.code = code;
  }

  @Override
  public CharSequence getCharContent(boolean ignoreEncodingErrors) {
    return code;
  }

  @Override
  public OutputStream openOutputStream() {
    byteCode = new ByteArrayOutputStream();
    return byteCode;
  }

  byte[] getByteCode() {
    return byteCode.toByteArray();
   }
}

class MemoryClassLoader extends ClassLoader {

  private final JavaMemoryFileObject jfo;

  public MemoryClassLoader(JavaMemoryFileObject jfo) {
    this.jfo = jfo;
  }

  public Class findClass(String name) {
    byte[] b = jfo.getByteCode();
    return defineClass(name, b, 0, b.length);
  }
}

class MemoryFileManager extends ForwardingJavaFileManager<JavaFileManager> {

  private final JavaFileObject jfo;

  public MemoryFileManager(StandardJavaFileManager jfm, JavaFileObject jfo) {
    super(jfm);
    this.jfo = jfo;
  }

  @Override
  public FileObject getFileForInput(Location location, String packageName,
                                    String relativeName) throws IOException {
    return jfo;
  }

  @Override
  public JavaFileObject getJavaFileForOutput(Location location, String qualifiedName,
                                             Kind kind, FileObject outputFile) throws IOException {
    return jfo;
  }

}
