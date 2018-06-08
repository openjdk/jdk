/*
 * Copyright (c) 2016, 2018, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8162353 8164747 8173707 8196202 8204303
 * @summary javadoc should provide a way to disable use of frames
 * @library /tools/lib ../lib
 * @modules
 *      jdk.compiler/com.sun.tools.javac.api
 *      jdk.compiler/com.sun.tools.javac.main
 *      jdk.javadoc/jdk.javadoc.internal.tool
 * @build toolbox.ModuleBuilder toolbox.ToolBox
 * @build JavadocTester
 * @run main TestFramesNoFrames
 */

import java.io.*;
import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Collectors;

import toolbox.ModuleBuilder;
import toolbox.ToolBox;

public class TestFramesNoFrames extends JavadocTester {

    public static void main(String... args) throws Exception {
        TestFramesNoFrames tester = new TestFramesNoFrames();
        tester.generateSource();
        tester.runTests();
    }

    ToolBox tb = new ToolBox();
    Path gensrcModules = Paths.get("gensrc/modules");
    Path gensrcPackages = Paths.get("gensrc/packages");

    void generateSource() throws IOException {
        String[] modules = { "", "m1", "m2", "m3" };
        String[] packages = { "p1", "p2", "p3" };
        String[] classes = { "C1", "C2", "C3" };
        for (String m: modules) {
            ModuleBuilder mb = m.equals("") ? null : new ModuleBuilder(tb, m);
            for (String p: packages) {
                Path pkgRoot;
                if (m.equals("")) {
                    pkgRoot = gensrcPackages;
                } else {
                    pkgRoot = gensrcModules.resolve(m);
                    mb.exports(m + p);
                }
                for (String c: classes) {
                    tb.writeJavaFiles(pkgRoot,
                        "package " + (m + p) + ";\n"
                        + "/** class " + (m + p + c).toUpperCase() + ". */\n"
                        + "public class " + (m + p + c).toUpperCase() + " { }"
                    );
                }
            }
            if (!m.equals("")) {
                mb.write(gensrcModules);
            }
        }
        tb.writeFile("overview.html",
                "<html><body>This is the overview file</body></html>");
    }

    enum FrameKind {
        DEFAULT(),
        FRAMES("--frames"),
        NO_FRAMES("--no-frames");
        FrameKind(String... opts) {
            this.opts = Arrays.asList(opts);
        }
        final List<String> opts;
    }

    enum OverviewKind {
        DEFAULT(),
        OVERVIEW("-overview", "overview.html"),
        NO_OVERVIEW("-nooverview");
        OverviewKind(String... opts) {
            this.opts = Arrays.asList(opts);
        }
        final List<String> opts;
    }

    enum HtmlKind {
        HTML4("-html4"),
        HTML5("-html5");
        HtmlKind(String... opts) {
            this.opts = Arrays.asList(opts);
        }
        final List<String> opts;
    }

    @Override
    public void runTests() throws Exception {
        for (Method m : getClass().getDeclaredMethods()) {
            Annotation a = m.getAnnotation(Test.class);
            if (a != null) {
                for (FrameKind fk : FrameKind.values()) {
                    for (OverviewKind ok : OverviewKind.values()) {
                        for (HtmlKind hk : HtmlKind.values()) {
                            try {
                                out.println("Running test " + m.getName() + " " + fk + " " + ok + " " + hk);
                                Path base = Paths.get(m.getName() + "_" + fk + "_" + ok + "_" + hk);
                                Files.createDirectories(base);
                                m.invoke(this, new Object[]{base, fk, ok, hk});
                            } catch (InvocationTargetException e) {
                                Throwable cause = e.getCause();
                                throw (cause instanceof Exception) ? ((Exception) cause) : e;
                            }
                            out.println();
                        }
                    }
                }
            }
        }
        printSummary();
    }

    void javadoc(Path outDir, FrameKind fKind, OverviewKind oKind, HtmlKind hKind, String... rest) {
        List<String> args = new ArrayList<>();
        args.add("-d");
        args.add(outDir.toString());
        args.addAll(fKind.opts);
        args.addAll(oKind.opts);
        args.addAll(hKind.opts);
        args.addAll(Arrays.asList(rest));
        javadoc(args.toArray(new String[0]));
        checkExit(Exit.OK);
    }

    @Test
    void testClass(Path base, FrameKind fKind, OverviewKind oKind, HtmlKind hKind) throws Exception {
        javadoc(base, fKind, oKind, hKind,
            gensrcPackages.resolve("p1/P1C1.java").toString());

        new Checker(fKind, oKind, hKind)
            .classes("p1.P1C1")
            .check();
    }

