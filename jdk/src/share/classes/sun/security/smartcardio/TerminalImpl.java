/*
 * Copyright 2005-2006 Sun Microsystems, Inc.  All Rights Reserved.
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

package sun.security.smartcardio;

import java.util.*;

import javax.smartcardio.*;

import static sun.security.smartcardio.PCSC.*;

/**
 * CardTerminal implementation.
 *
 * @since   1.6
 * @author  Andreas Sterbenz
 */
final class TerminalImpl extends CardTerminal {

    // native SCARDCONTEXT
    final long contextId;

    // the name of this terminal (native PC/SC name)
    final String name;

    private CardImpl card;

    TerminalImpl(long contextId, String name) {
        this.contextId = contextId;
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public synchronized Card connect(String protocol) throws CardException {
        SecurityManager sm = System.getSecurityManager();
        if (sm != null) {
            sm.checkPermission(new CardPermission(name, "connect"));
        }
        if (card != null) {
            if (card.isValid()) {
                String cardProto = card.getProtocol();
                if (protocol.equals("*") || protocol.equalsIgnoreCase(cardProto)) {
                    return card;
                } else {
                    throw new CardException("Cannot connect using " + protocol
                        + ", connection already established using " + cardProto);
                }
            } else {
                card = null;
            }
        }
        try {
            card =  new CardImpl(this, protocol);
            return card;
        } catch (PCSCException e) {
            if (e.code == SCARD_W_REMOVED_CARD) {
                throw new CardNotPresentException("No card present", e);
            } else {
                throw new CardException("connect() failed", e);
            }
        }
    }

    public boolean isCardPresent() throws CardException {
        try {
            int[] status = SCardGetStatusChange(contextId, 0,
                    new int[] {SCARD_STATE_UNAWARE}, new String[] {name});
            return (status[0] & SCARD_STATE_PRESENT) != 0;
        } catch (PCSCException e) {
            throw new CardException("isCardPresent() failed", e);
        }
    }

    private boolean waitForCard(boolean wantPresent, long timeout) throws CardException {
        if (timeout < 0) {
            throw new IllegalArgumentException("timeout must not be negative");
        }
        if (timeout == 0) {
            timeout = TIMEOUT_INFINITE;
        }
        int[] status = new int[] {SCARD_STATE_UNAWARE};
        String[] readers = new String[] {name};
        try {
            // check if card status already matches
            status = SCardGetStatusChange(contextId, 0, status, readers);
            boolean present = (status[0] & SCARD_STATE_PRESENT) != 0;
            if (wantPresent == present) {
                return true;
            }
            // no match, wait
            status = SCardGetStatusChange(contextId, timeout, status, readers);
            present = (status[0] & SCARD_STATE_PRESENT) != 0;
            // should never happen
            if (wantPresent != present) {
                throw new CardException("wait mismatch");
            }
            return true;
        } catch (PCSCException e) {
            if (e.code == SCARD_E_TIMEOUT) {
                return false;
            } else {
                throw new CardException("waitForCard() failed", e);
            }
        }
    }

    public boolean waitForCardPresent(long timeout) throws CardException {
        return waitForCard(true, timeout);
    }

    public boolean waitForCardAbsent(long timeout) throws CardException {
        return waitForCard(false, timeout);
    }

    public String toString() {
        return "PC/SC terminal " + name;
    }
}
