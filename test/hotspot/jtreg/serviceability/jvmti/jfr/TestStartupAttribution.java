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
 * @summary Environment attribution is one-time per startup and survives nesting and cross-thread disposal
 * @requires vm.flagless & vm.hasJFR & vm.jvmti & !jdk.static
 * @library /test/lib ../model
 * @modules java.management jdk.management jdk.jfr jdk.attach
 * @run main/othervm/native/timeout=300 TestStartupAttribution
 */

import java.lang.management.ManagementFactory;
import java.util.List;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;

import javax.management.ObjectName;

import jdk.test.lib.Asserts;
import jdk.test.lib.Utils;

public class TestStartupAttribution {
    // Options: operation
    //
    // Each attach passes one operation string to Agent_OnAttach and expects JNI_OK (0).
    // These operations do not use the slot and value arguments of the other attach tests.
    //
    // create-environment-after-nested-attach-success:
    //     Attach this library recursively on the current thread, then create and retain
    //     the outer startup's JVMTI environment.
    // create-two-environments-dispose-one-success:
    //     Verify same-thread nesting, create two JVMTI environments, retain the first,
    //     and dispose the second. The first belongs to this startup; the second must
    //     remain unassociated, even though the outer startup has no environment yet.
    // dispose-environments-success:
    //     Dispose the retained environments from the outer and nested startups.
    // create-dispose-create-environment-success:
    //     Within one startup, create and dispose the first JVMTI environment, then create
    //     and retain a second. Disposal must not make this startup eligible for association again.
    // create-environment-dispose-on-worker-success:
    //     Create a JVMTI environment on the startup thread and dispose it on a worker.
    //     Wait for disposal before returning from Agent_OnAttach.
    //
    // Each child JVM receives its initial operation as the sole argument.
    private static final String LIBRARY = JfrAgentTestSupport.library("AttributionAgent");
    private static AgentEventRecording recording;
    private static Thread outerAttachThread;
    private static int nestedAttachCalls;
    private static int workerDisposalCalls;

    // Registered explicitly so this operation does not depend on
    // native lookup being enabled before the first startup has returned.
    private static native void disposeOnWorker(long environment);

    // Resolve through JVMTI native lookup after startup, without another attach.
    private static native void verifyRetainedEnvironment();
    private static native void disposeRetainedEnvironment();

    public static void main(String[] args) throws Exception {
        if (args.length != 0) {
            runChild(args[0]);
            return;
        }
        testNestedStartupAssociatesOnlyWithInnermostAgent();
        testDisposalDoesNotAllowAnotherAssociation();
        testDisposalWaitsForStartupTiming();
    }

    // A second environment created by nested startup must not become associated with the outer agent.
    private static void testNestedStartupAssociatesOnlyWithInnermostAgent() throws Exception {
        launch("create-environment-after-nested-attach-success");
    }

    // Disposing the first association does not let a later environment become associated with the same startup.
    private static void testDisposalDoesNotAllowAnotherAssociation() throws Exception {
        launch("create-dispose-create-environment-success");
    }

    // Disposal on another thread is reported only after Agent_OnAttach returns with completed timing.
    private static void testDisposalWaitsForStartupTiming() throws Exception {
        launch("create-environment-dispose-on-worker-success");
    }

    private static void launch(String operation) throws Exception {
        var output = JfrAgentTestSupport.launch(TestStartupAttribution.class, List.of(), operation);
        output.shouldHaveExitValue(0).shouldContain("attribution operation complete");
        JfrAgentTestSupport.unloadedOnce(output, "AttributionAgent");
    }

    private static void runChild(String operation) throws Exception {
        switch (operation) {
            case "create-environment-after-nested-attach-success" ->
                verifyCreateEnvironmentAfterNestedAttachSuccess();
            case "create-dispose-create-environment-success" ->
                verifyCreateDisposeCreateEnvironmentSuccess();
            case "create-environment-dispose-on-worker-success" ->
                verifyCreateEnvironmentDisposeOnWorkerSuccess();
            default -> throw new AssertionError("Unknown attribution operation: " + operation);
        }
        AgentEventRecording.assertNoEventsInNewRecording(LIBRARY);
        System.out.println("attribution operation complete");
    }

