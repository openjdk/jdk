/*
 * Copyright (c) 1999, 2003, Oracle and/or its affiliates. All rights reserved.
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

package javax.security.sasl;

/**
 * Performs SASL authentication as a client.
 *<p>
 * A protocol library such as one for LDAP gets an instance of this
 * class in order to perform authentication defined by a specific SASL
 * mechanism. Invoking methods on the <tt>SaslClient</tt> instance
 * process challenges and create responses according to the SASL
 * mechanism implemented by the <tt>SaslClient</tt>.
 * As the authentication proceeds, the instance
 * encapsulates the state of a SASL client's authentication exchange.
 *<p>
 * Here's an example of how an LDAP library might use a <tt>SaslClient</tt>.
 * It first gets an instance of a <tt>SaslClient</tt>:
 *<blockquote><pre>{@code
 * SaslClient sc = Sasl.createSaslClient(mechanisms,
 *     authorizationId, protocol, serverName, props, callbackHandler);
 *}</pre></blockquote>
 * It can then proceed to use the client for authentication.
 * For example, an LDAP library might use the client as follows:
 *<blockquote><pre>{@code
 * // Get initial response and send to server
 * byte[] response = (sc.hasInitialResponse() ? sc.evaluateChallenge(new byte[0]) :
 *     null);
 * LdapResult res = ldap.sendBindRequest(dn, sc.getName(), response);
 * while (!sc.isComplete() &&
 *     (res.status == SASL_BIND_IN_PROGRESS || res.status == SUCCESS)) {
 *     response = sc.evaluateChallenge(res.getBytes());
 *     if (res.status == SUCCESS) {
 *         // we're done; don't expect to send another BIND
 *         if (response != null) {
 *             throw new SaslException(
 *                 "Protocol error: attempting to send response after completion");
 *         }
 *         break;
 *     }
 *     res = ldap.sendBindRequest(dn, sc.getName(), response);
 * }
 * if (sc.isComplete() && res.status == SUCCESS) {
 *    String qop = (String) sc.getNegotiatedProperty(Sasl.QOP);
 *    if (qop != null
 *        && (qop.equalsIgnoreCase("auth-int")
 *            || qop.equalsIgnoreCase("auth-conf"))) {
 *
 *      // Use SaslClient.wrap() and SaslClient.unwrap() for future
 *      // communication with server
 *      ldap.in = new SecureInputStream(sc, ldap.in);
 *      ldap.out = new SecureOutputStream(sc, ldap.out);
 *    }
 * }
 *}</pre></blockquote>
 *
 * If the mechanism has an initial response, the library invokes
 * <tt>evaluateChallenge()</tt> with an empty
 * challenge and to get initial response.
 * Protocols such as IMAP4, which do not include an initial response with
 * their first authentication command to the server, initiates the
 * authentication without first calling <tt>hasInitialResponse()</tt>
 * or <tt>evaluateChallenge()</tt>.
 * When the server responds to the command, it sends an initial challenge.
 * For a SASL mechanism in which the client sends data first, the server should
 * have issued a challenge with no data. This will then result in a call
 * (on the client) to <tt>evaluateChallenge()</tt> with an empty challenge.
 *
 * @since 1.5
 *
 * @see Sasl
 * @see SaslClientFactory
 *
 * @author Rosanna Lee
 * @author Rob Weltman
 */
public abstract interface SaslClient {

    /**
     * Returns the IANA-registered mechanism name of this SASL client.
     * (e.g. "CRAM-MD5", "GSSAPI").
     * @return A non-null string representing the IANA-registered mechanism name.
     */
    public abstract String getMechanismName();

    /**
     * Determines whether this mechanism has an optional initial response.
     * If true, caller should call <tt>evaluateChallenge()</tt> with an
     * empty array to get the initial response.
     *
     * @return true if this mechanism has an initial response.
     */
    public abstract boolean hasInitialResponse();

