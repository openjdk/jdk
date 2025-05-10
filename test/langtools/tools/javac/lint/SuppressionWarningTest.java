/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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

/**
 * @test
 * @bug 8344159
 * @summary Test "suppressed" and "suppressed-option" lint warnings
 * @library /tools/lib
 * @modules
 *      jdk.compiler/com.sun.tools.javac.api
 *      jdk.compiler/com.sun.tools.javac.code
 *      jdk.compiler/com.sun.tools.javac.main
 * @build toolbox.ToolBox toolbox.JavacTask toolbox.JarTask
 * @run main SuppressionWarningTest
 */

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Method;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.sun.tools.javac.code.Lint;
import com.sun.tools.javac.code.Lint.LintCategory;
import com.sun.tools.javac.code.Source;

import toolbox.JarTask;
import toolbox.JavacTask;
import toolbox.Task.Mode;
import toolbox.Task;
import toolbox.TestRunner;
import toolbox.ToolBox;

import static com.sun.tools.javac.code.Lint.LintCategory.*;

public class SuppressionWarningTest extends TestRunner {

    // Test cases for testSuppressWarnings()
    public static final List<SuppressTest> SUPPRESS_WARNINGS_TEST_CASES = Stream.of(LintCategory.values())
      .filter(category -> category.suppressionTracking)
      .map(category -> switch (category) {
        case AUXILIARYCLASS -> new SuppressTest(category,
            "compiler.warn.auxiliary.class.accessed.from.outside.of.its.source.file",
            null,
            """
            public class Class1 { }
            class AuxClass { }
            """,
            """
            @OUTER@
            public class Class2 {
                @INNER@
                public Object obj = new AuxClass();
            }
            """
        );

        case CAST -> new SuppressTest(category,
            "compiler.warn.redundant.cast",
            null,
            """
            @OUTER@
            public class Test {
                @INNER@
                public Object obj = (Object)new Object();
            }
            """
        );

        case CLASSFILE -> null; // skip, too hard to simluate

        case DANGLING_DOC_COMMENTS -> new SuppressTest(category,
            "compiler.warn.dangling.doc.comment",
            null,
            """
            @OUTER@
            public class Test {
                /** Dangling comment */
                /** Javadoc comment */
                @INNER@
                public void foo() {
                }
            }
            """
        );

        case DEPRECATION -> new SuppressTest(category,
            "compiler.warn.has.been.deprecated",
            null,
            """
            public class Super {
                @Deprecated
                public void foo() { }
            }
            """,
            """
            @OUTER@
            public class Sub extends Super {
                @INNER@
                @Override
                public void foo() { }
            }
            """
        );

        case DEP_ANN -> new SuppressTest(category,
            "compiler.warn.missing.deprecated.annotation",
            null,
            """
            @OUTER@
            public class Test {
                @INNER@
                public class TestSub {
                    /** @deprecated */
                    public void method() { }
                }
            }
            """
        );

        case DIVZERO -> new SuppressTest(category,
            "compiler.warn.div.zero",
            null,
            """
            @OUTER@
            public class Test {
                @INNER@
                public int method() {
                    return 1/0;
                }
            }
            """
        );

        case EMPTY -> new SuppressTest(category,
            "compiler.warn.empty.if",
            null,
            """
            @OUTER@
            public class Test {
                @INNER@
                public void method(boolean x) {
                    if (x);
                }
            }
            """
        );

        case EXPORTS -> new SuppressTest(category,
            "compiler.warn.leaks.not.accessible",
            null,
            """
            module mod {
                exports pkg1;
            }
            """,
            """
            // @MODULE@:mod
            package pkg1;
            @OUTER@
            public class Class1 {
                @INNER@
                public pkg2.Class2 obj2;    // warning here
            }
            """,
            """
            // @MODULE@:mod
            package pkg2;
            public class Class2 {
            }
            """
        );

        case FALLTHROUGH -> new SuppressTest(category,
            "compiler.warn.possible.fall-through.into.case",
            null,
            """
            @OUTER@
            public class Test {
                @INNER@
                public void method(int x) {
                    switch (x) {
                    case 1:
                        System.out.println(1);
                    default:
                        System.out.println(0);
                    }
                }
            }
            """
        );

        case FINALLY -> new SuppressTest(category,
            "compiler.warn.finally.cannot.complete",
            null,
            """
            @OUTER@
            public class Test {
                @INNER@
                public void method(int x) {
                    try {
                        System.out.println(x);
                    } finally {
                        throw new RuntimeException();
                    }
                }
            }
            """
        );

        case INCUBATING -> null; // skip, too hard to simluate reliably over time

        case LOSSY_CONVERSIONS -> new SuppressTest(category,
            "compiler.warn.possible.loss.of.precision",
            null,
            """
            @OUTER@
            public class Test {
                @INNER@
                public void method() {
                    long b = 1L;
                    b += 0.1 * 3L;
                }
            }
            """
        );

        case MISSING_EXPLICIT_CTOR -> new SuppressTest(category,
            "compiler.warn.missing-explicit-ctor",
            null,
            """
            module mod {
                exports pkg1;
            }
            """,
            """
            package pkg1;
            @OUTER@
            public class Class1 {
                public Class1(int x) {
                }
                @INNER@
                public static class Sub {
                }
            }
            """
        );

        case MODULE -> new SuppressTest(category,
            "compiler.warn.poor.choice.for.module.name",
            null,
            """
            @OUTER@
            module mod0 {
            }
            """
        );

        case OPENS -> new SuppressTest(category,
            "compiler.warn.package.empty.or.not.found",
            null,
            """
            @OUTER@
            module mod {
                opens pkg1;
            }
            """
        );

        // This test case only works on MacOS
        case OUTPUT_FILE_CLASH ->
            System.getProperty("os.name").startsWith("Mac") ?
              new SuppressTest(category,
                "compiler.warn.output.file.clash",
                null,
                """
                @OUTER@
                public class Test {
                    interface Cafe\u0301 {      // macos normalizes "e" + U0301 -> U00e9
                    }
                    interface Caf\u00e9 {
                    }
                }
                """
              ) : null;

        case OVERLOADS -> new SuppressTest(category,
            "compiler.warn.potentially.ambiguous.overload",
            null,
            """
            import java.util.function.*;
            @OUTER@
            public class Super {
                public void foo(IntConsumer c) {
                }
                @INNER@
                public void foo(Consumer<Integer> c) {
                }
            }
            """
        );

        case OVERRIDES -> new SuppressTest(category,
            "compiler.warn.override.equals.but.not.hashcode",
            null,
            """
            @OUTER@
            public class Test {
                @INNER@
                public class Test2 {
                    public boolean equals(Object obj) {
                        return false;
                    }
                }
            }
            """
        );

        case PROCESSING -> null;    // skip for now

        case RAW -> new SuppressTest(category,
            "compiler.warn.raw.class.use",
            null,
            """
            @OUTER@
            public class Test {
                @INNER@
                public void foo() {
                    Iterable i = null;
                }
            }
            """
        );

        case REMOVAL -> new SuppressTest(category,
            "compiler.warn.has.been.deprecated.for.removal",
            null,
            """
            public class Super {
                @Deprecated(forRemoval = true)
                public void foo() { }
            }
            """,
            """
            @OUTER@
            public class Sub extends Super {
                @INNER@
                @Override
                public void foo() { }
            }
            """
        );

        // This test case requires special support; see testSuppressWarnings()
        case REQUIRES_AUTOMATIC -> new SuppressTest(category,
            "compiler.warn.requires.automatic",
            null,
            """
            @OUTER@
            module m1x {
                requires randomjar;
            }
            """
        );

        // This test case requires special support; see testSuppressWarnings()
        case REQUIRES_TRANSITIVE_AUTOMATIC -> new SuppressTest(category,
            "compiler.warn.requires.transitive.automatic",
            null,
            """
            @OUTER@
            module m1x {
                requires transitive randomjar;
            }
            """
        );

        case SERIAL -> new SuppressTest(category,
            "compiler.warn.missing.SVUID",
            null,
            """
            @OUTER@
            public class Test {
                @INNER@
                public static class Inner implements java.io.Serializable {
                    public int x;
                }
            }
            """
        );

        case STATIC -> new SuppressTest(category,
            "compiler.warn.static.not.qualified.by.type",
            null,
            """
            @OUTER@
            public class Test {
                public static void foo() {
                }
                @INNER@
                public void bar() {
                    this.foo();
                }
            }
            """
        );

        case STRICTFP -> new SuppressTest(category,
            "compiler.warn.strictfp",
            null,
            """
            @OUTER@
            public class Test {
                @INNER@
                public strictfp void foo() {
                }
            }
            """
        );

        case SYNCHRONIZATION -> new SuppressTest(category,
            "compiler.warn.attempt.to.synchronize.on.instance.of.value.based.class",
            null,
            """
            @OUTER@
            public class Outer {
                @INNER@
                public void foo() {
                    Integer i = 42;
                    synchronized (i) {
                    }
                }
            }
            """
        );

        case TEXT_BLOCKS -> new SuppressTest(category,
            "compiler.warn.trailing.white.space.will.be.removed",
            null,
            """
            @OUTER@
            public class Test {
                public void foo() {
                    String s =
                        \"\"\"
                        add trailing spaces here:
                        \"\"\";
                }
            }
            """.replaceAll("add trailing spaces here:", "$0    ")
        );

        case THIS_ESCAPE -> new SuppressTest(category,
            "compiler.warn.possible.this.escape",
            null,
            """
            @OUTER@
            public class Outer {
                @INNER@
                public static class Inner {
                    public Inner() {
                        leak();
                    }
                    public void leak() { }
                }
            }
            """
        );

        case TRY -> new SuppressTest(category,
            "compiler.warn.try.explicit.close.call",
            null,
            """
            import java.io.*;
            @OUTER@
            public class Outer {
                @INNER@
                public void foo() throws IOException {
                    try (InputStream in = new FileInputStream("x")) {
                        in.close();
                    }
                }
            }
            """
        );

        case UNCHECKED -> new SuppressTest(category,
            "compiler.warn.prob.found.req: (compiler.misc.unchecked.cast.to.type)",
            null,
            """
            @OUTER@
            public class Test {
                public void foo() {
                    Iterable<?> c = null;
                    @INNER@
                    Iterable<Short> t = (Iterable<Short>)c, s = null;
                }
            }
            """
        );

        case VARARGS -> new SuppressTest(category,
            "compiler.warn.varargs.unsafe.use.varargs.param",
            null,
            """
            @OUTER@
            public class Test {
                @INNER@
                @SafeVarargs
                public static <T> void bar(final T... barArgs) {
                    baz(barArgs);
                }
                public static <T> void baz(final T[] bazArgs) {
                }
            }
            """
        );

        // This test case requires special support; see testSuppressWarnings()
        case PREVIEW -> new SuppressTest(category,
            "compiler.warn.preview.feature.use",
            new String[] {
                "--enable-preview",
                "-XDforcePreview"
            },
            """
            @OUTER@
            public class Test {
                @INNER@
                public Test(Object x) {
                    int value = x instanceof Integer i ? i : -1;
                }
            }
            """
        );

        case RESTRICTED -> new SuppressTest(category,
            "compiler.warn.restricted.method",
            null,
            """
            @OUTER@
            public class Test {
                @INNER@
                public void foo() {
                    System.load("");
                }
            }
            """
        );

        default -> throw new AssertionError("missing test case for " + category);

      })
      .filter(Objects::nonNull)         // skip categories with no test case defined
      .collect(Collectors.toList());

