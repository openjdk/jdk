/*
 * Copyright © 2018 Adobe Inc.
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
 * Adobe Author(s): Michiharu Ariza
 */

#ifndef HB_OT_VORG_TABLE_HH
#define HB_OT_VORG_TABLE_HH

#include "hb-open-type.hh"

/*
 * VORG -- Vertical Origin Table
 * https://docs.microsoft.com/en-us/typography/opentype/spec/vorg
 */
#define HB_OT_TAG_VORG HB_TAG('V','O','R','G')

namespace OT {

struct VertOriginMetric
{
  int cmp (hb_codepoint_t g) const { return glyph.cmp (g); }

  bool sanitize (hb_sanitize_context_t *c) const
  {
    TRACE_SANITIZE (this);
    return_trace (c->check_struct (this));
  }

  public:
  GlyphID       glyph;
  FWORD         vertOriginY;

  public:
  DEFINE_SIZE_STATIC (4);
};

struct VORG
{
  static constexpr hb_tag_t tableTag = HB_OT_TAG_VORG;

  bool has_data () const { return version.to_int (); }

  int get_y_origin (hb_codepoint_t glyph) const
  {
    unsigned int i;
    if (!vertYOrigins.bfind (glyph, &i))
      return defaultVertOriginY;
    return vertYOrigins[i].vertOriginY;
  }

  bool _subset (const hb_subset_plan_t *plan HB_UNUSED,
                const VORG *vorg_table,
                const hb_vector_t<VertOriginMetric> &subset_metrics,
                unsigned int dest_sz,
                void *dest) const
  {
    hb_serialize_context_t c (dest, dest_sz);

    VORG *subset_table = c.start_serialize<VORG> ();
    if (unlikely (!c.extend_min (*subset_table)))
      return false;

    subset_table->version.major.set (1);
    subset_table->version.minor.set (0);

    subset_table->defaultVertOriginY.set (vorg_table->defaultVertOriginY);
    subset_table->vertYOrigins.len.set (subset_metrics.length);

    bool success = true;
    if (subset_metrics.length > 0)
    {
      unsigned int  size = VertOriginMetric::static_size * subset_metrics.length;
      VertOriginMetric  *metrics = c.allocate_size<VertOriginMetric> (size);
      if (likely (metrics != nullptr))
        memcpy (metrics, &subset_metrics[0], size);
      else
        success = false;
    }
    c.end_serialize ();

    return success;
  }

  bool subset (hb_subset_plan_t *plan) const
  {
    hb_blob_t *vorg_blob = hb_sanitize_context_t().reference_table<VORG> (plan->source);
    const VORG *vorg_table = vorg_blob->as<VORG> ();

    /* count the number of glyphs to be included in the subset table */
    hb_vector_t<VertOriginMetric> subset_metrics;
    subset_metrics.init ();
    unsigned int glyph = 0;
    unsigned int i = 0;
    while ((glyph < plan->glyphs.length) && (i < vertYOrigins.len))
    {
      if (plan->glyphs[glyph] > vertYOrigins[i].glyph)
        i++;
      else if (plan->glyphs[glyph] < vertYOrigins[i].glyph)
        glyph++;
      else
      {
        VertOriginMetric *metrics = subset_metrics.push ();
        metrics->glyph.set (glyph);
        metrics->vertOriginY.set (vertYOrigins[i].vertOriginY);
        glyph++;
        i++;
      }
    }

    /* alloc the new table */
    unsigned int dest_sz = VORG::min_size + VertOriginMetric::static_size * subset_metrics.length;
    void *dest = (void *) malloc (dest_sz);
    if (unlikely (!dest))
    {
      subset_metrics.fini ();
      hb_blob_destroy (vorg_blob);
      return false;
    }

    /* serialize the new table */
    if (!_subset (plan, vorg_table, subset_metrics, dest_sz, dest))
    {
      subset_metrics.fini ();
      free (dest);
      hb_blob_destroy (vorg_blob);
      return false;
    }

    hb_blob_t *result = hb_blob_create ((const char *)dest,
                                        dest_sz,
                                        HB_MEMORY_MODE_READONLY,
                                        dest,
                                        free);
    bool success = plan->add_table (HB_OT_TAG_VORG, result);
    hb_blob_destroy (result);
    subset_metrics.fini ();
    hb_blob_destroy (vorg_blob);
    return success;
  }

  bool sanitize (hb_sanitize_context_t *c) const
  {
    TRACE_SANITIZE (this);
    return_trace (c->check_struct (this) &&
                  version.major == 1 &&
                  vertYOrigins.sanitize (c));
  }

  protected:
  FixedVersion<>        version;                /* Version of VORG table. Set to 0x00010000u. */
  FWORD                 defaultVertOriginY;     /* The default vertical origin. */
  SortedArrayOf<VertOriginMetric>
                        vertYOrigins;           /* The array of vertical origins. */

  public:
  DEFINE_SIZE_ARRAY(8, vertYOrigins);
};
} /* namespace OT */

#endif /* HB_OT_VORG_TABLE_HH */
