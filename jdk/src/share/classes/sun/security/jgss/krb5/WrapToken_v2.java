/*
 * Copyright (c) 2004, 2007, Oracle and/or its affiliates. All rights reserved.
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

package sun.security.jgss.krb5;

import org.ietf.jgss.*;
import sun.security.jgss.*;
import java.security.GeneralSecurityException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.IOException;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import sun.security.krb5.Confounder;
import sun.security.krb5.KrbException;

/**
 * This class represents the new format of GSS tokens, as specified in
 * draft-ietf-krb-wg-gssapi-cfx-07.txt, emitted by the GSSContext.wrap()
 * call. It is a MessageToken except that it also contains plaintext or
 * encrypted data at the end. A WrapToken has certain other rules that are
 * peculiar to it and different from a  MICToken, which is another type of
 * MessageToken. All data in a WrapToken is prepended by a random counfounder
 * of 16 bytes. Thus, all application data is replaced by
 * (confounder || data || tokenHeader || checksum).
 *
 * @author Seema Malkani
 */
class WrapToken_v2 extends MessageToken_v2 {
    /**
     * The size of the random confounder used in a WrapToken.
     */
    static final int CONFOUNDER_SIZE = 16;

    /*
     * A token may come in either in an InputStream or as a
     * byte[]. Store a reference to it in either case and process
     * it's data only later when getData() is called and
     * decryption/copying is needed to be done. Note that JCE can
     * decrypt both from a byte[] and from an InputStream.
     */
    private boolean readTokenFromInputStream = true;
    private InputStream is = null;
    private byte[] tokenBytes = null;
    private int tokenOffset = 0;
    private int tokenLen = 0;

    /*
     * Application data may come from an InputStream or from a
     * byte[]. However, it will always be stored and processed as a
     * byte[] since
     * (a) the MessageDigest class only accepts a byte[] as input and
     * (b) It allows writing to an OuputStream via a CipherOutputStream.
     */
    private byte[] dataBytes = null;
    private int dataOffset = 0;
    private int dataLen = 0;

    // the len of the token data:
    //          (confounder || data || tokenHeader || checksum)
    private int dataSize = 0;

    // Accessed by CipherHelper
    byte[] confounder = null;

    private boolean privacy = false;
    private boolean initiator = true;

    /**
     * Constructs a WrapToken from token bytes obtained from the
     * peer.
     * @param context the mechanism context associated with this
     * token
     * @param tokenBytes the bytes of the token
     * @param tokenOffset the offset of the token
     * @param tokenLen the length of the token
     * @param prop the MessageProp into which characteristics of the
     * parsed token will be stored.
     * @throws GSSException if the token is defective
     */
    public WrapToken_v2(Krb5Context context,
                     byte[] tokenBytes, int tokenOffset, int tokenLen,
                     MessageProp prop)  throws GSSException {

        // Just parse the MessageToken part first
        super(Krb5Token.WRAP_ID_v2, context,
              tokenBytes, tokenOffset, tokenLen, prop);
        this.readTokenFromInputStream = false;

        // rotate token bytes as per RRC
        byte[] new_tokenBytes = new byte[tokenLen];
        if (rotate_left(tokenBytes, tokenOffset, new_tokenBytes, tokenLen)) {
            this.tokenBytes = new_tokenBytes;
            this.tokenOffset = 0;
        } else {
            this.tokenBytes = tokenBytes;
            this.tokenOffset = tokenOffset;
        }

        // Will need the token bytes again when extracting data
        this.tokenLen = tokenLen;
        this.privacy = prop.getPrivacy();

        dataSize = tokenLen - TOKEN_HEADER_SIZE;

        // save initiator
        this.initiator = context.isInitiator();

    }

    /**
     * Constructs a WrapToken from token bytes read on the fly from
     * an InputStream.
     * @param context the mechanism context associated with this
     * token
     * @param is the InputStream containing the token bytes
     * @param prop the MessageProp into which characteristics of the
     * parsed token will be stored.
     * @throws GSSException if the token is defective or if there is
     * a problem reading from the InputStream
     */
    public WrapToken_v2(Krb5Context context,
                     InputStream is, MessageProp prop)
        throws GSSException {

        // Just parse the MessageToken part first
        super(Krb5Token.WRAP_ID_v2, context, is, prop);

        // Will need the token bytes again when extracting data
        this.is = is;
        this.privacy = prop.getPrivacy();

        // get the token length
        try {
            this.tokenLen = is.available();
        } catch (IOException e) {
            throw new GSSException(GSSException.DEFECTIVE_TOKEN, -1,
                                   getTokenName(getTokenId())
                                   + ": " + e.getMessage());
        }

        // data size
        dataSize = tokenLen - TOKEN_HEADER_SIZE;

        // save initiator
        this.initiator = context.isInitiator();
    }

    /**
     * Obtains the application data that was transmitted in this
     * WrapToken.
     * @return a byte array containing the application data
     * @throws GSSException if an error occurs while decrypting any
     * cipher text and checking for validity
     */
    public byte[] getData() throws GSSException {

        byte[] temp = new byte[dataSize];
        int len = getData(temp, 0);
        // len obtained is after removing confounder, tokenHeader and HMAC

        byte[] retVal = new byte[len];
        System.arraycopy(temp, 0, retVal, 0, retVal.length);
        return retVal;
    }

