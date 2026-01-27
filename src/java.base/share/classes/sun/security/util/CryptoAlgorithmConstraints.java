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
import java.net.URL;
import java.security.AlgorithmParameters;
import java.security.CodeSource;
import java.security.CryptoPrimitive;
import java.security.Key;
import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
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

    private static final String PROPERTY_CRYPTO_LEGACY_ALGS =
            "jdk.crypto.legacyAlgorithms";

    private static class CryptoHolder {
        static final CryptoAlgorithmConstraints DISABLED_CONSTRAINTS =
                new CryptoAlgorithmConstraints(PROPERTY_CRYPTO_DISABLED_ALGS);
    }
    private static class LegacyHolder {
        static final CryptoAlgorithmConstraints LEGACY_CONSTRAINTS =
                new CryptoAlgorithmConstraints(PROPERTY_CRYPTO_LEGACY_ALGS);
    }

    private static void debug(String msg) {
        if (debug != null) {
            debug.println("CryptoAlgoConstraints: ", msg);
        }
    }

    public static boolean permits(String service, String algo) {
        return CryptoHolder.DISABLED_CONSTRAINTS.cachedCheckAlgorithm(
                service + "." + algo);
    }

    private record CallerInfo(Class<?> caller, String serviceAndAlg) { }

    private static class CallersHolder {
        static final Map<CallerInfo, Boolean> callers
            = Collections.synchronizedMap(new WeakHashMap<>());
    }

    public static void warn(String service, String alg, Class<?> callerClass) {
        String serviceAndAlg = service + "." + alg;
        if (!LegacyHolder.LEGACY_CONSTRAINTS.cachedCheckAlgorithm(
                serviceAndAlg) && CallersHolder.callers.putIfAbsent(
                    new CallerInfo(callerClass, serviceAndAlg), true) == null) {
                URL url = codeSource(callerClass);
                String source = (url == null) ? callerClass.getName() : 
                                    callerClass.getName() + " (" + url + ")";
                System.err.printf("""
                    WARNING: An outdated %s algorithm has been called by %s
                    WARNING: %s will be disabled by default in a future release
                    """, service, callerClass.getName(), alg);
        }
    }

    private static URL codeSource(Class<?> clazz) {
        CodeSource cs = clazz.getProtectionDomain().getCodeSource();
        return (cs != null) ? cs.getLocation() : null;
    }

    private final Set<String> affectedServices; // syntax is <service>.<algo>
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
        affectedServices = getAlgorithms(propertyName, true);
        String[] entries = affectedServices.toArray(new String[0]);
        debug("Before " + Arrays.deepToString(entries));

        for (String k : entries) {
            int idx = k.indexOf(".");
            if (idx < 1 || idx == k.length() - 1) {
                // wrong syntax: missing "." or empty service or algorithm
                throw new IllegalArgumentException("Invalid entry: " + k);
            }
            String service = k.substring(0, idx);
            String algo = k.substring(idx + 1);
            if (SUPPORTED_SERVICES.stream().anyMatch(e -> e.equalsIgnoreCase
                    (service))) {
                KnownOIDs oid = KnownOIDs.findMatch(algo);
                if (oid != null) {
                    debug("Add oid: " + oid.value());
                    affectedServices.add(service + "." + oid.value());
                    debug("Add oid stdName: " + oid.stdName());
                    affectedServices.add(service + "." + oid.stdName());
                    for (String a : oid.aliases()) {
                        debug("Add oid alias: " + a);
                        affectedServices.add(service + "." + a);
                    }
                }
            } else {
                // unsupported service
                throw new IllegalArgumentException("Invalid entry: " + k);
            }
        }
        debug("After " + Arrays.deepToString(affectedServices.toArray()));
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
        result = checkAlgorithm(affectedServices, serviceDesc, null);
        cache.put(serviceDesc, result);
        return result;
    }
}
