/*
 * Copyright (c) 1997, 2013, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.xml.internal.bind.v2.util;

import java.util.AbstractMap;
import java.util.Arrays;
import java.util.Collection;
import java.util.WeakHashMap;

/**
 * Computes the string edit distance.
 *
 * <p>
 * Refer to a computer science text book for the definition
 * of the "string edit distance".
 *
 * @author
 *     Kohsuke Kawaguchi (kohsuke.kawaguchi@sun.com)
 */
public class EditDistance {

    /**
     * Weak results cache to avoid additional computations.
     * Because of high complexity caching is required.
     */
    private static final WeakHashMap<AbstractMap.SimpleEntry<String,String>, Integer> CACHE = new WeakHashMap<AbstractMap.SimpleEntry<String, String>, Integer>();

    /**
     * Computes the edit distance between two strings.
     *
     * <p>
     * The complexity is O(nm) where n=a.length() and m=b.length().
     */
    public static int editDistance( String a, String b ) {
        // let's check cache
        AbstractMap.SimpleEntry<String,String> entry = new AbstractMap.SimpleEntry<String, String>(a, b); // using this class to avoid creation of my own which will handle PAIR of values
        Integer result = null;
        if (CACHE.containsKey(entry))
            result = CACHE.get(entry); // looks like we have it

        if (result == null) {
            result = new EditDistance(a, b).calc();
            CACHE.put(entry, result); // cache the result
        }
        return result;
    }

    /**
     * Finds the string in the <code>group</code> closest to
     * <code>key</code> and returns it.
     *
     * @return null if group.length==0.
     */
    public static String findNearest( String key, String[] group ) {
        return findNearest(key, Arrays.asList(group));
    }

    /**
     * Finds the string in the <code>group</code> closest to
     * <code>key</code> and returns it.
     *
     * @return null if group.length==0.
     */
    public static String findNearest( String key, Collection<String> group ) {
        int c = Integer.MAX_VALUE;
        String r = null;

        for (String s : group) {
            int ed = editDistance(key,s);
            if( c>ed ) {
                c = ed;
                r = s;
            }
        }
        return r;
    }

    /** cost vector. */
    private int[] cost;
    /** back buffer. */
    private int[] back;

    /** Two strings to be compared. */
    private final String a,b;

    private EditDistance( String a, String b ) {
        this.a=a;
        this.b=b;
        cost = new int[a.length()+1];
        back = new int[a.length()+1]; // back buffer

        for( int i=0; i<=a.length(); i++ )
            cost[i] = i;
    }

    /**
     * Swaps two buffers.
     */
    private void flip() {
        int[] t = cost;
        cost = back;
        back = t;
    }

    private int min(int a,int b,int c) {
        return Math.min(a,Math.min(b,c));
    }

    private int calc() {
        for( int j=0; j<b.length(); j++ ) {
            flip();
            cost[0] = j+1;
            for( int i=0; i<a.length(); i++ ) {
                int match = (a.charAt(i)==b.charAt(j))?0:1;
                cost[i+1] = min( back[i]+match, cost[i]+1, back[i+1]+1 );
            }
        }
        return cost[a.length()];
    }
}
