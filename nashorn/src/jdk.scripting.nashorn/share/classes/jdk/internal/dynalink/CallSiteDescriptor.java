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

import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodHandles.Lookup;
import java.lang.invoke.MethodType;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.StringTokenizer;
import jdk.internal.dynalink.support.NameCodec;

/**
 * An immutable descriptor of a call site. It is an immutable object that contains all the information about a call
 * site: the class performing the lookups, the name of the method being invoked, and the method signature. Call site descriptors are used in this library in place of passing a real call site to
 * guarding linkers so they aren't tempted to directly manipulate the call sites. The constructors of built-in
 * {@link RelinkableCallSite} implementations all need a call site descriptor. Even if you create your own call site
 * descriptors consider using {@link CallSiteDescriptor#tokenizeName(String)} in your implementation.
 */
public interface CallSiteDescriptor {
    /**
     * The index of the name token that will carry the operation scheme prefix (usually, "dyn").
     */
    public static final int SCHEME = 0;
    /**
     * The index of the name token that will usually carry the operation name.
     */

    public static final int OPERATOR=1;
    /**
     * The index of the name token that will usually carry a name of an operand (of a property, method, etc.)
     */

    public static final int NAME_OPERAND=2;

    /**
     * Character used to delimit tokens in an call site name.
     */
    public static final String TOKEN_DELIMITER = ":";

    /**
     * Character used to delimit operation names in a composite operation specification.
     */
    public static final String OPERATOR_DELIMITER = "|";

    /**
     * Returns the number of tokens in the name of the method at the call site. Method names are tokenized with the
     * colon ":" character, i.e. "dyn:getProp:color" would be the name used to describe a method that retrieves the
     * property named "color" on the object it is invoked on.
     * @return the number of tokens in the name of the method at the call site.
     */
    public int getNameTokenCount();

    /**
     * Returns the <i>i<sup>th</sup></i> token in the method name at the call site. Method names are tokenized with the
     * colon ":" character.
     * @param i the index of the token. Must be between 0 (inclusive) and {@link #getNameTokenCount()} (exclusive)
     * @throws IllegalArgumentException if the index is outside the allowed range.
     * @return the <i>i<sup>th</sup></i> token in the method name at the call site. The returned strings are interned.
     */
    public String getNameToken(int i);

    /**
     * Returns the name of the method at the call site. Note that the object internally only stores the tokenized name,
     * and has to reconstruct the full name from tokens on each invocation.
     * @return the name of the method at the call site.
     */
    public String getName();

    /**
     * The type of the method at the call site.
     *
     * @return type of the method at the call site.
     */
    public MethodType getMethodType();

    /**
     * Returns the lookup passed to the bootstrap method.
     * @return the lookup passed to the bootstrap method.
     * @throws SecurityException if the lookup isn't the {@link MethodHandles#publicLookup()} and a security
     * manager is present, and a check for {@code RuntimePermission("dynalink.getLookup")} fails.
     */
    public Lookup getLookup();

    /**
     * Creates a new call site descriptor from this descriptor, which is identical to this, except it changes the method
     * type.
     *
     * @param newMethodType the new method type
     * @return a new call site descriptor, with the method type changed.
     */
    public CallSiteDescriptor changeMethodType(MethodType newMethodType);


    /**
     * Tokenizes a composite operation name along pipe characters. I.e. if you have a "dyn:getElem|getProp|getMethod"
     * operation, returns a list of ["getElem", "getProp", "getMethod"]. The tokens are not interned.
     * @return a list of tokens
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
     * Tokenizes the composite name along colons, as well as {@link NameCodec#decode(String) demangles} and interns
     * the tokens. The first two tokens are not demangled as they are supposed to be the naming scheme and the name of
     * the operation which can be expected to consist of just alphabetical characters.
     * @param name the composite name consisting of colon-separated, possibly mangled tokens.
     * @return an array of tokens
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
