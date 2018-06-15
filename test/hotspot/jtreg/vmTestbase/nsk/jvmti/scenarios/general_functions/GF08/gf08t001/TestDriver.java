/*
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
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
 *
 * @summary converted from VM Testbase nsk/jvmti/scenarios/general_functions/GF08/gf08t001.
 * VM Testbase keywords: [quick, jpda, jvmti, onload_only_logic, noras]
 * VM Testbase readme:
 * DESCRIPTION
 *    This test implements GF08 scenario of test plan for General
 *    Functions:
 *        Do the following:
 *        Run simple java apllication with VM option '-verbose:gc'.
 *        Run the same application with JVMTI agent. The agent should
 *        set JVMTI_VERBOSE_GC with SetVerboseFlag. Check that outputs
 *        in stderr in both runs are equal.
 *    The test agent has a special input parameter 'setVerboseMode'.
 *    When VM runs the test class 'gf08t001' with
 *      '-agentlib:gf08t001=setVerboseMode=yes'
 *    option, then the agent calls SetVerboseFlag with
 *    JVMTI_VERBOSE_GC flag in Onload phase.
 *    The test's script wrapper runs the 'gf08t001' class twice.
 *    First time, with "setVerboseMode=yes" agent mode. Second
 *    time, with "setVerboseMode=no" agent mode and with
 *    "-verbose:gc" VM option. In both cases the output is
 *    searched for 'Full GC' string, unless ExplicitGCInvokesConcurrent
 *    is enabled and G1 or CMS GCs are enbled. If ExplicitGCInvokesConcurrent and
 *    either G1 or CMS GCs are enbled the test searches for 'GC' string in output.
 *    The test fails if this string is not found in the output.
 * COMMENTS
 *
 * @library /vmTestbase
 *          /test/lib
 * @run driver jdk.test.lib.FileInstaller . .
 * @build sun.hotspot.WhiteBox
 * @run driver ClassFileInstaller sun.hotspot.WhiteBox
 *                                sun.hotspot.WhiteBox$WhiteBoxPermission
 * @run main/othervm/native
 *      -Xbootclasspath/a:.
 *      -XX:+UnlockDiagnosticVMOptions
 *      -XX:+WhiteBoxAPI
 *      TestDriver
 */

import sun.hotspot.code.Compiler;

public class TestDriver {
    public static void main(String[] args) throws Exception {
        sun.hotspot.WhiteBox wb = sun.hotspot.WhiteBox.getWhiteBox();
        Boolean isExplicitGCInvokesConcurrentOn = wb.getBooleanVMFlag("ExplicitGCInvokesConcurrent");
        Boolean isUseG1GCon = wb.getBooleanVMFlag("UseG1GC");
        Boolean isUseConcMarkSweepGCon = wb.getBooleanVMFlag("UseConcMarkSweepGC");
        Boolean isUseZGCon = wb.getBooleanVMFlag("UseZGC");
        Boolean isUseEpsilonGCon = wb.getBooleanVMFlag("UseEpsilonGC");

        if (Compiler.isGraalEnabled() &&
            (isUseConcMarkSweepGCon || isUseZGCon || isUseEpsilonGCon)) {
            return; // Graal does not support these GCs
        }

        String keyPhrase;
        if ((isExplicitGCInvokesConcurrentOn && (isUseG1GCon || isUseConcMarkSweepGCon)) || isUseZGCon) {
            keyPhrase = "GC";
        } else {
            keyPhrase = "Pause Full";
        }

        nsk.jvmti.scenarios.general_functions.GF08.gf08t.main(new String[] {
                "gf08t001",
                nsk.jvmti.scenarios.general_functions.GF08.gf08t001.class.getName(),
                keyPhrase,
                "gc"});
    }
}

