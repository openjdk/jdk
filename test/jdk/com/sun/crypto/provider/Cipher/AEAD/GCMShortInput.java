/*
 * Copyright (c) 2023, Alphabet LLC. All rights reserved.
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
 * @bug 8314045
 * @summary ArithmeticException in GaloisCounterMode
 */

import java.nio.ByteBuffer;

import javax.crypto.AEADBadTagException;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

public class GCMShortInput {

    public static void main(String args[]) throws Exception {
        SecretKeySpec keySpec =
                new SecretKeySpec(
                        new byte[] {
                            88, 26, 43, -100, -24, -29, -70, 10, 34, -85, 52, 101, 45, -68, -105,
                            -123
                        },
                        "AES");
        GCMParameterSpec params =
                new GCMParameterSpec(8 * 16, new byte[] {0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0});
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, keySpec, params);
        try {
            cipher.doFinal(ByteBuffer.allocate(0), ByteBuffer.allocate(0));
            throw new AssertionError("AEADBadTagException expected");
        } catch (AEADBadTagException e) {
            // expected
        }
    }
}
