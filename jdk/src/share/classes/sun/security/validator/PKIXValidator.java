/*
 * Copyright 2002-2009 Sun Microsystems, Inc.  All Rights Reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Sun designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Sun in the LICENSE file that accompanied this code.
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
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 */

package sun.security.validator;

import java.util.*;

import java.security.*;
import java.security.cert.*;

import javax.security.auth.x500.X500Principal;

/**
 * Validator implementation built on the PKIX CertPath API. This
 * implementation will be emphasized going forward.<p>
 *
 * Note that the validate() implementation tries to use a PKIX validator
 * if that appears possible and a PKIX builder otherwise. This increases
 * performance and currently also leads to better exception messages
 * in case of failures.
 *
 * @author Andreas Sterbenz
 */
public final class PKIXValidator extends Validator {

    // enable use of the validator if possible
    private final static boolean TRY_VALIDATOR = true;

    private final Set<X509Certificate> trustedCerts;
    private final PKIXBuilderParameters parameterTemplate;
    private int certPathLength = -1;

    // needed only for the validator
    private Map<X500Principal, X509Certificate> trustedSubjects;
    private CertificateFactory factory;

    private boolean plugin = false;

    PKIXValidator(String variant, Collection<X509Certificate> trustedCerts) {
        super(TYPE_PKIX, variant);
        if (trustedCerts instanceof Set) {
            this.trustedCerts = (Set<X509Certificate>)trustedCerts;
        } else {
            this.trustedCerts = new HashSet<X509Certificate>(trustedCerts);
        }
        Set<TrustAnchor> trustAnchors = new HashSet<TrustAnchor>();
        for (X509Certificate cert : trustedCerts) {
            trustAnchors.add(new TrustAnchor(cert, null));
        }
        try {
            parameterTemplate = new PKIXBuilderParameters(trustAnchors, null);
        } catch (InvalidAlgorithmParameterException e) {
            throw new RuntimeException("Unexpected error: " + e.toString(), e);
        }
        setDefaultParameters(variant);
        initCommon();
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
        initCommon();
    }

    private void initCommon() {
        if (TRY_VALIDATOR == false) {
            return;
        }
        trustedSubjects = new HashMap<X500Principal, X509Certificate>();
        for (X509Certificate cert : trustedCerts) {
            trustedSubjects.put(cert.getSubjectX500Principal(), cert);
        }
        try {
            factory = CertificateFactory.getInstance("X.509");
        } catch (CertificateException e) {
            throw new RuntimeException("Internal error", e);
        }
        plugin = variant.equals(VAR_PLUGIN_CODE_SIGNING);
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
    public int getCertPathLength() {
        return certPathLength;
    }

    /**
     * Set J2SE global default PKIX parameters. Currently, hardcoded to disable
     * revocation checking. In the future, this should be configurable.
     */
    private void setDefaultParameters(String variant) {
        parameterTemplate.setRevocationEnabled(false);
    }

    /**
     * Return the PKIX parameters used by this instance. An application may
     * modify the parameters but must make sure not to perform any concurrent
     * validations.
     */
    public PKIXBuilderParameters getParameters() {
        return parameterTemplate;
    }

    X509Certificate[] engineValidate(X509Certificate[] chain,
            Collection<X509Certificate> otherCerts, Object parameter)
            throws CertificateException {
        if ((chain == null) || (chain.length == 0)) {
            throw new CertificateException
                ("null or zero-length certificate chain");
        }
        if (TRY_VALIDATOR) {
            // check that chain is in correct order and check if chain contains
            // trust anchor
            X500Principal prevIssuer = null;
            for (int i = 0; i < chain.length; i++) {
                X509Certificate cert = chain[i];
                if (i != 0 &&
                    !cert.getSubjectX500Principal().equals(prevIssuer)) {
                    // chain is not ordered correctly, call builder instead
                    return doBuild(chain, otherCerts);
                }
                if (trustedCerts.contains(cert)) {
                    if (i == 0) {
                        return new X509Certificate[] {chain[0]};
                    }
                    // Remove and call validator
                    X509Certificate[] newChain = new X509Certificate[i];
                    System.arraycopy(chain, 0, newChain, 0, i);
                    return doValidate(newChain);
                }
                prevIssuer = cert.getIssuerX500Principal();
            }

            // apparently issued by trust anchor?
            X509Certificate last = chain[chain.length - 1];
            X500Principal issuer = last.getIssuerX500Principal();
            X500Principal subject = last.getSubjectX500Principal();
            if (trustedSubjects.containsKey(issuer) &&
                    isSignatureValid(trustedSubjects.get(issuer), last)) {
                return doValidate(chain);
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
                    PKIXBuilderParameters params =
                        (PKIXBuilderParameters) parameterTemplate.clone();
                    try {
                        params.setTrustAnchors
                            (Collections.singleton(new TrustAnchor
                                (chain[chain.length-1], null)));
                    } catch (InvalidAlgorithmParameterException iape) {
                        // should never occur, but ...
                        throw new CertificateException(iape);
                    }
                    doValidate(newChain, params);
                }
                // if the rest of the chain is valid, throw exception
                // indicating no trust anchor was found
                throw new ValidatorException
                    (ValidatorException.T_NO_TRUST_ANCHOR);
            }
            // otherwise, fall back to builder
        }

        return doBuild(chain, otherCerts);
    }

    private boolean isSignatureValid(X509Certificate iss, X509Certificate sub) {
        if (plugin) {
            try {
                sub.verify(iss.getPublicKey());
            } catch (Exception ex) {
                return false;
            }
            return true;
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
        Date date = validationDate;
        if (date != null) {
            params.setDate(date);
        }
    }

    private X509Certificate[] doValidate(X509Certificate[] chain)
            throws CertificateException {
        PKIXBuilderParameters params =
            (PKIXBuilderParameters)parameterTemplate.clone();
        return doValidate(chain, params);
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
        Collection<X509Certificate> otherCerts) throws CertificateException {

        try {
            PKIXBuilderParameters params =
                (PKIXBuilderParameters)parameterTemplate.clone();
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