    /**
     * Obtains the application data that was transmitted in this
     * WrapToken, writing it into an application provided output
     * array.
     * @param dataBuf the output buffer into which the data must be
     * written
     * @param dataBufOffset the offset at which to write the data
     * @return the size of the data written
     * @throws GSSException if an error occurs while decrypting any
     * cipher text and checking for validity
     */
    public int getData(byte[] dataBuf, int dataBufOffset)
        throws GSSException {

        if (readTokenFromInputStream)
            getDataFromStream(dataBuf, dataBufOffset);
        else
            getDataFromBuffer(dataBuf, dataBufOffset);

        int retVal = 0;
        if (privacy) {
            retVal = dataSize - confounder.length -
                TOKEN_HEADER_SIZE - cipherHelper.getChecksumLength();
        } else {
            retVal = dataSize - cipherHelper.getChecksumLength();
        }
        return retVal;
    }

    /**
     * Helper routine to obtain the application data transmitted in
     * this WrapToken. It is called if the WrapToken was constructed
     * with a byte array as input.
     * @param dataBuf the output buffer into which the data must be
     * written
     * @param dataBufOffset the offset at which to write the data
     * @throws GSSException if an error occurs while decrypting any
     * cipher text and checking for validity
     */
    private void getDataFromBuffer(byte[] dataBuf, int dataBufOffset)
        throws GSSException {

        int dataPos = tokenOffset + TOKEN_HEADER_SIZE;
        int data_length = 0;

        if (dataPos + dataSize > tokenOffset + tokenLen)
            throw new GSSException(GSSException.DEFECTIVE_TOKEN, -1,
                                   "Insufficient data in "
                                   + getTokenName(getTokenId()));
        // debug("WrapToken cons: data is token is [" +
        //      getHexBytes(tokenBytes, tokenOffset, tokenLen) + "]\n");
        confounder = new byte[CONFOUNDER_SIZE];

        // Do decryption if this token was privacy protected.
        if (privacy) {

            // decrypt data
            cipherHelper.decryptData(this, tokenBytes, dataPos, dataSize,
                                dataBuf, dataBufOffset, getKeyUsage());
            /*
            debug("\t\tDecrypted data is [" +
                getHexBytes(confounder) + " " +
                getHexBytes(dataBuf, dataBufOffset,
                dataSize - CONFOUNDER_SIZE) +
                "]\n");
            */

            data_length = dataSize - CONFOUNDER_SIZE -
                        TOKEN_HEADER_SIZE - cipherHelper.getChecksumLength();
        } else {

            // Token data is in cleartext
            debug("\t\tNo encryption was performed by peer.\n");

            // data
            data_length = dataSize - cipherHelper.getChecksumLength();
            System.arraycopy(tokenBytes, dataPos,
                             dataBuf, dataBufOffset,
                             data_length);
            // debug("\t\tData is: " + getHexBytes(dataBuf, data_length));

            /*
             * Make sure checksum is not corrupt
             */
            if (!verifySign(dataBuf, dataBufOffset, data_length)) {
                throw new GSSException(GSSException.BAD_MIC, -1,
                         "Corrupt checksum in Wrap token");
            }
        }
    }

    /**
     * Helper routine to obtain the application data transmitted in
     * this WrapToken. It is called if the WrapToken was constructed
     * with an Inputstream.
     * @param dataBuf the output buffer into which the data must be
     * written
     * @param dataBufOffset the offset at which to write the data
     * @throws GSSException if an error occurs while decrypting any
     * cipher text and checking for validity
     */
    private void getDataFromStream(byte[] dataBuf, int dataBufOffset)
        throws GSSException {

        int data_length = 0;
        // Don't check the token length. Data will be read on demand from
        // the InputStream.
        // debug("WrapToken cons: data will be read from InputStream.\n");

        confounder = new byte[CONFOUNDER_SIZE];

        try {
            // Do decryption if this token was privacy protected.
            if (privacy) {

                cipherHelper.decryptData(this, is, dataSize,
                    dataBuf, dataBufOffset, getKeyUsage());

                /*
                debug("\t\tDecrypted data is [" +
                     getHexBytes(confounder) + " " +
                     getHexBytes(dataBuf, dataBufOffset,
                  dataSize - CONFOUNDER_SIZE) +
                     "]\n");
                */
                data_length = dataSize - CONFOUNDER_SIZE -
                        TOKEN_HEADER_SIZE - cipherHelper.getChecksumLength();
            } else {

                // Token data is in cleartext
                debug("\t\tNo encryption was performed by peer.\n");
                readFully(is, confounder);

                // read the data
                data_length = dataSize - cipherHelper.getChecksumLength();
                readFully(is, dataBuf, dataBufOffset, data_length);

                /*
                 * Make sure checksum is not corrupt
                 */
                if (!verifySign(dataBuf, dataBufOffset, data_length)) {
                    throw new GSSException(GSSException.BAD_MIC, -1,
                         "Corrupt checksum in Wrap token");
                }
            }
        } catch (IOException e) {
            throw new GSSException(GSSException.DEFECTIVE_TOKEN, -1,
                                   getTokenName(getTokenId())
                                   + ": " + e.getMessage());
        }

    }


