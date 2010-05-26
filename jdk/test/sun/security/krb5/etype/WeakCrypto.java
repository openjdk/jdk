/*
 * Copyright (c) 2010, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 6844909
 * @run main/othervm WeakCrypto
 * @summary support allow_weak_crypto in krb5.conf
 */

import java.io.File;
import sun.security.krb5.internal.crypto.EType;
import sun.security.krb5.EncryptedData;

public class WeakCrypto {
    public static void main(String[] args) throws Exception {
        System.setProperty("java.security.krb5.conf",
                System.getProperty("test.src", ".") +
                File.separator +
                "weakcrypto.conf");
        int[] etypes = EType.getBuiltInDefaults();

        for (int i=0, length = etypes.length; i<length; i++) {
            if (etypes[i] == EncryptedData.ETYPE_DES_CBC_CRC ||
                    etypes[i] == EncryptedData.ETYPE_DES_CBC_MD4 ||
                    etypes[i] == EncryptedData.ETYPE_DES_CBC_MD5) {
                throw new Exception("DES should not appear");
            }
        }
    }
}
