/*
 * Copyright (c) 2023, 2024, Oracle and/or its affiliates. All rights reserved.
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
package jdk.jfr.internal.query;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.Set;

abstract class Function {

    public abstract void add(Object value);

    public abstract Object result();

    public static Function create(Field field) {
        Aggregator aggregator = field.aggregator;

        if (field.grouper != null || aggregator == Aggregator.MISSING) {
            return new FirstNonNull();
        }
        if (aggregator == Aggregator.LIST) {
            return new Container(new ArrayList<>());
        }

        if (aggregator == Aggregator.SET) {
            return new Container(new LinkedHashSet<>());
        }

        if (aggregator == Aggregator.DIFFERENCE) {
            if (field.timestamp) {
                return new TimeDifference();
            } else {
                return new Difference();
            }
        }

        if (aggregator == Aggregator.STANDARD_DEVIATION) {
            if (field.timespan) {
                return new TimespanFunction(new StandardDeviation());
            } else {
                return new StandardDeviation();
            }
        }

        if (aggregator == Aggregator.MEDIAN) {
            if (field.timespan) {
                return new TimespanFunction(new Median());
            } else {
                return new Median();
            }
        }

        if (aggregator == Aggregator.P90) {
            return createPercentile(field, 0.90);
        }

        if (aggregator == Aggregator.P95) {
            return createPercentile(field, 0.95);

        }
        if (aggregator == Aggregator.P99) {
            return createPercentile(field, 0.99);
        }
        if (aggregator == Aggregator.P999) {
            return createPercentile(field, 0.9999);
        }
        if (aggregator == Aggregator.MAXIMUM) {
            return new Maximum();
        }
        if (aggregator == Aggregator.MINIMUM) {
            return new Minimum();
        }
        if (aggregator == Aggregator.SUM) {
            if (field.timespan) {
                return new SumDuration();
            }
            if (field.fractionalType) {
                return new SumDouble();
            }
            if (field.integralType) {
                return new SumLong();
            }
        }

        if (aggregator == Aggregator.FIRST) {
            return new First();
        }
        if (aggregator == Aggregator.LAST_BATCH) {
            return new LastBatch(field);
        }
        if (aggregator == Aggregator.LAST) {
            return new Last();
        }
        if (aggregator == Aggregator.AVERAGE) {
            if (field.timespan) {
                return new AverageDuration();
            } else {
                return new Average();
            }
        }
        if (aggregator == Aggregator.COUNT) {
            return new Count();
        }
        if (aggregator == Aggregator.UNIQUE) {
            return new Unique();
        }
        return new Null();
    }

    // **** AVERAGE ****

    private static final class Average extends Function {
        private double total;
        private long count;

        @Override
        public void add(Object value) {
            if (value instanceof Number n && Double.isFinite(n.doubleValue())) {
                total += n.doubleValue();
                count++;
            }
        }

        @Override
        public Object result() {
            if (count != 0) {
                return total / count;
            } else {
                return null;
            }
        }
    }

    private static final class AverageDuration extends Function {
        private long seconds;
        private long nanos;
        private int count;

        @Override
        public void add(Object value) {
            if (value instanceof Duration duration) {
                seconds += duration.getSeconds();
                nanos += duration.getNano();
                count++;
            }
        }

        @Override
        public Object result() {
            if (count != 0) {
                long s = seconds / count;
                long n = nanos / count;
                return Duration.ofSeconds(s, n);
            } else {
                return null;
            }
        }
    }

    // **** COUNT ****

    private static final class Count extends Function {
        private long count = 0;

        @Override
        public void add(Object value) {
            count++;
        }

        @Override
        public Object result() {
            return count;
        }
    }

    // **** FIRST ****

    private static final class First extends Function {
        private static Object firstObject = new Object();
        private Object first = firstObject;

        @Override
        public void add(Object value) {
            if (first == firstObject) {
                first = value;
            }
        }

        @Override
        public Object result() {
            return first == firstObject ? null : first;
        }
    }

    // **** LAST ****

    private static final class Last extends Function {
        private static Object lastObject = new Object();
        private Object last = lastObject;

        @Override
        public void add(Object value) {
            last = value;
        }

        @Override
        public Object result() {
            return last == lastObject ? null : last;
        }
    }

    private static final class FirstNonNull extends Function {
        private Object first;

        @Override
        public void add(Object value) {
            if (value == null) {
                return;
            }
            first = value;
        }

        @Override
        public Object result() {
            return first;
        }
    }

    // **** MAXIMUM ****

    @SuppressWarnings("rawtypes")
    private static final class Maximum extends Function {
        private Comparable<Comparable> maximum;

        @SuppressWarnings("unchecked")
        @Override
        public void add(Object value) {
            if (value instanceof Comparable comparable) {
                if (maximum == null) {
                    maximum = comparable;
                    return;
                }
                if (comparable.compareTo(maximum) > 0) {
                    maximum = comparable;
                }
            }
        }

        @Override
        public Object result() {
            return maximum;
        }
    }

    // **** MINIMUM ****

    @SuppressWarnings("rawtypes")
    private static final class Minimum extends Function {
        private Comparable<Comparable> minimum;

        @SuppressWarnings("unchecked")
        @Override
        public void add(Object value) {
            if (value instanceof Comparable comparable) {
                if (minimum == null) {
                    minimum = comparable;
                    return;
                }
                if (comparable.compareTo(minimum) < 0) {
                    minimum = comparable;
                }
            }
        }

        @Override
        public Object result() {
            return minimum;
        }
    }

    // *** NULL ****

    private static final class Null extends Function {
        @Override
        public void add(Object value) {
        }

        @Override
        public Object result() {
            return null;
        }
    }

    // **** SUM ****

    private static final class SumDouble extends Function {
        private boolean hasValue = false;
        private double sum = 0;

        @Override
        public void add(Object value) {
            if (value instanceof Number n && Double.isFinite(n.doubleValue())) {
                sum += n.doubleValue();
                hasValue = true;
            }
        }

        @Override
        public Object result() {
            return hasValue ? sum : null;
        }
    }

    private static final class SumDuration extends Function {
        private long seconds;
        private long nanos;
        private boolean hasValue;

        @Override
        public void add(Object value) {
            if (value instanceof Duration n) {
                seconds += n.getSeconds();
                nanos += n.getNano();
                hasValue = true;
            }
        }

        @Override
        public Object result() {
            return hasValue ? Duration.ofSeconds(seconds, nanos) : null;
        }
    }

    private static final class SumLong extends Function {
        private boolean hasValue = false;
        private long sum = 0;

        @Override
        public void add(Object value) {
            if (value instanceof Number n) {
                sum += n.longValue();
                hasValue = true;
            }
        }

        @Override
        public Object result() {
            return hasValue ? sum : null;
        }
    }

    // **** UNIQUE ****

    private static final class Unique extends Function {
        private final Set<Object> unique = new LinkedHashSet<>();

        @Override
        public void add(Object value) {
            unique.add(value);
        }

        @Override
        public Object result() {
            return unique.size();
        }
    }

    // **** LIST and SET ****

    private static final class Container extends Function {
        private final Collection<Object> collection;

        private Container(Collection<Object> collection) {
            this.collection = collection;
        }
        @Override
        public void add(Object value) {
            collection.add(value);
        }

        @Override
        public Object result() {
            return collection;
        }
    }

    // **** DIFF ****

    private static final class Difference extends Function {
        private Number first;
        private Number last;

        @Override
        public void add(Object value) {
            if (value instanceof Number number && Double.isFinite(number.doubleValue())) {
                if (first == null) {
                    first = number;
                }
                last = number;
            }
        }

        @Override
        public Object result() {
            if (last == null) {
                return null;
            }
            if (isIntegral(first) && isIntegral(last)) {
                return last.longValue() - first.longValue();
            }
            if (first instanceof Float f && last instanceof Float l) {
                return l - f;
            }
            return last.doubleValue() - first.doubleValue();
        }

        private boolean isIntegral(Number number) {
            if ((number instanceof Long) || (number instanceof Integer) || (number instanceof Short)
                    || (number instanceof Byte)) {
                return true;
            }
            return false;
        }
    }
    private static final class TimeDifference extends Function {
        private Instant first;
        private Instant last;

        @Override
        public void add(Object value) {
            if (value instanceof Instant instant) {
                if (first == null) {
                    first = instant;
                    return;
                }
                last = instant;
            }
        }

        @Override
        public Object result() {
            if (first == null) {
                return null;
            }
            if (last == null) {
                return ChronoUnit.FOREVER.getDuration();
            }
            return Duration.between(first, last);
        }
    }

    @SuppressWarnings("rawtypes")
    private static final class Median extends Function {
        private final java.util.List<Comparable> comparables = new ArrayList<>();

        @Override
        public void add(Object value) {
            if (value instanceof Number && value instanceof Comparable c) {
                comparables.add(c);
            }
        }

        @Override
        @SuppressWarnings("unchecked")
        public Object result() {
            if (comparables.isEmpty()) {
                return null;
            }
            if (comparables.size() == 1) {
                return comparables.getFirst();
            }
            comparables.sort(Comparator.naturalOrder());
            if (comparables.size() % 2 == 1) {
                return comparables.get(comparables.size() / 2);
            }
            Number a = (Number) comparables.get(comparables.size() / 2 - 1);
            Number b = (Number) comparables.get(comparables.size() / 2);
            return (a.doubleValue() + b.doubleValue()) / 2;
        }
    }

    // **** PERCENTILE ****
    private static Function createPercentile(Field field, double percentile) {
        Percentile p = new Percentile(percentile);
        if (field.timespan) {
            return new TimespanFunction(p);
        } else {
            return p;
        }
    }

    private static final class TimespanFunction extends Function {
        private final Function function;

        TimespanFunction(Function function) {
            this.function = function;
        }

        @Override
        public void add(Object value) {
            if (value instanceof Duration duration) {
                long nanos = 1_000_000_000L * duration.getSeconds() + duration.getNano();
                function.add(nanos);
            }
        }

        @Override
        public Object result() {
            Object object = function.result();
            if (object instanceof Number nanos) {
                return Duration.ofNanos(nanos.longValue());
            }
            return null;
        }
    }
    private static final class Percentile extends Function {
        private final double percentile;
        private final java.util.List<Number> numbers = new ArrayList<>();

        Percentile(double percentile) {
            this.percentile = percentile;
        }

        @Override
        public void add(Object value) {
            if (value instanceof Number number) {
                if (Double.isFinite(number.doubleValue())) {
                    numbers.add(number);
                }
            }
        }

        @Override
        public Object result() {
            if (numbers.isEmpty()) {
                return null;
            }
            if (numbers.size() == 1) {
                return numbers.getFirst();
            }
            numbers.sort((n1, n2) -> Double.compare(n1.doubleValue(), n2.doubleValue()));
            int size = numbers.size();
            // Use size + 1 so range is stretched out for interpolation
            // For example with percentile 50%
            // size |  valueIndex |  valueNextindex | fraction
            //   2         0               1            0.50
            //   3         1               2             0.0
            //   4         1               2            0.50
            //   5         2               3             0.0
            //   6         2               3            0.50
            double doubleIndex = (size + 1) * percentile;
            int valueIndex = (int) doubleIndex - 1;
            int valueNextIndex = (int) doubleIndex;
            double fraction = doubleIndex - valueIndex;

            if (valueIndex < 0) {
                return numbers.getFirst();
            }
            if (valueNextIndex >= size) {
                return numbers.getLast();
            }
            double a = numbers.get(valueIndex).doubleValue();
            double b = numbers.get(valueNextIndex).doubleValue();
            return a + fraction * (b - a);
        }
    }

    // **** STANDARD DEVIATION ****

    private static final class StandardDeviation extends Function {
        private final java.util.List<Number> values = new ArrayList<>();

        @Override
        public void add(Object value) {
            if (value instanceof Number n && Double.isFinite(n.doubleValue())) {
                values.add(n);
            }
        }

        @Override
        public Object result() {
            if (values.size() > 0) {
                long N = values.size();
                double average = sum() / N;
                double sum = 0;
                for (Number number : values) {
                    double diff = number.doubleValue() - average;
                    sum = sum + (diff * diff);
                }
                return Math.sqrt(sum / N);
            }
            return null;
        }

        private double sum() {
            double sum = 0;
            for (Number number : values) {
                sum += number.doubleValue();
            }
            return sum ;
        }
    }

    public static final class LastBatch extends Function {
        private final Field field;
        private final Last last = new Last();
        private Instant timestamp;

        public LastBatch(Field field) {
            this.field = field;
        }

        @Override
        public void add(Object value) {
            last.add(value);
        }

        @Override
        public Object result() {
            return last.result();
        }

        public void setTime(Instant timestamp) {
           this.timestamp = timestamp;
           field.last = timestamp;
        }

        public boolean valid() {
            if (timestamp != null) {
                return timestamp.equals(field.last);
            }
            return true;
        }
    }
}
