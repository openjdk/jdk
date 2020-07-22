/*
 * Copyright © 2007,2008,2009,2010  Red Hat, Inc.
 * Copyright © 2010,2012,2013  Google, Inc.
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

#ifndef HB_OT_LAYOUT_GSUB_TABLE_HH
#define HB_OT_LAYOUT_GSUB_TABLE_HH

#include "hb-ot-layout-gsubgpos.hh"


namespace OT {


static inline void SingleSubst_serialize (hb_serialize_context_t *c,
                                          hb_array_t<const GlyphID> glyphs,
                                          hb_array_t<const GlyphID> substitutes);

struct SingleSubstFormat1
{
  bool intersects (const hb_set_t *glyphs) const
  { return (this+coverage).intersects (glyphs); }

  void closure (hb_closure_context_t *c) const
  {
    for (Coverage::Iter iter (this+coverage); iter.more (); iter.next ())
    {
      /* TODO Switch to range-based API to work around malicious fonts.
       * https://github.com/harfbuzz/harfbuzz/issues/363 */
      hb_codepoint_t glyph_id = iter.get_glyph ();
      if (c->glyphs->has (glyph_id))
        c->out->add ((glyph_id + deltaGlyphID) & 0xFFFFu);
    }
  }

  void collect_glyphs (hb_collect_glyphs_context_t *c) const
  {
    if (unlikely (!(this+coverage).add_coverage (c->input))) return;
    for (Coverage::Iter iter (this+coverage); iter.more (); iter.next ())
    {
      /* TODO Switch to range-based API to work around malicious fonts.
       * https://github.com/harfbuzz/harfbuzz/issues/363 */
      hb_codepoint_t glyph_id = iter.get_glyph ();
      c->output->add ((glyph_id + deltaGlyphID) & 0xFFFFu);
    }
  }

  const Coverage &get_coverage () const { return this+coverage; }

  bool would_apply (hb_would_apply_context_t *c) const
  {
    TRACE_WOULD_APPLY (this);
    return_trace (c->len == 1 && (this+coverage).get_coverage (c->glyphs[0]) != NOT_COVERED);
  }

  bool apply (hb_ot_apply_context_t *c) const
  {
    TRACE_APPLY (this);
    hb_codepoint_t glyph_id = c->buffer->cur().codepoint;
    unsigned int index = (this+coverage).get_coverage (glyph_id);
    if (likely (index == NOT_COVERED)) return_trace (false);

    /* According to the Adobe Annotated OpenType Suite, result is always
     * limited to 16bit. */
    glyph_id = (glyph_id + deltaGlyphID) & 0xFFFFu;
    c->replace_glyph (glyph_id);

    return_trace (true);
  }

  bool serialize (hb_serialize_context_t *c,
                  hb_array_t<const GlyphID> glyphs,
                  int delta)
  {
    TRACE_SERIALIZE (this);
    if (unlikely (!c->extend_min (*this))) return_trace (false);
    if (unlikely (!coverage.serialize (c, this).serialize (c, glyphs))) return_trace (false);
    deltaGlyphID.set (delta); /* TODO(serialize) overflow? */
    return_trace (true);
  }

  bool subset (hb_subset_context_t *c) const
  {
    TRACE_SUBSET (this);
    const hb_set_t &glyphset = *c->plan->glyphset;
    const hb_map_t &glyph_map = *c->plan->glyph_map;
    hb_vector_t<GlyphID> from;
    hb_vector_t<GlyphID> to;
    hb_codepoint_t delta = deltaGlyphID;
    for (Coverage::Iter iter (this+coverage); iter.more (); iter.next ())
    {
      if (!glyphset.has (iter.get_glyph ())) continue;
      from.push ()->set (glyph_map[iter.get_glyph ()]);
      to.push ()->set (glyph_map[(iter.get_glyph () + delta) & 0xFFFF]);
    }
    c->serializer->propagate_error (from, to);
    SingleSubst_serialize (c->serializer, from, to);
    return_trace (from.length);
  }

  bool sanitize (hb_sanitize_context_t *c) const
  {
    TRACE_SANITIZE (this);
    return_trace (coverage.sanitize (c, this) && deltaGlyphID.sanitize (c));
  }

  protected:
  HBUINT16      format;                 /* Format identifier--format = 1 */
  OffsetTo<Coverage>
                coverage;               /* Offset to Coverage table--from
                                         * beginning of Substitution table */
  HBINT16       deltaGlyphID;           /* Add to original GlyphID to get
                                         * substitute GlyphID */
  public:
  DEFINE_SIZE_STATIC (6);
};

struct SingleSubstFormat2
{
  bool intersects (const hb_set_t *glyphs) const
  { return (this+coverage).intersects (glyphs); }

  void closure (hb_closure_context_t *c) const
  {
    unsigned int count = substitute.len;
    for (Coverage::Iter iter (this+coverage); iter.more (); iter.next ())
    {
      if (unlikely (iter.get_coverage () >= count))
        break; /* Work around malicious fonts. https://github.com/harfbuzz/harfbuzz/issues/363 */
      if (c->glyphs->has (iter.get_glyph ()))
        c->out->add (substitute[iter.get_coverage ()]);
    }
  }

  void collect_glyphs (hb_collect_glyphs_context_t *c) const
  {
    if (unlikely (!(this+coverage).add_coverage (c->input))) return;
    unsigned int count = substitute.len;
    for (Coverage::Iter iter (this+coverage); iter.more (); iter.next ())
    {
      if (unlikely (iter.get_coverage () >= count))
        break; /* Work around malicious fonts. https://github.com/harfbuzz/harfbuzz/issues/363 */
      c->output->add (substitute[iter.get_coverage ()]);
    }
  }

  const Coverage &get_coverage () const { return this+coverage; }

  bool would_apply (hb_would_apply_context_t *c) const
  {
    TRACE_WOULD_APPLY (this);
    return_trace (c->len == 1 && (this+coverage).get_coverage (c->glyphs[0]) != NOT_COVERED);
  }

  bool apply (hb_ot_apply_context_t *c) const
  {
    TRACE_APPLY (this);
    unsigned int index = (this+coverage).get_coverage (c->buffer->cur().codepoint);
    if (likely (index == NOT_COVERED)) return_trace (false);

    if (unlikely (index >= substitute.len)) return_trace (false);

    c->replace_glyph (substitute[index]);

    return_trace (true);
  }

