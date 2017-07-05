/*
 * Copyright (c) 2003, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.java.util.jar.pack;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.util.Arrays;

/**
 * Histogram derived from an integer array of events (int[]).
 * @author John Rose
 */
class Histogram {
    // Compact histogram representation:  4 bytes per distinct value,
    // plus 5 words per distinct count.
    protected final int[][] matrix;  // multi-row matrix {{counti,valueij...}}
    protected final int     totalWeight;  // sum of all counts

    // These are created eagerly also, since that saves work.
    // They cost another 8 bytes per distinct value.
    protected final int[]   values;  // unique values, sorted by value
    protected final int[]   counts;  // counts, same order as values

    private static final long LOW32 = (long)-1 >>> 32;

    /** Build a histogram given a sequence of values.
     *  To save work, the input should be sorted, but need not be.
     */
    public
    Histogram(int[] valueSequence) {
        long[] hist2col = computeHistogram2Col(maybeSort(valueSequence));
        int[][] table = makeTable(hist2col);
        values = table[0];
        counts = table[1];
        this.matrix = makeMatrix(hist2col);
        this.totalWeight = valueSequence.length;
        assert(assertWellFormed(valueSequence));
    }
    public
    Histogram(int[] valueSequence, int start, int end) {
        this(sortedSlice(valueSequence, start, end));
    }

    /** Build a histogram given a compact matrix of counts and values. */
    public
    Histogram(int[][] matrix) {
        // sort the rows
        matrix = normalizeMatrix(matrix);  // clone and sort
        this.matrix = matrix;
        int length = 0;
        int weight = 0;
        for (int i = 0; i < matrix.length; i++) {
            int rowLength = matrix[i].length-1;
            length += rowLength;
            weight += matrix[i][0] * rowLength;
        }
        this.totalWeight = weight;
        long[] hist2col = new long[length];
        int fillp = 0;
        for (int i = 0; i < matrix.length; i++) {
            for (int j = 1; j < matrix[i].length; j++) {
                // sort key is value, so put it in the high 32!
                hist2col[fillp++] = ((long) matrix[i][j] << 32)
                                  | (LOW32 & matrix[i][0]);
            }
        }
        assert(fillp == hist2col.length);
        Arrays.sort(hist2col);
        int[][] table = makeTable(hist2col);
        values = table[1]; //backwards
        counts = table[0]; //backwards
        assert(assertWellFormed(null));
    }

    /** Histogram of int values, reported compactly as a ragged matrix,
     *  indexed by descending frequency rank.
     *  <p>
     *  Format of matrix:
     *  Each row in the matrix begins with an occurrence count,
     *  and continues with all int values that occur at that frequency.
     *  <pre>
     *  int[][] matrix = {
     *    { count1, value11, value12, value13, ...  },
     *    { count2, value21, value22, ... },
     *    ...
     *  }
     *  </pre>
     *  The first column of the matrix { count1, count2, ... }
     *  is sorted in descending order, and contains no duplicates.
     *  Each row of the matrix (apart from its first element)
     *  is sorted in ascending order, and contains no duplicates.
     *  That is, each sequence { valuei1, valuei2, ... } is sorted.
     */
    public
    int[][] getMatrix() { return matrix; }

    public
    int getRowCount() { return matrix.length; }

    public
    int getRowFrequency(int rn) { return matrix[rn][0]; }

    public
    int getRowLength(int rn) { return matrix[rn].length-1; }

    public
    int getRowValue(int rn, int vn) { return matrix[rn][vn+1]; }

    public
    int getRowWeight(int rn) {
        return getRowFrequency(rn) * getRowLength(rn);
    }

    public
    int getTotalWeight() {
        return totalWeight;
    }

    public
    int getTotalLength() {
        return values.length;
    }

    /** Returns an array of all values, sorted. */
    public
    int[] getAllValues() {

        return values;
    }

    /** Returns an array parallel with {@link #getValues},
     *  with a frequency for each value.
     */
    public
    int[] getAllFrequencies() {
        return counts;
    }

    private static double log2 = Math.log(2);

    public
    int getFrequency(int value) {
        int pos = Arrays.binarySearch(values, value);
        if (pos < 0)  return 0;
        assert(values[pos] == value);
        return counts[pos];
    }

