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

/**
 * @test
 * @bug 8361352
 * @summary In OSR compilation, we must correctly determine the initialization
 * state of value objects coming from above the OSR start, and not consider
 * everything as potentially early larval. Value objects that are known to be
 * unrestricted (late larval or fully initialized) are immutable, and can be
 * scalarized.
 * @library /test/jdk/java/lang/invoke/common
 * @enablePreview
 * @requires vm.debug
 * @run main compiler.valhalla.inlinetypes.LarvalDetectionAboveOSR
 */

package compiler.valhalla.inlinetypes;

import test.java.lang.invoke.lib.InstructionHelper;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.lang.classfile.Label;
import java.lang.classfile.TypeKind;
import java.lang.constant.ClassDesc;
import java.lang.constant.MethodTypeDesc;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.nio.ByteBuffer;
import java.nio.file.FileSystems;
import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static test.java.lang.invoke.lib.InstructionHelper.classDesc;

public class LarvalDetectionAboveOSR {
    public static ArrayList<String> runInSeparateVM(String scenario, String compile_pattern) throws Throwable {
        String separator = FileSystems.getDefault().getSeparator();
        String path = System.getProperty("java.home") + separator + "bin" + separator + "java";

        String javaFile = LarvalDetectionAboveOSR.class.getProtectionDomain().getCodeSource().getLocation().getPath();
        String classpath = javaFile.replace("LarvalDetectionAboveOSR.java", "");
        ProcessBuilder processBuilder = new ProcessBuilder(
                path, "-cp", classpath,
                "--enable-preview", "-XX:-TieredCompilation",
                "-XX:CompileCommand=compileonly," + compile_pattern,
                "-XX:CompileCommand=printcompilation,*::*",
                "-XX:CompileCommand=PrintIdealPhase,*::*,BEFORE_MACRO_EXPANSION",
                "-XX:+PrintEliminateAllocations",
                LarvalDetectionAboveOSR.class.getCanonicalName(), scenario
        );
        Process process = processBuilder.start();
        processBuilder.redirectErrorStream(true);

        BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
        BufferedReader error_reader = new BufferedReader(new InputStreamReader(process.getErrorStream()));
        var lines = new ArrayList<String>();
        var error_lines = new ArrayList<String>();
        String line;
        while ((line = reader.readLine()) != null) {
            lines.add(line);
        }
        while ((line = error_reader.readLine()) != null) {
            error_lines.add(line);
        }
        process.waitFor();

        if (process.exitValue() != 0) {
            System.out.println("stdout:");
            System.out.println(String.join("\n", lines));
            System.out.println("stderr:");
            System.out.println(String.join("\n", error_lines));
            throw new RuntimeException("Process exited with status: " + process.exitValue());
        }

        return lines;
    }

    static class CompilationBlock {
        public boolean is_osr;
        public ArrayList<String> lines = new ArrayList<>();

        CompilationBlock(boolean osr) {
            is_osr = osr;
        }
    }

    static Pattern print_compilation_regex = Pattern.compile("\\d+\\s+\\d+\\s+(%\\s+)?compiler\\.valhalla\\.inlinetypes\\.\\S*::[a-zA-Z0-9_]* (@ \\d+ )?\\(\\d+ bytes\\)");
    static Pattern allocate_elimination_regex = Pattern.compile("\\+{4} Eliminated: \\d+ Allocate");
    static Pattern allocate_regex = Pattern.compile("\\s*\\d+ {2}Allocate {2}={3}.*");

    static ArrayList<CompilationBlock> splitLines(ArrayList<String> lines) {
        var blocks = new ArrayList<CompilationBlock>();
        for (String line : lines) {
            Matcher m = print_compilation_regex.matcher(line);
            if (m.matches()) {
                blocks.add(new CompilationBlock(line.contains("%")));
            } else if (!blocks.isEmpty()) {
                blocks.getLast().lines.add(line);
            }
        }
        return blocks;
    }

    static void analyzeLines(ArrayList<String> lines) {
        var blocks = splitLines(lines);
        int blocks_actually_checked = 0;
        for (CompilationBlock block : blocks) {
            if (checkBlock(block)) {
                blocks_actually_checked++;
            }
        }
        if (blocks_actually_checked == 0) {
            throw new RuntimeException("Found no OSR logging block to check.");
        }
    }