  bool serialize (hb_serialize_context_t *c,
                  hb_array_t<const GlyphID> glyphs,
                  hb_array_t<const GlyphID> substitutes)
  {
    TRACE_SERIALIZE (this);
    if (unlikely (!c->extend_min (*this))) return_trace (false);
    if (unlikely (!substitute.serialize (c, substitutes))) return_trace (false);
    if (unlikely (!coverage.serialize (c, this).serialize (c, glyphs))) return_trace (false);
    return_trace (true);
  }

  bool subset (hb_subset_context_t *c) const
  {
    TRACE_SUBSET (this);
    const hb_set_t &glyphset = *c->plan->glyphset;
    const hb_map_t &glyph_map = *c->plan->glyph_map;
    hb_vector_t<GlyphID> from;
    hb_vector_t<GlyphID> to;
    for (Coverage::Iter iter (this+coverage); iter.more (); iter.next ())
    {
      if (!glyphset.has (iter.get_glyph ())) continue;
      from.push ()->set (glyph_map[iter.get_glyph ()]);
      to.push ()->set (glyph_map[substitute[iter.get_coverage ()]]);
    }
    c->serializer->propagate_error (from, to);
    SingleSubst_serialize (c->serializer, from, to);
    return_trace (from.length);
  }

  bool sanitize (hb_sanitize_context_t *c) const
  {
    TRACE_SANITIZE (this);
    return_trace (coverage.sanitize (c, this) && substitute.sanitize (c));
  }

  protected:
  HBUINT16      format;                 /* Format identifier--format = 2 */
  OffsetTo<Coverage>
                coverage;               /* Offset to Coverage table--from
                                         * beginning of Substitution table */
  ArrayOf<GlyphID>
                substitute;             /* Array of substitute
                                         * GlyphIDs--ordered by Coverage Index */
  public:
  DEFINE_SIZE_ARRAY (6, substitute);
};

struct SingleSubst
{
  bool serialize (hb_serialize_context_t *c,
                  hb_array_t<const GlyphID> glyphs,
                  hb_array_t<const GlyphID> substitutes)
  {
    TRACE_SERIALIZE (this);
    if (unlikely (!c->extend_min (u.format))) return_trace (false);
    unsigned int format = 2;
    int delta = 0;
    if (glyphs.length)
    {
      format = 1;
      /* TODO(serialize) check for wrap-around */
      delta = substitutes[0] - glyphs[0];
      for (unsigned int i = 1; i < glyphs.length; i++)
        if (delta != (int) (substitutes[i] - glyphs[i])) {
          format = 2;
          break;
        }
    }
    u.format.set (format);
    switch (u.format) {
    case 1: return_trace (u.format1.serialize (c, glyphs, delta));
    case 2: return_trace (u.format2.serialize (c, glyphs, substitutes));
    default:return_trace (false);
    }
  }

  template <typename context_t>
  typename context_t::return_t dispatch (context_t *c) const
  {
    TRACE_DISPATCH (this, u.format);
    if (unlikely (!c->may_dispatch (this, &u.format))) return_trace (c->no_dispatch_return_value ());
    switch (u.format) {
    case 1: return_trace (c->dispatch (u.format1));
    case 2: return_trace (c->dispatch (u.format2));
    default:return_trace (c->default_return_value ());
    }
  }

  protected:
  union {
  HBUINT16              format;         /* Format identifier */
  SingleSubstFormat1    format1;
  SingleSubstFormat2    format2;
  } u;
};

static inline void
SingleSubst_serialize (hb_serialize_context_t *c,
                       hb_array_t<const GlyphID> glyphs,
                       hb_array_t<const GlyphID> substitutes)
{ c->start_embed<SingleSubst> ()->serialize (c, glyphs, substitutes); }

struct Sequence
{
  void closure (hb_closure_context_t *c) const
  {
    unsigned int count = substitute.len;
    for (unsigned int i = 0; i < count; i++)
      c->out->add (substitute[i]);
  }

  void collect_glyphs (hb_collect_glyphs_context_t *c) const
  { c->output->add_array (substitute.arrayZ, substitute.len); }

  bool apply (hb_ot_apply_context_t *c) const
  {
    TRACE_APPLY (this);
    unsigned int count = substitute.len;

    /* Special-case to make it in-place and not consider this
     * as a "multiplied" substitution. */
    if (unlikely (count == 1))
    {
      c->replace_glyph (substitute.arrayZ[0]);
      return_trace (true);
    }
    /* Spec disallows this, but Uniscribe allows it.
     * https://github.com/harfbuzz/harfbuzz/issues/253 */
    else if (unlikely (count == 0))
    {
      c->buffer->delete_glyph ();
      return_trace (true);
    }

    unsigned int klass = _hb_glyph_info_is_ligature (&c->buffer->cur()) ?
                         HB_OT_LAYOUT_GLYPH_PROPS_BASE_GLYPH : 0;

    for (unsigned int i = 0; i < count; i++) {
      _hb_glyph_info_set_lig_props_for_component (&c->buffer->cur(), i);
      c->output_glyph_for_component (substitute.arrayZ[i], klass);
    }
    c->buffer->skip_glyph ();

    return_trace (true);
  }

  bool serialize (hb_serialize_context_t *c,
                  hb_array_t<const GlyphID> glyphs)
  {
    TRACE_SERIALIZE (this);
    return_trace (substitute.serialize (c, glyphs));
  }

  bool sanitize (hb_sanitize_context_t *c) const
  {
    TRACE_SANITIZE (this);
    return_trace (substitute.sanitize (c));
  }

  protected:
  ArrayOf<GlyphID>
                substitute;             /* String of GlyphIDs to substitute */
  public:
  DEFINE_SIZE_ARRAY (2, substitute);
};

struct MultipleSubstFormat1
{
  bool intersects (const hb_set_t *glyphs) const
  { return (this+coverage).intersects (glyphs); }

  void closure (hb_closure_context_t *c) const
  {
    unsigned int count = sequence.len;
    for (Coverage::Iter iter (this+coverage); iter.more (); iter.next ())
    {
      if (unlikely (iter.get_coverage () >= count))
        break; /* Work around malicious fonts. https://github.com/harfbuzz/harfbuzz/issues/363 */
      if (c->glyphs->has (iter.get_glyph ()))
        (this+sequence[iter.get_coverage ()]).closure (c);
    }
  }

