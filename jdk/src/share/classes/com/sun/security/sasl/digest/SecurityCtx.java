/*
 * Copyright 2000-2003 Sun Microsystems, Inc.  All Rights Reserved.
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

package com.sun.security.sasl.digest;

import javax.security.sasl.SaslException;

/**
  * Interface used for classes implementing integrity checking and privacy
  * for DIGEST-MD5 SASL mechanism implementation.
  *
  * @see <a href="http://www.ietf.org/rfc/rfc2831.txt">RFC 2831</a>
  * - Using Digest Authentication as a SASL Mechanism
  *
  * @author Jonathan Bruce
  */

interface SecurityCtx {

    /**
     * Wrap out-going message and return wrapped message
     *
     * @throws SaslException
     */
    byte[] wrap(byte[] dest, int start, int len)
        throws SaslException;

    /**
     * Unwrap incoming message and return original message
     *
     * @throws SaslException
     */
    byte[] unwrap(byte[] outgoing, int start, int len)
        throws SaslException;
}
