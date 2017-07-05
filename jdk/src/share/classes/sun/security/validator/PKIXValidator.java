/*
 * Copyright (c) 2002, 2014, Oracle and/or its affiliates. All rights reserved.
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

package sun.security.validator;

import java.util.*;

import java.security.*;
import java.security.cert.*;

import javax.security.auth.x500.X500Principal;
import sun.security.action.GetBooleanAction;
import sun.security.provider.certpath.AlgorithmChecker;

/**
 * Validator implementation built on the PKIX CertPath API. This
 * implementation will be emphasized going forward.<p>
 * <p>
 * Note that the validate() implementation tries to use a PKIX validator
 * if that appears possible and a PKIX builder otherwise. This increases
 * performance and currently also leads to better exception messages
 * in case of failures.
 * <p>
 * {@code PKIXValidator} objects are immutable once they have been created.
 * Please DO NOT add methods that can change the state of an instance once
 * it has been created.
 *
 * @author Andreas Sterbenz
 */
public final class PKIXValidator extends Validator {

    /**
     * Flag indicating whether to enable revocation check for the PKIX trust
     * manager. Typically, this will only work if the PKIX implementation
     * supports CRL distribution points as we do not manually setup CertStores.
     */
    private final static boolean checkTLSRevocation =
        AccessController.doPrivileged
            (new GetBooleanAction("com.sun.net.ssl.checkRevocation"));

    private final Set<X509Certificate> trustedCerts;
    private final PKIXBuilderParameters parameterTemplate;
    private int certPathLength = -1;

    // needed only for the validator
    private final Map<X500Principal, List<PublicKey>> trustedSubjects;
    private final CertificateFactory factory;

    private final boolean plugin;

    PKIXValidator(String variant, Collection<X509Certificate> trustedCerts) {
        super(TYPE_PKIX, variant);
        this.trustedCerts = (trustedCerts instanceof Set) ?
                            (Set<X509Certificate>)trustedCerts :
                            new HashSet<X509Certificate>(trustedCerts);

        Set<TrustAnchor> trustAnchors = new HashSet<>();
        for (X509Certificate cert : trustedCerts) {
            trustAnchors.add(new TrustAnchor(cert, null));
        }

        try {
            parameterTemplate = new PKIXBuilderParameters(trustAnchors, null);
            factory = CertificateFactory.getInstance("X.509");
        } catch (InvalidAlgorithmParameterException e) {
            throw new RuntimeException("Unexpected error: " + e.toString(), e);
        } catch (CertificateException e) {
            throw new RuntimeException("Internal error", e);
        }

        setDefaultParameters(variant);
        plugin = variant.equals(VAR_PLUGIN_CODE_SIGNING);

        trustedSubjects = setTrustedSubjects();
    }

    PKIXValidator(String variant, PKIXBuilderParameters params) {
        super(TYPE_PKIX, variant);
        trustedCerts = new HashSet<X509Certificate>();
        for (TrustAnchor anchor : params.getTrustAnchors()) {
            X509Certificate cert = anchor.getTrustedCert();
            if (cert != null) {
                trustedCerts.add(cert);
            }
        }
        parameterTemplate = params;

        try {
            factory = CertificateFactory.getInstance("X.509");
        } catch (CertificateException e) {
            throw new RuntimeException("Internal error", e);
        }

        plugin = variant.equals(VAR_PLUGIN_CODE_SIGNING);

        trustedSubjects = setTrustedSubjects();
    }

    /**
     * Populate the trustedSubjects Map using the DN and public keys from
     * the list of trusted certificates
     *
     * @return Map containing each subject DN and one or more public keys
     *    tied to those DNs.
     */
    private Map<X500Principal, List<PublicKey>> setTrustedSubjects() {
        Map<X500Principal, List<PublicKey>> subjectMap = new HashMap<>();

        for (X509Certificate cert : trustedCerts) {
            X500Principal dn = cert.getSubjectX500Principal();
            List<PublicKey> keys;
            if (subjectMap.containsKey(dn)) {
                keys = subjectMap.get(dn);
            } else {
                keys = new ArrayList<PublicKey>();
                subjectMap.put(dn, keys);
            }
            keys.add(cert.getPublicKey());
        }

        return subjectMap;
    }

