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


#include "generateJvmOffsets.h"

const char *HELP =
    "HELP: generateJvmOffsets {-header | -index | -table} \n";

int main(int argc, const char *argv[]) {
    GEN_variant gen_var;

    if (argc != 2) {
        printf("%s", HELP);
        return 1;
    }

    if (0 == strcmp(argv[1], "-header")) {
        gen_var = GEN_OFFSET;
    }
    else if (0 == strcmp(argv[1], "-index")) {
        gen_var = GEN_INDEX;
    }
    else if (0 == strcmp(argv[1], "-table")) {
        gen_var = GEN_TABLE;
    }
    else {
        printf("%s", HELP);
        return 1;
    }
    return generateJvmOffsets(gen_var);
}
