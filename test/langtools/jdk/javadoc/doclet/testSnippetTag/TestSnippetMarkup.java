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

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Reader;
import java.io.StringReader;
import java.io.StringWriter;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;
import java.util.regex.MatchResult;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.lang.model.element.Modifier;
import javax.lang.model.element.NestingKind;
import javax.tools.FileObject;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;

import toolbox.ToolBox;

import static javax.tools.DocumentationTool.Location.DOCUMENTATION_OUTPUT;

/*
 * @test
 * @bug 8266666
 * @summary Implementation for snippets
 * @library /tools/lib ../../lib
 * @modules jdk.compiler/com.sun.tools.javac.api
 *          jdk.compiler/com.sun.tools.javac.main
 *          jdk.javadoc/jdk.javadoc.internal.tool
 * @build javadoc.tester.* toolbox.ToolBox toolbox.ModuleBuilder builder.ClassBuilder
 * @run main TestSnippetMarkup
 */
public class TestSnippetMarkup extends SnippetTester {

    private final ToolBox tb = new ToolBox();

    private TestSnippetMarkup() {}

    public static void main(String... args) throws Exception {
        new TestSnippetMarkup().runTests(m -> new Object[]{Paths.get(m.getName())});
    }

    /*
     * The semantics of expectedOutput depends on the test case this record is
     * used in.
     */
    record TestCase(String region, String input, String expectedOutput) {
        TestCase(String input, String expectedOutput) {
            this("", input, expectedOutput);
        }
    }

    // @highlight [region|region=<name>]
    //            [regex=<val>|substring=<val>]
    //            [type=bold|italics|highlighted]
    //            [:]
    @Test
    public void testHighlight(Path base) throws Exception {
        var testCases = List.of(
                new TestCase( // FIXME: newline should not be included
                        """
                                First line  // @highlight
                                  Second line
                                """,
                        """
                                <span class="bold">First line
                                </span>  Second line
                                """),
                new TestCase(
                        """
                                First line  // @highlight regex="\\w" type="bold"
                                  Second line
                                """,
                        """
                                <span class="bold">First</span> <span class="bold">line</span>
                                  Second line
                                """),
                new TestCase( // FIXME: newline should not be included
                        """
                                First line  // @highlight @highlight regex="\\w" type="bold"
                                  Second line
                                """,
                        """
                                <span class="bold">First line
                                </span>  Second line
                                """
                ));
        testPositive(base, testCases);
    }

    // @replace [region|region=<name>]
    //          [regex=<val>|substring=<val>]
    //          [replacement=<val>]
    //          [:]
    @Test
    public void testReplace(Path base) throws Exception {
        var testCases = List.of(
                new TestCase(
                        """
                                First line  // @replace regex="\\w" replacement="."
                                  Second line
                                """,
                        """
                                ..... ....
                                  Second line
                                """),
                new TestCase( // "substring" is not treated like "regex"
                        """
                                First line  // @replace substring="\\w" replacement="."
                                  Second line
                                """,
                        """
                                First line
                                  Second line
                                """
                ),
                new TestCase(
                        """
                                First line  // @replace substring="i" replacement="."
                                  Second line
                                """,
                        """
                                F.rst l.ne
                                  Second line
                                """
                ));
        testPositive(base, testCases);
    }

    // @link [region|region=<name>]
    //       [regex=<val>|substring=<val>]
    //       [target=<val>]
    //       [type=link|linkplain]
    //       [:]
    @Test
    public void testLink(Path base) throws Exception {
        var testCases = List.of(
                new TestCase(
                        """
                                First line  // @link regex="\\w" target="java.lang.Object#Object"
                                  Second line
                                """,
                        replace("""
                                link(First) link(line)
                                  Second line
                                """, "link\\((.+?)\\)", r -> link(true, "java.lang.Object#Object", r.group(1)))
                ));
        testPositive(base, testCases);
    }

    @Test
    public void testCornerCases(Path base) throws Exception {
        var testCases = List.of(
                new TestCase( // This is how one might represent a unicode escape sequence uninterpreted, if required.
                        """
                                \\$0041  // @replace substring="$" replacement="u"
                                """,
                        """
                                \\u0041
                                """
                ),
                new TestCase( // This is how one might represent `*/` without ending an enclosing comment, if required.
                              // A non-whitespace character that is also not `*` is needed before `*` so that `*`
                              // is not confused with the optional doc comment decoration.
                              // (We cannot use, for example, `**$` or ` *$`.)
                        """
                                a*$  // @replace substring="$" replacement="/"
                                """,
                        """
                                a*/
                                """
                )
        );
        testPositive(base, testCases);
    }

