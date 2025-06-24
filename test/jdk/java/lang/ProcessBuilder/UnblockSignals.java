/*
 * Copyright (c) 2022, 2023, Oracle and/or its affiliates. All rights reserved.
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

import java.io.IOException;

/*
 * @test id=posix_spawn
 * @summary Verify Signal mask is cleared by ProcessBuilder start when using posix_spawn mode
 * @bug 8234262
 * @requires (os.family == "linux" | os.family == "mac")
 * @comment Don't allow -Xcomp, it disturbs the relative timing of the sleep and kill commands
 * @requires (vm.compMode != "Xcomp")
 * @run main/othervm -Djdk.lang.Process.launchMechanism=POSIX_SPAWN UnblockSignals
 * @run main/othervm -Djdk.lang.Process.launchMechanism=POSIX_SPAWN -Xrs UnblockSignals
 */

/*
 * @test id=fork
 * @summary Verify Signal mask is cleared by ProcessBuilder start when using fork mode
 * @bug 8357683
 * @requires (os.family == "linux" | os.family == "mac")
 * @comment Don't allow -Xcomp, it disturbs the relative timing of the sleep and kill commands
 * @requires (vm.compMode != "Xcomp")
 * @run main/othervm -Djdk.lang.Process.launchMechanism=FORK UnblockSignals
 * @run main/othervm -Djdk.lang.Process.launchMechanism=FORK -Xrs UnblockSignals
 */

public class UnblockSignals {
    public static void main(String[] args)  throws IOException, InterruptedException {
        // Check that SIGQUIT is not masked, in previous releases it was masked
        final ProcessBuilder pb = new ProcessBuilder("sleep", "30").inheritIO();
        Process p = pb.start();
        System.out.printf("Child %d, %s%n", p.pid(), pb.command());
        ProcessBuilder killpb = new ProcessBuilder("kill", "-s", "QUIT", Long.toString(p.pid()));
        Process killp = killpb.start();
        System.out.printf("Child %d, %s%n", killp.pid(), killpb.command());
        int killst = killp.waitFor();
        if (killst != 0) {
            throw new RuntimeException("Kill process failed, exit status: " + killst);
        }
        int sleepStatus = p.waitFor();
        if (sleepStatus == 0) {
            throw new RuntimeException("Child not killed");
        }
    }
}
