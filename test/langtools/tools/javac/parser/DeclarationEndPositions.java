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
 * @bug 8350212
 * @summary Verify ending source positions are calculated for declarations supporting SuppressWarnings
 * @modules jdk.compiler/com.sun.tools.javac.tree
 * @run main DeclarationEndPositions
 */

import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.Tree;
import com.sun.source.tree.Tree;
import com.sun.source.util.JavacTask;
import com.sun.source.util.TreeScanner;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.JCTree.*;
import com.sun.tools.javac.tree.TreeInfo;

import java.io.IOException;
import java.net.URI;
import java.util.List;

import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.ToolProvider;

public class DeclarationEndPositions {

    public static void checkEndPosition(Class<? extends JCTree> nodeType, String input, String marker) throws IOException {

        // Create source
        var source = new SimpleJavaFileObject(URI.create("file://T.java"), JavaFileObject.Kind.SOURCE) {
            @Override
            public CharSequence getCharContent(boolean ignoreEncodingErrors) throws IOException {
                return input;
            }
        };

        // Parse source
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        JavaCompiler.CompilationTask task = compiler.getTask(null, null, null, List.of(), List.of(), List.of(source));
        Iterable<? extends CompilationUnitTree> units = ((JavacTask)task).parse();

        // Find node and check end position
        JCTree.JCCompilationUnit unit = (JCTree.JCCompilationUnit)units.iterator().next();
        unit.accept(new TreeScanner<Void, Void>() {
            @Override
            public Void scan(Tree node, Void aVoid) {
                if (nodeType.isInstance(node)) {
                    JCTree tree = (JCTree)node;
                    int actual = TreeInfo.getEndPos(tree, unit.endPositions);
                    int expected = marker.indexOf('^') + 1;
                    if (actual != expected) {
                        throw new AssertionError(String.format(
                          "wrong end pos %d != %d for \"%s\" @ %d", actual, expected, input, tree.pos));
                    }
                }
                return super.scan(node, aVoid);
            }
        }, null);
    }

    public static void main(String... args) throws Exception {

        // JCModuleDecl
        checkEndPosition(JCModuleDecl.class,
           "/* comment */ module fred { /* comment */ } /* comment */",
           "                                          ^              ");

        // JCPackageDecl
        checkEndPosition(JCPackageDecl.class,
           "/* comment */ package fred; /* comment */",
           "                          ^              ");

        // JCClassDecl
        checkEndPosition(JCClassDecl.class,
           "/* comment */ class Fred { /* comment */ } /* comment */",
           "                                         ^              ");

        // JCMethodDecl
        checkEndPosition(JCMethodDecl.class,
           "/* comment */ class Fred { void m() { /* comment */ } } /* comment */",
           "                                                    ^                ");

        // JCVariableDecl
        checkEndPosition(JCVariableDecl.class,
           "/* comment */ class Fred { int x; } /* comment */",
           "                                ^                ");
        checkEndPosition(JCVariableDecl.class,
           "/* comment */ class Fred { int x = 123; } /* comment */",
           "                                      ^                ");
        checkEndPosition(JCVariableDecl.class,
           "/* comment */ class A { try {} catch (Error err) {} } /* comment */",
           "                                              ^                    ");
    }
}
