/*
 * Copyright 1999-2003 Sun Microsystems, Inc.  All Rights Reserved.
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

package com.sun.security.sasl;

import javax.security.sasl.*;

/**
  * Implements the EXTERNAL SASL client mechanism.
  * (<A HREF="ftp://ftp.isi.edu/in-notes/rfc2222.txt">RFC 2222</A>).
  * The EXTERNAL mechanism returns the optional authorization ID as
  * the initial response. It processes no challenges.
  *
  * @author Rosanna Lee
  */
final class ExternalClient implements SaslClient {
    private byte[] username;
    private boolean completed = false;

    /**
     * Constructs an External mechanism with optional authorization ID.
     *
     * @param authorizationID If non-null, used to specify authorization ID.
     * @throws SaslException if cannot convert authorizationID into UTF-8
     *     representation.
     */
    ExternalClient(String authorizationID) throws SaslException {
        if (authorizationID != null) {
            try {
                username = authorizationID.getBytes("UTF8");
            } catch (java.io.UnsupportedEncodingException e) {
                throw new SaslException("Cannot convert " + authorizationID +
                    " into UTF-8", e);
            }
        } else {
            username = new byte[0];
        }
    }

    /**
     * Retrieves this mechanism's name for initiating the "EXTERNAL" protocol
     * exchange.
     *
     * @return  The string "EXTERNAL".
     */
    public String getMechanismName() {
        return "EXTERNAL";
    }

    /**
     * This mechanism has an initial response.
     */
    public boolean hasInitialResponse() {
        return true;
    }

    public void dispose() throws SaslException {
    }

    /**
     * Processes the challenge data.
     * It returns the EXTERNAL mechanism's initial response,
     * which is the authorization id encoded in UTF-8.
     * This is the optional information that is sent along with the SASL command.
     * After this method is called, isComplete() returns true.
     *
     * @param challengeData Ignored.
     * @return The possible empty initial response.
     * @throws SaslException If authentication has already been called.
     */
    public byte[] evaluateChallenge(byte[] challengeData)
        throws SaslException {
        if (completed) {
            throw new IllegalStateException(
                "EXTERNAL authentication already completed");
        }
        completed = true;
        return username;
    }

    /**
     * Returns whether this mechanism is complete.
     * @return true if initial response has been sent; false otherwise.
     */
    public boolean isComplete() {
        return completed;
    }

    /**
      * Unwraps the incoming buffer.
      *
      * @throws SaslException Not applicable to this mechanism.
      */
    public byte[] unwrap(byte[] incoming, int offset, int len)
        throws SaslException {
        if (completed) {
            throw new SaslException("EXTERNAL has no supported QOP");
        } else {
            throw new IllegalStateException(
                "EXTERNAL authentication Not completed");
        }
    }

    /**
      * Wraps the outgoing buffer.
      *
      * @throws SaslException Not applicable to this mechanism.
      */
    public byte[] wrap(byte[] outgoing, int offset, int len)
        throws SaslException {
        if (completed) {
            throw new SaslException("EXTERNAL has no supported QOP");
        } else {
            throw new IllegalStateException(
                "EXTERNAL authentication not completed");
        }
    }

    /**
     * Retrieves the negotiated property.
     * This method can be called only after the authentication exchange has
     * completed (i.e., when <tt>isComplete()</tt> returns true); otherwise, a
     * <tt>IllegalStateException</tt> is thrown.
     *
     * @return null No property is applicable to this mechanism.
     * @exception IllegalStateException if this authentication exchange
     * has not completed
     */
    public Object getNegotiatedProperty(String propName) {
        if (completed) {
            return null;
        } else {
            throw new IllegalStateException(
                "EXTERNAL authentication not completed");
        }
    }
}
