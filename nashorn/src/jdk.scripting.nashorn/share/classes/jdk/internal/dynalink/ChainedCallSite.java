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

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.concurrent.atomic.AtomicReference;
import jdk.internal.dynalink.linker.GuardedInvocation;
import jdk.internal.dynalink.support.AbstractRelinkableCallSite;
import jdk.internal.dynalink.support.Lookup;

/**
 * A relinkable call site that maintains a chain of linked method handles. In the default implementation, up to 8 method
 * handles can be chained, cascading from one to the other through
 * {@link MethodHandles#guardWithTest(MethodHandle, MethodHandle, MethodHandle)}. When this call site has to link a new
 * method handle and the length of the chain is already at the maximum, it will throw away the oldest method handle.
 * Switchpoint-invalidated handles in the chain are removed eagerly (on each linking request, and whenever a
 * switchpoint-invalidated method handle is traversed during invocation). There is currently no profiling
 * attached to the handles in the chain, so they are never reordered based on usage; the most recently linked method
 * handle is always at the start of the chain.
 */
public class ChainedCallSite extends AbstractRelinkableCallSite {
    private static final MethodHandle PRUNE_CATCHES;
    private static final MethodHandle PRUNE_SWITCHPOINTS;
    static {
        final MethodHandle PRUNE = Lookup.findOwnSpecial(MethodHandles.lookup(), "prune", MethodHandle.class,
                MethodHandle.class, boolean.class);
        PRUNE_CATCHES      = MethodHandles.insertArguments(PRUNE, 2, true);
        PRUNE_SWITCHPOINTS = MethodHandles.insertArguments(PRUNE, 2, false);
    }

    private final AtomicReference<LinkedList<GuardedInvocation>> invocations = new AtomicReference<>();

    /**
     * Creates a new chained call site.
     * @param descriptor the descriptor for the call site.
     */
    public ChainedCallSite(final CallSiteDescriptor descriptor) {
        super(descriptor);
    }

    /**
     * The maximum number of method handles in the chain. Defaults to 8. You can override it in a subclass if you need
     * to change the value. If your override returns a value less than 1, the code will break.
     * @return the maximum number of method handles in the chain.
     */
    protected int getMaxChainLength() {
        return 8;
    }

    @Override
    public void relink(final GuardedInvocation guardedInvocation, final MethodHandle fallback) {
        relinkInternal(guardedInvocation, fallback, false, false);
    }

    @Override
    public void resetAndRelink(final GuardedInvocation guardedInvocation, final MethodHandle fallback) {
        relinkInternal(guardedInvocation, fallback, true, false);
    }

    private MethodHandle relinkInternal(final GuardedInvocation invocation, final MethodHandle relink, final boolean reset, final boolean removeCatches) {
        final LinkedList<GuardedInvocation> currentInvocations = invocations.get();
        @SuppressWarnings({ "unchecked", "rawtypes" })
        final LinkedList<GuardedInvocation> newInvocations =
            currentInvocations == null || reset ? new LinkedList<>() : (LinkedList)currentInvocations.clone();

        // First, prune the chain of invalidated switchpoints, we always do this
        // We also remove any catches if the remove catches flag is set
        for(final Iterator<GuardedInvocation> it = newInvocations.iterator(); it.hasNext();) {
            final GuardedInvocation inv = it.next();
            if(inv.hasBeenInvalidated() || (removeCatches && inv.getException() != null)) {
                it.remove();
            }
        }

        // prune() is allowed to invoke this method with invocation == null meaning we're just pruning the chain and not
        // adding any new invocations to it.
        if(invocation != null) {
            // Remove oldest entry if we're at max length
            if(newInvocations.size() == getMaxChainLength()) {
                newInvocations.removeFirst();
            }
            newInvocations.addLast(invocation);
        }

        // prune-and-invoke is used as the fallback for invalidated switchpoints. If a switchpoint gets invalidated, we
        // rebuild the chain and get rid of all invalidated switchpoints instead of letting them linger.
        final MethodHandle pruneAndInvokeSwitchPoints = makePruneAndInvokeMethod(relink, PRUNE_SWITCHPOINTS);
        final MethodHandle pruneAndInvokeCatches      = makePruneAndInvokeMethod(relink, PRUNE_CATCHES);

        // Fold the new chain
        MethodHandle target = relink;
        for(final GuardedInvocation inv: newInvocations) {
            target = inv.compose(target, pruneAndInvokeSwitchPoints, pruneAndInvokeCatches);
        }

        // If nobody else updated the call site while we were rebuilding the chain, set the target to our chain. In case
        // we lost the race for multithreaded update, just do nothing. Either the other thread installed the same thing
        // we wanted to install, or otherwise, we'll be asked to relink again.
        if(invocations.compareAndSet(currentInvocations, newInvocations)) {
            setTarget(target);
        }
        return target;
    }

    /**
     * Creates a method that rebuilds our call chain, pruning it of any invalidated switchpoints, and then invokes that
     * chain.
     * @param relink the ultimate fallback for the chain (the {@code DynamicLinker}'s relink).
     * @return a method handle for prune-and-invoke
     */
    private MethodHandle makePruneAndInvokeMethod(final MethodHandle relink, final MethodHandle prune) {
        // Bind prune to (this, relink)
        final MethodHandle boundPrune = MethodHandles.insertArguments(prune, 0, this, relink);
        // Make it ignore all incoming arguments
        final MethodHandle ignoreArgsPrune = MethodHandles.dropArguments(boundPrune, 0, type().parameterList());
        // Invoke prune, then invoke the call site target with original arguments
        return MethodHandles.foldArguments(MethodHandles.exactInvoker(type()), ignoreArgsPrune);
    }

    @SuppressWarnings("unused")
    private MethodHandle prune(final MethodHandle relink, final boolean catches) {
        return relinkInternal(null, relink, false, catches);
    }
}
