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
 * @bug 8299214
 * @key randomness
 * @summary Jasm Fuzzer for irreducible loops.
 * @library /test/lib /
 * @compile ../../compiler/lib/verify/Verify.java
 * @run driver ${test.main.class}
 */

package compiler.loopopts;

// TODO: all needed?
import java.util.List;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.Set;
import java.util.Collections;
import java.util.Random;
import jdk.test.lib.Utils;
import java.util.stream.IntStream;

import compiler.lib.compile_framework.CompileFramework;

// TODO: all needed?
import compiler.lib.template_framework.Template;
import compiler.lib.template_framework.TemplateToken;
import static compiler.lib.template_framework.Template.scope;
import static compiler.lib.template_framework.Template.let;
import static compiler.lib.template_framework.Template.$;
import compiler.lib.template_framework.library.CodeGenerationDataNameType;
import compiler.lib.template_framework.library.Expression;
import compiler.lib.template_framework.library.Expression.Nesting;
import compiler.lib.template_framework.library.Operations;
import compiler.lib.template_framework.library.TestFrameworkClass;
import compiler.lib.template_framework.library.PrimitiveType;
import compiler.lib.template_framework.library.ShortCarriesFloat16Type;
import compiler.lib.template_framework.library.VectorElementType;
import compiler.lib.template_framework.library.VectorType;

/**
 * Fuzzer for irreducible loops.
 * Regular method compilations of Java code is structured, so no irreducible loops.
 * OSR can create some irreducible loops, but with Jasm we have the full freedom
 * to create arbitrary code graphs.
 *
 * Idea:
 * - Create control-flow graph:
 *   - Initially: start and sink nodes.
 *   - Expansion: replace edges with:
 *     - if-else
 *     - reducible loop
 *     - irreducible loop
 * - Variable types
 * - Stack height and types
 */
public class IrreducibleLoopFuzzer {
    private static final Random RANDOM = Utils.getRandomInstance();

    public static void main(String[] args) {
        // Create a new CompileFramework instance.
        CompileFramework comp = new CompileFramework();

        // Add a java source file.
        comp.addJasmSourceCode("compiler.loopopts.templated.Templated", generate());

        // Compile the source file.
        comp.compile();

        // TODO: invoke strategy: timeout, oom, ... multiple methods?
        // TODO: maybe result verification?
        comp.invoke("compiler.loopopts.templated.Templated", "test", new Object[] {} );
    }

    static interface JasmType {
        String name();
        String prefix();
        int slots();
        Object pushCon();
        List<Operation> ifGotoOperations(String label);
    }

    static class IntType implements JasmType {
        public String name() { return "int"; }
        public String prefix() { return "i"; }
        public int slots() { return 1; }
        public Object pushCon() { return "ldc " + RANDOM.nextInt() + ";\n"; }

        public List<Operation> ifGotoOperations(String label) {
            return List.of(
                new Operation(List.of(INTS, INTS),  List.of(),  "if_icmple " + label + ";\n"),
                new Operation(List.of(INTS, INTS),  List.of(),  "if_icmplt " + label + ";\n")
            );
        }
    }

    static class LongType implements JasmType {
        public String name() { return "long"; }
        public String prefix() { return "l"; }
        public int slots() { return 2; }
        public Object pushCon() { return "ldc2_w " + RANDOM.nextLong() + "L;\n"; }

        public List<Operation> ifGotoOperations(String label) {
            return List.of(
                new Operation(List.of(LONGS, LONGS),  List.of(),  "lcmp; ifle " + label + ";\n"),
                new Operation(List.of(LONGS, LONGS),  List.of(),  "lcmp; iflt " + label + ";\n")
            );
        }
    }

    static final JasmType INTS = new IntType();
    static final JasmType LONGS = new LongType();

    static final List<JasmType> TYPES = List.of(
        INTS,
        LONGS
    );

    static JasmType randomType() {
        return TYPES.get(RANDOM.nextInt(TYPES.size()));
    }

    static record Local(int index, JasmType type) {}

    static record Operation(List<JasmType> in, List<JasmType> out, String op) {}

    // TODO: expand list of ops.
    static final List<Operation> OPERATIONS = List.of(
        // Copy
        new Operation(List.of(INTS),  List.of(INTS),  null),
        new Operation(List.of(LONGS), List.of(LONGS), null),
        // Arithmetic
        new Operation(List.of(INTS, INTS), List.of(INTS), "iadd"),
        new Operation(List.of(INTS, INTS), List.of(INTS), "imul"),
        new Operation(List.of(INTS, INTS), List.of(INTS), "iand"),
        new Operation(List.of(LONGS, LONGS), List.of(LONGS), "ladd"),
        new Operation(List.of(LONGS, LONGS), List.of(LONGS), "lmul"),
        new Operation(List.of(LONGS, LONGS), List.of(LONGS), "land"),
        new Operation(List.of(INTS), List.of(LONGS), "i2l"),
        new Operation(List.of(LONGS), List.of(INTS), "l2i")
    );

