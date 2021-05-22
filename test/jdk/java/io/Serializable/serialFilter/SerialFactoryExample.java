/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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

import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InvalidClassException;
import java.io.ObjectInputFilter;
import java.io.ObjectInputFilter.Status;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.io.UncheckedIOException;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Objects;
import java.util.function.BinaryOperator;
import java.util.function.Predicate;

import static java.io.ObjectInputFilter.Status.ALLOWED;
import static java.io.ObjectInputFilter.Status.REJECTED;
import static java.io.ObjectInputFilter.Status.UNDECIDED;

/* @test
 * @run testng/othervm -Djdk.serialFilterTrace=true SerialFactoryExample
 * @run testng/othervm -Djdk.serialFilterFactory=SerialFactoryExample$FilterInThread -Djdk.serialFilterTrace=true SerialFactoryExample
 * @summary Test SerialFactoryExample
 */

/*
 * Context-specific Deserialization Filter Example
 *
 * To protect deserialization of a thread or a call to an untrusted library function,
 * a filter is set that applies to every deserialization within the thread.
 *
 * The `doWithSerialFilter` method arguments are a serial filter and
 * a lambda to invoke with the filter in force.  Its implementation creates a stack of filters
 * using a `ThreadLocal`. The stack of filters is composed with the static JVM-wide filter,
 * and an optional stream-specific filter.
 *
 * The FilterInThread filter factory is set as the JVM-wide filter factory.
 * When the filter factory is invoked during the construction of each `ObjectInputStream`,
 * it retrieves the filter(s) from the thread local and combines it with the static JVM-wide filter,
 * and the stream-specific filter.
 *
 * If more than one filter is to be applied to the stream, two filters can be composed
 * using `ObjectInputFilter.merge`.  When invoked, each of the filters is invoked and the results
 * are combined such that if either filter rejects a class, the result is rejected.
 * If either filter allows the class, then it is allowed, otherwise it is undecided.
 * Hierarchies and chains of filters can be built using `ObjectInputFilter.merge`.
 *
 * The `doWithSerialFilter` calls can be nested. When nested, the filters are concatenated.
 */
@Test
public class SerialFactoryExample {

    private static final Class<? extends Exception> NO_EXCEPTION = null;

    @DataProvider(name = "Examples")
    static Object[][] examples() {
        return new Object[][]{
                {new Point(1, 2), null,
                        ALLOWED},
                {new Point(1, 2), ObjectInputFilter.Config.createFilter("SerialFactoryExample$Point"),
                        ALLOWED},
                {Integer.valueOf(10), Filters.allowPlatformClasses(),
                        ALLOWED},          // Integer is a platform class
                {new int[10], ObjectInputFilter.Config.createFilter("SerialFactoryExample$Point"),
                        UNDECIDED},          // arrays of primitives are UNDECIDED -> allowed
                {int.class, ObjectInputFilter.Config.createFilter("SerialFactoryExample$Point"),
                        UNDECIDED},          // primitive classes are UNDECIDED -> allowed
                {new Point[] {new Point(1, 1)}, ObjectInputFilter.Config.createFilter("SerialFactoryExample$Point"),
                        ALLOWED},          // Arrays of allowed classes are allowed
                {new Integer[10], ObjectInputFilter.Config.createFilter("SerialFactoryExample$Point"),
                        REJECTED},   // Base component type is checked -> REJECTED
                {new Point(1, 2), ObjectInputFilter.Config.createFilter("!SerialFactoryExample$Point"),
                        REJECTED},   // Denied
                {new Point(1, 3), Filters.allowPlatformClasses(),
                        REJECTED},   // Not a platform class
                {new Point(1, 4), ObjectInputFilter.Config.createFilter("java.lang.Integer"),
                        REJECTED},   // Only Integer is ALLOWED
                {new Point(1, 5), ObjectInputFilter.Config.allowFilter(cl -> cl.getClassLoader() == ClassLoader.getPlatformClassLoader(), UNDECIDED),
                        REJECTED},   // Not platform loader is UNDECIDED -> a class that should not be undecided -> rejected
        };
    }


