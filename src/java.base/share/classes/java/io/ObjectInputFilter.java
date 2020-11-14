/*
 * Copyright (c) 2016, 2021, Oracle and/or its affiliates. All rights reserved.
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

package java.io;

import jdk.internal.access.SharedSecrets;
import jdk.internal.util.StaticProperty;

import java.lang.reflect.InvocationTargetException;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.security.Security;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Predicate;

import static java.io.ObjectInputFilter.Status.*;

/**
 * Filter classes, array lengths, and graph metrics during deserialization.
 *
 * <p><strong>Warning: Deserialization of untrusted data is inherently dangerous
 * and should be avoided. Untrusted data should be carefully validated according to the
 * "Serialization and Deserialization" section of the
 * {@extLink secure_coding_guidelines_javase Secure Coding Guidelines for Java SE}.
 * {@extLink serialization_filter_guide Serialization Filtering} describes best
 * practices for defensive use of serial filters.
 * </strong></p>
 *
 * <p>To protect the JVM against deserialization vulnerabilities, application developers
 * need a clear description of the objects that can be serialized or deserialized
 * by each component or library. For each context and use case, developers should
 * construct and apply an appropriate filter.
 *
 * <p>For simple cases, a static filter can be {@linkplain Config#setSerialFilter(ObjectInputFilter) set}
 * for the entire application.
 * For example, a filter that allows example classes, allows classes in the
 * {@code java.base} module, and rejects all other classes:
 *
 * <pre>{@code
 *     var filter = ObjectInputFilter.Config.createFilter("example.*;java.base/*;!*")
 *     ObjectInputFilter.Config.setSerialFilter(filter);
 * }</pre>
 *
 * <p>In an application with multiple execution contexts, the
 * {@linkplain Config#setSerialFilterFactory(BiFunction) filter factory} can better
 * protect individual contexts by providing a custom filter for each. When the stream
 * is constructed, the filter factory can identify the execution context based upon
 * the current thread-local state, hierarchy of callers, library, module, and class loader.
 * At that point, a policy for creating or selecting filters can choose a specific filter
 * or composition of filters based on the context.
 *
 * <p> When a filter is set on an {@link ObjectInputStream}, the {@link #checkInput checkInput(FilterInfo)}
 * method is called to validate classes, the length of each array,
 * the number of objects being read from the stream, the depth of the graph,
 * and the total number of bytes read from the stream.
 * The filter is invoked zero or more times
 * while {@linkplain ObjectInputStream#readObject() reading objects}.
 * The JVM-wide deserialization filter factory ensures that a deserialization filter can be set
 * on every {@link ObjectInputStream} and every object read from the stream can be checked.
 * <p>
 * The deserialization filter for a stream is determined in one of the following ways:
 * <ul>
 * <li>A JVM-wide filter factory can be set via {@link Config#setSerialFilterFactory(BiFunction)}
 *     or the system property {@code jdk.serialFilterFactory} or
 *     the security property {@code jdk.serialFilterFactory}.
 *     The filter factory is invoked for each new ObjectInputStream and
 *     when a filter is set for a stream.
 *     The filter factory determines the filter to be used for each stream based
 *     on its inputs, thread context, other filters, or state that is available.
 * <li>If a JVM-wide filter factory is not set, a builtin deserialization filter factory
 *     provides the {@link Config#getSerialFilter static JVM-wide filter} when invoked from the
 *     {@link ObjectInputStream#ObjectInputStream(InputStream) ObjectInputStream constructors}
 *     and replaces the static filter when invoked from
 *     {@link ObjectInputStream#setObjectInputFilter(ObjectInputFilter)}.
 *     See {@link Config#getSerialFilterFactory() getSerialFilterFactory}.
 * <li>A stream-specific filter can be set for an individual ObjectInputStream
 *     via {@link ObjectInputStream#setObjectInputFilter setObjectInputFilter}.
 *     Note that the filter may be used directly or combined with other filters by the
 *     {@linkplain Config#setSerialFilterFactory(BiFunction) JVM-wide filter factory}.
 * </ul>
 * <p>
 * A deserialization filter determines whether the arguments are allowed or rejected and
 * should return the appropriate status: {@link Status#ALLOWED ALLOWED} or {@link Status#REJECTED REJECTED}.
 * If the filter cannot determine the status it should return {@link Status#UNDECIDED UNDECIDED}.
 * Filters should be designed for the specific use case and expected types.
 * A filter designed for a particular use may be passed a class that is outside
 * of the scope of the filter. If the purpose of the filter is to reject classes
 * then it can reject a candidate class that matches and report {@code UNDECIDED} for others.
 * A filter may be called with class equals {@code null}, {@code arrayLength} equal -1,
 * the depth, number of references, and stream size and return a status
 * that reflects only one or only some of the values.
 * This allows a filter to specific about the choice it is reporting and
 * to use other filters without forcing either allowed or rejected status.
 *
 * <h2>Filter Model Examples</h2>
 * For simple applications, a single predefined filter listing allowed or rejected
 * classes may be sufficient to manage the risk of deserializing unexpected classes.
 * <p>For an application composed from multiple modules or libraries, the structure
 * of the application can be used to identify the classes to be allowed or rejected
 * by each {@linkplain ObjectInputStream} in each context of the application.
 * The JVM-wide deserialization filter factory is invoked when each stream is constructed and
 * can examine the thread or program to determine a context-specific filter to be applied.
 * Some possible examples:
 * <ul>
 *     <li>Thread-local state can hold the filter to be applied or composed
 *         with a stream-specific filter.
 *         Filters could be pushed and popped from a virtual stack of filters
 *         maintained by the application or libraries.
 *     <li>The filter factory can identify the caller of the deserialization method
 *         and use module or library context to select a filter or compose an appropriate
 *         context-specific filter.
 *         A mechanism could identify a callee with restricted or unrestricted
 *         access to serialized classes and choose a filter accordingly.
 * </ul>
 *
 * <p>
 * Typically, the stream-specific filter should check if a static JVM-wide filter
 * is configured and defer to it if so. For example,
 * <pre>{@code
 * ObjectInputFilter.Status checkInput(FilterInfo info) {
 *     ObjectInputFilter serialFilter = ObjectInputFilter.Config.getSerialFilter();
 *     if (serialFilter != null) {
 *         ObjectInputFilter.Status status = serialFilter.checkInput(info);
 *         if (status != ObjectInputFilter.Status.UNDECIDED) {
 *             // The JVM-wide filter overrides this filter
 *             return status;
 *         }
 *     }
 *     if (info.serialClass() instanceof java.rmi.Remote) {
 *         return Status.REJECTED;      // Do not allow Remote objects
 *     }
 *     return Status.UNDECIDED;
 * }
 *}</pre>
 * <p>
 * Unless otherwise noted, passing a {@code null} argument to a
 * method in this interface and its nested classes will cause a
 * {@link NullPointerException} to be thrown.
 *
 * @see ObjectInputStream#setObjectInputFilter(ObjectInputFilter)
 * @since 9
 */
