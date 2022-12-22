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

package javadoc.tester;

import javax.tools.FileObject;
import javax.tools.JavaFileManager;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.lang.reflect.UndeclaredThrowableException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.BiFunction;
import java.util.function.Predicate;

/**
 * A builder to create "test file managers" that can return "test file objects".
 * All such objects can throw user-provided exceptions when specified methods
 * are called. This is done by registering "handlers" to be associated with individual
 * methods.
 *
 * The file objects that are returned as "test file objects" are filtered by a predicate
 * on the file object.
 *
 * Note that "test file objects" passed as arguments to methods on the "test file manager"
 * that created them are unwrapped, and replaced by the original file object.
 * This ensures that the underlying file manager sees the underlying file objects,
 * for cases when the identity of the file objects is important.
 * However, it does mean that methods on file objects called internally by a
 * file manager will not throw any user-provided exceptions.
 *
 * For now, the handlers for a file object are simply grouped by predicate and then by
 * method, and the group of methods used for a "test file object" is determined by the
 * first predicate that matches.
 * An alternative, more expensive, implementation would be to group the handlers
 * by method and predicate and then dynamically build the set of methods to be used for
 * a file object by filtering the methods by their applicable predicate.
 */
public class TestJavaFileManagerBuilder {
    private final StandardJavaFileManager fm;
    private Map<Method, BiFunction<JavaFileManager, Object[], Throwable>> fileManagerHandlers;

    private record FileObjectHandlers(Predicate<JavaFileObject> filter,
                                      Map<Method, BiFunction<JavaFileObject, Object[], Throwable>> handlers) { }
    private final List<FileObjectHandlers> fileObjectHandlers;

    public TestJavaFileManagerBuilder(StandardJavaFileManager fm) {
        this.fm = fm;
        fileManagerHandlers = Collections.emptyMap();
        fileObjectHandlers = new ArrayList<>();
    }

    /**
     * Provides functions to be called when given file manager methods are called.
     * The function should either return an exception to be thrown, or {@code null}
     * to indicate that no exception should be thrown.
     *
     * <p>It is an error for any function to return a checked exception that is not
     * declared by the method. This error will result in {@link UndeclaredThrowableException}
     * being thrown when the method is called.
     *
     * @param handlers a map giving the function to be called before a file manager method is invoked
     *
     * @return this object
     *
     * @throws IllegalArgumentException if any key in the map of handlers is a method that is not
     *                                  declared in {@code JavaFileManager}
     */
    public TestJavaFileManagerBuilder handle(Map<Method, BiFunction<JavaFileManager, Object[], Throwable>> handlers) {
        handlers.forEach((m, h) -> {
            if (!JavaFileManager.class.isAssignableFrom(m.getDeclaringClass())) {
                throw new IllegalArgumentException(("not a method on JavaFileManager: " + m));
            }
        });

        fileManagerHandlers = handlers;
        return this;
    }

    /**
     * Provides functions to be called when given file object methods are called,
     * for file objects that match a given predicate.
     * The function should either return an exception to be thrown, or {@code null}
     * to indicate that no exception should be thrown.
     *
     * <p>It is an error for the function to return a checked exception that is not
     * declared by the method. This error will result in {@link UndeclaredThrowableException}
     * being thrown when the method is called.
     *
     * <p>When subsequently finding the handlers to be used for a particular file object, the various
     * predicates passed to this method will be tested in the order that they were registered.
     * The handlers associated with the first matching predicate will be used.
     *
     * @apiNote Examples of predicates include:
     * <ul>
     * <li>using {@code .equals} or {@link JavaFileObject#isNameCompatible(String, JavaFileObject.Kind)}
     *     to match a specific file object,
     * <li>using string or regular expression operations on the name or URI of the file object,
     * <li>using {@code Path} operations on the file object's {@link StandardJavaFileManager#asPath(FileObject) path}.
     * </ul>
     *
     * @param filter   the predicate used to identify file objects for which the handlers are applicable
     * @param handlers a map giving the function to be called before a file object method is invoked
     *
     * @return this object
     *
     * @throws IllegalArgumentException if any key in the map is a method that is not declared in a class
     *                                  that is assignable to {@code FileObject}
     */
    public TestJavaFileManagerBuilder handle(Predicate<JavaFileObject> filter,
                                             Map<Method, BiFunction<JavaFileObject, Object[], Throwable>> handlers) {
        handlers.forEach((m, h) -> {
            if (!FileObject.class.isAssignableFrom(m.getDeclaringClass())) {
                throw new IllegalArgumentException(("not a method on FileObject: " + m));
            }
        });

        fileObjectHandlers.add(new FileObjectHandlers(filter, handlers));
        return this;
    }

