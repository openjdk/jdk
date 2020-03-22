/*
 * Copyright (c) 2016, 2020, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8163805 8224252
 * @summary Checks that the jshdb debugd utility sucessfully starts
 *          and tries to attach to a running process
 * @requires vm.hasSA
 * @requires os.family != "windows"
 * @modules java.base/jdk.internal.misc
 * @library /test/lib
 *
 * @run main/othervm SADebugDTest
 */

import java.util.concurrent.TimeUnit;

import jdk.test.lib.apps.LingeredApp;
import jdk.test.lib.JDKToolLauncher;
import jdk.test.lib.SA.SATestUtils;
import static jdk.test.lib.process.ProcessTools.startProcess;

import jtreg.SkippedException;

public class SADebugDTest {

    private static final String GOLDEN = "Debugger attached";

    public static void main(String[] args) throws Exception {
        SATestUtils.skipIfCannotAttach(); // throws SkippedException if attach not expected to work.

        if (SATestUtils.needsPrivileges()) {
            // This tests has issues if you try adding privileges on OSX. The debugd process cannot
            // be killed if you do this (because it is a root process and the test is not), so the destroy()
            // call fails to do anything, and then waitFor() will time out. If you try to manually kill it with
            // a "sudo kill" command, that seems to work, but then leaves the LingeredApp it was
            // attached to in a stuck state for some unknown reason, causing the stopApp() call
            // to timeout. For that reason we don't run this test when privileges are needed. Note
            // it does appear to run fine as root, so we still allow it to run on OSX when privileges
            // are not required.
            throw new SkippedException("Cannot run this test on OSX if adding privileges is required.");
        }

        LingeredApp app = null;

        try {
            app = LingeredApp.startApp();
            System.out.println("Started LingeredApp with pid " + app.getPid());

            JDKToolLauncher jhsdbLauncher = JDKToolLauncher.createUsingTestJDK("jhsdb");
            jhsdbLauncher.addToolArg("debugd");
            jhsdbLauncher.addToolArg("--pid");
            jhsdbLauncher.addToolArg(Long.toString(app.getPid()));
            ProcessBuilder pb = SATestUtils.createProcessBuilder(jhsdbLauncher);

            // The startProcess will block untl the 'golden' string appears in either process' stdout or stderr
            // In case of timeout startProcess kills the debugd process
            Process debugd = startProcess("debugd", pb, null, l -> l.contains(GOLDEN), 20, TimeUnit.SECONDS);

            // If we are here, this means we have received the golden line and the test has passed
            // The debugd remains running, we have to kill it
            debugd.destroy();
            debugd.waitFor();
        } finally {
            LingeredApp.stopApp(app);
        }

    }

}