    public Collection<X509Certificate> getTrustedCertificates() {
        return trustedCerts;
    }

    /**
     * Returns the length of the last certification path that is validated by
     * CertPathValidator. This is intended primarily as a callback mechanism
     * for PKIXCertPathCheckers to determine the length of the certification
     * path that is being validated. It is necessary since engineValidate()
     * may modify the length of the path.
     *
     * @return the length of the last certification path passed to
     *   CertPathValidator.validate, or -1 if it has not been invoked yet
     */
    public int getCertPathLength() { // mutable, should be private
        return certPathLength;
    }

    /**
     * Set J2SE global default PKIX parameters. Currently, hardcoded to disable
     * revocation checking. In the future, this should be configurable.
     */
    private void setDefaultParameters(String variant) {
        if ((variant == Validator.VAR_TLS_SERVER) ||
                (variant == Validator.VAR_TLS_CLIENT)) {
            parameterTemplate.setRevocationEnabled(checkTLSRevocation);
        } else {
            parameterTemplate.setRevocationEnabled(false);
        }
    }

    /**
     * Return the PKIX parameters used by this instance. An application may
     * modify the parameters but must make sure not to perform any concurrent
     * validations.
     */
    public PKIXBuilderParameters getParameters() { // mutable, should be private
        return parameterTemplate;
    }

    @Override
    X509Certificate[] engineValidate(X509Certificate[] chain,
            Collection<X509Certificate> otherCerts,
            AlgorithmConstraints constraints,
            Object parameter) throws CertificateException {
        if ((chain == null) || (chain.length == 0)) {
            throw new CertificateException
                ("null or zero-length certificate chain");
        }

        // add  new algorithm constraints checker
        PKIXBuilderParameters pkixParameters =
                    (PKIXBuilderParameters) parameterTemplate.clone();
        AlgorithmChecker algorithmChecker = null;
        if (constraints != null) {
            algorithmChecker = new AlgorithmChecker(constraints);
            pkixParameters.addCertPathChecker(algorithmChecker);
        }

        // check that chain is in correct order and check if chain contains
        // trust anchor
        X500Principal prevIssuer = null;
        for (int i = 0; i < chain.length; i++) {
            X509Certificate cert = chain[i];
            X500Principal dn = cert.getSubjectX500Principal();
            if (i != 0 && !dn.equals(prevIssuer)) {
                // chain is not ordered correctly, call builder instead
                return doBuild(chain, otherCerts, pkixParameters);
            }

            // Check if chain[i] is already trusted. It may be inside
            // trustedCerts, or has the same dn and public key as a cert
            // inside trustedCerts. The latter happens when a CA has
            // updated its cert with a stronger signature algorithm in JRE
            // but the weak one is still in circulation.

            if (trustedCerts.contains(cert) ||          // trusted cert
                    (trustedSubjects.containsKey(dn) && // replacing ...
                     trustedSubjects.get(dn).contains(  // ... weak cert
                        cert.getPublicKey()))) {
                if (i == 0) {
                    return new X509Certificate[] {chain[0]};
                }
                // Remove and call validator on partial chain [0 .. i-1]
                X509Certificate[] newChain = new X509Certificate[i];
                System.arraycopy(chain, 0, newChain, 0, i);
                return doValidate(newChain, pkixParameters);
            }
            prevIssuer = cert.getIssuerX500Principal();
        }

        // apparently issued by trust anchor?
        X509Certificate last = chain[chain.length - 1];
        X500Principal issuer = last.getIssuerX500Principal();
        X500Principal subject = last.getSubjectX500Principal();
        if (trustedSubjects.containsKey(issuer) &&
                isSignatureValid(trustedSubjects.get(issuer), last)) {
            return doValidate(chain, pkixParameters);
        }

        // don't fallback to builder if called from plugin/webstart
        if (plugin) {
            // Validate chain even if no trust anchor is found. This
            // allows plugin/webstart to make sure the chain is
            // otherwise valid
            if (chain.length > 1) {
                X509Certificate[] newChain =
                    new X509Certificate[chain.length-1];
                System.arraycopy(chain, 0, newChain, 0, newChain.length);

                // temporarily set last cert as sole trust anchor
                try {
                    pkixParameters.setTrustAnchors
                        (Collections.singleton(new TrustAnchor
                            (chain[chain.length-1], null)));
                } catch (InvalidAlgorithmParameterException iape) {
                    // should never occur, but ...
                    throw new CertificateException(iape);
                }
                doValidate(newChain, pkixParameters);
            }
            // if the rest of the chain is valid, throw exception
            // indicating no trust anchor was found
            throw new ValidatorException
                (ValidatorException.T_NO_TRUST_ANCHOR);
        }
        // otherwise, fall back to builder

        return doBuild(chain, otherCerts, pkixParameters);
    }