    protected final ToolBox tb;

    public SuppressionWarningTest() {
        super(System.err);
        tb = new ToolBox();
    }

    public static void main(String... args) throws Exception {
        SuppressionWarningTest test = new SuppressionWarningTest();

        // Run parameterized tests
        test.runTestsMulti(m -> switch (m.getName()) {
          case "testSuppressWarnings" ->        SUPPRESS_WARNINGS_TEST_CASES.stream()
                                                  .map(testCase -> new Object[] { testCase });
          case "testUselessAnnotation" ->       Stream.of(LintCategory.values())
                                                  .filter(category -> category.suppressionTracking)
                                                  .map(category -> new Object[] { category });
          case "testUselessLintFlag" ->         Stream.of(LintCategory.values())
                                                  .filter(category -> category.suppressionTracking)
                                                  .map(category -> new Object[] { category });
          case "testSelfSuppression" ->         Stream.of(RAW, SUPPRESSION)
                                                  .map(category -> new Object[] { category });
          case "testOverloads" ->               Stream.<Object[]>of(new Object[0]);    // no parameters for this test
          case "testThisEscape" ->              Stream.<Object[]>of(new Object[0]);    // no parameters for this test
          default -> throw new AssertionError("missing params for " + m);
        });
    }

    // We are testing all combinations of nested @SuppressWarning annotations and lint flags
    @Test
    public void testSuppressWarnings(SuppressTest test) throws Exception {

        // Setup directories
        Path base = Paths.get("testSuppressWarnings");
        resetCompileDirectories(base);

        // Detect if any modules are being compiled; if so we need to create an extra source directory level
        Pattern moduleDecl = Pattern.compile("module\\s+(\\S*).*");
        Set<String> moduleNames = test.sources().stream()
          .flatMap(source -> Stream.of(source.split("\\n")))
          .map(moduleDecl::matcher)
          .filter(Matcher::matches)
          .map(matcher -> matcher.group(1))
          .collect(Collectors.toSet());

        // Special JAR file support for REQUIRES_AUTOMATIC and REQUIRES_TRANSITIVE_AUTOMATIC
        Path modulePath = base.resolve("modules");
        resetDirectory(modulePath);
        LintCategory category = test.category();
        switch (category) {
        case REQUIRES_AUTOMATIC:
        case REQUIRES_TRANSITIVE_AUTOMATIC:

            // Compile a simple automatic module (randomjar-1.0)
            Path randomJarBase = base.resolve("randomjar");
            tb.writeJavaFiles(getSourcesDir(randomJarBase), "package api; public class Api {}");
            List<String> log = compile(randomJarBase, Task.Expect.SUCCESS, "-Werror");
            if (!log.isEmpty()) {
                throw new AssertionError(String.format(
                  "non-empty log output:%n  %s", log.stream().collect(Collectors.joining("\n  "))));
            }

            // JAR it up
            Path automaticJar = modulePath.resolve("randomjar-1.0.jar");
            new JarTask(tb, automaticJar)
              .baseDir(getClassesDir(randomJarBase))
              .files("api/Api.class")
              .run();
            break;

        default:
            modulePath = null;
            break;
        };

        // Create a @SuppressWarnings annotation
        String annotation = String.format("@SuppressWarnings(\"%s\")", category.option);

        // See which annotation substitutions this test supports
        boolean hasOuterAnnotation = test.sources().stream().anyMatch(source -> source.contains("@OUTER@"));
        boolean hasInnerAnnotation = test.sources().stream().anyMatch(source -> source.contains("@INNER@"));

        // Try all combinations of inner and outer @SuppressWarnings
        boolean[] booleans = new boolean[] { false, true };
        for (boolean outerAnnotation : booleans) { for (boolean innerAnnotation : booleans) {

          // Skip this scenario if not supported by test case
          if ((outerAnnotation && !hasOuterAnnotation) || (innerAnnotation && !hasInnerAnnotation))
              continue;

          // Insert or comment out the @SuppressWarnings annotations in the source templates
          String[] sources = test.sources().stream()
            .map(source -> source.replace("@OUTER@",
              String.format("%s@SuppressWarnings(\"%s\")", outerAnnotation ? "" : "//", category.option)))
            .map(source -> source.replace("@INNER@",
              String.format("%s@SuppressWarnings(\"%s\")", innerAnnotation ? "" : "//", category.option)))
            .toArray(String[]::new);
          for (String source : sources) {
              Path pkgRoot = getSourcesDir(base);
              String moduleName = Optional.of("@MODULE@:(\\S+)")
                                  .map(Pattern::compile)
                                  .map(p -> p.matcher(source))
                                  .filter(Matcher::find)
                                  .map(m -> m.group(1))
                                  .orElse(null);
              if (moduleName != null) {                                     // add an extra directory for module
                  if (!moduleNames.contains(moduleName))
                      throw new AssertionError(String.format("unknown module \"%s\" in %s", moduleName, category));
                  pkgRoot = pkgRoot.resolve(moduleName);
              }
              tb.writeJavaFiles(pkgRoot, source);
          }

          // Try all combinations of lint flags
          for (boolean enableCategory : booleans) {                         // [-]category
            for (boolean enableSuppression : booleans) {                    // [-]suppression
              for (boolean enableSuppressionOption : booleans) {            // [-]suppression-option

                // Should we expect the warning to be emitted?
                boolean expectCategoryWarning = category.annotationSuppression ?
                  enableCategory && !outerAnnotation && !innerAnnotation : enableCategory;

                // Should we expect the SUPPRESSION warning to be emitted?
                boolean expectSuppressionWarning = category.annotationSuppression ?
                  enableSuppression && outerAnnotation && innerAnnotation :   // only if both, outer is redundant
                  enableSuppression && (outerAnnotation || innerAnnotation);  // either one is always redundant

                // Should we expect the SUPPRESSION_OPTION warning to be emitted?
                boolean expectSuppressionOptionWarning = category.annotationSuppression ?
                  enableSuppressionOption && !enableCategory && (outerAnnotation || innerAnnotation) :
                  false;

                // Prepare command line flags
                ArrayList<String> flags = new ArrayList<>();
                if (modulePath != null) {
                    flags.add("--module-path");
                    flags.add(modulePath.toString());
                }
                flags.add("--release");
                flags.add(Source.DEFAULT.name);
                flags.addAll(test.compileFlags());

                ArrayList<String> lints = new ArrayList<>();
                lints.add(String.format("%s%s", enableCategory ? "" : "-", category.option));
                if (enableSuppression)
                    lints.add(SUPPRESSION.option);
                if (enableSuppressionOption) {
                    lints.add(OPTIONS.option);
                    lints.add(SUPPRESSION_OPTION.option);
                }
                if (!lints.isEmpty())
                    flags.add("-Xlint:" + lints.stream().collect(Collectors.joining(",")));

                // Test case description
                String description = String.format("[%s] outer=%s inner=%s enable=%s flags=\"%s\"",
                  category, outerAnnotation, innerAnnotation, enableCategory,
                  flags.stream().collect(Collectors.joining(" ")));

                // Only print log if test case fails
                StringWriter buf = new StringWriter();
                PrintWriter log = new PrintWriter(buf);
                try {

                  // Logging
                  log.println(String.format(">>> Test  START: %s", description));
                  Stream.of(sources).forEach(log::println);
                  log.println(String.format(">>> expectCategoryWarning=%s", expectCategoryWarning));
                  log.println(String.format(">>> expectSuppressionWarning=%s", expectSuppressionWarning));
                  log.println(String.format(">>> expectSuppressionOptionWarning=%s", expectSuppressionOptionWarning));

                  // Compile sources and get log output
                  List<String> output = compile(base, Task.Expect.SUCCESS, flags.toArray(new String[0]));

                  // Scrub insignificant log output
                  output.removeIf(line -> line.matches("[0-9]+ (error|warning)s?"));
                  output.removeIf(line -> line.contains("compiler.err.warnings.and.werror"));
                  output.removeIf(line -> line.matches("- compiler\\.note\\..*"));   // mandatory warning "recompile" etc.

                  // See which warnings appeared
                  boolean foundSuppressionWarning = output.removeIf(
                    line -> line.contains("compiler.warn.unnecessary.warning.suppression"));
                  boolean foundSuppressionOptionWarning = output.removeIf(
                    line -> line.contains("compiler.warn.unnecessary.lint.warning.suppression"));
                  boolean foundCategoryWarning = output.removeIf(line -> line.contains(test.warningKey()));

                  // Compare that vs. expectations
                  if (foundCategoryWarning != expectCategoryWarning) {
                      throw new AssertionError(String.format("%s: category warning: found=%s but expected=%s",
                        description, foundCategoryWarning, expectCategoryWarning));
                  }
                  if (foundSuppressionWarning != expectSuppressionWarning) {
                      throw new AssertionError(String.format("%s: \"%s\" warning: found=%s but expected=%s",
                        description, SUPPRESSION.option, foundSuppressionWarning, expectSuppressionWarning));
                  }
                  if (foundSuppressionOptionWarning != expectSuppressionOptionWarning) {
                      throw new AssertionError(String.format("%s: \"%s\" warning: found=%s but expected=%s",
                        description, SUPPRESSION_OPTION.option, foundSuppressionOptionWarning,
                        expectSuppressionOptionWarning));
                  }

                  // There shouldn't be any other warnings
                  if (!output.isEmpty()) {
                      throw new AssertionError(String.format(
                        "%s: %d unexpected warning(s): %s", description, output.size(), output));
                  }

                  // Done
                  log.println(String.format("<<< Test PASSED: %s", description));
                } catch (AssertionError e) {
                    log.println(String.format("<<< Test FAILED: %s", description));
                    log.flush();
                    out.print(buf);
                    throw e;
                }
              }
            }
          }
        } }
    }

