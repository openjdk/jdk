/*
 * Copyright Amazon.com Inc. or its affiliates. All Rights Reserved.
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

package gc.shenandoah.generational;

import jdk.test.whitebox.WhiteBox;
import java.util.Random;
import java.util.HashMap;

/*
 *  To avoid the risk of false regressions identified by this test, the heap
 *  size is set artificially high.  Though this test is known to run reliably
 *  in 66 MB heap, the heap size for this test run is currently set to 256 MB.
 */

/*
 * @test id=generational
 * @requires vm.gc.Shenandoah
 * @summary Confirm that card marking and remembered set scanning do not crash.
 * @library /testlibrary /test/lib /
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run main/othervm -Xbootclasspath/a:.
 *      -Xms256m -Xmx256m
 *      -XX:+IgnoreUnrecognizedVMOptions
 *      -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI
 *      -XX:+UseShenandoahGC -XX:ShenandoahGCMode=generational
 *      -XX:NewRatio=1 -XX:+UnlockExperimentalVMOptions
 *      -XX:ShenandoahGuaranteedGCInterval=3000
 *      -XX:-UseDynamicNumberOfGCThreads
 *      gc.shenandoah.generational.TestConcurrentEvac
 */

public class TestConcurrentEvac {
    private static WhiteBox wb = WhiteBox.getWhiteBox();

    private static final int RANDOM_SEED = 46;

    // Smaller table will cause creation of more old-gen garbage
    // as previous entries in table are overwritten with new values.
    private static final int TABLE_SIZE = 53;
    private static final int MAX_STRING_LENGTH = 47;
    private static final int SENTENCE_LENGTH = 5;

    private static Random random = new Random(RANDOM_SEED);

    public static class Node {

        private String name;

        // Each Node instance holds an array containing all substrings of its name

        // This array has entries from 0 .. (name.length() - 1).
        // numSubstrings[i] represents the number of substrings that
        // correspond to a name of length i+1.
        private static int [] numSubstrings;

        static {
            // Initialize numSubstrings.
            // For a name of length N, there are
            //  N substrings of length 1
            //  N-1 substrings of length 2
            //  N-2 substrings of length 3
            //  ...
            //  1 substring of length N
            // Note that:
            //   numSubstrings[0] = 1
            //   numSubstrings[1] = 3
            //   numSubstrings[i] = (i + 1) + numSubstrings[i - 1]
            numSubstrings = new int[MAX_STRING_LENGTH];
            numSubstrings[0] = 1;
            for (int i = 1; i < MAX_STRING_LENGTH; i++) {
                numSubstrings[i] = (i + 1) + numSubstrings[i - 1];
            }
        }

        private String [] substrings;
        private Node [] neighbors;

        public Node(String name) {
            this.name = name;
            this.substrings = new String[numSubstrings[name.length() - 1]];

            int index = 0;
            for (int substringLength = 1; substringLength <= name.length(); substringLength++) {
                for (int offset = 0; offset + substringLength <= name.length(); offset++) {
                    this.substrings[index++] = name.substring(offset, offset + substringLength);
                }
            }
        }

        public String value() {
            return name;
        }

        public String arbitrarySubstring() {
            int index = TestConcurrentEvac.randomUnsignedInt(substrings.length);
            return substrings[index];
        }
    }


    // Return random int between 1 and MAX_STRING_LENGTH inclusive
    static int randomStringLength() {
        return randomUnsignedInt(MAX_STRING_LENGTH - 1) + 1;
    }

    static String randomCharacter() {
        int index = randomUnsignedInt(52);
        return "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ".substring(index, index + 1);
    }

    static String randomString() {
        int length = randomStringLength();
        String result = new String(); // make the compiler work for this garbage...
        for (int i = 0; i < length; i++) {
            result += randomCharacter();
        }
        return result;
    }

    static int randomUnsignedInt(int max) {
        return random.nextInt(max);
    }

    static int randomIndex() {
        return randomUnsignedInt(TABLE_SIZE);
    }

    public static void main(String args[]) throws Exception {
        HashMap<Integer, Node> table = new HashMap<Integer, Node>(TABLE_SIZE);

        if (!wb.getBooleanVMFlag("UseShenandoahGC") || !wb.getStringVMFlag("ShenandoahGCMode").equals("generational")) {
            throw new IllegalStateException("Command-line options not honored!");
        }

        for (int count = java.lang.Integer.MAX_VALUE/1024; count >= 0; count--) {
            int index = randomIndex();
            String name = randomString();
            table.put(index, new Node(name));
        }

        String conclusion = "";

        for (int i = 0; i < SENTENCE_LENGTH; i++) {
            Node node = table.get(randomIndex());
            if (node == null) {
                i--;
            } else {
                String s = node.arbitrarySubstring();
                conclusion += s;
                conclusion += " ";
            }
        }

        conclusion = conclusion.substring(0, conclusion.length() - 1);

        System.out.println("Conclusion is [" + conclusion + "]");

        if (!conclusion.equals("HN TInkzoLSDFVJYM mQAirHXbbgCJmUWozx DeispxWF MYFKBh")) {
            throw new IllegalStateException("Random sequence of words did not end well!");
        }
    }
}
