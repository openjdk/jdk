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

package jdk.internal.dynalink.linker;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import jdk.internal.dynalink.DynamicLinker;
import jdk.internal.dynalink.DynamicLinkerFactory;
import jdk.internal.dynalink.linker.ConversionComparator.Comparison;
import jdk.internal.dynalink.support.TypeUtilities;

/**
 * Interface for services provided to {@link GuardingDynamicLinker} instances by the {@link DynamicLinker} that owns
 * them. You can think of it as the interface of the {@link DynamicLinker} that faces the {@link GuardingDynamicLinker}
 * s.
 */
public interface LinkerServices {
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
     * @return a method handle that is a suitable combination of {@link MethodHandle#asType(MethodType)},
     * {@link MethodHandles#filterArguments(MethodHandle, int, MethodHandle...)}, and
     * {@link MethodHandles#filterReturnValue(MethodHandle, MethodHandle)} with
     * {@link GuardingTypeConverterFactory}-produced type converters as filters.
     */
    public MethodHandle asType(MethodHandle handle, MethodType fromType);

    /**
     * Similar to {@link #asType(MethodHandle, MethodType)} except it only converts the return type of the method handle
     * when it can be done using a conversion that loses neither precision nor magnitude, otherwise it leaves it
     * unchanged. The idea is that other conversions should not be performed by individual linkers, but instead the
     * {@link DynamicLinkerFactory#setPrelinkFilter(jdk.internal.dynalink.GuardedInvocationFilter) pre-link filter of
     * the dynamic linker} should implement the strategy of dealing with potentially lossy return type conversions in a
     * manner specific to the language runtime.
     *
     * @param handle target method handle
     * @param fromType the types of source arguments
     * @return a method handle that is a suitable combination of {@link MethodHandle#asType(MethodType)}, and
     * {@link MethodHandles#filterArguments(MethodHandle, int, MethodHandle...)} with
     * {@link GuardingTypeConverterFactory}-produced type converters as filters.
     */
    public default MethodHandle asTypeLosslessReturn(final MethodHandle handle, final MethodType fromType) {
        final Class<?> handleReturnType = handle.type().returnType();
        return asType(handle, TypeUtilities.isConvertibleWithoutLoss(handleReturnType, fromType.returnType()) ?
                fromType : fromType.changeReturnType(handleReturnType));
    }

    /**
     * Given a source and target type, returns a method handle that converts between them. Never returns null; in worst
     * case it will return an identity conversion (that might fail for some values at runtime). You rarely need to use
     * this method directly; you should mostly rely on {@link #asType(MethodHandle, MethodType)} instead. You really
     * only need this method if you have a piece of your program that is written in Java, and you need to reuse existing
     * type conversion machinery in a non-invokedynamic context.
     * @param sourceType the type to convert from
     * @param targetType the type to convert to
     * @return a method handle performing the conversion.
     */
    public MethodHandle getTypeConverter(Class<?> sourceType, Class<?> targetType);

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
    public boolean canConvert(Class<?> from, Class<?> to);

    /**
     * Creates a guarded invocation using the {@link DynamicLinker} that exposes this linker services interface. Linkers
     * can typically use them to delegate linking of wrapped objects.
     *
     * @param linkRequest a request for linking the invocation
     * @return a guarded invocation linked by the top-level linker (or any of its delegates). Can be null if no
     * available linker is able to link the invocation.
     * @throws Exception in case the top-level linker throws an exception
     */
    public GuardedInvocation getGuardedInvocation(LinkRequest linkRequest) throws Exception;

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
    public Comparison compareConversion(Class<?> sourceType, Class<?> targetType1, Class<?> targetType2);

    /**
     * Modifies the method handle so that any parameters that can receive potentially internal language runtime objects
     * will have a filter added on them to prevent them from escaping, potentially by wrapping them.
     * It can also potentially add an unwrapping filter to the return value. Basically transforms the method
     * handle using the transformer configured by {@link DynamicLinkerFactory#setInternalObjectsFilter(MethodHandleTransformer)}.
     * @param target the target method handle
     * @return a method handle with parameters and/or return type potentially filtered for wrapping and unwrapping.
     */
    public MethodHandle filterInternalObjects(final MethodHandle target);
}
