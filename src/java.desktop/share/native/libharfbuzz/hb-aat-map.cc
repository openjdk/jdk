/*
 * Copyright © 2009,2010  Red Hat, Inc.
 * Copyright © 2010,2011,2013  Google, Inc.
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

#include "hb-aat-map.hh"

#include "hb-aat-layout.hh"


void hb_aat_map_builder_t::add_feature (hb_tag_t tag,
                                        unsigned int value)
{
  if (tag == HB_TAG ('a','a','l','t'))
  {
    feature_info_t *info = features.push();
    info->type = HB_AAT_LAYOUT_FEATURE_TYPE_CHARACTER_ALTERNATIVES;
    info->setting = (hb_aat_layout_feature_selector_t) value;
    return;
  }

  const hb_aat_feature_mapping_t *mapping = hb_aat_layout_find_feature_mapping (tag);
  if (!mapping) return;

  feature_info_t *info = features.push();
  info->type = mapping->aatFeatureType;
  info->setting = value ? mapping->selectorToEnable : mapping->selectorToDisable;
}

void
hb_aat_map_builder_t::compile (hb_aat_map_t  &m)
{
  /* Sort features and merge duplicates */
  if (features.length)
  {
    features.qsort ();
    unsigned int j = 0;
    for (unsigned int i = 1; i < features.length; i++)
      if (features[i].type != features[j].type)
        features[++j] = features[i];
    features.shrink (j + 1);
  }

  hb_aat_layout_compile_map (this, &m);
}
