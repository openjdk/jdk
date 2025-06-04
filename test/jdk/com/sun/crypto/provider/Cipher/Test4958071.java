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

/*
 * @test
 * @library /test/lib
 * @bug 4958071
 * @summary verify InvalidParameterException for Cipher.init
 */

import jdk.test.lib.Utils;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import java.security.AlgorithmParameters;
import java.security.InvalidParameterException;
import java.security.SecureRandom;
import java.security.cert.Certificate;
import java.security.spec.AlgorithmParameterSpec;

public class Test4958071 {

    public boolean execute() throws Exception {

        KeyGenerator aesKey = KeyGenerator.getInstance("AES");
        aesKey.init(128);
        SecretKey generatedAESKey = aesKey.generateKey();

        Cipher c = Cipher.getInstance("AES");

        SecureRandom nullSR = null;
        AlgorithmParameters nullAP = null;
        AlgorithmParameterSpec nullAPS = null;
        Certificate nullCert = null;

        Utils.runAndCheckException(() -> c.init((Cipher.ENCRYPT_MODE - 1),
                generatedAESKey), InvalidParameterException.class);
        Utils.runAndCheckException(() -> c.init((Cipher.UNWRAP_MODE + 1),
                generatedAESKey), InvalidParameterException.class);
        Utils.runAndCheckException(() -> c.init((Cipher.ENCRYPT_MODE - 1),
                generatedAESKey, nullSR), InvalidParameterException.class);
        Utils.runAndCheckException(() -> c.init((Cipher.UNWRAP_MODE + 1),
                generatedAESKey, nullSR), InvalidParameterException.class);
        Utils.runAndCheckException(() -> c.init((Cipher.ENCRYPT_MODE - 1),
                generatedAESKey, nullAP), InvalidParameterException.class);
        Utils.runAndCheckException(() -> c.init((Cipher.UNWRAP_MODE + 1),
                generatedAESKey, nullAP), InvalidParameterException.class);
        Utils.runAndCheckException(() -> c.init((Cipher.ENCRYPT_MODE - 1),
                generatedAESKey, nullAP, nullSR), InvalidParameterException.class);
        Utils.runAndCheckException(() -> c.init((Cipher.UNWRAP_MODE + 1),
                generatedAESKey, nullAP, nullSR), InvalidParameterException.class);
        Utils.runAndCheckException(() -> c.init((Cipher.ENCRYPT_MODE - 1),
                generatedAESKey, nullAPS), InvalidParameterException.class);
        Utils.runAndCheckException(() -> c.init((Cipher.UNWRAP_MODE + 1),
                generatedAESKey, nullAPS), InvalidParameterException.class);
        Utils.runAndCheckException(() -> c.init((Cipher.ENCRYPT_MODE - 1),
                generatedAESKey, nullAPS, nullSR), InvalidParameterException.class);
        Utils.runAndCheckException(() -> c.init((Cipher.UNWRAP_MODE + 1),
                generatedAESKey, nullAPS, nullSR), InvalidParameterException.class);
        Utils.runAndCheckException(() -> c.init((Cipher.ENCRYPT_MODE - 1),
                nullCert), InvalidParameterException.class);
        Utils.runAndCheckException(() -> c.init((Cipher.UNWRAP_MODE + 1),
                nullCert), InvalidParameterException.class);
        Utils.runAndCheckException(() -> c.init((Cipher.ENCRYPT_MODE - 1),
                nullCert, nullSR), InvalidParameterException.class);
        Utils.runAndCheckException(() -> c.init((Cipher.UNWRAP_MODE + 1),
                nullCert, nullSR), InvalidParameterException.class);

        return true;
    }

    public static void main(String[] args) throws Exception {

        Test4958071 test = new Test4958071();

        if (test.execute()) {
            System.out.println(test.getClass().getName() + ": passed!");
        }

    }
}
