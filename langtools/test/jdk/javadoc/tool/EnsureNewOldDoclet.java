/*
 * Copyright (c) 2015, 2016, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8035473 8154482
 * @summary make sure the javadoc tool responds correctly to Xold,
 *          old doclets and taglets.
 * @library /tools/lib
 * @build toolbox.ToolBox toolbox.TestRunner
 * @run main EnsureNewOldDoclet
 */

import java.io.*;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import com.sun.javadoc.Tag;
import com.sun.source.doctree.DocTree;

import toolbox.*;


/**
 * This test ensures the doclet responds correctly when given
 * various conditions that force a fall back to the old javadoc
 * tool. The following condition in the order described will
 * force a dispatch to the old tool, -Xold, old doclet and old taglet.
 *
 */
public class EnsureNewOldDoclet extends TestRunner {

    final ToolBox tb;
    final File testSrc;
    final Path javadocPath;
    final ExecTask task;
    final String testClasses;
    final PrintStream ostream;

    final static String CLASS_NAME = "EnsureNewOldDoclet";
    final static String OLD_DOCLET_CLASS_NAME = CLASS_NAME + "$OldDoclet";
    final static String NEW_DOCLET_CLASS_NAME = CLASS_NAME + "$NewDoclet"; //unused
    final static String OLD_TAGLET_CLASS_NAME = CLASS_NAME + "$OldTaglet";
    final static String NEW_TAGLET_CLASS_NAME = CLASS_NAME + "$NewTaglet";

    final static Pattern OLD_HEADER = Pattern.compile("^Standard Doclet \\(Old\\) version.*");
    final static Pattern NEW_HEADER = Pattern.compile("^Standard Doclet version.*");


    final static String OLD_DOCLET_MARKER = "OLD_DOCLET_MARKER";
    final static String OLD_TAGLET_MARKER = "Registered: OldTaglet";

    final static String NEW_DOCLET_MARKER = "NEW_DOCLET_MARKER";
    final static String NEW_TAGLET_MARKER = "Registered Taglet " + CLASS_NAME + "\\$NewTaglet";

    final static Pattern WARN_TEXT = Pattern.compile("Users are strongly recommended to migrate" +
                                                    " to the new APIs.");
    final static String OLD_DOCLET_ERROR = "java.lang.NoSuchMethodException: " +
            CLASS_NAME +"\\$NewTaglet";
    final static Pattern NEW_DOCLET_ERROR = Pattern.compile(".*java.lang.ClassCastException.*Taglet " +
            CLASS_NAME + "\\$OldTaglet.*");

    final static String OLD_STDDOCLET = "com.sun.tools.doclets.standard.Standard";
    final static String NEW_STDDOCLET = "jdk.javadoc.internal.doclets.standard.Standard";


    public EnsureNewOldDoclet() throws Exception {
        super(System.err);
        ostream = System.err;
        testClasses = System.getProperty("test.classes");
        tb = new ToolBox();
        javadocPath = tb.getJDKTool("javadoc");
        task = new ExecTask(tb, javadocPath);
        testSrc = new File("Foo.java");
        generateSample(testSrc);
    }

    void generateSample(File testSrc) throws Exception {
        String nl = System.getProperty("line.separator");
        String src = Arrays.asList(
            "/**",
            " * A test class to test javadoc. Nothing more nothing less.",
            " */",
            " public class Foo{}").stream().collect(Collectors.joining(nl));
        tb.writeFile(testSrc.getPath(), src);
    }

    public static void main(String... args) throws Exception {
        new EnsureNewOldDoclet().runTests();
    }

    // input: nothing, default mode
    // outcome: new tool and new doclet
    @Test
    public void testDefault() throws Exception {
        setArgs("-classpath", ".", // insulates us from ambient classpath
                  testSrc.toString());
        Task.Result tr = task.run(Task.Expect.SUCCESS);
        List<String> out = tr.getOutputLines(Task.OutputKind.STDOUT);
        checkOutput(testName, out, NEW_HEADER);
    }

