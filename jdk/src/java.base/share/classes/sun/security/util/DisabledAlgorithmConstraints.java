/*
 * Copyright (c) 2010, 2015, Oracle and/or its affiliates. All rights reserved.
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

import java.security.AlgorithmConstraints;
import java.security.CryptoPrimitive;
import java.security.AlgorithmParameters;

import java.security.Key;
import java.security.Security;
import java.security.PrivilegedAction;
import java.security.AccessController;

import java.util.Locale;
import java.util.Set;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.HashMap;
import java.util.regex.Pattern;
import java.util.regex.Matcher;

/**
 * Algorithm constraints for disabled algorithms property
 *
 * See the "jdk.certpath.disabledAlgorithms" specification in java.security
 * for the syntax of the disabled algorithm string.
 */
public class DisabledAlgorithmConstraints implements AlgorithmConstraints {

    // the known security property, jdk.certpath.disabledAlgorithms
    public final static String PROPERTY_CERTPATH_DISABLED_ALGS =
            "jdk.certpath.disabledAlgorithms";

    // the known security property, jdk.tls.disabledAlgorithms
    public final static String PROPERTY_TLS_DISABLED_ALGS =
            "jdk.tls.disabledAlgorithms";

    private final static Map<String, String[]> disabledAlgorithmsMap =
                                                            new HashMap<>();
    private final static Map<String, KeySizeConstraints> keySizeConstraintsMap =
                                                            new HashMap<>();

    private String[] disabledAlgorithms;
    private KeySizeConstraints keySizeConstraints;

    /**
     * Initialize algorithm constraints with the specified security property.
     *
     * @param propertyName the security property name that define the disabled
     *        algorithm constraints
     */
    public DisabledAlgorithmConstraints(String propertyName) {
        // Both disabledAlgorithmsMap and keySizeConstraintsMap are
        // synchronized with the lock of disabledAlgorithmsMap.
        synchronized (disabledAlgorithmsMap) {
            if(!disabledAlgorithmsMap.containsKey(propertyName)) {
                loadDisabledAlgorithmsMap(propertyName);
            }

            disabledAlgorithms = disabledAlgorithmsMap.get(propertyName);
            keySizeConstraints = keySizeConstraintsMap.get(propertyName);
        }
    }

    @Override
    final public boolean permits(Set<CryptoPrimitive> primitives,
            String algorithm, AlgorithmParameters parameters) {

        if (algorithm == null || algorithm.length() == 0) {
            throw new IllegalArgumentException("No algorithm name specified");
        }

        if (primitives == null || primitives.isEmpty()) {
            throw new IllegalArgumentException(
                        "No cryptographic primitive specified");
        }

        Set<String> elements = null;
        for (String disabled : disabledAlgorithms) {
            if (disabled == null || disabled.isEmpty()) {
                continue;
            }

            // check the full name
            if (disabled.equalsIgnoreCase(algorithm)) {
                return false;
            }

            // decompose the algorithm into sub-elements
            if (elements == null) {
                elements = decomposes(algorithm);
            }

            // check the items of the algorithm
            for (String element : elements) {
                if (disabled.equalsIgnoreCase(element)) {
                    return false;
                }
            }
        }

        return true;
    }

    @Override
    final public boolean permits(Set<CryptoPrimitive> primitives, Key key) {
        return checkConstraints(primitives, "", key, null);
    }

    @Override
    final public boolean permits(Set<CryptoPrimitive> primitives,
            String algorithm, Key key, AlgorithmParameters parameters) {

        if (algorithm == null || algorithm.length() == 0) {
            throw new IllegalArgumentException("No algorithm name specified");
        }

        return checkConstraints(primitives, algorithm, key, parameters);
    }

    /**
     * Decompose the standard algorithm name into sub-elements.
     * <p>
     * For example, we need to decompose "SHA1WithRSA" into "SHA1" and "RSA"
     * so that we can check the "SHA1" and "RSA" algorithm constraints
     * separately.
     * <p>
     * Please override the method if need to support more name pattern.
     */
    protected Set<String> decomposes(String algorithm) {
        if (algorithm == null || algorithm.length() == 0) {
            return new HashSet<String>();
        }

        // algorithm/mode/padding
        Pattern transPattern = Pattern.compile("/");
        String[] transTockens = transPattern.split(algorithm);

        Set<String> elements = new HashSet<String>();
        for (String transTocken : transTockens) {
            if (transTocken == null || transTocken.length() == 0) {
                continue;
            }

            // PBEWith<digest>And<encryption>
            // PBEWith<prf>And<encryption>
            // OAEPWith<digest>And<mgf>Padding
            // <digest>with<encryption>
            // <digest>with<encryption>and<mgf>
            // <digest>with<encryption>in<format>
            Pattern pattern =
                    Pattern.compile("with|and|in", Pattern.CASE_INSENSITIVE);
            String[] tokens = pattern.split(transTocken);

            for (String token : tokens) {
                if (token == null || token.length() == 0) {
                    continue;
                }

                elements.add(token);
            }
        }

        // In Java standard algorithm name specification, for different
        // purpose, the SHA-1 and SHA-2 algorithm names are different. For
        // example, for MessageDigest, the standard name is "SHA-256", while
        // for Signature, the digest algorithm component is "SHA256" for
        // signature algorithm "SHA256withRSA". So we need to check both
        // "SHA-256" and "SHA256" to make the right constraint checking.

        // handle special name: SHA-1 and SHA1
        if (elements.contains("SHA1") && !elements.contains("SHA-1")) {
            elements.add("SHA-1");
        }
        if (elements.contains("SHA-1") && !elements.contains("SHA1")) {
            elements.add("SHA1");
        }

        // handle special name: SHA-224 and SHA224
        if (elements.contains("SHA224") && !elements.contains("SHA-224")) {
            elements.add("SHA-224");
        }
        if (elements.contains("SHA-224") && !elements.contains("SHA224")) {
            elements.add("SHA224");
        }

        // handle special name: SHA-256 and SHA256
        if (elements.contains("SHA256") && !elements.contains("SHA-256")) {
            elements.add("SHA-256");
        }
        if (elements.contains("SHA-256") && !elements.contains("SHA256")) {
            elements.add("SHA256");
        }

        // handle special name: SHA-384 and SHA384
        if (elements.contains("SHA384") && !elements.contains("SHA-384")) {
            elements.add("SHA-384");
        }
        if (elements.contains("SHA-384") && !elements.contains("SHA384")) {
            elements.add("SHA384");
        }

        // handle special name: SHA-512 and SHA512
        if (elements.contains("SHA512") && !elements.contains("SHA-512")) {
            elements.add("SHA-512");
        }
        if (elements.contains("SHA-512") && !elements.contains("SHA512")) {
            elements.add("SHA512");
        }

        return elements;
    }

