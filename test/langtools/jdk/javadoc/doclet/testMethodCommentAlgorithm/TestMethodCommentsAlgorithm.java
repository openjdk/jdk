/*
 * Copyright (c) 2022, 2024, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8285368
 * @library /tools/lib ../../lib /test/lib
 * @modules jdk.javadoc/jdk.javadoc.internal.tool
 * @build toolbox.ToolBox javadoc.tester.*
 * @build jtreg.SkippedException
 * @run main TestMethodCommentsAlgorithm
 */

import java.io.IOError;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import javax.lang.model.element.Modifier;
import javax.lang.model.type.TypeKind;
import javax.tools.ToolProvider;

import com.sun.source.doctree.DocCommentTree;
import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.IdentifierTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.PrimitiveTypeTree;
import com.sun.source.tree.Tree;
import com.sun.source.tree.VariableTree;
import com.sun.source.util.DocTrees;
import com.sun.source.util.JavacTask;
import com.sun.source.util.TreePath;
import com.sun.source.util.TreePathScanner;
import javadoc.tester.JavadocTester;
import jtreg.SkippedException;
import toolbox.ToolBox;

import static javadoc.tester.JavadocTester.Exit.OK;

/*
 * These tests assert search order for _undirected_ documentation inheritance by
 * following a series of javadoc runs on a progressively undocumented hierarchy
 * of supertypes.
 *
 * Design
 * ======
 *
 * Each test creates a hierarchy consisting of N types (T1, T2, ..., Tn) for
 * which the search order is to be asserted. N-1 types are created (T1, T2,
 * ..., T(n-1)) and one type, Tn, is implicitly present java.lang.Object.
 * T1 is a type under test; T2, T3, ..., T(n-1) are direct or indirect
 * supertypes of T1.
 *
 * By design, the index of a type is evocative of the order in which that type
 * should be considered for documentation inheritance. If T1 lacks a doc
 * comment, T2 should be considered next. If in turn T2 lacks a doc comment,
 * T3 should be considered after that, and so on. Finally, Tn, which is
 * java.lang.Object, whose documentation is ever-present, is considered.
 *
 * The test then runs javadoc N-1 times. Each run one fewer type has a doc
 * comment: for the i-th run (1 <= i < N), type Tj has a doc comment if and
 * only if j > i. So, for the i-th run, i comments are missing and N-i are
 * present. In particular, for the first run (i = 1) the only _missing_ doc
 * comment is that of T1 and for the last run (i = N-1) the only _available_
 * doc comment is that of java.lang.Object.
 *
 * The test challenges javadoc by asking the following question:
 *
 *     Whose documentation will T1 inherit if Tj (1 <= j <= i)
 *     do not have doc comments, but Tk (i < k <= N) do?
 *
 * For the i-th run the test checks that T1 inherits documentation of T(i+1).
 *
 * Technicalities
 * ==============
 *
 * 1. To follow search order up to and including java.lang.Object, these tests
 *    need to be able to inherit documentation for java.lang.Object. For that,
 *    the tests access doc comments of java.lang.Object. To get such access,
 *    the tests patch the java.base module.
 *
 * 2. The documentation for java.lang.Object is slightly amended for
 *    uniformity with test documentation and for additional test
 *    coverage.
 *
 * 3. While documentation for java.lang.Object is currently inaccessible outside
 *    of the JDK, these test mimic what happens when the JDK documentation is
 *    built.
 *
 * Note
 * ====
 *
 * If any of these tests cannot find a valid Object.java file in the test
 * environment, they will throw jtreg.SkippedException.
 */
public class TestMethodCommentsAlgorithm extends JavadocTester {

    private final ToolBox tb = new ToolBox();

    public static void main(String... args) throws Exception {
        new TestMethodCommentsAlgorithm().runTests();
    }

    /*
     * Tests that the documentation search order is as shown:
     *
     *               (5)
     *                ^
     *      *        /
     *     [7] (3) (4)
     *      ^   ^   ^
     *       \  |  /
     *        \ | /
     *         [2] (6)
     *          ^   ^
     *          |  /
     *          | /
     *         [1]
     */
    @Test
    public void testMixedHierarchyEquals(Path base) throws Exception {
        var javaBase = findJavaBase();
        for (int i = 1; i < 7; i++) {
            mixedHierarchyI(base, javaBase, i);
            new OutputChecker("mymodule/x/T1.html").check("""
                    <div class="block">T%s: main description</div>
                    """.formatted(i + 1), """
                    <dt>Parameters:</dt>
                    <dd><code>obj</code> - T%1$s: parameter description</dd>
                    <dt>Returns:</dt>
                    <dd>T%1$s: return description</dd>""".formatted(i + 1));
        }
    }

