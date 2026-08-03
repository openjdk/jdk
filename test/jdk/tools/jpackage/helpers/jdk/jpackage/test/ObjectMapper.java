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
package jdk.jpackage.test;

import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.toSet;
import static jdk.jpackage.internal.util.function.ExceptionBox.toUnchecked;
import static jdk.jpackage.internal.util.function.ThrowingConsumer.toConsumer;
import static jdk.jpackage.internal.util.function.ThrowingRunnable.toRunnable;
import static jdk.jpackage.internal.util.function.ThrowingSupplier.toSupplier;

import java.lang.reflect.Array;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.math.BigInteger;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.IntPredicate;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collector;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.xml.stream.XMLStreamWriter;
import jdk.jpackage.internal.util.IdentityWrapper;

public final class ObjectMapper {

    private ObjectMapper(
            Predicate<String> classFilter,
            Predicate<List<String>> methodFilter,
            Predicate<String> leafClassFilter,
            Map<Method, Function<?, Object>> substitutes,
            Map<Class<?>, BiConsumer<Object, Map<String, Object>>> mutators,
            Set<String> accessPackageMethods) {

        this.classFilter = Objects.requireNonNull(classFilter);
        this.methodFilter = Objects.requireNonNull(methodFilter);
        this.leafClassFilter = Objects.requireNonNull(leafClassFilter);
        this.substitutes = Objects.requireNonNull(substitutes);
        this.mutators = Objects.requireNonNull(mutators);
        this.accessPackageMethods = accessPackageMethods;
    }

    public static Builder blank() {
        return new Builder().allowAllLeafClasses(false).exceptLeafClasses().add(Stream.of(
                Object.class,
                String.class, String[].class,
                boolean.class, Boolean.class, boolean[].class, Boolean[].class,
                byte.class, Byte.class, byte[].class, Byte[].class,
                char.class, Character.class, char[].class, Character[].class,
                short.class, Short.class, short[].class, Short[].class,
                int.class, Integer.class, int[].class, Integer[].class,
                long.class, Long.class, long[].class, Long[].class,
                float.class, Float.class, float[].class, Float[].class,
                double.class, Double.class, double[].class, Double[].class,
                void.class, Void.class, Void[].class
        ).map(Class::getName).toList()).apply();
    }

    public static Builder standard() {
        return blank()
                .mutate(configureObject())
                .mutate(configureLeafClasses())
                .mutate(configureOptional())
                .mutate(configureFunctionalTypes())
                .mutate(configureEnum())
                .mutate(configureException());
    }

    public static Consumer<Builder> configureObject() {
        // Exclude all method of Object class.
        return builder -> {
            builder.exceptMethods().add(OBJECT_METHODS).apply();
        };
    }

    public static Consumer<Builder> configureLeafClasses() {
        return builder -> {
            builder.exceptLeafClasses().add(Stream.of(
                    IdentityWrapper.class,
                    Class.class,
                    Path.class,
                    Path.of("").getClass(),
                    UUID.class,
                    BigInteger.class
            ).map(Class::getName).toList()).apply();
        };
    }

    public static Consumer<Builder> configureOptional() {
        return builder -> {
            // Filter out all but "get()" methods of "Optional" class.
            builder.exceptAllMethods(Optional.class).remove("get").apply();
            // Substitute "Optional.get()" with the function that will return "null" if the value is "null".
            builder.subst(Optional.class, "get", opt -> {
                if (opt.isPresent()) {
                    return opt.get();
                } else {
                    return null;
                }
            });
        };
    }

    public static Consumer<Builder> configureFunctionalTypes() {
        // Remove all getters from the standard functional types.
        return builder -> {
            builder.exceptAllMethods(Predicate.class).apply();
            builder.exceptAllMethods(Supplier.class).apply();
        };
    }

    public static Consumer<Builder> configureEnum() {
        return builder -> {
            // Filter out "getDeclaringClass()" and "describeConstable()" methods of "Enum" class.
            builder.exceptSomeMethods(Enum.class).add("getDeclaringClass", "describeConstable").apply();
        };
    }

    public static Consumer<Builder> configureException() {
        return builder -> {
            // Include only "getMessage()" and "getCause()" methods of "Exception" class.
            builder.exceptAllMethods(Exception.class).remove("getMessage", "getCause").apply();
            builder.mutator(Exception.class, (ex, map) -> {
                var eit = map.entrySet().iterator();
                while (eit.hasNext()) {
                    var e = eit.next();
                    if (e.getValue() == NULL) {
                        // Remove property with the "null" value.
                        eit.remove();
                    }
                }
                map.put("getClass", ex.getClass().getName());
            });
        };
    }