    public
    double getBitLength(int value) {
        double prob = (double) getFrequency(value) / getTotalWeight();
        return - Math.log(prob) / log2;
    }

    public
    double getRowBitLength(int rn) {
        double prob = (double) getRowFrequency(rn) / getTotalWeight();
        return - Math.log(prob) / log2;
    }

    public
    interface BitMetric {
        public double getBitLength(int value);
    }
    private final BitMetric bitMetric = new BitMetric() {
        public double getBitLength(int value) {
            return Histogram.this.getBitLength(value);
        }
    };
    public BitMetric getBitMetric() {
        return bitMetric;
    }

    /** bit-length is negative entropy:  -H(matrix). */
    public
    double getBitLength() {
        double sum = 0;
        for (int i = 0; i < matrix.length; i++) {
            sum += getRowBitLength(i) * getRowWeight(i);
        }
        assert(0.1 > Math.abs(sum - getBitLength(bitMetric)));
        return sum;
    }

    /** bit-length in to another coding (cross-entropy) */
    public
    double getBitLength(BitMetric len) {
        double sum = 0;
        for (int i = 0; i < matrix.length; i++) {
            for (int j = 1; j < matrix[i].length; j++) {
                sum += matrix[i][0] * len.getBitLength(matrix[i][j]);
            }
        }
        return sum;
    }

    static private
    double round(double x, double scale) {
        return Math.round(x * scale) / scale;
    }

    /** Sort rows and columns.
     *  Merge adjacent rows with the same key element [0].
     *  Make a fresh copy of all of it.
     */
    public int[][] normalizeMatrix(int[][] matrix) {
        long[] rowMap = new long[matrix.length];
        for (int i = 0; i < matrix.length; i++) {
            if (matrix[i].length <= 1)  continue;
            int count = matrix[i][0];
            if (count <= 0)  continue;
            rowMap[i] = (long) count << 32 | i;
        }
        Arrays.sort(rowMap);
        int[][] newMatrix = new int[matrix.length][];
        int prevCount = -1;
        int fillp1 = 0;
        int fillp2 = 0;
        for (int i = 0; ; i++) {
            int[] row;
            if (i < matrix.length) {
                long rowMapEntry = rowMap[rowMap.length-i-1];
                if (rowMapEntry == 0)  continue;
                row = matrix[(int)rowMapEntry];
                assert(rowMapEntry>>>32 == row[0]);
            } else {
                row = new int[]{ -1 };  // close it off
            }
            if (row[0] != prevCount && fillp2 > fillp1) {
                // Close off previous run.
                int length = 0;
                for (int p = fillp1; p < fillp2; p++) {
                    int[] row0 = newMatrix[p];  // previously visited row
                    assert(row0[0] == prevCount);
                    length += row0.length-1;
                }
                int[] row1 = new int[1+length];  // cloned & consolidated row
                row1[0] = prevCount;
                int rfillp = 1;
                for (int p = fillp1; p < fillp2; p++) {
                    int[] row0 = newMatrix[p];  // previously visited row
                    assert(row0[0] == prevCount);
                    System.arraycopy(row0, 1, row1, rfillp, row0.length-1);
                    rfillp += row0.length-1;
                }
                if (!isSorted(row1, 1, true)) {
                    Arrays.sort(row1, 1, row1.length);
                    int jfillp = 2;
                    // Detect and squeeze out duplicates.
                    for (int j = 2; j < row1.length; j++) {
                        if (row1[j] != row1[j-1])
                            row1[jfillp++] = row1[j];
                    }
                    if (jfillp < row1.length) {
                        // Reallocate because of lost duplicates.
                        int[] newRow1 = new int[jfillp];
                        System.arraycopy(row1, 0, newRow1, 0, jfillp);
                        row1 = newRow1;
                    }
                }
                newMatrix[fillp1++] = row1;
                fillp2 = fillp1;
            }
            if (i == matrix.length)
                break;
            prevCount = row[0];
            newMatrix[fillp2++] = row;
        }
        assert(fillp1 == fillp2);  // no unfinished business
        // Now drop missing rows.
        matrix = newMatrix;
        if (fillp1 < matrix.length) {
            newMatrix = new int[fillp1][];
            System.arraycopy(matrix, 0, newMatrix, 0, fillp1);
            matrix = newMatrix;
        }
        return matrix;
    }

