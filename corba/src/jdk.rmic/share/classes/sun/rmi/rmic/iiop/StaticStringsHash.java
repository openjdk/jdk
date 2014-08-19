/*
 * Copyright (c) 1999, 2007, Oracle and/or its affiliates. All rights reserved.
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
/*
 * Licensed Materials - Property of IBM
 * RMI-IIOP v1.0
 * Copyright IBM Corp. 1998 1999  All Rights Reserved
 *
 */

package sun.rmi.rmic.iiop;

/**
 * StaticStringsHash takes an array of constant strings and
 * uses several different hash methods to try to find the
 * 'best' one for that set. The set of methods is currently
 * fixed, but with a little work could be made extensible thru
 * subclassing.
 * <p>
 * The current set of methods is:
 * <ol>
 * <li> length() - works well when all strings are different length.</li>
 * <li> charAt(n) - works well when one offset into all strings is different.</li>
 * <li> hashCode() - works well with larger arrays.</li>
 * </ol>
 * After constructing an instance over the set of strings, the
 * <code>getKey(String)</code> method can be used to use the selected hash
 * method to produce a key.  The <code>method</code> string will contain
 * "length()", "charAt(n)", or "hashCode()", and is intended for use by
 * code generators.
 * <p>
 * The <code>keys</code> array will contain the full set of unique keys.
 * <p>
 * The <code>buckets</code> array will contain a set of arrays, one for
 * each key in the <code>keys</code>, where <code>buckets[x][y]</code>
 * is an index into the <code>strings</code> array.
 * @author      Bryan Atsatt
 */
public class StaticStringsHash {

    /** The set of strings upon which the hash info is created */
    public String[] strings = null;

    /** Unique hash keys */
    public int[] keys = null;

    /** Buckets for each key, where buckets[x][y] is an index
     * into the strings[] array. */
    public int[][] buckets = null;

    /** The method to invoke on String to produce the hash key */
    public String method = null;

    /** Get a key for the given string using the
     * selected hash method.
     * @param str the string to return a key for.
     * @return the key.
     */
    public int getKey(String str) {
        switch (keyKind) {
        case LENGTH: return str.length();
        case CHAR_AT: return str.charAt(charAt);
        case HASH_CODE: return str.hashCode();
        }
        throw new Error("Bad keyKind");
    }

    /** Constructor
     * @param strings the set of strings upon which to
     * find an optimal hash method. Must not contain
     * duplicates.
     */
    public StaticStringsHash(String[] strings) {
        this.strings = strings;
        length = strings.length;
        tempKeys = new int[length];
        bucketSizes = new int[length];
        setMinStringLength();

        // Decide on the best algorithm based on
        // which one has the smallest maximum
        // bucket depth. First, try length()...

        int currentMaxDepth = getKeys(LENGTH);
        int useCharAt = -1;
        boolean useHashCode = false;

        if (currentMaxDepth > 1) {

            // At least one bucket had more than one
            // entry, so try charAt(i).  If there
            // are a lot of strings in the array,
            // and minStringLength is large, limit
            // the search to a smaller number of
            // characters to avoid spending a lot
            // of time here that is most likely to
            // be pointless...

            int minLength = minStringLength;
            if (length > CHAR_AT_MAX_LINES &&
                length * minLength > CHAR_AT_MAX_CHARS) {
                minLength = length/CHAR_AT_MAX_CHARS;
            }

            charAt = 0;
            for (int i = 0; i < minLength; i++) {
                int charAtDepth = getKeys(CHAR_AT);
                if (charAtDepth < currentMaxDepth) {
                    currentMaxDepth = charAtDepth;
                    useCharAt = i;
                    if (currentMaxDepth == 1) {
                        break;
                    }
                }
                charAt++;
            }
            charAt = useCharAt;


            if (currentMaxDepth > 1) {

                // At least one bucket had more than one
                // entry, try hashCode().
                //
                // Since the cost of computing a full hashCode
                // (for the runtime target string) is much higher
                // than the previous methods, use it only if it is
                // substantially better. The definition of 'substantial'
                // here is not very well founded, and could be improved
                // with some further analysis ;^)

                int hashCodeDepth = getKeys(HASH_CODE);
                if (hashCodeDepth < currentMaxDepth-3) {

                    // Using the full hashCode results in at least
                    // 3 fewer entries in the worst bucket, so will
                    // therefore avoid at least 3 calls to equals()
                    // in the worst case.
                    //
                    // Note that using a number smaller than 3 could
                    // result in using a hashCode when there are only
                    // 2 strings in the array, and that would surely
                    // be a poor performance choice.

                    useHashCode = true;
                }
            }

            // Reset keys if needed...

            if (!useHashCode) {
                if (useCharAt >= 0) {

                    // Use the charAt(i) method...

                    getKeys(CHAR_AT);

                } else {

                    // Use length method...

                    getKeys(LENGTH);
                }
            }
        }

        // Now allocate and fill our real hashKeys array...

        keys = new int[bucketCount];
        System.arraycopy(tempKeys,0,keys,0,bucketCount);

        // Sort keys and bucketSizes arrays...

        boolean didSwap;
        do {
            didSwap = false;
            for (int i = 0; i < bucketCount - 1; i++) {
                if (keys[i] > keys[i+1]) {
                    int temp = keys[i];
                    keys[i] = keys[i+1];
                    keys[i+1] = temp;
                    temp = bucketSizes[i];
                    bucketSizes[i] = bucketSizes[i+1];
                    bucketSizes[i+1] = temp;
                    didSwap = true;
                }
            }
        }
        while (didSwap == true);

        // Allocate our buckets array. Fill the string
        // index slot with an unused key so we can
        // determine which are free...

        int unused = findUnusedKey();
        buckets = new int[bucketCount][];
        for (int i = 0; i < bucketCount; i++) {
            buckets[i] = new int[bucketSizes[i]];
            for (int j = 0; j < bucketSizes[i]; j++) {
                buckets[i][j] = unused;
            }
        }

        // And fill it in...

        for(int i = 0; i < strings.length; i++) {
            int key = getKey(strings[i]);
            for (int j = 0; j < bucketCount; j++) {
                if (keys[j] == key) {
                    int k = 0;
                    while (buckets[j][k] != unused) {
                        k++;
                    }
                    buckets[j][k] = i;
                    break;
                }
            }
        }
    }

