/*
 * Copyright (c) 2014, 2018, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8039410 8042601 8042829 8049393 8050031 8155061 8155995 8167967 8169813 8182765 8196202
 * @summary test to determine if members are ordered correctly
 * @library ../lib/
 * @modules jdk.javadoc/jdk.javadoc.internal.tool
 * @build JavadocTester
 * @run main TestOrdering
 */

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static java.nio.file.StandardOpenOption.*;

public class TestOrdering extends JavadocTester {

    public static void main(String[] args) throws Exception {
        TestOrdering tester = new TestOrdering();
        tester.runTests();
    }

    @Test
    void testUnnamedPackagesForClassUse() {
        new UnnamedPackageForClassUseTest(this).run();
    }

    @Test
    void testNamedPackagesForClassUse() {
        new NamedPackagesForClassUseTest(this).run();
    }

    @Test
    void testIndexOrdering() throws IOException {
        new IndexOrderingTest(this).run();
    }

    @Test
    void testIndexTypeClustering() {
        new IndexTypeClusteringTest(this).run();
    }

    @Test
    void testTypeElementMemberOrdering() {
        new TypeElementMemberOrderingTest(this).run();
    }

    static class UnnamedPackageForClassUseTest {
        final JavadocTester tester;

        UnnamedPackageForClassUseTest(JavadocTester tester) {
            this.tester = tester;
        }

        void run() {
            tester.javadoc("-d", "out",
                    "-sourcepath", testSrc,
                    "-use",
                    tester.testSrc("C.java"), tester.testSrc("UsedInC.java"));
            tester.checkExit(Exit.OK);
            checkExecutableMemberOrdering("class-use/UsedInC.html");
        }

        void checkExecutableMemberOrdering(String usePage) {
            String contents = tester.readFile(usePage);
            // check constructors
            tester.checking("constructors");
            int idx1 = contents.indexOf("C.html#%3Cinit%3E(UsedInC");
            int idx2 = contents.indexOf("C.html#%3Cinit%3E(UsedInC,int");
            int idx3 = contents.indexOf("C.html#%3Cinit%3E(UsedInC,java.lang.String");
            if (idx1 == -1 || idx2 == -1 || idx3 == -1) {
                tester.failed("ctor strings not found");
            } else if (idx1 > idx2 || idx2 > idx3 || idx1 > idx3) {
                tester.failed("ctor strings are out of order");
            } else {
                tester.passed("ctor strings are in order");
            }

            // check methods
            tester.checking("methods");
            idx1 = contents.indexOf("C.html#ymethod(int");
            idx2 = contents.indexOf("C.html#ymethod(java.lang.String");
            if (idx1 == -1 || idx2 == -1) {
                tester.failed("#ymethod strings not found");
            } else if (idx1 > idx2) {
                tester.failed("#ymethod strings are out of order");
            } else {
                tester.passed("Executable Member Ordering: OK");
            }
        }
    }

    static class NamedPackagesForClassUseTest {
        final JavadocTester tester;

        NamedPackagesForClassUseTest(JavadocTester tester) {
            this.tester = tester;
        }

