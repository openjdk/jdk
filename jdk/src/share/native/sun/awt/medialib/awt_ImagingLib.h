/*
 * Copyright 2000 Sun Microsystems, Inc.  All Rights Reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Sun designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Sun in the LICENSE file that accompanied this code.
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
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 */
#ifndef _AWT_IMAGINGLIB_H_
#define _AWT_IMAGINGLIB_H_

#include "mlib_types.h"
#include "mlib_status.h"
#include "mlib_image_types.h"
#include "mlib_image_get.h"

/* Struct that holds the mlib function ptrs and names */
typedef struct {
    mlib_status (*fptr)();
    char *fname;
} mlibFnS_t;

typedef mlib_image *(*MlibCreateFP_t)(mlib_type, mlib_s32, mlib_s32,
                                       mlib_s32);
typedef mlib_image *(*MlibCreateStructFP_t)(mlib_type, mlib_s32, mlib_s32,
                                             mlib_s32, mlib_s32, void *);
typedef void (*MlibDeleteFP_t)(mlib_image *);

typedef struct {
    MlibCreateFP_t createFP;
    MlibCreateStructFP_t createStructFP;
    MlibDeleteFP_t deleteImageFP;
} mlibSysFnS_t;

#endif /* _AWT_IMAGINGLIB_H */
