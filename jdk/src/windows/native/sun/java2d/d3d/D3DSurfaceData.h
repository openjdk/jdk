/*
 * Copyright 2005 Sun Microsystems, Inc.  All Rights Reserved.
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

#ifndef D3DSurfaceData_h_Included
#define D3DSurfaceData_h_Included

#include "java_awt_image_AffineTransformOp.h"
#include "sun_java2d_d3d_D3DSurfaceData.h"
#include "Win32SurfaceData.h"

// Shortcut macros

#define D3D_PLAIN_SURFACE      sun_java2d_d3d_D3DSurfaceData_D3D_PLAIN_SURFACE
#define D3D_TEXTURE_SURFACE    sun_java2d_d3d_D3DSurfaceData_D3D_TEXTURE_SURFACE
#define D3D_BACKBUFFER_SURFACE sun_java2d_d3d_D3DSurfaceData_D3D_BACKBUFFER_SURFACE
#define D3D_RTT_SURFACE        sun_java2d_d3d_D3DSurfaceData_D3D_RTT_SURFACE

#define D3D_RENDER_TARGET      sun_java2d_d3d_D3DSurfaceData_D3D_RENDER_TARGET
#define D3D_ATTACHED_SURFACE   sun_java2d_d3d_D3DSurfaceData_D3D_ATTACHED_SURFACE

#define PF_INVALID          sun_java2d_d3d_D3DSurfaceData_PF_INVALID
#define PF_INT_ARGB         sun_java2d_d3d_D3DSurfaceData_PF_INT_ARGB
#define PF_INT_RGB          sun_java2d_d3d_D3DSurfaceData_PF_INT_RGB
#define PF_INT_RGBX         sun_java2d_d3d_D3DSurfaceData_PF_INT_RGBX
#define PF_INT_BGR          sun_java2d_d3d_D3DSurfaceData_PF_INT_BGR
#define PF_USHORT_565_RGB   sun_java2d_d3d_D3DSurfaceData_PF_USHORT_565_RGB
#define PF_USHORT_555_RGB   sun_java2d_d3d_D3DSurfaceData_PF_USHORT_555_RGB
#define PF_USHORT_555_RGBX  sun_java2d_d3d_D3DSurfaceData_PF_USHORT_555_RGBX
#define PF_INT_ARGB_PRE     sun_java2d_d3d_D3DSurfaceData_PF_INT_ARGB_PRE
#define PF_USHORT_4444_ARGB sun_java2d_d3d_D3DSurfaceData_PF_USHORT_4444_ARGB

typedef struct _D3DSDOps D3DSDOps;

struct _D3DSDOps {
    Win32SDOps dxOps;
    jint d3dType; // surface type (plain/texture/bb/rtt) - see D3DSurfaceData.java
};

#define D3DSD_XFORM_NEAREST_NEIGHBOR \
    java_awt_image_AffineTransformOp_TYPE_NEAREST_NEIGHBOR
#define D3DSD_XFORM_BILINEAR \
    java_awt_image_AffineTransformOp_TYPE_BILINEAR


#endif /* D3DSurfaceData_h_Included */
