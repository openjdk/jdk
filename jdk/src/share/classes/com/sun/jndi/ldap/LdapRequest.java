/*
 * Copyright (c) 1999, 2011, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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

package com.sun.jndi.ldap;

import java.io.IOException;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import javax.naming.CommunicationException;

final class LdapRequest {

    LdapRequest next;   // Set/read in synchronized Connection methods
    int msgId;          // read-only

    private int gotten = 0;
    private BlockingQueue<BerDecoder> replies;
    private int highWatermark = -1;
    private boolean cancelled = false;
    private boolean pauseAfterReceipt = false;
    private boolean completed = false;

    LdapRequest(int msgId, boolean pause) {
        this(msgId, pause, -1);
    }

    LdapRequest(int msgId, boolean pause, int replyQueueCapacity) {
        this.msgId = msgId;
        this.pauseAfterReceipt = pause;
        if (replyQueueCapacity == -1) {
            this.replies = new LinkedBlockingQueue<BerDecoder>();
        } else {
            this.replies =
                new LinkedBlockingQueue<BerDecoder>(replyQueueCapacity);
            highWatermark = (replyQueueCapacity * 80) / 100; // 80% capacity
        }
    }

    synchronized void cancel() {
        cancelled = true;

        // Unblock reader of pending request
        // Should only ever have atmost one waiter
        notify();
    }

    synchronized boolean addReplyBer(BerDecoder ber) {
        if (cancelled) {
            return false;
        }

        // Add a new reply to the queue of unprocessed replies.
        try {
            replies.put(ber);
        } catch (InterruptedException e) {
            // ignore
        }

        // peek at the BER buffer to check if it is a SearchResultDone PDU
        try {
            ber.parseSeq(null);
            ber.parseInt();
            completed = (ber.peekByte() == LdapClient.LDAP_REP_RESULT);
        } catch (IOException e) {
            // ignore
        }
        ber.reset();

        notify(); // notify anyone waiting for reply
        /*
         * If a queue capacity has been set then trigger a pause when the
         * queue has filled to 80% capacity. Later, when the queue has drained
         * then the reader gets unpaused.
         */
        if (highWatermark != -1 && replies.size() >= highWatermark) {
            return true; // trigger the pause
        }
        return pauseAfterReceipt;
    }

    synchronized BerDecoder getReplyBer() throws CommunicationException {
        if (cancelled) {
            throw new CommunicationException("Request: " + msgId +
                " cancelled");
        }

        /*
         * Remove a reply if the queue is not empty.
         * poll returns null if queue is empty.
         */
        BerDecoder reply = replies.poll();
        return reply;
    }

    synchronized boolean hasSearchCompleted() {
        return completed;
    }
}
