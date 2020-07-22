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

#include "hb-open-type.hh"
#include "hb-ot-cff1-table.hh"
#include "hb-set.h"
#include "hb-subset-cff1.hh"
#include "hb-subset-plan.hh"
#include "hb-subset-cff-common.hh"
#include "hb-cff1-interp-cs.hh"

using namespace CFF;

struct remap_sid_t : remap_t
{
  unsigned int add (unsigned int sid)
  {
    if ((sid != CFF_UNDEF_SID) && !is_std_std (sid))
      return offset_sid (remap_t::add (unoffset_sid (sid)));
    else
      return sid;
  }

  unsigned int operator[] (unsigned int sid) const
  {
    if (is_std_std (sid) || (sid == CFF_UNDEF_SID))
      return sid;
    else
      return offset_sid (remap_t::operator [] (unoffset_sid (sid)));
  }

  static const unsigned int num_std_strings = 391;

  static bool is_std_std (unsigned int sid) { return sid < num_std_strings; }
  static unsigned int offset_sid (unsigned int sid) { return sid + num_std_strings; }
  static unsigned int unoffset_sid (unsigned int sid) { return sid - num_std_strings; }
};

struct cff1_sub_table_offsets_t : cff_sub_table_offsets_t
{
  cff1_sub_table_offsets_t ()
    : cff_sub_table_offsets_t (),
      nameIndexOffset (0),
      encodingOffset (0)
  {
    stringIndexInfo.init ();
    charsetInfo.init ();
    privateDictInfo.init ();
  }

  unsigned int  nameIndexOffset;
  table_info_t  stringIndexInfo;
  unsigned int  encodingOffset;
  table_info_t  charsetInfo;
  table_info_t  privateDictInfo;
};

/* a copy of a parsed out cff1_top_dict_values_t augmented with additional operators */
struct cff1_top_dict_values_mod_t : cff1_top_dict_values_t
{
  void init (const cff1_top_dict_values_t *base_= &Null(cff1_top_dict_values_t))
  {
    SUPER::init ();
    base = base_;
  }

  void fini () { SUPER::fini (); }

  unsigned get_count () const { return base->get_count () + SUPER::get_count (); }
  const cff1_top_dict_val_t &get_value (unsigned int i) const
  {
    if (i < base->get_count ())
      return (*base)[i];
    else
      return SUPER::values[i - base->get_count ()];
  }
  const cff1_top_dict_val_t &operator [] (unsigned int i) const { return get_value (i); }

  void reassignSIDs (const remap_sid_t& sidmap)
  {
    for (unsigned int i = 0; i < name_dict_values_t::ValCount; i++)
      nameSIDs[i] = sidmap[base->nameSIDs[i]];
  }

  protected:
  typedef cff1_top_dict_values_t SUPER;
  const cff1_top_dict_values_t *base;
};

struct top_dict_modifiers_t
{
  top_dict_modifiers_t (const cff1_sub_table_offsets_t &offsets_,
                           const unsigned int (&nameSIDs_)[name_dict_values_t::ValCount])
    : offsets (offsets_),
      nameSIDs (nameSIDs_)
  {}

  const cff1_sub_table_offsets_t &offsets;
  const unsigned int    (&nameSIDs)[name_dict_values_t::ValCount];
};

struct cff1_top_dict_op_serializer_t : cff_top_dict_op_serializer_t<cff1_top_dict_val_t>
{
  bool serialize (hb_serialize_context_t *c,
                  const cff1_top_dict_val_t &opstr,
                  const top_dict_modifiers_t &mod) const
  {
    TRACE_SERIALIZE (this);

    op_code_t op = opstr.op;
    switch (op)
    {
      case OpCode_charset:
        return_trace (FontDict::serialize_offset4_op(c, op, mod.offsets.charsetInfo.offset));

      case OpCode_Encoding:
        return_trace (FontDict::serialize_offset4_op(c, op, mod.offsets.encodingOffset));

      case OpCode_Private:
        {
          if (unlikely (!UnsizedByteStr::serialize_int2 (c, mod.offsets.privateDictInfo.size)))
            return_trace (false);
          if (unlikely (!UnsizedByteStr::serialize_int4 (c, mod.offsets.privateDictInfo.offset)))
            return_trace (false);
          HBUINT8 *p = c->allocate_size<HBUINT8> (1);
          if (unlikely (p == nullptr)) return_trace (false);
          p->set (OpCode_Private);
        }
        break;

      case OpCode_version:
      case OpCode_Notice:
      case OpCode_Copyright:
      case OpCode_FullName:
      case OpCode_FamilyName:
      case OpCode_Weight:
      case OpCode_PostScript:
      case OpCode_BaseFontName:
      case OpCode_FontName:
        return_trace (FontDict::serialize_offset2_op(c, op, mod.nameSIDs[name_dict_values_t::name_op_to_index (op)]));

      case OpCode_ROS:
        {
          /* for registry & ordering, reassigned SIDs are serialized
           * for supplement, the original byte string is copied along with the op code */
          op_str_t supp_op;
          supp_op.op = op;
          if ( unlikely (!(opstr.str.length >= opstr.last_arg_offset + 3)))
            return_trace (false);
          supp_op.str = byte_str_t (&opstr.str + opstr.last_arg_offset, opstr.str.length - opstr.last_arg_offset);
          return_trace (UnsizedByteStr::serialize_int2 (c, mod.nameSIDs[name_dict_values_t::registry]) &&
                        UnsizedByteStr::serialize_int2 (c, mod.nameSIDs[name_dict_values_t::ordering]) &&
                        copy_opstr (c, supp_op));
        }
      default:
        return_trace (cff_top_dict_op_serializer_t<cff1_top_dict_val_t>::serialize (c, opstr, mod.offsets));
    }
    return_trace (true);
  }

