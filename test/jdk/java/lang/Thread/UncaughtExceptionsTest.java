/*
 * Copyright (c) 2004, 2020, Oracle and/or its affiliates. All rights reserved.
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

import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.process.ProcessTools;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import static java.lang.System.err;
import static java.lang.System.out;

/*
 * @test
 * @bug 4833089 4992454
 * @summary Check for proper handling of uncaught exceptions
 * @author Martin Buchholz
 * @library /test/lib
 * @build jdk.test.lib.process.*
 * @run testng UncaughtExceptionsTest
 */
public class UncaughtExceptionsTest {

    @DataProvider
    public Object[][] testCases() {
        return new Object[][]{
            new Object[] { "ThreadIsDeadAfterJoin",
                           0,
                           Seppuku.EXPECTED_RESULT,
                           "Exception in thread \"Thread-0\".*Seppuku"
            },
            new Object[] {
                            "MainThreadAbruptTermination",
                            1,
                            Seppuku.EXPECTED_RESULT,
                            "Exception in thread \"main\".*Seppuku"
            },
            new Object[] { "MainThreadNormalTermination", 0, Seppuku.EXPECTED_RESULT, ""},
            new Object[] { "DefaultUncaughtExceptionHandlerOnMainThread", 1, Seppuku.EXPECTED_RESULT, "" },
            new Object[] { "DefaultUncaughtExceptionHandlerOnMainThreadOverride", 1, Seppuku.EXPECTED_RESULT, "" },
            new Object[] { "DefaultUncaughtExceptionHandlerOnNonMainThreadOverride", 0, Seppuku.EXPECTED_RESULT, "" },
            new Object[] { "DefaultUncaughtExceptionHandlerOnNonMainThread", 0, Seppuku.EXPECTED_RESULT, "" },
            new Object[] { "ThreadGroupUncaughtExceptionHandlerOnNonMainThread", 0, Seppuku.EXPECTED_RESULT, "" }
        };
    }

    @Test(dataProvider = "testCases")
    public void test(String className, int exitValue, String stdOutMatch, String stdErrMatch) throws Throwable {
        ProcessBuilder processBuilder = ProcessTools.createJavaProcessBuilder(String.format("Seppuku$%s",className));
        OutputAnalyzer outputAnalyzer = ProcessTools.executeCommand(processBuilder);
        outputAnalyzer.shouldHaveExitValue(exitValue);
        outputAnalyzer.stderrShouldMatch(stdErrMatch);
        outputAnalyzer.stdoutShouldMatch(stdOutMatch);
    }

}


class OK implements Thread.UncaughtExceptionHandler {
    public void uncaughtException(Thread t, Throwable e) {
        out.println(Seppuku.EXPECTED_RESULT);
    }
}

class NeverInvoked implements Thread.UncaughtExceptionHandler {
    public void uncaughtException(Thread t, Throwable e) {
        err.println("Test failure: This handler should never be invoked!");
    }
}

class Seppuku extends Thread implements Runnable {

    final static String EXPECTED_RESULT = "OK";

    public static void seppuku() { throw new RuntimeException("Seppuku!"); }

    public void run() { seppuku(); }

    /**
     * A thread is never alive after you've join()ed it.
     */
    public static class ThreadIsDeadAfterJoin extends Seppuku {
        public static void main(String[] args) throws Exception {
            Thread t = new Seppuku();
            t.start(); t.join();
            if (! t.isAlive()) {
                out.println(EXPECTED_RESULT);
            }
        }
    }

    /**
     * Even the main thread is mortal - here it terminates "abruptly"
     */
    public static class MainThreadAbruptTermination extends Seppuku {
        public static void main(String[] args) {
            final Thread mainThread = currentThread();
            new Thread() { public void run() {
                try { mainThread.join(); }
                catch (InterruptedException e) {}
                if (! mainThread.isAlive())
                    out.println(EXPECTED_RESULT);
            }}.start();
            seppuku();
        }
    }

    /**
     * Even the main thread is mortal - here it terminates normally.
     */
    public static class MainThreadNormalTermination extends Seppuku {
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
    public static class DefaultUncaughtExceptionHandlerOnMainThread extends Seppuku {
        public static void main(String[] args) {
            currentThread().setUncaughtExceptionHandler(new OK());
            setDefaultUncaughtExceptionHandler(new NeverInvoked());
            seppuku();
        }
    }

    /**
     * Check that thread-level handler overrides global default handler.
     */
    public static class DefaultUncaughtExceptionHandlerOnMainThreadOverride extends Seppuku {
        public static void main(String[] args) {
            setDefaultUncaughtExceptionHandler(new OK());
            seppuku();
        }
    }

    /**
     * Check uncaught exception handler mechanism on non-main threads.
     */
    public static class DefaultUncaughtExceptionHandlerOnNonMainThreadOverride extends Seppuku {
        public static void main(String[] args) {
            Thread t = new Seppuku();
            t.setUncaughtExceptionHandler(new OK());
            t.start();
        }
    }

    /**
     * Check uncaught exception handler mechanism on non-main threads.
     */
    public static class DefaultUncaughtExceptionHandlerOnNonMainThread extends Seppuku {
        public static void main(String[] args) {
            setDefaultUncaughtExceptionHandler(new OK());
            new Seppuku().start();
        }
    }

    /**
     * Test ThreadGroup based uncaught exception handler mechanism.
     * Since the handler for the main thread group cannot be changed,
     * there are no tests for the main thread here.
     */
    public static class ThreadGroupUncaughtExceptionHandlerOnNonMainThread extends Seppuku {
        public static void main(String[] args) {
            setDefaultUncaughtExceptionHandler(new NeverInvoked());
            new Thread(
                    new ThreadGroup(EXPECTED_RESULT) {
                        public void uncaughtException(Thread t, Throwable e) {
                            out.println(EXPECTED_RESULT);
                        }
                    },
                    new Seppuku()
            ).start();
        }
    }
}
