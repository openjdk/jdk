
#line 1 "hb-ot-shape-complex-use-machine.rl"
/*
 * Copyright © 2015  Mozilla Foundation.
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
 * Mozilla Author(s): Jonathan Kew
 * Google Author(s): Behdad Esfahbod
 */

#ifndef HB_OT_SHAPE_COMPLEX_USE_MACHINE_HH
#define HB_OT_SHAPE_COMPLEX_USE_MACHINE_HH

#include "hb-private.hh"


#line 38 "hb-ot-shape-complex-use-machine.hh"
static const unsigned char _use_syllable_machine_trans_keys[] = {
        1u, 1u, 0u, 43u, 21u, 21u, 8u, 39u, 8u, 39u, 1u, 1u, 8u, 39u, 8u, 39u,
        8u, 39u, 8u, 26u, 8u, 26u, 8u, 26u, 8u, 39u, 8u, 39u, 8u, 39u, 8u, 39u,
        8u, 39u, 8u, 39u, 8u, 39u, 8u, 39u, 8u, 39u, 8u, 39u, 8u, 39u, 8u, 39u,
        13u, 21u, 4u, 4u, 13u, 13u, 8u, 39u, 8u, 39u, 8u, 39u, 8u, 39u, 8u, 26u,
        8u, 26u, 8u, 26u, 8u, 39u, 8u, 39u, 8u, 39u, 8u, 39u, 8u, 39u, 8u, 39u,
        8u, 39u, 8u, 39u, 8u, 39u, 8u, 39u, 8u, 39u, 1u, 1u, 1u, 39u, 8u, 39u,
        21u, 42u, 41u, 42u, 42u, 42u, 1u, 5u, 0
};

static const char _use_syllable_machine_key_spans[] = {
        1, 44, 1, 32, 32, 1, 32, 32,
        32, 19, 19, 19, 32, 32, 32, 32,
        32, 32, 32, 32, 32, 32, 32, 32,
        9, 1, 1, 32, 32, 32, 32, 19,
        19, 19, 32, 32, 32, 32, 32, 32,
        32, 32, 32, 32, 32, 1, 39, 32,
        22, 2, 1, 5
};

static const short _use_syllable_machine_index_offsets[] = {
        0, 2, 47, 49, 82, 115, 117, 150,
        183, 216, 236, 256, 276, 309, 342, 375,
        408, 441, 474, 507, 540, 573, 606, 639,
        672, 682, 684, 686, 719, 752, 785, 818,
        838, 858, 878, 911, 944, 977, 1010, 1043,
        1076, 1109, 1142, 1175, 1208, 1241, 1243, 1283,
        1316, 1339, 1342, 1344
};

