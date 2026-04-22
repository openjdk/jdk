/*
 * Copyright (c) 2020, 2025, Oracle and/or its affiliates. All rights reserved.
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
package jdk.internal.foreign.abi;

import java.lang.foreign.FunctionDescriptor;
import java.lang.invoke.MethodType;
import java.util.List;
import java.util.stream.Stream;

public class CallingSequence {
    private final boolean forUpcall;
    private final MethodType callerMethodType;
    private final MethodType calleeMethodType;
    private final FunctionDescriptor desc;
    private final boolean needsReturnBuffer;
    private final long returnBufferSize;
    private final long allocationSize;

    private final List<Binding> returnBindings;
    private final List<List<Binding>> argumentBindings;

    private final LinkerOptions linkerOptions;

    public CallingSequence(boolean forUpcall, MethodType callerMethodType, MethodType calleeMethodType, FunctionDescriptor desc,
                           boolean needsReturnBuffer, long returnBufferSize, long allocationSize,
                           List<List<Binding>> argumentBindings, List<Binding> returnBindings,
                           LinkerOptions linkerOptions) {
        this.forUpcall = forUpcall;
        this.callerMethodType = callerMethodType;
        this.calleeMethodType = calleeMethodType;
        this.desc = desc;
        this.needsReturnBuffer = needsReturnBuffer;
        this.returnBufferSize = returnBufferSize;
        this.allocationSize = allocationSize;
        this.returnBindings = returnBindings;
        this.argumentBindings = argumentBindings;
        this.linkerOptions = linkerOptions;
    }

    /**
     * An important distinction is that downcalls have 1 recipe per caller parameter and
     * each callee parameter corresponds to a VM_STORE. Upcalls have 1 recipe per callee parameter and
     * each caller parameter corresponds to a VM_LOAD.
     *
     * The VM_STOREs are then implemented by the leaf handle for downcalls, and vice versa, the wrapper
     * stub that wraps an upcall handle implements the VM_LOADS. In both cases the register values are
     * communicated through Java primitives.
     *
     * The 'argumentBindingsCount' below corresponds to the number of recipes, so it is the
     * caller parameter count for downcalls, and the callee parameter count for upcalls.
     *
     * @return the number of binding recipes in this calling sequence
     */
    public int argumentBindingsCount() {
        return argumentBindings.size();
    }

    public List<Binding> argumentBindings(int i) {
        return argumentBindings.get(i);
    }

    public Stream<Binding> argumentBindings() {
        return argumentBindings.stream().flatMap(List::stream);
    }

    public List<Binding> returnBindings() {
        return returnBindings;
    }

    public boolean forUpcall() {
        return forUpcall;
    }

    public boolean forDowncall() {
        return !forUpcall;
    }

    /**
     * Returns the caller method type, which is the high-level method type
     * for downcalls (the type of the downcall method handle)
     * and the low-level method type (all primitives, VM facing) for upcalls.
     *
     * Note that for downcalls a single parameter in this method type corresponds
     * to a single argument binding recipe in this calling sequence, but it may
     * correspond to multiple parameters in the callee method type (for instance
     * if a struct is split into multiple register values).
     *
     * @return the caller method type.
     */
    public MethodType callerMethodType() {
        return callerMethodType;
    }

    /**
     * Returns the callee method type, which is the low-level method type
     * (all primitives, VM facing) for downcalls and the high-level method type
     * for upcalls (also the method type of the user-supplied target MH).
     *
     * Note that for upcalls a single parameter in this method type corresponds
     * to a single argument binding recipe in this calling sequence, but it may
     * correspond to multiple parameters in the caller method type (for instance
     * if a struct is reconstructed from multiple register values).
     *
     * @return the callee method type.
     */
    public MethodType calleeMethodType() {
        return calleeMethodType;
    }

    public FunctionDescriptor functionDesc() {
        return desc;
    }

    /**
     * Whether this calling sequence needs a return buffer.
     *
     * A return buffer is used to support functions that  return values
     * in multiple registers, which is not possible to do just with Java primitives
     * (we can only return 1 value in Java, meaning only 1 register value).
     *
     * To emulate these multi-register returns, we instead use a pre-allocated buffer
     * (the return buffer) from/into which the return values are loaded/stored.
     *
     * For downcalls, we allocate the buffer in Java code, and pass the address down
     * to the VM stub, which stores the returned register values into this buffer.
     * VM_LOADs in the binding recipe for the return value then load the value from this buffer.
     *
     * For upcalls, the VM stub allocates a buffer (on the stack), and passes the address
     * to the Java method handle it calls. VM_STOREs in the return binding recipe then
     * store values into this buffer, after which the VM stub moves the values from the buffer
     * into the right register.
     *
     * @return whether this calling sequence needs a return buffer.
     */
    public boolean needsReturnBuffer() {
        return needsReturnBuffer;
    }

    /**
     * The size of the return buffer, if one is needed.
     *
     * @see #needsReturnBuffer
     *
     * @return the return buffer size
     */
    public long returnBufferSize() {
        return returnBufferSize;
    }

    /**
     * The amount of bytes this calling sequence needs to allocate during an invocation.
     *
     * Includes the return buffer size as well as space for any buffer copies in the recipes.
     *
     * @return the allocation size
     */
    public long allocationSize() {
        return allocationSize;
    }

    public boolean hasReturnBindings() {
        return !returnBindings.isEmpty();
    }

    public int capturedStateMask() {
        return linkerOptions.capturedCallStateMask();
    }

    public boolean needsTransition() {
        return !linkerOptions.isCritical();
    }

    public boolean usingAddressPairs() {
        return linkerOptions.allowsHeapAccess();
    }

    public int numLeadingParams() {
        return 2 + (linkerOptions.hasCapturedCallState() ? 1 : 0); // 2 for addr, allocator
    }

    public String asString() {
        StringBuilder sb = new StringBuilder();

        sb.append("CallingSequence: {\n");
        sb.append("  callerMethodType: ").append(callerMethodType);
        sb.append("  calleeMethodType: ").append(calleeMethodType);
        sb.append("  FunctionDescriptor: ").append(desc);
        sb.append("  Argument Bindings:\n");
        for (int i = 0; i < argumentBindingsCount(); i++) {
            sb.append("    ").append(i).append(": ").append(argumentBindings.get(i)).append("\n");
        }
        if (!returnBindings.isEmpty()) {
            sb.append("    ").append("Return: ").append(returnBindings).append("\n");
        }
        sb.append("}\n");

        return sb.toString();
    }
}
