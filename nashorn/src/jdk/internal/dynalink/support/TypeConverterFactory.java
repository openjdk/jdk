/*
 * Copyright (c) 2010, 2013, Oracle and/or its affiliates. All rights reserved.
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

/*
 * This file is available under and governed by the GNU General Public
 * License version 2 only, as published by the Free Software Foundation.
 * However, the following notice accompanied the original version of this
 * file, and Oracle licenses the original version of this file under the BSD
 * license:
 */
/*
   Copyright 2009-2013 Attila Szegedi

   Licensed under both the Apache License, Version 2.0 (the "Apache License")
   and the BSD License (the "BSD License"), with licensee being free to
   choose either of the two at their discretion.

   You may not use this file except in compliance with either the Apache
   License or the BSD License.

   If you choose to use this file in compliance with the Apache License, the
   following notice applies to you:

       You may obtain a copy of the Apache License at

           http://www.apache.org/licenses/LICENSE-2.0

       Unless required by applicable law or agreed to in writing, software
       distributed under the License is distributed on an "AS IS" BASIS,
       WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
       implied. See the License for the specific language governing
       permissions and limitations under the License.

   If you choose to use this file in compliance with the BSD License, the
   following notice applies to you:

       Redistribution and use in source and binary forms, with or without
       modification, are permitted provided that the following conditions are
       met:
       * Redistributions of source code must retain the above copyright
         notice, this list of conditions and the following disclaimer.
       * Redistributions in binary form must reproduce the above copyright
         notice, this list of conditions and the following disclaimer in the
         documentation and/or other materials provided with the distribution.
       * Neither the name of the copyright holder nor the names of
         contributors may be used to endorse or promote products derived from
         this software without specific prior written permission.

       THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS
       IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED
       TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A
       PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL COPYRIGHT HOLDER
       BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
       CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
       SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR
       BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
       WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR
       OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF
       ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
*/

package jdk.internal.dynalink.support;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.invoke.WrongMethodTypeException;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.LinkedList;
import java.util.List;
import jdk.internal.dynalink.linker.ConversionComparator;
import jdk.internal.dynalink.linker.ConversionComparator.Comparison;
import jdk.internal.dynalink.linker.GuardedInvocation;
import jdk.internal.dynalink.linker.GuardedTypeConversion;
import jdk.internal.dynalink.linker.GuardingTypeConverterFactory;
import jdk.internal.dynalink.linker.LinkerServices;

/**
 * A factory for type converters. This class is the main implementation behind the
 * {@link LinkerServices#asType(MethodHandle, MethodType)}. It manages the known {@link GuardingTypeConverterFactory}
 * instances and creates appropriate converters for method handles.
 *
 * @author Attila Szegedi
 */
public class TypeConverterFactory {

    private final GuardingTypeConverterFactory[] factories;
    private final ConversionComparator[] comparators;