  void collect_glyphs (hb_collect_glyphs_context_t *c) const
  {
    if (unlikely (!(this+coverage).add_coverage (c->input))) return;
    unsigned int count = sequence.len;
    for (unsigned int i = 0; i < count; i++)
      (this+sequence[i]).collect_glyphs (c);
  }

  const Coverage &get_coverage () const { return this+coverage; }

  bool would_apply (hb_would_apply_context_t *c) const
  {
    TRACE_WOULD_APPLY (this);
    return_trace (c->len == 1 && (this+coverage).get_coverage (c->glyphs[0]) != NOT_COVERED);
  }

  bool apply (hb_ot_apply_context_t *c) const
  {
    TRACE_APPLY (this);

    unsigned int index = (this+coverage).get_coverage (c->buffer->cur().codepoint);
    if (likely (index == NOT_COVERED)) return_trace (false);

    return_trace ((this+sequence[index]).apply (c));
  }

  bool serialize (hb_serialize_context_t *c,
                  hb_array_t<const GlyphID> glyphs,
                  hb_array_t<const unsigned int> substitute_len_list,
                  hb_array_t<const GlyphID> substitute_glyphs_list)
  {
    TRACE_SERIALIZE (this);
    if (unlikely (!c->extend_min (*this))) return_trace (false);
    if (unlikely (!sequence.serialize (c, glyphs.length))) return_trace (false);
    for (unsigned int i = 0; i < glyphs.length; i++)
    {
      unsigned int substitute_len = substitute_len_list[i];
      if (unlikely (!sequence[i].serialize (c, this)
                                .serialize (c, substitute_glyphs_list.sub_array (0, substitute_len))))
        return_trace (false);
      substitute_glyphs_list += substitute_len;
    }
    return_trace (coverage.serialize (c, this).serialize (c, glyphs));
  }

  bool subset (hb_subset_context_t *c) const
  {
    TRACE_SUBSET (this);
    // TODO(subset)
    return_trace (false);
  }

  bool sanitize (hb_sanitize_context_t *c) const
  {
    TRACE_SANITIZE (this);
    return_trace (coverage.sanitize (c, this) && sequence.sanitize (c, this));
  }

  protected:
  HBUINT16      format;                 /* Format identifier--format = 1 */
  OffsetTo<Coverage>
                coverage;               /* Offset to Coverage table--from
                                         * beginning of Substitution table */
  OffsetArrayOf<Sequence>
                sequence;               /* Array of Sequence tables
                                         * ordered by Coverage Index */
  public:
  DEFINE_SIZE_ARRAY (6, sequence);
};

struct MultipleSubst
{
  bool serialize (hb_serialize_context_t *c,
                  hb_array_t<const GlyphID> glyphs,
                  hb_array_t<const unsigned int> substitute_len_list,
                  hb_array_t<const GlyphID> substitute_glyphs_list)
  {
    TRACE_SERIALIZE (this);
    if (unlikely (!c->extend_min (u.format))) return_trace (false);
    unsigned int format = 1;
    u.format.set (format);
    switch (u.format) {
    case 1: return_trace (u.format1.serialize (c, glyphs, substitute_len_list, substitute_glyphs_list));
    default:return_trace (false);
    }
  }

  template <typename context_t>
  typename context_t::return_t dispatch (context_t *c) const
  {
    TRACE_DISPATCH (this, u.format);
    if (unlikely (!c->may_dispatch (this, &u.format))) return_trace (c->no_dispatch_return_value ());
    switch (u.format) {
    case 1: return_trace (c->dispatch (u.format1));
    default:return_trace (c->default_return_value ());
    }
  }

  protected:
  union {
  HBUINT16              format;         /* Format identifier */
  MultipleSubstFormat1  format1;
  } u;
};

struct AlternateSet
{
  void closure (hb_closure_context_t *c) const
  {
    unsigned int count = alternates.len;
    for (unsigned int i = 0; i < count; i++)
      c->out->add (alternates[i]);
  }

  void collect_glyphs (hb_collect_glyphs_context_t *c) const
  { c->output->add_array (alternates.arrayZ, alternates.len); }

  bool apply (hb_ot_apply_context_t *c) const
  {
    TRACE_APPLY (this);
    unsigned int count = alternates.len;

    if (unlikely (!count)) return_trace (false);

    hb_mask_t glyph_mask = c->buffer->cur().mask;
    hb_mask_t lookup_mask = c->lookup_mask;

    /* Note: This breaks badly if two features enabled this lookup together. */
    unsigned int shift = hb_ctz (lookup_mask);
    unsigned int alt_index = ((lookup_mask & glyph_mask) >> shift);

    /* If alt_index is MAX, randomize feature if it is the rand feature. */
    if (alt_index == HB_OT_MAP_MAX_VALUE && c->random)
      alt_index = c->random_number () % count + 1;

    if (unlikely (alt_index > count || alt_index == 0)) return_trace (false);

    c->replace_glyph (alternates[alt_index - 1]);

    return_trace (true);
  }

  bool serialize (hb_serialize_context_t *c,
                  hb_array_t<const GlyphID> glyphs)
  {
    TRACE_SERIALIZE (this);
    return_trace (alternates.serialize (c, glyphs));
  }

  bool sanitize (hb_sanitize_context_t *c) const
  {
    TRACE_SANITIZE (this);
    return_trace (alternates.sanitize (c));
  }

  protected:
  ArrayOf<GlyphID>
                alternates;             /* Array of alternate GlyphIDs--in
                                         * arbitrary order */
  public:
  DEFINE_SIZE_ARRAY (2, alternates);
};

struct AlternateSubstFormat1
{
  bool intersects (const hb_set_t *glyphs) const
  { return (this+coverage).intersects (glyphs); }

  void closure (hb_closure_context_t *c) const
  {
    unsigned int count = alternateSet.len;
    for (Coverage::Iter iter (this+coverage); iter.more (); iter.next ())
    {
      if (unlikely (iter.get_coverage () >= count))
        break; /* Work around malicious fonts. https://github.com/harfbuzz/harfbuzz/issues/363 */
      if (c->glyphs->has (iter.get_glyph ()))
        (this+alternateSet[iter.get_coverage ()]).closure (c);
    }
  }