    @Test(dataProvider = "Examples")
    static void examples(Serializable obj, ObjectInputFilter filter, Status expected) {
        // Establish FilterInThread as the application-wide filter factory
        FilterInThread filterInThread;
        if (ObjectInputFilter.Config.getSerialFilterFactory() instanceof FilterInThread fit) {
            // Filter factory selected on the command line with -Djdk.serialFilterFactory=<classname>
            filterInThread = fit;
        } else {
            // Create a FilterInThread filter factory and set
            // An IllegalStateException will be thrown if the filter factory was already
            // initialized to an incompatible filter factory.
            filterInThread = new FilterInThread();
            ObjectInputFilter.Config.setSerialFilterFactory(filterInThread);
        }
        try {
            filterInThread.doWithSerialFilter(filter, () -> {
                byte[] bytes = writeObject(obj);
                Object o = deserializeObject(bytes);
            });
            if (expected.equals(REJECTED))
                Assert.fail("IllegalClassException should have occurred");
        } catch (UncheckedIOException uioe) {
            IOException ioe = uioe.getCause();
            Assert.assertEquals(ioe.getClass(), InvalidClassException.class, "Wrong exception");
            Assert.assertTrue(expected.equals(REJECTED), "Exception should not have occurred");
        }
    }

    /**
     * Test various filters with various objects and the resulting status
     * @param obj an object
     * @param filter a filter
     * @param expected status
     */
    @Test(dataProvider = "Examples")
    static void checkStatus(Serializable obj, ObjectInputFilter filter, Status expected) {
        // Establish FilterInThread as the application-wide filter factory
        FilterInThread filterInThread;
        if (ObjectInputFilter.Config.getSerialFilterFactory() instanceof FilterInThread fit) {
            // Filter factory selected on the command line with -Djdk.serialFilterFactory=<classname>
            filterInThread = fit;
        } else {
            // Create a FilterInThread filter factory and set
            // An IllegalStateException will be thrown if the filter factory was already
            // initialized to an incompatible filter factory.
            filterInThread = new FilterInThread();
            ObjectInputFilter.Config.setSerialFilterFactory(filterInThread);
        }

        try {
            filterInThread.doWithSerialFilter(filter, () -> {
                // Classes are serialized as themselves, otherwise pass the object's class
                Class<?> clazz = (obj instanceof Class<?>) ? (Class<?>)obj : obj.getClass();
                ObjectInputFilter.FilterInfo info = new SerialInfo(clazz);
                var compositeFilter = filterInThread.apply(null, ObjectInputFilter.Config.getSerialFilter());
                System.out.println("    filter in effect: " + filterInThread.currFilter);
                if (compositeFilter != null) {
                    Status actualStatus = compositeFilter.checkInput(info);
                    Assert.assertEquals(actualStatus, expected, "Wrong Status");
                }
            });

        } catch (Exception ex) {
            Assert.fail("unexpected exception", ex);
        }
    }

