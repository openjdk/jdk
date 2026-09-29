/*
 * Copyright (c) 2024 Alibaba Group Holding Limited. All Rights Reserved.
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
#include "export.h"
#include <stdbool.h>
#if defined(__linux__)
#include <stdio.h>
#include <stdint.h>
#include <string.h>
#include <sys/types.h>
#include <sys/stat.h>
#include <dlfcn.h>
#ifndef STATX_BASIC_STATS
#define STATX_BASIC_STATS 0x000007ffU
#endif
#ifndef STATX_BTIME
#define STATX_BTIME 0x00000800U
#endif
#ifndef RTLD_DEFAULT
#define RTLD_DEFAULT RTLD_LOCAL
#endif
#ifndef AT_SYMLINK_NOFOLLOW
#define AT_SYMLINK_NOFOLLOW 0x100
#endif
#ifndef AT_FDCWD
#define AT_FDCWD -100
#endif


/*
 * Timestamp structure for the timestamps in struct statx.
 */
struct my_statx_timestamp {
        int64_t   tv_sec;
        uint32_t  tv_nsec;
        int32_t   __reserved;
};

/*
 * struct statx used by statx system call on >= glibc 2.28
 * systems
 */
struct my_statx
{
  uint32_t stx_mask;
  uint32_t stx_blksize;
  uint64_t stx_attributes;
  uint32_t stx_nlink;
  uint32_t stx_uid;
  uint32_t stx_gid;
  uint16_t stx_mode;
  uint16_t __statx_pad1[1];
  uint64_t stx_ino;
  uint64_t stx_size;
  uint64_t stx_blocks;
  uint64_t stx_attributes_mask;
  struct my_statx_timestamp stx_atime;
  struct my_statx_timestamp stx_btime;
  struct my_statx_timestamp stx_ctime;
  struct my_statx_timestamp stx_mtime;
  uint32_t stx_rdev_major;
  uint32_t stx_rdev_minor;
  uint32_t stx_dev_major;
  uint32_t stx_dev_minor;
  uint64_t __statx_pad2[14];
};

typedef int statx_func(int dirfd, const char *restrict pathname, int flags,
                       unsigned int mask, struct my_statx *restrict statxbuf);

static statx_func* my_statx_func = NULL;
#endif  //#defined(__linux__)

// static boolean linuxIsCreationTimeSupported(char* file)
EXPORT bool linuxIsCreationTimeSupported(char* file) {
#if defined(__linux__)
    struct my_statx stx = {0};
    int ret, atflag = AT_SYMLINK_NOFOLLOW;
    unsigned int mask = STATX_BASIC_STATS | STATX_BTIME;

    my_statx_func = (statx_func*) dlsym(RTLD_DEFAULT, "statx");
    if (my_statx_func == NULL) {
        return false;
    }

    if (file == NULL) {
        printf("input file error!\n");
        return false;
    }

    ret = my_statx_func(AT_FDCWD, file, atflag, mask, &stx);
    if (ret != 0) {
        return false;
    }
    // On some systems where statx is available but birth time might still not
    // be supported as it's file system specific. The only reliable way to
    // check for supported or not is looking at the filled in STATX_BTIME bit
    // in the returned statx buffer mask.
    if ((stx.stx_mask & STATX_BTIME) != 0)
        return true;
    return false;
#else
    return false;
#endif
}
