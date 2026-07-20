/*
 * Copyright (c) 2022, 2024, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 6227536
 * @summary Verify HmacSHA1 and HmacMD5 KeyGenerators throw an Exception when
 *  a keysize of zero is requested
 */

import jdk.test.lib.Utils;

import javax.crypto.KeyGenerator;

public class Test6227536 {

    String[] keyGensToTest = new String[]{"HmacSHA1", "HmacMD5"};

    public boolean execute(String algo) throws Exception {
        KeyGenerator kg = KeyGenerator.getInstance(algo,
                                System.getProperty("test.provider.name", "SunJCE"));

        Utils.runAndCheckException(() -> kg.init(0),
                IllegalArgumentException.class);

        return true;
    }

    public static void main(String[] args) throws Exception {
        Test6227536 test = new Test6227536();
        String testName = test.getClass().getName();

        for (String keyGenToTest : test.keyGensToTest) {

            if (test.execute(keyGenToTest)) {

                System.out.println(testName + ": " + keyGenToTest + " Passed!");

            }

        }

    }
}
