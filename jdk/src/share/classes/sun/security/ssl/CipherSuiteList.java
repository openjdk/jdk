/*
 * Copyright (c) 2002, 2007, Oracle and/or its affiliates. All rights reserved.
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

import java.io.*;
import java.util.*;

import javax.net.ssl.SSLException;

/**
 * A list of CipherSuites. Also maintains the lists of supported and
 * default ciphersuites and supports I/O from handshake streams.
 *
 * Instances of this class are immutable.
 *
 */
final class CipherSuiteList {

    // lists of supported and default enabled ciphersuites
    // created on demand
    private static CipherSuiteList supportedSuites, defaultSuites;

    private final Collection<CipherSuite> cipherSuites;
    private String[] suiteNames;

    // flag indicating whether this list contains any ECC ciphersuites.
    // null if not yet checked.
    private volatile Boolean containsEC;

    // for use by buildAvailableCache() only
    private CipherSuiteList(Collection<CipherSuite> cipherSuites) {
        this.cipherSuites = cipherSuites;
    }

    /**
     * Create a CipherSuiteList with a single element.
     */
    CipherSuiteList(CipherSuite suite) {
        cipherSuites = new ArrayList<CipherSuite>(1);
        cipherSuites.add(suite);
    }

    /**
     * Construct a CipherSuiteList from a array of names. We don't bother
     * to eliminate duplicates.
     *
     * @exception IllegalArgumentException if the array or any of its elements
     * is null or if the ciphersuite name is unrecognized or unsupported
     * using currently installed providers.
     */
    CipherSuiteList(String[] names) {
        if (names == null) {
            throw new IllegalArgumentException("CipherSuites may not be null");
        }
        cipherSuites = new ArrayList<CipherSuite>(names.length);
        // refresh available cache once if a CipherSuite is not available
        // (maybe new JCE providers have been installed)
        boolean refreshed = false;
        for (int i = 0; i < names.length; i++) {
            String suiteName = names[i];
            CipherSuite suite = CipherSuite.valueOf(suiteName);
            if (suite.isAvailable() == false) {
                if (refreshed == false) {
                    // clear the cache so that the isAvailable() call below
                    // does a full check
                    clearAvailableCache();
                    refreshed = true;
                }
                // still missing?
                if (suite.isAvailable() == false) {
                    throw new IllegalArgumentException("Cannot support "
                        + suiteName + " with currently installed providers");
                }
            }
            cipherSuites.add(suite);
        }
    }

    /**
     * Read a CipherSuiteList from a HandshakeInStream in V3 ClientHello
     * format. Does not check if the listed ciphersuites are known or
     * supported.
     */
    CipherSuiteList(HandshakeInStream in) throws IOException {
        byte[] bytes = in.getBytes16();
        if ((bytes.length & 1) != 0) {
            throw new SSLException("Invalid ClientHello message");
        }
        cipherSuites = new ArrayList<CipherSuite>(bytes.length >> 1);
        for (int i = 0; i < bytes.length; i += 2) {
            cipherSuites.add(CipherSuite.valueOf(bytes[i], bytes[i+1]));
        }
    }

    /**
     * Return whether this list contains the given CipherSuite.
     */
    boolean contains(CipherSuite suite) {
        return cipherSuites.contains(suite);
    }

    // Return whether this list contains any ECC ciphersuites
    boolean containsEC() {
        if (containsEC == null) {
            for (CipherSuite c : cipherSuites) {
                switch (c.keyExchange) {
                case K_ECDH_ECDSA:
                case K_ECDH_RSA:
                case K_ECDHE_ECDSA:
                case K_ECDHE_RSA:
                case K_ECDH_ANON:
                    containsEC = true;
                    return true;
                default:
                    break;
                }
            }
            containsEC = false;
        }
        return containsEC;
    }

    /**
     * Return an Iterator for the CipherSuites in this list.
     */
    Iterator<CipherSuite> iterator() {
        return cipherSuites.iterator();
    }

    /**
     * Return a reference to the internal Collection of CipherSuites.
     * The Collection MUST NOT be modified.
     */
    Collection<CipherSuite> collection() {
        return cipherSuites;
    }

    /**
     * Return the number of CipherSuites in this list.
     */
    int size() {
        return cipherSuites.size();
    }

    /**
     * Return an array with the names of the CipherSuites in this list.
     */
    synchronized String[] toStringArray() {
        if (suiteNames == null) {
            suiteNames = new String[cipherSuites.size()];
            int i = 0;
            for (CipherSuite c : cipherSuites) {
                suiteNames[i++] = c.name;
            }
        }
        return suiteNames.clone();
    }

    public String toString() {
        return cipherSuites.toString();
    }

    /**
     * Write this list to an HandshakeOutStream in V3 ClientHello format.
     */
    void send(HandshakeOutStream s) throws IOException {
        byte[] suiteBytes = new byte[cipherSuites.size() * 2];
        int i = 0;
        for (CipherSuite c : cipherSuites) {
            suiteBytes[i] = (byte)(c.id >> 8);
            suiteBytes[i+1] = (byte)c.id;
            i += 2;
        }
        s.putBytes16(suiteBytes);
    }

    /**
     * Clear cache of available ciphersuites. If we support all ciphers
     * internally, there is no need to clear the cache and calling this
     * method has no effect.
     */
    static synchronized void clearAvailableCache() {
        if (CipherSuite.DYNAMIC_AVAILABILITY) {
            supportedSuites = null;
            defaultSuites = null;
            CipherSuite.BulkCipher.clearAvailableCache();
            JsseJce.clearEcAvailable();
        }
    }

    /**
     * Return the list of all available CipherSuites with a priority of
     * minPriority or above.
     * Should be called with the Class lock held.
     */
    private static CipherSuiteList buildAvailableCache(int minPriority) {
        // SortedSet automatically arranges ciphersuites in default
        // preference order
        Set<CipherSuite> cipherSuites = new TreeSet<CipherSuite>();
        Collection<CipherSuite> allowedCipherSuites = CipherSuite.allowedCipherSuites();
        for (CipherSuite c : allowedCipherSuites) {
            if ((c.allowed == false) || (c.priority < minPriority)) {
                continue;
            }
            if (c.isAvailable()) {
                cipherSuites.add(c);
            }
        }
        return new CipherSuiteList(cipherSuites);
    }

    /**
     * Return supported CipherSuites in preference order.
     */
    static synchronized CipherSuiteList getSupported() {
        if (supportedSuites == null) {
            supportedSuites =
                buildAvailableCache(CipherSuite.SUPPORTED_SUITES_PRIORITY);
        }
        return supportedSuites;
    }

    /**
     * Return default enabled CipherSuites in preference order.
     */
    static synchronized CipherSuiteList getDefault() {
        if (defaultSuites == null) {
            defaultSuites =
                buildAvailableCache(CipherSuite.DEFAULT_SUITES_PRIORITY);
        }
        return defaultSuites;
    }

}
