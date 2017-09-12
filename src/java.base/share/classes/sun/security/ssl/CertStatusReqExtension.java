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
import java.util.Objects;

/*
 * RFC6066 defines the TLS extension,"status_request" (type 0x5),
 * which allows the client to request that the server perform OCSP
 * on the client's behalf.
 * The "extension data" field of this extension contains a
 * "CertificateStatusRequest" structure:
 *
 *      struct {
 *          CertificateStatusType status_type;
 *          select (status_type) {
 *              case ocsp: OCSPStatusRequest;
 *          } request;
 *      } CertificateStatusRequest;
 *
 *      enum { ocsp(1), (255) } CertificateStatusType;
 *
 *      struct {
 *          ResponderID responder_id_list<0..2^16-1>;
 *          Extensions  request_extensions;
 *      } OCSPStatusRequest;
 *
 *      opaque ResponderID<1..2^16-1>;
 *      opaque Extensions<0..2^16-1>;
 */

final class CertStatusReqExtension extends HelloExtension {

    private final StatusRequestType statReqType;
    private final StatusRequest request;


    /**
     * Construct the default status request extension object.  The default
     * object results in a status_request extension where the extension
     * data segment is zero-length.  This is used primarily in ServerHello
     * messages where the server asserts it can do RFC 6066 status stapling.
     */
    CertStatusReqExtension() {
        super(ExtensionType.EXT_STATUS_REQUEST);
        statReqType = null;
        request = null;
    }

    /**
     * Construct the status request extension object given a request type
     *      and {@code StatusRequest} object.
     *
     * @param reqType a {@code StatusRequestExtType object correspoding
     *      to the underlying {@code StatusRequest} object.  A value of
     *      {@code null} is not allowed.
     * @param statReq the {@code StatusRequest} object used to provide the
     *      encoding for the TLS extension.  A value of {@code null} is not
     *      allowed.
     *
     * @throws IllegalArgumentException if the provided {@code StatusRequest}
     *      does not match the type.
     * @throws NullPointerException if either the {@code reqType} or
     *      {@code statReq} arguments are {@code null}.
     */
    CertStatusReqExtension(StatusRequestType reqType, StatusRequest statReq) {
        super(ExtensionType.EXT_STATUS_REQUEST);

        statReqType = Objects.requireNonNull(reqType,
                "Unallowed null value for status_type");
        request = Objects.requireNonNull(statReq,
                "Unallowed null value for request");

        // There is currently only one known status type (OCSP)
        // We can add more clauses to cover other types in the future
        if (statReqType == StatusRequestType.OCSP) {
            if (!(statReq instanceof OCSPStatusRequest)) {
                throw new IllegalArgumentException("StatusRequest not " +
                        "of type OCSPStatusRequest");
            }
        }
    }

    /**
     * Construct the {@code CertStatusReqExtension} object from data read from
     *      a {@code HandshakeInputStream}
     *
     * @param s the {@code HandshakeInputStream} providing the encoded data
     * @param len the length of the extension data
     *
     * @throws IOException if any decoding errors happen during object
     *      construction.
     */
    CertStatusReqExtension(HandshakeInStream s, int len) throws IOException {
        super(ExtensionType.EXT_STATUS_REQUEST);

        if (len > 0) {
            // Obtain the status type (first byte)
            statReqType = StatusRequestType.get(s.getInt8());
            if (statReqType == StatusRequestType.OCSP) {
                request = new OCSPStatusRequest(s);
            } else {
                // This is a status_type we don't understand.  Create
                // an UnknownStatusRequest in order to preserve the data
                request = new UnknownStatusRequest(s, len - 1);
            }
        } else {
            // Treat this as a zero-length extension (i.e. from a ServerHello
            statReqType = null;
            request = null;
        }
    }

    /**
     * Return the length of the encoded extension, including extension type,
     *      extension length and status_type fields.
     *
     * @return the length in bytes, including the extension type and
     *      length fields.
     */
    @Override
    int length() {
        return (statReqType != null ? 5 + request.length() : 4);
    }

    /**
     * Send the encoded TLS extension through a {@code HandshakeOutputStream}
     *
     * @param s the {@code HandshakeOutputStream} used to send the encoded data
     *
     * @throws IOException tf any errors occur during the encoding process
     */
    @Override
    void send(HandshakeOutStream s) throws IOException {
        s.putInt16(type.id);
        s.putInt16(this.length() - 4);

        if (statReqType != null) {
            s.putInt8(statReqType.id);
            request.send(s);
        }
    }

    /**
     * Create a string representation of this {@code CertStatusReqExtension}
     *
     * @return the string representation of this {@code CertStatusReqExtension}
     */
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("Extension ").append(type);
        if (statReqType != null) {
            sb.append(": ").append(statReqType).append(", ").append(request);
        }

        return sb.toString();
    }

    /**
     * Return the type field for this {@code CertStatusReqExtension}
     *
     * @return the {@code StatusRequestType} for this extension.  {@code null}
     *      will be returned if the default constructor is used to create
     *      a zero length status_request extension (found in ServerHello
     *      messages)
     */
    StatusRequestType getType() {
        return statReqType;
    }

    /**
     * Get the underlying {@code StatusRequest} for this
     *      {@code CertStatusReqExtension}
     *
     * @return the {@code StatusRequest} or {@code null} if the default
     * constructor was used to create this extension.
     */
    StatusRequest getRequest() {
        return request;
    }
}
