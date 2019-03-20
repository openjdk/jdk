/*
 * Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8219958
 * @summary Automatically load taglets from a jar file
 * @library /tools/lib ../../lib
 * @modules
 *     jdk.javadoc/jdk.javadoc.internal.tool
 *     jdk.compiler/com.sun.tools.javac.api
 *     jdk.compiler/com.sun.tools.javac.main
 * @build javadoc.tester.* toolbox.ToolBox builder.ClassBuilder toolbox.JarTask
 * @run main/othervm TestAutoLoadTaglets
 */


import java.nio.file.Path;
import java.nio.file.Paths;

import builder.ClassBuilder;
import toolbox.JarTask;
import toolbox.JavacTask;
import toolbox.ToolBox;

import javadoc.tester.JavadocTester;

public class TestAutoLoadTaglets extends JavadocTester {

    final ToolBox tb;

    public static void main(String... args) throws Exception {
        TestAutoLoadTaglets tester = new TestAutoLoadTaglets();
        tester.runTests(m -> new Object[]{Paths.get(m.getName())});
    }

    TestAutoLoadTaglets() {
        tb = new ToolBox();
    }

    @Test
    public void test(Path base) throws Exception {
        Path srcDir = base.resolve("src");
        Path outDir = base.resolve("out");

        createTagletsJar(base, srcDir);

        new ClassBuilder(tb, "pkg.A")
                .setComments("test {@taglet1} and {@taglet2}")
                .setModifiers("public", "class")
                .write(srcDir);

        javadoc("-d", outDir.toString(),
                "-sourcepath", srcDir.toString(),
                "-tagletpath", "taglets.jar",
                "pkg");

        checkExit(Exit.OK);

        checkOutput("pkg/A.html", true,
                "test user taglet taglet1 and user taglet taglet2");
    }

    private void createTagletsJar(Path base, Path srcDir) throws Exception {
        Path classes = base.resolve("classes");
        tb.createDirectories(classes);
        createTaglets(srcDir);

        new JavacTask(tb).files(srcDir.resolve("Taglet1.java"), srcDir.resolve("Taglet2.java"))
                .outdir(classes).run();

        Path services = classes.resolve("META-INF").resolve("services").resolve("jdk.javadoc.doclet.Taglet");
        tb.writeFile(services,
                "Taglet1\n"
                + "Taglet2");

        new JarTask(tb, srcDir).run("cf", "taglets.jar", "-C", classes.toString(), ".");
    }

    private void createTaglets(Path srcDir) throws Exception {
        for (int i = 1; i < 3; i++) {
            tb.writeJavaFiles(srcDir,
                    "import com.sun.source.doctree.DocTree;\n"
                    + "import jdk.javadoc.doclet.Taglet;\n"
                    + "import javax.lang.model.element.Element;\n"
                    + "import java.util.List;\n"
                    + "import java.util.Set;\n"
                    + "public class Taglet" + i + " implements Taglet {\n"
                    + "    @Override\n"
                    + "    public Set<Location> getAllowedLocations() {\n"
                    + "        return null;\n"
                    + "    }\n"
                    + "    @Override\n"
                    + "    public boolean isInlineTag() {\n"
                    + "        return true;\n"
                    + "    }\n"
                    + "    @Override\n"
                    + "    public String getName() {\n"
                    + "        return \"taglet" + i + "\";\n"
                    + "    }\n"
                    + "    @Override\n"
                    + "    public String toString(List<? extends DocTree> tags, Element "
                    + "element) {\n"
                    + "        return \"user taglet taglet" + i + "\";\n"
                    + "    }\n"
                    + "}\n");
        }
    }

}