    public
    String[] getRowTitles(String name) {
        int totalUnique = getTotalLength();
        int totalWeight = getTotalWeight();
        String[] histTitles = new String[matrix.length];
        int cumWeight = 0;
        int cumUnique = 0;
        for (int i = 0; i < matrix.length; i++) {
            int count  = getRowFrequency(i);
            int unique = getRowLength(i);
            int weight = getRowWeight(i);
            cumWeight += weight;
            cumUnique += unique;
            long wpct = ((long)cumWeight * 100 + totalWeight/2) / totalWeight;
            long upct = ((long)cumUnique * 100 + totalUnique/2) / totalUnique;
            double len = getRowBitLength(i);
            assert(0.1 > Math.abs(len - getBitLength(matrix[i][1])));
            histTitles[i] = name+"["+i+"]"
                +" len="+round(len,10)
                +" ("+count+"*["+unique+"])"
                +" ("+cumWeight+":"+wpct+"%)"
                +" ["+cumUnique+":"+upct+"%]";
        }
        return histTitles;
    }

    /** Print a report of this histogram.
     */
    public
    void print(PrintStream out) {
        print("hist", out);
    }

    /** Print a report of this histogram.
     */
    public
    void print(String name, PrintStream out) {
        print(name, getRowTitles(name), out);
    }

    /** Print a report of this histogram.
     */
    public
    void print(String name, String[] histTitles, PrintStream out) {
        int totalUnique = getTotalLength();
        int totalWeight = getTotalWeight();
        double tlen = getBitLength();
        double avgLen = tlen / totalWeight;
        double avg = (double) totalWeight / totalUnique;
        String title = (name
                        +" len="+round(tlen,10)
                        +" avgLen="+round(avgLen,10)
                        +" weight("+totalWeight+")"
                        +" unique["+totalUnique+"]"
                        +" avgWeight("+round(avg,100)+")");
        if (histTitles == null) {
            out.println(title);
        } else {
            out.println(title+" {");
            StringBuffer buf = new StringBuffer();
            for (int i = 0; i < matrix.length; i++) {
                buf.setLength(0);
                buf.append("  "+histTitles[i]+" {");
                for (int j = 1; j < matrix[i].length; j++) {
                    buf.append(" "+matrix[i][j]);
                }
                buf.append(" }");
                out.println(buf);
            }
            out.println("}");
        }
    }

/*
    public static
    int[][] makeHistogramMatrix(int[] values) {
        // Make sure they are sorted.
        values = maybeSort(values);
        long[] hist2col = computeHistogram2Col(values);
        int[][] matrix = makeMatrix(hist2col);
        return matrix;
    }
*/

    private static
    int[][] makeMatrix(long[] hist2col) {
        // Sort by increasing count, then by increasing value.
        Arrays.sort(hist2col);
        int[] counts = new int[hist2col.length];
        for (int i = 0; i < counts.length; i++) {
            counts[i] = (int)( hist2col[i] >>> 32 );
        }
        long[] countHist = computeHistogram2Col(counts);
        int[][] matrix = new int[countHist.length][];
        int histp = 0;  // cursor into hist2col (increasing count, value)
        int countp = 0; // cursor into countHist (increasing count)
        // Do a join between hist2col (resorted) and countHist.
        for (int i = matrix.length; --i >= 0; ) {
            long countAndRep = countHist[countp++];
            int count  = (int) (countAndRep);  // what is the value count?
            int repeat = (int) (countAndRep >>> 32);  // # times repeated?
            int[] row = new int[1+repeat];
            row[0] = count;
            for (int j = 0; j < repeat; j++) {
                long countAndValue = hist2col[histp++];
                assert(countAndValue >>> 32 == count);
                row[1+j] = (int) countAndValue;
            }
            matrix[i] = row;
        }
        assert(histp == hist2col.length);
        return matrix;
    }