  unsigned int calculate_serialized_size (const cff1_top_dict_val_t &opstr) const
  {
    op_code_t op = opstr.op;
    switch (op)
    {
      case OpCode_charset:
      case OpCode_Encoding:
        return OpCode_Size (OpCode_longintdict) + 4 + OpCode_Size (op);

      case OpCode_Private:
        return OpCode_Size (OpCode_longintdict) + 4 + OpCode_Size (OpCode_shortint) + 2 + OpCode_Size (OpCode_Private);

      case OpCode_version:
      case OpCode_Notice:
      case OpCode_Copyright:
      case OpCode_FullName:
      case OpCode_FamilyName:
      case OpCode_Weight:
      case OpCode_PostScript:
      case OpCode_BaseFontName:
      case OpCode_FontName:
        return OpCode_Size (OpCode_shortint) + 2 + OpCode_Size (op);

      case OpCode_ROS:
        return ((OpCode_Size (OpCode_shortint) + 2) * 2) + (opstr.str.length - opstr.last_arg_offset)/* supplement + op */;

      default:
        return cff_top_dict_op_serializer_t<cff1_top_dict_val_t>::calculate_serialized_size (opstr);
    }
  }
};

struct font_dict_values_mod_t
{
  void init (const cff1_font_dict_values_t *base_,
             unsigned int fontName_,
             const table_info_t &privateDictInfo_)
  {
    base = base_;
    fontName = fontName_;
    privateDictInfo = privateDictInfo_;
  }

  unsigned get_count () const { return base->get_count (); }

  const op_str_t &operator [] (unsigned int i) const { return (*base)[i]; }

  const cff1_font_dict_values_t    *base;
  table_info_t             privateDictInfo;
  unsigned int          fontName;
};

struct cff1_font_dict_op_serializer_t : cff_font_dict_op_serializer_t
{
  bool serialize (hb_serialize_context_t *c,
                  const op_str_t &opstr,
                  const font_dict_values_mod_t &mod) const
  {
    TRACE_SERIALIZE (this);

    if (opstr.op == OpCode_FontName)
      return_trace (FontDict::serialize_uint2_op (c, opstr.op, mod.fontName));
    else
      return_trace (SUPER::serialize (c, opstr, mod.privateDictInfo));
  }

  unsigned int calculate_serialized_size (const op_str_t &opstr) const
  {
    if (opstr.op == OpCode_FontName)
      return OpCode_Size (OpCode_shortint) + 2 + OpCode_Size (OpCode_FontName);
    else
      return SUPER::calculate_serialized_size (opstr);
  }

  private:
  typedef cff_font_dict_op_serializer_t SUPER;
};

struct cff1_cs_opset_flatten_t : cff1_cs_opset_t<cff1_cs_opset_flatten_t, flatten_param_t>
{
  static void flush_args_and_op (op_code_t op, cff1_cs_interp_env_t &env, flatten_param_t& param)
  {
    if (env.arg_start > 0)
      flush_width (env, param);

    switch (op)
    {
      case OpCode_hstem:
      case OpCode_hstemhm:
      case OpCode_vstem:
      case OpCode_vstemhm:
      case OpCode_hintmask:
      case OpCode_cntrmask:
      case OpCode_dotsection:
        if (param.drop_hints)
        {
          env.clear_args ();
          return;
        }
        HB_FALLTHROUGH;

      default:
        SUPER::flush_args_and_op (op, env, param);
        break;
    }
  }
  static void flush_args (cff1_cs_interp_env_t &env, flatten_param_t& param)
  {
    str_encoder_t  encoder (param.flatStr);
    for (unsigned int i = env.arg_start; i < env.argStack.get_count (); i++)
      encoder.encode_num (env.eval_arg (i));
    SUPER::flush_args (env, param);
  }

  static void flush_op (op_code_t op, cff1_cs_interp_env_t &env, flatten_param_t& param)
  {
    str_encoder_t  encoder (param.flatStr);
    encoder.encode_op (op);
  }

  static void flush_width (cff1_cs_interp_env_t &env, flatten_param_t& param)
  {
    assert (env.has_width);
    str_encoder_t  encoder (param.flatStr);
    encoder.encode_num (env.width);
  }