    /**
     * A Context-specific Deserialization Filter Factory to create filters that apply
     * a serial filter to all of the deserializations performed in a thread.
     * The purpose is to establish a deserialization filter that will reject all classes
     * that are not explicitly included.
     * <p>
     * The filter factory creates a composite filter of the stream-specific filter,
     * the thread-specific filter, the static JVM-wide filter, and a filter to reject all UNDECIDED cases.
     * The static JVM-wide filter is always included, if it is configured;
     * see ObjectInputFilter.Config.getSerialFilter().
     * <p>
     * To enable these protections the FilterInThread instance should be set as the
     * JVM-wide filter factory in ObjectInputFilter.Config.setSerialFilterFactory.
     *
     * The {@code doWithSerialFilter} is invoked with a serial filter and a lambda
     * to be invoked after the filter is applied.
     */
    public static final class FilterInThread
            implements BinaryOperator<ObjectInputFilter> {

        // ThreadLocal holding the Deque of serial filters to be applied, not null
        private final ThreadLocal<ArrayDeque<ObjectInputFilter>> filterThreadLocal =
                ThreadLocal.withInitial(() -> new ArrayDeque<>());

        private ObjectInputFilter currFilter;

        /**
         * Construct a FilterInThread deserialization filter factory.
         * The constructor is public so FilterInThread can be set on the command line
         * with {@code -Djdk.serialFilterFactory=SerialFactoryExample$FilterInThread}.
         */
        public FilterInThread() {
        }

        /**
         * Applies the filter to the thread and invokes the runnable.
         * The filter is pushed to a ThreadLocal, saving the old value.
         * If there was a previous thread filter, the new filter is appended
         * and made the active filter.
         * The runnable is invoked.
         * The previous filter is restored to the ThreadLocal.
         *
         * @param filter the serial filter to apply
         * @param runnable a runnable to invoke
         */
        public void doWithSerialFilter(ObjectInputFilter filter, Runnable runnable) {
            var prevFilters = filterThreadLocal.get();
            try {
                if (filter != null)
                    prevFilters.addLast(filter);
                runnable.run();
            } finally {
                if (filter != null) {
                    var lastFilter = prevFilters.removeLast();
                    assert lastFilter == filter : "Filter removed out of order";
                }
            }
        }

        /**
         * Returns a composite filter of the stream-specific filter, the thread-specific filter,
         * the static JVM-wide filter, and a filter to reject all UNDECIDED cases.
         * The purpose is to establish a deserialization filter that will reject all classes
         * that are not explicitly included.
         * The static JVM-wide filter is always checked, if it is configured;
         * see ObjectInputFilter.Config.getSerialFilter().
         * Any or all of the filters are optional and if not supplied or configured are null.
         * <p>
         * This method is first called from the constructor with current == null and
         * next == static JVM-wide filter.
         * The filter returned is the static JVM-wide filter merged with the thread-specific filter
         * and followed by a filter to map all UNDECIDED status values to REJECTED.
         * This last step ensures that the collective group of filters covers every possible case,
         * any classes that are not ALLOWED will be REJECTED.
         * <p>
         * The method mayy be called a second time from {@code ObjectInputStream.setObjectInputFilter(next)}
         * to add a stream-specific filter.  The stream-specific filter is prepended to the
         * composite filter created above when called from the constructor.
         * <p>
         *
         * @param curr the current filter, may be null
         * @param next the next filter, may be null
         * @return a deserialization filter to use for the stream, may be null
         */
        public ObjectInputFilter apply(ObjectInputFilter curr, ObjectInputFilter next) {
            if (curr == null) {
                // Called from the OIS constructor or perhaps OIS.setObjectInputFilter with no previous filter
                // no current filter, prepend next to threadFilter, both may be null or non-null

                // Assemble the filters in sequence, most recently added first
                var filters = filterThreadLocal.get();
                ObjectInputFilter filter = null;
                for (ObjectInputFilter f : filters) {
                    filter = f.merge(filter);
                }
                if (next != null) {
                    // Prepend a filter to assert that all classes have been Allowed or Rejected
                    if (filter != null) {
                        filter = filter.rejectUndecidedClass();
                    }

                    // Prepend the next filter to the thread filter, if any
                    // Initially this would be the static JVM-wide filter passed from the OIS constructor
                    // The static JVM-wide filter allow, reject, or leave classes undecided
                    filter = next.merge(filter);
                }
                // Check that the static JVM-wide filter did not leave any classes undecided
                if (filter != null) {
                    // Append the filter to reject all UNDECIDED results
                    filter = filter.rejectUndecidedClass();
                }
                // Return the filter, unless a stream-specific filter is set later
                // The filter may be null if no filters are configured
                currFilter = filter;
                return currFilter;
            } else {
                // Called from OIS.setObjectInputFilter with a previously set filter.
                // The curr filter already incorporates the thread filter and rejection of undecided status
                // Prepend the stream-specific filter or the current filter if no stream-specific filter
                currFilter = (next == null) ? curr : next.merge(curr).rejectUndecidedClass();
                return currFilter;
            }
        }
        public String toString() {
            return Objects.toString(currFilter, "none");
        }
    }


    /**
     * Simple example code from the ObjectInputFilter Class javadoc.
     */
    public static final class SimpleFilterInThread implements BinaryOperator<ObjectInputFilter> {

        private final ThreadLocal<ObjectInputFilter> filterThreadLocal = new InheritableThreadLocal<>();

        // Construct a FilterInThread deserialization filter factory.
        public SimpleFilterInThread() {}

