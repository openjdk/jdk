/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
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
   Copyright 2015 Attila Szegedi

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

import java.util.Arrays;
import java.util.Objects;

/**
 * Describes an operation that is composed of at least two other operations. The
 * component operations are treated as alternatives to each other in order of
 * preference. The semantics of the composite operation is "first successful".
 * That is, a composite of {@code GET_PROPERTY|GET_ELEMENT:color} should be
 * interpreted as <i>get the property named "color" on the object, but if the
 * property does not exist, then get the collection element named "color"
 * instead</i>.
 * <p>
 * Composite operations are helpful in implementation of languages that
 * don't distinguish between one or more of the property, method, and element
 * namespaces, or when expressing operations against objects that can be
 * considered both ordinary objects and collections, e.g. Java
 * {@link java.util.Map} objects. A composite operation
 * {@code GET_PROPERTY|GET_ELEMENT:empty} against a Java map will always match
 * the {@link java.util.Map#isEmpty()} property, but
 * {@code GET_ELEMENT|GET_PROPERTY:empty} will actually match a map element with
 * key {@code "empty"} if the map contains that key, and only fall back to the
 * {@code isEmpty()} property getter if the map does not contain the key. If
 * the source language mandates this semantics, it can be easily achieved using
 * composite operations.
 * <p>
 * Even if the language itself doesn't distinguish between some of the
 * namespaces, it can be helpful to map different syntaxes to different
 * compositions. E.g. the source expression {@code obj.color} could map to
 * {@code GET_PROPERTY|GET_ELEMENT|GET_METHOD:color}, but a different source
 * expression that looks like collection element access {@code obj[key]} could
 * be expressed instead as {@code GET_ELEMENT|GET_PROPERTY|GET_METHOD}.
 * Finally, if the retrieved value is subsequently called, then it makes sense
 * to bring {@code GET_METHOD} to the front of the list: the getter part of the
 * source expression {@code obj.color()} should be
 * {@code GET_METHOD|GET_PROPERTY|GET_ELEMENT:color} and the one for
 * {@code obj[key]()} should be {@code GET_METHOD|GET_ELEMENT|GET_PROPERTY}.
 * <p>
 * The elements of a composite operation can not be composites or named
 * operations, but rather simple operations such are elements of
 * {@link StandardOperation}. A composite operation itself can serve as the base
 * operation of a named operation, though; a typical way to construct e.g. the
 * {@code GET_ELEMENT|GET_PROPERTY:empty} from above would be:
 * <pre>
 * Operation getElementOrPropertyEmpty = new NamedOperation(
 *     new CompositeOperation(
 *         StandardOperation.GET_ELEMENT,
 *         StandardOperation.GET_PROPERTY),
 *     "empty");
 * </pre>
 * <p>
 * Not all compositions make sense. Typically, any combination in any order of
 * standard getter operations {@code GET_PROPERTY}, {@code GET_ELEMENT}, and
 * {@code GET_METHOD} make sense, as do combinations of {@code SET_PROPERTY} and
 * {@code SET_ELEMENT}; other standard operations should not be combined. The
 * constructor will allow any combination of operations, though.
 */
public class CompositeOperation implements Operation {
    private final Operation[] operations;

    /**
     * Constructs a new composite operation.
     * @param operations the components for this composite operation. The passed
     * array will be cloned.
     * @throws IllegalArgumentException if less than two components are
     * specified, or any component is itself a {@link CompositeOperation} or a
     * {@link NamedOperation}.
     * @throws NullPointerException if either the operations array or any of its
     * elements are {@code null}.
     */
    public CompositeOperation(final Operation... operations) {
        Objects.requireNonNull(operations, "operations array is null");
        if (operations.length < 2) {
            throw new IllegalArgumentException("Must have at least two operations");
        }
        final Operation[] clonedOps = operations.clone();
        for(int i = 0; i < clonedOps.length; ++i) {
            final Operation op = clonedOps[i];
            if (op == null) {
                throw new NullPointerException("operations[" + i + "] is null");
            } else if (op instanceof NamedOperation) {
                throw new IllegalArgumentException("operations[" + i + "] is a NamedOperation");
            } else if (op instanceof CompositeOperation) {
                throw new IllegalArgumentException("operations[" + i + "] is a CompositeOperation");
            }
        }
        this.operations = clonedOps;
    }

