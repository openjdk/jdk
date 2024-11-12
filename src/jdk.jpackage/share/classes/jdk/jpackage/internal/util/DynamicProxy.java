/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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
package jdk.jpackage.internal.util;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.BinaryOperator;
import static java.util.stream.Collectors.toMap;
import java.util.stream.Stream;
import static jdk.jpackage.internal.util.function.ThrowingSupplier.toSupplier;

public final class DynamicProxy {

    public static <T> T createProxyFromPieces(Class<T> interfaceType, Object ... pieces) {
        return createProxyFromPieces(interfaceType, STANDARD_CONFLICT_RESOLVER, pieces);
    }

    public static <T> T createProxyFromPieces(Class<T> interfaceType,
            BinaryOperator<Method> conflictResolver, Object... pieces) {
        if (!interfaceType.isInterface()) {
            throw new IllegalArgumentException(String.format(
                    "Type %s must be an interface", interfaceType.getName()));
        }

        final Class<?>[] interfaces = interfaceType.getInterfaces();
        if (interfaces.length != pieces.length) {
            throw new IllegalArgumentException(String.format(
                    "Type %s must extend %d interfaces",
                    interfaceType.getName(), pieces.length));
        }

        final Map<Class<?>, Object> interfaceDispatch;
        try {
            interfaceDispatch = Stream.of(interfaces).collect(toMap(x -> x, iface -> {
                return Stream.of(pieces).filter(obj -> {
                    return Set.of(obj.getClass().getInterfaces()).contains(iface);
                }).reduce((a, b) -> {
                    throw new IllegalArgumentException(String.format(
                            "Both [%s] and [%s] pieces implement %s", a, b,
                            iface));
                }).orElseThrow(() -> createInterfaceNotImplementedException(List.of(iface)));
            }));
        } catch (IllegalStateException ex) {
            throw new IllegalArgumentException(String.format(
                    "Multiple pieces implement the same interface"));
        }

        if (interfaceDispatch.size() != interfaces.length) {
            final List<Class<?>> missingInterfaces = new ArrayList<>(Set.of(interfaces));
            missingInterfaces.removeAll(interfaceDispatch.entrySet());
            throw createInterfaceNotImplementedException(missingInterfaces);
        }

        Map<Method, Handler> methodDispatch = Stream.of(interfaceType.getMethods())
                .map(method -> {
                    final var methodDeclaringClass = method.getDeclaringClass();
                    if (!methodDeclaringClass.equals(interfaceType)) {
                        var piece = interfaceDispatch.get(methodDeclaringClass);
                        var pieceMethod = toSupplier(
                                    () -> piece.getClass().getMethod(
                                            method.getName(),
                                            method.getParameterTypes())).get();
                        if (!method.isDefault()) {
                            return Map.entry(method, new Handler(piece, pieceMethod));
                        } else if (method.equals(pieceMethod)) {
                            // The handler class doesn't override the default method
                            // of the interface, don't add it to the dispatch map.
                            return null;
                        } else {
                            return Map.entry(method, new Handler(piece, pieceMethod));
                        }
                    } else if (method.isDefault()) {
                        return null;
                    } else {
                        // Find a piece handling the method.
                        var handler = interfaceDispatch.values().stream().map(piece -> {
                            try {
                                return new Handler(piece,
                                        piece.getClass().getMethod(
                                                method.getName(),
                                                method.getParameterTypes()));
                            } catch (NoSuchMethodException ex) {
                                return null;
                            }
                        }).filter(Objects::nonNull).reduce(new ConflictResolverAdapter(conflictResolver)).orElseThrow(() -> {
                            return new IllegalArgumentException(String.format(
                                    "None of the pieces can handle %s", method));
                        });

                        return Map.entry(method, handler);
                    }
                })
                .filter(Objects::nonNull)
                .collect(toMap(Map.Entry::getKey, Map.Entry::getValue));

        return createProxy(interfaceType, methodDispatch);
    }

    @SuppressWarnings("unchecked")
    private static <T> T createProxy(Class<T> interfaceType, Map<Method, Handler> dispatch) {
        return (T) Proxy.newProxyInstance(interfaceType.getClassLoader(),
                new Class<?>[]{interfaceType},
                new DynamicProxyInvocationHandler(dispatch));
    }

    private static IllegalArgumentException createInterfaceNotImplementedException(
            Collection<Class<?>> missingInterfaces) {
        return new IllegalArgumentException(String.format(
                "None of the pieces implement %s", missingInterfaces));
    }

    private record DynamicProxyInvocationHandler(Map<Method, Handler> dispatch) implements InvocationHandler {

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            var handler = dispatch.get(method);
            if (handler != null) {
                return handler.invoke(args);
            } else if (method.isDefault()) {
                return InvocationHandler.invokeDefault(proxy, method, args);
            } else {
                throw new UnsupportedOperationException(String.format("No handler for %s", method));
            }
        }
    }

    private record Handler(Object obj, Method method) {
        Object invoke(Object[] args) throws Throwable {
            return method.invoke(obj, args);
        }
    }

    private record ConflictResolverAdapter(
            BinaryOperator<Method> conflictResolver) implements
            BinaryOperator<Handler> {

        @Override
        public Handler apply(Handler a, Handler b) {
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

    public static final BinaryOperator<Method> STANDARD_CONFLICT_RESOLVER = (a, b) -> {
        if (a.isDefault() == b.isDefault()) {
            throw new IllegalArgumentException(String.format(
                    "Ambiguous choice between %s and %s", a, b));
        } else if (!a.isDefault()) {
            return a;
        } else {
            return b;
        }
    };
}
