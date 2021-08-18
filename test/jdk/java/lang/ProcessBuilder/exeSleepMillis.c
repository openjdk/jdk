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
#include <time.h>

/**
 * Command line program to sleep at least given number of seconds.
 *
 * Note: the file name prefix "exe" identifies the source should be built into SleepMillis(.exe).
 */
int main(int argc, char** argv) {
    // Use higher resolution nanosleep to be able to retry accurately if interrupted
    struct timespec sleeptime;
    int millis;

    if (argc < 2 || (millis = atoi(argv[1])) < 0) {
        fprintf(stderr, "usage: sleepmillis <non-negative milli-seconds>\n");
        exit(1);
    }

    sleeptime.tv_sec = millis / 1000;
    sleeptime.tv_nsec = (millis % 1000) * 1000 * 1000;
    int rc;
    while ((rc = nanosleep(&sleeptime, &sleeptime)) > 0) {
        // Repeat until == 0 or negative (error)
    }
    exit(rc == 0 ? 0 : 1);
}
