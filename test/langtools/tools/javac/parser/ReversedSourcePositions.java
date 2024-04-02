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
 * @bug 8324736
 * @summary Verify starting and ending source positions are not backwards
 * @modules jdk.compiler/com.sun.tools.javac.tree
 * @run main ReversedSourcePositions
 */

import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.Tree;
import com.sun.source.util.JavacTask;
import com.sun.source.util.TreeScanner;
import com.sun.tools.javac.tree.JCTree;

import java.io.IOException;
import java.net.URI;
import java.util.List;

import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.ToolProvider;

public class ReversedSourcePositions {

    public static void main(String... args) throws Exception {

        // Create test case source
        var source = new SimpleJavaFileObject(URI.create("file://T.java"), JavaFileObject.Kind.SOURCE) {
            @Override
            public CharSequence getCharContent(boolean ignoreEncodingErrors) throws IOException {
                return """
                    package errorpronecrash;
                    ;
                    public class ReproFile {}
                """;
            }
        };

        // Parse source
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        JavaCompiler.CompilationTask task = compiler.getTask(null, null, null, List.of(), List.of(), List.of(source));
        Iterable<? extends CompilationUnitTree> units = ((JavacTask)task).parse();

        // Look for reversed source positions
        JCTree.JCCompilationUnit unit = (JCTree.JCCompilationUnit)units.iterator().next();
        unit.accept(new TreeScanner<Void, Void>() {
            @Override
            public Void scan(Tree node, Void aVoid) {
                if (node instanceof JCTree tree) {
                    int start = tree.getStartPosition();
                    int end = tree.getEndPosition(unit.endPositions);
                    if (start >= end) {
                        throw new AssertionError(
                          String.format("[%d, %d] %s %s\n", start, end, tree.getKind(), tree));
                    }
                }
                return super.scan(node, aVoid);
            }
        }, null);
    }
}
