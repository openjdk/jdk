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
import java.util.List;
import java.util.Collections;
import java.util.ArrayList;
import java.util.Objects;
import javax.net.ssl.SSLException;

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

final class CertStatusReqListV2Extension extends HelloExtension {

    private final List<CertStatusReqItemV2> itemList;
    private final int itemListLength;

    /**
     * Construct a default {@code CertStatusReqListV2Extension}.  The default
     * object results in a status_request_v2 extension where the extension
     * data segment is zero-length.  This is used primarily in ServerHello
     * messages where the server asserts it can do RFC 6961 status stapling.
     */
    CertStatusReqListV2Extension() {
        super(ExtensionType.EXT_STATUS_REQUEST_V2);
        itemList = Collections.emptyList();
        itemListLength = 0;
    }

    /**
     * Construct a {@code CertStatusReqListV2Extension} from a provided list
     *      of {@code CertStatusReqItemV2} objects.
     *
     * @param reqList a {@code List} containing one or more
     *      {@code CertStatusReqItemV2} objects to be included in this TLS
     *      Hello extension.  Passing an empty list will result in the encoded
     *      extension having a zero-length extension_data segment, and is
     *      the same as using the default constructor.
     *
     * @throws NullPointerException if reqList is {@code null}
     */
    CertStatusReqListV2Extension(List<CertStatusReqItemV2> reqList) {
        super(ExtensionType.EXT_STATUS_REQUEST_V2);
        Objects.requireNonNull(reqList,
                "Unallowed null value for certificate_status_req_list");
        itemList = Collections.unmodifiableList(new ArrayList<>(reqList));
        itemListLength = calculateListLength();
    }

    /**
     *  Construct the {@code CertStatusReqListV2Extension} object from data
     *      read from a {@code HandshakeInputStream}
     *
     * @param s the {@code HandshakeInputStream} providing the encoded data
     * @param len the length of the extension data
     *
     * @throws IOException if any decoding errors happen during object
     *      construction.
     */
    CertStatusReqListV2Extension(HandshakeInStream s, int len)
            throws IOException {
        super(ExtensionType.EXT_STATUS_REQUEST_V2);

        if (len <= 0) {
            // Handle the empty extension data case (from a ServerHello)
            itemList = Collections.emptyList();
            itemListLength = 0;
        } else {
            List<CertStatusReqItemV2> workingList = new ArrayList<>();

            itemListLength = s.getInt16();
            if (itemListLength <= 0) {
                throw new SSLException("certificate_status_req_list length " +
                        "must be greater than zero (received length: " +
                        itemListLength + ")");
            }

            int totalRead = 0;
            CertStatusReqItemV2 reqItem;
            do {
                reqItem = new CertStatusReqItemV2(s);
                totalRead += reqItem.length();
            } while (workingList.add(reqItem) && totalRead < itemListLength);

            // If for some reason the add returns false, we may not have read
            // all the necessary bytes from the stream.  Check this and throw
            // an exception if we terminated the loop early.
            if (totalRead != itemListLength) {
                throw new SSLException("Not all certificate_status_req_list " +
                        "bytes were read: expected " + itemListLength +
                        ", read " + totalRead);
            }

            itemList = Collections.unmodifiableList(workingList);
        }
    }

    /**
     * Get the list of {@code CertStatusReqItemV2} objects for this extension
     *
     * @return an unmodifiable list of {@code CertStatusReqItemV2} objects
     */
    List<CertStatusReqItemV2> getRequestItems() {
        return itemList;
    }

    /**
     * Return the length of the encoded extension, including extension type
     *      and extension length fields.
     *
     * @return the length in bytes, including the extension type and
     *      extension_data length.
     */
    @Override
    int length() {
        return (itemList.isEmpty() ? 4 : itemListLength + 6);
    }

    /**
     * Send the encoded {@code CertStatusReqListV2Extension} through a
     *      {@code HandshakeOutputStream}
     *
     * @param s the {@code HandshakeOutputStream} used to send the encoded data
     *
     * @throws IOException if any errors occur during the encoding process
     */
    @Override
    void send(HandshakeOutStream s) throws IOException {
        s.putInt16(type.id);
        s.putInt16(this.length() - 4);
        if (itemListLength > 0) {
            s.putInt16(itemListLength);
            for (CertStatusReqItemV2 item : itemList) {
                item.send(s);
            }
        }
    }

    /**
     * Create a string representation of this
     *      {@code CertStatusReqListV2Extension}
     *
     * @return the string representation of this
     *      {@code CertStatusReqListV2Extension}
     */
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("Extension ").append(type);
        for (CertStatusReqItemV2 item : itemList) {
            sb.append("\n").append(item);
        }

        return sb.toString();
    }

    /**
     * Determine the length of the certificate_status_req_list field in
     * the status_request_v2 extension.
     *
     * @return the total encoded length of all items in the list, or 0 if the
     *      encapsulating extension_data is zero-length (from a ServerHello)
     */
    private int calculateListLength() {
        int listLen = 0;

        for (CertStatusReqItemV2 item : itemList) {
            listLen += item.length();
        }

        return listLen;
    }

}
