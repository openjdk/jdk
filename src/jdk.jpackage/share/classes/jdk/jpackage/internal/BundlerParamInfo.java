/*
 * Copyright (c) 2014, 2025, Oracle and/or its affiliates. All rights reserved.
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

package jdk.jpackage.internal;

import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import jdk.jpackage.internal.util.function.ThrowingFunction;

/**
 * BundlerParamInfo<T>
 *
 * A BundlerParamInfo encapsulates an individual bundler parameter of type <T>.
 *
 * @param id The command line and hashmap name of the parameter
 *
 * @param valueType Type of the parameter
 *
 * @param defaultValueFunction If the value is not set, and no fallback value is found, the
 * parameter uses the value returned by the producer.
 *
 * @param stringConverter An optional string converter for command line arguments.
 */
record BundlerParamInfo<T>(String id, Class<T> valueType,
        Function<Map<String, ? super Object>, T> defaultValueFunction,
        BiFunction<String, Map<String, ? super Object>, T> stringConverter) {

    BundlerParamInfo {
        Objects.requireNonNull(id);
        Objects.requireNonNull(valueType);
    }

    static BundlerParamInfo<String> createStringBundlerParam(String id) {
        return new BundlerParamInfo<>(id, String.class, null, null);
    }

    static BundlerParamInfo<Boolean> createBooleanBundlerParam(String id) {
        return new BundlerParamInfo<>(id, Boolean.class, null, BundlerParamInfo::toBoolean);
    }

    static BundlerParamInfo<Path> createPathBundlerParam(String id) {
        return new BundlerParamInfo<>(id, Path.class, null, BundlerParamInfo::toPath);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    static <U> BundlerParamInfo<U> createBundlerParam(String id, Class<? super U> valueType,
            ThrowingFunction<Map<String, ? super Object>, U> valueCtor) {
        return new BundlerParamInfo(id, valueType, ThrowingFunction.toFunction(valueCtor), null);
    }

    static <U> BundlerParamInfo<U> createBundlerParam(Class<? super U> valueType,
            ThrowingFunction<Map<String, ? super Object>, U> valueCtor) {
        return createBundlerParam(valueType.getName(), valueType, valueCtor);
    }

    static boolean toBoolean(String value, Map<String, ? super Object> params) {
        if (value == null || "null".equalsIgnoreCase(value)) {
            return false;
        } else {
            return Boolean.valueOf(value);
        }
    }

    static Path toPath(String value, Map<String, ? super Object> params) {
        return Path.of(value);
    }

    String getID() {
        return id;
    }

    Class<T> getValueType() {
        return valueType;
    }

    /**
     * Returns true if value was not provided on command line for this parameter.
     *
     * @param params - params from which value will be fetch
     * @return true if value was not provided on command line, false otherwise
     */
    boolean getIsDefaultValue(Map<String, ? super Object> params) {
        Object o = params.get(getID());
        if (o != null) {
            return false; // We have user provided value
        }

        if (params.containsKey(getID())) {
            return false; // explicit nulls are allowed for provided value
        }

        return true;
    }

    Function<Map<String, ? super Object>, T> getDefaultValueFunction() {
        return defaultValueFunction;
    }

    BiFunction<String, Map<String, ? super Object>, T> getStringConverter() {
        return stringConverter;
    }

    final T fetchFrom(Map<String, ? super Object> params) {
        return fetchFrom(params, true);
    }

    @SuppressWarnings("unchecked")
    final T fetchFrom(Map<String, ? super Object> params,
            boolean invokeDefault) {
        Object o = params.get(getID());
        if (o instanceof String && getStringConverter() != null) {
            return getStringConverter().apply((String) o, params);
        }

        Class<T> klass = getValueType();
        if (klass.isInstance(o)) {
            return (T) o;
        }
        if (o != null) {
            throw new IllegalArgumentException("Param " + getID()
                    + " should be of type " + getValueType()
                    + " but is a " + o.getClass());
        }
        if (params.containsKey(getID())) {
            // explicit nulls are allowed
            return null;
        }

        if (invokeDefault && (getDefaultValueFunction() != null)) {
            T result = getDefaultValueFunction().apply(params);
            if (result != null) {
                params.put(getID(), result);
            }
            return result;
        }

        // ultimate fallback
        return null;
    }

    Optional<T> findIn(Map<String, ? super Object> params) {
        if (params.containsKey(getID())) {
            return Optional.of(fetchFrom(params, true));
        } else {
            return Optional.empty();
        }
    }

    void copyInto(Map<String, ? super Object> params, Consumer<T> consumer) {
        findIn(params).ifPresent(consumer);
    }
}
