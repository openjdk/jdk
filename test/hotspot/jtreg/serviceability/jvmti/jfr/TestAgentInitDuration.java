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
 * @summary Initialization timing distinguishes skipped, executing, and absent VMInit callbacks
 * @requires vm.flagless & vm.hasJFR & vm.jvmti & !jdk.static
 * @library /test/lib ../model
 * @modules jdk.jfr jdk.attach
 * @run main/othervm/native/timeout=300 TestAgentInitDuration
 */

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;

import jdk.test.lib.Asserts;
import jdk.test.lib.Utils;

public class TestAgentInitDuration {
    // launch arguments: <operation>, <agent options>...
    //
    // Operation selects the verification performed by the child JVM's main method.
    // It is not passed to Agent_OnLoad. Here, callback means VMInit.
    //
    // Operation                            Agent options, in command-line order
    // dispose-environment-before-callback  dispose-environments-in-callback, dispose-environment-in-callback
    // dispose-environment-in-callback      dispose-environment-in-callback
    // dispose-environment-on-worker        dispose-environment-on-worker
    // create-environment-without-callback  create-environment-without-callback
    //
    // dispose-environment-before-callback-from-other-library uses the first operation's
    // agent options, but passes dispose-environments-in-callback to DISPOSER_LIBRARY.
    // Its VMInit callback invokes the Java test, which resolves LIBRARY's native disposal
    // method before that library's agent can enter its own VMInit callback.
    //
    // Each agent options value becomes a separate -agentpath:<library>=<options> argument.
    // Apart from the cross-library case above, each -agentpath argument uses LIBRARY.
    // Its options instruct the library which callbacks to register and how to retain or
    // dispose JVMTI environments.
    // Each Agent_OnLoad invocation receives one such string and obtains its own JVMTI environment.
    // These options do not use the operation, slot, value protocol of the attach tests.
    //
    // Instructions passed in the Agent_OnLoad options string:
    // dispose-environments-in-callback:
    //     Register VMInit; in that callback, dispose the other invocation's retained environment
    //     before its VMInit callback can run, then dispose the callback's own environment.
    // dispose-environment-in-callback:
    //     Register VMStart and VMInit; if VMInit runs, dispose the callback's own environment.
    // dispose-environment-on-worker:
    //     Register VMStart and VMInit; release a worker to dispose the retained environment
    //     just before VMInit returns, allowing disposal to compete with timing publication.
    // create-environment-without-callback:
    //     Register no callbacks; retain the environment for the Java test to dispose.
    private static final String LIBRARY = JfrAgentTestSupport.library("AgentInitDuration");
    private static final String DISPOSER_LIBRARY = JfrAgentTestSupport.library("AgentInitDisposer");

    // Resolve through JVMTI native lookup, without System.loadLibrary or RegisterNatives.
    private static native void disposeRetainedEnvironment();

    private static AgentEventRecording recording;
    private static boolean vmStartReceived;
    private static boolean disposeBothVmInitReceived;
    private static boolean disposeOwnVmInitReceived;
    private static boolean disposeOnWorkerVmInitReceived;
    private static FutureTask<Void> workerDisposal;

    public static void main(String[] args) throws Exception {
        if (args.length != 0) {
            runChild(args[0]);
            return;
        }
        testSkippedCallbackHasNoTiming();
        testOtherLibraryDisposalBeforeCallbackHasNoTiming();
        testDisposalWaitsForCallbackTiming();
        testWorkerDisposalReportsCompletedCallbackTiming();
        testNoCallbackHasZeroDuration();
    }

    // Disposal before an environment's VMInit callback reports unavailable initialization timing.
    private static void testSkippedCallbackHasNoTiming() throws Exception {
        launch("dispose-environment-before-callback", "dispose-environments-in-callback", "dispose-environment-in-callback");
    }

    // Another library's VMInit callback resolves the target's native disposal method before its callback runs.
    private static void testOtherLibraryDisposalBeforeCallbackHasNoTiming() throws Exception {
        launch("dispose-environment-before-callback-from-other-library",
               List.of("-agentpath:" + DISPOSER_LIBRARY + "=dispose-environments-in-callback",
                       "-agentpath:" + LIBRARY + "=dispose-environment-in-callback"),
               "AgentInitDisposer", "AgentInitDuration");
    }

    // Disposal inside VMInit is reported only after the callback returns with completed timing.
    private static void testDisposalWaitsForCallbackTiming() throws Exception {
        launch("dispose-environment-in-callback", "dispose-environment-in-callback");
    }

