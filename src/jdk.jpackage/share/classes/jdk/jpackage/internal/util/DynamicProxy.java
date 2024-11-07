/*
 * Copyright (c) 2019, 2024, Oracle and/or its affiliates. All rights reserved.
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
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import static java.util.stream.Collectors.toMap;
import java.util.stream.Stream;

public final class DynamicProxy {

    public static <T> T createProxyFromPieces(Class<T> interfaceType, Object ... pieces) {
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
                            "Both [%s] and [%s] objects implement %s", a, b,
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

        var methodDispatch = Stream.of(interfaces)
                .map(Class::getMethods)
                .flatMap(Stream::of)
                .filter(Predicate.not(Method::isDefault))
                .collect(toMap(x -> x, method -> {
                    return interfaceDispatch.get(method.getDeclaringClass());
                }));

        return createProxy(interfaceType, methodDispatch);
    }

    @SuppressWarnings("unchecked")
    private static <T> T createProxy(Class<T> interfaceType, Map<Method, Object> dispatch) {
        return (T) Proxy.newProxyInstance(interfaceType.getClassLoader(),
                new Class<?>[]{interfaceType},
                new DynamicMixinInvocationHandler(dispatch));
    }

    private static IllegalArgumentException createInterfaceNotImplementedException(
            Collection<Class<?>> missingInterfaces) {
        return new IllegalArgumentException(String.format(
                "None of the pieces implement %s", missingInterfaces));
    }

    private record DynamicMixinInvocationHandler(Map<Method, Object> dispatch) implements InvocationHandler {

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            if (method.isDefault()) {
                return InvocationHandler.invokeDefault(proxy, method, args);
            } else {
                var handler = dispatch.get(method);
                return method.invoke(handler, args);
            }
        }
    }
}
