/*
 * Copyright (c) 2013, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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

/*
 * @test
 * @bug 7160837
 * @summary Make sure Cipher IO streams doesn't call extra doFinal if close()
 * is called multiple times.  Additionally, verify the input and output streams
 * match with encryption and decryption with non-stream crypto.
 */

import java.io.*;
import java.security.DigestOutputStream;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.util.Arrays;

import javax.crypto.Cipher;
import javax.crypto.CipherOutputStream;
import javax.crypto.CipherInputStream;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import javax.xml.bind.DatatypeConverter;

public class CipherStreamClose {
    private static final String message = "This is the sample message";
    static boolean debug = false;

    /*
     * This method does encryption by cipher.doFinal(), and not with
     * CipherOutputStream
     */
    public static byte[] blockEncrypt(String message, SecretKey key)
        throws Exception {

        byte[] data;
        Cipher encCipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
        encCipher.init(Cipher.ENCRYPT_MODE, key);
        try (ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
            try (ObjectOutputStream oos = new ObjectOutputStream(bos)) {
                oos.writeObject(message);
            }
            data = bos.toByteArray();
        }

        if (debug) {
            System.out.println(DatatypeConverter.printHexBinary(data));
        }
        return encCipher.doFinal(data);

    }

    /*
     * This method does decryption by cipher.doFinal(), and not with
     * CipherIntputStream
     */
    public static Object blockDecrypt(byte[] data, SecretKey key)
        throws Exception {

        Cipher c = Cipher.getInstance("AES/ECB/PKCS5Padding");
        c.init(Cipher.DECRYPT_MODE, key);
        data = c.doFinal(data);
        try (ByteArrayInputStream bis = new ByteArrayInputStream(data)) {
            try (ObjectInputStream ois = new ObjectInputStream(bis)) {
                return ois.readObject();
            }
        }
    }

    public static byte[] streamEncrypt(String message, SecretKey key,
        MessageDigest digest)
        throws Exception {

        byte[] data;
        Cipher encCipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
        encCipher.init(Cipher.ENCRYPT_MODE, key);
        try (ByteArrayOutputStream bos = new ByteArrayOutputStream();
            DigestOutputStream dos = new DigestOutputStream(bos, digest);
            CipherOutputStream cos = new CipherOutputStream(dos, encCipher)) {
            try (ObjectOutputStream oos = new ObjectOutputStream(cos)) {
                oos.writeObject(message);
            }
            data = bos.toByteArray();
        }

        if (debug) {
            System.out.println(DatatypeConverter.printHexBinary(data));
        }
        return data;
    }

    public static Object streamDecrypt(byte[] data, SecretKey key,
        MessageDigest digest) throws Exception {

        Cipher decCipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
        decCipher.init(Cipher.DECRYPT_MODE, key);
        digest.reset();
        try (ByteArrayInputStream bis = new ByteArrayInputStream(data);
            DigestInputStream dis = new DigestInputStream(bis, digest);
            CipherInputStream cis = new CipherInputStream(dis, decCipher)) {

            try (ObjectInputStream ois = new ObjectInputStream(cis)) {
                return ois.readObject();
            }
        }
    }

    public static void main(String[] args) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA1");
        SecretKeySpec key = new SecretKeySpec(
            DatatypeConverter.parseHexBinary(
            "12345678123456781234567812345678"), "AES");

        // Run 'message' through streamEncrypt
        byte[] se = streamEncrypt(message, key, digest);
        // 'digest' already has the value from the stream, just finish the op
        byte[] sd = digest.digest();
        digest.reset();
        // Run 'message' through blockEncrypt
        byte[] be = blockEncrypt(message, key);
        // Take digest of encrypted blockEncrypt result
        byte[] bd = digest.digest(be);
        // Verify both returned the same value
        if (!Arrays.equals(sd, bd)) {
            System.err.println("Stream: "+DatatypeConverter.printHexBinary(se)+
                "\t Digest: "+DatatypeConverter.printHexBinary(sd));
            System.err.println("Block : "+DatatypeConverter.printHexBinary(be)+
                "\t Digest: "+DatatypeConverter.printHexBinary(bd));
            throw new Exception("stream & block encryption does not match");
        }

        digest.reset();
        // Sanity check: Decrypt separately from stream to verify operations
        String bm = (String) blockDecrypt(be, key);
        if (message.compareTo(bm) != 0) {
            System.err.println("Expected: "+message+"\nBlock:    "+bm);
            throw new Exception("Block decryption does not match expected");
        }

        // Have decryption and digest included in the object stream
        String sm = (String) streamDecrypt(se, key, digest);
        if (message.compareTo(sm) != 0) {
            System.err.println("Expected: "+message+"\nStream:   "+sm);
            throw new Exception("Stream decryption does not match expected.");
        }
    }
}
