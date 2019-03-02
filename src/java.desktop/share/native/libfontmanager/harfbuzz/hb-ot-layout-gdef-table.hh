/*
 * Copyright © 2007,2008,2009  Red Hat, Inc.
 * Copyright © 2010,2011,2012  Google, Inc.
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
 * Red Hat Author(s): Behdad Esfahbod
 * Google Author(s): Behdad Esfahbod
 */

#ifndef HB_OT_LAYOUT_GDEF_TABLE_HH
#define HB_OT_LAYOUT_GDEF_TABLE_HH

#include "hb-ot-layout-common.hh"

#include "hb-font.hh"


namespace OT {


/*
 * Attachment List Table
 */

typedef ArrayOf<HBUINT16> AttachPoint;  /* Array of contour point indices--in
                                         * increasing numerical order */

struct AttachList
{
  unsigned int get_attach_points (hb_codepoint_t glyph_id,
                                  unsigned int start_offset,
                                  unsigned int *point_count /* IN/OUT */,
                                  unsigned int *point_array /* OUT */) const
  {
    unsigned int index = (this+coverage).get_coverage (glyph_id);
    if (index == NOT_COVERED)
    {
      if (point_count)
        *point_count = 0;
      return 0;
    }

    const AttachPoint &points = this+attachPoint[index];

    if (point_count)
    {
      hb_array_t<const HBUINT16> array = points.sub_array (start_offset, point_count);
      unsigned int count = array.length;
      for (unsigned int i = 0; i < count; i++)
        point_array[i] = array[i];
    }

    return points.len;
  }

  bool sanitize (hb_sanitize_context_t *c) const
  {
    TRACE_SANITIZE (this);
    return_trace (coverage.sanitize (c, this) && attachPoint.sanitize (c, this));
  }

  protected:
  OffsetTo<Coverage>
                coverage;               /* Offset to Coverage table -- from
                                         * beginning of AttachList table */
  OffsetArrayOf<AttachPoint>
                attachPoint;            /* Array of AttachPoint tables
                                         * in Coverage Index order */
  public:
  DEFINE_SIZE_ARRAY (4, attachPoint);
};

/*
 * Ligature Caret Table
 */

struct CaretValueFormat1
{
  friend struct CaretValue;

  private:
  hb_position_t get_caret_value (hb_font_t *font, hb_direction_t direction) const
  {
    return HB_DIRECTION_IS_HORIZONTAL (direction) ? font->em_scale_x (coordinate) : font->em_scale_y (coordinate);
  }

  bool sanitize (hb_sanitize_context_t *c) const
  {
    TRACE_SANITIZE (this);
    return_trace (c->check_struct (this));
  }

  protected:
  HBUINT16      caretValueFormat;       /* Format identifier--format = 1 */
  FWORD         coordinate;             /* X or Y value, in design units */
  public:
  DEFINE_SIZE_STATIC (4);
};

struct CaretValueFormat2
{
  friend struct CaretValue;

  private:
  hb_position_t get_caret_value (hb_font_t *font, hb_direction_t direction, hb_codepoint_t glyph_id) const
  {
    hb_position_t x, y;
    font->get_glyph_contour_point_for_origin (glyph_id, caretValuePoint, direction, &x, &y);
    return HB_DIRECTION_IS_HORIZONTAL (direction) ? x : y;
  }

  bool sanitize (hb_sanitize_context_t *c) const
  {
    TRACE_SANITIZE (this);
    return_trace (c->check_struct (this));
  }

  protected:
  HBUINT16      caretValueFormat;       /* Format identifier--format = 2 */
  HBUINT16      caretValuePoint;        /* Contour point index on glyph */
  public:
  DEFINE_SIZE_STATIC (4);
};

struct CaretValueFormat3
{
  friend struct CaretValue;

  hb_position_t get_caret_value (hb_font_t *font, hb_direction_t direction,
                                 const VariationStore &var_store) const
  {
    return HB_DIRECTION_IS_HORIZONTAL (direction) ?
           font->em_scale_x (coordinate) + (this+deviceTable).get_x_delta (font, var_store) :
           font->em_scale_y (coordinate) + (this+deviceTable).get_y_delta (font, var_store);
  }

  bool sanitize (hb_sanitize_context_t *c) const
  {
    TRACE_SANITIZE (this);
    return_trace (c->check_struct (this) && deviceTable.sanitize (c, this));
  }

