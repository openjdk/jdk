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

#ifdef _WIN32
    #include <windows.h>
#else
    #include <unistd.h>
#endif
/**
 * Command line program to sleep at least given number of seconds.
 * The behavior should equivalent to the Unix sleep command.
 * Actual time sleeping may vary if interrupted, the remaining time
 * returned from sleep has limited accuracy.
 *
 * Note: the file name prefix "exe" identifies the source should be built into BasicSleep(.exe).
 */
int main(int argc, char** argv) {
    int seconds;

    if (argc < 2 || (seconds = atoi(argv[1])) < 0) {
        fprintf(stderr, "usage: BasicSleep <non-negative seconds>\n");
        exit(1);
    }

#ifdef _WIN32
    Sleep(seconds * 1000);
#else
    while ((seconds = sleep(seconds)) > 0) {
        // until no more to sleep
    }
#endif
}