    // Test a @SuppressWarning annotation that suppresses nothing
    @Test
    public void testUselessAnnotation(LintCategory category) throws Exception {
        compileAndExpectWarning(
          "compiler.warn.unnecessary.warning.suppression",
          String.format(
            """
                @SuppressWarnings(\"%s\")
                public class Test { }
            """,
            category.option),
          String.format("-Xlint:%s", SUPPRESSION.option));
    }

    // Test a -Xlint:-foo flag that suppresses nothing
    @Test
    public void testUselessLintFlag(LintCategory category) throws Exception {
        compileAndExpectWarning(
          "compiler.warn.unnecessary.lint.warning.suppression",
          """
              public class Test {
              }
          """,
          String.format("-Xlint:%s", OPTIONS.option),
          String.format("-Xlint:%s", SUPPRESSION_OPTION.option),
          String.format("-Xlint:-%s", category.option));
    }

    // Test the suppression of SUPPRESSION itself, which should always work,
    // even when the same annotation uselessly suppresses some other category.
    @Test
    public void testSelfSuppression(LintCategory category) throws Exception {

        // Test category and SUPPRESSION in the same annotation
        compileAndExpectSuccess(
          String.format(
            """
                @SuppressWarnings({ \"%s\", \"%s\" })
                public class Test {
                }
            """,
            category.option,        // this is actually a useless suppression
            SUPPRESSION.option),    // but this prevents us from reporting it
          String.format("-Xlint:%s", SUPPRESSION.option));

        // Test category and SUPPRESSION in nested annotations
        compileAndExpectSuccess(
          String.format(
            """
                @SuppressWarnings(\"%s\")       // suppress useless suppression warnings
                public class Test {
                    @SuppressWarnings(\"%s\")   // a useless suppression
                    public class Sub { }
                }
            """,
            SUPPRESSION.option,     // this prevents us from reporting the nested useless suppression
            category.option),       // this is a useless suppression
          String.format("-Xlint:%s", SUPPRESSION.option));
    }

