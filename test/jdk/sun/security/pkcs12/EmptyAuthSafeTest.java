/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8326087
 * @summary Verify keystore loads when authSafe content is absent.
 */

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.security.KeyStore;
import java.util.Base64;

public class EmptyAuthSafeTest {

    static final char[] PASSWORD = "1234".toCharArray();

    public static void main(String[] args) throws Exception {
        var authsafe = """
                MFsCAQMwCwYJKoZIhvcNAQcBMEkwMTANBglghkgBZQMEAgEFAAQgX2iKyj065lT1hA8c7H+NREUaXuTdy/W2aHjeVJNT/N0EEAARIjNEVWZ3iJmqu8zd7v8CAggA""";

        loadAndStore(authsafe);
    }

    static void loadAndStore(String data) throws Exception {
        var bytes = Base64.getMimeDecoder().decode(data);
        var ks = KeyStore.getInstance("PKCS12");
        ks.load(new ByteArrayInputStream(bytes), PASSWORD);
        var baos = new ByteArrayOutputStream();
        ks.store(baos, PASSWORD);
        var newBytes = baos.toByteArray();
        var bais = new ByteArrayInputStream(newBytes);
        ks.load(bais, PASSWORD);
    }
}