    // input: -Xold
    // outcome: old tool
    @Test
    public void testXold() throws Exception {
        setArgs("-Xold",
                "-classpath", ".", // ambient classpath insulation
                testSrc.toString());
        Task.Result tr = task.run(Task.Expect.SUCCESS);
        List<String> out = tr.getOutputLines(Task.OutputKind.STDOUT);
        List<String> err = tr.getOutputLines(Task.OutputKind.STDERR);
        checkOutput(testName, out, OLD_HEADER);
        checkOutput(testName, err, WARN_TEXT);
    }

    // input: old doclet
    // outcome: old tool
    @Test
    public void testOldDoclet() throws Exception {
        setArgs("-classpath", ".", // ambient classpath insulation
                "-doclet",
                OLD_DOCLET_CLASS_NAME,
                "-docletpath",
                testClasses,
                testSrc.toString());
        Task.Result tr = task.run(Task.Expect.SUCCESS);
        List<String> out = tr.getOutputLines(Task.OutputKind.STDOUT);
        List<String> err = tr.getOutputLines(Task.OutputKind.STDERR);
        checkOutput(testName, out, OLD_DOCLET_MARKER);
        checkOutput(testName, err, WARN_TEXT);
    }

    // input: old taglet
    // outcome: old tool
    @Test
    public void testOldTaglet() throws Exception {
        setArgs("-classpath", ".", // ambient classpath insulation
            "-taglet",
            OLD_TAGLET_CLASS_NAME,
            "-tagletpath",
            testClasses,
            testSrc.toString());
        Task.Result tr = task.run(Task.Expect.SUCCESS);
        List<String> out = tr.getOutputLines(Task.OutputKind.STDOUT);
        List<String> err = tr.getOutputLines(Task.OutputKind.STDERR);
        checkOutput(testName, out, OLD_TAGLET_MARKER);
        checkOutput(testName, err, WARN_TEXT);
    }

    // input: new doclet and old taglet
    // outcome: new doclet with failure
    @Test
    public void testNewDocletOldTaglet() throws Exception {
        setArgs("-classpath", ".", // ambient classpath insulation
                "-doclet",
                NEW_STDDOCLET,
                "-taglet",
                OLD_TAGLET_CLASS_NAME,
                "-tagletpath",
                testClasses,
                testSrc.toString());
        Task.Result tr = task.run(Task.Expect.FAIL, 1);
        //Task.Result tr = task.run();
        List<String> out = tr.getOutputLines(Task.OutputKind.STDOUT);
        List<String> err = tr.getOutputLines(Task.OutputKind.STDERR);
        checkOutput(testName, out, NEW_HEADER);
        checkOutput(testName, err, NEW_DOCLET_ERROR);
    }

    // input: old doclet and old taglet
    // outcome: old doclet and old taglet should register
    @Test
    public void testOldDocletOldTaglet() throws Exception {
        setArgs("-classpath", ".", // ambient classpath insulation
                "-doclet",
                OLD_STDDOCLET,
                "-taglet",
                OLD_TAGLET_CLASS_NAME,
                "-tagletpath",
                testClasses,
                testSrc.toString());
        Task.Result tr = task.run(Task.Expect.SUCCESS);
        List<String> out = tr.getOutputLines(Task.OutputKind.STDOUT);
        List<String> err = tr.getOutputLines(Task.OutputKind.STDERR);
        checkOutput(testName, out, OLD_HEADER);
        checkOutput(testName, out, OLD_TAGLET_MARKER);
        checkOutput(testName, err, WARN_TEXT);
    }

