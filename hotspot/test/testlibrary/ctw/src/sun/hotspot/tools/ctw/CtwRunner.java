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

package sun.hotspot.tools.ctw;

import jdk.test.lib.Utils;
import jdk.test.lib.process.ProcessTools;
import jdk.test.lib.util.Pair;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Runs CompileTheWorld for exact one target. If an error occurs during
 * compilation of class N, this driver class saves error information and
 * restarts CTW from class N + 1. All saved errors are reported at the end.
 * <pre>
 * Usage: <target to compile>
 * </pre>
 */
public class CtwRunner {
    private static final Predicate<String> IS_CLASS_LINE = Pattern.compile(
            "^\\[\\d+\\]\\s*\\S+\\s*$").asPredicate();

    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            throw new Error("Usage: <artifact to compile>");
        }
        new CtwRunner(args[0]).run();
    }

    private final List<Throwable> errors;
    private final Path targetPath;
    private final String targetName;

    private CtwRunner(String target) {
        if (target.equals("modules")) {
            targetPath = Paths
                    .get(Utils.TEST_JDK)
                    .resolve("lib")
                    .resolve(target);
        } else {
            targetPath = Paths.get(target).toAbsolutePath();
        }
        targetName = targetPath.getFileName().toString();
        errors = new ArrayList<>();
    }


    private void run() {
        startCtwforAllClasses();
        if (!errors.isEmpty()) {
            StringBuilder sb = new StringBuilder();
            sb.append("There were ")
              .append(errors.size())
              .append(" errors:[");
            System.err.println(sb.toString());
            for (Throwable e : errors) {
                sb.append("{")
                  .append(e.getMessage())
                  .append("}");
                e.printStackTrace(System.err);
                System.err.println();
            }
            sb.append("]");
            throw new AssertionError(sb.toString());
        }
    }


    private void startCtwforAllClasses() {
        long classStart = 0;
        boolean done = false;
        while (!done) {
            String[] cmd = cmd(classStart);
            try {
                ProcessBuilder pb = ProcessTools.createJavaProcessBuilder(
                        /* addTestVmAndJavaOptions = */ true,
                        cmd);
                String commandLine = pb.command()
                        .stream()
                        .collect(Collectors.joining(" "));
                String phase = phaseName(classStart);
                Path out = Paths.get(".", phase + ".out");
                System.out.printf("%s %dms START : [%s]%n" +
                        "cout/cerr are redirected to %s%n",
                        phase, TimeUnit.NANOSECONDS.toMillis(System.nanoTime()),
                        commandLine, out);
                int exitCode = pb.redirectErrorStream(true)
                        .redirectOutput(out.toFile())
                        .start()
                        .waitFor();
                System.out.printf("%s %dms END : exit code = %d%n",
                        phase, TimeUnit.NANOSECONDS.toMillis(System.nanoTime()),
                        exitCode);
                Pair<String, Long> lastClass = getLastClass(out);
                if (exitCode == 0) {
                    done = true;
                } else {
                    if (lastClass == null) {
                        errors.add(new Error(phase + ": failed during preload"
                                + " with classStart = " + classStart));
                        // skip one class
                        ++classStart;
                    } else {
                        errors.add(new Error(phase + ": failed during"
                                + " compilation of class #" + lastClass.second
                                + " : " + lastClass.first));
                        // continue with the next class
                        classStart = lastClass.second + 1;
                    }
                }
            } catch (Exception e) {
                throw new Error("failed to run from " + classStart, e);
            }
        }
    }

    private Pair<String, Long> getLastClass(Path errFile) {
        try {
            String line = Files.newBufferedReader(errFile)
                    .lines()
                    .filter(IS_CLASS_LINE)
                    .reduce((a, b) -> b).orElse(null);
            if (line != null) {
                int open = line.indexOf('[') + 1;
                int close = line.indexOf(']');
                long index = Long.parseLong(line.substring(open, close));
                String name = line.substring(close + 1).trim().replace('.', '/');
                return new Pair<>(name, index);
            }
        } catch (IOException ioe) {
            throw new Error("can not read " + errFile + " : "
                    + ioe.getMessage(), ioe);
        }
        return null;
    }

    private String[] cmd(long classStart) {
        String phase = phaseName(classStart);
        return new String[]{
                "-Xbatch",
                "-XX:-UseCounterDecay",
                "-XX:-ShowMessageBoxOnError",
                "-XX:+UnlockDiagnosticVMOptions",
                // define phase start
                "-DCompileTheWorldStartAt=" + classStart,
                // CTW library uses WhiteBox API
                "-XX:+WhiteBoxAPI", "-Xbootclasspath/a:.",
                // export jdk.internal packages used by CTW library
                "--add-exports", "java.base/jdk.internal.jimage=ALL-UNNAMED",
                "--add-exports", "java.base/jdk.internal.misc=ALL-UNNAMED",
                "--add-exports", "java.base/jdk.internal.reflect=ALL-UNNAMED",
                // enable diagnostic logging
                "-XX:+LogCompilation",
                // use phase specific log, hs_err and ciReplay files
                String.format("-XX:LogFile=hotspot_%s_%%p.log", phase),
                String.format("-XX:ErrorFile=hs_err_%s_%%p.log", phase),
                String.format("-XX:ReplayDataFile=replay_%s_%%p.log", phase),
                // MethodHandle MUST NOT be compiled
                "-XX:CompileCommand=exclude,java/lang/invoke/MethodHandle.*",
                // CTW entry point
                CompileTheWorld.class.getName(),
                targetPath.toString(),
        };
    }

    private String phaseName(long classStart) {
        return String.format("%s_%d", targetName, classStart);
    }

}