    /*
     * Generates source for the i-th run such that types whose index is less
     * than i provide no documentation and those whose index is greater or
     * equal to i provide documentation.
     */
    private void mixedHierarchyI(Path base, Path javaBase, int i) throws IOException {
        Path src = base.resolve("src-" + i);
        Path mod = base.resolve("src-" + i).resolve("mymodule");
        tb.writeJavaFiles(mod, """
                package x;
                public class T1 extends T2 implements T6 {
                %s
                    @Override public boolean equals(Object obj) { return super.equals(obj); }
                }
                """.formatted(generateDocComment(1, i)), """
                package x;
                public class T2 /* extends Object */ implements T3, T4 {
                %s
                    @Override public boolean equals(Object obj) { return super.equals(obj); }
                }
                """.formatted(generateDocComment(2, i)), """
                package x;
                public interface T3 {
                %s
                    @Override boolean equals(Object obj);
                }
                """.formatted(generateDocComment(3, i)), """
                package x;
                public interface T4 extends T5 {
                %s
                    @Override boolean equals(Object obj);
                }
                """.formatted(generateDocComment(4, i)), """
                package x;
                public interface T5 {
                %s
                    @Override boolean equals(Object obj);
                }
                """.formatted(generateDocComment(5, i)), """
                package x;
                public interface T6 {
                %s
                    @Override boolean equals(Object obj);
                }
                """.formatted(generateDocComment(6, i)), """
                module mymodule { }
                """);

        createPatchedJavaLangObject(javaBase.resolve("share").resolve("classes").toAbsolutePath(),
                Files.createDirectories(src.resolve("java.base")).toAbsolutePath(),
                generateDocComment(7, i, false));

        javadoc("-d", base.resolve("out-" + i).toAbsolutePath().toString(),
                "-tag", "apiNote:a:API Note:",
                "-tag", "implSpec:a:Implementation Requirements:",
                "-tag", "implNote:a:Implementation Note:",
                "--patch-module", "java.base=" + src.resolve("java.base").toAbsolutePath().toString(),
                "--module-source-path", src.toAbsolutePath().toString(),
                "mymodule/x");

        checkExit(OK);
    }

    private static String generateDocComment(int index, int run) {
        return generateDocComment(index, run, true);
    }

    /*
     * Provides a doc comment for an override of Object.equals in a type with
     * the specified index for the specified run.
     */
    private static String generateDocComment(int index, int run, boolean includeCommentMarkers) {
        if (index > run) {
            String s = """
                    T%s: main description
                    *
                    * @param obj T%1$s: parameter description
                    * @return T%1$s: return description""";
            if (includeCommentMarkers)
                s = "/**\n* " + s + "\n*/";
            return s.formatted(index).indent(4);
        } else {
            return "";
        }
    }

    /*
     * Tests that the documentation search order is as shown:
     *
     *    (3) (4)
     *     ^   ^
     *      \ /
     *      (2) (5)
     *       ^   ^
     *        \ /
     *        (1)
     *         |
     *         v
     *        [6]
     *         *
     */
    @Test
    public void testInterfaceHierarchy(Path base) throws Exception {
        var javaBase = findJavaBase();
        for (int i = 1; i < 6; i++) {
            interfaceHierarchyI(base, javaBase, i);
            new OutputChecker("mymodule/x/T1.html").check("""
                    <div class="block">T%s: main description</div>
                    """.formatted(i + 1), """
                    <dt>Parameters:</dt>
                    <dd><code>obj</code> - T%1$s: parameter description</dd>
                    <dt>Returns:</dt>
                    <dd>T%1$s: return description</dd>""".formatted(i + 1));
        }
    }