@FunctionalInterface
public interface ObjectInputFilter {

    /**
     * Check the class, array length, number of object references, depth,
     * stream size, and other available filtering information.
     * Implementations of this method check the contents of the object graph being created
     * during deserialization. The filter returns {@link Status#ALLOWED Status.ALLOWED},
     * {@link Status#REJECTED Status.REJECTED}, or {@link Status#UNDECIDED Status.UNDECIDED}.
     *
     * @apiNote Each filter implementation of {@code checkInput} should return one of the values of {@link Status}.
     * Returning {@code null} may result in a {@link NullPointerException} or other unpredictable behavior.
     *
     * @param filterInfo provides information about the current object being deserialized,
     *             if any, and the status of the {@link ObjectInputStream}
     * @return  {@link Status#ALLOWED Status.ALLOWED} if accepted,
     *          {@link Status#REJECTED Status.REJECTED} if rejected,
     *          {@link Status#UNDECIDED Status.UNDECIDED} if undecided.
     */
    Status checkInput(FilterInfo filterInfo);

    /**
     * Returns a filter that combines the status of this filter and another filter.
     * If the other filter is {@code null}, this filter is returned.
     * Otherwise, a filter is returned wrapping the pair of {@code non-null} filters.
     * When used as an ObjectInputFilter by invoking the {@link ObjectInputFilter#checkInput} method,
     * the result is:
     * <ul>
     *     <li>{@link ObjectInputFilter.Status#REJECTED}, if either filter returns {@link ObjectInputFilter.Status#REJECTED}, </li>
     *     <li>Otherwise, {@link ObjectInputFilter.Status#ALLOWED}, if either filter returned {@link ObjectInputFilter.Status#ALLOWED}, </li>
     *     <li>Otherwise, return {@link ObjectInputFilter.Status#UNDECIDED}</li>
     * </ul>
     *
     * @param otherFilter another filter to be checked after this filter, may be null
     * @return an {@link ObjectInputFilter} that combines the status of this and another filter
     */
    default ObjectInputFilter andThen(ObjectInputFilter otherFilter) {
        return (otherFilter == null) ? ObjectInputFilter.this : new Config.PairFilter(this, otherFilter);
    }

    /**
     * FilterInfo provides access to information about the current object
     * being deserialized and the status of the {@link ObjectInputStream}.
     * @since 9
     */
    interface FilterInfo {
        /**
         * The class of an object being deserialized.
         * For arrays, it is the array type.
         * For example, the array class name of a 2 dimensional array of strings is
         * "{@code [[Ljava.lang.String;}".
         * To check the array's element type, iteratively use
         * {@link Class#getComponentType() Class.getComponentType} while the result
         * is an array and then check the class.
         * The {@code serialClass is null} in the case where a new object is not being
         * created and to give the filter a chance to check the depth, number of
         * references to existing objects, and the stream size.
         *
         * @return class of an object being deserialized; may be null
         */
        Class<?> serialClass();

        /**
         * The number of array elements when deserializing an array of the class.
         *
         * @return the non-negative number of array elements when deserializing
         * an array of the class, otherwise -1
         */
        long arrayLength();

        /**
         * The current depth.
         * The depth starts at {@code 1} and increases for each nested object and
         * decrements when each nested object returns.
         *
         * @return the current depth
         */
        long depth();

        /**
         * The current number of object references.
         *
         * @return the non-negative current number of object references
         */
        long references();

        /**
         * The current number of bytes consumed.
         * @implSpec  {@code streamBytes} is implementation specific
         * and may not be directly related to the object in the stream
         * that caused the callback.
         *
         * @return the non-negative current number of bytes consumed
         */
        long streamBytes();
    }

    /**
     * The status of a check on the class, array length, number of references,
     * depth, and stream size.
     *
     * @since 9
     */
    enum Status {
        /**
         * The status is undecided, not allowed and not rejected.
         */
        UNDECIDED,
        /**
         * The status is allowed.
         */
        ALLOWED,
        /**
         * The status is rejected.
         */
        REJECTED;
    }

