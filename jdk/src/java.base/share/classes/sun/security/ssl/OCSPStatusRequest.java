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
import java.security.cert.Extension;
import java.util.ArrayList;
import java.util.List;
import java.util.Collections;
import javax.net.ssl.SSLException;
import sun.security.util.DerValue;
import sun.security.util.DerInputStream;
import sun.security.util.DerOutputStream;
import sun.security.provider.certpath.ResponderId;

/*
 * RFC6066 defines the TLS extension,"status_request" (type 0x5),
 * which allows the client to request that the server perform OCSP
 * on the client's behalf.
 *
 * The RFC defines an OCSPStatusRequest structure:
 *
 *      struct {
 *          ResponderID responder_id_list<0..2^16-1>;
 *          Extensions  request_extensions;
 *      } OCSPStatusRequest;
 */
final class OCSPStatusRequest implements StatusRequest {

    private final List<ResponderId> responderIds;
    private final List<Extension> extensions;
    private int encodedLen;
    private int ridListLen;
    private int extListLen;

    /**
     * Construct a default {@code OCSPStatusRequest} object with empty
     * responder ID and code extension list fields.
     */
    OCSPStatusRequest() {
        responderIds = new ArrayList<>();
        extensions = new ArrayList<>();
        encodedLen = this.length();
    }

    /**
     * Construct an {@code OCSPStatusRequest} object using the provided
     *      {@code ResponderId} and {@code Extension} lists.
     *
     * @param respIds the list of {@code ResponderId} objects to be placed
     *      into the {@code OCSPStatusRequest}.  If the user wishes to place
     *      no {@code ResponderId} objects in the request, either an empty
     *      {@code List} or {@code null} is acceptable.
     * @param exts the list of {@code Extension} objects to be placed into
     *      the {@code OCSPStatusRequest}  If the user wishes to place
     *      no {@code Extension} objects in the request, either an empty
     *      {@code List} or {@code null} is acceptable.
     */
    OCSPStatusRequest(List<ResponderId> respIds, List<Extension> exts) {
        responderIds = new ArrayList<>(respIds != null ? respIds :
                Collections.emptyList());
        extensions = new ArrayList<>(exts != null ? exts :
                Collections.emptyList());
        encodedLen = this.length();
    }

    /**
     * Construct an {@code OCSPStatusRequest} object from data read from
     * a {@code HandshakeInputStream}
     *
     * @param s the {@code HandshakeInputStream} providing the encoded data
     *
     * @throws IOException if any decoding errors happen during object
     *      construction.
     */
    OCSPStatusRequest(HandshakeInStream in) throws IOException {
        responderIds = new ArrayList<>();
        extensions = new ArrayList<>();

        int ridListBytesRemaining = in.getInt16();
        while (ridListBytesRemaining != 0) {
            byte[] ridBytes = in.getBytes16();
            responderIds.add(new ResponderId(ridBytes));
            ridListBytesRemaining -= (ridBytes.length + 2);
            // Make sure that no individual responder ID's length caused an
            // overrun relative to the outer responder ID list length
            if (ridListBytesRemaining < 0) {
                throw new SSLException("Responder ID length overflow: " +
                        "current rid = " + ridBytes.length + ", remaining = " +
                        ridListBytesRemaining);
            }
        }

        int extensionLength = in.getInt16();
        if (extensionLength > 0) {
            byte[] extensionData = new byte[extensionLength];
            in.read(extensionData);
            DerInputStream dis = new DerInputStream(extensionData);
            DerValue[] extSeqContents = dis.getSequence(extensionData.length);
            for (DerValue extDerVal : extSeqContents) {
                extensions.add(new sun.security.x509.Extension(extDerVal));
            }
        }
    }

    /**
     * Construct an {@code OCSPStatusRequest} from its encoded form
     *
     * @param requestBytes the status request extension bytes
     *
     * @throws IOException if any error occurs during decoding
     */
    OCSPStatusRequest(byte[] requestBytes) throws IOException {
        responderIds = new ArrayList<>();
        extensions = new ArrayList<>();
        ByteBuffer reqBuf = ByteBuffer.wrap(requestBytes);

        // Get the ResponderId list length
        encodedLen = requestBytes.length;
        ridListLen = Short.toUnsignedInt(reqBuf.getShort());
        int endOfRidList = reqBuf.position() + ridListLen;

        // The end position of the ResponderId list in the ByteBuffer
        // should be at least 2 less than the end of the buffer.  This
        // 2 byte defecit is the minimum length required to encode a
        // zero-length extensions segment.
        if (reqBuf.limit() - endOfRidList < 2) {
            throw new SSLException
                ("ResponderId List length exceeds provided buffer - Len: "
                 + ridListLen + ", Buffer: " + reqBuf.remaining());
        }

        while (reqBuf.position() < endOfRidList) {
            int ridLength = Short.toUnsignedInt(reqBuf.getShort());
            // Make sure an individual ResponderId length doesn't
            // run past the end of the ResponderId list portion of the
            // provided buffer.
            if (reqBuf.position() + ridLength > endOfRidList) {
                throw new SSLException
                    ("ResponderId length exceeds list length - Off: "
                     + reqBuf.position() + ", Length: " + ridLength
                     + ", End offset: " + endOfRidList);
            }

            // Consume/add the ResponderId
            if (ridLength > 0) {
                byte[] ridData = new byte[ridLength];
                reqBuf.get(ridData);
                responderIds.add(new ResponderId(ridData));
            }
        }

        // Get the Extensions length
        int extensionsLen = Short.toUnsignedInt(reqBuf.getShort());

        // The end of the extensions should also be the end of the
        // encoded OCSPStatusRequest
        if (extensionsLen != reqBuf.remaining()) {
            throw new SSLException("Incorrect extensions length: Read "
                    + extensionsLen + ", Data length: " + reqBuf.remaining());
        }

        // Extensions are a SEQUENCE of Extension
        if (extensionsLen > 0) {
            byte[] extensionData = new byte[extensionsLen];
            reqBuf.get(extensionData);
            DerInputStream dis = new DerInputStream(extensionData);
            DerValue[] extSeqContents = dis.getSequence(extensionData.length);
            for (DerValue extDerVal : extSeqContents) {
                extensions.add(new sun.security.x509.Extension(extDerVal));
            }
        }
    }

