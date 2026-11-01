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

#include <assert.h>
#include <stdbool.h>

#include <unistd.h>
#include "childproc.h"
#include "childproc_errorcodes.h"

void buildErrorCode(errcode_t* errcode, int step, int hint, int errno_) {
    errcode_t e;

    assert(step < (1 << 8));
    e.step = step;

    assert(errno_ < (1 << 8));
    e.errno_ = errno_;

    const int maxhint = (1 << 16);
    e.hint = hint < maxhint ? hint : maxhint;

    (*errcode) = e;
}

int exitCodeFromErrorCode(errcode_t errcode) {
    /* We use the fail step number as exit code, but avoid 0 and 1
     * and try to avoid the [128..256) range since that one is used by
     * shells to codify abnormal kills by signal. */
    return 0x10 + errcode.step;
}

bool sendErrorCode(int fd, errcode_t errcode) {
    return writeFully(fd, &errcode, sizeof(errcode)) == sizeof(errcode);
}

bool sendAlivePing(int fd) {
    errcode_t errcode;
    buildErrorCode(&errcode, ESTEP_CHILD_ALIVE, getpid(), 0);
    return sendErrorCode(fd, errcode);
}
