/*
 * Copyright (c) 2026, Alibaba Group Holding Limited. All Rights Reserved.
 * Copyright (c) 2018, 2020, Red Hat, Inc. All rights reserved.
 * Copyright (c) 2021, 2026, Oracle and/or its affiliates. All rights reserved.
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
 * common code to run and validate tests of code generation for
 * volatile ops on RISC-V
 *
 * incoming args are <testclass> <testtype>
 *
 * where <testclass> in {TestVolatileLoad,
 *                       TestVolatileStore,
 *                       TestUnsafeVolatileLoad,
 *                       TestUnsafeVolatileStore}
 * and <testtype> in {G1,
 *                    Serial,
 *                    Parallel,
 *                    Shenandoah}
 */

package compiler.c2.riscv64;

import java.util.List;
import java.util.ListIterator;
import java.util.regex.Pattern;

import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.process.ProcessTools;

// runner class that spawns a new JVM to exercise a combination of
// volatile MemOp and GC, once with the Zalasr l{w|d}.aq and
// s{w|d}.rl sequences in use and once without them. this runner
// parses the PrintOptoAssembly output checking that the generated code
// is correct, i.e. that the access is annotated and the membars around
// it elided exactly when Zalasr is in use, and that neither happens
// when it is not.

public class TestVolatiles {
    public void runtest(String classname, String testType) throws Throwable {
        // n.b. clients omit the package name for the class
        String fullclassname = "compiler.c2.riscv64." + classname;

        // Zalasr's availability depends on the CPU, so the VM is asked
        // what it settled on rather than forcing the flag on: forcing it
        // where the extension is not implemented would just make the JIT
        // emit instructions which trap.
        boolean zalasr = probeZalasr();
        System.out.println("UseZalasr=" + zalasr);

        // build up a command line for the spawned JVM
        String[] procArgs;
        int argcount = 13;
        // add the GC-selecting argument according to test type
        switch (testType) {
        case "G1":
            procArgs = new String[argcount];
            procArgs[argcount - 3] = "-XX:+UseG1GC";
            break;
        case "Parallel":
            procArgs = new String[argcount];
            procArgs[argcount - 3] = "-XX:+UseParallelGC";
            break;
        case "Serial":
            procArgs = new String[argcount];
            procArgs[argcount - 3] = "-XX:+UseSerialGC";
            break;
        case "Shenandoah":
            procArgs = new String[argcount];
            procArgs[argcount - 3] = "-XX:+UseShenandoahGC";
            break;
        default:
            throw new RuntimeException("unexpected test type " + testType);
        }

        // fill in arguments common to all cases
        procArgs[1] = "-XX:+UnlockExperimentalVMOptions";
        procArgs[2] = "-XX:+UnlockDiagnosticVMOptions";
        procArgs[3] = "-XX:-UseZtso";
        procArgs[4] = "-XX:-BackgroundCompilation";
        procArgs[5] = "-XX:-TieredCompilation";
        procArgs[6] = "-XX:+PrintOptoAssembly";
        procArgs[7] = "-XX:CompileCommand=compileonly," + fullclassname + "::" + "test*";
        procArgs[8] = "--add-exports";
        procArgs[9] = "java.base/jdk.internal.misc=ALL-UNNAMED";
        procArgs[argcount - 1] = fullclassname;

        // the first round of tests disables Zalasr so the fall back
        // membar/fence sequence can be checked
        procArgs[0] = "-XX:+UseCompressedOops";
        procArgs[argcount - 2] = "-XX:-UseZalasr";
        runtest(classname, testType, true, false, procArgs);

        procArgs[0] = "-XX:-UseCompressedOops";
        runtest(classname, testType, false, false, procArgs);

        // the second round enables Zalasr and checks the annotated
        // sequence, wherever the hardware provides the extension
        if (zalasr) {
            procArgs[0] = "-XX:+UseCompressedOops";
            procArgs[argcount - 2] = "-XX:+UseZalasr";
            runtest(classname, testType, true, true, procArgs);

            procArgs[0] = "-XX:-UseCompressedOops";
            runtest(classname, testType, false, true, procArgs);
        }
    }