    private static
    int[][] makeTable(long[] hist2col) {
        int[][] table = new int[2][hist2col.length];
        // Break apart the entries in hist2col.
        // table[0] gets values, table[1] gets entries.
        for (int i = 0; i < hist2col.length; i++) {
            table[0][i] = (int)( hist2col[i] );
            table[1][i] = (int)( hist2col[i] >>> 32 );
        }
        return table;
    }

    /** Simple two-column histogram.  Contains repeated counts.
     *  Assumes input is sorted.  Does not sort output columns.
     *  <p>
     *  Format of result:
     *  <pre>
     *  long[] hist = {
     *    (count1 << 32) | (value1),
     *    (count2 << 32) | (value2),
     *    ...
     *  }
     *  </pre>
     *  In addition, the sequence {valuei...} is guaranteed to be sorted.
     *  Note that resorting this using Arrays.sort() will reorder the
     *  entries by increasing count.
     */
    private static
    long[] computeHistogram2Col(int[] sortedValues) {
        switch (sortedValues.length) {
        case 0:
            return new long[]{ };
        case 1:
            return new long[]{ ((long)1 << 32) | (LOW32 & sortedValues[0]) };
        }
        long[] hist = null;
        for (boolean sizeOnly = true; ; sizeOnly = false) {
            int prevIndex = -1;
            int prevValue = sortedValues[0] ^ -1;  // force a difference
            int prevCount = 0;
            for (int i = 0; i <= sortedValues.length; i++) {
                int thisValue;
                if (i < sortedValues.length)
                    thisValue = sortedValues[i];
                else
                    thisValue = prevValue ^ -1;  // force a difference at end
                if (thisValue == prevValue) {
                    prevCount += 1;
                } else {
                    // Found a new value.
                    if (!sizeOnly && prevCount != 0) {
                        // Save away previous value.
                        hist[prevIndex] = ((long)prevCount << 32)
                                        | (LOW32 & prevValue);
                    }
                    prevValue = thisValue;
                    prevCount = 1;
                    prevIndex += 1;
                }
            }
            if (sizeOnly) {
                // Finished the sizing pass.  Allocate the histogram.
                hist = new long[prevIndex];
            } else {
                break;  // done
            }
        }
        return hist;
    }

    /** Regroup the histogram, so that it becomes an approximate histogram
     *  whose rows are of the given lengths.
     *  If matrix rows must be split, the latter parts (larger values)
     *  are placed earlier in the new matrix.
     *  If matrix rows are joined, they are resorted into ascending order.
     *  In the new histogram, the counts are averaged over row entries.
     */
    private static
    int[][] regroupHistogram(int[][] matrix, int[] groups) {
        long oldEntries = 0;
        for (int i = 0; i < matrix.length; i++) {
            oldEntries += matrix[i].length-1;
        }
        long newEntries = 0;
        for (int ni = 0; ni < groups.length; ni++) {
            newEntries += groups[ni];
        }
        if (newEntries > oldEntries) {
            int newlen = groups.length;
            long ok = oldEntries;
            for (int ni = 0; ni < groups.length; ni++) {
                if (ok < groups[ni]) {
                    int[] newGroups = new int[ni+1];
                    System.arraycopy(groups, 0, newGroups, 0, ni+1);
                    groups = newGroups;
                    groups[ni] = (int) ok;
                    ok = 0;
                    break;
                }
                ok -= groups[ni];
            }
        } else {
            long excess = oldEntries - newEntries;
            int[] newGroups = new int[groups.length+1];
            System.arraycopy(groups, 0, newGroups, 0, groups.length);
            newGroups[groups.length] = (int) excess;
            groups = newGroups;
        }
        int[][] newMatrix = new int[groups.length][];
        // Fill pointers.
        int i = 0;  // into matrix
        int jMin = 1;
        int jMax = matrix[i].length;
        for (int ni = 0; ni < groups.length; ni++) {
            int groupLength = groups[ni];
            int[] group = new int[1+groupLength];
            long groupWeight = 0;  // count of all in new group
            newMatrix[ni] = group;
            int njFill = 1;
            while (njFill < group.length) {
                int len = group.length - njFill;
                while (jMin == jMax) {
                    jMin = 1;
                    jMax = matrix[++i].length;
                }
                if (len > jMax - jMin)  len = jMax - jMin;
                groupWeight += (long) matrix[i][0] * len;
                System.arraycopy(matrix[i], jMax - len, group, njFill, len);
                jMax -= len;
                njFill += len;
            }
            Arrays.sort(group, 1, group.length);
            // compute average count of new group:
            group[0] = (int) ((groupWeight + groupLength/2) / groupLength);
        }
        assert(jMin == jMax);
        assert(i == matrix.length-1);
        return newMatrix;
    }

