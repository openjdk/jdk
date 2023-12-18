/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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

#include <stdlib.h>
#include "hb.h"
#include "hb-jdk-p.h"
#include "hb-ot.h"
#include "scriptMapping.h"

static float euclidianDistance(float a, float b)
{
    float root;
    if (a < 0) {
        a = -a;
    }

    if (b < 0) {
        b = -b;
    }

    if (a == 0) {
        return b;
    }

    if (b == 0) {
        return a;
    }

    /* Do an initial approximation, in root */
    root = a > b ? a + (b / 2) : b + (a / 2);

    /* An unrolled Newton-Raphson iteration sequence */
    root = (root + (a * (a / root)) + (b * (b / root)) + 1) / 2;
    root = (root + (a * (a / root)) + (b * (b / root)) + 1) / 2;
    root = (root + (a * (a / root)) + (b * (b / root)) + 1) / 2;

    return root;
}

#define TYPO_KERN 0x00000001
#define TYPO_LIGA 0x00000002
#define TYPO_RTL  0x80000000

JDKEXPORT int jdk_hb_shape(
     float ptSize,
     float *matrix,
     void* pFace,
     unsigned short *chars,
     int len,
     int script,
     int offset,
     int limit,
     int baseIndex,
     float startX,
     float startY,
     int flags,
     int slot,
     hb_font_funcs_t* font_funcs,
     store_layoutdata_func_t store_layout_results_fn
    ) {

     hb_buffer_t *buffer;
     hb_face_t* hbface;
     hb_font_t* hbfont;
     int glyphCount;
     hb_glyph_info_t *glyphInfo;
     hb_glyph_position_t *glyphPos;
     hb_direction_t direction = HB_DIRECTION_LTR;
     hb_feature_t *features = NULL;
     int featureCount = 0;
     char* kern = (flags & TYPO_KERN) ? "kern" : "-kern";
     char* liga = (flags & TYPO_LIGA) ? "liga" : "-liga";
     int ret;
     unsigned int buflen;

     float devScale = 1.0f;
     if (getenv("HB_NODEVTX") != NULL) {
         float xPtSize = euclidianDistance(matrix[0], matrix[1]);
         float yPtSize = euclidianDistance(matrix[2], matrix[3]);
         devScale = xPtSize / ptSize;
     }

     hbface = (hb_face_t*)pFace;
     hbfont = jdk_font_create_hbp(hbface,
                                  ptSize, devScale, NULL,
                                  font_funcs);

     buffer = hb_buffer_create();
     hb_buffer_set_script(buffer, getHBScriptCode(script));
     hb_buffer_set_language(buffer,
                            hb_ot_tag_to_language(HB_OT_TAG_DEFAULT_LANGUAGE));
     if ((flags & TYPO_RTL) != 0) {
         direction = HB_DIRECTION_RTL;
     }
     hb_buffer_set_direction(buffer, direction);
     hb_buffer_set_cluster_level(buffer,
                                 HB_BUFFER_CLUSTER_LEVEL_MONOTONE_CHARACTERS);

     int charCount = limit - offset;
     hb_buffer_add_utf16(buffer, chars, len, offset, charCount);

     features = calloc(2, sizeof(hb_feature_t));
     if (features) {
         hb_feature_from_string(kern, -1, &features[featureCount++]);
         hb_feature_from_string(liga, -1, &features[featureCount++]);
     }

     hb_shape_full(hbfont, buffer, features, featureCount, 0);
     glyphCount = hb_buffer_get_length(buffer);
     glyphInfo = hb_buffer_get_glyph_infos(buffer, 0);
     glyphPos = hb_buffer_get_glyph_positions(buffer, &buflen);

     ret = (*store_layout_results_fn)
               (slot, baseIndex, offset, startX, startY, devScale,
                charCount, glyphCount, glyphInfo, glyphPos);

     hb_buffer_destroy (buffer);
     hb_font_destroy(hbfont);
     if (features != NULL) {
         free(features);
     }
     return ret;
}