    // probe what the VM settles UseZalasr to under the flags common to
    // every run above. this also keeps the test honest about the gating
    // in VM_Version::initialize(), where Zalasr is switched off again on
    // a JVM built by a pre-psABI toolchain and under Ztso.
    private boolean probeZalasr() throws Throwable
    {
        String[] procArgs = {
            "-XX:+UnlockExperimentalVMOptions",
            "-XX:-UseZtso",
            "-XX:+PrintFlagsFinal",
            "-version"
        };
        ProcessBuilder pb = ProcessTools.createLimitedTestJavaProcessBuilder(procArgs);
        OutputAnalyzer output = new OutputAnalyzer(pb.start());
        output.shouldHaveExitValue(0);

        for (String line : output.asLines()) {
            if (line.matches("^\\s*bool\\s+UseZalasr\\s+=\\s+true.*")) {
                return true;
            }
            if (line.matches("^\\s*bool\\s+UseZalasr\\s+=\\s+false.*")) {
                return false;
            }
        }
        throw new RuntimeException("UseZalasr not found in -XX:+PrintFlagsFinal output!\n\n" + output.getOutput());
    }

    public void runtest(String classname, String testType, boolean useCompressedOops, boolean zalasr, String[] procArgs) throws Throwable {
        ProcessBuilder pb = ProcessTools.createLimitedTestJavaProcessBuilder(procArgs);
        OutputAnalyzer output = new OutputAnalyzer(pb.start());

        output.stderrShouldBeEmptyIgnoreVMWarnings();
        output.stdoutShouldNotBeEmpty();
        output.shouldHaveExitValue(0);

        // check the output for the correct asm sequence as
        // appropriate to test class, test type and whether Zalasr
        // was in use
        checkoutput(output, classname, testType, useCompressedOops, zalasr);
    }

    // skip through output returning a line containing the desired
    // substring or null
    private String skipTo(ListIterator<String> iter, String substring)
    {
        while (iter.hasNext()) {
            String nextLine = iter.next();
            if (nextLine.matches(".*" + substring + ".*")) {
                return nextLine;
            }
        }
        return null;
    }

    // locate the start of compiler output for the desired method and
    // then check that each expected instruction occurs in the output
    // in the order supplied, and that none of the forbidden ones occurs
    // in between. throw an exception if not. n.b. the spawned JVM's
    // output is included in the exception message to make it easier to
    // identify what is missing.
    //
    // checking the forbidden list matters as much as the expected one: a
    // predicate which elides a membar the instruction does not replace
    // leaves silently broken code behind, and one which fails to elide
    // leaves the optimization silently doing nothing. either mistake
    // still leaves the plain instruction/fence sequence matching, so it
    // has to be an error to see the other configuration's markers too.
    private void checkCompile(List<String> lines, ListIterator<String> iter, String methodname,
                              String[] expected, String[] forbidden, OutputAnalyzer output)
    {
        // trace call to allow eyeball check of what we are checking against
        System.out.println("checkCompile(" + methodname + ",");
        String sepr = "  { ";
        for (String s : expected) {
            System.out.print(sepr);
            System.out.print(s);
            sepr = ",\n    ";
        }
        System.out.println(" })");

        int startIdx = iter.nextIndex();

        // look for the start of an opto assembly print block
        String match = skipTo(iter, Pattern.quote("{method}"));
        if (match == null) {
            throw new RuntimeException("Missing compiler output for " + methodname + "!\n\n" + output.getOutput());
        }
        // check the compiled method name is right
        match = skipTo(iter, Pattern.quote("- name:"));
        if (match == null) {
            throw new RuntimeException("Missing compiled method name!\n\n" + output.getOutput());
        }
        if (!match.contains(methodname)) {
            throw new RuntimeException("Wrong method " + match + "!\n  -- expecting " + methodname + "\n\n" + output.getOutput());
        }
        // make sure we can match each expected term in order
        for (String s : expected) {
            match = skipTo(iter, s);
            if (match == null) {
                throw new RuntimeException("Missing expected output " + s + "!\n\n" + output.getOutput());
            }
        }

        // now make sure none of the forbidden terms shows up anywhere in
        // between, i.e. in the same method as the expected sequence above
        int endIdx = iter.nextIndex();
        for (String s : forbidden) {
            for (String line : lines.subList(startIdx, endIdx)) {
                if (line.matches(".*" + s + ".*")) {
                    throw new RuntimeException("Unexpected output " + s + " for " + methodname + ": " + line + "!\n\n" + output.getOutput());
                }
            }
        }
    }

