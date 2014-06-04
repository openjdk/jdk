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
import java.lang.invoke.MethodType;
import java.lang.ref.Reference;
import java.lang.ref.WeakReference;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.StringTokenizer;
import java.util.WeakHashMap;
import jdk.internal.dynalink.CallSiteDescriptor;

/**
 * Usable as a default factory for call site descriptor implementations. It is weakly canonicalizing, meaning it will
 * return the same immutable call site descriptor for identical inputs, i.e. repeated requests for a descriptor
 * signifying public lookup for "dyn:getProp:color" of type "Object(Object)" will return the same object as long as
 * a previously created, at least softly reachable one exists. It also uses several different implementations of the
 * {@link CallSiteDescriptor} internally, and chooses the most space-efficient one based on the input.
 * @author Attila Szegedi
 */
public class CallSiteDescriptorFactory {
    private static final WeakHashMap<CallSiteDescriptor, Reference<CallSiteDescriptor>> publicDescs =
            new WeakHashMap<>();


    private CallSiteDescriptorFactory() {
    }

    /**
     * Creates a new call site descriptor instance. The actual underlying class of the instance is dependent on the
     * passed arguments to be space efficient; i.e. if you  only use the public lookup, you'll get back an
     * implementation that doesn't waste space on storing the lookup object.
     * @param lookup the lookup that determines access rights at the call site. If your language runtime doesn't have
     * equivalents of Java access concepts, just use {@link MethodHandles#publicLookup()}. Must not be null.
     * @param name the name of the method at the call site. Must not be null.
     * @param methodType the type of the method at the call site. Must not be null.
     * @return a call site descriptor representing the input. Note that although the method name is "create", it will
     * in fact return a weakly-referenced canonical instance.
     */
    public static CallSiteDescriptor create(final Lookup lookup, final String name, final MethodType methodType) {
        name.getClass(); // NPE check
        methodType.getClass(); // NPE check
        lookup.getClass(); // NPE check
        final String[] tokenizedName = tokenizeName(name);
        if(isPublicLookup(lookup)) {
            return getCanonicalPublicDescriptor(createPublicCallSiteDescriptor(tokenizedName, methodType));
        }
        return new LookupCallSiteDescriptor(tokenizedName, methodType, lookup);
    }

    static CallSiteDescriptor getCanonicalPublicDescriptor(final CallSiteDescriptor desc) {
        synchronized(publicDescs) {
            final Reference<CallSiteDescriptor> ref = publicDescs.get(desc);
            if(ref != null) {
                final CallSiteDescriptor canonical = ref.get();
                if(canonical != null) {
                    return canonical;
                }
            }
            publicDescs.put(desc, createReference(desc));
        }
        return desc;
    }

    /**
     * Override this to use a different kind of references for the cache
     * @param desc desc
     * @return reference
     */
    protected static Reference<CallSiteDescriptor> createReference(final CallSiteDescriptor desc) {
        return new WeakReference<>(desc);
    }

    private static CallSiteDescriptor createPublicCallSiteDescriptor(final String[] tokenizedName, final MethodType methodType) {
        final int l = tokenizedName.length;
        if(l > 0 && tokenizedName[0] == "dyn") {
            if(l == 2) {
                return new UnnamedDynCallSiteDescriptor(tokenizedName[1], methodType);
            } if (l == 3) {
                return new NamedDynCallSiteDescriptor(tokenizedName[1], tokenizedName[2], methodType);
            }
        }
        return new DefaultCallSiteDescriptor(tokenizedName, methodType);
    }

    private static boolean isPublicLookup(final Lookup lookup) {
        return lookup == MethodHandles.publicLookup();
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

    /**
     * Tokenizes a composite operation name along pipe characters. I.e. if you have a "dyn:getElem|getProp|getMethod"
     * operation, returns a list of ["getElem", "getProp", "getMethod"]. The tokens are not interned.
     * @param desc the call site descriptor with the operation
     * @return a list of tokens
     */
    public static List<String> tokenizeOperators(final CallSiteDescriptor desc) {
        final String ops = desc.getNameToken(CallSiteDescriptor.OPERATOR);
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
     * Returns a new call site descriptor that is identical to the passed one, except that it has some parameter types
     * removed from its method type.
     * @param desc the original call site descriptor
     * @param start index of the first parameter to remove
     * @param end index of the first parameter to not remove
     * @return a new call site descriptor with modified method type
     */
    public static CallSiteDescriptor dropParameterTypes(final CallSiteDescriptor desc, final int start, final int end) {
        return desc.changeMethodType(desc.getMethodType().dropParameterTypes(start, end));
    }

    /**
     * Returns a new call site descriptor that is identical to the passed one, except that it has a single parameter
     * type changed in its method type.
     * @param desc the original call site descriptor
     * @param num index of the parameter to change
     * @param nptype the new parameter type
     * @return a new call site descriptor with modified method type
     */
    public static CallSiteDescriptor changeParameterType(final CallSiteDescriptor desc, final int num, final Class<?> nptype) {
        return desc.changeMethodType(desc.getMethodType().changeParameterType(num, nptype));
    }

    /**
     * Returns a new call site descriptor that is identical to the passed one, except that it has the return type
     * changed in its method type.
     * @param desc the original call site descriptor
     * @param nrtype the new return type
     * @return a new call site descriptor with modified method type
     */
    public static CallSiteDescriptor changeReturnType(final CallSiteDescriptor desc, final Class<?> nrtype) {
        return desc.changeMethodType(desc.getMethodType().changeReturnType(nrtype));
    }

    /**
     * Returns a new call site descriptor that is identical to the passed one, except that it has additional parameter
     * types inserted into its method type.
     * @param desc the original call site descriptor
     * @param num index at which the new parameters are inserted
     * @param ptypesToInsert the new types to insert
     * @return a new call site descriptor with modified method type
     */
    public static CallSiteDescriptor insertParameterTypes(final CallSiteDescriptor desc, final int num, final Class<?>... ptypesToInsert) {
        return desc.changeMethodType(desc.getMethodType().insertParameterTypes(num, ptypesToInsert));
    }

    /**
     * Returns a new call site descriptor that is identical to the passed one, except that it has additional parameter
     * types inserted into its method type.
     * @param desc the original call site descriptor
     * @param num index at which the new parameters are inserted
     * @param ptypesToInsert the new types to insert
     * @return a new call site descriptor with modified method type
     */
    public static CallSiteDescriptor insertParameterTypes(final CallSiteDescriptor desc, final int num, final List<Class<?>> ptypesToInsert) {
        return desc.changeMethodType(desc.getMethodType().insertParameterTypes(num, ptypesToInsert));
    }
}