    public static
    Histogram makeByteHistogram(InputStream bytes) throws IOException {
        byte[] buf = new byte[1<<12];
        int[] tally = new int[1<<8];
        for (int nr; (nr = bytes.read(buf)) > 0; ) {
            for (int i = 0; i < nr; i++) {
                tally[buf[i] & 0xFF] += 1;
            }
        }
        // Build a matrix.
        int[][] matrix = new int[1<<8][2];
        for (int i = 0; i < tally.length; i++) {
            matrix[i][0] = tally[i];
            matrix[i][1] = i;
        }
        return new Histogram(matrix);
    }

    /** Slice and sort the given input array. */
    private static
    int[] sortedSlice(int[] valueSequence, int start, int end) {
        if (start == 0 && end == valueSequence.length &&
            isSorted(valueSequence, 0, false)) {
            return valueSequence;
        } else {
            int[] slice = new int[end-start];
            System.arraycopy(valueSequence, start, slice, 0, slice.length);
            Arrays.sort(slice);
            return slice;
        }
    }

    /** Tell if an array is sorted. */
    private static
    boolean isSorted(int[] values, int from, boolean strict) {
        for (int i = from+1; i < values.length; i++) {
            if (strict ? !(values[i-1] < values[i])
                       : !(values[i-1] <= values[i])) {
                return false;  // found witness to disorder
            }
        }
        return true;  // no witness => sorted
    }

    /** Clone and sort the array, if not already sorted. */
    private static
    int[] maybeSort(int[] values) {
        if (!isSorted(values, 0, false)) {
            values = (int[]) values.clone();
            Arrays.sort(values);
        }
        return values;
    }


    /// Debug stuff follows.

