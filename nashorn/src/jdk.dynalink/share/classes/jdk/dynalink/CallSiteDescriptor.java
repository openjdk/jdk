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

package jdk.dynalink;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodHandles.Lookup;
import java.lang.invoke.MethodType;
import java.util.Objects;

/**
 * Call site descriptors contain all the information necessary for linking a
 * call site. This information is normally passed as parameters to bootstrap
 * methods and consists of the {@code MethodHandles.Lookup} object on the caller
 * class in which the call site occurs, the dynamic operation at the call
 * site, and the method type of the call site. {@code CallSiteDescriptor}
 * objects are used in Dynalink to capture and store these parameters for
 * subsequent use by the {@link DynamicLinker}.
 * <p>
 * The constructors of built-in {@link RelinkableCallSite} implementations all
 * take a call site descriptor.
 * <p>
 * Call site descriptors must be immutable. You can use this class as-is or you
 * can subclass it, especially if you need to add further information to the
 * descriptors (typically, values passed in additional parameters to the
 * bootstrap method. Since the descriptors must be immutable, you can set up a
 * cache for equivalent descriptors to have the call sites share them.
 */
public class CallSiteDescriptor {
    private final MethodHandles.Lookup lookup;
    private final Operation operation;
    private final MethodType methodType;

    /**
     * The name of a runtime permission to invoke the {@link #getLookup()}
     * method.
     */
    public static final String GET_LOOKUP_PERMISSION_NAME = "dynalink.getLookup";

    private static final RuntimePermission GET_LOOKUP_PERMISSION = new RuntimePermission(GET_LOOKUP_PERMISSION_NAME);

    /**
     * Creates a new call site descriptor.
     * @param lookup the lookup object describing the class the call site belongs to.
     * @param operation the dynamic operation at the call site.
     * @param methodType the method type of the call site.
     */
    public CallSiteDescriptor(final Lookup lookup, final Operation operation, final MethodType methodType) {
        this.lookup = Objects.requireNonNull(lookup, "lookup");
        this.operation = Objects.requireNonNull(operation, "name");
        this.methodType = Objects.requireNonNull(methodType, "methodType");
    }

    /**
     * Returns the operation at the call site.
     * @return the operation at the call site.
     */
    public final Operation getOperation() {
        return operation;
    }

    /**
     * The type of the method at the call site.
     *
     * @return type of the method at the call site.
     */
    public final MethodType getMethodType() {
        return methodType;
    }

    /**
     * Returns the lookup that should be used to find method handles to set as
     * targets of the call site described by this descriptor. When creating
     * descriptors from a {@link java.lang.invoke} bootstrap method, it should
     * be the lookup passed to the bootstrap.
     * @return the lookup that should be used to find method handles to set as
     * targets of the call site described by this descriptor.
     * @throws SecurityException if the lookup isn't the
     * {@link MethodHandles#publicLookup()} and a security manager is present,
     * and a check for {@code RuntimePermission("dynalink.getLookup")} fails.
     */
    public final Lookup getLookup() {
        final SecurityManager sm = System.getSecurityManager();
        if (sm != null && lookup != MethodHandles.publicLookup()) {
            sm.checkPermission(GET_LOOKUP_PERMISSION);
        }
        return lookup;
    }

    /**
     * Returns the value of {@link #getLookup()} without a security check. Can
     * be used by subclasses to access the lookup quickly.
     * @return same as returned value of {@link #getLookup()}.
     */
    protected final Lookup getLookupPrivileged() {
        return lookup;
    }

    /**
     * Creates a new call site descriptor from this descriptor, which is
     * identical to this, except it changes the method type. Invokes
     * {@link #changeMethodTypeInternal(MethodType)} and checks that it returns
     * a descriptor of the same class as this descriptor.
     *
     * @param newMethodType the new method type
     * @return a new call site descriptor, with the method type changed.
     * @throws RuntimeException if {@link #changeMethodTypeInternal(MethodType)}
     * returned a descriptor of different class than this object.
     * @throws NullPointerException if {@link #changeMethodTypeInternal(MethodType)}
     * returned null.
     */
    public final CallSiteDescriptor changeMethodType(final MethodType newMethodType) {
        final CallSiteDescriptor changed = Objects.requireNonNull(
                changeMethodTypeInternal(newMethodType),
                "changeMethodTypeInternal() must not return null.");

        if (getClass() != changed.getClass()) {
            throw new RuntimeException(
                    "changeMethodTypeInternal() must return an object of the same class it is invoked on.");
        }

        return changed;
    }

    /**
     * Creates a new call site descriptor from this descriptor, which is
     * identical to this, except it changes the method type. Subclasses must
     * override this method to return an object of their exact class.
     *
     * @param newMethodType the new method type
     * @return a new call site descriptor, with the method type changed.
     */
    protected CallSiteDescriptor changeMethodTypeInternal(final MethodType newMethodType) {
        return new CallSiteDescriptor(lookup, operation, newMethodType);
    }

    /**
     * Returns true if this call site descriptor is equal to the passed object.
     * It is considered equal if the other object is of the exact same class,
     * their operations and method types are equal, and their lookups have the
     * same {@link java.lang.invoke.MethodHandles.Lookup#lookupClass()} and
     * {@link java.lang.invoke.MethodHandles.Lookup#lookupModes()}.
     */
    @Override
    public boolean equals(final Object obj) {
        if (obj == this) {
            return true;
        } else if (obj == null) {
            return false;
        } else if (obj.getClass() != getClass()) {
            return false;
        }
        final CallSiteDescriptor other = (CallSiteDescriptor)obj;
        return operation.equals(other.operation) &&
               methodType.equals(other.methodType) &&
               lookupsEqual(lookup, other.lookup);
    }

    /**
     * Compares two lookup objects for value-based equality. They are considered
     * equal if they have the same
     * {@link java.lang.invoke.MethodHandles.Lookup#lookupClass()} and
     * {@link java.lang.invoke.MethodHandles.Lookup#lookupModes()}.
     * @param l1 first lookup
     * @param l2 second lookup
     * @return true if the two lookups are equal, false otherwise.
     */
    private static boolean lookupsEqual(final Lookup l1, final Lookup l2) {
        return l1.lookupClass() == l2.lookupClass() && l1.lookupModes() == l2.lookupModes();
    }

    /**
     * Returns a value-based hash code of this call site descriptor computed
     * from its operation, method type, and lookup object's lookup class and
     * lookup modes.
     * @return value-based hash code for this call site descriptor.
     */
    @Override
    public int hashCode() {
        return operation.hashCode() + 31 * methodType.hashCode() + 31 * 31 * lookupHashCode(lookup);
    }

    /**
     * Returns a value-based hash code for the passed lookup object. It is
     * based on the lookup object's
     * {@link java.lang.invoke.MethodHandles.Lookup#lookupClass()} and
     * {@link java.lang.invoke.MethodHandles.Lookup#lookupModes()} values.
     * @param lookup the lookup object.
     * @return a hash code for the object..
     */
    private static int lookupHashCode(final Lookup lookup) {
        return lookup.lookupClass().hashCode() + 31 * lookup.lookupModes();
    }

    /**
     * Returns the string representation of this call site descriptor, of the
     * format {@code name(parameterTypes)returnType@lookup}.
     */
    @Override
    public String toString() {
        final String mt = methodType.toString();
        final String l = lookup.toString();
        final String o = operation.toString();
        final StringBuilder b = new StringBuilder(o.length() + mt.length() + 1 + l.length());
        return b.append(o).append(mt).append('@').append(l).toString();
    }
}
