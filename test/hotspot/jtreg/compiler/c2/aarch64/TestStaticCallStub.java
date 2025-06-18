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

import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.process.ProcessTools;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.*;

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

    static String[] nearStaticCallOpcodeSeq = {"isb", "mov", "movk", "movk", "b"};
    static String[] farStaticCallOpcodeSeq = {"isb", "mov", "movk", "movk", "mov", "movk", "movk", "br"};

    static String extractOpcode(String line) {
        line = line.trim();
        int semicolonIndex = line.indexOf(';');
        if (semicolonIndex != -1) {
            line = line.substring(0, semicolonIndex).trim();
        }

        String[] words = line.split("\\s+");
        if (words.length > 1) {
            return words[1];
        }

        return "";
    }

    static List<String> extractOpcodesN(ListIterator<String> itr, int n) {
        List<String> extractedOpcodes = new ArrayList<>();

        while (itr.hasNext() && extractedOpcodes.size() < n) {
            String opcode = extractOpcode(itr.next());
            if (!opcode.isEmpty()) {
                extractedOpcodes.add(opcode);
            }
        }

        return extractedOpcodes;
    }

    static void verifyNearStaticCall(ListIterator<String> itr) {
        List<String> extractedOpcodes = extractOpcodesN(itr, nearStaticCallOpcodeSeq.length);

        if (!Arrays.asList(nearStaticCallOpcodeSeq).equals(extractedOpcodes)) {
            throw new RuntimeException("for code cache < 250MB the static call stub is expected to be implemented using near branch");
        }

        return;
    }

    static void verifyFarStaticCall(ListIterator<String> itr) {
        List<String> extractedOpcodes = extractOpcodesN(itr, farStaticCallOpcodeSeq.length);

        if (!Arrays.asList(farStaticCallOpcodeSeq).equals(extractedOpcodes)) {
            throw new RuntimeException("for code cache > 250MB the static call stub is expected to be implemented using far branch");
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
            className};


        ProcessBuilder pb = ProcessTools.createLimitedTestJavaProcessBuilder(procArgs);
        OutputAnalyzer output = new OutputAnalyzer(pb.start());
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