    /**
     * A utility class to set and get the JVM-wide deserialization filter factory,
     * the static JVM-wide filter, or to create a filter from a pattern string.
     * If a JVM-wide filter factory or static JVM-wide filter is set, it will determine the filter
     * to be used for each {@link ObjectInputStream}, and be combined with a
     * stream-specific filter, if one is set.
     * <p>
     * When each {@link ObjectInputStream#ObjectInputStream() ObjectInputStream}
     * is created the {@linkplain Config#getSerialFilterFactory() filter factory}
     * is invoked to determine the initial filter for the stream. A stream-specific filter can be set with
     * {@link ObjectInputStream#setObjectInputFilter(ObjectInputFilter) ObjectInputStream.setObjectInputFilter}.
     * The {@linkplain Config#getSerialFilterFactory() JVM-wide filter factory} is also
     * invoked when a stream-specific filter is set to enable combining that filter with the initial filter.
     * <p>
     * Setting a {@linkplain #setSerialFilterFactory(BiFunction) deserialization filter factory}
     * allows the application provided factory to choose a filter for each stream when it is created
     * based on the context of the thread and call stack. It may simply return a static filter,
     * select a filter, compose a filter from the requested filter and any other filters including
     * the {@linkplain #getSerialFilter() JVM-wide filter}.
     * <p>
     * If a JVM-wide filter factory is {@linkplain Config#setSerialFilterFactory(BiFunction) not set}
     * the builtin deserialization filter factory returns the
     * {@link Config#getSerialFilter() static JVM-wide filter}.
     * <p>
     * When setting the filter, it should be stateless and idempotent,
     * reporting the same result when passed the same arguments.
     * <p>
     * The JVM-wide filter is configured during the initialization of the
     * {@code ObjectInputFilter.Config} class.
     * For example, by calling {@link #getSerialFilter() Config.getSerialFilter}.
     * If the Java virtual machine is started with the system property
     * {@systemProperty jdk.serialFilter}, its value is used to configure the filter.
     * If the system property is not defined, and the {@link java.security.Security} property
     * {@code jdk.serialFilter} is defined then it is used to configure the filter.
     * Otherwise, the filter is not configured during initialization and
     * can be set with {@link #setSerialFilter(ObjectInputFilter) Config.setSerialFilter}.
     * Setting the {@code jdk.serialFilter} with {@link System#setProperty(String, String)
     * System.setProperty} <em>does not set the filter</em>.
     * The syntax for the property value is the same as for the
     * {@link #createFilter(String) createFilter} method.
     * <p>
     * If the Java virtual machine is started with the system property
     * {@systemProperty jdk.serialFilterFactory}, its value names the class to configure the
     * JVM-wide deserialization filter factory.
     * If the system property is not defined, and the {@link java.security.Security} property
     * {@code jdk.serialFilterFactory} is defined then it is used to configure the filter factory.
     * The class must have a public zero-argument constructor, implement the
     * {@link BiFunction} interface,
     * and provide its implementation.
     * The filter factory configured using the system or security property during initialization
     * can NOT be replaced with {@link #setSerialFilterFactory(BiFunction) Config.setSerialFilterFactory}.
     * This ensures that a filter factory set on the command line is not overridden accidentally
     * or intentionally by the application.
     * Setting the {@code jdk.serialFilterFactory} with {@link System#setProperty(String, String)
     * System.setProperty} <em>does not set the filter factory</em>.
     * The syntax for the system property value and security property value is the
     * fully qualified class name of the deserialization filter factory.
     * @since 9
     */
    final class Config {
        /**
         * Lock object for JVM-wide filter and filter factory.
         */
        private final static Object serialFilterLock = new Object();

        /**
         * The property name for the JVM-wide filter.
         * Used as a system property and a java.security.Security property.
         */
        private final static String SERIAL_FILTER_PROPNAME = "jdk.serialFilter";

        /**
         * The property name for the JVM-wide filter factory.
         * Used as a system property and a java.security.Security property.
         */
        private final static String SERIAL_FILTER_FACTORY_PROPNAME = "jdk.serialFilterFactory";

        /**
         * Current static filter.
         */
        private static volatile ObjectInputFilter serialFilter;

        /**
         * Current serial filter factory.
         * @see Config#setSerialFilterFactory(BiFunction)
         */
        private static volatile BiFunction<ObjectInputFilter, ObjectInputFilter, ObjectInputFilter>
                serialFilterFactory;

        /**
         * Debug: Logger
         */
        private static final System.Logger configLog;