    /** Print an optimized 'contains' method for the
     * argument strings
     */
    public static void main (String[] args) {
        StaticStringsHash hash = new StaticStringsHash(args);
        System.out.println();
        System.out.println("    public boolean contains(String key) {");
        System.out.println("        switch (key."+hash.method+") {");
        for (int i = 0; i < hash.buckets.length; i++) {
            System.out.println("            case "+hash.keys[i]+": ");
            for (int j = 0; j < hash.buckets[i].length; j++) {
                if (j > 0) {
                    System.out.print("                } else ");
                } else {
                    System.out.print("                ");
                }
                System.out.println("if (key.equals(\""+ hash.strings[hash.buckets[i][j]] +"\")) {");
                System.out.println("                    return true;");
            }
            System.out.println("                }");
        }
        System.out.println("        }");
        System.out.println("        return false;");
        System.out.println("    }");
    }

    private int length;
    private int[] tempKeys;
    private int[] bucketSizes;
    private int bucketCount;
    private int maxDepth;
    private int minStringLength = Integer.MAX_VALUE;
    private int keyKind;
    private int charAt;

    private static final int LENGTH = 0;
    private static final int CHAR_AT = 1;
    private static final int HASH_CODE = 2;

    /* Determines the maximum number of charAt(i)
     * tests that will be done. The search is
     * limited because if the number of characters
     * is large enough, the likelyhood of finding
     * a good hash key  based on this method is
     * low. The CHAR_AT_MAX_CHARS limit only
     * applies f there are more strings than
     * CHAR_AT_MAX_LINES.
     */
    private static final int CHAR_AT_MAX_LINES = 50;
    private static final int CHAR_AT_MAX_CHARS = 1000;

    private void resetKeys(int keyKind) {
        this.keyKind = keyKind;
        switch (keyKind) {
        case LENGTH: method = "length()"; break;
        case CHAR_AT: method = "charAt("+charAt+")"; break;
        case HASH_CODE: method = "hashCode()"; break;
        }
        maxDepth = 1;
        bucketCount = 0;
        for (int i = 0; i < length; i++) {
            tempKeys[i] = 0;
            bucketSizes[i] = 0;
        }
    }

    private void setMinStringLength() {
        for (int i = 0; i < length; i++) {
            if (strings[i].length() < minStringLength) {
                minStringLength = strings[i].length();
            }
        }
    }

    private int findUnusedKey() {
        int unused = 0;
        int keysLength = keys.length;

        // Note that we just assume that resource
        // exhaustion will occur rather than an
        // infinite loop here if the set of keys
        // is very large.

        while (true) {
            boolean match = false;
            for (int i = 0; i < keysLength; i++) {
                if (keys[i] == unused) {
                    match = true;
                    break;
                }
            }
            if (match) {
                unused--;
            } else {
                break;
            }
        }
        return unused;
    }

    private int getKeys(int methodKind) {
        resetKeys(methodKind);
        for(int i = 0; i < strings.length; i++) {
            addKey(getKey(strings[i]));
        }
        return maxDepth;
    }

    private void addKey(int key) {

        // Have we seen this one before?

        boolean addIt = true;
        for (int j = 0; j < bucketCount; j++) {
            if (tempKeys[j] == key) {
                addIt = false;
                bucketSizes[j]++;
                if (bucketSizes[j] > maxDepth) {
                    maxDepth = bucketSizes[j];
                }
                break;
            }
        }

        if (addIt) {
            tempKeys[bucketCount] = key;
            bucketSizes[bucketCount] = 1;
            bucketCount++;
        }
    }
}
