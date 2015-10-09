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
import java.nio.ByteBuffer;
import java.util.Objects;
import javax.net.ssl.SSLException;

/*
 * RFC6961 defines the TLS extension,"status_request_v2" (type 0x5),
 * which allows the client to request that the server perform OCSP
 * on the client's behalf.
 *
 * The RFC defines an CertStatusReqItemV2 structure:
 *
 *      struct {
 *          CertificateStatusType status_type;
 *          uint16 request_length;
 *          select (status_type) {
 *              case ocsp: OCSPStatusRequest;
 *              case ocsp_multi: OCSPStatusRequest;
 *          } request;
 *      } CertificateStatusRequestItemV2;
 *
 *      enum { ocsp(1), ocsp_multi(2), (255) } CertificateStatusType;
 */

final class CertStatusReqItemV2 {

    private final StatusRequestType statReqType;
    private final StatusRequest request;

    /**
     * Construct a {@code CertStatusReqItemV2} object using a type value
     *      and empty ResponderId and Extension lists.
     *
     * @param reqType the type of request (e.g. ocsp).  A {@code null} value
     *      is not allowed.
     * @param statReq the {@code StatusRequest} object used to provide the
     *      encoding for this {@code CertStatusReqItemV2}.  A {@code null}
     *      value is not allowed.
     *
     * @throws IllegalArgumentException if the provided {@code StatusRequest}
     *      does not match the type.
     * @throws NullPointerException if either the reqType or statReq arguments
     *      are {@code null}.
     */
    CertStatusReqItemV2(StatusRequestType reqType, StatusRequest statReq) {
        statReqType = Objects.requireNonNull(reqType,
                "Unallowed null value for status_type");
        request = Objects.requireNonNull(statReq,
                "Unallowed null value for request");

        // There is currently only one known status type (OCSP)
        // We can add more clauses to cover other types in the future
        if (statReqType.equals(StatusRequestType.OCSP) ||
                statReqType.equals(StatusRequestType.OCSP_MULTI)) {
            if (!(statReq instanceof OCSPStatusRequest)) {
                throw new IllegalArgumentException("StatusRequest not " +
                        "of type OCSPStatusRequest");
            }
        }
    }

    /**
     * Construct a {@code CertStatusReqItemV2} object from encoded bytes
     *
     * @param requestBytes the encoded bytes for the {@code CertStatusReqItemV2}
     *
     * @throws IOException if any decoding errors take place
     * @throws IllegalArgumentException if the parsed reqType value is not a
     *      supported status request type.
     */
    CertStatusReqItemV2(byte[] reqItemBytes) throws IOException {
        ByteBuffer reqBuf = ByteBuffer.wrap(reqItemBytes);
        statReqType = StatusRequestType.get(reqBuf.get());
        int requestLength = Short.toUnsignedInt(reqBuf.getShort());

        if (requestLength == reqBuf.remaining()) {
            byte[] statReqBytes = new byte[requestLength];
            reqBuf.get(statReqBytes);
            if (statReqType == StatusRequestType.OCSP ||
                    statReqType == StatusRequestType.OCSP_MULTI) {
                request = new OCSPStatusRequest(statReqBytes);
            } else {
                request = new UnknownStatusRequest(statReqBytes);
            }
        } else {
            throw new SSLException("Incorrect request_length: " +
                    "Expected " + reqBuf.remaining() + ", got " +
                    requestLength);
        }
    }

    /**
     * Construct an {@code CertStatusReqItemV2} object from data read from
     * a {@code HandshakeInputStream}
     *
     * @param s the {@code HandshakeInputStream} providing the encoded data
     *
     * @throws IOException if any decoding errors happen during object
     *      construction.
     * @throws IllegalArgumentException if the parsed reqType value is not a
     *      supported status request type.
     */
    CertStatusReqItemV2(HandshakeInStream in) throws IOException {
        statReqType = StatusRequestType.get(in.getInt8());
        int requestLength = in.getInt16();

        if (statReqType == StatusRequestType.OCSP ||
                statReqType == StatusRequestType.OCSP_MULTI) {
            request = new OCSPStatusRequest(in);
        } else {
            request = new UnknownStatusRequest(in, requestLength);
        }
    }

    /**
     * Return the length of this {@code CertStatusReqItemV2} in its encoded form
     *
     * @return the encoded length of this {@code CertStatusReqItemV2}
     */
    int length() {
        // The length is the the status type (1 byte) + the request length
        // field (2 bytes) + the StatusRequest data length.
        return request.length() + 3;
    }

    /**
     * Send the encoded {@code CertStatusReqItemV2} through a
     *      {@code HandshakeOutputStream}
     *
     * @param s the {@code HandshakeOutputStream} used to send the encoded data
     *
     * @throws IOException if any errors occur during the encoding process
     */
    void send(HandshakeOutStream s) throws IOException {
        s.putInt8(statReqType.id);
        s.putInt16(request.length());
        request.send(s);
    }

    /**
     * Create a string representation of this {@code CertStatusReqItemV2}
     *
     * @return the string representation of this {@code CertStatusReqItemV2}
     */
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("CertStatusReqItemV2: ").append(statReqType).append(", ");
        sb.append(request.toString());

        return sb.toString();
    }

    /**
     * Return the type field for this {@code CertStatusReqItemV2}
     *
     * @return the {@code StatusRequestType} for this extension.
     */
    StatusRequestType getType() {
        return statReqType;
    }

    /**
     * Get the underlying {@code StatusRequest} for this
     *      {@code CertStatusReqItemV2}
     *
     * @return the {@code StatusRequest}
     */
    StatusRequest getRequest() {
        return request;
    }
}
