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
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.io.UncheckedIOException;
import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.Predicate;

import static java.io.ObjectInputFilter.Status.*;

/* @test
 * @run testng  SerialFactoryExample
 * @run testng/othervm SerialFactoryExample
 * @run testng/othervm -Djdk.serialFilterFactory=SerialFactoryExample$FilterInThread  SerialFactoryExample
 * @summary Test SerialFactoryExample
 */

/*
 * Context-specific Deserialization Filter Example
 *
 * To protect deserialization of a thread or a call to an untrusted library function
 * a filter is set that applies to every deserialization within the thread.
 *
 * The `doWithSerialFilter` method arguments are a serial filter and
 * a lambda to invoke with the filter in force.  Its implementation saves the filter
 * in an InheritableThreadLocal, saving the previous value.  `InheritableThreadLocal`
 * is used instead of `ThreadLocal` so the filter applies to any threads created
 * from the initial thread.
 *
 * The FilterInThread filter factory is set as the JVM-wide dynamic file factory.
 * When the filter factory is invoked during the construction of each `ObjectInputStream`,
 * it retrieves the filter from the thread local and uses it to filter the stream.
 * When checking classes, if the accumulated filter results are undecided, the result
 * is biased toward reject to prevent gaps in the filters from allowing unknown classes to be deserialized.
 *
 * If more than one filter is to be applied to the stream, two filters can be composed
 * using `ObjectInputFilter.andThen`.  When invoked, each of the filters is invoked and the results are
 * combined such that if either filter rejects a class, the result is rejected.
 * If either filter allows the class, then it is allowed otherwise it is undecided.
 * Hierarchies and chains of filters can be built using the `ObjectInputFilter.andThen` filter.
 *
 * The example shows a filter that only allows classes loaded from the platform class loader.
 *
 * The `doWithSerialFilter` calls can be nested and as shown replace the filter.
 * An alternative is to append or concatenate the filters when calls are nested,
 * narrowing the list of acceptable classes.
 */
@Test
public class SerialFactoryExample {

    private static final Class<? extends Exception> NO_EXCEPTION = null;

    @DataProvider(name = "Examples")
    static Object[][] examples() {
        return new Object[][]{
                {new Point(1, 2), null,
                        NO_EXCEPTION},
                {new Point(1, 2), ObjectInputFilter.Config.createFilter("SerialFactoryExample$Point"),
                        NO_EXCEPTION},
                {10, Filters.allowPlatformClasses(),
                        NO_EXCEPTION},
                {new Point(1, 2), ObjectInputFilter.Config.createFilter("!SerialFactoryExample$Point"),
                        InvalidClassException.class},
                {new Point(1, 3), Filters.rejectUndecided(
                        Filters.allowPlatformClasses()
                            .andThen(Filters.allowFilter(cl -> cl == null))),   // class is null in all of the metrics checks
                        InvalidClassException.class},
                {new Point(1, 4), Filters.rejectUndecided(
                        Filters.allowFilter(cl -> cl.getClassLoader() == ClassLoader.getPlatformClassLoader())
                            .andThen(Filters.allowFilter(cl -> cl == null))),    // allow all of the metrics checks
                        InvalidClassException.class},
        };
    }

