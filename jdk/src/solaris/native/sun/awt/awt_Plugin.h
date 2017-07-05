/*
 * Copyright (c) 1999, 2001, Oracle and/or its affiliates. All rights reserved.
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
 * Fix 4221246: Export functions for Netscape to use to get AWT info
 */

#ifndef _AWT_PLUGIN_H_
#define _AWT_PLUGIN_H_

#include <jni.h>
#include <X11/Xlib.h>
#include <X11/Xutil.h>

void getAwtLockFunctions(void (**AwtLock)(JNIEnv *),
                         void (**AwtUnlock)(JNIEnv *),
                         void (**AwtNoFlushUnlock)(JNIEnv *),
                         void *);

void getExtAwtData(Display *,
                   int32_t,
                   int32_t *,      /* awt_depth */
                   Colormap *,     /* awt_cmap  */
                   Visual **,      /* awt_visInfo.visual */
                   int32_t *,      /* awt_num_colors */
                   void *);

void getAwtData(int32_t *, Colormap *, Visual **, int32_t *, void *);

Display *getAwtDisplay(void);

#endif /* _AWT_PLUGIN_H_ */
