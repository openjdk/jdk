/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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

/* @test
 * @summary Basic tests for dependency injection
 * @enablePreview
 * @run junit DemoContainerInjectionTest
 */

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

final class DemoContainerInjectionTest {

    interface Foo{}
    interface Bar{};
    static class FooImpl implements Foo{};
    static class BarImpl implements Bar{};

    // Provides a type safe way of associating a type to a supplier
    record Provider<T>(Class<T> type, Supplier<? extends T> supplier){}

    @Test
    void ComputedComponentsViaLambda() {
        Container container = ComputedContainer.of(Set.of(Foo.class, Bar.class), t -> switch (t) {
            case Class<?> c when c.equals(Foo.class) -> new FooImpl();
            case Class<?> c when c.equals(Bar.class) -> new BarImpl();
            default -> throw new IllegalArgumentException();
        });
        assertContainerPopulated(container);
    }

    @Test
    void SettableComponents() {
        SettableContainer container = SettableScratchContainer.of(Set.of(Foo.class, Bar.class));
        container.set(Foo.class, new FooImpl());
        container.set(Bar.class, new BarImpl());
        assertContainerPopulated(container);
    }


    @Test
    void ProviderComponents() {
        Container container = ProviderContainer.of(Map.of(
                Foo.class, FooImpl::new,
                Bar.class, BarImpl::new));
        assertContainerPopulated(container);
    }

    @Test
    void ProviderTypedComponents() {
        Container container = providerTypedContainer(Set.of(
           new Provider<>(Foo.class, FooImpl::new),
           new Provider<>(Bar.class, BarImpl::new)
        ));
        assertContainerPopulated(container);
    }

    private static void assertContainerPopulated(Container container) {
        assertInstanceOf(FooImpl.class, container.get(Foo.class));
        assertInstanceOf(BarImpl.class, container.get(Bar.class));
    }

    interface Container {
        <T> T get(Class<T> type);
    }

    interface SettableContainer extends Container {
        <T> void set(Class<T> type, T implementation);
    }

    record ComputedContainer(Map<Class<?>, ?> components) implements Container {

        @Override
        public <T> T get(Class<T> type) {
            return type.cast(components.get(type));
        }

        static Container of(Set<Class<?>> components, Function<Class<?>, ?> mapper) {
            return new ComputedContainer(Map.ofLazy(components, mapper));
        }

    }

    record SettableScratchContainer(Map<Class<?>, Object> scratch, Map<Class<?>, ?> components) implements SettableContainer {

        @Override
        public <T> void set(Class<T> type, T implementation) {
            if (scratch.putIfAbsent(type, type.cast(implementation)) != null) {
                throw new IllegalStateException("Can only set once for " + type);
            }
        }

        @Override
        public <T> T get(Class<T> type) {
            return type.cast(components.get(type));
        }

        static SettableContainer of(Set<Class<?>> components) {
            Map<Class<?>, Object> scratch = new ConcurrentHashMap<>();
            return new SettableScratchContainer(scratch, Map.ofLazy(components, scratch::get));
        }

    }

    record ProviderContainer(Map<Class<?>, ?> components) implements Container {

        @Override
        public <T> T get(Class<T> type) {
            return type.cast(components.get(type));
        }

        static Container of(Map<Class<?>, Supplier<?>> components) {
            var map = Map.ofLazy(components.keySet(), t -> components.get(t).get());
            return new ProviderContainer(map);
        }

    }

    static Container providerTypedContainer(Set<Provider<?>> providers) {
                return ProviderContainer.of(providers.stream()
                                .collect(Collectors.toMap(Provider::type, Provider::supplier)));
    }

}