    private boolean isSignatureValid(List<PublicKey> keys,
            X509Certificate sub) {
        if (plugin) {
            for (PublicKey key: keys) {
                try {
                    sub.verify(key);
                    return true;
                } catch (Exception ex) {
                    continue;
                }
            }
            return false;
        }
        return true; // only check if PLUGIN is set
    }

    private static X509Certificate[] toArray(CertPath path, TrustAnchor anchor)
            throws CertificateException {
        List<? extends java.security.cert.Certificate> list =
                                                path.getCertificates();
        X509Certificate[] chain = new X509Certificate[list.size() + 1];
        list.toArray(chain);
        X509Certificate trustedCert = anchor.getTrustedCert();
        if (trustedCert == null) {
            throw new ValidatorException
                ("TrustAnchor must be specified as certificate");
        }
        chain[chain.length - 1] = trustedCert;
        return chain;
    }

    /**
     * Set the check date (for debugging).
     */
    private void setDate(PKIXBuilderParameters params) {
        @SuppressWarnings("deprecation")
        Date date = validationDate;
        if (date != null) {
            params.setDate(date);
        }
    }

    private X509Certificate[] doValidate(X509Certificate[] chain,
            PKIXBuilderParameters params) throws CertificateException {
        try {
            setDate(params);

            // do the validation
            CertPathValidator validator = CertPathValidator.getInstance("PKIX");
            CertPath path = factory.generateCertPath(Arrays.asList(chain));
            certPathLength = chain.length;
            PKIXCertPathValidatorResult result =
                (PKIXCertPathValidatorResult)validator.validate(path, params);

            return toArray(path, result.getTrustAnchor());
        } catch (GeneralSecurityException e) {
            throw new ValidatorException
                ("PKIX path validation failed: " + e.toString(), e);
        }
    }

    private X509Certificate[] doBuild(X509Certificate[] chain,
        Collection<X509Certificate> otherCerts,
        PKIXBuilderParameters params) throws CertificateException {

        try {
            setDate(params);

            // setup target constraints
            X509CertSelector selector = new X509CertSelector();
            selector.setCertificate(chain[0]);
            params.setTargetCertConstraints(selector);

            // setup CertStores
            Collection<X509Certificate> certs =
                                        new ArrayList<X509Certificate>();
            certs.addAll(Arrays.asList(chain));
            if (otherCerts != null) {
                certs.addAll(otherCerts);
            }
            CertStore store = CertStore.getInstance("Collection",
                                new CollectionCertStoreParameters(certs));
            params.addCertStore(store);

            // do the build
            CertPathBuilder builder = CertPathBuilder.getInstance("PKIX");
            PKIXCertPathBuilderResult result =
                (PKIXCertPathBuilderResult)builder.build(params);

            return toArray(result.getCertPath(), result.getTrustAnchor());
        } catch (GeneralSecurityException e) {
            throw new ValidatorException
                ("PKIX path building failed: " + e.toString(), e);
        }
    }
}