        public void run() {
            tester.javadoc("-d", "out-1",
                    "-sourcepath", tester.testSrc,
                    "-use",
                    "--frames",
                    "pkg1");
            tester.checkExit(Exit.OK);
            tester.checkFiles(false, "allclasses-noframe.html");

            checkClassUseOrdering("pkg1/class-use/UsedClass.html");

            tester.checkOrder("pkg1/class-use/UsedClass.html",
                    "../MethodOrder.html#m()",
                    "../MethodOrder.html#m(byte%5B%5D)",
                    "../MethodOrder.html#m(double)",
                    "../MethodOrder.html#m(double,double)",
                    "../MethodOrder.html#m(double,java.lang.Double)",
                    "../MethodOrder.html#m(int)",
                    "../MethodOrder.html#m(int,int)",
                    "../MethodOrder.html#m(int,java.lang.Integer)",
                    "../MethodOrder.html#m(long)",
                    "../MethodOrder.html#m(long,long)",
                    "../MethodOrder.html#m(long,java.lang.Long)",
                    "../MethodOrder.html#m(long,java.lang.Long...)",
                    "../MethodOrder.html#m(java.lang.Double)",
                    "../MethodOrder.html#m(java.lang.Double,double)",
                    "../MethodOrder.html#m(java.lang.Double,java.lang.Double)",
                    "../MethodOrder.html#m(java.lang.Integer)",
                    "../MethodOrder.html#m(java.lang.Integer,int)",
                    "../MethodOrder.html#m(java.lang.Integer,java.lang.Integer)",
                    "../MethodOrder.html#m(java.lang.Object%5B%5D)",
                    "../MethodOrder.html#m(java.util.ArrayList)",
                    "../MethodOrder.html#m(java.util.Collection)",
                    "../MethodOrder.html#m(java.util.List)");

            tester.checkOrder("pkg1/class-use/UsedClass.html",
                    "../MethodOrder.html#tpm(pkg1.UsedClass)",
                    "../MethodOrder.html#tpm(pkg1.UsedClass,pkg1.UsedClass)",
                    "../MethodOrder.html#tpm(pkg1.UsedClass,pkg1.UsedClass%5B%5D)",
                    "../MethodOrder.html#tpm(pkg1.UsedClass,java.lang.String)");

            tester.checkOrder("pkg1/class-use/UsedClass.html",
                    "../A.html#%3Cinit%3E(pkg1.UsedClass)",
                    "../B.A.html#%3Cinit%3E(pkg1.UsedClass)",
                    "../B.html#%3Cinit%3E(pkg1.UsedClass)",
                    "../A.C.html#%3Cinit%3E(pkg1.UsedClass,java.lang.Object%5B%5D)",
                    "../A.C.html#%3Cinit%3E(pkg1.UsedClass,java.util.Collection)",
                    "../A.C.html#%3Cinit%3E(pkg1.UsedClass,java.util.List)");

            tester.checkOrder("pkg1/ImplementsOrdering.html",
                    "<dd><code>close</code>&nbsp;in interface&nbsp;<code>java.lang.AutoCloseable</code></dd>",
                    "<dd><code>close</code>&nbsp;in interface&nbsp;<code>java.nio.channels.Channel</code></dd>",
                    "<dd><code>close</code>&nbsp;in interface&nbsp;<code>java.io.Closeable</code></dd>");

            tester.checkOrder("pkg1/OverrideOrdering.html",
                    "<dd><code>iterator</code>&nbsp;in interface&nbsp;<code>java.util.Collection&lt;",
                    "<dd><code>iterator</code>&nbsp;in interface&nbsp;<code>java.lang.Iterable&lt;");

            tester.checkOrder("allclasses-frame.html",
                    "pkg1/A.html\" title=\"class in pkg1",
                    "pkg1/A.C.html\" title=\"class in pkg1",
                    "pkg1/B.html\" title=\"class in pkg1",
                    "pkg1/B.A.html\" title=\"class in pkg1",
                    "pkg1/C1.html\" title=\"class in pkg1",
                    "pkg1/C2.html\" title=\"class in pkg1",
                    "pkg1/C3.html\" title=\"class in pkg1",
                    "pkg1/C4.html\" title=\"class in pkg1",
                    "pkg1/ImplementsOrdering.html\" title=\"interface in pkg1",
                    "pkg1/MethodOrder.html\" title=\"class in pkg1",
                    "pkg1/OverrideOrdering.html\" title=\"class in pkg1",
                    "pkg1/UsedClass.html\" title=\"class in pkg1");
        }

        void checkClassUseOrdering(String usePage) {
            checkClassUseOrdering(usePage, "C#ITERATION#.html#zfield");
            checkClassUseOrdering(usePage, "C#ITERATION#.html#fieldInC#ITERATION#");
            checkClassUseOrdering(usePage, "C#ITERATION#.html#zmethod(pkg1.UsedClass");
            checkClassUseOrdering(usePage, "C#ITERATION#.html#methodInC#ITERATION#");
        }

