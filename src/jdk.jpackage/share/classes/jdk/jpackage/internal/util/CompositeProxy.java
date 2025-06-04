/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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
package jdk.jpackage.internal.util;

import static java.util.stream.Collectors.toMap;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.BinaryOperator;
import java.util.function.Predicate;
import java.util.stream.Stream;

/**
 * Dynamic proxy dispatching method calls to multiple objects. It is aimed at
 * creating objects from mixins. The class provides functionality similar to
 * that of <code>net.sf.cglib.proxy.Mixin</code> class from the cglib library.
 *
 * Sample usage:
 * {@snippet :
 * interface Sailboat {
 *     default void trimSails() {
 *     }
 * }
 *
 * interface WithMain {
 *     void trimMain();
 * }
 *
 * interface WithJib {
 *     void trimJib();
 * }
 *
 * interface Sloop extends Sailboat, WithMain, WithJib {
 *     @Override
 *     public default void trimSails() {
 *         System.out.println("On the sloop:");
 *         trimMain();
 *         trimJib();
 *     }
 * }
 *
 * interface Catboat extends Sailboat, WithMain {
 *     @Override
 *     public default void trimSails() {
 *         System.out.println("On the catboat:");
 *         trimMain();
 *     }
 * }
 *
 * final var withMain = new WithMain() {
 *     @Override
 *     public void trimMain() {
 *         System.out.println("  trim the main");
 *     }
 * };
 *
 * final var withJib = new WithJib() {
 *     @Override
 *     public void trimJib() {
 *         System.out.println("  trim the jib");
 *     }
 * };
 *
 * Sloop sloop = CompositeProxy.create(Sloop.class, new Sailboat() {
 * }, withMain, withJib);
 *
 * Catboat catboat = CompositeProxy.create(Catboat.class, new Sailboat() {
 * }, withMain);
 *
 * sloop.trimSails();
 * catboat.trimSails();
 * }
 *
 * Output:
 *
 * <pre>
 * On the sloop:
 *   trim the main
 *   trim the jib
 * On the cat:
 *   trim the main
 * </pre>
 *
 * @see Proxy
 */
public final class CompositeProxy {

    /**
     * Builder of {@link CompositeProxy} instances.
     */
    public static final class Builder {

        /**
         * Returns a proxy instance for the specified interface that dispatches method
         * invocations to the specified handlers. Uses previously configured invocation
         * tunnel and conflict resolver objects with the created proxy object.
         *
         * @param <T>           the interface type
         * @param interfaceType the interface class composite proxy instance should
         *                      implement
         * @param slices        handlers for the method calls of the interface
         * @return a new instance of {@link Proxy} implementing the given interface and
         *         dispatching the interface method invocations to the given handlers
         */
        public <T> T create(Class<T> interfaceType, Object... slices) {
            return CompositeProxy.createCompositeProxy(interfaceType, conflictResolver, invokeTunnel, slices);
        }

        /**
         * Sets the method dispatch conflict resolver for this builder. The conflict
         * resolver is used by composite proxy to select a method call handler from
         * multiple candidates.
         *
         * @param v the conflict resolver for this builder or <code>null</code> if the
         *          default conflict resolver should be used
         * @return this
         */
        public Builder conflictResolver(BinaryOperator<Method> v) {
            conflictResolver = v;
            return this;
        }

        /**
         * Sets the invocation tunnel for this builder.
         *
         * @param v the invocation tunnel for this builder or <code>null</code> if no
         *          invocation tunnel should be used
         * @return this
         */
        public Builder invokeTunnel(InvokeTunnel v) {
            invokeTunnel = v;
            return this;
        }

        private Builder() {}

        private BinaryOperator<Method> conflictResolver = STANDARD_CONFLICT_RESOLVER;
        private InvokeTunnel invokeTunnel;
    }

