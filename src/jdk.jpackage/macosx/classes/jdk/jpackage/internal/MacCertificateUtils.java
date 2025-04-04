/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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

package jdk.jpackage.internal;

import static jdk.jpackage.internal.util.function.ThrowingSupplier.toSupplier;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public final class MacCertificateUtils {

    public static Collection<X509Certificate> findCertificates(Optional<Keychain> keychain, Optional<String> certificateNameFilter) {
        List<String> args = new ArrayList<>();
        args.add("/usr/bin/security");
        args.add("find-certificate");
        args.add("-a");
        certificateNameFilter.ifPresent(filter -> args.addAll(List.of("-c", filter)));
        args.add("-p"); // PEM format
        keychain.map(Keychain::asCliArg).ifPresent(args::add);

        return toSupplier(() -> {
            final var output = Executor.of(args.toArray(String[]::new))
                    .setQuiet(true).saveOutput(true).executeExpectSuccess()
                    .getOutput();

            final byte[] pemCertificatesBuffer = output.stream()
                    .reduce(new StringBuilder(),
                            MacCertificateUtils::append,
                            MacCertificateUtils::append).toString().getBytes(StandardCharsets.US_ASCII);

            final var cf = CertificateFactory.getInstance("X.509");

            Collection<X509Certificate> certs = new ArrayList<>();

            try (var in = new BufferedInputStream(new ByteArrayInputStream(pemCertificatesBuffer))) {
                while (in.available() > 0) {
                    final X509Certificate cert;
                    try {
                        cert = (X509Certificate)cf.generateCertificate(in);
                    } catch (CertificateException ex) {
                        // Not a valid X505 certificate, silently ignore.
                        continue;
                    }
                    certs.add(cert);
                }
            }
            return certs;
        }).get();
    }

    record CertificateHash(byte[] value) {
        CertificateHash {
            Objects.requireNonNull(value);
            if (value.length != 20) {
                throw new IllegalArgumentException("Invalid SHA-1 hash");
            }
        }

        static CertificateHash of(X509Certificate cert) {
            return new CertificateHash(toSupplier(() -> {
                final MessageDigest md = MessageDigest.getInstance("SHA-1");
                md.update(cert.getEncoded());
                return md.digest();
            }).get());
        }

        @Override
        public String toString() {
            return FORMAT.formatHex(value);
        }

        static CertificateHash fromHexString(String hash) {
            return new CertificateHash(FORMAT.parseHex(hash));
        }

        private static final HexFormat FORMAT = HexFormat.of().withUpperCase();
    }

    private static StringBuilder append(StringBuilder sb, Object v) {
        return sb.append(v).append('\n');
    }
}
