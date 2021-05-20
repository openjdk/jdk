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

 /*
  * @test
  * @bug      8267204
  * @summary  Expose access to underlying streams in Reporter
  * @library  /tools/lib ../../lib
  * @modules  jdk.javadoc/jdk.javadoc.internal.tool
  * @build    toolbox.ToolBox javadoc.tester.*
  * @run main TestReporterStreams
  */

 import java.io.IOException;
 import java.nio.file.Path;
 import java.util.Collections;
 import java.util.Locale;
 import java.util.Set;

 import javax.lang.model.SourceVersion;
 import javax.tools.Diagnostic;

 import javadoc.tester.JavadocTester;
 import jdk.javadoc.doclet.Doclet;
 import jdk.javadoc.doclet.DocletEnvironment;
 import jdk.javadoc.doclet.Reporter;
 import toolbox.ToolBox;

 public class TestReporterStreams extends JavadocTester {

     public static void main(String... args) throws Exception {
         TestReporterStreams tester = new TestReporterStreams();
         tester.runTests(m -> new Object[] { Path.of(m.getName() )});
     }

     ToolBox tb = new ToolBox();

     /**
      * Tests the entry point used by the DocumentationTool API and JavadocTester, in which
      * all output is written to a single specified writer.
      */
     @Test
     public void testSingleStream(Path base) throws IOException {
         test(base, false, Output.OUT, Output.OUT);
     }

     /**
      * Tests the entry point used by the launcher, in which output is written to
      * writers that wrap {@code System.out} and {@code System.err}.
      */
     @Test
     public void testStandardStreams(Path base) throws IOException {
         test(base, true, Output.STDOUT, Output.STDERR);
     }

     void test(Path base, boolean useStdStreams, Output stdOut, Output stdErr) throws IOException {
         Path src = base.resolve("src");
         tb.writeJavaFiles(src, "public class C { }");

         String testClasses = System.getProperty("test.classes");

         setOutputDirectoryCheck(DirectoryCheck.NONE);
         setUseStandardStreams(useStdStreams);
         javadoc("-docletpath", testClasses,
                 "-doclet", MyDoclet.class.getName(),
                 src.resolve("C.java").toString()
         );
         checkExit(Exit.OK);
         checkOutput(stdOut, true,
                 "Writing to the standard writer");
         checkOutput(stdErr, true,
                 "Writing to the diagnostic writer");
     }

     public static class MyDoclet implements Doclet {
         private Locale locale;
         private Reporter reporter;

         @Override
         public void init(Locale locale, Reporter reporter) {
             this.locale = locale;
             this.reporter = reporter;
         }

         @Override
         public String getName() {
             return "MyDoclet";
         }

         @Override
         public Set<? extends Option> getSupportedOptions() {
             return Collections.emptySet();
         }

         @Override
         public SourceVersion getSupportedSourceVersion() {
             return SourceVersion.latestSupported();
         }

         @Override
         public boolean run(DocletEnvironment environment) {
             reporter.getStandardWriter().println("Writing to the standard writer");
             reporter.getDiagnosticWriter().println("Writing to the diagnostic writer");
             reporter.print(Diagnostic.Kind.NOTE, "The locale is " + locale.getDisplayName());
             return true;
         }
     }
 }
