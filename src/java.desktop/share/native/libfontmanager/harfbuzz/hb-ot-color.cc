/*
 * Copyright © 2016  Google, Inc.
 * Copyright © 2018  Ebrahim Byagowi
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
 * Google Author(s): Sascha Brawer, Behdad Esfahbod
 */

#include "hb-open-type.hh"
#include "hb-ot-color-cbdt-table.hh"
#include "hb-ot-color-colr-table.hh"
#include "hb-ot-color-cpal-table.hh"
#include "hb-ot-color-sbix-table.hh"
#include "hb-ot-color-svg-table.hh"
#include "hb-ot-face.hh"
#include "hb-ot.h"

#include <stdlib.h>
#include <string.h>

#include "hb-ot-layout.hh"


/**
 * SECTION:hb-ot-color
 * @title: hb-ot-color
 * @short_description: OpenType Color Fonts
 * @include: hb-ot.h
 *
 * Functions for fetching color-font information from OpenType font faces.
 **/


/*
 * CPAL
 */


/**
 * hb_ot_color_has_palettes:
 * @face: a font face.
 *
 * Returns: whether CPAL table is available.
 *
 * Since: 2.1.0
 */
hb_bool_t
hb_ot_color_has_palettes (hb_face_t *face)
{
  return face->table.CPAL->has_data ();
}

/**
 * hb_ot_color_palette_get_count:
 * @face: a font face.
 *
 * Returns: the number of color palettes in @face, or zero if @face has
 * no colors.
 *
 * Since: 2.1.0
 */
unsigned int
hb_ot_color_palette_get_count (hb_face_t *face)
{
  return face->table.CPAL->get_palette_count ();
}

/**
 * hb_ot_color_palette_get_name_id:
 * @face:    a font face.
 * @palette_index: the index of the color palette whose name is being requested.
 *
 * Retrieves the name id of a color palette. For example, a color font can
 * have themed palettes like "Spring", "Summer", "Fall", and "Winter".
 *
 * Returns: an identifier within @face's `name` table.
 * If the requested palette has no name the result is #HB_OT_NAME_ID_INVALID.
 *
 * Since: 2.1.0
 */
hb_ot_name_id_t
hb_ot_color_palette_get_name_id (hb_face_t *face,
                                 unsigned int palette_index)
{
  return face->table.CPAL->get_palette_name_id (palette_index);
}

/**
 * hb_ot_color_palette_color_get_name_id:
 * @face:        a font face.
 * @color_index: palette entry index.
 *
 * Returns: Name ID associated with a palette entry, e.g. eye color
 *
 * Since: 2.1.0
 */
hb_ot_name_id_t
hb_ot_color_palette_color_get_name_id (hb_face_t *face,
                                       unsigned int color_index)
{
  return face->table.CPAL->get_color_name_id (color_index);
}

/**
 * hb_ot_color_palette_get_flags:
 * @face:          a font face
 * @palette_index: the index of the color palette whose flags are being requested
 *
 * Returns: the flags for the requested color palette.
 *
 * Since: 2.1.0
 */
hb_ot_color_palette_flags_t
hb_ot_color_palette_get_flags (hb_face_t *face,
                               unsigned int palette_index)
{
  return face->table.CPAL->get_palette_flags (palette_index);
}

/**
 * hb_ot_color_palette_get_colors:
 * @face:         a font face.
 * @palette_index:the index of the color palette whose colors
 *                are being requested.
 * @start_offset: the index of the first color being requested.
 * @color_count:  (inout) (optional): on input, how many colors
 *                can be maximally stored into the @colors array;
 *                on output, how many colors were actually stored.
 * @colors: (array length=color_count) (out) (optional):
 *                an array of #hb_color_t records. After calling
 *                this function, @colors will be filled with
 *                the palette colors. If @colors is NULL, the function
 *                will just return the number of total colors
 *                without storing any actual colors; this can be used
 *                for allocating a buffer of suitable size before calling
 *                hb_ot_color_palette_get_colors() a second time.
 *
 * Retrieves the colors in a color palette.
 *
 * Returns: the total number of colors in the palette.
 *
 * Since: 2.1.0
 */
