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

#ifndef D3DRUNTIMETEST_H
#define D3DRUNTIMETEST_H

#include "ddrawObject.h"
#include "dxCapabilities.h"
#include "D3DContext.h"

/*
 * This is a minimum set of capabilities required for
 * enabling D3D pipeline. If any of these is
 * missing, d3d will be disabled completely.
 *
 * This set is used if the use of d3d pipeline is
 * forced via flag or env. variable.
 */
#define J2D_D3D_REQUIRED_RESULTS ( \
   J2D_D3D_HW_OK                 | \
   J2D_D3D_DEVICE_OK             | \
   J2D_D3D_DEPTH_SURFACE_OK      | \
   J2D_D3D_PLAIN_SURFACE_OK      | \
   J2D_D3D_PIXEL_FORMATS_OK      | \
   J2D_D3D_OP_TEXTURE_SURFACE_OK | \
   J2D_D3D_TR_TEXTURE_SURFACE_OK | \
   J2D_D3D_SET_TRANSFORM_OK)

/*
 * This is a set of capabilities desired for
 * enabling D3D pipeline. It includes the set
 * of required caps, plus a number of rendering
 * quality related caps.
 *
 * This is the set of caps checked by default when
 * deciding on whether to enable the d3d pipeline.
 */
#define J2D_D3D_DESIRED_RESULTS ( \
   J2D_D3D_REQUIRED_RESULTS      | \
   J2D_D3D_BM_TEXTURE_SURFACE_OK | \
   J2D_D3D_TEXTURE_BLIT_OK       | \
   J2D_D3D_TEXTURE_TRANSFORM_OK  | \
   J2D_D3D_LINES_OK              | \
   J2D_D3D_LINE_CLIPPING_OK)


/*
 * This function tests the direct3d device associated
 * with the passed ddraw object.
 *
 * The function returns the capabilities of the tested device, and the
 * results of the quality testing.
 * Enabling the d3d pipeline for this particular device is based on the
 * result of this function.
 */
int TestD3DDevice(DDraw *ddObject, D3DContext *d3dContext, DxCapabilities *dxCaps);

void PrintD3DCaps(int caps);

#endif //D3DRUNTIMETEST_H
