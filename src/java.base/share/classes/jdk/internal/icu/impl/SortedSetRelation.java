// Copyright 2016 and later: Unicode, Inc. and others.
// License & terms of use: http://www.unicode.org/copyright.html
/*
**********************************************************************
* Copyright (c) 2002-2010, International Business Machines
* Corporation and others.  All Rights Reserved.
**********************************************************************
* Author: M. Davis
* Created: December 2002 (moved from UnicodeSet)
* Since: ICU 2.4
**********************************************************************
*/
package jdk.internal.icu.impl;

import java.util.Iterator;
import java.util.SortedSet;
import java.util.TreeSet;

/**
 * Computationally efficient determination of the relationship between
 * two SortedSets.
 */
public class SortedSetRelation {

    /**
     * The relationship between two sets A and B can be determined by looking at:
     * A - B
     * A & B (intersection)
     * B - A
     * These are represented by a set of bits.
     * Bit 2 is true if A - B is not empty
     * Bit 1 is true if A & B is not empty
     * BIT 0 is true if B - A is not empty
     */
    public static final int
        A_NOT_B = 4,
        A_AND_B = 2,
        B_NOT_A = 1;
    
    /**
     * There are 8 combinations of the relationship bits. These correspond to
     * the filters (combinations of allowed bits) in hasRelation. They also
     * correspond to the modification functions, listed in comments.
     */
    public static final int 
       ANY =            A_NOT_B |   A_AND_B |   B_NOT_A,    // union,           addAll
       CONTAINS =       A_NOT_B |   A_AND_B,                // A                (unnecessary)
       DISJOINT =       A_NOT_B |               B_NOT_A,    // A xor B,         missing Java function
       ISCONTAINED =                A_AND_B |   B_NOT_A,    // B                (unnecessary)
       NO_B =           A_NOT_B,                            // A setDiff B,     removeAll
       EQUALS =                     A_AND_B,                // A intersect B,   retainAll
       NO_A =                                   B_NOT_A,    // B setDiff A,     removeAll
       NONE =           0,                                  // null             (unnecessary)
       
       ADDALL = ANY,                // union,           addAll
       A = CONTAINS,                // A                (unnecessary)
       COMPLEMENTALL = DISJOINT,    // A xor B,         missing Java function
       B = ISCONTAINED,             // B                (unnecessary)
       REMOVEALL = NO_B,            // A setDiff B,     removeAll
       RETAINALL = EQUALS,          // A intersect B,   retainAll
       B_REMOVEALL = NO_A;          // B setDiff A,     removeAll
       
  
    /**
     * Utility that could be on SortedSet. Faster implementation than
     * what is in Java for doing contains, equals, etc.
     * @param a first set
     * @param allow filter, using ANY, CONTAINS, etc.
     * @param b second set
     * @return whether the filter relationship is true or not.
     */    
    public static <T extends Object & Comparable<? super T>> boolean hasRelation(SortedSet<T> a, int allow, SortedSet<T> b) {
        if (allow < NONE || allow > ANY) {
            throw new IllegalArgumentException("Relation " + allow + " out of range");
        }
        
        // extract filter conditions
        // these are the ALLOWED conditions Set
        
        boolean anb = (allow & A_NOT_B) != 0;
        boolean ab = (allow & A_AND_B) != 0;
        boolean bna = (allow & B_NOT_A) != 0;
        
        // quick check on sizes
        switch(allow) {
            case CONTAINS: if (a.size() < b.size()) return false; break;
            case ISCONTAINED: if (a.size() > b.size()) return false; break;
            case EQUALS: if (a.size() != b.size()) return false; break;
        }
        
        // check for null sets
        if (a.size() == 0) {
            if (b.size() == 0) return true;
            return bna;
        } else if (b.size() == 0) {
            return anb;
        }
        
        // pick up first strings, and start comparing
        Iterator<? extends T> ait = a.iterator();
        Iterator<? extends T> bit = b.iterator();
        
        T aa = ait.next();
        T bb = bit.next();
        
        while (true) {
            int comp = aa.compareTo(bb);
            if (comp == 0) {
                if (!ab) return false;
                if (!ait.hasNext()) {
                    if (!bit.hasNext()) return true;
                    return bna;
                } else if (!bit.hasNext()) {
                    return anb;
                }
                aa = ait.next();
                bb = bit.next();
            } else if (comp < 0) {
                if (!anb) return false;
                if (!ait.hasNext()) {
                    return bna;
                }
                aa = ait.next(); 
            } else  {
                if (!bna) return false;
                if (!bit.hasNext()) {
                    return anb;
                }
                bb = bit.next();
            }
        }
    }
    
    /**
     * Utility that could be on SortedSet. Allows faster implementation than
     * what is in Java for doing addAll, removeAll, retainAll, (complementAll).
     * @param a first set
     * @param relation the relation filter, using ANY, CONTAINS, etc.
     * @param b second set
     * @return the new set
     */    
    public static <T extends Object & Comparable<? super T>> SortedSet<? extends T> doOperation(SortedSet<T> a, int relation, SortedSet<T> b) {
        // TODO: optimize this as above
        TreeSet<? extends T> temp;
        switch (relation) {
            case ADDALL:
                a.addAll(b); 
                return a;
            case A:
                return a; // no action
            case B:
                a.clear(); 
                a.addAll(b); 
                return a;
            case REMOVEALL: 
                a.removeAll(b);
                return a;
            case RETAINALL: 
                a.retainAll(b);
                return a;
            // the following is the only case not really supported by Java
            // although all could be optimized
            case COMPLEMENTALL:
                temp = new TreeSet<T>(b);
                temp.removeAll(a);
                a.removeAll(b);
                a.addAll(temp);
                return a;
            case B_REMOVEALL:
                temp = new TreeSet<T>(b);
                temp.removeAll(a);
                a.clear();
                a.addAll(temp);
                return a;
            case NONE:
                a.clear();
                return a;
            default: 
                throw new IllegalArgumentException("Relation " + relation + " out of range");
        }
    }    
}
