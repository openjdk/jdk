/*
 * Copyright (c) 2010, Oracle and/or its affiliates. All rights reserved.
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

package java.dyn;

/**
 * A {@code ConstantCallSite} is a {@link CallSite} whose target is permanent, and can never be changed.
 * An {@code invokedynamic} instruction linked to a {@code ConstantCallSite} is permanently
 * bound to the call site's target.
 * @author John Rose, JSR 292 EG
 */
public class ConstantCallSite extends CallSite {
    /** Create a call site with a permanent target.
     * @throws NullPointerException if the proposed target is null
     */
    public ConstantCallSite(MethodHandle target) {
        super(target);
    }
    /**
     * Throw an {@link UnsupportedOperationException}, because this kind of call site cannot change its target.
     */
    @Override public final void setTarget(MethodHandle ignore) {
        throw new UnsupportedOperationException("ConstantCallSite");
    }
}