    // check for expected asm output from a volatile load
    private void checkload(OutputAnalyzer output, boolean useCompressedOops, boolean zalasr) throws Throwable
    {
        List<String> lines = output.asLines();
        ListIterator<String> iter = lines.listIterator();

        String[] matches;
        String[] forbidden;

        if (zalasr) {
            // l{w|d}.aq orders the load against everything after it, so
            // the trailing MemBarAcquire goes away. the narrow oop load
            // is an lw.aq because Zalasr has no zero extending form; the
            // widening is a separate zext.
            //
            // the oop load may be matched by a GC specific rule --
            // Shenandoah has an acquiring variant ahead of its barrier --
            // but it emits the same l{w|d}.aq.
            matches = new String[] {
                "lw\\.aq",
                "#@unnecessary_membar_acquire_rvwmo",
                "#@Ret"
            };
            forbidden = new String[] { "#@membar_acquire_rvwmo" };
            checkCompile(lines, iter, "testInt", matches, forbidden, output);

            matches = new String[] {
                useCompressedOops ? "lw\\.aq" : "ld\\.aq",
                "#@unnecessary_membar_acquire_rvwmo",
                "#@Ret"
            };
            checkCompile(lines, iter, "testObj", matches, forbidden, output);
        } else {
            matches = new String[] {
                "#@membar_acquire_rvwmo",
                "fence r, rw",
                "#@Ret"
            };
            forbidden = new String[] { "\\.aq\\b", "\\.rl\\b" };
            checkCompile(lines, iter, "testInt", matches, forbidden, output);
            checkCompile(lines, iter, "testObj", matches, forbidden, output);
        }
    }

    // check for expected asm output from a volatile store
    private void checkstore(OutputAnalyzer output, boolean useCompressedOops, boolean zalasr) throws Throwable
    {
        List<String> lines = output.asLines();
        ListIterator<String> iter = lines.listIterator();

        String[] matches;
        String[] forbidden;

        if (zalasr) {
            // s{w|d}.rl orders the store against everything before it,
            // so the leading MemBarRelease goes away, and the trailing
            // MemBarVolatile follows it because RCsc release-acquire
            // pairs supply the StoreLoad edge a volatile store needs.
            //
            // the oop store may be matched by a GC specific rule -- G1
            // and Shenandoah both have releasing variants -- but both of
            // them end up emitting the same s{w|d}.rl.
            matches = new String[] {
                "#@unnecessary_membar_release_rvwmo",
                "sw\\.rl",
                "#@unnecessary_membar_volatile_rvwmo",
                "#@Ret"
            };
            forbidden = new String[] { "#@membar_release_rvwmo", "#@membar_volatile_rvwmo" };
            checkCompile(lines, iter, "testInt", matches, forbidden, output);

            matches = new String[] {
                "#@unnecessary_membar_release_rvwmo",
                useCompressedOops ? "sw\\.rl" : "sd\\.rl",
                "#@unnecessary_membar_volatile_rvwmo",
                "#@Ret"
            };
            checkCompile(lines, iter, "testObj", matches, forbidden, output);
        } else {
            matches = new String[] {
                "#@membar_release_rvwmo",
                "fence rw, w",
                "#@membar_volatile_rvwmo",
                "fence w, r",
                "#@Ret"
            };
            forbidden = new String[] { "\\.aq\\b", "\\.rl\\b" };
            checkCompile(lines, iter, "testInt", matches, forbidden, output);
            checkCompile(lines, iter, "testObj", matches, forbidden, output);
        }
    }

    // perform a check appropriate to the classname
    private void checkoutput(OutputAnalyzer output, String classname, String testType, boolean useCompressedOops, boolean zalasr) throws Throwable
    {
        // trace call to allow eyeball check of what is being checked
        System.out.println("checkoutput(" +
                           classname + ", " +
                           testType + ", UseCompressedOops=" + useCompressedOops +
                           ", UseZalasr=" + zalasr + ")\n" +
                           output.getOutput());

        switch (classname) {
        case "TestVolatileLoad":
        case "TestUnsafeVolatileLoad":
            checkload(output, useCompressedOops, zalasr);
            break;
        case "TestVolatileStore":
        case "TestUnsafeVolatileStore":
            checkstore(output, useCompressedOops, zalasr);
            break;
        default:
            throw new RuntimeException("unexpected test class " + classname);
        }
    }
}