  void collect_glyphs (hb_collect_glyphs_context_t *c) const
  {
    if (unlikely (!(this+coverage).add_coverage (c->input))) return;
    unsigned int count = alternateSet.len;
    for (Coverage::Iter iter (this+coverage); iter.more (); iter.next ())
    {
      if (unlikely (iter.get_coverage () >= count))
        break; /* Work around malicious fonts. https://github.com/harfbuzz/harfbuzz/issues/363 */
      (this+alternateSet[iter.get_coverage ()]).collect_glyphs (c);
    }
  }

  const Coverage &get_coverage () const { return this+coverage; }

  bool would_apply (hb_would_apply_context_t *c) const
  {
    TRACE_WOULD_APPLY (this);
    return_trace (c->len == 1 && (this+coverage).get_coverage (c->glyphs[0]) != NOT_COVERED);
  }

  bool apply (hb_ot_apply_context_t *c) const
  {
    TRACE_APPLY (this);

    unsigned int index = (this+coverage).get_coverage (c->buffer->cur().codepoint);
    if (likely (index == NOT_COVERED)) return_trace (false);

    return_trace ((this+alternateSet[index]).apply (c));
  }

  bool serialize (hb_serialize_context_t *c,
                  hb_array_t<const GlyphID> glyphs,
                  hb_array_t<const unsigned int> alternate_len_list,
                  hb_array_t<const GlyphID> alternate_glyphs_list)
  {
    TRACE_SERIALIZE (this);
    if (unlikely (!c->extend_min (*this))) return_trace (false);
    if (unlikely (!alternateSet.serialize (c, glyphs.length))) return_trace (false);
    for (unsigned int i = 0; i < glyphs.length; i++)
    {
      unsigned int alternate_len = alternate_len_list[i];
      if (unlikely (!alternateSet[i].serialize (c, this)
                                    .serialize (c, alternate_glyphs_list.sub_array (0, alternate_len))))
        return_trace (false);
      alternate_glyphs_list += alternate_len;
    }
    return_trace (coverage.serialize (c, this).serialize (c, glyphs));
  }

  bool subset (hb_subset_context_t *c) const
  {
    TRACE_SUBSET (this);
    // TODO(subset)
    return_trace (false);
  }

  bool sanitize (hb_sanitize_context_t *c) const
  {
    TRACE_SANITIZE (this);
    return_trace (coverage.sanitize (c, this) && alternateSet.sanitize (c, this));
  }

  protected:
  HBUINT16      format;                 /* Format identifier--format = 1 */
  OffsetTo<Coverage>
                coverage;               /* Offset to Coverage table--from
                                         * beginning of Substitution table */
  OffsetArrayOf<AlternateSet>
                alternateSet;           /* Array of AlternateSet tables
                                         * ordered by Coverage Index */
  public:
  DEFINE_SIZE_ARRAY (6, alternateSet);
};

struct AlternateSubst
{
  bool serialize (hb_serialize_context_t *c,
                  hb_array_t<const GlyphID> glyphs,
                  hb_array_t<const unsigned int> alternate_len_list,
                  hb_array_t<const GlyphID> alternate_glyphs_list)
  {
    TRACE_SERIALIZE (this);
    if (unlikely (!c->extend_min (u.format))) return_trace (false);
    unsigned int format = 1;
    u.format.set (format);
    switch (u.format) {
    case 1: return_trace (u.format1.serialize (c, glyphs, alternate_len_list, alternate_glyphs_list));
    default:return_trace (false);
    }
  }

  template <typename context_t>
  typename context_t::return_t dispatch (context_t *c) const
  {
    TRACE_DISPATCH (this, u.format);
    if (unlikely (!c->may_dispatch (this, &u.format))) return_trace (c->no_dispatch_return_value ());
    switch (u.format) {
    case 1: return_trace (c->dispatch (u.format1));
    default:return_trace (c->default_return_value ());
    }
  }

  protected:
  union {
  HBUINT16              format;         /* Format identifier */
  AlternateSubstFormat1 format1;
  } u;
};


struct Ligature
{
  bool intersects (const hb_set_t *glyphs) const
  {
    unsigned int count = component.lenP1;
    for (unsigned int i = 1; i < count; i++)
      if (!glyphs->has (component[i]))
        return false;
    return true;
  }

  void closure (hb_closure_context_t *c) const
  {
    unsigned int count = component.lenP1;
    for (unsigned int i = 1; i < count; i++)
      if (!c->glyphs->has (component[i]))
        return;
    c->out->add (ligGlyph);
  }

  void collect_glyphs (hb_collect_glyphs_context_t *c) const
  {
    c->input->add_array (component.arrayZ, component.lenP1 ? component.lenP1 - 1 : 0);
    c->output->add (ligGlyph);
  }

  bool would_apply (hb_would_apply_context_t *c) const
  {
    TRACE_WOULD_APPLY (this);
    if (c->len != component.lenP1)
      return_trace (false);

    for (unsigned int i = 1; i < c->len; i++)
      if (likely (c->glyphs[i] != component[i]))
        return_trace (false);

    return_trace (true);
  }

  bool apply (hb_ot_apply_context_t *c) const
  {
    TRACE_APPLY (this);
    unsigned int count = component.lenP1;

    if (unlikely (!count)) return_trace (false);

    /* Special-case to make it in-place and not consider this
     * as a "ligated" substitution. */
    if (unlikely (count == 1))
    {
      c->replace_glyph (ligGlyph);
      return_trace (true);
    }

    unsigned int total_component_count = 0;

    unsigned int match_length = 0;
    unsigned int match_positions[HB_MAX_CONTEXT_LENGTH];

    if (likely (!match_input (c, count,
                              &component[1],
                              match_glyph,
                              nullptr,
                              &match_length,
                              match_positions,
                              &total_component_count)))
      return_trace (false);

    ligate_input (c,
                  count,
                  match_positions,
                  match_length,
                  ligGlyph,
                  total_component_count);

    return_trace (true);
  }

  bool serialize (hb_serialize_context_t *c,
                  GlyphID ligature,
                  hb_array_t<const GlyphID> components /* Starting from second */)
  {
    TRACE_SERIALIZE (this);
    if (unlikely (!c->extend_min (*this))) return_trace (false);
    ligGlyph = ligature;
    if (unlikely (!component.serialize (c, components))) return_trace (false);
    return_trace (true);
  }

  public:
  bool sanitize (hb_sanitize_context_t *c) const
  {
    TRACE_SANITIZE (this);
    return_trace (ligGlyph.sanitize (c) && component.sanitize (c));
  }

