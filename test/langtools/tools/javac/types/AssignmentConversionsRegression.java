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
 * @bug 8385070
 * @summary javac should use subtype checks for language rules specified in terms of subtyping
 * @library /tools/lib
 * @modules jdk.compiler/com.sun.tools.javac.api
 *          jdk.compiler/com.sun.tools.javac.main
 * @build toolbox.ToolBox toolbox.JavacTask
 * @run main AssignmentConversionsRegression
 */

import java.util.List;
import toolbox.JavacTask;
import toolbox.Task;
import toolbox.ToolBox;

public class AssignmentConversionsRegression {
    final ToolBox tb = new ToolBox();

    public static void main(String... args) throws Exception {
        AssignmentConversionsRegression test = new AssignmentConversionsRegression();
        test.subtypeOnlyChecks();
    }

    void subtypeOnlyChecks() {
        List<String> output = new JavacTask(tb)
                .options("-XDrawDiagnostics")
                .sources("""
                         class Cases {
                             sealed interface I permits A {}
                             static final class A implements I {}
                             static class Super {
                                 A m() {
                                     return null;
                                 }
                             }
                             static class BadCovariantReturn extends Super {
                                 @Override // always error
                                 I m() { // always error
                                     return null;
                                 }
                             }

                             sealed interface Enclosing permits Outer {}
                             static final class Outer implements Enclosing {
                                 class Base {}
                             }
                             static class BadQualifiedSuper extends Outer.Base {
                                 BadQualifiedSuper(Enclosing outer) {
                                     outer.super(); // always error
                                 }
                             }

                             sealed interface Resource permits ResourceImpl {}
                             static final class ResourceImpl implements Resource, AutoCloseable {
                                 public void close() {}
                             }
                             void badTwr(Resource r) throws Exception {
                                 try (r) { // always error
                                 }
                             }

                             sealed interface ExceptionType permits ExceptionImpl {}
                             static final class ExceptionImpl extends Exception implements ExceptionType {}
                             void badThrows() throws ExceptionType { // always error
                             }

                             static final class NotThrowable {}
                             void badCatch() {
                                 try {
                                 } catch (NotThrowable ex) { // always error
                                 }
                             }

                             void badMultiCatch() {
                                 try {
                                 } catch (NotThrowable | RuntimeException ex) { // always error
                                 }
                             }
                         }
                         """)
                .run(Task.Expect.FAIL)
                .writeAll()
                .getOutputLines(Task.OutputKind.DIRECT);

        tb.checkEqual(output, List.of(
                "Cases.java:11:11: compiler.err.override.incompatible.ret: (compiler.misc.cant.override: m(), Cases.BadCovariantReturn, m(), Cases.Super), Cases.I, Cases.A",
                "Cases.java:10:9: compiler.err.method.does.not.override.superclass: m(), Cases.BadCovariantReturn",
                "Cases.java:22:13: compiler.err.prob.found.req: (compiler.misc.inconvertible.types: Cases.Enclosing, Cases.Outer)",
                "Cases.java:31:14: compiler.err.prob.found.req: (compiler.misc.try.not.applicable.to.type: (compiler.misc.inconvertible.types: Cases.Resource, java.lang.AutoCloseable))",
                "Cases.java:37:29: compiler.err.prob.found.req: (compiler.misc.inconvertible.types: Cases.ExceptionType, java.lang.Throwable)",
                "Cases.java:43:18: compiler.err.prob.found.req: (compiler.misc.inconvertible.types: Cases.NotThrowable, java.lang.Throwable)",
                "Cases.java:49:18: compiler.err.prob.found.req: (compiler.misc.inconvertible.types: Cases.NotThrowable, java.lang.Throwable)",
                "7 errors"));
    }
}
