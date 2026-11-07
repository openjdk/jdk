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
 * @bug 8383786
 * @summary TreeCopier should preserve known end positions
 * @library /tools/lib
 * @modules jdk.compiler/com.sun.tools.javac.api
 *          jdk.compiler/com.sun.tools.javac.code
 *          jdk.compiler/com.sun.tools.javac.tree
 *          jdk.compiler/com.sun.tools.javac.util
 * @run main TreeCopierEndPositionsTest
 */

import java.util.List;
import javax.tools.Diagnostic;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileManager;
import javax.tools.ToolProvider;

import com.sun.source.tree.AnnotatedTypeTree;
import com.sun.source.util.JavacTask;
import com.sun.source.util.Trees;
import com.sun.tools.javac.api.BasicJavacTask;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.JCTree.JCAnnotatedType;
import com.sun.tools.javac.tree.JCTree.JCClassDecl;
import com.sun.tools.javac.tree.JCTree.JCTypeApply;
import com.sun.tools.javac.tree.JCTree.JCVariableDecl;
import com.sun.tools.javac.tree.TreeCopier;
import com.sun.tools.javac.tree.TreeInfo;
import com.sun.tools.javac.tree.TreeMaker;
import com.sun.tools.javac.util.Position;

import toolbox.ToolBox;

public class TreeCopierEndPositionsTest {
    public static void main(String... args) throws Exception {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        try (var fm = compiler.getStandardFileManager(null, null, null)) {
            testRecordAnnotation(compiler, fm);
            testAnnotatedType(compiler, fm);
        }
    }

    private static void testRecordAnnotation(JavaCompiler compiler, JavaFileManager fm) throws Exception {
        String source = "record T(@Override int x) {}";
        JavacTask task = newTask(compiler, fm, source);
        var unit = task.parse().iterator().next();
        JCClassDecl record = (JCClassDecl) unit.getTypeDecls().get(0);
        task.analyze();
        var components = record.sym.getRecordComponents();
        if (components.size() != 1 || components.head.getOriginalAnnos().size() != 1) {
            throw new AssertionError("Expected one record component with one original annotation");
        }
        var annotation = components.head.getOriginalAnnos().head;
        var positions = Trees.instance(task).getSourcePositions();
        long start = positions.getStartPosition(annotation);
        long end = positions.getEndPosition(annotation);
        int expected = source.indexOf("@Override");
        if (start != expected || end != expected + "@Override".length()
                || !source.substring((int) start, (int) end).equals("@Override")) {
            throw new AssertionError("Unexpected annotation positions: " + start + ":" + end);
        }
    }

    private static void testAnnotatedType(JavaCompiler compiler, JavaFileManager fm) throws Exception {
        String source = """
                class T {
                    java.util.List<@A String[]> field;
                    @java.lang.annotation.Target(java.lang.annotation.ElementType.TYPE_USE)
                    @interface A {}
                }
                """;
        JavacTask task = newTask(compiler, fm, source);
        JCClassDecl clazz = (JCClassDecl) task.parse().iterator().next().getTypeDecls().get(0);
        JCVariableDecl field = (JCVariableDecl) clazz.defs.head;
        JCAnnotatedType annotated = (JCAnnotatedType) TreeInfo.innermostType(
                ((JCTypeApply) field.vartype).arguments.head, false);
        int expected = TreeInfo.getEndPos(annotated.underlyingType);
        if (expected == Position.NOPOS) {
            throw new AssertionError("Expected a known underlying end position");
        }
        // Do not depend on whether the parser records an end for this wrapper.
        annotated.endpos = Position.NOPOS;
        // Match DeferredAttr's copier, which strips the annotated type wrapper.
        var copier = new TreeCopier<Void>(TreeMaker.instance(((BasicJavacTask) task).getContext())) {
            @Override
            public JCTree visitAnnotatedType(AnnotatedTypeTree tree, Void p) {
                return copy(((JCAnnotatedType) tree).underlyingType, p);
            }
        };
        JCTree copied = copier.copy((JCTree) annotated);
        if (copied == annotated.underlyingType || copied.getKind() != annotated.underlyingType.getKind()
                || TreeInfo.getEndPos(copied) != expected) {
            throw new AssertionError("Underlying type end position was not preserved: "
                    + TreeInfo.getEndPos(copied) + ", expected " + expected);
        }
    }

    private static JavacTask newTask(JavaCompiler compiler, JavaFileManager fm, String source) {
        return (JavacTask) compiler.getTask(null, fm, diagnostic -> {
            if (diagnostic.getKind() == Diagnostic.Kind.ERROR) {
                throw new AssertionError(diagnostic);
            }
        }, List.of("-proc:none"), null, List.of(new ToolBox.JavaSource(source)));
    }
}
