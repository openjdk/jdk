/*
 * Copyright (c) 2008, 2012, Oracle and/or its affiliates. All rights reserved.
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

#include <stdio.h>
#include <errno.h>
#include <unistd.h>
#include <fcntl.h>
#include <sys/stat.h>

/**
 * Generates sun.nio.fs.UnixConstants
 */

static void out(char* s) {
    printf("%s\n", s);
}

static void emit(char* name, int value) {
    printf("    static final int %s = %d;\n", name, value);
}

static void emitX(char* name, int value) {
    printf("    static final int %s = 0x%x;\n", name, value);
}

#define DEF(X) emit(#X, X);
#define DEFX(X) emitX(#X, X);

int main(int argc, const char* argv[]) {
    out("// AUTOMATICALLY GENERATED FILE - DO NOT EDIT                                  ");
    out("package sun.nio.fs;                                                            ");
    out("class UnixConstants {                                                          ");
    out("    private UnixConstants() { }                                                ");

    // open flags
    DEF(O_RDONLY);
    DEF(O_WRONLY);
    DEF(O_RDWR);
    DEFX(O_APPEND);
    DEFX(O_CREAT);
    DEFX(O_EXCL);
    DEFX(O_TRUNC);
    DEFX(O_SYNC);
#ifndef O_DSYNC
    // At least FreeBSD doesn't define O_DSYNC
    emit("O_DSYNC", O_SYNC);
#else
    DEFX(O_DSYNC);
#endif
#ifdef O_NOFOLLOW
    DEFX(O_NOFOLLOW);
#else
    // not supported (dummy values will not be used at runtime).
    emitX("O_NOFOLLOW", 0x0);
#endif

    // mode masks
    emitX("S_IAMB",
         (S_IRUSR|S_IWUSR|S_IXUSR|S_IRGRP|S_IWGRP|S_IXGRP|S_IROTH|S_IWOTH|S_IXOTH));
    DEF(S_IRUSR);
    DEF(S_IWUSR);
    DEF(S_IXUSR);
    DEF(S_IRGRP);
    DEF(S_IWGRP);
    DEF(S_IXGRP);
    DEF(S_IROTH);
    DEF(S_IWOTH);
    DEF(S_IXOTH);
    DEFX(S_IFMT);
    DEFX(S_IFREG);
    DEFX(S_IFDIR);
    DEFX(S_IFLNK);
    DEFX(S_IFCHR);
    DEFX(S_IFBLK);
    DEFX(S_IFIFO);

    // access modes
    DEF(R_OK);
    DEF(W_OK);
    DEF(X_OK);
    DEF(F_OK);

    // errors
    DEF(ENOENT);
    DEF(EACCES);
    DEF(EEXIST);
    DEF(ENOTDIR);
    DEF(EINVAL);
    DEF(EXDEV);
    DEF(EISDIR);
    DEF(ENOTEMPTY);
    DEF(ENOSPC);
    DEF(EAGAIN);
    DEF(ENOSYS);
    DEF(ELOOP);
    DEF(EROFS);
#ifndef ENODATA
    // Only used in Linux java source, provide any value so it compiles
    emit("ENODATA", ELAST);
#else
    DEF(ENODATA);
#endif
    DEF(ERANGE);
    DEF(EMFILE);

    // flags used with openat/unlinkat/etc.
#if defined(AT_SYMLINK_NOFOLLOW) && defined(AT_REMOVEDIR)
    DEFX(AT_SYMLINK_NOFOLLOW)
    DEFX(AT_REMOVEDIR);
#else
    // not supported (dummy values will not be used at runtime).
    emitX("AT_SYMLINK_NOFOLLOW", 0x0);
    emitX("AT_REMOVEDIR", 0x0);
#endif

    out("}                                                                              ");

    return 0;
}
