// Copyright 2016 and later: Unicode, Inc. and others.
// License & terms of use: http://www.unicode.org/copyright.html
/*
 *******************************************************************************
 * Copyright (C) 1996-2015, Google, Inc., International Business Machines Corporation and
 * others. All Rights Reserved.
 *******************************************************************************
 */
package jdk.internal.icu.impl;

import java.util.Collection;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import jdk.internal.icu.lang.CharSequences;
import jdk.internal.icu.util.ICUException;

@SuppressWarnings("deprecation")
public class StringRange {
    private static final boolean DEBUG = false;

    public interface Adder {
        /**
         * @param start
         * @param end   may be null, for adding single string
         */
        void add(String start, String end);
    }

    public static final Comparator<int[]> COMPARE_INT_ARRAYS = new Comparator<int[]>() {
        @Override
        public int compare(int[] o1, int[] o2) {
            int minIndex = Math.min(o1.length, o2.length);
            for (int i = 0; i < minIndex; ++i) {
                int diff = o1[i] - o2[i];
                if (diff != 0) {
                    return diff;
                }
            }
            return o1.length - o2.length;
        }
    };

    /**
     * Compact the set of strings.
     * @param source set of strings
     * @param adder adds each pair to the output. See the {@link Adder} interface.
     * @param shorterPairs use abc-d instead of abc-abd
     * @param moreCompact use a more compact form, at the expense of more processing. If false, source must be sorted.
     */
    public static void compact(Set<String> source, Adder adder, boolean shorterPairs, boolean moreCompact) {
        if (!moreCompact) {
            String start = null;
            String end = null;
            int lastCp = 0;
            int prefixLen = 0;
            for (String s : source) {
                if (start != null) { // We have something queued up
                    if (s.regionMatches(0, start, 0, prefixLen)) {
                        int currentCp = s.codePointAt(prefixLen);
                        if (currentCp == 1+lastCp && s.length() == prefixLen + Character.charCount(currentCp)) {
                            end = s;
                            lastCp = currentCp;
                            continue;
                        }
                    }
                    // We failed to find continuation. Add what we have and restart
                    adder.add(start, end == null ? null
                        : !shorterPairs ? end
                            : end.substring(prefixLen, end.length()));
                }
                // new possible range
                start = s;
                end = null;
                lastCp = s.codePointBefore(s.length());
                prefixLen = s.length() - Character.charCount(lastCp);
            }
            adder.add(start, end == null ? null
                : !shorterPairs ? end
                    : end.substring(prefixLen, end.length()));
        } else {
            // not a fast algorithm, but ok for now
            // TODO rewire to use the first (slower) algorithm to generate the ranges, then compact them from there.
            // first sort by lengths
            Relation<Integer,Ranges> lengthToArrays = Relation.of(new TreeMap<Integer,Set<Ranges>>(), TreeSet.class);
            for (String s : source) {
                Ranges item = new Ranges(s);
                lengthToArrays.put(item.size(), item);
            }
            // then compact items of each length and emit compacted sets
            for (Entry<Integer, Set<Ranges>> entry : lengthToArrays.keyValuesSet()) {
                LinkedList<Ranges> compacted = compact(entry.getKey(), entry.getValue());
                for (Ranges ranges : compacted) {
                    adder.add(ranges.start(), ranges.end(shorterPairs));
                }
            }
        }
    }

    /**
     * Faster but not as good compaction. Only looks at final codepoint.
     * @param source set of strings
     * @param adder adds each pair to the output. See the {@link Adder} interface.
     * @param shorterPairs use abc-d instead of abc-abd
     */
    public static void compact(Set<String> source, Adder adder, boolean shorterPairs) {
        compact(source,adder,shorterPairs,false);
    }

    private static LinkedList<Ranges> compact(int size, Set<Ranges> inputRanges) {
        LinkedList<Ranges> ranges = new LinkedList<Ranges>(inputRanges);
        for (int i = size-1; i >= 0; --i) {
            Ranges last = null;
            for (Iterator<Ranges> it = ranges.iterator(); it.hasNext();) {
                Ranges item = it.next();
                if (last == null) {
                    last = item;
                } else if (last.merge(i, item)) {
                    it.remove();
                } else {
                    last = item; // go to next
                }
            }
        };
        return ranges;
    }

