/*
 * Copyright (c) 1997, 1999, Oracle and/or its affiliates. All rights reserved.
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

#include "awt.h"
#include "awt_image.h"

extern "C" {
#include "img_colors.h"
} // extern "C"

char *programname = "awt_makecube";

unsigned char cube[LOOKUPSIZE * LOOKUPSIZE * LOOKUPSIZE];

unsigned char reds[256], greens[256], blues[256], indices[256];
int num_colors;

PALETTEENTRY sysPal[256];

int sys2cmap[256];
int cmap2sys[256];
int error[256];

int cmapsize = 0;
int virtcubesize = 0;
int makecube_verbose = 0;

void printPalette(char *label, HPALETTE hPal);

void usage(char *errmsg)
{
    fprintf(stderr, "%s\n", errmsg);
    fprintf(stderr, "usage: %s [-cmapsize N] [-cubesize N]\n", programname);
    fprintf(stderr, "\t-cmapsize N   set the number of colors to allocate\n");
    fprintf(stderr, "\t              in the colormap (2 <= N <= 256)\n");
    fprintf(stderr, "\t-cubesize N   set the size of the cube of colors to\n");
    fprintf(stderr, "                scan as potential entries in the cmap\n");
    fprintf(stderr, "                (N must be a power of 2 and <= 32)\n");
    exit(1);
}

void setsyscolor(int index, int red, int green, int blue)
{
    if (index >= 0) {
        if (sysPal[index].peFlags != 0) {
            usage("Internal error: system palette conflict");
        }
    } else {
        for (int i = 0; i < 256; i++) {
            if (sysPal[i].peFlags != 0) {
                if (sysPal[i].peRed   == red &&
                    sysPal[i].peGreen == green &&
                    sysPal[i].peBlue  == blue)
                {
                    // Already there.  Ignore it.
                    return;
                }
            } else if (index < 0) {
                index = i;
            }
        }
        if (index < 0) {
            usage("Internal error: ran out of system palette entries");
        }
    }
    sysPal[index].peRed   = red;
    sysPal[index].peGreen = green;
    sysPal[index].peBlue  = blue;
    sysPal[index].peFlags = 1;
}

void addcmapcolor(int red, int green, int blue)
{
    for (int i = 0; i < num_colors; i++) {
        if (red == reds[i] && green == greens[i] && blue == blues[i]) {
            return;
        }
    }
    if (num_colors >= cmapsize) {
        usage("Internal error: more than cmapsize static colors defined");
    }
    reds[num_colors]   = red;
    greens[num_colors] = green;
    blues[num_colors]  = blue;
    num_colors++;
}

int main(int argc, char **argv)
{
    int i;

    programname = argv[0];

    for (i = 1; i < argc; i++) {
        if (strcmp(argv[i], "-cmapsize") == 0) {
            if (i++ >= argc) {
                usage("no argument to -cmapsize");
            }
            cmapsize = atoi(argv[i]);
            if (cmapsize <= 2 || cmapsize > 256) {
                usage("colormap size must be between 2 and 256");
            }
        } else if (strcmp(argv[1], "-cubesize") == 0) {
            if (i++ >= argc) {
                usage("no argument to -cubesize");
            }
            virtcubesize = atoi(argv[i]);
            if (virtcubesize == 0 ||
                (virtcubesize & (virtcubesize - 1)) != 0 ||
                virtcubesize > 32)
            {
                usage("cube size must by a power of 2 <= 32");
            }
        } else if (strcmp(argv[i], "-verbose") == 0) {
            makecube_verbose = 1;
        } else {
            usage("unknown argument");
        }
    }

    if (cmapsize == 0) {
        cmapsize = CMAPSIZE;
    }
    if (virtcubesize == 0) {
        virtcubesize = VIRTCUBESIZE;
    }

    if (0) {  // For testing
        HDC hDC = CreateDC("DISPLAY", NULL, NULL, NULL);
        HPALETTE hPal = CreateHalftonePalette(hDC);
        printPalette("Halftone palette for current display", hPal);
        printPalette("Stock DEFAULT_PALETTE", (HPALETTE)GetStockObject(DEFAULT_PALETTE));
        BITMAPINFOHEADER bmInfo = {
            sizeof(BITMAPINFOHEADER), 1, 1, 1, 8, BI_RGB, 0, 1000, 1000, 0, 0
            };
        HBITMAP hBitmap = CreateDIBitmap(hDC, &bmInfo,
                                         0, NULL, NULL, DIB_RGB_COLORS);
        HDC hMemDC = CreateCompatibleDC(hDC);
        SelectObject(hDC, hBitmap);
        hPal = CreateHalftonePalette(hMemDC);
        printPalette("Halftone palette for 8-bit DIBitmap", hPal);
        exit(0);
    }

    // Allocate Windows static system colors.
    {
        PALETTEENTRY palEntries[256];
        HPALETTE hPal = (HPALETTE)GetStockObject(DEFAULT_PALETTE);
        int n = GetPaletteEntries(hPal, 0, 256, palEntries);
        for (i = 0; i < n; i++) {
            addcmapcolor(palEntries[i].peRed,
                         palEntries[i].peGreen,
                         palEntries[i].peBlue);
            setsyscolor((i < n / 2) ? i : i + (256 - n),
                        palEntries[i].peRed,
                        palEntries[i].peGreen,
                        palEntries[i].peBlue);
        }
    }

    // Allocate java.awt.Color constant colors.
    addcmapcolor(  0,   0,   0);        // black
    addcmapcolor(255, 255, 255);        // white
    addcmapcolor(255,   0,   0);        // red
    addcmapcolor(  0, 255,   0);        // green
    addcmapcolor(  0,   0, 255);        // blue
    addcmapcolor(255, 255,   0);        // yellow
    addcmapcolor(255,   0, 255);        // magenta
    addcmapcolor(  0, 255, 255);        // cyan
    addcmapcolor(192, 192, 192);        // lightGray
    addcmapcolor(128, 128, 128);        // gray
    addcmapcolor( 64,  64,  64);        // darkGray
    addcmapcolor(255, 175, 175);        // pink
    addcmapcolor(255, 200,   0);        // orange

    img_makePalette(cmapsize, virtcubesize, LOOKUPSIZE,
                    50.0f, 250.0f,
                    num_colors, TRUE, reds, greens, blues, cube);

    if (makecube_verbose) {
        fprintf(stderr, "Calculated colormap:\n");
        for (i = 0; i < cmapsize; i++) {
            fprintf(stderr, "%3d:(%3d,%3d,%3d)   ",
                    i, reds[i], greens[i], blues[i]);
        }
        fprintf(stderr, "\n");
    }

    // Now simulate adding the halftone palette to the system
    // palette to get an idea of palette ordering.
    {
        int cubevals[6] = {0, 44, 86, 135, 192, 255};
        for (int b = 0; b < 6; b++) {
            for (int g = 0; g < 6; g++) {
                for (int r = 0; r < 6; r++) {
                    setsyscolor(-1, cubevals[r], cubevals[g], cubevals[b]);
                }
            }
        }
        int grayvals[26] = {  0,  17,  24,  30,  37,  44,  52,  60,
                             68,  77,  86,  95, 105, 114, 125, 135,
                            146, 157, 168, 180, 192, 204, 216, 229,
                            242, 255 };
        for (i = 0; i < 26; i++) {
            setsyscolor(-1, grayvals[i], grayvals[i], grayvals[i]);
        }
    }

    if (makecube_verbose) {
        fprintf(stderr, "System palette with simulated halftone palette:\n");
        for (i = 0; i < 256; i++) {
            fprintf(stderr, "%3d:(%3d,%3d,%3d)   ",
                    i, sysPal[i].peRed, sysPal[i].peGreen, sysPal[i].peBlue);
        }
    }

    if (makecube_verbose) {
        HDC hDC = CreateDC("DISPLAY", NULL, NULL, NULL);
        HPALETTE hPal = CreateHalftonePalette(hDC);
        SelectPalette(hDC, hPal, FALSE);
        RealizePalette(hDC);
        PALETTEENTRY palEntries[256];
        int n = GetSystemPaletteEntries(hDC, 0, 256, palEntries);
        fprintf(stderr,
                "realized halftone palette reads back %d entries\n", n);
        int broken = 0;
        for (i = 0; i < 256; i++) {
            char *msg1 = "";
            char *msg2 = "";
            if (palEntries[i].peRed != sysPal[i].peRed ||
                palEntries[i].peGreen != sysPal[i].peGreen ||
                palEntries[i].peBlue != sysPal[i].peBlue)
            {
                msg1 = "no sysPal match!";
                if (sysPal[i].peFlags == 0) {
                    msg2 = "(OK)";
                } else {
                    broken++;
                }
            } else if (sysPal[i].peFlags == 0) {
                msg1 = "no sysPal entry...";
            }
            fprintf(stderr,
                    "palEntries[%3d] = (%3d, %3d, %3d), flags = %d  %s %s\n",
                    i,
                    palEntries[i].peRed,
                    palEntries[i].peGreen,
                    palEntries[i].peBlue,
                    palEntries[i].peFlags, msg1, msg2);
        }
        fprintf(stderr, "%d broken entries\n", broken);
    }

#if 0
#define BIGERROR (255 * 255 * 255)

    for (i = 0; i < 256; i++) {
        sys2cmap[i] = -1;
        cmap2sys[i] = -1;
        error[i] = BIGERROR;
        // error[i] = -1 means cmap[i] is locked to cmap2sys[i]
        // error[i] >= 0 means cmap[i] may lock to cmap2sys[i] on this run
    }

    int nummapped;
    int totalmapped = 0;
    do {
        int maxerror = BIGERROR;
        for (i = 0; i < 256; i++) {
            if (sysPal[i].peFlags == 0 || sys2cmap[i] >= 0) {
                continue;
            }
            int red   = sysPal[i].peRed;
            int green = sysPal[i].peGreen;
            int blue  = sysPal[i].peBlue;
            int e = maxerror;
            int ix = -1;
            for (int j = 0; j < 256; j++) {
                if (error[j] < 0) {
                    continue;
                }
                int t = red - reds[j];
                int d = t * t;
                t = green - greens[j];
                d += t * t;
                t = blue - blues[j];
                d += t * t;
                if (d < e) {
                    e = d;
                    ix = j;
                }
            }
            if (ix >= 0) {
                if (e < error[ix]) {
                    if (cmap2sys[ix] >= 0) {
                        // To be fair we will not accept any matches
                        // looser than this former match that we just
                        // displaced with a better match.
                        if (maxerror > error[ix]) {
                            maxerror = error[ix];
                        }
                        sys2cmap[cmap2sys[ix]] = -1;
                    }
                    error[ix] = e;
                    sys2cmap[i] = ix;
                    cmap2sys[ix] = i;
                }
            }
        }
        nummapped = 0;
        for (i = 0; i < 256; i++) {
            if (error[i] >= 0) {
                if (error[i] >= maxerror) {
                    // Throw this one back to be fair to a displaced entry.
                    error[i] = BIGERROR;
                    sys2cmap[cmap2sys[i]] = -1;
                    cmap2sys[i] = -1;
                    continue;
                }
                error[i] = -1;
                nummapped++;
            }
        }
        totalmapped += nummapped;
        if (makecube_verbose) {
            fprintf(stderr, "%3d colors mapped (%3d total), maxerror = %d\n",
                    nummapped, totalmapped, maxerror);
        }
    } while (nummapped != 0);

    for (i = 0; i < 256; i++) {
        if (cmap2sys[i] < 0) {
            for (int j = 0; j < 256; j++) {
                if (sys2cmap[j] < 0) {
                    cmap2sys[i] = j;
                    sys2cmap[j] = i;
                    break;
                }
            }
            if (j == 256) {
                usage("Internal error: no unused system entry for cmap entry!\n");
            }
        }
    }
#else
    for (i = 0; i < 256; i++) {
        if (i < 10) {
            sys2cmap[i] = i;
            cmap2sys[i] = i;
        } else if (i < 20) {
            sys2cmap[256 - 20 + i] = i;
            cmap2sys[i] = 256 - 20 + i;
        } else {
            sys2cmap[i - 10] = i;
            cmap2sys[i] = i - 10;
        }
    }
#endif

    if (makecube_verbose) {
        fprintf(stderr, "cmap2sys mapping: \n");
        for (i = 0; i < 256; i++) {
            fprintf(stderr, "%4d", cmap2sys[i]);
            if (sys2cmap[cmap2sys[i]] != i) {
                usage("Internal error: bad system palette back pointer!\n");
            }
        }
        fprintf(stderr, "\n");
    }

    printf("unsigned char awt_reds[256] = {");
    for (i = 0; i < 256; i++) {
        if ((i & 0xf) == 0) printf("\n\t");
        printf("%3d,", reds[sys2cmap[i]]);
    }
    printf("\n};\n");
    printf("unsigned char awt_greens[256] = {");
    for (i = 0; i < 256; i++) {
        if ((i & 0xf) == 0) printf("\n\t");
        printf("%3d,", greens[sys2cmap[i]]);
    }
    printf("\n};\n");
    printf("unsigned char awt_blues[256] = {");
    for (i = 0; i < 256; i++) {
        if ((i & 0xf) == 0) printf("\n\t");
        printf("%3d,", blues[sys2cmap[i]]);
    }
    printf("\n};\n");
    fflush(stdout);
    return 0;
}

void printPalette(char *label, HPALETTE hPal)
{
    PALETTEENTRY palEntries[256];
    fprintf(stderr, "%s (0x%08x):\n", label, hPal);
    int n = GetPaletteEntries(hPal, 0, 256, palEntries);
    for (int i = 0; i < n; i++) {
        fprintf(stderr, "palEntries[%3d] = (%3d, %3d, %3d), flags = %d\n",
                i,
                palEntries[i].peRed,
                palEntries[i].peGreen,
                palEntries[i].peBlue,
                palEntries[i].peFlags);
    }
}

/* This helps eliminate any dependence on javai.dll at build time. */
int
jio_fprintf (FILE *handle, const char *format, ...)
{
    int len;

    va_list args;
    va_start(args, format);
    len = vfprintf(handle, format, args);
    va_end(args);

    return len;
}
