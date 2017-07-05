/*
 * Copyright (c) 2000, 2012, Oracle and/or its affiliates. All rights reserved.
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
import java.security.GeneralSecurityException;
import java.security.InvalidAlgorithmParameterException;
import java.security.Principal;
import java.security.PublicKey;
import java.security.cert.*;
import java.security.cert.PKIXReason;
import java.security.interfaces.DSAPublicKey;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.LinkedList;
import java.util.Set;
import javax.security.auth.x500.X500Principal;

import sun.security.action.GetBooleanSecurityPropertyAction;
import sun.security.x509.X500Name;
import sun.security.x509.PKIXExtensions;
import sun.security.util.Debug;

/**
 * This class is able to build certification paths in either the forward
 * or reverse directions.
 *
 * <p> If successful, it returns a certification path which has succesfully
 * satisfied all the constraints and requirements specified in the
 * PKIXBuilderParameters object and has been validated according to the PKIX
 * path validation algorithm defined in RFC 3280.
 *
 * <p> This implementation uses a depth-first search approach to finding
 * certification paths. If it comes to a point in which it cannot find
 * any more certificates leading to the target OR the path length is too long
 * it backtracks to previous paths until the target has been found or
 * all possible paths have been exhausted.
 *
 * <p> This implementation is not thread-safe.
 *
 * @since       1.4
 * @author      Sean Mullan
 * @author      Yassir Elley
 */
public final class SunCertPathBuilder extends CertPathBuilderSpi {

    private static final Debug debug = Debug.getInstance("certpath");

    /*
     * private objects shared by methods
     */
    private PKIXBuilderParameters buildParams;
    private CertificateFactory cf;
    private boolean pathCompleted = false;
    private X500Principal targetSubjectDN;
    private PolicyNode policyTreeResult;
    private TrustAnchor trustAnchor;
    private PublicKey finalPublicKey;
    private X509CertSelector targetSel;
    private List<CertStore> orderedCertStores;
    private boolean onlyEECert = false;

    /**
     * Create an instance of <code>SunCertPathBuilder</code>.
     *
     * @throws CertPathBuilderException if an error occurs
     */
    public SunCertPathBuilder() throws CertPathBuilderException {
        try {
            cf = CertificateFactory.getInstance("X.509");
        } catch (CertificateException e) {
            throw new CertPathBuilderException(e);
        }
        onlyEECert = AccessController.doPrivileged(
            new GetBooleanSecurityPropertyAction
                ("com.sun.security.onlyCheckRevocationOfEECert"));
    }

