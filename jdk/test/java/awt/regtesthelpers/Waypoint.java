/*
 * Copyright (c) 2011, Oracle and/or its affiliates. All rights reserved.
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
 * @summary This is a utility for coordinating the flow of events on different
            threads.
 * @summary com.apple.junit.utils
 */
package test.java.awt.regtesthelpers;

public class Waypoint {

    static final String MSG = "Waypoint timed out";
    // Wait up to five seconds for our clear() to be called
    static final int TIMEOUT = 5000;
    boolean clear = false;

    public Waypoint() {

    }

    //
    //    Pause for either TIMEOUT millis or until clear() is called
    //
    synchronized public void requireClear() throws RuntimeException {
        requireClear(MSG, TIMEOUT);
    }

    synchronized public void requireClear(long timeout)
            throws RuntimeException {
        requireClear(MSG, timeout);
    }

    synchronized public void requireClear(String timeOutMsg)
            throws RuntimeException {
        requireClear(timeOutMsg, TIMEOUT);
    }

    synchronized public void requireClear(String timeOutMsg, long timeout)
            throws RuntimeException {
        long endtime = System.currentTimeMillis() + timeout;
        try {
            while (isClear() == false) {
                if (System.currentTimeMillis() < endtime) {
                    wait(200);
                } else {
                    break;
                }
            }

            if (!isClear()) {
                throw new RuntimeException(timeOutMsg);
            }
        } catch (InterruptedException ix) {
        }
    }

    //
    //    Called when it is valid to procede past the waypoint
    //
    synchronized public void clear() {
        clear = true;
        notify();
    }

    //
    //    Should be checked after a call to requireClear() to make
    //    sure that we did not time out.
    //
    synchronized public boolean isClear() {
        return clear;
    }

    synchronized public boolean isValid() {
        return clear;
    }

    //
    //    For re-use of a waypoint.  Be careful.
    //
    synchronized public void reset() {
        clear = false;
    }

}
