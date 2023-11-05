/*
 * Copyright (c) 2004, 2023, Oracle and/or its affiliates. All rights reserved.
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

import java.util.stream.Stream;

import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.process.ProcessTools;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.Arguments;

import static java.lang.System.err;
import static java.lang.System.out;

/*
 * @test
 * @bug 4833089 4992454
 * @summary Check for proper handling of uncaught exceptions
 * @author Martin Buchholz
 * @library /test/lib
 * @build jdk.test.lib.process.*
 * @run junit UncaughtExceptionsTest
 */
class UncaughtExceptionsTest {

    private static Stream<Arguments> testCases() {
        return Stream.of(
            Arguments.of("ThreadIsDeadAfterJoin",
                         0,
                         UncaughtExitSimulator.EXPECTED_RESULT,
                         "Exception in thread \"Thread-\\d+\".*simulateUncaughtExitEvent"),
            Arguments.of("MainThreadAbruptTermination",
                         1,
                         UncaughtExitSimulator.EXPECTED_RESULT,
                         "Exception in thread \"main\".*simulateUncaughtExitEvent"),
            Arguments.of("MainThreadNormalTermination",
                         0,
                         UncaughtExitSimulator.EXPECTED_RESULT,
                         ""),
            Arguments.of("DefaultUncaughtExceptionHandlerOnMainThread",
                         1,
                         UncaughtExitSimulator.EXPECTED_RESULT,
                         ""),
            Arguments.of("DefaultUncaughtExceptionHandlerOnMainThreadOverride",
                         1,
                         UncaughtExitSimulator.EXPECTED_RESULT,
                         ""),
            Arguments.of("DefaultUncaughtExceptionHandlerOnNonMainThreadOverride",
                         0,
                         UncaughtExitSimulator.EXPECTED_RESULT,
                         ""),
            Arguments.of("DefaultUncaughtExceptionHandlerOnNonMainThread",
                         0,
                         UncaughtExitSimulator.EXPECTED_RESULT,
                         ""),
            Arguments.of("ThreadGroupUncaughtExceptionHandlerOnNonMainThread",
                         0,
                         UncaughtExitSimulator.EXPECTED_RESULT,
                         "")
        );
    }

    @ParameterizedTest
    @MethodSource("testCases")
    void test(String className, int exitValue, String stdOutMatch, String stdErrMatch) throws Throwable {
        String cmd = "UncaughtExitSimulator$" + className;
        ProcessBuilder processBuilder = ProcessTools.createLimitedTestJavaProcessBuilder(cmd);
        OutputAnalyzer outputAnalyzer = ProcessTools.executeCommand(processBuilder);
        outputAnalyzer.shouldHaveExitValue(exitValue);
        outputAnalyzer.stderrShouldMatch(stdErrMatch);
        outputAnalyzer.stdoutShouldMatch(stdOutMatch);
    }

}

class OK implements Thread.UncaughtExceptionHandler {
    public void uncaughtException(Thread t, Throwable e) {
        out.println(UncaughtExitSimulator.EXPECTED_RESULT);
    }
}

class NeverInvoked implements Thread.UncaughtExceptionHandler {
    public void uncaughtException(Thread t, Throwable e) {
        err.println("Test failure: This handler should never be invoked!");
    }
}

class UncaughtExitSimulator extends Thread implements Runnable {

    final static String EXPECTED_RESULT = "OK";

    public static void throwRuntimeException() {
        throw new RuntimeException("simulateUncaughtExitEvent");
    }

    public void run() { throwRuntimeException(); }

    /**
     * A thread is never alive after you've join()ed it.
     */
    public static class ThreadIsDeadAfterJoin extends UncaughtExitSimulator {
        public static void main(String[] args) throws Exception {
            Thread t = new UncaughtExitSimulator();
            t.start(); t.join();
            if (! t.isAlive()) {
                out.println(EXPECTED_RESULT);
            }
        }
    }

    /**
     * Even the main thread is mortal - here it terminates "abruptly"
     */
    public static class MainThreadAbruptTermination extends UncaughtExitSimulator {
        public static void main(String[] args) {
            final Thread mainThread = currentThread();
            new Thread() { public void run() {
                try { mainThread.join(); }
                catch (InterruptedException e) {}
                if (! mainThread.isAlive())
                    out.println(EXPECTED_RESULT);
            }}.start();
            throwRuntimeException();
        }
    }

    /**
     * Even the main thread is mortal - here it terminates normally.
     */
    public static class MainThreadNormalTermination extends UncaughtExitSimulator {
        public static void main(String[] args) {
            final Thread mainThread = currentThread();
            new Thread() {
                public void run() {
                    try {
                        mainThread.join();
                    } catch (InterruptedException e) {
                    }
                    if (!mainThread.isAlive())
                        out.println(EXPECTED_RESULT);
                }
            }.start();
        }
    }

    /**
     * Check uncaught exception handler mechanism on the main thread.
     */
    public static class DefaultUncaughtExceptionHandlerOnMainThread extends UncaughtExitSimulator {
        public static void main(String[] args) {
            currentThread().setUncaughtExceptionHandler(new OK());
            setDefaultUncaughtExceptionHandler(new NeverInvoked());
            throwRuntimeException();
        }
    }

    /**
     * Check that thread-level handler overrides global default handler.
     */
    public static class DefaultUncaughtExceptionHandlerOnMainThreadOverride extends UncaughtExitSimulator {
        public static void main(String[] args) {
            setDefaultUncaughtExceptionHandler(new OK());
            throwRuntimeException();
        }
    }

    /**
     * Check uncaught exception handler mechanism on non-main threads.
     */
    public static class DefaultUncaughtExceptionHandlerOnNonMainThreadOverride extends UncaughtExitSimulator {
        public static void main(String[] args) {
            Thread t = new UncaughtExitSimulator();
            t.setUncaughtExceptionHandler(new OK());
            t.start();
        }
    }

    /**
     * Check uncaught exception handler mechanism on non-main threads.
     */
    public static class DefaultUncaughtExceptionHandlerOnNonMainThread extends UncaughtExitSimulator {
        public static void main(String[] args) {
            setDefaultUncaughtExceptionHandler(new OK());
            new UncaughtExitSimulator().start();
        }
    }

    /**
     * Test ThreadGroup based uncaught exception handler mechanism.
     * Since the handler for the main thread group cannot be changed,
     * there are no tests for the main thread here.
     */
    public static class ThreadGroupUncaughtExceptionHandlerOnNonMainThread extends UncaughtExitSimulator {
        public static void main(String[] args) {
            setDefaultUncaughtExceptionHandler(new NeverInvoked());
            new Thread(
                    new ThreadGroup(EXPECTED_RESULT) {
                        public void uncaughtException(Thread t, Throwable e) {
                            out.println(EXPECTED_RESULT);
                        }
                    },
                    new UncaughtExitSimulator()
            ).start();
        }
    }
}
