/*
 * Copyright (c) 2025, 2026, Oracle and/or its affiliates. All rights reserved.
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

package sun.security.util;

import java.lang.ref.SoftReference;
import java.security.AlgorithmParameters;
import java.security.CryptoPrimitive;
import java.security.Key;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * This class implements the algorithm constraints for the
 * "jdk.crypto.disabledAlgorithms" security property. This security property
 * can be overridden by the system property of the same name. See the
 * java.security file for the syntax of the property value.
 */
public class CryptoAlgorithmConstraints extends AbstractAlgorithmConstraints {
    private static final Debug debug = Debug.getInstance("jca");

    // for validating the service
    private static final Set<String> SUPPORTED_SERVICES =
            Set.of("Cipher", "KeyStore", "MessageDigest", "Signature");

    // Disabled algorithm security property for JCE crypto services
    private static final String PROPERTY_CRYPTO_DISABLED_ALGS =
            "jdk.crypto.disabledAlgorithms";

    private static class CryptoHolder {
        static final CryptoAlgorithmConstraints CONSTRAINTS =
                new CryptoAlgorithmConstraints(PROPERTY_CRYPTO_DISABLED_ALGS);
    }

    private static void debug(String msg) {
        if (debug != null) {
            debug.println("CryptoAlgoConstraints: ", msg);
        }
    }

    public static boolean permits(String service, String algo) {
        return CryptoHolder.CONSTRAINTS.cachedCheckAlgorithm(
                service + "." + algo);
    }

    private final Set<String> disabledServices; // syntax is <service>.<algo>
    private volatile SoftReference<Map<String, Boolean>> cacheRef =
            new SoftReference<>(null);

    /**
     * Initialize algorithm constraints with the specified security property
     * {@code propertyName}. Note that if a system property of the same name
     * is set, it overrides the security property.
     *
     * @param propertyName the security property name that define the disabled
     *        algorithm constraints
     */
    CryptoAlgorithmConstraints(String propertyName) {
        super(null);
        disabledServices = getAlgorithms(propertyName, true);
        String[] entries = disabledServices.toArray(new String[0]);
        debug("Before " + Arrays.deepToString(entries));

        for (String dk : entries) {
            int idx = dk.indexOf(".");
            if (idx < 1 || idx == dk.length() - 1) {
                // wrong syntax: missing "." or empty service or algorithm
                throw new IllegalArgumentException("Invalid entry: " + dk);
            }
            String service = dk.substring(0, idx);
            String algo = dk.substring(idx + 1);
            if (SUPPORTED_SERVICES.stream().anyMatch(e -> e.equalsIgnoreCase
                    (service))) {
                KnownOIDs oid = KnownOIDs.findMatch(algo);
                if (oid != null) {
                    debug("Add oid: " + oid.value());
                    disabledServices.add(service + "." + oid.value());
                    debug("Add oid stdName: " + oid.stdName());
                    disabledServices.add(service + "." + oid.stdName());
                    for (String a : oid.aliases()) {
                        debug("Add oid alias: " + a);
                        disabledServices.add(service + "." + a);
                    }
                }
            } else {
                // unsupported service
                throw new IllegalArgumentException("Invalid entry: " + dk);
            }
        }
        debug("After " + Arrays.deepToString(disabledServices.toArray()));
    }

    @Override
    public final boolean permits(Set<CryptoPrimitive> notUsed1,
            String serviceDesc, AlgorithmParameters notUsed2) {
        throw new UnsupportedOperationException("Unsupported permits() method");
    }

    @Override
    public final boolean permits(Set<CryptoPrimitive> primitives, Key key) {
        throw new UnsupportedOperationException("Unsupported permits() method");
    }

    @Override
    public final boolean permits(Set<CryptoPrimitive> primitives,
            String algorithm, Key key, AlgorithmParameters parameters) {
        throw new UnsupportedOperationException("Unsupported permits() method");
    }

    // Return false if algorithm is found in the disabledServices Set.
    // Otherwise, return true.
    private boolean cachedCheckAlgorithm(String serviceDesc) {
        Map<String, Boolean> cache;
        if ((cache = cacheRef.get()) == null) {
            synchronized (this) {
                if ((cache = cacheRef.get()) == null) {
                    cache = new ConcurrentHashMap<>();
                    cacheRef = new SoftReference<>(cache);
                }
            }
        }
        Boolean result = cache.get(serviceDesc);
        if (result != null) {
            return result;
        }
        result = checkAlgorithm(disabledServices, serviceDesc, null);
        cache.put(serviceDesc, result);
        return result;
    }
}