    // Worker disposal competing with VMInit completion reports completed timing and exactly one disposal event.
    private static void testWorkerDisposalReportsCompletedCallbackTiming() throws Exception {
        launch("dispose-environment-on-worker", "dispose-environment-on-worker");
    }

    // An agent without a VMInit callback reports valid initialization timing with zero duration.
    private static void testNoCallbackHasZeroDuration() throws Exception {
        launch("create-environment-without-callback", "create-environment-without-callback");
    }

    private static void launch(String operation, String... options) throws Exception {
        var startup = Arrays.stream(options).map(option -> "-agentpath:" + LIBRARY + "=" + option).toList();
        // Multiple command-line agents sharing one library still require only one unload notification.
        launch(operation, startup, "AgentInitDuration");
    }

    private static void launch(String operation, List<String> startup, String... libraries) throws Exception {
        var output = JfrAgentTestSupport.launch(TestAgentInitDuration.class, startup, operation);
        output.shouldHaveExitValue(0).shouldContain("initialization operation complete");
        for (String library : libraries) {
            JfrAgentTestSupport.unloadedOnce(output, library);
        }
    }

    private static void runChild(String operation) throws Exception {
        switch (operation) {
            case "dispose-environment-before-callback" -> verifyDisposeEnvironmentBeforeCallback(LIBRARY);
            case "dispose-environment-before-callback-from-other-library" -> verifyDisposeEnvironmentBeforeCallback(DISPOSER_LIBRARY);
            case "dispose-environment-in-callback" -> verifyDisposeEnvironmentInCallback();
            case "dispose-environment-on-worker" -> verifyDisposeEnvironmentOnWorker();
            case "create-environment-without-callback" -> verifyCreateEnvironmentWithoutCallback();
            default -> throw new AssertionError("Unknown initialization operation: " + operation);
        }
        AgentEventRecording.assertNoEventsInNewRecording(LIBRARY, DISPOSER_LIBRARY);
        System.out.println("initialization operation complete");
    }

    private static void verifyDisposeEnvironmentBeforeCallback(String disposerLibrary) throws Exception {
        Asserts.assertTrue(vmStartReceived, "VMStart was not delivered");
        Asserts.assertTrue(disposeBothVmInitReceived);
        Asserts.assertFalse(disposeOwnVmInitReceived);
        Asserts.assertNotNull(recording);
        try (AgentEventRecording completed = recording) {
            var results = completed.stop();
            if (disposerLibrary.equals(LIBRARY)) {
                results.forLibrary(LIBRARY).assertOptions("dispose-environments-in-callback", "dispose-environment-in-callback");
            } else {
                results.forLibrary(disposerLibrary).assertOptions("dispose-environments-in-callback");
                results.forLibrary(LIBRARY).assertOptions("dispose-environment-in-callback");
            }
            assertDisposedAgent(results.forLibrary(disposerLibrary), "dispose-environments-in-callback").assertInitializationTiming();
            assertDisposedAgent(results.forLibrary(LIBRARY), "dispose-environment-in-callback").assertNoInitializationTiming();
        } finally {
            recording = null;
        }
    }

    private static void verifyDisposeEnvironmentInCallback() throws Exception {
        Asserts.assertTrue(vmStartReceived, "VMStart was not delivered");
        Asserts.assertFalse(disposeBothVmInitReceived);
        Asserts.assertTrue(disposeOwnVmInitReceived);
        Asserts.assertNotNull(recording);
        try (AgentEventRecording completed = recording) {
            assertDisposedAgent(completed.stop(), "dispose-environment-in-callback").assertInitializationTiming();
        } finally {
            recording = null;
        }
    }

    private static void verifyDisposeEnvironmentOnWorker() throws Exception {
        Asserts.assertTrue(vmStartReceived, "VMStart was not delivered");
        Asserts.assertFalse(disposeBothVmInitReceived);
        Asserts.assertFalse(disposeOwnVmInitReceived);
        Asserts.assertTrue(disposeOnWorkerVmInitReceived);
        Asserts.assertNotNull(recording);
        Asserts.assertNotNull(workerDisposal);
        try (AgentEventRecording completed = recording) {
            // VMInit has returned; wait here so neither side is forced to publish first.
            workerDisposal.get(Utils.adjustTimeout(30_000), TimeUnit.MILLISECONDS);
            assertDisposedAgent(completed.stop(), "dispose-environment-on-worker").assertInitializationTiming();
        } finally {
            recording = null;
            workerDisposal = null;
        }
    }