    /**
     * Attempts to build a certification path using the Sun build
     * algorithm from a trusted anchor(s) to a target subject, which must both
     * be specified in the input parameter set. By default, this method will
     * attempt to build in the forward direction. In order to build in the
     * reverse direction, the caller needs to pass in an instance of
     * SunCertPathBuilderParameters with the buildForward flag set to false.
     *
     * <p>The certification path that is constructed is validated
     * according to the PKIX specification.
     *
     * @param params the parameter set for building a path. Must be an instance
     *  of <code>PKIXBuilderParameters</code>.
     * @return a certification path builder result.
     * @exception CertPathBuilderException Exception thrown if builder is
     *  unable to build a complete certification path from the trusted anchor(s)
     *  to the target subject.
     * @throws InvalidAlgorithmParameterException if the given parameters are
     *  inappropriate for this certification path builder.
     */
    public CertPathBuilderResult engineBuild(CertPathParameters params)
        throws CertPathBuilderException, InvalidAlgorithmParameterException {

        if (debug != null) {
            debug.println("SunCertPathBuilder.engineBuild(" + params + ")");
        }

        if (!(params instanceof PKIXBuilderParameters)) {
            throw new InvalidAlgorithmParameterException("inappropriate " +
                "parameter type, must be an instance of PKIXBuilderParameters");
        }

        boolean buildForward = true;
        if (params instanceof SunCertPathBuilderParameters) {
            buildForward =
                ((SunCertPathBuilderParameters)params).getBuildForward();
        }

        buildParams = (PKIXBuilderParameters)params;

        /* Check mandatory parameters */

        // Make sure that none of the trust anchors include name constraints
        // (not supported).
        for (TrustAnchor anchor : buildParams.getTrustAnchors()) {
            if (anchor.getNameConstraints() != null) {
                throw new InvalidAlgorithmParameterException
                    ("name constraints in trust anchor not supported");
            }
        }

        CertSelector sel = buildParams.getTargetCertConstraints();
        if (!(sel instanceof X509CertSelector)) {
            throw new InvalidAlgorithmParameterException("the "
                + "targetCertConstraints parameter must be an "
                + "X509CertSelector");
        }
        targetSel = (X509CertSelector)sel;
        targetSubjectDN = targetSel.getSubject();
        if (targetSubjectDN == null) {
            X509Certificate targetCert = targetSel.getCertificate();
            if (targetCert != null) {
                targetSubjectDN = targetCert.getSubjectX500Principal();
            }
        }
        // reorder CertStores so that local CertStores are tried first
        orderedCertStores =
            new ArrayList<CertStore>(buildParams.getCertStores());
        Collections.sort(orderedCertStores, new CertStoreComparator());
        if (targetSubjectDN == null) {
            targetSubjectDN = getTargetSubjectDN(orderedCertStores, targetSel);
        }
        if (targetSubjectDN == null) {
            throw new InvalidAlgorithmParameterException
                ("Could not determine unique target subject");
        }

        List<List<Vertex>> adjList = new ArrayList<List<Vertex>>();
        CertPathBuilderResult result =
            buildCertPath(buildForward, false, adjList);
        if (result == null) {
            if (debug != null) {
                debug.println("SunCertPathBuilder.engineBuild: 2nd pass");
            }
            // try again
            adjList.clear();
            result = buildCertPath(buildForward, true, adjList);
            if (result == null) {
                throw new SunCertPathBuilderException("unable to find valid "
                    + "certification path to requested target",
                    new AdjacencyList(adjList));
            }
        }
        return result;
    }

    private CertPathBuilderResult buildCertPath(boolean buildForward,
        boolean searchAllCertStores, List<List<Vertex>> adjList)
        throws CertPathBuilderException {

        // Init shared variables and build certification path
        pathCompleted = false;
        trustAnchor = null;
        finalPublicKey = null;
        policyTreeResult = null;
        LinkedList<X509Certificate> certPathList =
            new LinkedList<X509Certificate>();
        try {
            if (buildForward) {
                buildForward(adjList, certPathList, searchAllCertStores);
            } else {
                buildReverse(adjList, certPathList);
            }
        } catch (Exception e) {
            if (debug != null) {
                debug.println("SunCertPathBuilder.engineBuild() exception in "
                    + "build");
                e.printStackTrace();
            }
            throw new SunCertPathBuilderException("unable to find valid "
                + "certification path to requested target", e,
                new AdjacencyList(adjList));
        }

        // construct SunCertPathBuilderResult
        try {
            if (pathCompleted) {
                if (debug != null)
                    debug.println("SunCertPathBuilder.engineBuild() "
                                  + "pathCompleted");

                // we must return a certpath which has the target
                // as the first cert in the certpath - i.e. reverse
                // the certPathList
                Collections.reverse(certPathList);

                return new SunCertPathBuilderResult(
                    cf.generateCertPath(certPathList), this.trustAnchor,
                    policyTreeResult, finalPublicKey,
                    new AdjacencyList(adjList));
            }
        } catch (Exception e) {
            if (debug != null) {
                debug.println("SunCertPathBuilder.engineBuild() exception "
                              + "in wrap-up");
                e.printStackTrace();
            }
            throw new SunCertPathBuilderException("unable to find valid "
                + "certification path to requested target", e,
                new AdjacencyList(adjList));
        }

        return null;
    }

