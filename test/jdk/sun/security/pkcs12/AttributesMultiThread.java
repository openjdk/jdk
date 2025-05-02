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
 * @bug 8309667
 * @library /test/lib
 * @modules java.base/sun.security.tools.keytool
 *          java.base/sun.security.x509
 * @summary ensures attributes reading is thread safe
 */
import sun.security.tools.keytool.CertAndKeyGen;
import sun.security.x509.X500Name;

import java.security.KeyStore;
import java.security.PKCS12Attribute;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class AttributesMultiThread {

    static KeyStore ks;
    static AtomicBoolean ab = new AtomicBoolean();

    public static void main(String[] args) throws Exception {

        ks = KeyStore.getInstance("pkcs12");
        ks.load(null, null);
        var cak = new CertAndKeyGen("ed25519", "ed25519");
        cak.generate("ed25519");
        var c = cak.getSelfCertificate(new X500Name("CN=A"), 1000);
        Set<KeyStore.Entry.Attribute> ss = Set.of(
                new PKCS12Attribute("1.1.1.1", "b"),
                new PKCS12Attribute("1.1.1.2", "b"),
                new PKCS12Attribute("1.1.1.3", "b"),
                new PKCS12Attribute("1.1.1.4", "b"),
                new PKCS12Attribute("1.1.1.5", "b"),
                new PKCS12Attribute("1.1.1.6", "b"),
                new PKCS12Attribute("1.1.1.7", "b"),
                new PKCS12Attribute("1.1.1.8", "b"),
                new PKCS12Attribute("1.1.1.9", "b"),
                new PKCS12Attribute("1.1.1.10", "b"));
        ks.setEntry("a", new KeyStore.TrustedCertificateEntry(c, ss), null);

        var x = Executors.newVirtualThreadPerTaskExecutor();
        for (int i = 0; i < 1000; i++) {
            x.submit(AttributesMultiThread::check);
        }
        x.shutdown();
        x.awaitTermination(1, TimeUnit.MINUTES);

        if (ab.get()) {
            throw new RuntimeException();
        }
    }

    static void check() {
        for (int i = 0; i < 100; i++) {
            var s = get();
            if (s.size() != 12) { // 10 presets and 2 added by PKCS12
                ab.set(true);
                throw new RuntimeException();
            }
        }
    }

    static Set<?> get() {
        try {
            return ks.getAttributes("a");
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