        void checkClassUseOrdering(String usePage, String searchString) {
            String contents = tester.readFile(usePage);
            int lastidx = 0;
            System.out.println("testing for " + searchString);
            for (int i = 1; i < 5; i++) {
                String s = searchString.replaceAll("#ITERATION#", Integer.toString(i));
                tester.checking(s);
                int idx = contents.indexOf(s);
                if (idx < lastidx) {
                    tester.failed(s + ", member ordering error, last:" + lastidx + ", got:" + idx);
                } else {
                    tester.passed("\tlast: " + lastidx + " got:" + idx);
                }
                lastidx = idx;
            }
        }
    }

    static class IndexOrderingTest {
        private final JavadocTester tester;

        IndexOrderingTest(JavadocTester tester) {
            this.tester = tester;
        }

        enum ListOrder {
            NONE, REVERSE, SHUFFLE
        };

        /*
         * By default we do not shuffle the input list, in order to keep the list deterministic,
         * and the test predictable. However, we can turn on the stress mode, by setting the following
         * property if required.
         */
        static final ListOrder STRESS_MODE = Boolean.getBoolean("TestOrder.STRESS")
                ? ListOrder.SHUFFLE
                : ListOrder.REVERSE;

        /*
         * Controls the number of children packages, pkg0, pkg0.pkg, pkg0.pkg.pkg, .....
         * Note: having too long a depth (> 256 chars on Windows), will likely lead to
         * cause problems with automated build and test systems.
         */
        static final int MAX_SUBPACKAGES_DEPTH = 4;

        /*
         * Controls the number of sibling packages,  pkg0, pkg1, pkg2, .....
         */
        static final int MAX_PACKAGES = 4;

        static String[] contents = {
            "public add ADDADD;",
            "public add AddAdd;",
            "public add addadd;",
            "public enum add {add, ADD, addd, ADDD};",
            "public enum ADD {ADD, add, addd, ADDD};",
            "public void   add(){}",
            "public void   add(double d){}",
            "public void   add(int i, float f){}",
            "public void   add(float f, int i){}",
            "public void   add(double d, byte b){}",
            "public Double add(Double d) {return (double) 22/7;}",
            "public double add(double d1, double d2) {return d1 + d2;}",
            "public double add(double d1, Double  d2) {return d1 + d2;}",
            "public Float  add(float f) {return (float) 22/7;}",
            "public void   add(int i){}",
            "public int    add(Integer i) {return 0;}"
        };

        static String expectedEnumOrdering[] = {
            "Add.add.html\" title=\"enum in REPLACE_ME\"",
            "Add.ADD.html\" title=\"enum in REPLACE_ME\""
        };

