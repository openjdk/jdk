/*
 * Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 7020499
 * @summary Verify that the code that closes the resources is shared by among try-with-resources
 * @library /tools/lib
 * @modules jdk.compiler/com.sun.tools.javac.api
 *          jdk.compiler/com.sun.tools.javac.code
 *          jdk.compiler/com.sun.tools.javac.comp
 *          jdk.compiler/com.sun.tools.javac.tree
 *          jdk.compiler/com.sun.tools.javac.util
 * @build toolbox.ToolBox TwrShareCloseCode
 * @run main TwrShareCloseCode
 */

import java.io.IOException;
import java.util.Arrays;

import com.sun.source.util.JavacTask;
import com.sun.tools.javac.api.JavacTool;
import com.sun.tools.javac.comp.AttrContext;
import com.sun.tools.javac.comp.Env;
import com.sun.tools.javac.comp.Lower;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.JCTree.JCMethodDecl;
import com.sun.tools.javac.tree.JCTree.JCMethodInvocation;
import com.sun.tools.javac.tree.TreeInfo;
import com.sun.tools.javac.tree.TreeMaker;
import com.sun.tools.javac.tree.TreeScanner;
import com.sun.tools.javac.util.Context;
import com.sun.tools.javac.util.Context.Factory;
import com.sun.tools.javac.util.List;

import toolbox.ToolBox;

public class TwrShareCloseCode {
    public static void main(String... args) throws IOException {
        new TwrShareCloseCode().run();
    }

    void run() throws IOException {
        run("try (Test t1 = new Test()) { }", true);
        run("try (Test t1 = new Test()) { }\n" +
            "try (Test t2 = new Test()) { }", true);
        run("try (Test t1 = new Test();\n" +
            "     Test t2 = new Test()) { }", true);
        run("try (Test t1 = new Test()) { }\n" +
            "try (Test t2 = new Test()) { }\n" +
            "try (Test t3 = new Test()) { }", true);
        run("try (Test t1 = new Test();\n" +
            "     Test t2 = new Test();\n" +
            "     Test t3 = new Test()) { }", false);
        run("try (Test t1 = new Test()) { }\n" +
            "try (Test t2 = new Test()) { }\n" +
            "try (Test t3 = new Test()) { }\n" +
            "try (Test t4 = new Test()) { }", false);

        run("try (Test t1 = new Test()) { i++; }", true);
        run("try (Test t1 = new Test()) { i++; }\n" +
            "try (Test t2 = new Test()) { i++; }", false);

        run("try (Test t1 = new Test(); Test t2 = new Test()) { i++; }", false);

        run("try (Test t1 = new Test()) { i++; }\n" +
            "try (Test t2 = new Test()) { }", true);

        run("try (Test t1 = new Test()) { i++; }\n" +
            "try (Test t2 = new Test()) { }\n" +
            "try (Test t3 = new Test()) { }", false);

        run("try (Test t1 = new Test()) { i++; }\n" +
            "try (Test t2 = new Test()) { i++; }\n" +
            "try (Test t3 = new Test()) { }", false);
    }
    void run(String trySpec, boolean expected) throws IOException {
        String template = "public class Test implements AutoCloseable {\n" +
                          "    void t(int i) {\n" +
                          "        TRY\n" +
                          "    }\n" +
                          "    public void close() { }\n" +
                          "}\n";
        String code = template.replace("TRY", trySpec);
        Context ctx = new Context();
        DumpLower.preRegister(ctx);
        Iterable<ToolBox.JavaSource> files = Arrays.asList(new ToolBox.JavaSource(code));
        JavacTask task = JavacTool.create().getTask(null, null, null, null, null, files, ctx);
        task.call();
        boolean actual = ((DumpLower) DumpLower.instance(ctx)).closeSeen;

        if (expected != actual) {
            throw new IllegalStateException("expected: " + expected + "; actual: " + actual + "; code:\n" + code);
        }
    }

    static class DumpLower extends Lower {

        public static void preRegister(Context ctx) {
            ctx.put(lowerKey, new Factory<Lower>() {
                @Override
                public Lower make(Context c) {
                    return new DumpLower(c);
                }
            });
        }

        public DumpLower(Context context) {
            super(context);
        }

        boolean closeSeen;

        @Override
        public List<JCTree> translateTopLevelClass(Env<AttrContext> env, JCTree cdef, TreeMaker make) {
            List<JCTree> result = super.translateTopLevelClass(env, cdef, make);

            new TreeScanner() {
                @Override
                public void visitMethodDef(JCMethodDecl tree) {
                    if (!tree.name.contentEquals("t"))
                        return;

                    super.visitMethodDef(tree);
                }

                @Override
                public void visitApply(JCMethodInvocation tree) {
                    closeSeen |= TreeInfo.symbol(tree.meth).name.contentEquals("close");
                    super.visitApply(tree);
                }
            }.scan(result);

            return result;
        }

    }
}