    @Test
    void testClasses(Path base, FrameKind fKind, OverviewKind oKind, HtmlKind hKind) throws IOException {
        javadoc(base, fKind, oKind, hKind,
            gensrcPackages.resolve("p1/P1C1.java").toString(),
            gensrcPackages.resolve("p1/P1C2.java").toString(),
            gensrcPackages.resolve("p1/P1C3.java").toString());

        new Checker(fKind, oKind, hKind)
            .classes("p1.P1C1", "p1.P1C2", "p1.P1C3")
            .check();
    }

    @Test
    void testPackage(Path base, FrameKind fKind, OverviewKind oKind, HtmlKind hKind) throws IOException {
        javadoc(base, fKind, oKind, hKind,
            "-sourcepath", gensrcPackages.toString(),
            "p1");

        new Checker(fKind, oKind, hKind)
            .classes("p1.P1C1", "p1.P1C2", "p1.P1C3")
            .check();
    }

    @Test
    void testPackages(Path base, FrameKind fKind, OverviewKind oKind, HtmlKind hKind) throws IOException {
        javadoc(base, fKind, oKind, hKind,
            "-sourcepath", gensrcPackages.toString(),
            "p1", "p2", "p3");

        new Checker(fKind, oKind, hKind)
            .classes("p1.P1C1", "p1.P1C2", "p1.P1C3",
                    "p2.P2C1", "p2.P2C2", "p2.P2C3",
                    "p3.P3C1", "p3.P3C2", "p3.P3C3")
            .check();
    }

    @Test
    void testModules(Path base, FrameKind fKind, OverviewKind oKind, HtmlKind hKind) throws IOException {
        javadoc(base, fKind, oKind, hKind,
            "--module-source-path", gensrcModules.toString(),
            "--module", "m1,m2,m3");

        new Checker(fKind, oKind, hKind)
            .classes("m1/m1p1.M1P1C1", "m1/m1p1.M1P1C2", "m1/m1p1.M1P1C3",
                    "m2/m2p1.M2P1C1", "m2/m2p1.M2P1C2", "m2/m2p1.M2P1C3",
                    "m3/m3p1.M3P1C1", "m3/m3p1.M3P1C2", "m3/m3p1.M3P1C3")
            .check();
    }

    /**
     * Check the contents of the generated output, according to the
     * specified options.
     */
    class Checker {
        private final FrameKind fKind;
        private final OverviewKind oKind;
        private final HtmlKind hKind;
        List<String> classes;

        private boolean frames;
        private boolean overview;
        private static final String framesWarning
                = "javadoc: warning - You have specified to generate frames, by using the --frames option.\n"
                + "The default is currently to not generate frames and the support for \n"
                + "frames will be removed in a future release.\n"
                + "To suppress this warning, remove the --frames option and avoid the use of frames.";

        Checker(FrameKind fKind, OverviewKind oKind, HtmlKind hKind) {
            this.fKind = fKind;
            this.oKind = oKind;
            this.hKind = hKind;
        }

        Checker classes(String... classes) {
            this.classes = Arrays.asList(classes);
            return this;
        }

        void check() throws IOException {
            switch (fKind) {
                case FRAMES:
                    frames = true;
                    break;

                case DEFAULT:
                case NO_FRAMES:
                    frames = false;
                    break;
            }

            switch (oKind) {
                case DEFAULT:
                    overview = (getPackageCount() > 1);
                    break;

                case OVERVIEW:
                    overview = true;
                    break;

                case NO_OVERVIEW:
                    overview = false;
                    break;
            }

            out.println("Checker: " + fKind + " " + oKind + " " + hKind
                + ": frames:" + frames + " overview:" + overview);

            checkAllClassesFiles();
            checkFrameFiles();
            checkOverviewSummary();

            checkIndex();
            checkNavBar();
            checkHelpDoc();

            checkWarning();

        }

        private void checkAllClassesFiles() {
            // these files are only generated in frames mode
            checkFiles(frames,
                    "allclasses-frame.html",
                    "allclasses-noframe.html");

            // this file is only generated when not in frames mode
            checkFiles(!frames,
                    "allclasses.html");

            if (frames) {
                checkOutput("allclasses-frame.html", true,
                        classes.stream()
                            .map(c -> "title=\"class in " + packagePart(c) + "\" target=\"classFrame\">" + classPart(c) + "</a>")
                            .toArray(String[]::new));
                checkOutput("allclasses-noframe.html", false,
                            "target=\"classFrame\">");
            } else {
                checkOutput("allclasses.html", false,
                            "target=\"classFrame\">");

            }
        }