        static String expectedFieldOrdering[] = {
            "Add.html#addadd\"",
            "add0/add/add/add/Add.html#addadd\"",
            "add0/add/add/Add.html#addadd\"",
            "add0/add/Add.html#addadd\"",
            "add0/Add.html#addadd\"",
            "add1/add/add/add/Add.html#addadd\"",
            "add1/add/add/Add.html#addadd\"",
            "add1/add/Add.html#addadd\"",
            "add1/Add.html#addadd\"",
            "add2/add/add/add/Add.html#addadd\"",
            "add2/add/add/Add.html#addadd\"",
            "add2/add/Add.html#addadd\"",
            "add2/Add.html#addadd\"",
            "add3/add/add/add/Add.html#addadd\"",
            "add3/add/add/Add.html#addadd\"",
            "add3/add/Add.html#addadd\"",
            "add3/Add.html#addadd\"",
            "Add.html#AddAdd\"",
            "add0/add/add/add/Add.html#AddAdd\"",
            "add0/add/add/Add.html#AddAdd\"",
            "add0/add/Add.html#AddAdd\"",
            "add0/Add.html#AddAdd\"",
            "add1/add/add/add/Add.html#AddAdd\"",
            "add1/add/add/Add.html#AddAdd\"",
            "add1/add/Add.html#AddAdd\"",
            "add1/Add.html#AddAdd\"",
            "add2/add/add/add/Add.html#AddAdd\"",
            "add2/add/add/Add.html#AddAdd\"",
            "add2/add/Add.html#AddAdd\"",
            "add2/Add.html#AddAdd\"",
            "add3/add/add/add/Add.html#AddAdd\"",
            "add3/add/add/Add.html#AddAdd\"",
            "add3/add/Add.html#AddAdd\"",
            "add3/Add.html#AddAdd\"",
            "Add.html#ADDADD\"",
            "add0/add/add/add/Add.html#ADDADD\"",
            "add0/add/add/Add.html#ADDADD\"",
            "add0/add/Add.html#ADDADD\"",
            "add0/Add.html#ADDADD\"",
            "add1/add/add/add/Add.html#ADDADD\"",
            "add1/add/add/Add.html#ADDADD\"",
            "add1/add/Add.html#ADDADD\"",
            "add1/Add.html#ADDADD\"",
            "add2/add/add/add/Add.html#ADDADD\"",
            "add2/add/add/Add.html#ADDADD\"",
            "add2/add/Add.html#ADDADD\"",
            "add2/Add.html#ADDADD\"",
            "add3/add/add/add/Add.html#ADDADD\"",
            "add3/add/add/Add.html#ADDADD\"",
            "add3/add/Add.html#ADDADD\"",
            "add3/Add.html#ADDADD\""
        };

        static String expectedMethodOrdering[] = {
            "Add.html#add()",
            "Add.html#add(double)",
            "Add.html#add(double,byte)",
            "Add.html#add(double,double)",
            "Add.html#add(double,java.lang.Double)",
            "Add.html#add(float)",
            "Add.html#add(float,int)",
            "Add.html#add(int)",
            "Add.html#add(int,float)",
            "Add.html#add(java.lang.Double)",
            "Add.html#add(java.lang.Integer)"
        };

        static String expectedPackageOrdering[] = {
            "\"add0/package-summary.html\">add0</a> - package add0",
            "\"add0/add/package-summary.html\">add0.add</a> - package add0.add",
            "\"add0/add/add/package-summary.html\">add0.add.add</a> - package add0.add.add",
            "\"add0/add/add/add/package-summary.html\">add0.add.add.add</a> - package add0.add.add.add",
            "\"add1/package-summary.html\">add1</a> - package add1",
            "\"add1/add/package-summary.html\">add1.add</a> - package add1.add",
            "\"add1/add/add/package-summary.html\">add1.add.add</a> - package add1.add.add",
            "\"add1/add/add/add/package-summary.html\">add1.add.add.add</a> - package add1.add.add.add",
            "\"add2/package-summary.html\">add2</a> - package add2",
            "\"add2/add/package-summary.html\">add2.add</a> - package add2.add",
            "\"add2/add/add/package-summary.html\">add2.add.add</a> - package add2.add.add",
            "\"add2/add/add/add/package-summary.html\">add2.add.add.add</a> - package add2.add.add.add",
            "\"add3/package-summary.html\">add3</a> - package add3",
            "\"add3/add/package-summary.html\">add3.add</a> - package add3.add",
            "\"add3/add/add/package-summary.html\">add3.add.add</a> - package add3.add.add",
            "\"add3/add/add/add/package-summary.html\">add3.add.add.add</a> - package add3.add.add.add"
        };