    // Check algorithm constraints
    private boolean checkConstraints(Set<CryptoPrimitive> primitives,
            String algorithm, Key key, AlgorithmParameters parameters) {

        // check the key parameter, it cannot be null.
        if (key == null) {
            throw new IllegalArgumentException("The key cannot be null");
        }

        // check the target algorithm
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
        if (keySizeConstraints.disables(key)) {
            return false;
        }

        return true;
    }

    // Get disabled algorithm constraints from the specified security property.
    private static void loadDisabledAlgorithmsMap(
            final String propertyName) {

        String property = AccessController.doPrivileged(
            new PrivilegedAction<String>() {
                public String run() {
                    return Security.getProperty(propertyName);
                }
            });

        String[] algorithmsInProperty = null;

        if (property != null && !property.isEmpty()) {

            // remove double quote marks from beginning/end of the property
            if (property.charAt(0) == '"' &&
                    property.charAt(property.length() - 1) == '"') {
                property = property.substring(1, property.length() - 1);
            }

            algorithmsInProperty = property.split(",");
            for (int i = 0; i < algorithmsInProperty.length; i++) {
                algorithmsInProperty[i] = algorithmsInProperty[i].trim();
            }
        }

        // map the disabled algorithms
        if (algorithmsInProperty == null) {
            algorithmsInProperty = new String[0];
        }
        disabledAlgorithmsMap.put(propertyName, algorithmsInProperty);

        // map the key constraints
        KeySizeConstraints keySizeConstraints =
            new KeySizeConstraints(algorithmsInProperty);
        keySizeConstraintsMap.put(propertyName, keySizeConstraints);
    }

    /**
     * key constraints
     */
    private static class KeySizeConstraints {
        private static final Pattern pattern = Pattern.compile(
                "(\\S+)\\s+keySize\\s*(<=|<|==|!=|>|>=)\\s*(\\d+)");

        private Map<String, Set<KeySizeConstraint>> constraintsMap =
            Collections.synchronizedMap(
                        new HashMap<String, Set<KeySizeConstraint>>());

        public KeySizeConstraints(String[] restrictions) {
            for (String restriction : restrictions) {
                if (restriction == null || restriction.isEmpty()) {
                    continue;
                }

                Matcher matcher = pattern.matcher(restriction);
                if (matcher.matches()) {
                    String algorithm = matcher.group(1);

                    KeySizeConstraint.Operator operator =
                             KeySizeConstraint.Operator.of(matcher.group(2));
                    int length = Integer.parseInt(matcher.group(3));

                    algorithm = algorithm.toLowerCase(Locale.ENGLISH);

                    synchronized (constraintsMap) {
                        if (!constraintsMap.containsKey(algorithm)) {
                            constraintsMap.put(algorithm,
                                new HashSet<KeySizeConstraint>());
                        }

                        Set<KeySizeConstraint> constraintSet =
                            constraintsMap.get(algorithm);
                        KeySizeConstraint constraint =
                            new KeySizeConstraint(operator, length);
                        constraintSet.add(constraint);
                    }
                }
            }
        }

        // Does this KeySizeConstraints disable the specified key?
        public boolean disables(Key key) {
            String algorithm = key.getAlgorithm().toLowerCase(Locale.ENGLISH);
            synchronized (constraintsMap) {
                if (constraintsMap.containsKey(algorithm)) {
                    Set<KeySizeConstraint> constraintSet =
                                        constraintsMap.get(algorithm);
                    for (KeySizeConstraint constraint : constraintSet) {
                        if (constraint.disables(key)) {
                            return true;
                        }
                    }
                }
            }

            return false;
        }
    }

    /**
     * Key size constraint.
     *
     * e.g.  "keysize <= 1024"
     */
    private static class KeySizeConstraint {
        // operator
        static enum Operator {
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

                throw new IllegalArgumentException(
                        s + " is not a legal Operator");
            }
        }

        private int minSize;            // the minimal available key size
        private int maxSize;            // the maximal available key size
        private int prohibitedSize = -1;    // unavailable key sizes

        public KeySizeConstraint(Operator operator, int length) {
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

        // Does this key constraint disable the specified key?
        public boolean disables(Key key) {
            int size = KeyUtil.getKeySize(key);

            if (size == 0) {
                return true;    // we don't allow any key of size 0.
            } else if (size > 0) {
                return ((size < minSize) || (size > maxSize) ||
                    (prohibitedSize == size));
            }   // Otherwise, the key size is not accessible. Conservatively,
                // please don't disable such keys.

            return false;
        }
    }

}

