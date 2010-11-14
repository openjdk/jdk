/*
 * Copyright (c) 2000, 2010, Oracle and/or its affiliates. All rights reserved.
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
import java.security.AccessController;
import java.security.InvalidAlgorithmParameterException;
import java.security.cert.CertPath;
import java.security.cert.CertPathParameters;
import java.security.cert.CertPathValidatorException;
import java.security.cert.CertPathValidatorSpi;
import java.security.cert.CertPathValidatorResult;
import java.security.cert.PKIXCertPathChecker;
import java.security.cert.PKIXCertPathValidatorResult;
import java.security.cert.PKIXParameters;
import java.security.cert.PKIXReason;
import java.security.cert.PolicyNode;
import java.security.cert.TrustAnchor;
import java.security.cert.X509Certificate;
import java.security.cert.X509CertSelector;
import java.util.Collections;
import java.util.List;
import java.util.ArrayList;
import java.util.Date;
import java.util.Set;
import javax.security.auth.x500.X500Principal;
import sun.security.action.GetBooleanSecurityPropertyAction;
import sun.security.util.Debug;

/**
 * This class implements the PKIX validation algorithm for certification
 * paths consisting exclusively of <code>X509Certificates</code>. It uses
 * the specified input parameter set (which must be a
 * <code>PKIXParameters</code> object) and signature provider (if any).
 *
 * @since       1.4
 * @author      Yassir Elley
 */
public class PKIXCertPathValidator extends CertPathValidatorSpi {

    private static final Debug debug = Debug.getInstance("certpath");
    private Date testDate;
    private List<PKIXCertPathChecker> userCheckers;
    private String sigProvider;
    private BasicChecker basicChecker;
    private boolean ocspEnabled = false;
    private boolean onlyEECert = false;

    /**
     * Default constructor.
     */
    public PKIXCertPathValidator() {}

    /**
     * Validates a certification path consisting exclusively of
     * <code>X509Certificate</code>s using the PKIX validation algorithm,
     * which uses the specified input parameter set.
     * The input parameter set must be a <code>PKIXParameters</code> object.
     *
     * @param cp the X509 certification path
     * @param param the input PKIX parameter set
     * @return the result
     * @exception CertPathValidatorException Exception thrown if cert path
     * does not validate.
     * @exception InvalidAlgorithmParameterException if the specified
     * parameters are inappropriate for this certification path validator
     */
    public CertPathValidatorResult engineValidate(CertPath cp,
        CertPathParameters param)
        throws CertPathValidatorException, InvalidAlgorithmParameterException
    {
        if (debug != null)
            debug.println("PKIXCertPathValidator.engineValidate()...");

        if (!(param instanceof PKIXParameters)) {
            throw new InvalidAlgorithmParameterException("inappropriate "
                + "parameters, must be an instance of PKIXParameters");
        }

        if (!cp.getType().equals("X.509") && !cp.getType().equals("X509")) {
            throw new InvalidAlgorithmParameterException("inappropriate "
                + "certification path type specified, must be X.509 or X509");
        }

        PKIXParameters pkixParam = (PKIXParameters) param;

        // Make sure that none of the trust anchors include name constraints
        // (not supported).
        Set<TrustAnchor> anchors = pkixParam.getTrustAnchors();
        for (TrustAnchor anchor : anchors) {
            if (anchor.getNameConstraints() != null) {
                throw new InvalidAlgorithmParameterException
                    ("name constraints in trust anchor not supported");
            }
        }

        // the certpath which has been passed in (cp)
        // has the target cert as the first certificate - we
        // need to keep this cp so we can return it
        // in case of an exception and for policy qualifier
        // processing - however, for certpath validation,
        // we need to create a reversed path, where we reverse the
        // ordering so that the target cert is the last certificate

        // Must copy elements of certList into a new modifiable List before
        // calling Collections.reverse().
        ArrayList<X509Certificate> certList = new ArrayList<X509Certificate>
            ((List<X509Certificate>)cp.getCertificates());
        if (debug != null) {
            if (certList.isEmpty()) {
                debug.println("PKIXCertPathValidator.engineValidate() "
                    + "certList is empty");
            }
            debug.println("PKIXCertPathValidator.engineValidate() "
                + "reversing certpath...");
        }
        Collections.reverse(certList);

        // now certList has the target cert as the last cert and we
        // can proceed with normal validation

        populateVariables(pkixParam);

        // Retrieve the first certificate in the certpath
        // (to be used later in pre-screening)
        X509Certificate firstCert = null;
        if (!certList.isEmpty()) {
            firstCert = certList.get(0);
        }

        CertPathValidatorException lastException = null;

        // We iterate through the set of trust anchors until we find
        // one that works at which time we stop iterating
        for (TrustAnchor anchor : anchors) {
            X509Certificate trustedCert = anchor.getTrustedCert();
            if (trustedCert != null) {
                if (debug != null) {
                    debug.println("PKIXCertPathValidator.engineValidate() "
                        + "anchor.getTrustedCert() != null");
                }
                // if this trust anchor is not worth trying,
                // we move on to the next one
                if (!isWorthTrying(trustedCert, firstCert)) {
                    continue;
                }

                if (debug != null) {
                    debug.println("anchor.getTrustedCert()."
                        + "getSubjectX500Principal() = "
                        + trustedCert.getSubjectX500Principal());
                }
            } else {
                if (debug != null) {
                    debug.println("PKIXCertPathValidator.engineValidate(): "
                        + "anchor.getTrustedCert() == null");
                }
            }

            try {
                PolicyNodeImpl rootNode = new PolicyNodeImpl(null,
                    PolicyChecker.ANY_POLICY, null, false,
                    Collections.singleton(PolicyChecker.ANY_POLICY), false);
                PolicyNode policyTree =
                    doValidate(anchor, cp, certList, pkixParam, rootNode);
                // if this anchor works, return success
                return new PKIXCertPathValidatorResult(anchor, policyTree,
                    basicChecker.getPublicKey());
            } catch (CertPathValidatorException cpe) {
                // remember this exception
                lastException = cpe;
            }
        }

        // could not find a trust anchor that verified
        // (a) if we did a validation and it failed, use that exception
        if (lastException != null) {
            throw lastException;
        }
        // (b) otherwise, generate new exception
        throw new CertPathValidatorException
            ("Path does not chain with any of the trust anchors",
             null, null, -1, PKIXReason.NO_TRUST_ANCHOR);
    }