  protected:
  HBUINT16      caretValueFormat;       /* Format identifier--format = 3 */
  FWORD         coordinate;             /* X or Y value, in design units */
  OffsetTo<Device>
                deviceTable;            /* Offset to Device table for X or Y
                                         * value--from beginning of CaretValue
                                         * table */
  public:
  DEFINE_SIZE_STATIC (6);
};

struct CaretValue
{
  hb_position_t get_caret_value (hb_font_t *font,
                                        hb_direction_t direction,
                                        hb_codepoint_t glyph_id,
                                        const VariationStore &var_store) const
  {
    switch (u.format) {
    case 1: return u.format1.get_caret_value (font, direction);
    case 2: return u.format2.get_caret_value (font, direction, glyph_id);
    case 3: return u.format3.get_caret_value (font, direction, var_store);
    default:return 0;
    }
  }

  bool sanitize (hb_sanitize_context_t *c) const
  {
    TRACE_SANITIZE (this);
    if (!u.format.sanitize (c)) return_trace (false);
    switch (u.format) {
    case 1: return_trace (u.format1.sanitize (c));
    case 2: return_trace (u.format2.sanitize (c));
    case 3: return_trace (u.format3.sanitize (c));
    default:return_trace (true);
    }
  }

  protected:
  union {
  HBUINT16              format;         /* Format identifier */
  CaretValueFormat1     format1;
  CaretValueFormat2     format2;
  CaretValueFormat3     format3;
  } u;
  public:
  DEFINE_SIZE_UNION (2, format);
};

struct LigGlyph
{
  unsigned int get_lig_carets (hb_font_t *font,
                               hb_direction_t direction,
                               hb_codepoint_t glyph_id,
                               const VariationStore &var_store,
                               unsigned int start_offset,
                               unsigned int *caret_count /* IN/OUT */,
                               hb_position_t *caret_array /* OUT */) const
  {
    if (caret_count)
    {
      hb_array_t <const OffsetTo<CaretValue> > array = carets.sub_array (start_offset, caret_count);
      unsigned int count = array.length;
      for (unsigned int i = 0; i < count; i++)
        caret_array[i] = (this+array[i]).get_caret_value (font, direction, glyph_id, var_store);
    }

    return carets.len;
  }

  bool sanitize (hb_sanitize_context_t *c) const
  {
    TRACE_SANITIZE (this);
    return_trace (carets.sanitize (c, this));
  }

  protected:
  OffsetArrayOf<CaretValue>
                carets;                 /* Offset array of CaretValue tables
                                         * --from beginning of LigGlyph table
                                         * --in increasing coordinate order */
  public:
  DEFINE_SIZE_ARRAY (2, carets);
};

struct LigCaretList
{
  unsigned int get_lig_carets (hb_font_t *font,
                               hb_direction_t direction,
                               hb_codepoint_t glyph_id,
                               const VariationStore &var_store,
                               unsigned int start_offset,
                               unsigned int *caret_count /* IN/OUT */,
                               hb_position_t *caret_array /* OUT */) const
  {
    unsigned int index = (this+coverage).get_coverage (glyph_id);
    if (index == NOT_COVERED)
    {
      if (caret_count)
        *caret_count = 0;
      return 0;
    }
    const LigGlyph &lig_glyph = this+ligGlyph[index];
    return lig_glyph.get_lig_carets (font, direction, glyph_id, var_store, start_offset, caret_count, caret_array);
  }

  bool sanitize (hb_sanitize_context_t *c) const
  {
    TRACE_SANITIZE (this);
    return_trace (coverage.sanitize (c, this) && ligGlyph.sanitize (c, this));
  }

  protected:
  OffsetTo<Coverage>
                coverage;               /* Offset to Coverage table--from
                                         * beginning of LigCaretList table */
  OffsetArrayOf<LigGlyph>
                ligGlyph;               /* Array of LigGlyph tables
                                         * in Coverage Index order */
  public:
  DEFINE_SIZE_ARRAY (4, ligGlyph);
};


struct MarkGlyphSetsFormat1
{
  bool covers (unsigned int set_index, hb_codepoint_t glyph_id) const
  { return (this+coverage[set_index]).get_coverage (glyph_id) != NOT_COVERED; }

  bool sanitize (hb_sanitize_context_t *c) const
  {
    TRACE_SANITIZE (this);
    return_trace (coverage.sanitize (c, this));
  }

