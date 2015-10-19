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

import java.lang.invoke.CallSite;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodHandles.Lookup;
import java.lang.invoke.MethodType;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.StringTokenizer;
import jdk.internal.dynalink.support.NameCodec;

/**
 * Interface for objects describing a call site. A call site descriptor contains
 * all the information about a call site necessary for linking it: the class
 * performing the lookups, the name of the method being invoked, and the method
 * signature. Call site descriptors are used in Dynalink in place of passing
 * {@link CallSite} objects to linkers so they can't directly manipulate them.
 * The constructors of built-in {@link RelinkableCallSite} implementations all
 * take a call site descriptor. Call site descriptors must be immutable.
 */
public interface CallSiteDescriptor {
    /**
     * A runtime permission to invoke the {@link #getLookup()} method. It is
     * named {@code "dynalink.getLookup"}.
     */
    public static final RuntimePermission GET_LOOKUP_PERMISSION =
            new RuntimePermission("dynalink.getLookup");

    /**
     * The index of the name token that will carry the operation scheme prefix,
     * e.g. {@code "dyn"} for operations specified by Dynalink itself.
     */
    public static final int SCHEME = 0;

    /**
     * The index of the name token that carries the operation name, at least
     * when using the {@code "dyn"} scheme.
     */

    public static final int OPERATOR = 1;

    /**
     * The index of the name token that carries the name of an operand (e.g. a
     * property or a method), at least when using the {@code "dyn"} scheme.
     */
    public static final int NAME_OPERAND = 2;

    /**
     * String used to delimit tokens in a call site name; its value is
     * {@code ":"}, that is the colon character.
     */
    public static final String TOKEN_DELIMITER = ":";

    /**
     * String used to delimit operation names in a composite operation name;
     * its value is {@code "|"}, that is the pipe character.
     */
    public static final String OPERATOR_DELIMITER = "|";

    /**
     * Returns the number of tokens in the name of the method at the call site.
     * Method names are tokenized with the {@link #TOKEN_DELIMITER} character
     * character, e.g. {@code "dyn:getProp:color"} would be the name used to
     * describe a method that retrieves the property named "color" on the object
     * it is invoked on.
     * @return the number of tokens in the name of the method at the call site.
     */
    public int getNameTokenCount();

    /**
     * Returns the <i>i<sup>th</sup></i> token in the method name at the call
     * site. Method names are tokenized with the {@link #TOKEN_DELIMITER}
     * character.
     * @param i the index of the token. Must be between 0 (inclusive) and
     * {@link #getNameTokenCount()} (exclusive).
     * @throws IllegalArgumentException if the index is outside the allowed
     * range.
     * @return the <i>i<sup>th</sup></i> token in the method name at the call
     * site.
     */
    public String getNameToken(int i);

    /**
     * Returns the full (untokenized) name of the method at the call site.
     * @return the full (untokenized) name of the method at the call site.
     */
    public String getName();

    /**
     * The type of the method at the call site.
     *
     * @return type of the method at the call site.
     */
    public MethodType getMethodType();

    /**
     * Returns the lookup that should be used to find method handles to set as
     * targets of the call site described by this descriptor. When creating
     * descriptors from a {@link java.lang.invoke} bootstrap method, it should
     * be the lookup passed to the bootstrap. An implementation should use
     * {@link #checkLookup(MethodHandles.Lookup)} to ensure the necessary
     * security properties.
     * @return the lookup that should be used to find method handles to set as
     * targets of the call site described by this descriptor.
     * @throws SecurityException if the lookup isn't the
     * {@link MethodHandles#publicLookup()} and a security manager is present,
     * and a check for {@code RuntimePermission("dynalink.getLookup")}
     * (a canonical instance of which is available as
     * {@link #GET_LOOKUP_PERMISSION}) fails.
     */
    public Lookup getLookup();

    /**
     * Creates a new call site descriptor from this descriptor, which is
     * identical to this, except it changes the method type.
     *
     * @param newMethodType the new method type
     * @return a new call site descriptor, with the method type changed.
     */
    public CallSiteDescriptor changeMethodType(MethodType newMethodType);


    /**
     * Tokenizes a composite operation name of this descriptor along
     * {@link #OPERATOR_DELIMITER} characters. E.g. if this descriptor's name is
     * {@code "dyn:getElem|getProp|getMethod"}, then it returns a list of
     * {@code ["getElem", "getProp", "getMethod"]}.
     * @return a list of operator tokens.
     */
    public default List<String> tokenizeOperators() {
        final String ops = getNameToken(CallSiteDescriptor.OPERATOR);
        final StringTokenizer tok = new StringTokenizer(ops, CallSiteDescriptor.OPERATOR_DELIMITER);
        final int count = tok.countTokens();
        if(count == 1) {
            return Collections.singletonList(ops);
        }
        final String[] tokens = new String[count];
        for(int i = 0; i < count; ++i) {
            tokens[i] = tok.nextToken();
        }
        return Arrays.asList(tokens);
    }

    /**
     * Checks if the current access context is granted the
     * {@code RuntimePermission("dynalink.getLookup")} permission, if the
     * system contains a security manager, and the passed lookup is not the
     * {@link MethodHandles#publicLookup()}. This method should be used in all
     * implementations of {@link #getLookup()} method to ensure that only
     * code with permission can retrieve the lookup object.
     * @param lookup the lookup being checked for access
     * @return the passed in lookup if there's either no security manager in
     * the system, or the passed lookup is the public lookup, or the current
     * access context is granted the relevant permission.
     * @throws SecurityException if the system contains a security manager, and
     * the passed lookup is not the public lookup, and the current access
     * context is not granted the relevant permission.
     */
    public static Lookup checkLookup(final Lookup lookup) {
        final SecurityManager sm = System.getSecurityManager();
        if (sm != null && lookup != MethodHandles.publicLookup()) {
            sm.checkPermission(GET_LOOKUP_PERMISSION);
        }
        return lookup;
    }

    /**
     * Tokenizes the composite name along {@link #TOKEN_DELIMITER} characters,
     * as well as {@link NameCodec#decode(String) demangles} and interns the
     * tokens. The first two tokens are not demangled as they are supposed to
     * be the naming scheme and the name of the operation which can be expected
     * to consist of just alphabetical characters.
     * @param name the composite name consisting of
     * {@link #TOKEN_DELIMITER}-separated, possibly mangled tokens.
     * @return an array of unmangled, interned tokens.
     */
    public static String[] tokenizeName(final String name) {
        final StringTokenizer tok = new StringTokenizer(name, CallSiteDescriptor.TOKEN_DELIMITER);
        final String[] tokens = new String[tok.countTokens()];
        for(int i = 0; i < tokens.length; ++i) {
            String token = tok.nextToken();
            if(i > 1) {
                token = NameCodec.decode(token);
            }
            tokens[i] = token.intern();
        }
        return tokens;
    }
}
