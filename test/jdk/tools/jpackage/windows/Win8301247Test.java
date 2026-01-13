/*
 * Copyright (c) 2023, 2026, Oracle and/or its affiliates. All rights reserved.
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

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import jdk.jpackage.test.Annotations.Test;
import jdk.jpackage.test.HelloApp;
import jdk.jpackage.test.JPackageCommand;
import jdk.jpackage.test.TKit;

/**
 * Test that terminating of the parent app launcher process automatically
 * terminates child app launcher process.
 */

/*
 * @test
 * @summary Test case for JDK-8301247
 * @library /test/jdk/tools/jpackage/helpers
 * @build jdk.jpackage.test.*
 * @build Win8301247Test
 * @requires (os.family == "windows")
 * @run main/othervm/timeout=360 -Xmx512m  jdk.jpackage.test.Main
 *  --jpt-run=Win8301247Test
 */
public class Win8301247Test {

    @Test
    public void test() throws InterruptedException, ExecutionException {
        var cmd = JPackageCommand.helloAppImage().ignoreFakeRuntime();

        // Launch the app in a way it doesn't exit to let us trap app laucnher
        // processes in the process list
        cmd.addArguments("--java-options", "-Djpackage.test.noexit=true");
        cmd.executeAndAssertImageCreated();

        var f = new CompletableFuture<Process>();

        // Launch the app in a separate thread
        new Thread(() -> {
            HelloApp.assertMainLauncher(cmd).get().processListener(f::complete).execute();
        }).start();

        var mainLauncherProcess = f.get();

        Optional<ProcessHandle> childProcess = Optional.empty();

        try {
            // Wait a bit to let the app start
            Thread.sleep(Duration.ofSeconds(10));

            try (var children = mainLauncherProcess.children()) {
                childProcess = children.filter(p -> {
                    return mainLauncherProcess.info().command().equals(p.info().command());
                }).findFirst();
            }

            TKit.assertTrue(childProcess.isPresent(),
                    String.format("Check the main launcher process with PID=%d restarted", mainLauncherProcess.pid()));
        } finally {
            // Kill the main app launcher process
            TKit.trace("About to kill the main launcher process...");
            mainLauncherProcess.destroyForcibly();

            // Wait a bit and check if child app launcher process is still running (it must NOT)
            Thread.sleep(Duration.ofSeconds(5));

            childProcess.ifPresent(p -> {
                TKit.assertTrue(!p.isAlive(), String.format(
                        "Check restarted main launcher process with PID=%d is not alive", p.pid()));
            });
        }
    }
}
