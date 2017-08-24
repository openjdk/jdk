/*
 * Copyright (c) 2007, 2012, Oracle and/or its affiliates. All rights reserved.
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
// Checkstyle: stop

package org.graalvm.compiler.jtt.hotpath;

import org.junit.Test;

import org.graalvm.compiler.jtt.JTTTest;

/*
 */
public class HP_invoke01 extends JTTTest {

    private static int sum;

    public static int test(int count) {
        sum = 0;
        final Instruction[] instructions = new Instruction[]{new Instruction.Add(), new Instruction.Sub(), new Instruction.Mul(), new Instruction.Div()};
        final Visitor v = new Visitor();
        for (int i = 0; i < count; i++) {
            instructions[i % 4].accept(v);
        }
        return sum;
    }

    public static abstract class Instruction {

        public abstract void accept(Visitor v);

        public static abstract class Binary extends Instruction {

        }

        public static class Add extends Binary {

            @Override
            public void accept(Visitor v) {
                v.visit(this);
            }
        }

        public static class Sub extends Binary {

            @Override
            public void accept(Visitor v) {
                v.visit(this);
            }
        }

        public static class Mul extends Binary {

            @Override
            public void accept(Visitor v) {
                v.visit(this);
            }
        }

        public static class Div extends Binary {

            @Override
            public void accept(Visitor v) {
                v.visit(this);
            }
        }
    }

    @SuppressWarnings("unused")
    public static class Visitor {

        public void visit(Instruction.Add i) {
            sum += 7;
        }

        public void visit(Instruction.Sub i) {
            sum += 194127;
        }

        public void visit(Instruction.Mul i) {
            sum += 18991;
        }

        public void visit(Instruction.Div i) {
            sum += 91823;
        }
    }

    @Test
    public void run0() throws Throwable {
        runTest("test", 40);
    }

    @Test
    public void run1() throws Throwable {
        runTest("test", 80);
    }

}
