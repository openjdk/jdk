/*
 * Copyright (c) 1999, 2022, Oracle and/or its affiliates. All rights reserved.
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

package javax.crypto;

import java.io.Serial;
import java.security.*;
import java.security.spec.AlgorithmParameterSpec;
import java.io.Serializable;
import java.util.Enumeration;
import java.util.Vector;

import javax.crypto.spec.*;

/**
 * The {@code CryptoPermission} class extends the
 * {@code java.security.Permission} class. A
 * {@code CryptoPermission} object is used to represent
 * the ability of an application/applet to use certain
 * algorithms with certain key sizes and other
 * restrictions in certain environments.
 *
 * @see java.security.Permission
 *
 * @author Jan Luehe
 * @author Sharon Liu
 * @since 1.4
 */
class CryptoPermission extends java.security.Permission {

    @java.io.Serial
    private static final long serialVersionUID = 8987399626114087514L;

    private final String alg;
    private int maxKeySize = Integer.MAX_VALUE; // no restriction on maxKeySize
    private String exemptionMechanism = null;
    @SuppressWarnings("serial") // Not statically typed as Serializable
    private AlgorithmParameterSpec algParamSpec = null;
    private boolean checkParam = false; // no restriction on param

    static final String ALG_NAME_WILDCARD = "*";

    /**
     * Constructor that takes an algorithm name.
     *
     * This constructor implies that the given algorithm can be
     * used without any restrictions.
     *
     * @param alg the algorithm name.
     */
    CryptoPermission(String alg) {
        super(null);
        this.alg = alg;
    }

    /**
     * Constructor that takes an algorithm name and a maximum
     * key size.
     *
     * This constructor implies that the given algorithm can be
     * used with a key size up to {@code maxKeySize}.
     *
     * @param alg the algorithm name.
     *
     * @param maxKeySize the maximum allowable key size,
     * specified in number of bits.
     */
    CryptoPermission(String alg, int maxKeySize) {
        super(null);
        this.alg = alg;
        this.maxKeySize = maxKeySize;
    }

    /**
     * Constructor that takes an algorithm name, a maximum
     * key size, and an {@code AlgorithmParameterSpec} object.
     *
     * This constructor implies that the given algorithm can be
     * used with a key size up to {@code maxKeySize}, and
     * algorithm
     * parameters up to the limits set in {@code algParamSpec}.
     *
     * @param alg the algorithm name.
     *
     * @param maxKeySize the maximum allowable key size,
     * specified in number of bits.
     *
     * @param algParamSpec the limits for allowable algorithm
     * parameters.
     */
    CryptoPermission(String alg,
                     int maxKeySize,
                     AlgorithmParameterSpec algParamSpec) {
        super(null);
        this.alg = alg;
        this.maxKeySize = maxKeySize;
        this.checkParam = true;
        this.algParamSpec = algParamSpec;
    }

    /**
     * Constructor that takes an algorithm name and the name of
     * an exemption mechanism.
     *
     * This constructor implies that the given algorithm can be
     * used without any key size or algorithm parameter restrictions
     * provided that the specified exemption mechanism is enforced.
     *
     * @param alg the algorithm name.
     *
     * @param exemptionMechanism the name of the exemption mechanism.
     */
    CryptoPermission(String alg,
                     String exemptionMechanism) {
        super(null);
        this.alg = alg;
        this.exemptionMechanism = exemptionMechanism;
    }

    /**
     * Constructor that takes an algorithm name, a maximum key
     * size, and the name of an exemption mechanism.
     *
     * This constructor implies that the given algorithm can be
     * used with a key size up to {@code maxKeySize}
     * provided that the
     * specified exemption mechanism is enforced.
     *
     * @param alg the algorithm name.
     * @param maxKeySize the maximum allowable key size,
     * specified in number of bits.
     * @param exemptionMechanism the name of the exemption
     * mechanism.
     */
    CryptoPermission(String alg,
                     int maxKeySize,
                     String exemptionMechanism) {
        super(null);
        this.alg = alg;
        this.exemptionMechanism = exemptionMechanism;
        this.maxKeySize = maxKeySize;
    }

    /**
     * Constructor that takes an algorithm name, a maximum key
     * size, the name of an exemption mechanism, and an
     * {@code AlgorithmParameterSpec} object.
     *
     * This constructor implies that the given algorithm can be
     * used with a key size up to {@code maxKeySize}
     * and algorithm
     * parameters up to the limits set in {@code algParamSpec}
     * provided that
     * the specified exemption mechanism is enforced.
     *
     * @param alg the algorithm name.
     * @param maxKeySize the maximum allowable key size,
     * specified in number of bits.
     * @param algParamSpec the limit for allowable algorithm
     *  parameter spec.
     * @param exemptionMechanism the name of the exemption
     * mechanism.
     */
    CryptoPermission(String alg,
                     int maxKeySize,
                     AlgorithmParameterSpec algParamSpec,
                     String exemptionMechanism) {
        super(null);
        this.alg = alg;
        this.exemptionMechanism = exemptionMechanism;
        this.maxKeySize = maxKeySize;
        this.checkParam = true;
        this.algParamSpec = algParamSpec;
    }

