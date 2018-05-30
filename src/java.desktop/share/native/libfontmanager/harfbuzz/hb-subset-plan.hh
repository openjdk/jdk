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
 * Google Author(s): Garret Rieger, Roderick Sheeter
 */

#ifndef HB_SUBSET_PLAN_HH
#define HB_SUBSET_PLAN_HH

#include "hb-private.hh"

#include "hb-subset.h"

#include "hb-object-private.hh"

struct hb_subset_plan_t {
  hb_object_header_t header;
  ASSERT_POD ();

  hb_bool_t drop_hints;

  // TODO(Q1) actual map, drop this crap
  // Look at me ma, I'm a poor mans map codepoint : new gid
  // codepoints is sorted and aligned with gids_to_retain.

  // These first two lists provide a mapping from cp -> gid
  // As a result it does not list the full set of glyphs to retain.
  hb_prealloced_array_t<hb_codepoint_t> codepoints;
  hb_prealloced_array_t<hb_codepoint_t> gids_to_retain;

  // This list contains the complete set of glyphs to retain and may contain
  // more glyphs then the lists above.
  hb_prealloced_array_t<hb_codepoint_t> gids_to_retain_sorted;

  // Plan is only good for a specific source/dest so keep them with it
  hb_face_t *source;
  hb_face_t *dest;
};

typedef struct hb_subset_plan_t hb_subset_plan_t;

HB_INTERNAL hb_subset_plan_t *
hb_subset_plan_create (hb_face_t           *face,
                       hb_subset_profile_t *profile,
                       hb_subset_input_t   *input);

HB_INTERNAL hb_bool_t
hb_subset_plan_new_gid_for_old_id(hb_subset_plan_t *plan,
                                  hb_codepoint_t old_gid,
                                  hb_codepoint_t *new_gid /* OUT */);

HB_INTERNAL hb_bool_t
hb_subset_plan_new_gid_for_codepoint(hb_subset_plan_t *plan,
                                     hb_codepoint_t codepont,
                                     hb_codepoint_t *new_gid /* OUT */);

HB_INTERNAL hb_bool_t
hb_subset_plan_add_table(hb_subset_plan_t *plan,
                         hb_tag_t tag,
                         hb_blob_t *contents);

HB_INTERNAL void
hb_subset_plan_destroy (hb_subset_plan_t *plan);

#endif /* HB_SUBSET_PLAN_HH */
