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
 *
 */

/*
 * @test
 * @summary Attach preserves startup return codes without determining environment lifetime
 * @requires vm.flagless & vm.jvmti & !jdk.static
 * @library /test/lib
 * @modules jdk.attach
 * @run main/othervm/native -Djdk.attach.allowAttachSelf=true -XX:+EnableDynamicAgentLoading TestAttachEnvironmentLifetime
 */

public class TestAttachEnvironmentLifetime {
    // Agent_OnAttach options format: <operation>, <slot>, <value>.
    //
    // Operation selects the action. The -success/-fail suffix describes the expected startup outcome:
    //
    // check-no-environments-success / check-no-environments-fail:
    //     Check that neither slot holds a JVMTI environment.
    // create-environment-success / create-environment-fail:
    //     Create a JVMTI environment in an empty slot and reset its visit count.
    // visit-environment-success:
    //     Check the next visit number and live phase, then advance the count.
    // dispose-environment-success / dispose-environment-fail:
    //     Dispose the JVMTI environment in the selected slot and clear the slot.
    // create-dispose-environment-success / create-dispose-environment-fail:
    //     Create and dispose a JVMTI environment within this attach call.
    //
    // Slot selects retained JVMTI environment 0 or 1.
    // The slot is unused by check-no-environments and create-dispose-environment,
    // but both operations still require a valid slot number.
    //
    // Value supplies the exact return code, except for visit-environment-success,
    // where it is the expected next visit number.
    // Unexpected operation failures return JNI_ERR.
    private static final String LIBRARY = AgentTestSupport.library("OnAttachAgent");

    public static void main(String[] args) throws Exception {
        testReturnCodesWithoutEnvironment();
        testRetainedEnvironmentLifetimes();
        testDisposalDuringFailedStartup();
        testDisposalDuringSuccessfulStartup();
    }

    // Attach preserves exact startup return codes even when no JVMTI environment is created.
    private static void testReturnCodesWithoutEnvironment() throws Exception {
        for (int status : new int[] {0, 23, 71}) {
            String operation = status == 0 ? "check-no-environments-success" : "check-no-environments-fail";
            attach(operation + ", 0, " + status);
        }
    }

    // A failed startup leaves its JVMTI environment usable by later attach calls.
    private static void testRetainedEnvironmentLifetimes() throws Exception {
        attach("create-environment-fail, 0, 23");
        attach("visit-environment-success, 0, 1");
        attach("visit-environment-success, 0, 2");

        attach("create-environment-success, 1, 0");
        attach("visit-environment-success, 1, 1");
        attach("visit-environment-success, 0, 3");

        // Returning an error does not undo a completed disposal either.
        attach("dispose-environment-fail, 1, 71");
        attach("visit-environment-success, 0, 4");
        attach("dispose-environment-success, 0, 0");
        attach("check-no-environments-success, 0, 0");

        // The same library can establish a new environment after both were disposed.
        attach("create-environment-success, 0, 0");
        attach("visit-environment-success, 0, 1");
        attach("dispose-environment-success, 0, 0");
        attach("check-no-environments-success, 0, 0");
    }

    // Failed startup preserves completed disposal and leaves neither environment slot occupied.
    private static void testDisposalDuringFailedStartup() throws Exception {
        verifyCreateDisposeEnvironment("create-dispose-environment-fail, 0, 23");
    }

    // Successful startup also permits creation and disposal within a single attach.
    private static void testDisposalDuringSuccessfulStartup() throws Exception {
        verifyCreateDisposeEnvironment("create-dispose-environment-success, 0, 0");
    }

    private static void verifyCreateDisposeEnvironment(String options) throws Exception {
        attach(options);
        attach("check-no-environments-success, 0, 0");
    }

    private static void attach(String options) throws Exception {
        AgentTestSupport.attach(LIBRARY, options);
    }
}
