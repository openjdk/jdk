/*
 * Copyright © 2017  Google, Inc.
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
 * Google Author(s): Behdad Esfahbod
 */

#ifndef HB_OT_VAR_FVAR_TABLE_HH
#define HB_OT_VAR_FVAR_TABLE_HH

#include "hb-open-type.hh"

/*
 * fvar -- Font Variations
 * https://docs.microsoft.com/en-us/typography/opentype/spec/fvar
 */

#define HB_OT_TAG_fvar HB_TAG('f','v','a','r')


namespace OT {


struct InstanceRecord
{
  friend struct fvar;

  hb_array_t<const Fixed> get_coordinates (unsigned int axis_count) const
  { return coordinatesZ.as_array (axis_count); }

  bool sanitize (hb_sanitize_context_t *c, unsigned int axis_count) const
  {
    TRACE_SANITIZE (this);
    return_trace (c->check_struct (this) &&
                  c->check_array (coordinatesZ.arrayZ, axis_count));
  }

  protected:
  NameID        subfamilyNameID;/* The name ID for entries in the 'name' table
                                 * that provide subfamily names for this instance. */
  HBUINT16      flags;          /* Reserved for future use — set to 0. */
  UnsizedArrayOf<Fixed>
                coordinatesZ;   /* The coordinates array for this instance. */
  //NameID      postScriptNameIDX;/*Optional. The name ID for entries in the 'name'
  //                              * table that provide PostScript names for this
  //                              * instance. */

  public:
  DEFINE_SIZE_UNBOUNDED (4);
};

struct AxisRecord
{
  enum
  {
    AXIS_FLAG_HIDDEN    = 0x0001,
  };

  bool sanitize (hb_sanitize_context_t *c) const
  {
    TRACE_SANITIZE (this);
    return_trace (c->check_struct (this));
  }

  public:
  Tag           axisTag;        /* Tag identifying the design variation for the axis. */
  Fixed         minValue;       /* The minimum coordinate value for the axis. */
  Fixed         defaultValue;   /* The default coordinate value for the axis. */
  Fixed         maxValue;       /* The maximum coordinate value for the axis. */
  HBUINT16      flags;          /* Axis flags. */
  NameID        axisNameID;     /* The name ID for entries in the 'name' table that
                                 * provide a display name for this axis. */

  public:
  DEFINE_SIZE_STATIC (20);
};

struct fvar
{
  static constexpr hb_tag_t tableTag = HB_OT_TAG_fvar;

  bool has_data () const { return version.to_int (); }

  bool sanitize (hb_sanitize_context_t *c) const
  {
    TRACE_SANITIZE (this);
    return_trace (version.sanitize (c) &&
                  likely (version.major == 1) &&
                  c->check_struct (this) &&
                  axisSize == 20 && /* Assumed in our code. */
                  instanceSize >= axisCount * 4 + 4 &&
                  get_axes ().sanitize (c) &&
                  c->check_range (get_instance (0), instanceCount, instanceSize));
  }

  unsigned int get_axis_count () const { return axisCount; }

  void get_axis_deprecated (unsigned int axis_index,
                                   hb_ot_var_axis_t *info) const
  {
    const AxisRecord &axis = get_axes ()[axis_index];
    info->tag = axis.axisTag;
    info->name_id =  axis.axisNameID;
    info->default_value = axis.defaultValue / 65536.;
    /* Ensure order, to simplify client math. */
    info->min_value = MIN<float> (info->default_value, axis.minValue / 65536.);
    info->max_value = MAX<float> (info->default_value, axis.maxValue / 65536.);
  }

  void get_axis_info (unsigned int axis_index,
                      hb_ot_var_axis_info_t *info) const
  {
    const AxisRecord &axis = get_axes ()[axis_index];
    info->axis_index = axis_index;
    info->tag = axis.axisTag;
    info->name_id =  axis.axisNameID;
    info->flags = (hb_ot_var_axis_flags_t) (unsigned int) axis.flags;
    info->default_value = axis.defaultValue / 65536.;
    /* Ensure order, to simplify client math. */
    info->min_value = MIN<float> (info->default_value, axis.minValue / 65536.);
    info->max_value = MAX<float> (info->default_value, axis.maxValue / 65536.);
    info->reserved = 0;
  }

  unsigned int get_axes_deprecated (unsigned int      start_offset,
                                    unsigned int     *axes_count /* IN/OUT */,
                                    hb_ot_var_axis_t *axes_array /* OUT */) const
  {
    if (axes_count)
    {
      /* TODO Rewrite as hb_array_t<>::sub-array() */
      unsigned int count = axisCount;
      start_offset = MIN (start_offset, count);

      count -= start_offset;
      axes_array += start_offset;

      count = MIN (count, *axes_count);
      *axes_count = count;

      for (unsigned int i = 0; i < count; i++)
        get_axis_deprecated (start_offset + i, axes_array + i);
    }
    return axisCount;
  }

