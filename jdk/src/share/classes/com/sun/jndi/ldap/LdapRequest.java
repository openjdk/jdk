/*
 * Copyright (c) 1999, 2002, Oracle and/or its affiliates. All rights reserved.
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
import java.util.Vector;
import javax.naming.CommunicationException;

final class LdapRequest {

    LdapRequest next;   // Set/read in synchronized Connection methods
    int msgId;          // read-only

    private int gotten = 0;
    private Vector replies = new Vector(3);
    private boolean cancelled = false;
    private boolean pauseAfterReceipt = false;
    private boolean completed = false;

    LdapRequest(int msgId, boolean pause) {
        this.msgId = msgId;
        this.pauseAfterReceipt = pause;
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
        replies.addElement(ber);

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
        return pauseAfterReceipt;
    }

    synchronized BerDecoder getReplyBer() throws CommunicationException {
        if (cancelled) {
            throw new CommunicationException("Request: " + msgId +
                " cancelled");
        }

        if (gotten < replies.size()) {
            BerDecoder answer = (BerDecoder)replies.elementAt(gotten);
            replies.setElementAt(null, gotten); // remove reference
            ++gotten; // skip to next
            return answer;
        } else {
            return null;
        }
    }

    synchronized boolean hasSearchCompleted() {
        return completed;
    }
}
