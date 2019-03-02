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

#ifndef HB_SUBSET_HH
#define HB_SUBSET_HH


#include "hb.hh"

#include "hb-subset.h"

#include "hb-machinery.hh"
#include "hb-subset-input.hh"
#include "hb-subset-plan.hh"

struct hb_subset_context_t :
       hb_dispatch_context_t<hb_subset_context_t, bool, HB_DEBUG_SUBSET>
{
  const char *get_name () { return "SUBSET"; }
  template <typename T>
  bool dispatch (const T &obj) { return obj.subset (this); }
  static bool default_return_value () { return true; }

  hb_subset_plan_t *plan;
  hb_serialize_context_t *serializer;
  unsigned int debug_depth;

  hb_subset_context_t (hb_subset_plan_t *plan_,
                       hb_serialize_context_t *serializer_) :
                        plan (plan_),
                        serializer (serializer_),
                        debug_depth (0) {}
};


#endif /* HB_SUBSET_HH */
