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

    public static void checkPositions(Class<? extends JCTree> nodeType, String input, String markers) throws IOException {

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

                    // Verify declaration start and end positions
                    int start = tree.getStartPosition();
                    if (markers.charAt(start) != '<') {
                        throw new AssertionError(String.format(
                          "wrong %s pos %d for \"%s\" in \"%s\"", "start", start, tree, input));
                    }
                    int end = TreeInfo.getEndPos(tree);
                    if (markers.charAt(end - 1) != '>') {
                        throw new AssertionError(String.format(
                          "wrong %s pos %d for \"%s\" in \"%s\"", "end", end, tree, input));
                    }

                    // For variable declarations using "var", verify the "var" position
                    if (tree instanceof JCVariableDecl varDecl && varDecl.declaredUsingVar()) {
                        int vpos = varDecl.typePos;
                        if (!input.substring(vpos).startsWith("var")) {
                            throw new AssertionError(String.format(
                              "wrong %s pos %d for \"%s\" in \"%s\"", "var", vpos, tree, input));
                        }
                    }
                }
                return super.scan(node, aVoid);
            }
        }, null);
    }

    public static void main(String... args) throws Exception {

        // JCModuleDecl
        checkPositions(JCModuleDecl.class,
           "/* comment */ module fred { /* comment */ } /* comment */",
           "              <--------------------------->              ");

        // JCPackageDecl
        checkPositions(JCPackageDecl.class,
           "/* comment */ package fred; /* comment */",
           "              <----------->              ");

        // JCClassDecl
        checkPositions(JCClassDecl.class,
           "/* comment */ class Fred { /* comment */ } /* comment */",
           "              <-------------------------->              ");

        // JCMethodDecl
        checkPositions(JCMethodDecl.class,
           "/* comment */ class Fred { void m() { /* comment */ } } /* comment */",
           "                           <------------------------>                ");

        // JCVariableDecl
        checkPositions(JCVariableDecl.class,
           "/* comment */ class Fred { int x; } /* comment */",
           "                           <---->                ");
        checkPositions(JCVariableDecl.class,
           "/* comment */ class Fred { int x = 123; } /* comment */",
           "                           <---------->                ");
        checkPositions(JCVariableDecl.class,
           "/* comment */ class A { try {} catch (Error err) {} } /* comment */",
           "                                      <------->                    ");
        checkPositions(JCVariableDecl.class,
           "/* comment */ class Fred { final int x = 123; } /* comment */",
           "                           <---------------->                ");
        checkPositions(JCVariableDecl.class,
           "/* comment */ class Fred { final int x = 123, y = 456; } /* comment */",
           "                           <---------------->-------->                ");
        checkPositions(JCVariableDecl.class,
           "/* comment */ class A { void m() { try {} catch (Error err) {} } } /* comment */",
           "                                                 <------->                    ");

        // JCVariableDecl with "var" declarations
        checkPositions(JCVariableDecl.class,
           "class A { void m() { var foo; } }",
           "                     <------>    ");
        checkPositions(JCVariableDecl.class,
           "class A { void m() { var foo = 42; } }",
           "                     <----------->    ");
        checkPositions(JCVariableDecl.class,
           "class A { void m() { final var foo = 42; } }",
           "                     <----------------->    ");

        checkPositions(JCVariableDecl.class,
           "class A { void m() { java.util.function.Consumer<Byte> = foo -> { } } }",
           "                                                         <->           ");
        checkPositions(JCVariableDecl.class,
           "class A { void m() { java.util.function.Consumer<Byte> = (foo) -> { } } }",
           "                                                          <->            ");
        checkPositions(JCVariableDecl.class,
           "class A { void m() { java.util.function.Consumer<Byte> = (var foo) -> { } } }",
           "                                                          <----->            ");
        checkPositions(JCVariableDecl.class,
           "class A { void m() { java.util.function.Consumer<Byte> = (final var foo) -> { } } }",
           "                                                          <----------->            ");

        checkPositions(JCVariableDecl.class,
           "class A { record R(int x) { } void m() { switch (null) { case R(var x) -> {} default -> {} } } }",
           "                   <--->                                        <--->                           ");
        checkPositions(JCVariableDecl.class,
           "class A { record R(int x) { } void m() { switch (null) { case R(final var x) -> {} default -> {} } } }",
           "                   <--->                                        <--------->                           ");
    }
}