    private void testPositive(Path base, List<TestCase> testCases)
            throws IOException {
        StringBuilder methods = new StringBuilder();
        forEachNumbered(testCases, (i, n) -> {
            String r = i.region.isBlank() ? "" : "region=" + i.region;
            var methodDef = """

                    /**
                    {@snippet %s:
                    %s}*/
                    public void case%s() {}
                    """.formatted(r, i.input, n);
            methods.append(methodDef);
        });
        var classDef = """
                public class A {
                %s
                }
                """.formatted(methods.toString());
        Path src = Files.createDirectories(base.resolve("src"));
        tb.writeJavaFiles(src, classDef);
        javadoc("-d", base.resolve("out").toString(),
                "-sourcepath", src.toString(),
                src.resolve("A.java").toString());
        checkExit(Exit.OK);
        checkNoCrashes();
        forEachNumbered(testCases, (t, index) -> {
            String html = """
                        <span class="element-name">case%s</span>()</div>
                        <div class="block">
                        %s
                        </div>""".formatted(index, getSnippetHtmlRepresentation("A.html", t.expectedOutput()));
            checkOutput("A.html", true, html);
        });
    }

    // FIXME: move error (i.e. negative) tests from TestSnippetTag to here

    // @start region=<name> ... @end [region|region=<name>]
    @Test
    public void testStart(Path base) throws Exception {
        var testCases = new ArrayList<TestCase>();
        for (var variant : generateStartEndVariants()) {
            var t = new TestCase(variant.region,
                    """
                            First line
                              Second line // ###START
                              Third line
                              Fourth line // ###END
                            Fifth line
                            """.replaceFirst("###START", variant.start)
                            .replaceFirst("###END", variant.end),
                    """
                            Second line
                            Third line
                            Fourth line""");
            testCases.add(t);
        }
        testPositive(base, testCases);
    }