static const char _use_syllable_machine_indicies[] = {
        1, 0, 2, 3, 4, 2, 5, 3,
        4, 4, 6, 4, 4, 1, 7, 4,
        4, 4, 2, 2, 8, 9, 4, 4,
        10, 11, 12, 13, 14, 15, 16, 10,
        17, 18, 19, 20, 21, 22, 4, 23,
        24, 25, 4, 4, 4, 26, 4, 28,
        27, 30, 29, 29, 31, 32, 29, 29,
        29, 29, 29, 29, 29, 29, 33, 34,
        35, 36, 37, 38, 39, 40, 34, 41,
        33, 42, 43, 44, 45, 29, 46, 47,
        48, 29, 30, 29, 29, 31, 32, 29,
        29, 29, 29, 29, 29, 29, 29, 49,
        34, 35, 36, 37, 38, 39, 40, 34,
        41, 42, 42, 43, 44, 45, 29, 46,
        47, 48, 29, 31, 50, 30, 29, 29,
        31, 32, 29, 29, 29, 29, 29, 29,
        29, 29, 29, 34, 35, 36, 37, 38,
        39, 40, 34, 41, 42, 42, 43, 44,
        45, 29, 46, 47, 48, 29, 30, 29,
        29, 29, 29, 29, 29, 29, 29, 29,
        29, 29, 29, 29, 34, 35, 36, 37,
        38, 29, 29, 29, 29, 29, 29, 43,
        44, 45, 29, 46, 47, 48, 29, 30,
        29, 29, 29, 29, 29, 29, 29, 29,
        29, 29, 29, 29, 29, 29, 35, 36,
        37, 38, 29, 29, 29, 29, 29, 29,
        29, 29, 29, 29, 46, 47, 48, 29,
        30, 29, 29, 29, 29, 29, 29, 29,
        29, 29, 29, 29, 29, 29, 29, 29,
        36, 37, 38, 29, 30, 29, 29, 29,
        29, 29, 29, 29, 29, 29, 29, 29,
        29, 29, 29, 29, 29, 37, 38, 29,
        30, 29, 29, 29, 29, 29, 29, 29,
        29, 29, 29, 29, 29, 29, 29, 29,
        29, 29, 38, 29, 30, 29, 29, 29,
        29, 29, 29, 29, 29, 29, 29, 29,
        29, 29, 29, 29, 36, 37, 38, 29,
        29, 29, 29, 29, 29, 29, 29, 29,
        29, 46, 47, 48, 29, 30, 29, 29,
        29, 29, 29, 29, 29, 29, 29, 29,
        29, 29, 29, 29, 29, 36, 37, 38,
        29, 29, 29, 29, 29, 29, 29, 29,
        29, 29, 29, 47, 48, 29, 30, 29,
        29, 29, 29, 29, 29, 29, 29, 29,
        29, 29, 29, 29, 29, 29, 36, 37,
        38, 29, 29, 29, 29, 29, 29, 29,
        29, 29, 29, 29, 29, 48, 29, 30,
        29, 29, 29, 29, 29, 29, 29, 29,
        29, 29, 29, 29, 29, 29, 35, 36,
        37, 38, 29, 29, 29, 29, 29, 29,
        43, 44, 45, 29, 46, 47, 48, 29,
        30, 29, 29, 29, 29, 29, 29, 29,
        29, 29, 29, 29, 29, 29, 29, 35,
        36, 37, 38, 29, 29, 29, 29, 29,
        29, 29, 44, 45, 29, 46, 47, 48,
        29, 30, 29, 29, 29, 29, 29, 29,
        29, 29, 29, 29, 29, 29, 29, 29,
        35, 36, 37, 38, 29, 29, 29, 29,
        29, 29, 29, 29, 45, 29, 46, 47,
        48, 29, 30, 29, 29, 29, 29, 29,
        29, 29, 29, 29, 29, 29, 29, 29,
        34, 35, 36, 37, 38, 29, 40, 34,
        29, 29, 29, 43, 44, 45, 29, 46,
        47, 48, 29, 30, 29, 29, 29, 29,
        29, 29, 29, 29, 29, 29, 29, 29,
        29, 34, 35, 36, 37, 38, 29, 51,
        34, 29, 29, 29, 43, 44, 45, 29,
        46, 47, 48, 29, 30, 29, 29, 29,
        29, 29, 29, 29, 29, 29, 29, 29,
        29, 29, 34, 35, 36, 37, 38, 29,
        29, 34, 29, 29, 29, 43, 44, 45,
        29, 46, 47, 48, 29, 30, 29, 29,
        29, 29, 29, 29, 29, 29, 29, 29,
        29, 29, 29, 34, 35, 36, 37, 38,
        39, 40, 34, 29, 29, 29, 43, 44,
        45, 29, 46, 47, 48, 29, 30, 29,
        29, 31, 32, 29, 29, 29, 29, 29,
        29, 29, 29, 29, 34, 35, 36, 37,
        38, 39, 40, 34, 41, 29, 42, 43,
        44, 45, 29, 46, 47, 48, 29, 30,
        29, 29, 31, 32, 29, 29, 29, 29,
        29, 29, 29, 29, 29, 34, 35, 36,
        37, 38, 39, 40, 34, 41, 33, 42,
        43, 44, 45, 29, 46, 47, 48, 29,
        53, 52, 52, 52, 52, 52, 52, 52,
        54, 52, 5, 55, 53, 52, 6, 56,
        56, 1, 57, 56, 56, 56, 56, 56,
        56, 56, 56, 58, 10, 11, 12, 13,
        14, 15, 16, 10, 17, 19, 19, 20,
        21, 22, 56, 23, 24, 25, 56, 6,
        56, 56, 1, 57, 56, 56, 56, 56,
        56, 56, 56, 56, 56, 10, 11, 12,
        13, 14, 15, 16, 10, 17, 19, 19,
        20, 21, 22, 56, 23, 24, 25, 56,
        6, 56, 56, 56, 56, 56, 56, 56,
        56, 56, 56, 56, 56, 56, 10, 11,
        12, 13, 14, 56, 56, 56, 56, 56,
        56, 20, 21, 22, 56, 23, 24, 25,
        56, 6, 56, 56, 56, 56, 56, 56,
        56, 56, 56, 56, 56, 56, 56, 56,
        11, 12, 13, 14, 56, 56, 56, 56,
        56, 56, 56, 56, 56, 56, 23, 24,
        25, 56, 6, 56, 56, 56, 56, 56,
        56, 56, 56, 56, 56, 56, 56, 56,
        56, 56, 12, 13, 14, 56, 6, 56,
        56, 56, 56, 56, 56, 56, 56, 56,
        56, 56, 56, 56, 56, 56, 56, 13,
        14, 56, 6, 56, 56, 56, 56, 56,
        56, 56, 56, 56, 56, 56, 56, 56,
        56, 56, 56, 56, 14, 56, 6, 56,
        56, 56, 56, 56, 56, 56, 56, 56,
        56, 56, 56, 56, 56, 56, 12, 13,
        14, 56, 56, 56, 56, 56, 56, 56,
        56, 56, 56, 23, 24, 25, 56, 6,
        56, 56, 56, 56, 56, 56, 56, 56,
        56, 56, 56, 56, 56, 56, 56, 12,
        13, 14, 56, 56, 56, 56, 56, 56,
        56, 56, 56, 56, 56, 24, 25, 56,
        6, 56, 56, 56, 56, 56, 56, 56,
        56, 56, 56, 56, 56, 56, 56, 56,
        12, 13, 14, 56, 56, 56, 56, 56,
        56, 56, 56, 56, 56, 56, 56, 25,
        56, 6, 56, 56, 56, 56, 56, 56,
        56, 56, 56, 56, 56, 56, 56, 56,
        11, 12, 13, 14, 56, 56, 56, 56,
        56, 56, 20, 21, 22, 56, 23, 24,
        25, 56, 6, 56, 56, 56, 56, 56,
        56, 56, 56, 56, 56, 56, 56, 56,
        56, 11, 12, 13, 14, 56, 56, 56,
        56, 56, 56, 56, 21, 22, 56, 23,
        24, 25, 56, 6, 56, 56, 56, 56,
        56, 56, 56, 56, 56, 56, 56, 56,
        56, 56, 11, 12, 13, 14, 56, 56,
        56, 56, 56, 56, 56, 56, 22, 56,
        23, 24, 25, 56, 6, 56, 56, 56,
        56, 56, 56, 56, 56, 56, 56, 56,
        56, 56, 10, 11, 12, 13, 14, 56,
        16, 10, 56, 56, 56, 20, 21, 22,
        56, 23, 24, 25, 56, 6, 56, 56,
        56, 56, 56, 56, 56, 56, 56, 56,
        56, 56, 56, 10, 11, 12, 13, 14,
        56, 59, 10, 56, 56, 56, 20, 21,
        22, 56, 23, 24, 25, 56, 6, 56,
        56, 56, 56, 56, 56, 56, 56, 56,
        56, 56, 56, 56, 10, 11, 12, 13,
        14, 56, 56, 10, 56, 56, 56, 20,
        21, 22, 56, 23, 24, 25, 56, 6,
        56, 56, 56, 56, 56, 56, 56, 56,
        56, 56, 56, 56, 56, 10, 11, 12,
        13, 14, 15, 16, 10, 56, 56, 56,
        20, 21, 22, 56, 23, 24, 25, 56,
        6, 56, 56, 1, 57, 56, 56, 56,
        56, 56, 56, 56, 56, 56, 10, 11,
        12, 13, 14, 15, 16, 10, 17, 56,
        19, 20, 21, 22, 56, 23, 24, 25,
        56, 1, 60, 3, 56, 56, 56, 3,
        56, 56, 6, 56, 56, 1, 57, 56,
        56, 56, 56, 56, 56, 56, 56, 56,
        10, 11, 12, 13, 14, 15, 16, 10,
        17, 18, 19, 20, 21, 22, 56, 23,
        24, 25, 56, 6, 56, 56, 1, 57,
        56, 56, 56, 56, 56, 56, 56, 56,
        56, 10, 11, 12, 13, 14, 15, 16,
        10, 17, 18, 19, 20, 21, 22, 56,
        23, 24, 25, 56, 62, 61, 61, 61,
        61, 61, 61, 61, 61, 61, 61, 61,
        61, 61, 61, 61, 61, 61, 61, 61,
        62, 63, 61, 62, 63, 61, 63, 61,
        3, 60, 60, 60, 3, 60, 0
};

