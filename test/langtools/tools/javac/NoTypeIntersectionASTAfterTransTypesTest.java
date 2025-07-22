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

/*
 * @test
 * @bug 8361499
 * @summary intersection type cast causes javac crash with -Xjcov
 * @library /tools/lib
 * @modules jdk.compiler/com.sun.tools.javac.api
 *          jdk.compiler/com.sun.tools.javac.comp
 *          jdk.compiler/com.sun.tools.javac.main
 *          jdk.compiler/com.sun.tools.javac.tree
 *          jdk.compiler/com.sun.tools.javac.util
 * @build toolbox.ToolBox toolbox.JavacTask NoTypeIntersectionASTAfterTransTypesTest
 * @run main NoTypeIntersectionASTAfterTransTypesTest
 */

import java.util.List;
import com.sun.source.util.JavacTask;
import com.sun.tools.javac.api.JavacTool;
import com.sun.tools.javac.comp.TransTypes;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.JCTree.JCTypeIntersection;
import com.sun.tools.javac.util.Context;
import com.sun.tools.javac.util.Context.Factory;

import toolbox.ToolBox;

public class NoTypeIntersectionASTAfterTransTypesTest {
    public static void main(String... args) {
        new NoTypeIntersectionASTAfterTransTypesTest().run();
    }

    void run() {
        String code =
                """
                class Test {
                    interface I {}
                    void test() {
                        Runnable r1 = (Runnable & I)() -> {};
                        Runnable r2 = (I & Runnable)() -> {};
                    }
                }
                """;
        Context ctx = new Context();
        MyTransTypes.preRegister(ctx);
        JavacTask task = JavacTool.create().getTask(null, null, null, null, null, List.of(new ToolBox.JavaSource(code)), ctx);
        if (!task.call()) {
            throw new AssertionError("test failed due to a compilation error");
        }
    }

    static class MyTransTypes extends TransTypes {
        public static void preRegister(Context ctx) {
            ctx.put(transTypesKey, new Factory<TransTypes>() {
                @Override
                public TransTypes make(Context c) {
                    return new MyTransTypes(c);
                }
            });
        }
        public MyTransTypes(Context context) {
            super(context);
        }
        @Override
        public void visitTypeIntersection(JCTypeIntersection tree) {
            super.visitTypeIntersection(tree);
            // after the invocation to the method in the super class the JCTypeIntersection should have been lowered
            if (result instanceof JCTypeIntersection) {
                throw new AssertionError("there are unexpected type intersection ASTs");
            }
        }
    }
}