  static void flush_hintmask (op_code_t op, cff1_cs_interp_env_t &env, flatten_param_t& param)
  {
    SUPER::flush_hintmask (op, env, param);
    if (!param.drop_hints)
    {
      str_encoder_t  encoder (param.flatStr);
      for (unsigned int i = 0; i < env.hintmask_size; i++)
        encoder.encode_byte (env.str_ref[i]);
    }
  }

  private:
  typedef cff1_cs_opset_t<cff1_cs_opset_flatten_t, flatten_param_t> SUPER;
};

struct range_list_t : hb_vector_t<code_pair_t>
{
  /* replace the first glyph ID in the "glyph" field each range with a nLeft value */
  bool finalize (unsigned int last_glyph)
  {
    bool  two_byte = false;
    for (unsigned int i = (*this).length; i > 0; i--)
    {
      code_pair_t &pair = (*this)[i - 1];
      unsigned int  nLeft = last_glyph - pair.glyph - 1;
      if (nLeft >= 0x100)
        two_byte = true;
      last_glyph = pair.glyph;
      pair.glyph = nLeft;
    }
    return two_byte;
  }
};

struct cff1_cs_opset_subr_subset_t : cff1_cs_opset_t<cff1_cs_opset_subr_subset_t, subr_subset_param_t>
{
  static void process_op (op_code_t op, cff1_cs_interp_env_t &env, subr_subset_param_t& param)
  {
    switch (op) {

      case OpCode_return:
        param.current_parsed_str->add_op (op, env.str_ref);
        param.current_parsed_str->set_parsed ();
        env.returnFromSubr ();
        param.set_current_str (env, false);
        break;

      case OpCode_endchar:
        param.current_parsed_str->add_op (op, env.str_ref);
        param.current_parsed_str->set_parsed ();
        SUPER::process_op (op, env, param);
        break;

      case OpCode_callsubr:
        process_call_subr (op, CSType_LocalSubr, env, param, env.localSubrs, param.local_closure);
        break;

      case OpCode_callgsubr:
        process_call_subr (op, CSType_GlobalSubr, env, param, env.globalSubrs, param.global_closure);
        break;

      default:
        SUPER::process_op (op, env, param);
        param.current_parsed_str->add_op (op, env.str_ref);
        break;
    }
  }

  protected:
  static void process_call_subr (op_code_t op, cs_type_t type,
                                 cff1_cs_interp_env_t &env, subr_subset_param_t& param,
                                 cff1_biased_subrs_t& subrs, hb_set_t *closure)
  {
    byte_str_ref_t    str_ref = env.str_ref;
    env.callSubr (subrs, type);
    param.current_parsed_str->add_call_op (op, str_ref, env.context.subr_num);
    hb_set_add (closure, env.context.subr_num);
    param.set_current_str (env, true);
  }

  private:
  typedef cff1_cs_opset_t<cff1_cs_opset_subr_subset_t, subr_subset_param_t> SUPER;
};

struct cff1_subr_subsetter_t : subr_subsetter_t<cff1_subr_subsetter_t, CFF1Subrs, const OT::cff1::accelerator_subset_t, cff1_cs_interp_env_t, cff1_cs_opset_subr_subset_t>
{
  static void finalize_parsed_str (cff1_cs_interp_env_t &env, subr_subset_param_t& param, parsed_cs_str_t &charstring)
  {
    /* insert width at the beginning of the charstring as necessary */
    if (env.has_width)
      charstring.set_prefix (env.width);

    /* subroutines/charstring left on the call stack are legally left unmarked
     * unmarked when a subroutine terminates with endchar. mark them.
     */
    param.current_parsed_str->set_parsed ();
    for (unsigned int i = 0; i < env.callStack.get_count (); i++)
    {
      parsed_cs_str_t  *parsed_str = param.get_parsed_str_for_context (env.callStack[i]);
      if (likely (parsed_str != nullptr))
        parsed_str->set_parsed ();
      else
        env.set_error ();
    }
  }
};

struct cff_subset_plan {
  cff_subset_plan ()
    : final_size (0),
      offsets (),
      orig_fdcount (0),
      subset_fdcount (1),
      subset_fdselect_format (0),
      drop_hints (false),
      desubroutinize(false)
  {
    topdict_sizes.init ();
    topdict_sizes.resize (1);
    topdict_mod.init ();
    subset_fdselect_ranges.init ();
    fdmap.init ();
    subset_charstrings.init ();
    subset_globalsubrs.init ();
    subset_localsubrs.init ();
    fontdicts_mod.init ();
    subset_enc_code_ranges.init ();
    subset_enc_supp_codes.init ();
    subset_charset_ranges.init ();
    sidmap.init ();
    for (unsigned int i = 0; i < name_dict_values_t::ValCount; i++)
      topDictModSIDs[i] = CFF_UNDEF_SID;
  }

  ~cff_subset_plan ()
  {
    topdict_sizes.fini ();
    topdict_mod.fini ();
    subset_fdselect_ranges.fini ();
    fdmap.fini ();
    subset_charstrings.fini_deep ();
    subset_globalsubrs.fini_deep ();
    subset_localsubrs.fini_deep ();
    fontdicts_mod.fini ();
    subset_enc_code_ranges.fini ();
    subset_enc_supp_codes.fini ();
    subset_charset_ranges.fini ();
    sidmap.fini ();
  }

