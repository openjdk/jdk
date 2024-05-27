/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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

package sun.security.ssl;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Objects;

import javax.crypto.SecretKey;

import static java.nio.charset.StandardCharsets.US_ASCII;

/**
 * A TLS key logger for SunJSSE.
 * <p>
 * This follows the SSLKEYLOGFILE format propsed in draft RFC:
 * <a href="https://datatracker.ietf.org/doc/draft-ietf-tls-keylogfile/"
 * <p>
 * This is an example implementation of a TLSKeyLogger library API that
 * will allow SunJSSE to output connection keys.
 * <p>
 * Note well, this facility is for development/testing only, and should not be
 * used in production.
 *
 * {@snippet lang = java:
 *     #!/bin/sh
 *
 *     # Build the test app
 *     /java/ws/jdk/build/linux-x64/jdk/bin/javac \
 *         TLSKeyLoggerTest.java
 *
 *     # Build the patch module for java.base.
 *     /java/ws/jdk/build/linux-x64/jdk/bin/javac \
 *         -g:vars,lines \
 *         -d module/java.base \
 *         --patch-module java.base=TLSKeyLogger/java.base \
 *         --add-reads java.base=ALL-UNNAMED \
 *         TLSKeyLogger/java.base/sun/security/ssl/TLSKeyLogger.java
 *
 *     # Run the test app
 *     /java/ws/jdk/build/linux-x64/jdk/bin/java \
 *         --patch-module java.base=module/java.base \
 *         TLSKeyLoggerTest
 *  }
 */
final class TLSKeyLogger {

    private static final Path keyLogFile;
    private static final HexFormat hexFormat = HexFormat.of();

    static final TLSKeyLogger INSTANCE = new TLSKeyLogger();

    static {
        // TODO: env variables vs system property
        @SuppressWarnings("removal")
        final String envVal = AccessController.doPrivileged(
                (PrivilegedAction<String>) () ->
                    {return System.getenv("SSLKEYLOGFILE");
        });

        if (envVal == null) {
            // keyLogFile = null;
            keyLogFile = Path.of("SSLKEYLOGFILE");
        } else {
            Path file = null;
            try {
                // TODO: allow only secure values/paths
                file = Path.of(envVal);
            } catch (InvalidPathException ipe) {
                // ignore
            }
            keyLogFile = file;
        }
    }

    void log(final TLSKeyLoggerLabel label, final byte[] clientHelloRandom,
            final SecretKey secret) {
        log(label, clientHelloRandom, secret, 0);
    }

    void log(final TLSKeyLoggerLabel label, final byte[] clientHelloRandom,
            final SecretKey secret, final int keyUpdateCount) {
        if (keyLogFile == null) {
            return;
        }
        Objects.requireNonNull(label);
        Objects.requireNonNull(clientHelloRandom);
        Objects.requireNonNull(secret);
        if (keyUpdateCount < 0) {
            throw new IllegalArgumentException(
                    "Key update count should be >= 0");
        }
        if (keyUpdateCount > 0
                && label != TLSKeyLoggerLabel.CLIENT_TRAFFIC_SECRET
                && label != TLSKeyLoggerLabel.SERVER_TRAFFIC_SECRET) {
            throw new IllegalArgumentException(
                    "Non-zero key update count cannot be used for " + label);
        }

        final String labelVal = switch (label) {
            case CLIENT_TRAFFIC_SECRET, SERVER_TRAFFIC_SECRET ->
                    label.name() + "_" + keyUpdateCount;
            default -> label.name();
        };
        final StringBuilder line = new StringBuilder(labelVal).append(" ");
        line.append(hexFormat.formatHex(clientHelloRandom)).append(" ");
        line.append(hexFormat.formatHex(secret.getEncoded()));
        // RFC states that the line separator should correspond to the OS where
        // the file was generated
        line.append(System.lineSeparator());
        try {
            // TODO: security manager
            // TODO: consider thread safety
            Files.writeString(keyLogFile, line.toString(), US_ASCII,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException | SecurityException e) {
            // ignore
        }
    }
}
