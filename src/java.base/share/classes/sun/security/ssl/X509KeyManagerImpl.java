/*
 * Copyright (c) 2004, 2025, Oracle and/or its affiliates. All rights reserved.
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

import java.lang.ref.*;
import java.net.Socket;
import java.security.AlgorithmConstraints;
import java.security.KeyStore;
import java.security.KeyStore.Builder;
import java.security.KeyStore.Entry;
import java.security.KeyStore.PrivateKeyEntry;
import java.security.KeyStoreException;
import java.security.Principal;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import javax.net.ssl.*;
import javax.security.auth.x500.X500Principal;

/**
 * The new X509 key manager implementation. The main differences to the
 * old SunX509 key manager are:
 *  . it is based around the KeyStore.Builder API. This allows it to use
 *    other forms of KeyStore protection or password input (e.g. a
 *    CallbackHandler) or to have keys within one KeyStore protected by
 *    different keys.
 *  . it can use multiple KeyStores at the same time.
 *  . it is explicitly designed to accommodate KeyStores that change over
 *    the lifetime of the process.
 *  . it makes an effort to choose the key that matches best, i.e. one that
 *    is not expired and has the appropriate certificate extensions.
 *
 * Note that this code is not explicitly performance optimized yet.
 *
 * @author  Andreas Sterbenz
 */

final class X509KeyManagerImpl extends X509KeyManagerCertChecking {

    // for unit testing only, set via privileged reflection
    private static Date verificationDate;

    // list of the builders
    private final List<Builder> builders;

    // counter to generate unique ids for the aliases
    private final AtomicLong uidCounter;

    // cached entries
    private final Map<String,Reference<PrivateKeyEntry>> entryCacheMap;

    X509KeyManagerImpl(Builder builder) {
        this(Collections.singletonList(builder));
    }

    X509KeyManagerImpl(List<Builder> builders) {
        this.builders = builders;
        uidCounter = new AtomicLong();
        entryCacheMap = Collections.synchronizedMap(new SizedMap<>());
    }

    @Override
    protected boolean isCheckingDisabled() {
        return false;
    }

    // LinkedHashMap with a max size of 10, see LinkedHashMap JavaDocs
    private static class SizedMap<K,V> extends LinkedHashMap<K,V> {
        @java.io.Serial
        private static final long serialVersionUID = -8211222668790986062L;

        @Override protected boolean removeEldestEntry(Map.Entry<K,V> eldest) {
            return size() > 10;
        }
    }

    //
    // public methods
    //

    @Override
    public X509Certificate[] getCertificateChain(String alias) {
        PrivateKeyEntry entry = getEntry(alias);
        return entry == null ? null :
                (X509Certificate[])entry.getCertificateChain();
    }

    @Override
    public PrivateKey getPrivateKey(String alias) {
        PrivateKeyEntry entry = getEntry(alias);
        return entry == null ? null : entry.getPrivateKey();
    }

    @Override
    public String chooseClientAlias(String[] keyTypes, Principal[] issuers,
            Socket socket) {
        return chooseAlias(getKeyTypes(keyTypes), issuers, CheckType.CLIENT,
                getAlgorithmConstraints(socket), null, null);
    }

    @Override
    public String chooseEngineClientAlias(String[] keyTypes,
            Principal[] issuers, SSLEngine engine) {
        return chooseAlias(getKeyTypes(keyTypes), issuers, CheckType.CLIENT,
                getAlgorithmConstraints(engine), null, null);
    }

    @Override
    public String chooseServerAlias(String keyType,
            Principal[] issuers, Socket socket) {
        return chooseAlias(getKeyTypes(keyType), issuers, CheckType.SERVER,
            getAlgorithmConstraints(socket),
            X509TrustManagerImpl.getRequestedServerNames(socket),
            "HTTPS");    // The SNI HostName is a fully qualified domain name.
                         // The certificate selection scheme for SNI HostName
                         // is similar to HTTPS endpoint identification scheme
                         // implemented in this provider.
                         //
                         // Using HTTPS endpoint identification scheme to guide
                         // the selection of an appropriate authentication
                         // certificate according to requested SNI extension.
                         //
                         // It is not a really HTTPS endpoint identification.
    }