    /**
     * Evaluates the challenge data and generates a response.
     * If a challenge is received from the server during the authentication
     * process, this method is called to prepare an appropriate next
     * response to submit to the server.
     *
     * @param challenge The non-null challenge sent from the server.
     * The challenge array may have zero length.
     *
     * @return The possibly null reponse to send to the server.
     * It is null if the challenge accompanied a "SUCCESS" status and the challenge
     * only contains data for the client to update its state and no response
     * needs to be sent to the server. The response is a zero-length byte
     * array if the client is to send a response with no data.
     * @exception SaslException If an error occurred while processing
     * the challenge or generating a response.
     */
    public abstract byte[] evaluateChallenge(byte[] challenge)
        throws SaslException;

    /**
      * Determines whether the authentication exchange has completed.
      * This method may be called at any time, but typically, it
      * will not be called until the caller has received indication
      * from the server
      * (in a protocol-specific manner) that the exchange has completed.
      *
      * @return true if the authentication exchange has completed; false otherwise.
      */
    public abstract boolean isComplete();

    /**
     * Unwraps a byte array received from the server.
     * This method can be called only after the authentication exchange has
     * completed (i.e., when <tt>isComplete()</tt> returns true) and only if
     * the authentication exchange has negotiated integrity and/or privacy
     * as the quality of protection; otherwise, an
     * <tt>IllegalStateException</tt> is thrown.
     *<p>
     * <tt>incoming</tt> is the contents of the SASL buffer as defined in RFC 2222
     * without the leading four octet field that represents the length.
     * <tt>offset</tt> and <tt>len</tt> specify the portion of <tt>incoming</tt>
     * to use.
     *
     * @param incoming A non-null byte array containing the encoded bytes
     *                from the server.
     * @param offset The starting position at <tt>incoming</tt> of the bytes to use.
     * @param len The number of bytes from <tt>incoming</tt> to use.
     * @return A non-null byte array containing the decoded bytes.
     * @exception SaslException if <tt>incoming</tt> cannot be successfully
     * unwrapped.
     * @exception IllegalStateException if the authentication exchange has
     * not completed, or  if the negotiated quality of protection
     * has neither integrity nor privacy.
     */
    public abstract byte[] unwrap(byte[] incoming, int offset, int len)
        throws SaslException;

    /**
     * Wraps a byte array to be sent to the server.
     * This method can be called only after the authentication exchange has
     * completed (i.e., when <tt>isComplete()</tt> returns true) and only if
     * the authentication exchange has negotiated integrity and/or privacy
     * as the quality of protection; otherwise, an
     * <tt>IllegalStateException</tt> is thrown.
     *<p>
     * The result of this method will make up the contents of the SASL buffer
     * as defined in RFC 2222 without the leading four octet field that
     * represents the length.
     * <tt>offset</tt> and <tt>len</tt> specify the portion of <tt>outgoing</tt>
     * to use.
     *
     * @param outgoing A non-null byte array containing the bytes to encode.
     * @param offset The starting position at <tt>outgoing</tt> of the bytes to use.
     * @param len The number of bytes from <tt>outgoing</tt> to use.
     * @return A non-null byte array containing the encoded bytes.
     * @exception SaslException if <tt>outgoing</tt> cannot be successfully
     * wrapped.
     * @exception IllegalStateException if the authentication exchange has
     * not completed, or if the negotiated quality of protection
     * has neither integrity nor privacy.
     */
    public abstract byte[] wrap(byte[] outgoing, int offset, int len)
        throws SaslException;

    /**
     * Retrieves the negotiated property.
     * This method can be called only after the authentication exchange has
     * completed (i.e., when <tt>isComplete()</tt> returns true); otherwise, an
     * <tt>IllegalStateException</tt> is thrown.
     *
     * @param propName The non-null property name.
     * @return The value of the negotiated property. If null, the property was
     * not negotiated or is not applicable to this mechanism.
     * @exception IllegalStateException if this authentication exchange
     * has not completed
     */

    public abstract Object getNegotiatedProperty(String propName);

     /**
      * Disposes of any system resources or security-sensitive information
      * the SaslClient might be using. Invoking this method invalidates
      * the SaslClient instance. This method is idempotent.
      * @throws SaslException If a problem was encountered while disposing
      * the resources.
      */
    public abstract void dispose() throws SaslException;
}
