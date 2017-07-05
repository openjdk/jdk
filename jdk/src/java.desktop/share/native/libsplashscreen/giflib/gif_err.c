/*
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

/*****************************************************************************
 *   "Gif-Lib" - Yet another gif library.
 *
 * Written by:  Gershon Elber            IBM PC Ver 0.1,    Jun. 1989
 *****************************************************************************
 * Handle error reporting for the GIF library.
 *****************************************************************************
 * History:
 * 17 Jun 89 - Version 1.0 by Gershon Elber.
 ****************************************************************************/

#ifdef HAVE_CONFIG_H
#include <config.h>
#endif

#include <stdio.h>
#include "gif_lib.h"

int _GifError = 0;

/*****************************************************************************
 * Return the last GIF error (0 if none) and reset the error.
 ****************************************************************************/
int
GifLastError(void) {
    int i = _GifError;

    _GifError = 0;

    return i;
}

/*****************************************************************************
 * Print the last GIF error to stderr.
 ****************************************************************************/
void
PrintGifError(void) {
    char *Err;

    switch (_GifError) {
      case D_GIF_ERR_OPEN_FAILED:
        Err = "Failed to open given file";
        break;
      case D_GIF_ERR_READ_FAILED:
        Err = "Failed to Read from given file";
        break;
      case D_GIF_ERR_NOT_GIF_FILE:
        Err = "Given file is NOT GIF file";
        break;
      case D_GIF_ERR_NO_SCRN_DSCR:
        Err = "No Screen Descriptor detected";
        break;
      case D_GIF_ERR_NO_IMAG_DSCR:
        Err = "No Image Descriptor detected";
        break;
      case D_GIF_ERR_NO_COLOR_MAP:
        Err = "Neither Global Nor Local color map";
        break;
      case D_GIF_ERR_WRONG_RECORD:
        Err = "Wrong record type detected";
        break;
      case D_GIF_ERR_DATA_TOO_BIG:
        Err = "#Pixels bigger than Width * Height";
        break;
      case D_GIF_ERR_NOT_ENOUGH_MEM:
        Err = "Fail to allocate required memory";
        break;
      case D_GIF_ERR_CLOSE_FAILED:
        Err = "Failed to close given file";
        break;
      case D_GIF_ERR_NOT_READABLE:
        Err = "Given file was not opened for read";
        break;
      case D_GIF_ERR_IMAGE_DEFECT:
        Err = "Image is defective, decoding aborted";
        break;
      case D_GIF_ERR_EOF_TOO_SOON:
        Err = "Image EOF detected, before image complete";
        break;
      default:
        Err = NULL;
        break;
    }
    if (Err != NULL)
        fprintf(stderr, "\nGIF-LIB error: %s.\n", Err);
    else
        fprintf(stderr, "\nGIF-LIB undefined error %d.\n", _GifError);
}
