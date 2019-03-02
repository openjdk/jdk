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
#include "hb-ot-cff2-table.hh"
#include "hb-set.h"
#include "hb-subset-cff2.hh"
#include "hb-subset-plan.hh"
#include "hb-subset-cff-common.hh"
#include "hb-cff2-interp-cs.hh"

using namespace CFF;

struct cff2_sub_table_offsets_t : cff_sub_table_offsets_t
{
  cff2_sub_table_offsets_t ()
    : cff_sub_table_offsets_t (),
      varStoreOffset (0)
  {}

  unsigned int  varStoreOffset;
};

struct cff2_top_dict_op_serializer_t : cff_top_dict_op_serializer_t<>
{
  bool serialize (hb_serialize_context_t *c,
                  const op_str_t &opstr,
                  const cff2_sub_table_offsets_t &offsets) const
  {
    TRACE_SERIALIZE (this);

    switch (opstr.op)
    {
      case OpCode_vstore:
        return_trace (FontDict::serialize_offset4_op(c, opstr.op, offsets.varStoreOffset));

      default:
        return_trace (cff_top_dict_op_serializer_t<>::serialize (c, opstr, offsets));
    }
  }

  unsigned int calculate_serialized_size (const op_str_t &opstr) const
  {
    switch (opstr.op)
    {
      case OpCode_vstore:
        return OpCode_Size (OpCode_longintdict) + 4 + OpCode_Size (opstr.op);

      default:
        return cff_top_dict_op_serializer_t<>::calculate_serialized_size (opstr);
    }
  }
};

