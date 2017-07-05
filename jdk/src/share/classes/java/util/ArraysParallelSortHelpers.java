/*
 * Copyright (c) 2012, Oracle and/or its affiliates. All rights reserved.
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
package java.util;

import java.util.concurrent.RecursiveAction;

/**
 * Helper utilities for the parallel sort methods in Arrays.parallelSort.
 *
 * For each primitive type, plus Object, we define a static class to
 * contain the Sorter and Merger implementations for that type:
 *
 * Sorter classes based mainly on CilkSort
 * <A href="http://supertech.lcs.mit.edu/cilk/"> Cilk</A>:
 * Basic algorithm:
 * if array size is small, just use a sequential quicksort (via Arrays.sort)
 *         Otherwise:
 *         1. Break array in half.
 *         2. For each half,
 *             a. break the half in half (i.e., quarters),
 *             b. sort the quarters
 *             c. merge them together
 *         3. merge together the two halves.
 *
 * One reason for splitting in quarters is that this guarantees
 * that the final sort is in the main array, not the workspace
 * array.  (workspace and main swap roles on each subsort step.)
 * Leaf-level sorts use a Sequential quicksort, that in turn uses
 * insertion sort if under threshold.  Otherwise it uses median of
 * three to pick pivot, and loops rather than recurses along left
 * path.
 *
 *
 * Merger classes perform merging for Sorter. If big enough, splits Left
 * partition in half; finds the greatest point in Right partition
 * less than the beginning of the second half of Left via binary
 * search; and then, in parallel, merges left half of Left with
 * elements of Right up to split point, and merges right half of
 * Left with elements of R past split point. At leaf, it just
 * sequentially merges. This is all messy to code; sadly we need
 * distinct versions for each type.
 *
 */