  protected:
  GlyphID       ligGlyph;               /* GlyphID of ligature to substitute */
  HeadlessArrayOf<GlyphID>
                component;              /* Array of component GlyphIDs--start
                                         * with the second  component--ordered
                                         * in writing direction */
  public:
  DEFINE_SIZE_ARRAY (4, component);
};

struct LigatureSet
{
  bool intersects (const hb_set_t *glyphs) const
  {
    unsigned int num_ligs = ligature.len;
    for (unsigned int i = 0; i < num_ligs; i++)
      if ((this+ligature[i]).intersects (glyphs))
        return true;
    return false;
  }

  void closure (hb_closure_context_t *c) const
  {
    unsigned int num_ligs = ligature.len;
    for (unsigned int i = 0; i < num_ligs; i++)
      (this+ligature[i]).closure (c);
  }

  void collect_glyphs (hb_collect_glyphs_context_t *c) const
  {
    unsigned int num_ligs = ligature.len;
    for (unsigned int i = 0; i < num_ligs; i++)
      (this+ligature[i]).collect_glyphs (c);
  }

  bool would_apply (hb_would_apply_context_t *c) const
  {
    TRACE_WOULD_APPLY (this);
    unsigned int num_ligs = ligature.len;
    for (unsigned int i = 0; i < num_ligs; i++)
    {
      const Ligature &lig = this+ligature[i];
      if (lig.would_apply (c))
        return_trace (true);
    }
    return_trace (false);
  }

  bool apply (hb_ot_apply_context_t *c) const
  {
    TRACE_APPLY (this);
    unsigned int num_ligs = ligature.len;
    for (unsigned int i = 0; i < num_ligs; i++)
    {
      const Ligature &lig = this+ligature[i];
      if (lig.apply (c)) return_trace (true);
    }

    return_trace (false);
  }

  bool serialize (hb_serialize_context_t *c,
                  hb_array_t<const GlyphID> ligatures,
                  hb_array_t<const unsigned int> component_count_list,
                  hb_array_t<const GlyphID> &component_list /* Starting from second for each ligature */)
  {
    TRACE_SERIALIZE (this);
    if (unlikely (!c->extend_min (*this))) return_trace (false);
    if (unlikely (!ligature.serialize (c, ligatures.length))) return_trace (false);
    for (unsigned int i = 0; i < ligatures.length; i++)
    {
      unsigned int component_count = MAX<int> (component_count_list[i] - 1, 0);
      if (unlikely (!ligature[i].serialize (c, this)
                                .serialize (c,
                                            ligatures[i],
                                            component_list.sub_array (0, component_count))))
        return_trace (false);
      component_list += component_count;
    }
    return_trace (true);
  }

  bool sanitize (hb_sanitize_context_t *c) const
  {
    TRACE_SANITIZE (this);
    return_trace (ligature.sanitize (c, this));
  }

  protected:
  OffsetArrayOf<Ligature>
                ligature;               /* Array LigatureSet tables
                                         * ordered by preference */
  public:
  DEFINE_SIZE_ARRAY (2, ligature);
};

struct LigatureSubstFormat1
{
  bool intersects (const hb_set_t *glyphs) const
  {
    unsigned int count = ligatureSet.len;
    for (Coverage::Iter iter (this+coverage); iter.more (); iter.next ())
    {
      if (unlikely (iter.get_coverage () >= count))
        break; /* Work around malicious fonts. https://github.com/harfbuzz/harfbuzz/issues/363 */
      if (glyphs->has (iter.get_glyph ()) &&
          (this+ligatureSet[iter.get_coverage ()]).intersects (glyphs))
        return true;
    }
    return false;
  }

  void closure (hb_closure_context_t *c) const
  {
    unsigned int count = ligatureSet.len;
    for (Coverage::Iter iter (this+coverage); iter.more (); iter.next ())
    {
      if (unlikely (iter.get_coverage () >= count))
        break; /* Work around malicious fonts. https://github.com/harfbuzz/harfbuzz/issues/363 */
      if (c->glyphs->has (iter.get_glyph ()))
        (this+ligatureSet[iter.get_coverage ()]).closure (c);
    }
  }

  void collect_glyphs (hb_collect_glyphs_context_t *c) const
  {
    if (unlikely (!(this+coverage).add_coverage (c->input))) return;
    unsigned int count = ligatureSet.len;
    for (Coverage::Iter iter (this+coverage); iter.more (); iter.next ())
    {
      if (unlikely (iter.get_coverage () >= count))
        break; /* Work around malicious fonts. https://github.com/harfbuzz/harfbuzz/issues/363 */
      (this+ligatureSet[iter.get_coverage ()]).collect_glyphs (c);
    }
  }

  const Coverage &get_coverage () const { return this+coverage; }

  bool would_apply (hb_would_apply_context_t *c) const
  {
    TRACE_WOULD_APPLY (this);
    unsigned int index = (this+coverage).get_coverage (c->glyphs[0]);
    if (likely (index == NOT_COVERED)) return_trace (false);

    const LigatureSet &lig_set = this+ligatureSet[index];
    return_trace (lig_set.would_apply (c));
  }

  bool apply (hb_ot_apply_context_t *c) const
  {
    TRACE_APPLY (this);

    unsigned int index = (this+coverage).get_coverage (c->buffer->cur().codepoint);
    if (likely (index == NOT_COVERED)) return_trace (false);

    const LigatureSet &lig_set = this+ligatureSet[index];
    return_trace (lig_set.apply (c));
  }

  bool serialize (hb_serialize_context_t *c,
                  hb_array_t<const GlyphID> first_glyphs,
                  hb_array_t<const unsigned int> ligature_per_first_glyph_count_list,
                  hb_array_t<const GlyphID> ligatures_list,
                  hb_array_t<const unsigned int> component_count_list,
                  hb_array_t<const GlyphID> component_list /* Starting from second for each ligature */)
  {
    TRACE_SERIALIZE (this);
    if (unlikely (!c->extend_min (*this))) return_trace (false);
    if (unlikely (!ligatureSet.serialize (c, first_glyphs.length))) return_trace (false);
    for (unsigned int i = 0; i < first_glyphs.length; i++)
    {
      unsigned int ligature_count = ligature_per_first_glyph_count_list[i];
      if (unlikely (!ligatureSet[i].serialize (c, this)
                                   .serialize (c,
                                               ligatures_list.sub_array (0, ligature_count),
                                               component_count_list.sub_array (0, ligature_count),
                                               component_list))) return_trace (false);
      ligatures_list += ligature_count;
      component_count_list += ligature_count;
    }
    return_trace (coverage.serialize (c, this).serialize (c, first_glyphs));
  }