        static {
            /*
             * Initialize the configuration containing the filter factory, JVM-wide filter, and logger.
             * <ul>
             * <li>The property 'jdk.serialFilter" is read, either as a system property or a security property,
             *     and if set, defines the configured static JVM-wide filter and is logged.
             * <li>The property jdk.serialFilterFactory is read, either as a system property or a security property,
             *     and if set, defines the initial filter factory and is logged.
             * <li>If either property is defined, the logger is created.
             * </ul>
             */

            // Get the values of the system properties, if they are defined
            String factoryClassName = StaticProperty.jdkSerialFilterFactory();
            if (factoryClassName == null) {
                // Fallback to security property
                factoryClassName = AccessController.doPrivileged((PrivilegedAction<String>) () ->
                        Security.getProperty(SERIAL_FILTER_FACTORY_PROPNAME));
            }

            String filterString = StaticProperty.jdkSerialFilter();
            if (filterString == null) {
                // Fallback to security property
                filterString = AccessController.doPrivileged((PrivilegedAction<String>) () ->
                        Security.getProperty(SERIAL_FILTER_PROPNAME));
            }

            // Initialize the logger if either filter factory or filter property is set
            configLog = (filterString != null || factoryClassName != null)
                    ? System.getLogger("java.io.serialization") : null;

            // Initialize the JVM-wide filter if the jdk.serialFilter is present
            ObjectInputFilter filter = null;
            if (filterString != null) {
                configLog.log(System.Logger.Level.INFO,
                        "Creating deserialization filter from {0}", filterString);
                try {
                    filter = createFilter(filterString);
                } catch (RuntimeException re) {
                    configLog.log(System.Logger.Level.ERROR,
                            "Error configuring filter: {0}", re);
                }
            }
            serialFilter = filter;

            // Initialize the filter factory if the jdk.serialFilterFactory is defined
            // otherwise use the builtin filter factory.
            BiFunction<ObjectInputFilter, ObjectInputFilter, ObjectInputFilter> factory;
            if (factoryClassName != null) {
                configLog.log(System.Logger.Level.INFO,
                        "Creating deserialization filter factory for {0}", factoryClassName);
                try {
                    // Load using the system class loader, the named class may be an application class.
                    // The static initialization of the class or constructor may create a race
                    // if either calls Config.setSerialFilterFactory; the command line configured
                    // Class should not be overridden.
                    var factoryClass= Class.forName(factoryClassName, true,
                            ClassLoader.getSystemClassLoader());
                    @SuppressWarnings("unchecked")
                    var f = (BiFunction<ObjectInputFilter, ObjectInputFilter, ObjectInputFilter>)
                            factoryClass.getConstructor().newInstance(new Object[0]);
                    if (serialFilterFactory != null) {
                        configLog.log(System.Logger.Level.ERROR,
                                "FilterFactory provided on the command line can not be overridden");
                        // Do not continue if configuration not initialized
                        throw new ExceptionInInitializerError("FilterFactory provided on the command line can not be overridden");
                    }
                    factory = f;
                } catch (RuntimeException | ClassNotFoundException | NoSuchMethodException |
                        IllegalAccessException | InstantiationException | InvocationTargetException ex) {
                    configLog.log(System.Logger.Level.ERROR,
                            "Error configuring filter factory", ex);
                    // Do not continue if configuration not initialized
                    throw new ExceptionInInitializerError(ex);
                }
            } else {
                factory = new BuiltinFilterFactory();
            }
            serialFilterFactory = factory;

            // Setup shared secrets for RegistryImpl to use.
            SharedSecrets.setJavaObjectInputFilterAccess(Config::createFilter2);
        }

        /**
         * Config has no instances.
         */
        private Config() {
        }

        /**
         * Logger for debugging.
         */
        private static void filterLog(System.Logger.Level level, String msg, Object... args) {
            if (configLog != null) {
                configLog.log(level, msg, args);
            }
        }

        /**
         * Returns the static JVM-wide deserialization filter or {@code null} if not configured.
         *
         * @return the static JVM-wide deserialization filter or {@code null} if not configured
         */
        public static ObjectInputFilter getSerialFilter() {
            return serialFilter;
        }

        /**
         * Set the static JVM-wide filter if it has not already been configured or set.
         *
         * @param filter the deserialization filter to set as the JVM-wide filter; not null
         * @throws SecurityException if there is security manager and the
         *       {@code SerializablePermission("serialFilter")} is not granted
         * @throws IllegalStateException if the filter has already been set {@code non-null}
         */
        public static void setSerialFilter(ObjectInputFilter filter) {
            Objects.requireNonNull(filter, "filter");
            SecurityManager sm = System.getSecurityManager();
            if (sm != null) {
                sm.checkPermission(ObjectStreamConstants.SERIAL_FILTER_PERMISSION);
            }
            synchronized (serialFilterLock) {
                if (serialFilter != null) {
                    throw new IllegalStateException("Serial filter can only be set once");
                }
                serialFilter = filter;
            }
        }

        /**
         * Returns the JVM-wide deserialization filter factory.
         * If the filter factory has been {@link #setSerialFilterFactory(BiFunction) set} it is returned,
         * otherwise, a builtin deserialization filter factory is returned.
         * The filter factory provides a filter for every ObjectInputStream when invoked from
         * {@link ObjectInputStream#ObjectInputStream(InputStream) ObjectInputStream constructors}
         * and when a stream-specific filter is set with
         * {@link ObjectInputStream#setObjectInputFilter(ObjectInputFilter) setObjectInputFilter}.
         *
         * @implSpec
         * The builtin deserialization filter factory provides the
         * {@link #getSerialFilter static JVM-wide filter} when invoked from
         * {@link ObjectInputStream#ObjectInputStream(InputStream) ObjectInputStream constructors}.
         * When invoked {@link ObjectInputStream#setObjectInputFilter(ObjectInputFilter)
         * to set the stream-specific filter} the requested filter replaces the static JVM-wide filter,
         * unless it has already been set. The stream-specific filter can only be set once,
         * if it has already been set, an {@link IllegalStateException} is thrown.
         * The builtin deserialization filter factory implements the behavior of earlier versions of
         * setting the initial filter in the {@link ObjectInputStream} constructor and
         * {@link ObjectInputStream#setObjectInputFilter}.
         *
         * @return the JVM-wide deserialization filter factory; non-null
         * @since TBD
         */
        public static BiFunction<ObjectInputFilter, ObjectInputFilter, ObjectInputFilter> getSerialFilterFactory() {
            if (serialFilterFactory == null)
                throw new IllegalStateException("Serial filter factory initialization incomplete");
            return serialFilterFactory;
        }