    @Test(dataProvider = "Examples")
    static void examples(Serializable obj, ObjectInputFilter filter, Class<?> exception) {
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
            Assert.assertNull(exception, "exception should have occurred: " + exception);
        } catch (UncheckedIOException uioe) {
            IOException ioe = uioe.getCause();
            Assert.assertEquals(ioe.getClass(), exception, "Wrong exception");
        }
    }

    /**
     * Write an object and return a byte array with the bytes.
     *
     * @param object object to serialize
     * @return the byte array of the serialized object
     * @throws UncheckedIOException if an exception occurs
     */
    static byte[] writeObject(Object object) {
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
    static Object deserializeObject(byte[] bytes) {
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
     * A Context-specific Deserialization Filter Factory to create filters that applies
     * a serial filter to all of the deserialization performed in a thread.
     *
     * The FilterInThread instance should be set as the JVM-wide filter factory
     * in ObjectInputFilter.Config.setSerialFilterFactory.
     *
     * The {@code doWithSerialFilter} is invoked with a serial filter and a lambda
     * to be invoked after the filter is applied.
     */
    public static final class FilterInThread
            implements BiFunction<ObjectInputFilter, ObjectInputFilter, ObjectInputFilter> {

        // ThreadLocal holding the serial filter to be applied, may be null
        private final ThreadLocal<ObjectInputFilter> filterThreadLocal = new InheritableThreadLocal<>();

        /**
         * Construct a FilterInThread deserialization filter factory.
         * The constructor is public so FilterInThread can be set on the command line
         * with {@code -Djdk.serialFilterFactory=SerialFactoryExample$FilterInThread}.
         */
        public FilterInThread() {
        }

        /**
         * Applies the filter to the thread and invokes the runnable.
         * The filter is pushed on a InheritedThreadLocal, saving the old value.
         * The runnable is invoked.
         * The previous filter is restored to the ThreadLocal.
         *
         * @param filter the serial filter to apply
         * @param runnable a runnable to invoke
         */
        public void doWithSerialFilter(ObjectInputFilter filter, Runnable runnable) {
            var prevFilter = filterThreadLocal.get();
            try {
                filterThreadLocal.set(filter);
                runnable.run();
            } finally {
                filterThreadLocal.set(prevFilter);
            }
        }

        /**
         * Returns a composite filter to be used for an ObjectInputStream.
         * First called from the constructor with current == null & next == null.
         * Maybe called later from {@code setObjectInputFilter(next)} to add a stream-specific filter.
         * <p>
         * If there is no thread filter, the stream-specific filter is returned.
         * If there is a thread filter, use it and then the stream-specific filter, if any.
         * <p>
         * In this algorithm, the current filter is either null, or the filter set in the
         * previous call which will be the threadFilter; the current filter is redundant and unused.
         *
         * @param curr the current filter, if any
         * @param next the next filter, if any
         * @return a deserialization filter to use for the stream
         */
        public ObjectInputFilter apply(ObjectInputFilter curr, ObjectInputFilter next) {
            var threadFilter = filterThreadLocal.get();
            if (threadFilter == null) {
                // There no thread filter so just use the stream-specific filter if any
                return next;
            }
            // Assert curr == null or is the threadFilter from the previous call
            return threadFilter         // establish the thread filter
                    .andThen(next);     // followed by the stream-specific filter if any
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
         *     <li>{@link ObjectInputFilter.Status#ALLOWED}, if the predicate on the class returns {@code true}, </li>
         *     <li>Otherwise, return {@link ObjectInputFilter.Status#UNDECIDED}</li>
         * </ul>
         * <p>
         * Example, to create a filter that will allow any class loaded from the platform classloader.
         * <pre><code>
         *     ObjectInputFilter f = allowFilter(cl -> cl.getClassLoader() == ClassLoader.getPlatformClassLoader());
         * </code></pre>
         *
         * @param predicate a predicate to map a class to a boolean
         * @return {@link ObjectInputFilter.Status#ALLOWED} if the predicate on the class returns true
         */
        public static ObjectInputFilter allowFilter(Predicate<Class<?>> predicate) {
            return new PredicateFilter(predicate, ALLOWED);
        }

        /**
         * Returns a filter that rejects a class if a predicate on the class returns true.
         * <p>
         * When used as an ObjectInputFilter by invoking the {@link ObjectInputFilter#checkInput} method,
         * the result is:
         * <ul>
         *     <li>{@link ObjectInputFilter.Status#REJECTED}, if the predicate on the class returns {@code true}, </li>
         *     <li>Otherwise, return {@link ObjectInputFilter.Status#UNDECIDED}</li>
         * </ul>
         * <p>
         * Example, to create a filter that will reject any class loaded from the application classloader.
         * <pre><code>
         *     ObjectInputFilter f = rejectFilter(cl -> cl.getClassLoader() == ClassLoader.ClassLoader.getSystemClassLoader());
         * </code></pre>
         *
         * @param predicate a predicate to map a class to a boolean
         * @return {@link ObjectInputFilter.Status#REJECTED} if the predicate on the class returns true
         */
        public static ObjectInputFilter rejectFilter(Predicate<Class<?>> predicate) {
            return new PredicateFilter(predicate, REJECTED);
        }

        /**
         * Returns a filter that returns the result of combining a filter and another filter.
         * If the filter is null, the other filter is returned.
         * If the other filter is null, the filter is returned.
         * Otherwise, a new filter is returned wrapping the pair of non-null filters.
         * When used as an ObjectInputFilter by invoking the {@link ObjectInputFilter#checkInput} method,
         * the result is:
         * <ul>
         *     <li>{@link ObjectInputFilter.Status#REJECTED}, if either filter returns {@link ObjectInputFilter.Status#REJECTED}, </li>
         *     <li>Otherwise, {@link ObjectInputFilter.Status#ALLOWED}, if either filter returned {@link ObjectInputFilter.Status#ALLOWED}, </li>
         *     <li>Otherwise, return {@link ObjectInputFilter.Status#UNDECIDED}</li>
         * </ul>
         *
         * @param filter      a filter to invoke first, may be null
         * @param otherFilter another filter to be checked after this filter, may be null
         * @return an {@link ObjectInputFilter} that returns the result of combining this filter and another filter
         */
        public static ObjectInputFilter andThen(ObjectInputFilter filter, ObjectInputFilter otherFilter) {
            if (filter == null)
                return otherFilter;
            return (otherFilter == null) ? filter :
                    new PairFilter(Objects.requireNonNull(filter, "filter"),
                            Objects.requireNonNull(otherFilter, "otherFilter"));
        }

        /**
         * Returns a filter that returns the complement of the status of invoking the filter.
         * <p>
         * When used as an ObjectInputFilter by invoking the {@link ObjectInputFilter#checkInput} method,
         * the result is:
         * <ul>
         *     <li>{@link ObjectInputFilter.Status#REJECTED}, if this filter returns {@link ObjectInputFilter.Status#ALLOWED}, </li>
         *     <li>{@link ObjectInputFilter.Status#ALLOWED}, if this filter  {@link ObjectInputFilter.Status#REJECTED}, </li>
         *     <li>Otherwise, return {@link ObjectInputFilter.Status#UNDECIDED}</li>
         * </ul>
         *
         * @param filter a filter to wrap and complement its status
         * @return an {@link ObjectInputFilter}
         */
        public static ObjectInputFilter not(ObjectInputFilter filter) {
            return new NotFilter(Objects.requireNonNull(filter, "filter"));
        }

        /**
         * Returns a filter that returns rejected if the a filter returns UNDECIDED.
         * Object serialization accepts a class if the filter returns UNDECIDED or ALLOWED.
         * Appending a filter to reject undefined results for classes that have not been
         * either allowed or rejected can prevent classes from slipping through the filter.
         *
         * <p>
         * When used as an ObjectInputFilter by invoking the {@link ObjectInputFilter#checkInput} method,
         * the result is:
         * <ul>
         *     <li>{@link ObjectInputFilter.Status#ALLOWED}, if this filter  {@link ObjectInputFilter.Status#ALLOWED}, </li>
         *     <li>Otherwise, return {@link ObjectInputFilter.Status#REJECTED}</li>
         * </ul>
         *
         * @param filter a filter to map the UNDECIDED status to REJECTED
         * @return an {@link ObjectInputFilter} that maps an {@link ObjectInputFilter.Status#UNDECIDED}
         * status to {@link ObjectInputFilter.Status#REJECTED}
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
         * An ObjectInputFilter to evaluate a predicate mapping a class to a boolean
         * and using the result to return ALLOWED, REJECTED, or UNDECIDED.
         */
        private static class PredicateFilter implements ObjectInputFilter {
            private final Predicate<Class<?>> predicate;
            private final Status ifTrueStatus;

            private PredicateFilter(Predicate<Class<?>> predicate, Status ifTrueStatus) {
                this.predicate = predicate;
                this.ifTrueStatus = ifTrueStatus;
            }

            /**
             * Apply the predicate to the Class and if it returns true, return the requested status.
             *
             * @param info the FilterInfo
             * @return the status of applying the predicate, otherwise UNDECIDED
             */
            public ObjectInputFilter.Status checkInput(FilterInfo info) {
                Class<?> cl = info.serialClass();
                return (cl != null && predicate.test(cl)) ? ifTrueStatus : UNDECIDED;
            }
        }

        /**
         * An ObjectInputFilter that combines the results of two filters.
         */
        private static class PairFilter implements ObjectInputFilter {
            private final ObjectInputFilter first;
            private final ObjectInputFilter second;

            PairFilter(ObjectInputFilter first, ObjectInputFilter second) {
                this.first = first;
                this.second = second;
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
                if (REJECTED.equals(firstStatus))
                    return REJECTED;
                Status secondStatus = Objects.requireNonNull(second.checkInput(info), "other status");
                if (REJECTED.equals(secondStatus))
                    return REJECTED;
                if (ALLOWED.equals(firstStatus) || ALLOWED.equals(secondStatus)) {
                    return ALLOWED;
                }
                return UNDECIDED;
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
             *     <li>REJECTED, if the other filter returns {@link ObjectInputFilter.Status#ALLOWED}, </li>
             *     <li>ALLOWED, if the other filter returns {@link ObjectInputFilter.Status#REJECTED}, </li>
             *     <li>Otherwise, return {@link ObjectInputFilter.Status#UNDECIDED}</li>
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
                return "reject undecided of (" + filter.toString() + ")";
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
                        ClassLoader.getPlatformClassLoader().equals(serialClass.getClassLoader()))
                        ? ObjectInputFilter.Status.ALLOWED
                        : ObjectInputFilter.Status.UNDECIDED;
            }

            public String toString() {
                return "platform classes allowed";
            }
        }
    }

    /**
     * A test class.
     */
    static record Point(int x, int y) implements Serializable {
    }

}
