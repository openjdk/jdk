/*
 * Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
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
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.security.cert.CertificateFactory;

/**
 * Generate cacerts
 *    args[0]: Full path string to the directory that contains CA certs
 *    args[1]: Full path string to the generated cacerts
 */
public class GenerateCacerts {
    public static void main(String[] args) throws Exception {
        KeyStore ks = KeyStore.getInstance("JKS");
        ks.load(null, null);
        CertificateFactory cf = CertificateFactory.getInstance("X509");
        Files.list(Path.of(args[0]))
                .filter(p -> !p.getFileName().toString().equals("README"))
                .forEach(p -> {
                    try {
                        String alias = p.getFileName().toString() + " [jdk]";
                        try (InputStream fis = Files.newInputStream(p)) {
                            ks.setCertificateEntry(alias, cf.generateCertificate(fis));
                        }
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                });
        try (FileOutputStream fos = new FileOutputStream(args[1])) {
            ks.store(fos, "changeit".toCharArray());
        }
    }
}
