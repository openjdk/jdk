/*
 * reserved comment block
 * DO NOT REMOVE OR ALTER!
 */
/*
 * $XFree86: xc/include/extensions/randr.h,v 1.4 2001/11/24 07:24:58 keithp Exp $
 *
 * Copyright © 2000, Compaq Computer Corporation,
 * Copyright © 2002, Hewlett Packard, Inc.
 *
 * Permission to use, copy, modify, distribute, and sell this software and its
 * documentation for any purpose is hereby granted without fee, provided that
 * the above copyright notice appear in all copies and that both that
 * copyright notice and this permission notice appear in supporting
 * documentation, and that the name of Compaq or HP not be used in advertising
 * or publicity pertaining to distribution of the software without specific,
 * written prior permission.  HP makes no representations about the
 * suitability of this software for any purpose.  It is provided "as is"
 * without express or implied warranty.
 *
 * HP DISCLAIMS ALL WARRANTIES WITH REGARD TO THIS SOFTWARE, INCLUDING ALL
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS, IN NO EVENT SHALL HP
 * BE LIABLE FOR ANY SPECIAL, INDIRECT OR CONSEQUENTIAL DAMAGES OR ANY DAMAGES
 * WHATSOEVER RESULTING FROM LOSS OF USE, DATA OR PROFITS, WHETHER IN AN ACTION
 * OF CONTRACT, NEGLIGENCE OR OTHER TORTIOUS ACTION, ARISING OUT OF OR IN
 * CONNECTION WITH THE USE OR PERFORMANCE OF THIS SOFTWARE.
 *
 * Author:  Jim Gettys, HP Labs, Hewlett-Packard, Inc.
 */

#ifndef _RANDR_H_
#define _RANDR_H_

typedef unsigned short  Rotation;
typedef unsigned short  SizeID;
typedef unsigned short  SubpixelOrder;

#define RANDR_NAME              "RANDR"
#define RANDR_MAJOR             1
#define RANDR_MINOR             1

#define RRNumberErrors          0
#define RRNumberEvents          1

#define X_RRQueryVersion        0
/* we skip 1 to make old clients fail pretty immediately */
#define X_RROldGetScreenInfo    1
#define X_RR1_0SetScreenConfig  2
/* V1.0 apps share the same set screen config request id */
#define X_RRSetScreenConfig     2
#define X_RROldScreenChangeSelectInput  3
/* 3 used to be ScreenChangeSelectInput; deprecated */
#define X_RRSelectInput         4
#define X_RRGetScreenInfo       5

/* used in XRRSelectInput */

#define RRScreenChangeNotifyMask  (1L << 0)

#define RRScreenChangeNotify    0

/* used in the rotation field; rotation and reflection in 0.1 proto. */
#define RR_Rotate_0             1
#define RR_Rotate_90            2
#define RR_Rotate_180           4
#define RR_Rotate_270           8

/* new in 1.0 protocol, to allow reflection of screen */

#define RR_Reflect_X            16
#define RR_Reflect_Y            32

#define RRSetConfigSuccess              0
#define RRSetConfigInvalidConfigTime    1
#define RRSetConfigInvalidTime          2
#define RRSetConfigFailed               3

#endif  /* _RANDR_H_ */
