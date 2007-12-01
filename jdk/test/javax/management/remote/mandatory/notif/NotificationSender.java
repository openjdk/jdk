/*
 * Copyright 2003 Sun Microsystems, Inc.  All Rights Reserved.
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
 * build        @BUILD_TAG_PLACEHOLDER@
 *
 * @COPYRIGHT_MINI_LEGAL_NOTICE_PLACEHOLDER@
 */

import javax.management.ListenerNotFoundException;
import javax.management.Notification;
import javax.management.NotificationBroadcasterSupport;
import javax.management.NotificationFilter;
import javax.management.NotificationListener;

public class NotificationSender
        extends NotificationBroadcasterSupport
        implements NotificationSenderMBean {

    public void sendNotifs(String type, int count) {
        for (int i = 0; i < count; i++) {
            Notification n = new Notification(type, this, newSeqNo());
            sendNotification(n);
        }
    }

    public int getListenerCount() {
        return listenerCount;
    }

    public void addNotificationListener(NotificationListener l,
                                        NotificationFilter f,
                                        Object h) {
        super.addNotificationListener(l, f, h);
        listenerCount++;
    }

    public void removeNotificationListener(NotificationListener l)
            throws ListenerNotFoundException {
        super.removeNotificationListener(l);
        listenerCount--;
    }

    public void removeNotificationListener(NotificationListener l,
                                           NotificationFilter f,
                                           Object h)
            throws ListenerNotFoundException {
        super.removeNotificationListener(l, f, h);
        listenerCount--;
    }

    private static long newSeqNo() {
        return ++seqNo;
    }

    private static long seqNo = 0;
    private int listenerCount = 0;
}
