/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8332725
 * @summary Verify the AST model works correctly for binding patterns with var
 */

import com.sun.source.tree.BindingPatternTree;
import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.Tree;
import com.sun.source.util.JavacTask;
import com.sun.source.util.TreeScanner;
import java.net.URI;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.ToolProvider;

public class BindingPatternVarTypeModel {
    private final JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();

    public static void main(String... args) throws Exception {
        new BindingPatternVarTypeModel().run();
    }

    private void run() throws Exception {
        JavaFileObject input =
                SimpleJavaFileObject.forSource(URI.create("mem://Test.java"),
                                               """
                                               public class Test {
                                                   record R(int i) {}
                                                   int test(Object o) {
                                                       return switch (o) {
                                                           case R(var v) -> 0;
                                                           default -> 0;
                                                       };
                                                   }
                                               }
                                               """);
        JavacTask task =
                (JavacTask) compiler.getTask(null, null, null, null, null, List.of(input));
        CompilationUnitTree cut = task.parse().iterator().next();

        task.analyze();

        AtomicBoolean foundBindingPattern = new AtomicBoolean();

        new TreeScanner<Void, Void>() {
            @Override
            public Void visitBindingPattern(BindingPatternTree node, Void p) {
                if (node.getVariable().getType().getKind() != Tree.Kind.PRIMITIVE_TYPE) {
                    throw new AssertionError("Unexpected type for var: " +
                                             node.getVariable().getType().getKind() +
                                             ":" + node.getVariable().getType());
                }
                foundBindingPattern.set(true);
                return super.visitBindingPattern(node, p);
            }
        }.scan(cut, null);

        if (!foundBindingPattern.get()) {
            throw new AssertionError("Didn't find the binding pattern!");
        }
    }
}