  unsigned int get_axis_infos (unsigned int           start_offset,
                               unsigned int          *axes_count /* IN/OUT */,
                               hb_ot_var_axis_info_t *axes_array /* OUT */) const
  {
    if (axes_count)
    {
      /* TODO Rewrite as hb_array_t<>::sub-array() */
      unsigned int count = axisCount;
      start_offset = MIN (start_offset, count);

      count -= start_offset;
      axes_array += start_offset;

      count = MIN (count, *axes_count);
      *axes_count = count;

      for (unsigned int i = 0; i < count; i++)
        get_axis_info (start_offset + i, axes_array + i);
    }
    return axisCount;
  }

  bool find_axis_deprecated (hb_tag_t tag,
                             unsigned int *axis_index,
                             hb_ot_var_axis_t *info) const
  {
    const AxisRecord *axes = get_axes ();
    unsigned int count = get_axis_count ();
    for (unsigned int i = 0; i < count; i++)
      if (axes[i].axisTag == tag)
      {
        if (axis_index)
          *axis_index = i;
        get_axis_deprecated (i, info);
        return true;
      }
    if (axis_index)
      *axis_index = HB_OT_VAR_NO_AXIS_INDEX;
    return false;
  }

  bool find_axis_info (hb_tag_t tag,
                       hb_ot_var_axis_info_t *info) const
  {
    const AxisRecord *axes = get_axes ();
    unsigned int count = get_axis_count ();
    for (unsigned int i = 0; i < count; i++)
      if (axes[i].axisTag == tag)
      {
        get_axis_info (i, info);
        return true;
      }
    return false;
  }

  int normalize_axis_value (unsigned int axis_index, float v) const
  {
    hb_ot_var_axis_info_t axis;
    get_axis_info (axis_index, &axis);

    v = MAX (MIN (v, axis.max_value), axis.min_value); /* Clamp. */

    if (v == axis.default_value)
      return 0;
    else if (v < axis.default_value)
      v = (v - axis.default_value) / (axis.default_value - axis.min_value);
    else
      v = (v - axis.default_value) / (axis.max_value - axis.default_value);
    return (int) (v * 16384.f + (v >= 0.f ? .5f : -.5f));
  }

  unsigned int get_instance_count () const { return instanceCount; }

  hb_ot_name_id_t get_instance_subfamily_name_id (unsigned int instance_index) const
  {
    const InstanceRecord *instance = get_instance (instance_index);
    if (unlikely (!instance)) return HB_OT_NAME_ID_INVALID;
    return instance->subfamilyNameID;
  }

  hb_ot_name_id_t get_instance_postscript_name_id (unsigned int instance_index) const
  {
    const InstanceRecord *instance = get_instance (instance_index);
    if (unlikely (!instance)) return HB_OT_NAME_ID_INVALID;
    if (instanceSize >= axisCount * 4 + 6)
      return StructAfter<NameID> (instance->get_coordinates (axisCount));
    return HB_OT_NAME_ID_INVALID;
  }

  unsigned int get_instance_coords (unsigned int  instance_index,
                                           unsigned int *coords_length, /* IN/OUT */
                                           float        *coords         /* OUT */) const
  {
    const InstanceRecord *instance = get_instance (instance_index);
    if (unlikely (!instance))
    {
      if (coords_length)
        *coords_length = 0;
      return 0;
    }

    if (coords_length && *coords_length)
    {
      hb_array_t<const Fixed> instanceCoords = instance->get_coordinates (axisCount)
                                                         .sub_array (0, *coords_length);
      for (unsigned int i = 0; i < instanceCoords.length; i++)
        coords[i] = instanceCoords.arrayZ[i].to_float ();
    }
    return axisCount;
  }

  protected:
  hb_array_t<const AxisRecord> get_axes () const
  { return hb_array (&(this+firstAxis), axisCount); }

  const InstanceRecord *get_instance (unsigned int i) const
  {
    if (unlikely (i >= instanceCount)) return nullptr;
   return &StructAtOffset<InstanceRecord> (&StructAfter<InstanceRecord> (get_axes ()),
                                           i * instanceSize);
  }

  protected:
  FixedVersion<>version;        /* Version of the fvar table
                                 * initially set to 0x00010000u */
  OffsetTo<AxisRecord>
                firstAxis;      /* Offset in bytes from the beginning of the table
                                 * to the start of the AxisRecord array. */
  HBUINT16      reserved;       /* This field is permanently reserved. Set to 2. */
  HBUINT16      axisCount;      /* The number of variation axes in the font (the
                                 * number of records in the axes array). */
  HBUINT16      axisSize;       /* The size in bytes of each VariationAxisRecord —
                                 * set to 20 (0x0014) for this version. */
  HBUINT16      instanceCount;  /* The number of named instances defined in the font
                                 * (the number of records in the instances array). */
  HBUINT16      instanceSize;   /* The size in bytes of each InstanceRecord — set
                                 * to either axisCount * sizeof(Fixed) + 4, or to
                                 * axisCount * sizeof(Fixed) + 6. */

  public:
  DEFINE_SIZE_STATIC (16);
};

} /* namespace OT */


#endif /* HB_OT_VAR_FVAR_TABLE_HH */