    @Override
    public String chooseEngineServerAlias(String keyType,
            Principal[] issuers, SSLEngine engine) {
        return chooseAlias(getKeyTypes(keyType), issuers, CheckType.SERVER,
            getAlgorithmConstraints(engine),
            X509TrustManagerImpl.getRequestedServerNames(engine),
            "HTTPS");    // The SNI HostName is a fully qualified domain name.
                         // The certificate selection scheme for SNI HostName
                         // is similar to HTTPS endpoint identification scheme
                         // implemented in this provider.
                         //
                         // Using HTTPS endpoint identification scheme to guide
                         // the selection of an appropriate authentication
                         // certificate according to requested SNI extension.
                         //
                         // It is not a really HTTPS endpoint identification.
    }

    @Override
    public String[] getClientAliases(String keyType, Principal[] issuers) {
        return getAliases(keyType, issuers, CheckType.CLIENT);
    }

    @Override
    public String[] getServerAliases(String keyType, Principal[] issuers) {
        return getAliases(keyType, issuers, CheckType.SERVER);
    }

    //
    // implementation private methods
    //

    // we construct the alias we return to JSSE as seen in the code below
    // a unique id is included to allow us to reliably cache entries
    // between the calls to getCertificateChain() and getPrivateKey()
    // even if tokens are inserted or removed
    private String makeAlias(EntryStatus entry) {
        return uidCounter.incrementAndGet() + "." + entry.keyStoreIndex + "."
                + entry.alias;
    }

    private PrivateKeyEntry getEntry(String alias) {
        // if the alias is null, return immediately
        if (alias == null) {
            return null;
        }

        // try to get the entry from cache
        Reference<PrivateKeyEntry> ref = entryCacheMap.get(alias);
        PrivateKeyEntry entry = (ref != null) ? ref.get() : null;
        if (entry != null) {
            return entry;
        }

        // parse the alias
        int firstDot = alias.indexOf('.');
        int secondDot = alias.indexOf('.', firstDot + 1);
        if ((firstDot == -1) || (secondDot == firstDot)) {
            // invalid alias
            return null;
        }
        try {
            int builderIndex = Integer.parseInt
                                (alias.substring(firstDot + 1, secondDot));
            String keyStoreAlias = alias.substring(secondDot + 1);
            Builder builder = builders.get(builderIndex);
            KeyStore ks = builder.getKeyStore();
            Entry newEntry = ks.getEntry(keyStoreAlias,
                    builder.getProtectionParameter(keyStoreAlias));
            if (!(newEntry instanceof PrivateKeyEntry)) {
                // unexpected type of entry
                return null;
            }
            entry = (PrivateKeyEntry)newEntry;
            entryCacheMap.put(alias, new SoftReference<>(entry));
            return entry;
        } catch (Exception e) {
            // ignore
            return null;
        }
    }

    /*
     * Return the best alias that fits the given parameters.
     * The algorithm we use is:
     *   . scan through all the aliases in all builders in order
     *   . as soon as we find a perfect match, return
     *     (i.e. a match with a cert that has appropriate key usage,
     *      qualified endpoint identity, and is not expired).
     *   . if we do not find a perfect match, keep looping and remember
     *     the imperfect matches
     *   . at the end, sort the imperfect matches. we prefer expired certs
     *     with appropriate key usage to certs with the wrong key usage.
     *     return the first one of them.
     */
    private String chooseAlias(List<KeyType> keyTypeList, Principal[] issuers,
            CheckType checkType, AlgorithmConstraints constraints,
            List<SNIServerName> requestedServerNames, String idAlgorithm) {

        if (keyTypeList == null || keyTypeList.isEmpty()) {
            return null;
        }

        Set<X500Principal> issuerSet = getIssuerSet(issuers);
        List<EntryStatus> allResults = null;

        for (int i = 0, n = builders.size(); i < n; i++) {
            try {
                List<EntryStatus> results = getAliases(i, keyTypeList,
                            issuerSet, false, checkType, constraints,
                            requestedServerNames, idAlgorithm);
                if (results != null) {
                    for (EntryStatus status : results) {
                        if (status.checkResult == CheckResult.OK) {
                            if (SSLLogger.isOn && SSLLogger.isOn("keymanager")) {
                                SSLLogger.fine("KeyMgr: choosing key: " + status);
                            }
                            return makeAlias(status);
                        }
                    }
                    if (allResults == null) {
                        allResults = new ArrayList<>();
                    }
                    allResults.addAll(results);
                }
            } catch (KeyStoreException e) {
                // ignore
            }
        }
        if (allResults == null) {
            if (SSLLogger.isOn && SSLLogger.isOn("keymanager")) {
                SSLLogger.fine("KeyMgr: no matching key found");
            }
            return null;
        }
        Collections.sort(allResults);
        if (SSLLogger.isOn && SSLLogger.isOn("keymanager")) {
            SSLLogger.fine(
                    "KeyMgr: no good matching key found, "
                    + "returning best match out of", allResults);
        }
        return makeAlias(allResults.get(0));
    }

