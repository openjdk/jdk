/*
 * Copyright (c) 2025, Arm Limited. All rights reserved.
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
package compiler.c2.aarch64;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.ListIterator;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.process.ProcessTools;

/*
 * @test
 * @summary Calls to c2i interface stubs should be generated with near branches
 * for segmented code cache up to 250MB
 * @library /test/lib /
 *
 * @requires vm.flagless
 * @requires os.arch=="aarch64"
 * @requires vm.debug == false
 * @requires vm.compiler2.enabled
 *
 * @run driver compiler.c2.aarch64.TestStaticCallStub
 */
public class TestStaticCallStub {

    private static class InstructionList extends ArrayList<Instruction> {
        public InstructionList(List<Instruction> instructions) {
            super(instructions);
        }

        public boolean matches(List<String> codes) {
            if (size() != codes.size()) {
                return false;
            }

            return IntStream.range(0, size()).allMatch(i -> get(i).matches(codes.get(i)));
        }
    }

    private static abstract class Instruction {
        private static final List<Instruction> registry = new ArrayList<>();

        protected static void register(Instruction inst) {
            registry.add(inst);
        }

        private static String reverseEndian(String encoding) {
            if (encoding.length() != 8) {
                throw new IllegalArgumentException("Input must be 8 hex characters long.");
            }

            return new StringBuilder()
                    .append(encoding, 6, 8)
                    .append(encoding, 4, 6)
                    .append(encoding, 2, 4)
                    .append(encoding, 0, 2)
                    .toString();
        }

        public final boolean matches(int encoding) {
            return (encoding & mask()) == value();
        }

        public final boolean matches(String encoding) {
            String cleaned = encoding.replaceAll("\\s+", "");

            if (cleaned.matches("^[0-9a-fA-F]{8}$")) {
                return matches((int) Long.parseLong(reverseEndian(cleaned), 16));
            }

            return cleaned.equals(opcode());
        }

        protected abstract int mask();

        protected abstract int value();

        protected abstract String opcode();
    }

    private static class B extends Instruction {
        public static final B INSTANCE = new B();

        static {
            Instruction.register(INSTANCE);
        }

        public static final B get() {
            return INSTANCE;
        }

        protected final int mask() {
            return 0b1111_1100_0000_0000_0000_0000_0000_0000;
        }

        protected final int value() {
            return 0b0001_0100_0000_0000_0000_0000_0000_0000;
        }

        protected final String opcode() {
            return "b";
        }
    }

    private static class BR extends Instruction {
        public static final BR INSTANCE = new BR();

        public static final BR get() {
            return INSTANCE;
        }

        protected final int mask() {
            return 0b1111_1111_1111_1111_1111_1100_0001_1111;
        }

        protected final int value() {
            return 0b1101_0110_0001_1111_0000_0000_0000_0000;
        }

        protected final String opcode() {
            return "br";
        }
    };

    private static class ISB extends Instruction {
        public static final ISB INSTANCE = new ISB();

        static {
            Instruction.register(INSTANCE);
        }

        public static final ISB get() {
            return INSTANCE;
        }

        protected final int mask() {
            return 0b1111_1111_1111_1111_1111_0000_1111_1111;
        }

        protected final int value() {
            return 0b1101_0101_0000_0011_0011_0000_1101_1111;
        }

        protected final String opcode() {
            return "isb";
        }
    }

    private static class MOVK extends Instruction {
        public static final MOVK INSTANCE = new MOVK();

        static {
            Instruction.register(INSTANCE);
        }

        public static final MOVK get() {
            return INSTANCE;
        }

        protected final int mask() {
            return 0b1111_1111_1000_0000_0000_0000_0000_0000;
        }

        protected final int value() {
            return 0b1111_0010_1000_0000_0000_0000_0000_0000;
        }

        protected final String opcode() {
            return "movk";
        }
    }

    private static class MOVZ extends Instruction {
        public static final MOVZ INSTANCE = new MOVZ();

        static {
            Instruction.register(INSTANCE);
        }

        public static final MOVZ get() {
            return INSTANCE;
        }

        protected final int mask() {
            return 0b1111_1111_1000_0000_0000_0000_0000_0000;
        }

