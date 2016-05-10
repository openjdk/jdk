/*
 * Copyright (c) 2010, 2016, Oracle and/or its affiliates. All rights reserved.
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

import java.security.CryptoPrimitive;
import java.security.AlgorithmParameters;
import java.security.Key;
import java.security.cert.CertPathValidatorException;
import java.security.cert.CertPathValidatorException.BasicReason;
import java.security.cert.X509Certificate;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.regex.Matcher;

/**
 * Algorithm constraints for disabled algorithms property
 *
 * See the "jdk.certpath.disabledAlgorithms" specification in java.security
 * for the syntax of the disabled algorithm string.
 */
public class DisabledAlgorithmConstraints extends AbstractAlgorithmConstraints {
    private static final Debug debug = Debug.getInstance("certpath");

    // the known security property, jdk.certpath.disabledAlgorithms
    public static final String PROPERTY_CERTPATH_DISABLED_ALGS =
            "jdk.certpath.disabledAlgorithms";

    // the known security property, jdk.tls.disabledAlgorithms
    public static final String PROPERTY_TLS_DISABLED_ALGS =
            "jdk.tls.disabledAlgorithms";

    private final String[] disabledAlgorithms;
    private final Constraints algorithmConstraints;

    /**
     * Initialize algorithm constraints with the specified security property.
     *
     * @param propertyName the security property name that define the disabled
     *        algorithm constraints
     */
    public DisabledAlgorithmConstraints(String propertyName) {
        this(propertyName, new AlgorithmDecomposer());
    }

    public DisabledAlgorithmConstraints(String propertyName,
            AlgorithmDecomposer decomposer) {
        super(decomposer);
        disabledAlgorithms = getAlgorithms(propertyName);
        algorithmConstraints = new Constraints(disabledAlgorithms);
    }

    /*
     * This only checks if the algorithm has been completely disabled.  If
     * there are keysize or other limit, this method allow the algorithm.
     */
    @Override
    public final boolean permits(Set<CryptoPrimitive> primitives,
            String algorithm, AlgorithmParameters parameters) {

        if (primitives == null || primitives.isEmpty()) {
            throw new IllegalArgumentException(
                        "No cryptographic primitive specified");
        }

        return checkAlgorithm(disabledAlgorithms, algorithm, decomposer);
    }

    /*
     * Checks if the key algorithm has been disabled or constraints have been
     * placed on the key.
     */
    @Override
    public final boolean permits(Set<CryptoPrimitive> primitives, Key key) {
        return checkConstraints(primitives, "", key, null);
    }

    /*
     * Checks if the key algorithm has been disabled or if constraints have
     * been placed on the key.
     */
    @Override
    public final boolean permits(Set<CryptoPrimitive> primitives,
            String algorithm, Key key, AlgorithmParameters parameters) {

        if (algorithm == null || algorithm.length() == 0) {
            throw new IllegalArgumentException("No algorithm name specified");
        }

        return checkConstraints(primitives, algorithm, key, parameters);
    }

    /*
     * Check if a x509Certificate object is permitted.  Check if all
     * algorithms are allowed, certificate constraints, and the
     * public key against key constraints.
     *
     * Uses new style permit() which throws exceptions.
     */
    public final void permits(Set<CryptoPrimitive> primitives,
            CertConstraintParameters cp) throws CertPathValidatorException {
        checkConstraints(primitives, cp);
    }

    /*
     * Check if Certificate object is within the constraints.
     * Uses new style permit() which throws exceptions.
     */
    public final void permits(Set<CryptoPrimitive> primitives,
            X509Certificate cert) throws CertPathValidatorException {
        checkConstraints(primitives, new CertConstraintParameters(cert));
    }

    // Check if a string is contained inside the property
    public boolean checkProperty(String param) {
        param = param.toLowerCase(Locale.ENGLISH);
        for (String block : disabledAlgorithms) {
            if (block.toLowerCase(Locale.ENGLISH).indexOf(param) >= 0) {
                return true;
            }
        }
        return false;
    }