        // Returns a composite filter of the static JVM-wide filter, a thread-specific filter,
        // and the stream-specific filter.
        public ObjectInputFilter apply(ObjectInputFilter curr, ObjectInputFilter next) {
            if (curr == null) {
                // Called from the OIS constructor or perhaps OIS.setObjectInputFilter with no previous filter
                var filter = filterThreadLocal.get();
                if (filter != null) {
                    // Prepend a filter to assert that all classes have been Allowed or Rejected
                    filter = filter.rejectUndecidedClass();
                }
                if (next != null) {
                    // Prepend the `next` filter to the thread filter, if any
                    // Initially this is the static JVM-wide filter passed from the OIS constructor
                    // Append the filter to reject all UNDECIDED results
                    filter = next.merge(filter).rejectUndecidedClass();
                }
                return filter;
            } else {
                // Called from OIS.setObjectInputFilter with a current filter and a stream-specific filter.
                // The curr filter already incorporates the thread filter and static JVM-wide filter
                // and rejection of undecided classes
                // Use the current filter or prepend the stream-specific filter and recheck for undecided
                return (next == null) ? curr : next.merge(curr).rejectUndecidedClass();
            }
        }

        // Applies the filter to the thread and invokes the runnable.
        public void doWithSerialFilter(ObjectInputFilter filter, Runnable runnable) {
            var prevFilter = filterThreadLocal.get();
            try {
                filterThreadLocal.set(filter);
                runnable.run();
            } finally {
                filterThreadLocal.set(prevFilter);
            }
        }
    }

