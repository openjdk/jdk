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

package jdk.internal.dynalink.linker;

import jdk.internal.dynalink.CallSiteDescriptor;
import jdk.internal.dynalink.DynamicLinkerFactory;

/**
 * Represents a request to link a particular invocation at a particular call site. Instances of these requests are being
 * passed to {@link GuardingDynamicLinker}.
 *
 * @author Attila Szegedi
 */
public interface LinkRequest {
    /**
     * Returns the call site descriptor for the call site being linked.
     *
     * @return the call site descriptor for the call site being linked.
     */
    public CallSiteDescriptor getCallSiteDescriptor();

    /**
     * Returns the call site token for the call site being linked. This token is an opaque object that is guaranteed to
     * have different identity for different call sites, and is also guaranteed to not become weakly reachable before
     * the call site does and to become weakly reachable some time after the call site does. This makes it ideal as a
     * candidate for a key in a weak hash map in which a linker might want to keep per-call site linking state (usually
     * profiling information).
     *
     * @return the call site token for the call site being linked.
     */
    public Object getCallSiteToken();

    /**
     * Returns the arguments for the invocation being linked. The returned array is a clone; modifications to it won't
     * affect the arguments in this request.
     *
     * @return the arguments for the invocation being linked.
     */
    public Object[] getArguments();

    /**
     * Returns the 0th argument for the invocation being linked; this is typically the receiver object.
     *
     * @return the receiver object.
     */
    public Object getReceiver();

    /**
     * Returns the number of times this callsite has been linked/relinked. This can be useful if you want to
     * change e.g. exception based relinking to guard based relinking. It's probably not a good idea to keep,
     * for example, expensive exception throwing relinkage based on failed type checks/ClassCastException in
     * a nested callsite tree where the exception is thrown repeatedly for the common case. There it would be
     * much more performant to use exact type guards instead.
     *
     * @return link count for call site
     */
    public int getLinkCount();

    /**
     * Returns true if the call site is considered unstable, that is, it has been relinked more times than was
     * specified in {@link DynamicLinkerFactory#setUnstableRelinkThreshold(int)}. Linkers should use this as a
     * hint to prefer producing linkage that is more stable (its guard fails less frequently), even if that assumption
     * causes a less effective version of an operation to be linked. This is just a hint, of course, and linkers are
     * free to ignore this property.
     * @return true if the call site is considered unstable.
     */
    public boolean isCallSiteUnstable();

    /**
     * Returns a request stripped from runtime context arguments. Some language runtimes will include runtime-specific
     * context parameters in their call sites as few arguments between 0th argument "this" and the normal arguments. If
     * a linker does not recognize such contexts at all, or does not recognize the call site as one with its own
     * context, it can ask for the alternative link request with context parameters and arguments removed, and link
     * against it instead.
     *
     * @return the context-stripped request. If the link request does not have any language runtime specific context
     * parameters, the same link request is returned.
     */
    public LinkRequest withoutRuntimeContext();

    /**
     * Returns a request identical to this one with call site descriptor and arguments replaced with the ones specified.
     *
     * @param callSiteDescriptor the new call site descriptor
     * @param arguments the new arguments
     * @return a new request identical to this one, except with the call site descriptor and arguments replaced with the
     * specified ones.
     */
    public LinkRequest replaceArguments(CallSiteDescriptor callSiteDescriptor, Object[] arguments);
}