    // Check algorithm constraints with key and algorithm
    private boolean checkConstraints(Set<CryptoPrimitive> primitives,
            String algorithm, Key key, AlgorithmParameters parameters) {

        // check the key parameter, it cannot be null.
        if (key == null) {
            throw new IllegalArgumentException("The key cannot be null");
        }

        // check the signature algorithm
        if (algorithm != null && algorithm.length() != 0) {
            if (!permits(primitives, algorithm, parameters)) {
                return false;
            }
        }

        // check the key algorithm
        if (!permits(primitives, key.getAlgorithm(), null)) {
            return false;
        }

        // check the key constraints
        return algorithmConstraints.permits(key);
    }

    /*
     * Check algorithm constraints with Certificate
     * Uses new style permit() which throws exceptions.
     */
    private void checkConstraints(Set<CryptoPrimitive> primitives,
            CertConstraintParameters cp) throws CertPathValidatorException {

        X509Certificate cert = cp.getCertificate();
        String algorithm = cert.getSigAlgName();

        // Check signature algorithm is not disabled
        if (!permits(primitives, algorithm, null)) {
            throw new CertPathValidatorException(
                    "Algorithm constraints check failed on disabled "+
                            "signature algorithm: " + algorithm,
                    null, null, -1, BasicReason.ALGORITHM_CONSTRAINED);
        }

        // Check key algorithm is not disabled
        if (!permits(primitives, cert.getPublicKey().getAlgorithm(), null)) {
            throw new CertPathValidatorException(
                    "Algorithm constraints check failed on disabled "+
                            "public key algorithm: " + algorithm,
                    null, null, -1, BasicReason.ALGORITHM_CONSTRAINED);
        }

        // Check the certificate and key constraints
        algorithmConstraints.permits(cp);

    }

    /**
     * Key and Certificate Constraints
     *
     * The complete disabling of an algorithm is not handled by Constraints or
     * Constraint classes.  That is addressed with
     *   permit(Set<CryptoPrimitive>, String, AlgorithmParameters)
     *
     * When passing a Key to permit(), the boolean return values follow the
     * same as the interface class AlgorithmConstraints.permit().  This is to
     * maintain compatibility:
     * 'true' means the operation is allowed.
     * 'false' means it failed the constraints and is disallowed.
     *
     * When passing CertConstraintParameters through permit(), an exception
     * will be thrown on a failure to better identify why the operation was
     * disallowed.
     */

    private static class Constraints {
        private Map<String, Set<Constraint>> constraintsMap = new HashMap<>();
        private static final Pattern keySizePattern = Pattern.compile(
                "keySize\\s*(<=|<|==|!=|>|>=)\\s*(\\d+)");