    /**
     * Write an object and return a byte array with the bytes.
     *
     * @param object object to serialize
     * @return the byte array of the serialized object
     * @throws UncheckedIOException if an exception occurs
     */
    private static byte[] writeObject(Object object) {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
             ObjectOutputStream oos = new ObjectOutputStream(baos)) {
            oos.writeObject(object);
            return baos.toByteArray();
        } catch (IOException ioe) {
            throw new UncheckedIOException(ioe);
        }
    }

    /**
     * Deserialize an object.
     *
     * @param bytes an object.
     * @throws UncheckedIOException for I/O exceptions and ClassNotFoundException
     */
    private static Object deserializeObject(byte[] bytes) {
        try {
            InputStream is = new ByteArrayInputStream(bytes);
            ObjectInputStream ois = new ObjectInputStream(is);
            System.out.println("  filter in effect: " + ois.getObjectInputFilter());
            return ois.readObject();
        } catch (IOException ioe) {
            throw new UncheckedIOException(ioe);
        } catch (ClassNotFoundException cnfe) {
            throw new UncheckedIOException(new InvalidClassException(cnfe.getMessage()));
        }
    }


    /**
     * ObjectInputFilter utilities to create filters that combine the results of other filters.
     */
    public static final class Filters {
        /**
         * Returns a filter that allows a class if a predicate on the class returns true.
         * <p>
         * When used as an ObjectInputFilter by invoking the {@link ObjectInputFilter#checkInput} method,
         * the result is:
         * <ul>
         *     <li>{@link Status#ALLOWED}, if the predicate on the class returns {@code true}, </li>
         *     <li>Otherwise, return {@code otherStatus}</li>
         * </ul>
         * <p>
         * Example, to create a filter that will allow any class loaded from the platform classloader.
         * <pre><code>
         *     ObjectInputFilter f = allowFilter(cl -> cl.getClassLoader() == ClassLoader.getPlatformClassLoader());
         * </code></pre>
         *
         * @param predicate a predicate to map a class to a boolean
         * @param otherStatus a Status to use if the predicate is {@code false}
         * @return {@link Status#ALLOWED} if the predicate on the class returns true
         */
        public static ObjectInputFilter allowFilter(Predicate<Class<?>> predicate, Status otherStatus) {
            Objects.requireNonNull(predicate, "predicate");
            Objects.requireNonNull(otherStatus, "otherStatus");
            return new PredicateFilter(predicate, ALLOWED, otherStatus);
        }

        /**
         * Returns a filter that rejects a class if a predicate on the class returns true.
         * <p>
         * When used as an ObjectInputFilter by invoking the {@link ObjectInputFilter#checkInput} method,
         * the result is:
         * <ul>
         *     <li>{@link Status#REJECTED}, if the predicate on the class returns {@code true}, </li>
         *     <li>Otherwise, return {@code otherStatus}</li>
         * </ul>
         * <p>
         * Example, to create a filter that will reject any class loaded from the application classloader.
         * <pre><code>
         *     ObjectInputFilter f = rejectFilter(cl -> cl.getClassLoader() == ClassLoader.ClassLoader.getSystemClassLoader());
         * </code></pre>
         *
         * @param predicate a predicate to map a class to a boolean
         * @param otherStatus a Status to use if the predicate is {@code false}
         * @return {@link Status#REJECTED} if the predicate on the class returns true
         */
        public static ObjectInputFilter rejectFilter(Predicate<Class<?>> predicate, Status otherStatus) {
            Objects.requireNonNull(predicate, "predicate");
            Objects.requireNonNull(otherStatus, "otherStatus");
            return new PredicateFilter(predicate, REJECTED, otherStatus);
        }

        /**
         * Returns a filter that returns the result of combining a filter and another filter.
         * If the filter is null, the other filter is returned.
         * If the other filter is null, the filter is returned.
         * Otherwise, a new filter is returned wrapping the pair of non-null filters.
         * When used as an ObjectInputFilter by invoking the {@link ObjectInputFilter#checkInput} method,
         * the result is:
         * <ul>
         *     <li>{@link Status#REJECTED}, if either filter returns {@link Status#REJECTED}, </li>
         *     <li>Otherwise, {@link Status#ALLOWED}, if either filter returned {@link Status#ALLOWED}, </li>
         *     <li>Otherwise, return {@link Status#UNDECIDED}</li>
         * </ul>
         *
         * @param filter      a filter to invoke first, may be null
         * @param otherFilter another filter to be checked after this filter, may be null
         * @return an {@link ObjectInputFilter} that returns the result of combining this filter and another filter
         */
        public static ObjectInputFilter merge(ObjectInputFilter filter, ObjectInputFilter otherFilter) {
            if (filter == null)
                return otherFilter;
            return (otherFilter == null) ? filter :
                    new MergeFilter(filter, otherFilter);
        }

        /**
         * Returns a filter that merges the status of a list of filters.
         * <p>
         * When used as an ObjectInputFilter by invoking the {@link ObjectInputFilter#checkInput} method,
         * the result is:
         * <ul>
         *     <li>{@link Status#UNDECIDED}, if the serialClass is null,</li>
         *     <li>Otherwize, {@link Status#REJECTED}, if any filter returns {@link Status#REJECTED}, </li>
         *     <li>Otherwise, {@link Status#ALLOWED}, if any filter returns {@link Status#ALLOWED}, </li>
         *     <li>Otherwise, return {@code otherStatus}</li>
         * </ul>
         *
         * @param filters a List of filters evaluate
         * @param otherStatus the status to returned if none produce REJECTED or ALLOWED
         * @return an {@link ObjectInputFilter}
         */
        public static ObjectInputFilter mergeOrUndecided(List<ObjectInputFilter> filters,
                                                         Status otherStatus) {
            return new MergeManyFilter(filters, otherStatus);
        }

        /**
         * Returns a filter that returns the complement of the status of invoking the filter.
         * <p>
         * When used as an ObjectInputFilter by invoking the {@link ObjectInputFilter#checkInput} method,
         * the result is:
         * <ul>
         *     <li>{@link Status#REJECTED}, if this filter returns {@link Status#ALLOWED}, </li>
         *     <li>{@link Status#ALLOWED}, if this filter  {@link Status#REJECTED}, </li>
         *     <li>Otherwise, return {@link Status#UNDECIDED}</li>
         * </ul>
         *
         * @param filter a filter to wrap and complement its status
         * @return an {@link ObjectInputFilter}
         */
        public static ObjectInputFilter not(ObjectInputFilter filter) {
            return new NotFilter(Objects.requireNonNull(filter, "filter"));
        }

        /**
         * Returns a filter that returns REJECTED if the a filter returns UNDECIDED.
         * Object serialization accepts a class if the filter returns UNDECIDED or ALLOWED.
         * Appending a filter to reject undefined results for classes that have not been
         * either allowed or rejected can prevent classes from slipping through the filter.
         *
         * <p>
         * When used as an ObjectInputFilter by invoking the {@link ObjectInputFilter#checkInput} method,
         * the result is:
         * <ul>
         *     <li>{@link Status#ALLOWED}, if this filter  {@link Status#ALLOWED}, </li>
         *     <li>Otherwise, return {@link Status#REJECTED}</li>
         * </ul>
         *
         * @param filter a filter to map the UNDECIDED status to REJECTED
         * @return an {@link ObjectInputFilter} that maps an {@link Status#UNDECIDED}
         * status to {@link Status#REJECTED}
         */
        public static ObjectInputFilter rejectUndecided(ObjectInputFilter filter) {
            return new RejectUndecided(Objects.requireNonNull(filter, "filter"));
        }

        /**
         * Returns a filter that allows a class only if the class was loaded by the platform class loader.
         * Otherwise, it returns UNDECIDED; leaving the choice to another filter.
         * @return a filter that allows a class only if the class was loaded by the platform class loader
         */
        public static ObjectInputFilter allowPlatformClasses() {
            return new AllowPlatformClassFilter();
        }

        /**
         * An ObjectInputFilter to evaluate a predicate mapping a class to a boolean.
         */
        private static class PredicateFilter implements ObjectInputFilter {
            private final Predicate<Class<?>> predicate;
            private final Status ifTrueStatus;
            private final Status ifFalseStatus;

            PredicateFilter(Predicate<Class<?>> predicate, Status ifTrueStatus, Status ifFalseStatus) {
                this.predicate = predicate;
                this.ifTrueStatus = ifTrueStatus;
                this.ifFalseStatus = ifFalseStatus;
            }

            /**
             * Apply the predicate to the class being deserialized, if the class is non-null
             * and if it returns {@code true}, return the requested status. Otherwise, return UNDECIDED.
             *
             * @param info the FilterInfo
             * @return the status of applying the predicate, otherwise {@code UNDECIDED}
             */
            public ObjectInputFilter.Status checkInput(FilterInfo info) {
                return (info.serialClass() != null &&
                        predicate.test(info.serialClass())) ? ifTrueStatus : ifFalseStatus;
            }

            public String toString() {
                return "predicate(" + predicate + ")";
            }
        }

        /**
         * An ObjectInputFilter that merges the results of two filters.
         */
        private static class MergeFilter implements ObjectInputFilter {
            private final ObjectInputFilter first;
            private final ObjectInputFilter second;

            MergeFilter(ObjectInputFilter first, ObjectInputFilter second) {
                this.first = Objects.requireNonNull(first, "first");
                this.second = Objects.requireNonNull(second, "second");
            }

            /**
             * Returns REJECTED if either of the filters returns REJECTED,
             * and ALLOWED if any of the filters returns ALLOWED.
             * Returns UNDECIDED if there is no class to be checked or all filters return UNDECIDED.
             *
             * @param info the FilterInfo
             * @return Status.REJECTED if either of the filters returns REJECTED,
             * and ALLOWED if either filter returns ALLOWED; otherwise returns
             * UNDECIDED if there is no class to check or both filters returned UNDECIDED
             */
            public ObjectInputFilter.Status checkInput(FilterInfo info) {
                if (info.serialClass() == null) return UNDECIDED;
                Status firstStatus = Objects.requireNonNull(first.checkInput(info), "status");
                if (REJECTED.equals(firstStatus)) {
                    return REJECTED;
                }
                Status secondStatus = Objects.requireNonNull(second.checkInput(info), "other status");
                if (REJECTED.equals(secondStatus)) {
                    return REJECTED;
                }
                if (ALLOWED.equals(firstStatus) || ALLOWED.equals(secondStatus)) {
                    return ALLOWED;
                }
                return UNDECIDED;
            }

            @Override
            public String toString() {
                return "merge(" + first + ", " + second + ")";
            }
        }

        /**
         * An ObjectInputFilter that merges the results of two filters.
         */
        private static class MergeManyFilter implements ObjectInputFilter {
            private final List<ObjectInputFilter> filters;
            private final Status otherStatus;

            MergeManyFilter(List<ObjectInputFilter> first, Status otherStatus) {
                this.filters = Objects.requireNonNull(first, "filters");
                this.otherStatus = Objects.requireNonNull(otherStatus, "otherStatus");
            }

            /**
             * Returns REJECTED if any of the filters returns REJECTED,
             * and ALLOWED if any of the filters returns ALLOWED.
             * Returns UNDECIDED if there is no class to be checked or all filters return UNDECIDED.
             *
             * @param info the FilterInfo
             * @return Status.UNDECIDED if there is no class to check,
             *      Status.REJECTED if any of the filters returns REJECTED,
             *      Status.ALLOWED if any filter returns ALLOWED;
             *      otherwise returns {@code otherStatus}
             */
            public ObjectInputFilter.Status checkInput(FilterInfo info) {
                if (info.serialClass() == null)
                    return UNDECIDED;
                Status status = otherStatus;
                for (ObjectInputFilter filter : filters) {
                    Status aStatus = filter.checkInput(info);
                    if (REJECTED.equals(aStatus)) {
                        return REJECTED;
                    }
                    if (ALLOWED.equals(aStatus)) {
                        status = ALLOWED;
                    }
                }
                return status;
            }

            @Override
            public String toString() {
                return "mergeManyFilter(" + filters + ")";
            }
        }

        /**
         * An ObjectInputFilter that combines the results of two filters.
         */
        private static class NotFilter implements ObjectInputFilter {
            private final ObjectInputFilter other;

            NotFilter(ObjectInputFilter filter) {
                this.other = filter;
            }

            /**
             * Returns the complement of the result of the filter.
             * When used as an ObjectInputFilter by invoking the {@link ObjectInputFilter#checkInput} method,
             * the result is:
             * <ul>
             *     <li>REJECTED, if the other filter returns {@link Status#ALLOWED}, </li>
             *     <li>ALLOWED, if the other filter returns {@link Status#REJECTED}, </li>
             *     <li>Otherwise, return {@link Status#UNDECIDED}</li>
             * </ul>
             *
             * @param info the FilterInfo
             * @return
             */
            public ObjectInputFilter.Status checkInput(FilterInfo info) {
                Status status = Objects.requireNonNull(other.checkInput(info), "status");
                if (ALLOWED.equals(status)) return REJECTED;
                if (REJECTED.equals(status)) return ALLOWED;
                return UNDECIDED;
            }
        }

        /**
         * An ObjectInputFilter that rejects a class if the other filter returned UNDECIDED.
         */
        private static class RejectUndecided implements ObjectInputFilter {
            private final ObjectInputFilter filter;

            private RejectUndecided(ObjectInputFilter filter) {
                this.filter = Objects.requireNonNull(filter, "filter");
            }

            /**
             * Apply the filter and return the status if ALLOWED, otherwise REJECTED.
             * The effect is to map UNDECIDED to REJECTED, and otherwise return the status.
             *
             * @param info the FilterInfo
             * @return the status of applying the filter if ALLOWED, otherwise REJECTED
             */
            public ObjectInputFilter.Status checkInput(FilterInfo info) {
                Status status = Objects.requireNonNull(filter.checkInput(info), "status");
                return ALLOWED.equals(status) ? ObjectInputFilter.Status.ALLOWED : REJECTED;
            }

            public String toString() {
                return "rejectUndecided(" + filter.toString() + ")";
            }
        }

        /**
         * An ObjectInputFilter that allows a class only if the class was loaded by the platform class loader.
         * Otherwise, it returns undecided; leaving the choice to another filter.
         */
        private static class AllowPlatformClassFilter implements ObjectInputFilter {

            /**
             * Returns ALLOWED only if the class, if non-null, was loaded by the platformClassLoader.
             *
             * @param filter the FilterInfo
             * @return Status.ALLOWED only if the class loader of the class was the PlatformClassLoader;
             * otherwise Status.UNDECIDED
             */
            public ObjectInputFilter.Status checkInput(FilterInfo filter) {
                final Class<?> serialClass = filter.serialClass();
                return (serialClass != null &&
                        (serialClass.getClassLoader() == null ||
                        ClassLoader.getPlatformClassLoader().equals(serialClass.getClassLoader())))
                        ? ObjectInputFilter.Status.ALLOWED
                        : ObjectInputFilter.Status.UNDECIDED;
            }

            public String toString() {
                return "allowPlatformClasses";
            }
        }
    }

    /**
     * FilterInfo instance with a specific class.
     */
    static class SerialInfo implements ObjectInputFilter.FilterInfo {
        private final Class<?> clazz;

        SerialInfo(Class<?> clazz) {
            this.clazz = clazz;
        }

        @Override
        public Class<?> serialClass() {
            return clazz;
        }

        @Override
        public long arrayLength() {
            return 0;
        }

        @Override
        public long depth() {
            return 0;
        }

        @Override
        public long references() {
            return 0;
        }

        @Override
        public long streamBytes() {
            return 0;
        }
    }

    /**
     * A test class.
     */
    static record Point(int x, int y) implements Serializable {
    }
}
