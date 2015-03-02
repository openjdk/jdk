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

import java.security.CryptoPrimitive;
import java.security.AlgorithmParameters;
import java.security.Key;
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
public class DisabledAlgorithmConstraints extends AbstractAlgorithmConstraints {

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

    private final String[] disabledAlgorithms;
    private final KeySizeConstraints keySizeConstraints;

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
        disabledAlgorithms = getAlgorithms(disabledAlgorithmsMap, propertyName);
        keySizeConstraints = getKeySizeConstraints(disabledAlgorithms,
                propertyName);
    }

    @Override
    final public boolean permits(Set<CryptoPrimitive> primitives,
            String algorithm, AlgorithmParameters parameters) {

        if (primitives == null || primitives.isEmpty()) {
            throw new IllegalArgumentException(
                        "No cryptographic primitive specified");
        }

        return checkAlgorithm(disabledAlgorithms, algorithm, decomposer);
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

    private static KeySizeConstraints getKeySizeConstraints(
            String[] disabledAlgorithms, String propertyName) {
        synchronized (keySizeConstraintsMap) {
            if(!keySizeConstraintsMap.containsKey(propertyName)) {
                // map the key constraints
                KeySizeConstraints keySizeConstraints =
                        new KeySizeConstraints(disabledAlgorithms);
                keySizeConstraintsMap.put(propertyName, keySizeConstraints);
            }

            return keySizeConstraintsMap.get(propertyName);
        }
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

