/*
 * reserved comment block
 * DO NOT REMOVE OR ALTER!
 */
/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package com.sun.org.apache.xml.internal.security.utils;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * A collection of different, general-purpose methods for JAVA-specific things
 * @author Christian Geuer-Pollmann
 */
public class JavaUtils {

    /** {@link org.apache.commons.logging} logging facility */
    private static java.util.logging.Logger log =
        java.util.logging.Logger.getLogger(JavaUtils.class.getName());

    private JavaUtils() {
        // we don't allow instantiation
    }

    /**
     * Method getBytesFromFile
     *
     * @param fileName
     * @return the bytes read from the file
     *
     * @throws FileNotFoundException
     * @throws IOException
     */
    public static byte[] getBytesFromFile(String fileName)
        throws FileNotFoundException, IOException {

        byte refBytes[] = null;

        FileInputStream fisRef = null;
        UnsyncByteArrayOutputStream baos = null;
        try {
            fisRef = new FileInputStream(fileName);
            baos = new UnsyncByteArrayOutputStream();
            byte buf[] = new byte[1024];
            int len;

            while ((len = fisRef.read(buf)) > 0) {
                baos.write(buf, 0, len);
            }

            refBytes = baos.toByteArray();
        } finally {
            if (baos != null) {
                baos.close();
            }
            if (fisRef != null) {
                fisRef.close();
            }
        }

        return refBytes;
    }

    /**
     * Method writeBytesToFilename
     *
     * @param filename
     * @param bytes
     */
    public static void writeBytesToFilename(String filename, byte[] bytes) {
        FileOutputStream fos = null;
        try {
            if (filename != null && bytes != null) {
                File f = new File(filename);

                fos = new FileOutputStream(f);

                fos.write(bytes);
                fos.close();
            } else {
                if (log.isLoggable(java.util.logging.Level.FINE)) {
                    log.log(java.util.logging.Level.FINE, "writeBytesToFilename got null byte[] pointed");
                }
            }
        } catch (IOException ex) {
            if (fos != null) {
                try {
                    fos.close();
                } catch (IOException ioe) {
                    if (log.isLoggable(java.util.logging.Level.FINE)) {
                        log.log(java.util.logging.Level.FINE, ioe.getMessage(), ioe);
                    }
                }
            }
        }
    }

    /**
     * This method reads all bytes from the given InputStream till EOF and
     * returns them as a byte array.
     *
     * @param inputStream
     * @return the bytes read from the stream
     *
     * @throws FileNotFoundException
     * @throws IOException
     */
    public static byte[] getBytesFromStream(InputStream inputStream) throws IOException {
        UnsyncByteArrayOutputStream baos = null;

        byte[] retBytes = null;
        try {
            baos = new UnsyncByteArrayOutputStream();
            byte buf[] = new byte[4 * 1024];
            int len;

            while ((len = inputStream.read(buf)) > 0) {
                baos.write(buf, 0, len);
            }
            retBytes = baos.toByteArray();
        } finally {
            baos.close();
        }

        return retBytes;
    }

    /**
     * Converts an ASN.1 DSA value to a XML Signature DSA Value.
     *
     * The JCE DSA Signature algorithm creates ASN.1 encoded (r,s) value
     * pairs (see section 2.2.2 of RFC 3279); the XML Signature requires the
     * core BigInteger values.
     *
     * @param asn1Bytes the ASN.1 encoded bytes
     * @param size size of r and s in bytes
     * @return the XML Signature encoded bytes
     * @throws IOException if the bytes are not encoded correctly
     * @see <A HREF="http://www.w3.org/TR/xmldsig-core1/#sec-DSA">6.4.1 DSA</A>
     */
    public static byte[] convertDsaASN1toXMLDSIG(byte[] asn1Bytes, int size)
        throws IOException
    {
        if (asn1Bytes[0] != 48 || asn1Bytes[1] != asn1Bytes.length - 2
            || asn1Bytes[2] != 2) {
            throw new IOException("Invalid ASN.1 format of DSA signature");
        }

        byte rLength = asn1Bytes[3];
        int i;
        for (i = rLength; i > 0 && asn1Bytes[4 + rLength - i] == 0; i--);

        byte sLength = asn1Bytes[5 + rLength];
        int j;
        for (j = sLength;
             j > 0 && asn1Bytes[6 + rLength + sLength - j] == 0; j--);

        if (i > size || asn1Bytes[4 + rLength] != 2 || j > size) {
            throw new IOException("Invalid ASN.1 format of DSA signature");
        } else {
            byte[] xmldsigBytes = new byte[size * 2];
            System.arraycopy(asn1Bytes, 4 + rLength - i, xmldsigBytes,
                             size - i, i);
            System.arraycopy(asn1Bytes, 6 + rLength + sLength - j,
                             xmldsigBytes, size * 2 - j, j);
            return xmldsigBytes;
        }
    }

    /**
     * Converts an XML Signature DSA Value to a ASN.1 DSA value.
     *
     * The JCE DSA Signature algorithm creates ASN.1 encoded (r,s) value
     * pairs (see section 2.2.2 of RFC 3279); the XML Signature requires the
     * core BigInteger values.
     *
     * @param xmldsigBytes the XML Signature encoded bytes
     * @param size size of r and s in bytes
     * @return the ASN.1 encoded bytes
     * @throws IOException if the bytes are not encoded correctly
     * @see <A HREF="http://www.w3.org/TR/xmldsig-core1/#sec-DSA">6.4.1 DSA</A>
     */
    public static byte[] convertDsaXMLDSIGtoASN1(byte[] xmldsigBytes, int size)
        throws IOException
    {
        int totalSize = size * 2;
        if (xmldsigBytes.length != totalSize) {
            throw new IOException("Invalid XMLDSIG format of DSA signature");
        }

        int i;
        for (i = size; i > 0 && xmldsigBytes[size - i] == 0; i--);

        int j = i;
        if (xmldsigBytes[size - i] < 0) {
            j++;
        }

        int k;
        for (k = size; k > 0 && xmldsigBytes[totalSize - k] == 0; k--);

        int l = k;
        if (xmldsigBytes[totalSize - k] < 0) {
            l++;
        }

        byte[] asn1Bytes = new byte[6 + j + l];
        asn1Bytes[0] = 48;
        asn1Bytes[1] = (byte)(4 + j + l);
        asn1Bytes[2] = 2;
        asn1Bytes[3] = (byte)j;
        System.arraycopy(xmldsigBytes, size - i, asn1Bytes, 4 + j - i, i);

        asn1Bytes[4 + j] = 2;
        asn1Bytes[5 + j] = (byte) l;
        System.arraycopy(xmldsigBytes, totalSize - k, asn1Bytes,
                         6 + j + l - k, k);

        return asn1Bytes;
    }
}
