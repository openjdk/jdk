/*
 * Copyright (c) 1998, 1999, Oracle and/or its affiliates. All rights reserved.
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

#ifndef _JAVASOFT_SOLARIS_LARGEFILE_SUPPORT_H_
#define _JAVASOFT_SOLARIS_LARGEFILE_SUPPORT_H_

#include <sys/stat.h>
#include <sys/types.h>

/**
 * This file contains the definitions for providing 64 bit File I/O support.
 */

#if !defined(_LFS_LARGEFILE) || !_LFS_LARGEFILE

#ifdef __GLIBC__
typedef jlong longlong_t;
#endif

/*
 * This definition is from Solaris 2.6; it is required by systems that do not
 * support large files (e.g., Solaris 2.5.1).
 */

typedef longlong_t      off64_t;        /* offsets within files */


#ifdef __GLIBC__
/* Doesn't matter what these are, there is no 64 bit support. */
typedef int u_longlong_t;
typedef int timestruc_t;
#define _ST_FSTYPSZ 1
#endif /* __GLIBC__ */

/*
 * The stat64 structure must be provided on systems without large file
 * support (e.g., Solaris 2.5.1).  These definitions are from Solaris 2.6
 * sys/stat.h and sys/types.h.
 */

typedef u_longlong_t    ino64_t;        /* expanded inode type  */
typedef longlong_t      blkcnt64_t;     /* count of file blocks */

struct  stat64 {
        dev_t   st_dev;
        long    st_pad1[3];
        ino64_t st_ino;
        mode_t  st_mode;
        nlink_t st_nlink;
        uid_t   st_uid;
        gid_t   st_gid;
        dev_t   st_rdev;
        long    st_pad2[2];
        off64_t st_size;
        timestruc_t st_atim;
        timestruc_t st_mtim;
        timestruc_t st_ctim;
        long    st_blksize;
        blkcnt64_t st_blocks;
        char    st_fstype[_ST_FSTYPSZ];
        long    st_pad4[8];
};

#define O_LARGEFILE     0x2000  /* Solaris 2.6 sys/fcntl.h */
#endif  /* !_LFS_LARGEFILE */

#endif /* !_JAVASOFT_SOLARIS_LARGEFILE_SUPPORT_H_ */
