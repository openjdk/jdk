/*
 * Copyright © 2016  Google, Inc.
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
 * Google Author(s): Seigo Nonaka
 */

#ifndef HB_OT_COLOR_CBDT_TABLE_HH
#define HB_OT_COLOR_CBDT_TABLE_HH

#include "hb-open-type.hh"

/*
 * CBLC -- Color Bitmap Location
 * https://docs.microsoft.com/en-us/typography/opentype/spec/cblc
 * https://docs.microsoft.com/en-us/typography/opentype/spec/eblc
 * CBDT -- Color Bitmap Data
 * https://docs.microsoft.com/en-us/typography/opentype/spec/cbdt
 * https://docs.microsoft.com/en-us/typography/opentype/spec/ebdt
 */
#define HB_OT_TAG_CBLC HB_TAG('C','B','L','C')
#define HB_OT_TAG_CBDT HB_TAG('C','B','D','T')


namespace OT {

struct SmallGlyphMetrics
{
  bool sanitize (hb_sanitize_context_t *c) const
  {
    TRACE_SANITIZE (this);
    return_trace (c->check_struct (this));
  }

  void get_extents (hb_glyph_extents_t *extents) const
  {
    extents->x_bearing = bearingX;
    extents->y_bearing = bearingY;
    extents->width = width;
    extents->height = - (hb_position_t) height;
  }

  HBUINT8       height;
  HBUINT8       width;
  HBINT8        bearingX;
  HBINT8        bearingY;
  HBUINT8       advance;
  public:
  DEFINE_SIZE_STATIC(5);
};

struct BigGlyphMetrics : SmallGlyphMetrics
{
  HBINT8        vertBearingX;
  HBINT8        vertBearingY;
  HBUINT8       vertAdvance;
  public:
  DEFINE_SIZE_STATIC(8);
};

struct SBitLineMetrics
{
  bool sanitize (hb_sanitize_context_t *c) const
  {
    TRACE_SANITIZE (this);
    return_trace (c->check_struct (this));
  }

  HBINT8        ascender;
  HBINT8        decender;
  HBUINT8       widthMax;
  HBINT8        caretSlopeNumerator;
  HBINT8        caretSlopeDenominator;
  HBINT8        caretOffset;
  HBINT8        minOriginSB;
  HBINT8        minAdvanceSB;
  HBINT8        maxBeforeBL;
  HBINT8        minAfterBL;
  HBINT8        padding1;
  HBINT8        padding2;
  public:
  DEFINE_SIZE_STATIC(12);
};


/*
 * Index Subtables.
 */

struct IndexSubtableHeader
{
  bool sanitize (hb_sanitize_context_t *c) const
  {
    TRACE_SANITIZE (this);
    return_trace (c->check_struct (this));
  }

  HBUINT16      indexFormat;
  HBUINT16      imageFormat;
  HBUINT32      imageDataOffset;
  public:
  DEFINE_SIZE_STATIC(8);
};

template <typename OffsetType>
struct IndexSubtableFormat1Or3
{
  bool sanitize (hb_sanitize_context_t *c, unsigned int glyph_count) const
  {
    TRACE_SANITIZE (this);
    return_trace (c->check_struct (this) &&
                  offsetArrayZ.sanitize (c, glyph_count + 1));
  }

  bool get_image_data (unsigned int idx,
                       unsigned int *offset,
                       unsigned int *length) const
  {
    if (unlikely (offsetArrayZ[idx + 1] <= offsetArrayZ[idx]))
      return false;

    *offset = header.imageDataOffset + offsetArrayZ[idx];
    *length = offsetArrayZ[idx + 1] - offsetArrayZ[idx];
    return true;
  }

  IndexSubtableHeader   header;
  UnsizedArrayOf<Offset<OffsetType> >
                        offsetArrayZ;
  public:
  DEFINE_SIZE_ARRAY(8, offsetArrayZ);
};

struct IndexSubtableFormat1 : IndexSubtableFormat1Or3<HBUINT32> {};
struct IndexSubtableFormat3 : IndexSubtableFormat1Or3<HBUINT16> {};

struct IndexSubtable
{
  bool sanitize (hb_sanitize_context_t *c, unsigned int glyph_count) const
  {
    TRACE_SANITIZE (this);
    if (!u.header.sanitize (c)) return_trace (false);
    switch (u.header.indexFormat) {
    case 1: return_trace (u.format1.sanitize (c, glyph_count));
    case 3: return_trace (u.format3.sanitize (c, glyph_count));
    default:return_trace (true);
    }
  }

