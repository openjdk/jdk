/*
 * Copyright 2005-2006 Sun Microsystems, Inc.  All Rights Reserved.
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
package com.sun.tools.internal.xjc.model;



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
    public final int min;
    public final Integer max;    // null is used to represent "unbounded".

    public static Multiplicity create( int min, Integer max ) {
        if(min==0 && max==null) return STAR;
        if(min==1 && max==null) return PLUS;
        if(max!=null) {
            if(min==0 && max==0)    return ZERO;
            if(min==0 && max==1)    return OPTIONAL;
            if(min==1 && max==1)    return ONE;
        }
        return new Multiplicity(min,max);
    }

    private Multiplicity( int min, Integer max ) {
        this.min = min; this.max = max;
    }

    public boolean equals(Object o) {
        if (!(o instanceof Multiplicity)) return false;

        Multiplicity that = (Multiplicity) o;

        if (this.min != that.min) return false;
        if (this.max != null ? !this.max.equals(that.max) : that.max != null) return false;

        return true;
    }

    public int hashCode() {
        int result;
        result = min;
        result = 29 * result + (max != null ? max.hashCode() : 0);
        return result;
    }

    /** returns true if the multiplicity is (1,1). */
    public boolean isUnique() {
        if(max==null)    return false;
        return min==1 && max==1;
    }

    /** returns true if the multiplicity is (0,1) */
    public boolean isOptional() {
        if(max==null) return false;
        return min==0 && max==1;
    }

    /** returns true if the multiplicity is (0,1) or (1,1). */
    public boolean isAtMostOnce() {
        if(max==null)    return false;
        return max<=1;
    }

    /** returns true if the multiplicity is (0,0). */
    public boolean isZero() {
        if(max==null)    return false;
        return max==0;
    }

    /**
     * Returns true if the multiplicity represented by this object
     * completely includes the multiplicity represented by the
     * other object. For example, we say [1,3] includes [1,2] but
     * [2,4] doesn't include [1,3].
     */
    public boolean includes( Multiplicity rhs ) {
        if( rhs.min<min )   return false;
        if( max==null )     return true;
        if( rhs.max==null ) return false;
        return rhs.max <= max;
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
//============================
    public static Multiplicity choice( Multiplicity lhs, Multiplicity rhs ) {
        return create(
            Math.min(lhs.min,rhs.min),
            (lhs.max==null||rhs.max==null)?
                null:
                (Integer)Math.max(lhs.max, rhs.max) );
    }
    public static Multiplicity group( Multiplicity lhs, Multiplicity rhs ) {
        return create( lhs.min+rhs.min,
            (lhs.max==null||rhs.max==null)?
                null:
                (Integer)(lhs.max + rhs.max) );
    }
    public static Multiplicity multiply( Multiplicity lhs, Multiplicity rhs ) {
        int min = lhs.min*rhs.min;
        Integer max;
        if(isZero(lhs.max) || isZero(rhs.max))
            max = 0;
        else
        if(lhs.max==null || rhs.max==null)
            max = null;
        else
            max = lhs.max*rhs.max;
        return create(min,max);
    }

    private static boolean isZero(Integer i) {
        return i!=null && i==0;
    }

    public static Multiplicity oneOrMore( Multiplicity c ) {
        if(c.max==null)  return c; // (x,*) => (x,*)
        if(c.max==0 )    return c; // (0,0) => (0,0)
        else        return create( c.min, null );    // (x,y) => (x,*)
    }

    public Multiplicity makeOptional() {
        if(min==0)    return this;
        return create(0,max);
    }

    public Multiplicity makeRepeated() {
        if(max==null || max==0)  return this;   // (0,0)* = (0,0)  and (n,unbounded)* = (n,unbounded)
        return create(min,null);
    }
}
