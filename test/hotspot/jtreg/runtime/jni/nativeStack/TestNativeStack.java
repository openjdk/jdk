/*
 * Copyright (c) 2022, 2023, Oracle and/or its affiliates. All rights reserved.
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
 * @bug  8295974
 * @requires os.arch != "arm"
 * @library /test/lib
 * @summary Generate a JNI Fatal error, or a warning, in a launched VM and check
 *          the native stack is present as expected.
 * @run driver TestNativeStack
 */

import jdk.test.lib.Utils;
import jdk.test.lib.process.ProcessTools;
import jdk.test.lib.process.OutputAnalyzer;

public class TestNativeStack {

    /**
     * Create a native thread that will execute native code that
     * will either trigger a JNI warning (with -Xcheck:jni) or a JNI
     * error, depending on the value of `warning`.
     */
    static native void triggerJNIStackTrace(boolean warning);

    static {
        System.loadLibrary("nativeStack");
    }

    public static void main(String[] args) throws Throwable {
        // case 1: Trigger a JNI warning with Xcheck:jni
        OutputAnalyzer oa =
            ProcessTools.executeTestJava("-Xcheck:jni",
                                         "-Djava.library.path=" + Utils.TEST_NATIVE_PATH,
                                         "TestNativeStack$Main");
        oa.shouldHaveExitValue(0);
        oa.shouldContain("WARNING in native method");
        oa.shouldContain("thread_start");
        oa.reportDiagnosticSummary();

        // Case 2: Trigger a JNI FatalError call
        oa = ProcessTools.executeTestJava("-XX:-CreateCoredumpOnCrash",
                                          "-Djava.library.path=" + Utils.TEST_NATIVE_PATH,
                                          "TestNativeStack$Main",
                                          "error");
        oa.shouldNotHaveExitValue(0);
        oa.shouldContain("FATAL ERROR in native method");
        oa.shouldContain("thread_start");
        oa.reportDiagnosticSummary();
    }

    static class Main {
        public static void main(String[] args) throws Throwable {
            boolean doWarning = args.length == 0;
            System.out.println("Triggering a JNI " +
                               (doWarning ? "warning" : "fatal error"));
            triggerJNIStackTrace(doWarning);
        }
    }
}