    /*
     * Private build reverse method.
     */
    private void buildReverse(List<List<Vertex>> adjacencyList,
        LinkedList<X509Certificate> certPathList) throws Exception
    {
        if (debug != null) {
            debug.println("SunCertPathBuilder.buildReverse()...");
            debug.println("SunCertPathBuilder.buildReverse() InitialPolicies: "
                + buildParams.getInitialPolicies());
        }

        ReverseState currentState = new ReverseState();
        /* Initialize adjacency list */
        adjacencyList.clear();
        adjacencyList.add(new LinkedList<Vertex>());

        /*
         * Perform a search using each trust anchor, until a valid
         * path is found
         */
        Iterator<TrustAnchor> iter = buildParams.getTrustAnchors().iterator();
        while (iter.hasNext()) {
            TrustAnchor anchor = iter.next();

            /* check if anchor satisfies target constraints */
            if (anchorIsTarget(anchor, targetSel)) {
                this.trustAnchor = anchor;
                this.pathCompleted = true;
                this.finalPublicKey = anchor.getTrustedCert().getPublicKey();
                break;
            }

            /* Initialize current state */
            currentState.initState(buildParams.getMaxPathLength(),
                       buildParams.isExplicitPolicyRequired(),
                       buildParams.isPolicyMappingInhibited(),
                       buildParams.isAnyPolicyInhibited(),
                       buildParams.getCertPathCheckers());
            currentState.updateState(anchor);
            // init the crl checker
            currentState.crlChecker =
                new CrlRevocationChecker(null, buildParams, null, onlyEECert);
            currentState.algorithmChecker = new AlgorithmChecker(anchor);
            currentState.untrustedChecker = new UntrustedChecker();
            try {
                depthFirstSearchReverse(null, currentState,
                new ReverseBuilder(buildParams, targetSubjectDN), adjacencyList,
                certPathList);
            } catch (Exception e) {
                // continue on error if more anchors to try
                if (iter.hasNext())
                    continue;
                else
                    throw e;
            }

            // break out of loop if search is successful
            if (pathCompleted) {
                break;
            }
        }

        if (debug != null) {
            debug.println("SunCertPathBuilder.buildReverse() returned from "
                + "depthFirstSearchReverse()");
            debug.println("SunCertPathBuilder.buildReverse() "
                + "certPathList.size: " + certPathList.size());
        }
    }

    /*
     * Private build forward method.
     */
    private void buildForward(List<List<Vertex>> adjacencyList,
        LinkedList<X509Certificate> certPathList, boolean searchAllCertStores)
        throws GeneralSecurityException, IOException
    {
        if (debug != null) {
            debug.println("SunCertPathBuilder.buildForward()...");
        }

        /* Initialize current state */
        ForwardState currentState = new ForwardState();
        currentState.initState(buildParams.getCertPathCheckers());

        /* Initialize adjacency list */
        adjacencyList.clear();
        adjacencyList.add(new LinkedList<Vertex>());

        // init the crl checker
        currentState.crlChecker
            = new CrlRevocationChecker(null, buildParams, null, onlyEECert);
        currentState.untrustedChecker = new UntrustedChecker();

        depthFirstSearchForward(targetSubjectDN, currentState,
          new ForwardBuilder
              (buildParams, targetSubjectDN, searchAllCertStores, onlyEECert),
          adjacencyList, certPathList);
    }

