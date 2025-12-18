/*
 * Copyright (c) 2005, 2025, Oracle and/or its affiliates. All rights reserved.
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

package jdk.test.lib.net;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.io.*;
import java.security.*;
import javax.net.ssl.*;

/**
 * Utility for creating a simple usable {@link SSLContext} for testing purposes.
 */
public final class SimpleSSLContext {

    private static final String DEFAULT_PROTOCOL = "TLS";

    private static final String DEFAULT_KEY_STORE_FILE_REL_PATH = "jdk/test/lib/net/testkeys";

    private final SSLContext ssl;

    // Made `public` for backward compatibility
    public SimpleSSLContext() throws IOException {
        this.ssl = findSSLContext(DEFAULT_KEY_STORE_FILE_REL_PATH, DEFAULT_PROTOCOL);
    }

    // Kept for backward compatibility
    public SimpleSSLContext(String keyStoreFileRelPath) throws IOException {
        this.ssl = findSSLContext(Objects.requireNonNull(keyStoreFileRelPath), DEFAULT_PROTOCOL);
    }

    /**
     * {@return a new {@link SSLContext} instance by searching for a key store
     * file path, and loading the first found one}
     *
     * @throws RuntimeException if no key store file can be found or the found
     * one cannot be loaded
     */
    public static SSLContext findSSLContext() {
        return findSSLContext(DEFAULT_PROTOCOL);
    }

    /**
     * {@return a new {@link SSLContext} instance by searching for a key store
     * file path, and loading the first found one}
     *
     * @param protocol an {@link SSLContext} protocol
     *
     * @throws NullPointerException if {@code protocol} is null
     * @throws RuntimeException if no key store file can be found or the found
     * one cannot be loaded
     */
    public static SSLContext findSSLContext(String protocol) {
        Objects.requireNonNull(protocol);
        return findSSLContext(DEFAULT_KEY_STORE_FILE_REL_PATH, protocol);
    }

    /**
     * {@return a new {@link SSLContext} instance by searching for a key store
     * file path, and loading the first found one}
     *
     * @param keyStoreFileRelPath a key store file path to be concatenated with
     *                            the search path(s) obtained from the
     *                            {@code test.src.path} system property
     * @param protocol an {@link SSLContext} protocol
     *
     * @throws NullPointerException if {@code keyStoreFileRelPath} or {@code protocol} is null
     * @throws RuntimeException if no key store file can be found or the found
     * one cannot be loaded
     */
    public static SSLContext findSSLContext(String keyStoreFileRelPath, String protocol) {
        Objects.requireNonNull(keyStoreFileRelPath);
        Objects.requireNonNull(protocol);
        var sourcePaths = System.getProperty("test.src.path");
        for (var sourcePath : Collections.list(new StringTokenizer(sourcePaths, File.pathSeparator))) {
            var keyStoreFileAbsPath = Path.of((String) sourcePath, keyStoreFileRelPath);
            if (Files.exists(keyStoreFileAbsPath)) {
                return loadSSLContext(keyStoreFileAbsPath, protocol);
            }
        }
        throw new RuntimeException(
                "Could not find any key store at source path(s) '%s' using key store file relative path '%s'".formatted(
                        sourcePaths, keyStoreFileRelPath));
    }

    /**
     * {@return a new {@link SSLContext} loaded from the provided key store file
     * path using the given protocol}
     *
     * @param keyStoreFilePath a {@link KeyStore} file path
     * @param protocol an {@link SSLContext} protocol
     *
     * @throws RuntimeException if loading fails
     */
    private static SSLContext loadSSLContext(Path keyStoreFilePath, String protocol) {
        try (var storeStream = Files.newInputStream(keyStoreFilePath)) {
            char[] passphrase = "passphrase".toCharArray();
            KeyStore ks = KeyStore.getInstance("PKCS12");
            ks.load(storeStream, passphrase);

            KeyManagerFactory kmf = KeyManagerFactory.getInstance("PKIX");
            kmf.init(ks, passphrase);

            TrustManagerFactory tmf = TrustManagerFactory.getInstance("PKIX");
            tmf.init(ks);

            var sslContext = SSLContext.getInstance(protocol);
            sslContext.init(kmf.getKeyManagers(), tmf.getTrustManagers(), null);
            return sslContext;
        } catch (Exception e) {
            var message = "Failed loading 'SSLContext' from key store at location '%s' for protocol '%s'".formatted(
                    keyStoreFilePath, protocol);
            throw new RuntimeException(message, e);
        }
    }

    // Kept for backward compatibility
    public static SSLContext getContext(String protocol) throws IOException {
        try {
            return protocol == null || protocol.isEmpty()
                    ? findSSLContext()
                    : findSSLContext(protocol);
        } catch (RuntimeException re) {
            throw new IOException(re);
        }
    }

    // Kept for backward compatibility
    public SSLContext get() {
        return ssl;
    }

}
