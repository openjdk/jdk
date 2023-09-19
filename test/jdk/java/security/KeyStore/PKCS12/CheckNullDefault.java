/*
 * Copyright (c) 2023 Oracle and/or its affiliates. All rights reserved.
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
import java.security.KeyStore;
import static java.lang.System.out;

/**
 * @test
 * @bug 8304956
 * @summary Set up keystore.type as null and check that
 * KeyStore.getDefaultType() value is related to property value. Expect a full
 * match the value 'keystore.type' and the value of the
 * KeyStore.getDefaultType()
 * @run main/othervm CheckDefaults
 *  -Djava.security.properties=./java.security
 */
public class CheckNullDefault {
    private static final String DEFAULT_KEY_STORE_TYPE = "pkcs12";
    private void runTest(String[] args) {
        if (!KeyStore.getDefaultType().
            equalsIgnoreCase(DEFAULT_KEY_STORE_TYPE)) {
            throw new RuntimeException(String.format("Default keystore type "
                    + "Expected '%s' . Actual: '%s' ", DEFAULT_KEY_STORE_TYPE,
                KeyStore.getDefaultType()));
        }
        out.println("Test Passed");
    }

    public static void main(String[] args) {
        CheckNullDefault checkDefaultsTest = new CheckNullDefault();
        checkDefaultsTest.runTest(args);
    }
}
