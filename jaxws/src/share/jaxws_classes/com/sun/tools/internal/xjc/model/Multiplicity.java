/*
 * Copyright (c) 1997, 2012, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.tools.internal.xjc.model;

import java.math.BigInteger;



/**
 * represents a possible number of occurence.
 *
 * Usually, denoted by a pair of integers like (1,1) or (5,10).
 * A special value "unbounded" is allowed as the upper bound.
 *
 * <p>
 * For example, (0,unbounded) corresponds to the '*' occurence of DTD.
 * (0,1) corresponds to the '?' occurence of DTD.
 *
 * @author
 *    <a href="mailto:kohsuke.kawaguchi@sun.com">Kohsuke KAWAGUCHI</a>
 */
public final class Multiplicity {
    public final BigInteger min;
    public final BigInteger max;    // null is used to represent "unbounded".

    public static Multiplicity create(BigInteger min, BigInteger max ) {
        if (BigInteger.ZERO.equals(min) && max==null) return STAR;
        if (BigInteger.ONE.equals(min) && max==null) return PLUS;
        if (max!=null) {
            if(BigInteger.ZERO.equals(min) && BigInteger.ZERO.equals(max))    return ZERO;
            if(BigInteger.ZERO.equals(min) && BigInteger.ONE.equals(max))    return OPTIONAL;
            if(BigInteger.ONE.equals(min) && BigInteger.ONE.equals(max))    return ONE;
        }
        return new Multiplicity(min, max);
    }

    public static Multiplicity create(int min, Integer max ) {
        return Multiplicity.create(BigInteger.valueOf(min), BigInteger.valueOf(max.intValue()));
    }

    private Multiplicity(BigInteger min, BigInteger max) {
        this.min = min;
        this.max = max;
    }

    private Multiplicity(int min, int max) {
        this(BigInteger.valueOf(min), BigInteger.valueOf(max));
    }

    private Multiplicity(int min, Integer max) {
        this(BigInteger.valueOf(min), (max == null) ? null : BigInteger.valueOf(max));
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof Multiplicity)) return false;

        Multiplicity that = (Multiplicity) o;

        if (!this.min.equals(that.min)) return false;
        if (this.max != null ? !this.max.equals(that.max) : that.max != null) return false;

        return true;
    }

    @Override
    public int hashCode() {
        return min.add(max).intValue();
    }

    /** returns true if the multiplicity is (1,1). */
    public boolean isUnique() {
        if(max==null)    return false;
        return BigInteger.ONE.equals(min) && BigInteger.ONE.equals(max);
    }

    /** returns true if the multiplicity is (0,1) */
    public boolean isOptional() {
        if(max==null) return false;
        return BigInteger.ZERO.equals(min) && BigInteger.ONE.equals(max);
    }

    /** returns true if the multiplicity is (0,1) or (1,1). */
    public boolean isAtMostOnce() {
        if(max==null)    return false;
        return max.compareTo(BigInteger.ONE)<=0;
    }

    /** returns true if the multiplicity is (0,0). */
    public boolean isZero() {
        if(max==null)    return false;
        return BigInteger.ZERO.equals(max);
    }

    /**
     * Returns true if the multiplicity represented by this object
     * completely includes the multiplicity represented by the
     * other object. For example, we say [1,3] includes [1,2] but
     * [2,4] doesn't include [1,3].
     */
    public boolean includes( Multiplicity rhs ) {
        if (rhs.min.compareTo(min) == -1)   return false;
        if (max==null) return true;
        if (rhs.max==null) return false;
        return rhs.max.compareTo(max) <= 0;
    }

    /**
     * Returns the string representation of the 'max' property.
     * Either a number or a token "unbounded".
     */
    public String getMaxString() {
        if(max==null)       return "unbounded";
        else                return max.toString();
    }

    /** gets the string representation.
     * mainly debug purpose.
     */
    @Override
    public String toString() {
        return "("+min+','+getMaxString()+')';
    }

    /** the constant representing the (0,0) multiplicity. */
    public static final Multiplicity ZERO = new Multiplicity(0,0);

    /** the constant representing the (1,1) multiplicity. */
    public static final Multiplicity ONE = new Multiplicity(1,1);

    /** the constant representing the (0,1) multiplicity. */
    public static final Multiplicity OPTIONAL = new Multiplicity(0,1);

    /** the constant representing the (0,unbounded) multiplicity. */
    public static final Multiplicity STAR = new Multiplicity(0,null);

    /** the constant representing the (1,unbounded) multiplicity. */
    public static final Multiplicity PLUS = new Multiplicity(1,null);

// arithmetic methods
    public static Multiplicity choice( Multiplicity lhs, Multiplicity rhs ) {
        return create(
            lhs.min.min(rhs.min),
            (lhs.max==null || rhs.max==null) ? null : lhs.max.max(rhs.max) );
    }
    public static Multiplicity group( Multiplicity lhs, Multiplicity rhs ) {
        return create(
            lhs.min.add(rhs.min),
            (lhs.max==null || rhs.max==null) ? null : lhs.max.add(rhs.max) );
    }
    public static Multiplicity multiply( Multiplicity lhs, Multiplicity rhs ) {
        BigInteger min = lhs.min.multiply(rhs.min);
        BigInteger max;
        if (isZero(lhs.max) || isZero(rhs.max))
            max = BigInteger.ZERO;
        else
        if (lhs.max==null || rhs.max==null)
            max = null;
        else
            max = lhs.max.multiply(rhs.max);
        return create(min,max);
    }

    private static boolean isZero(BigInteger i) {
        return (i != null && BigInteger.ZERO.equals(i));
    }

    public static Multiplicity oneOrMore( Multiplicity c ) {
        if(c.max==null)  return c; // (x,*) => (x,*)
        if(BigInteger.ZERO.equals(c.max)) return c; // (0,0) => (0,0)
        else        return create( c.min, null );    // (x,y) => (x,*)
    }

    public Multiplicity makeOptional() {
        if (BigInteger.ZERO.equals(min)) return this;
        return create(BigInteger.ZERO, max);
    }

    public Multiplicity makeRepeated() {
        if (max==null || BigInteger.ZERO.equals(max))  return this;   // (0,0)* = (0,0)  and (n,unbounded)* = (n,unbounded)
        return create(min,null);
    }
}