        /**
         * Set the {@linkplain #getSerialFilterFactory() JVM-wide deserialization filter factory}.
         * The filter factory can be configured exactly once with one of:
         * setting the {@code jdk.serialFilterFactory} property on the command line,
         * setting the {@code jdk.serialFilterFactory} property in the {@link java.security.Security}
         * file, or using this {@code setSerialFilterFactory} method.
         *
         * <p>The JVM-wide filter factory is invoked when an ObjectInputStream
         * {@linkplain ObjectInputStream#ObjectInputStream() is constructed} and when the
         * {@linkplain ObjectInputStream#setObjectInputFilter(ObjectInputFilter) stream-specific filter is set}.
         * The parameters are the current filter and a requested filter and it
         * returns the filter to be used for the stream.
         * The current and new filter may each be {@code null} and the factory may return {@code null}.
         * The factory determines the filter to be used for {@code ObjectInputStream} streams based
         * on its inputs, and any other filters, context, or state that is available.
         * The factory may throw runtime exceptions to signal incorrect use or invalid parameters.
         * See the {@linkplain ObjectInputFilter filter models} for examples of composition and delegation.
         *
         * @param filterFactory the deserialization filter factory to set as the JVM-wide filter factory; not null
         * @throws IllegalStateException if the builtin deserialization filter factory has already been set once
         * @throws SecurityException if there is security manager and the
         *       {@code SerializablePermission("serialFilter")} is not granted
         * @since TBD
         */
        public static void setSerialFilterFactory(
                BiFunction<ObjectInputFilter, ObjectInputFilter, ObjectInputFilter> filterFactory) {
            Objects.requireNonNull(filterFactory, "filterFactory");
            SecurityManager sm = System.getSecurityManager();
            if (sm != null) {
                sm.checkPermission(ObjectStreamConstants.SERIAL_FILTER_PERMISSION);
            }
            if (serialFilterFactory instanceof BuiltinFilterFactory) {
                // The factory can be set only if it has been initialized to the builtin.
                serialFilterFactory = filterFactory;
                return;
            }
            // Either the serialFilterFactory has already been set by setSerialFilterFactory
            // or it is {@code null}, because the Config static initialization has not completed.
            // In either case, the serialFilterFactory can not be set.
            throw new IllegalStateException("Serial filter factory can not replace: " +
                    serialFilterFactory.getClass().getName());
        }

        /**
         * Returns an ObjectInputFilter from a string of patterns.
         * <p>
         * Patterns are separated by ";" (semicolon). Whitespace is significant and
         * is considered part of the pattern.
         * If a pattern includes an equals assignment, "{@code =}" it sets a limit.
         * If a limit appears more than once the last value is used.
         * <ul>
         *     <li>maxdepth={@code value} - the maximum depth of a graph</li>
         *     <li>maxrefs={@code value}  - the maximum number of internal references</li>
         *     <li>maxbytes={@code value} - the maximum number of bytes in the input stream</li>
         *     <li>maxarray={@code value} - the maximum array length allowed</li>
         * </ul>
         * <p>
         * Other patterns match or reject class or package name
         * as returned from {@link Class#getName() Class.getName()} and
         * if an optional module name is present
         * {@link Module#getName() class.getModule().getName()}.
         * Note that for arrays the element type is used in the pattern,
         * not the array type.
         * <ul>
         * <li>If the pattern starts with "!", the class is rejected if the remaining pattern is matched;
         *     otherwise the class is allowed if the pattern matches.
         * <li>If the pattern contains "/", the non-empty prefix up to the "/" is the module name;
         *     if the module name matches the module name of the class then
         *     the remaining pattern is matched with the class name.
         *     If there is no "/", the module name is not compared.
         * <li>If the pattern ends with ".**" it matches any class in the package and all subpackages.
         * <li>If the pattern ends with ".*" it matches any class in the package.
         * <li>If the pattern ends with "*", it matches any class with the pattern as a prefix.
         * <li>If the pattern is equal to the class name, it matches.
         * <li>Otherwise, the pattern is not matched.
         * </ul>
         * <p>
         * The resulting filter performs the limit checks and then
         * tries to match the class, if any. If any of the limits are exceeded,
         * the filter returns {@link Status#REJECTED Status.REJECTED}.
         * If the class is an array type, the class to be matched is the element type.
         * Arrays of any number of dimensions are treated the same as the element type.
         * For example, a pattern of "{@code !example.Foo}",
         * rejects creation of any instance or array of {@code example.Foo}.
         * The first pattern that matches, working from left to right, determines
         * the {@link Status#ALLOWED Status.ALLOWED}
         * or {@link Status#REJECTED Status.REJECTED} result.
         * If the limits are not exceeded and no pattern matches the class,
         * the result is {@link Status#UNDECIDED Status.UNDECIDED}.
         *
         * @param pattern the pattern string to parse; not null
         * @return a filter to check a class being deserialized;
         *          {@code null} if no patterns
         * @throws IllegalArgumentException if the pattern string is illegal or
         *         malformed and cannot be parsed.
         *         In particular, if any of the following is true:
         * <ul>
         * <li>   if a limit is missing the name or the name is not one of
         *        "maxdepth", "maxrefs", "maxbytes", or "maxarray"
         * <li>   if the value of the limit can not be parsed by
         *        {@link Long#parseLong Long.parseLong} or is negative
         * <li>   if the pattern contains "/" and the module name is missing
         *        or the remaining pattern is empty
         * <li>   if the package is missing for ".*" and ".**"
         * </ul>
         */
        public static ObjectInputFilter createFilter(String pattern) {
            Objects.requireNonNull(pattern, "pattern");
            return Global.createFilter(pattern, true);
        }

        /**
         * Returns an ObjectInputFilter from a string of patterns that
         * checks only the length for arrays, not the component type.
         *
         * @param pattern the pattern string to parse; not null
         * @return a filter to check a class being deserialized;
         *          {@code null} if no patterns
         */
        static ObjectInputFilter createFilter2(String pattern) {
            Objects.requireNonNull(pattern, "pattern");
            return Global.createFilter(pattern, false);
        }