    /*
     * This method performs a depth first search for a certification
     * path while building forward which meets the requirements set in
     * the parameters object.
     * It uses an adjacency list to store all certificates which were
     * tried (i.e. at one time added to the path - they may not end up in
     * the final path if backtracking occurs). This information can
     * be used later to debug or demo the build.
     *
     * See "Data Structure and Algorithms, by Aho, Hopcroft, and Ullman"
     * for an explanation of the DFS algorithm.
     *
     * @param dN the distinguished name being currently searched for certs
     * @param currentState the current PKIX validation state
     */
    void depthFirstSearchForward(X500Principal dN, ForwardState currentState,
        ForwardBuilder builder, List<List<Vertex>> adjList,
        LinkedList<X509Certificate> certPathList)
        throws GeneralSecurityException, IOException
    {
        //XXX This method should probably catch & handle exceptions

        if (debug != null) {
            debug.println("SunCertPathBuilder.depthFirstSearchForward(" + dN
                + ", " + currentState.toString() + ")");
        }

        /*
         * Find all the certificates issued to dN which
         * satisfy the PKIX certification path constraints.
         */
        List<Vertex> vertices = addVertices
           (builder.getMatchingCerts(currentState, orderedCertStores), adjList);
        if (debug != null) {
            debug.println("SunCertPathBuilder.depthFirstSearchForward(): "
                + "certs.size=" + vertices.size());
        }

        /*
         * For each cert in the collection, verify anything
         * that hasn't been checked yet (signature, revocation, etc)
         * and check for loops. Call depthFirstSearchForward()
         * recursively for each good cert.
         */

               vertices:
        for (Vertex vertex : vertices) {
            /**
             * Restore state to currentState each time through the loop.
             * This is important because some of the user-defined
             * checkers modify the state, which MUST be restored if
             * the cert eventually fails to lead to the target and
             * the next matching cert is tried.
             */
            ForwardState nextState = (ForwardState) currentState.clone();
            X509Certificate cert = (X509Certificate) vertex.getCertificate();

            try {
                builder.verifyCert(cert, nextState, certPathList);
            } catch (GeneralSecurityException gse) {
                if (debug != null) {
                    debug.println("SunCertPathBuilder.depthFirstSearchForward()"
                        + ": validation failed: " + gse);
                    gse.printStackTrace();
                }
                vertex.setThrowable(gse);
                continue;
            }

            /*
             * Certificate is good.
             * If cert completes the path,
             *    process userCheckers that don't support forward checking
             *    and process policies over whole path
             *    and backtrack appropriately if there is a failure
             * else if cert does not complete the path,
             *    add it to the path
             */
            if (builder.isPathCompleted(cert)) {

                BasicChecker basicChecker = null;
                if (debug != null)
                    debug.println("SunCertPathBuilder.depthFirstSearchForward()"
                        + ": commencing final verification");

                ArrayList<X509Certificate> appendedCerts =
                    new ArrayList<X509Certificate>(certPathList);

                /*
                 * if the trust anchor selected is specified as a trusted
                 * public key rather than a trusted cert, then verify this
                 * cert (which is signed by the trusted public key), but
                 * don't add it yet to the certPathList
                 */
                if (builder.trustAnchor.getTrustedCert() == null) {
                    appendedCerts.add(0, cert);
                }

                HashSet<String> initExpPolSet = new HashSet<String>(1);
                initExpPolSet.add(PolicyChecker.ANY_POLICY);

                PolicyNodeImpl rootNode = new PolicyNodeImpl(null,
                    PolicyChecker.ANY_POLICY, null, false, initExpPolSet, false);

                PolicyChecker policyChecker
                    = new PolicyChecker(buildParams.getInitialPolicies(),
                                appendedCerts.size(),
                                buildParams.isExplicitPolicyRequired(),
                                buildParams.isPolicyMappingInhibited(),
                                buildParams.isAnyPolicyInhibited(),
                                buildParams.getPolicyQualifiersRejected(),
                                rootNode);

                List<PKIXCertPathChecker> userCheckers = new
                    ArrayList<PKIXCertPathChecker>
                        (buildParams.getCertPathCheckers());
                int mustCheck = 0;
                userCheckers.add(mustCheck, policyChecker);
                mustCheck++;

                // add the algorithm checker
                userCheckers.add(mustCheck,
                        new AlgorithmChecker(builder.trustAnchor));
                mustCheck++;

                if (nextState.keyParamsNeeded()) {
                    PublicKey rootKey = cert.getPublicKey();
                    if (builder.trustAnchor.getTrustedCert() == null) {
                        rootKey = builder.trustAnchor.getCAPublicKey();
                        if (debug != null)
                            debug.println(
                                "SunCertPathBuilder.depthFirstSearchForward " +
                                "using buildParams public key: " +
                                rootKey.toString());
                    }
                    TrustAnchor anchor = new TrustAnchor
                        (cert.getSubjectX500Principal(), rootKey, null);

                    // add the basic checker
                    basicChecker = new BasicChecker(anchor,
                                           builder.date,
                                           buildParams.getSigProvider(),
                                           true);
                    userCheckers.add(mustCheck, basicChecker);
                    mustCheck++;

                    // add the crl revocation checker
                    if (buildParams.isRevocationEnabled()) {
                        userCheckers.add(mustCheck, new CrlRevocationChecker
                            (anchor, buildParams, null, onlyEECert));
                        mustCheck++;
                    }
                }
                // Why we don't need BasicChecker and CrlRevocationChecker
                // if nextState.keyParamsNeeded() is false?

                for (int i=0; i<appendedCerts.size(); i++) {
                    X509Certificate currCert = appendedCerts.get(i);
                    if (debug != null)
                        debug.println("current subject = "
                                      + currCert.getSubjectX500Principal());
                    Set<String> unresCritExts =
                        currCert.getCriticalExtensionOIDs();
                    if (unresCritExts == null) {
                        unresCritExts = Collections.<String>emptySet();
                    }

                    for (int j=0; j<userCheckers.size(); j++) {
                        PKIXCertPathChecker currChecker = userCheckers.get(j);
                        if (j < mustCheck ||
                            !currChecker.isForwardCheckingSupported()) {
                            if (i == 0) {
                                currChecker.init(false);

                                // The user specified
                                // AlgorithmChecker may not be
                                // able to set the trust anchor until now.
                                if (j >= mustCheck &&
                                    currChecker instanceof AlgorithmChecker) {
                                    ((AlgorithmChecker)currChecker).
                                        trySetTrustAnchor(builder.trustAnchor);
                                }
                            }

                            try {
                                currChecker.check(currCert, unresCritExts);
                            } catch (CertPathValidatorException cpve) {
                                if (debug != null)
                                    debug.println
                                    ("SunCertPathBuilder.depthFirstSearchForward(): " +
                                    "final verification failed: " + cpve);
                                vertex.setThrowable(cpve);
                                continue vertices;
                            }
                        }
                    }

                    /*
                     * Remove extensions from user checkers that support
                     * forward checking. After this step, we will have
                     * removed all extensions that all user checkers
                     * are capable of processing.
                     */
                    for (PKIXCertPathChecker checker :
                         buildParams.getCertPathCheckers())
                    {
                        if (checker.isForwardCheckingSupported()) {
                            Set<String> suppExts =
                                checker.getSupportedExtensions();
                            if (suppExts != null) {
                                unresCritExts.removeAll(suppExts);
                            }
                        }
                    }

                    if (!unresCritExts.isEmpty()) {
                        unresCritExts.remove
                            (PKIXExtensions.BasicConstraints_Id.toString());
                        unresCritExts.remove
                            (PKIXExtensions.NameConstraints_Id.toString());
                        unresCritExts.remove
                            (PKIXExtensions.CertificatePolicies_Id.toString());
                        unresCritExts.remove
                            (PKIXExtensions.PolicyMappings_Id.toString());
                        unresCritExts.remove
                            (PKIXExtensions.PolicyConstraints_Id.toString());
                        unresCritExts.remove
                            (PKIXExtensions.InhibitAnyPolicy_Id.toString());
                        unresCritExts.remove(PKIXExtensions.
                            SubjectAlternativeName_Id.toString());
                        unresCritExts.remove
                            (PKIXExtensions.KeyUsage_Id.toString());
                        unresCritExts.remove
                            (PKIXExtensions.ExtendedKeyUsage_Id.toString());

                        if (!unresCritExts.isEmpty()) {
                            throw new CertPathValidatorException
                                ("unrecognized critical extension(s)", null,
                                 null, -1, PKIXReason.UNRECOGNIZED_CRIT_EXT);
                        }
                    }
                }
                if (debug != null)
                    debug.println("SunCertPathBuilder.depthFirstSearchForward()"
                        + ": final verification succeeded - path completed!");
                pathCompleted = true;

                /*
                 * if the user specified a trusted public key rather than
                 * trusted certs, then add this cert (which is signed by
                 * the trusted public key) to the certPathList
                 */
                if (builder.trustAnchor.getTrustedCert() == null)
                    builder.addCertToPath(cert, certPathList);
                // Save the trust anchor
                this.trustAnchor = builder.trustAnchor;

                /*
                 * Extract and save the final target public key
                 */
                if (basicChecker != null) {
                    finalPublicKey = basicChecker.getPublicKey();
                } else {
                    Certificate finalCert;
                    if (certPathList.size() == 0) {
                        finalCert = builder.trustAnchor.getTrustedCert();
                    } else {
                        finalCert = certPathList.get(certPathList.size()-1);
                    }
                    finalPublicKey = finalCert.getPublicKey();
                }

                policyTreeResult = policyChecker.getPolicyTree();
                return;
            } else {
                builder.addCertToPath(cert, certPathList);
            }

            /* Update the PKIX state */
            nextState.updateState(cert);

            /*
             * Append an entry for cert in adjacency list and
             * set index for current vertex.
             */
            adjList.add(new LinkedList<Vertex>());
            vertex.setIndex(adjList.size() - 1);

            /* recursively search for matching certs at next dN */
            depthFirstSearchForward(cert.getIssuerX500Principal(),
                                    nextState, builder, adjList, certPathList);

            /*
             * If path has been completed, return ASAP!
             */
            if (pathCompleted) {
                return;
            } else {
                /*
                 * If we get here, it means we have searched all possible
                 * certs issued by the dN w/o finding any matching certs.
                 * This means we have to backtrack to the previous cert in
                 * the path and try some other paths.
                 */
                if (debug != null)
                    debug.println("SunCertPathBuilder.depthFirstSearchForward()"
                        + ": backtracking");
                builder.removeFinalCertFromPath(certPathList);
            }
        }
    }

