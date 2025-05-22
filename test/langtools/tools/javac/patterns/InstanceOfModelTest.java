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
 * @bug 8348906
 * @summary Verify the InstanceOfTree model
 */

import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.InstanceOfTree;
import com.sun.source.util.JavacTask;
import com.sun.source.util.TreeScanner;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.ToolProvider;

public class InstanceOfModelTest {
    private final JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();

    public static void main(String... args) throws Exception {
        new InstanceOfModelTest().run();
    }

    private void run() throws Exception {
        JavaFileObject input =
                SimpleJavaFileObject.forSource(URI.create("mem://Test.java"),
                                               """
                                               public class Test {
                                                   void test(Object o) {
                                                       boolean _ = o instanceof R;
                                                       boolean _ = o instanceof R r;
                                                       boolean _ = o instanceof R(var v);
                                                   }
                                                   record R(int i) {}
                                               }
                                               """);
        JavacTask task =
                (JavacTask) compiler.getTask(null, null, null, null, null, List.of(input));
        CompilationUnitTree cut = task.parse().iterator().next();

        task.analyze();

        List<String> instanceOf = new ArrayList<>();

        new TreeScanner<Void, Void>() {
            @Override
            public Void visitInstanceOf(InstanceOfTree node, Void p) {
                instanceOf.add(node.getPattern() + ":" + node.getType());
                return super.visitInstanceOf(node, p);
            }
        }.scan(cut, null);

        List<String> expectedInstanceOf = List.of(
            "null:R",
            "R r:R",
            "R(int v):null"
        );

        if (!Objects.equals(expectedInstanceOf, instanceOf)) {
            throw new AssertionError("Expected: " + expectedInstanceOf + ",\n" +
                                     "got: " + instanceOf);
        }
    }
}
