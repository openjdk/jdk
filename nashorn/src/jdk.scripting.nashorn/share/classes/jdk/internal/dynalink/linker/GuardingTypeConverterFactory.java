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

import java.lang.invoke.MethodHandles;
import java.util.function.Supplier;
import jdk.internal.dynalink.CallSiteDescriptor;
import jdk.internal.dynalink.beans.BeansLinker;
import jdk.internal.dynalink.linker.support.TypeUtilities;

/**
 * Optional interface that can be implemented by {@link GuardingDynamicLinker} implementations to provide
 * language-runtime specific implicit type conversion capabilities. Note that if you implement this interface, you will
 * very likely want to implement {@link ConversionComparator} interface too, as your additional language-specific
 * conversions, in absence of a strategy for prioritizing these conversions, will cause more ambiguity for
 * {@link BeansLinker} in selecting the correct overload when trying to link to an overloaded Java method.
 */
public interface GuardingTypeConverterFactory {
    /**
     * Returns a guarded type conversion that receives an Object of the specified source type and returns an Object
     * converted to the specified target type. The type of the invocation is targetType(sourceType), while the type of
     * the guard is boolean(sourceType). Note that this will never be invoked for type conversions allowed by the JLS
     * 5.3 "Method Invocation Conversion", see {@link TypeUtilities#isMethodInvocationConvertible(Class, Class)} for
     * details. An implementation can assume it is never requested to produce a converter for these conversions.
     *
     * @param sourceType source type
     * @param targetType the target type.
     * @param lookupSupplier a supplier for retrieving the lookup of the class
     * on whose behalf a type converter is requested. When a converter is
     * requested as part of linking an {@code invokedynamic} instruction the
     * supplier will return the lookup passed to the bootstrap method, otherwise
     * it will return the public lookup. For most conversions, the lookup is
     * irrelevant. A typical case where the lookup might be needed is when the
     * converter creates a Java adapter class on the fly (e.g. to convert some
     * object from the dynamic language into a Java interface for
     * interoperability). Invoking the {@link Supplier#get()} method on the
     * passed supplier will be subject to the same security checks as
     * {@link CallSiteDescriptor#getLookup()}. An implementation should avoid
     * retrieving the lookup if it is not needed so to avoid the expense of
     * {@code AccessController.doPrivileged} call.
     * @return a guarded invocation that can take an object (if it passes guard)
     * and return another object that is its representation coerced into the target type. In case the factory is certain
     * it is unable to handle a conversion, it can return null. In case the factory is certain that it can always handle
     * the conversion, it can return an unconditional invocation (one whose guard is null).
     * @throws Exception if there was an error during creation of the converter
     */
    public GuardedInvocation convertToType(Class<?> sourceType, Class<?> targetType, Supplier<MethodHandles.Lookup> lookupSupplier) throws Exception;
}