    private final ClassValue<ClassMap<MethodHandle>> converterMap = new ClassValue<ClassMap<MethodHandle>>() {
        @Override
        protected ClassMap<MethodHandle> computeValue(final Class<?> sourceType) {
            return new ClassMap<MethodHandle>(getClassLoader(sourceType)) {
                @Override
                protected MethodHandle computeValue(Class<?> targetType) {
                    try {
                        return createConverter(sourceType, targetType);
                    } catch (RuntimeException e) {
                        throw e;
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                }
            };
        }
    };

    private final ClassValue<ClassMap<MethodHandle>> converterIdentityMap = new ClassValue<ClassMap<MethodHandle>>() {
        @Override
        protected ClassMap<MethodHandle> computeValue(final Class<?> sourceType) {
            return new ClassMap<MethodHandle>(getClassLoader(sourceType)) {
                @Override
                protected MethodHandle computeValue(Class<?> targetType) {
                    if(!canAutoConvert(sourceType, targetType)) {
                        final MethodHandle converter = getCacheableTypeConverter(sourceType, targetType);
                        if(converter != IDENTITY_CONVERSION) {
                            return converter;
                        }
                    }
                    return IDENTITY_CONVERSION.asType(MethodType.methodType(targetType, sourceType));
                }
            };
        }
    };

    private final ClassValue<ClassMap<Boolean>> canConvert = new ClassValue<ClassMap<Boolean>>() {
        @Override
        protected ClassMap<Boolean> computeValue(final Class<?> sourceType) {
            return new ClassMap<Boolean>(getClassLoader(sourceType)) {
                @Override
                protected Boolean computeValue(Class<?> targetType) {
                    try {
                        return getTypeConverterNull(sourceType, targetType) != null;
                    } catch (RuntimeException e) {
                        throw e;
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                }
            };
        }
    };

    private static final ClassLoader getClassLoader(final Class<?> clazz) {
        return AccessController.doPrivileged(new PrivilegedAction<ClassLoader>() {
            @Override
            public ClassLoader run() {
                return clazz.getClassLoader();
            }
        }, ClassLoaderGetterContextProvider.GET_CLASS_LOADER_CONTEXT);
    }

    /**
     * Creates a new type converter factory from the available {@link GuardingTypeConverterFactory} instances.
     *
     * @param factories the {@link GuardingTypeConverterFactory} instances to compose.
     */
    public TypeConverterFactory(Iterable<? extends GuardingTypeConverterFactory> factories) {
        final List<GuardingTypeConverterFactory> l = new LinkedList<>();
        final List<ConversionComparator> c = new LinkedList<>();
        for(GuardingTypeConverterFactory factory: factories) {
            l.add(factory);
            if(factory instanceof ConversionComparator) {
                c.add((ConversionComparator)factory);
            }
        }
        this.factories = l.toArray(new GuardingTypeConverterFactory[l.size()]);
        this.comparators = c.toArray(new ConversionComparator[c.size()]);

    }

    /**
     * Similar to {@link MethodHandle#asType(MethodType)} except it also hooks in method handles produced by
     * {@link GuardingTypeConverterFactory} implementations, providing for language-specific type coercing of
     * parameters. It will apply {@link MethodHandle#asType(MethodType)} for all primitive-to-primitive,
     * wrapper-to-primitive, primitive-to-wrapper conversions as well as for all upcasts. For all other conversions,
     * it'll insert {@link MethodHandles#filterArguments(MethodHandle, int, MethodHandle...)} with composite filters
     * provided by {@link GuardingTypeConverterFactory} implementations.
     *
     * @param handle target method handle
     * @param fromType the types of source arguments
     * @return a method handle that is a suitable combination of {@link MethodHandle#asType(MethodType)} and
     * {@link MethodHandles#filterArguments(MethodHandle, int, MethodHandle...)} with
     * {@link GuardingTypeConverterFactory} produced type converters as filters.
     */
    public MethodHandle asType(MethodHandle handle, final MethodType fromType) {
        MethodHandle newHandle = handle;
        final MethodType toType = newHandle.type();
        final int l = toType.parameterCount();
        if(l != fromType.parameterCount()) {
            throw new WrongMethodTypeException("Parameter counts differ: " + handle.type() + " vs. " + fromType);
        }
        int pos = 0;
        final List<MethodHandle> converters = new LinkedList<>();
        for(int i = 0; i < l; ++i) {
            final Class<?> fromParamType = fromType.parameterType(i);
            final Class<?> toParamType = toType.parameterType(i);
            if(canAutoConvert(fromParamType, toParamType)) {
                newHandle = applyConverters(newHandle, pos, converters);
            } else {
                final MethodHandle converter = getTypeConverterNull(fromParamType, toParamType);
                if(converter != null) {
                    if(converters.isEmpty()) {
                        pos = i;
                    }
                    converters.add(converter);
                } else {
                    newHandle = applyConverters(newHandle, pos, converters);
                }
            }
        }
        newHandle = applyConverters(newHandle, pos, converters);

        // Convert return type
        final Class<?> fromRetType = fromType.returnType();
        final Class<?> toRetType = toType.returnType();
        if(fromRetType != Void.TYPE && toRetType != Void.TYPE) {
            if(!canAutoConvert(toRetType, fromRetType)) {
                final MethodHandle converter = getTypeConverterNull(toRetType, fromRetType);
                if(converter != null) {
                    newHandle = MethodHandles.filterReturnValue(newHandle, converter);
                }
            }
        }

        // Take care of automatic conversions
        return newHandle.asType(fromType);
    }

    private static MethodHandle applyConverters(MethodHandle handle, int pos, List<MethodHandle> converters) {
        if(converters.isEmpty()) {
            return handle;
        }
        final MethodHandle newHandle =
                MethodHandles.filterArguments(handle, pos, converters.toArray(new MethodHandle[converters.size()]));
        converters.clear();
        return newHandle;
    }

    /**
     * Returns true if there might exist a conversion between the requested types (either an automatic JVM conversion,
     * or one provided by any available {@link GuardingTypeConverterFactory}), or false if there definitely does not
     * exist a conversion between the requested types. Note that returning true does not guarantee that the conversion
     * will succeed at runtime (notably, if the "from" or "to" types are sufficiently generic), but returning false
     * guarantees that it would fail.
     *
     * @param from the source type for the conversion
     * @param to the target type for the conversion
     * @return true if there can be a conversion, false if there can not.
     */
    public boolean canConvert(final Class<?> from, final Class<?> to) {
        return canAutoConvert(from, to) || canConvert.get(from).get(to).booleanValue();
    }

    /**
     * Determines which of the two type conversions from a source type to the two target types is preferred. This is
     * used for dynamic overloaded method resolution. If the source type is convertible to exactly one target type with
     * a method invocation conversion, it is chosen, otherwise available {@link ConversionComparator}s are consulted.
     * @param sourceType the source type.
     * @param targetType1 one potential target type
     * @param targetType2 another potential target type.
     * @return one of Comparison constants that establish which - if any - of the target types is preferable for the
     * conversion.
     */
    public Comparison compareConversion(Class<?> sourceType, Class<?> targetType1, Class<?> targetType2) {
        for(ConversionComparator comparator: comparators) {
            final Comparison result = comparator.compareConversion(sourceType, targetType1, targetType2);
            if(result != Comparison.INDETERMINATE) {
                return result;
            }
        }
        if(TypeUtilities.isMethodInvocationConvertible(sourceType, targetType1)) {
            if(!TypeUtilities.isMethodInvocationConvertible(sourceType, targetType2)) {
                return Comparison.TYPE_1_BETTER;
            }
        } else if(TypeUtilities.isMethodInvocationConvertible(sourceType, targetType2)) {
            return Comparison.TYPE_2_BETTER;
        }
        return Comparison.INDETERMINATE;
    }

    /**
     * Determines whether it's safe to perform an automatic conversion between the source and target class.
     *
     * @param fromType convert from this class
     * @param toType convert to this class
     * @return true if it's safe to let MethodHandles.convertArguments() to handle this conversion.
     */
    /*private*/ static boolean canAutoConvert(final Class<?> fromType, final Class<?> toType) {
        return TypeUtilities.isMethodInvocationConvertible(fromType, toType);
    }

    /*private*/ MethodHandle getCacheableTypeConverterNull(Class<?> sourceType, Class<?> targetType) {
        final MethodHandle converter = getCacheableTypeConverter(sourceType, targetType);
        return converter == IDENTITY_CONVERSION ? null : converter;
    }

    /*private*/ MethodHandle getTypeConverterNull(Class<?> sourceType, Class<?> targetType) {
        try {
            return getCacheableTypeConverterNull(sourceType, targetType);
        } catch(NotCacheableConverter e) {
            return e.converter;
        }
    }

    /*private*/ MethodHandle getCacheableTypeConverter(Class<?> sourceType, Class<?> targetType) {
        return converterMap.get(sourceType).get(targetType);
    }

    /**
     * Given a source and target type, returns a method handle that converts between them. Never returns null; in worst
     * case it will return an identity conversion (that might fail for some values at runtime). You can use this method
     * if you have a piece of your program that is written in Java, and you need to reuse existing type conversion
     * machinery in a non-invokedynamic context.
     * @param sourceType the type to convert from
     * @param targetType the type to convert to
     * @return a method handle performing the conversion.
     */
    public MethodHandle getTypeConverter(Class<?> sourceType, Class<?> targetType) {
        try {
            return converterIdentityMap.get(sourceType).get(targetType);
        } catch(NotCacheableConverter e) {
            return e.converter;
        }
    }

    /*private*/ MethodHandle createConverter(Class<?> sourceType, Class<?> targetType) throws Exception {
        final MethodType type = MethodType.methodType(targetType, sourceType);
        final MethodHandle identity = IDENTITY_CONVERSION.asType(type);
        MethodHandle last = identity;
        boolean cacheable = true;
        for(int i = factories.length; i-- > 0;) {
            final GuardedTypeConversion next = factories[i].convertToType(sourceType, targetType);
            if(next != null) {
                cacheable = cacheable && next.isCacheable();
                final GuardedInvocation conversionInvocation = next.getConversionInvocation();
                conversionInvocation.assertType(type);
                last = conversionInvocation.compose(last);
            }
        }
        if(last == identity) {
            return IDENTITY_CONVERSION;
        }
        if(cacheable) {
            return last;
        }
        throw new NotCacheableConverter(last);
    }

    /*private*/ static final MethodHandle IDENTITY_CONVERSION = MethodHandles.identity(Object.class);

    private static class NotCacheableConverter extends RuntimeException {
        final MethodHandle converter;

        NotCacheableConverter(final MethodHandle converter) {
            super("", null, false, false);
            this.converter = converter;
        }
    }
}
