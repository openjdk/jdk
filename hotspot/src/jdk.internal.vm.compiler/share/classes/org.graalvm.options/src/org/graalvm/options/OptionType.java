/*
 * Copyright (c) 2017, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.options;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Represents a type of an option that allows to convert string values to a java value.
 *
 * @since 1.0
 */
public final class OptionType<T> {

    private final String name;
    private final Function<String, T> stringConverter;
    private final Consumer<T> validator;
    private final T defaultValue;

    /**
     * Constructs a new option type with name, defaultValue and function that allows to convert a
     * string to the option type.
     *
     * @param name the name of the type to identify it
     * @param defaultValue the default value to use if no value is given
     * @param stringConverter a function that converts a string value to the actual option value.
     *            Can throw {@link IllegalArgumentException} to indicate an invalid string.
     * @param validator used for validating the option value. Throws
     *            {@link IllegalArgumentException} if the value is invalid.
     *
     * @since 1.0
     */
    public OptionType(String name, T defaultValue, Function<String, T> stringConverter, Consumer<T> validator) {
        Objects.requireNonNull(name);
        Objects.requireNonNull(defaultValue);
        Objects.requireNonNull(stringConverter);
        Objects.requireNonNull(validator);
        this.name = name;
        this.stringConverter = stringConverter;
        this.defaultValue = defaultValue;
        this.validator = validator;
    }

    /**
     * Constructs a new option type with name, defaultValue and function that allows to convert a
     * string to the option type.
     *
     * @param name the name of the type to identify it
     * @param defaultValue the default value to use if no value is given
     * @param stringConverter a function that converts a string value to the actual option value.
     *            Can throw {@link IllegalArgumentException} to indicate an invalid string.
     *
     * @since 1.0
     */
    public OptionType(String name, T defaultValue, Function<String, T> stringConverter) {
        this(name, defaultValue, stringConverter, new Consumer<T>() {
            public void accept(T t) {
            }
        });
    }

    /**
     * Returns the default value of this type, to be used if no value is available.
     *
     * @since 1.0
     */
    public T getDefaultValue() {
        return defaultValue;
    }

    /**
     * Returns the name of this type.
     *
     * @since 1.0
     */
    public String getName() {
        return name;
    }

    /**
     * Converts a string value, validates it and converts it to an object of this type.
     *
     * @throws IllegalArgumentException if the value is invalid or cannot be converted.
     * @since 1.0
     */
    public T convert(String value) {
        T v = stringConverter.apply(value);
        validate(v);
        return v;
    }

    /**
     * Validates an option value and throws an {@link IllegalArgumentException} if it is invalid.
     *
     * @throws IllegalArgumentException if the value is invalid or cannot be converted.
     * @since 1.0
     */
    public void validate(T value) {
        validator.accept(value);
    }

    /**
     * @since 1.0
     */
    @Override
    public String toString() {
        return "OptionType[name=" + name + ", defaultValue=" + defaultValue + "]";
    }

    private static Map<Class<?>, OptionType<?>> DEFAULTTYPES = new HashMap<>();
    static {
        DEFAULTTYPES.put(Boolean.class, new OptionType<>("Boolean", false, new Function<String, Boolean>() {
            public Boolean apply(String t) {
                if ("true".equals(t)) {
                    return Boolean.TRUE;
                } else if ("false".equals(t)) {
                    return Boolean.FALSE;
                } else {
                    throw new IllegalArgumentException(String.format("Invalid boolean option value '%s'. The value of the option must be '%s' or '%s'.", t, "true", "false"));
                }
            }
        }));
        DEFAULTTYPES.put(Byte.class, new OptionType<>("Byte", (byte) 0, new Function<String, Byte>() {
            public Byte apply(String t) {
                try {
                    return Byte.parseByte(t);
                } catch (NumberFormatException e) {
                    throw new IllegalArgumentException(e.getMessage(), e);
                }
            }
        }));
        DEFAULTTYPES.put(Integer.class, new OptionType<>("Integer", 0, new Function<String, Integer>() {
            public Integer apply(String t) {
                try {
                    return Integer.parseInt(t);
                } catch (NumberFormatException e) {
                    throw new IllegalArgumentException(e.getMessage(), e);
                }
            }
        }));
        DEFAULTTYPES.put(Long.class, new OptionType<>("Long", 0L, new Function<String, Long>() {
            public Long apply(String t) {
                try {
                    return Long.parseLong(t);
                } catch (NumberFormatException e) {
                    throw new IllegalArgumentException(e.getMessage(), e);
                }
            }
        }));
        DEFAULTTYPES.put(Float.class, new OptionType<>("Float", 0.0f, new Function<String, Float>() {
            public Float apply(String t) {
                try {
                    return Float.parseFloat(t);
                } catch (NumberFormatException e) {
                    throw new IllegalArgumentException(e.getMessage(), e);
                }
            }
        }));
        DEFAULTTYPES.put(Double.class, new OptionType<>("Double", 0.0d, new Function<String, Double>() {
            public Double apply(String t) {
                try {
                    return Double.parseDouble(t);
                } catch (NumberFormatException e) {
                    throw new IllegalArgumentException(e.getMessage(), e);
                }
            }
        }));
        DEFAULTTYPES.put(String.class, new OptionType<>("String", "0", new Function<String, String>() {
            public String apply(String t) {
                return t;
            }
        }));
    }

    /**
     * Returns the default option type for a given value. Returns <code>null</code> if no default
     * option type is available for this java type.
     *
     * @since 1.0
     */
    @SuppressWarnings("unchecked")
    public static <T> OptionType<T> defaultType(Object value) {
        return (OptionType<T>) DEFAULTTYPES.get(value.getClass());
    }

}