  unsigned int plan_subset_encoding (const OT::cff1::accelerator_subset_t &acc, hb_subset_plan_t *plan)
  {
    const Encoding *encoding = acc.encoding;
    unsigned int  size0, size1, supp_size;
    hb_codepoint_t  code, last_code = CFF_UNDEF_CODE;
    hb_vector_t<hb_codepoint_t> supp_codes;

    subset_enc_code_ranges.resize (0);
    supp_size = 0;
    supp_codes.init ();

    subset_enc_num_codes = plan->glyphs.length - 1;
    unsigned int glyph;
    for (glyph = 1; glyph < plan->glyphs.length; glyph++)
    {
      hb_codepoint_t  orig_glyph = plan->glyphs[glyph];
      code = acc.glyph_to_code (orig_glyph);
      if (code == CFF_UNDEF_CODE)
      {
        subset_enc_num_codes = glyph - 1;
        break;
      }

      if (code != last_code + 1)
      {
        code_pair_t pair = { code, glyph };
        subset_enc_code_ranges.push (pair);
      }
      last_code = code;

      if (encoding != &Null(Encoding))
      {
        hb_codepoint_t  sid = acc.glyph_to_sid (orig_glyph);
        encoding->get_supplement_codes (sid, supp_codes);
        for (unsigned int i = 0; i < supp_codes.length; i++)
        {
          code_pair_t pair = { supp_codes[i], sid };
          subset_enc_supp_codes.push (pair);
        }
        supp_size += SuppEncoding::static_size * supp_codes.length;
      }
    }
    supp_codes.fini ();

    subset_enc_code_ranges.finalize (glyph);

    assert (subset_enc_num_codes <= 0xFF);
    size0 = Encoding0::min_size + HBUINT8::static_size * subset_enc_num_codes;
    size1 = Encoding1::min_size + Encoding1_Range::static_size * subset_enc_code_ranges.length;

    if (size0 < size1)
      subset_enc_format = 0;
    else
      subset_enc_format = 1;

    return Encoding::calculate_serialized_size (
                        subset_enc_format,
                        subset_enc_format? subset_enc_code_ranges.length: subset_enc_num_codes,
                        subset_enc_supp_codes.length);
  }

  unsigned int plan_subset_charset (const OT::cff1::accelerator_subset_t &acc, hb_subset_plan_t *plan)
  {
    unsigned int  size0, size_ranges;
    hb_codepoint_t  sid, last_sid = CFF_UNDEF_CODE;

    subset_charset_ranges.resize (0);
    unsigned int glyph;
    for (glyph = 1; glyph < plan->glyphs.length; glyph++)
    {
      hb_codepoint_t  orig_glyph = plan->glyphs[glyph];
      sid = acc.glyph_to_sid (orig_glyph);

      if (!acc.is_CID ())
        sid = sidmap.add (sid);

      if (sid != last_sid + 1)
      {
        code_pair_t pair = { sid, glyph };
        subset_charset_ranges.push (pair);
      }
      last_sid = sid;
    }

    bool two_byte = subset_charset_ranges.finalize (glyph);

    size0 = Charset0::min_size + HBUINT16::static_size * (plan->glyphs.length - 1);
    if (!two_byte)
      size_ranges = Charset1::min_size + Charset1_Range::static_size * subset_charset_ranges.length;
    else
      size_ranges = Charset2::min_size + Charset2_Range::static_size * subset_charset_ranges.length;

    if (size0 < size_ranges)
      subset_charset_format = 0;
    else if (!two_byte)
      subset_charset_format = 1;
    else
      subset_charset_format = 2;

    return Charset::calculate_serialized_size (
                        subset_charset_format,
                        subset_charset_format? subset_charset_ranges.length: plan->glyphs.length);
  }

  bool collect_sids_in_dicts (const OT::cff1::accelerator_subset_t &acc)
  {
    if (unlikely (!sidmap.reset (acc.stringIndex->count)))
      return false;

    for (unsigned int i = 0; i < name_dict_values_t::ValCount; i++)
    {
      unsigned int sid = acc.topDict.nameSIDs[i];
      if (sid != CFF_UNDEF_SID)
      {
        (void)sidmap.add (sid);
        topDictModSIDs[i] = sidmap[sid];
      }
    }

    if (acc.fdArray != &Null(CFF1FDArray))
      for (unsigned int i = 0; i < orig_fdcount; i++)
        if (fdmap.includes (i))
          (void)sidmap.add (acc.fontDicts[i].fontName);

    return true;
  }

