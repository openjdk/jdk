/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8319311
 * @summary Tests JdiStarter
 * @modules jdk.jshell/jdk.jshell jdk.jshell/jdk.jshell.spi jdk.jshell/jdk.jshell.execution
 * @run testng JdiStarterTest
 */

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.testng.annotations.Test;
import jdk.jshell.JShell;
import jdk.jshell.SnippetEvent;
import jdk.jshell.execution.JdiDefaultExecutionControl.JdiStarter;
import jdk.jshell.execution.JdiDefaultExecutionControl.JdiStarter.TargetDescription;
import jdk.jshell.execution.JdiExecutionControlProvider;
import jdk.jshell.execution.JdiInitiator;
import static org.testng.Assert.assertEquals;

@Test
public class JdiStarterTest {

    public void jdiStarter() {
        // turn on logging of launch failures
        Logger.getLogger("jdk.jshell.execution").setLevel(Level.ALL);
        JdiStarter starter = (env, parameters, port) -> {
            assertEquals(parameters.get(JdiExecutionControlProvider.PARAM_HOST_NAME), "");
            assertEquals(parameters.get(JdiExecutionControlProvider.PARAM_LAUNCH), "false");
            assertEquals(parameters.get(JdiExecutionControlProvider.PARAM_REMOTE_AGENT), "jdk.jshell.execution.RemoteExecutionControl");
            assertEquals(parameters.get(JdiExecutionControlProvider.PARAM_TIMEOUT), "5000");
            JdiInitiator jdii =
                    new JdiInitiator(port,
                                     env.extraRemoteVMOptions(),
                                     "jdk.jshell.execution.RemoteExecutionControl",
                                     false,
                                     null,
                                     5000,
                                     Collections.emptyMap());
            return new TargetDescription(jdii.vm(), jdii.process());
        };
        JShell jshell =
                JShell.builder()
                      .executionEngine(new JdiExecutionControlProvider(starter), Map.of())
                      .build();
        List<SnippetEvent> evts = jshell.eval("1 + 2");
        assertEquals(1, evts.size());
        assertEquals("3", evts.get(0).value());
    }
}
