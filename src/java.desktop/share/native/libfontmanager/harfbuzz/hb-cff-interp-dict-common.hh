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
#ifndef HB_CFF_INTERP_DICT_COMMON_HH
#define HB_CFF_INTERP_DICT_COMMON_HH

#include "hb-cff-interp-common.hh"
#include <math.h>
#include <float.h>

namespace CFF {

using namespace OT;

/* an opstr and the parsed out dict value(s) */
struct dict_val_t : op_str_t
{
  void init () { single_val.set_int (0); }
  void fini () {}

  number_t            single_val;
};

typedef dict_val_t num_dict_val_t;

template <typename VAL> struct dict_values_t : parsed_values_t<VAL> {};

template <typename OPSTR=op_str_t>
struct top_dict_values_t : dict_values_t<OPSTR>
{
  void init ()
  {
    dict_values_t<OPSTR>::init ();
    charStringsOffset = 0;
    FDArrayOffset = 0;
  }
  void fini () { dict_values_t<OPSTR>::fini (); }

  unsigned int calculate_serialized_op_size (const OPSTR& opstr) const
  {
    switch (opstr.op)
    {
      case OpCode_CharStrings:
      case OpCode_FDArray:
        return OpCode_Size (OpCode_longintdict) + 4 + OpCode_Size (opstr.op);

      default:
        return opstr.str.length;
    }
  }

  unsigned int  charStringsOffset;
  unsigned int  FDArrayOffset;
};

struct dict_opset_t : opset_t<number_t>
{
  static void process_op (op_code_t op, interp_env_t<number_t>& env)
  {
    switch (op) {
      case OpCode_longintdict:  /* 5-byte integer */
        env.argStack.push_longint_from_substr (env.str_ref);
        break;

      case OpCode_BCD:  /* real number */
        env.argStack.push_real (parse_bcd (env.str_ref));
        break;

      default:
        opset_t<number_t>::process_op (op, env);
        break;
    }
  }

  static double parse_bcd (byte_str_ref_t& str_ref)
  {
    bool    neg = false;
    double  int_part = 0;
    uint64_t frac_part = 0;
    uint32_t  frac_count = 0;
    bool    exp_neg = false;
    uint32_t  exp_part = 0;
    bool    exp_overflow = false;
    enum Part { INT_PART=0, FRAC_PART, EXP_PART } part = INT_PART;
    enum Nibble { DECIMAL=10, EXP_POS, EXP_NEG, RESERVED, NEG, END };
    const uint64_t MAX_FRACT = 0xFFFFFFFFFFFFFull; /* 1^52-1 */
    const uint32_t MAX_EXP = 0x7FFu; /* 1^11-1 */

    double  value = 0.0;
    unsigned char byte = 0;
    for (uint32_t i = 0;; i++)
    {
      char d;
      if ((i & 1) == 0)
      {
        if (!str_ref.avail ())
        {
          str_ref.set_error ();
          return 0.0;
        }
        byte = str_ref[0];
        str_ref.inc ();
        d = byte >> 4;
      }
      else
        d = byte & 0x0F;

      switch (d)
      {
        case RESERVED:
          str_ref.set_error ();
          return value;

        case END:
          value = (double)(neg? -int_part: int_part);
          if (frac_count > 0)
          {
            double frac = (frac_part / pow (10.0, (double)frac_count));
            if (neg) frac = -frac;
            value += frac;
          }
          if (unlikely (exp_overflow))
          {
            if (value == 0.0)
              return value;
            if (exp_neg)
              return neg? -DBL_MIN: DBL_MIN;
            else
              return neg? -DBL_MAX: DBL_MAX;
          }
          if (exp_part != 0)
          {
            if (exp_neg)
              value /= pow (10.0, (double)exp_part);
            else
              value *= pow (10.0, (double)exp_part);
          }
          return value;

        case NEG:
          if (i != 0)
          {
            str_ref.set_error ();
            return 0.0;
          }
          neg = true;
          break;

        case DECIMAL:
          if (part != INT_PART)
          {
            str_ref.set_error ();
            return value;
          }
          part = FRAC_PART;
          break;

        case EXP_NEG:
          exp_neg = true;
          HB_FALLTHROUGH;

        case EXP_POS:
          if (part == EXP_PART)
          {
            str_ref.set_error ();
            return value;
          }
          part = EXP_PART;
          break;

        default:
          switch (part) {
            default:
            case INT_PART:
              int_part = (int_part * 10) + d;
              break;

            case FRAC_PART:
              if (likely (frac_part <= MAX_FRACT / 10))
              {
                frac_part = (frac_part * 10) + (unsigned)d;
                frac_count++;
              }
              break;

            case EXP_PART:
              if (likely (exp_part * 10 + d <= MAX_EXP))
              {
                exp_part = (exp_part * 10) + d;
              }
              else
                exp_overflow = true;
              break;
          }
      }
    }

    return value;
  }

  static bool is_hint_op (op_code_t op)
  {
    switch (op)
    {
      case OpCode_BlueValues:
      case OpCode_OtherBlues:
      case OpCode_FamilyBlues:
      case OpCode_FamilyOtherBlues:
      case OpCode_StemSnapH:
      case OpCode_StemSnapV:
      case OpCode_StdHW:
      case OpCode_StdVW:
      case OpCode_BlueScale:
      case OpCode_BlueShift:
      case OpCode_BlueFuzz:
      case OpCode_ForceBold:
      case OpCode_LanguageGroup:
      case OpCode_ExpansionFactor:
        return true;
      default:
        return false;
    }
  }
};

template <typename VAL=op_str_t>
struct top_dict_opset_t : dict_opset_t
{
  static void process_op (op_code_t op, interp_env_t<number_t>& env, top_dict_values_t<VAL> & dictval)
  {
    switch (op) {
      case OpCode_CharStrings:
        dictval.charStringsOffset = env.argStack.pop_uint ();
        env.clear_args ();
        break;
      case OpCode_FDArray:
        dictval.FDArrayOffset = env.argStack.pop_uint ();
        env.clear_args ();
        break;
      case OpCode_FontMatrix:
        env.clear_args ();
        break;
      default:
        dict_opset_t::process_op (op, env);
        break;
    }
  }
};

template <typename OPSET, typename PARAM, typename ENV=num_interp_env_t>
struct dict_interpreter_t : interpreter_t<ENV>
{
  bool interpret (PARAM& param)
  {
    param.init ();
    while (SUPER::env.str_ref.avail ())
    {
      OPSET::process_op (SUPER::env.fetch_op (), SUPER::env, param);
      if (unlikely (SUPER::env.in_error ()))
        return false;
    }

    return true;
  }

  private:
  typedef interpreter_t<ENV> SUPER;
};

} /* namespace CFF */

#endif /* HB_CFF_INTERP_DICT_COMMON_HH */
