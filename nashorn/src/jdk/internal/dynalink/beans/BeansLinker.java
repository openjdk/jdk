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

package jdk.internal.dynalink.beans;

import java.lang.invoke.MethodHandles;
import java.util.Collection;
import java.util.Collections;
import jdk.internal.dynalink.CallSiteDescriptor;
import jdk.internal.dynalink.DynamicLinkerFactory;
import jdk.internal.dynalink.linker.GuardedInvocation;
import jdk.internal.dynalink.linker.GuardingDynamicLinker;
import jdk.internal.dynalink.linker.LinkRequest;
import jdk.internal.dynalink.linker.LinkerServices;
import jdk.internal.dynalink.linker.TypeBasedGuardingDynamicLinker;

/**
 * A linker for POJOs. Normally used as the ultimate fallback linker by the {@link DynamicLinkerFactory} so it is given
 * the chance to link calls to all objects that no other language runtime recognizes. Specifically, this linker will:
 * <ul>
 * <li>expose all public methods of form {@code setXxx()}, {@code getXxx()}, and {@code isXxx()} as property setters and
 * getters for {@code dyn:setProp} and {@code dyn:getProp} operations;</li>
 * <li>expose all public methods for invocation through {@code dyn:callMethod} operation;</li>
 * <li>expose all public methods for retrieval for {@code dyn:getMethod} operation; the methods thus retrieved can then
 * be invoked using {@code dyn:call};</li>
 * <li>expose all public fields as properties, unless there are getters or setters for the properties of the same name;</li>
 * <li>expose {@code dyn:getLength}, {@code dyn:getElem} and {@code dyn:setElem} on native Java arrays, as well as
 * {@link java.util.List} and {@link java.util.Map} objects; ({@code dyn:getLength} works on any
 * {@link java.util.Collection});</li>
 * <li>expose a virtual property named {@code length} on Java arrays;</li>
 * <li>expose {@code dyn:new} on instances of {@link StaticClass} as calls to constructors, including those static class
 * objects that represent Java arrays (their constructors take a single {@code int} parameter representing the length of
 * the array to create);</li>
 * <li>expose static methods, fields, and properties of classes in a similar manner to how instance method, fields, and
 * properties are exposed, on {@link StaticClass} objects.</li>
 * <li>expose a virtual property named {@code static} on instances of {@link java.lang.Class} to access their
 * {@link StaticClass}.</li>
 * </ul>
 * <p><strong>Overloaded method resolution</strong> is performed automatically for property setters, methods, and
 * constructors. Additionally, manual overloaded method selection is supported by having a call site specify a name for
 * a method that contains an explicit signature, i.e. {@code dyn:getMethod:parseInt(String,int)}. You can use
 * non-qualified class names in such signatures regardless of those classes' packages, they will match any class with
 * the same non-qualified name. You only have to use a fully qualified class name in case non-qualified class names
 * would cause selection ambiguity (that is extremely rare).</p>
 * <p><strong>Variable argument invocation</strong> is handled for both methods and constructors.</p>
 * <p>Currently, only public fields and methods are supported. Any Lookup objects passed in the
 * {@link LinkRequest}s are ignored and {@link MethodHandles#publicLookup()} is used instead.</p>
 *
 * @author Attila Szegedi
 */
public class BeansLinker implements GuardingDynamicLinker {
    private static final ClassValue<TypeBasedGuardingDynamicLinker> linkers = new ClassValue<TypeBasedGuardingDynamicLinker>() {
        @Override
        protected TypeBasedGuardingDynamicLinker computeValue(Class<?> clazz) {
            // If ClassValue.put() were public, we could just pre-populate with these known mappings...
            return
                clazz == Class.class ? new ClassLinker() :
                clazz == StaticClass.class ? new StaticClassLinker() :
                DynamicMethod.class.isAssignableFrom(clazz) ? new DynamicMethodLinker() :
                new BeanLinker(clazz);
        }
    };

