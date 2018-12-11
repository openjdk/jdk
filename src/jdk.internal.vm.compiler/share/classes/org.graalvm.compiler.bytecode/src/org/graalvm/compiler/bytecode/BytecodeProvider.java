/*
 * Copyright (c) 2016, 2018, Oracle and/or its affiliates. All rights reserved.
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


package org.graalvm.compiler.bytecode;

import jdk.vm.ci.meta.ResolvedJavaMethod;

/**
 * Provides a {@link Bytecode} object for interposing on the bytecode of a
 * {@link ResolvedJavaMethod} (i.e., potentially getting bytecode different than
 * {@link ResolvedJavaMethod#getCode()}).
 */
public interface BytecodeProvider {

    /**
     * Gets a {@link Bytecode} object that supplies bytecode properties for {@code method}.
     */
    Bytecode getBytecode(ResolvedJavaMethod method);

    /**
     * Determines if this provider supports the INVOKEDYNAMIC bytecode.
     */
    boolean supportsInvokedynamic();

    /**
     * Determines if methods parsed using this provider should be recorded so that method
     * redefinition can invalidate the resulting code.
     */
    boolean shouldRecordMethodDependencies();
}