static const char _use_syllable_machine_trans_targs[] = {
        1, 27, 2, 3, 1, 24, 1, 45,
        46, 48, 29, 30, 31, 32, 33, 40,
        41, 43, 47, 44, 37, 38, 39, 34,
        35, 36, 51, 1, 1, 1, 1, 4,
        5, 23, 7, 8, 9, 10, 11, 18,
        19, 21, 22, 15, 16, 17, 12, 13,
        14, 6, 1, 20, 1, 25, 26, 1,
        1, 0, 28, 42, 1, 1, 49, 50
};

static const char _use_syllable_machine_trans_actions[] = {
        1, 2, 0, 0, 5, 0, 6, 0,
        2, 0, 0, 0, 0, 0, 0, 0,
        0, 0, 2, 2, 0, 0, 0, 0,
        0, 0, 0, 7, 8, 9, 10, 0,
        0, 0, 0, 0, 0, 0, 0, 0,
        0, 0, 0, 0, 0, 0, 0, 0,
        0, 0, 11, 0, 12, 0, 0, 13,
        14, 0, 2, 0, 15, 16, 0, 0
};

static const char _use_syllable_machine_to_state_actions[] = {
        0, 3, 0, 0, 0, 0, 0, 0,
        0, 0, 0, 0, 0, 0, 0, 0,
        0, 0, 0, 0, 0, 0, 0, 0,
        0, 0, 0, 0, 0, 0, 0, 0,
        0, 0, 0, 0, 0, 0, 0, 0,
        0, 0, 0, 0, 0, 0, 0, 0,
        0, 0, 0, 0
};

