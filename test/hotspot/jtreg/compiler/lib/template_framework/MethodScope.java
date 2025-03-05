/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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

package compiler.lib.template_framework;

/**
 * The {@link MethodScope} sets the scope of a method body, i.e. after its opening bracked and before its
 * closing bracket. This allows dispatching {@link CodeGenerator}s to the top of the method body, for
 * example for variable declarations, see {@link DispatchScope#dispatch}. In {@link Template}s, a
 * {@link MethodScope} is opened with {@code #open(method)} and closed with {@code #close(method)}.
 */
final class MethodScope extends DispatchScope {

    /**
     * Create a new {@link MethodScope}.
     *
     * @param parent Parent scope or null if the new scope is an outermost scope.
     * @param fuel Remaining fuel for recursive {@link CodeGenerator} instantiations.
     */
    public MethodScope(Scope parent, long fuel) {
        super(parent, fuel);
    }
}
