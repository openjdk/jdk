/*
 * Copyright (c) 2017, 2023, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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
package build.tools.depend;

import com.sun.source.util.Plugin;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.io.Writer;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import javax.tools.JavaCompiler;
import javax.tools.SimpleJavaFileObject;
import javax.tools.ToolProvider;


public class DependTest {

    public static void main(String... args) throws Exception {
        DependTest test = new DependTest();

        test.setupClass();

        test.testMethods();
        test.testFields();
        test.testModules();
        test.testAnnotations();
        test.testRecords();
        test.testImports();
        test.testModifiers();
        test.testPrimitiveTypeChanges();
        test.testWithErrors();
    }

    public void testMethods() throws Exception {
        doOrdinaryTest("package test;" +
                       "public class Test {\n" +
                       "}",
                       "package test;" +
                       "public class Test {\n" +
                       "    public void test() {\n" +
                       "    }\n" +
                       "}",
                       true);
        doOrdinaryTest("package test;" +
                       "public class Test {\n" +
                       "}",
                       "package test;" +
                       "public class Test {\n" +
                       "    private void test() {\n" +
                       "    }\n" +
                       "}",
                       false);
        doOrdinaryTest("package test;" +
                       "public class Test {\n" +
                       "    public void test() {\n" +
                       "    }\n" +
                       "}",
                       "package test;" +
                       "public class Test {\n" +
                       "}",
                       true);
        doOrdinaryTest("package test;" +
                       "public class Test {\n" +
                       "    private void test() {\n" +
                       "    }\n" +
                       "}",
                       "package test;" +
                       "public class Test {\n" +
                       "}",
                       false);
    }

    public void testFields() throws Exception {
        doOrdinaryTest("package test;" +
                       "public class Test {\n" +
                       "}",
                       "package test;" +
                       "public class Test {\n" +
                       "    public int test;\n" +
                       "}",
                       true);
        doOrdinaryTest("package test;" +
                       "public class Test {\n" +
                       "}",
                       "package test;" +
                       "public class Test {\n" +
                       "    private int test;\n" +
                       "}",
                       false);
        doOrdinaryTest("package test;" +
                       "public class Test {\n" +
                       "    public static final int test = 0;\n" +
                       "}",
                       "package test;" +
                       "public class Test {\n" +
                       "    public static final int test = 1;\n" +
                       "}",
                       true);
        doOrdinaryTest("package test;" +
                       "public class Test {\n" +
                       "    public int test;\n" +
                       "}",
                       "package test;" +
                       "public class Test {\n" +
                       "}",
                       true);
        doOrdinaryTest("package test;" +
                       "public class Test {\n" +
                       "    private int test;\n" +
                       "}",
                       "package test;" +
                       "public class Test {\n" +
                       "}",
                       false);
    }

    public void testAnnotations() throws Exception {
        doOrdinaryTest("package test;" +
                       "public class Test {\n" +
                       "}",
                       "package test;" +
                       "@SuppressWarnings(\"any\")\n" +
                       "public class Test {\n" +
                       "}",
                       false,
                       true); //Tree hash does not tolerate undocumented annotations
        doOrdinaryTest("package test;" +
                       "public class Test {\n" +
                       "}",
                       "package test;" +
                       "@Deprecated\n" +
                       "public class Test {\n" +
                       "}",
                       true);
    }

    public void testImports() throws Exception {
        doOrdinaryTest("package test;" +
                       "public class Test {\n" +
                       "}",
                       "package test;" +
                       "import java.util.List;\n" +
                       "public class Test {\n" +
                       "    private List l;\n" +
                       "}",
                       false);
        doOrdinaryTest("package test;" +
                       "public class Test {\n" +
                       "}",
                       "package test;" +
                       "import java.util.List;\n" +
                       "public class Test {\n" +
                       "    public List l;\n" +
                       "}",
                       true);
        doOrdinaryTest("package test;" +
                       "public class Test {\n" +
                       "}",
                       "package test;" +
                       "import java.util.List;\n" +
                       "public class Test {\n" +
                       "    List l;\n" +
                       "}",
                       false,
                       true);
        doOrdinaryTest("package test;" +
                       "import java.util.*;\n" +
                       "public abstract class Test implements List {\n" +
                       "}\n" +
                       "class H {\n" +
                       "    public interface List {}\n" +
                       "}",
                       "package test;" +
                       "import java.util.*;\n" +
                       "import test.H.List;\n" +
                       "public abstract class Test implements List {\n" +
                       "}\n" +
                       "class H {\n" +
                       "    public interface List {}\n" +
                       "}",
                       true);
        doOrdinaryTest("package test;" +
                       "public class Test {\n" +
                       "}",
                       "package test;" +
                       "import java.util.*;\n" +
                       "public class Test {\n" +
                       "}",
                       false,
                       true);
        doOrdinaryTest("package test;" +
                       "import java.util.*;\n" +
                       "public class Test {\n" +
                       "}",
                       "package test;" +
                       "public class Test {\n" +
                       "}",
                       false,
                       true);
    }

    public void testModifiers() throws Exception {
        doOrdinaryTest("package test;" +
                       "public class Test {\n" +
                       "    String l;\n" +
                       "}",
                       "package test;" +
                       "public class Test {\n" +
                       "    public String l;\n" +
                       "}",
                       true);
    }

    public void testModules() throws Exception {
        doModuleTest("module m { }",
                     "module m { requires java.compiler; }",
                     true);
        doModuleTest("module m { requires java.compiler; }",
                     "module m { requires java.compiler; }",
                     false);
        doModuleTest("module m { requires java.compiler; }",
                     "module m { requires jdk.compiler; }",
                     true);
        doModuleTest("module m { }",
                     "module m { exports test; }",
                     true);
        doModuleTest("module m { }",
                     "module m { exports test to java.base; }",
                     true);
        doModuleTest("module m { }",
                     "module m { exports test to java.compiler; }",
                     true);
        doModuleTest("module m { }",
                     "module m { uses test.Test1; }",
                     true);
        doModuleTest("module m { uses test.Test1; }",
                     "module m { uses test.Test2; }",
                     true);
        doModuleTest("module m { }",
                     "module m { provides test.Test1 with test.TestImpl1; }",
                     true);
        doModuleTest("module m { provides test.Test1 with test.TestImpl1; }",
                     "module m { provides test.Test2 with test.TestImpl1; }",
                     true);
        doModuleTest("module m { provides test.Test1 with test.TestImpl1; }",
                     "module m { provides test.Test1 with test.TestImpl2; }",
                     true);
    }

    public void testRecords() throws Exception {
        doOrdinaryTest("package test; public record Test (int x, int y) { }",
                       "package test; public record Test (int x, int y) { }",  // identical
                       false);
        doOrdinaryTest("package test; public record Test (int x, int y) { }",
                       "package test; public record Test (int x, int y) {" +
                               "public Test { } }",  // compact ctr
                       false,
                       true);
        doOrdinaryTest("package test; public record Test (int x, int y) { }",
                       "package test; public record Test (int x, int y) {" +
                               "public Test (int x, int y) { this.x=x; this.y=y;} }",  // canonical ctr
                       false,
                       true);
        doOrdinaryTest("package test; public record Test (int x, int y) { }",
                       "package test; public record Test (int y, int x) { }",  // reverse
                       true);
        doOrdinaryTest("package test; public record Test (int x, int y) { }",
                       "package test; public record Test (int x, int y, int z) { }", // additional
                       true);
        doOrdinaryTest("package test; public record Test (int x, int y) { }",
                       "package test; public record Test () { }", // empty
                       true);
        doOrdinaryTest("package test; public record Test (int x, int y) { }",
                       "package test; /*package*/ record Test (int x, int y) { }",  // package
                       true);
        doOrdinaryTest("package test; public record Test (int x, int y) { }",
                       "package test; public record Test (int x, int y) {" +
                               "public Test (int x, int y, int z) { this(x, y); } }",  // additional ctr
                       true);
        doOrdinaryTest("package test; public record Test (int x) { }",
                       "package test; public record Test (long x) { unresolved f; }", //erroneous record member, should not crash
                       false);
    }

    public void testPrimitiveTypeChanges() throws Exception {
        doOrdinaryTest("package test; public record Test (int x) { }",
                       "package test; public record Test (long x) { }",
                       true);
        doOrdinaryTest("package test; public record Test (int x) { }",
                       "package test; public record Test (Integer x) { }",
                       true);
        doOrdinaryTest("package test; public record Test (Integer x) { }",
                       "package test; public record Test (int x) { }",
                       true);
    }

    public void testWithErrors() throws Exception {
        doOrdinaryTest("package test; public record Test (int x) { }",
                       "package test; public record Test (long x) { static unresolved f; }",
                       false); //the API change should not be recorded for code with errors
    }

    private final JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
    private Path depend;
    private Path scratchServices;
    private Path scratchClasses;
    private Path apiHash;
    private Path treeHash;
    private Path modifiedFiles;

    private void setupClass() throws IOException {
        depend = Paths.get(Depend.class.getProtectionDomain().getCodeSource().getLocation().getPath());
        Path scratch = Files.createTempDirectory("depend-test");
        scratchServices = scratch.resolve("services");
        Path scratchClassesServices = scratchServices.resolve("META-INF").resolve("services");
        Files.createDirectories(scratchClassesServices);

        try (OutputStream out = Files.newOutputStream(scratchClassesServices.resolve(Plugin.class.getName()))) {
            out.write(Depend.class.getName().getBytes());
        }

        scratchClasses = scratch.resolve("classes");

        Files.createDirectories(scratchClasses);

        apiHash = scratch.resolve("api");
        treeHash = scratch.resolve("tree");
        modifiedFiles = scratch.resolve("modified-files");
    }

    private void doOrdinaryTest(String codeBefore, String codeAfter, boolean hashChangeExpected) throws Exception {
        doOrdinaryTest(codeBefore, codeAfter, hashChangeExpected, hashChangeExpected);
    }

    private void doOrdinaryTest(String codeBefore, String codeAfter, boolean apiHashChangeExpected, boolean treeHashChangeExpected) throws Exception {
        try (Writer out = Files.newBufferedWriter(modifiedFiles)) {
            out.append("module-info.java\n");
            out.append("test.Test.java\n");
        }
        List<String> options =
                Arrays.asList("-d", scratchClasses.toString(),
                              "-processorpath", depend.toString() + File.pathSeparator + scratchServices.toString(),
                              "-Xplugin:depend " + apiHash.toString(),
                              "-XDinternalAPIPath=" + treeHash.toString(),
                              "-XDmodifiedInputs=" + modifiedFiles.toString());
        List<TestJavaFileObject> beforeFiles =
                Arrays.asList(new TestJavaFileObject("module-info", "module m { exports test; }"),
                              new TestJavaFileObject("test.Test", codeBefore));
        compiler.getTask(null, null, null, options, null, beforeFiles).call();
        byte[] originalApiHash = Files.readAllBytes(apiHash);
        byte[] originalTreeHash = Files.readAllBytes(treeHash);
        List<TestJavaFileObject> afterFiles =
                Arrays.asList(new TestJavaFileObject("module-info", "module m { exports test; }"),
                              new TestJavaFileObject("test.Test", codeAfter));
        compiler.getTask(null, null, null, options, null, afterFiles).call();
        byte[] newApiHash = Files.readAllBytes(apiHash);
        byte[] newTreeHash = Files.readAllBytes(treeHash);

        if (Arrays.equals(originalApiHash, newApiHash) ^ !apiHashChangeExpected) {
            throw new AssertionError("Unexpected API hash state.");
        }

        if (Arrays.equals(originalTreeHash, newTreeHash) ^ !treeHashChangeExpected) {
            throw new AssertionError("Unexpected Tree hash state, " +
                                     "original: " + new String(originalTreeHash) +
                                     ", new: " + new String(newTreeHash));
        }
    }

    private void doModuleTest(String codeBefore, String codeAfter, boolean hashChangeExpected) throws Exception {
        doModuleTest(codeBefore, codeAfter, hashChangeExpected, hashChangeExpected);
    }

    private void doModuleTest(String codeBefore, String codeAfter, boolean apiHashChangeExpected, boolean treeHashChangeExpected) throws Exception {
        try (Writer out = Files.newBufferedWriter(modifiedFiles)) {
            out.append("module-info.java\n");
            out.append("test.Test1.java\n");
            out.append("test.Test2.java\n");
            out.append("test.TestImpl1.java\n");
            out.append("test.TestImpl2.java\n");
        }
        List<String> options =
                Arrays.asList("-d", scratchClasses.toString(),
                              "-processorpath", depend.toString() + File.pathSeparator + scratchServices.toString(),
                              "-Xplugin:depend " + apiHash.toString() + " " + treeHash.toString(),
                              "-XDinternalAPIPath=" + treeHash.toString(),
                              "-XDmodifiedInputs=" + modifiedFiles.toString());
        List<TestJavaFileObject> beforeFiles =
                Arrays.asList(new TestJavaFileObject("module-info", codeBefore),
                              new TestJavaFileObject("test.Test1", "package test; public interface Test1 {}"),
                              new TestJavaFileObject("test.Test2", "package test; public interface Test2 {}"),
                              new TestJavaFileObject("test.TestImpl1", "package test; public class TestImpl1 implements Test1, Test2 {}"),
                              new TestJavaFileObject("test.TestImpl2", "package test; public class TestImpl2 implements Test1, Test2 {}"));
        compiler.getTask(null, null, null, options, null, beforeFiles).call();
        byte[] originalApiHash = Files.readAllBytes(apiHash);
        byte[] originalTreeHash = Files.readAllBytes(treeHash);
        List<TestJavaFileObject> afterFiles =
                Arrays.asList(new TestJavaFileObject("module-info", codeAfter),
                              new TestJavaFileObject("test.Test1", "package test; public interface Test1 {}"),
                              new TestJavaFileObject("test.Test2", "package test; public interface Test2 {}"),
                              new TestJavaFileObject("test.TestImpl1", "package test; public class TestImpl1 implements Test1, Test2 {}"),
                              new TestJavaFileObject("test.TestImpl2", "package test; public class TestImpl2 implements Test1, Test2 {}"));
        compiler.getTask(null, null, null, options, null, afterFiles).call();
        byte[] newApiHash = Files.readAllBytes(apiHash);
        byte[] newTreeHash = Files.readAllBytes(treeHash);

        if (Arrays.equals(originalApiHash, newApiHash) ^ !apiHashChangeExpected) {
            throw new AssertionError("Unexpected API hash state.");
        }

        if (Arrays.equals(originalTreeHash, newTreeHash) ^ !treeHashChangeExpected) {
            throw new AssertionError("Unexpected Tree hash state, " +
                                     "original: " + new String(originalTreeHash) +
                                     ", new: " + new String(newTreeHash));
        }
    }

    private static final class TestJavaFileObject extends SimpleJavaFileObject {

        private final String className;
        private final String code;

        public TestJavaFileObject(String className, String code) throws URISyntaxException {
            super(new URI("mem:/" + className.replace('.', '/') + ".java"), Kind.SOURCE);
            this.className = className;
            this.code = code;
        }

        @Override
        public CharSequence getCharContent(boolean arg0) throws IOException {
            return code;
        }

        @Override
        public String getName() {
            return className + ".java";
        }

    }
}
