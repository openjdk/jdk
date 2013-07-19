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
import java.util.StringTokenizer;
import jdk.internal.dynalink.CallSiteDescriptor;
import jdk.internal.dynalink.linker.LinkerServices;
import jdk.internal.dynalink.support.Guards;

/**
 * Base class for dynamic methods that dispatch to a single target Java method or constructor. Handles adaptation of the
 * target method to a call site type (including mapping variable arity methods to a call site signature with different
 * arity).
 * @author Attila Szegedi
 * @version $Id: $
 */
abstract class SingleDynamicMethod extends DynamicMethod {
    SingleDynamicMethod(String name) {
        super(name);
    }

    /**
     * Returns true if this method is variable arity.
     * @return true if this method is variable arity.
     */
    abstract boolean isVarArgs();

    /**
     * Returns this method's native type.
     * @return this method's native type.
     */
    abstract MethodType getMethodType();

    /**
     * Given a specified lookup, returns a method handle to this method's target.
     * @param lookup the lookup to use.
     * @return the handle to this method's target method.
     */
    abstract MethodHandle getTarget(MethodHandles.Lookup lookup);

    @Override
    MethodHandle getInvocation(CallSiteDescriptor callSiteDescriptor, LinkerServices linkerServices) {
        return getInvocation(getTarget(callSiteDescriptor.getLookup()), callSiteDescriptor.getMethodType(),
                linkerServices);
    }

    @Override
    SingleDynamicMethod getMethodForExactParamTypes(String paramTypes) {
        return typeMatchesDescription(paramTypes, getMethodType()) ? this : null;
    }

    @Override
    boolean contains(SingleDynamicMethod method) {
        return getMethodType().parameterList().equals(method.getMethodType().parameterList());
    }

    static String getMethodNameWithSignature(MethodType type, String methodName) {
        final String typeStr = type.toString();
        final int retTypeIndex = typeStr.lastIndexOf(')') + 1;
        int secondParamIndex = typeStr.indexOf(',') + 1;
        if(secondParamIndex == 0) {
            secondParamIndex = retTypeIndex - 1;
        }
        return typeStr.substring(retTypeIndex) + " " + methodName + "(" + typeStr.substring(secondParamIndex, retTypeIndex);
    }

    /**
     * Given a method handle and a call site type, adapts the method handle to the call site type. Performs type
     * conversions as needed using the specified linker services, and in case that the method handle is a vararg
     * collector, matches it to the arity of the call site.
     * @param target the method handle to adapt
     * @param callSiteType the type of the call site
     * @param linkerServices the linker services used for type conversions
     * @return the adapted method handle.
     */
    static MethodHandle getInvocation(MethodHandle target, MethodType callSiteType, LinkerServices linkerServices) {
        final MethodType methodType = target.type();
        final int paramsLen = methodType.parameterCount();
        final boolean varArgs = target.isVarargsCollector();
        final MethodHandle fixTarget = varArgs ? target.asFixedArity() : target;
        final int fixParamsLen = varArgs ? paramsLen - 1 : paramsLen;
        final int argsLen = callSiteType.parameterCount();
        if(argsLen < fixParamsLen) {
            // Less actual arguments than number of fixed declared arguments; can't invoke.
            return null;
        }
        // Method handle has the same number of fixed arguments as the call site type
        if(argsLen == fixParamsLen) {
            // Method handle that matches the number of actual arguments as the number of fixed arguments
            final MethodHandle matchedMethod;
            if(varArgs) {
                // If vararg, add a zero-length array of the expected type as the last argument to signify no variable
                // arguments.
                matchedMethod = MethodHandles.insertArguments(fixTarget, fixParamsLen, Array.newInstance(
                        methodType.parameterType(fixParamsLen).getComponentType(), 0));
            } else {
                // Otherwise, just use the method
                matchedMethod = fixTarget;
            }
            return createConvertingInvocation(matchedMethod, linkerServices, callSiteType);
        }

        // What's below only works for varargs
        if(!varArgs) {
            return null;
        }

        final Class<?> varArgType = methodType.parameterType(fixParamsLen);
        // Handle a somewhat sinister corner case: caller passes exactly one argument in the vararg position, and we
        // must handle both a prepacked vararg array as well as a genuine 1-long vararg sequence.
        if(argsLen == paramsLen) {
            final Class<?> callSiteLastArgType = callSiteType.parameterType(fixParamsLen);
            if(varArgType.isAssignableFrom(callSiteLastArgType)) {
                // Call site signature guarantees we'll always be passed a single compatible array; just link directly
                // to the method, introducing necessary conversions. Also, preserve it being a variable arity method.
                return createConvertingInvocation(target, linkerServices, callSiteType).asVarargsCollector(
                        callSiteLastArgType);
            }
            if(!linkerServices.canConvert(callSiteLastArgType, varArgType)) {
                // Call site signature guarantees the argument can definitely not be an array (i.e. it is primitive);
                // link immediately to a vararg-packing method handle.
                return createConvertingInvocation(collectArguments(fixTarget, argsLen), linkerServices, callSiteType);
            }
            // Call site signature makes no guarantees that the single argument in the vararg position will be
            // compatible across all invocations. Need to insert an appropriate guard and fall back to generic vararg
            // method when it is not.
            return MethodHandles.guardWithTest(Guards.isInstance(varArgType, fixParamsLen, callSiteType),
                    createConvertingInvocation(fixTarget, linkerServices, callSiteType),
                    createConvertingInvocation(collectArguments(fixTarget, argsLen), linkerServices, callSiteType));
        }

        // Remaining case: more than one vararg.
        return createConvertingInvocation(collectArguments(fixTarget, argsLen), linkerServices, callSiteType);
    }

    /**
     * Creates a method handle out of the original target that will collect the varargs for the exact component type of
     * the varArg array. Note that this will nicely trigger language-specific type converters for exactly those varargs
     * for which it is necessary when later passed to linkerServices.convertArguments().
     *
     * @param target the original method handle
     * @param parameterCount the total number of arguments in the new method handle
     * @return a collecting method handle
     */
    static MethodHandle collectArguments(MethodHandle target, final int parameterCount) {
        final MethodType methodType = target.type();
        final int fixParamsLen = methodType.parameterCount() - 1;
        final Class<?> arrayType = methodType.parameterType(fixParamsLen);
        return target.asCollector(arrayType, parameterCount - fixParamsLen);
    }

    private static MethodHandle createConvertingInvocation(final MethodHandle sizedMethod,
            final LinkerServices linkerServices, final MethodType callSiteType) {
        return linkerServices.asType(sizedMethod, callSiteType);
    }

    private static boolean typeMatchesDescription(String paramTypes, MethodType type) {
        final StringTokenizer tok = new StringTokenizer(paramTypes, ", ");
        for(int i = 1; i < type.parameterCount(); ++i) { // i = 1 as we ignore the receiver
            if(!(tok.hasMoreTokens() && typeNameMatches(tok.nextToken(), type.parameterType(i)))) {
                return false;
            }
        }
        return !tok.hasMoreTokens();
    }

    private static boolean typeNameMatches(String typeName, Class<?> type) {
        return  typeName.equals(typeName.indexOf('.') == -1 ? type.getSimpleName() : type.getCanonicalName());
    }
}
