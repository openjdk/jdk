/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8278744
 * @summary KeyStore:getAttributes() not returning unmodifiable Set
 * @library /test/lib
 */

import jdk.test.lib.SecurityTools;
import jdk.test.lib.Utils;

import java.io.File;
import java.security.KeyStore;

public class UnmodifiableAttributes {
    public static final void main(String[] args) throws Exception {

        var oneAttr = new KeyStore.Entry.Attribute() {
            @Override
            public String getName() {
                return "1.2.3";
            }

            @Override
            public String getValue() {
                return "testVal";
            }
        };
        char[] pass = "changeit".toCharArray();

        SecurityTools.keytool("-keystore ks -storepass changeit -genkeypair -alias a -dname CN=A -keyalg EC")
                .shouldHaveExitValue(0);

        KeyStore ks = KeyStore.getInstance(new File("ks"), pass);

        var attrs = ks.getAttributes("a");
        Utils.runAndCheckException(() -> attrs.add(oneAttr), UnsupportedOperationException.class);

        var attrs2 = ks.getEntry("a", new KeyStore.PasswordProtection(pass)).getAttributes();
        Utils.runAndCheckException(() -> attrs2.add(oneAttr), UnsupportedOperationException.class);
    }
}
