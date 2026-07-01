/*
 * Copyright (c) 2025, 2026, Arm Limited. All rights reserved.
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

    record Instruction(String mnemonic, int mask, int value) {
    }

    private static final Instruction ISB  = new Instruction("isb",  0xFFFFFFFF, 0xD5033FDF);
    private static final Instruction MOVK = new Instruction("movk", 0xFF800000, 0xF2800000);
    private static final Instruction MOVZ = new Instruction("mov",  0xFF800000, 0xD2800000);
    private static final Instruction B =    new Instruction("b",    0xFC000000, 0x14000000);
    private static final Instruction BR =   new Instruction("br",   0xFFFFFC1F, 0xD61F0000);

    private static final Instruction[] nearStaticCallInsts = {ISB, MOVZ, MOVK, MOVK, B};
    private static final Instruction[] farStaticCallInsts =  {ISB, MOVZ, MOVK, MOVK, MOVZ, MOVK, MOVK, BR};

    static String instructionsSubstring(String line) {
        int colonIndex = line.indexOf(':');
        if (colonIndex != -1) {
            line = line.substring(colonIndex + 1).trim();
        }

        int semicolonIndex = line.indexOf(';');
        if (semicolonIndex != -1) {
            line = line.substring(0, semicolonIndex).trim();
        }

        return line;
    }

    static String extractMnemonic(String line) {
        String instructions = instructionsSubstring(line);

        if (instructions.isBlank()) {
            return "";
        }

        return instructions.split("\\s+")[0];
    }

    static List<Integer> extractOpcodes(String line) {
        List<Integer> opcodes = new ArrayList<>();
        String instructions = instructionsSubstring(line);

        if (instructions.isBlank()) {
            return Collections.emptyList();
        }

        String[] words = instructions.split("\\|");
        for (String word : words) {
            int value = Integer
                    .reverseBytes(Integer.parseUnsignedInt(word.replaceAll("\\s", ""), 16));
            opcodes.add(value);
        }

        return opcodes;
    }

    static List<String> extractMnemonicsN(ListIterator<String> iter, int n) {
        List<String> extracted = new ArrayList<>();

        while (iter.hasNext() && extracted.size() < n) {
            String mnemonic = extractMnemonic(iter.next());
            if (!mnemonic.isEmpty()) {
                extracted.add(mnemonic);
            }
        }

        return extracted;
    }

    static List<Integer> extractOpcodesN(ListIterator<String> iter, int n) {
        List<Integer> extracted = new ArrayList<>();

        while (iter.hasNext() && extracted.size() < n) {
            int left = n - extracted.size();
            extractOpcodes(iter.next()).stream().limit(left).forEach(extracted::add);
        }

        return extracted;
    }

    static boolean opcodesMatch(List<Integer> opcodes, Instruction[] insts) {
        return opcodes.size() == insts.length && IntStream.range(0, opcodes.size())
                .allMatch(i -> (opcodes.get(i) & insts[i].mask) == insts[i].value);
    }

    @FunctionalInterface
    interface StaticCallMatcher {
        boolean matches(ListIterator<String> iter, Instruction[] insts);
    }

    static boolean matchMnemonics(ListIterator<String> iter, Instruction[] insts) {
        return extractMnemonicsN(iter, insts.length)
                .equals(Arrays.stream(insts).map(Instruction::mnemonic).toList());
    }

    static boolean matchOpcodes(ListIterator<String> iter, Instruction[] insts) {
        return opcodesMatch(extractOpcodesN(iter, insts.length), insts);
    }

    static void verifyStaticCall(ListIterator<String> iter, StaticCallMatcher matcher,
            Instruction[] insts, String errorMessage) {
        if (!matcher.matches(iter, insts)) {
            throw new RuntimeException(errorMessage);
        }
    }

    static void runVM(boolean bigCodeCache) throws Exception {
        String className = TestStaticCallStub.class.getName();
        String[] procArgs = {
            "-XX:-Inline", "-Xcomp", "-Xbatch", "-XX:+TieredCompilation", "-XX:+SegmentedCodeCache",
            "-XX:ReservedCodeCacheSize=" + (bigCodeCache ? "256M" : "200M"),
            "-XX:+UnlockDiagnosticVMOptions", "-XX:PrintAssemblyOptions=",
            "-XX:CompileCommand=option," + className + "::main,bool,PrintAssembly,true", className,
            "child"
        };

        ProcessBuilder pb = ProcessTools.createLimitedTestJavaProcessBuilder(procArgs);
        OutputAnalyzer output = new OutputAnalyzer(pb.start());
        output.shouldHaveExitValue(0);
        ListIterator<String> iter = output.asLines().listIterator();

        try {
            // 1. Check whether printed instructions are disassembled
            boolean disassembled = false;
            while (iter.hasNext()) {
                String line = iter.next();
                if (line.contains("[Disassembly]")) {
                    disassembled = true;
                    break;
                }
                if (line.contains("[MachCode]")) {
                    break;
                }
            }
            StaticCallMatcher matcher = disassembled ? TestStaticCallStub::matchMnemonics
                                                     : TestStaticCallStub::matchOpcodes;


            // 2. Look for the block comment
            while (iter.hasNext()) {
                String line = iter.next();
                if (line.contains("{static_stub}")) {
                    iter.previous();
                    if (bigCodeCache) {
                        verifyStaticCall(iter, matcher, farStaticCallInsts,
                                "for code cache > 250MB the static call stub is expected to be implemented using far branch");
                    } else {
                        verifyStaticCall(iter, matcher, nearStaticCallInsts,
                                "for code cache < 250MB the static call stub is expected to be implemented using near branch");
                    }
                    return;
                }
            }
            throw new RuntimeException("Assembly output: static call stub is not found");
        } catch (RuntimeException ex) {
            System.out.println(output.getOutput());
            throw ex;
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
}
