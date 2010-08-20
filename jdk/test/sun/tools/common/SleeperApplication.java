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

/*
 * An example subclass of SimpleApplication that illustrates how to
 * override the doMyAppWork() method.
 */

public class SleeperApplication extends SimpleApplication {
    public static int DEFAULT_SLEEP_TIME = 60;  // time is in seconds

    // execute the sleeper app work
    public void doMyAppWork(String[] args) throws Exception {
        int sleep_time = DEFAULT_SLEEP_TIME;

        // args[0] is the port-file
        if (args.length < 2) {
            System.out.println("INFO: using default sleep time of "
                + sleep_time + " seconds.");
        } else {
            try {
                sleep_time = Integer.parseInt(args[1]);
            } catch (NumberFormatException nfe) {
                throw new RuntimeException("Error: '" + args[1] +
                    "': is not a valid seconds value.");
            }
        }

        Thread.sleep(sleep_time * 1000);  // our "work" is to sleep
    }

    public static void main(String[] args) throws Exception {
        SleeperApplication myApp = new SleeperApplication();

        SimpleApplication.setMyApp(myApp);

        SimpleApplication.main(args);
    }
}
