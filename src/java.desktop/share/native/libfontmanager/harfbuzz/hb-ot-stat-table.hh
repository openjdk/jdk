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

#ifndef HB_OT_STAT_TABLE_HH
#define HB_OT_STAT_TABLE_HH

#include "hb-open-type.hh"
#include "hb-ot-layout-common.hh"

/*
 * STAT -- Style Attributes
 * https://docs.microsoft.com/en-us/typography/opentype/spec/stat
 */
#define HB_OT_TAG_STAT HB_TAG('S','T','A','T')


namespace OT {

enum
{
  OLDER_SIBLING_FONT_ATTRIBUTE = 0x0001,        /* If set, this axis value table
                                                 * provides axis value information
                                                 * that is applicable to other fonts
                                                 * within the same font family. This
                                                 * is used if the other fonts were
                                                 * released earlier and did not include
                                                 * information about values for some axis.
                                                 * If newer versions of the other
                                                 * fonts include the information
                                                 * themselves and are present,
                                                 * then this record is ignored. */
  ELIDABLE_AXIS_VALUE_NAME = 0x0002             /* If set, it indicates that the axis
                                                 * value represents the “normal” value
                                                 * for the axis and may be omitted when
                                                 * composing name strings. */
  // Reserved = 0xFFFC                          /* Reserved for future use — set to zero. */
};

struct AxisValueFormat1
{
  bool sanitize (hb_sanitize_context_t *c) const
  {
    TRACE_SANITIZE (this);
    return_trace (likely (c->check_struct (this)));
  }

  protected:
  HBUINT16      format;         /* Format identifier — set to 1. */
  HBUINT16      axisIndex;      /* Zero-base index into the axis record array
                                 * identifying the axis of design variation
                                 * to which the axis value record applies.
                                 * Must be less than designAxisCount. */
  HBUINT16      flags;          /* Flags — see below for details. */
  NameID        valueNameID;    /* The name ID for entries in the 'name' table
                                 * that provide a display string for this
                                 * attribute value. */
  Fixed         value;          /* A numeric value for this attribute value. */
  public:
  DEFINE_SIZE_STATIC (12);
};

struct AxisValueFormat2
{
  bool sanitize (hb_sanitize_context_t *c) const
  {
    TRACE_SANITIZE (this);
    return_trace (likely (c->check_struct (this)));
  }

  protected:
  HBUINT16      format;         /* Format identifier — set to 2. */
  HBUINT16      axisIndex;      /* Zero-base index into the axis record array
                                 * identifying the axis of design variation
                                 * to which the axis value record applies.
                                 * Must be less than designAxisCount. */
  HBUINT16      flags;          /* Flags — see below for details. */
  NameID        valueNameID;    /* The name ID for entries in the 'name' table
                                 * that provide a display string for this
                                 * attribute value. */
  Fixed         nominalValue;   /* A numeric value for this attribute value. */
  Fixed         rangeMinValue;  /* The minimum value for a range associated
                                 * with the specified name ID. */
  Fixed         rangeMaxValue;  /* The maximum value for a range associated
                                 * with the specified name ID. */
  public:
  DEFINE_SIZE_STATIC (20);
};

struct AxisValueFormat3
{
  bool sanitize (hb_sanitize_context_t *c) const
  {
    TRACE_SANITIZE (this);
    return_trace (likely (c->check_struct (this)));
  }

  protected:
  HBUINT16      format;         /* Format identifier — set to 3. */
  HBUINT16      axisIndex;      /* Zero-base index into the axis record array
                                 * identifying the axis of design variation
                                 * to which the axis value record applies.
                                 * Must be less than designAxisCount. */
  HBUINT16      flags;          /* Flags — see below for details. */
  NameID        valueNameID;    /* The name ID for entries in the 'name' table
                                 * that provide a display string for this
                                 * attribute value. */
  Fixed         value;          /* A numeric value for this attribute value. */
  Fixed         linkedValue;    /* The numeric value for a style-linked mapping
                                 * from this value. */
  public:
  DEFINE_SIZE_STATIC (16);
};

struct AxisValueRecord
{
  bool sanitize (hb_sanitize_context_t *c) const
  {
    TRACE_SANITIZE (this);
    return_trace (likely (c->check_struct (this)));
  }

  protected:
  HBUINT16      axisIndex;      /* Zero-base index into the axis record array
                                 * identifying the axis to which this value
                                 * applies. Must be less than designAxisCount. */
  Fixed         value;          /* A numeric value for this attribute value. */
  public:
  DEFINE_SIZE_STATIC (6);
};

struct AxisValueFormat4
{
  bool sanitize (hb_sanitize_context_t *c) const
  {
    TRACE_SANITIZE (this);
    return_trace (likely (c->check_struct (this)));
  }