    /**
     * Checks if the specified permission is "implied" by
     * this object.
     * <p>
     * More specifically, this method returns {@code true} if:
     * <ul>
     * <li> <i>p</i> is an instance of {@code CryptoPermission}, and</li>
     * <li> <i>p</i>'s algorithm name equals or (in the case of wildcards)
     *       is implied by this permission's algorithm name, and</li>
     * <li> <i>p</i>'s maximum allowable key size is less or
     *       equal to this permission's maximum allowable key size, and</li>
     * <li> <i>p</i>'s algorithm parameter spec equals or is
     *        implied by this permission's algorithm parameter spec, and</li>
     * <li> <i>p</i>'s exemptionMechanism equals or
     *        is implied by this permission's
     *        exemptionMechanism (a {@code null} exemption mechanism
     *        implies any other exemption mechanism).</li>
     * </ul>
     *
     * @param p the permission to check against.
     *
     * @return {@code true} if the specified permission is equal to or
     * implied by this permission, {@code false} otherwise.
     */
    public boolean implies(Permission p) {
        if (!(p instanceof CryptoPermission cp))
            return false;

        if ((!alg.equalsIgnoreCase(cp.alg)) &&
            (!alg.equalsIgnoreCase(ALG_NAME_WILDCARD))) {
            return false;
        }

        // alg is the same as cp's alg or
        // alg is a wildcard.
        if (cp.maxKeySize <= this.maxKeySize) {
            // check algParamSpec.
            if (!impliesParameterSpec(cp.checkParam, cp.algParamSpec)) {
                return false;
            }

            // check exemptionMechanism.
            return impliesExemptionMechanism(cp.exemptionMechanism);
        }

        return false;
    }

    /**
     * Checks two {@code CryptoPermission} objects for equality.
     * Checks that {@code obj} is a {@code CryptoPermission}
     * object, and has the same algorithm name,
     * exemption mechanism name, maximum allowable key size and
     * algorithm parameter spec as this object.
     * @param obj the object to test for equality with this object.
     * @return {@code true} if {@code obj} is equal to this object.
     */
    public boolean equals(Object obj) {
        if (obj == this)
            return true;

        if (!(obj instanceof CryptoPermission that))
            return false;

        if (!(alg.equalsIgnoreCase(that.alg)) ||
            (maxKeySize != that.maxKeySize)) {
            return false;
        }
        if (this.checkParam != that.checkParam) {
            return false;
        }
        return (equalObjects(this.exemptionMechanism,
                             that.exemptionMechanism) &&
                equalObjects(this.algParamSpec,
                             that.algParamSpec));
    }

    /**
     * Returns the hash code value for this object.
     *
     * @return a hash code value for this object.
     */

    public int hashCode() {
        int retval = alg.hashCode();
        retval ^= maxKeySize;
        if (exemptionMechanism != null) {
            retval ^= exemptionMechanism.hashCode();
        }
        if (checkParam) retval ^= 100;
        if (algParamSpec != null) {
            retval ^= algParamSpec.hashCode();
        }
        return retval;
    }

    /**
     * There is no action defined for a {@code CryptoPermission}
     * object.
     */
    public String getActions()
    {
        return null;
    }

    /**
     * Returns a new {@code PermissionCollection} object for storing
     * {@code CryptoPermission} objects.
     *
     * @return a new {@code PermissionCollection} object suitable for storing
     * CryptoPermissions.
     */

    public PermissionCollection newPermissionCollection() {
        return new CryptoPermissionCollection();
    }

    /**
     * Returns the algorithm name associated with
     * this {@code CryptoPermission} object.
     */
    final String getAlgorithm() {
        return alg;
    }

    /**
     * Returns the exemption mechanism name
     * associated with this {@code CryptoPermission}
     * object.
     */
    final String getExemptionMechanism() {
        return exemptionMechanism;
    }

    /**
     * Returns the maximum allowable key size associated
     * with this {@code CryptoPermission} object.
     */
    final int getMaxKeySize() {
        return maxKeySize;
    }

    /**
     * Returns {@code true} if there is a limitation on the
     * {@code AlgorithmParameterSpec} associated with this
     * {@code CryptoPermission} object and {@code false} if otherwise.
     */
    final boolean getCheckParam() {
        return checkParam;
    }

    /**
     * Returns the AlgorithmParameterSpec
     * associated with this CryptoPermission
     * object.
     */
    final AlgorithmParameterSpec getAlgorithmParameterSpec() {
        return algParamSpec;
    }