    private static void verifyCreateEnvironmentAfterNestedAttachSuccess() throws Exception {
        try (AgentEventRecording localRecording = new AgentEventRecording()) {
            recording = localRecording;
            attach("create-environment-after-nested-attach-success");
            var results = localRecording.stop();
            Asserts.assertEquals(nestedAttachCalls, 1);
            assertAgentEvents(results,
                              "create-environment-after-nested-attach-success",
                              "create-two-environments-dispose-one-success")
                              .assertLive();
        } finally {
            recording = null;
        }
        try (AgentEventRecording localRecording = new AgentEventRecording()) {
            attach("dispose-environments-success");
            assertAgentEvents(localRecording.stop(),
                              "create-environment-after-nested-attach-success",
                              "create-two-environments-dispose-one-success")
                              .assertDisposed();
        }
    }

    private static void verifyCreateDisposeCreateEnvironmentSuccess() throws Exception {
        try (AgentEventRecording localRecording = new AgentEventRecording()) {
            attach("create-dispose-create-environment-success");
            assertAgentEvents(localRecording.stop(), "create-dispose-create-environment-success").assertDisposed();
        }
        try (AgentEventRecording localRecording = new AgentEventRecording()) {
            verifyRetainedEnvironment();
            localRecording.snapshot().assertNoEvents(LIBRARY);
            disposeRetainedEnvironment();
            localRecording.stop().assertNoEvents(LIBRARY);
        }
    }

    private static void verifyCreateEnvironmentDisposeOnWorkerSuccess() throws Exception {
        try (AgentEventRecording localRecording = new AgentEventRecording()) {
            recording = localRecording;
            attach("create-environment-dispose-on-worker-success");
            var results = localRecording.stop();
            Asserts.assertEquals(workerDisposalCalls, 1);
            assertAgentEvents(results, "create-environment-dispose-on-worker-success").assertDisposed();
        } finally {
            recording = null;
        }
    }

    private static AgentEventRecording.FilteredList assertAgentEvents(AgentEventRecording.Results results, String... options) {
        return results.forLibrary(LIBRARY).assertOptions(options).assertDynamic(true).assertInitializationTiming();
    }

    public static void disposeEnvironmentOnWorker(long environment) throws Exception {
        ++workerDisposalCalls;
        Thread invokingThread = Thread.currentThread();
        FutureTask<Void> disposal = new FutureTask<>(() -> {
            Asserts.assertNE(Thread.currentThread(), invokingThread);
            disposeOnWorker(environment);
            return null;
        });
        Thread worker = new Thread(disposal, "dispose-pending-agent");
        worker.setDaemon(true);
        worker.start();
        disposal.get(Utils.adjustTimeout(30_000), TimeUnit.MILLISECONDS);
        recording.snapshot().assertNoEvents(LIBRARY);
    }

    public static void attachNestedAgent() throws Exception {
        Asserts.assertNull(outerAttachThread);
        outerAttachThread = Thread.currentThread();
        try {
            attach("create-two-environments-dispose-one-success");
            assertAgentEvents(recording.snapshot(), "create-two-environments-dispose-one-success").assertLive();
        } finally {
            outerAttachThread = null;
        }
    }

    public static void verifyNestedAttachThread() {
        Asserts.assertEquals(Thread.currentThread(), outerAttachThread, "Startup must actually be nested");
        ++nestedAttachCalls;
    }

    // Calling the diagnostic operation on the dynamic MBean to get a recursive startup request
    // for this thread instead of queueing a second request to the attach listener.
    private static void attach(String options) throws Exception {
        String answer = (String) ManagementFactory.getPlatformMBeanServer().invoke(
            new ObjectName("com.sun.management:type=DiagnosticCommand"), "jvmtiAgentLoad",
            new Object[] {new String[] {"\"" + LIBRARY + "\"", options}},
            new String[] {String[].class.getName()});
        Asserts.assertTrue(answer.lines().anyMatch("return code: 0"::equals), answer);
    }
}