    private boolean assertWellFormed(int[] valueSequence) {
/*
        // Sanity check.
        int weight = 0;
        int vlength = 0;
        for (int i = 0; i < matrix.length; i++) {
            int vlengthi = (matrix[i].length-1);
            int count = matrix[i][0];
            assert(vlengthi > 0);  // no empty rows
            assert(count > 0);  // no impossible rows
            vlength += vlengthi;
            weight += count * vlengthi;
        }
        assert(isSorted(values, 0, true));
        // make sure the counts all add up
        assert(totalWeight == weight);
        assert(vlength == values.length);
        assert(vlength == counts.length);
        int weight2 = 0;
        for (int i = 0; i < counts.length; i++) {
            weight2 += counts[i];
        }
        assert(weight2 == weight);
        int[] revcol1 = new int[matrix.length];  //1st matrix colunm
        for (int i = 0; i < matrix.length; i++) {
            // spot checking:  try a random query on each matrix row
            assert(matrix[i].length > 1);
            revcol1[matrix.length-i-1] = matrix[i][0];
            assert(isSorted(matrix[i], 1, true));
            int rand = (matrix[i].length+1) / 2;
            int val = matrix[i][rand];
            int count = matrix[i][0];
            int pos = Arrays.binarySearch(values, val);
            assert(values[pos] == val);
            assert(counts[pos] == matrix[i][0]);
            if (valueSequence != null) {
                int count2 = 0;
                for (int j = 0; j < valueSequence.length; j++) {
                    if (valueSequence[j] == val)  count2++;
                }
                assert(count2 == count);
            }
        }
        assert(isSorted(revcol1, 0, true));
//*/
        return true;
    }

/*
    public static
    int[] readValuesFrom(InputStream instr) {
        return readValuesFrom(new InputStreamReader(instr));
    }
    public static
    int[] readValuesFrom(Reader inrdr) {
        inrdr = new BufferedReader(inrdr);
        final StreamTokenizer in = new StreamTokenizer(inrdr);
        final int TT_NOTHING = -99;
        in.commentChar('#');
        return readValuesFrom(new Iterator() {
            int token = TT_NOTHING;
            private int getToken() {
                if (token == TT_NOTHING) {
                    try {
                        token = in.nextToken();
                        assert(token != TT_NOTHING);
                    } catch (IOException ee) {
                        throw new RuntimeException(ee);
                    }
                }
                return token;
            }
            public boolean hasNext() {
                return getToken() != StreamTokenizer.TT_EOF;
            }
            public Object next() {
                int ntok = getToken();
                token = TT_NOTHING;
                switch (ntok) {
                case StreamTokenizer.TT_EOF:
                    throw new NoSuchElementException();
                case StreamTokenizer.TT_NUMBER:
                    return new Integer((int) in.nval);
                default:
                    assert(false);
                    return null;
                }
            }
            public void remove() {
                throw new UnsupportedOperationException();
            }
        });
    }
    public static
    int[] readValuesFrom(Iterator iter) {
        return readValuesFrom(iter, 0);
    }
    public static
    int[] readValuesFrom(Iterator iter, int initSize) {
        int[] na = new int[Math.max(10, initSize)];
        int np = 0;
        while (iter.hasNext()) {
            Integer val = (Integer) iter.next();
            if (np == na.length) {
                int[] na2 = new int[np*2];
                System.arraycopy(na, 0, na2, 0, np);
                na = na2;
            }
            na[np++] = val.intValue();
        }
        if (np != na.length) {
            int[] na2 = new int[np];
            System.arraycopy(na, 0, na2, 0, np);
            na = na2;
        }
        return na;
    }

    public static
    Histogram makeByteHistogram(byte[] bytes) {
        try {
            return makeByteHistogram(new ByteArrayInputStream(bytes));
        } catch (IOException ee) {
            throw new RuntimeException(ee);
        }
    }

    public static
    void main(String[] av) throws IOException {
        if (av.length > 0 && av[0].equals("-r")) {
            int[] values = new int[Integer.parseInt(av[1])];
            int limit = values.length;
            if (av.length >= 3) {
                limit  = (int)( limit * Double.parseDouble(av[2]) );
            }
            Random rnd = new Random();
            for (int i = 0; i < values.length; i++) {
                values[i] = rnd.nextInt(limit);;
            }
            Histogram rh = new Histogram(values);
            rh.print("random", System.out);
            return;
        }
        if (av.length > 0 && av[0].equals("-s")) {
            int[] values = readValuesFrom(System.in);
            Random rnd = new Random();
            for (int i = values.length; --i > 0; ) {
                int j = rnd.nextInt(i+1);
                if (j < i) {
                    int tem = values[i];
                    values[i] = values[j];
                    values[j] = tem;
                }
            }
            for (int i = 0; i < values.length; i++)
                System.out.println(values[i]);
            return;
        }
        if (av.length > 0 && av[0].equals("-e")) {
            // edge cases
            new Histogram(new int[][] {
                {1, 11, 111},
                {0, 123, 456},
                {1, 111, 1111},
                {0, 456, 123},
                {3},
                {},
                {3},
                {2, 22},
                {4}
            }).print(System.out);
            return;
        }
        if (av.length > 0 && av[0].equals("-b")) {
            // edge cases
            Histogram bh = makeByteHistogram(System.in);
            bh.print("bytes", System.out);
            return;
        }
        boolean regroup = false;
        if (av.length > 0 && av[0].equals("-g")) {
            regroup = true;
        }

        int[] values = readValuesFrom(System.in);
        Histogram h = new Histogram(values);
        if (!regroup)
            h.print(System.out);
        if (regroup) {
            int[] groups = new int[12];
            for (int i = 0; i < groups.length; i++) {
                groups[i] = 1<<i;
            }
            int[][] gm = regroupHistogram(h.getMatrix(), groups);
            Histogram g = new Histogram(gm);
            System.out.println("h.getBitLength(g) = "+
                               h.getBitLength(g.getBitMetric()));
            System.out.println("g.getBitLength(h) = "+
                               g.getBitLength(h.getBitMetric()));
            g.print("regrouped", System.out);
        }
    }
//*/
}
