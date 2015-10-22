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

/**
 * Defines the standard dynamic operations. Getter and setter operations defined
 * in this enumeration can be composed into a {@link CompositeOperation}, and
 * {@link NamedOperation} can be used to bind the name parameter of operations
 * that take one, in which case it disappears from the type signature.
 */
public enum StandardOperation implements Operation {
    /**
     * Get the value of a property defined on an object. Call sites with this
     * operation should have a signature of
     * <tt>(receiver,&nbsp;propertyName)&rarr;value</tt> or
     * <tt>(receiver)&rarr;value</tt> when used with {@link NamedOperation}, with
     * all parameters and return type being of any type (either primitive or
     * reference).
     */
    GET_PROPERTY,
    /**
     * Set the value of a property defined on an object. Call sites with this
     * operation should have a signature of
     * <tt>(receiver,&nbsp;propertyName,&nbsp;value)&rarr;void</tt> or
     * <tt>(receiver,&nbsp;value)&rarr;void</tt> when used with {@link NamedOperation},
     * with all parameters and return type being of any type (either primitive
     * or reference).
     */
    SET_PROPERTY,
    /**
     * Get the value of an element of a collection. Call sites with this
     * operation should have a signature of
     * <tt>(receiver,&nbsp;index)&rarr;value</tt> or
     * <tt>(receiver)&rarr;value</tt> when used with {@link NamedOperation}, with
     * all parameters and return type being of any type (either primitive or
     * reference).
     */
    GET_ELEMENT,
    /**
     * Set the value of an element of a collection. Call sites with this
     * operation should have a signature of
     * <tt>(receiver,&nbsp;index,&nbsp;value)&rarr;void</tt> or
     * <tt>(receiver,&nbsp;value)&rarr;void</tt> when used with {@link NamedOperation},
     * with all parameters and return type being of any type (either primitive
     * or reference).
     */
    SET_ELEMENT,
    /**
     * Get the length of an array of size of a collection. Call sites with
     * this operation should have a signature of <tt>(receiver)&rarr;value</tt>,
     * with all parameters and return type being of any type (either primitive
     * or reference).
     */
    GET_LENGTH,
    /**
     * Gets an object representing a method defined on an object. Call sites
     * with this operation should have a signature of
     * <tt>(receiver,&nbsp;methodName)&rarr;value</tt>, or
     * <tt>(receiver)&rarr;value</tt> when used with {@link NamedOperation}
     * with all parameters and return type being of any type (either primitive
     * or reference).
     */
    GET_METHOD,
    /**
     * Calls a method defined on an object. Call sites with this
     * operation should have a signature of
     * <tt>(receiver,&nbsp;methodName,&nbsp;arguments...)&rarr;value</tt> or
     * <tt>(receiver,&nbsp;arguments...)&rarr;value</tt> when used with {@link NamedOperation},
     * with all parameters and return type being of any type (either primitive
     * or reference).
     */
    CALL_METHOD,
    /**
     * Calls a callable object. Call sites with this operation should have a
     * signature of <tt>(receiver,&nbsp;arguments...)&rarr;value</tt>, with all
     * parameters and return type being of any type (either primitive or
     * reference). Typically, if the callable is a method of an object, the
     * first argument will act as the "this" value passed to the called method.
     * The <tt>CALL</tt> operation is allowed to be used with a
     * {@link NamedOperation} even though it does not take a name. Using it with
     * a named operation won't affect its signature; the name is solely meant to
     * be used as a diagnostic description for error messages.
     */
    CALL,
    /**
     * Calls a constructor object. Call sites with this operation should have a
     * signature of <tt>(receiver,&nbsp;arguments...)&rarr;value</tt>, with all
     * parameters and return type being of any type (either primitive or
     * reference). The <tt>NEW</tt> operation is allowed to be used with a
     * {@link NamedOperation} even though it does not take a name. Using it with
     * a named operation won't affect its signature; the name is solely meant to
     * be used as a diagnostic description for error messages.
     */
    NEW
}