  protected:
  HBUINT16      format;                 /* Format identifier--format = 1 */
  ArrayOf<LOffsetTo<Coverage> >
                coverage;               /* Array of long offsets to mark set
                                         * coverage tables */
  public:
  DEFINE_SIZE_ARRAY (4, coverage);
};

struct MarkGlyphSets
{
  bool covers (unsigned int set_index, hb_codepoint_t glyph_id) const
  {
    switch (u.format) {
    case 1: return u.format1.covers (set_index, glyph_id);
    default:return false;
    }
  }

  bool sanitize (hb_sanitize_context_t *c) const
  {
    TRACE_SANITIZE (this);
    if (!u.format.sanitize (c)) return_trace (false);
    switch (u.format) {
    case 1: return_trace (u.format1.sanitize (c));
    default:return_trace (true);
    }
  }

  protected:
  union {
  HBUINT16              format;         /* Format identifier */
  MarkGlyphSetsFormat1  format1;
  } u;
  public:
  DEFINE_SIZE_UNION (2, format);
};


/*
 * GDEF -- Glyph Definition
 * https://docs.microsoft.com/en-us/typography/opentype/spec/gdef
 */


struct GDEF
{
  static constexpr hb_tag_t tableTag = HB_OT_TAG_GDEF;

  enum GlyphClasses {
    UnclassifiedGlyph   = 0,
    BaseGlyph           = 1,
    LigatureGlyph       = 2,
    MarkGlyph           = 3,
    ComponentGlyph      = 4
  };

  bool has_data () const { return version.to_int (); }
  bool has_glyph_classes () const { return glyphClassDef != 0; }
  unsigned int get_glyph_class (hb_codepoint_t glyph) const
  { return (this+glyphClassDef).get_class (glyph); }
  void get_glyphs_in_class (unsigned int klass, hb_set_t *glyphs) const
  { (this+glyphClassDef).add_class (glyphs, klass); }

  bool has_mark_attachment_types () const { return markAttachClassDef != 0; }
  unsigned int get_mark_attachment_type (hb_codepoint_t glyph) const
  { return (this+markAttachClassDef).get_class (glyph); }

  bool has_attach_points () const { return attachList != 0; }
  unsigned int get_attach_points (hb_codepoint_t glyph_id,
                                  unsigned int start_offset,
                                  unsigned int *point_count /* IN/OUT */,
                                  unsigned int *point_array /* OUT */) const
  { return (this+attachList).get_attach_points (glyph_id, start_offset, point_count, point_array); }

  bool has_lig_carets () const { return ligCaretList != 0; }
  unsigned int get_lig_carets (hb_font_t *font,
                               hb_direction_t direction,
                               hb_codepoint_t glyph_id,
                               unsigned int start_offset,
                               unsigned int *caret_count /* IN/OUT */,
                               hb_position_t *caret_array /* OUT */) const
  { return (this+ligCaretList).get_lig_carets (font,
                                               direction, glyph_id, get_var_store(),
                                               start_offset, caret_count, caret_array); }

  bool has_mark_sets () const { return version.to_int () >= 0x00010002u && markGlyphSetsDef != 0; }
  bool mark_set_covers (unsigned int set_index, hb_codepoint_t glyph_id) const
  { return version.to_int () >= 0x00010002u && (this+markGlyphSetsDef).covers (set_index, glyph_id); }

  bool has_var_store () const { return version.to_int () >= 0x00010003u && varStore != 0; }
  const VariationStore &get_var_store () const
  { return version.to_int () >= 0x00010003u ? this+varStore : Null(VariationStore); }

  /* glyph_props is a 16-bit integer where the lower 8-bit have bits representing
   * glyph class and other bits, and high 8-bit the mark attachment type (if any).
   * Not to be confused with lookup_props which is very similar. */
  unsigned int get_glyph_props (hb_codepoint_t glyph) const
  {
    unsigned int klass = get_glyph_class (glyph);

    static_assert (((unsigned int) HB_OT_LAYOUT_GLYPH_PROPS_BASE_GLYPH == (unsigned int) LookupFlag::IgnoreBaseGlyphs), "");
    static_assert (((unsigned int) HB_OT_LAYOUT_GLYPH_PROPS_LIGATURE == (unsigned int) LookupFlag::IgnoreLigatures), "");
    static_assert (((unsigned int) HB_OT_LAYOUT_GLYPH_PROPS_MARK == (unsigned int) LookupFlag::IgnoreMarks), "");

    switch (klass) {
    default:                    return 0;
    case BaseGlyph:             return HB_OT_LAYOUT_GLYPH_PROPS_BASE_GLYPH;
    case LigatureGlyph:         return HB_OT_LAYOUT_GLYPH_PROPS_LIGATURE;
    case MarkGlyph:
          klass = get_mark_attachment_type (glyph);
          return HB_OT_LAYOUT_GLYPH_PROPS_MARK | (klass << 8);
    }
  }

