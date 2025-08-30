/*
 * Copyright (c) 1999, 2025, Oracle and/or its affiliates. All rights reserved.
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
import java.util.concurrent.TimeUnit;

final class LdapRequest {

    private static final BerDecoder CLOSED_MARKER = new BerDecoder(new byte[]{}, -1, 0);
    private static final BerDecoder CANCELLED_MARKER = new BerDecoder(new byte[]{}, -1, 0);
    private static final String CLOSE_MSG = "LDAP connection has been closed";
    private static final String TIMEOUT_MSG_FMT = "LDAP response read timed out, timeout used: %d ms.";

    LdapRequest next;   // Set/read in synchronized Connection methods
    final int msgId;          // read-only

    private final BlockingQueue<BerDecoder> replies;
    private final boolean pauseAfterReceipt;

    private volatile boolean cancelled;
    private volatile boolean closed;
    private volatile boolean completed;

    LdapRequest(int msgId, boolean pause, int replyQueueCapacity) {
        this.msgId = msgId;
        this.pauseAfterReceipt = pause;
        if (replyQueueCapacity == -1) {
            this.replies = new LinkedBlockingQueue<>();
        } else {
            this.replies = new LinkedBlockingQueue<>(8 * replyQueueCapacity / 10);
        }
    }

    void cancel() {
        cancelled = true;
        replies.offer(CANCELLED_MARKER);
    }

    void close() {
        closed = true;
        replies.offer(CLOSED_MARKER);
    }

    boolean addReplyBer(BerDecoder ber) {
        // check if the request is closed or cancelled, if yes then don't
        // add the reply since it won't be returned back later through getReplyBer().
        // this is merely a best effort basis check and if we do add the reply
        // due to a race, that's OK since the replies queue would have necessary
        // markers for cancelled/closed state and those will be detected by getReplyBer().
        if (cancelled || closed) {
            return false;
        }
        // if the request is not already completed, check if the reply being added
        // is a LDAP_REP_RESULT, representing a SearchResultDone PDU
        if (!completed) {
            boolean isLdapResResult = false;
            try {
                ber.parseSeq(null);
                ber.parseInt();
                isLdapResResult = (ber.peekByte() == LdapClient.LDAP_REP_RESULT);
            } catch (IOException e) {
                // ignore
            }
            ber.reset();

            if (isLdapResResult) {
                completed = true;
            }
        }

        // Add a new reply to the queue of unprocessed replies.
        try {
            replies.put(ber);
        } catch (InterruptedException e) {
            // ignore
        }
        return pauseAfterReceipt;
    }

    /**
     * Read reply BER
     * @param millis timeout, infinite if the value is negative
     * @return BerDecoder if reply was read successfully
     * @throws CommunicationException request has been canceled and request
     *                                does not need to be abandoned (i.e. a LDAP_REQ_ABANDON
     *                                message need not be sent across)
     * @throws IOException            request has been closed or timed out.
     *                                Request needs to be abandoned (i.e. a LDAP_REQ_ABANDON
     *                                message needs to be sent across)
     * @throws InterruptedException   the wait to read a reply has been interrupted
     */
    // more than one thread invoking this method concurrently isn't expected
    BerDecoder getReplyBer(long millis) throws IOException, CommunicationException,
                                               InterruptedException {

        final boolean hasReplies = replies.peek() != null;
        if (!hasReplies) {
            // no replies have been queued, so if the request has
            // been cancelled or closed, then raise an exception
            if (cancelled) {
                throw new CommunicationException("Request: " + msgId +
                        " cancelled");
            }
            if (closed) {
                throw new IOException(CLOSE_MSG);
            }
        }
        // either there already are queued replies or the request is still
        // alive (i.e. not cancelled or closed). we wait for a reply to arrive
        // or the request to be cancelled/closed, in which case the replies
        // queue will contain the relevant marker.
        final BerDecoder result = millis > 0
                ? replies.poll(millis, TimeUnit.MILLISECONDS)
                : replies.take();
        // poll from 'replies' blocking queue ended-up with timeout
        if (result == null) {
            throw new IOException(String.format(TIMEOUT_MSG_FMT, millis));
        }
        if (result == CANCELLED_MARKER) {
            throw new CommunicationException("Request: " + msgId +
                " cancelled");
        }
        if (result == CLOSED_MARKER) {
            throw new IOException(CLOSE_MSG);
        }
        return result;
    }

    boolean hasSearchCompleted() {
        return completed;
    }
}
