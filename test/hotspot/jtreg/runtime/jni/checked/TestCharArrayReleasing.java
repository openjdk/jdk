/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8357601
 * @requires vm.flagless
 * @library /test/lib
 * @run main/othervm/native TestCharArrayReleasing 0 0
 * @run main/othervm/native TestCharArrayReleasing 1 0
 * @run main/othervm/native TestCharArrayReleasing 2 0
 * @run main/othervm/native TestCharArrayReleasing 3 0
 * @run main/othervm/native TestCharArrayReleasing 4 0
 * @run main/othervm/native TestCharArrayReleasing 0 1
 * @run main/othervm/native TestCharArrayReleasing 1 1
 * @run main/othervm/native TestCharArrayReleasing 2 1
 * @run main/othervm/native TestCharArrayReleasing 3 1
 * @run main/othervm/native TestCharArrayReleasing 4 1
 * @run main/othervm/native TestCharArrayReleasing 0 2
 * @run main/othervm/native TestCharArrayReleasing 1 2
 * @run main/othervm/native TestCharArrayReleasing 2 2
 * @run main/othervm/native TestCharArrayReleasing 3 2
 * @run main/othervm/native TestCharArrayReleasing 4 2
 * @run main/othervm/native TestCharArrayReleasing 0 3
 * @run main/othervm/native TestCharArrayReleasing 1 3
 * @run main/othervm/native TestCharArrayReleasing 2 3
 * @run main/othervm/native TestCharArrayReleasing 3 3
 * @run main/othervm/native TestCharArrayReleasing 4 3
 */

import jdk.test.lib.Platform;
import jdk.test.lib.process.ProcessTools;
import jdk.test.lib.process.OutputAnalyzer;

// Test the behaviour of the JNI "char" releasing functions, under Xcheck:jni,
// when they are passed "char" arrays obtained from different sources:
// - source_mode indicates which array to use
//   - 0: use a raw malloc'd array
//   - 1: use an array from GetCharArrayElements
//   - 2: use an array from GetStringChars
//   - 3: use an array from GetStringUTFChars
//   - 4: use an array from GetPrimitiveArrayCritical
// - release_mode indicates which releasing function to use
//   - 0: ReleaseCharArrayElements
//   - 1: ReleaseStringChars
//   - 2: ReleaseStringUTFChars
//   - 3: ReleasePrimitiveArrayCritical

public class TestCharArrayReleasing {

    static native void testIt(int srcMode, int releaseMode);

    static class Driver {

        static {
            System.loadLibrary("CharArrayReleasing");
        }

        public static void main(String[] args) {
            int srcMode = Integer.parseInt(args[0]);
            int relMode = Integer.parseInt(args[1]);
            testIt(srcMode, relMode);
        }
    }

    public static void main(String[] args) throws Throwable {
        int ABRT = 1;
        int[][] errorCodes = new int[][] {
            { ABRT, 0, ABRT, ABRT, ABRT },
            { ABRT, ABRT, 0, ABRT, ABRT },
            { ABRT, ABRT, ABRT, 0, ABRT },
            { ABRT, ABRT, ABRT, ABRT, 0 },
        };

        String rcae = "ReleaseCharArrayElements called on something allocated by GetStringChars";
        String rcaeUTF = "ReleaseCharArrayElements called on something allocated by GetStringUTFChars";
        String rcaeCrit = "ReleaseCharArrayElements called on something allocated by GetPrimitiveArrayCritical";
        String rcaeBounds = "ReleaseCharArrayElements: release array failed bounds check";
        String rsc = "ReleaseStringChars called on something not allocated by GetStringChars";
        String rscBounds = "ReleaseStringChars: release chars failed bounds check";
        String rsuc = "ReleaseStringUTFChars called on something not allocated by GetStringUTFChars";
        String rsucBounds = "ReleaseStringUTFChars: release chars failed bounds check";
        String rpac = "ReleasePrimitiveArrayCritical called on something not allocated by GetPrimitiveArrayCritical";
        String rpacBounds = "ReleasePrimitiveArrayCritical: release array failed bounds check";
        String rpacStr = "ReleasePrimitiveArrayCritical called on something allocated by GetStringChars";
        String rpacStrUTF = "ReleasePrimitiveArrayCritical called on something allocated by GetStringUTFChars";

        String[][] errorMsgs = new String[][] {
            { rcaeBounds, "", rcae, rcaeUTF, rcaeCrit },
            { rscBounds, rsc, "", rsc, rsc },
            { rsucBounds, rsuc, rsuc, "", rsuc },
            { rpacBounds, rpac, rpacStr, rpacStrUTF, "" },
        };

        int srcMode = Integer.parseInt(args[0]);
        int relMode = Integer.parseInt(args[1]);

        ProcessBuilder pb = ProcessTools.createLimitedTestJavaProcessBuilder(
             "-Djava.library.path=" + System.getProperty("test.nativepath"),
             "--enable-native-access=ALL-UNNAMED",
             "-XX:-CreateCoredumpOnCrash",
             "-Xcheck:jni",
             "TestCharArrayReleasing$Driver",
             args[0], args[1]);
        OutputAnalyzer output = new OutputAnalyzer(pb.start());
        output.shouldHaveExitValue(errorCodes[relMode][srcMode]);
        output.shouldContain(errorMsgs[relMode][srcMode]);
    }
}
