/*
 * Copyright (c) 2003, 2010, Oracle and/or its affiliates. All rights reserved.
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
 *
 */

#ifndef OS_SOLARIS_DTRACE_GENERATEJVMOFFSETS_H
#define OS_SOLARIS_DTRACE_GENERATEJVMOFFSETS_H

#include <stdio.h>
#include <strings.h>

typedef enum GEN_variant {
        GEN_OFFSET = 0,
        GEN_INDEX  = 1,
        GEN_TABLE  = 2
} GEN_variant;

extern "C" {
        int generateJvmOffsets(GEN_variant gen_var);
        void gen_prologue(GEN_variant gen_var);
        void gen_epilogue(GEN_variant gen_var);
}

#endif // OS_SOLARIS_DTRACE_GENERATEJVMOFFSETS_H
