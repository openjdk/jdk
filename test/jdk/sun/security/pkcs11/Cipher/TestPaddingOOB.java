/*
 * Copyright (c) 2022, Red Hat, Inc.
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
 * @bug 8289301
 * @summary P11Cipher should not throw OOB exception during padding when "reqBlockUpdates" == true
 * @library /test/lib ..
 * @run main/othervm TestPaddingOOB
 */

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.security.Key;
import java.security.Provider;

public class TestPaddingOOB extends PKCS11Test {

    public static void main(String[] args) throws Exception {
        main(new TestPaddingOOB(), args);
    }

    @Override
    public void main(Provider p) throws Exception {
        KeyGenerator kg = KeyGenerator.getInstance("AES", p);
        Key key = kg.generateKey();

        Cipher c = Cipher.getInstance("AES/ECB/PKCS5Padding", p);
        int bs = c.getBlockSize();

        // Test with arrays
        byte[] plainArr = new byte[bs];
        Arrays.fill(plainArr, (byte) 'a');
        c.init(Cipher.ENCRYPT_MODE, key);
        byte[] encArr = new byte[c.getOutputSize(plainArr.length)];
        int off = c.update(plainArr, 0, 1, encArr, 0);
        off += c.doFinal(plainArr, 1, plainArr.length - 1, encArr, off);
        if (off != 2 * bs) {
            throw new Exception("Unexpected encrypted size (array): " + off);
        }
        c.init(Cipher.DECRYPT_MODE, key);
        byte[] plainArr2 = new byte[c.getOutputSize(encArr.length)];
        off = c.doFinal(encArr, 0, encArr.length, plainArr2, 0);
        if (off != bs) {
            throw new Exception("Unexpected decrypted size (array): " + off);
        }
        if (!Arrays.equals(plainArr, Arrays.copyOfRange(plainArr2, 0, off))) {
            throw new Exception("Invalid decrypted data (array)");
        }

        // Test with buffers
        ByteBuffer plainBuf = ByteBuffer.allocate(bs);
        Arrays.fill(plainArr, (byte) 'b');
        plainBuf.put(plainArr);
        plainBuf.flip();
        c.init(Cipher.ENCRYPT_MODE, key);
        ByteBuffer encBuf = ByteBuffer.allocate(c.getOutputSize(plainBuf.limit()));
        plainBuf.limit(1);
        off = c.update(plainBuf, encBuf);
        plainBuf.limit(bs);
        off += c.doFinal(plainBuf, encBuf);
        if (off != 2 * bs) {
            throw new Exception("Unexpected encrypted size (buffer): " + off);
        }
        encBuf.flip();
        c.init(Cipher.DECRYPT_MODE, key);
        ByteBuffer plainBuf2 = ByteBuffer.allocate(c.getOutputSize(encBuf.limit()));
        off = c.doFinal(encBuf, plainBuf2);
        if (off != bs) {
            throw new Exception("Unexpected decrypted size (buffer): " + off);
        }
        plainBuf2.flip();
        plainBuf2.get(plainArr2, 0, off);
        if (!Arrays.equals(plainArr, Arrays.copyOfRange(plainArr2, 0, off))) {
            throw new Exception("Invalid decrypted data (buffer)");
        }
    }

}
