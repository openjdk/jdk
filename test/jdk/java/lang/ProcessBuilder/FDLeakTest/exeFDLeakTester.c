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

#include <errno.h>
#include <fcntl.h>
#include <stdio.h>
#include <string.h>
#include <unistd.h>

/* Check if any fd past stderr is valid; if true, print warning on stderr and return -1
 *
 * Note: check without accessing /proc since:
 * - non-portable
 * - may cause creation of temporary file descriptors
 */
int main(int argc, char** argv) {
    int errors = 0;
    int rc = 0;
    char buf[128];
    int max_fd = (int)sysconf(_SC_OPEN_MAX);
    if (max_fd == -1) {
        snprintf(buf, sizeof(buf), "*** sysconf(_SC_OPEN_MAX) failed? (%d) ***\n", errno);
        rc = write(2, buf, strlen(buf));
        max_fd = 10000;
    }
    // We start after stderr fd
    for (int fd = 3; fd < max_fd; fd++) {
        if (fcntl(fd, F_GETFD, 0) >= 0) {
            // Error: found valid file descriptor
            errors++;
            snprintf(buf, sizeof(buf), "*** Parent leaked file descriptor %d ***\n", fd);
            rc = write(2, buf, strlen(buf));
        }
    }
    return errors > 0 ? -1 : 0;
}
