/*
 * Copyright 2005 Sun Microsystems, Inc.  All Rights Reserved.
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
 * @bug 6336980
 * @summary test 2 new methods: isListenedTo() and clear()
 * @author Shanliang JIANG
 * @run clean SupportClearTest
 * @run build SupportClearTest
 * @run main SupportClearTest
 */

import javax.management.*;

public class SupportClearTest {
    private static boolean received = false;

    public static void main(String[] args) throws Exception {
        System.out.println(">>> test 2 new methods: isListenedTo() and clear().");

        final NotificationListener listener = new NotificationListener() {
                public void handleNotification(Notification n, Object hb) {
                    received = true;
                }
            };

        final NotificationBroadcasterSupport broadcaster =
                new NotificationBroadcasterSupport();

        System.out.println(">>> testing the method \"isListenedTo\"...");
        if (broadcaster.isListenedTo()) {
            throw new RuntimeException(
                    "Bad implementation of the method \"isListenedTo\"!");
        }

        broadcaster.addNotificationListener(listener, null, null);

        if (!broadcaster.isListenedTo()) {
            throw new RuntimeException(
                    "Bad implementation of the method \"isListenedTo\"!");
        }

        System.out.println(">>> testing the method \"clear\"...");
        broadcaster.removeAllNotificationListeners();
        if (broadcaster.isListenedTo()) {
            throw new RuntimeException(
                    "Bad implementation of the method \"clear\"!");
        }

        broadcaster.sendNotification(new Notification("", "", 1L));

        if (received) {
            throw new RuntimeException(
                    "Bad implementation of the method \"clear\"!");
        }

        System.out.println(">>> PASSED!");
    }
}
