/*
 * Copyright © 2015  Google, Inc.
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

#ifndef HB_OT_GLYF_TABLE_HH
#define HB_OT_GLYF_TABLE_HH

#include "hb-open-type-private.hh"


namespace OT {


/*
 * loca -- Index to Location
 */

#define HB_OT_TAG_loca HB_TAG('l','o','c','a')


struct loca
{
  static const hb_tag_t tableTag = HB_OT_TAG_loca;

  inline bool sanitize (hb_sanitize_context_t *c) const
  {
    TRACE_SANITIZE (this);
    return_trace (true);
  }

  public:
  union {
    USHORT      shortsZ[VAR];           /* Location offset divided by 2. */
    ULONG       longsZ[VAR];            /* Location offset. */
  } u;
  DEFINE_SIZE_ARRAY (0, u.longsZ);
};


/*
 * glyf -- TrueType Glyph Data
 */

#define HB_OT_TAG_glyf HB_TAG('g','l','y','f')


struct glyf
{
  static const hb_tag_t tableTag = HB_OT_TAG_glyf;

  inline bool sanitize (hb_sanitize_context_t *c) const
  {
    TRACE_SANITIZE (this);
    /* We don't check for anything specific here.  The users of the
     * struct do all the hard work... */
    return_trace (true);
  }

  public:
  BYTE          dataX[VAR];             /* Glyphs data. */

  DEFINE_SIZE_ARRAY (0, dataX);
};

struct glyfGlyphHeader
{
  SHORT         numberOfContours;       /* If the number of contours is
                                         * greater than or equal to zero,
                                         * this is a simple glyph; if negative,
                                         * this is a composite glyph. */
  SHORT         xMin;                   /* Minimum x for coordinate data. */
  SHORT         yMin;                   /* Minimum y for coordinate data. */
  SHORT         xMax;                   /* Maximum x for coordinate data. */
  SHORT         yMax;                   /* Maximum y for coordinate data. */

  DEFINE_SIZE_STATIC (10);
};

} /* namespace OT */


#endif /* HB_OT_GLYF_TABLE_HH */