unsigned int
hb_ot_color_palette_get_colors (hb_face_t     *face,
                                unsigned int   palette_index,
                                unsigned int   start_offset,
                                unsigned int  *colors_count  /* IN/OUT.  May be NULL. */,
                                hb_color_t    *colors        /* OUT.     May be NULL. */)
{
  return face->table.CPAL->get_palette_colors (palette_index, start_offset, colors_count, colors);
}


/*
 * COLR
 */

/**
 * hb_ot_color_has_layers:
 * @face: a font face.
 *
 * Returns: whether COLR table is available.
 *
 * Since: 2.1.0
 */
hb_bool_t
hb_ot_color_has_layers (hb_face_t *face)
{
  return face->table.COLR->has_data ();
}

/**
 * hb_ot_color_glyph_get_layers:
 * @face:         a font face.
 * @glyph:        a layered color glyph id.
 * @start_offset: starting offset of layers.
 * @count:  (inout) (optional): gets number of layers available to be written on buffer
 *                              and returns number of written layers.
 * @layers: (array length=count) (out) (optional): layers buffer to buffer.
 *
 * Returns: Total number of layers a layered color glyph have.
 *
 * Since: 2.1.0
 */
unsigned int
hb_ot_color_glyph_get_layers (hb_face_t           *face,
                              hb_codepoint_t       glyph,
                              unsigned int         start_offset,
                              unsigned int        *count, /* IN/OUT.  May be NULL. */
                              hb_ot_color_layer_t *layers /* OUT.     May be NULL. */)
{
  return face->table.COLR->get_glyph_layers (glyph, start_offset, count, layers);
}


/*
 * SVG
 */

/**
 * hb_ot_color_has_svg:
 * @face: a font face.
 *
 * Check whether @face has SVG glyph images.
 *
 * Returns true if available, false otherwise.
 *
 * Since: 2.1.0
 */
hb_bool_t
hb_ot_color_has_svg (hb_face_t *face)
{
  return face->table.SVG->has_data ();
}

/**
 * hb_ot_color_glyph_reference_svg:
 * @face:  a font face.
 * @glyph: a svg glyph index.
 *
 * Get SVG document for a glyph. The blob may be either plain text or gzip-encoded.
 *
 * Returns: (transfer full): respective svg blob of the glyph, if available.
 *
 * Since: 2.1.0
 */
hb_blob_t *
hb_ot_color_glyph_reference_svg (hb_face_t *face, hb_codepoint_t glyph)
{
  return face->table.SVG->reference_blob_for_glyph (glyph);
}


/*
 * PNG: CBDT or sbix
 */

/**
 * hb_ot_color_has_png:
 * @face: a font face.
 *
 * Check whether @face has PNG glyph images (either CBDT or sbix tables).
 *
 * Returns true if available, false otherwise.
 *
 * Since: 2.1.0
 */
hb_bool_t
hb_ot_color_has_png (hb_face_t *face)
{
  return face->table.CBDT->has_data () || face->table.sbix->has_data ();
}

/**
 * hb_ot_color_glyph_reference_png:
 * @font:  a font object, not face. upem should be set on
 *         that font object if one wants to get optimal png blob, otherwise
 *         return the biggest one
 * @glyph: a glyph index.
 *
 * Get PNG image for a glyph.
 *
 * Returns: (transfer full): respective PNG blob of the glyph, if available.
 *
 * Since: 2.1.0
 */
hb_blob_t *
hb_ot_color_glyph_reference_png (hb_font_t *font, hb_codepoint_t  glyph)
{
  hb_blob_t *blob = hb_blob_get_empty ();

  if (font->face->table.sbix->has_data ())
    blob = font->face->table.sbix->reference_png (font, glyph, nullptr, nullptr, nullptr);

  if (!blob->length && font->face->table.CBDT->has_data ())
    blob = font->face->table.CBDT->reference_png (font, glyph);

  return blob;
}