  bool get_extents (hb_glyph_extents_t *extents HB_UNUSED) const
  {
    switch (u.header.indexFormat) {
    case 2: case 5: /* TODO */
    case 1: case 3: case 4: /* Variable-metrics formats do not have metrics here. */
    default:return (false);
    }
  }

  bool get_image_data (unsigned int idx,
                       unsigned int *offset,
                       unsigned int *length,
                       unsigned int *format) const
  {
    *format = u.header.imageFormat;
    switch (u.header.indexFormat) {
    case 1: return u.format1.get_image_data (idx, offset, length);
    case 3: return u.format3.get_image_data (idx, offset, length);
    default: return false;
    }
  }

  protected:
  union {
  IndexSubtableHeader   header;
  IndexSubtableFormat1  format1;
  IndexSubtableFormat3  format3;
  /* TODO: Format 2, 4, 5. */
  } u;
  public:
  DEFINE_SIZE_UNION (8, header);
};

struct IndexSubtableRecord
{
  bool sanitize (hb_sanitize_context_t *c, const void *base) const
  {
    TRACE_SANITIZE (this);
    return_trace (c->check_struct (this) &&
                  firstGlyphIndex <= lastGlyphIndex &&
                  offsetToSubtable.sanitize (c, base, lastGlyphIndex - firstGlyphIndex + 1));
  }

  bool get_extents (hb_glyph_extents_t *extents,
                    const void *base) const
  {
    return (base+offsetToSubtable).get_extents (extents);
  }

  bool get_image_data (unsigned int  gid,
                       const void   *base,
                       unsigned int *offset,
                       unsigned int *length,
                       unsigned int *format) const
  {
    if (gid < firstGlyphIndex || gid > lastGlyphIndex) return false;
    return (base+offsetToSubtable).get_image_data (gid - firstGlyphIndex,
                                                   offset, length, format);
  }

  GlyphID                       firstGlyphIndex;
  GlyphID                       lastGlyphIndex;
  LOffsetTo<IndexSubtable>      offsetToSubtable;
  public:
  DEFINE_SIZE_STATIC(8);
};

struct IndexSubtableArray
{
  friend struct CBDT;

  bool sanitize (hb_sanitize_context_t *c, unsigned int count) const
  {
    TRACE_SANITIZE (this);
    return_trace (indexSubtablesZ.sanitize (c, count, this));
  }

  public:
  const IndexSubtableRecord* find_table (hb_codepoint_t glyph, unsigned int numTables) const
  {
    for (unsigned int i = 0; i < numTables; ++i)
    {
      unsigned int firstGlyphIndex = indexSubtablesZ[i].firstGlyphIndex;
      unsigned int lastGlyphIndex = indexSubtablesZ[i].lastGlyphIndex;
      if (firstGlyphIndex <= glyph && glyph <= lastGlyphIndex)
        return &indexSubtablesZ[i];
    }
    return nullptr;
  }

  protected:
  UnsizedArrayOf<IndexSubtableRecord>   indexSubtablesZ;
};

struct BitmapSizeTable
{
  friend struct CBLC;
  friend struct CBDT;

  bool sanitize (hb_sanitize_context_t *c, const void *base) const
  {
    TRACE_SANITIZE (this);
    return_trace (c->check_struct (this) &&
                  indexSubtableArrayOffset.sanitize (c, base, numberOfIndexSubtables) &&
                  horizontal.sanitize (c) &&
                  vertical.sanitize (c));
  }

  const IndexSubtableRecord *find_table (hb_codepoint_t glyph,
                                         const void *base,
                                         const void **out_base) const
  {
    *out_base = &(base+indexSubtableArrayOffset);
    return (base+indexSubtableArrayOffset).find_table (glyph, numberOfIndexSubtables);
  }

