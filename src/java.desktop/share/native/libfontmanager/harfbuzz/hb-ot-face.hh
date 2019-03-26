/*
 * Copyright © 2007,2008,2009  Red Hat, Inc.
 * Copyright © 2012,2013  Google, Inc.
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

#ifndef HB_OT_FACE_HH
#define HB_OT_FACE_HH

#include "hb.hh"

#include "hb-machinery.hh"


/*
 * hb_ot_face_t
 */

#define HB_OT_TABLES \
    /* OpenType fundamentals. */ \
    HB_OT_TABLE(OT, head) \
    HB_OT_ACCELERATOR(OT, cmap) \
    HB_OT_ACCELERATOR(OT, hmtx) \
    HB_OT_ACCELERATOR(OT, vmtx) \
    HB_OT_ACCELERATOR(OT, post) \
    HB_OT_TABLE(OT, kern) \
    HB_OT_ACCELERATOR(OT, glyf) \
    HB_OT_ACCELERATOR(OT, cff1) \
    HB_OT_ACCELERATOR(OT, cff2) \
    HB_OT_TABLE(OT, VORG) \
    HB_OT_ACCELERATOR(OT, name) \
    HB_OT_TABLE(OT, OS2) \
    HB_OT_TABLE(OT, STAT) \
    /* OpenType shaping. */ \
    HB_OT_ACCELERATOR(OT, GDEF) \
    HB_OT_ACCELERATOR(OT, GSUB) \
    HB_OT_ACCELERATOR(OT, GPOS) \
    HB_OT_TABLE(OT, BASE) \
    HB_OT_TABLE(OT, JSTF) \
    /* AAT shaping. */ \
    HB_OT_TABLE(AAT, mort) \
    HB_OT_TABLE(AAT, morx) \
    HB_OT_TABLE(AAT, kerx) \
    HB_OT_TABLE(AAT, ankr) \
    HB_OT_TABLE(AAT, trak) \
    HB_OT_TABLE(AAT, lcar) \
    HB_OT_TABLE(AAT, ltag) \
    HB_OT_TABLE(AAT, feat) \
    /* OpenType variations. */ \
    HB_OT_TABLE(OT, fvar) \
    HB_OT_TABLE(OT, avar) \
    HB_OT_TABLE(OT, MVAR) \
    /* OpenType math. */ \
    HB_OT_TABLE(OT, MATH) \
    /* OpenType color fonts. */ \
    HB_OT_TABLE(OT, COLR) \
    HB_OT_TABLE(OT, CPAL) \
    HB_OT_ACCELERATOR(OT, CBDT) \
    HB_OT_ACCELERATOR(OT, sbix) \
    HB_OT_ACCELERATOR(OT, SVG) \
    /* */

/* Declare tables. */
#define HB_OT_TABLE(Namespace, Type) namespace Namespace { struct Type; }
#define HB_OT_ACCELERATOR(Namespace, Type) HB_OT_TABLE (Namespace, Type##_accelerator_t)
HB_OT_TABLES
#undef HB_OT_ACCELERATOR
#undef HB_OT_TABLE

struct hb_ot_face_t
{
  HB_INTERNAL void init0 (hb_face_t *face);
  HB_INTERNAL void fini ();

#define HB_OT_TABLE_ORDER(Namespace, Type) \
    HB_PASTE (ORDER_, HB_PASTE (Namespace, HB_PASTE (_, Type)))
  enum order_t
  {
    ORDER_ZERO,
#define HB_OT_TABLE(Namespace, Type) HB_OT_TABLE_ORDER (Namespace, Type),
#define HB_OT_ACCELERATOR(Namespace, Type) HB_OT_TABLE (Namespace, Type)
    HB_OT_TABLES
#undef HB_OT_ACCELERATOR
#undef HB_OT_TABLE
  };

  hb_face_t *face; /* MUST be JUST before the lazy loaders. */
#define HB_OT_TABLE(Namespace, Type) \
  hb_table_lazy_loader_t<Namespace::Type, HB_OT_TABLE_ORDER (Namespace, Type)> Type;
#define HB_OT_ACCELERATOR(Namespace, Type) \
  hb_face_lazy_loader_t<Namespace::Type##_accelerator_t, HB_OT_TABLE_ORDER (Namespace, Type)> Type;
  HB_OT_TABLES
#undef HB_OT_ACCELERATOR
#undef HB_OT_TABLE
};


#endif /* HB_OT_FACE_HH */