    /**
     * Creates a new POJO linker.
     */
    public BeansLinker() {
    }

    /**
     * Returns a bean linker for a particular single class. Useful when you need to override or extend the behavior of
     * linking for some classes in your language runtime's linker, but still want to delegate to the default behavior in
     * some cases.
     * @param clazz the class
     * @return a bean linker for that class
     */
    public static TypeBasedGuardingDynamicLinker getLinkerForClass(Class<?> clazz) {
        return linkers.get(clazz);
    }

    /**
     * Returns true if the object is a Dynalink Java dynamic method.
     *
     * @param obj the object we want to test for being a dynamic method
     * @return true if it is a dynamic method, false otherwise.
     */
    public static boolean isDynamicMethod(final Object obj) {
        return obj instanceof DynamicMethod;
    }

    /**
     * Returns a collection of names of all readable instance properties of a class.
     * @param clazz the class
     * @return a collection of names of all readable instance properties of a class.
     */
    public static Collection<String> getReadableInstancePropertyNames(Class<?> clazz) {
        TypeBasedGuardingDynamicLinker linker = getLinkerForClass(clazz);
        if(linker instanceof BeanLinker) {
            return ((BeanLinker)linker).getReadablePropertyNames();
        }
        return Collections.emptySet();
    }

    /**
     * Returns a collection of names of all writable instance properties of a class.
     * @param clazz the class
     * @return a collection of names of all writable instance properties of a class.
     */
    public static Collection<String> getWritableInstancePropertyNames(Class<?> clazz) {
        TypeBasedGuardingDynamicLinker linker = getLinkerForClass(clazz);
        if(linker instanceof BeanLinker) {
            return ((BeanLinker)linker).getWritablePropertyNames();
        }
        return Collections.emptySet();
    }

    /**
     * Returns a collection of names of all instance methods of a class.
     * @param clazz the class
     * @return a collection of names of all instance methods of a class.
     */
    public static Collection<String> getInstanceMethodNames(Class<?> clazz) {
        TypeBasedGuardingDynamicLinker linker = getLinkerForClass(clazz);
        if(linker instanceof BeanLinker) {
            return ((BeanLinker)linker).getMethodNames();
        }
        return Collections.emptySet();
    }

    /**
     * Returns a collection of names of all readable static properties of a class.
     * @param clazz the class
     * @return a collection of names of all readable static properties of a class.
     */
    public static Collection<String> getReadableStaticPropertyNames(Class<?> clazz) {
        return StaticClassLinker.getReadableStaticPropertyNames(clazz);
    }

    /**
     * Returns a collection of names of all writable static properties of a class.
     * @param clazz the class
     * @return a collection of names of all writable static properties of a class.
     */
    public static Collection<String> getWritableStaticPropertyNames(Class<?> clazz) {
        return StaticClassLinker.getWritableStaticPropertyNames(clazz);
    }

    /**
     * Returns a collection of names of all static methods of a class.
     * @param clazz the class
     * @return a collection of names of all static methods of a class.
     */
    public static Collection<String> getStaticMethodNames(Class<?> clazz) {
        return StaticClassLinker.getStaticMethodNames(clazz);
    }

    @Override
    public GuardedInvocation getGuardedInvocation(LinkRequest request, final LinkerServices linkerServices)
            throws Exception {
        final CallSiteDescriptor callSiteDescriptor = request.getCallSiteDescriptor();
        final int l = callSiteDescriptor.getNameTokenCount();
        // All names conforming to the dynalang MOP should have at least two tokens, the first one being "dyn"
        if(l < 2 || "dyn" != callSiteDescriptor.getNameToken(CallSiteDescriptor.SCHEME)) {
            return null;
        }

        final Object receiver = request.getReceiver();
        if(receiver == null) {
            // Can't operate on null
            return null;
        }
        return getLinkerForClass(receiver.getClass()).getGuardedInvocation(request, linkerServices);
    }
}
