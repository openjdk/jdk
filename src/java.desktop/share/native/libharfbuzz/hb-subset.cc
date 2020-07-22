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
 * Google Author(s): Garret Rieger, Rod Sheeter, Behdad Esfahbod
 */

#include "hb.hh"
#include "hb-open-type.hh"

#include "hb-subset.hh"
#include "hb-subset-glyf.hh"

#include "hb-open-file.hh"
#include "hb-ot-cmap-table.hh"
#include "hb-ot-glyf-table.hh"
#include "hb-ot-hdmx-table.hh"
#include "hb-ot-head-table.hh"
#include "hb-ot-hhea-table.hh"
#include "hb-ot-hmtx-table.hh"
#include "hb-ot-maxp-table.hh"
#include "hb-ot-os2-table.hh"
#include "hb-ot-post-table.hh"
#include "hb-ot-cff1-table.hh"
#include "hb-ot-cff2-table.hh"
#include "hb-ot-vorg-table.hh"
#include "hb-ot-layout-gsub-table.hh"
#include "hb-ot-layout-gpos-table.hh"


static unsigned int
_plan_estimate_subset_table_size (hb_subset_plan_t *plan,
                                  unsigned int table_len)
{
  unsigned int src_glyphs = plan->source->get_num_glyphs ();
  unsigned int dst_glyphs = plan->glyphset->get_population ();

  if (unlikely (!src_glyphs))
    return 512 + table_len;

  return 512 + (unsigned int) (table_len * sqrt ((double) dst_glyphs / src_glyphs));
}

template<typename TableType>
static bool
_subset2 (hb_subset_plan_t *plan)
{
  hb_blob_t *source_blob = hb_sanitize_context_t ().reference_table<TableType> (plan->source);
  const TableType *table = source_blob->as<TableType> ();

  hb_tag_t tag = TableType::tableTag;
  hb_bool_t result = false;
  if (source_blob->data)
  {
    hb_vector_t<char> buf;
    unsigned int buf_size = _plan_estimate_subset_table_size (plan, source_blob->length);
    DEBUG_MSG(SUBSET, nullptr, "OT::%c%c%c%c initial estimated table size: %u bytes.", HB_UNTAG (tag), buf_size);
    if (unlikely (!buf.alloc (buf_size)))
    {
      DEBUG_MSG(SUBSET, nullptr, "OT::%c%c%c%c failed to allocate %u bytes.", HB_UNTAG (tag), buf_size);
      return false;
    }
  retry:
    hb_serialize_context_t serializer ((void *) buf, buf_size);
    hb_subset_context_t c (plan, &serializer);
    result = table->subset (&c);
    if (serializer.in_error ())
    {
      buf_size += (buf_size >> 1) + 32;
      DEBUG_MSG(SUBSET, nullptr, "OT::%c%c%c%c ran out of room; reallocating to %u bytes.", HB_UNTAG (tag), buf_size);
      if (unlikely (!buf.alloc (buf_size)))
      {
        DEBUG_MSG(SUBSET, nullptr, "OT::%c%c%c%c failed to reallocate %u bytes.", HB_UNTAG (tag), buf_size);
        return false;
      }
      goto retry;
    }
    if (result)
    {
      hb_blob_t *dest_blob = serializer.copy_blob ();
      DEBUG_MSG(SUBSET, nullptr, "OT::%c%c%c%c final subset table size: %u bytes.", HB_UNTAG (tag), dest_blob->length);
      result = c.plan->add_table (tag, dest_blob);
      hb_blob_destroy (dest_blob);
    }
    else
    {
      DEBUG_MSG(SUBSET, nullptr, "OT::%c%c%c%c::subset table subsetted to empty.", HB_UNTAG (tag));
      result = true;
    }
  }
  else
    DEBUG_MSG(SUBSET, nullptr, "OT::%c%c%c%c::subset sanitize failed on source table.", HB_UNTAG (tag));

  hb_blob_destroy (source_blob);
  DEBUG_MSG(SUBSET, nullptr, "OT::%c%c%c%c::subset %s", HB_UNTAG (tag), result ? "success" : "FAILED!");
  return result;
}

template<typename TableType>
static bool
_subset (hb_subset_plan_t *plan)
{
  hb_blob_t *source_blob = hb_sanitize_context_t ().reference_table<TableType> (plan->source);
  const TableType *table = source_blob->as<TableType> ();

  hb_tag_t tag = TableType::tableTag;
  hb_bool_t result = false;
  if (source_blob->data)
    result = table->subset (plan);
  else
    DEBUG_MSG(SUBSET, nullptr, "OT::%c%c%c%c::subset sanitize failed on source table.", HB_UNTAG (tag));

  hb_blob_destroy (source_blob);
  DEBUG_MSG(SUBSET, nullptr, "OT::%c%c%c%c::subset %s", HB_UNTAG (tag), result ? "success" : "FAILED!");
  return result;
}