  bool subset (hb_subset_context_t *c) const
  {
    TRACE_SUBSET (this);
    // TODO(subset)
    return_trace (false);
  }

  bool sanitize (hb_sanitize_context_t *c) const
  {
    TRACE_SANITIZE (this);
    return_trace (coverage.sanitize (c, this) && ligatureSet.sanitize (c, this));
  }

  protected:
  HBUINT16      format;                 /* Format identifier--format = 1 */
  OffsetTo<Coverage>
                coverage;               /* Offset to Coverage table--from
                                         * beginning of Substitution table */
  OffsetArrayOf<LigatureSet>
                ligatureSet;            /* Array LigatureSet tables
                                         * ordered by Coverage Index */
  public:
  DEFINE_SIZE_ARRAY (6, ligatureSet);
};

struct LigatureSubst
{
  bool serialize (hb_serialize_context_t *c,
                  hb_array_t<const GlyphID> first_glyphs,
                  hb_array_t<const unsigned int> ligature_per_first_glyph_count_list,
                  hb_array_t<const GlyphID> ligatures_list,
                  hb_array_t<const unsigned int> component_count_list,
                  hb_array_t<const GlyphID> component_list /* Starting from second for each ligature */)
  {
    TRACE_SERIALIZE (this);
    if (unlikely (!c->extend_min (u.format))) return_trace (false);
    unsigned int format = 1;
    u.format.set (format);
    switch (u.format) {
    case 1: return_trace (u.format1.serialize (c,
                                               first_glyphs,
                                               ligature_per_first_glyph_count_list,
                                               ligatures_list,
                                               component_count_list,
                                               component_list));
    default:return_trace (false);
    }
  }

  template <typename context_t>
  typename context_t::return_t dispatch (context_t *c) const
  {
    TRACE_DISPATCH (this, u.format);
    if (unlikely (!c->may_dispatch (this, &u.format))) return_trace (c->no_dispatch_return_value ());
    switch (u.format) {
    case 1: return_trace (c->dispatch (u.format1));
    default:return_trace (c->default_return_value ());
    }
  }

  protected:
  union {
  HBUINT16              format;         /* Format identifier */
  LigatureSubstFormat1  format1;
  } u;
};


struct ContextSubst : Context {};

struct ChainContextSubst : ChainContext {};

struct ExtensionSubst : Extension<ExtensionSubst>
{
  typedef struct SubstLookupSubTable SubTable;

  bool is_reverse () const;
};


struct ReverseChainSingleSubstFormat1
{
  bool intersects (const hb_set_t *glyphs) const
  {
    if (!(this+coverage).intersects (glyphs))
      return false;

    const OffsetArrayOf<Coverage> &lookahead = StructAfter<OffsetArrayOf<Coverage> > (backtrack);

    unsigned int count;

    count = backtrack.len;
    for (unsigned int i = 0; i < count; i++)
      if (!(this+backtrack[i]).intersects (glyphs))
        return false;

    count = lookahead.len;
    for (unsigned int i = 0; i < count; i++)
      if (!(this+lookahead[i]).intersects (glyphs))
        return false;

    return true;
  }

  void closure (hb_closure_context_t *c) const
  {
    const OffsetArrayOf<Coverage> &lookahead = StructAfter<OffsetArrayOf<Coverage> > (backtrack);

    unsigned int count;

    count = backtrack.len;
    for (unsigned int i = 0; i < count; i++)
      if (!(this+backtrack[i]).intersects (c->glyphs))
        return;

    count = lookahead.len;
    for (unsigned int i = 0; i < count; i++)
      if (!(this+lookahead[i]).intersects (c->glyphs))
        return;

    const ArrayOf<GlyphID> &substitute = StructAfter<ArrayOf<GlyphID> > (lookahead);
    count = substitute.len;
    for (Coverage::Iter iter (this+coverage); iter.more (); iter.next ())
    {
      if (unlikely (iter.get_coverage () >= count))
        break; /* Work around malicious fonts. https://github.com/harfbuzz/harfbuzz/issues/363 */
      if (c->glyphs->has (iter.get_glyph ()))
        c->out->add (substitute[iter.get_coverage ()]);
    }
  }

  void collect_glyphs (hb_collect_glyphs_context_t *c) const
  {
    if (unlikely (!(this+coverage).add_coverage (c->input))) return;

    unsigned int count;

    count = backtrack.len;
    for (unsigned int i = 0; i < count; i++)
      if (unlikely (!(this+backtrack[i]).add_coverage (c->before))) return;

    const OffsetArrayOf<Coverage> &lookahead = StructAfter<OffsetArrayOf<Coverage> > (backtrack);
    count = lookahead.len;
    for (unsigned int i = 0; i < count; i++)
      if (unlikely (!(this+lookahead[i]).add_coverage (c->after))) return;

    const ArrayOf<GlyphID> &substitute = StructAfter<ArrayOf<GlyphID> > (lookahead);
    count = substitute.len;
    c->output->add_array (substitute.arrayZ, substitute.len);
  }

  const Coverage &get_coverage () const { return this+coverage; }

  bool would_apply (hb_would_apply_context_t *c) const
  {
    TRACE_WOULD_APPLY (this);
    return_trace (c->len == 1 && (this+coverage).get_coverage (c->glyphs[0]) != NOT_COVERED);
  }