    /**
     * Returns a string describing this {@code CryptoPermission} object.
     * The convention is to specify the class name, the algorithm name,
     * the maximum allowable key size, and the name of the exemption mechanism,
     * in the following
     * format: '("ClassName" "algorithm" "keysize" "exemption_mechanism")'.
     *
     * @return information about this {@code CryptoPermission} object.
     */
    public String toString() {
        StringBuilder buf = new StringBuilder(100);
        buf.append("(CryptoPermission " + alg + " " + maxKeySize);
        if (algParamSpec != null) {
            if (algParamSpec instanceof RC2ParameterSpec) {
                buf.append(" , effective " +
                    ((RC2ParameterSpec)algParamSpec).getEffectiveKeyBits());
            } else if (algParamSpec instanceof RC5ParameterSpec) {
                buf.append(" , rounds " +
                    ((RC5ParameterSpec)algParamSpec).getRounds());
            }
        }
        if (exemptionMechanism != null) { // OPTIONAL
            buf.append(" " + exemptionMechanism);
        }
        buf.append(")");
        return buf.toString();
    }

    private boolean impliesExemptionMechanism(String exemptionMechanism) {
        if (this.exemptionMechanism == null) {
            return true;
        }

        if (exemptionMechanism == null) {
            return false;
        }

        return this.exemptionMechanism.equals(exemptionMechanism);
    }

    private boolean impliesParameterSpec(boolean checkParam,
                                         AlgorithmParameterSpec algParamSpec) {
        if ((this.checkParam) && checkParam) {
            if (algParamSpec == null) {
                return true;
            } else if (this.algParamSpec == null) {
                return false;
            }

            if (this.algParamSpec.getClass() != algParamSpec.getClass()) {
                return false;
            }

            if (algParamSpec instanceof RC2ParameterSpec) {
                if (((RC2ParameterSpec)algParamSpec).getEffectiveKeyBits() <=
                    ((RC2ParameterSpec)
                     (this.algParamSpec)).getEffectiveKeyBits()) {
                    return true;
                }
            }

            if (algParamSpec instanceof RC5ParameterSpec) {
                if (((RC5ParameterSpec)algParamSpec).getRounds() <=
                    ((RC5ParameterSpec)this.algParamSpec).getRounds()) {
                    return true;
                }
            }

            if (algParamSpec instanceof PBEParameterSpec) {
                if (((PBEParameterSpec)algParamSpec).getIterationCount() <=
                    ((PBEParameterSpec)this.algParamSpec).getIterationCount()) {
                    return true;
                }
            }

            // For classes we don't know, the following
            // may be the best try.
            return this.algParamSpec.equals(algParamSpec);
        } else {
            return !this.checkParam;
        }
    }

    private boolean equalObjects(Object obj1, Object obj2) {
        if (obj1 == null) {
            return (obj2 == null);
        }

        return obj1.equals(obj2);
    }
}

/**
 * A {@code CryptoPermissionCollection} object stores a set of
 * {@code CryptoPermission} objects.
 *
 * @see java.security.Permission
 * @see java.security.Permissions
 * @see java.security.PermissionCollection
 *
 * @author Sharon Liu
 */
final class CryptoPermissionCollection extends PermissionCollection
    implements Serializable
{
    @Serial
    private static final long serialVersionUID = -511215555898802763L;

    private final Vector<Permission> permissions;

    /**
     * Creates an empty CryptoPermissionCollection
     * object.
     */
    CryptoPermissionCollection() {
        permissions = new Vector<>(3);
    }

    /**
     * Adds a permission to the {@code CryptoPermissionCollection} object.
     *
     * @param permission the {@code Permission} object to add.
     *
     * @exception SecurityException if this
     * {@code CryptoPermissionCollection} object has been marked
     * <i>readOnly</i>.
     */
    public void add(Permission permission) {
        if (isReadOnly())
            throw new SecurityException("attempt to add a Permission " +
                                        "to a readonly PermissionCollection");

        if (!(permission instanceof CryptoPermission))
            return;

        permissions.addElement(permission);
    }

    /**
     * Check and see if this {@code CryptoPermission} object implies
     * the given {@code Permission} object.
     *
     * @param permission the {@code Permission} object to compare
     *
     * @return {@code true} if the given permission is implied by this
     * {@code CryptoPermissionCollection}, {@code false} if not.
     */
    public boolean implies(Permission permission) {
        if (!(permission instanceof CryptoPermission cp))
            return false;

        Enumeration<Permission> e = permissions.elements();

        while (e.hasMoreElements()) {
            CryptoPermission x = (CryptoPermission) e.nextElement();
            if (x.implies(cp)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Returns an enumeration of all the {@code CryptoPermission} objects
     * in the container.
     *
     * @return an enumeration of all the {@code CryptoPermission} objects.
     */

    public Enumeration<Permission> elements() {
        return permissions.elements();
    }
}