    /**
     * Internal method to do some simple checks to see if a given cert is
     * worth trying to validate in the chain.
     */
    private boolean isWorthTrying(X509Certificate trustedCert,
                                  X509Certificate firstCert)
    {
        if (debug != null) {
            debug.println("PKIXCertPathValidator.isWorthTrying() checking "
                + "if this trusted cert is worth trying ...");
        }

        if (firstCert == null) {
            return true;
        }

        // the subject of the trusted cert should match the
        // issuer of the first cert in the certpath

        X500Principal trustedSubject = trustedCert.getSubjectX500Principal();
        if (trustedSubject.equals(firstCert.getIssuerX500Principal())) {
            if (debug != null)
                debug.println("YES - try this trustedCert");
            return true;
        } else {
            if (debug != null)
                debug.println("NO - don't try this trustedCert");
            return false;
        }
    }

    /**
     * Internal method to setup the internal state
     */
    private void populateVariables(PKIXParameters pkixParam)
    {
        // default value for testDate is current time
        testDate = pkixParam.getDate();
        if (testDate == null) {
            testDate = new Date(System.currentTimeMillis());
        }

        userCheckers = pkixParam.getCertPathCheckers();
        sigProvider = pkixParam.getSigProvider();

        if (pkixParam.isRevocationEnabled()) {
            // Examine OCSP security property
            ocspEnabled = AccessController.doPrivileged(
                new GetBooleanSecurityPropertyAction
                    (OCSPChecker.OCSP_ENABLE_PROP));
            onlyEECert = AccessController.doPrivileged(
                new GetBooleanSecurityPropertyAction
                    ("com.sun.security.onlyCheckRevocationOfEECert"));
        }
    }

    /**
     * Internal method to actually validate a constructed path.
     *
     * @return the valid policy tree
     */
    private PolicyNode doValidate(
            TrustAnchor anchor, CertPath cpOriginal,
            ArrayList<X509Certificate> certList, PKIXParameters pkixParam,
            PolicyNodeImpl rootNode) throws CertPathValidatorException
    {
        int certPathLen = certList.size();

        basicChecker = new BasicChecker(anchor, testDate, sigProvider, false);
        AlgorithmChecker algorithmChecker = new AlgorithmChecker(anchor);
        KeyChecker keyChecker = new KeyChecker(certPathLen,
            pkixParam.getTargetCertConstraints());
        ConstraintsChecker constraintsChecker =
            new ConstraintsChecker(certPathLen);

        PolicyChecker policyChecker =
            new PolicyChecker(pkixParam.getInitialPolicies(), certPathLen,
                              pkixParam.isExplicitPolicyRequired(),
                              pkixParam.isPolicyMappingInhibited(),
                              pkixParam.isAnyPolicyInhibited(),
                              pkixParam.getPolicyQualifiersRejected(),
                              rootNode);

        ArrayList<PKIXCertPathChecker> certPathCheckers =
            new ArrayList<PKIXCertPathChecker>();
        // add standard checkers that we will be using
        certPathCheckers.add(algorithmChecker);
        certPathCheckers.add(keyChecker);
        certPathCheckers.add(constraintsChecker);
        certPathCheckers.add(policyChecker);
        certPathCheckers.add(basicChecker);

        // only add a revocationChecker if revocation is enabled
        if (pkixParam.isRevocationEnabled()) {

            // Use OCSP if it has been enabled
            if (ocspEnabled) {
                OCSPChecker ocspChecker =
                    new OCSPChecker(cpOriginal, pkixParam, onlyEECert);
                certPathCheckers.add(ocspChecker);
            }

            // Always use CRLs
            CrlRevocationChecker revocationChecker = new
                CrlRevocationChecker(anchor, pkixParam, certList, onlyEECert);
            certPathCheckers.add(revocationChecker);
        }

        // add user-specified checkers
        certPathCheckers.addAll(userCheckers);

        PKIXMasterCertPathValidator masterValidator =
            new PKIXMasterCertPathValidator(certPathCheckers);

        masterValidator.validate(cpOriginal, certList);

        return policyChecker.getPolicyTree();
    }
}