        /**
         * Returns a filter that returns {@code Status.ALLOWED} if the predicate on the class is {@code true}.
         *
         * When the filter's {@link ObjectInputFilter#checkInput} method is invoked,
         * the predicate is applied to the {@link FilterInfo#serialClass()}.
         * Note that {@code serialClass} may be {@code null} and the predicate should be prepared
         * to handle {@code null}. The result is:
         * <ul>
         *     <li>{@link ObjectInputFilter.Status#ALLOWED ALLOWED}, if the predicate on the class returns {@code true},</li>
         *     <li>Otherwise, return {@link ObjectInputFilter.Status#UNDECIDED}</li>
         * </ul>
         * <p>
         * Example, to create a filter that will allow any class loaded from the platform classloader.
         * <pre><code>
         *     ObjectInputFilter f = allowFilter(cl -> cl.getClassLoader() == ClassLoader.getPlatformClassLoader());
         * </code></pre>
         *
         * @param predicate a predicate to test a Class
         * @return {@link ObjectInputFilter.Status#ALLOWED} if the predicate on the class returns {@code true}
         */
        public static ObjectInputFilter allowFilter(Predicate<Class<?>> predicate) {
            return new Config.PredicateFilter(predicate, ALLOWED);
        }

        /**
         * Returns a filter that returns {@code Status.REJECTED REJECTED} if the predicate on the class is {@code true}.
         *
         * When the filter's {@link ObjectInputFilter#checkInput} method is invoked,
         * the predicate is applied to the {@link FilterInfo#serialClass()}.
         * Note that {@code serialClass} may be {@code null} and the predicate should be prepared
         * to handle {@code null}. The result is:
         * <ul>
         *     <li>{@link ObjectInputFilter.Status#REJECTED}, if the predicate on the class returns {@code true},</li>
         *     <li>Otherwise, return {@link ObjectInputFilter.Status#UNDECIDED}</li>
         * </ul>
         * <p>
         * Example, to create a filter that will reject any class loaded from the application classloader.
         * <pre><code>
         *     ObjectInputFilter f = rejectFilter(cl -> cl.getClassLoader() == ClassLoader.ClassLoader.getSystemClassLoader());
         * </code></pre>
         *
         * @param predicate a predicate to test a Class
         * @return {@link ObjectInputFilter.Status#REJECTED} if the predicate on the class returns {@code true}
         */
        public static ObjectInputFilter rejectFilter(Predicate<Class<?>> predicate) {
            return new Config.PredicateFilter(predicate, REJECTED);
        }

        /**
         * Implementation of ObjectInputFilter that performs the checks of
         * the JVM-wide deserialization filter. If configured, it will be
         * used for all ObjectInputStreams that do not set their own filters.
         *
         */
        final static class Global implements ObjectInputFilter {
            /**
             * The pattern used to create the filter.
             */
            private final String pattern;
            /**
             * The list of class filters.
             */
            private final List<Function<Class<?>, Status>> filters;
            /**
             * Maximum allowed bytes in the stream.
             */
            private long maxStreamBytes;
            /**
             * Maximum depth of the graph allowed.
             */
            private long maxDepth;
            /**
             * Maximum number of references in a graph.
             */
            private long maxReferences;
            /**
             * Maximum length of any array.
             */
            private long maxArrayLength;
            /**
             * True to check the component type for arrays.
             */
            private final boolean checkComponentType;

            /**
             * Returns an ObjectInputFilter from a string of patterns.
             *
             * @param pattern the pattern string to parse
             * @param checkComponentType true if the filter should check
             *                           the component type of arrays
             * @return a filter to check a class being deserialized;
             *          {@code null} if no patterns
             * @throws IllegalArgumentException if the parameter is malformed
             *                if the pattern is missing the name, the long value
             *                is not a number or is negative.
             */
            static ObjectInputFilter createFilter(String pattern, boolean checkComponentType) {
                try {
                    return new Global(pattern, checkComponentType);
                } catch (UnsupportedOperationException uoe) {
                    // no non-empty patterns
                    return null;
                }
            }

