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

/**
 * <p>
 * Dynalink is a library for dynamic linking high-level operations on objects
 * such as "read a property", "write a property", "invoke a function" and so on,
 * expressed as {@link java.lang.invoke.CallSite call sites}. As such, it is
 * closely related to, and relies on, the {@link java.lang.invoke} package.
 * </p><p>
 * While {@link java.lang.invoke} provides a JVM-level foundation for
 * application-specific dynamic linking of methods, it does not provide a way to
 * express higher level operations on objects, nor methods that implement them.
 * These operations are the usual regimen of operations in object-oriented
 * environments: property access, access of elements of collections, invocation
 * of constructors, invocation of named methods (potentially with multiple
 * dispatch, e.g. link- and run-time equivalents of Java overloaded method
 * resolution). These are all functions that are normally desired in a language
 * on the JVM. When a JVM language is statically typed and its type system
 * matches that of the JVM, it can accomplish this with use of the usual
 * invocation bytecodes ({@code INVOKEVIRTUAL} etc.) as well as field access
 * bytecodes ({@code GETFIELD}, {@code PUTFIELD}). However, if the language is
 * dynamic (hence, types of some expressions are not known at the time the
 * program is compiled to bytecode), or its type system doesn't match closely
 * that of the JVM, then it should use {@code invokedynamic} call sites and let
 * Dynalink link those.
 * </p><p>
 * Dynalink lets programs have their operations on objects of unknown static
 * types linked dynamically at run time. It also lets a language expose a linker
 * for its own object model. Finally, it provides a default linker for ordinary
 * Java objects. Two languages both exporting their linkers in the same JVM will
 * even be able to cross-link their operations with each other if an object
 * belonging to one language is passed to code from the other language.
 * </p>
 * <p>
 * Languages that use Dynalink will create and configure a
 * {@link jdk.internal.dynalink.DynamicLinkerFactory} and use it to create a
 * {@link jdk.internal.dynalink.DynamicLinker}.
 * The thus created dynamic linker will have to be used to link any
 * {@link jdk.internal.dynalink.RelinkableCallSite}s they create, most often from a
 * {@link java.lang.invoke} bootstrap method.
 * </p>
 * <p>
 * Languages that wish to define and use their own linkers will also need to
 * use the {@link jdk.internal.dynalink.linker} package.
 * </p>
 * @since 1.9
 */
@jdk.Exported
package jdk.internal.dynalink;
