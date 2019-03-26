/*
 * Copyright © 2011,2014  Google, Inc.
 *
 *  This is part of HarfBuzz, a text shaping library.
 *
 * Permission is hereby granted, without written agreement and without
 * license or royalty fees, to use, copy, modify, and distribute this
 * software and its documentation for any purpose, provided that the
 * above copyright notice and the following two paragraphs appear in
 * all copies of this software.
 *
 * IN NO EVENT SHALL THE COPYRIGHT HOLDER BE LIABLE TO ANY PARTY FOR
 * DIRECT, INDIRECT, SPECIAL, INCIDENTAL, OR CONSEQUENTIAL DAMAGES
 * ARISING OUT OF THE USE OF THIS SOFTWARE AND ITS DOCUMENTATION, EVEN
 * IF THE COPYRIGHT HOLDER HAS BEEN ADVISED OF THE POSSIBILITY OF SUCH
 * DAMAGE.
 *
 * THE COPYRIGHT HOLDER SPECIFICALLY DISCLAIMS ANY WARRANTIES, INCLUDING,
 * BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND
 * FITNESS FOR A PARTICULAR PURPOSE.  THE SOFTWARE PROVIDED HEREUNDER IS
 * ON AN "AS IS" BASIS, AND THE COPYRIGHT HOLDER HAS NO OBLIGATION TO
 * PROVIDE MAINTENANCE, SUPPORT, UPDATES, ENHANCEMENTS, OR MODIFICATIONS.
 *
 * Google Author(s): Behdad Esfahbod, Roozbeh Pournader
 */

#include "hb.hh"

#include "hb-ot.h"

#include "hb-font.hh"
#include "hb-machinery.hh"
#include "hb-ot-face.hh"

#include "hb-ot-cmap-table.hh"
#include "hb-ot-glyf-table.hh"
#include "hb-ot-cff1-table.hh"
#include "hb-ot-cff2-table.hh"
#include "hb-ot-hmtx-table.hh"
#include "hb-ot-kern-table.hh"
#include "hb-ot-os2-table.hh"
#include "hb-ot-post-table.hh"
#include "hb-ot-stat-table.hh" // Just so we compile it; unused otherwise.
#include "hb-ot-vorg-table.hh"
#include "hb-ot-color-cbdt-table.hh"
#include "hb-ot-color-sbix-table.hh"


/**
 * SECTION:hb-ot-font
 * @title: hb-ot-font
 * @short_description: OpenType font implementation
 * @include: hb-ot.h
 *
 * Functions for using OpenType fonts with hb_shape().  Not that fonts returned
 * by hb_font_create() default to using these functions, so most clients would
 * never need to call these functions directly.
 **/


static hb_bool_t
hb_ot_get_nominal_glyph (hb_font_t *font HB_UNUSED,
                         void *font_data,
                         hb_codepoint_t unicode,
                         hb_codepoint_t *glyph,
                         void *user_data HB_UNUSED)
{
  const hb_ot_face_t *ot_face = (const hb_ot_face_t *) font_data;
  return ot_face->cmap->get_nominal_glyph (unicode, glyph);
}

static unsigned int
hb_ot_get_nominal_glyphs (hb_font_t *font HB_UNUSED,
                          void *font_data,
                          unsigned int count,
                          const hb_codepoint_t *first_unicode,
                          unsigned int unicode_stride,
                          hb_codepoint_t *first_glyph,
                          unsigned int glyph_stride,
                          void *user_data HB_UNUSED)
{
  const hb_ot_face_t *ot_face = (const hb_ot_face_t *) font_data;
  return ot_face->cmap->get_nominal_glyphs (count,
                                            first_unicode, unicode_stride,
                                            first_glyph, glyph_stride);
}

static hb_bool_t
hb_ot_get_variation_glyph (hb_font_t *font HB_UNUSED,
                           void *font_data,
                           hb_codepoint_t unicode,
                           hb_codepoint_t variation_selector,
                           hb_codepoint_t *glyph,
                           void *user_data HB_UNUSED)
{
  const hb_ot_face_t *ot_face = (const hb_ot_face_t *) font_data;
  return ot_face->cmap->get_variation_glyph (unicode, variation_selector, glyph);
}

