/*
* Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8165000
 * @summary Verify no IOException on OS X for large timeout value in select().
 * @requires (os.family == "mac")
 */
import java.io.IOException;
import java.nio.channels.Selector;

public class SelectTimeout {
    private static final long HUGE_TIMEOUT = 100000001000L;
    private static final long SLEEP_MILLIS = 10000;

    private static Exception theException;

    public static void main(String[] args)
        throws IOException, InterruptedException {
        int failures = 0;
        long[] timeouts =
            new long[] {0, HUGE_TIMEOUT/2, HUGE_TIMEOUT - 1, HUGE_TIMEOUT};
        for (long t : timeouts) {
            if (!test(t)) {
                failures++;
            }
        }
        if (failures > 0) {
            throw new RuntimeException("Test failed!");
        } else {
            System.out.println("Test succeeded");
        }
    }

    private static boolean test(final long timeout)
        throws InterruptedException, IOException {
        theException = null;

        Selector selector = Selector.open();

        Thread t = new Thread(() -> {
            try {
                selector.select(timeout);
            } catch (IOException ioe) {
                theException = ioe;
            }
        });
        t.start();

        Thread.currentThread().sleep(SLEEP_MILLIS);
        t.interrupt();

        if (theException == null) {
            System.out.printf("Test succeeded with timeout %d%n", timeout);
            return true;
        } else {
            System.err.printf("Test failed with timeout %d%n", timeout);
            theException.printStackTrace();
            return false;
        }
    }
}