    /**
     * Obtain the length of the {@code OCSPStatusRequest} object in its
     *      encoded form
     *
     * @return the length of the {@code OCSPStatusRequest} object in its
     *      encoded form
     */
    @Override
    public int length() {
        // If we've previously calculated encodedLen simply return it
        if (encodedLen != 0) {
            return encodedLen;
        }

        ridListLen = 0;
        for (ResponderId rid : responderIds) {
            ridListLen += rid.length() + 2;
        }

        extListLen = 0;
        if (!extensions.isEmpty()) {
            try {
                DerOutputStream extSequence = new DerOutputStream();
                DerOutputStream extEncoding = new DerOutputStream();
                for (Extension ext : extensions) {
                    ext.encode(extEncoding);
                }
                extSequence.write(DerValue.tag_Sequence, extEncoding);
                extListLen = extSequence.size();
            } catch (IOException ioe) {
                // Not sure what to do here
            }
        }

        // Total length is the responder ID list length and extensions length
        // plus each lists' 2-byte length fields.
        encodedLen = ridListLen + extListLen + 4;

        return encodedLen;
    }

    /**
     * Send the encoded {@code OCSPStatusRequest} out through the provided
     *      {@code HandshakeOutputStream}
     *
     * @param s the {@code HandshakeOutputStream} on which to send the encoded
     *      data
     *
     * @throws IOException if any encoding errors occur
     */
    @Override
    public void send(HandshakeOutStream s) throws IOException {
        s.putInt16(ridListLen);
        for (ResponderId rid : responderIds) {
            s.putBytes16(rid.getEncoded());
        }

        DerOutputStream seqOut = new DerOutputStream();
        DerOutputStream extBytes = new DerOutputStream();

        if (extensions.size() > 0) {
            for (Extension ext : extensions) {
                ext.encode(extBytes);
            }
            seqOut.write(DerValue.tag_Sequence, extBytes);
        }
        s.putBytes16(seqOut.toByteArray());
    }

    /**
     * Determine if a provided {@code OCSPStatusRequest} objects is equal to
     *      this one.
     *
     * @param obj an {@code OCSPStatusRequest} object to be compared against
     *
     * @return {@code true} if the objects are equal, {@code false} otherwise.
     *      Equivalence is established if the lists of responder IDs and
     *      extensions between the two objects are also equal.
     */
    @Override
    public boolean equals(Object obj) {
        if (obj == null) {
            return false;
        } else if (this == obj) {
            return true;
        } else if (obj instanceof OCSPStatusRequest) {
            OCSPStatusRequest respObj = (OCSPStatusRequest)obj;
            return responderIds.equals(respObj.getResponderIds()) &&
                extensions.equals(respObj.getExtensions());
        }

        return false;
    }

    /**
     * Returns the hash code value for this {@code OCSPStatusRequest}
     *
     * @return the hash code value for this {@code OCSPStatusRequest}
     */
    @Override
    public int hashCode() {
        int result = 17;

        result = 31 * result + responderIds.hashCode();
        result = 31 * result + extensions.hashCode();

        return result;
    }

    /**
     * Create a string representation of this {@code OCSPStatusRequest}
     *
     * @return a string representation of this {@code OCSPStatusRequest}
     */
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("OCSPStatusRequest\n");
        sb.append("    ResponderIds:");

        if (responderIds.isEmpty()) {
            sb.append(" <EMPTY>");
        } else {
            for (ResponderId rid : responderIds) {
                sb.append("\n    ").append(rid.toString());
            }
        }

        sb.append("\n").append("    Extensions:");
        if (extensions.isEmpty()) {
            sb.append(" <EMPTY>");
        } else {
            for (Extension ext : extensions) {
                sb.append("\n    ").append(ext.toString());
            }
        }

        return sb.toString();
    }

    /**
     * Get the list of {@code ResponderId} objects for this
     *      {@code OCSPStatusRequest}
     *
     * @return an unmodifiable {@code List} of {@code ResponderId} objects
     */
    List<ResponderId> getResponderIds() {
        return Collections.unmodifiableList(responderIds);
    }

    /**
     * Get the list of {@code Extension} objects for this
     *      {@code OCSPStatusRequest}
     *
     * @return an unmodifiable {@code List} of {@code Extension} objects
     */
    List<Extension> getExtensions() {
        return Collections.unmodifiableList(extensions);
    }
}
