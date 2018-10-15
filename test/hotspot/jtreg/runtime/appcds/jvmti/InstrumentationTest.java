/*
 * Copyright (c) 2014, 2017, Oracle and/or its affiliates. All rights reserved.
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
 *
 */

/*
 * @test
 * @summary Exercise the java.lang.instrument.Instrumentation APIs on classes archived
 *          using CDS/AppCDSv1/AppCDSv2.
 * @library /test/lib /test/hotspot/jtreg/runtime/appcds /test/hotspot/jtreg/runtime/appcds/test-classes
 * @requires vm.cds
 * @requires vm.flavor != "minimal"
 * @modules java.base/jdk.internal.misc
 *          jdk.jartool/sun.tools.jar
 *          java.management
 * @build sun.hotspot.WhiteBox
 *        InstrumentationApp
 *        InstrumentationClassFileTransformer
 *        InstrumentationRegisterClassFileTransformer
 * @run main/othervm InstrumentationTest
 */

// Note: TestCommon is from /test/hotspot/jtreg/runtime/appcds/TestCommon.java
// Note: Util       is from /test/hotspot/jtreg/runtime/appcds/test-classes/TestCommon.java

import com.sun.tools.attach.VirtualMachine;
import com.sun.tools.attach.VirtualMachineDescriptor;
import java.io.File;
import java.io.FileOutputStream;
import java.util.List;
import jdk.test.lib.Asserts;
import jdk.test.lib.cds.CDSOptions;
import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.process.ProcessTools;

public class InstrumentationTest {
    public static String bootClasses[] = {
        "InstrumentationApp$Intf",
        "InstrumentationApp$Bar",
        "sun.hotspot.WhiteBox",
    };
    public static String appClasses[] = {
        "InstrumentationApp",
        "InstrumentationApp$Foo",
        "InstrumentationApp$MyLoader",
    };
    public static String custClasses[] = {
        "InstrumentationApp$Coo",
    };
    public static String sharedClasses[] = TestCommon.concat(bootClasses, appClasses);

    public static String agentClasses[] = {
        "InstrumentationClassFileTransformer",
        "InstrumentationRegisterClassFileTransformer",
        "Util",
    };

    public static void main(String[] args) throws Throwable {
        runTest(false);
        runTest(true);
    }

    public static void runTest(boolean attachAgent) throws Throwable {
        String bootJar =
            ClassFileInstaller.writeJar("InstrumentationBoot.jar", bootClasses);
        String appJar =
            ClassFileInstaller.writeJar("InstrumentationApp.jar",
                                        TestCommon.concat(appClasses,
                                                          "InstrumentationApp$ArchivedIfAppCDSv2Enabled"));
        String custJar =
            ClassFileInstaller.writeJar("InstrumentationCust.jar", custClasses);
        String agentJar =
            ClassFileInstaller.writeJar("InstrumentationAgent.jar",
                                        ClassFileInstaller.Manifest.fromSourceFile("InstrumentationAgent.mf"),
                                        agentClasses);

        String bootCP = "-Xbootclasspath/a:" + bootJar;

        System.out.println("");
        System.out.println("============================================================");
        System.out.println("CDS: NO, attachAgent: " + (attachAgent ? "YES" : "NO"));
        System.out.println("============================================================");
        System.out.println("");

        String agentCmdArg, flagFile;
        if (attachAgent) {
            // we will attach the agent, so don't specify -javaagent in the command line. We'll use
            // something harmless like -showversion to make it easier to construct the command line
            agentCmdArg = "-showversion";
        } else {
            agentCmdArg = "-javaagent:" + agentJar;
        }

        // First, run the test class directly, w/o sharing, as a baseline reference
        flagFile = getFlagFile(attachAgent);
        AgentAttachThread t = doAttach(attachAgent, flagFile, agentJar);
        ProcessBuilder pb = ProcessTools.createJavaProcessBuilder(
                bootCP,
                "-cp", appJar,
                "-XX:+UnlockDiagnosticVMOptions",
                "-XX:+WhiteBoxAPI",
                "-Xshare:off",
                agentCmdArg,
                "InstrumentationApp", flagFile, bootJar, appJar, custJar);
        TestCommon.executeAndLog(pb, "no-sharing").shouldHaveExitValue(0);
        checkAttach(t);

        // Dump the AppCDS archive. On some platforms AppCDSv2 may not be enabled, so we
        // first try the v2 classlist, and if that fails, revert to the v1 classlist.
        // Note that the InstrumentationApp$ArchivedIfAppCDSv2Enabled class is archived
        // only if V2 is enabled. This is tested by InstrumentationApp.isAppCDSV2Enabled().
        String[] v2Classes = {
            "InstrumentationApp$ArchivedIfAppCDSv2Enabled",
            "java/lang/Object id: 0",
            "InstrumentationApp$Intf id: 1",
            "InstrumentationApp$Coo  id: 2 super: 0 interfaces: 1 source: " + custJar,
        };
        String[] sharedClassesWithV2 = TestCommon.concat(v2Classes, sharedClasses);
        OutputAnalyzer out = TestCommon.dump(appJar, sharedClassesWithV2, bootCP);
        if (out.getExitValue() != 0) {
            System.out.println("Redumping with AppCDSv2 disabled");
                TestCommon.testDump(appJar, sharedClasses, bootCP);
        }

        // Run with AppCDS.
        System.out.println("");
        System.out.println("============================================================");
        System.out.println("CDS: YES, attachAgent: " + (attachAgent ? "YES" : "NO"));
        System.out.println("============================================================");
        System.out.println("");

        flagFile = getFlagFile(attachAgent);
        t = doAttach(attachAgent, flagFile, agentJar);
        out = TestCommon.execAuto("-cp", appJar,
                bootCP,
                "-XX:+UnlockDiagnosticVMOptions",
                "-XX:+WhiteBoxAPI",
                agentCmdArg,
               "InstrumentationApp", flagFile, bootJar, appJar, custJar);

        CDSOptions opts = (new CDSOptions()).setXShareMode("auto");
        TestCommon.checkExec(out, opts);
        checkAttach(t);
    }