            /**
             * Construct a new filter from the pattern String.
             *
             * @param pattern a pattern string of filters
             * @param checkComponentType true if the filter should check
             *                           the component type of arrays
             * @throws IllegalArgumentException if the pattern is malformed
             * @throws UnsupportedOperationException if there are no non-empty patterns
             */
            private Global(String pattern, boolean checkComponentType) {
                boolean hasLimits = false;
                this.pattern = pattern;
                this.checkComponentType = checkComponentType;

                maxArrayLength = Long.MAX_VALUE; // Default values are unlimited
                maxDepth = Long.MAX_VALUE;
                maxReferences = Long.MAX_VALUE;
                maxStreamBytes = Long.MAX_VALUE;

                String[] patterns = pattern.split(";");
                filters = new ArrayList<>(patterns.length);
                for (int i = 0; i < patterns.length; i++) {
                    String p = patterns[i];
                    int nameLen = p.length();
                    if (nameLen == 0) {
                        continue;
                    }
                    if (parseLimit(p)) {
                        // If the pattern contained a limit setting, i.e. type=value
                        hasLimits = true;
                        continue;
                    }
                    boolean negate = p.charAt(0) == '!';
                    int poffset = negate ? 1 : 0;

                    // isolate module name, if any
                    int slash = p.indexOf('/', poffset);
                    if (slash == poffset) {
                        throw new IllegalArgumentException("module name is missing in: \"" + pattern + "\"");
                    }
                    final String moduleName = (slash >= 0) ? p.substring(poffset, slash) : null;
                    poffset = (slash >= 0) ? slash + 1 : poffset;

                    final Function<Class<?>, Status> patternFilter;
                    if (p.endsWith("*")) {
                        // Wildcard cases
                        if (p.endsWith(".*")) {
                            // Pattern is a package name with a wildcard
                            final String pkg = p.substring(poffset, nameLen - 2);
                            if (pkg.isEmpty()) {
                                throw new IllegalArgumentException("package missing in: \"" + pattern + "\"");
                            }
                            if (negate) {
                                // A Function that fails if the class starts with the pattern, otherwise don't care
                                patternFilter = c -> matchesPackage(c, pkg) ? Status.REJECTED : Status.UNDECIDED;
                            } else {
                                // A Function that succeeds if the class starts with the pattern, otherwise don't care
                                patternFilter = c -> matchesPackage(c, pkg) ? Status.ALLOWED : Status.UNDECIDED;
                            }
                        } else if (p.endsWith(".**")) {
                            // Pattern is a package prefix with a double wildcard
                            final String pkgs = p.substring(poffset, nameLen - 2);
                            if (pkgs.length() < 2) {
                                throw new IllegalArgumentException("package missing in: \"" + pattern + "\"");
                            }
                            if (negate) {
                                // A Function that fails if the class starts with the pattern, otherwise don't care
                                patternFilter = c -> c.getName().startsWith(pkgs) ? Status.REJECTED : Status.UNDECIDED;
                            } else {
                                // A Function that succeeds if the class starts with the pattern, otherwise don't care
                                patternFilter = c -> c.getName().startsWith(pkgs) ? Status.ALLOWED : Status.UNDECIDED;
                            }
                        } else {
                            // Pattern is a classname (possibly empty) with a trailing wildcard
                            final String className = p.substring(poffset, nameLen - 1);
                            if (negate) {
                                // A Function that fails if the class starts with the pattern, otherwise don't care
                                patternFilter = c -> c.getName().startsWith(className) ? Status.REJECTED : Status.UNDECIDED;
                            } else {
                                // A Function that succeeds if the class starts with the pattern, otherwise don't care
                                patternFilter = c -> c.getName().startsWith(className) ? Status.ALLOWED : Status.UNDECIDED;
                            }
                        }
                    } else {
                        final String name = p.substring(poffset);
                        if (name.isEmpty()) {
                            throw new IllegalArgumentException("class or package missing in: \"" + pattern + "\"");
                        }
                        // Pattern is a class name
                        if (negate) {
                            // A Function that fails if the class equals the pattern, otherwise don't care
                            patternFilter = c -> c.getName().equals(name) ? Status.REJECTED : Status.UNDECIDED;
                        } else {
                            // A Function that succeeds if the class equals the pattern, otherwise don't care
                            patternFilter = c -> c.getName().equals(name) ? Status.ALLOWED : Status.UNDECIDED;
                        }
                    }
                    // If there is a moduleName, combine the module name check with the package/class check
                    if (moduleName == null) {
                        filters.add(patternFilter);
                    } else {
                        filters.add(c -> moduleName.equals(c.getModule().getName()) ? patternFilter.apply(c) : Status.UNDECIDED);
                    }
                }
                if (filters.isEmpty() && !hasLimits) {
                    throw new UnsupportedOperationException("no non-empty patterns");
                }
            }

            /**
             * Parse out a limit for one of maxarray, maxdepth, maxbytes, maxreferences.
             *
             * @param pattern a string with a type name, '=' and a value
             * @return {@code true} if a limit was parsed, else {@code false}
             * @throws IllegalArgumentException if the pattern is missing
             *                the name, the Long value is not a number or is negative.
             */
            private boolean parseLimit(String pattern) {
                int eqNdx = pattern.indexOf('=');
                if (eqNdx < 0) {
                    // not a limit pattern
                    return false;
                }
                String valueString = pattern.substring(eqNdx + 1);
                if (pattern.startsWith("maxdepth=")) {
                    maxDepth = parseValue(valueString);
                } else if (pattern.startsWith("maxarray=")) {
                    maxArrayLength = parseValue(valueString);
                } else if (pattern.startsWith("maxrefs=")) {
                    maxReferences = parseValue(valueString);
                } else if (pattern.startsWith("maxbytes=")) {
                    maxStreamBytes = parseValue(valueString);
                } else {
                    throw new IllegalArgumentException("unknown limit: " + pattern.substring(0, eqNdx));
                }
                return true;
            }

            /**
             * Parse the value of a limit and check that it is non-negative.
             * @param string inputstring
             * @return the parsed value
             * @throws IllegalArgumentException if parsing the value fails or the value is negative
             */
            private static long parseValue(String string) throws IllegalArgumentException {
                // Parse a Long from after the '=' to the end
                long value = Long.parseLong(string);
                if (value < 0) {
                    throw new IllegalArgumentException("negative limit: " + string);
                }
                return value;
            }

            /**
             * {@inheritDoc}
             */
            @Override
            public Status checkInput(FilterInfo filterInfo) {
                if (filterInfo.references() < 0
                        || filterInfo.depth() < 0
                        || filterInfo.streamBytes() < 0
                        || filterInfo.references() > maxReferences
                        || filterInfo.depth() > maxDepth
                        || filterInfo.streamBytes() > maxStreamBytes) {
                    return Status.REJECTED;
                }

                Class<?> clazz = filterInfo.serialClass();
                if (clazz != null) {
                    if (clazz.isArray()) {
                        if (filterInfo.arrayLength() >= 0 && filterInfo.arrayLength() > maxArrayLength) {
                            // array length is too big
                            return Status.REJECTED;
                        }
                        if (!checkComponentType) {
                            // As revised; do not check the component type for arrays
                            return Status.UNDECIDED;
                        }
                        do {
                            // Arrays are decided based on the component type
                            clazz = clazz.getComponentType();
                        } while (clazz.isArray());
                    }

                    if (clazz.isPrimitive())  {
                        // Primitive types are undecided; let someone else decide
                        return Status.UNDECIDED;
                    } else {
                        // Find any filter that allowed or rejected the class
                        final Class<?> cl = clazz;
                        Optional<Status> status = filters.stream()
                                .map(f -> f.apply(cl))
                                .filter(p -> p != Status.UNDECIDED)
                                .findFirst();
                        return status.orElse(Status.UNDECIDED);
                    }
                }
                return Status.UNDECIDED;
            }