    // Test OVERLOADS which has tricky "either-or" suppression
    @Test
    public void testOverloads() throws Exception {
        compileAndExpectSuccess(
          """
          import java.util.function.*;
          public class Super {
              @SuppressWarnings("overloads")
              public void foo(IntConsumer c) {
              }
              @SuppressWarnings("overloads")
              public void foo(Consumer<Integer> c) {
              }
          }
          """,
          String.format("-Xlint:%s", OVERLOADS.option),
          String.format("-Xlint:%s", SUPPRESSION.option));
    }

    // Test THIS_ESCAPE which has tricky control-flow based suppression
    @Test
    public void testThisEscape() throws Exception {
        compileAndExpectSuccess(
          """
          public class Test {
              public Test() {
                  this(0);
              }
              @SuppressWarnings("this-escape")
              private Test(int x) {
                  this.leak();
              }
              protected void leak() { }
          }
          """,
          String.format("-Xlint:%s", THIS_ESCAPE.option),
          String.format("-Xlint:%s", SUPPRESSION.option));
    }

    public void compileAndExpectWarning(String errorKey, String source, String... flags) throws Exception {

        // Setup source & destination diretories
        Path base = Paths.get("compileAndExpectWarning");
        resetCompileDirectories(base);

        // Write source file
        tb.writeJavaFiles(getSourcesDir(base), source);

        // Compile sources and verify we got the warning
        List<String> log = compile(base, Task.Expect.FAIL, addWerror(flags));
        if (log.stream().noneMatch(line -> line.contains(errorKey))) {
            throw new AssertionError(String.format(
              "did not find \"%s\" in log output:%n  %s",
              errorKey, log.stream().collect(Collectors.joining("\n  "))));
        }
    }

