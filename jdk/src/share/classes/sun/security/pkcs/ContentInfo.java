/*
 * Copyright (c) 1996, 2011, Oracle and/or its affiliates. All rights reserved.
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

package sun.security.pkcs;

import java.io.*;

import sun.security.util.*;

/**
 * A ContentInfo type, as defined in PKCS#7.
 *
 * @author Benjamin Renaud
 */

public class ContentInfo {

    // pkcs7 pre-defined content types
    private static int[]  pkcs7 = {1, 2, 840, 113549, 1, 7};
    private static int[]   data = {1, 2, 840, 113549, 1, 7, 1};
    private static int[]  sdata = {1, 2, 840, 113549, 1, 7, 2};
    private static int[]  edata = {1, 2, 840, 113549, 1, 7, 3};
    private static int[] sedata = {1, 2, 840, 113549, 1, 7, 4};
    private static int[]  ddata = {1, 2, 840, 113549, 1, 7, 5};
    private static int[] crdata = {1, 2, 840, 113549, 1, 7, 6};
    private static int[] nsdata = {2, 16, 840, 1, 113730, 2, 5};
    // timestamp token (id-ct-TSTInfo) from RFC 3161
    private static int[] tstInfo = {1, 2, 840, 113549, 1, 9, 16, 1, 4};
    // this is for backwards-compatibility with JDK 1.1.x
    private static final int[] OLD_SDATA = {1, 2, 840, 1113549, 1, 7, 2};
    private static final int[] OLD_DATA = {1, 2, 840, 1113549, 1, 7, 1};
    public static ObjectIdentifier PKCS7_OID;
    public static ObjectIdentifier DATA_OID;
    public static ObjectIdentifier SIGNED_DATA_OID;
    public static ObjectIdentifier ENVELOPED_DATA_OID;
    public static ObjectIdentifier SIGNED_AND_ENVELOPED_DATA_OID;
    public static ObjectIdentifier DIGESTED_DATA_OID;
    public static ObjectIdentifier ENCRYPTED_DATA_OID;
    public static ObjectIdentifier OLD_SIGNED_DATA_OID;
    public static ObjectIdentifier OLD_DATA_OID;
    public static ObjectIdentifier NETSCAPE_CERT_SEQUENCE_OID;
    public static ObjectIdentifier TIMESTAMP_TOKEN_INFO_OID;

    static {
        PKCS7_OID =  ObjectIdentifier.newInternal(pkcs7);
        DATA_OID = ObjectIdentifier.newInternal(data);
        SIGNED_DATA_OID = ObjectIdentifier.newInternal(sdata);
        ENVELOPED_DATA_OID = ObjectIdentifier.newInternal(edata);
        SIGNED_AND_ENVELOPED_DATA_OID = ObjectIdentifier.newInternal(sedata);
        DIGESTED_DATA_OID = ObjectIdentifier.newInternal(ddata);
        ENCRYPTED_DATA_OID = ObjectIdentifier.newInternal(crdata);
        OLD_SIGNED_DATA_OID = ObjectIdentifier.newInternal(OLD_SDATA);
        OLD_DATA_OID = ObjectIdentifier.newInternal(OLD_DATA);
        /**
         * The ASN.1 systax for the Netscape Certificate Sequence
         * data type is defined
         * <a href=http://wp.netscape.com/eng/security/comm4-cert-download.html>
         * here.</a>
         */
        NETSCAPE_CERT_SEQUENCE_OID = ObjectIdentifier.newInternal(nsdata);
        TIMESTAMP_TOKEN_INFO_OID = ObjectIdentifier.newInternal(tstInfo);
    }

    ObjectIdentifier contentType;
    DerValue content; // OPTIONAL

    public ContentInfo(ObjectIdentifier contentType, DerValue content) {
        this.contentType = contentType;
        this.content = content;
    }

    /**
     * Make a contentInfo of type data.
     */
    public ContentInfo(byte[] bytes) {
        DerValue octetString = new DerValue(DerValue.tag_OctetString, bytes);
        this.contentType = DATA_OID;
        this.content = octetString;
    }

    /**
     * Parses a PKCS#7 content info.
     */
    public ContentInfo(DerInputStream derin)
        throws IOException, ParsingException
    {
        this(derin, false);
    }

    /**
     * Parses a PKCS#7 content info.
     *
     * <p>This constructor is used only for backwards compatibility with
     * PKCS#7 blocks that were generated using JDK1.1.x.
     *
     * @param derin the ASN.1 encoding of the content info.
     * @param oldStyle flag indicating whether or not the given content info
     * is encoded according to JDK1.1.x.
     */
    public ContentInfo(DerInputStream derin, boolean oldStyle)
        throws IOException, ParsingException
    {
        DerInputStream disType;
        DerInputStream disTaggedContent;
        DerValue type;
        DerValue taggedContent;
        DerValue[] typeAndContent;
        DerValue[] contents;

        typeAndContent = derin.getSequence(2);

        // Parse the content type
        type = typeAndContent[0];
        disType = new DerInputStream(type.toByteArray());
        contentType = disType.getOID();

        if (oldStyle) {
            // JDK1.1.x-style encoding
            content = typeAndContent[1];
        } else {
            // This is the correct, standards-compliant encoding.
            // Parse the content (OPTIONAL field).
            // Skip the [0] EXPLICIT tag by pretending that the content is the
            // one and only element in an implicitly tagged set
            if (typeAndContent.length > 1) { // content is OPTIONAL
                taggedContent = typeAndContent[1];
                disTaggedContent
                    = new DerInputStream(taggedContent.toByteArray());
                contents = disTaggedContent.getSet(1, true);
                content = contents[0];
            }
        }
    }

    public DerValue getContent() {
        return content;
    }

    public ObjectIdentifier getContentType() {
        return contentType;
    }

    public byte[] getData() throws IOException {
        if (contentType.equals((Object)DATA_OID) ||
            contentType.equals((Object)OLD_DATA_OID) ||
            contentType.equals((Object)TIMESTAMP_TOKEN_INFO_OID)) {
            if (content == null)
                return null;
            else
                return content.getOctetString();
        }
        throw new IOException("content type is not DATA: " + contentType);
    }

    public void encode(DerOutputStream out) throws IOException {
        DerOutputStream contentDerCode;
        DerOutputStream seq;

        seq = new DerOutputStream();
        seq.putOID(contentType);

        // content is optional, it could be external
        if (content != null) {
            DerValue taggedContent = null;
            contentDerCode = new DerOutputStream();
            content.encode(contentDerCode);

            // Add the [0] EXPLICIT tag in front of the content encoding
            taggedContent = new DerValue((byte)0xA0,
                                         contentDerCode.toByteArray());
            seq.putDerValue(taggedContent);
        }

        out.write(DerValue.tag_Sequence, seq);
    }

    /**
     * Returns a byte array representation of the data held in
     * the content field.
     */
    public byte[] getContentBytes() throws IOException {
        if (content == null)
            return null;

        DerInputStream dis = new DerInputStream(content.toByteArray());
        return dis.getOctetString();
    }

    public String toString() {
        String out = "";

        out += "Content Info Sequence\n\tContent type: " + contentType + "\n";
        out += "\tContent: " + content;
        return out;
    }
}