    /*
     * Return all aliases that (approximately) fit the parameters.
     * These are perfect matches plus imperfect matches (expired certificates
     * and certificates with the wrong extensions).
     * The perfect matches will be first in the array.
     */
    private String[] getAliases(
            String keyType, Principal[] issuers, CheckType checkType) {

        if (keyType == null) {
            return null;
        }

        Set<X500Principal> issuerSet = getIssuerSet(issuers);
        List<KeyType> keyTypeList = getKeyTypes(keyType);
        List<EntryStatus> allResults = null;

        for (int i = 0, n = builders.size(); i < n; i++) {
            try {
                List<EntryStatus> results = getAliases(i, keyTypeList,
                        issuerSet, true, checkType, null, null, null);
                if (results != null) {
                    if (allResults == null) {
                        allResults = new ArrayList<>();
                    }
                    allResults.addAll(results);
                }
            } catch (KeyStoreException e) {
                // ignore
            }
        }
        if (allResults == null || allResults.isEmpty()) {
            if (SSLLogger.isOn && SSLLogger.isOn("keymanager")) {
                SSLLogger.fine("KeyMgr: no matching alias found");
            }
            return null;
        }
        Collections.sort(allResults);
        if (SSLLogger.isOn && SSLLogger.isOn("keymanager")) {
            SSLLogger.fine("KeyMgr: getting aliases", allResults);
        }
        return toAliases(allResults);
    }

    // turn candidate entries into unique aliases we can return to JSSE
    private String[] toAliases(List<EntryStatus> results) {
        String[] s = new String[results.size()];
        int i = 0;
        for (EntryStatus result : results) {
            s[i++] = makeAlias(result);
        }
        return s;
    }

    /*
     * Return a List of all candidate matches in the specified builder
     * that fit the parameters.
     * We exclude entries in the KeyStore if they are not:
     *  . private key entries
     *  . the certificates are not X509 certificates
     *  . the algorithm of the key in the EE cert doesn't match one of keyTypes
     *  . none of the certs is issued by a Principal in issuerSet
     * Using those entries would not be possible or they would almost
     * certainly be rejected by the peer.
     *
     * In addition to those checks, we also check the extensions in the EE
     * cert and its expiration. Even if there is a mismatch, we include
     * such certificates because they technically work and might be accepted
     * by the peer. This leads to more graceful failure and better error
     * messages if the cert expires from one day to the next.
     *
     * The return values are:
     *   . null, if there are no matching entries at all
     *   . if 'findAll' is 'false' and there is a perfect match, a List
     *     with a single element (early return)
     *   . if 'findAll' is 'false' and there is NO perfect match, a List
     *     with all the imperfect matches (expired, wrong extensions)
     *   . if 'findAll' is 'true', a List with all perfect and imperfect
     *     matches
     */
    private List<EntryStatus> getAliases(int builderIndex,
            List<KeyType> keyTypes, Set<X500Principal> issuerSet,
            boolean findAll, CheckType checkType,
            AlgorithmConstraints constraints,
            List<SNIServerName> requestedServerNames,
            String idAlgorithm) throws KeyStoreException {

        Builder builder = builders.get(builderIndex);
        KeyStore ks = builder.getKeyStore();
        List<EntryStatus> results = null;
        boolean preferred = false;

        for (Enumeration<String> e = ks.aliases(); e.hasMoreElements(); ) {

            String alias = e.nextElement();

            // check if it is a key entry (private key or secret key)
            if (!ks.isKeyEntry(alias)) {
                continue;
            }

            EntryStatus status = checkAlias(builderIndex, alias,
                    ks.getCertificateChain(alias),
                    verificationDate, keyTypes, issuerSet, checkType,
                    constraints, requestedServerNames, idAlgorithm);

            if (status == null) {
                continue;
            }

            if (!preferred && status.checkResult == CheckResult.OK
                    && status.keyIndex == 0) {
                preferred = true;
            }

            if (preferred && !findAll) {
                // If we have a good match and do not need all matches,
                // return immediately
                return Collections.singletonList(status);
            } else {
                if (results == null) {
                    results = new ArrayList<>();
                }
                results.add(status);
            }
        }

        return results;
    }
}