            /**
             * Returns {@code true} if the class is in the package.
             *
             * @param c   a class
             * @param pkg a package name
             * @return {@code true} if the class is in the package,
             * otherwise {@code false}
             */
            private static boolean matchesPackage(Class<?> c, String pkg) {
                return pkg.equals(c.getPackageName());
            }

            /**
             * Returns the pattern used to create this filter.
             * @return the pattern used to create this filter
             */
            @Override
            public String toString() {
                return pattern;
            }
        }

        /**
         * An ObjectInputFilter to evaluate a predicate mapping a class to a boolean.
         */
        private static class PredicateFilter implements ObjectInputFilter {
            private final Predicate<Class<?>> predicate;
            private final Status ifTrueStatus;

            PredicateFilter(Predicate<Class<?>> predicate, Status ifTrueStatus) {
                this.predicate = predicate;
                this.ifTrueStatus = ifTrueStatus;
            }

            /**
             * Apply the predicate to the Class being deserialized and if it returns {@code true},
             * return the requested status.
             *
             * @param info the FilterInfo
             * @return the status of applying the predicate, otherwise {@code UNDECIDED}
             */
            public ObjectInputFilter.Status checkInput(FilterInfo info) {
                return (predicate.test(info.serialClass())) ? ifTrueStatus : UNDECIDED;
            }
        }

        /**
         * An ObjectInputFilter that combines the status of two filters.
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
             * and ALLOWED if either of the filters returns ALLOWED.
             * Returns {@code UNDECIDED} if there is no class to be checked or either filter
             * returns {@code UNDECIDED}.
             *
             * @param info the FilterInfo
             * @return Status.REJECTED if either of the filters returns REJECTED,
             * and ALLOWED if either filter returns ALLOWED; otherwise returns
             * {@code UNDECIDED} if there is no class to check or both filters returned {@code UNDECIDED}
             */
            public ObjectInputFilter.Status checkInput(FilterInfo info) {
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
         * Builtin Deserialization filter factory.
         * The builtin deserialization filter factory provides the
         * {@link #getSerialFilter static serial filter} when invoked from
         * {@link ObjectInputStream#ObjectInputStream(InputStream) ObjectInputStream constructors}.
         * When invoked from {@link ObjectInputStream#setObjectInputFilter(ObjectInputFilter)
         * to set the stream-specific filter} the requested filter replaces the static serial filter,
         * unless it has already been set. The stream-specific filter can only be set once,
         * if it has already been set, {@link IllegalStateException} is thrown.
         * The builtin deserialization filter factory implements the behavior of earlier versions of
         * setting the static serial filter in the {@link ObjectInputStream} constructor and
         * {@link ObjectInputStream#setObjectInputFilter}.
         *
         */
        private static final class BuiltinFilterFactory
                implements BiFunction<ObjectInputFilter, ObjectInputFilter, ObjectInputFilter> {
            /**
             * Returns the ObjectInputFilter to be used for an ObjectInputStream.
             * This method implements the builtin deserialization filter factory.
             * If the {@code oldFilter} and {@code newFilter} are null,
             *     the {@link Config#getSerialFilter()} is returned.
             * If the {@code oldFilter} is {@code null} and {@code newFilter} is {@code not null},
             *     the {@code newFilter} is returned.
             * If the {@code oldFilter} is equal to {@link Config#getSerialFilter},
             *     the {@code newFilter} is returned.
             * Otherwise {@code IllegalStateException} exception is thrown.
             *
             * <p>This is backward compatible behavior with earlier versions of
             * {@link ObjectInputStream#setObjectInputFilter},
             * and the initial filter in the {@link ObjectInputStream} constructor.
             *
             * @param oldFilter the current filter, may be null
             * @param newFilter a new filter, may be null
             * @return an ObjectInputFilter, the new Filter
             * @throws IllegalStateException if the {@linkplain ObjectInputStream#getObjectInputFilter() current filter}
             *       is not {@code null} and is not the JVM-wide filter
             * @throws SecurityException if there is security manager and the
             *       {@code SerializablePermission("serialFilter")} is not granted
             */@Override
            public ObjectInputFilter apply(ObjectInputFilter oldFilter, ObjectInputFilter newFilter) {
                if (oldFilter != null) {
                    // JEP 290 spec restricts setting the stream-specific filter more than once.
                    // Allow replacement of the JVM-wide filter but not replacement
                    // of a stream-specific filter that has been set.
                    if (oldFilter != getSerialFilter()) {
                        throw new IllegalStateException("filter can not be set more than once");
                    }
                } else if (newFilter == null) {
                    // Called from constructor, default to the configured filter, (may be null)
                    return Config.getSerialFilter();
                }
                return newFilter;
            }

            /**
             * Returns the class name name of this builtin deserialization filter factory.
             * @return returns the class name of this builtin deserialization filter factory
             */
            public String toString() {
                return this.getClass().getName();
            }
        }
    }
}