        public Constraints(String[] constraintArray) {
            for (String constraintEntry : constraintArray) {
                if (constraintEntry == null || constraintEntry.isEmpty()) {
                    continue;
                }

                constraintEntry = constraintEntry.trim();
                if (debug != null) {
                    debug.println("Constraints: " + constraintEntry);
                }

                // Check if constraint is a complete disabling of an
                // algorithm or has conditions.
                String algorithm;
                String policy;
                int space = constraintEntry.indexOf(' ');
                if (space > 0) {
                    algorithm = AlgorithmDecomposer.hashName(
                            constraintEntry.substring(0, space).
                                    toUpperCase(Locale.ENGLISH));
                    policy = constraintEntry.substring(space + 1);
                } else {
                    constraintsMap.computeIfAbsent(
                            constraintEntry.toUpperCase(Locale.ENGLISH),
                            k -> new HashSet<>());
                    continue;
                }

                // Convert constraint conditions into Constraint classes
                Constraint c, lastConstraint = null;
                // Allow only one jdkCA entry per constraint entry
                boolean jdkCALimit = false;

                for (String entry : policy.split("&")) {
                    entry = entry.trim();

                    Matcher matcher = keySizePattern.matcher(entry);
                    if (matcher.matches()) {
                        if (debug != null) {
                            debug.println("Constraints set to keySize: " +
                                    entry);
                        }
                        c = new KeySizeConstraint(algorithm,
                                KeySizeConstraint.Operator.of(matcher.group(1)),
                                Integer.parseInt(matcher.group(2)));

                    } else if (entry.equalsIgnoreCase("jdkCA")) {
                        if (debug != null) {
                            debug.println("Constraints set to jdkCA.");
                        }
                        if (jdkCALimit) {
                            throw new IllegalArgumentException("Only one " +
                                    "jdkCA entry allowed in property. " +
                                    "Constraint: " + constraintEntry);
                        }
                        c = new jdkCAConstraint(algorithm);
                        jdkCALimit = true;
                    } else {
                        throw new IllegalArgumentException("Error in security" +
                                " property. Constraint unknown: " + entry);
                    }

                    // Link multiple conditions for a single constraint
                    // into a linked list.
                    if (lastConstraint == null) {
                        if (!constraintsMap.containsKey(algorithm)) {
                            constraintsMap.putIfAbsent(algorithm,
                                    new HashSet<>());
                        }
                        constraintsMap.get(algorithm).add(c);
                    } else {
                        lastConstraint.nextConstraint = c;
                    }
                    lastConstraint = c;
                }
            }
        }

        // Get applicable constraints based off the signature algorithm
        private Set<Constraint> getConstraints(String algorithm) {
            return constraintsMap.get(algorithm);
        }

        // Check if KeySizeConstraints permit the specified key
        public boolean permits(Key key) {
            Set<Constraint> set = getConstraints(key.getAlgorithm());
            if (set == null) {
                return true;
            }
            for (Constraint constraint : set) {
                if (!constraint.permits(key)) {
                    if (debug != null) {
                        debug.println("keySizeConstraint: failed key " +
                                "constraint check " + KeyUtil.getKeySize(key));
                    }
                    return false;
                }
            }
            return true;
        }

        // Check if constraints permit this cert.
        public void permits(CertConstraintParameters cp)
                throws CertPathValidatorException {
            X509Certificate cert = cp.getCertificate();

            if (debug != null) {
                debug.println("Constraints.permits(): " + cert.getSigAlgName());
            }

            // Get all signature algorithms to check for constraints
            Set<String> algorithms =
                    AlgorithmDecomposer.decomposeOneHash(cert.getSigAlgName());
            if (algorithms == null || algorithms.isEmpty()) {
                return;
            }

            // Attempt to add the public key algorithm to the set
            algorithms.add(cert.getPublicKey().getAlgorithm());

            // Check all applicable constraints
            for (String algorithm : algorithms) {
                Set<Constraint> set = getConstraints(algorithm);
                if (set == null) {
                    continue;
                }
                for (Constraint constraint : set) {
                    constraint.permits(cp);
                }
            }
        }
    }

    // Abstract class for algorithm constraint checking
    private abstract static class Constraint {
        String algorithm;
        Constraint nextConstraint = null;

        // operator
        enum Operator {
            EQ,         // "=="
            NE,         // "!="
            LT,         // "<"
            LE,         // "<="
            GT,         // ">"
            GE;         // ">="

            static Operator of(String s) {
                switch (s) {
                    case "==":
                        return EQ;
                    case "!=":
                        return NE;
                    case "<":
                        return LT;
                    case "<=":
                        return LE;
                    case ">":
                        return GT;
                    case ">=":
                        return GE;
                }

                throw new IllegalArgumentException("Error in security " +
                        "property. " + s + " is not a legal Operator");
            }
        }

        /**
         * Check if an algorithm constraint permit this key to be used.
         * @param key Public key
         * @return true if constraints do not match
         */
        public boolean permits(Key key) {
            return true;
        }

        /**
         * Check if an algorithm constraint is permit this certificate to
         * be used.
         * @param cp CertificateParameter containing certificate and state info
         * @return true if constraints do not match
         */
        public abstract void permits(CertConstraintParameters cp)
                throws CertPathValidatorException;
    }