    /**
     * {@return a file manager configured with the given handlers}
     */
    public StandardJavaFileManager build() {
        return (StandardJavaFileManager) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[] { StandardJavaFileManager.class },
                new JavaFileManager_InvocationHandler());
    }

    /**
     * An invocation handler for "test file managers", which provides "test file objects"
     * that may be configured to invoke functions to handle selected methods.
     */
    private class JavaFileManager_InvocationHandler implements InvocationHandler {
        // a cache of "real file object" -> "proxy file object".
        Map<JavaFileObject, JavaFileObject> cache = new WeakHashMap<>();

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            Object result = handleMethod(fm, method, unwrap(args));

            if (result instanceof Iterable iterable) {
                // All methods on StandardJavaFileManager that return Iterable<T> for some T
                // are such that T is one of ? extends [Java]FileObject, ? extends File, ? extends Path.
                // If the result is empty, return it unchanged; otherwise check the first
                // element to determine the type of the iterable, and if it is an iterable of
                // file objects, post-process the result to use proxy file objects where appropriate.
                // Note 1: this assumes that no methods return a mixture of FileObject and JavaFileObject.
                // Note 2: all file objects returned by the standard file manager are instances of javaFileObject
                Iterator<?> iter = iterable.iterator();
                if (iter.hasNext() && iter.next() instanceof JavaFileObject) {
                    List<JavaFileObject> list = new ArrayList<>();
                    for (JavaFileObject jfo : (Iterable<JavaFileObject>) iterable) {
                        list.add(wrap(jfo));
                    }
                    return list;
                } else {
                    return result;
                }
            } else if (result instanceof JavaFileObject jfo) {
                return wrap(jfo);
            } else {
                return result;
            }
        }

        /**
         * Returns a proxy file object that either calls handler functions for specific methods
         * or delegates to an underlying file object.
         *
         * @param jfo the underlying file object
         *
         * @return the proxy file object
         */
        private JavaFileObject wrap(JavaFileObject jfo) {
            return fileObjectHandlers.stream()
                    .filter(e -> e.filter().test(jfo))
                    .findFirst()
                    .map(e -> cache.computeIfAbsent(jfo, jfo_ -> createProxyFileObject(jfo_, e.handlers())))
                    .orElse(jfo);
        }

        /**
         * Creates a proxy file object that either calls handler functions for specific methods
         * or delegates to an underlying file object.
         *
         * @param jfo      the underlying file object
         * @param handlers the handlers
         *
         * @return the proxy file object
         */
        private JavaFileObject createProxyFileObject(JavaFileObject jfo,
                                                     Map<Method, BiFunction<JavaFileObject, Object[], Throwable>> handlers) {
            return (JavaFileObject) Proxy.newProxyInstance(getClass().getClassLoader(),
                    new Class<?>[] { JavaFileObject.class },
                    new JavaFileObject_InvocationHandler(jfo, handlers));
        }

        /**
         * {@return an array of objects with any proxy file objects replaced by their underlying
         * delegate value}
         *
         * If there are no proxy objects in the array, the original array is returned.
         *
         * @param args the array of values
         */
        private Object[] unwrap(Object[] args) {
            if (!containsProxyFileObject(args)) {
                return args;
            }

            Object[] uArgs = new Object[args.length];
            for (int i = 0; i < args.length; i++) {
                Object arg = args[i];
                uArgs[i] = (Proxy.isProxyClass(arg.getClass())
                        && Proxy.getInvocationHandler(arg) instanceof JavaFileObject_InvocationHandler ih)
                        ? ih.jfo
                        : arg;
            }
            return uArgs;
        }

        /**
         * {@return {@code true} if an array of objects contains any proxy file objects,
         *          and {@code false} otherwise}
         *
         * @param args the array of objects
         */
        private boolean containsProxyFileObject(Object[] args) {
            for (Object arg : args) {
                if (arg != null && Proxy.isProxyClass(arg.getClass())
                        && Proxy.getInvocationHandler(arg) instanceof JavaFileObject_InvocationHandler) {
                    return true;
                }
            }
            return false;
        }

        private Object handleMethod(JavaFileManager fm, Method method, Object[] args) throws Throwable {
            var handler = fileManagerHandlers.get(method);
            if (handler != null) {
                Throwable t = handler.apply(fm, args);
                if (t != null) {
                    throw t;
                }
            }

            return method.invoke(fm, args);
        }
    }

    /**
     * An invocation handler for "test file objects" which can be configured to call functions
     * to handle the calls for individual methods.
     * It is expected that a common use case is to throw an exception in circumstances that
     * would otherwise be hard to create.
     */
    private record JavaFileObject_InvocationHandler(JavaFileObject jfo,
                                                    Map<Method, BiFunction<JavaFileObject, Object[], Throwable>> handlers)
            implements InvocationHandler {

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            return handleMethod(jfo, method, args);
        }

        private Object handleMethod(JavaFileObject jfo, Method method, Object[] args) throws Throwable {
            var handler = handlers.get(method);
            if (handler != null) {
                Throwable t =  handler.apply(jfo, args);
                if (t != null) {
                    throw t;
                }
            }
            return method.invoke(jfo, args);
        }
    }
}