    static final class Range implements Comparable<Range>{
        int min;
        int max;
        public Range(int min, int max) {
            this.min = min;
            this.max = max;
        }
        @Override
        public boolean equals(Object obj) {
            return this == obj || (obj != null && obj instanceof Range && compareTo((Range)obj) == 0);
        }
        @Override
        public int compareTo(Range that) {
            int diff = min - that.min;
            if (diff != 0) {
                return diff;
            }
            return max - that.max;
        }
        @Override
        public int hashCode() {
            return min * 37 + max;
        }
        @Override
        public String toString() {
            StringBuilder result = new StringBuilder().appendCodePoint(min);
            return min == max ? result.toString() : result.append('~').appendCodePoint(max).toString();
        }
    }

    static final class Ranges implements Comparable<Ranges> {
        private final Range[] ranges;
        public Ranges(String s) {
            int[] array = CharSequences.codePoints(s);
            ranges = new Range[array.length];
            for (int i = 0; i < array.length; ++i) {
                ranges[i] = new Range(array[i], array[i]);
            }
        }
        public boolean merge(int pivot, Ranges other) {
           // We merge items if the pivot is adjacent, and all later ranges are equal.
           for (int i = ranges.length-1; i >= 0; --i) {
               if (i == pivot) {
                   if (ranges[i].max != other.ranges[i].min-1) { // not adjacent
                       return false;
                   }
               } else {
                   if (!ranges[i].equals(other.ranges[i])) {
                       return false;
                   }
               }
           }
           if (DEBUG) System.out.print("Merging: " + this + ", " + other);
           ranges[pivot].max = other.ranges[pivot].max;
           if (DEBUG) System.out.println(" => " + this);
           return true;
        }

        public String start() {
            StringBuilder result = new StringBuilder();
            for (int i = 0; i < ranges.length; ++i) {
                result.appendCodePoint(ranges[i].min);
            }
            return result.toString();
        }
        public String end(boolean mostCompact) {
            int firstDiff = firstDifference();
            if (firstDiff == ranges.length) {
                return null;
            }
            StringBuilder result = new StringBuilder();
            for (int i = mostCompact ? firstDiff : 0; i < ranges.length; ++i) {
                result.appendCodePoint(ranges[i].max);
            }
            return result.toString();
        }
        public int firstDifference() {
            for (int i = 0; i < ranges.length; ++i) {
                if (ranges[i].min != ranges[i].max){
                    return i;
                }
            }
            return ranges.length;
        }
        public Integer size() {
            return ranges.length;
        }
        @Override
        public int compareTo(Ranges other) {
            int diff = ranges.length - other.ranges.length;
            if (diff != 0) {
                return diff;
            }
            for (int i = 0; i < ranges.length; ++i) {
                diff = ranges[i].compareTo(other.ranges[i]);
                if (diff != 0) {
                    return diff;
                }
            }
            return 0;
        }
        @Override
        public String toString() {
            String start = start();
            String end = end(false);
            return end == null ? start : start + "~" + end;
        }
    }

    public static Collection<String> expand(String start, String end, boolean requireSameLength, Collection<String> output) {
        if (start == null || end == null) {
            throw new ICUException("Range must have 2 valid strings");
        }
        int[] startCps = CharSequences.codePoints(start);
        int[] endCps = CharSequences.codePoints(end);
        int startOffset = startCps.length - endCps.length;

        if (requireSameLength && startOffset != 0) {
            throw new ICUException("Range must have equal-length strings");
        } else if (startOffset < 0) {
            throw new ICUException("Range must have start-length \u2265 end-length");
        } else if (endCps.length == 0) {
            throw new ICUException("Range must have end-length > 0");
        }

        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < startOffset; ++i) {
            builder.appendCodePoint(startCps[i]);
        }
        add(0, startOffset, startCps, endCps, builder, output);
        return output;
    }

    private static void add(int endIndex, int startOffset, int[] starts, int[] ends, StringBuilder builder, Collection<String> output) {
        int start = starts[endIndex+startOffset];
        int end = ends[endIndex];
        if (start > end) {
            throw new ICUException("Range must have x\u1d62 \u2264 y\u1d62 for each index i");
        }
        boolean last = endIndex == ends.length - 1;
        int startLen = builder.length();
        for (int i = start; i <= end; ++i) {
            builder.appendCodePoint(i);
            if (last) {
                output.add(builder.toString());
            } else {
                add(endIndex+1, startOffset, starts, ends, builder, output);
            }
            builder.setLength(startLen);
        }
    }
}