/*package*/ class ArraysParallelSortHelpers {

    // RFE: we should only need a working array as large as the subarray
    //      to be sorted, but the logic assumes that indices in the two
    //      arrays always line-up

    /** byte support class */
    static final class FJByte {
        static final class Sorter extends RecursiveAction {
            static final long serialVersionUID = 749471161188027634L;
            final byte[] a;     // array to be sorted.
            final byte[] w;     // workspace for merge
            final int origin;   // origin of the part of array we deal with
            final int n;        // Number of elements in (sub)arrays.
            final int gran;     // split control

            Sorter(byte[] a, byte[] w, int origin, int n, int gran) {
                this.a = a;
                this.w = w;
                this.origin = origin;
                this.n = n;
                this.gran = gran;
            }

            public void compute() {
                final int l = origin;
                final int g = gran;
                final int n = this.n;
                final byte[] a = this.a;
                final byte[] w = this.w;
                if (n > g) {
                    int h = n >>> 1; // half
                    int q = n >>> 2; // lower quarter index
                    int u = h + q;   // upper quarter
                    FJSubSorter ls = new FJSubSorter(new Sorter(a, w, l, q,  g),
                                                     new Sorter(a, w, l+q, h-q, g),
                                                     new Merger(a, w, l,   q,
                                                                l+q, h-q, l, g, null));
                    FJSubSorter rs = new FJSubSorter(new Sorter(a, w, l+h, q,   g),
                                                     new Sorter(a, w, l+u, n-u, g),
                                                     new Merger(a, w, l+h, q,
                                                                l+u, n-u, l+h, g, null));
                    rs.fork();
                    ls.compute();
                    if (rs.tryUnfork()) rs.compute(); else rs.join();
                    new Merger(w, a, l, h,
                               l+h, n-h, l, g, null).compute();
                } else {
                    DualPivotQuicksort.sort(a, l, l+n-1);   //skip rangeCheck
                }
            }
        }

        static final class Merger extends RecursiveAction {
            static final long serialVersionUID = -9090258248781844470L;
            final byte[] a;
            final byte[] w;
            final int lo;
            final int ln;
            final int ro;
            final int rn;
            final int wo;
            final int gran;
            final Merger next;

            Merger(byte[] a, byte[] w, int lo, int ln, int ro, int rn, int wo,
                   int gran, Merger next) {
                this.a = a;
                this.w = w;
                this.lo = lo;
                this.ln = ln;
                this.ro = ro;
                this.rn = rn;
                this.wo = wo;
                this.gran = gran;
                this.next = next;
            }

            public void compute() {
                final byte[] a = this.a;
                final byte[] w = this.w;
                Merger rights = null;
                int nleft = ln;
                int nright = rn;
                while (nleft > gran) {
                    int lh = nleft >>> 1;
                    int splitIndex = lo + lh;
                    byte split = a[splitIndex];
                    int rl = 0;
                    int rh = nright;
                    while (rl < rh) {
                        int mid = (rl + rh) >>> 1;
                        if (split <= a[ro + mid])
                            rh = mid;
                        else
                            rl = mid + 1;
                    }
                    (rights = new Merger(a, w, splitIndex, nleft-lh, ro+rh,
                                         nright-rh, wo+lh+rh, gran, rights)).fork();
                    nleft = lh;
                    nright = rh;
                }

                int l = lo;
                int lFence = l + nleft;
                int r = ro;
                int rFence = r + nright;
                int k = wo;
                while (l < lFence && r < rFence) {
                    byte al = a[l];
                    byte ar = a[r];
                    byte t;
                    if (al <= ar) {++l; t=al;} else {++r; t = ar;}
                    w[k++] = t;
                }
                while (l < lFence)
                    w[k++] = a[l++];
                while (r < rFence)
                    w[k++] = a[r++];
                while (rights != null) {
                    if (rights.tryUnfork())
                        rights.compute();
                    else
                        rights.join();
                    rights = rights.next;
                }
            }
        }
    } // FJByte

    /** char support class */
    static final class FJChar {
        static final class Sorter extends RecursiveAction {
            static final long serialVersionUID = 8723376019074596641L;
            final char[] a;     // array to be sorted.
            final char[] w;     // workspace for merge
            final int origin;   // origin of the part of array we deal with
            final int n;        // Number of elements in (sub)arrays.
            final int gran;     // split control

            Sorter(char[] a, char[] w, int origin, int n, int gran) {
                this.a = a;
                this.w = w;
                this.origin = origin;
                this.n = n;
                this.gran = gran;
            }

            public void compute() {
                final int l = origin;
                final int g = gran;
                final int n = this.n;
                final char[] a = this.a;
                final char[] w = this.w;
                if (n > g) {
                    int h = n >>> 1; // half
                    int q = n >>> 2; // lower quarter index
                    int u = h + q;   // upper quarter
                    FJSubSorter ls = new FJSubSorter(new Sorter(a, w, l, q,   g),
                                                     new Sorter(a, w, l+q, h-q, g),
                                                     new Merger(a, w, l,   q,
                                                                l+q, h-q, l, g, null));
                    FJSubSorter rs = new FJSubSorter(new Sorter(a, w, l + h, q,   g),
                                                     new Sorter(a, w, l+u, n-u, g),
                                                     new Merger(a, w, l+h, q,
                                                                l+u, n-u, l+h, g, null));
                    rs.fork();
                    ls.compute();
                    if (rs.tryUnfork()) rs.compute(); else rs.join();
                    new Merger(w, a, l, h, l + h, n - h, l, g, null).compute();
                } else {
                    DualPivotQuicksort.sort(a, l, l+n-1);   // skip rangeCheck
                }
            }
        }

        static final class Merger extends RecursiveAction {
            static final long serialVersionUID = -1383975444621698926L;
            final char[] a;
            final char[] w;
            final int lo;
            final int ln;
            final int ro;
            final int rn;
            final int wo;
            final int gran;
            final Merger next;

            Merger(char[] a, char[] w, int lo, int ln, int ro, int rn, int wo,
                   int gran, Merger next) {
                this.a = a;
                this.w = w;
                this.lo = lo;
                this.ln = ln;
                this.ro = ro;
                this.rn = rn;
                this.wo = wo;
                this.gran = gran;
                this.next = next;
            }

            public void compute() {
                final char[] a = this.a;
                final char[] w = this.w;
                Merger rights = null;
                int nleft = ln;
                int nright = rn;
                while (nleft > gran) {
                    int lh = nleft >>> 1;
                    int splitIndex = lo + lh;
                    char split = a[splitIndex];
                    int rl = 0;
                    int rh = nright;
                    while (rl < rh) {
                        int mid = (rl + rh) >>> 1;
                        if (split <= a[ro + mid])
                            rh = mid;
                        else
                            rl = mid + 1;
                    }
                    (rights = new Merger(a, w, splitIndex, nleft-lh, ro+rh,
                                         nright-rh, wo+lh+rh, gran, rights)).fork();
                    nleft = lh;
                    nright = rh;
                }

                int l = lo;
                int lFence = l + nleft;
                int r = ro;
                int rFence = r + nright;
                int k = wo;
                while (l < lFence && r < rFence) {
                    char al = a[l];
                    char ar = a[r];
                    char t;
                    if (al <= ar) {++l; t=al;} else {++r; t = ar;}
                    w[k++] = t;
                }
                while (l < lFence)
                    w[k++] = a[l++];
                while (r < rFence)
                    w[k++] = a[r++];
                while (rights != null) {
                    if (rights.tryUnfork())
                        rights.compute();
                    else
                        rights.join();
                    rights = rights.next;
                }
            }
        }
    } // FJChar

    /** short support class */
    static final class FJShort {
        static final class Sorter extends RecursiveAction {
            static final long serialVersionUID = -7886754793730583084L;
            final short[] a;    // array to be sorted.
            final short[] w;    // workspace for merge
            final int origin;   // origin of the part of array we deal with
            final int n;        // Number of elements in (sub)arrays.
            final int gran;     // split control

            Sorter(short[] a, short[] w, int origin, int n, int gran) {
                this.a = a;
                this.w = w;
                this.origin = origin;
                this.n = n;
                this.gran = gran;
            }

            public void compute() {
                final int l = origin;
                final int g = gran;
                final int n = this.n;
                final short[] a = this.a;
                final short[] w = this.w;
                if (n > g) {
                    int h = n >>> 1; // half
                    int q = n >>> 2; // lower quarter index
                    int u = h + q;   // upper quarter
                    FJSubSorter ls = new FJSubSorter(new Sorter(a, w, l, q,   g),
                                                     new Sorter(a, w, l+q, h-q, g),
                                                     new Merger(a, w, l,   q,
                                                                l+q, h-q, l, g, null));
                    FJSubSorter rs = new FJSubSorter(new Sorter(a, w, l + h, q,   g),
                                                     new Sorter(a, w, l+u, n-u, g),
                                                     new Merger(a, w, l+h, q,
                                                                l+u, n-u, l+h, g, null));
                    rs.fork();
                    ls.compute();
                    if (rs.tryUnfork()) rs.compute(); else rs.join();
                    new Merger(w, a, l, h, l + h, n - h, l, g, null).compute();
                } else {
                    DualPivotQuicksort.sort(a, l, l+n-1);   // skip rangeCheck
                }
            }
        }

        static final class Merger extends RecursiveAction {
            static final long serialVersionUID = 3895749408536700048L;
            final short[] a;
            final short[] w;
            final int lo;
            final int ln;
            final int ro;
            final int rn;
            final int wo;
            final int gran;
            final Merger next;

            Merger(short[] a, short[] w, int lo, int ln, int ro, int rn, int wo,
                   int gran, Merger next) {
                this.a = a;
                this.w = w;
                this.lo = lo;
                this.ln = ln;
                this.ro = ro;
                this.rn = rn;
                this.wo = wo;
                this.gran = gran;
                this.next = next;
            }

            public void compute() {
                final short[] a = this.a;
                final short[] w = this.w;
                Merger rights = null;
                int nleft = ln;
                int nright = rn;
                while (nleft > gran) {
                    int lh = nleft >>> 1;
                    int splitIndex = lo + lh;
                    short split = a[splitIndex];
                    int rl = 0;
                    int rh = nright;
                    while (rl < rh) {
                        int mid = (rl + rh) >>> 1;
                        if (split <= a[ro + mid])
                            rh = mid;
                        else
                            rl = mid + 1;
                    }
                    (rights = new Merger(a, w, splitIndex, nleft-lh, ro+rh,
                                         nright-rh, wo+lh+rh, gran, rights)).fork();
                    nleft = lh;
                    nright = rh;
                }

                int l = lo;
                int lFence = l + nleft;
                int r = ro;
                int rFence = r + nright;
                int k = wo;
                while (l < lFence && r < rFence) {
                    short al = a[l];
                    short ar = a[r];
                    short t;
                    if (al <= ar) {++l; t=al;} else {++r; t = ar;}
                    w[k++] = t;
                }
                while (l < lFence)
                    w[k++] = a[l++];
                while (r < rFence)
                    w[k++] = a[r++];
                while (rights != null) {
                    if (rights.tryUnfork())
                        rights.compute();
                    else
                        rights.join();
                    rights = rights.next;
                }
            }
        }
    } // FJShort

    /** int support class */
    static final class FJInt {
        static final class Sorter extends RecursiveAction {
            static final long serialVersionUID = 4263311808957292729L;
            final int[] a;     // array to be sorted.
            final int[] w;     // workspace for merge
            final int origin;  // origin of the part of array we deal with
            final int n;       // Number of elements in (sub)arrays.
            final int gran;    // split control

            Sorter(int[] a, int[] w, int origin, int n, int gran) {
                this.a = a;
                this.w = w;
                this.origin = origin;
                this.n = n;
                this.gran = gran;
            }

            public void compute() {
                final int l = origin;
                final int g = gran;
                final int n = this.n;
                final int[] a = this.a;
                final int[] w = this.w;
                if (n > g) {
                    int h = n >>> 1; // half
                    int q = n >>> 2; // lower quarter index
                    int u = h + q;   // upper quarter
                    FJSubSorter ls = new FJSubSorter(new Sorter(a, w, l, q,   g),
                                                     new Sorter(a, w, l+q, h-q, g),
                                                     new Merger(a, w, l,   q,
                                                                l+q, h-q, l, g, null));
                    FJSubSorter rs = new FJSubSorter(new Sorter(a, w, l + h, q,   g),
                                                     new Sorter(a, w, l+u, n-u, g),
                                                     new Merger(a, w, l+h, q,
                                                                l+u, n-u, l+h, g, null));
                    rs.fork();
                    ls.compute();
                    if (rs.tryUnfork()) rs.compute(); else rs.join();
                    new Merger(w, a, l, h, l + h, n - h, l, g, null).compute();
                } else {
                    DualPivotQuicksort.sort(a, l, l+n-1);   // skip rangeCheck
                }
            }
        }

        static final class Merger extends RecursiveAction {
            static final long serialVersionUID = -8727507284219982792L;
            final int[] a;
            final int[] w;
            final int lo;
            final int ln;
            final int ro;
            final int rn;
            final int wo;
            final int gran;
            final Merger next;

            Merger(int[] a, int[] w, int lo, int ln, int ro, int rn, int wo,
                   int gran, Merger next) {
                this.a = a;
                this.w = w;
                this.lo = lo;
                this.ln = ln;
                this.ro = ro;
                this.rn = rn;
                this.wo = wo;
                this.gran = gran;
                this.next = next;
            }

            public void compute() {
                final int[] a = this.a;
                final int[] w = this.w;
                Merger rights = null;
                int nleft = ln;
                int nright = rn;
                while (nleft > gran) {
                    int lh = nleft >>> 1;
                    int splitIndex = lo + lh;
                    int split = a[splitIndex];
                    int rl = 0;
                    int rh = nright;
                    while (rl < rh) {
                        int mid = (rl + rh) >>> 1;
                        if (split <= a[ro + mid])
                            rh = mid;
                        else
                            rl = mid + 1;
                    }
                    (rights = new Merger(a, w, splitIndex, nleft-lh, ro+rh,
                                         nright-rh, wo+lh+rh, gran, rights)).fork();
                    nleft = lh;
                    nright = rh;
                }

                int l = lo;
                int lFence = l + nleft;
                int r = ro;
                int rFence = r + nright;
                int k = wo;
                while (l < lFence && r < rFence) {
                    int al = a[l];
                    int ar = a[r];
                    int t;
                    if (al <= ar) {++l; t=al;} else {++r; t = ar;}
                    w[k++] = t;
                }
                while (l < lFence)
                    w[k++] = a[l++];
                while (r < rFence)
                    w[k++] = a[r++];
                while (rights != null) {
                    if (rights.tryUnfork())
                        rights.compute();
                    else
                        rights.join();
                    rights = rights.next;
                }
            }
        }
    } // FJInt

    /** long support class */
    static final class FJLong {
        static final class Sorter extends RecursiveAction {
            static final long serialVersionUID = 6553695007444392455L;
            final long[] a;     // array to be sorted.
            final long[] w;     // workspace for merge
            final int origin;   // origin of the part of array we deal with
            final int n;        // Number of elements in (sub)arrays.
            final int gran;     // split control

            Sorter(long[] a, long[] w, int origin, int n, int gran) {
                this.a = a;
                this.w = w;
                this.origin = origin;
                this.n = n;
                this.gran = gran;
            }

            public void compute() {
                final int l = origin;
                final int g = gran;
                final int n = this.n;
                final long[] a = this.a;
                final long[] w = this.w;
                if (n > g) {
                    int h = n >>> 1; // half
                    int q = n >>> 2; // lower quarter index
                    int u = h + q;   // upper quarter
                    FJSubSorter ls = new FJSubSorter(new Sorter(a, w, l, q,   g),
                                                     new Sorter(a, w, l+q, h-q, g),
                                                     new Merger(a, w, l,   q,
                                                                l+q, h-q, l, g, null));
                    FJSubSorter rs = new FJSubSorter(new Sorter(a, w, l + h, q,   g),
                                                     new Sorter(a, w, l+u, n-u, g),
                                                     new Merger(a, w, l+h, q,
                                                                l+u, n-u, l+h, g, null));
                    rs.fork();
                    ls.compute();
                    if (rs.tryUnfork()) rs.compute(); else rs.join();
                    new Merger(w, a, l, h, l + h, n - h, l, g, null).compute();
                } else {
                    DualPivotQuicksort.sort(a, l, l+n-1);   // skip rangeCheck
                }
            }
        }

        static final class Merger extends RecursiveAction {
            static final long serialVersionUID = 8843567516333283861L;
            final long[] a;
            final long[] w;
            final int lo;
            final int ln;
            final int ro;
            final int rn;
            final int wo;
            final int gran;
            final Merger next;

            Merger(long[] a, long[] w, int lo, int ln, int ro, int rn, int wo,
                   int gran, Merger next) {
                this.a = a;
                this.w = w;
                this.lo = lo;
                this.ln = ln;
                this.ro = ro;
                this.rn = rn;
                this.wo = wo;
                this.gran = gran;
                this.next = next;
            }

            public void compute() {
                final long[] a = this.a;
                final long[] w = this.w;
                Merger rights = null;
                int nleft = ln;
                int nright = rn;
                while (nleft > gran) {
                    int lh = nleft >>> 1;
                    int splitIndex = lo + lh;
                    long split = a[splitIndex];
                    int rl = 0;
                    int rh = nright;
                    while (rl < rh) {
                        int mid = (rl + rh) >>> 1;
                        if (split <= a[ro + mid])
                            rh = mid;
                        else
                            rl = mid + 1;
                    }
                    (rights = new Merger(a, w, splitIndex, nleft-lh, ro+rh,
                      nright-rh, wo+lh+rh, gran, rights)).fork();
                    nleft = lh;
                    nright = rh;
                }

                int l = lo;
                int lFence = l + nleft;
                int r = ro;
                int rFence = r + nright;
                int k = wo;
                while (l < lFence && r < rFence) {
                    long al = a[l];
                    long ar = a[r];
                    long t;
                    if (al <= ar) {++l; t=al;} else {++r; t = ar;}
                    w[k++] = t;
                }
                while (l < lFence)
                    w[k++] = a[l++];
                while (r < rFence)
                    w[k++] = a[r++];
                while (rights != null) {
                    if (rights.tryUnfork())
                        rights.compute();
                    else
                        rights.join();
                    rights = rights.next;
                }
            }
        }
    } // FJLong

    /** float support class */
    static final class FJFloat {
        static final class Sorter extends RecursiveAction {
            static final long serialVersionUID = 1602600178202763377L;
            final float[] a;    // array to be sorted.
            final float[] w;    // workspace for merge
            final int origin;   // origin of the part of array we deal with
            final int n;        // Number of elements in (sub)arrays.
            final int gran;     // split control

            Sorter(float[] a, float[] w, int origin, int n, int gran) {
                this.a = a;
                this.w = w;
                this.origin = origin;
                this.n = n;
                this.gran = gran;
            }

            public void compute() {
                final int l = origin;
                final int g = gran;
                final int n = this.n;
                final float[] a = this.a;
                final float[] w = this.w;
                if (n > g) {
                    int h = n >>> 1; // half
                    int q = n >>> 2; // lower quarter index
                    int u = h + q;   // upper quarter
                    FJSubSorter ls = new FJSubSorter(new Sorter(a, w, l, q,   g),
                                                     new Sorter(a, w, l+q, h-q, g),
                                                     new Merger(a, w, l,   q,
                                                                l+q, h-q, l, g, null));
                    FJSubSorter rs = new FJSubSorter(new Sorter(a, w, l + h, q,   g),
                                                     new Sorter(a, w, l+u, n-u, g),
                                                     new Merger(a, w, l+h, q,
                                                                l+u, n-u, l+h, g, null));
                    rs.fork();
                    ls.compute();
                    if (rs.tryUnfork()) rs.compute(); else rs.join();
                    new Merger(w, a, l, h, l + h, n - h, l, g, null).compute();
                } else {
                    DualPivotQuicksort.sort(a, l, l+n-1);   // skip rangeCheck
                }
            }
        }

        static final class Merger extends RecursiveAction {
            static final long serialVersionUID = 1518176433845397426L;
            final float[] a;
            final float[] w;
            final int lo;
            final int ln;
            final int ro;
            final int rn;
            final int wo;
            final int gran;
            final Merger next;

            Merger(float[] a, float[] w, int lo, int ln, int ro, int rn, int wo,
                   int gran, Merger next) {
                this.a = a;
                this.w = w;
                this.lo = lo;
                this.ln = ln;
                this.ro = ro;
                this.rn = rn;
                this.wo = wo;
                this.gran = gran;
                this.next = next;
            }

            public void compute() {
                final float[] a = this.a;
                final float[] w = this.w;
                Merger rights = null;
                int nleft = ln;
                int nright = rn;
                while (nleft > gran) {
                    int lh = nleft >>> 1;
                    int splitIndex = lo + lh;
                    float split = a[splitIndex];
                    int rl = 0;
                    int rh = nright;
                    while (rl < rh) {
                        int mid = (rl + rh) >>> 1;
                        if (Float.compare(split, a[ro+mid]) <= 0)
                            rh = mid;
                        else
                            rl = mid + 1;
                    }
                    (rights = new Merger(a, w, splitIndex, nleft-lh, ro+rh,
                                         nright-rh, wo+lh+rh, gran, rights)).fork();
                    nleft = lh;
                    nright = rh;
                }

                int l = lo;
                int lFence = l + nleft;
                int r = ro;
                int rFence = r + nright;
                int k = wo;
                while (l < lFence && r < rFence) {
                    float al = a[l];
                    float ar = a[r];
                    float t;
                    if (Float.compare(al, ar) <= 0) {
                        ++l;
                        t = al;
                    } else {
                        ++r;
                        t = ar;
                    }
                    w[k++] = t;
                }
                while (l < lFence)
                    w[k++] = a[l++];
                while (r < rFence)
                    w[k++] = a[r++];
                while (rights != null) {
                    if (rights.tryUnfork())
                        rights.compute();
                    else
                        rights.join();
                    rights = rights.next;
                }
            }
        }
    } // FJFloat

    /** double support class */
    static final class FJDouble {
        static final class Sorter extends RecursiveAction {
            static final long serialVersionUID = 2446542900576103244L;
            final double[] a;    // array to be sorted.
            final double[] w;    // workspace for merge
            final int origin;    // origin of the part of array we deal with
            final int n;         // Number of elements in (sub)arrays.
            final int gran;      // split control

            Sorter(double[] a, double[] w, int origin, int n, int gran) {
                this.a = a;
                this.w = w;
                this.origin = origin;
                this.n = n;
                this.gran = gran;
            }

            public void compute() {
                final int l = origin;
                final int g = gran;
                final int n = this.n;
                final double[] a = this.a;
                final double[] w = this.w;
                if (n > g) {
                    int h = n >>> 1; // half
                    int q = n >>> 2; // lower quarter index
                    int u = h + q;   // upper quarter
                    FJSubSorter ls = new FJSubSorter(new Sorter(a, w, l, q,   g),
                                                     new Sorter(a, w, l+q, h-q, g),
                                                     new Merger(a, w, l,   q,
                                                                l+q, h-q, l, g, null));
                    FJSubSorter rs = new FJSubSorter(new Sorter(a, w, l + h, q,   g),
                                                     new Sorter(a, w, l+u, n-u, g),
                                                     new Merger(a, w, l+h, q,
                                                                l+u, n-u, l+h, g, null));
                    rs.fork();
                    ls.compute();
                    if (rs.tryUnfork()) rs.compute(); else rs.join();
                    new Merger(w, a, l, h, l + h, n - h, l, g, null).compute();
                } else {
                    DualPivotQuicksort.sort(a, l, l+n-1);   // skip rangeCheck
                }
            }
        }

        static final class Merger extends RecursiveAction {
            static final long serialVersionUID = 8076242187166127592L;
            final double[] a;
            final double[] w;
            final int lo;
            final int ln;
            final int ro;
            final int rn;
            final int wo;
            final int gran;
            final Merger next;

            Merger(double[] a, double[] w, int lo, int ln, int ro, int rn, int wo,
                   int gran, Merger next) {
                this.a = a;
                this.w = w;
                this.lo = lo;
                this.ln = ln;
                this.ro = ro;
                this.rn = rn;
                this.wo = wo;
                this.gran = gran;
                this.next = next;
            }

            public void compute() {
                final double[] a = this.a;
                final double[] w = this.w;
                Merger rights = null;
                int nleft = ln;
                int nright = rn;
                while (nleft > gran) {
                    int lh = nleft >>> 1;
                    int splitIndex = lo + lh;
                    double split = a[splitIndex];
                    int rl = 0;
                    int rh = nright;
                    while (rl < rh) {
                        int mid = (rl + rh) >>> 1;
                        if (Double.compare(split, a[ro+mid]) <= 0)
                            rh = mid;
                        else
                            rl = mid + 1;
                    }
                    (rights = new Merger(a, w, splitIndex, nleft-lh, ro+rh,
                                         nright-rh, wo+lh+rh, gran, rights)).fork();
                    nleft = lh;
                    nright = rh;
                }

                int l = lo;
                int lFence = l + nleft;
                int r = ro;
                int rFence = r + nright;
                int k = wo;
                while (l < lFence && r < rFence) {
                    double al = a[l];
                    double ar = a[r];
                    double t;
                    if (Double.compare(al, ar) <= 0) {
                        ++l;
                        t = al;
                    } else {
                        ++r;
                        t = ar;
                    }
                    w[k++] = t;
                }
                while (l < lFence)
                    w[k++] = a[l++];
                while (r < rFence)
                    w[k++] = a[r++];
                while (rights != null) {
                    if (rights.tryUnfork())
                        rights.compute();
                    else
                        rights.join();
                    rights = rights.next;
                }
            }
        }
    } // FJDouble

    /** Comparable support class */
    static final class FJComparable {
        static final class Sorter<T extends Comparable<? super T>> extends RecursiveAction {
            static final long serialVersionUID = -1024003289463302522L;
            final T[] a;
            final T[] w;
            final int origin;
            final int n;
            final int gran;

            Sorter(T[] a, T[] w, int origin, int n, int gran) {
                this.a = a;
                this.w = w;
                this.origin = origin;
                this.n = n;
                this.gran = gran;
            }

            public void compute() {
                final int l = origin;
                final int g = gran;
                final int n = this.n;
                final T[] a = this.a;
                final T[] w = this.w;
                if (n > g) {
                    int h = n >>> 1;
                    int q = n >>> 2;
                    int u = h + q;
                    FJSubSorter ls = new FJSubSorter(new Sorter<>(a, w, l, q,   g),
                                                     new Sorter<>(a, w, l+q, h-q, g),
                                                     new Merger<>(a, w, l,   q,
                                                                  l+q, h-q, l, g, null));
                    FJSubSorter rs = new FJSubSorter(new Sorter<>(a, w, l+h, q,   g),
                                                     new Sorter<>(a, w, l+u, n-u, g),
                                                     new Merger<>(a, w, l+h, q,
                                                                  l+u, n-u, l+h, g, null));
                    rs.fork();
                    ls.compute();
                    if (rs.tryUnfork()) rs.compute(); else rs.join();
                    new Merger<>(w, a, l, h, l + h, n - h, l, g, null).compute();
                } else {
                    Arrays.sort(a, l, l+n);
                }
            }
        }

        static final class Merger<T extends Comparable<? super T>> extends RecursiveAction {
            static final long serialVersionUID = -3989771675258379302L;
            final T[] a;
            final T[] w;
            final int lo;
            final int ln;
            final int ro;
            final int rn;
            final int wo;
            final int gran;
            final Merger<T> next;

            Merger(T[] a, T[] w, int lo, int ln, int ro, int rn, int wo,
                   int gran, Merger<T> next) {
                this.a = a;
                this.w = w;
                this.lo = lo;
                this.ln = ln;
                this.ro = ro;
                this.rn = rn;
                this.wo = wo;
                this.gran = gran;
                this.next = next;
            }

            public void compute() {
                final T[] a = this.a;
                final T[] w = this.w;
                Merger<T> rights = null;
                int nleft = ln;
                int nright = rn;
                while (nleft > gran) {
                    int lh = nleft >>> 1;
                    int splitIndex = lo + lh;
                    T split = a[splitIndex];
                    int rl = 0;
                    int rh = nright;
                    while (rl < rh) {
                        int mid = (rl + rh) >>> 1;
                        if (split.compareTo(a[ro + mid]) <= 0)
                            rh = mid;
                        else
                            rl = mid + 1;
                    }
                    (rights = new Merger<>(a, w, splitIndex, nleft-lh, ro+rh,
                                           nright-rh, wo+lh+rh, gran, rights)).fork();
                    nleft = lh;
                    nright = rh;
                }

                int l = lo;
                int lFence = l + nleft;
                int r = ro;
                int rFence = r + nright;
                int k = wo;
                while (l < lFence && r < rFence) {
                    T al = a[l];
                    T ar = a[r];
                    T t;
                    if (al.compareTo(ar) <= 0) {++l; t=al;} else {++r; t=ar; }
                    w[k++] = t;
                }
                while (l < lFence)
                    w[k++] = a[l++];
                while (r < rFence)
                    w[k++] = a[r++];
                while (rights != null) {
                    if (rights.tryUnfork())
                        rights.compute();
                    else
                        rights.join();
                    rights = rights.next;
                }
            }
        }
    } // FJComparable

    /** Object + Comparator support class */
    static final class FJComparator {
        static final class Sorter<T> extends RecursiveAction {
            static final long serialVersionUID = 9191600840025808581L;
            final T[] a;       // array to be sorted.
            final T[] w;       // workspace for merge
            final int origin;  // origin of the part of array we deal with
            final int n;       // Number of elements in (sub)arrays.
            final int gran;    // split control
            final Comparator<? super T> cmp; // Comparator to use

            Sorter(T[] a, T[] w, int origin, int n, int gran, Comparator<? super T> cmp) {
                this.a = a;
                this.w = w;
                this.origin = origin;
                this.n = n;
                this.cmp = cmp;
                this.gran = gran;
            }

            public void compute() {
                final int l = origin;
                final int g = gran;
                final int n = this.n;
                final T[] a = this.a;
                final T[] w = this.w;
                if (n > g) {
                    int h = n >>> 1; // half
                    int q = n >>> 2; // lower quarter index
                    int u = h + q;   // upper quarter
                    FJSubSorter ls = new FJSubSorter(new Sorter<>(a, w, l, q,   g, cmp),
                                                     new Sorter<>(a, w, l+q, h-q, g, cmp),
                                                     new Merger<>(a, w, l,   q,
                                                                  l+q, h-q, l, g, null, cmp));
                    FJSubSorter rs = new FJSubSorter(new Sorter<>(a, w, l + h, q,   g, cmp),
                                                     new Sorter<>(a, w, l+u, n-u, g, cmp),
                                                     new Merger<>(a, w, l+h, q,
                                                                  l+u, n-u, l+h, g, null, cmp));
                    rs.fork();
                    ls.compute();
                    if (rs.tryUnfork()) rs.compute(); else rs.join();
                    new Merger<>(w, a, l, h, l + h, n - h, l, g, null, cmp).compute();
                } else {
                    Arrays.sort(a, l, l+n, cmp);
                }
            }
        }

        static final class Merger<T> extends RecursiveAction {
            static final long serialVersionUID = -2679539040379156203L;
            final T[] a;
            final T[] w;
            final int lo;
            final int ln;
            final int ro;
            final int rn;
            final int wo;
            final int gran;
            final Merger<T> next;
            final Comparator<? super T> cmp;

            Merger(T[] a, T[] w, int lo, int ln, int ro, int rn, int wo,
                   int gran, Merger<T> next, Comparator<? super T> cmp) {
                this.a = a;
                this.w = w;
                this.lo = lo;
                this.ln = ln;
                this.ro = ro;
                this.rn = rn;
                this.wo = wo;
                this.gran = gran;
                this.next = next;
                this.cmp = cmp;
            }

            public void compute() {
                final T[] a = this.a;
                final T[] w = this.w;
                Merger<T> rights = null;
                int nleft = ln;
                int nright = rn;
                while (nleft > gran) {
                    int lh = nleft >>> 1;
                    int splitIndex = lo + lh;
                    T split = a[splitIndex];
                    int rl = 0;
                    int rh = nright;
                    while (rl < rh) {
                        int mid = (rl + rh) >>> 1;
                        if (cmp.compare(split, a[ro+mid]) <= 0)
                            rh = mid;
                        else
                            rl = mid + 1;
                    }
                    (rights = new Merger<>(a, w, splitIndex, nleft-lh, ro+rh,
                                           nright-rh, wo+lh+rh, gran, rights, cmp)).fork();
                    nleft = lh;
                    nright = rh;
                }

                int l = lo;
                int lFence = l + nleft;
                int r = ro;
                int rFence = r + nright;
                int k = wo;
                while (l < lFence && r < rFence) {
                    T al = a[l];
                    T ar = a[r];
                    T t;
                    if (cmp.compare(al, ar) <= 0) {
                        ++l;
                        t = al;
                    } else {
                        ++r;
                        t = ar;
                    }
                    w[k++] = t;
                }
                while (l < lFence)
                    w[k++] = a[l++];
                while (r < rFence)
                    w[k++] = a[r++];
                while (rights != null) {
                    if (rights.tryUnfork())
                        rights.compute();
                    else
                        rights.join();
                    rights = rights.next;
                }
            }
        }
    } // FJComparator

    /** Utility class to sort half a partitioned array */
    private static final class FJSubSorter extends RecursiveAction {
        static final long serialVersionUID = 9159249695527935512L;
        final RecursiveAction left;
        final RecursiveAction right;
        final RecursiveAction merger;

        FJSubSorter(RecursiveAction left, RecursiveAction right,
                    RecursiveAction merger) {
            this.left = left;
            this.right = right;
            this.merger = merger;
        }

        public void compute() {
            right.fork();
            left.invoke();
            right.join();
            merger.invoke();
        }
    }
}
