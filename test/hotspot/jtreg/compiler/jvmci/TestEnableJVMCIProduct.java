/*
 * Copyright (c) 2019, 2023, Oracle and/or its affiliates. All rights reserved.
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
 * @test
 * @bug 8235539 8245717
 * @summary Tests effect of -XX:+EnableJVMCIProduct on EnableJVMCI and UseJVMCICompiler
 * @requires vm.flagless
 * @requires vm.jvmci
 * @library /test/lib
 * @run driver TestEnableJVMCIProduct
 */

import jdk.test.lib.process.ProcessTools;
import jdk.test.lib.process.OutputAnalyzer;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;

public class TestEnableJVMCIProduct {

    static class Expectation {
        final String name;
        final String value;
        final String origin;
        final String pattern;
        Expectation(final String name, String value, String origin) {
            this.name = name;
            this.value = value;
            this.origin = origin;
            this.pattern = "bool +" + name + " += " + value + " +\\{JVMCI product\\} \\{" + origin + "\\}";
        }
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 0) {
            // Called as subprocess. Print system properties named by
            // `args[1..]` to the file `args[0]` and then exit.
            Files.writeString(Path.of(args[0]),
                List.of(args).subList(1, args.length).stream()
                      .map(a -> "%s=%s".formatted(a, System.getProperty(a)))
                      .collect(Collectors.joining(",")));
            return;
        }
        // Test EnableJVMCIProduct without any other explicit JVMCI option
        test("-XX:-PrintWarnings",
            new Expectation("EnableJVMCI", "true", "default"),
            new Expectation("UseJVMCICompiler", "true", "default"));
        test("-XX:+UseJVMCICompiler",
            new Expectation("EnableJVMCI", "true", "default"),
            new Expectation("UseJVMCICompiler", "true", "command line"));
        test("-XX:-UseJVMCICompiler",
            new Expectation("EnableJVMCI", "true", "default"),
            new Expectation("UseJVMCICompiler", "false", "command line"));
        test("-XX:+EnableJVMCI",
            new Expectation("EnableJVMCI", "true", "command line"),
            new Expectation("UseJVMCICompiler", "true", "default"));
        test("-XX:-EnableJVMCI",
            new Expectation("EnableJVMCI", "false", "command line"),
            new Expectation("UseJVMCICompiler", "false", "default"));
        test("-XX:+EnableJVMCIProduct",
            new Expectation("EnableJVMCIProduct", "true", "(?:command line|jimage)"),
            new Expectation("EnableJVMCI", "true", "default"),
            new Expectation("UseJVMCICompiler", "true", "default"));
    }

    static int id;

    static void test(String explicitFlag, Expectation... expectations) throws Exception {
        String[] flags = {"-XX:+EnableJVMCIProduct", "-XX:+UseGraalJIT"};
        String cwd = System.getProperty("user.dir");
        for (String flag : flags) {
            Path propsPath = Path.of("props." + id++);
            ProcessBuilder pb = ProcessTools.createLimitedTestJavaProcessBuilder(
                "-XX:+UnlockExperimentalVMOptions", flag, "-XX:-UnlockExperimentalVMOptions",
                explicitFlag,
                "-XX:+PrintFlagsFinal",
                "--class-path=" + System.getProperty("java.class.path"),
                "TestEnableJVMCIProduct", propsPath.toString(), "jvmci.Compiler");
            OutputAnalyzer output = new OutputAnalyzer(pb.start());
            for (Expectation expectation : expectations) {
                output.stdoutShouldMatch(expectation.pattern);
            }
            if (output.getExitValue() != 0) {
                // This should only happen when JVMCI compilation is requested and the VM has no
                // JVMCI compiler (e.g. Graal is not included in the build)
                if (flag.equals("-XX:+UseGraalJIT")) {
                    output.shouldContain("JVMCI compiler 'graal' specified by jvmci.Compiler not found");
                } else {
                    output.stdoutShouldMatch("No JVMCI compiler found");
                }
            } else if (flag.equals("-XX:+UseGraalJIT")) {
                String props = Files.readString(propsPath);
                String expect = "jvmci.Compiler=graal";
                if (!props.contains(expect)) {
                    throw new RuntimeException("\"%s\" does not contain \"%s\"".formatted(props, expect));
                }
            }
        }
    }
}