        protected final int value() {
            return 0b1101_0010_1000_0000_0000_0000_0000_0000;
        }

        protected final String opcode() {
            return "mov"; // this is not a typo
        }
    }

    public static void main(String[] args) throws Exception {
        if (args.length == 0) {
            // Main VM: fork VM with options
            runVM(true);
            runVM(false);
            return;
        }
        if (args.length > 0) {
            // We are in a forked VM. Just exit
            System.out.println("Ok");
        }
    }

    static List<String> extractOpcodeOrBytecodes(String line) {
        int colonIndex = line.indexOf(':');
        if (colonIndex != -1) {
            line = line.substring(colonIndex + 1).trim();
        }

        int semicolonIndex = line.indexOf(';');
        if (semicolonIndex != -1) {
            line = line.substring(0, semicolonIndex).trim();
        }

        if (line.isBlank()) {
            return Collections.emptyList();
        }

        String[] words = line.split("\\s+");
        if (words.length > 0) {
            String opcode = words[0];
            // Does this look like a bytecode instead?
            if (opcode.matches("^[0-9a-fA-F]{4}$")) {
                List<String> retval = Arrays.stream(line.split("\\|"))
                        .map(String::trim)
                        .collect(Collectors.toList());
                return retval;
            }

            return new ArrayList<>(Arrays.asList(opcode));
        }

        return Collections.emptyList();
    }

    static List<String> extractCodesN(ListIterator<String> itr, int n) {
        List<String> extracted = new ArrayList<>();

        while (itr.hasNext() && extracted.size() < n) {
            int left = n - extracted.size();
            extractOpcodeOrBytecodes(itr.next()).stream().limit(left).forEach(extracted::add);
        }

        return extracted;
    }

    static void verifyNearStaticCall(ListIterator<String> itr) {
        InstructionList nearStaticCallInstList = new InstructionList(
                Arrays.asList(ISB.get(), MOVZ.get(), MOVK.get(), MOVK.get(), B.get()));
        List<String> codes = extractCodesN(itr, nearStaticCallInstList.size());

        if (!nearStaticCallInstList.matches(codes)) {
            throw new RuntimeException(
                    "for code cache < 250MB the static call stub is expected to be implemented using near branch");
        }

        return;
    }

    static void verifyFarStaticCall(ListIterator<String> itr) {
        InstructionList farStaticCallInstList = new InstructionList(Arrays.asList(ISB.get(), MOVZ.get(), MOVK.get(),
                MOVK.get(), MOVZ.get(), MOVK.get(), MOVK.get(), BR.get()));
        List<String> codes = extractCodesN(itr, farStaticCallInstList.size());

        if (!farStaticCallInstList.matches(codes)) {
            throw new RuntimeException(
                    "for code cache > 250MB the static call stub is expected to be implemented using far branch");
        }

        return;
    }

    static void runVM(boolean bigCodeCache) throws Exception {
        String className = TestStaticCallStub.class.getName();
        String[] procArgs = {
                "-XX:-Inline",
                "-Xcomp",
                "-Xbatch",
                "-XX:+TieredCompilation",
                "-XX:+SegmentedCodeCache",
                "-XX:ReservedCodeCacheSize=" + (bigCodeCache ? "256M" : "200M"),
                "-XX:+UnlockDiagnosticVMOptions",
                "-XX:CompileCommand=option," + className + "::main,bool,PrintAssembly,true",
                className };

        ProcessBuilder pb = ProcessTools.createLimitedTestJavaProcessBuilder(procArgs);
        OutputAnalyzer output = new OutputAnalyzer(pb.start());
        System.out.println(output.getOutput());
        List<String> lines = output.asLines();

        ListIterator<String> itr = lines.listIterator();
        while (itr.hasNext()) {
            String line = itr.next();
            if (line.contains("{static_stub}")) {
                itr.previous();
                if (bigCodeCache) {
                    verifyFarStaticCall(itr);
                } else {
                    verifyNearStaticCall(itr);
                }
                return;
            }
        }
        throw new RuntimeException("Assembly output: static call stub is not found");
    }
}
