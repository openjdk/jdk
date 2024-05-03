/*
 * Copyright (c) 2015, 2024, Oracle and/or its affiliates. All rights reserved.
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

import java.io.File;
import java.io.InputStream;
import java.io.Writer;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.FileVisitResult;
import java.nio.file.FileVisitor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import toolbox.JavacTask;
import toolbox.Task;
import toolbox.Task.Expect;
import toolbox.ToolBox;
import build.tools.symbolgenerator.CreateSymbols;
import build.tools.symbolgenerator.CreateSymbols.ClassDescription;
import build.tools.symbolgenerator.CreateSymbols.ClassList;
import build.tools.symbolgenerator.CreateSymbols.ExcludeIncludeList;
import build.tools.symbolgenerator.CreateSymbols.VersionDescription;
import com.sun.tools.classfile.Attribute;
import com.sun.tools.classfile.Attributes;
import com.sun.tools.classfile.ClassFile;
import com.sun.tools.classfile.ClassWriter;
import com.sun.tools.classfile.ConstantPool;
import com.sun.tools.classfile.ConstantPool.CPInfo;
import com.sun.tools.classfile.ConstantPool.CONSTANT_Utf8_info;
import com.sun.tools.classfile.ModulePackages_attribute;

public class CreateSymbolsTestImpl {

    static final String CREATE_SYMBOLS_NAME = "symbolgenerator.CreateSymbols";

    public static void main(String... args) throws Exception {
        new CreateSymbolsTestImpl().doTest();
    }

    void doTest() throws Exception {
        boolean testRun = false;
        for (Method m : CreateSymbolsTestImpl.class.getDeclaredMethods()) {
            if (m.isAnnotationPresent(Test.class)) {
                m.invoke(this);
                testRun = true;
            }
        }
        if (!testRun) {
            throw new IllegalStateException("No tests found.");
        }
    }

    @Test
    void testMethodRemoved() throws Exception {
        doTest("package t; public class T { public void m() { } }",
               "package t; public class T { }",
               "package t; public class Test { { T t = null; t.m(); } }",
               Expect.SUCCESS,
               Expect.FAIL);
        doTest("package t; public class T { public void b() { } public void m() { } public void a() { } }",
               "package t; public class T { public void b() { }                     public void a() { } }",
               "package t; public class Test { { T t = null; t.b(); t.a(); } }",
               Expect.SUCCESS,
               Expect.SUCCESS);
        //with additional attribute (need to properly skip the member):
        doTest("package t; public class T { public void m() throws IllegalStateException { } public void a() { } }",
               "package t; public class T {                                                  public void a() { } }",
               "package t; public class Test { { T t = null; t.a(); } }",
               Expect.SUCCESS,
               Expect.SUCCESS);
    }

    @Test
    void testMethodAdded() throws Exception {
        doTest("package t; public class T { }",
               "package t; public class T { public void m() { } }",
               "package t; public class Test { { T t = null; t.m(); } }",
               Expect.FAIL,
               Expect.SUCCESS);
        doTest("package t; public class T { public void b() { }                     public void a() { } }",
               "package t; public class T { public void b() { } public void m() { } public void a() { } }",
               "package t; public class Test { { T t = null; t.b(); t.a(); } }",
               Expect.SUCCESS,
               Expect.SUCCESS);
    }

    //verify fields added/modified/removed

    @Test
    void testClassAdded() throws Exception {
        doTest("class Dummy {}",
               "package t; public class T { }",
               "package t; public class Test { { T t = new T(); } }",
               Expect.FAIL,
               Expect.SUCCESS);
    }

    @Test
    void testClassModified() throws Exception {
        doTest("package t; public class T { public void m() { } }",
               "package t; public class T implements java.io.Serializable { public void m() { } }",
               "package t; public class Test { { java.io.Serializable t = new T(); } }",
               Expect.FAIL,
               Expect.SUCCESS);
    }

    @Test
    void testClassRemoved() throws Exception {
        doTest("package t; public class T { }",
               "class Dummy {}",
               "package t; public class Test { { T t = new T(); } }",
               Expect.SUCCESS,
               Expect.FAIL);
    }

    @Test
    void testInnerClassAttributes() throws Exception {
        doTest("package t; public class T { public static class Inner { } }",
               "package t; public class T { public static class Inner { } public void extra() {} }",
               "package t; import t.T.Inner; public class Test { Inner i; }",
               Expect.SUCCESS,
               Expect.SUCCESS);
    }

    @Test
    void testConstantAdded() throws Exception {
        doTest("package t; public class T { }",
               "package t; public class T { public static final int A = 0; }",
               "package t; public class Test { void t(int i) { switch (i) { case T.A: break;} } }",
               Expect.FAIL,
               Expect.SUCCESS);
    }

    @Test
    void testAnnotationAttributeDefaultvalue() throws Exception {
        //TODO: this only verifies that there is *some* value, but we should also verify there is a specific value:
        doTest("package t; public @interface T { }",
               "package t;\n" +
               "public @interface T {\n" +
               "    public boolean booleanValue() default true;\n" +
               "    public byte byteValue() default 1;\n" +
               "    public char charValue() default 2;\n" +
               "    public short shortValue() default 3;\n" +
               "    public int intValue() default 4;\n" +
               "    public long longValue() default 5;\n" +
               "    public float floatValue() default 6;\n" +
               "    public double doubleValue() default 7;\n" +
               "    public String stringValue() default \"8\";\n" +
               "    public java.lang.annotation.RetentionPolicy enumValue() default java.lang.annotation.RetentionPolicy.RUNTIME;\n" +
               "    public Class classValue() default Number.class;\n" +
               "    public int[] arrayValue() default {1, 2};\n" +
               "    public SuppressWarnings annotationValue() default @SuppressWarnings(\"cast\");\n" +
               "}\n",
               "package t; public @T class Test { }",
               Expect.SUCCESS,
               Expect.SUCCESS);
    }

    @Test
    void testConstantTest() throws Exception {
        //XXX: other constant types (String in particular) - see testStringConstant
        doPrintElementTest("package t; public class T { public static final int A = 1; }",
                           "package t; public class T { public static final int A = 2; }",
                           "t.T",
                           "package t;\n\n" +
                           "public class T {\n" +
                           "  public static final int A = 1;\n\n" +
                           "  public T();\n" +
                           "}\n",
                           "t.T",
                           "package t;\n\n" +
                           "public class T {\n" +
                           "  public static final int A = 2;\n\n" +
                           "  public T();\n" +
                           "}\n");
        doPrintElementTest("package t; public class T { public static final boolean A = false; }",
                           "package t; public class T { public static final boolean A = true; }",
                           "t.T",
                           "package t;\n\n" +
                           "public class T {\n" +
                           "  public static final boolean A = false;\n\n" +
                           "  public T();\n" +
                           "}\n",
                           "t.T",
                           "package t;\n\n" +
                           "public class T {\n" +
                           "  public static final boolean A = true;\n\n" +
                           "  public T();\n" +
                           "}\n");
    }

    @Test
    void testAnnotations() throws Exception {
        Set<String> extraAnnotations = Set.of("Ljava/lang/annotation/Retention;");
        CreateSymbols.HARDCODED_ANNOTATIONS.addAll(extraAnnotations);
        try {
            doPrintElementTest("package t;" +
                               "import java.lang.annotation.*;" +
                               "public @Visible @Invisible class T { public void extra() { } }" +
                               "@Retention(RetentionPolicy.RUNTIME) @interface Visible { }" +
                               "@Retention(RetentionPolicy.CLASS) @interface Invisible { }",
                               "package t;" +
                               "import java.lang.annotation.*;" +
                               "public @Visible @Invisible class T { }" +
                               "@Retention(RetentionPolicy.RUNTIME) @interface Visible { }" +
                               "@Retention(RetentionPolicy.CLASS) @interface Invisible { }",
                               "t.T",
                               "package t;\n\n" +
                               "@t.Invisible\n" +
                               "@t.Visible\n" +
                               "public class T {\n\n" +
                               "  public T();\n\n" +
                               "  public void extra();\n" +
                               "}\n",
                               "t.Visible",
                               "package t;\n\n" +
                               "@java.lang.annotation.Retention(RUNTIME)\n" +
                               "@interface Visible {\n" +
                               "}\n");
            doPrintElementTest("package t;" +
                               "import java.lang.annotation.*;" +
                               "import java.util.*;" +
                               "public class T {" +
                               "    public void test(int h, @Invisible int i, @Visible List<String> j, int k) { }" +
                               "}" +
                               "@Retention(RetentionPolicy.RUNTIME) @interface Visible { }" +
                               "@Retention(RetentionPolicy.CLASS) @interface Invisible { }",
                               "package t;" +
                               "import java.lang.annotation.*;" +
                               "import java.util.*;" +
                               "public class T {" +
                               "    public void test(int h, @Invisible int i, @Visible List<String> j, int k) { }" +
                               "    public void extra() { }" +
                               "}" +
                               "@Retention(RetentionPolicy.RUNTIME) @interface Visible { }" +
                               "@Retention(RetentionPolicy.CLASS) @interface Invisible { }",
                               "t.T",
                               "package t;\n\n" +
                               "public class T {\n\n" +
                               "  public T();\n\n" +
                               "  public void test(int arg0,\n" +
                               "    @t.Invisible int arg1,\n" +
                               "    @t.Visible java.util.List<java.lang.String> arg2,\n" +
                               "    int arg3);\n" +
                               "}\n",
                               "t.Visible",
                               "package t;\n\n" +
                               "@java.lang.annotation.Retention(RUNTIME)\n" +
                               "@interface Visible {\n" +
                               "}\n");
            doPrintElementTest("package t;" +
                               "import java.lang.annotation.*;" +
                               "public class T {" +
                               "    public void test(@Ann(v=\"url\", dv=\"\\\"\\\"\") String str) { }" +
                               "}" +
                               "@Retention(RetentionPolicy.RUNTIME) @interface Ann {" +
                               "    public String v();" +
                               "    public String dv();" +
                               "}",
                               "package t;" +
                               "public class T { }",
                               "t.T",
                               "package t;\n\n" +
                               "public class T {\n\n" +
                               "  public T();\n\n" +
                               "  public void test(@t.Ann(dv=\"\\\"\\\"\", v=\"url\") java.lang.String arg0);\n" +
                               "}\n",
                               "t.T",
                               "package t;\n\n" +
                               "public class T {\n\n" +
                               "  public T();\n" +
                               "}\n");
        } finally {
            CreateSymbols.HARDCODED_ANNOTATIONS.removeAll(extraAnnotations);
        }
    }

    @Test
    void testStringConstant() throws Exception {
        doTest("package t; public class T { public static final String C = \"\"; }",
               "package t; public class T { public static final String C = \"\"; public void extra() { } }",
               "package t; public class Test { { System.err.println(T.C); } }",
                Expect.SUCCESS,
                Expect.SUCCESS);
    }

    @Test
    void testCopyProfileAnnotation() throws Exception {
        String oldProfileAnnotation = CreateSymbols.PROFILE_ANNOTATION;
        try {
            CreateSymbols.PROFILE_ANNOTATION = "Lt/Ann;";
            doTestEquivalence("package t; public @Ann class T { public void t() {} } @interface Ann { }",
                              "package t; public class T { public void t() {} }",
                              "t.T");
        } finally {
            CreateSymbols.PROFILE_ANNOTATION = oldProfileAnnotation;
        }
    }

    @Test
    void testParseAnnotation() throws Exception {
        CreateSymbols.parseAnnotations("@Lsun/Proprietary+Annotation;@Ljdk/Profile+Annotation;(value=I1)", new int[1]);
        CreateSymbols.parseAnnotations("@Ltest;(value={\"\"})", new int[1]);
        CreateSymbols.parseAnnotations("@Ljava/beans/ConstructorProperties;(value={\"path\"})", new int[1]);
        CreateSymbols.parseAnnotations("@Ljava/beans/ConstructorProperties;(value=I-2)", new int[1]);
    }

    @Test
    void testStringCharLiterals() throws Exception {
        doPrintElementTest("package t;" +
                           "public class T {" +
                           "    public static final String STR = \"\\u0000\\u0001\\uffff\";" +
                           "    public static final String EMPTY = \"\";" +
                           "    public static final String AMP = \"&amp;&&lt;<&gt;>&apos;'\";" +
                           "}",
                           "package t;" +
                           "    public class T {" +
                           "    public static final char c = '\\uffff';" +
                           "}",
                           "t.T",
                           "package t;\n\n" +
                           "public class T {\n" +
                           "  public static final java.lang.String STR = \"\\u0000\\u0001\\uffff\";\n" +
                           "  public static final java.lang.String EMPTY = \"\";\n" +
                           "  public static final java.lang.String AMP = \"&amp;&&lt;<&gt;>&apos;'\";\n\n" +
                           "  public T();\n" +
                           "}\n",
                           "t.T",
                           "package t;\n\n" +
                           "public class T {\n" +
                           "  public static final char c = '\\uffff';\n\n" +
                           "  public T();\n" +
                           "}\n");
    }

    @Test
    void testGenerification() throws Exception {
        doTest("package t; public class T { public class TT { public Object t() { return null; } } }",
               "package t; public class T<E> { public class TT { public E t() { return null; } } }",
               "package t; public class Test { { T.TT tt = null; tt.t(); } }",
               Expect.SUCCESS,
               Expect.SUCCESS);
    }

    @Test
    void testClearMissingAnnotations() throws Exception {
        doPrintElementTest(new String[] {
                               """
                               package t;
                               import t.impl.HC;
                               import t.impl.HR;
                               @HC @HR public class T {
                                   @HC @HR public static final int i = 0;
                                   @HC @HR public void t() {}
                               }
                               """,
                               """
                               package t.impl;
                               import java.lang.annotation.*;
                               @Retention(RetentionPolicy.CLASS)
                               public @interface HC {
                               }
                               """,
                               """
                               package t.impl;
                               import java.lang.annotation.*;
                               @Retention(RetentionPolicy.RUNTIME)
                               public @interface HR {
                               }
                               """
                           },
                           new String[] {
                               """
                               package t;
                               public class T {
                                   public static final int i = 0;
                               }
                               """
                           },
                           "t.T",
                           """
                           package t;

                           public class T {
                             public static final int i = 0;

                             public T();

                             public void t();
                           }
                           """,
                           "t.T",
                           """
                           package t;

                           public class T {
                             public static final int i = 0;

                             public T();
                           }
                           """);
    }

    int i = 0;

    void doTest(String code7, String code8, String testCode, Expect result7, Expect result8) throws Exception {
        ToolBox tb = new ToolBox();
        Path classes = prepareVersionedCTSym(new String[] {code7}, new String[] {code8});
        Path output = classes.getParent();
        Path scratch = output.resolve("scratch");

        Files.createDirectories(scratch);

        new JavacTask(tb)
          .sources(testCode)
          .options("-d", scratch.toAbsolutePath().toString(), "-classpath", computeClassPath(classes, "7"))
          .run(result7)
          .writeAll();
        new JavacTask(tb)
          .sources(testCode)
          .options("-d", scratch.toAbsolutePath().toString(), "-classpath", computeClassPath(classes, "8"))
          .run(result8)
          .writeAll();
    }

    private static String computeClassPath(Path classes, String version) throws IOException {
        try (Stream<Path> elements = Files.list(classes)) {
            return elements.filter(el -> el.getFileName().toString().contains(version))
                            .map(el -> el.resolve("java.base"))
                            .map(el -> el.toAbsolutePath().toString())
                            .collect(Collectors.joining(File.pathSeparator));
        }
    }

    void doPrintElementTest(String code7, String code8, String className7, String printed7, String className8, String printed8) throws Exception {
        doPrintElementTest(new String[] {code7}, new String[] {code8}, className7, printed7, className8, printed8);
    }

    void doPrintElementTest(String[] code7, String[] code8, String className7, String printed7, String className8, String printed8) throws Exception {
        ToolBox tb = new ToolBox();
        Path classes = prepareVersionedCTSym(code7, code8);
        Path output = classes.getParent();
        Path scratch = output.resolve("scratch");

        Files.createDirectories(scratch);

        String out;
        out = new JavacTask(tb, Task.Mode.CMDLINE)
                .options("-d", scratch.toAbsolutePath().toString(), "-classpath", computeClassPath(classes, "7"), "-Xprint", className7)
                .run(Expect.SUCCESS)
                .getOutput(Task.OutputKind.STDOUT)
                .replaceAll("\\R", "\n");
        if (!out.equals(printed7)) {
            throw new AssertionError("out=" + out + "; printed7=" + printed7);
        }
        out = new JavacTask(tb, Task.Mode.CMDLINE)
                .options("-d", scratch.toAbsolutePath().toString(), "-classpath", computeClassPath(classes, "8"), "-Xprint", className8)
                .run(Expect.SUCCESS)
                .getOutput(Task.OutputKind.STDOUT)
                .replaceAll("\\R", "\n");
        if (!out.equals(printed8)) {
            throw new AssertionError("out=" + out + "; printed8=" + printed8);
        }
    }

    void doTestEquivalence(String code7, String code8, String testClass) throws Exception {
        Path classes = prepareVersionedCTSym(new String[] {code7}, new String[] {code8});
        Path classfile = classes.resolve("78").resolve("java.base").resolve(testClass.replace('.', '/') + ".class");

        if (!Files.isReadable(classfile)) {
            throw new AssertionError("Cannot find expected class.");
        }
    }

    @Test
    void testIncluded() throws Exception {
        doTestIncluded("package t;\n" +
                       "public class Test extends PP1<PP2> implements PP3<PP4>, PP5<PP6> {\n" +
                       "     public PP7 m1(PP8 p) { return null;}\n" +
                       "     public PP9<PPA> m2(PPB<PPC> p) { return null;}\n" +
                       "     public PPD f1;\n" +
                       "     public PPE<PPF> f2;\n" +
                       "     public Test2 aux;\n" +
                       "}\n" +
                       "class Test2 extends PPG implements PPH, PPI {\n" +
                       "}\n" +
                       "class PP1<T> {}\n" +
                       "class PP2 {}\n" +
                       "interface PP3<T> {}\n" +
                       "class PP4 {}\n" +
                       "interface PP5<T> {}\n" +
                       "class PP6 {}\n" +
                       "class PP7 {}\n" +
                       "class PP8 {}\n" +
                       "class PP9<T> {}\n" +
                       "class PPA {}\n" +
                       "class PPB<T> {}\n" +
                       "class PPC {}\n" +
                       "class PPD {}\n" +
                       "class PPE<T> {}\n" +
                       "class PPF {}\n" +
                       "class PPG {}\n" +
                       "interface PPH {}\n" +
                       "interface PPI {}\n",
                       "t.Test",
                       "t.Test2",
                       "t.PP1",
                       "t.PP2",
                       "t.PP3",
                       "t.PP4",
                       "t.PP5",
                       "t.PP6",
                       "t.PP7",
                       "t.PP8",
                       "t.PP9",
                       "t.PPA",
                       "t.PPB",
                       "t.PPC",
                       "t.PPD",
                       "t.PPE",
                       "t.PPF",
                       "t.PPG",
                       "t.PPH",
                       "t.PPI");
    }

    @Test
    void testRecords() throws Exception {
        doPrintElementTest("package t;" +
                           "public class T {" +
                           "    public record R(int i, java.util.List<String> l) { }" +
                           "}",
                           "package t;" +
                           "public class T {" +
                           "    public record R(@Ann int i, long j, java.util.List<String> l) { }" +
                           "    public @interface Ann {} " +
                           "}",
                           "t.T$R",
                           """

                           public static record R(int i, java.util.List<java.lang.String> l) {

                             public R(int i,
                               java.util.List<java.lang.String> l);

                             public final java.lang.String toString();

                             public final int hashCode();

                             public final boolean equals(java.lang.Object arg0);

                             public int i();

                             public java.util.List<java.lang.String> l();
                           }
                           """,
                           "t.T$R",
                           """

                           public static record R(@t.T.Ann int i, long j, java.util.List<java.lang.String> l) {

                             public final java.lang.String toString();

                             public final int hashCode();

                             public final boolean equals(java.lang.Object arg0);

                             public java.util.List<java.lang.String> l();

                             public R(@t.T.Ann int i,
                               long j,
                               java.util.List<java.lang.String> l);

                             @t.T.Ann
                             public int i();

                             public long j();
                           }
                           """);
        doPrintElementTest("package t;" +
                           "public record R() {" +
                           "}",
                           "package t;" +
                           "public record R(int i) {" +
                           "}",
                           "t.R",
                           """
                           package t;
                           \n\
                           public record R() {
                           \n\
                             public R();
                           \n\
                             public final java.lang.String toString();
                           \n\
                             public final int hashCode();
                           \n\
                             public final boolean equals(java.lang.Object arg0);
                           }
                           """,
                           "t.R",
                           """
                           package t;
                           \n\
                           public record R(int i) {
                           \n\
                             public final java.lang.String toString();
                           \n\
                             public final int hashCode();
                           \n\
                             public final boolean equals(java.lang.Object arg0);
                           \n\
                             public R(int i);
                           \n\
                             public int i();
                           }
                           """);
    }

    @Test
    void testNonExportedSuperclass() throws Exception {
        doTestComplex("api.Api",
                      """
                      package api;

                      public class Api extends nonapi.Impl.Nested.Exp {

                        public Api();
                      }
                      """,
                      """
                      import api.Api;
                      public class Test {
                          private void t(Api api) {
                            api.run();
                          }
                      }
                      """,
                      """
                      import api.Api;
                      public class Test {
                          private void t(Api api) {
                              fail
                          }
                      }
                      """,
                      """
                      module m {
                          exports api;
                      }
                      """,
                      """
                      package api;
                      import nonapi.Impl;
                      public class Api extends Impl.Nested.Exp {
                      }
                      """,
                      """
                      package api;
                      public @interface Ann {
                      }
                      """,
                      """
                      package nonapi;
                      import api.Ann;
                      public class Impl {
                          public static final String C = "";
                          public void test() {}
                          @Ann
                          public static class Nested {
                              public static class Exp extends Nested implements Runnable {
                                  public void run() {}
                                  public OtherNested get() { return null; }
                              }
                          }
                          public static class OtherNested {}
                      }
                      """);
    }

    void doTestComplex(String printClass,
                      String expected,
                      String depSuccess,
                      String depFailure,
                      String... code) throws Exception {
        ToolBox tb = new ToolBox();
        String testClasses = System.getProperty("test.classes");
        Path output = Paths.get(testClasses, "test-data" + i++);
        deleteRecursively(output);
        Files.createDirectories(output);
        Path ver9Jar = output.resolve("9.jar");
        compileAndPack(output,
                       ver9Jar,
                       code);


        Path ctSym = output.resolve("ct.sym");

        deleteRecursively(ctSym);

        CreateSymbols.ALLOW_NON_EXISTING_CLASSES = true;
        CreateSymbols.EXTENSION = ".class";

        deleteRecursively(ctSym);

        List<VersionDescription> versions =
                Arrays.asList(new VersionDescription(ver9Jar.toAbsolutePath().toString(), "9", null));

        ExcludeIncludeList acceptAll = new ExcludeIncludeList(null, null) {
            @Override public boolean accepts(String className, boolean includePrivateClasses) {
                return true;
            }
        };
        new CreateSymbols().createBaseLine(versions, acceptAll, ctSym, new String[0]);
        Path symbolsDesc = ctSym.resolve("symbols");
        Path modules = ctSym.resolve("modules");
        Path modulesList = ctSym.resolve("modules-list");

        Files.createDirectories(modules);
        try (Writer w = Files.newBufferedWriter(modulesList)) {}

        Path classesZip = output.resolve("classes.zip");
        Path classesDir = output.resolve("classes");

        new CreateSymbols().createSymbols(null, symbolsDesc.toAbsolutePath().toString(), classesZip.toAbsolutePath().toString(), 0, "9", "", modules.toString(), modulesList.toString());

        try (JarFile jf = new JarFile(classesZip.toFile())) {
            Enumeration<JarEntry> en = jf.entries();

            while (en.hasMoreElements()) {
                JarEntry je = en.nextElement();
                if (je.isDirectory()) continue;
                Path target = classesDir.resolve(je.getName());
                Files.createDirectories(target.getParent());
                Files.copy(jf.getInputStream(je), target);
            }
        }

        Path classes = classesDir;
        Path scratch = output.resolve("scratch");

        Files.createDirectories(scratch);

        String modulePath;

        try (Stream<Path> elements = Files.list(classes)) {
            modulePath = elements.filter(el -> el.getFileName().toString().contains("9"))
                            .map(el -> el.resolve("m"))
                            .map(el -> el.toAbsolutePath().toString())
                            .collect(Collectors.joining(File.pathSeparator));
        }

        {
            String out = new JavacTask(tb, Task.Mode.CMDLINE)
                    .options("-d", scratch.toAbsolutePath().toString(), "--module-path", modulePath,
                             "--add-modules", "m",  "-Xprint", "api.Api")
                    .run(Expect.SUCCESS)
                    .getOutput(Task.OutputKind.STDOUT)
                    .replaceAll("\\R", "\n");

            if (!out.equals(expected)) {
                throw new AssertionError("out=" + out + "; expected=" + expected);
            }
        }

        {
            new JavacTask(tb)
                    .options("-d", scratch.toAbsolutePath().toString(), "--module-path", modulePath,
                             "--add-modules", "m")
                    .sources(depSuccess)
                    .run(Expect.SUCCESS)
                    .writeAll();
        }

        {
            String expectedFailure = new JavacTask(tb)
                    .options("-d", scratch.toAbsolutePath().toString(), "--module-path", output.resolve("temp").toString(),
                             "--add-modules", "m", "-XDrawDiagnostics")
                    .sources(depFailure)
                    .run(Expect.FAIL)
                    .getOutput(Task.OutputKind.DIRECT)
                    .replaceAll("\\R", "\n");

            String out = new JavacTask(tb)
                    .options("-d", scratch.toAbsolutePath().toString(), "--module-path", modulePath,
                             "--add-modules", "m", "-XDrawDiagnostics")
                    .sources(depFailure)
                    .run(Expect.FAIL)
                    .getOutput(Task.OutputKind.DIRECT)
                    .replaceAll("\\R", "\n");

            if (!out.equals(expectedFailure)) {
                throw new AssertionError("out=" + out + "; expected=" + expectedFailure);
            }
        }
    }

    @Test
    void testExtendsInternalData1() throws Exception {
        doTestData("""
                   module name m
                   header exports api extraModulePackages nonapi requires name\\u0020;java.base\\u0020;flags\\u0020;8000\\u0020;version\\u0020;0 flags 8000

                   class name api/Ann
                   header extends java/lang/Object implements java/lang/annotation/Annotation flags 2601

                   class name api/Api
                   header extends nonapi/Impl$Nested$Exp flags 21
                   innerclass innerClass nonapi/Impl$Nested outerClass nonapi/Impl innerClassName Nested flags 9
                   innerclass innerClass nonapi/Impl$Nested$Exp outerClass nonapi/Impl$Nested innerClassName Exp flags 9
                   method name <init> descriptor ()V flags 1

                   class name nonapi/Impl
                   header extends java/lang/Object nestMembers nonapi/Impl$Nested,nonapi/Impl$Nested$Exp flags 21
                   innerclass innerClass nonapi/Impl$Nested outerClass nonapi/Impl innerClassName Nested flags 9
                   innerclass innerClass nonapi/Impl$Nested$Exp outerClass nonapi/Impl$Nested innerClassName Exp flags 9
                   field name C descriptor Ljava/lang/String; constantValue  flags 19
                   method name <init> descriptor ()V flags 1
                   method name test descriptor ()V flags 1

                   class name nonapi/Impl$Nested
                   header extends java/lang/Object nestHost nonapi/Impl flags 21 classAnnotations @Lapi/Ann;
                   innerclass innerClass nonapi/Impl$Nested outerClass nonapi/Impl innerClassName Nested flags 9
                   innerclass innerClass nonapi/Impl$Nested$Exp outerClass nonapi/Impl$Nested innerClassName Exp flags 9
                   method name <init> descriptor ()V flags 1

                   class name nonapi/Impl$Nested$Exp
                   header extends nonapi/Impl$Nested implements java/lang/Runnable nestHost nonapi/Impl flags 21
                   innerclass innerClass nonapi/Impl$Nested outerClass nonapi/Impl innerClassName Nested flags 9
                   innerclass innerClass nonapi/Impl$Nested$Exp outerClass nonapi/Impl$Nested innerClassName Exp flags 9
                   method name <init> descriptor ()V flags 1
                   method name run descriptor ()V flags 1
                   method name get descriptor ()Lnonapi/Impl$OtherNested; flags 1

                   """,
                   """
                   module m {
                       exports api;
                       exports nonapi to java.base;
                   }
                   """,
                   """
                   package api;
                   import nonapi.Impl;
                   public class Api extends Impl.Nested.Exp {
                   }
                   """,
                   """
                   package api;
                   public @interface Ann {
                   }
                   """,
                   """
                   package nonapi;
                   import api.Ann;
                   public class Impl {
                       public static final String C = "";
                       public void test() {}
                       @Ann
                       public static class Nested {
                           public static class Exp extends Nested implements Runnable {
                               public void run() {}
                               public OtherNested get() { return null; }
                           }
                       }
                       public static class OtherNested {}
                   }
                   """);
    }

    void doTestData(String data,
                          String... code) throws Exception {
        String testClasses = System.getProperty("test.classes");
        Path output = Paths.get(testClasses, "test-data" + i++);
        deleteRecursively(output);
        Files.createDirectories(output);
        Path ver9Jar = output.resolve("9.jar");
        compileAndPack(output,
                       ver9Jar,
                       code);

        Path ctSym = output.resolve("ct.sym");

        deleteRecursively(ctSym);

        CreateSymbols.ALLOW_NON_EXISTING_CLASSES = true;
        CreateSymbols.DO_NOT_MODIFY = "";
        CreateSymbols.EXTENSION = ".class";
        CreateSymbols.INJECTED_VERSION = "0";

        deleteRecursively(ctSym);

        List<VersionDescription> versions =
                Arrays.asList(new VersionDescription(ver9Jar.toAbsolutePath().toString(), "9", null));

        ExcludeIncludeList acceptAll = new ExcludeIncludeList(null, null) {
            @Override public boolean accepts(String className, boolean includePrivateClasses) {
                return true;
            }
        };
        new CreateSymbols().createBaseLine(versions, acceptAll, ctSym, new String[0]);

        Path symFile = null;

        try (DirectoryStream<Path> ds = Files.newDirectoryStream(ctSym)) {
            for (Path p : ds) {
                if (p.toString().endsWith(".sym.txt")) {
                    if (symFile != null) {
                        throw new IllegalStateException("Multiple sym files!");
                    } else {
                        symFile = p;
                    }
                }
            }
        }
        String acutalContent = new String(Files.readAllBytes(symFile), StandardCharsets.UTF_8);
        if (!acutalContent.equals(data)) {
            throw new AssertionError("out=" + acutalContent + "; expected=" + data);
        }
    }

    void doTestIncluded(String code, String... includedClasses) throws Exception {
        boolean oldIncludeAll = includeAll;
        try {
            includeAll = false;
            Path classes = prepareVersionedCTSym(new String[] {code}, new String[] {"package other; public class Other {}"});
            Path root = classes.resolve("7").resolve("java.base");
            try (Stream<Path> classFiles = Files.walk(root)) {
                Set<String> names = classFiles.map(p -> root.relativize(p))
                                              .map(p -> p.toString())
                                              .map(n -> {System.err.println("n= " + n); return n;})
                                              .filter(n -> n.endsWith(".class"))
                                              .map(n -> n.substring(0, n.lastIndexOf('.')))
                                              .map(n -> n.replace(File.separator, "."))
                                              .collect(Collectors.toSet());

                if (!names.equals(new HashSet<>(Arrays.asList(includedClasses))))
                    throw new AssertionError("Expected classes not included: " + names);
            }
        } finally {
            includeAll = oldIncludeAll;
        }
    }

    Path prepareVersionedCTSym(String[] code7, String[] code8) throws Exception {
        String testClasses = System.getProperty("test.classes");
        Path output = Paths.get(testClasses, "test-data" + i++);
        deleteRecursively(output);
        Files.createDirectories(output);
        Path ver7Jar = output.resolve("7.jar");
        compileAndPack(output, ver7Jar, code7);
        Path ver8Jar = output.resolve("8.jar");
        compileAndPack(output, ver8Jar, code8);

        Path classes = output.resolve("classes.zip");

        Path ctSym = output.resolve("ct.sym");

        deleteRecursively(ctSym);

        CreateSymbols.ALLOW_NON_EXISTING_CLASSES = true;
        CreateSymbols.EXTENSION = ".class";

        testGenerate(ver7Jar, ver8Jar, ctSym, "8", classes.toAbsolutePath().toString());

        Path classesDir = output.resolve("classes");

        try (JarFile jf = new JarFile(classes.toFile())) {
            Enumeration<JarEntry> en = jf.entries();

            while (en.hasMoreElements()) {
                JarEntry je = en.nextElement();
                if (je.isDirectory()) continue;
                Path target = classesDir.resolve(je.getName());
                Files.createDirectories(target.getParent());
                Files.copy(jf.getInputStream(je), target);
            }
        }

        return classesDir;
    }

    boolean includeAll = true;

    void testGenerate(Path jar7, Path jar8, Path descDest, String version, String classDest) throws IOException {
        deleteRecursively(descDest);

        List<VersionDescription> versions =
                Arrays.asList(new VersionDescription(jar7.toAbsolutePath().toString(), "7", null),
                              new VersionDescription(jar8.toAbsolutePath().toString(), "8", "7"));

        ExcludeIncludeList acceptAll = new ExcludeIncludeList(null, null) {
            @Override public boolean accepts(String className, boolean includePrivateClasses) {
                return true;
            }
        };
        new CreateSymbols() {
            @Override
            protected boolean includeEffectiveAccess(ClassList classes, ClassDescription clazz) {
                return includeAll ? true : super.includeEffectiveAccess(classes, clazz);
            }
        }.createBaseLine(versions, acceptAll, descDest, new String[0]);
        Path symbolsDesc = descDest.resolve("symbols");
        Path modules = descDest.resolve("modules");
        Path modulesList = descDest.resolve("modules-list");

        Files.createDirectories(modules);
        try (Writer w = Files.newBufferedWriter(modulesList)) {}

        new CreateSymbols().createSymbols(null, symbolsDesc.toAbsolutePath().toString(), classDest, 0, "8", "", modules.toString(), modulesList.toString());
    }

    void compileAndPack(Path output, Path outputFile, String... code) throws Exception {
        ToolBox tb = new ToolBox();
        Path scratch = output.resolve("temp");
        deleteRecursively(scratch);
        Files.createDirectories(scratch);
        System.err.println(Arrays.asList(code));
        new JavacTask(tb).sources(code).options("-d", scratch.toAbsolutePath().toString()).run(Expect.SUCCESS);
        List<String> classFiles = collectClassFile(scratch);
        Path moduleInfo = scratch.resolve("module-info.class");
        if (Files.exists(moduleInfo)) {
            Set<String> packages = new HashSet<>();
            for (String cf : classFiles) {
                int sep = cf.lastIndexOf(scratch.getFileSystem().getSeparator());
                if (sep != (-1)) {
                    packages.add(cf.substring(0, sep));
                }
            }
            ClassFile cf = ClassFile.read(moduleInfo);
            List<CPInfo> cp = new ArrayList<>();
            cp.add(null);
            cf.constant_pool.entries().forEach(cp::add);
            Map<String, Attribute> attrs = new HashMap<>(cf.attributes.map);
            int[] encodedPackages = new int[packages.size()];
            int i = 0;
            for (String p : packages) {
                int nameIndex = cp.size();
                cp.add(new CONSTANT_Utf8_info(p));
                encodedPackages[i++] = cp.size();
                cp.add(new ConstantPool.CONSTANT_Package_info(null, nameIndex));
            }
            int attrName = cp.size();
            cp.add(new CONSTANT_Utf8_info(Attribute.ModulePackages));
            attrs.put(Attribute.ModulePackages, new ModulePackages_attribute(attrName, encodedPackages));
            ClassFile newFile = new ClassFile(cf.magic, cf.minor_version, cf.major_version, new ConstantPool(cp.toArray(new CPInfo[0])), cf.access_flags, cf.this_class, cf.super_class, cf.interfaces, cf.fields, cf.methods, new Attributes(attrs));
            try (OutputStream out = Files.newOutputStream(moduleInfo)) {
                new ClassWriter().write(newFile, out);
            }
        }
        try (Writer out = Files.newBufferedWriter(outputFile)) {
            for (String classFile : classFiles) {
                try (InputStream in = Files.newInputStream(scratch.resolve(classFile))) {
                    int read;

                    while ((read = in.read()) != (-1)) {
                        out.write(String.format("%02x", read));
                    }

                    out.write("\n");
                }
            }
        }
    }

    List<String> collectClassFile(Path root) throws IOException {
        try (Stream<Path> files = Files.walk(root)) {
            return files.filter(p -> Files.isRegularFile(p))
                        .filter(p -> p.getFileName().toString().endsWith(".class"))
                        .map(p -> root.relativize(p).toString())
                        .filter(p -> !p.contains("impl"))
                        .collect(Collectors.toList());
        }
    }

    void deleteRecursively(Path dir) throws IOException {
        Files.walkFileTree(dir, new FileVisitor<Path>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                return FileVisitResult.CONTINUE;
            }
            @Override public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.delete(file);
                return FileVisitResult.CONTINUE;
            }
            @Override public FileVisitResult visitFileFailed(Path file, IOException exc) throws IOException {
                return FileVisitResult.CONTINUE;
            }
            @Override public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                Files.delete(dir);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    @Retention(RetentionPolicy.RUNTIME)
    @interface Test {
    }
}