  bool apply (hb_ot_apply_context_t *c) const
  {
    TRACE_APPLY (this);
    if (unlikely (c->nesting_level_left != HB_MAX_NESTING_LEVEL))
      return_trace (false); /* No chaining to this type */

    unsigned int index = (this+coverage).get_coverage (c->buffer->cur().codepoint);
    if (likely (index == NOT_COVERED)) return_trace (false);

    const OffsetArrayOf<Coverage> &lookahead = StructAfter<OffsetArrayOf<Coverage> > (backtrack);
    const ArrayOf<GlyphID> &substitute = StructAfter<ArrayOf<GlyphID> > (lookahead);

  unsigned int start_index = 0, end_index = 0;
    if (match_backtrack (c,
                         backtrack.len, (HBUINT16 *) backtrack.arrayZ,
                         match_coverage, this,
                         &start_index) &&
        match_lookahead (c,
                         lookahead.len, (HBUINT16 *) lookahead.arrayZ,
                         match_coverage, this,
                         1, &end_index))
    {
      c->buffer->unsafe_to_break_from_outbuffer (start_index, end_index);
      c->replace_glyph_inplace (substitute[index]);
      /* Note: We DON'T decrease buffer->idx.  The main loop does it
       * for us.  This is useful for preventing surprises if someone
       * calls us through a Context lookup. */
      return_trace (true);
    }

    return_trace (false);
  }

  bool subset (hb_subset_context_t *c) const
  {
    TRACE_SUBSET (this);
    // TODO(subset)
    return_trace (false);
  }

  bool sanitize (hb_sanitize_context_t *c) const
  {
    TRACE_SANITIZE (this);
    if (!(coverage.sanitize (c, this) && backtrack.sanitize (c, this)))
      return_trace (false);
    const OffsetArrayOf<Coverage> &lookahead = StructAfter<OffsetArrayOf<Coverage> > (backtrack);
    if (!lookahead.sanitize (c, this))
      return_trace (false);
    const ArrayOf<GlyphID> &substitute = StructAfter<ArrayOf<GlyphID> > (lookahead);
    return_trace (substitute.sanitize (c));
  }

  protected:
  HBUINT16      format;                 /* Format identifier--format = 1 */
  OffsetTo<Coverage>
                coverage;               /* Offset to Coverage table--from
                                         * beginning of table */
  OffsetArrayOf<Coverage>
                backtrack;              /* Array of coverage tables
                                         * in backtracking sequence, in glyph
                                         * sequence order */
  OffsetArrayOf<Coverage>
                lookaheadX;             /* Array of coverage tables
                                         * in lookahead sequence, in glyph
                                         * sequence order */
  ArrayOf<GlyphID>
                substituteX;            /* Array of substitute
                                         * GlyphIDs--ordered by Coverage Index */
  public:
  DEFINE_SIZE_MIN (10);
};

struct ReverseChainSingleSubst
{
  template <typename context_t>
  typename context_t::return_t dispatch (context_t *c) const
  {
    TRACE_DISPATCH (this, u.format);
    if (unlikely (!c->may_dispatch (this, &u.format))) return_trace (c->no_dispatch_return_value ());
    switch (u.format) {
    case 1: return_trace (c->dispatch (u.format1));
    default:return_trace (c->default_return_value ());
    }
  }

  protected:
  union {
  HBUINT16                              format;         /* Format identifier */
  ReverseChainSingleSubstFormat1        format1;
  } u;
};



/*
 * SubstLookup
 */

struct SubstLookupSubTable
{
  friend struct Lookup;
  friend struct SubstLookup;

  enum Type {
    Single              = 1,
    Multiple            = 2,
    Alternate           = 3,
    Ligature            = 4,
    Context             = 5,
    ChainContext        = 6,
    Extension           = 7,
    ReverseChainSingle  = 8
  };

  template <typename context_t>
  typename context_t::return_t dispatch (context_t *c, unsigned int lookup_type) const
  {
    TRACE_DISPATCH (this, lookup_type);
    switch (lookup_type) {
    case Single:                return_trace (u.single.dispatch (c));
    case Multiple:              return_trace (u.multiple.dispatch (c));
    case Alternate:             return_trace (u.alternate.dispatch (c));
    case Ligature:              return_trace (u.ligature.dispatch (c));
    case Context:               return_trace (u.context.dispatch (c));
    case ChainContext:          return_trace (u.chainContext.dispatch (c));
    case Extension:             return_trace (u.extension.dispatch (c));
    case ReverseChainSingle:    return_trace (u.reverseChainContextSingle.dispatch (c));
    default:                    return_trace (c->default_return_value ());
    }
  }

  protected:
  union {
  SingleSubst                   single;
  MultipleSubst                 multiple;
  AlternateSubst                alternate;
  LigatureSubst                 ligature;
  ContextSubst                  context;
  ChainContextSubst             chainContext;
  ExtensionSubst                extension;
  ReverseChainSingleSubst       reverseChainContextSingle;
  } u;
  public:
  DEFINE_SIZE_MIN (0);
};


struct SubstLookup : Lookup
{
  typedef SubstLookupSubTable SubTable;

  const SubTable& get_subtable (unsigned int i) const
  { return Lookup::get_subtable<SubTable> (i); }

  static bool lookup_type_is_reverse (unsigned int lookup_type)
  { return lookup_type == SubTable::ReverseChainSingle; }

  bool is_reverse () const
  {
    unsigned int type = get_type ();
    if (unlikely (type == SubTable::Extension))
      return CastR<ExtensionSubst> (get_subtable(0)).is_reverse ();
    return lookup_type_is_reverse (type);
  }

  bool apply (hb_ot_apply_context_t *c) const
  {
    TRACE_APPLY (this);
    return_trace (dispatch (c));
  }

  bool intersects (const hb_set_t *glyphs) const
  {
    hb_intersects_context_t c (glyphs);
    return dispatch (&c);
  }

  hb_closure_context_t::return_t closure (hb_closure_context_t *c, unsigned int this_index) const
  {
    if (!c->should_visit_lookup (this_index))
      return hb_closure_context_t::default_return_value ();

    c->set_recurse_func (dispatch_closure_recurse_func);

    hb_closure_context_t::return_t ret = dispatch (c);

    c->flush ();

    return ret;
  }

  hb_collect_glyphs_context_t::return_t collect_glyphs (hb_collect_glyphs_context_t *c) const
  {
    c->set_recurse_func (dispatch_recurse_func<hb_collect_glyphs_context_t>);
    return dispatch (c);
  }

  template <typename set_t>
  void add_coverage (set_t *glyphs) const
  {
    hb_add_coverage_context_t<set_t> c (glyphs);
    dispatch (&c);
  }

  bool would_apply (hb_would_apply_context_t *c,
                    const hb_ot_layout_lookup_accelerator_t *accel) const
  {
    TRACE_WOULD_APPLY (this);
    if (unlikely (!c->len))  return_trace (false);
    if (!accel->may_have (c->glyphs[0]))  return_trace (false);
      return_trace (dispatch (c));
  }