    /*
     * Nested/recursive `{@inheritDoc}` are processed before the comments that
     * refer to them. This test highlights that a lone `{@inheritDoc}` is
     * different from a missing/empty comment part.
     *
     * Whenever doclet sees `{@inheritDoc}` or `{@inheritDoc <supertype>}`
     * while searching for a comment to inherit from up the hierarchy, it
     * considers the comment found. A separate and unrelated search is
     * then performed for that found `{@inheritDoc}`.
     *
     * The test case is wrapped in a module in order to be able to patch
     * java.base (otherwise it doesn't seem to work).
     */
    @Test
    public void testRecursiveInheritDocTagsAreProcessedFirst(Path base) throws Exception {
        var javaBase = findJavaBase();
        Path src = base.resolve("src");
        tb.writeJavaFiles(src.resolve("mymodule"), """
                package x;
                public class S {
                    /** {@inheritDoc} */
                    public boolean equals(Object obj) { return super.equals(obj); }
                }
                """, """
                package x;
                public interface I {
                    /** I::equals */
                    boolean equals(Object obj);
                }
                """, """
                package x;
                public class T extends S implements I {
                    public boolean equals(Object obj) { return super.equals(obj); }
                }
                """, """
                module mymodule {}
                """);

        createPatchedJavaLangObject(javaBase.resolve("share").resolve("classes").toAbsolutePath(),
                Files.createDirectories(src.resolve("java.base")).toAbsolutePath(),
                "Object::equals");

        javadoc("-d", base.resolve("out").toString(),
                "-tag", "apiNote:a:API Note:",
                "-tag", "implSpec:a:Implementation Requirements:",
                "-tag", "implNote:a:Implementation Note:",
                "--patch-module", "java.base=" + src.resolve("java.base").toAbsolutePath().toString(),
                "--module-source-path", src.toAbsolutePath().toString(),
                "mymodule/x");

        checkExit(Exit.OK);

        new OutputChecker("mymodule/x/T.html").check("""
                <div class="block">Object::equals</div>""");
    }

    /*
     * Generates source for the i-th run such that types whose index is less
     * than i provide no documentation and those whose index is greater or
     * equal to i provide documentation.
     */
    private void interfaceHierarchyI(Path base, Path javaBase, int i) throws IOException {
        Path src = base.resolve("src-" + i);
        Path mod = base.resolve("src-" + i).resolve("mymodule");
        tb.writeJavaFiles(mod, """
                package x;
                public interface T1 extends T2, T5 {
                %s
                    @Override boolean equals(Object obj);
                }
                """.formatted(generateDocComment(1, i)), """
                package x;
                public interface T2 extends T3, T4 {
                %s
                    @Override boolean equals(Object obj);
                }
                """.formatted(generateDocComment(2, i)), """
                package x;
                public interface T3 {
                %s
                    @Override boolean equals(Object obj);
                }
                """.formatted(generateDocComment(3, i)), """
                package x;
                public interface T4 {
                %s
                    @Override boolean equals(Object obj);
                }
                """.formatted(generateDocComment(4, i)), """
                package x;
                public interface T5 {
                %s
                    @Override boolean equals(Object obj);
                }
                """.formatted(generateDocComment(5, i)), """
                module mymodule { }
                """);

        createPatchedJavaLangObject(javaBase.resolve("share").resolve("classes").toAbsolutePath(),
                Files.createDirectories(src.resolve("java.base")).toAbsolutePath(),
                generateDocComment(6, i, false));

        javadoc("-d", base.resolve("out-" + i).toAbsolutePath().toString(),
                "-tag", "apiNote:a:API Note:",
                "-tag", "implSpec:a:Implementation Requirements:",
                "-tag", "implNote:a:Implementation Note:",
                "--patch-module", "java.base=" + src.resolve("java.base").toAbsolutePath().toString(),
                "--module-source-path", src.toAbsolutePath().toString(),
                "mymodule/x");

        checkExit(OK);
    }

    /*
     * Locates source of the java.base module.
     */
    private Path findJavaBase() {
        String testSrc = System.getProperty("test.src");
        if (testSrc == null) {
            // shouldn't happen
            throw new SkippedException("test.src is not set");
        }
        Path start;
        try {
            start = Path.of(testSrc).toAbsolutePath();
        } catch (InvalidPathException | IOError e) {
            throw new SkippedException("Couldn't make sense of '" + testSrc + "'", e);
        }
        Path p = start;
        while (!Files.exists(p.resolve("TEST.ROOT"))) {
            p = p.getParent();
            if (p == null) {
                // shouldn't happen as jtreg won't even run a test without TEST.ROOT
                throw new SkippedException("Couldn't find TEST.ROOT above '" + start + "'");
            }
        }
        Path javaBase = p.resolve("../../src/java.base").normalize();
        out.println("Source for java.base is found at: " + javaBase);
        return javaBase;
    }