    public WrapToken_v2(Krb5Context context, MessageProp prop,
                     byte[] dataBytes, int dataOffset, int dataLen)
        throws GSSException {

        super(Krb5Token.WRAP_ID_v2, context);

        confounder = Confounder.bytes(CONFOUNDER_SIZE);

        dataSize = confounder.length + dataLen + TOKEN_HEADER_SIZE +
                        cipherHelper.getChecksumLength();
        this.dataBytes = dataBytes;
        this.dataOffset = dataOffset;
        this.dataLen = dataLen;

        // save initiator
        this.initiator = context.isInitiator();

        // debug("\nWrapToken cons: data to wrap is [" +
        // getHexBytes(confounder) + " " +
        // getHexBytes(dataBytes, dataOffset, dataLen) + "]\n");

        genSignAndSeqNumber(prop,
                            dataBytes, dataOffset, dataLen);

        /*
         * If the application decides to ask for privacy when the context
         * did not negotiate for it, do not provide it. The peer might not
         * have support for it. The app will realize this with a call to
         * pop.getPrivacy() after wrap().
         */
        if (!context.getConfState())
            prop.setPrivacy(false);

        privacy = prop.getPrivacy();
    }

    public void encode(OutputStream os) throws IOException, GSSException {

        super.encode(os);

        // debug("\n\nWriting data: [");
        if (!privacy) {

            // Wrap Tokens (without confidentiality) =
            // { 16 byte token_header | plaintext | 12-byte HMAC }
            // where HMAC is on { plaintext | token_header }

            // calculate checksum
            byte[] checksum = getChecksum(dataBytes, dataOffset, dataLen);

            // data
            // debug(" " + getHexBytes(dataBytes, dataOffset, dataLen));
            os.write(dataBytes, dataOffset, dataLen);

            // write HMAC
            // debug(" " + getHexBytes(checksum,
            //                  cipherHelper.getChecksumLength()));
            os.write(checksum);

        } else {

            // Wrap Tokens (with confidentiality) =
            // { 16 byte token_header |
            // Encrypt(16-byte confounder | plaintext | token_header) |
            // 12-byte HMAC }

            cipherHelper.encryptData(this, confounder, getTokenHeader(),
                dataBytes, dataOffset, dataLen, getKeyUsage(), os);

        }
        // debug("]\n");
    }

    public byte[] encode() throws IOException, GSSException {
        // XXX Fine tune this initial size
        ByteArrayOutputStream bos = new ByteArrayOutputStream(dataSize + 50);
        encode(bos);
        return bos.toByteArray();
    }

    public int encode(byte[] outToken, int offset)
        throws IOException, GSSException  {

        int retVal = 0;

        // Token header is small
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        super.encode(bos);
        byte[] header = bos.toByteArray();
        System.arraycopy(header, 0, outToken, offset, header.length);
        offset += header.length;

        // debug("WrapToken.encode: Writing data: [");
        if (!privacy) {

            // Wrap Tokens (without confidentiality) =
            // { 16 byte token_header | plaintext | 12-byte HMAC }
            // where HMAC is on { plaintext | token_header }

            // calculate checksum
            byte[] checksum = getChecksum(dataBytes, dataOffset, dataLen);

            // data
            // debug(" " + getHexBytes(dataBytes, dataOffset, dataLen));
            System.arraycopy(dataBytes, dataOffset, outToken, offset,
                             dataLen);
            offset += dataLen;

            // write HMAC
            // debug(" " + getHexBytes(checksum,
            //                  cipherHelper.getChecksumLength()));
            System.arraycopy(checksum, 0, outToken, offset,
                                cipherHelper.getChecksumLength());

            retVal = header.length + dataLen + cipherHelper.getChecksumLength();
        } else {

            // Wrap Tokens (with confidentiality) =
            // { 16 byte token_header |
            // Encrypt(16-byte confounder | plaintext | token_header) |
            // 12-byte HMAC }
            int cLen = cipherHelper.encryptData(this, confounder,
                        getTokenHeader(), dataBytes, dataOffset, dataLen,
                        outToken, offset, getKeyUsage());

            retVal = header.length + cLen;
            // debug(getHexBytes(outToken, offset, dataSize));
        }

        // debug("]\n");

        // %%% assume that plaintext length == ciphertext len
        return retVal;

    }

    protected int getKrb5TokenSize() throws GSSException {
        return (getTokenSize() + dataSize);
    }

    // This implementation is way to conservative. And it certainly
    // doesn't return the maximum limit.
    static int getSizeLimit(int qop, boolean confReq, int maxTokenSize,
        CipherHelper ch) throws GSSException {
        return (GSSHeader.getMaxMechTokenSize(OID, maxTokenSize) -
                (getTokenSize(ch) + CONFOUNDER_SIZE) - 8 /* safety */);
    }
}
