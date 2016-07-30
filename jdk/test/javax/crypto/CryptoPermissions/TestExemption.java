/*
 * Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.
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

import javax.crypto.*;
import java.security.*;

public class TestExemption {

    public static void main(String[] args) throws Exception {

        KeyGenerator kg = KeyGenerator.getInstance("AES");
        kg.init(128);
        SecretKey key128 = kg.generateKey();

        kg.init(192);
        SecretKey key192 = kg.generateKey();

        kg.init(256);
        SecretKey key256 = kg.generateKey();

        Cipher c = Cipher.getInstance("AES/CBC/NoPadding");

        System.out.println("Testing 128-bit");
        c.init(Cipher.ENCRYPT_MODE, key128);

        System.out.println("Testing 192-bit");
        c.init(Cipher.ENCRYPT_MODE, key192);

        try {
            System.out.println("Testing 256-bit");
            c.init(Cipher.ENCRYPT_MODE, key256);
        } catch (InvalidKeyException e) {
            System.out.println("Caught the right exception");
        }

        System.out.println("DONE!");
    }
}