  protected:
  HBUINT16      format;         /* Format identifier — set to 4. */
  HBUINT16      axisCount;      /* The total number of axes contributing to
                                 * this axis-values combination. */
  HBUINT16      flags;          /* Flags — see below for details. */
  NameID        valueNameID;    /* The name ID for entries in the 'name' table
                                 * that provide a display string for this
                                 * attribute value. */
  UnsizedArrayOf<AxisValueRecord>
                axisValues;     /* Array of AxisValue records that provide the
                                 * combination of axis values, one for each
                                 * contributing axis. */
  public:
  DEFINE_SIZE_ARRAY (8, axisValues);
};

struct AxisValue
{
  bool sanitize (hb_sanitize_context_t *c) const
  {
    TRACE_SANITIZE (this);
    if (unlikely (c->check_struct (this)))
      return_trace (false);

    switch (u.format)
    {
    case 1:  return_trace (likely (u.format1.sanitize (c)));
    case 2:  return_trace (likely (u.format2.sanitize (c)));
    case 3:  return_trace (likely (u.format3.sanitize (c)));
    case 4:  return_trace (likely (u.format4.sanitize (c)));
    default: return_trace (true);
    }
  }

  protected:
  union
  {
  HBUINT16              format;
  AxisValueFormat1      format1;
  AxisValueFormat2      format2;
  AxisValueFormat3      format3;
  AxisValueFormat4      format4;
  } u;
  public:
  DEFINE_SIZE_UNION (2, format);
};

struct StatAxisRecord
{
  bool sanitize (hb_sanitize_context_t *c) const
  {
    TRACE_SANITIZE (this);
    return_trace (likely (c->check_struct (this)));
  }

  protected:
  Tag           tag;            /* A tag identifying the axis of design variation. */
  NameID        nameID;         /* The name ID for entries in the 'name' table that
                                 * provide a display string for this axis. */
  HBUINT16      ordering;       /* A value that applications can use to determine
                                 * primary sorting of face names, or for ordering
                                 * of descriptors when composing family or face names. */
  public:
  DEFINE_SIZE_STATIC (8);
};

struct STAT
{
  static constexpr hb_tag_t tableTag = HB_OT_TAG_STAT;

  bool sanitize (hb_sanitize_context_t *c) const
  {
    TRACE_SANITIZE (this);
    return_trace (likely (c->check_struct (this) &&
                          majorVersion == 1 &&
                          minorVersion > 0 &&
                          designAxesOffset.sanitize (c, this, designAxisCount) &&
                          offsetToAxisValueOffsets.sanitize (c, this, axisValueCount, &(this+offsetToAxisValueOffsets))));
  }

  protected:
  HBUINT16      majorVersion;   /* Major version number of the style attributes
                                 * table — set to 1. */
  HBUINT16      minorVersion;   /* Minor version number of the style attributes
                                 * table — set to 2. */
  HBUINT16      designAxisSize; /* The size in bytes of each axis record. */
  HBUINT16      designAxisCount;/* The number of design axis records. In a
                                 * font with an 'fvar' table, this value must be
                                 * greater than or equal to the axisCount value
                                 * in the 'fvar' table. In all fonts, must
                                 * be greater than zero if axisValueCount
                                 * is greater than zero. */
  LNNOffsetTo<UnsizedArrayOf<StatAxisRecord> >
                designAxesOffset;
                                /* Offset in bytes from the beginning of
                                 * the STAT table to the start of the design
                                 * axes array. If designAxisCount is zero,
                                 * set to zero; if designAxisCount is greater
                                 * than zero, must be greater than zero. */
  HBUINT16      axisValueCount; /* The number of axis value tables. */
  LNNOffsetTo<UnsizedArrayOf<OffsetTo<AxisValue> > >
                offsetToAxisValueOffsets;
                                /* Offset in bytes from the beginning of
                                 * the STAT table to the start of the design
                                 * axes value offsets array. If axisValueCount
                                 * is zero, set to zero; if axisValueCount is
                                 * greater than zero, must be greater than zero. */
  NameID        elidedFallbackNameID;
                                /* Name ID used as fallback when projection of
                                 * names into a particular font model produces
                                 * a subfamily name containing only elidable
                                 * elements. */
  public:
  DEFINE_SIZE_STATIC (20);
};


} /* namespace OT */


#endif /* HB_OT_STAT_TABLE_HH */