struct cff2_cs_opset_flatten_t : cff2_cs_opset_t<cff2_cs_opset_flatten_t, flatten_param_t>
{
  static void flush_args_and_op (op_code_t op, cff2_cs_interp_env_t &env, flatten_param_t& param)
  {
    switch (op)
    {
      case OpCode_return:
      case OpCode_endchar:
        /* dummy opcodes in CFF2. ignore */
        break;

      case OpCode_hstem:
      case OpCode_hstemhm:
      case OpCode_vstem:
      case OpCode_vstemhm:
      case OpCode_hintmask:
      case OpCode_cntrmask:
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

  static void flush_args (cff2_cs_interp_env_t &env, flatten_param_t& param)
  {
    for (unsigned int i = 0; i < env.argStack.get_count ();)
    {
      const blend_arg_t &arg = env.argStack[i];
      if (arg.blending ())
      {
        if (unlikely (!((arg.numValues > 0) && (env.argStack.get_count () >= arg.numValues))))
        {
          env.set_error ();
          return;
        }
        flatten_blends (arg, i, env, param);
        i += arg.numValues;
      }
      else
      {
        str_encoder_t  encoder (param.flatStr);
        encoder.encode_num (arg);
        i++;
      }
    }
    SUPER::flush_args (env, param);
  }

  static void flatten_blends (const blend_arg_t &arg, unsigned int i, cff2_cs_interp_env_t &env, flatten_param_t& param)
  {
    /* flatten the default values */
    str_encoder_t  encoder (param.flatStr);
    for (unsigned int j = 0; j < arg.numValues; j++)
    {
      const blend_arg_t &arg1 = env.argStack[i + j];
      if (unlikely (!((arg1.blending () && (arg.numValues == arg1.numValues) && (arg1.valueIndex == j) &&
              (arg1.deltas.length == env.get_region_count ())))))
      {
        env.set_error ();
        return;
      }
      encoder.encode_num (arg1);
    }
    /* flatten deltas for each value */
    for (unsigned int j = 0; j < arg.numValues; j++)
    {
      const blend_arg_t &arg1 = env.argStack[i + j];
      for (unsigned int k = 0; k < arg1.deltas.length; k++)
        encoder.encode_num (arg1.deltas[k]);
    }
    /* flatten the number of values followed by blend operator */
    encoder.encode_int (arg.numValues);
    encoder.encode_op (OpCode_blendcs);
  }

  static void flush_op (op_code_t op, cff2_cs_interp_env_t &env, flatten_param_t& param)
  {
    switch (op)
    {
      case OpCode_return:
      case OpCode_endchar:
        return;
      default:
        str_encoder_t  encoder (param.flatStr);
        encoder.encode_op (op);
    }
  }

  private:
  typedef cff2_cs_opset_t<cff2_cs_opset_flatten_t, flatten_param_t> SUPER;
  typedef cs_opset_t<blend_arg_t, cff2_cs_opset_flatten_t, cff2_cs_opset_flatten_t, cff2_cs_interp_env_t, flatten_param_t> CSOPSET;
};

struct cff2_cs_opset_subr_subset_t : cff2_cs_opset_t<cff2_cs_opset_subr_subset_t, subr_subset_param_t>
{
  static void process_op (op_code_t op, cff2_cs_interp_env_t &env, subr_subset_param_t& param)
  {
    switch (op) {

      case OpCode_return:
        param.current_parsed_str->set_parsed ();
        env.returnFromSubr ();
        param.set_current_str (env, false);
        break;

      case OpCode_endchar:
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
                                 cff2_cs_interp_env_t &env, subr_subset_param_t& param,
                                 cff2_biased_subrs_t& subrs, hb_set_t *closure)
  {
    byte_str_ref_t    str_ref = env.str_ref;
    env.callSubr (subrs, type);
    param.current_parsed_str->add_call_op (op, str_ref, env.context.subr_num);
    hb_set_add (closure, env.context.subr_num);
    param.set_current_str (env, true);
  }

  private:
  typedef cff2_cs_opset_t<cff2_cs_opset_subr_subset_t, subr_subset_param_t> SUPER;
};

struct cff2_subr_subsetter_t : subr_subsetter_t<cff2_subr_subsetter_t, CFF2Subrs, const OT::cff2::accelerator_subset_t, cff2_cs_interp_env_t, cff2_cs_opset_subr_subset_t>
{
  static void finalize_parsed_str (cff2_cs_interp_env_t &env, subr_subset_param_t& param, parsed_cs_str_t &charstring)
  {
    /* vsindex is inserted at the beginning of the charstring as necessary */
    if (env.seen_vsindex ())
    {
      number_t  ivs;
      ivs.set_int ((int)env.get_ivs ());
      charstring.set_prefix (ivs, OpCode_vsindexcs);
    }
  }
};

struct cff2_subset_plan {
  cff2_subset_plan ()
    : final_size (0),
      orig_fdcount (0),
      subset_fdcount(1),
      subset_fdselect_format (0),
      drop_hints (false),
      desubroutinize (false)
  {
    subset_fdselect_ranges.init ();
    fdmap.init ();
    subset_charstrings.init ();
    subset_globalsubrs.init ();
    subset_localsubrs.init ();
    privateDictInfos.init ();
  }

  ~cff2_subset_plan ()
  {
    subset_fdselect_ranges.fini ();
    fdmap.fini ();
    subset_charstrings.fini_deep ();
    subset_globalsubrs.fini_deep ();
    subset_localsubrs.fini_deep ();
    privateDictInfos.fini ();
  }

  bool create (const OT::cff2::accelerator_subset_t &acc,
              hb_subset_plan_t *plan)
  {
    final_size = 0;
    orig_fdcount = acc.fdArray->count;

    drop_hints = plan->drop_hints;
    desubroutinize = plan->desubroutinize;

    /* CFF2 header */
    final_size += OT::cff2::static_size;

    /* top dict */
    {
      cff2_top_dict_op_serializer_t topSzr;
      offsets.topDictInfo.size = TopDict::calculate_serialized_size (acc.topDict, topSzr);
      final_size += offsets.topDictInfo.size;
    }

    if (desubroutinize)
    {
      /* Flatten global & local subrs */
      subr_flattener_t<const OT::cff2::accelerator_subset_t, cff2_cs_interp_env_t, cff2_cs_opset_flatten_t>
                    flattener(acc, plan->glyphs, plan->drop_hints);
      if (!flattener.flatten (subset_charstrings))
        return false;

      /* no global/local subroutines */
      offsets.globalSubrsInfo.size = CFF2Subrs::calculate_serialized_size (1, 0, 0);
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
      offsets.globalSubrsInfo.size = CFF2Subrs::calculate_serialized_size (offsets.globalSubrsInfo.offSize, subset_globalsubrs.length, dataSize);

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
            offsets.localSubrsInfos[fd].size = CFF2Subrs::calculate_serialized_size (offsets.localSubrsInfos[fd].offSize, subset_localsubrs[fd].length, dataSize);
          }
        }
      }
    }

    /* global subrs */
    offsets.globalSubrsInfo.offset = final_size;
    final_size += offsets.globalSubrsInfo.size;

    /* variation store */
    if (acc.varStore != &Null(CFF2VariationStore))
    {
      offsets.varStoreOffset = final_size;
      final_size += acc.varStore->get_size ();
    }

    /* FDSelect */
    if (acc.fdSelect != &Null(CFF2FDSelect))
    {
      offsets.FDSelectInfo.offset = final_size;
      if (unlikely (!hb_plan_subset_cff_fdselect (plan->glyphs,
                                  orig_fdcount,
                                  *(const FDSelect *)acc.fdSelect,
                                  subset_fdcount,
                                  offsets.FDSelectInfo.size,
                                  subset_fdselect_format,
                                  subset_fdselect_ranges,
                                  fdmap)))
        return false;

      final_size += offsets.FDSelectInfo.size;
    }
    else
      fdmap.identity (1);

    /* FDArray (FDIndex) */
    {
      offsets.FDArrayInfo.offset = final_size;
      cff_font_dict_op_serializer_t fontSzr;
      unsigned int dictsSize = 0;
      for (unsigned int i = 0; i < acc.fontDicts.length; i++)
        if (fdmap.includes (i))
          dictsSize += FontDict::calculate_serialized_size (acc.fontDicts[i], fontSzr);

      offsets.FDArrayInfo.offSize = calcOffSize (dictsSize);
      final_size += CFF2Index::calculate_serialized_size (offsets.FDArrayInfo.offSize, subset_fdcount, dictsSize);
    }

    /* CharStrings */
    {
      offsets.charStringsInfo.offset = final_size;
      unsigned int dataSize = subset_charstrings.total_size ();
      offsets.charStringsInfo.offSize = calcOffSize (dataSize);
      final_size += CFF2CharStrings::calculate_serialized_size (offsets.charStringsInfo.offSize, plan->glyphs.length, dataSize);
    }

    /* private dicts & local subrs */
    offsets.privateDictsOffset = final_size;
    for (unsigned int i = 0; i < orig_fdcount; i++)
    {
      if (fdmap.includes (i))
      {
        bool  has_localsubrs = offsets.localSubrsInfos[i].size > 0;
        cff_private_dict_op_serializer_t privSzr (desubroutinize, drop_hints);
        unsigned int  priv_size = PrivateDict::calculate_serialized_size (acc.privateDicts[i], privSzr, has_localsubrs);
        table_info_t  privInfo = { final_size, priv_size, 0 };
        privateDictInfos.push (privInfo);
        final_size += privInfo.size;

        if (!plan->desubroutinize && has_localsubrs)
        {
          offsets.localSubrsInfos[i].offset = final_size;
          final_size += offsets.localSubrsInfos[i].size;
        }
      }
    }

    return true;
  }