static void
hb_ot_get_glyph_h_advances (hb_font_t* font, void* font_data,
                            unsigned count,
                            const hb_codepoint_t *first_glyph,
                            unsigned glyph_stride,
                            hb_position_t *first_advance,
                            unsigned advance_stride,
                            void *user_data HB_UNUSED)
{
  const hb_ot_face_t *ot_face = (const hb_ot_face_t *) font_data;
  const OT::hmtx_accelerator_t &hmtx = *ot_face->hmtx;

  for (unsigned int i = 0; i < count; i++)
  {
    *first_advance = font->em_scale_x (hmtx.get_advance (*first_glyph, font));
    first_glyph = &StructAtOffsetUnaligned<hb_codepoint_t> (first_glyph, glyph_stride);
    first_advance = &StructAtOffsetUnaligned<hb_position_t> (first_advance, advance_stride);
  }
}

static void
hb_ot_get_glyph_v_advances (hb_font_t* font, void* font_data,
                            unsigned count,
                            const hb_codepoint_t *first_glyph,
                            unsigned glyph_stride,
                            hb_position_t *first_advance,
                            unsigned advance_stride,
                            void *user_data HB_UNUSED)
{
  const hb_ot_face_t *ot_face = (const hb_ot_face_t *) font_data;
  const OT::vmtx_accelerator_t &vmtx = *ot_face->vmtx;

  for (unsigned int i = 0; i < count; i++)
  {
    *first_advance = font->em_scale_y (-(int) vmtx.get_advance (*first_glyph, font));
    first_glyph = &StructAtOffsetUnaligned<hb_codepoint_t> (first_glyph, glyph_stride);
    first_advance = &StructAtOffsetUnaligned<hb_position_t> (first_advance, advance_stride);
  }
}

static hb_bool_t
hb_ot_get_glyph_v_origin (hb_font_t *font,
                          void *font_data,
                          hb_codepoint_t glyph,
                          hb_position_t *x,
                          hb_position_t *y,
                          void *user_data HB_UNUSED)
{
  const hb_ot_face_t *ot_face = (const hb_ot_face_t *) font_data;

  *x = font->get_glyph_h_advance (glyph) / 2;

  const OT::VORG &VORG = *ot_face->VORG;
  if (VORG.has_data ())
  {
    *y = font->em_scale_y (VORG.get_y_origin (glyph));
    return true;
  }

  hb_glyph_extents_t extents = {0};
  if (ot_face->glyf->get_extents (glyph, &extents))
  {
    const OT::vmtx_accelerator_t &vmtx = *ot_face->vmtx;
    hb_position_t tsb = vmtx.get_side_bearing (glyph);
    *y = font->em_scale_y (extents.y_bearing + tsb);
    return true;
  }

  hb_font_extents_t font_extents;
  font->get_h_extents_with_fallback (&font_extents);
  *y = font_extents.ascender;

  return true;
}

static hb_bool_t
hb_ot_get_glyph_extents (hb_font_t *font,
                         void *font_data,
                         hb_codepoint_t glyph,
                         hb_glyph_extents_t *extents,
                         void *user_data HB_UNUSED)
{
  const hb_ot_face_t *ot_face = (const hb_ot_face_t *) font_data;
  bool ret = ot_face->sbix->get_extents (font, glyph, extents);
  if (!ret)
    ret = ot_face->glyf->get_extents (glyph, extents);
  if (!ret)
    ret = ot_face->cff1->get_extents (glyph, extents);
  if (!ret)
    ret = ot_face->cff2->get_extents (font, glyph, extents);
  if (!ret)
    ret = ot_face->CBDT->get_extents (font, glyph, extents);
  // TODO Hook up side-bearings variations.
  extents->x_bearing = font->em_scale_x (extents->x_bearing);
  extents->y_bearing = font->em_scale_y (extents->y_bearing);
  extents->width     = font->em_scale_x (extents->width);
  extents->height    = font->em_scale_y (extents->height);
  return ret;
}

static hb_bool_t
hb_ot_get_glyph_name (hb_font_t *font HB_UNUSED,
                      void *font_data,
                      hb_codepoint_t glyph,
                      char *name, unsigned int size,
                      void *user_data HB_UNUSED)
{
  const hb_ot_face_t *ot_face = (const hb_ot_face_t *) font_data;
  return ot_face->post->get_glyph_name (glyph, name, size);
}

static hb_bool_t
hb_ot_get_glyph_from_name (hb_font_t *font HB_UNUSED,
                           void *font_data,
                           const char *name, int len,
                           hb_codepoint_t *glyph,
                           void *user_data HB_UNUSED)
{
  const hb_ot_face_t *ot_face = (const hb_ot_face_t *) font_data;
  return ot_face->post->get_glyph_from_name (name, len, glyph);
}