static bool
_subset_table (hb_subset_plan_t *plan,
               hb_tag_t          tag)
{
  DEBUG_MSG(SUBSET, nullptr, "begin subset %c%c%c%c", HB_UNTAG (tag));
  bool result = true;
  switch (tag) {
    case HB_OT_TAG_glyf:
      result = _subset<const OT::glyf> (plan);
      break;
    case HB_OT_TAG_hdmx:
      result = _subset<const OT::hdmx> (plan);
      break;
    case HB_OT_TAG_head:
      // TODO that won't work well if there is no glyf
      DEBUG_MSG(SUBSET, nullptr, "skip head, handled by glyf");
      result = true;
      break;
    case HB_OT_TAG_hhea:
      DEBUG_MSG(SUBSET, nullptr, "skip hhea handled by hmtx");
      return true;
    case HB_OT_TAG_hmtx:
      result = _subset<const OT::hmtx> (plan);
      break;
    case HB_OT_TAG_vhea:
      DEBUG_MSG(SUBSET, nullptr, "skip vhea handled by vmtx");
      return true;
    case HB_OT_TAG_vmtx:
      result = _subset<const OT::vmtx> (plan);
      break;
    case HB_OT_TAG_maxp:
      result = _subset<const OT::maxp> (plan);
      break;
    case HB_OT_TAG_loca:
      DEBUG_MSG(SUBSET, nullptr, "skip loca handled by glyf");
      return true;
    case HB_OT_TAG_cmap:
      result = _subset<const OT::cmap> (plan);
      break;
    case HB_OT_TAG_OS2:
      result = _subset<const OT::OS2> (plan);
      break;
    case HB_OT_TAG_post:
      result = _subset<const OT::post> (plan);
      break;
    case HB_OT_TAG_cff1:
      result = _subset<const OT::cff1> (plan);
      break;
    case HB_OT_TAG_cff2:
      result = _subset<const OT::cff2> (plan);
      break;
    case HB_OT_TAG_VORG:
      result = _subset<const OT::VORG> (plan);
      break;
    case HB_OT_TAG_GDEF:
      result = _subset2<const OT::GDEF> (plan);
      break;
    case HB_OT_TAG_GSUB:
      result = _subset2<const OT::GSUB> (plan);
      break;
    case HB_OT_TAG_GPOS:
      result = _subset2<const OT::GPOS> (plan);
      break;

    default:
      hb_blob_t *source_table = hb_face_reference_table (plan->source, tag);
      if (likely (source_table))
        result = plan->add_table (tag, source_table);
      else
        result = false;
      hb_blob_destroy (source_table);
      break;
  }
  DEBUG_MSG(SUBSET, nullptr, "subset %c%c%c%c %s", HB_UNTAG (tag), result ? "ok" : "FAILED");
  return result;
}

static bool
_should_drop_table (hb_subset_plan_t *plan, hb_tag_t tag)
{
  switch (tag) {
    case HB_TAG ('c', 'v', 'a', 'r'): /* hint table, fallthrough */
    case HB_TAG ('c', 'v', 't', ' '): /* hint table, fallthrough */
    case HB_TAG ('f', 'p', 'g', 'm'): /* hint table, fallthrough */
    case HB_TAG ('p', 'r', 'e', 'p'): /* hint table, fallthrough */
    case HB_TAG ('h', 'd', 'm', 'x'): /* hint table, fallthrough */
    case HB_TAG ('V', 'D', 'M', 'X'): /* hint table, fallthrough */
      return plan->drop_hints;
    // Drop Layout Tables if requested.
    case HB_OT_TAG_GDEF:
    case HB_OT_TAG_GPOS:
    case HB_OT_TAG_GSUB:
      return plan->drop_layout;
    // Drop these tables below by default, list pulled
    // from fontTools:
    case HB_TAG ('B', 'A', 'S', 'E'):
    case HB_TAG ('J', 'S', 'T', 'F'):
    case HB_TAG ('D', 'S', 'I', 'G'):
    case HB_TAG ('E', 'B', 'D', 'T'):
    case HB_TAG ('E', 'B', 'L', 'C'):
    case HB_TAG ('E', 'B', 'S', 'C'):
    case HB_TAG ('S', 'V', 'G', ' '):
    case HB_TAG ('P', 'C', 'L', 'T'):
    case HB_TAG ('L', 'T', 'S', 'H'):
    // Graphite tables:
    case HB_TAG ('F', 'e', 'a', 't'):
    case HB_TAG ('G', 'l', 'a', 't'):
    case HB_TAG ('G', 'l', 'o', 'c'):
    case HB_TAG ('S', 'i', 'l', 'f'):
    case HB_TAG ('S', 'i', 'l', 'l'):
    // Colour
    case HB_TAG ('s', 'b', 'i', 'x'):
      return true;
    default:
      return false;
  }
}

/**
 * hb_subset:
 * @source: font face data to be subset.
 * @input: input to use for the subsetting.
 *
 * Subsets a font according to provided input.
 **/
hb_face_t *
hb_subset (hb_face_t *source,
           hb_subset_input_t *input)
{
  if (unlikely (!input || !source)) return hb_face_get_empty ();

  hb_subset_plan_t *plan = hb_subset_plan_create (source, input);

  hb_tag_t table_tags[32];
  unsigned int offset = 0, count;
  bool success = true;
  do {
    count = ARRAY_LENGTH (table_tags);
    hb_face_get_table_tags (source, offset, &count, table_tags);
    for (unsigned int i = 0; i < count; i++)
    {
      hb_tag_t tag = table_tags[i];
      if (_should_drop_table (plan, tag))
      {
        DEBUG_MSG(SUBSET, nullptr, "drop %c%c%c%c", HB_UNTAG (tag));
        continue;
      }
      success = success && _subset_table (plan, tag);
    }
    offset += count;
  } while (success && count == ARRAY_LENGTH (table_tags));

  hb_face_t *result = success ? hb_face_reference (plan->dest) : hb_face_get_empty ();
  hb_subset_plan_destroy (plan);
  return result;
}
