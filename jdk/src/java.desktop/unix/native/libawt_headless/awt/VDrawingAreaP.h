/*
 * Copyright (c) 1997, Oracle and/or its affiliates. All rights reserved.
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

#ifndef _VDrawingAreaP_h_
#define _VDrawingAreaP_h_

#include <Xm/DrawingAP.h>
#include "VDrawingArea.h"


/***************************************************************
 * VDrawingArea Widget Data Structures
 *
 *
 **************************************************************/

/* Define part class structure */
typedef struct _VDrawingAreaClass {
        XtPointer                       extension;
} VDrawingAreaClassPart;

/* Define the full class record */
typedef struct _VDrawingAreaClassRec {
        CoreClassPart           core_class;
        CompositeClassPart      composite_class;
        ConstraintClassPart     constraint_class;
        XmManagerClassPart      manager_class;
        XmDrawingAreaClassPart  drawing_area_class;
        VDrawingAreaClassPart   vdrawingarea_class;
} VDrawingAreaClassRec;

/* External definition for class record */
extern VDrawingAreaClassRec vDrawingAreaClassRec;

typedef struct {
        Visual *visual;
} VDrawingAreaPart;

/****************************************************************
 *
 * Full instance record declaration
 *
 ****************************************************************/

typedef struct _VDrawingAreaRec
{
        CorePart                core;
        CompositePart           composite;
        ConstraintPart          constraint;
        XmManagerPart           manager;
        XmDrawingAreaPart       drawing_area;
        VDrawingAreaPart        vdrawing_area;
} VDrawingAreaRec;



#endif /* !_VDrawingAreaP_h_ */