    static class Block {
        static int count = 0;

        final String name = "L" + (count++);
        Block out0 = null;
        Block out1 = null;

        public Object token(Method method) {
            // Swap randomly.
            final boolean r = RANDOM.nextBoolean();
            final Block b0 = r ? out0 : out1;
            final Block b1 = r ? out1 : out0;
            // If possible, make goto1 non-null.
            final Block goto0 = (b0 == null) ? b0 : b1;
            final Block goto1 = (b0 == null) ? b1 : b0;
            var template = Template.make(() -> scope(
                let("name", name),
                """
                #name:
                // body:
                """,
                method.blockBody(),
                """
                // branch:
                """,
                (goto0 != null) ? scope(
                    method.maybeGoto(goto0.name)
                ) : "",
                (goto1 != null) ? scope(
                    let("goto1", goto1.name),
                    """
                    goto #goto1;
                    """
                ) : scope(
                    """
                    return;
                    """
                )

            ));
            return template.asToken();
        }
    }

    static class Method {
        private final Block entry;
        private final List<Block> blocks = new ArrayList<Block>();

        private final List<Local> locals = new ArrayList<Local>();
        private final int localsSize;

        public Method() {
            this.entry = new Block();
            blocks.add(this.entry);

            int n = 1 + RANDOM.nextInt(10);
            int j = 0;
            for (int i = 0; i < n; i++) {
                JasmType t = randomType();
                locals.add(new Local(j, t));
                j += t.slots();
            }
            this.localsSize = j;
        }

        public void mutate() {
            Block b = blocks.get(RANDOM.nextInt(blocks.size()));

            // TODO: other cfg
            // if/else
            Block b0 = new Block();
            Block b1 = new Block();
            Block b2 = new Block();
            b2.out0 = b.out0;
            b2.out1 = b.out1;
            b0.out0 = b2;
            b1.out0 = b2;
            b.out0 = b0;
            b.out1 = b1;
            blocks.add(b0);
            blocks.add(b1);
            blocks.add(b2);
        }

        public Object pushType(JasmType type) {
            List<Local> localIndices = locals.stream()
                .filter(l -> l.type == type)
                .toList();
            if (localIndices.size() > 0 && RANDOM.nextBoolean()) {
                var l = localIndices.get(RANDOM.nextInt(localIndices.size()));
                return l.type.prefix() + "load " + l.index + ";\n";
            }
            return type.pushCon();
        }

        public Object popType(JasmType type) {
            List<Local> localIndices = locals.stream()
                .filter(l -> l.type == type)
                .toList();
            if (localIndices.size() > 0 && RANDOM.nextBoolean()) {
                var l = localIndices.get(RANDOM.nextInt(localIndices.size()));
                return l.type.prefix() + "store " + l.index + ";\n";
            }
            return switch(type.slots()) {
                case 1 -> "pop;\n";
                case 2 -> "pop2;\n";
                default -> throw new RuntimeException("not supported: " + type);
            };
        }

        public Object ballancedOp() {
            Operation op = OPERATIONS.get(RANDOM.nextInt(OPERATIONS.size()));
            var template = Template.make(() -> scope(
                """
                // ballanced op:
                """,
                randomOp(OPERATIONS)
            ));
            return template.asToken();
        }

        public Object randomOp(List<Operation> ops) {
            Operation op = ops.get(RANDOM.nextInt(ops.size()));
            var template = Template.make(() -> scope(
                op.in.stream().map(t -> pushType(t)).toList(),
                (op.op == null) ? "" : scope(let("op", op.op), "#op;\n"),
                op.out.stream().map(t -> popType(t)).toList()
            ));
            return template.asToken();
        }

        public Object maybeGoto(String label) {
            JasmType type = randomType();
            List<Operation> ops = type.ifGotoOperations(label);
            return randomOp(ops);
        }

        // TODO: non-ballanced, have stack depth at block boundary.
        public Object blockBody() {
            int n = RANDOM.nextInt(3);
            return Collections.nCopies(n, ballancedOp());
        }

        public Object token() {

            var template = Template.make(() -> scope(
                let("localsSize", localsSize),
                """
                public static Method test:"()V"
                stack 20 locals #localsSize
                {
                // Init locals:
                """,
                locals.stream().map(l -> scope(
                    l.type.pushCon(),
                    l.type.prefix() + "store " + l.index + ";\n"
                )).toList(),
                """
                // Blocks:
                """,
                blocks.stream().map(b -> b.token(this)).toList(),
                """
                }
                """

            ));
            return template.asToken();
        }
    }

    public static String generate() {
        Method method = new Method();
        for (int i = 0; i < 10; i++) {
            method.mutate();
        }

        var template = Template.make(() -> scope(
            """
            package compiler/loopopts/templated;

            super public class Templated {
            """,
            method.token(),
            """
            }
            """
        ));
        return template.render();
    }
}


