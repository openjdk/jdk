/*
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
 */
#ifndef HB_AAT_LAYOUT_LCAR_TABLE_HH
#define HB_AAT_LAYOUT_LCAR_TABLE_HH

#include "hb-open-type.hh"
#include "hb-aat-layout-common.hh"

/*
 * lcar -- Ligature caret
 * https://developer.apple.com/fonts/TrueType-Reference-Manual/RM06/Chap6lcar.html
 */
#define HB_AAT_TAG_lcar HB_TAG('l','c','a','r')


namespace AAT {

typedef ArrayOf<HBINT16> LigCaretClassEntry;

struct lcar
{
  static constexpr hb_tag_t tableTag = HB_AAT_TAG_lcar;

  unsigned int get_lig_carets (hb_font_t      *font,
                               hb_direction_t  direction,
                               hb_codepoint_t  glyph,
                               unsigned int    start_offset,
                               unsigned int   *caret_count /* IN/OUT */,
                               hb_position_t  *caret_array /* OUT */) const
  {
    const OffsetTo<LigCaretClassEntry>* entry_offset = lookup.get_value (glyph,
                                                                         font->face->get_num_glyphs ());
    const LigCaretClassEntry& array = entry_offset ? this+*entry_offset : Null (LigCaretClassEntry);
    if (caret_count)
    {
      hb_array_t<const HBINT16> arr = array.sub_array (start_offset, caret_count);
      unsigned int count = arr.length;
      for (unsigned int i = 0; i < count; ++i)
        switch (format)
        {
        case 0: caret_array[i] = font->em_scale_dir (arr[i], direction); break;
        case 1:
          hb_position_t x, y;
          font->get_glyph_contour_point_for_origin (glyph, arr[i], direction, &x, &y);
          caret_array[i] = HB_DIRECTION_IS_HORIZONTAL (direction) ? x : y;
          break;
        }
    }
    return array.len;
  }

  bool sanitize (hb_sanitize_context_t *c) const
  {
    TRACE_SANITIZE (this);
    return_trace (likely (c->check_struct (this) &&
                          version.major == 1 &&
                          lookup.sanitize (c, this)));
  }

  protected:
  FixedVersion<>version;        /* Version number of the ligature caret table */
  HBUINT16      format;         /* Format of the ligature caret table. */
  Lookup<OffsetTo<LigCaretClassEntry> >
                lookup;         /* data Lookup table associating glyphs */

  public:
  DEFINE_SIZE_MIN (8);
};

} /* namespace AAT */

#endif /* HB_AAT_LAYOUT_LCAR_TABLE_HH */
