/*
 * Copyright © 2018  Google, Inc.
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
 * Google Author(s): Garret Rieger
 */

#ifndef HB_OT_HDMX_TABLE_HH
#define HB_OT_HDMX_TABLE_HH

#include "hb-open-type.hh"

/*
 * hdmx -- Horizontal Device Metrics
 * https://docs.microsoft.com/en-us/typography/opentype/spec/hdmx
 */
#define HB_OT_TAG_hdmx HB_TAG('h','d','m','x')


namespace OT {


struct DeviceRecord
{
  struct SubsetView
  {
    const DeviceRecord *source_device_record;
    unsigned int sizeDeviceRecord;
    hb_subset_plan_t *subset_plan;

    void init (const DeviceRecord *source_device_record,
               unsigned int sizeDeviceRecord,
               hb_subset_plan_t   *subset_plan)
    {
      this->source_device_record = source_device_record;
      this->sizeDeviceRecord = sizeDeviceRecord;
      this->subset_plan = subset_plan;
    }

    unsigned int len () const
    { return this->subset_plan->glyphs.length; }

    const HBUINT8* operator [] (unsigned int i) const
    {
      if (unlikely (i >= len ())) return nullptr;
      hb_codepoint_t gid = this->subset_plan->glyphs [i];

      if (gid >= sizeDeviceRecord - DeviceRecord::min_size)
        return nullptr;
      return &(this->source_device_record->widthsZ[gid]);
    }
  };

  static unsigned int get_size (unsigned int count)
  { return hb_ceil_to_4 (min_size + count * HBUINT8::static_size); }

  bool serialize (hb_serialize_context_t *c, const SubsetView &subset_view)
  {
    TRACE_SERIALIZE (this);

    unsigned int size = get_size (subset_view.len ());
    if (unlikely (!c->allocate_size<DeviceRecord> (size)))
    {
      DEBUG_MSG(SUBSET, nullptr, "Couldn't allocate enough space for DeviceRecord: %d.",
                 size);
      return_trace (false);
    }

    this->pixelSize.set (subset_view.source_device_record->pixelSize);
    this->maxWidth.set (subset_view.source_device_record->maxWidth);

    for (unsigned int i = 0; i < subset_view.len (); i++)
    {
      const HBUINT8 *width = subset_view[i];
      if (!width)
      {
        DEBUG_MSG(SUBSET, nullptr, "HDMX width for new gid %d is missing.", i);
        return_trace (false);
      }
      widthsZ[i].set (*width);
    }

    return_trace (true);
  }

  bool sanitize (hb_sanitize_context_t *c, unsigned int sizeDeviceRecord) const
  {
    TRACE_SANITIZE (this);
    return_trace (likely (c->check_struct (this) &&
                          c->check_range (this, sizeDeviceRecord)));
  }

  HBUINT8                       pixelSize;      /* Pixel size for following widths (as ppem). */
  HBUINT8                       maxWidth;       /* Maximum width. */
  UnsizedArrayOf<HBUINT8>       widthsZ;        /* Array of widths (numGlyphs is from the 'maxp' table). */
  public:
  DEFINE_SIZE_ARRAY (2, widthsZ);
};


struct hdmx
{
  static constexpr hb_tag_t tableTag = HB_OT_TAG_hdmx;

  unsigned int get_size () const
  { return min_size + numRecords * sizeDeviceRecord; }

  const DeviceRecord& operator [] (unsigned int i) const
  {
    /* XXX Null(DeviceRecord) is NOT safe as it's num-glyphs lengthed.
     * https://github.com/harfbuzz/harfbuzz/issues/1300 */
    if (unlikely (i >= numRecords)) return Null (DeviceRecord);
    return StructAtOffset<DeviceRecord> (&this->firstDeviceRecord, i * sizeDeviceRecord);
  }

  bool serialize (hb_serialize_context_t *c, const hdmx *source_hdmx, hb_subset_plan_t *plan)
  {
    TRACE_SERIALIZE (this);

    if (unlikely (!c->extend_min ((*this))))  return_trace (false);

    this->version.set (source_hdmx->version);
    this->numRecords.set (source_hdmx->numRecords);
    this->sizeDeviceRecord.set (DeviceRecord::get_size (plan->glyphs.length));

    for (unsigned int i = 0; i < source_hdmx->numRecords; i++)
    {
      DeviceRecord::SubsetView subset_view;
      subset_view.init (&(*source_hdmx)[i], source_hdmx->sizeDeviceRecord, plan);

      if (!c->start_embed<DeviceRecord> ()->serialize (c, subset_view))
        return_trace (false);
    }

    return_trace (true);
  }

  static size_t get_subsetted_size (const hdmx *source_hdmx, hb_subset_plan_t *plan)
  {
    return min_size + source_hdmx->numRecords * DeviceRecord::get_size (plan->glyphs.length);
  }

  bool subset (hb_subset_plan_t *plan) const
  {
    size_t dest_size = get_subsetted_size (this, plan);
    hdmx *dest = (hdmx *) malloc (dest_size);
    if (unlikely (!dest))
    {
      DEBUG_MSG(SUBSET, nullptr, "Unable to alloc %lu for hdmx subset output.", (unsigned long) dest_size);
      return false;
    }

    hb_serialize_context_t c (dest, dest_size);
    hdmx *hdmx_prime = c.start_serialize<hdmx> ();
    if (!hdmx_prime || !hdmx_prime->serialize (&c, this, plan))
    {
      free (dest);
      DEBUG_MSG(SUBSET, nullptr, "Failed to serialize write new hdmx.");
      return false;
    }
    c.end_serialize ();

    hb_blob_t *hdmx_prime_blob = hb_blob_create ((const char *) dest,
                                                 dest_size,
                                                 HB_MEMORY_MODE_READONLY,
                                                 dest,
                                                 free);
    bool result = plan->add_table (HB_OT_TAG_hdmx, hdmx_prime_blob);
    hb_blob_destroy (hdmx_prime_blob);

    return result;
  }

  bool sanitize (hb_sanitize_context_t *c) const
  {
    TRACE_SANITIZE (this);
    return_trace (c->check_struct (this) &&
                  !hb_unsigned_mul_overflows (numRecords, sizeDeviceRecord) &&
                  sizeDeviceRecord >= DeviceRecord::min_size &&
                  c->check_range (this, get_size ()));
  }

  protected:
  HBUINT16              version;                /* Table version number (0) */
  HBUINT16              numRecords;             /* Number of device records. */
  HBUINT32              sizeDeviceRecord;       /* Size of a device record, 32-bit aligned. */
  DeviceRecord          firstDeviceRecord;      /* Array of device records. */
  public:
  DEFINE_SIZE_MIN (8);
};

} /* namespace OT */


#endif /* HB_OT_HDMX_TABLE_HH */
