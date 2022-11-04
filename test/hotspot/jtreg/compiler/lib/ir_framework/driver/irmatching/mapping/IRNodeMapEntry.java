/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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

package compiler.lib.ir_framework.driver.irmatching.mapping;

import compiler.lib.ir_framework.CompilePhase;
import compiler.lib.ir_framework.IR;
import compiler.lib.ir_framework.IRNode;

/**
 * This interface represents an IR node mapping entry in {@code IR_NODE_MAPPINGS} in {@link IRNode}. Each entry must
 * return a regex for a {@link CompilePhase} or null if it does not support the compile phase. Additionally, a default
 * compile phase must be specified to fall back to when a user test specifies {@link CompilePhase#DEFAULT} in
 * {@link IR#phase} or does not provide the {@link IR#phase} attribute.
 *
 * @see IRNode
 * @see CompilePhase
 */
public interface IRNodeMapEntry {
    /**
     * Return the regex string which shall be used by the IR framework when matching on {@code compilePhase}.
     */
    String regexForCompilePhase(CompilePhase compilePhase);

    /**
     * Return the default compile phase that shall be used for an {@link IRNode} placeholeder string when the user test
     * specifies {@link CompilePhase#DEFAULT}.
     */
    CompilePhase defaultCompilePhase();
}
