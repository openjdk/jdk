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

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import jdk.jpackage.internal.util.PathUtils;

public final class MacCertificateUtils {

    public static Collection<? extends Certificate> findCertificates(Optional<Path> keychain, Optional<String> certificateNameFilter) {
        List<String> args = new ArrayList<>();
        args.add("/usr/bin/security");
        args.add("find-certificate");
        args.add("-a");
        certificateNameFilter.ifPresent(filter -> args.addAll(List.of("-c", filter)));
        args.add("-p"); // PEM format
        keychain.map(PathUtils::normalizedAbsolutePathString).ifPresent(args::add);

        return toSupplier(() -> {
            final var output = Executor.of(args.toArray(String[]::new))
                    .setQuiet(true).saveOutput(true).executeExpectSuccess()
                    .getOutput();

            final byte[] pemCertificatesBuffer = output.stream()
                    .reduce(new StringBuilder(),
                            StringBuilder::append,
                            StringBuilder::append).toString().getBytes(StandardCharsets.US_ASCII);

            try (var in = new ByteArrayInputStream(pemCertificatesBuffer)) {
                final var cf = CertificateFactory.getInstance("X.509");
                return cf.generateCertificates(in);
            }
        }).get();
    }

    public static Collection<X509Certificate> filterX509Certificates(Collection<? extends Certificate> certs) {
        return certs.stream()
                .filter(X509Certificate.class::isInstance)
                .map(X509Certificate.class::cast)
                .toList();
    }
}
