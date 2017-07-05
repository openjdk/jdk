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

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Array;
import java.util.Arrays;
import java.util.Collection;
import jdk.internal.dynalink.CallSiteDescriptor;
import jdk.internal.dynalink.beans.GuardedInvocationComponent.ValidationType;
import jdk.internal.dynalink.linker.GuardedInvocation;
import jdk.internal.dynalink.linker.LinkRequest;
import jdk.internal.dynalink.linker.LinkerServices;
import jdk.internal.dynalink.linker.TypeBasedGuardingDynamicLinker;
import jdk.internal.dynalink.support.Lookup;

/**
 * Provides a linker for the {@link StaticClass} objects.
 * @author Attila Szegedi
 */
class StaticClassLinker implements TypeBasedGuardingDynamicLinker {
    private static final ClassValue<SingleClassStaticsLinker> linkers = new ClassValue<SingleClassStaticsLinker>() {
        @Override
        protected SingleClassStaticsLinker computeValue(Class<?> clazz) {
            return new SingleClassStaticsLinker(clazz);
        }
    };

    private static class SingleClassStaticsLinker extends AbstractJavaLinker {
        private final DynamicMethod constructor;

        SingleClassStaticsLinker(Class<?> clazz) {
            super(clazz, IS_CLASS.bindTo(clazz));
            // Map "staticClassObject.class" to StaticClass.getRepresentedClass(). Some adventurous soul could subclass
            // StaticClass, so we use INSTANCE_OF validation instead of EXACT_CLASS.
            setPropertyGetter("class", GET_CLASS, ValidationType.INSTANCE_OF);
            constructor = createConstructorMethod(clazz);
        }

        /**
         * Creates a dynamic method containing all overloads of a class' public constructor
         * @param clazz the target class
         * @return a dynamic method containing all overloads of a class' public constructor. If the class has no public
         * constructors, returns null.
         */
        private static DynamicMethod createConstructorMethod(Class<?> clazz) {
            if(clazz.isArray()) {
                final MethodHandle boundArrayCtor = ARRAY_CTOR.bindTo(clazz.getComponentType());
                return new SimpleDynamicMethod(StaticClassIntrospector.editConstructorMethodHandle(
                        boundArrayCtor.asType(boundArrayCtor.type().changeReturnType(clazz))), clazz, "<init>");
            }

            return createDynamicMethod(Arrays.asList(clazz.getConstructors()), clazz, "<init>");
        }

        @Override
        FacetIntrospector createFacetIntrospector() {
            return new StaticClassIntrospector(clazz);
        }

        @Override
        public GuardedInvocation getGuardedInvocation(LinkRequest request, LinkerServices linkerServices)
                throws Exception {
            final GuardedInvocation gi = super.getGuardedInvocation(request, linkerServices);
            if(gi != null) {
                return gi;
            }
            final CallSiteDescriptor desc = request.getCallSiteDescriptor();
            final String op = desc.getNameToken(CallSiteDescriptor.OPERATOR);
            if("new" == op && constructor != null) {
                final MethodHandle ctorInvocation = constructor.getInvocation(desc, linkerServices);
                if(ctorInvocation != null) {
                    return new GuardedInvocation(ctorInvocation, getClassGuard(desc.getMethodType()));
                }
            }
            return null;
        }
    }

    static Collection<String> getReadableStaticPropertyNames(Class<?> clazz) {
        return linkers.get(clazz).getReadablePropertyNames();
    }

    static Collection<String> getWritableStaticPropertyNames(Class<?> clazz) {
        return linkers.get(clazz).getWritablePropertyNames();
    }

    static Collection<String> getStaticMethodNames(Class<?> clazz) {
        return linkers.get(clazz).getMethodNames();
    }

    @Override
    public GuardedInvocation getGuardedInvocation(LinkRequest request, LinkerServices linkerServices) throws Exception {
        final Object receiver = request.getReceiver();
        if(receiver instanceof StaticClass) {
            return linkers.get(((StaticClass)receiver).getRepresentedClass()).getGuardedInvocation(request,
                    linkerServices);
        }
        return null;
    }

    @Override
    public boolean canLinkType(Class<?> type) {
        return type == StaticClass.class;
    }

    /*private*/ static final MethodHandle GET_CLASS = new Lookup(MethodHandles.lookup()).findVirtual(StaticClass.class,
            "getRepresentedClass", MethodType.methodType(Class.class));

    /*private*/ static final MethodHandle IS_CLASS = new Lookup(MethodHandles.lookup()).findStatic(StaticClassLinker.class,
            "isClass", MethodType.methodType(Boolean.TYPE, Class.class, Object.class));

    /*private*/ static final MethodHandle ARRAY_CTOR = Lookup.PUBLIC.findStatic(Array.class, "newInstance",
            MethodType.methodType(Object.class, Class.class, int.class));

    @SuppressWarnings("unused")
    private static boolean isClass(Class<?> clazz, Object obj) {
        return obj instanceof StaticClass && ((StaticClass)obj).getRepresentedClass() == clazz;
    }
}