        void run() throws IOException {
            final String clsname = "Add";
            List<String> cmdArgs = new ArrayList();
            cmdArgs.add("-d");
            cmdArgs.add("out-2");
            cmdArgs.add("--frames");
            cmdArgs.add("-sourcepath");
            cmdArgs.add("src");
            cmdArgs.add("-package");
            System.out.println("STRESS_MODE: " + STRESS_MODE);
            emitFile(null, clsname, STRESS_MODE);
            for (int width = 0; width < MAX_PACKAGES; width++) {
                String wpkgname = "add" + width;
                String dpkgname = wpkgname;
                emitFile(wpkgname, clsname, ListOrder.NONE); // list as-is
                cmdArgs.add(wpkgname);
                for (int depth = 1; depth < MAX_SUBPACKAGES_DEPTH; depth++) {
                    dpkgname = dpkgname + ".add";
                    emitFile(dpkgname, clsname, STRESS_MODE);
                    cmdArgs.add(dpkgname);
                }
            }
            File srcDir = new File(new File("."), "src");
            cmdArgs.add(new File(srcDir, clsname + ".java").getPath());
            tester.javadoc(cmdArgs.toArray(new String[cmdArgs.size()]));
            tester.checkExit(Exit.OK);
            tester.checkOrder("index-all.html", composeTestVectors());
            tester.checkOrder("add0/add/package-tree.html",
                    "<a href=\"Add.add.html\" title=\"enum in add0.add\">",
                    "<a href=\"Add.ADD.html\" title=\"enum in add0.add\">");
            tester.checkOrder("overview-tree.html",
                    "<a href=\"Add.add.html\" title=\"enum in &lt;Unnamed&gt;\">",
                    "<a href=\"add0/Add.add.html\" title=\"enum in add0\">",
                    "<a href=\"add0/add/Add.add.html\" title=\"enum in add0.add\">",
                    "<a href=\"add0/add/add/Add.add.html\" title=\"enum in add0.add.add\">",
                    "<a href=\"add0/add/add/add/Add.add.html\" title=\"enum in add0.add.add.add\">",
                    "<a href=\"add1/Add.add.html\" title=\"enum in add1\">",
                    "<a href=\"add1/add/Add.add.html\" title=\"enum in add1.add\">",
                    "<a href=\"add1/add/add/Add.add.html\" title=\"enum in add1.add.add\">",
                    "<a href=\"add1/add/add/add/Add.add.html\" title=\"enum in add1.add.add.add\">",
                    "<a href=\"add2/Add.add.html\" title=\"enum in add2\">",
                    "<a href=\"add2/add/Add.add.html\" title=\"enum in add2.add\">",
                    "<a href=\"add2/add/add/Add.add.html\" title=\"enum in add2.add.add\">",
                    "<a href=\"add2/add/add/add/Add.add.html\" title=\"enum in add2.add.add.add\">",
                    "<a href=\"add3/Add.add.html\" title=\"enum in add3\">",
                    "<a href=\"add3/add/Add.add.html\" title=\"enum in add3.add\">",
                    "<a href=\"add3/add/add/Add.add.html\" title=\"enum in add3.add.add\">",
                    "<a href=\"add3/add/add/add/Add.add.html\" title=\"enum in add3.add.add.add\">",
                    "<a href=\"Add.ADD.html\" title=\"enum in &lt;Unnamed&gt;\">",
                    "<a href=\"add0/Add.ADD.html\" title=\"enum in add0\">",
                    "<a href=\"add0/add/Add.ADD.html\" title=\"enum in add0.add\">",
                    "<a href=\"add0/add/add/Add.ADD.html\" title=\"enum in add0.add.add\">",
                    "<a href=\"add0/add/add/add/Add.ADD.html\" title=\"enum in add0.add.add.add\">",
                    "<a href=\"add1/Add.ADD.html\" title=\"enum in add1\">",
                    "<a href=\"add1/add/Add.ADD.html\" title=\"enum in add1.add\">",
                    "<a href=\"add1/add/add/Add.ADD.html\" title=\"enum in add1.add.add\">",
                    "<a href=\"add1/add/add/add/Add.ADD.html\" title=\"enum in add1.add.add.add\">",
                    "<a href=\"add2/Add.ADD.html\" title=\"enum in add2\">",
                    "<a href=\"add2/add/Add.ADD.html\" title=\"enum in add2.add\">",
                    "<a href=\"add2/add/add/Add.ADD.html\" title=\"enum in add2.add.add\">",
                    "<a href=\"add2/add/add/add/Add.ADD.html\" title=\"enum in add2.add.add.add\">",
                    "<a href=\"add3/Add.ADD.html\" title=\"enum in add3\">",
                    "<a href=\"add3/add/Add.ADD.html\" title=\"enum in add3.add\">",
                    "<a href=\"add3/add/add/Add.ADD.html\" title=\"enum in add3.add.add\">",
                    "<a href=\"add3/add/add/add/Add.ADD.html\" title=\"enum in add3.add.add.add\">");

            tester.checkOrder("overview-frame.html",
                    "<a href=\"package-frame.html\" target=\"packageFrame\">&lt;unnamed package&gt;</a>",
                    "<a href=\"add0/package-frame.html\" target=\"packageFrame\">add0</a>",
                    "<a href=\"add0/add/package-frame.html\" target=\"packageFrame\">add0.add</a>",
                    "<a href=\"add0/add/add/package-frame.html\" target=\"packageFrame\">add0.add.add</a>",
                    "<a href=\"add0/add/add/add/package-frame.html\" target=\"packageFrame\">add0.add.add.add</a>",
                    "<a href=\"add1/package-frame.html\" target=\"packageFrame\">add1</a>",
                    "<a href=\"add1/add/package-frame.html\" target=\"packageFrame\">add1.add</a>",
                    "<a href=\"add1/add/add/package-frame.html\" target=\"packageFrame\">add1.add.add</a>",
                    "<a href=\"add1/add/add/add/package-frame.html\" target=\"packageFrame\">add1.add.add.add</a>",
                    "<a href=\"add2/package-frame.html\" target=\"packageFrame\">add2</a>",
                    "<a href=\"add2/add/package-frame.html\" target=\"packageFrame\">add2.add</a>",
                    "<a href=\"add2/add/add/package-frame.html\" target=\"packageFrame\">add2.add.add</a>",
                    "<a href=\"add2/add/add/add/package-frame.html\" target=\"packageFrame\">add2.add.add.add</a>",
                    "<a href=\"add3/package-frame.html\" target=\"packageFrame\">add3</a>",
                    "<a href=\"add3/add/package-frame.html\" target=\"packageFrame\">add3.add</a>",
                    "<a href=\"add3/add/add/package-frame.html\" target=\"packageFrame\">add3.add.add</a>",
                    "<a href=\"add3/add/add/add/package-frame.html\" target=\"packageFrame\">add3.add.add.add</a></li>");
        }