    // input: new doclet and new taglet
    // outcome: new doclet and new taglet should register
    @Test
    public void testNewDocletNewTaglet() throws Exception {
        setArgs("-classpath", ".", // ambient classpath insulation
                "-doclet",
                NEW_STDDOCLET,
                "-taglet",
                NEW_TAGLET_CLASS_NAME,
                "-tagletpath",
                testClasses,
                testSrc.toString());
        Task.Result tr = task.run(Task.Expect.SUCCESS);
        List<String> out = tr.getOutputLines(Task.OutputKind.STDOUT);
        List<String> err = tr.getOutputLines(Task.OutputKind.STDERR);
        checkOutput(testName, out, NEW_HEADER);
        checkOutput(testName, out, NEW_TAGLET_MARKER);
    }

    // input: old doclet and new taglet
    // outcome: old doclet and error
    @Test
    public void testOldDocletNewTaglet() throws Exception {
        setArgs("-classpath", ".", // ambient classpath insulation
                "-doclet",
                OLD_STDDOCLET,
                "-taglet",
                NEW_TAGLET_CLASS_NAME,
                "-tagletpath",
                testClasses,
                testSrc.toString());
        Task.Result tr = task.run(Task.Expect.FAIL, 1);
        List<String> out = tr.getOutputLines(Task.OutputKind.STDOUT);
        List<String> err = tr.getOutputLines(Task.OutputKind.STDERR);
        checkOutput(testName, out, OLD_HEADER);
        checkOutput(testName, err, WARN_TEXT);
        checkOutput(testName, err, OLD_DOCLET_ERROR);
    }

    void setArgs(String... args) {
        ostream.println("cmds: " + Arrays.asList(args));
        task.args(args);
    }

    void checkOutput(String testCase, List<String> content, String toFind) throws Exception {
        checkOutput(testCase, content, Pattern.compile(".*" + toFind + ".*"));
    }

    void checkOutput(String testCase, List<String> content, Pattern toFind) throws Exception {
        ostream.println("---" + testCase + "---");
        content.stream().forEach(x -> System.out.println(x));
        for (String x : content) {
            ostream.println(x);
            if (toFind.matcher(x).matches()) {
                return;
            }
        }
        throw new Exception(testCase + ": Expected string not found: " +  toFind);
    }

    public static class OldDoclet extends com.sun.javadoc.Doclet {
        public static boolean start(com.sun.javadoc.RootDoc root) {
            System.out.println(OLD_DOCLET_MARKER);
            return true;
        }
    }

    public static class OldTaglet implements com.sun.tools.doclets.Taglet {

        public static void register(Map map) {
            EnsureNewOldDoclet.OldTaglet tag = new OldTaglet();
            com.sun.tools.doclets.Taglet t = (com.sun.tools.doclets.Taglet) map.get(tag.getName());
            System.out.println(OLD_TAGLET_MARKER);
        }

        @Override
        public boolean inField() {
            return true;
        }

        @Override
        public boolean inConstructor() {
            return true;
        }

        @Override
        public boolean inMethod() {
            return true;
        }

        @Override
        public boolean inOverview() {
            return true;
        }

        @Override
        public boolean inPackage() {
            return true;
        }

        @Override
        public boolean inType() {
            return true;
        }

        @Override
        public boolean isInlineTag() {
            return true;
        }

        @Override
        public String getName() {
            return "OldTaglet";
        }

        @Override
        public String toString(Tag tag) {
            return getName();
        }

        @Override
        public String toString(Tag[] tags) {
            return getName();
        }
    }

    public static class NewTaglet implements jdk.javadoc.doclet.taglet.Taglet {

        @Override
        public Set<Location> getAllowedLocations() {
            return Collections.emptySet();
        }

        @Override
        public boolean isInlineTag() {
            return true;
        }

        @Override
        public String getName() {
            return "NewTaglet";
        }

        @Override
        public String toString(DocTree tag) {
            return tag.toString();
        }

        @Override
        public String toString(List<? extends DocTree> tags) {
            return tags.toString();
        }

    }
}
