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

import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodHandles.Lookup;
import java.util.Objects;
import jdk.internal.dynalink.CallSiteDescriptor;

/**
 * A base class for call site descriptor implementations. Provides
 * reconstruction of the name from the tokens, as well as generally useful
 * {@code equals}, {@code hashCode}, and {@code toString} methods. In order to
 * both prevent unprivileged access to its internal {@link MethodHandles.Lookup}
 * object, and at the same time not force privileged access to it from
 * {@code equals}, {@code hashCode}, and {@code toString} methods, subclasses
 * must implement {@link #lookupEquals(AbstractCallSiteDescriptor)},
 * {@link #lookupHashCode()} and {@link #lookupToString()} methods.
 * Additionally, {@link #equalsInKind(AbstractCallSiteDescriptor)} should be
 * overridden instead of {@link #equals(Object)} to compare descriptors in
 * subclasses; it is only necessary if they have implementation-specific
 * properties other than the standard name, type, and lookup.
 * @param <T> The call site descriptor subclass
 */
public abstract class AbstractCallSiteDescriptor<T extends AbstractCallSiteDescriptor<T>> implements CallSiteDescriptor {

    @Override
    public String getName() {
        return appendName(new StringBuilder(getNameLength())).toString();
    }

    /**
     * Checks if this call site descriptor is equality to another object. It is
     * considered equal iff and only if they belong to the exact same class, and
     * have the same name, method type, and lookup. Subclasses with additional
     * properties should override
     * {@link #equalsInKind(AbstractCallSiteDescriptor)} instead of this method.
     * @param obj the object checked for equality
     * @return true if they are equal, false otherwise
     */
    @SuppressWarnings("unchecked")
    @Override
    public boolean equals(final Object obj) {
        return obj != null && obj.getClass() == getClass() && equalsInKind((T)obj);
    }

    /**
     * Returns true if this call site descriptor is equal to the passed,
     * non-null call site descriptor of the same class.
     * @param csd the other call site descriptor.
     * @return true if they are equal.
     */
    protected boolean equalsInKind(final T csd) {
        if(csd == this) {
            return true;
        }
        final int ntc = getNameTokenCount();
        if(ntc != csd.getNameTokenCount()) {
            return false;
        }
        for(int i = ntc; i-- > 0;) { // Reverse order as variability is higher at the end
            if(!Objects.equals(getNameToken(i), csd.getNameToken(i))) {
                return false;
            }
        }
        if(!getMethodType().equals(csd.getMethodType())) {
            return false;
        }
        return lookupEquals(csd);
    }

    /**
     * Returns true if this call site descriptor's lookup is equal to the other
     * call site descriptor's lookup. Typical implementation should try to
     * obtain the other lookup directly without going through privileged
     * {@link #getLookup()} (e.g. by reading the field as the type system
     * enforces that they are of the same class) and then delegate to
     * {@link #lookupsEqual(MethodHandles.Lookup, MethodHandles.Lookup)}.
     * @param other the other lookup
     * @return true if the lookups are equal
     */
    protected abstract boolean lookupEquals(T other);

    /**
     * Compares two lookup objects for value-based equality. They are considered
     * equal if they have the same
     * {@link java.lang.invoke.MethodHandles.Lookup#lookupClass()} and
     * {@link java.lang.invoke.MethodHandles.Lookup#lookupModes()}.
     * @param l1 first lookup
     * @param l2 second lookup
     * @return true if the two lookups are equal, false otherwise.
     */
    protected static boolean lookupsEqual(final Lookup l1, final Lookup l2) {
        if(l1 == l2) {
            return true;
        } else if (l1 == null || l2 == null) {
            return false;
        } else if(l1.lookupClass() != l2.lookupClass()) {
            return false;
        }
        return l1.lookupModes() == l2.lookupModes();
    }

    @Override
    public int hashCode() {
        int h = lookupHashCode();
        final int c = getNameTokenCount();
        for(int i = 0; i < c; ++i) {
            h = h * 31 + getNameToken(i).hashCode();
        }
        return h * 31 + getMethodType().hashCode();
    }

    /**
     * Return the hash code of this call site descriptor's {@link Lookup}
     * object. Typical implementation should delegate to
     * {@link #lookupHashCode(MethodHandles.Lookup)}.
     * @return the hash code of this call site descriptor's {@link Lookup}
     * object.
     */
    protected abstract int lookupHashCode();

    /**
     * Returns a value-based hash code for the passed lookup object. It is
     * based on the lookup object's
     * {@link java.lang.invoke.MethodHandles.Lookup#lookupClass()} and
     * {@link java.lang.invoke.MethodHandles.Lookup#lookupModes()} values.
     * @param lookup the lookup object.
     * @return a hash code for the object. Returns 0 for null.
     */
    protected static int lookupHashCode(final Lookup lookup) {
        return lookup != null ? lookup.lookupClass().hashCode() + 31 * lookup.lookupModes() : 0;
    }

    @Override
    public String toString() {
        final String mt = getMethodType().toString();
        final String l = lookupToString();
        final StringBuilder b = new StringBuilder(l.length() + 1 + mt.length() + getNameLength());
        return appendName(b).append(mt).append("@").append(l).toString();
    }

    /**
     * Return a string representation of this call site descriptor's
     * {@link Lookup} object. Typically will return
     * {@link java.lang.invoke.MethodHandles.Lookup#toString()}.
     * @return a string representation of this call site descriptor's
     * {@link Lookup} object.
     */
    protected abstract String lookupToString();

    private int getNameLength() {
        final int c = getNameTokenCount();
        int l = 0;
        for(int i = 0; i < c; ++i) {
            l += getNameToken(i).length();
        }
        return l +  c - 1;
    }

    private StringBuilder appendName(final StringBuilder b) {
        b.append(getNameToken(0));
        final int c = getNameTokenCount();
        for(int i = 1; i < c; ++i) {
            b.append(':').append(getNameToken(i));
        }
        return b;
    }
}
