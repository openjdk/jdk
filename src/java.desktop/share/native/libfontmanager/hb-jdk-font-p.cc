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

#include "hb.h"
#include "hb-jdk-p.h"
#include <stdlib.h>

#if defined(__GNUC__) &&  __GNUC__ >= 4
#define HB_UNUSED       __attribute__((unused))
#else
#define HB_UNUSED
#endif

static hb_bool_t
hb_jdk_get_glyph_h_origin (hb_font_t *font HB_UNUSED,
                          void *font_data HB_UNUSED,
                          hb_codepoint_t glyph HB_UNUSED,
                          hb_position_t *x HB_UNUSED,
                          hb_position_t *y HB_UNUSED,
                          void *user_data HB_UNUSED)
{
  /* We always work in the horizontal coordinates. */
  return true;
}

static hb_bool_t
hb_jdk_get_glyph_v_origin (hb_font_t *font HB_UNUSED,
                          void *font_data,
                          hb_codepoint_t glyph,
                          hb_position_t *x,
                          hb_position_t *y,
                          void *user_data HB_UNUSED)
{
  return false;
}

static hb_position_t
hb_jdk_get_glyph_h_kerning (hb_font_t *font,
                           void *font_data,
                           hb_codepoint_t lejdk_glyph,
                           hb_codepoint_t right_glyph,
                           void *user_data HB_UNUSED)
{
  /* Not implemented. This seems to be in the HB API
   * as a way to fall back to Freetype's kerning support
   * which could be based on some on-the fly glyph analysis.
   * But more likely it reads the kern table. That is easy
   * enough code to add if we find a need to fall back
   * to that instead of using gpos. It seems like if
   * there is a gpos table at all, the practice is to
   * use that and ignore kern, no matter that gpos does
   * not implement the kern feature.
   */
  return 0;
}

static hb_position_t
hb_jdk_get_glyph_v_kerning (hb_font_t *font HB_UNUSED,
                           void *font_data HB_UNUSED,
                           hb_codepoint_t top_glyph HB_UNUSED,
                           hb_codepoint_t bottom_glyph HB_UNUSED,
                           void *user_data HB_UNUSED)
{
  /* OpenType doesn't have vertical-kerning other than GPOS. */
  return 0;
}

static hb_bool_t
hb_jdk_get_glyph_extents (hb_font_t *font HB_UNUSED,
                         void *font_data,
                         hb_codepoint_t glyph,
                         hb_glyph_extents_t *extents,
                         void *user_data HB_UNUSED)
{
  /* TODO */
  return false;
}

static hb_bool_t
hb_jdk_get_glyph_name (hb_font_t *font HB_UNUSED,
                      void *font_data,
                      hb_codepoint_t glyph,
                      char *name, unsigned int size,
                      void *user_data HB_UNUSED)
{
  return false;
}

static hb_bool_t
hb_jdk_get_glyph_from_name (hb_font_t *font HB_UNUSED,
                           void *font_data,
                           const char *name, int len,
                           hb_codepoint_t *glyph,
                           void *user_data HB_UNUSED)
{
  return false;
}

