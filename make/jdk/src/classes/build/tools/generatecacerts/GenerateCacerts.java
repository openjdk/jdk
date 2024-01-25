/*
 * Copyright (c) 2019, 2021, Oracle and/or its affiliates. All rights reserved.
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

package build.tools.generatecacerts;

import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Generate cacerts
 *    args[0]: Full path string to the directory that contains CA certs
 *    args[1]: Full path string to the generated cacerts
 */
public class GenerateCacerts {
    public static void main(String[] args) throws Exception {
        try (FileOutputStream fos = new FileOutputStream(args[1])) {
            store(args[0], fos);
        }
    }

    public static void store(String dir, OutputStream stream) throws Exception {

        CertificateFactory cf = CertificateFactory.getInstance("X.509");

        KeyStore ks = KeyStore.getInstance("pkcs12");
        ks.load(null, null);

        // All file names in dir sorted.
        // README is excluded. Name starting with "." excluded.
        List<String> entries = Files.list(Path.of(dir))
                .map(p -> p.getFileName().toString())
                .filter(s -> !s.equals("README") && !s.startsWith("."))
                .collect(Collectors.toList());

        entries.sort(String::compareTo);

        for (String entry : entries) {
            String alias = entry + " [jdk]";
            X509Certificate cert;
            try (InputStream fis = Files.newInputStream(Path.of(dir, entry))) {
                cert = (X509Certificate) cf.generateCertificate(fis);
            }
            ks.setCertificateEntry(alias, cert);
        }

        ks.store(stream, null);
    }
}
