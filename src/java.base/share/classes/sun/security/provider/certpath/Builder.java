/*
 * Copyright (c) 2000, 2025, Oracle and/or its affiliates. All rights reserved.
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

package sun.security.provider.certpath;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.cert.*;
import java.util.*;

import sun.security.provider.certpath.PKIX.BuilderParams;
import sun.security.util.Debug;

/**
 * Abstract class representing a builder, which is able to retrieve
 * matching certificates and is able to verify a particular certificate.
 *
 * @since       1.4
 * @author      Sean Mullan
 * @author      Yassir Elley
 */

abstract class Builder {

    private static final Debug debug = Debug.getInstance("certpath");
    private Set<String> matchingPolicies;
    final BuilderParams buildParams;
    final X509CertSelector targetCertConstraints;

    /**
     * Flag indicating whether support for the caIssuers field of the
     * Authority Information Access extension shall be enabled. Currently
     * disabled by default for compatibility reasons.
     */
    static final boolean USE_AIA =
            Boolean.getBoolean("com.sun.security.enableAIAcaIssuers");

    /**
     * Initialize the builder with the input parameters.
     *
     * @param buildParams the parameter set used to build a certification path
     */
    Builder(BuilderParams buildParams) {
        this.buildParams = buildParams;
        this.targetCertConstraints =
            (X509CertSelector)buildParams.targetCertConstraints();
    }

    /**
     * Retrieves certificates from the list of certStores using the buildParams
     * and the currentState as a filter
     *
     * @param currentState the current State
     * @param certStores list of CertStores
     */
    abstract Collection<X509Certificate> getMatchingCerts
        (State currentState, List<CertStore> certStores)
        throws CertStoreException, CertificateException, IOException;

    /**
     * Verifies the cert against the currentState, using the certPathList
     * generated thus far to help with loop detection
     *
     * @param cert the certificate to be verified
     * @param currentState the current state against which the cert is verified
     * @param certPathList the certPathList generated thus far
     */
    abstract void verifyCert(X509Certificate cert, State currentState,
                             List<X509Certificate> certPathList)
        throws GeneralSecurityException;

    /**
     * Verifies whether the input certificate completes the path.
     * When building in the forward direction, a trust anchor will
     * complete the path.
     *
     * @param cert the certificate to test
     * @return a boolean value indicating whether the cert completes the path.
     */
    abstract boolean isPathCompleted(X509Certificate cert);

    /**
     * Adds the certificate to the certPathList
     *
     * @param cert the certificate to be added
     * @param certPathList the certification path list
     */
    abstract void addCertToPath(X509Certificate cert,
                                LinkedList<X509Certificate> certPathList);

    /**
     * Removes final certificate from the certPathList
     *
     * @param certPathList the certification path list
     */
    abstract void removeFinalCertFromPath
        (LinkedList<X509Certificate> certPathList);

    /**
     * This method can be used as an optimization to filter out
     * certificates that do not have policies which are valid.
     * It returns the set of policies (String OIDs) that should exist in
     * the certificate policies extension of the certificate that is
     * needed by the builder. The logic applied is as follows:
     * <p>
     *   1) If some initial policies have been set *and* policy mappings are
     *   inhibited, then acceptable certificates are those that include
     *   the ANY_POLICY OID or with policies that intersect with the
     *   initial policies.
     *   2) If no initial policies have been set *or* policy mappings are
     *   not inhibited then we don't have much to work with. All we know is
     *   that a certificate must have *some* policy because if it didn't
     *   have any policy then the policy tree would become null (and validation
     *   would fail).
     *
     * @return the Set of policies any of which must exist in a
     * cert's certificate policies extension in order for a cert to be selected.
     */
    Set<String> getMatchingPolicies() {
        if (matchingPolicies == null) {
            Set<String> initialPolicies = buildParams.initialPolicies();
            if ((!initialPolicies.isEmpty()) &&
                (!initialPolicies.contains(PolicyChecker.ANY_POLICY)) &&
                (buildParams.policyMappingInhibited()))
            {
                matchingPolicies = new HashSet<>(initialPolicies);
                matchingPolicies.add(PolicyChecker.ANY_POLICY);
            } else {
                // we just return an empty set to make sure that there is
                // at least a certificate policies extension in the cert
                matchingPolicies = Collections.emptySet();
            }
        }
        return matchingPolicies;
    }

    /**
     * Search the specified CertStores and add all certificates matching
     * selector to resultCerts.
     *
     * If the targetCert criterion of the selector is set, only that cert
     * is examined and the CertStores are not searched.
     *
     * If checkAll is true, all CertStores are searched for matching certs.
     * If false, the method returns as soon as the first CertStore returns
     * a matching cert(s).
     *
     * Returns true iff resultCerts changed (a cert was added to the collection)
     */
    boolean addMatchingCerts(X509CertSelector selector,
                             Collection<CertStore> certStores,
                             Collection<X509Certificate> resultCerts,
                             boolean checkAll)
    {
        X509Certificate targetCert = selector.getCertificate();
        if (targetCert != null) {
            // no need to search CertStores
            if (selector.match(targetCert)) {
                if (debug != null) {
                    debug.println("Builder.addMatchingCerts: " +
                        "adding target cert" +
                        "\n  SN: " + Debug.toString(targetCert.getSerialNumber()) +
                        "\n  Subject: " + targetCert.getSubjectX500Principal() +
                        "\n  Issuer: " + targetCert.getIssuerX500Principal());
                }
                return resultCerts.add(targetCert);
            }
            return false;
        }
        boolean add = false;
        for (CertStore store : certStores) {
            try {
                Collection<? extends Certificate> certs =
                                        store.getCertificates(selector);
                for (Certificate cert : certs) {
                    if (resultCerts.add((X509Certificate)cert)) {
                        add = true;
                    }
                }
                if (!checkAll && add) {
                    return true;
                }
            } catch (CertStoreException cse) {
                // if getCertificates throws a CertStoreException, we ignore
                // it and move on to the next CertStore
                if (debug != null) {
                    debug.println("Builder.addMatchingCerts, non-fatal " +
                        "exception retrieving certs: " + cse);
                    cse.printStackTrace();
                }
            }
        }
        return add;
    }
}