  protected:
  LNNOffsetTo<IndexSubtableArray>
                        indexSubtableArrayOffset;
  HBUINT32              indexTablesSize;
  HBUINT32              numberOfIndexSubtables;
  HBUINT32              colorRef;
  SBitLineMetrics       horizontal;
  SBitLineMetrics       vertical;
  GlyphID               startGlyphIndex;
  GlyphID               endGlyphIndex;
  HBUINT8               ppemX;
  HBUINT8               ppemY;
  HBUINT8               bitDepth;
  HBINT8                flags;
  public:
  DEFINE_SIZE_STATIC(48);
};


/*
 * Glyph Bitmap Data Formats.
 */

struct GlyphBitmapDataFormat17
{
  SmallGlyphMetrics     glyphMetrics;
  LArrayOf<HBUINT8>     data;
  public:
  DEFINE_SIZE_ARRAY(9, data);
};

struct GlyphBitmapDataFormat18
{
  BigGlyphMetrics       glyphMetrics;
  LArrayOf<HBUINT8>     data;
  public:
  DEFINE_SIZE_ARRAY(12, data);
};

struct GlyphBitmapDataFormat19
{
  LArrayOf<HBUINT8>     data;
  public:
  DEFINE_SIZE_ARRAY(4, data);
};

struct CBLC
{
  friend struct CBDT;

  static constexpr hb_tag_t tableTag = HB_OT_TAG_CBLC;

  bool sanitize (hb_sanitize_context_t *c) const
  {
    TRACE_SANITIZE (this);
    return_trace (c->check_struct (this) &&
                  likely (version.major == 2 || version.major == 3) &&
                  sizeTables.sanitize (c, this));
  }

  protected:
  const BitmapSizeTable &choose_strike (hb_font_t *font) const
  {
    unsigned count = sizeTables.len;
    if (unlikely (!count))
      return Null(BitmapSizeTable);

    unsigned int requested_ppem = MAX (font->x_ppem, font->y_ppem);
    if (!requested_ppem)
      requested_ppem = 1<<30; /* Choose largest strike. */
    unsigned int best_i = 0;
    unsigned int best_ppem = MAX (sizeTables[0].ppemX, sizeTables[0].ppemY);

    for (unsigned int i = 1; i < count; i++)
    {
      unsigned int ppem = MAX (sizeTables[i].ppemX, sizeTables[i].ppemY);
      if ((requested_ppem <= ppem && ppem < best_ppem) ||
          (requested_ppem > best_ppem && ppem > best_ppem))
      {
        best_i = i;
        best_ppem = ppem;
      }
    }

    return sizeTables[best_i];
  }

  protected:
  FixedVersion<>                version;
  LArrayOf<BitmapSizeTable>     sizeTables;
  public:
  DEFINE_SIZE_ARRAY(8, sizeTables);
};

struct CBDT
{
  static constexpr hb_tag_t tableTag = HB_OT_TAG_CBDT;

  struct accelerator_t
  {
    void init (hb_face_t *face)
    {
      cblc = hb_sanitize_context_t().reference_table<CBLC> (face);
      cbdt = hb_sanitize_context_t().reference_table<CBDT> (face);

      upem = hb_face_get_upem (face);
    }

    void fini ()
    {
      this->cblc.destroy ();
      this->cbdt.destroy ();
    }