        void emitFile(String pkgname, String clsname, ListOrder order) throws IOException {
            File srcDir = new File("src");
            File outDir = pkgname == null
                    ? srcDir
                    : new File(srcDir, pkgname.replace(".", File.separator));
            File outFile = new File(outDir, clsname + ".java");
            outDir.mkdirs();
            List<String> scratch = new ArrayList<>(Arrays.asList(contents));
            switch (order) {
                case SHUFFLE:
                    Collections.shuffle(scratch);
                    break;
                case REVERSE:
                    Collections.reverse(scratch);
                    break;
                default:
                // leave list as-is
            }
            // insert the header
            scratch.add(0, "public class " + clsname + " {");
            if (pkgname != null) {
                scratch.add(0, "package " + pkgname + ";");
            }
            // append the footer
            scratch.add("}");
            Files.write(outFile.toPath(), scratch, CREATE, TRUNCATE_EXISTING);
        }

        String[] composeTestVectors() {
            List<String> testList = new ArrayList<>();

            for (String x : expectedEnumOrdering) {
                testList.add(x.replace("REPLACE_ME", "&lt;Unnamed&gt;"));
            }
            for (int i = 0; i < MAX_PACKAGES; i++) {
                String wpkg = "add" + i;
                for (String x : expectedEnumOrdering) {
                    testList.add(wpkg + "/" + x.replace("REPLACE_ME", wpkg));
                }
                String dpkg = wpkg;
                for (int j = 1; j < MAX_SUBPACKAGES_DEPTH; j++) {
                    dpkg = dpkg + "/" + "add";
                    for (String x : expectedEnumOrdering) {
                        testList.add(dpkg + "/" + x.replace("REPLACE_ME", pathToPackage(dpkg)));
                    }
                }
            }

            for (String x : expectedMethodOrdering) {
                testList.add(x);
                for (int i = 0; i < MAX_PACKAGES; i++) {
                    String wpkg = "add" + i;
                    testList.add(wpkg + "/" + x);
                    String dpkg = wpkg;
                    for (int j = 1; j < MAX_SUBPACKAGES_DEPTH; j++) {
                        dpkg = dpkg + "/" + "add";
                        testList.add(dpkg + "/" + x);
                    }
                }
            }
            testList.addAll(Arrays.asList(expectedPackageOrdering));
            testList.addAll(Arrays.asList(expectedFieldOrdering));

            return testList.toArray(new String[testList.size()]);
        }

