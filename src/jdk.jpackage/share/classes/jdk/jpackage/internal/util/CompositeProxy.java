/*
 * Copyright (c) 2025, 2026, Oracle and/or its affiliates. All rights reserved.
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
import static java.util.stream.Collectors.toSet;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Collectors;
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
 * Sloop sloop = CompositeProxy.create(Sloop.class, withMain, withJib);
 *
 * Catboat catboat = CompositeProxy.create(Catboat.class, withMain);
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
            return CompositeProxy.createCompositeProxy(
                    interfaceType,
                    Optional.ofNullable(methodConflictResolver).orElse(JPACKAGE_METHOD_CONFLICT_RESOLVER),
                    Optional.ofNullable(objectConflictResolver).orElse(JPACKAGE_OBJECT_CONFLICT_RESOLVER),
                    invokeTunnel,
                    allowUnreferencedSlices,
                    slices);
        }

        /**
         * Sets the method dispatch conflict resolver for this builder. The conflict
         * resolver is used by composite proxy to select a method call handler from
         * several candidates.
         *
         * @param v the method conflict resolver for this builder or <code>null</code>
         *          if the default conflict resolver should be used
         * @return this
         */
        public Builder methodConflictResolver(MethodConflictResolver v) {
            methodConflictResolver = v;
            return this;
        }

        /**
         * Sets the object dispatch conflict resolver for this builder. The conflict
         * resolver is used by the composite proxy to select an object from several
         * candidates.
         *
         * @param v the object conflict resolver for this builder or <code>null</code>
         *          if the default conflict resolver should be used
         * @return this
         */
        public Builder objectConflictResolver(ObjectConflictResolver v) {
            objectConflictResolver = v;
            return this;
        }

        /**
         * Configures if this builder allows unreferenced slices in the
         * {@link #create(Class, Object...)}.
         * <p>
         * By default, if the builder happens to create such a composite proxy that one
         * or more slices passed in the {@link #create(Class, Object...)} method happen
         * to be unreferenced, it will throw {@code IllegalArgumentException}. Passing
         * <code>true</code> disables this throw cause.
         *
         * @param v <code>true</code> to disable throwing of
         *          {@code IllegalArgumentException} from
         *          {@link #create(Class, Object...)} if some of the passed in slices
         *          happen to be unreferenced and <code>false</code> otherwise
         * @return this
         */
        public Builder allowUnreferencedSlices(boolean v) {
            allowUnreferencedSlices = v;
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

        private MethodConflictResolver methodConflictResolver;
        private ObjectConflictResolver objectConflictResolver;
        private InvokeTunnel invokeTunnel;
        private boolean allowUnreferencedSlices;
    }

    /**
     * Method conflict resolver. Used when the composite proxy needs to decide if
     * the default method of the interface it implements should be overridden by an
     * implementing object.
     */
    @FunctionalInterface
    public interface MethodConflictResolver {

        /**
         * Returns {@code true} if the composite proxy should override the default
         * method {@code method} in {@code interfaceType} type with the corresponding
         * method form the {@code obj}.
         *
         * @param interfaceType the interface type composite proxy instance should
         *                      implement
         * @param slices        all objects passed to the calling composite proxy. The
         *                      value is a copy of the last parameter passed in the
         *                      {@link Builder#create(Class, Object...)}
         * @param method        default method in {@code interfaceType} type
         * @param obj           object providing a usable method with the same signature
         *                      (the name and parameter types) as the signature of the
         *                      {@code method} method
         */
        boolean isOverrideDefault(Class<?> interfaceType, Object[] slices, Method method, Object obj);
    }

    /**
     * Object conflict resolver. Used when several objects have methods that are
     * candidates to implement some method in an interface and the composite proxy
     * needs to choose one of these objects.
     */
    @FunctionalInterface
    public interface ObjectConflictResolver {

        /**
         * Returns the object that should be used in a composite proxy to implement
         * abstract method {@code method}.
         *
         * @param interfaceType the interface type composite proxy instance should
         *                      implement
         * @param slices        all objects passed to the calling composite proxy. The
         *                      value is a copy of the last parameter passed in the
         *                      {@link Builder#create(Class, Object...)}
         * @param method        abstract method
         * @param candidates    objects with a method with the same signature (the name
         *                      and parameter types) as the signature of the
         *                      {@code method} method. The array is unordered, doesn't
         *                      contain duplicates, and is a subset of the
         *                      {@code slices} array
         * @return either one of items from the {@code candidates} or {@code null} if
         *         can't choose one
         */
        Object choose(Class<?> interfaceType, Object[] slices, Method method, Object[] candidates);
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

    private static <T> T createCompositeProxy(
            Class<T> interfaceType,
            MethodConflictResolver methodConflictResolver,
            ObjectConflictResolver objectConflictResolver,
            InvokeTunnel invokeTunnel,
            boolean allowUnreferencedSlices,
            Object... slices) {

        Objects.requireNonNull(interfaceType);
        Objects.requireNonNull(methodConflictResolver);
        Objects.requireNonNull(objectConflictResolver);
        Stream.of(slices).forEach(Objects::requireNonNull);

        if (!interfaceType.isInterface()) {
            throw new IllegalArgumentException(String.format("Type %s must be an interface", interfaceType.getName()));
        }

        final var uniqueSlices = Stream.of(slices).map(IdentityWrapper::new).collect(toSet());

        final var unreferencedSlicesBuilder = SetBuilder.<IdentityWrapper<Object>>build().emptyAllowed(true);

        if (!allowUnreferencedSlices) {
            unreferencedSlicesBuilder.add(uniqueSlices);
        }

        final Map<Method, Handler> methodDispatch = getProxyableMethods(interfaceType).map(method -> {
            return Map.entry(method, uniqueSlices.stream().flatMap(slice -> {
                var sliceMethods = getImplementerMethods(slice.value()).filter(sliceMethod -> {
                    return signatureEquals(sliceMethod, method);
                }).toList();

                if (sliceMethods.size() > 1) {
                    throw new AssertionError();
                }

                return sliceMethods.stream().findFirst().map(sliceMethod -> {
                    return Map.entry(slice, sliceMethod);
                }).stream();
            }).toList());
        }).flatMap(e -> {
            final Method method = e.getKey();
            final List<Map.Entry<IdentityWrapper<Object>, Method>> slicesWithMethods = e.getValue();

            final Map.Entry<IdentityWrapper<Object>, Method> sliceWithMethods;
            switch (slicesWithMethods.size()) {
                case 0 -> {
                    if (!method.isDefault()) {
                        throw new IllegalArgumentException(String.format("None of the slices can handle %s", method));
                    } else {
                        return Optional.ofNullable(createHandlerForDefaultMethod(method, invokeTunnel)).map(handler -> {
                            return Map.entry(method, handler);
                        }).stream();
                    }
                }
                case 1 -> {
                    sliceWithMethods = slicesWithMethods.getFirst();
                }
                default -> {
                    var candidates = slicesWithMethods.stream().map(sliceEntry -> {
                        return sliceEntry.getKey().value();
                    }).toList();

                    var candidate = objectConflictResolver.choose(
                            interfaceType, Arrays.copyOf(slices, slices.length), method, candidates.toArray());
                    if (candidate == null) {
                        throw new IllegalArgumentException(String.format(
                                "Ambiguous choice between %s for %s", candidates, method));
                    }

                    var candidateIdentity = IdentityWrapper.wrapIdentity(candidate);

                    if (candidates.stream().map(IdentityWrapper::new).noneMatch(Predicate.isEqual(candidateIdentity))) {
                        throw new UnsupportedOperationException();
                    }

                    sliceWithMethods = slicesWithMethods.stream().filter(v -> {
                        return candidateIdentity.equals(v.getKey());
                    }).findFirst().orElseThrow();
                }
            }

            final var slice = sliceWithMethods.getKey().value();
            final var sliceMethod = sliceWithMethods.getValue();
            final Handler handler;
            if (!method.isDefault()
                    || (method.equals(sliceMethod)
                            && getUnfilteredImplementerMethods(slice)
                                    .map(FullMethodSignature::new)
                                    .anyMatch(Predicate.isEqual(new FullMethodSignature(sliceMethod))))
                    || (       method.getReturnType().equals(sliceMethod.getReturnType())
                            && !sliceMethod.isDefault()
                            && methodConflictResolver.isOverrideDefault(interfaceType, Arrays.copyOf(slices, slices.length), method, slice))) {
                // Use implementation from the slice if one of the statements is "true":
                // - The target method is abstract (not default)
                // - The target method is default and it is the same method in the slice which overrides it.
                //   This is a special case when default method must not be invoked via InvocationHandler.invokeDefault().
                // - The target method is default and the matching slice method has the same return type,
                //   is not default, and the method conflict resolver approves the use of the slice method
                if (!allowUnreferencedSlices) {
                    unreferencedSlicesBuilder.remove(sliceWithMethods.getKey());
                }
                handler = createHandlerForMethod(slice, sliceMethod, invokeTunnel);
            } else {
                handler = createHandlerForDefaultMethod(method, invokeTunnel);
            }

            return Optional.ofNullable(handler).map(h -> {
                return Map.entry(method, h);
            }).stream();

        }).collect(toMap(Map.Entry::getKey, Map.Entry::getValue));

        if (!allowUnreferencedSlices) {
            var unreferencedSlices = unreferencedSlicesBuilder.create().stream().map(IdentityWrapper::value).toList();
            if (!unreferencedSlices.isEmpty()) {
                throw new IllegalArgumentException(String.format("Unreferenced slices: %s", unreferencedSlices));
            }
        }

        @SuppressWarnings("unchecked")
        T proxy = (T) Proxy.newProxyInstance(interfaceType.getClassLoader(), new Class<?>[] { interfaceType },
                new CompositeProxyInvocationHandler(Collections.unmodifiableMap(methodDispatch)));

        return proxy;
    }

    private static Stream<Class<?>> unfoldInterface(Class<?> interfaceType) {
        return Stream.concat(
                Stream.of(interfaceType),
                Stream.of(interfaceType.getInterfaces()
        ).flatMap(CompositeProxy::unfoldInterface));
    }

    private static List<Class<?>> getSuperclasses(Class<?> type) {
        List<Class<?>> superclasses = new ArrayList<>();

        var current = type.getSuperclass();

        while (current != null) {
            superclasses.add(current);
            current = current.getSuperclass();
        }

        return superclasses;
    }

    private static Stream<Method> getUnfilteredProxyableMethods(Class<?> interfaceType) {
        return unfoldInterface(interfaceType).flatMap(type -> {
            return Stream.of(type.getMethods());
        }).filter(method -> {
            return     !Modifier.isStatic(method.getModifiers())
                    && !method.isBridge();
        });
    }

    private static Stream<Method> getProxyableMethods(Class<?> interfaceType) {
        return removeRedundancy(getUnfilteredProxyableMethods(interfaceType));
    }

    private static Stream<Method> getUnfilteredImplementerMethods(Object slice) {
        var sliceType = slice.getClass();

        return Stream.of(
                Stream.of(sliceType),
                getSuperclasses(sliceType).stream()
        ).flatMap(x -> x).flatMap(type -> {
            return Stream.of(type.getMethods());
        }).filter(method -> {
            return     !Modifier.isStatic(method.getModifiers())
                    && !method.isBridge()
                    && !method.isDefault()
                    && !Modifier.isPrivate(method.getModifiers());
        });
    }

    private static Stream<Method> getImplementerMethods(Object slice) {
        var sliceType = slice.getClass();

        var proxyableMethods = Stream.of(
                Stream.of(sliceType),
                getSuperclasses(sliceType).stream()
        ).flatMap(x -> x)
                .map(Class::getInterfaces)
                .flatMap(Stream::of)
                .flatMap(CompositeProxy::unfoldInterface)
                .flatMap(CompositeProxy::getUnfilteredProxyableMethods)
                .toList();

        var proxyableMethodSignatures = proxyableMethods.stream()
                .map(FullMethodSignature::new)
                .collect(toSet());

        var methods = getUnfilteredImplementerMethods(slice).filter(method -> {
            return !proxyableMethodSignatures.contains(new FullMethodSignature(method));
        });

        return removeRedundancy(Stream.concat(methods, proxyableMethods.stream()));
    }

    private static Stream<Method> removeRedundancy(Stream<Method> methods) {
        var groups = methods.distinct().collect(Collectors.groupingBy(MethodSignature::new)).values();
        return groups.stream().map(group -> {
            // All but a single method should be filtered out from the group.
            return group.stream().reduce((a, b) -> {
                var ac = a.getDeclaringClass();
                var bc = b.getDeclaringClass();
                if (ac.equals(bc)) {
                    // Both methods don't fit: they are declared in the same class and have the same signatures.
                    // That is possible only with code generation bypassing compiler checks.
                    throw new AssertionError();
                } else if (ac.isAssignableFrom(bc)) {
                    return b;
                } else if (bc.isAssignableFrom(ac)) {
                    return a;
                } else if (a.isDefault()) {
                    return b;
                } else {
                    return a;
                }
            }).orElseThrow();
        });
    }

    private static boolean signatureEquals(Method a, Method b) {
        return Objects.equals(new MethodSignature(a), new MethodSignature(b));
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

        private static boolean objectIsSame(Object obj, Object other) {
            return obj == other;
        }

        private record ObjectMethodHandler(Method method) implements Handler {

            ObjectMethodHandler {
                Objects.requireNonNull(method);
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

        private static final Map<Method, Handler> OBJECT_METHOD_DISPATCH;

        static {
            try {
                OBJECT_METHOD_DISPATCH = Map.of(
                        Object.class.getMethod("toString"),
                        new ObjectMethodHandler(CompositeProxyInvocationHandler.class.getDeclaredMethod("objectToString", Object.class)),

                        Object.class.getMethod("equals", Object.class),
                        new ObjectMethodHandler(CompositeProxyInvocationHandler.class.getDeclaredMethod("objectIsSame", Object.class, Object.class)),

                        Object.class.getMethod("hashCode"),
                        new ObjectMethodHandler(System.class.getMethod("identityHashCode", Object.class))
                );
            } catch (NoSuchMethodException | SecurityException ex) {
                throw new InternalError(ex);
            }
        }
    }

    private static Handler createHandlerForDefaultMethod(Method method, InvokeTunnel invokeTunnel) {
        Objects.requireNonNull(method);
        if (invokeTunnel != null) {
            return (proxy, args) -> {
                return invokeTunnel.invokeDefault(proxy, method, args);
            };
        } else {
            return null;
        }
    }

    private static Handler createHandlerForMethod(Object obj, Method method, InvokeTunnel invokeTunnel) {
        Objects.requireNonNull(obj);
        Objects.requireNonNull(method);
        if (invokeTunnel != null) {
            return (proxy, args) -> {
                return invokeTunnel.invoke(obj, method, args);
            };
        } else {
            return (proxy, args) -> {
                return method.invoke(obj, args);
            };
        }
    }

    @FunctionalInterface
    private interface Handler {

        Object invoke(Object proxy, Object[] args) throws Throwable;
    }

    private record MethodSignature(String name, List<Class<?>> parameterTypes) {
        MethodSignature {
            Objects.requireNonNull(name);
            parameterTypes.forEach(Objects::requireNonNull);
        }

        MethodSignature(Method m) {
            this(m.getName(), List.of(m.getParameterTypes()));
        }
    }

    private record FullMethodSignature(MethodSignature signature, Class<?> returnType) {
        FullMethodSignature {
            Objects.requireNonNull(signature);
            Objects.requireNonNull(returnType);
        }

        FullMethodSignature(Method m) {
            this(new MethodSignature(m), m.getReturnType());
        }
    }

    /**
     * Returns the standard jpackage configuration if the values of
     * {@code interfaceType} and {@code slices} parameters comprise such or an empty
     * {@code Optional} otherwise.
     * <p>
     * Standard jpackage configuration is:
     * <ul>
     * <li>The proxy implements an interface comprised of two direct
     * superinterfaces.
     * <li>The superinterfaces are distinct, i.e. they are not superinterfaces of
     * each other.
     * <li>Each supplied slice implements one of the superinterfaces.
     * </ul>
     *
     * @param interfaceType the interface type composite proxy instance should
     *                      implement
     * @param slices        all objects passed to the calling composite proxy. The
     *                      value is a copy of the last parameter passed in the
     *                      {@link Builder#create(Class, Object...)}
     */
    static Optional<Map<IdentityWrapper<Object>, Class<?>>> detectJPackageConfiguration(Class<?> interfaceType, Object... slices) {
        var interfaces = interfaceType.getInterfaces();

        if (interfaces.length != 2) {
            return Optional.empty();
        }

        if (interfaces[0].isAssignableFrom(interfaces[1]) || interfaces[1].isAssignableFrom(interfaces[0])) {
            return Optional.empty();
        }

        var uniqueSlices = Stream.of(slices).map(IdentityWrapper::new).distinct().toList();
        if (uniqueSlices.size() != interfaces.length) {
            return Optional.empty();
        }

        Map<Class<?>, List<IdentityWrapper<Object>>> dispatch = Stream.of(interfaces).collect(toMap(x -> x, iface -> {
            return uniqueSlices.stream().filter(slice -> {
                return iface.isInstance(slice.value());
            }).toList();
        }));

        return dispatch.values().stream().filter(v -> {
            return v.size() == 1;
        }).findFirst().map(anambiguous -> {
            return dispatch.entrySet().stream().collect(toMap(e -> {
                var ifaceSlices = e.getValue();
                if (ifaceSlices.size() == 1) {
                    return ifaceSlices.getFirst();
                } else {
                    if (anambiguous.size() != 1) {
                        throw new AssertionError();
                    }
                    return ifaceSlices.stream().filter(Predicate.isEqual(anambiguous.getFirst()).negate()).findFirst().orElseThrow();
                }
            }, Map.Entry::getKey));
        });
    }

    // jpackage-specific object conflict resolver
    private static final ObjectConflictResolver JPACKAGE_OBJECT_CONFLICT_RESOLVER = (interfaceType, slices, method, candidates) -> {
        return detectJPackageConfiguration(interfaceType, slices).map(dispatch -> {
            // In this configuration, if one slice contains matching default method and
            // another contains matching implemented method,
            // the latter slice is selected as a supplier of this method for the composite proxy.

            var nonDefaultImplementations = new BitSet(candidates.length);
            var defaultImplementations = new BitSet(candidates.length);
            for (int i = 0; i != candidates.length; i++) {
                var slice = candidates[i];

                var limitSignatures = new Predicate<Method>() {

                    @Override
                    public boolean test(Method m) {
                        return limitSignatures.contains(new MethodSignature(m));
                    }

                    private final Collection<MethodSignature> limitSignatures =
                            getProxyableMethods(dispatch.get(IdentityWrapper.wrapIdentity(slice)))
                                    .map(MethodSignature::new)
                                    .toList();
                };

                int cur = i;

                getImplementerMethods(slice).filter(limitSignatures).filter(sliceMethod -> {
                    return signatureEquals(sliceMethod, method);
                }).findFirst().ifPresent(sliceMethod -> {
                    if (!sliceMethod.isDefault() ||
                            getUnfilteredImplementerMethods(slice)
                                    .filter(limitSignatures)
                                    .map(FullMethodSignature::new)
                                    .anyMatch(Predicate.isEqual(new FullMethodSignature(sliceMethod)))) {
                        nonDefaultImplementations.set(cur);
                    } else {
                        defaultImplementations.set(cur);
                    }
                });
            }

            if (nonDefaultImplementations.cardinality() == 1) {
                return candidates[nonDefaultImplementations.nextSetBit(0)];
            } else if (nonDefaultImplementations.cardinality() == 0 && defaultImplementations.cardinality() == 1) {
                return candidates[defaultImplementations.nextSetBit(0)];
            } else {
                throw new AssertionError();
            }
        }).orElse(null);
    };

    // jpackage-specific method conflict resolver
    private static final MethodConflictResolver JPACKAGE_METHOD_CONFLICT_RESOLVER = (interfaceType, slices, method, obj) -> {
        return false;
    };
}
