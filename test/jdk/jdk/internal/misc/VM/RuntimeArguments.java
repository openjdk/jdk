/*
 * Copyright (c) 2016, 2023, Oracle and/or its affiliates. All rights reserved.
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
 * @requires vm.flagless
 * @library /test/lib
 * @modules java.base/jdk.internal.misc
 *          jdk.zipfs
 * @run testng RuntimeArguments
 * @summary Basic test of VM::getRuntimeArguments
 */

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.lang.module.ModuleFinder;
import java.lang.module.ModuleReader;
import java.lang.module.ModuleReference;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;
import jdk.internal.misc.VM;
import jdk.test.lib.process.ProcessTools;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import static org.testng.Assert.*;

public class RuntimeArguments {
    static final List<String> VM_OPTIONS = getInitialOptions();

    /*
     * Read jdk/internal/vm/options resource from the runtime image.
     * If present, the runtime image was created with jlink --add-options and
     * the java launcher launches the application as if
     *   $ java @options <app>
     * The VM options listed in the jdk/internal/vm/options resource file
     * are passed to the VM.
     */
    static List<String> getInitialOptions() {
        ModuleReference mref = ModuleFinder.ofSystem().find("java.base").orElseThrow();
        try (ModuleReader reader = mref.open()) {
            InputStream in = reader.open("jdk/internal/vm/options").orElse(null);
            if (in != null) {
                // support the simplest form for now: whitespace-separated
                return List.of(new String(in.readAllBytes()).split("\s"));
            } else {
                return List.of();
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @DataProvider(name = "options")
    public Object[][] options() {
        return new Object[][] {
            { // CLI options
              List.of("--add-exports",
                      "java.base/jdk.internal.misc=ALL-UNNAMED",
                      "--add-exports",
                      "java.base/jdk.internal.perf=ALL-UNNAMED",
                      "--add-modules",
                      "jdk.zipfs"),
              // expected runtime arguments
              List.of("--add-exports=java.base/jdk.internal.misc=ALL-UNNAMED",
                      "--add-exports=java.base/jdk.internal.perf=ALL-UNNAMED",
                      "--add-modules=jdk.zipfs")
            },
            { // CLI options
              List.of("--add-exports",
                      "java.base/jdk.internal.misc=ALL-UNNAMED",
                      "--add-modules",
                      "jdk.zipfs",
                      "--limit-modules",
                      "java.logging,java.xml,jdk.charsets,jdk.zipfs"),
              // expected runtime arguments
              List.of("--add-exports=java.base/jdk.internal.misc=ALL-UNNAMED",
                      "--add-modules=jdk.zipfs",
                      "--limit-modules=java.logging,java.xml,jdk.charsets,jdk.zipfs"),
            },
        };
    };

    public static void main(String... expected) {
        String[] vmArgs = VM.getRuntimeArguments();
        if (!Arrays.equals(vmArgs, expected)) {
            throw new RuntimeException(Arrays.toString(vmArgs) +
                " != " + Arrays.toString(expected));
        }
    }

    @Test(dataProvider = "options")
    public void test(List<String> args, List<String> expected) throws Exception {
        // launch a test program with classpath set by ProcessTools::createLimitedTestJavaProcessBuilder
        // $ java <args> RuntimeArguments <vm_options> <expected>
        Stream<String> options = Stream.concat(args.stream(), Stream.of("RuntimeArguments"));
        ProcessBuilder pb = ProcessTools.createLimitedTestJavaProcessBuilder(
            // The runtime image may be created with jlink --add-options
            // The initial VM options will be included in the result
            // returned by VM.getRuntimeArguments()
            Stream.concat(options, Stream.concat(VM_OPTIONS.stream(), expected.stream()))
                  .toArray(String[]::new)
        );
        ProcessTools.executeProcess(pb).shouldHaveExitValue(0);
    }
}