    /*
     * This method performs a depth first search for a certification
     * path while building reverse which meets the requirements set in
     * the parameters object.
     * It uses an adjacency list to store all certificates which were
     * tried (i.e. at one time added to the path - they may not end up in
     * the final path if backtracking occurs). This information can
     * be used later to debug or demo the build.
     *
     * See "Data Structure and Algorithms, by Aho, Hopcroft, and Ullman"
     * for an explanation of the DFS algorithm.
     *
     * @param dN the distinguished name being currently searched for certs
     * @param currentState the current PKIX validation state
     */
    void depthFirstSearchReverse(X500Principal dN, ReverseState currentState,
        ReverseBuilder builder, List<List<Vertex>> adjList,
        LinkedList<X509Certificate> certPathList)
        throws GeneralSecurityException, IOException
    {
        if (debug != null)
            debug.println("SunCertPathBuilder.depthFirstSearchReverse(" + dN
                + ", " + currentState.toString() + ")");

        /*
         * Find all the certificates issued by dN which
         * satisfy the PKIX certification path constraints.
         */
        List<Vertex> vertices = addVertices
           (builder.getMatchingCerts(currentState, orderedCertStores), adjList);
        if (debug != null)
            debug.println("SunCertPathBuilder.depthFirstSearchReverse(): "
                + "certs.size=" + vertices.size());

        /*
         * For each cert in the collection, verify anything
         * that hasn't been checked yet (signature, revocation, etc)
         * and check for loops. Call depthFirstSearchReverse()
         * recursively for each good cert.
         */
        for (Vertex vertex : vertices) {
            /**
             * Restore state to currentState each time through the loop.
             * This is important because some of the user-defined
             * checkers modify the state, which MUST be restored if
             * the cert eventually fails to lead to the target and
             * the next matching cert is tried.
             */
            ReverseState nextState = (ReverseState) currentState.clone();
            X509Certificate cert = (X509Certificate) vertex.getCertificate();
            try {
                builder.verifyCert(cert, nextState, certPathList);
            } catch (GeneralSecurityException gse) {
                if (debug != null)
                    debug.println("SunCertPathBuilder.depthFirstSearchReverse()"
                        + ": validation failed: " + gse);
                vertex.setThrowable(gse);
                continue;
            }

            /*
             * Certificate is good, add it to the path (if it isn't a
             * self-signed cert) and update state
             */
            if (!currentState.isInitial())
                builder.addCertToPath(cert, certPathList);
            // save trust anchor
            this.trustAnchor = currentState.trustAnchor;

            /*
             * Check if path is completed, return ASAP if so.
             */
            if (builder.isPathCompleted(cert)) {
                if (debug != null)
                    debug.println("SunCertPathBuilder.depthFirstSearchReverse()"
                        + ": path completed!");
                pathCompleted = true;

                PolicyNodeImpl rootNode = nextState.rootNode;

                if (rootNode == null)
                    policyTreeResult = null;
                else {
                    policyTreeResult = rootNode.copyTree();
                    ((PolicyNodeImpl)policyTreeResult).setImmutable();
                }

                /*
                 * Extract and save the final target public key
                 */
                finalPublicKey = cert.getPublicKey();
                if (finalPublicKey instanceof DSAPublicKey &&
                    ((DSAPublicKey)finalPublicKey).getParams() == null)
                {
                    finalPublicKey =
                        BasicChecker.makeInheritedParamsKey
                            (finalPublicKey, currentState.pubKey);
                }

                return;
            }

            /* Update the PKIX state */
            nextState.updateState(cert);

            /*
             * Append an entry for cert in adjacency list and
             * set index for current vertex.
             */
            adjList.add(new LinkedList<Vertex>());
            vertex.setIndex(adjList.size() - 1);

            /* recursively search for matching certs at next dN */
            depthFirstSearchReverse(cert.getSubjectX500Principal(), nextState,
                builder, adjList, certPathList);

            /*
             * If path has been completed, return ASAP!
             */
            if (pathCompleted) {
                return;
            } else {
                /*
                 * If we get here, it means we have searched all possible
                 * certs issued by the dN w/o finding any matching certs. This
                 * means we have to backtrack to the previous cert in the path
                 * and try some other paths.
                 */
                if (debug != null)
                    debug.println("SunCertPathBuilder.depthFirstSearchReverse()"
                        + ": backtracking");
                if (!currentState.isInitial())
                    builder.removeFinalCertFromPath(certPathList);
            }
        }
        if (debug != null)
            debug.println("SunCertPathBuilder.depthFirstSearchReverse() all "
                + "certs in this adjacency list checked");
    }

