/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
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

package sun.security.ssl;

import java.io.IOException;

/*
 * RFC 6066 defines the TLS extension,"status_request" (type 0x5),
 * which allows the client to request that the server perform OCSP
 * on the client's behalf.
 *
 * This class is an interface for multiple types of StatusRequests
 * (e.g. OCSPStatusRequest).
 */
interface StatusRequest {

    /**
     * Obtain the length of the {@code StatusRequest} object in encoded form
     *
     * @return the length of the {@code StatusRequest} object in encoded form
     */
    int length();

    /**
     * Place the encoded {@code StatusRequest} bytes into the
     *      {@code HandshakeOutputStream}
     *
     * @param s the target {@code HandshakeOutputStream}
     *
     * @throws IOException if any encoding error occurs
     */
    void send(HandshakeOutStream s) throws IOException;
}
