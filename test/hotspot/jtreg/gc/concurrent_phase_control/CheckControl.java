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

package gc.concurrent_phase_control;

/*
 * Utility class that uses the WhiteBox concurrent GC phase control to
 * step through a provided sequence of phases, and verify that the
 * phases were actually reached as expected.
 *
 * To use:
 *
 * (1) The main test class has a main function which calls this helper
 * class's check() function with appropriate arguments for the
 * collector being tested.
 *
 * (2) The test program must provide access to WhiteBox, as it is used
 * by this support class.
 *
 * (4) The main test class should be invoked as a driver.  This
 * helper class's check() function will run its Executor class in a
 * subprocess, in order to capture its output for analysis.
 */

import sun.hotspot.WhiteBox;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.process.ProcessTools;

public final class CheckControl {
    // gcName: The name of the GC, logged as "Using <name>" near the
    // beginning of the log output.
    //
    // gcOptions: Command line options for invoking the desired
    // collector and logging options to produce output that can be
    // matched against the regex patterns in the gcPhaseInfo pairs.
    //
    // gcPhaseInfo: An array of pairs of strings.  Each pair is a
    // phase name and a regex pattern for recognizing the associated
    // log message.  The regex pattern can be null if no log message
    // is associated with the named phase.  The test will iterate
    // through the array, requesting each phase in turn.
    public static void check(String gcName,
                             String[] gcOptions,
                             String[][] gcPhaseInfo) throws Exception {
        String[] stepPhases = new String[gcPhaseInfo.length];
        for (int i = 0; i < gcPhaseInfo.length; ++i) {
            stepPhases[i] = gcPhaseInfo[i][0];
        }
        String messages = executeTest(gcName, gcOptions, stepPhases);
        checkPhaseControl(messages, gcPhaseInfo);
    }

    private static void fail(String message) throws Exception {
        throw new RuntimeException(message);
    }

    private static final String requestPrefix = "Requesting concurrent phase: ";
    private static final String reachedPrefix = "Reached concurrent phase: ";

    private static String executeTest(String gcName,
                                      String[] gcOptions,
                                      String[] gcStepPhases) throws Exception {
        System.out.println("\n---------- Testing ---------");

        final String[] wb_arguments = {
            "-Xbootclasspath/a:.",
            "-XX:+UnlockDiagnosticVMOptions",
            "-XX:+WhiteBoxAPI"
        };

        List<String> arglist = new ArrayList<String>();
        Collections.addAll(arglist, wb_arguments);
        Collections.addAll(arglist, gcOptions);
        arglist.add(Executor.class.getName());
        Collections.addAll(arglist, gcStepPhases);
        String[] arguments = arglist.toArray(new String[arglist.size()]);

        ProcessBuilder pb = ProcessTools.createJavaProcessBuilder(arguments);
        OutputAnalyzer output = new OutputAnalyzer(pb.start());

        String messages = output.getStdout();
        System.out.println(messages);

        output.shouldHaveExitValue(0);
        output.shouldContain("Using " + gcName);

        return messages;
    }

    private static void checkPhaseControl(String messages,
                                          String[][] gcPhaseInfo)
        throws Exception
    {
        // Iterate through the phase sequence for the test, verifying
        // output contains appropriate sequences of request message,
        // log message for phase, and request reached message.  Note
        // that a log message for a phase may occur later than the
        // associated request reached message, or even the following
        // request message.

        Pattern nextReqP = Pattern.compile(requestPrefix);
        Matcher nextReqM = nextReqP.matcher(messages);

        Pattern nextReachP = Pattern.compile(reachedPrefix);
        Matcher nextReachM = nextReachP.matcher(messages);

        String pendingPhaseMessage = null;
        int pendingPhaseMessagePosition = -1;

        int position = 0;
        for (String[] phase: gcPhaseInfo) {
            String phaseName = phase[0];
            String phaseMsg = phase[1];

            System.out.println("Checking phase " + phaseName);

            // Update the "next" matchers to refer to the next
            // corresponding pair of request and reached messages.
            if (!nextReqM.find()) {
                fail("Didn't find next phase request");
            } else if ((position != 0) && (nextReqM.start() < nextReachM.end())) {
                fail("Next request before previous reached");
            } else if (!nextReachM.find()) {
                fail("Didn't find next phase reached");
            } else if (nextReachM.start() <= nextReqM.end()) {
                fail("Next request/reached misordered");
            }

            // Find the expected request message, and ensure it is the next.
            Pattern reqP = Pattern.compile(requestPrefix + phaseName);
            Matcher reqM = reqP.matcher(messages);
            if (!reqM.find(position)) {
                fail("Didn't find request for " + phaseName);
            } else if (reqM.start() != nextReqM.start()) {
                fail("Request mis-positioned for " + phaseName);
            }

            // Find the expected reached message, and ensure it is the next.
            Pattern reachP = Pattern.compile(reachedPrefix + phaseName);
            Matcher reachM = reachP.matcher(messages);
            if (!reachM.find(position)) {
                fail("Didn't find reached for " + phaseName);
            } else if (reachM.start() != nextReachM.start()) {
                fail("Reached mis-positioned for " + phaseName);
            }

            // If there is a pending log message (see below), ensure
            // it was before the current reached message.
            if (pendingPhaseMessage != null) {
                if (pendingPhaseMessagePosition >= reachM.start()) {
                    fail("Log message after next reached message: " +
                         pendingPhaseMessage);
                }
            }

            // If the phase has an associated logging message, verify
            // such a logging message is present following the
            // request, and otherwise positioned appropriately.  The
            // complication here is that the logging message
            // associated with a request might follow the reached
            // message, and even the next request message, if there is
            // a later request.  But it must preceed the next
            // logging message and the next reached message.
            boolean clearPendingPhaseMessage = true;
            if (phaseMsg != null) {
                Pattern logP = Pattern.compile("GC\\(\\d+\\)\\s+" + phaseMsg);
                Matcher logM = logP.matcher(messages);
                if (!logM.find(reqM.end())) {
                    fail("Didn't find message " + phaseMsg);
                }

                if (pendingPhaseMessage != null) {
                    if (pendingPhaseMessagePosition >= logM.start()) {
                        fail("Log messages out of order: " +
                             pendingPhaseMessage + " should preceed " +
                             phaseMsg);
                    }
                }

                if (reachM.end() <= logM.start()) {
                    clearPendingPhaseMessage = false;
                    pendingPhaseMessage = phaseMsg;
                    pendingPhaseMessagePosition = logM.end();
                }
            }
            if (clearPendingPhaseMessage) {
                pendingPhaseMessage = null;
                pendingPhaseMessagePosition = -1;
            }

            // Update position for start of next phase search.
            position = reachM.end();
        }
        // It's okay for there to be a leftover pending phase message.
        // We know it was found before the end of the log.
    }

    private static final class Executor {
        private static final WhiteBox WB = WhiteBox.getWhiteBox();

        private static void step(String phase) {
            System.out.println(requestPrefix + phase);
            WB.requestConcurrentGCPhase(phase);
            System.out.println(reachedPrefix + phase);
        }

        public static void main(String[] phases) throws Exception {
            // Iterate through set sequence of phases, reporting each.
            for (String phase: phases) {
                step(phase);
            }
            // Wait a little to allow a delayed logging message for
            // the final request/reached to be printed before exiting
            // the program.
            Thread.sleep(250);
        }
    }
}
