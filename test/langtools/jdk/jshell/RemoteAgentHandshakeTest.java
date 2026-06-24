/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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
 * @test 8384736
 * @summary Verify the correct handshake is used for remote agents.
 * @run junit RemoteAgentHandshakeTest
 */

import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import jdk.jfr.consumer.RecordingStream;
import jdk.jshell.JShell;
import jdk.jshell.execution.JdiExecutionControlProvider;
import jdk.jshell.execution.RemoteExecutionControl;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class RemoteAgentHandshakeTest {

    @Test
    public void testCustomAgentOldHandshake() throws Exception {
        assertThrows(IllegalStateException.class, () -> {
            try (JShell jshell =
                    JShell.builder()
                          .executionEngine(new JdiExecutionControlProvider(),
                                           Map.of(JdiExecutionControlProvider.PARAM_REMOTE_AGENT,
                                                  OldHandShake.class.getName()))
                          .build()) {
                jshell.eval("1");
            }
        });
    }

    @Test
    public void testCustomAgentNewHandshake() throws Exception {
        try (JShell jshell =
                JShell.builder()
                      .executionEngine(new JdiExecutionControlProvider(),
                                       Map.of(JdiExecutionControlProvider.PARAM_REMOTE_AGENT,
                                              NewHandShake.class.getName()))
                      .build()) {
            jshell.eval("1");
        }
    }

    @Test
    public void testDefaultAgentUsesNewHandshake() throws Exception {
        try (var rs = new RecordingStream()) {
            rs.enable("jdk.ProcessStart");
            rs.enable("jdk.CPULoad").withPeriod(Duration.ofMillis(10));

            CompletableFuture<String> command = new CompletableFuture<>();

            rs.onEvent("jdk.ProcessStart", evt -> {
                String commandLine = evt.getValue("command");

                if (commandLine.contains("RemoteExecutionControl")) {
                    command.complete(commandLine);
                }
            });

            CountDownLatch running = new CountDownLatch(1);

            rs.onEvent("jdk.CPULoad", evt -> {
                running.countDown();
            });

            rs.startAsync();

            running.await();

            try (JShell jshell = JShell.create()) {
                jshell.eval("1");
            }

            String commandLine = command.get(10, TimeUnit.SECONDS);
            if (!commandLine.endsWith(" -1")) {
                throw new AssertionError("Does not contain a correct port: '" + commandLine + "'");
            }
        }
    }

    public static class OldHandShake extends RemoteExecutionControl {
        public static void main(String[] args) throws Exception {
            if (Integer.parseInt(args[0]) <= 0) {
                throw new AssertionError("Unexpected port number!");
            }

            RemoteExecutionControl.main(args);
        }
    }

    public static class NewHandShake extends RemoteExecutionControl {
        public static void main(String[] args) throws Exception {
            if (Integer.parseInt(args[0]) != (-1)) {
                throw new AssertionError("Unexpected port number!");
            }

            RemoteExecutionControl.main(args);
        }
    }
}
