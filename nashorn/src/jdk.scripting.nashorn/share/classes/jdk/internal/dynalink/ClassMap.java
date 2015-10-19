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

package jdk.internal.dynalink;

import java.lang.ref.Reference;
import java.lang.ref.SoftReference;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import jdk.internal.dynalink.support.Guards;

/**
 * A dual map that can either strongly or weakly reference a given class depending on whether the class is visible from
 * a class loader or not.
 *
 * @param <T> the type of the values in the map
 */
abstract class ClassMap<T> {
    private final ConcurrentMap<Class<?>, T> map = new ConcurrentHashMap<>();
    private final Map<Class<?>, Reference<T>> weakMap = new WeakHashMap<>();
    private final ClassLoader classLoader;

    /**
     * Creates a new class map. It will use strong references for all keys and values where the key is a class visible
     * from the class loader, and will use weak keys and soft values for all other classes.
     *
     * @param classLoader the classloader that determines strong referenceability.
     */
    ClassMap(final ClassLoader classLoader) {
        this.classLoader = classLoader;
    }

    /**
     * Compute the value associated with the given class. It is possible that the method will be invoked several times
     * (or even concurrently) for the same class parameter.
     *
     * @param clazz the class to compute the value for
     * @return the return value. Must not be null.
     */
    abstract T computeValue(Class<?> clazz);

    /**
     * Returns the value associated with the class
     *
     * @param clazz the class
     * @return the value associated with the class
     */
    T get(final Class<?> clazz) {
        // Check in fastest first - objects we're allowed to strongly reference
        final T v = map.get(clazz);
        if(v != null) {
            return v;
        }
        // Check objects we're not allowed to strongly reference
        Reference<T> ref;
        synchronized(weakMap) {
            ref = weakMap.get(clazz);
        }
        if(ref != null) {
            final T refv = ref.get();
            if(refv != null) {
                return refv;
            }
        }
        // Not found in either place; create a new value
        final T newV = computeValue(clazz);
        assert newV != null;

        final ClassLoader clazzLoader = AccessController.doPrivileged(new PrivilegedAction<ClassLoader>() {
            @Override
            public ClassLoader run() {
                return clazz.getClassLoader();
            }
        }, ClassLoaderGetterContextProvider.GET_CLASS_LOADER_CONTEXT);

        // If allowed to strongly reference, put it in the fast map
        if(Guards.canReferenceDirectly(classLoader, clazzLoader)) {
            final T oldV = map.putIfAbsent(clazz, newV);
            return oldV != null ? oldV : newV;
        }
        // Otherwise, put it into the weak map
        synchronized(weakMap) {
            ref = weakMap.get(clazz);
            if(ref != null) {
                final T oldV = ref.get();
                if(oldV != null) {
                    return oldV;
                }
            }
            weakMap.put(clazz, new SoftReference<>(newV));
            return newV;
        }
    }
}