    /*
     * Finds java/lang/Object.java rooted at src, creates a copy of that file
     * _with the modified doc comment_ for Object.equals in dst, and returns
     * the path to that copy.
     */
    private Path createPatchedJavaLangObject(Path src, Path dst, String newComment) {
        var obj = Path.of("java/lang/Object.java");
        try {
            Optional<Path> files;
            try (var s = Files.find(src, Integer.MAX_VALUE,
                    (p, attr) -> attr.isRegularFile() && p.endsWith(obj))) {
                files = s.findAny();
            }
            if (files.isEmpty()) {
                throw new SkippedException("Couldn't find '" + obj + "' at '" + src + "'");
            }
            var original = files.get();
            out.println("Found '" + obj + "' at " + original.toAbsolutePath());
            var source = Files.readString(original);
            var region = findDocCommentRegion(original);
            var newSource = source.substring(0, region.start)
                    + newComment
                    + source.substring(region.end);
            // create intermediate directories in the destination first, otherwise
            // writeString will throw java.nio.file.NoSuchFileException
            var copy = dst.resolve(src.relativize(original));
            out.println("To be copied to '" + copy + "'");
            if (Files.notExists(copy.getParent())) {
                Files.createDirectories(copy.getParent());
            }
            return Files.writeString(copy, newSource, StandardOpenOption.CREATE);
        } catch (IOException e) {
            throw new SkippedException("Couldn't create patched '" + obj + "'", e);
        }
    }

    private static SourceRegion findDocCommentRegion(Path src) throws IOException {
        // to _reliably_ find the doc comment, parse the file and find source
        // position of the doc tree corresponding to that comment
        var compiler = ToolProvider.getSystemJavaCompiler();
        var fileManager = compiler.getStandardFileManager(null, null, null);
        var fileObject = fileManager.getJavaFileObjects(src).iterator().next();
        var task = (JavacTask) compiler.getTask(null, null, null, null, null, List.of(fileObject));
        Iterator<? extends CompilationUnitTree> iterator = task.parse().iterator();
        if (!iterator.hasNext()) {
            throw new SkippedException("Couldn't parse '" + src + "'");
        }
        var tree = iterator.next();
        var pathToEqualsMethod = findMethod(tree);
        if (pathToEqualsMethod == null) {
            throw new SkippedException("Couldn't find the equals method in '" + src + "'");
        }
        var trees = DocTrees.instance(task);
        DocCommentTree docCommentTree = trees.getDocCommentTree(pathToEqualsMethod);
        if (docCommentTree == null) {
            throw new SkippedException("Couldn't find documentation for the equals method at '" + src + "'");
        }
        var positions = trees.getSourcePositions();
        long start = positions.getStartPosition(null, docCommentTree, docCommentTree);
        long end = positions.getEndPosition(null, docCommentTree, docCommentTree);
        return new SourceRegion((int) start, (int) end);
    }

    private static TreePath findMethod(Tree src) {

        class Result extends RuntimeException {
            final TreePath p;

            Result(TreePath p) {
                super("", null, false, false); // lightweight exception to short-circuit scan
                this.p = p;
            }
        }

        var scanner = new TreePathScanner<Void, Void>() {
            @Override
            public Void visitMethod(MethodTree m, Void unused) {
                boolean solelyPublic = m.getModifiers().getFlags().equals(Set.of(Modifier.PUBLIC));
                if (!solelyPublic) {
                    return null;
                }
                var returnType = m.getReturnType();
                boolean returnsBoolean = returnType != null
                        && returnType.getKind() == Tree.Kind.PRIMITIVE_TYPE
                        && ((PrimitiveTypeTree) returnType).getPrimitiveTypeKind() == TypeKind.BOOLEAN;
                if (!returnsBoolean) {
                    return null;
                }
                boolean hasNameEquals = m.getName().toString().equals("equals");
                if (!hasNameEquals) {
                    return null;
                }
                List<? extends VariableTree> params = m.getParameters();
                if (params.size() != 1)
                    return null;
                var parameterType = params.getFirst().getType();
                if (parameterType.getKind() == Tree.Kind.IDENTIFIER &&
                        ((IdentifierTree) parameterType).getName().toString().equals("Object")) {
                    throw new Result(getCurrentPath());
                }
                return null;
            }
        };
        try {
            scanner.scan(src, null);
            return null; // not found
        } catch (Result e) {
            return e.p; // found
        }
    }

    record SourceRegion(int start, int end) { }
}