    private static void verifyCreateEnvironmentWithoutCallback() throws Exception {
        Asserts.assertFalse(vmStartReceived);
        Asserts.assertFalse(disposeBothVmInitReceived);
        Asserts.assertFalse(disposeOwnVmInitReceived);
        Asserts.assertNull(recording);
        try (AgentEventRecording localRecording = new AgentEventRecording()) {
            assertZeroDurationAgent(localRecording.snapshot()).assertLive();
            disposeRetainedEnvironment();
            assertZeroDurationAgent(localRecording.stop()).assertDisposed();
        }
    }

    private static AgentEventRecording.FilteredList assertDisposedAgent(AgentEventRecording.Results results, String options) {
        return assertDisposedAgent(results.forLibrary(LIBRARY).assertOptions(options), options);
    }

    private static AgentEventRecording.FilteredList assertDisposedAgent(AgentEventRecording.FilteredList events, String options) {
        return events.forOptions(options).assertDynamic(false).assertDisposed();
    }

    private static AgentEventRecording.FilteredList assertZeroDurationAgent(AgentEventRecording.Results results) {
        var events = results.forLibrary(LIBRARY).assertOptions("create-environment-without-callback").assertDynamic(false).assertInitializationTiming();
        for (var event : events.events()) {
            Asserts.assertEquals(event.getLong("initializationDuration"), 0L, event.toString());
        }
        return events;
    }

    public static void vmStarted() {
        Asserts.assertFalse(vmStartReceived);
        vmStartReceived = true;
    }

    public static void beginDisposeBothCallback() {
        Asserts.assertTrue(vmStartReceived);
        Asserts.assertFalse(disposeBothVmInitReceived);
        Asserts.assertFalse(disposeOwnVmInitReceived);
        Asserts.assertNull(recording);
        disposeBothVmInitReceived = true;
        recording = new AgentEventRecording();
    }

    public static void verifyDisposeEnvironmentBeforeCallbackSnapshot() throws Exception {
        Asserts.assertFalse(disposeOwnVmInitReceived);
        var results = recording.snapshot();
        assertDisposedAgent(results, "dispose-environment-in-callback").assertNoInitializationTiming();
        // The other library, if present, is still executing its VMInit callback.
        results.assertNoEvents(DISPOSER_LIBRARY);
    }

    public static void disposeEnvironmentFromOtherLibraryCallback() throws Exception {
        beginDisposeBothCallback();
        // This first native resolution occurs during VMInit, before main and before the target's callback.
        disposeRetainedEnvironment();
        verifyDisposeEnvironmentBeforeCallbackSnapshot();
    }

    public static void beginDisposeOwnCallback() {
        Asserts.assertTrue(vmStartReceived);
        Asserts.assertFalse(disposeBothVmInitReceived);
        Asserts.assertFalse(disposeOwnVmInitReceived);
        Asserts.assertNull(recording);
        disposeOwnVmInitReceived = true;
        recording = new AgentEventRecording();
    }

    public static void verifyDisposeEnvironmentInCallbackSnapshot() throws Exception {
        // DisposeEnvironment has returned, but the VMInit duration is unfinished.
        recording.snapshot().assertNoEvents(LIBRARY);
    }

    public static void disposeEnvironmentOnWorker() throws Exception {
        Asserts.assertTrue(vmStartReceived);
        Asserts.assertFalse(disposeBothVmInitReceived);
        Asserts.assertFalse(disposeOwnVmInitReceived);
        Asserts.assertFalse(disposeOnWorkerVmInitReceived);
        Asserts.assertNull(recording);
        Asserts.assertNull(workerDisposal);
        disposeOnWorkerVmInitReceived = true;
        recording = new AgentEventRecording();
        Thread callbackThread = Thread.currentThread();
        CountDownLatch ready = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        workerDisposal = new FutureTask<>(() -> {
            Asserts.assertNE(Thread.currentThread(), callbackThread);
            ready.countDown();
            Asserts.assertTrue(release.await(Utils.adjustTimeout(30_000), TimeUnit.MILLISECONDS), "VMInit did not release the disposal worker");
            disposeRetainedEnvironment();
            return null;
        });
        Thread worker = new Thread(workerDisposal, "dispose-vm-init-agent");
        worker.setDaemon(true);
        worker.start();
        Asserts.assertTrue(ready.await(Utils.adjustTimeout(30_000), TimeUnit.MILLISECONDS), "Disposal worker did not become ready");
        recording.snapshot().assertNoEvents(LIBRARY);
        // Release at callback return without waiting for disposal. Either lock ordering is valid.
        release.countDown();
    }
}