    static boolean checkBlock(CompilationBlock block) {
        if (!block.is_osr) return false;  // It's about OSR here!

        int eliminated_allocations = 0;
        int i = 0;
        while (i < block.lines.size()) {
            String line = block.lines.get(i);
            i++;
            if (line.equals("AFTER: BEFORE_MACRO_EXPANSION")) {
                break;
            }
            if (allocate_elimination_regex.matcher(line).matches()) {
                eliminated_allocations++;
            }
        }
        if (eliminated_allocations == 0) {
            throw new RuntimeException("No allocation elimination found, there should be some.");
        }
        if (i >= block.lines.size()) {
            throw new RuntimeException("Cannot find BEFORE_MACRO_EXPANSION printout");
        }
        while (i < block.lines.size()) {
            if (allocate_regex.matcher(block.lines.get(i)).matches()) {
                throw new RuntimeException("Found allocation in line: " + block.lines.get(i));
            }
            i++;
        }
        return true;
    }

    public static short test() {
        ByteBuffer bf = ByteBuffer.allocate(8);
        return bf.getShort(0);
    }

    public static void main(String[] args) throws Throwable {
        if (args.length != 0) {
            switch (args[0]) {
                case "without_new" -> MyNumber.main_without_new();
                case "with_new" -> MyNumber.main_with_new();
                case "bytecode" -> Bytecode.test();
                default -> throw new RuntimeException("Wrong scenario: " + args[0]);
            }
            return;
        }
        analyzeLines(runInSeparateVM("without_new", "compiler.valhalla.inlinetypes.MyNumber::loop*"));
        analyzeLines(runInSeparateVM("with_new", "compiler.valhalla.inlinetypes.MyNumber::loop*"));
        analyzeLines(runInSeparateVM("bytecode", "compiler.valhalla.inlinetypes.Bytecode$Code_0::meth"));
    }
}

value class MyNumber {
    public long l;
    static int MANY = 1_000_000_000;

    MyNumber(long l) {
        this.l = l;
    }

    MyNumber add(long v) {
        return new MyNumber(l + v);
    }

    static long loop_without_new(MyNumber dec) {
        for (int i = 0; i < MANY; ++i) {
            dec = dec.add(i);
        }
        return dec.l;
    }

    public static void main_without_new() {
        for (int i = 0; i < 10; ++i) {
            MyNumber dec = new MyNumber(123);
            loop_without_new(dec);
        }
    }

    static long loop_with_new() {
        MyNumber dec = new MyNumber(123);
        for (int i = 0; i < MANY; ++i) {
            dec = dec.add(i);
        }
        return dec.l;
    }

    public static void main_with_new() {
        for (int i = 0; i < 10; ++i) {
            loop_with_new();
        }
    }
}

class Bytecode {
    static MethodHandles.Lookup LOOKUP = MethodHandles.lookup();

    static void test() throws Throwable {
        var myNumber = MyNumber.class;
        final ClassDesc myNumberDesc = classDesc(myNumber);

        MethodHandle meth = InstructionHelper.buildMethodHandle(
                LOOKUP,
                "meth",
                MethodType.methodType(myNumber, int.class),
                CODE -> {
                    Label loop = CODE.newLabel();
                    CODE
                            .new_(myNumberDesc)
                            .dup()
                            // stack: early larval (this one we init), early larval (this one we store in local 10)
                            .astore(10)
                            .iconst_0()
                            .i2l()
                            .invokespecial(myNumberDesc, "<init>", MethodTypeDesc.ofDescriptor("(J)V"))
                            // local 10 should also be initialized, it is now not early larval, so scalarization is allowed

                            // local(11) = 0
                            .iconst_0()
                            .istore(11)

                            .labelBinding(loop)
                            // local(10) = local(10).add((long)local(11))
                            .aload(10)
                            .iload(11)
                            .i2l()
                            .invokevirtual(myNumberDesc, "add", MethodTypeDesc.ofDescriptor("(J)Lcompiler/valhalla/inlinetypes/MyNumber;"))
                            .astore(10)

                            // local(11)++
                            .iload(11)
                            .iconst_1()
                            .iadd()
                            .dup()
                            // if local(11) < param(0) goto loop
                            .istore(11)
                            .iload(0)
                            .if_icmplt(loop)

                            .aload(10)
                            .return_(TypeKind.from(myNumberDesc));
                });
        var _ = (MyNumber)meth.invokeExact(MyNumber.MANY);
    }
}