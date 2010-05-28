/*
 * Copyright (c) 1994, 1998, Oracle and/or its affiliates. All rights reserved.
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
 * Win32 system dependent low level io definitions
 */

#ifndef _JAVASOFT_WIN32_IO_MD_H_
#define _JAVASOFT_WIN32_IO_MD_H_

#include <stdio.h>
#include <io.h>                 /* For read(), lseek() etc. */
#include <direct.h>             /* For mkdir() */
#include <windows.h>
#include <winsock.h>
#include <sys/types.h>
#include <ctype.h>
#include <stdlib.h>

#include "jvm_md.h"

#define R_OK    4
#define W_OK    2
#define X_OK    1
#define F_OK    0

#define MAXPATHLEN _MAX_PATH

#define S_ISFIFO(mode)  (((mode) & _S_IFIFO) == _S_IFIFO)
#define S_ISCHR(mode)   (((mode) & _S_IFCHR) == _S_IFCHR)
#define S_ISDIR(mode)   (((mode) & _S_IFDIR) == _S_IFDIR)
#define S_ISREG(mode)   (((mode) & _S_IFREG) == _S_IFREG)

#define LINE_SEPARATOR "\r\n"

#endif /* !_JAVASOFT_WIN32_IO_MD_H_ */
