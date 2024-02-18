/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8308010
 * @summary X509Key and PKCS8Key allows garbage bytes at the end
 * @library /test/lib
 */

import jdk.test.lib.Utils;

import java.security.KeyFactory;
import java.security.KeyPairGenerator;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Arrays;

public class LongPKCS8orX509KeySpec {

    public static void main(String[] argv) throws Exception {
        var g = KeyPairGenerator.getInstance("EC");
        var f = KeyFactory.getInstance("EC");
        Utils.runAndCheckException(() -> f.generatePublic(new X509EncodedKeySpec(
                Arrays.copyOf(g.generateKeyPair().getPublic().getEncoded(), 1000))),
                InvalidKeySpecException.class);
        Utils.runAndCheckException(() -> f.generatePrivate(new PKCS8EncodedKeySpec(
                Arrays.copyOf(g.generateKeyPair().getPrivate().getEncoded(), 1000))),
                InvalidKeySpecException.class);
    }
}