static hb_bool_t
hb_ot_get_font_h_extents (hb_font_t *font,
                          void *font_data,
                          hb_font_extents_t *metrics,
                          void *user_data HB_UNUSED)
{
  const hb_ot_face_t *ot_face = (const hb_ot_face_t *) font_data;
  const OT::hmtx_accelerator_t &hmtx = *ot_face->hmtx;
  metrics->ascender = font->em_scale_y (hmtx.ascender);
  metrics->descender = font->em_scale_y (hmtx.descender);
  metrics->line_gap = font->em_scale_y (hmtx.line_gap);
  // TODO Hook up variations.
  return hmtx.has_font_extents;
}

static hb_bool_t
hb_ot_get_font_v_extents (hb_font_t *font,
                          void *font_data,
                          hb_font_extents_t *metrics,
                          void *user_data HB_UNUSED)
{
  const hb_ot_face_t *ot_face = (const hb_ot_face_t *) font_data;
  const OT::vmtx_accelerator_t &vmtx = *ot_face->vmtx;
  metrics->ascender = font->em_scale_x (vmtx.ascender);
  metrics->descender = font->em_scale_x (vmtx.descender);
  metrics->line_gap = font->em_scale_x (vmtx.line_gap);
  // TODO Hook up variations.
  return vmtx.has_font_extents;
}

#if HB_USE_ATEXIT
static void free_static_ot_funcs ();
#endif

static struct hb_ot_font_funcs_lazy_loader_t : hb_font_funcs_lazy_loader_t<hb_ot_font_funcs_lazy_loader_t>
{
  static hb_font_funcs_t *create ()
  {
    hb_font_funcs_t *funcs = hb_font_funcs_create ();

    hb_font_funcs_set_font_h_extents_func (funcs, hb_ot_get_font_h_extents, nullptr, nullptr);
    hb_font_funcs_set_font_v_extents_func (funcs, hb_ot_get_font_v_extents, nullptr, nullptr);
    hb_font_funcs_set_nominal_glyph_func (funcs, hb_ot_get_nominal_glyph, nullptr, nullptr);
    hb_font_funcs_set_nominal_glyphs_func (funcs, hb_ot_get_nominal_glyphs, nullptr, nullptr);
    hb_font_funcs_set_variation_glyph_func (funcs, hb_ot_get_variation_glyph, nullptr, nullptr);
    hb_font_funcs_set_glyph_h_advances_func (funcs, hb_ot_get_glyph_h_advances, nullptr, nullptr);
    hb_font_funcs_set_glyph_v_advances_func (funcs, hb_ot_get_glyph_v_advances, nullptr, nullptr);
    //hb_font_funcs_set_glyph_h_origin_func (funcs, hb_ot_get_glyph_h_origin, nullptr, nullptr);
    hb_font_funcs_set_glyph_v_origin_func (funcs, hb_ot_get_glyph_v_origin, nullptr, nullptr);
    hb_font_funcs_set_glyph_extents_func (funcs, hb_ot_get_glyph_extents, nullptr, nullptr);
    //hb_font_funcs_set_glyph_contour_point_func (funcs, hb_ot_get_glyph_contour_point, nullptr, nullptr);
    hb_font_funcs_set_glyph_name_func (funcs, hb_ot_get_glyph_name, nullptr, nullptr);
    hb_font_funcs_set_glyph_from_name_func (funcs, hb_ot_get_glyph_from_name, nullptr, nullptr);

    hb_font_funcs_make_immutable (funcs);

#if HB_USE_ATEXIT
    atexit (free_static_ot_funcs);
#endif

    return funcs;
  }
} static_ot_funcs;

#if HB_USE_ATEXIT
static
void free_static_ot_funcs ()
{
  static_ot_funcs.free_instance ();
}
#endif

static hb_font_funcs_t *
_hb_ot_get_font_funcs ()
{
  return static_ot_funcs.get_unconst ();
}


/**
 * hb_ot_font_set_funcs:
 *
 * Since: 0.9.28
 **/
void
hb_ot_font_set_funcs (hb_font_t *font)
{
  hb_font_set_funcs (font,
                     _hb_ot_get_font_funcs (),
                     &font->face->table,
                     nullptr);
}
