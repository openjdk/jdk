/*
 * Copyright 2007-2008 Sun Microsystems, Inc.  All Rights Reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Sun designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Sun in the LICENSE file that accompanied this code.
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

package com.sun.jmx.event;

import com.sun.jmx.remote.util.ClassLogger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import javax.management.remote.NotificationResult;
import javax.management.remote.TargetedNotification;


public class ReceiverBuffer {
    public void addNotifs(NotificationResult nr) {
        if (nr == null) {
            return;
        }

        TargetedNotification[] tns = nr.getTargetedNotifications();

        if (logger.traceOn()) {
            logger.trace("addNotifs", "" + tns.length);
        }

        long impliedStart = nr.getEarliestSequenceNumber();
        final long missed = impliedStart - start;
        start = nr.getNextSequenceNumber();

        if (missed > 0) {
            if (logger.traceOn()) {
                logger.trace("addNotifs",
                        "lost: "+missed);
            }

            lost += missed;
        }

        Collections.addAll(notifList, nr.getTargetedNotifications());
    }

    public TargetedNotification[] removeNotifs() {
        if (logger.traceOn()) {
            logger.trace("removeNotifs", String.valueOf(notifList.size()));
        }

        if (notifList.size() == 0) {
            return null;
        }

        TargetedNotification[] ret = notifList.toArray(
                new TargetedNotification[]{});
        notifList.clear();

        return ret;
    }

    public int size() {
        return notifList.size();
    }

    public int removeLost() {
        int ret = lost;
        lost = 0;
        return ret;
    }

    private List<TargetedNotification> notifList
            = new ArrayList<TargetedNotification>();
    private long start = 0;
    private int lost = 0;

    private static final ClassLogger logger =
            new ClassLogger("javax.management.event", "ReceiverBuffer");
}