        private void checkFrameFiles() {
            // these files are all only generated in frames mode

            // <module>/module-frame.html and <module>/module-type-frame.html files
            checkFiles(frames, classes.stream()
                .filter(c -> isInModule(c))
                .map(c -> modulePart(c))
                .flatMap(m -> Arrays.asList(
                        m + "/module-frame.html",
                        m + "/module-type-frame.html").stream())
                .collect(Collectors.toSet()));

            // <package>/package-frame.html files
            checkFiles(frames, classes.stream()
                    .map(c -> (isInModule(c) ? (modulePart(c) + "/") : "")
                                + packagePart(c)
                                + "/package-frame.html")
                    .collect(Collectors.toSet()));
        }

        private void checkHelpDoc() {
            // the Help page only describes Frame/NoFrames in frames mode
            checkOutput("help-doc.html", frames,
                        "<h2>Frames/No Frames</h2>");
        }

        private void checkIndex() {
            // the index.html page only contains frames and Javascript to default to no-frames view,
            // in frames mode
            checkOutput("index.html", frames,
                    "<iframe ",
                    "</iframe>",
                    "<body onload=\"loadFrames()\">\n"
                    + "<script type=\"text/javascript\">\n"
                    + "if (targetPage == \"\" || targetPage == \"undefined\")");

            // the index.html contains the overview if one
            // has been given, and not in frames mode
            checkOutput("index.html", !frames && oKind == OverviewKind.OVERVIEW,
                    "This is the overview file");

            // the index.html file contains a summary table
            // if an overview was generated and not in frames mode
            checkOutput("index.html", !frames && overview,
                    "<table class=\"overviewSummary\"");

            // the index.html file contains a redirect if
            // no frames and no overview
            checkOutput("index.html", !frames && !overview,
                    "<meta http-equiv=\"Refresh\" content=\"0;",
                    "<script type=\"text/javascript\">window.location.replace(");

            // the index.html file <meta> refresh should only use <noscript> in HTML 5
            if (!frames && !overview) {
                checkOutput("index.html", hKind == HtmlKind.HTML5,
                        "<noscript>\n<meta http-equiv=\"Refresh\" content=\"0;");
            }
        }

        private void checkNavBar() {
            // the files containing a navigation bar should only
            // contain FRAMES/NO-FRAMES links in frames mode
            List<String> navbarFiles = new ArrayList<>();
            navbarFiles.addAll(classes.stream()
                    .map(c -> (isInModule(c) ? (modulePart(c) + "/") : "")
                                + toHtml(packageClassPart(c)))
                    .collect(Collectors.toSet()));
            for (String f : navbarFiles) {
                checkOutput(f, frames,
                        "target=\"_top\">Frames</a>",
                        "target=\"_top\">No&nbsp;Frames</a>");
            }
        }

        private void checkOverviewSummary() {
            // To accommodate the historical behavior of generating
            // overview-summary.html in frames mode, the file
            // will still be generated in no-frames mode,
            // but will be a redirect to index.html
            checkFiles(overview,
                    "overview-summary.html");
            if (overview) {
                checkOutput("overview-summary.html",  !frames,
                        "<link rel=\"canonical\" href=\"index.html\">",
                        "<script type=\"text/javascript\">window.location.replace('index.html')</script>",
                        "<meta http-equiv=\"Refresh\" content=\"0;index.html\">",
                        "<p><a href=\"index.html\">index.html</a></p>");
            }
        }

        private void checkWarning() {
            checkOutput(Output.OUT, frames, framesWarning);
        }

        private long getPackageCount() {
            return this.classes.stream()
                .filter(name -> name.contains("."))
                .map(name -> name.substring(0, name.lastIndexOf(".")))
                .distinct()
                .count();
        }

        private String classPart(String className) {
            int lastDot = className.lastIndexOf(".");
            return className.substring(lastDot + 1);
        }

        private String packagePart(String className) {
            int slash = className.indexOf("/");
            int lastDot = className.lastIndexOf(".");
            return className.substring(slash + 1, lastDot);
        }

        private String packageClassPart(String className) {
            int slash = className.indexOf("/");
            return className.substring(slash + 1);
        }

        private boolean isInModule(String className) {
            return className.contains("/");
        }

        private String modulePart(String className) {
            int slash = className.indexOf("/");
            return className.substring(0, slash);
        }

        private String toHtml(String className) {
            return className.replace(".", "/") + ".html";
        }
    }
}