    private static String link(boolean linkPlain,
                               String targetReference,
                               String content)
            throws UncheckedIOException {

        // The HTML <a> tag generated from the @link snippet markup tag is the
        // same as that of the {@link} Standard doclet tag. This is specified
        // and can be used for comparison and testing.

        // generate documentation for {@link} to grab its HTML <a> tag;
        // generate documentation at low cost and do not interfere with the
        // calling test state; for that, do not create file trees, do not write
        // to std out/err, and generally try to keep everything in memory

        String source = """
                /** {@link %s %s} */
                public interface A { }
                """.formatted(targetReference, content);

        JavaFileObject src = new JavaFileObject() {
            @Override
            public Kind getKind() {return Kind.SOURCE;}

            @Override
            public boolean isNameCompatible(String simpleName, Kind kind) {
                return kind == Kind.SOURCE;
            }

            @Override
            public NestingKind getNestingKind() {return NestingKind.TOP_LEVEL;}

            @Override
            public Modifier getAccessLevel() {return Modifier.PUBLIC;}

            @Override
            public URI toUri() {throw new UnsupportedOperationException();}

            @Override
            public String getName() {return "A.java";}

            @Override
            public InputStream openInputStream() {
                return new ByteArrayInputStream(source.getBytes(StandardCharsets.UTF_8));
            }

            @Override
            public OutputStream openOutputStream() {
                throw new UnsupportedOperationException("Read only");
            }

            @Override
            public Reader openReader(boolean ignoreEncodingErrors) {
                return new StringReader(source);
            }

            @Override
            public CharSequence getCharContent(boolean ignoreEncodingErrors) {
                return source;
            }

            @Override
            public Writer openWriter() {
                throw new UnsupportedOperationException("Read only");
            }

            @Override
            public long getLastModified() {
                return 0;
            }

            @Override
            public boolean delete() {
                throw new UnsupportedOperationException("Read only");
            }
        };

        var documentationTool = ToolProvider.getSystemDocumentationTool();
        var writer = new StringWriter();

        // FileManager has to be StandardJavaFileManager; JavaDoc is adamant about it
        class InMemoryFileManager extends ToolBox.MemoryFileManager
                implements StandardJavaFileManager {

            private final StandardJavaFileManager delegate = documentationTool
                    .getStandardFileManager(null, null, null);

            @Override
            public Iterable<? extends JavaFileObject> getJavaFileObjectsFromFiles(Iterable<? extends File> files) {
                return delegate.getJavaFileObjectsFromFiles(files);
            }

            @Override
            public Iterable<? extends JavaFileObject> getJavaFileObjects(File... files) {
                return delegate.getJavaFileObjects(files);
            }

            @Override
            public Iterable<? extends JavaFileObject> getJavaFileObjectsFromStrings(Iterable<String> names) {
                return delegate.getJavaFileObjectsFromStrings(names);
            }

            @Override
            public Iterable<? extends JavaFileObject> getJavaFileObjects(String... names) {
                return delegate.getJavaFileObjects(names);
            }

            @Override
            public void setLocation(Location location, Iterable<? extends File> files) throws IOException {
                delegate.setLocation(location, files);
            }

            @Override
            public Iterable<? extends File> getLocation(Location location) {
                return delegate.getLocation(location);
            }

            @Override
            public FileObject getFileForOutput(Location location,
                                               String packageName,
                                               String relativeName,
                                               FileObject sibling) {
                return getJavaFileForOutput(location, packageName + relativeName, JavaFileObject.Kind.OTHER, null);
            }
        }
        try {
            var fileManager = new InMemoryFileManager();
            fileManager.setLocation(DOCUMENTATION_OUTPUT, Collections.singleton(new File(".")));
            // exclude extraneous output; we're only after @link
            List<String> options = List.of("--limit-modules", "java.base",
                    "-quiet", "-nohelp", "-noindex", "-nonavbar", "-nosince",
                    "-notimestamp", "-notree", "-Xdoclint:none");
            var documentationTask = documentationTool.getTask(null, fileManager,
                    null, null, options, List.of(src));
            if (!documentationTask.call()) {
                throw new IOException(writer.toString());
            }
            String output = fileManager.getFileString(DOCUMENTATION_OUTPUT, "A.html");
            // use the [^<>] regex to select HTML elements that immediately enclose "content"
            Matcher m = Pattern.compile("(?is)<a href=\"[^<>]*\" title=\"[^<>]*\" class=\"[^<>]*\"><code>"
                    + content + "</code></a>").matcher(output);
            if (!m.find()) {
                throw new IOException(output);
            }
            return m.group(0);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static String replace(String source,
                                  String regex,
                                  Function<MatchResult, String> replacer) {
        return Pattern.compile(regex).matcher(source).replaceAll(replacer);
    }

    private static final AtomicLong UNIQUE_INTEGER_NUMBER = new AtomicLong();

    private static Collection<StartEndVariant> generateStartEndVariants() {
        var variants = new ArrayList<StartEndVariant>();
        for (var start : startAttributes())
            for (var end : endAttributes()) {
                var region = uniqueValue();
                var v = new StartEndVariant(region,
                        "@start" + start.apply(region),
                        "@end" + end.apply(region));
                variants.add(v);
            }
        return variants;
    }

    private static String uniqueValue() {
        return "auto_generated_value_" + UNIQUE_INTEGER_NUMBER.incrementAndGet();
    }

    public static Collection<Function<String, String>> startAttributes() {
        return attributes("region");
    }

    private static Collection<Function<String, String>> endAttributes() {
        var variants = new ArrayList<Function<String, String>>();
        variants.add(value -> "");
        variants.add(value -> " region");
        variants.addAll(attributes("region"));
        return variants;
    }

    private static Collection<Function<String, String>> attributes(String name) {
        var variants = new ArrayList<Function<String, String>>();
        for (var whitespace1 : List.of(" ", "  "))
            for (var whitespace2 : List.of("", " "))
                for (var quote : List.of("", "'", "\""))
                    for (var whitespace3 : List.of("", " ")) {
                        Function<String, String> f = value ->
                                whitespace1 + name + whitespace2
                                        + "=" + whitespace3 + (quote + value + quote);
                        variants.add(f);
                    }
        return variants;
    }

    record StartEndVariant(String region, String start, String end) {}
}
