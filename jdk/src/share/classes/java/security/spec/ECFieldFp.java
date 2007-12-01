/*
 * Copyright 2003 Sun Microsystems, Inc.  All Rights Reserved.
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
package java.security.spec;

import java.math.BigInteger;
import java.util.Arrays;

/**
 * This immutable class defines an elliptic curve (EC) prime
 * finite field.
 *
 * @see ECField
 *
 * @author Valerie Peng
 *
 * @since 1.5
 */
public class ECFieldFp implements ECField {

    private BigInteger p;

    /**
     * Creates an elliptic curve prime finite field
     * with the specified prime <code>p</code>.
     * @param p the prime.
     * @exception NullPointerException if <code>p</code> is null.
     * @exception IllegalArgumentException if <code>p</code>
     * is not positive.
     */
    public ECFieldFp(BigInteger p) {
        if (p.signum() != 1) {
            throw new IllegalArgumentException("p is not positive");
        }
        this.p = p;
    }

    /**
     * Returns the field size in bits which is size of prime p
     * for this prime finite field.
     * @return the field size in bits.
     */
    public int getFieldSize() {
        return p.bitLength();
    };

    /**
     * Returns the prime <code>p</code> of this prime finite field.
     * @return the prime.
     */
    public BigInteger getP() {
        return p;
    }

    /**
     * Compares this prime finite field for equality with the
     * specified object.
     * @param obj the object to be compared.
     * @return true if <code>obj</code> is an instance
     * of ECFieldFp and the prime value match, false otherwise.
     */
    public boolean equals(Object obj) {
        if (this == obj)  return true;
        if (obj instanceof ECFieldFp) {
            return (p.equals(((ECFieldFp)obj).p));
        }
        return false;
    }

    /**
     * Returns a hash code value for this prime finite field.
     * @return a hash code value.
     */
    public int hashCode() {
        return p.hashCode();
    }
}
