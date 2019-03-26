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

#ifndef HB_OT_COLOR_COLR_TABLE_HH
#define HB_OT_COLOR_COLR_TABLE_HH

#include "hb-open-type.hh"

/*
 * COLR -- Color
 * https://docs.microsoft.com/en-us/typography/opentype/spec/colr
 */
#define HB_OT_TAG_COLR HB_TAG('C','O','L','R')


namespace OT {


struct LayerRecord
{
  bool sanitize (hb_sanitize_context_t *c) const
  {
    TRACE_SANITIZE (this);
    return_trace (c->check_struct (this));
  }

  public:
  GlyphID       glyphId;        /* Glyph ID of layer glyph */
  Index         colorIdx;       /* Index value to use with a
                                 * selected color palette.
                                 * An index value of 0xFFFF
                                 * is a special case indicating
                                 * that the text foreground
                                 * color (defined by a
                                 * higher-level client) should
                                 * be used and shall not be
                                 * treated as actual index
                                 * into CPAL ColorRecord array. */
  public:
  DEFINE_SIZE_STATIC (4);
};

struct BaseGlyphRecord
{
  int cmp (hb_codepoint_t g) const
  { return g < glyphId ? -1 : g > glyphId ? 1 : 0; }

  bool sanitize (hb_sanitize_context_t *c) const
  {
    TRACE_SANITIZE (this);
    return_trace (likely (c->check_struct (this)));
  }

  public:
  GlyphID       glyphId;        /* Glyph ID of reference glyph */
  HBUINT16      firstLayerIdx;  /* Index (from beginning of
                                 * the Layer Records) to the
                                 * layer record. There will be
                                 * numLayers consecutive entries
                                 * for this base glyph. */
  HBUINT16      numLayers;      /* Number of color layers
                                 * associated with this glyph */
  public:
  DEFINE_SIZE_STATIC (6);
};

struct COLR
{
  static constexpr hb_tag_t tableTag = HB_OT_TAG_COLR;

  bool has_data () const { return numBaseGlyphs; }

  unsigned int get_glyph_layers (hb_codepoint_t       glyph,
                                 unsigned int         start_offset,
                                 unsigned int        *count, /* IN/OUT.  May be NULL. */
                                 hb_ot_color_layer_t *layers /* OUT.     May be NULL. */) const
  {
    const BaseGlyphRecord &record = (this+baseGlyphsZ).bsearch (numBaseGlyphs, glyph);

    hb_array_t<const LayerRecord> all_layers ((this+layersZ).arrayZ, numLayers);
    hb_array_t<const LayerRecord> glyph_layers = all_layers.sub_array (record.firstLayerIdx,
                                                                       record.numLayers);
    if (count)
    {
      hb_array_t<const LayerRecord> segment_layers = glyph_layers.sub_array (start_offset, *count);
      *count = segment_layers.length;
      for (unsigned int i = 0; i < segment_layers.length; i++)
      {
        layers[i].glyph = segment_layers.arrayZ[i].glyphId;
        layers[i].color_index = segment_layers.arrayZ[i].colorIdx;
      }
    }
    return glyph_layers.length;
  }

  bool sanitize (hb_sanitize_context_t *c) const
  {
    TRACE_SANITIZE (this);
    return_trace (likely (c->check_struct (this) &&
                          (this+baseGlyphsZ).sanitize (c, numBaseGlyphs) &&
                          (this+layersZ).sanitize (c, numLayers)));
  }

  protected:
  HBUINT16      version;        /* Table version number (starts at 0). */
  HBUINT16      numBaseGlyphs;  /* Number of Base Glyph Records. */
  LNNOffsetTo<SortedUnsizedArrayOf<BaseGlyphRecord> >
                baseGlyphsZ;    /* Offset to Base Glyph records. */
  LNNOffsetTo<UnsizedArrayOf<LayerRecord> >
                layersZ;        /* Offset to Layer Records. */
  HBUINT16      numLayers;      /* Number of Layer Records. */
  public:
  DEFINE_SIZE_STATIC (14);
};

} /* namespace OT */


#endif /* HB_OT_COLOR_COLR_TABLE_HH */
