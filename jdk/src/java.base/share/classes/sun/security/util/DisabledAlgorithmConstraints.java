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
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.TimeZone;
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

    // the known security property, jdk.jar.disabledAlgorithms
    public static final String PROPERTY_JAR_DISABLED_ALGS =
            "jdk.jar.disabledAlgorithms";

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

    /**
     * Initialize algorithm constraints with the specified security property
     * for a specific usage type.
     *
     * @param propertyName the security property name that define the disabled
     *        algorithm constraints
     * @param decomposer an alternate AlgorithmDecomposer.
     */
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

        private static class Holder {
            private static final Pattern DENY_AFTER_PATTERN = Pattern.compile(
                    "denyAfter\\s+(\\d{4})-(\\d{2})-(\\d{2})");
        }

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
                    algorithm = constraintEntry.toUpperCase(Locale.ENGLISH);
                    if (!constraintsMap.containsKey(algorithm)) {
                        constraintsMap.putIfAbsent(algorithm,
                                new HashSet<>());
                    }
                    continue;
                }

                // Convert constraint conditions into Constraint classes
                Constraint c, lastConstraint = null;
                // Allow only one jdkCA entry per constraint entry
                boolean jdkCALimit = false;
                // Allow only one denyAfter entry per constraint entry
                boolean denyAfterLimit = false;

                for (String entry : policy.split("&")) {
                    entry = entry.trim();

                    Matcher matcher;
                    if (entry.startsWith("keySize")) {
                        if (debug != null) {
                            debug.println("Constraints set to keySize: " +
                                    entry);
                        }
                        StringTokenizer tokens = new StringTokenizer(entry);
                        if (!"keySize".equals(tokens.nextToken())) {
                            throw new IllegalArgumentException("Error in " +
                                    "security property. Constraint unknown: " +
                                    entry);
                        }
                        c = new KeySizeConstraint(algorithm,
                                KeySizeConstraint.Operator.of(tokens.nextToken()),
                                Integer.parseInt(tokens.nextToken()));

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

                    } else if(entry.startsWith("denyAfter") &&
                            (matcher = Holder.DENY_AFTER_PATTERN.matcher(entry))
                                    .matches()) {
                        if (debug != null) {
                            debug.println("Constraints set to denyAfter");
                        }
                        if (denyAfterLimit) {
                            throw new IllegalArgumentException("Only one " +
                                    "denyAfter entry allowed in property. " +
                                    "Constraint: " + constraintEntry);
                        }
                        int year = Integer.parseInt(matcher.group(1));
                        int month = Integer.parseInt(matcher.group(2));
                        int day = Integer.parseInt(matcher.group(3));
                        c = new DenyAfterConstraint(algorithm, year, month,
                                day);
                        denyAfterLimit = true;
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

    /**
     * This abstract Constraint class for algorithm-based checking
     * may contain one or more constraints.  If the '&' on the {@Security}
     * property is used, multiple constraints have been grouped together
     * requiring all the constraints to fail for the check to be disallowed.
     *
     * If the class contains multiple constraints, the next constraint
     * is stored in {@code nextConstraint} in linked-list fashion.
     */
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
         * Check if an algorithm constraint is permitted with a given key.
         *
         * If the check inside of {@code permit()} fails, it must call
         * {@code next()} with the same {@code Key} parameter passed if
         * multiple constraints need to be checked.
         *
         * @param key Public key
         * @return 'true' if constraint is allowed, 'false' if disallowed.
         */
        public boolean permits(Key key) {
            return true;
        }

        /**
         * Check if an algorithm constraint is permitted with a given
         * CertConstraintParameters.
         *
         * If the check inside of {@code permits()} fails, it must call
         * {@code next()} with the same {@code CertConstraintParameters}
         * parameter passed if multiple constraints need to be checked.
         *
         * @param cp CertConstraintParameter containing certificate info
         * @throws CertPathValidatorException if constraint disallows.
         *
         */
        public abstract void permits(CertConstraintParameters cp)
                throws CertPathValidatorException;

        /**
         * Recursively check if the constraints are allowed.
         *
         * If {@code nextConstraint} is non-null, this method will
         * call {@code nextConstraint}'s {@code permits()} to check if the
         * constraint is allowed or denied.  If the constraint's
         * {@code permits()} is allowed, this method will exit this and any
         * recursive next() calls, returning 'true'.  If the constraints called
         * were disallowed, the last constraint will throw
         * {@code CertPathValidatorException}.
         *
         * @param cp CertConstraintParameters
         * @return 'true' if constraint allows the operation, 'false' if
         * we are at the end of the constraint list or,
         * {@code nextConstraint} is null.
         */
        boolean next(CertConstraintParameters cp)
                throws CertPathValidatorException {
            if (nextConstraint != null) {
                nextConstraint.permits(cp);
                return true;
            }
            return false;
        }

        /**
         * Recursively check if this constraint is allowed,
         *
         * If {@code nextConstraint} is non-null, this method will
         * call {@code nextConstraint}'s {@code permit()} to check if the
         * constraint is allowed or denied.  If the constraint's
         * {@code permit()} is allowed, this method will exit this and any
         * recursive next() calls, returning 'true'.  If the constraints
         * called were disallowed the check will exit with 'false'.
         *
         * @param key Public key
         * @return 'true' if constraint allows the operation, 'false' if
         * the constraint denies the operation.
         */
        boolean next(Key key) {
            if (nextConstraint != null && nextConstraint.permits(key)) {
                return true;
            }
            return false;
        }
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
         * Check if CertConstraintParameters has a trusted match, if it does
         * call next() for any following constraints. If it does not, exit
         * as this constraint(s) does not restrict the operation.
         */
        public void permits(CertConstraintParameters cp)
                throws CertPathValidatorException {
            if (debug != null) {
                debug.println("jdkCAConstraints.permits(): " + algorithm);
            }

            // Check chain has a trust anchor in cacerts
            if (cp.isTrustedMatch()) {
                if (next(cp)) {
                    return;
                }
                throw new CertPathValidatorException(
                        "Algorithm constraints check failed on certificate " +
                                "anchor limits. " + algorithm + " used with " +
                                cp.getCertificate().getSubjectX500Principal(),
                        null, null, -1, BasicReason.ALGORITHM_CONSTRAINED);
            }
        }
    }

    /*
     * This class handles the denyAfter constraint.  The date is in the UTC/GMT
     * timezone.
     */
     private static class DenyAfterConstraint extends Constraint {
         private Date denyAfterDate;
         private static final SimpleDateFormat dateFormat =
                 new SimpleDateFormat("EEE, MMM d HH:mm:ss z yyyy");

         DenyAfterConstraint(String algo, int year, int month, int day) {
             Calendar c;

             algorithm = algo;

             if (debug != null) {
                 debug.println("DenyAfterConstraint read in as:  year " +
                         year + ", month = " + month + ", day = " + day);
             }

             c = new Calendar.Builder().setTimeZone(TimeZone.getTimeZone("GMT"))
                     .setDate(year, month - 1, day).build();

             if (year > c.getActualMaximum(Calendar.YEAR) ||
                     year < c.getActualMinimum(Calendar.YEAR)) {
                 throw new IllegalArgumentException(
                         "Invalid year given in constraint: " + year);
             }
             if ((month - 1) > c.getActualMaximum(Calendar.MONTH) ||
                     (month - 1) < c.getActualMinimum(Calendar.MONTH)) {
                 throw new IllegalArgumentException(
                         "Invalid month given in constraint: " + month);
             }
             if (day > c.getActualMaximum(Calendar.DAY_OF_MONTH) ||
                     day < c.getActualMinimum(Calendar.DAY_OF_MONTH)) {
                 throw new IllegalArgumentException(
                         "Invalid Day of Month given in constraint: " + day);
             }

             denyAfterDate = c.getTime();
             if (debug != null) {
                 debug.println("DenyAfterConstraint date set to: " +
                         dateFormat.format(denyAfterDate));
             }
         }

         /*
          * Checking that the provided date is not beyond the constraint date.
          * The provided date can be the PKIXParameter date if given,
          * otherwise it is the current date.
          *
          * If the constraint disallows, call next() for any following
          * constraints. Throw an exception if this is the last constraint.
          */
         @Override
         public void permits(CertConstraintParameters cp)
                 throws CertPathValidatorException {
             Date currentDate;
             String errmsg;

             if (cp.getJARTimestamp() != null) {
                 currentDate = cp.getJARTimestamp().getTimestamp();
                 errmsg = "JAR Timestamp date: ";
             } else if (cp.getPKIXParamDate() != null) {
                 currentDate = cp.getPKIXParamDate();
                 errmsg = "PKIXParameter date: ";
             } else {
                 currentDate = new Date();
                 errmsg = "Certificate date: ";
             }

             if (!denyAfterDate.after(currentDate)) {
                 if (next(cp)) {
                     return;
                 }
                 throw new CertPathValidatorException(
                         "denyAfter constraint check failed: " + algorithm +
                                 " used with Constraint date: " +
                                 dateFormat.format(denyAfterDate) + "; "
                                 + errmsg + dateFormat.format(currentDate),
                         null, null, -1, BasicReason.ALGORITHM_CONSTRAINED);
             }
         }

         /*
          * Return result if the constraint's date is beyond the current date
          * in UTC timezone.
          */
         public boolean permits(Key key) {
             if (next(key)) {
                 return true;
             }
             if (debug != null) {
                 debug.println("DenyAfterConstraints.permits(): " + algorithm);
             }

             return denyAfterDate.after(new Date());
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
        private int size;

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
                        "Algorithm constraints check failed on keysize limits. "
                                + algorithm + " " + size + "bit key used with "
                                + cp.getCertificate().getSubjectX500Principal(),
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

            size = KeyUtil.getKeySize(key);
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

