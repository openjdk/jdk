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

    private static final Instruction[] nearStaticCallInsts = {
        new Instruction("isb",  0xFFFFF0FF, 0xD50330DF), // ISB
        new Instruction("mov",  0xFF800000, 0xD2800000), // MOVZ
        new Instruction("movk", 0xFF800000, 0xF2800000), // MOVK
        new Instruction("movk", 0xFF800000, 0xF2800000), // MOVK
        new Instruction("b",    0xFC000000, 0x14000000)  // B
    };
    private static final Instruction[] farStaticCallInsts = {
        new Instruction("isb",  0xFFFFF0FF, 0xD50330DF), // ISB
        new Instruction("mov",  0xFF800000, 0xD2800000), // MOVZ
        new Instruction("movk", 0xFF800000, 0xF2800000), // MOVK
        new Instruction("movk", 0xFF800000, 0xF2800000), // MOVK
        new Instruction("mov",  0xFF800000, 0xD2800000), // MOVZ
        new Instruction("movk", 0xFF800000, 0xF2800000), // MOVK
        new Instruction("movk", 0xFF800000, 0xF2800000), // MOVK
        new Instruction("br",   0xFFFFFC1F, 0xD61F0000)  // BR
    };

    static String extractMnemonic(String line) {
        int colonIndex = line.indexOf(':');
        if (colonIndex != -1) {
            line = line.substring(colonIndex + 1).trim();
        }

        int semicolonIndex = line.indexOf(';');
        if (semicolonIndex != -1) {
            line = line.substring(0, semicolonIndex).trim();
        }

        if (line.isBlank()) {
            return "";
        }

        String[] words = line.split("\\s+");
        if (words.length > 0) {
            return words[0].trim();
        }

        return "";
    }

    static List<Integer> extractOpcodes(String line) {
        List<Integer> opcodes = new ArrayList<>();

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

        String[] words = line.split("\\|");
        for (String word : words) {
            String[] halfwords = word.trim().split("\\s+");
            int value = (Integer.parseUnsignedInt(halfwords[0].trim(), 16) << 16)
                    + Integer.parseUnsignedInt(halfwords[1].trim(), 16);
            value = Integer.reverseBytes(value);
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

    static boolean staticCallMatches(ListIterator<String> iter, Instruction[] insts,
            boolean disassembled) {
        boolean matches;

        if (disassembled) {
            List<String> extracted = extractMnemonicsN(iter, insts.length);
            matches = extracted.equals(Arrays.stream(insts).map(Instruction::mnemonic).toList());
        } else {
            List<Integer> extracted = extractOpcodesN(iter, insts.length);
            matches = opcodesMatch(extracted, insts);
        }

        return matches;
    }

    static void verifyNearStaticCall(ListIterator<String> iter, boolean disassembled) {
        if (!staticCallMatches(iter, nearStaticCallInsts, disassembled)) {
            throw new RuntimeException(
                    "for code cache < 250MB the static call stub is expected to be implemented using near branch");
        }

        return;
    }

    static void verifyFarStaticCall(ListIterator<String> iter, boolean disassembled) {
        if (!staticCallMatches(iter, farStaticCallInsts, disassembled)) {
            throw new RuntimeException(
                    "for code cache > 250MB the static call stub is expected to be implemented using far branch");
        }

        return;
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

            // 2. Look for the block comment
            while (iter.hasNext()) {
                String line = iter.next();
                if (line.contains("{static_stub}")) {
                    iter.previous();
                    if (bigCodeCache) {
                        verifyFarStaticCall(iter, disassembled);
                    } else {
                        verifyNearStaticCall(iter, disassembled);
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