    public void compileAndExpectSuccess(String source, String... flags) throws Exception {

        // Setup source & destination diretories
        Path base = Paths.get("compileAndExpectSuccess");
        resetCompileDirectories(base);

        // Write source file
        tb.writeJavaFiles(getSourcesDir(base), source);

        // Compile sources and verify there is no log output
        List<String> log = compile(base, Task.Expect.SUCCESS, addWerror(flags));
        if (!log.isEmpty()) {
            throw new AssertionError(String.format(
              "non-empty log output:%n  %s", log.stream().collect(Collectors.joining("\n  "))));
        }
    }

    private List<String> compile(Path base, Task.Expect expectation, String... flags) throws Exception {
        ArrayList<String> options = new ArrayList<>();
        options.add("-XDrawDiagnostics");
        Stream.of(flags).forEach(options::add);
        List<String> log;
        try {
            log = new JavacTask(tb, Mode.CMDLINE)
              .options(options.toArray(new String[0]))
              .files(tb.findJavaFiles(getSourcesDir(base)))
              .outdir(getClassesDir(base))
              .run(expectation)
              .writeAll()
              .getOutputLines(Task.OutputKind.DIRECT);
        } catch (Task.TaskError e) {
            throw new AssertionError(String.format(
              "compile in %s failed: %s", getSourcesDir(base), e.getMessage()), e);
        }
        log.removeIf(line -> line.trim().isEmpty());
        return log;
    }

