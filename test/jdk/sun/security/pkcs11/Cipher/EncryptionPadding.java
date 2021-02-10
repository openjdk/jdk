/*
 * Copyright (c) 2021, Red Hat, Inc.
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
 * @bug 8261355
 * @library /test/lib ..
 * @run main/othervm EncryptionPadding
 */

import java.nio.ByteBuffer;
import java.security.Provider;
import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;

public class EncryptionPadding extends PKCS11Test {

    public static void main(String[] args) throws Exception {
        main(new EncryptionPadding(), args);
    }

    @Override
    public void main(Provider p) throws Exception {

        Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding", p);
        cipher.init(Cipher.ENCRYPT_MODE,
                new SecretKeySpec(new byte[16], "AES"));

        cipher.update(new byte[1], 0, 1);
        cipher.update(ByteBuffer.allocate(1), ByteBuffer.allocate(16));

        System.out.println("TEST PASS - OK");
    }
}
