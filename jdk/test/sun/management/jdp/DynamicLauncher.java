/*
 * Copyright (c) 2013, 2014, Oracle and/or its affiliates. All rights reserved.
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


import jdk.testlibrary.OutputAnalyzer;
import jdk.testlibrary.ProcessTools;
import jdk.testlibrary.Utils;

import java.util.UUID;


/**
 * This class will try to find an unused port and run a JdpTestCase using it.
 * The unused port is needed for jmxremote.port.
 * The problem with busy ports arises when running many automated tests on the same host.
 * Note that jdp.port is a multicast port and thus it can be binded by different processes at the same time.
 */
public abstract class DynamicLauncher {

    final String jdpName = UUID.randomUUID().toString();
    int jmxPort;

    protected void run() throws Exception {
        OutputAnalyzer out;
        int retries = 1;
        boolean tryAgain;

        do {
            tryAgain = false;
            jmxPort = Utils.getFreePort();
            out = runVM();
            try {
                out.shouldNotContain("Port already in use");
            } catch (RuntimeException e) {
                if (retries < 3) {
                    retries++;
                    tryAgain = true;
                }
            }
        } while (tryAgain);
    }

    protected OutputAnalyzer runVM() throws Exception {
        String[] options = this.options();
        ProcessBuilder pb = ProcessTools.createJavaProcessBuilder(options);
        OutputAnalyzer out = ProcessTools.executeProcess(pb);
        System.out.println(out.getStdout());
        System.err.println(out.getStderr());
        return out;
    }

    protected abstract String[] options();

}
