/*
 * Copyright (c) 2007, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 6665028
 * @summary verify that the memory corruption doesn't happen. Note
 * that this test case fails without the fix in some different ways,
 * including timeout, due to the memory corruption.
 * @build Bug6665028
 * @run main/othervm/timeout=60 -Xmx16m Bug6665028
 */

import java.awt.font.TextAttribute;
import java.text.AttributedString;
import java.text.Bidi;

// test1() and test2() were derived from BidiEmbeddingTest.
public class Bug6665028 {

    private static boolean runrun = true;

    private static class Test extends Thread {
        public void run() {
            while (runrun) {
                test1();
                test2();
            }
        }
    }

    public static void main(String[] args) {
        Test[] tests = new Test[4];
        for (int i = 0; i < tests.length; i++) {
            Test t = new Test();
            tests[i] = t;
            t.start();
        }

        try {
            Thread.sleep(45000);
        } catch (InterruptedException e) {
        }

        runrun = false;

        for (int i = 0; i < tests.length; i++) {
            try {
                tests[i].join();
            } catch (InterruptedException e) {
            }
        }
    }

    static String target;
    static {
        String s = "A Bidi object provides information on the bidirectional reordering of the text used to create it. This is required, for example, to properly display Arabic or Hebrew text. ";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 1000; i++) {
            sb.append(s);
        }
        target = sb.toString();
    }

    static void test1() {
        String str = "If this text is >" + target + "< the test passed.";
        int start = str.indexOf(target);
        int limit = start + target.length();

        AttributedString astr = new AttributedString(str);
        astr.addAttribute(TextAttribute.BIDI_EMBEDDING,
                         new Integer(-1),
                         start,
                         limit);

        Bidi bidi = new Bidi(astr.getIterator());

        byte[] embs = new byte[str.length() + 3];
        for (int i = start + 1; i < limit + 1; ++i) {
            embs[i] = -1;
        }

        Bidi bidi2 = new Bidi(str.toCharArray(), 0, embs, 1, str.length(), Bidi.DIRECTION_DEFAULT_LEFT_TO_RIGHT);
        if (bidi.getRunCount() != 3 || bidi2.getRunCount() != 3) {
            throw new Error("Bidi run count incorrect");
        }
    }

    static void test2() {
        String str = "If this text is >" + target + "< the test passed.";
        int length = str.length();
        int start = str.indexOf(target);
        int limit = start + target.length();

        AttributedString astr = new AttributedString(str);
        astr.addAttribute(TextAttribute.RUN_DIRECTION, TextAttribute.RUN_DIRECTION_RTL);

        astr.addAttribute(TextAttribute.BIDI_EMBEDDING,
                         new Integer(-3),
                         start,
                         limit);

        Bidi bidi = new Bidi(astr.getIterator());

        if (bidi.getRunCount() != 6) { // runs of spaces and angles at embedding bound,s and final period, each get level 1
            throw new Error("Bidi embedding processing failed");
        }
    }
}
