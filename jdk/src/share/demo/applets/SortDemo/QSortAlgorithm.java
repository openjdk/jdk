/*
 * Copyright (c) 1997, 2011, Oracle and/or its affiliates. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 *   - Redistributions of source code must retain the above copyright
 *     notice, this list of conditions and the following disclaimer.
 *
 *   - Redistributions in binary form must reproduce the above copyright
 *     notice, this list of conditions and the following disclaimer in the
 *     documentation and/or other materials provided with the distribution.
 *
 *   - Neither the name of Oracle nor the names of its
 *     contributors may be used to endorse or promote products derived
 *     from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS
 * IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO,
 * THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR
 * PURPOSE ARE DISCLAIMED.  IN NO EVENT SHALL THE COPYRIGHT OWNER OR
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
 * PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR
 * PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
 * LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */


/**
 * A quick sort demonstration algorithm
 * SortAlgorithm.java
 *
 * @author James Gosling
 * @author Kevin A. Smith
 */
public class QSortAlgorithm extends SortAlgorithm {

    /**
     * A version of pause() that makes it easier to ensure that we pause
     * exactly the right number of times.
     */
    private boolean pauseTrue(int lo, int hi) throws Exception {
        super.pause(lo, hi);
        return true;
    }

    /** This is a generic version of C.A.R Hoare's Quick Sort
     * algorithm.  This will handle arrays that are already
     * sorted, and arrays with duplicate keys.<BR>
     *
     * If you think of a one dimensional array as going from
     * the lowest index on the left to the highest index on the right
     * then the parameters to this function are lowest index or
     * left and highest index or right.  The first time you call
     * this function it will be with the parameters 0, a.length - 1.
     *
     * @param a       an integer array
     * @param lo0     left boundary of array partition
     * @param hi0     right boundary of array partition
     */
    void QuickSort(int a[], int lo0, int hi0) throws Exception {
        int lo = lo0;
        int hi = hi0;
        int mid;

        if (hi0 > lo0) {

            /* Arbitrarily establishing partition element as the midpoint of
             * the array.
             */
            mid = a[(lo0 + hi0) / 2];

            // loop through the array until indices cross
            while (lo <= hi) {
                /* find the first element that is greater than or equal to
                 * the partition element starting from the left Index.
                 */
                while ((lo < hi0) && pauseTrue(lo0, hi0) && (a[lo] < mid)) {
                    ++lo;
                }

                /* find an element that is smaller than or equal to
                 * the partition element starting from the right Index.
                 */
                while ((hi > lo0) && pauseTrue(lo0, hi0) && (a[hi] > mid)) {
                    --hi;
                }

                // if the indexes have not crossed, swap
                if (lo <= hi) {
                    swap(a, lo, hi);
                    ++lo;
                    --hi;
                }
            }

            /* If the right index has not reached the left side of array
             * must now sort the left partition.
             */
            if (lo0 < hi) {
                QuickSort(a, lo0, hi);
            }

            /* If the left index has not reached the right side of array
             * must now sort the right partition.
             */
            if (lo < hi0) {
                QuickSort(a, lo, hi0);
            }

        }
    }

    private void swap(int a[], int i, int j) {
        int T;
        T = a[i];
        a[i] = a[j];
        a[j] = T;

    }

    @Override
    public void sort(int a[]) throws Exception {
        QuickSort(a, 0, a.length - 1);
    }
}
