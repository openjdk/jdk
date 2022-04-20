/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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

#include <stdio.h>
#include <stdlib.h>
#include <dlfcn.h>

int main(int argc, char** argv)
{
    void *handle;

    if (argc != 2) {
        fprintf(stderr, "Usage: %s <lib_filename_or_full_path>\n", argv[0]);
        return EXIT_FAILURE;
    }

    printf("Attempting to load library '%s'...\n", argv[1]);

    handle = dlopen(argv[1], RTLD_LAZY);

    if (handle == NULL) {
       fprintf(stderr, "Unable to load library!\n");
       return EXIT_FAILURE;
    }

    printf("Library successfully loaded!\n");

    return dlclose(handle);
}