  static bool apply_recurse_func (hb_ot_apply_context_t *c, unsigned int lookup_index);

  SubTable& serialize_subtable (hb_serialize_context_t *c,
                                       unsigned int i)
  { return get_subtables<SubTable> ()[i].serialize (c, this); }

  bool serialize_single (hb_serialize_context_t *c,
                         uint32_t lookup_props,
                         hb_array_t<const GlyphID> glyphs,
                         hb_array_t<const GlyphID> substitutes)
  {
    TRACE_SERIALIZE (this);
    if (unlikely (!Lookup::serialize (c, SubTable::Single, lookup_props, 1))) return_trace (false);
    return_trace (serialize_subtable (c, 0).u.single.serialize (c, glyphs, substitutes));
  }

  bool serialize_multiple (hb_serialize_context_t *c,
                           uint32_t lookup_props,
                           hb_array_t<const GlyphID> glyphs,
                           hb_array_t<const unsigned int> substitute_len_list,
                           hb_array_t<const GlyphID> substitute_glyphs_list)
  {
    TRACE_SERIALIZE (this);
    if (unlikely (!Lookup::serialize (c, SubTable::Multiple, lookup_props, 1))) return_trace (false);
    return_trace (serialize_subtable (c, 0).u.multiple.serialize (c,
                                                                  glyphs,
                                                                  substitute_len_list,
                                                                  substitute_glyphs_list));
  }

  bool serialize_alternate (hb_serialize_context_t *c,
                            uint32_t lookup_props,
                            hb_array_t<const GlyphID> glyphs,
                            hb_array_t<const unsigned int> alternate_len_list,
                            hb_array_t<const GlyphID> alternate_glyphs_list)
  {
    TRACE_SERIALIZE (this);
    if (unlikely (!Lookup::serialize (c, SubTable::Alternate, lookup_props, 1))) return_trace (false);
    return_trace (serialize_subtable (c, 0).u.alternate.serialize (c,
                                                                   glyphs,
                                                                   alternate_len_list,
                                                                   alternate_glyphs_list));
  }

  bool serialize_ligature (hb_serialize_context_t *c,
                           uint32_t lookup_props,
                           hb_array_t<const GlyphID> first_glyphs,
                           hb_array_t<const unsigned int> ligature_per_first_glyph_count_list,
                           hb_array_t<const GlyphID> ligatures_list,
                           hb_array_t<const unsigned int> component_count_list,
                           hb_array_t<const GlyphID> component_list /* Starting from second for each ligature */)
  {
    TRACE_SERIALIZE (this);
    if (unlikely (!Lookup::serialize (c, SubTable::Ligature, lookup_props, 1))) return_trace (false);
    return_trace (serialize_subtable (c, 0).u.ligature.serialize (c,
                                                                  first_glyphs,
                                                                  ligature_per_first_glyph_count_list,
                                                                  ligatures_list,
                                                                  component_count_list,
                                                                  component_list));
  }

  template <typename context_t>
  static typename context_t::return_t dispatch_recurse_func (context_t *c, unsigned int lookup_index);

  static hb_closure_context_t::return_t dispatch_closure_recurse_func (hb_closure_context_t *c, unsigned int lookup_index)
  {
    if (!c->should_visit_lookup (lookup_index))
      return HB_VOID;

    hb_closure_context_t::return_t ret = dispatch_recurse_func (c, lookup_index);

    /* While in theory we should flush here, it will cause timeouts because a recursive
     * lookup can keep growing the glyph set.  Skip, and outer loop will retry up to
     * HB_CLOSURE_MAX_STAGES time, which should be enough for every realistic font. */
    //c->flush ();

    return ret;
  }

  template <typename context_t>
  typename context_t::return_t dispatch (context_t *c) const
  { return Lookup::dispatch<SubTable> (c); }

  bool subset (hb_subset_context_t *c) const
  { return Lookup::subset<SubTable> (c); }

  bool sanitize (hb_sanitize_context_t *c) const
  { return Lookup::sanitize<SubTable> (c); }
};

/*
 * GSUB -- Glyph Substitution
 * https://docs.microsoft.com/en-us/typography/opentype/spec/gsub
 */

struct GSUB : GSUBGPOS
{
  static constexpr hb_tag_t tableTag = HB_OT_TAG_GSUB;

  const SubstLookup& get_lookup (unsigned int i) const
  { return CastR<SubstLookup> (GSUBGPOS::get_lookup (i)); }

  bool subset (hb_subset_context_t *c) const
  { return GSUBGPOS::subset<SubstLookup> (c); }

  bool sanitize (hb_sanitize_context_t *c) const
  { return GSUBGPOS::sanitize<SubstLookup> (c); }

  HB_INTERNAL bool is_blacklisted (hb_blob_t *blob,
                                   hb_face_t *face) const;

  typedef GSUBGPOS::accelerator_t<GSUB> accelerator_t;
};


struct GSUB_accelerator_t : GSUB::accelerator_t {};


/* Out-of-class implementation for methods recursing */

/*static*/ inline bool ExtensionSubst::is_reverse () const
{
  unsigned int type = get_type ();
  if (unlikely (type == SubTable::Extension))
    return CastR<ExtensionSubst> (get_subtable<SubTable>()).is_reverse ();
  return SubstLookup::lookup_type_is_reverse (type);
}

template <typename context_t>
/*static*/ inline typename context_t::return_t SubstLookup::dispatch_recurse_func (context_t *c, unsigned int lookup_index)
{
  const SubstLookup &l = c->face->table.GSUB.get_relaxed ()->table->get_lookup (lookup_index);
  return l.dispatch (c);
}

/*static*/ inline bool SubstLookup::apply_recurse_func (hb_ot_apply_context_t *c, unsigned int lookup_index)
{
  const SubstLookup &l = c->face->table.GSUB.get_relaxed ()->table->get_lookup (lookup_index);
  unsigned int saved_lookup_props = c->lookup_props;
  unsigned int saved_lookup_index = c->lookup_index;
  c->set_lookup_index (lookup_index);
  c->set_lookup_props (l.get_props ());
  bool ret = l.dispatch (c);
  c->set_lookup_index (saved_lookup_index);
  c->set_lookup_props (saved_lookup_props);
  return ret;
}

} /* namespace OT */


#endif /* HB_OT_LAYOUT_GSUB_TABLE_HH */