  bool create (const OT::cff1::accelerator_subset_t &acc,
                      hb_subset_plan_t *plan)
  {
     /* make sure notdef is first */
    if ((plan->glyphs.length == 0) || (plan->glyphs[0] != 0)) return false;

    final_size = 0;
    num_glyphs = plan->glyphs.length;
    orig_fdcount = acc.fdCount;
    drop_hints = plan->drop_hints;
    desubroutinize = plan->desubroutinize;

    /* check whether the subset renumbers any glyph IDs */
    gid_renum = false;
    for (unsigned int glyph = 0; glyph < plan->glyphs.length; glyph++)
    {
      if (plan->glyphs[glyph] != glyph) {
        gid_renum = true;
        break;
      }
    }

    subset_charset = gid_renum || !acc.is_predef_charset ();
    subset_encoding = !acc.is_CID() && !acc.is_predef_encoding ();

    /* CFF header */
    final_size += OT::cff1::static_size;

    /* Name INDEX */
    offsets.nameIndexOffset = final_size;
    final_size += acc.nameIndex->get_size ();

    /* top dict INDEX */
    {
      /* Add encoding/charset to a (copy of) top dict as necessary */
      topdict_mod.init (&acc.topDict);
      bool need_to_add_enc = (subset_encoding && !acc.topDict.has_op (OpCode_Encoding));
      bool need_to_add_set = (subset_charset && !acc.topDict.has_op (OpCode_charset));
      if (need_to_add_enc || need_to_add_set)
      {
        if (need_to_add_enc)
          topdict_mod.add_op (OpCode_Encoding);
        if (need_to_add_set)
          topdict_mod.add_op (OpCode_charset);
      }
      offsets.topDictInfo.offset = final_size;
      cff1_top_dict_op_serializer_t topSzr;
      unsigned int topDictSize = TopDict::calculate_serialized_size (topdict_mod, topSzr);
      offsets.topDictInfo.offSize = calcOffSize(topDictSize);
      if (unlikely (offsets.topDictInfo.offSize > 4))
        return false;
      final_size += CFF1IndexOf<TopDict>::calculate_serialized_size<cff1_top_dict_values_mod_t>
                                                (offsets.topDictInfo.offSize,
                                                 &topdict_mod, 1, topdict_sizes, topSzr);
    }

    /* Determine re-mapping of font index as fdmap among other info */
    if (acc.fdSelect != &Null(CFF1FDSelect))
    {
        if (unlikely (!hb_plan_subset_cff_fdselect (plan->glyphs,
                                  orig_fdcount,
                                  *acc.fdSelect,
                                  subset_fdcount,
                                  offsets.FDSelectInfo.size,
                                  subset_fdselect_format,
                                  subset_fdselect_ranges,
                                  fdmap)))
        return false;
    }
    else
      fdmap.identity (1);

    /* remove unused SIDs & reassign SIDs */
    {
      /* SIDs for name strings in dicts are added before glyph names so they fit in 16-bit int range */
      if (unlikely (!collect_sids_in_dicts (acc)))
        return false;
      if (unlikely (sidmap.get_count () > 0x8000))      /* assumption: a dict won't reference that many strings */
        return false;
      if (subset_charset)
        offsets.charsetInfo.size = plan_subset_charset (acc, plan);

      topdict_mod.reassignSIDs (sidmap);
    }

    /* String INDEX */
    {
      offsets.stringIndexInfo.offset = final_size;
      offsets.stringIndexInfo.size = acc.stringIndex->calculate_serialized_size (offsets.stringIndexInfo.offSize, sidmap);
      final_size += offsets.stringIndexInfo.size;
    }

    if (desubroutinize)
    {
      /* Flatten global & local subrs */
      subr_flattener_t<const OT::cff1::accelerator_subset_t, cff1_cs_interp_env_t, cff1_cs_opset_flatten_t>
                    flattener(acc, plan->glyphs, plan->drop_hints);
      if (!flattener.flatten (subset_charstrings))
        return false;

      /* no global/local subroutines */
      offsets.globalSubrsInfo.size = CFF1Subrs::calculate_serialized_size (1, 0, 0);
    }
    else
    {
      /* Subset subrs: collect used subroutines, leaving all unused ones behind */
      if (!subr_subsetter.subset (acc, plan->glyphs, plan->drop_hints))
        return false;

      /* encode charstrings, global subrs, local subrs with new subroutine numbers */
      if (!subr_subsetter.encode_charstrings (acc, plan->glyphs, subset_charstrings))
        return false;

      if (!subr_subsetter.encode_globalsubrs (subset_globalsubrs))
        return false;

      /* global subrs */
      unsigned int dataSize = subset_globalsubrs.total_size ();
      offsets.globalSubrsInfo.offSize = calcOffSize (dataSize);
      if (unlikely (offsets.globalSubrsInfo.offSize > 4))
        return false;
      offsets.globalSubrsInfo.size = CFF1Subrs::calculate_serialized_size (offsets.globalSubrsInfo.offSize, subset_globalsubrs.length, dataSize);

      /* local subrs */
      if (!offsets.localSubrsInfos.resize (orig_fdcount))
        return false;
      if (!subset_localsubrs.resize (orig_fdcount))
        return false;
      for (unsigned int fd = 0; fd < orig_fdcount; fd++)
      {
        subset_localsubrs[fd].init ();
        offsets.localSubrsInfos[fd].init ();
        if (fdmap.includes (fd))
        {
          if (!subr_subsetter.encode_localsubrs (fd, subset_localsubrs[fd]))
            return false;

          unsigned int dataSize = subset_localsubrs[fd].total_size ();
          if (dataSize > 0)
          {
            offsets.localSubrsInfos[fd].offset = final_size;
            offsets.localSubrsInfos[fd].offSize = calcOffSize (dataSize);
            if (unlikely (offsets.localSubrsInfos[fd].offSize > 4))
              return false;
            offsets.localSubrsInfos[fd].size = CFF1Subrs::calculate_serialized_size (offsets.localSubrsInfos[fd].offSize, subset_localsubrs[fd].length, dataSize);
          }
        }
      }
    }

    /* global subrs */
    offsets.globalSubrsInfo.offset = final_size;
    final_size += offsets.globalSubrsInfo.size;

    /* Encoding */
    if (!subset_encoding)
      offsets.encodingOffset = acc.topDict.EncodingOffset;
    else
    {
      offsets.encodingOffset = final_size;
      final_size += plan_subset_encoding (acc, plan);
    }

    /* Charset */
    if (!subset_charset && acc.is_predef_charset ())
      offsets.charsetInfo.offset = acc.topDict.CharsetOffset;
    else
      offsets.charsetInfo.offset = final_size;
    final_size += offsets.charsetInfo.size;

    /* FDSelect */
    if (acc.fdSelect != &Null(CFF1FDSelect))
    {
      offsets.FDSelectInfo.offset = final_size;
      final_size += offsets.FDSelectInfo.size;
    }

    /* FDArray (FDIndex) */
    if (acc.fdArray != &Null(CFF1FDArray)) {
      offsets.FDArrayInfo.offset = final_size;
      cff1_font_dict_op_serializer_t fontSzr;
      unsigned int dictsSize = 0;
      for (unsigned int i = 0; i < acc.fontDicts.length; i++)
        if (fdmap.includes (i))
          dictsSize += FontDict::calculate_serialized_size (acc.fontDicts[i], fontSzr);

      offsets.FDArrayInfo.offSize = calcOffSize (dictsSize);
      if (unlikely (offsets.FDArrayInfo.offSize > 4))
        return false;
      final_size += CFF1Index::calculate_serialized_size (offsets.FDArrayInfo.offSize, subset_fdcount, dictsSize);
    }

    /* CharStrings */
    {
      offsets.charStringsInfo.offset = final_size;
      unsigned int dataSize = subset_charstrings.total_size ();
      offsets.charStringsInfo.offSize = calcOffSize (dataSize);
      if (unlikely (offsets.charStringsInfo.offSize > 4))
        return false;
      final_size += CFF1CharStrings::calculate_serialized_size (offsets.charStringsInfo.offSize, plan->glyphs.length, dataSize);
    }

    /* private dicts & local subrs */
    offsets.privateDictInfo.offset = final_size;
    for (unsigned int i = 0; i < orig_fdcount; i++)
    {
      if (fdmap.includes (i))
      {
        bool  has_localsubrs = offsets.localSubrsInfos[i].size > 0;
        cff_private_dict_op_serializer_t privSzr (desubroutinize, plan->drop_hints);
        unsigned int  priv_size = PrivateDict::calculate_serialized_size (acc.privateDicts[i], privSzr, has_localsubrs);
        table_info_t  privInfo = { final_size, priv_size, 0 };
        font_dict_values_mod_t fontdict_mod;
        if (!acc.is_CID ())
          fontdict_mod.init ( &Null(cff1_font_dict_values_t), CFF_UNDEF_SID, privInfo );
        else
          fontdict_mod.init ( &acc.fontDicts[i], sidmap[acc.fontDicts[i].fontName], privInfo );
        fontdicts_mod.push (fontdict_mod);
        final_size += privInfo.size;

        if (!plan->desubroutinize && has_localsubrs)
        {
          offsets.localSubrsInfos[i].offset = final_size;
          final_size += offsets.localSubrsInfos[i].size;
        }
      }
    }

    if (!acc.is_CID ())
      offsets.privateDictInfo = fontdicts_mod[0].privateDictInfo;

    return ((subset_charstrings.length == plan->glyphs.length)
           && (fontdicts_mod.length == subset_fdcount));
  }

