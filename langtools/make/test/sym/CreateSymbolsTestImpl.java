/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
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
import java.util.List;
import com.sun.tools.javac.file.ZipFileIndexCache;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.FileVisitor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import build.tools.symbolgenerator.CreateSymbols;
import build.tools.symbolgenerator.CreateSymbols.ClassDescription;
import build.tools.symbolgenerator.CreateSymbols.ClassList;
import build.tools.symbolgenerator.CreateSymbols.CtSymKind;
import build.tools.symbolgenerator.CreateSymbols.ExcludeIncludeList;
import build.tools.symbolgenerator.CreateSymbols.VersionDescription;

public class CreateSymbolsTestImpl {

    static final String CREATE_SYMBOLS_NAME = "symbolgenerator.CreateSymbols";

    public static void main(String... args) throws Exception {
        new CreateSymbolsTestImpl().doTest();
    }

    void doTest() throws Exception {
        boolean testRun = false;
        for (Method m : CreateSymbolsTestImpl.class.getDeclaredMethods()) {
            if (!"testIncluded".equals(m.getName()))
                continue;
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
               ToolBox.Expect.SUCCESS,
               ToolBox.Expect.FAIL);
        doTest("package t; public class T { public void b() { } public void m() { } public void a() { } }",
               "package t; public class T { public void b() { }                     public void a() { } }",
               "package t; public class Test { { T t = null; t.b(); t.a(); } }",
               ToolBox.Expect.SUCCESS,
               ToolBox.Expect.SUCCESS);
        //with additional attribute (need to properly skip the member):
        doTest("package t; public class T { public void m() throws IllegalStateException { } public void a() { } }",
               "package t; public class T {                                                  public void a() { } }",
               "package t; public class Test { { T t = null; t.a(); } }",
               ToolBox.Expect.SUCCESS,
               ToolBox.Expect.SUCCESS);
    }

    @Test
    void testMethodAdded() throws Exception {
        doTest("package t; public class T { }",
               "package t; public class T { public void m() { } }",
               "package t; public class Test { { T t = null; t.m(); } }",
               ToolBox.Expect.FAIL,
               ToolBox.Expect.SUCCESS);
        doTest("package t; public class T { public void b() { }                     public void a() { } }",
               "package t; public class T { public void b() { } public void m() { } public void a() { } }",
               "package t; public class Test { { T t = null; t.b(); t.a(); } }",
               ToolBox.Expect.SUCCESS,
               ToolBox.Expect.SUCCESS);
    }

    //verify fields added/modified/removed

    @Test
    void testClassAdded() throws Exception {
        doTest("class Dummy {}",
               "package t; public class T { }",
               "package t; public class Test { { T t = new T(); } }",
               ToolBox.Expect.FAIL,
               ToolBox.Expect.SUCCESS);
    }

    @Test
    void testClassModified() throws Exception {
        doTest("package t; public class T { public void m() { } }",
               "package t; public class T implements java.io.Serializable { public void m() { } }",
               "package t; public class Test { { java.io.Serializable t = new T(); } }",
               ToolBox.Expect.FAIL,
               ToolBox.Expect.SUCCESS);
    }

    @Test
    void testClassRemoved() throws Exception {
        doTest("package t; public class T { }",
               "class Dummy {}",
               "package t; public class Test { { T t = new T(); } }",
               ToolBox.Expect.SUCCESS,
               ToolBox.Expect.FAIL);
    }

    @Test
    void testInnerClassAttributes() throws Exception {
        doTest("package t; public class T { public static class Inner { } }",
               "package t; public class T { public static class Inner { } }",
               "package t; import t.T.Inner; public class Test { Inner i; }",
               ToolBox.Expect.SUCCESS,
               ToolBox.Expect.SUCCESS);
    }