        String pathToPackage(String in) {
            return in.replace("/", ".");
        }
    }

    static class IndexTypeClusteringTest {

        private final JavadocTester tester;

        IndexTypeClusteringTest(JavadocTester tester) {
            this.tester = tester;
        }

        void run() {
            tester.javadoc("-d", "out-3",
                    "-sourcepath", tester.testSrc("src-2"),
                    "-use",
                    "a",
                    "b",
                    "e",
                    "something");

            tester.checkExit(Exit.OK);

            tester.checkOrder("index-all.html",
                    "something</a> - package something</dt>",
                    "something</span></a> - Class in",
                    "something</span></a> - Enum in",
                    "something</span></a> - Interface in",
                    "something</span></a> - Annotation Type in",
                    "something</a></span> - Variable in class",
                    "something()</a></span> - Constructor",
                    "something()</a></span> - Method in class a.<a href=\"a/A.html\"",
                    "something()</a></span> - Method in class a.<a href=\"a/something.html\"",
                    "something()</a></span> - Method in class something.<a href=\"something/J.html\"");
        }
    }

    static class TypeElementMemberOrderingTest {

        final JavadocTester tester;

        TypeElementMemberOrderingTest(JavadocTester tester) {
            this.tester = tester;
        }

        void run() {
            tester.javadoc("-d", "out-5",
                    "-javafx",
                    "--disable-javafx-strict-checks",
                    "-sourcepath", tester.testSrc(new File(".").getPath()),
                    "pkg5"
            );

            tester.checkExit(Exit.OK);

            tester.checkOrder("pkg5/AnnoFieldTest.html",
                    "<h3>Field Detail</h3>",
                    "<pre>static final&nbsp;int&nbsp;one</pre>",
                    "<pre>static final&nbsp;int&nbsp;two</pre>",
                    "<pre>static final&nbsp;int&nbsp;three</pre>",
                    "<pre>static final&nbsp;int&nbsp;four</pre>");

            tester.checkOrder("pkg5/AnnoOptionalTest.html",
                    "<h3>Optional Element Summary</h3>",
                    "<a href=\"#four()\">four</a>",
                    "<a href=\"#one()\">one</a>",
                    "<a href=\"#three()\">three</a>",
                    "<a href=\"#two()\">two</a>",
                    "<h3>Element Detail</h3>",
                    "<h4>one</h4>",
                    "<h4>two</h4>",
                    "<h4>three</h4>",
                    "<h4>four</h4>");

            tester.checkOrder("pkg5/AnnoRequiredTest.html",
                    "<h3>Required Element Summary</h3>",
                    "<a href=\"#four()\">four</a>",
                    "<a href=\"#one()\">one</a>",
                    "<a href=\"#three()\">three</a>",
                    "<a href=\"#two()\">two</a>",
                    "<h3>Element Detail</h3>",
                    "<h4>one</h4>",
                    "<h4>two</h4>",
                    "<h4>three</h4>",
                    "<h4>four</h4>");

            tester.checkOrder("pkg5/CtorTest.html",
                    "<h3>Constructor Summary</h3>",
                    "<a href=\"#%3Cinit%3E(int)\"",
                    "<a href=\"#%3Cinit%3E(int,int)\"",
                    "<a href=\"#%3Cinit%3E(int,int,int)\"",
                    "<a href=\"#%3Cinit%3E(int,int,int,int)\"",
                    "<h3>Constructor Detail</h3>",
                    "<a id=\"&lt;init&gt;(int,int,int,int)\">",
                    "<a id=\"&lt;init&gt;(int,int,int)\">",
                    "<a id=\"&lt;init&gt;(int,int)\">",
                    "<a id=\"&lt;init&gt;(int)\">");

            tester.checkOrder("pkg5/EnumTest.html",
                    "<h3>Enum Constant Summary</h3>",
                    "<a href=\"#FOUR\">FOUR</a>",
                    "<a href=\"#ONE\">ONE</a>",
                    "<a href=\"#THREE\">THREE</a>",
                    "<a href=\"#TWO\">TWO</a>",
                    "<h3>Enum Constant Detail</h3>",
                    "<h4>ONE</h4>",
                    "<h4>TWO</h4>",
                    "<h4>THREE</h4>",
                    "<h4>FOUR</h4>");

            tester.checkOrder("pkg5/FieldTest.html",
                    "<h3>Field Summary</h3>",
                    "<a href=\"#four\">four</a>",
                    "<a href=\"#one\">one</a>",
                    "<a href=\"#three\">three</a>",
                    "<a href=\"#two\">two</a>",
                    "<h3>Field Detail</h3>",
                    "<h4>one</h4>",
                    "<h4>two</h4>",
                    "<h4>three</h4>",
                    "<h4>four</h4>");

            tester.checkOrder("pkg5/IntfTest.html",
                    "<h3>Method Summary</h3>",
                    "<a href=\"#four()\">four</a>",
                    "<a href=\"#one()\">one</a>",
                    "<a href=\"#three()\">three</a>",
                    "<a href=\"#two()\">two</a>",
                    "<h3>Method Detail</h3>",
                    "<h4>one</h4>",
                    "<h4>two</h4>",
                    "<h4>three</h4>",
                    "<h4>four</h4>");

            tester.checkOrder("pkg5/MethodTest.html",
                    "<h3>Method Summary</h3>",
                    "<a href=\"#four()\">four</a>",
                    "<a href=\"#one()\">one</a>",
                    "<a href=\"#three()\">three</a>",
                    "<a href=\"#two()\">two</a>",
                    "<h3>Method Detail</h3>",
                    "<h4>one</h4>",
                    "<h4>two</h4>",
                    "<h4>three</h4>",
                    "<h4>four</h4>");

            tester.checkOrder("pkg5/PropertyTest.html",
                    "<h3>Property Summary</h3>",
                    "<a href=\"#fourProperty\">four</a>",
                    "<a href=\"#oneProperty\">one</a>",
                    "<a href=\"#threeProperty\">three</a>",
                    "<a href=\"#twoProperty\">two</a>",
                    "<h3>Property Detail</h3>",
                    "<h4>oneProperty</h4>",
                    "<h4>twoProperty</h4>",
                    "<h4>threeProperty</h4>",
                    "<h4>fourProperty</h4>");

            tester.javadoc("-d", "out-5-html4",
                    "-html4",
                    "-javafx",
                    "-sourcepath", tester.testSrc(new File(".").getPath()),
                    "pkg5"
            );

            tester.checkExit(Exit.OK);

            tester.checkOrder("pkg5/CtorTest.html",
                    "<a href=\"#CtorTest-int-\"",
                    "<a href=\"#CtorTest-int-int-\"",
                    "<a href=\"#CtorTest-int-int-int-\"",
                    "<a href=\"#CtorTest-int-int-int-int-\"",
                    "<a name=\"CtorTest-int-int-int-int-\">",
                    "<a name=\"CtorTest-int-int-int-\">",
                    "<a name=\"CtorTest-int-int-\">",
                    "<a name=\"CtorTest-int-\">");
        }
    }
}
