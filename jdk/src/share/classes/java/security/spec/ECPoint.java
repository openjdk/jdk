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

/**
 * This immutable class represents a point on an elliptic curve (EC)
 * in affine coordinates. Other coordinate systems can
 * extend this class to represent this point in other
 * coordinates.
 *
 * @author Valerie Peng
 *
 * @since 1.5
 */
public class ECPoint {

    private final BigInteger x;
    private final BigInteger y;

    /**
     * This defines the point at infinity.
     */
    public static final ECPoint POINT_INFINITY = new ECPoint();

    // private constructor for constructing point at infinity
    private ECPoint() {
        this.x = null;
        this.y = null;
    }

    /**
     * Creates an ECPoint from the specified affine x-coordinate
     * <code>x</code> and affine y-coordinate <code>y</code>.
     * @param x the affine x-coordinate.
     * @param y the affine y-coordinate.
     * @exception NullPointerException if <code>x</code> or
     * <code>y</code> is null.
     */
    public ECPoint(BigInteger x, BigInteger y) {
        if ((x==null) || (y==null)) {
            throw new NullPointerException("affine coordinate x or y is null");
        }
        this.x = x;
        this.y = y;
    }

    /**
     * Returns the affine x-coordinate <code>x</code>.
     * Note: POINT_INFINITY has a null affine x-coordinate.
     * @return the affine x-coordinate.
     */
    public BigInteger getAffineX() {
        return x;
    }

    /**
     * Returns the affine y-coordinate <code>y</code>.
     * Note: POINT_INFINITY has a null affine y-coordinate.
     * @return the affine y-coordinate.
     */
    public BigInteger getAffineY() {
        return y;
    }

    /**
     * Compares this elliptic curve point for equality with
     * the specified object.
     * @param obj the object to be compared.
     * @return true if <code>obj</code> is an instance of
     * ECPoint and the affine coordinates match, false otherwise.
     */
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (this == POINT_INFINITY) return false;
        if (obj instanceof ECPoint) {
            return ((x.equals(((ECPoint)obj).x)) &&
                    (y.equals(((ECPoint)obj).y)));
        }
        return false;
    }

    /**
     * Returns a hash code value for this elliptic curve point.
     * @return a hash code value.
     */
    public int hashCode() {
        if (this == POINT_INFINITY) return 0;
        return x.hashCode() << 5 + y.hashCode();
    }
}