  unsigned int get_final_size () const  { return final_size; }

  unsigned int        final_size;
  hb_vector_t<unsigned int>     topdict_sizes;
  cff1_top_dict_values_mod_t    topdict_mod;
  cff1_sub_table_offsets_t      offsets;

  unsigned int    num_glyphs;
  unsigned int    orig_fdcount;
  unsigned int    subset_fdcount;
  unsigned int    subset_fdselect_format;
  hb_vector_t<code_pair_t>   subset_fdselect_ranges;

  /* font dict index remap table from fullset FDArray to subset FDArray.
   * set to CFF_UNDEF_CODE if excluded from subset */
  remap_t   fdmap;

  str_buff_vec_t                subset_charstrings;
  str_buff_vec_t                subset_globalsubrs;
  hb_vector_t<str_buff_vec_t>   subset_localsubrs;
  hb_vector_t<font_dict_values_mod_t>  fontdicts_mod;

  bool          drop_hints;

  bool          gid_renum;
  bool          subset_encoding;
  uint8_t       subset_enc_format;
  unsigned int  subset_enc_num_codes;
  range_list_t  subset_enc_code_ranges;
  hb_vector_t<code_pair_t>  subset_enc_supp_codes;

  uint8_t       subset_charset_format;
  range_list_t  subset_charset_ranges;
  bool          subset_charset;