static const char _use_syllable_machine_from_state_actions[] = {
        0, 4, 0, 0, 0, 0, 0, 0,
        0, 0, 0, 0, 0, 0, 0, 0,
        0, 0, 0, 0, 0, 0, 0, 0,
        0, 0, 0, 0, 0, 0, 0, 0,
        0, 0, 0, 0, 0, 0, 0, 0,
        0, 0, 0, 0, 0, 0, 0, 0,
        0, 0, 0, 0
};

static const short _use_syllable_machine_eof_trans[] = {
        1, 0, 28, 30, 30, 51, 30, 30,
        30, 30, 30, 30, 30, 30, 30, 30,
        30, 30, 30, 30, 30, 30, 30, 30,
        53, 56, 53, 57, 57, 57, 57, 57,
        57, 57, 57, 57, 57, 57, 57, 57,
        57, 57, 57, 57, 57, 61, 57, 57,
        62, 62, 62, 61
};

static const int use_syllable_machine_start = 1;
static const int use_syllable_machine_first_final = 1;
static const int use_syllable_machine_error = -1;

static const int use_syllable_machine_en_main = 1;


#line 38 "hb-ot-shape-complex-use-machine.rl"



#line 140 "hb-ot-shape-complex-use-machine.rl"


#define found_syllable(syllable_type) \
  HB_STMT_START { \
    if (0) fprintf (stderr, "syllable %d..%d %s\n", last, p+1, #syllable_type); \
    for (unsigned int i = last; i < p+1; i++) \
      info[i].syllable() = (syllable_serial << 4) | syllable_type; \
    last = p+1; \
    syllable_serial++; \
    if (unlikely (syllable_serial == 16)) syllable_serial = 1; \
  } HB_STMT_END

static void
find_syllables (hb_buffer_t *buffer)
{
  unsigned int p, pe, eof, ts HB_UNUSED, te HB_UNUSED, act HB_UNUSED;
  int cs;
  hb_glyph_info_t *info = buffer->info;

#line 324 "hb-ot-shape-complex-use-machine.hh"
        {
        cs = use_syllable_machine_start;
        ts = 0;
        te = 0;
        act = 0;
        }

#line 161 "hb-ot-shape-complex-use-machine.rl"


  p = 0;
  pe = eof = buffer->len;

  unsigned int last = 0;
  unsigned int syllable_serial = 1;

#line 341 "hb-ot-shape-complex-use-machine.hh"
        {
        int _slen;
        int _trans;
        const unsigned char *_keys;
        const char *_inds;
        if ( p == pe )
                goto _test_eof;
_resume:
        switch ( _use_syllable_machine_from_state_actions[cs] ) {
        case 4:
#line 1 "NONE"
        {ts = p;}
        break;
#line 355 "hb-ot-shape-complex-use-machine.hh"
        }

        _keys = _use_syllable_machine_trans_keys + (cs<<1);
        _inds = _use_syllable_machine_indicies + _use_syllable_machine_index_offsets[cs];

        _slen = _use_syllable_machine_key_spans[cs];
        _trans = _inds[ _slen > 0 && _keys[0] <=( info[p].use_category()) &&
                ( info[p].use_category()) <= _keys[1] ?
                ( info[p].use_category()) - _keys[0] : _slen ];

_eof_trans:
        cs = _use_syllable_machine_trans_targs[_trans];

        if ( _use_syllable_machine_trans_actions[_trans] == 0 )
                goto _again;

        switch ( _use_syllable_machine_trans_actions[_trans] ) {
        case 2:
#line 1 "NONE"
        {te = p+1;}
        break;
        case 8:
#line 129 "hb-ot-shape-complex-use-machine.rl"
        {te = p+1;{ found_syllable (independent_cluster); }}
        break;
        case 10:
#line 131 "hb-ot-shape-complex-use-machine.rl"
        {te = p+1;{ found_syllable (standard_cluster); }}
        break;
        case 6:
#line 135 "hb-ot-shape-complex-use-machine.rl"
        {te = p+1;{ found_syllable (broken_cluster); }}
        break;
        case 5:
#line 136 "hb-ot-shape-complex-use-machine.rl"
        {te = p+1;{ found_syllable (non_cluster); }}
        break;
        case 7:
#line 129 "hb-ot-shape-complex-use-machine.rl"
        {te = p;p--;{ found_syllable (independent_cluster); }}
        break;
        case 11:
#line 130 "hb-ot-shape-complex-use-machine.rl"
        {te = p;p--;{ found_syllable (virama_terminated_cluster); }}
        break;
        case 9:
#line 131 "hb-ot-shape-complex-use-machine.rl"
        {te = p;p--;{ found_syllable (standard_cluster); }}
        break;
        case 13:
#line 132 "hb-ot-shape-complex-use-machine.rl"
        {te = p;p--;{ found_syllable (number_joiner_terminated_cluster); }}
        break;
        case 12:
#line 133 "hb-ot-shape-complex-use-machine.rl"
        {te = p;p--;{ found_syllable (numeral_cluster); }}
        break;
        case 16:
#line 134 "hb-ot-shape-complex-use-machine.rl"
        {te = p;p--;{ found_syllable (symbol_cluster); }}
        break;
        case 14:
#line 135 "hb-ot-shape-complex-use-machine.rl"
        {te = p;p--;{ found_syllable (broken_cluster); }}
        break;
        case 15:
#line 136 "hb-ot-shape-complex-use-machine.rl"
        {te = p;p--;{ found_syllable (non_cluster); }}
        break;
        case 1:
#line 135 "hb-ot-shape-complex-use-machine.rl"
        {{p = ((te))-1;}{ found_syllable (broken_cluster); }}
        break;
#line 429 "hb-ot-shape-complex-use-machine.hh"
        }

_again:
        switch ( _use_syllable_machine_to_state_actions[cs] ) {
        case 3:
#line 1 "NONE"
        {ts = 0;}
        break;
#line 438 "hb-ot-shape-complex-use-machine.hh"
        }

        if ( ++p != pe )
                goto _resume;
        _test_eof: {}
        if ( p == eof )
        {
        if ( _use_syllable_machine_eof_trans[cs] > 0 ) {
                _trans = _use_syllable_machine_eof_trans[cs] - 1;
                goto _eof_trans;
        }
        }

        }

#line 170 "hb-ot-shape-complex-use-machine.rl"

}

#undef found_syllable

#endif /* HB_OT_SHAPE_COMPLEX_USE_MACHINE_HH */
