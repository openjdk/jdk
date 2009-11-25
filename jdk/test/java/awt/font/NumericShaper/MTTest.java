/*
 * Copyright (c) 2009 Sun Microsystems, Inc.  All Rights Reserved.
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
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 */

/*
 * @test
 * @bug 6843181
 * @summary Confirm that NumericShaper is thread-safe.
 * @run main/timeout=300/othervm MTTest
 */

import java.awt.font.NumericShaper;
import java.util.Arrays;
import java.util.EnumSet;
import static java.awt.font.NumericShaper.*;

public class MTTest {
    static volatile boolean runrun = true;
    static volatile boolean err = false;

    final static String text = "-123 (English) 456.00 (Arabic) \u0641\u0642\u0643 -789 (Thai) \u0e01\u0e33 01.23";
    static char[] t1, t2;
    static NumericShaper ns1, ns2, ns3, ns4;

    public static void main(String[] args) {
        System.out.println("   original: " + text);
        ns1 = getContextualShaper(EnumSet.of(Range.ARABIC), Range.ARABIC);
        t1 = text.toCharArray();
        ns1.shape(t1, 0, t1.length);
        System.out.println("expected t1: " + String.valueOf(t1));

        ns2 = getContextualShaper(EnumSet.of(Range.THAI), Range.THAI);
        t2 = text.toCharArray();
        ns2.shape(t2, 0, t2.length);
        System.out.println("expected t2: " + String.valueOf(t2));

        ns3 = getContextualShaper(ARABIC, ARABIC);
        ns4 = getContextualShaper(THAI, THAI);

        Thread th1 = new Thread(new Work(ns1, t1));
        Thread th2 = new Thread(new Work(ns2, t2));
        Thread th3 = new Thread(new Work(ns1, t1));
        Thread th4 = new Thread(new Work(ns2, t2));
        Thread th5 = new Thread(new Work(ns3, t1));
        Thread th6 = new Thread(new Work(ns4, t2));
        Thread th7 = new Thread(new Work(ns3, t1));
        Thread th8 = new Thread(new Work(ns4, t2));

        th1.start();
        th2.start();
        th3.start();
        th4.start();
        th5.start();
        th6.start();
        th7.start();
        th8.start();

        try {
            for (int i = 0; runrun && i < 180; i++) {
                Thread.sleep(1000); // 1 seconds
            }
            runrun = false;
            th1.join();
            th2.join();
            th3.join();
            th4.join();
            th5.join();
            th6.join();
            th7.join();
            th8.join();
        }
        catch (InterruptedException e) {
        }

        if (err) {
            throw new RuntimeException("Thread-safe test failed.");
        }
    }

    private static class Work implements Runnable {
        NumericShaper ns;
        char[] expectedText;

        Work(NumericShaper ns, char[] expectedText) {
            this.ns = ns;
            this.expectedText = expectedText;

        }

        public void run() {
            int count = 0;
            while (runrun) {
                char[] t = text.toCharArray();
                try {
                    count++;
                    ns.shape(t, 0, t.length);
                } catch (Exception e) {
                    System.err.println("Error: Unexpected exception: " + e);
                    runrun = false;
                    err = true;
                    return;
                }
                if (!Arrays.equals(t, expectedText)) {
                    System.err.println("Error: shape() returned unexpected value: ");
                    System.err.println("count = " + count);
                    System.err.println("   expected: " + String.valueOf(expectedText));
                    System.err.println("        got: " + String.valueOf(t));
                    runrun = false;
                    err = true;
                    return;
                }
            }
        }
    }
}