  remap_sid_t   sidmap;
  unsigned int  topDictModSIDs[name_dict_values_t::ValCount];

  bool          desubroutinize;
  cff1_subr_subsetter_t       subr_subsetter;
};

static inline bool _write_cff1 (const cff_subset_plan &plan,
                                const OT::cff1::accelerator_subset_t  &acc,
                                const hb_vector_t<hb_codepoint_t>& glyphs,
                                unsigned int dest_sz,
                                void *dest)
{
  hb_serialize_context_t c (dest, dest_sz);

  OT::cff1 *cff = c.start_serialize<OT::cff1> ();
  if (unlikely (!c.extend_min (*cff)))
    return false;

  /* header */
  cff->version.major.set (0x01);
  cff->version.minor.set (0x00);
  cff->nameIndex.set (cff->min_size);
  cff->offSize.set (4); /* unused? */

  /* name INDEX */
  {
    assert (cff->nameIndex == (unsigned) (c.head - c.start));
    CFF1NameIndex *dest = c.start_embed<CFF1NameIndex> ();
    if (unlikely (dest == nullptr)) return false;
    if (unlikely (!dest->serialize (&c, *acc.nameIndex)))
    {
      DEBUG_MSG (SUBSET, nullptr, "failed to serialize CFF name INDEX");
      return false;
    }
  }

  /* top dict INDEX */
  {
    assert (plan.offsets.topDictInfo.offset == (unsigned) (c.head - c.start));
    CFF1IndexOf<TopDict> *dest = c.start_embed< CFF1IndexOf<TopDict> > ();
    if (dest == nullptr) return false;
    cff1_top_dict_op_serializer_t topSzr;
    top_dict_modifiers_t  modifier (plan.offsets, plan.topDictModSIDs);
    if (unlikely (!dest->serialize (&c, plan.offsets.topDictInfo.offSize,
                                    &plan.topdict_mod, 1,
                                    plan.topdict_sizes, topSzr, modifier)))
    {
      DEBUG_MSG (SUBSET, nullptr, "failed to serialize CFF top dict");
      return false;
    }
  }

  /* String INDEX */
  {
    assert (plan.offsets.stringIndexInfo.offset == (unsigned) (c.head - c.start));
    CFF1StringIndex *dest = c.start_embed<CFF1StringIndex> ();
    if (unlikely (dest == nullptr)) return false;
    if (unlikely (!dest->serialize (&c, *acc.stringIndex, plan.offsets.stringIndexInfo.offSize, plan.sidmap)))
    {
      DEBUG_MSG (SUBSET, nullptr, "failed to serialize CFF string INDEX");
      return false;
    }
  }

  /* global subrs */
  {
    assert (plan.offsets.globalSubrsInfo.offset != 0);
    assert (plan.offsets.globalSubrsInfo.offset == (unsigned) (c.head - c.start));

    CFF1Subrs *dest = c.start_embed <CFF1Subrs> ();
    if (unlikely (dest == nullptr)) return false;
    if (unlikely (!dest->serialize (&c, plan.offsets.globalSubrsInfo.offSize, plan.subset_globalsubrs)))
    {
      DEBUG_MSG (SUBSET, nullptr, "failed to serialize global subroutines");
      return false;
    }
  }

  /* Encoding */
  if (plan.subset_encoding)
  {
    assert (plan.offsets.encodingOffset == (unsigned) (c.head - c.start));
    Encoding *dest = c.start_embed<Encoding> ();
    if (unlikely (dest == nullptr)) return false;
    if (unlikely (!dest->serialize (&c,
                                    plan.subset_enc_format,
                                    plan.subset_enc_num_codes,
                                    plan.subset_enc_code_ranges,
                                    plan.subset_enc_supp_codes)))
    {
      DEBUG_MSG (SUBSET, nullptr, "failed to serialize Encoding");
      return false;
    }
  }

  /* Charset */
  if (plan.subset_charset)
  {
    assert (plan.offsets.charsetInfo.offset == (unsigned) (c.head - c.start));
    Charset *dest = c.start_embed<Charset> ();
    if (unlikely (dest == nullptr)) return false;
    if (unlikely (!dest->serialize (&c,
                                    plan.subset_charset_format,
                                    plan.num_glyphs,
                                    plan.subset_charset_ranges)))
    {
      DEBUG_MSG (SUBSET, nullptr, "failed to serialize Charset");
      return false;
    }
  }

  /* FDSelect */
  if (acc.fdSelect != &Null(CFF1FDSelect))
  {
    assert (plan.offsets.FDSelectInfo.offset == (unsigned) (c.head - c.start));

    if (unlikely (!hb_serialize_cff_fdselect (&c, glyphs.length, *acc.fdSelect, acc.fdCount,
                                              plan.subset_fdselect_format, plan.offsets.FDSelectInfo.size,
                                              plan.subset_fdselect_ranges)))
    {
      DEBUG_MSG (SUBSET, nullptr, "failed to serialize CFF subset FDSelect");
      return false;
    }
  }

  /* FDArray (FD Index) */
  if (acc.fdArray != &Null(CFF1FDArray))
  {
    assert (plan.offsets.FDArrayInfo.offset == (unsigned) (c.head - c.start));
    CFF1FDArray  *fda = c.start_embed<CFF1FDArray> ();
    if (unlikely (fda == nullptr)) return false;
    cff1_font_dict_op_serializer_t  fontSzr;
    if (unlikely (!fda->serialize (&c, plan.offsets.FDArrayInfo.offSize,
                                   plan.fontdicts_mod,
                                   fontSzr)))
    {
      DEBUG_MSG (SUBSET, nullptr, "failed to serialize CFF FDArray");
      return false;
    }
  }

  /* CharStrings */
  {
    assert (plan.offsets.charStringsInfo.offset == (unsigned) (c.head - c.start));
    CFF1CharStrings  *cs = c.start_embed<CFF1CharStrings> ();
    if (unlikely (cs == nullptr)) return false;
    if (unlikely (!cs->serialize (&c, plan.offsets.charStringsInfo.offSize, plan.subset_charstrings)))
    {
      DEBUG_MSG (SUBSET, nullptr, "failed to serialize CFF CharStrings");
      return false;
    }
  }

  /* private dicts & local subrs */
  assert (plan.offsets.privateDictInfo.offset == (unsigned) (c.head - c.start));
  for (unsigned int i = 0; i < acc.privateDicts.length; i++)
  {
    if (plan.fdmap.includes (i))
    {
      PrivateDict  *pd = c.start_embed<PrivateDict> ();
      if (unlikely (pd == nullptr)) return false;
      unsigned int priv_size = plan.fontdicts_mod[plan.fdmap[i]].privateDictInfo.size;
      bool result;
      cff_private_dict_op_serializer_t privSzr (plan.desubroutinize, plan.drop_hints);
      /* N.B. local subrs immediately follows its corresponding private dict. i.e., subr offset == private dict size */
      unsigned int  subroffset = (plan.offsets.localSubrsInfos[i].size > 0)? priv_size: 0;
      result = pd->serialize (&c, acc.privateDicts[i], privSzr, subroffset);
      if (unlikely (!result))
      {
        DEBUG_MSG (SUBSET, nullptr, "failed to serialize CFF Private Dict[%d]", i);
        return false;
      }
      if (plan.offsets.localSubrsInfos[i].size > 0)
      {
        CFF1Subrs *dest = c.start_embed <CFF1Subrs> ();
        if (unlikely (dest == nullptr)) return false;
        if (unlikely (!dest->serialize (&c, plan.offsets.localSubrsInfos[i].offSize, plan.subset_localsubrs[i])))
        {
          DEBUG_MSG (SUBSET, nullptr, "failed to serialize local subroutines");
          return false;
        }
      }
    }
  }

  assert (c.head == c.end);
  c.end_serialize ();

  return true;
}

