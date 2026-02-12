/*
 * Copyright (c) 2024, 2025, Oracle and/or its affiliates. All rights reserved.
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

#include <libgen.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>

#define JAVA_EXECUTABLE_NAME "java"

#ifndef LAUNCHER_ARGS
#error LAUNCHER_ARGS must be defined
#endif

static char *launcher_args[] = LAUNCHER_ARGS;

int main(int argc, char *argv[]) {
    ////////////////////////////////////////////////////////////////////////////
    // Create a fully qualified path to the java executable in the same
    // directory as this file resides in.

    char *our_full_path = realpath(argv[0], NULL);
    if (our_full_path == NULL) {
        perror("failed to get the full path of the executable");
        return 1;
    }

    char *last_slash_pos = strrchr(our_full_path, '/');
    if (last_slash_pos == NULL) {
        fprintf(stderr, "no '/' found in the full path of the executable\n");
        return 1;
    }

    size_t base_length = last_slash_pos - our_full_path + 1;
    size_t java_path_length = base_length + strlen(JAVA_EXECUTABLE_NAME) + 1;

    char *java_path = malloc(java_path_length);
    if (java_path == NULL) {
        perror("malloc failed");
        return 1;
    }

    memcpy(java_path, our_full_path, base_length);
    strcpy(java_path + base_length, JAVA_EXECUTABLE_NAME);

    ////////////////////////////////////////////////////////////////////////////
    // Build the argument list: our executable name + launcher args + users args

    int launcher_argsc = sizeof(launcher_args) / sizeof(char *);

    char **java_args = malloc((launcher_argsc + argc + 1) * sizeof(char *));
    if (java_args == NULL) {
        perror("malloc failed");
        return 1;
    }

    // Our executable name
    java_args[0] = argv[0];

    // Launcher arguments
    for (int i = 0; i < launcher_argsc; i++) {
        java_args[i + 1] = launcher_args[i];
    }

    // User arguments
    for (int i = 1; i < argc; i++) {
        java_args[launcher_argsc + i] = argv[i];
    }

    java_args[launcher_argsc + argc] = NULL;

    ////////////////////////////////////////////////////////////////////////////
    // Finally execute the real java process with the constructed arguments

    if (getenv("_JAVA_LAUNCHER_DEBUG")) {
        char *program_name = basename(argv[0]);

        fprintf(stderr, "%s: executing: '%s'", program_name, java_path);
        for (int i = 0; java_args[i] != NULL; i++) {
            fprintf(stderr, " '%s' ", java_args[i]);
        }
        fprintf(stderr, "\n");
    }

    execv(java_path, java_args);

    // Should not reach here, unless something went wrong
    perror("execv failed");
    return 1;
}
