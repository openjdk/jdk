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

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import static java.util.stream.Collectors.toMap;
import java.util.stream.Stream;

final class CompositeProxySpec {

    Map<Class<?>, Object> getInterfaceDispatch() {
        return interfaceDispatch;
    }

    static CompositeProxySpec create(Class<?> interfaceType, Object... slices) {
        validateTypeIsInterface(interfaceType);
        return new CompositeProxySpec(interfaceType, interfaceType.getInterfaces(), slices);
    }

    private CompositeProxySpec(Class<?> interfaceType, Class<?>[] interfaces, Object[] slices) {
        List.of(interfaces).forEach(CompositeProxySpec::validateTypeIsInterface);

        if (interfaces.length != slices.length) {
            throw new IllegalArgumentException(String.format(
                    "type %s must extend %d interfaces", interfaceType.getName(),
                    slices.length));
        }

        this.interfaceDispatch = createInterfaceDispatch(interfaces, slices);
    }

    private static Map<Class<?>, Object> createInterfaceDispatch(
            Class<?>[] interfaces, Object[] slices) {

        final Map<Class<?>, Object> interfaceDispatch = Stream.of(interfaces).collect(toMap(x -> x, iface -> {
            return Stream.of(slices).filter(obj -> {
                return Set.of(obj.getClass().getInterfaces()).contains(iface);
            }).reduce((a, b) -> {
                throw new IllegalArgumentException(String.format(
                        "both [%s] and [%s] slices implement %s", a, b, iface));
            }).orElseThrow(() -> createInterfaceNotImplementedException(List.of(iface)));
        }));

        if (interfaceDispatch.size() != interfaces.length) {
            final List<Class<?>> missingInterfaces = new ArrayList<>(Set.of(interfaces));
            missingInterfaces.removeAll(interfaceDispatch.entrySet());
            throw createInterfaceNotImplementedException(missingInterfaces);
        }

        return Stream.of(interfaces).flatMap(iface -> {
            return unfoldInterface(iface).map(unfoldedIface -> {
                return Map.entry(unfoldedIface, interfaceDispatch.get(iface));
            });
        }).collect(toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    private static Stream<Class<?>> unfoldInterface(Class<?> interfaceType) {
        return Stream.concat(Stream.of(interfaceType), Stream.of(
                interfaceType.getInterfaces()).flatMap(CompositeProxySpec::unfoldInterface));
    }

    private static IllegalArgumentException createInterfaceNotImplementedException(
            Collection<Class<?>> missingInterfaces) {
        return new IllegalArgumentException(String.format(
                "none of the slices implement %s", missingInterfaces));
    }

    private static void validateTypeIsInterface(Class<?> type) {
        if (!type.isInterface()) {
            throw new IllegalArgumentException(String.format(
                    "type %s must be an interface", type.getName()));
        }
    }

    final Map<Class<?>, Object> interfaceDispatch;
}
