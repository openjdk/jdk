/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8376843
 * @summary add more regression tests for local variable proxies
 * @modules jdk.compiler/com.sun.tools.javac.code
 *          jdk.compiler/com.sun.tools.javac.comp
 *          jdk.compiler/com.sun.tools.javac.file
 *          jdk.compiler/com.sun.tools.javac.main
 *          jdk.compiler/com.sun.tools.javac.tree
 *          jdk.compiler/com.sun.tools.javac.util
 */

import java.net.URI;

import java.util.HashSet;
import java.util.Set;

import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;

import com.sun.tools.javac.comp.AttrContext;
import com.sun.tools.javac.comp.Env;
import com.sun.tools.javac.comp.LocalProxyVarsGen;
import com.sun.tools.javac.comp.Modules;
import com.sun.tools.javac.file.JavacFileManager;
import com.sun.tools.javac.main.JavaCompiler;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.JCTree.*;
import com.sun.tools.javac.tree.TreeMaker;
import com.sun.tools.javac.util.Assert;
import com.sun.tools.javac.util.Context;
import com.sun.tools.javac.util.Context.Factory;
import com.sun.tools.javac.util.List;
import com.sun.tools.javac.util.Options;

import static com.sun.tools.javac.util.List.of;
import static com.sun.tools.javac.tree.JCTree.Tag.*;

public class LocalProxyVariablesTests {
    ReusableJavaCompiler tool;
    Context context;
    Options options;
    MyLocalProxyVarsGen myLocalProxyVarsGen;

    LocalProxyVariablesTests() {
        context = new Context();
        JavacFileManager.preRegister(context);
        MyLocalProxyVarsGen.preRegister(context);
        options = Options.instance(context);
        options.put("--enable-preview", "--enable-preview");
        options.put("--source", Integer.toString(Runtime.version().feature()));
        tool = new ReusableJavaCompiler(context);
        myLocalProxyVarsGen = (MyLocalProxyVarsGen)MyLocalProxyVarsGen.instance(context);
    }

    public static void main(String... args) throws Throwable {
        new LocalProxyVariablesTests().tests();
    }

    void tests() throws Throwable {
        doTest(
                """
                value class Test {
                    static String s0;
                    String s;
                    String ss;
                    Test(boolean b) {
                        s0 = null;
                        s = s0; // no local proxy variable for `s0` as it is static
                        ss = s; // but there should be a local proxy for `s`
                        super();
                    }
                }
                """, Set.of("local$s"));
        doTest(
                """
                value class Test {
                    int i;
                    int j;
                    Test() {// javac should generate a proxy local var for `i`
                        i = 1;
                        j = i; // as here `i` is being read during the early construction phase, use the local var instead
                        super();
                    }
                }
                """, Set.of("local$i"));
        doTest(
                """
                value class Test {
                    Integer x;
                    Integer y;
                    Test(boolean a, boolean b) {
                        if (a) {
                            x = 1;
                            if (b) {
                                y = 1;
                            } else {
                                y = 2;
                            }
                        } else {
                            x = y = 3;
                        }
                        super();
                    }
                }
                """, Set.of()); // no proxies in this case
        doTest(
                """
                value class Test {
                    Integer x;
                    Integer y;
                    Test(boolean a) {
                        x = 1;
                        if (a) {
                            y = x;
                        } else {
                            y = 2;
                        }
                        super();
                    }
                }
                """, Set.of("local$x"));
    }

    void doTest(String src, Set<String> expectedLocalProxyNames) throws Throwable {
        JavaSource source = new JavaSource(src);
        tool.clear();
        List<JavaFileObject> inputs = of(source);
        myLocalProxyVarsGen.expectedLocalProxyNames(expectedLocalProxyNames);
        try {
            tool.compile(inputs);
        } catch (Throwable ex) {
            throw new AssertionError(ex);
        }
        if (tool.errorCount() > 0) {
            throw new AssertionError("unexpected compilation error");
        }
    }

    class JavaSource extends SimpleJavaFileObject {
        String src;

        JavaSource(String src) {
            super(URI.create("myfo:/Test.java"), JavaFileObject.Kind.SOURCE);
            this.src = src;
        }

        @Override
        public CharSequence getCharContent(boolean ignoreEncodingErrors) {
            return src;
        }
    }

    static class MyLocalProxyVarsGen extends LocalProxyVarsGen {
        static void preRegister(Context context) {
            context.put(localProxyVarsGenKey, (Factory<LocalProxyVarsGen>) c -> new MyLocalProxyVarsGen(c));
        }

        void expectedLocalProxyNames(Set<String> expectedProxyNames) {
            // so that we can remove elements from our copy
            this.expectedProxyNames = new HashSet<>(expectedProxyNames);
        }

        Set<String> expectedProxyNames;

        MyLocalProxyVarsGen(Context context) {
            super(context);
        }

        @Override
        public JCTree translateTopLevelClass(JCTree cdef, TreeMaker make) {
            JCClassDecl transformed = (JCClassDecl)super.translateTopLevelClass(cdef, make);
            analyzeTransformedClass(transformed);
            return transformed;
        }

        /* we need to analyze the tree obtained from invoking `translateTopLevelClass` asap, we can't wait
         * until the compilation ends as other phases continue transforming the AST
         */
        void analyzeTransformedClass(JCClassDecl transformed) {
            for (JCTree def : transformed.defs) {
                if (def instanceof JCMethodDecl methodDecl && methodDecl.name.toString().equals("<init>")) {
                    for (JCStatement stat : methodDecl.body.stats) {
                        if (stat instanceof JCVariableDecl variableDecl && variableDecl.sym.isSynthetic()) {
                            Assert.check(expectedProxyNames.contains(variableDecl.name.toString()));
                            expectedProxyNames.remove(variableDecl.name.toString());
                        }
                    }
                }
            }
            Assert.check(expectedProxyNames.isEmpty());
        }
    }

    static class ReusableJavaCompiler extends JavaCompiler {
        ReusableJavaCompiler(Context context) {
            super(context);
        }

        @Override
        protected void checkReusable() {
            // do nothing
        }

        @Override
        public void close() {
            // do nothing
        }

        void clear() {
            chk.newRound();
            enter.newRound();
            newRound();
            Modules.instance(context).newRound();
            types.newRound();
            annotate.newRound();
        }
    }
}
