/*
 * Copyright 2006 Sun Microsystems, Inc.  All Rights Reserved.
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

#include "stdhdrs.h"
#include "windows.h"
#include <windowsx.h>
#include <zmouse.h>

#include "awt.h"
#include "awt_BitmapUtil.h"

HBITMAP BitmapUtil::CreateTransparencyMaskFromARGB(int width, int height, int* imageData)
{
    //Scan lines should be aligned to word boundary
    int bufLength = ((width + 15) / 16 * 2) * height;//buf length (bytes)
    int* srcPos = imageData;
    char* buf = new char[bufLength];
    char* bufPos = buf;
    int tmp = 0;
    int cbit = 0x80;
    if (buf == NULL) return NULL;
    for (int i = 0; i < height; i++) {
        for (int j = 0; j < width; j++) {
            //cbit is shifted right for every pixel
            //next byte is stored when cbit is zero
            if ((cbit & 0xFF) == 0x00) {
                *bufPos = tmp;
                bufPos++;
                tmp = 0;
                cbit = 0x80;
            }
            unsigned char alpha = (*srcPos >> 0x18) & 0xFF;
            if (alpha == 0x00) {
                tmp |= cbit;
            }
            cbit >>= 1;
            srcPos++;
        }
        //save last word at the end of scan line even if it's incomplete
        *bufPos = tmp;
        bufPos++;
        tmp = 0;
        cbit = 0x80;
        //add word-padding byte if necessary
        if (((bufPos - buf) & 0x01) == 0x01) {
            *bufPos = 0;
            bufPos++;
        }
    }
    HBITMAP bmp = CreateBitmap(width, height, 1, 1, buf);
    delete[] buf;

    return bmp;
}

//BITMAPINFO extended with
typedef struct tagBITMAPINFOEX  {
    BITMAPINFOHEADER bmiHeader;
    DWORD            dwMasks[256];
}   BITMAPINFOEX, *LPBITMAPINFOEX;

/*
 * Creates 32-bit ARGB bitmap from specified RAW data.
 * This function may not work on OS prior to Win95.
 * See MSDN articles for CreateDIBitmap, BITMAPINFOHEADER,
 * BITMAPV4HEADER, BITMAPV5HEADER for additional info.
 */
HBITMAP BitmapUtil::CreateV4BitmapFromARGB(int width, int height, int* imageData)
{
    BITMAPINFOEX    bitmapInfo;
    HDC             hDC;
    char            *bitmapData;
    HBITMAP         hTempBitmap;
    HBITMAP         hBitmap;

    hDC = ::GetDC(::GetDesktopWindow());
    if (!hDC) {
        return NULL;
    }

    memset(&bitmapInfo, 0, sizeof(BITMAPINFOEX));
    bitmapInfo.bmiHeader.biSize = sizeof(BITMAPINFOHEADER);
    bitmapInfo.bmiHeader.biWidth = width;
    bitmapInfo.bmiHeader.biHeight = -height;
    bitmapInfo.bmiHeader.biPlanes = 1;
    bitmapInfo.bmiHeader.biBitCount = 32;
    bitmapInfo.bmiHeader.biCompression = BI_RGB;

    hTempBitmap = ::CreateDIBSection(hDC, (BITMAPINFO*)&(bitmapInfo),
                                    DIB_RGB_COLORS,
                                    (void**)&(bitmapData),
                                    NULL, 0);

    if (!bitmapData) {
        ReleaseDC(::GetDesktopWindow(), hDC);
        return NULL;
    }

    int* src = imageData;
    char* dest = bitmapData;
    for (int i = 0; i < height; i++ ) {
        for (int j = 0; j < width; j++ ) {
            unsigned char alpha = (*src >> 0x18) & 0xFF;
            if (alpha == 0) {
                dest[3] = dest[2] = dest[1] = dest[0] = 0;
            } else {
                dest[3] = alpha;
                dest[2] = (*src >> 0x10) & 0xFF;
                dest[1] = (*src >> 0x08) & 0xFF;
                dest[0] = *src & 0xFF;
            }
            src++;
            dest += 4;
        }
    }

    hBitmap = CreateDIBitmap(hDC,
                             (BITMAPINFOHEADER*)&bitmapInfo,
                             CBM_INIT,
                             (void *)bitmapData,
                             (BITMAPINFO*)&bitmapInfo,
                             DIB_RGB_COLORS);

    ::DeleteObject(hTempBitmap);
    ::ReleaseDC(::GetDesktopWindow(), hDC);
    ::GdiFlush();
    return hBitmap;
}