    private Path getSourcesDir(Path base) {
        return base.resolve("sources");
    }

    private Path getClassesDir(Path base) {
        return base.resolve("classes");
    }

    private void resetCompileDirectories(Path base) throws IOException {
        for (Path dir : List.of(getSourcesDir(base), getClassesDir(base)))
            resetDirectory(dir);
    }

    private void resetDirectory(Path dir) throws IOException {
        if (Files.exists(dir, LinkOption.NOFOLLOW_LINKS))
            Files.walkFileTree(dir, new Deleter());
        Files.createDirectories(dir);
    }

    private String[] addWerror(String[] flags) {
        return Stream.concat(Stream.of(flags), Stream.of("-Werror")).toArray(String[]::new);
    }

// SuppressTest

    private record SuppressTest(
        LintCategory category,          // The Lint category being tested
        String warningKey,              // Expected warning message key in compiler.properties
        List<String> compileFlags,      // Any required compilation flags
        List<String> sources            // Source files with @MODULE@, @OUTER@ and @INNER@ placeholders
    ) {
        SuppressTest(LintCategory category, String warningKey, String[] compileFlags, String... sources) {
            this(category, warningKey, List.of(compileFlags != null ? compileFlags : new String[0]), List.of(sources));
        }
    }

// Deleter

    private static class Deleter extends SimpleFileVisitor<Path> {

        @Override
        public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
            Files.delete(file);
            return FileVisitResult.CONTINUE;
        }
    }
}
