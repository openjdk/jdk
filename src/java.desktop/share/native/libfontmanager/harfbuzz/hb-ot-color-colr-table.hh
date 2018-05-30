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

#include "hb-open-type-private.hh"

/*
 * Color Palette
 * http://www.microsoft.com/typography/otspec/colr.htm
 */

#define HB_OT_TAG_COLR HB_TAG('C','O','L','R')

namespace OT {


struct LayerRecord
{
  friend struct COLR;

  inline bool sanitize (hb_sanitize_context_t *c) const
  {
    TRACE_SANITIZE (this);
    return_trace (c->check_struct (this));
  }

  protected:
  GlyphID gID;                  /* Glyph ID of layer glyph */
  HBUINT16 paletteIndex;        /* Index value to use with a selected color palette */
  public:
  DEFINE_SIZE_STATIC (4);
};

struct BaseGlyphRecord
{
  friend struct COLR;

  inline bool sanitize (hb_sanitize_context_t *c) const
  {
    TRACE_SANITIZE (this);
    return_trace (c->check_struct (this));
  }

  protected:
  GlyphID gID;                  /* Glyph ID of reference glyph */
  HBUINT16 firstLayerIndex;     /* Index to the layer record */
  HBUINT16 numLayers;           /* Number of color layers associated with this glyph */
  public:
  DEFINE_SIZE_STATIC (6);
};

struct COLR
{
  static const hb_tag_t tableTag = HB_OT_TAG_COLR;

  inline bool sanitize (hb_sanitize_context_t *c) const
  {
    TRACE_SANITIZE (this);
    if (!(c->check_struct (this) &&
        c->check_array ((const void*) &layerRecordsOffsetZ, sizeof (LayerRecord), numLayerRecords) &&
        c->check_array ((const void*) &baseGlyphRecordsZ, sizeof (BaseGlyphRecord), numBaseGlyphRecords)))
      return_trace (false);

    const BaseGlyphRecord* base_glyph_records = &baseGlyphRecordsZ (this);
    for (unsigned int i = 0; i < numBaseGlyphRecords; ++i)
      if (base_glyph_records[i].firstLayerIndex +
          base_glyph_records[i].numLayers > numLayerRecords)
        return_trace (false);

    return_trace (true);
  }

  inline bool get_base_glyph_record (
    hb_codepoint_t glyph_id, unsigned int &first_layer, unsigned int &num_layers) const
  {
    const BaseGlyphRecord* base_glyph_records = &baseGlyphRecordsZ (this);
    unsigned int min = 0, max = numBaseGlyphRecords - 1;
    while (min <= max)
    {
      unsigned int mid = (min + max) / 2;
      hb_codepoint_t gID = base_glyph_records[mid].gID;
      if (gID > glyph_id)
        max = mid - 1;
      else if (gID < glyph_id)
        min = mid + 1;
      else
      {
        first_layer = base_glyph_records[mid].firstLayerIndex;
        num_layers = base_glyph_records[mid].numLayers;
        return true;
      }
    }
    return false;
  }

  inline void get_layer_record (int layer,
    hb_codepoint_t &glyph_id, unsigned int &palette_index) const
  {
    const LayerRecord* records = &layerRecordsOffsetZ (this);
    glyph_id = records[layer].gID;
    palette_index = records[layer].paletteIndex;
  }

  protected:
  HBUINT16      version;                /* Table version number */
  HBUINT16      numBaseGlyphRecords;    /* Number of Base Glyph Records */
  LOffsetTo<BaseGlyphRecord>
                baseGlyphRecordsZ;      /* Offset to Base Glyph records. */
  LOffsetTo<LayerRecord>
                layerRecordsOffsetZ;    /* Offset to Layer Records */
  HBUINT16      numLayerRecords;        /* Number of Layer Records */
  public:
  DEFINE_SIZE_STATIC (14);
};

} /* namespace OT */


#endif /* HB_OT_COLOR_COLR_TABLE_HH */
