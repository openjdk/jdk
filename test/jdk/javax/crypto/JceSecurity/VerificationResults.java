/*
 * Copyright (c) 2023, BELLSOFT. All rights reserved.
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
 * @bug 8168469
 * @summary Memory leak in JceSecurity
 * @compile --add-exports java.base/com.sun.crypto.provider=ALL-UNNAMED VerificationResults.java
 * @run main/othervm -Xmx128m --add-exports java.base/com.sun.crypto.provider=ALL-UNNAMED VerificationResults
 */

import java.security.NoSuchAlgorithmException;
import java.security.Provider;

import javax.crypto.Cipher;
import javax.crypto.NoSuchPaddingException;

import com.sun.crypto.provider.SunJCE;

public class VerificationResults {

    // approximate double the number of providers that fits in -Xmx128m heap
    private static final int PROVIDERS_COUNT = 2000;
    // the heap buffer size that triggers the OOME when the providers heap cannot be reclaimed
    private static final int OOM_TRIGGER_SIZE = 10 * 1024 * 1024;
    public static void main(String[] args) throws NoSuchAlgorithmException, NoSuchPaddingException {
        int i = 0;
        try {
            for (; i < PROVIDERS_COUNT; i++) {
                SunJCE jceProvider = new SunJCE();
                Cipher c = Cipher.getInstance("AES", jceProvider);
                char[] arr = new char[OOM_TRIGGER_SIZE];
            }
        } catch (OutOfMemoryError e) {
            System.out.println("Caught OOME - less than 10M heap left.\nCreated " + i + " SunJCE providers");
            throw e;
        }
    }
}