  HB_INTERNAL bool is_blacklisted (hb_blob_t *blob,
                                   hb_face_t *face) const;

  struct accelerator_t
  {
    void init (hb_face_t *face)
    {
      this->table = hb_sanitize_context_t().reference_table<GDEF> (face);
      if (unlikely (this->table->is_blacklisted (this->table.get_blob (), face)))
      {
        hb_blob_destroy (this->table.get_blob ());
        this->table = hb_blob_get_empty ();
      }
    }

    void fini () { this->table.destroy (); }

    hb_blob_ptr_t<GDEF> table;
  };

  unsigned int get_size () const
  {
    return min_size +
           (version.to_int () >= 0x00010002u ? markGlyphSetsDef.static_size : 0) +
           (version.to_int () >= 0x00010003u ? varStore.static_size : 0);
  }

  bool subset (hb_subset_context_t *c) const
  {
    TRACE_SUBSET (this);
    struct GDEF *out = c->serializer->embed (*this);
    if (unlikely (!out)) return_trace (false);

    out->glyphClassDef.serialize_subset (c, this+glyphClassDef, out);
    out->attachList.set (0);//TODO(subset) serialize_subset (c, this+attachList, out);
    out->ligCaretList.set (0);//TODO(subset) serialize_subset (c, this+ligCaretList, out);
    out->markAttachClassDef.serialize_subset (c, this+markAttachClassDef, out);

    if (version.to_int () >= 0x00010002u)
      out->markGlyphSetsDef.set (0);// TODO(subset) serialize_subset (c, this+markGlyphSetsDef, out);

    if (version.to_int () >= 0x00010003u)
      out->varStore.set (0);// TODO(subset) serialize_subset (c, this+varStore, out);

    return_trace (true);
  }

  bool sanitize (hb_sanitize_context_t *c) const
  {
    TRACE_SANITIZE (this);
    return_trace (version.sanitize (c) &&
                  likely (version.major == 1) &&
                  glyphClassDef.sanitize (c, this) &&
                  attachList.sanitize (c, this) &&
                  ligCaretList.sanitize (c, this) &&
                  markAttachClassDef.sanitize (c, this) &&
                  (version.to_int () < 0x00010002u || markGlyphSetsDef.sanitize (c, this)) &&
                  (version.to_int () < 0x00010003u || varStore.sanitize (c, this)));
  }

  protected:
  FixedVersion<>version;                /* Version of the GDEF table--currently
                                         * 0x00010003u */
  OffsetTo<ClassDef>
                glyphClassDef;          /* Offset to class definition table
                                         * for glyph type--from beginning of
                                         * GDEF header (may be Null) */
  OffsetTo<AttachList>
                attachList;             /* Offset to list of glyphs with
                                         * attachment points--from beginning
                                         * of GDEF header (may be Null) */
  OffsetTo<LigCaretList>
                ligCaretList;           /* Offset to list of positioning points
                                         * for ligature carets--from beginning
                                         * of GDEF header (may be Null) */
  OffsetTo<ClassDef>
                markAttachClassDef;     /* Offset to class definition table for
                                         * mark attachment type--from beginning
                                         * of GDEF header (may be Null) */
  OffsetTo<MarkGlyphSets>
                markGlyphSetsDef;       /* Offset to the table of mark set
                                         * definitions--from beginning of GDEF
                                         * header (may be NULL).  Introduced
                                         * in version 0x00010002. */
  LOffsetTo<VariationStore>
                varStore;               /* Offset to the table of Item Variation
                                         * Store--from beginning of GDEF
                                         * header (may be NULL).  Introduced
                                         * in version 0x00010003. */
  public:
  DEFINE_SIZE_MIN (12);
};

struct GDEF_accelerator_t : GDEF::accelerator_t {};

} /* namespace OT */


#endif /* HB_OT_LAYOUT_GDEF_TABLE_HH */