    /**
     * Invocation tunnel. Must be used when building a composite proxy from objects
     * that implement package-private interfaces to prevent
     * {@link IllegalAccessException} exceptions being thrown by {@link Proxy}
     * instances. Must be implemented by classes from packages with package-private
     * interfaces used with {@link CompositeProxy} class.
     *
     * Assumed implementation:
     * {@snippet :
     *
     * package org.foo;
     *
     * import java.lang.reflect.InvocationHandler;
     * import java.lang.reflect.Method;
     * import jdk.jpackage.internal.util.CompositeProxy;
     *
     * final class CompositeProxyTunnel implements CompositeProxy.InvokeTunnel {
     *
     *     @Override
     *     public Object invoke(Object obj, Method method, Object[] args) throws Throwable {
     *         return method.invoke(obj, args);
     *     }
     *
     *     @Override
     *     public Object invokeDefault(Object proxy, Method method, Object[] args) throws Throwable {
     *         return InvocationHandler.invokeDefault(proxy, method, args);
     *     }
     *
     *     static final CompositeProxyTunnel INSTANCE = new CompositeProxyTunnel();
     * }
     */
    public interface InvokeTunnel {
        /**
         * Processes a method invocation on an object of composite proxy and returns the result.
         *
         * @implNote Implementation should call the given method on the given object
         *           with the given arguments and return the result of the call.
         * @param obj    the object on which to invoke the method
         * @param method the method to invoke
         * @param args   the arguments to use in the method call
         * @return the result of the method call
         * @throws Throwable if the method throws
         */
        Object invoke(Object obj, Method method, Object[] args) throws Throwable;

        /**
         * Processes a default interface method invocation on a composite proxy and
         * returns the result.
         *
         * @implNote Implementation should call
         *           {@link InvocationHandler#invokeDefault(Object, Method, Object...)}
         *           method on the given proxy object with the given arguments and
         *           return the result of the call.
         * @param proxy  the <code>proxy</code> parameter for
         *               {@link InvocationHandler#invokeDefault(Object, Method, Object...)}
         *               call
         * @param method the <code>method</code> parameter for
         *               {@link InvocationHandler#invokeDefault(Object, Method, Object...)}
         *               call
         * @param args   the <code>args</code> parameter for
         *               {@link InvocationHandler#invokeDefault(Object, Method, Object...)}
         *               call
         * @return the result of the
         *         {@link InvocationHandler#invokeDefault(Object, Method, Object...)}
         *         call
         * @throws Throwable if the {@link InvocationHandler#invokeDefault(Object, Method, Object...)} call throws
         */
        Object invokeDefault(Object proxy, Method method, Object[] args) throws Throwable;
    }

    /**
     * Creates a new proxy builder.
     * @return a new proxy builder
     */
    public static Builder build() {
        return new Builder();
    }

    /**
     * Shortcut for
     * <code>CompositeProxy.build().create(interfaceType, slices)</code>.
     *
     * @see Builder#create(Class, Object...)
     */
    public static <T> T create(Class<T> interfaceType, Object... slices) {
        return build().create(interfaceType, slices);
    }

    private CompositeProxy() {
    }

    private static <T> T createCompositeProxy(Class<T> interfaceType, BinaryOperator<Method> conflictResolver,
            InvokeTunnel invokeTunnel, Object... slices) {

        validateTypeIsInterface(interfaceType);

        final var interfaces = interfaceType.getInterfaces();
        List.of(interfaces).forEach(CompositeProxy::validateTypeIsInterface);

        if (interfaces.length != slices.length) {
            throw new IllegalArgumentException(
                    String.format("type %s must extend %d interfaces", interfaceType.getName(), slices.length));
        }

        final Map<Class<?>, Object> interfaceDispatch = createInterfaceDispatch(interfaces, slices);

        final Map<Method, Handler> methodDispatch = getProxyableMethods(interfaceType).map(method -> {
            var handler = createHandler(interfaceType, method, interfaceDispatch, conflictResolver, invokeTunnel);
            if (handler != null) {
                return Map.entry(method, handler);
            } else {
                return null;
            }
        }).filter(Objects::nonNull).collect(toMap(Map.Entry::getKey, Map.Entry::getValue));

        @SuppressWarnings("unchecked")
        T proxy = (T) Proxy.newProxyInstance(interfaceType.getClassLoader(), new Class<?>[] { interfaceType },
                new CompositeProxyInvocationHandler(methodDispatch));

        return proxy;
    }