    /*
     * Adds a collection of matching certificates to the
     * adjacency list.
     */
    private List<Vertex> addVertices(Collection<X509Certificate> certs,
        List<List<Vertex>> adjList) {
        List<Vertex> l = adjList.get(adjList.size() - 1);

        for (X509Certificate cert : certs) {
           Vertex v = new Vertex(cert);
           l.add(v);
        }

        return l;
    }

    /**
     * Returns true if trust anchor certificate matches specified
     * certificate constraints.
     */
    private boolean anchorIsTarget(TrustAnchor anchor, X509CertSelector sel) {
        X509Certificate anchorCert = anchor.getTrustedCert();
        if (anchorCert != null) {
            return sel.match(anchorCert);
        }
        return false;
    }

    /**
     * Comparator that orders CertStores so that local CertStores come before
     * remote CertStores.
     */
    private static class CertStoreComparator implements Comparator<CertStore> {
        public int compare(CertStore store1, CertStore store2) {
            if (Builder.isLocalCertStore(store1)) {
                return -1;
            } else {
                return 1;
            }
        }
    }

    /**
     * Returns the target subject DN from the first X509Certificate that
     * is fetched that matches the specified X509CertSelector.
     */
    private X500Principal getTargetSubjectDN(List<CertStore> stores,
        X509CertSelector targetSel) {
        for (CertStore store : stores) {
            try {
                Collection<? extends Certificate> targetCerts =
                    (Collection<? extends Certificate>)
                        store.getCertificates(targetSel);
                if (!targetCerts.isEmpty()) {
                    X509Certificate targetCert =
                        (X509Certificate)targetCerts.iterator().next();
                    return targetCert.getSubjectX500Principal();
                }
            } catch (CertStoreException e) {
                // ignore but log it
                if (debug != null) {
                    debug.println("SunCertPathBuilder.getTargetSubjectDN: " +
                        "non-fatal exception retrieving certs: " + e);
                    e.printStackTrace();
                }
            }
        }
        return null;
    }
}