extern "C" {
/*
 * This is called exactly once, from Java code, and the result is
 * used by all downcalls to do shaping(), installing the functions
 * on the hb_font.
 * The parameters are all FFM upcall stubs.
 * I was surprised we can cache these native pointers to upcall
 * stubs on the native side, but it seems to be fine using the global Arena.
 * These stubs don't need to be bound to a particular font or strike
 * since they use Scoped Locals to access the data they need to operate on.
 * This is how we can cache them.
 * Also caching the hb_font_funcs_t on the Java side means we can
 * marshall fewer args to the calls to shape().
 */
JDKEXPORT hb_font_funcs_t *
HBCreateFontFuncs(hb_font_get_nominal_glyph_func_t nominal_fn,
                  hb_font_get_variation_glyph_func_t variation_fn,
                  hb_font_get_glyph_h_advance_func_t h_advance_fn,
                  hb_font_get_glyph_v_advance_func_t v_advance_fn,
                  hb_font_get_glyph_contour_point_func_t contour_pt_fn)
{
    hb_font_funcs_t *ff = hb_font_funcs_create();

    hb_font_funcs_set_nominal_glyph_func(ff, nominal_fn, NULL, NULL);
    hb_font_funcs_set_variation_glyph_func(ff, variation_fn, NULL, NULL);
    hb_font_funcs_set_glyph_h_advance_func(ff, h_advance_fn, NULL, NULL);
    hb_font_funcs_set_glyph_v_advance_func(ff, v_advance_fn, NULL, NULL);
    hb_font_funcs_set_glyph_contour_point_func(ff, contour_pt_fn, NULL, NULL);

    /* These are all simple default implementations */
    hb_font_funcs_set_glyph_h_origin_func(ff,
                    hb_jdk_get_glyph_h_origin, NULL, NULL);
    hb_font_funcs_set_glyph_v_origin_func(ff,
                    hb_jdk_get_glyph_v_origin, NULL, NULL);
    hb_font_funcs_set_glyph_h_kerning_func(ff,
                    hb_jdk_get_glyph_h_kerning, NULL, NULL);
    hb_font_funcs_set_glyph_v_kerning_func(ff,
                    hb_jdk_get_glyph_v_kerning, NULL, NULL);
    hb_font_funcs_set_glyph_extents_func(ff,
                    hb_jdk_get_glyph_extents, NULL, NULL);
    hb_font_funcs_set_glyph_name_func(ff,
                    hb_jdk_get_glyph_name, NULL, NULL);
    hb_font_funcs_set_glyph_from_name_func(ff,
                    hb_jdk_get_glyph_from_name, NULL, NULL);
    hb_font_funcs_make_immutable(ff); // done setting functions.

  return ff;
}

} /* extern "C" */

static void _do_nothing(void) {
}

typedef int (*GetTableDataFn) (int tag, char **dataPtr);

static hb_blob_t *
reference_table(hb_face_t *face HB_UNUSED, hb_tag_t tag, void *user_data) {

  // HB_TAG_NONE is 0 and is used to get the whole font file.
  // It is not expected to be needed for JDK.
  if (tag == 0) {
      return NULL;
  }

  // This has to be a method handle bound to the right Font2D
  GetTableDataFn getDataFn = (GetTableDataFn)user_data;

  char *tableData = NULL;
  int length = (*getDataFn)(tag, &tableData);
  if ((length == 0) || (tableData == NULL)) {
      return NULL;
  }

  /* Can't call this non-exported hb fn from Java so can't have
   * a Java version of the reference_table fn, which is why it
   * has as a parameter the upcall stub that will be used.
   * And the memory is freed by 'free' so the upcall needs to
   * call back down to malloc to allocate it.
   */
  return hb_blob_create((const char *)tableData, length,
                         HB_MEMORY_MODE_WRITABLE,
                         tableData, free);
}

extern "C" {

JDKEXPORT hb_face_t* HBCreateFace(GetTableDataFn *get_data_upcall_fn) {

    hb_face_t *face = hb_face_create_for_tables(reference_table, get_data_upcall_fn, NULL);
    return face;
}

JDKEXPORT void HBDisposeFace(hb_face_t* face) {
    hb_face_destroy(face);
}

// Use 16.16 for better precision than 26.6
#define HBFloatToFixedScale ((float)(1 << 16))
#define HBFloatToFixed(f) ((unsigned int)((f) * HBFloatToFixedScale))

hb_font_t* jdk_font_create_hbp(
               hb_face_t* face,
               float ptSize, float devScale,
               hb_destroy_func_t destroy,
               hb_font_funcs_t *font_funcs) {

    hb_font_t *font;

    font = hb_font_create(face);
    hb_font_set_funcs(font,
                      font_funcs,
                      NULL,
                      (hb_destroy_func_t)_do_nothing);
    hb_font_set_scale(font,
                      HBFloatToFixed(ptSize*devScale),
                      HBFloatToFixed(ptSize*devScale));
    return font;
}

} // extern "C"
