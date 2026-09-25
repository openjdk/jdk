/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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
 * Small native helper app to revive a process from a core or miniudump,
 * and run a jcmd command in the revived JVM.
 *
 * LD_USE_LOAD_BIAS=1 is required on Linux.
 */

#include "revival.hpp"

/**
 * Show usage message, and exit with an error status.
 */
void usageExit(const char* s) {
    error("usage: %s [ -L/path/to/libdir ] [ -R/path/for/dir ] COREFILE jcmd DCOMMAND...\n", s);
}

int main(int argc, char** argv) {
    char* corename;
    const char* libdirs = nullptr;
    const char* revival_data = nullptr;
    char command[BUFLEN];
    memset(command, 0, BUFLEN);
    int n = 1;

    if (argc < 4) {
        usageExit(argv[0]);
    }
    // Arguments:
    while (true) {
        if (strncmp(argv[n], "-L", 2) == 0) {
            // -L/path/to/libdir
            // Can be a list of directories, delimited by path separator char : or ;
            if (strlen(argv[n]) > 2) {
                libdirs = argv[n] + 2;
                n++;
            } else {
                error("Use -L/PATH to specify library directory, e.g. -L/my/libs");
            }
        } else if (strncmp(argv[n], "-R", 2) == 0) {
            // -R/path/for/dir
            if (strlen(argv[n]) > 2) {
                revival_data = argv[n] + 2;
                n++;
            } else {
                error("Use -R/PATH to specify directory to contain revival cache, e.g. -R/my/dir");
            }
        } else {
            break;
        }
    }
    if ((argc - n) < 2 ) {
        usageExit(argv[0]);
    }

    corename = argv[n++];

    // jcmd expected argument:
    if (strcmp(argv[n++], "jcmd") != 0) {
        error("jcmd keyword expected.");
    }
    // Build jcmd command line from all additional arguments:
    for (int i = n; i < argc; i++) {
        if (i != n) {
            strncat(command, " ", BUFLEN - 1); // Add a space if not adding the first item.
        }
        strncat(command, argv[i], BUFLEN - 1);
    }

    int e = revive_image(corename, libdirs, revival_data);
    if (e < 0) {
        logv("revivalhelper: revive failed: %d\n", e);
        // Will call revived_exit below, don't call error().
    } else {
        e = revived_dcmd(command);
    }
    revived_exit(e);
}