    @Test
    void testConstantAdded() throws Exception {
        doTest("package t; public class T { }",
               "package t; public class T { public static final int A = 0; }",
               "package t; public class Test { void t(int i) { switch (i) { case T.A: break;} } }",
               ToolBox.Expect.FAIL,
               ToolBox.Expect.SUCCESS);
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
               ToolBox.Expect.SUCCESS,
               ToolBox.Expect.SUCCESS);
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
        doPrintElementTest("package t;" +
                           "import java.lang.annotation.*;" +
                           "public @Visible @Invisible class T { }" +
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
                           "  public T();\n" +
                           "}\n",
                           "t.Visible",
                           "package t;\n\n" +
                           "@java.lang.annotation.Retention(java.lang.annotation.RetentionPolicy.RUNTIME)\n" +
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
                           "@java.lang.annotation.Retention(java.lang.annotation.RetentionPolicy.RUNTIME)\n" +
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
    }

    @Test
    void testStringConstant() throws Exception {
        doTest("package t; public class T { public static final String C = \"\"; }",
               "package t; public class T { public static final String C = \"\"; }",
               "package t; public class Test { { System.err.println(T.C); } }",
                ToolBox.Expect.SUCCESS,
                ToolBox.Expect.SUCCESS);
    }

    @Test
    void testCopyProfileAnnotation() throws Exception {
        String oldProfileAnnotation = CreateSymbols.PROFILE_ANNOTATION;
        try {
            CreateSymbols.PROFILE_ANNOTATION = "Lt/Ann;";
            doTestEquivalence("package t; public class T { public void t() {} } @interface Ann { }",
                              "package t; public @Ann class T { public void t() {} } @interface Ann { }",
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
                           "  public static final java.lang.String AMP = \"&amp;&&lt;<&gt;>&apos;\\'\";\n\n" +
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
               ToolBox.Expect.SUCCESS,
               ToolBox.Expect.SUCCESS);
    }

    int i = 0;

    void doTest(String code7, String code8, String testCode, ToolBox.Expect result7, ToolBox.Expect result8) throws Exception {
        ToolBox tb = new ToolBox();
        Path classes = prepareVersionedCTSym(code7, code8);
        Path output = classes.getParent();
        Path scratch = output.resolve("scratch");

        Files.createDirectories(scratch);

        tb.new JavacTask()
          .sources(testCode)
          .options("-d", scratch.toAbsolutePath().toString(), "-classpath", computeClassPath(classes, "7"), "-XDuseOptimizedZip=false")
          .run(result7)
          .writeAll();
        tb.new JavacTask()
          .sources(testCode)
          .options("-d", scratch.toAbsolutePath().toString(), "-classpath", computeClassPath(classes, "8"), "-XDuseOptimizedZip=false")
          .run(result8)
          .writeAll();
    }

    private static String computeClassPath(Path classes, String version) throws IOException {
        try (Stream<Path> elements = Files.list(classes)) {
            return elements.map(el -> el.toAbsolutePath().toString())
                           .collect(Collectors.joining(File.pathSeparator));
        }
    }

    void doPrintElementTest(String code7, String code8, String className7, String printed7, String className8, String printed8) throws Exception {
        ToolBox tb = new ToolBox();
        Path classes = prepareVersionedCTSym(code7, code8);
        Path output = classes.getParent();
        Path scratch = output.resolve("scratch");

        Files.createDirectories(scratch);

        String out;
        out = tb.new JavacTask(ToolBox.Mode.CMDLINE)
                .options("-d", scratch.toAbsolutePath().toString(), "-classpath", computeClassPath(classes, "7"), "-XDuseOptimizedZip=false", "-Xprint", className7)
                .run(ToolBox.Expect.SUCCESS)
                .getOutput(ToolBox.OutputKind.STDOUT);
        if (!out.equals(printed7)) {
            throw new AssertionError("out=" + out + "; printed7=" + printed7);
        }
        out = tb.new JavacTask(ToolBox.Mode.CMDLINE)
                .options("-d", scratch.toAbsolutePath().toString(), "-classpath", computeClassPath(classes, "8"), "-XDuseOptimizedZip=false", "-Xprint", className8)
                .run(ToolBox.Expect.SUCCESS)
                .getOutput(ToolBox.OutputKind.STDOUT);
        if (!out.equals(printed8)) {
            throw new AssertionError("out=" + out + "; printed8=" + printed8);
        }
    }

    void doTestEquivalence(String code7, String code8, String testClass) throws Exception {
        Path classes = prepareVersionedCTSym(code7, code8);
        Path classfile = classes.resolve("78").resolve(testClass.replace('.', '/') + ".class");

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

    void doTestIncluded(String code, String... includedClasses) throws Exception {
        boolean oldIncludeAll = includeAll;
        try {
            includeAll = false;
            Path classes = prepareVersionedCTSym(code, "package other; public class Other {}");
            Path root = classes.resolve("7");
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

    Path prepareVersionedCTSym(String code7, String code8) throws Exception {
        String testClasses = System.getProperty("test.classes");
        Path output = Paths.get(testClasses, "test-data" + i++);
        deleteRecursively(output);
        Files.createDirectories(output);
        Path ver7Jar = output.resolve("7.jar");
        compileAndPack(output, ver7Jar, code7);
        Path ver8Jar = output.resolve("8.jar");
        compileAndPack(output, ver8Jar, code8);

        ZipFileIndexCache.getSharedInstance().clearCache();

        Path classes = output.resolve("classes");

        Files.createDirectories(classes);

        Path ctSym = output.resolve("ct.sym");

        deleteRecursively(ctSym);

        CreateSymbols.ALLOW_NON_EXISTING_CLASSES = true;
        CreateSymbols.EXTENSION = ".class";

        testGenerate(ver7Jar, ver8Jar, ctSym, "8", classes.toAbsolutePath().toString());

        return classes;
    }

    boolean includeAll = true;

    void testGenerate(Path jar7, Path jar8, Path descDest, String version, String classDest) throws IOException {
        deleteRecursively(descDest);

        List<VersionDescription> versions =
                Arrays.asList(new VersionDescription(jar7.toAbsolutePath().toString(), "7", null),
                              new VersionDescription(jar8.toAbsolutePath().toString(), "8", "7"));

        ExcludeIncludeList acceptAll = new ExcludeIncludeList(null, null) {
            @Override public boolean accepts(String className) {
                return true;
            }
        };
        new CreateSymbols() {
            @Override
            protected boolean includeEffectiveAccess(ClassList classes, ClassDescription clazz) {
                return includeAll ? true : super.includeEffectiveAccess(classes, clazz);
            }
        }.createBaseLine(versions, acceptAll, descDest, null);
        Path symbolsDesc = descDest.resolve("symbols");
        try (Writer symbolsFile = Files.newBufferedWriter(symbolsDesc)) {
            symbolsFile.write("generate platforms 7:8");
            symbolsFile.write(System.lineSeparator());
            symbolsFile.write("platform version 7 files java.base-7.sym.txt");
            symbolsFile.write(System.lineSeparator());
            symbolsFile.write("platform version 8 base 7 files java.base-8.sym.txt");
            symbolsFile.write(System.lineSeparator());
        }
        new CreateSymbols().createSymbols(symbolsDesc.toAbsolutePath().toString(), classDest, CtSymKind.JOINED_VERSIONS);
    }

    void compileAndPack(Path output, Path outputFile, String... code) throws Exception {
        ToolBox tb = new ToolBox();
        Path scratch = output.resolve("temp");
        deleteRecursively(scratch);
        Files.createDirectories(scratch);
        System.err.println(Arrays.asList(code));
        tb.new JavacTask().sources(code).options("-d", scratch.toAbsolutePath().toString()).run(ToolBox.Expect.SUCCESS);
        List<String> classFiles = collectClassFile(scratch);
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