    bool get_extents (hb_font_t *font, hb_codepoint_t glyph,
                      hb_glyph_extents_t *extents) const
    {
      const void *base;
      const BitmapSizeTable &strike = this->cblc->choose_strike (font);
      const IndexSubtableRecord *subtable_record = strike.find_table (glyph, cblc, &base);
      if (!subtable_record || !strike.ppemX || !strike.ppemY)
        return false;

      if (subtable_record->get_extents (extents, base))
        return true;

      unsigned int image_offset = 0, image_length = 0, image_format = 0;
      if (!subtable_record->get_image_data (glyph, base, &image_offset, &image_length, &image_format))
        return false;

      {
        unsigned int cbdt_len = cbdt.get_length ();
        if (unlikely (image_offset > cbdt_len || cbdt_len - image_offset < image_length))
          return false;

        switch (image_format)
        {
          case 17: {
            if (unlikely (image_length < GlyphBitmapDataFormat17::min_size))
              return false;
            const GlyphBitmapDataFormat17& glyphFormat17 =
                StructAtOffset<GlyphBitmapDataFormat17> (this->cbdt, image_offset);
            glyphFormat17.glyphMetrics.get_extents (extents);
            break;
          }
          case 18: {
            if (unlikely (image_length < GlyphBitmapDataFormat18::min_size))
              return false;
            const GlyphBitmapDataFormat18& glyphFormat18 =
                StructAtOffset<GlyphBitmapDataFormat18> (this->cbdt, image_offset);
            glyphFormat18.glyphMetrics.get_extents (extents);
            break;
          }
          default:
            // TODO: Support other image formats.
            return false;
        }
      }

      /* Convert to font units. */
      double x_scale = upem / (double) strike.ppemX;
      double y_scale = upem / (double) strike.ppemY;
      extents->x_bearing = round (extents->x_bearing * x_scale);
      extents->y_bearing = round (extents->y_bearing * y_scale);
      extents->width = round (extents->width * x_scale);
      extents->height = round (extents->height * y_scale);

      return true;
    }

    hb_blob_t* reference_png (hb_font_t      *font,
                                     hb_codepoint_t  glyph) const
    {
      const void *base;
      const BitmapSizeTable &strike = this->cblc->choose_strike (font);
      const IndexSubtableRecord *subtable_record = strike.find_table (glyph, cblc, &base);
      if (!subtable_record || !strike.ppemX || !strike.ppemY)
        return hb_blob_get_empty ();

      unsigned int image_offset = 0, image_length = 0, image_format = 0;
      if (!subtable_record->get_image_data (glyph, base, &image_offset, &image_length, &image_format))
        return hb_blob_get_empty ();

      {
        unsigned int cbdt_len = cbdt.get_length ();
        if (unlikely (image_offset > cbdt_len || cbdt_len - image_offset < image_length))
          return hb_blob_get_empty ();

        switch (image_format)
        {
          case 17: {
            if (unlikely (image_length < GlyphBitmapDataFormat17::min_size))
              return hb_blob_get_empty ();
            const GlyphBitmapDataFormat17& glyphFormat17 =
              StructAtOffset<GlyphBitmapDataFormat17> (this->cbdt, image_offset);
            return hb_blob_create_sub_blob (cbdt.get_blob (),
                                            image_offset + GlyphBitmapDataFormat17::min_size,
                                            glyphFormat17.data.len);
          }
          case 18: {
            if (unlikely (image_length < GlyphBitmapDataFormat18::min_size))
              return hb_blob_get_empty ();
            const GlyphBitmapDataFormat18& glyphFormat18 =
              StructAtOffset<GlyphBitmapDataFormat18> (this->cbdt, image_offset);
            return hb_blob_create_sub_blob (cbdt.get_blob (),
                                            image_offset + GlyphBitmapDataFormat18::min_size,
                                            glyphFormat18.data.len);
          }
          case 19: {
            if (unlikely (image_length < GlyphBitmapDataFormat19::min_size))
              return hb_blob_get_empty ();
            const GlyphBitmapDataFormat19& glyphFormat19 =
              StructAtOffset<GlyphBitmapDataFormat19> (this->cbdt, image_offset);
            return hb_blob_create_sub_blob (cbdt.get_blob (),
                                            image_offset + GlyphBitmapDataFormat19::min_size,
                                            glyphFormat19.data.len);
          }
        }
      }

      return hb_blob_get_empty ();
    }

    bool has_data () const { return cbdt.get_length (); }

    private:
    hb_blob_ptr_t<CBLC> cblc;
    hb_blob_ptr_t<CBDT> cbdt;

    unsigned int upem;
  };

  bool sanitize (hb_sanitize_context_t *c) const
  {
    TRACE_SANITIZE (this);
    return_trace (c->check_struct (this) &&
                  likely (version.major == 2 || version.major == 3));
  }

  protected:
  FixedVersion<>                version;
  UnsizedArrayOf<HBUINT8>       dataZ;
  public:
  DEFINE_SIZE_ARRAY(4, dataZ);
};

struct CBDT_accelerator_t : CBDT::accelerator_t {};

} /* namespace OT */

#endif /* HB_OT_COLOR_CBDT_TABLE_HH */