static bool
_hb_subset_cff1 (const OT::cff1::accelerator_subset_t  &acc,
                const char              *data,
                hb_subset_plan_t        *plan,
                hb_blob_t               **prime /* OUT */)
{
  cff_subset_plan cff_plan;

  if (unlikely (!cff_plan.create (acc, plan)))
  {
    DEBUG_MSG(SUBSET, nullptr, "Failed to generate a cff subsetting plan.");
    return false;
  }

  unsigned int  cff_prime_size = cff_plan.get_final_size ();
  char *cff_prime_data = (char *) calloc (1, cff_prime_size);

  if (unlikely (!_write_cff1 (cff_plan, acc, plan->glyphs,
                              cff_prime_size, cff_prime_data))) {
    DEBUG_MSG(SUBSET, nullptr, "Failed to write a subset cff.");
    free (cff_prime_data);
    return false;
  }

  *prime = hb_blob_create (cff_prime_data,
                           cff_prime_size,
                           HB_MEMORY_MODE_READONLY,
                           cff_prime_data,
                           free);
  return true;
}

/**
 * hb_subset_cff1:
 * Subsets the CFF table according to a provided plan.
 *
 * Return value: subsetted cff table.
 **/
bool
hb_subset_cff1 (hb_subset_plan_t *plan,
                hb_blob_t       **prime /* OUT */)
{
  hb_blob_t *cff_blob = hb_sanitize_context_t().reference_table<CFF::cff1> (plan->source);
  const char *data = hb_blob_get_data(cff_blob, nullptr);

  OT::cff1::accelerator_subset_t acc;
  acc.init(plan->source);
  bool result = likely (acc.is_valid ()) &&
                        _hb_subset_cff1 (acc, data, plan, prime);
  hb_blob_destroy (cff_blob);
  acc.fini ();

  return result;
}