    private static Map<Class<?>, Object> createInterfaceDispatch(Class<?>[] interfaces, Object[] slices) {

        final Map<Class<?>, Object> interfaceDispatch = Stream.of(interfaces).collect(toMap(x -> x, iface -> {
            return Stream.of(slices).filter(obj -> {
                return Set.of(obj.getClass().getInterfaces()).contains(iface);
            }).reduce((a, b) -> {
                throw new IllegalArgumentException(
                        String.format("both [%s] and [%s] slices implement %s", a, b, iface));
            }).orElseThrow(() -> createInterfaceNotImplementedException(List.of(iface)));
        }));

        if (interfaceDispatch.size() != interfaces.length) {
            final List<Class<?>> missingInterfaces = new ArrayList<>(Set.of(interfaces));
            missingInterfaces.removeAll(interfaceDispatch.keySet());
            throw createInterfaceNotImplementedException(missingInterfaces);
        }

        return Stream.of(interfaces).flatMap(iface -> {
            return unfoldInterface(iface).map(unfoldedIface -> {
                return Map.entry(unfoldedIface, interfaceDispatch.get(iface));
            });
        }).collect(toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    private static Stream<Class<?>> unfoldInterface(Class<?> interfaceType) {
        return Stream.concat(Stream.of(interfaceType),
                Stream.of(interfaceType.getInterfaces()).flatMap(CompositeProxy::unfoldInterface));
    }

    private static IllegalArgumentException createInterfaceNotImplementedException(
            Collection<Class<?>> missingInterfaces) {
        return new IllegalArgumentException(String.format("none of the slices implement %s", missingInterfaces));
    }

    private static void validateTypeIsInterface(Class<?> type) {
        if (!type.isInterface()) {
            throw new IllegalArgumentException(String.format("type %s must be an interface", type.getName()));
        }
    }

    private static Handler createHandler(Class<?> interfaceType, Method method, Map<Class<?>, Object> interfaceDispatch,
            BinaryOperator<Method> conflictResolver, InvokeTunnel invokeTunnel) {

        final var methodDeclaringClass = method.getDeclaringClass();

        if (!methodDeclaringClass.equals(interfaceType)) {
            // The method is declared in one of the superinterfaces.
            final var slice = interfaceDispatch.get(methodDeclaringClass);

            if (isInvokeDefault(method, slice)) {
                return createHandlerForDefaultMethod(method, invokeTunnel);
            } else {
                return createHandlerForMethod(slice, method, invokeTunnel);
            }
        } else if (method.isDefault()) {
            return createHandlerForDefaultMethod(method, invokeTunnel);
        } else {
            // Find a slice handling the method.
            var handler = interfaceDispatch.entrySet().stream().map(e -> {
                try {
                    Class<?> iface = e.getKey();
                    Object slice = e.getValue();
                    return createHandlerForMethod(slice, iface.getMethod(method.getName(), method.getParameterTypes()),
                            invokeTunnel);
                } catch (NoSuchMethodException ex) {
                    return null;
                }
            }).filter(Objects::nonNull).reduce(new ConflictResolverAdapter(conflictResolver)).orElseThrow(() -> {
                return new IllegalArgumentException(String.format("none of the slices can handle %s", method));
            });

            return handler;
        }
    }

    private static Stream<Method> getProxyableMethods(Class<?> interfaceType) {
        return Stream.of(interfaceType.getMethods()).filter(method -> !Modifier.isStatic(method.getModifiers()));
    }

    private static boolean isInvokeDefault(Method method, Object slice) {
        if (!method.isDefault()) {
            return false;
        }

        // The "method" is default.
        // See if is overridden by any non-abstract method in the "slice".
        // If it is, InvocationHandler.invokeDefault() should not be used to call it.

        final var sliceClass = slice.getClass();

        final var methodOverriden = Stream.of(sliceClass.getMethods()).filter(Predicate.not(Predicate.isEqual(method)))
                .filter(sliceMethod -> !Modifier.isAbstract(sliceMethod.getModifiers()))
                .anyMatch(sliceMethod -> signatureEquals(sliceMethod, method));

        return !methodOverriden;
    }

    private static boolean signatureEquals(Method a, Method b) {
        if (!Objects.equals(a.getName(), b.getName()) || !Arrays.equals(a.getParameterTypes(), b.getParameterTypes())) {
            return false;
        }

        return Objects.equals(a.getReturnType(), b.getReturnType());
    }

    private record CompositeProxyInvocationHandler(Map<Method, Handler> dispatch) implements InvocationHandler {

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            var handler = dispatch.get(method);
            if (handler != null) {
                return handler.invoke(proxy, args);
            } else if (method.isDefault()) {
                return InvocationHandler.invokeDefault(proxy, method, args);
            } else {
                handler = OBJECT_METHOD_DISPATCH.get(method);
                if (handler != null) {
                    return handler.invoke(proxy, args);
                } else {
                    throw new UnsupportedOperationException(String.format("No handler for %s", method));
                }
            }
        }

        private static String objectToString(Object obj) {
            return obj.getClass().getName() + '@' + Integer.toHexString(System.identityHashCode(obj));
        }

        private static boolean objectEquals(Object obj, Object other) {
            return obj == other;
        }

        private static Method getMethod(Class<?> type, String methodName, Class<?>...paramaterTypes) {
            try {
                return type.getDeclaredMethod(methodName, paramaterTypes);
            } catch (NoSuchMethodException|SecurityException ex) {
                throw new InternalError(ex);
            }
        }

        static class ObjectMethodHandler extends HandlerOfMethod {

            ObjectMethodHandler(Method method) {
                super(method);
            }

            @Override
            public Object invoke(Object proxy, Object[] args) throws Throwable {
                if (args == null) {
                    return method.invoke(null, proxy);
                } else {
                    final var newArgs = new Object[args.length + 1];
                    newArgs[0] = proxy;
                    System.arraycopy(args, 0, newArgs, 1, args.length);
                    return method.invoke(null, newArgs);
                }
            }
        }

        private static final Map<Method, Handler> OBJECT_METHOD_DISPATCH = Map.of(
                getMethod(Object.class, "toString"),
                new ObjectMethodHandler(getMethod(CompositeProxyInvocationHandler.class, "objectToString", Object.class)),
                getMethod(Object.class, "equals", Object.class),
                new ObjectMethodHandler(getMethod(CompositeProxyInvocationHandler.class, "objectEquals", Object.class, Object.class)),
                getMethod(Object.class, "hashCode"),
                new ObjectMethodHandler(getMethod(System.class, "identityHashCode", Object.class))
        );
    }

    private static HandlerOfMethod createHandlerForDefaultMethod(Method method, InvokeTunnel invokeTunnel) {
        if (invokeTunnel != null) {
            return new HandlerOfMethod(method) {
                @Override
                public Object invoke(Object proxy, Object[] args) throws Throwable {
                    return invokeTunnel.invokeDefault(proxy, this.method, args);
                }
            };
        } else {
            return null;
        }
    }

    private static HandlerOfMethod createHandlerForMethod(Object obj, Method method, InvokeTunnel invokeTunnel) {
        if (invokeTunnel != null) {
            return new HandlerOfMethod(method) {
                @Override
                public Object invoke(Object proxy, Object[] args) throws Throwable {
                    return invokeTunnel.invoke(obj, this.method, args);
                }
            };
        } else {
            return new HandlerOfMethod(method) {
                @Override
                public Object invoke(Object proxy, Object[] args) throws Throwable {
                    return this.method.invoke(obj, args);
                }
            };
        }
    }

    @FunctionalInterface
    private interface Handler {

        Object invoke(Object proxy, Object[] args) throws Throwable;
    }

    private abstract static class HandlerOfMethod implements Handler {
        HandlerOfMethod(Method method) {
            this.method = method;
        }

        protected final Method method;
    }

    private record ConflictResolverAdapter(BinaryOperator<Method> conflictResolver)
            implements BinaryOperator<HandlerOfMethod> {

        @Override
        public HandlerOfMethod apply(HandlerOfMethod a, HandlerOfMethod b) {
            var m = conflictResolver.apply(a.method, b.method);
            if (m == a.method) {
                return a;
            } else if (m == b.method) {
                return b;
            } else {
                throw new UnsupportedOperationException();
            }
        }
    }

    private static final BinaryOperator<Method> STANDARD_CONFLICT_RESOLVER = (a, b) -> {
        if (a.isDefault() == b.isDefault()) {
            throw new IllegalArgumentException(String.format("ambiguous choice between %s and %s", a, b));
        } else if (!a.isDefault()) {
            return a;
        } else {
            return b;
        }
    };
}