    public static String lookupFullMethodName(Method m) {
        return lookupFullMethodName(m.getDeclaringClass(), m.getName());
    }

    public static String lookupFullMethodName(Class<?> c, String m) {
        return Objects.requireNonNull(c).getName() + lookupMethodName(m);
    }

    public static String lookupMethodName(Method m) {
        return lookupMethodName(m.getName());
    }

    public static String lookupMethodName(String m) {
        return "#" + Objects.requireNonNull(m);
    }

    public static Object wrapIdentity(Object v) {
        if (v instanceof IdentityWrapper<?> wrapper) {
            return wrapper;
        } else {
            return new IdentityWrapper<Object>(v);
        }
    }

    public static void store(Map<String, Object> map, XMLStreamWriter xml) {
        XmlWriter.writePropertyMap(map, xml);
    }

    @SuppressWarnings("unchecked")
    public static <T> Optional<T> findNonNullProperty(Map<String, Object> map, String propertyName) {
        Objects.requireNonNull(propertyName);
        Objects.requireNonNull(map);

        return Optional.ofNullable(map.get(propertyName)).filter(Predicate.not(NULL::equals)).map(v -> {
            return (T)v;
        });
    }

    public Object map(Object obj) {
        if (obj != null) {
            return mapObject(obj).orElseGet(Map::of);
        } else {
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> toMap(Object obj) {
        if (obj == null) {
            return null;
        } else {
            var mappedObj = map(obj);
            if (mappedObj instanceof Map<?, ?> m) {
                return (Map<String, Object>)m;
            } else {
                return Map.of("value", mappedObj);
            }
        }
    }

    public Optional<Object> mapObject(Object obj) {
        if (obj == null) {
            return Optional.empty();
        }

        if (leafClassFilter.test(obj.getClass().getName())) {
            return Optional.of(obj);
        }

        if (!filter(obj.getClass())) {
            return Optional.empty();
        }

        if (obj instanceof Iterable<?> col) {
            return Optional.of(mapIterable(col));
        }

        if (obj instanceof Map<?, ?> map) {
            return Optional.of(mapMap(map));
        }

        if (obj.getClass().isArray()) {
            return Optional.of(mapArray(obj));
        }

        var theMap = getMethods(obj).map(m -> {
            final Object propertyValue;
            final var subst = substitutes.get(m);
            if (subst != null) {
                propertyValue = applyGetter(obj, subst);
            } else {
                propertyValue = invoke(m, obj);
            }
            return Map.entry(m.getName(), mapObject(propertyValue).orElse(NULL));
        }).collect(toMutableMap(Map.Entry::getKey, Map.Entry::getValue));

        mutators.entrySet().stream().filter(m -> {
            return m.getKey().isInstance(obj);
        }).findFirst().ifPresent(m -> {
            m.getValue().accept(obj, theMap);
        });

        if (theMap.isEmpty()) {
            return Optional.of(wrapIdentity(obj));
        }

        return Optional.of(theMap);
    }

    private Object invoke(Method m, Object obj) {
        try {
            return m.invoke(obj);
        } catch (IllegalAccessException ex) {
            throw toUnchecked(ex);
        } catch (InvocationTargetException ex) {
            return map(ex.getTargetException());
        }
    }

    private Collection<Object> mapIterable(Iterable<?> col) {
        final List<Object> list = new ArrayList<>();
        for (var obj : col) {
            list.add(mapObject(obj).orElse(NULL));
        }
        return list;
    }

    private Map<Object, Object> mapMap(Map<?, ?> map) {
        return map.entrySet().stream().collect(toMutableMap(e -> {
            return mapObject(e.getKey()).orElse(NULL);
        }, e -> {
            return mapObject(e.getValue()).orElse(NULL);
        }));
    }

    private Object mapArray(Object arr) {
        final var len = Array.getLength(arr);

        if (len == 0) {
            return arr;
        }

        Object[] buf = null;

        for (int i = 0; i != len; i++) {
            var from = Array.get(arr, i);
            if (from != null) {
                var to = mapObject(from).orElseThrow();
                if (from != to || buf != null) {
                    if (buf == null) {
                        buf = (Object[])Array.newInstance(Object.class, len);
                        System.arraycopy(arr, 0, buf, 0, i);
                    }
                    buf[i] = to;
                }
            }
        }

        return Optional.ofNullable((Object)buf).orElse(arr);
    }

    @SuppressWarnings("unchecked")
    private static <T> Object applyGetter(Object obj, Function<T, Object> getter) {
        return getter.apply((T)obj);
    }

    private boolean filter(Class<?> type) {
        return classFilter.test(type.getName());
    }

    private boolean filter(Method m) {
        return methodFilter.test(List.of(lookupMethodName(m), lookupFullMethodName(m)));
    }

    private Stream<Method> getMethods(Object obj) {
        return MethodGroups.create(obj.getClass(), accessPackageMethods).filter(this::filter).map(MethodGroup::callable);
    }

    private static boolean defaultFilter(Method m) {
        if (Modifier.isStatic(m.getModifiers()) || (m.getParameterCount() > 0) || void.class.equals(m.getReturnType())) {
            return false;
        }
        return true;
    }

    private static <T, K, U>
    Collector<T, ?, Map<K,U>> toMutableMap(Function<? super T, ? extends K> keyMapper,
            Function<? super T, ? extends U> valueMapper) {
        return Collectors.toMap(keyMapper, valueMapper, (x , y) -> {
            throw new UnsupportedOperationException(
                    String.format("Entries with the same key and different values [%s] and [%s]", x, y));
        }, HashMap::new);
    }

    public static final class Builder {

        private Builder() {
            allowAllClasses();
            allowAllLeafClasses();
            allowAllMethods();
        }

        public ObjectMapper create() {
            return new ObjectMapper(
                    classFilter.createPredicate(),
                    methodFilter.createMultiPredicate(),
                    leafClassFilter.createPredicate(),
                    Map.copyOf(substitutes),
                    Map.copyOf(mutators),
                    accessPackageMethods);
        }


        public final class NamePredicateBuilder {

            NamePredicateBuilder(Filter sink) {
                this.sink = Objects.requireNonNull(sink);
            }

            public Builder apply() {
                sink.addAll(items);
                return Builder.this;
            }

            public NamePredicateBuilder add(String... v) {
                return add(List.of(v));
            }

            public NamePredicateBuilder add(Collection<String> v) {
                items.addAll(v);
                return this;
            }

            private final Filter sink;
            private final Set<String> items = new HashSet<>();
        }


        public final class AllMethodPredicateBuilder {

            AllMethodPredicateBuilder(Class<?> type) {
                impl = new MethodPredicateBuilder(type, false);
            }

            public AllMethodPredicateBuilder remove(String... v) {
                return remove(List.of(v));
            }

            public AllMethodPredicateBuilder remove(Collection<String> v) {
                impl.add(v);
                return this;
            }

            public Builder apply() {
                return impl.apply();
            }

            private final MethodPredicateBuilder impl;
        }


        public final class SomeMethodPredicateBuilder {

            SomeMethodPredicateBuilder(Class<?> type) {
                impl = new MethodPredicateBuilder(type, true);
            }

            public SomeMethodPredicateBuilder add(String... v) {
                return add(List.of(v));
            }

            public SomeMethodPredicateBuilder add(Collection<String> v) {
                impl.add(v);
                return this;
            }

            public Builder apply() {
                return impl.apply();
            }

            private final MethodPredicateBuilder impl;
        }


        public Builder allowAllClasses(boolean v) {
            classFilter.negate(v);
            return this;
        }

        public Builder allowAllClasses() {
            return allowAllClasses(true);
        }

        public Builder allowAllMethods(boolean v) {
            methodFilter.negate(v);
            return this;
        }

        public Builder allowAllMethods() {
            return allowAllMethods(true);
        }

        public Builder allowAllLeafClasses(boolean v) {
            leafClassFilter.negate(v);
            return this;
        }

        public Builder allowAllLeafClasses() {
            return allowAllLeafClasses(true);
        }

        public NamePredicateBuilder exceptClasses() {
            return new NamePredicateBuilder(classFilter);
        }

        public AllMethodPredicateBuilder exceptAllMethods(Class<?> type) {
            return new AllMethodPredicateBuilder(type);
        }

        public SomeMethodPredicateBuilder exceptSomeMethods(Class<?> type) {
            return new SomeMethodPredicateBuilder(type);
        }

        public NamePredicateBuilder exceptMethods() {
            return new NamePredicateBuilder(methodFilter);
        }

        public NamePredicateBuilder exceptLeafClasses() {
            return new NamePredicateBuilder(leafClassFilter);
        }

        public Builder subst(Method target, Function<?, Object> substitute) {
            substitutes.put(Objects.requireNonNull(target), Objects.requireNonNull(substitute));
            return this;
        }

        public <T> Builder subst(Class<? extends T> targetClass, String targetMethodName, Function<T, Object> substitute) {
            var method = toSupplier(() -> targetClass.getMethod(targetMethodName)).get();
            return subst(method, substitute);
        }

        public Builder mutator(Class<?> targetClass, BiConsumer<Object, Map<String, Object>> mutator) {
            mutators.put(Objects.requireNonNull(targetClass), Objects.requireNonNull(mutator));
            return this;
        }

        public Builder mutate(Consumer<Builder> mutator) {
            mutator.accept(this);
            return this;
        }

        public Builder accessPackageMethods(Package... packages) {
            Stream.of(packages).map(Package::getName).forEach(accessPackageMethods::add);
            return this;
        }


        private final class MethodPredicateBuilder {

            MethodPredicateBuilder(Class<?> type, boolean negate) {
                this.type = Objects.requireNonNull(type);
                buffer.negate(negate);
            }

            void add(Collection<String> v) {
                buffer.addAll(v);
            }

            Builder apply() {
                var pred = buffer.createPredicate();

                var items = MethodGroups.create(type, accessPackageMethods).groups().stream().map(MethodGroup::primary).filter(m -> {
                    return !OBJECT_METHODS.contains(ObjectMapper.lookupMethodName(m));
                }).filter(m -> {
                    return !pred.test(m.getName());
                }).map(ObjectMapper::lookupFullMethodName).toList();

                return exceptMethods().add(items).apply();
            }

            private final Class<?> type;
            private final Filter buffer = new Filter();
        }


        private static final class Filter {
            Predicate<List<String>> createMultiPredicate() {
                if (items.isEmpty()) {
                    var match = negate;
                    return v -> match;
                } else if (negate) {
                    return v -> {
                        return v.stream().noneMatch(Set.copyOf(items)::contains);
                    };
                } else {
                    return v -> {
                        return v.stream().anyMatch(Set.copyOf(items)::contains);
                    };
                }
            }

            Predicate<String> createPredicate() {
                if (items.isEmpty()) {
                    var match = negate;
                    return v -> match;
                } else if (negate) {
                    return Predicate.not(Set.copyOf(items)::contains);
                } else {
                    return Set.copyOf(items)::contains;
                }
            }

            void addAll(Collection<String> v) {
                items.addAll(v);
            }

            void negate(boolean v) {
                negate = v;
            }

            private boolean negate;
            private final Set<String> items = new HashSet<>();
        }


        private final Filter classFilter = new Filter();
        private final Filter methodFilter = new Filter();
        private final Filter leafClassFilter = new Filter();
        private final Map<Method, Function<?, Object>> substitutes = new HashMap<>();
        private final Map<Class<?>, BiConsumer<Object, Map<String, Object>>> mutators = new HashMap<>();
        private final Set<String> accessPackageMethods = new HashSet<>();
    }


    private record MethodGroup(List<Method> methods) {

        MethodGroup {
            Objects.requireNonNull(methods);

            if (methods.isEmpty()) {
                throw new IllegalArgumentException();
            }

            methods.stream().map(Method::getName).reduce((a, b) -> {
                if (!a.equals(b)) {
                    throw new IllegalArgumentException();
                } else {
                    return a;
                }
            });
        }

        Method callable() {
            var primary = primary();
            if (!primary.getDeclaringClass().isInterface()) {
                primary = methods.stream().filter(m -> {
                    return m.getDeclaringClass().isInterface();
                }).findFirst().orElse(primary);
            }
            return primary;
        }

        Method primary() {
            return methods.getFirst();
        }

        boolean match(Predicate<Method> predicate) {
            Objects.requireNonNull(predicate);
            return methods.stream().allMatch(predicate);
        }
    }


    private record MethodGroups(Collection<MethodGroup> groups) {

        MethodGroups {
            Objects.requireNonNull(groups);
        }

        Stream<MethodGroup> filter(Predicate<Method> predicate) {
            Objects.requireNonNull(predicate);

            return groups.stream().filter(g -> {
                return g.match(predicate);
            });
        }

        static MethodGroups create(Class<?> type, Set<String> accessPackageMethods) {
            List<Class<?>> types = new ArrayList<>();

            collectSuperclassAndInterfaces(type, types::add);

            final var methodGroups = types.stream()
                    .map(c -> {
                        if (accessPackageMethods.contains(c.getPackageName())) {
                            return PUBLIC_AND_PACKAGE_METHODS_GETTER.apply(c);
                        } else {
                            return PUBLIC_METHODS_GETTER.apply(c);
                        }
                    })
                    .flatMap(x -> x)
                    .filter(ObjectMapper::defaultFilter)
                    .collect(groupingBy(Method::getName));

            return new MethodGroups(methodGroups.values().stream().distinct().map(MethodGroup::new).toList());
        }

        private static void collectSuperclassAndInterfaces(Class<?> type, Consumer<Class<?>> sink) {
            Objects.requireNonNull(type);
            Objects.requireNonNull(sink);

            for (; type != null; type = type.getSuperclass()) {
                sink.accept(type);
                for (var i : type.getInterfaces()) {
                    collectSuperclassAndInterfaces(i, sink);
                }
            }
        }
    }


    private static final class XmlWriter {
        static void write(Object obj,  XMLStreamWriter xml) {
            if (obj instanceof Map<?, ?> map) {
                writePropertyMap(map, xml);
            } else if (obj instanceof Collection<?> col) {
                writeCollection(col, xml);
            } else if (obj.getClass().isArray()) {
                writeArray(obj, xml);
            } else {
                toRunnable(() -> xml.writeCharacters(obj.toString())).run();
            }
        }

        private static void writePropertyMap(Map<?, ?> map, XMLStreamWriter xml) {
            map.entrySet().stream().sorted(Comparator.comparing(e -> e.getKey().toString())).forEach(toConsumer(e -> {
                xml.writeStartElement("property");
                xml.writeAttribute("name", e.getKey().toString());
                write(e.getValue(), xml);
                xml.writeEndElement();
            }));
        }

        private static void writeCollection(Collection<?> col, XMLStreamWriter xml) {
            try {
                xml.writeStartElement("collection");
                xml.writeAttribute("size", Integer.toString(col.size()));
                for (var item : col) {
                    xml.writeStartElement("item");
                    write(item, xml);
                    xml.writeEndElement();
                }
                xml.writeEndElement();
            } catch (Exception ex) {
                throw toUnchecked(ex);
            }
        }

        private static void writeArray(Object arr, XMLStreamWriter xml) {
            var len = Array.getLength(arr);
            try {
                xml.writeStartElement("array");
                xml.writeAttribute("size", Integer.toString(len));
                for (int i = 0; i != len; i++) {
                    xml.writeStartElement("item");
                    write(Array.get(arr, i), xml);
                    xml.writeEndElement();
                }
                xml.writeEndElement();
            } catch (Exception ex) {
                throw toUnchecked(ex);
            }
        }
    }


    private final Predicate<String> classFilter;
    private final Predicate<List<String>> methodFilter;
    private final Predicate<String> leafClassFilter;
    private final Map<Method, Function<?, Object>> substitutes;
    private final Map<Class<?>, BiConsumer<Object, Map<String, Object>>> mutators;
    private final Set<String> accessPackageMethods;

    static final Object NULL = new Object() {
        @Override
        public String toString() {
            return "<null>";
        }
    };

    private static final Set<String> OBJECT_METHODS =
            Stream.of(Object.class.getMethods()).map(ObjectMapper::lookupMethodName).collect(toSet());

    private static final Function<Class<?>, Stream<Method>> PUBLIC_METHODS_GETTER = type -> {
        return Stream.of(type.getMethods());
    };

    private static final Function<Class<?>, Stream<Method>> PUBLIC_AND_PACKAGE_METHODS_GETTER = type -> {
        return Stream.of(type.getDeclaredMethods()).filter(m -> {
            return Stream.<IntPredicate>of(Modifier::isPrivate, Modifier::isProtected).map(p -> {
                return p.test(m.getModifiers());
            }).allMatch(v -> !v);
        }).map(m -> {
            m.setAccessible(true);
            return m;
        });
    };
}
