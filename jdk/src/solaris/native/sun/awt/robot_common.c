/*
 * Copyright (c) 1999, 2006, Oracle and/or its affiliates. All rights reserved.
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

#ifdef HEADLESS
    #error This file should not be included in headless library
#endif

#ifdef MACOSX
#include <stdlib.h>
#endif

#include "robot_common.h"

/*
 * QueryColorMap is taken from multiVis.c, part of the xwd distribution from
 * X.org. It was moved here so it can be shared with awt_DataTransferer.c
 */
int32_t
QueryColorMap(Display *disp,
              Colormap src_cmap,
              Visual *src_vis,
              XColor **src_colors,
              int32_t *rShift, int32_t *gShift, int32_t *bShift)

{
     int32_t ncolors, i;
     unsigned long redMask, greenMask, blueMask;
     int32_t                 redShift, greenShift, blueShift;
     XColor *colors ;

     ncolors = src_vis->map_entries ;
     *src_colors = colors = (XColor *)calloc(ncolors,sizeof(XColor) ) ;

     if(src_vis->class != TrueColor && src_vis->class != DirectColor)
     {
         for(i=0 ; i < ncolors ; i++)
         {
                colors[i].pixel = i ;
                colors[i].pad = 0;
                colors[i].flags = DoRed|DoGreen|DoBlue;
         }
     }
     else /** src is decomposed rgb ***/
     {
        /* Get the X colormap */
        redMask = src_vis->red_mask;
        greenMask = src_vis->green_mask;
        blueMask = src_vis->blue_mask;
        redShift = 0; while (!(redMask&0x1)) {
                redShift++;
                redMask = redMask>>1;
        }
        greenShift = 0; while (!(greenMask&0x1)) {
                greenShift++;
                greenMask = greenMask>>1;
        }
        blueShift = 0; while (!(blueMask&0x1)) {
                blueShift++;
                blueMask = blueMask>>1;
        }
        *rShift = redShift ;
        *gShift = greenShift ;
        *bShift = blueShift ;
        for (i=0; i<ncolors; i++) {
                if( (uint32_t)i <= redMask) colors[i].pixel = (i<<redShift) ;
                if( (uint32_t)i <= greenMask) colors[i].pixel |= (i<<greenShift) ;
                if( (uint32_t)i <= blueMask) colors[i].pixel |= (i<<blueShift) ;
                /***** example :for gecko's 3-3-2 map, blue index should be <= 3
.
                colors[i].pixel = (i<<redShift)|(i<<greenShift)|(i<<blueShift);
                *****/
                colors[i].pad = 0;
                colors[i].flags = DoRed|DoGreen|DoBlue;
        }
      }

      XQueryColors(disp, src_cmap, colors, ncolors);
      return ncolors ;
}