    /*
     * This class contains constraints dealing with the certificate chain
     * of the certificate.
     */
    private static class jdkCAConstraint extends Constraint {
        jdkCAConstraint(String algo) {
            algorithm = algo;
        }

        /*
         * Check if each constraint fails and check if there is a linked
         * constraint  Any permitted constraint will exit the linked list
         * to allow the operation.
         */
        public void permits(CertConstraintParameters cp)
                throws CertPathValidatorException {
            if (debug != null) {
                debug.println("jdkCAConstraints.permits(): " + algorithm);
            }

            // Return false if the chain has a trust anchor in cacerts
            if (cp.isTrustedMatch()) {
                if (nextConstraint != null) {
                    nextConstraint.permits(cp);
                    return;
                }
                throw new CertPathValidatorException(
                        "Algorithm constraints check failed on certificate " +
                                "anchor limits",
                        null, null, -1, BasicReason.ALGORITHM_CONSTRAINED);
            }
        }
    }


    /*
     * This class contains constraints dealing with the key size
     * support limits per algorithm.   e.g.  "keySize <= 1024"
     */
    private static class KeySizeConstraint extends Constraint {

        private int minSize;            // the minimal available key size
        private int maxSize;            // the maximal available key size
        private int prohibitedSize = -1;    // unavailable key sizes

        public KeySizeConstraint(String algo, Operator operator, int length) {
            algorithm = algo;
            switch (operator) {
                case EQ:      // an unavailable key size
                    this.minSize = 0;
                    this.maxSize = Integer.MAX_VALUE;
                    prohibitedSize = length;
                    break;
                case NE:
                    this.minSize = length;
                    this.maxSize = length;
                    break;
                case LT:
                    this.minSize = length;
                    this.maxSize = Integer.MAX_VALUE;
                    break;
                case LE:
                    this.minSize = length + 1;
                    this.maxSize = Integer.MAX_VALUE;
                    break;
                case GT:
                    this.minSize = 0;
                    this.maxSize = length;
                    break;
                case GE:
                    this.minSize = 0;
                    this.maxSize = length > 1 ? (length - 1) : 0;
                    break;
                default:
                    // unlikely to happen
                    this.minSize = Integer.MAX_VALUE;
                    this.maxSize = -1;
            }
        }

        /*
         * If we are passed a certificate, extract the public key and use it.
         *
         * Check if each constraint fails and check if there is a linked
         * constraint  Any permitted constraint will exit the linked list
         * to allow the operation.
         */
        public void permits(CertConstraintParameters cp)
                throws CertPathValidatorException {
            if (!permitsImpl(cp.getCertificate().getPublicKey())) {
                if (nextConstraint != null) {
                    nextConstraint.permits(cp);
                    return;
                }
                throw new CertPathValidatorException(
                        "Algorithm constraints check failed on keysize limits",
                        null, null, -1, BasicReason.ALGORITHM_CONSTRAINED);
            }
        }


        // Check if key constraint disable the specified key
        // Uses old style permit()
        public boolean permits(Key key) {
            // If we recursively find a constraint that permits us to use
            // this key, return true and skip any other constraint checks.
            if (nextConstraint != null && nextConstraint.permits(key)) {
                return true;
            }
            if (debug != null) {
                debug.println("KeySizeConstraints.permits(): " + algorithm);
            }

            return permitsImpl(key);
        }

        private boolean permitsImpl(Key key) {
            // Verify this constraint is for this public key algorithm
            if (algorithm.compareToIgnoreCase(key.getAlgorithm()) != 0) {
                return true;
            }

            int size = KeyUtil.getKeySize(key);
            if (size == 0) {
                return false;    // we don't allow any key of size 0.
            } else if (size > 0) {
                return !((size < minSize) || (size > maxSize) ||
                    (prohibitedSize == size));
            }   // Otherwise, the key size is not accessible. Conservatively,
                // please don't disable such keys.

            return true;
        }
    }
}

