/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.util.Arrays;

/**
 * @test
 */
public class BuilderTest {
    static Cipher c;

    public static void main(String[] args) throws Exception {
        byte[] keydata = new byte[16];
        byte[] pt = "FISH".getBytes();

        //SecretKeySpec key = new SecretKeySpec(keydata, "AES");
        Cipher.Builder cb = Cipher.newBuilder();
        c = cb.with("AES/GCM/NoPadding").generate("AES").build();
        SecretKeySpec key = (SecretKeySpec) cb.getKey();
        byte[] ct = c.doFinal(pt);
        c = Cipher.newBuilder().decrypt().with("AES/GCM/NoPadding").with(key).with(c.getParameters()).build();
        ct = c.doFinal(ct);
        assert(Arrays.compare(pt, ct) != 0);
    }
}
