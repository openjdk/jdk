/*
 * Copyright (c) 2010, Oracle and/or its affiliates. All rights reserved.
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

import java.util.logging.*;

public class AnonLoggerWeakRefLeak {
    public static int DEFAULT_LOOP_TIME = 60;  // time is in seconds

    public static void main(String[] args) {
        int loop_time = 0;
        int max_loop_time = DEFAULT_LOOP_TIME;

        if (args.length == 0) {
            System.out.println("INFO: using default time of "
                + max_loop_time + " seconds.");
        } else {
            try {
                max_loop_time = Integer.parseInt(args[0]);
            } catch (NumberFormatException nfe) {
                System.err.println("Error: '" + args[0]
                    + "': is not a valid seconds value.");
                System.err.println("Usage: AnonLoggerWeakRefLeak [seconds]");
                System.exit(1);
            }
        }

        long count = 0;
        long now = 0;
        long startTime = System.currentTimeMillis();

        while (now < (startTime + (max_loop_time * 1000))) {
            if ((count % 1000) == 0) {
                // Print initial call count to let caller know that
                // we're up and running and then periodically
                System.out.println("INFO: call count = " + count);
            }

            for (int i = 0; i < 100; i++) {
                // this Logger call is leaking a WeakReference in Logger.kids
                java.util.logging.Logger.getAnonymousLogger();
                count++;
            }

            try {
                // delay for 1/10 of a second to avoid CPU saturation
                Thread.sleep(100);
            } catch (InterruptedException ie) {
                // ignore any exceptions
            }

            now = System.currentTimeMillis();
        }

        System.out.println("INFO: final loop count = " + count);
    }
}