  unsigned int get_final_size () const  { return final_size; }

  unsigned int  final_size;
  cff2_sub_table_offsets_t offsets;

  unsigned int    orig_fdcount;
  unsigned int    subset_fdcount;
  unsigned int    subset_fdselect_format;
  hb_vector_t<code_pair_t>   subset_fdselect_ranges;

  remap_t   fdmap;

  str_buff_vec_t            subset_charstrings;
  str_buff_vec_t            subset_globalsubrs;
  hb_vector_t<str_buff_vec_t> subset_localsubrs;
  hb_vector_t<table_info_t>  privateDictInfos;

  bool      drop_hints;
  bool      desubroutinize;
  cff2_subr_subsetter_t       subr_subsetter;
};

static inline bool _write_cff2 (const cff2_subset_plan &plan,
                                const OT::cff2::accelerator_subset_t  &acc,
                                const hb_vector_t<hb_codepoint_t>& glyphs,
                                unsigned int dest_sz,
                                void *dest)
{
  hb_serialize_context_t c (dest, dest_sz);

  OT::cff2 *cff2 = c.start_serialize<OT::cff2> ();
  if (unlikely (!c.extend_min (*cff2)))
    return false;

  /* header */
  cff2->version.major.set (0x02);
  cff2->version.minor.set (0x00);
  cff2->topDict.set (OT::cff2::static_size);

  /* top dict */
  {
    assert (cff2->topDict == (unsigned) (c.head - c.start));
    cff2->topDictSize.set (plan.offsets.topDictInfo.size);
    TopDict &dict = cff2 + cff2->topDict;
    cff2_top_dict_op_serializer_t topSzr;
    if (unlikely (!dict.serialize (&c, acc.topDict, topSzr, plan.offsets)))
    {
      DEBUG_MSG (SUBSET, nullptr, "failed to serialize CFF2 top dict");
      return false;
    }
  }

  /* global subrs */
  {
    assert (cff2->topDict + plan.offsets.topDictInfo.size == (unsigned) (c.head - c.start));
    CFF2Subrs *dest = c.start_embed <CFF2Subrs> ();
    if (unlikely (dest == nullptr)) return false;
    if (unlikely (!dest->serialize (&c, plan.offsets.globalSubrsInfo.offSize, plan.subset_globalsubrs)))
    {
      DEBUG_MSG (SUBSET, nullptr, "failed to serialize global subroutines");
      return false;
    }
  }

  /* variation store */
  if (acc.varStore != &Null(CFF2VariationStore))
  {
    assert (plan.offsets.varStoreOffset == (unsigned) (c.head - c.start));
    CFF2VariationStore *dest = c.start_embed<CFF2VariationStore> ();
    if (unlikely (!dest->serialize (&c, acc.varStore)))
    {
      DEBUG_MSG (SUBSET, nullptr, "failed to serialize CFF2 Variation Store");
      return false;
    }
  }

  /* FDSelect */
  if (acc.fdSelect != &Null(CFF2FDSelect))
  {
    assert (plan.offsets.FDSelectInfo.offset == (unsigned) (c.head - c.start));

    if (unlikely (!hb_serialize_cff_fdselect (&c, glyphs.length, *(const FDSelect *)acc.fdSelect, acc.fdArray->count,
                                              plan.subset_fdselect_format, plan.offsets.FDSelectInfo.size,
                                              plan.subset_fdselect_ranges)))
    {
      DEBUG_MSG (SUBSET, nullptr, "failed to serialize CFF2 subset FDSelect");
      return false;
    }
  }

  /* FDArray (FD Index) */
  {
    assert (plan.offsets.FDArrayInfo.offset == (unsigned) (c.head - c.start));
    CFF2FDArray  *fda = c.start_embed<CFF2FDArray> ();
    if (unlikely (fda == nullptr)) return false;
    cff_font_dict_op_serializer_t  fontSzr;
    if (unlikely (!fda->serialize (&c, plan.offsets.FDArrayInfo.offSize,
                                   acc.fontDicts, plan.subset_fdcount, plan.fdmap,
                                   fontSzr, plan.privateDictInfos)))
    {
      DEBUG_MSG (SUBSET, nullptr, "failed to serialize CFF2 FDArray");
      return false;
    }
  }

  /* CharStrings */
  {
    assert (plan.offsets.charStringsInfo.offset == (unsigned) (c.head - c.start));
    CFF2CharStrings  *cs = c.start_embed<CFF2CharStrings> ();
    if (unlikely (cs == nullptr)) return false;
    if (unlikely (!cs->serialize (&c, plan.offsets.charStringsInfo.offSize, plan.subset_charstrings)))
    {
      DEBUG_MSG (SUBSET, nullptr, "failed to serialize CFF2 CharStrings");
      return false;
    }
  }

  /* private dicts & local subrs */
  assert (plan.offsets.privateDictsOffset == (unsigned) (c.head - c.start));
  for (unsigned int i = 0; i < acc.privateDicts.length; i++)
  {
    if (plan.fdmap.includes (i))
    {
      PrivateDict  *pd = c.start_embed<PrivateDict> ();
      if (unlikely (pd == nullptr)) return false;
      unsigned int priv_size = plan.privateDictInfos[plan.fdmap[i]].size;
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
        CFF2Subrs *dest = c.start_embed <CFF2Subrs> ();
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
_hb_subset_cff2 (const OT::cff2::accelerator_subset_t  &acc,
                const char                    *data,
                hb_subset_plan_t                *plan,
                hb_blob_t                      **prime /* OUT */)
{
  cff2_subset_plan cff2_plan;

  if (unlikely (!cff2_plan.create (acc, plan)))
  {
    DEBUG_MSG(SUBSET, nullptr, "Failed to generate a cff2 subsetting plan.");
    return false;
  }

  unsigned int  cff2_prime_size = cff2_plan.get_final_size ();
  char *cff2_prime_data = (char *) calloc (1, cff2_prime_size);

  if (unlikely (!_write_cff2 (cff2_plan, acc, plan->glyphs,
                              cff2_prime_size, cff2_prime_data))) {
    DEBUG_MSG(SUBSET, nullptr, "Failed to write a subset cff2.");
    free (cff2_prime_data);
    return false;
  }

  *prime = hb_blob_create (cff2_prime_data,
                                cff2_prime_size,
                                HB_MEMORY_MODE_READONLY,
                                cff2_prime_data,
                                free);
  return true;
}

/**
 * hb_subset_cff2:
 * Subsets the CFF2 table according to a provided plan.
 *
 * Return value: subsetted cff2 table.
 **/
bool
hb_subset_cff2 (hb_subset_plan_t *plan,
                hb_blob_t       **prime /* OUT */)
{
  hb_blob_t *cff2_blob = hb_sanitize_context_t().reference_table<CFF::cff2> (plan->source);
  const char *data = hb_blob_get_data(cff2_blob, nullptr);

  OT::cff2::accelerator_subset_t acc;
  acc.init(plan->source);
  bool result = likely (acc.is_valid ()) &&
                _hb_subset_cff2 (acc, data, plan, prime);

  hb_blob_destroy (cff2_blob);
  acc.fini ();

  return result;
}