    static int flagFileSerial = 1;
    static private String getFlagFile(boolean attachAgent) {
        if (attachAgent) {
            // Do not reuse the same file name as Windows may fail to
            // delete the file.
            return "attach.flag." + ProcessHandle.current().pid() +
                    "." + (flagFileSerial++) + "." + System.currentTimeMillis();
        } else {
            return "noattach";
        }
    }

    static AgentAttachThread doAttach(boolean attachAgent, String flagFile, String agentJar) throws Throwable {
        if (!attachAgent) {
            return null;
        }

        // We use the flagFile to prevent the child process to make progress, until we have
        // attached to it.
        File f = new File(flagFile);
        try (FileOutputStream o = new FileOutputStream(f)) {
            o.write(1);
        }
        if (!f.exists()) {
            throw new RuntimeException("Failed to create " + f);
        }

        // At this point, the child process is not yet launched. Note that
        // TestCommon.exec() and OutputAnalyzer.OutputAnalyzer() both block
        // until the child process has finished.
        //
        // So, we will launch a AgentAttachThread which will poll the system
        // until the child process is launched, and then do the attachment.
        // The child process is uniquely identified by having flagFile in its
        // command-line -- see AgentAttachThread.getPid().
        AgentAttachThread t = new AgentAttachThread(flagFile, agentJar);
        t.start();
        return t;
    }

    static void checkAttach(AgentAttachThread thread) throws Throwable {
        if (thread != null) {
            thread.check();
        }
    }

    static class AgentAttachThread extends Thread {
        String flagFile;
        String agentJar;
        volatile boolean succeeded;

        AgentAttachThread(String flagFile, String agentJar) {
            this.flagFile = flagFile;
            this.agentJar = agentJar;
            this.succeeded = false;
        }

        static String getPid(String flagFile) throws Throwable {
            while (true) {
                // Keep polling until the child process has been launched. If for some
                // reason the child process fails to launch, this test will be terminated
                // by JTREG's time-out mechanism.
                Thread.sleep(100);
                List<VirtualMachineDescriptor> vmds = VirtualMachine.list();
                for (VirtualMachineDescriptor vmd : vmds) {
                    if (vmd.displayName().contains(flagFile) && vmd.displayName().contains("InstrumentationApp")) {
                        // We use flagFile (which has the PID of this process) as a unique identifier
                        // to ident the child process, which we want to attach to.
                        System.out.println("Process found: " + vmd.id() + " " + vmd.displayName());
                        return vmd.id();
                    }
                }
            }
        }

        public void run() {
            try {
                String pid = getPid(flagFile);
                VirtualMachine vm = VirtualMachine.attach(pid);
                System.out.println(agentJar);
                vm.loadAgent(agentJar);
            } catch (Throwable t) {
                t.printStackTrace();
                throw new RuntimeException(t);
            }

            // Delete the flagFile to indicate to the child process that we
            // have attached to it, so it should proceed.
            File f = new File(flagFile);
            for (int i=0; i<5; i++) {
                // The detele may fail on Windows if the child JVM is checking
                // f.exists() at exactly the same time?? Let's do a little
                // dance.
                f.delete();
                try {
                    Thread.sleep(10);
                } catch (Throwable t) {;}
            }
            if (f.exists()) {
                throw new RuntimeException("Failed to delete " + f);
            }
            System.out.println("Attach succeeded (parent)");
            succeeded = true;
        }

        void check() throws Throwable {
            super.join();
            if (!succeeded) {
                throw new RuntimeException("Attaching agent to child VM failed");
            }
        }
    }
}

