/*
 * Copyright (c) 2009, 2018, Oracle and/or its affiliates. All rights reserved.
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


package org.graalvm.compiler.core.gen;

import static org.graalvm.compiler.core.gen.InstructionPrinter.InstructionLineColumn.BCI;
import static org.graalvm.compiler.core.gen.InstructionPrinter.InstructionLineColumn.END;
import static org.graalvm.compiler.core.gen.InstructionPrinter.InstructionLineColumn.INSTRUCTION;
import static org.graalvm.compiler.core.gen.InstructionPrinter.InstructionLineColumn.USE;
import static org.graalvm.compiler.core.gen.InstructionPrinter.InstructionLineColumn.VALUE;

import org.graalvm.compiler.debug.LogStream;
import org.graalvm.compiler.nodes.StateSplit;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.ValueNodeUtil;

/**
 * A utility for {@linkplain #printInstruction(ValueNode) printing} a node as an expression or
 * statement.
 */
public class InstructionPrinter {

    /**
     * The columns printed in a tabulated instruction
     * {@linkplain InstructionPrinter#printInstructionListing(ValueNode) listing}.
     */
    public enum InstructionLineColumn {
        /**
         * The instruction's bytecode index.
         */
        BCI(2, "bci"),

        /**
         * The instruction's use count.
         */
        USE(7, "use"),

        /**
         * The instruction as a value.
         */
        VALUE(12, "tid"),

        /**
         * The instruction formatted as an expression or statement.
         */
        INSTRUCTION(19, "instr"),

        END(60, "");

        final int position;
        final String label;

        InstructionLineColumn(int position, String label) {
            this.position = position;
            this.label = label;
        }

        /**
         * Prints this column's label to a given stream after padding the stream with '_' characters
         * until its {@linkplain LogStream#position() position} is equal to this column's position.
         *
         * @param out the print stream
         */
        public void printLabel(LogStream out) {
            out.fillTo(position + out.indentationLevel(), '_');
            out.print(label);
        }

        /**
         * Prints space characters to a given stream until its {@linkplain LogStream#position()
         * position} is equal to this column's position.
         *
         * @param out the print stream
         */
        public void advance(LogStream out) {
            out.fillTo(position + out.indentationLevel(), ' ');
        }
    }

    private final LogStream out;

    public InstructionPrinter(LogStream out) {
        this.out = out;
    }

    public LogStream out() {
        return out;
    }

    /**
     * Prints a header for the tabulated data printed by {@link #printInstructionListing(ValueNode)}
     * .
     */
    public void printInstructionListingHeader() {
        BCI.printLabel(out);
        USE.printLabel(out);
        VALUE.printLabel(out);
        INSTRUCTION.printLabel(out);
        END.printLabel(out);
        out.println();
    }

    /**
     * Prints an instruction listing on one line. The instruction listing is composed of the columns
     * specified by {@link InstructionLineColumn}.
     *
     * @param instruction the instruction to print
     */
    public void printInstructionListing(ValueNode instruction) {
        int indentation = out.indentationLevel();
        out.fillTo(BCI.position + indentation, ' ').print(0).fillTo(USE.position + indentation, ' ').print("0").fillTo(VALUE.position + indentation, ' ').print(
                        ValueNodeUtil.valueString(instruction)).fillTo(
                                        INSTRUCTION.position + indentation, ' ');
        printInstruction(instruction);
        if (instruction instanceof StateSplit) {
            out.print("  [state: " + ((StateSplit) instruction).stateAfter() + "]");
        }
        out.println();
    }

    public void printInstruction(ValueNode node) {
        out.print(node.toString());
    }
}
