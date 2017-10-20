/*
 * Copyright (c) 2017, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.hotspot.test;

import static org.graalvm.compiler.test.SubprocessUtil.getVMCommandLine;
import static org.graalvm.compiler.test.SubprocessUtil.withoutDebuggerArguments;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.graalvm.compiler.api.directives.GraalDirectives;
import org.graalvm.compiler.core.test.GraalCompilerTest;
import org.graalvm.compiler.test.SubprocessUtil;
import org.graalvm.compiler.test.SubprocessUtil.Subprocess;
import org.junit.Assert;
import org.junit.Test;

import sun.misc.Unsafe;

/**
 * Tests that a hs_err crash log contains expected content.
 */
public class HsErrLogTest extends GraalCompilerTest {

    @Test
    public void test1() throws IOException, InterruptedException {
        List<String> args = new ArrayList<>();
        if (Java8OrEarlier) {
            args.add("-XX:-UseJVMCIClassLoader");
        }
        args.add("-XX:+UseJVMCICompiler");
        args.add("-XX:CompileOnly=" + Crasher.class.getName() + "::tryCrash");
        args.add(Crasher.class.getName());
        testHelper(args);
    }

    private static final boolean VERBOSE = Boolean.getBoolean(HsErrLogTest.class.getSimpleName() + ".verbose");

    private static void testHelper(List<String> extraVmArgs, String... mainClassAndArgs) throws IOException, InterruptedException {
        List<String> vmArgs = withoutDebuggerArguments(getVMCommandLine());
        vmArgs.removeIf(a -> a.startsWith("-Dgraal."));
        vmArgs.remove("-esa");
        vmArgs.remove("-ea");
        vmArgs.addAll(extraVmArgs);

        Subprocess proc = SubprocessUtil.java(vmArgs, mainClassAndArgs);
        if (VERBOSE) {
            System.out.println(proc);
        }

        Pattern re = Pattern.compile("# +(.*hs_err_pid[\\d]+\\.log)");

        for (String line : proc.output) {
            Matcher m = re.matcher(line);
            if (m.matches()) {
                File path = new File(m.group(1));
                Assert.assertTrue(path.toString(), path.exists());
                checkHsErr(path);
                return;
            }
        }

        Assert.fail("Could not find " + re.pattern());
    }

    private static void checkHsErr(File hsErrPath) {
        try (BufferedReader br = new BufferedReader(new FileReader(hsErrPath))) {
            String line = br.readLine();
            String sig = Crasher.class.getName() + ".tryCrash(JI)I";
            List<String> lines = new ArrayList<>();
            while (line != null) {
                if (line.contains(sig)) {
                    if (!VERBOSE) {
                        hsErrPath.delete();
                    }
                    return;
                }
                lines.add(line);
                line = br.readLine();
            }
            throw new AssertionError("Could not find line containing \"" + sig + "\" in " + hsErrPath +
                            ":" + System.lineSeparator() + String.join(System.lineSeparator(), lines));
        } catch (IOException e) {
            throw new AssertionError(e);
        }
    }
}

class Crasher {
    public static void main(String[] args) {
        int iter = 0;
        long mem = UNSAFE.allocateMemory(1000);
        while (iter < Integer.MAX_VALUE) {
            tryCrash(mem, iter);
            iter++;
        }
    }

    protected static int tryCrash(long mem, int iter) {
        if (GraalDirectives.inCompiledCode()) {
            UNSAFE.putInt(0, iter);
            return 0;
        } else {
            UNSAFE.putInt(mem, iter);
            return UNSAFE.getInt(mem);
        }
    }

    static final Unsafe UNSAFE = initUnsafe();

    private static Unsafe initUnsafe() {
        try {
            // Fast path when we are trusted.
            return Unsafe.getUnsafe();
        } catch (SecurityException se) {
            // Slow path when we are not trusted.
            try {
                Field theUnsafe = Unsafe.class.getDeclaredField("theUnsafe");
                theUnsafe.setAccessible(true);
                return (Unsafe) theUnsafe.get(Unsafe.class);
            } catch (Exception e) {
                throw new RuntimeException("exception while trying to get Unsafe", e);
            }
        }
    }
}