    /**
     * Returns the component operations in this composite operation. The
     * returned array is a copy and changes to it don't have effect on this
     * object.
     * @return the component operations in this composite operation.
     */
    public Operation[] getOperations() {
        return operations.clone();
    }

    /**
     * Returns the number of component operations in this composite operation.
     * @return the number of component operations in this composite operation.
     */
    public int getOperationCount() {
        return operations.length;
    }

    /**
     * Returns the i-th component operation in this composite operation.
     * @param i the operation index
     * @return the i-th component operation in this composite operation.
     * @throws IndexOutOfBoundsException if the index is out of range.
     */
    public Operation getOperation(final int i) {
        try {
            return operations[i];
        } catch (final ArrayIndexOutOfBoundsException e) {
            throw new IndexOutOfBoundsException(Integer.toString(i));
        }
    }

    /**
     * Returns true if this composite operation contains an operation equal to
     * the specified operation.
     * @param operation the operation being searched for. Must not be null.
     * @return true if the if this composite operation contains an operation
     * equal to the specified operation.
     */
    public boolean contains(final Operation operation) {
        Objects.requireNonNull(operation);
        for(final Operation component: operations) {
            if (component.equals(operation)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Returns true if the other object is also a composite operation and their
     * component operations are equal.
     * @param obj the object to compare to
     * @return true if this object is equal to the other one, false otherwise.
     */
    @Override
    public boolean equals(final Object obj) {
        if (obj == null || obj.getClass() != CompositeOperation.class) {
            return false;
        }
        return Arrays.equals(operations, ((CompositeOperation)obj).operations);
    }

    /**
     * Returns the hash code of this composite operation. Defined to be equal
     * to {@code java.util.Arrays.hashCode(operations)}.
     */
    @Override
    public int hashCode() {
        return Arrays.hashCode(operations);
    };

    /**
     * Returns the string representation of this composite operation. Defined to
     * be the {@code toString} of its component operations, each separated by
     * the vertical line character (e.g. {@code "GET_PROPERTY|GET_ELEMENT"}).
     * @return the string representation of this composite operation.
     */
    @Override
    public String toString() {
        final StringBuilder b = new StringBuilder();
        b.append(operations[0]);
        for(int i = 1; i < operations.length; ++i) {
            b.append('|').append(operations[i]);
        }
        return b.toString();
    }

    /**
     * Returns the components of the passed operation if it is a composite
     * operation, otherwise returns an array containing the operation itself.
     * This allows for returning an array of component even if it is not known
     * whether the operation is itself a composite (treating a non-composite
     * operation as if it were a single-element composite of itself).
     * @param op the operation whose components are retrieved.
     * @return if the passed operation is a composite operation, returns its
     * {@link #getOperations()}, otherwise returns the operation itself.
     */
    public static Operation[] getOperations(final Operation op) {
        return op instanceof CompositeOperation
                ? ((CompositeOperation)op).operations.clone()
                : new Operation[] { op };
    }

    /**
     * Returns true if the specified potentially composite operation is a
     * {@link CompositeOperation} and contains an operation equal to the
     * specified operation. If {@code composite} is not a
     * {@link CompositeOperation}, then the two operations are compared for
     * equality.
     * @param composite the potentially composite operation. Must not be null.
     * @param operation the operation being searched for. Must not be null.
     * @return true if the if the passed operation is a
     * {@link CompositeOperation} and contains a component operation equal to
     * the specified operation, or if it is not a {@link CompositeOperation} and
     * is equal to {@code operation}.
     */
    public static boolean contains(final Operation composite, final Operation operation) {
        if (composite instanceof CompositeOperation) {
            return ((CompositeOperation)composite).contains(operation);
        }
        return composite.equals(Objects.requireNonNull(operation));
    }
}
