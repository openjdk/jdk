
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
        1u, 1u, 0u, 39u, 21u, 21u, 8u, 39u, 8u, 39u, 1u, 1u, 8u, 39u, 8u, 39u, 
        8u, 39u, 8u, 26u, 8u, 26u, 8u, 26u, 8u, 39u, 8u, 39u, 8u, 39u, 8u, 39u, 
        8u, 39u, 8u, 39u, 8u, 39u, 8u, 39u, 8u, 39u, 8u, 39u, 8u, 39u, 13u, 21u, 
        4u, 4u, 13u, 13u, 8u, 39u, 8u, 39u, 8u, 39u, 8u, 39u, 8u, 26u, 8u, 26u, 
        8u, 26u, 8u, 39u, 8u, 39u, 8u, 39u, 8u, 39u, 8u, 39u, 8u, 39u, 8u, 39u, 
        8u, 39u, 8u, 39u, 8u, 39u, 1u, 1u, 1u, 39u, 8u, 39u, 21u, 42u, 41u, 42u, 
        42u, 42u, 0
};

static const char _use_syllable_machine_key_spans[] = {
        1, 40, 1, 32, 32, 1, 32, 32, 
        32, 19, 19, 19, 32, 32, 32, 32, 
        32, 32, 32, 32, 32, 32, 32, 9, 
        1, 1, 32, 32, 32, 32, 19, 19, 
        19, 32, 32, 32, 32, 32, 32, 32, 
        32, 32, 32, 1, 39, 32, 22, 2, 
        1
};

static const short _use_syllable_machine_index_offsets[] = {
        0, 2, 43, 45, 78, 111, 113, 146, 
        179, 212, 232, 252, 272, 305, 338, 371, 
        404, 437, 470, 503, 536, 569, 602, 635, 
        645, 647, 649, 682, 715, 748, 781, 801, 
        821, 841, 874, 907, 940, 973, 1006, 1039, 
        1072, 1105, 1138, 1171, 1173, 1213, 1246, 1269, 
        1272
};

static const char _use_syllable_machine_indicies[] = {
        1, 0, 2, 3, 4, 2, 5, 3, 
        4, 4, 6, 4, 4, 1, 7, 4, 
        4, 4, 2, 2, 8, 9, 4, 4, 
        10, 11, 12, 13, 14, 15, 16, 10, 
        17, 18, 19, 20, 21, 22, 4, 23, 
        24, 25, 4, 27, 26, 29, 28, 28, 
        30, 31, 28, 28, 28, 28, 28, 28, 
        28, 28, 32, 33, 34, 35, 36, 37, 
        38, 39, 33, 40, 32, 41, 42, 43, 
        44, 28, 45, 46, 47, 28, 29, 28, 
        28, 30, 31, 28, 28, 28, 28, 28, 
        28, 28, 28, 48, 33, 34, 35, 36, 
        37, 38, 39, 33, 40, 41, 41, 42, 
        43, 44, 28, 45, 46, 47, 28, 30, 
        49, 29, 28, 28, 30, 31, 28, 28, 
        28, 28, 28, 28, 28, 28, 28, 33, 
        34, 35, 36, 37, 38, 39, 33, 40, 
        41, 41, 42, 43, 44, 28, 45, 46, 
        47, 28, 29, 28, 28, 28, 28, 28, 
        28, 28, 28, 28, 28, 28, 28, 28, 
        33, 34, 35, 36, 37, 28, 28, 28, 
        28, 28, 28, 42, 43, 44, 28, 45, 
        46, 47, 28, 29, 28, 28, 28, 28, 
        28, 28, 28, 28, 28, 28, 28, 28, 
        28, 28, 34, 35, 36, 37, 28, 28, 
        28, 28, 28, 28, 28, 28, 28, 28, 
        45, 46, 47, 28, 29, 28, 28, 28, 
        28, 28, 28, 28, 28, 28, 28, 28, 
        28, 28, 28, 28, 35, 36, 37, 28, 
        29, 28, 28, 28, 28, 28, 28, 28, 
        28, 28, 28, 28, 28, 28, 28, 28, 
        28, 36, 37, 28, 29, 28, 28, 28, 
        28, 28, 28, 28, 28, 28, 28, 28, 
        28, 28, 28, 28, 28, 28, 37, 28, 
        29, 28, 28, 28, 28, 28, 28, 28, 
        28, 28, 28, 28, 28, 28, 28, 28, 
        35, 36, 37, 28, 28, 28, 28, 28, 
        28, 28, 28, 28, 28, 45, 46, 47, 
        28, 29, 28, 28, 28, 28, 28, 28, 
        28, 28, 28, 28, 28, 28, 28, 28, 
        28, 35, 36, 37, 28, 28, 28, 28, 
        28, 28, 28, 28, 28, 28, 28, 46, 
        47, 28, 29, 28, 28, 28, 28, 28, 
        28, 28, 28, 28, 28, 28, 28, 28, 
        28, 28, 35, 36, 37, 28, 28, 28, 
        28, 28, 28, 28, 28, 28, 28, 28, 
        28, 47, 28, 29, 28, 28, 28, 28, 
        28, 28, 28, 28, 28, 28, 28, 28, 
        28, 28, 34, 35, 36, 37, 28, 28, 
        28, 28, 28, 28, 42, 43, 44, 28, 
        45, 46, 47, 28, 29, 28, 28, 28, 
        28, 28, 28, 28, 28, 28, 28, 28, 
        28, 28, 28, 34, 35, 36, 37, 28, 
        28, 28, 28, 28, 28, 28, 43, 44, 
        28, 45, 46, 47, 28, 29, 28, 28, 
        28, 28, 28, 28, 28, 28, 28, 28, 
        28, 28, 28, 28, 34, 35, 36, 37, 
        28, 28, 28, 28, 28, 28, 28, 28, 
        44, 28, 45, 46, 47, 28, 29, 28, 
        28, 28, 28, 28, 28, 28, 28, 28, 
        28, 28, 28, 28, 33, 34, 35, 36, 
        37, 28, 39, 33, 28, 28, 28, 42, 
        43, 44, 28, 45, 46, 47, 28, 29, 
        28, 28, 28, 28, 28, 28, 28, 28, 
        28, 28, 28, 28, 28, 33, 34, 35, 
        36, 37, 28, 28, 33, 28, 28, 28, 
        42, 43, 44, 28, 45, 46, 47, 28, 
        29, 28, 28, 28, 28, 28, 28, 28, 
        28, 28, 28, 28, 28, 28, 33, 34, 
        35, 36, 37, 38, 39, 33, 28, 28, 
        28, 42, 43, 44, 28, 45, 46, 47, 
        28, 29, 28, 28, 30, 31, 28, 28, 
        28, 28, 28, 28, 28, 28, 28, 33, 
        34, 35, 36, 37, 38, 39, 33, 40, 
        28, 41, 42, 43, 44, 28, 45, 46, 
        47, 28, 29, 28, 28, 30, 31, 28, 
        28, 28, 28, 28, 28, 28, 28, 28, 
        33, 34, 35, 36, 37, 38, 39, 33, 
        40, 32, 41, 42, 43, 44, 28, 45, 
        46, 47, 28, 51, 50, 50, 50, 50, 
        50, 50, 50, 52, 50, 5, 53, 51, 
        50, 6, 54, 54, 1, 55, 54, 54, 
        54, 54, 54, 54, 54, 54, 56, 10, 
        11, 12, 13, 14, 15, 16, 10, 17, 
        19, 19, 20, 21, 22, 54, 23, 24, 
        25, 54, 6, 54, 54, 1, 55, 54, 
        54, 54, 54, 54, 54, 54, 54, 54, 
        10, 11, 12, 13, 14, 15, 16, 10, 
        17, 19, 19, 20, 21, 22, 54, 23, 
        24, 25, 54, 6, 54, 54, 54, 54, 
        54, 54, 54, 54, 54, 54, 54, 54, 
        54, 10, 11, 12, 13, 14, 54, 54, 
        54, 54, 54, 54, 20, 21, 22, 54, 
        23, 24, 25, 54, 6, 54, 54, 54, 
        54, 54, 54, 54, 54, 54, 54, 54, 
        54, 54, 54, 11, 12, 13, 14, 54, 
        54, 54, 54, 54, 54, 54, 54, 54, 
        54, 23, 24, 25, 54, 6, 54, 54, 
        54, 54, 54, 54, 54, 54, 54, 54, 
        54, 54, 54, 54, 54, 12, 13, 14, 
        54, 6, 54, 54, 54, 54, 54, 54, 
        54, 54, 54, 54, 54, 54, 54, 54, 
        54, 54, 13, 14, 54, 6, 54, 54, 
        54, 54, 54, 54, 54, 54, 54, 54, 
        54, 54, 54, 54, 54, 54, 54, 14, 
        54, 6, 54, 54, 54, 54, 54, 54, 
        54, 54, 54, 54, 54, 54, 54, 54, 
        54, 12, 13, 14, 54, 54, 54, 54, 
        54, 54, 54, 54, 54, 54, 23, 24, 
        25, 54, 6, 54, 54, 54, 54, 54, 
        54, 54, 54, 54, 54, 54, 54, 54, 
        54, 54, 12, 13, 14, 54, 54, 54, 
        54, 54, 54, 54, 54, 54, 54, 54, 
        24, 25, 54, 6, 54, 54, 54, 54, 
        54, 54, 54, 54, 54, 54, 54, 54, 
        54, 54, 54, 12, 13, 14, 54, 54, 
        54, 54, 54, 54, 54, 54, 54, 54, 
        54, 54, 25, 54, 6, 54, 54, 54, 
        54, 54, 54, 54, 54, 54, 54, 54, 
        54, 54, 54, 11, 12, 13, 14, 54, 
        54, 54, 54, 54, 54, 20, 21, 22, 
        54, 23, 24, 25, 54, 6, 54, 54, 
        54, 54, 54, 54, 54, 54, 54, 54, 
        54, 54, 54, 54, 11, 12, 13, 14, 
        54, 54, 54, 54, 54, 54, 54, 21, 
        22, 54, 23, 24, 25, 54, 6, 54, 
        54, 54, 54, 54, 54, 54, 54, 54, 
        54, 54, 54, 54, 54, 11, 12, 13, 
        14, 54, 54, 54, 54, 54, 54, 54, 
        54, 22, 54, 23, 24, 25, 54, 6, 
        54, 54, 54, 54, 54, 54, 54, 54, 
        54, 54, 54, 54, 54, 10, 11, 12, 
        13, 14, 54, 16, 10, 54, 54, 54, 
        20, 21, 22, 54, 23, 24, 25, 54, 
        6, 54, 54, 54, 54, 54, 54, 54, 
        54, 54, 54, 54, 54, 54, 10, 11, 
        12, 13, 14, 54, 54, 10, 54, 54, 
        54, 20, 21, 22, 54, 23, 24, 25, 
        54, 6, 54, 54, 54, 54, 54, 54, 
        54, 54, 54, 54, 54, 54, 54, 10, 
        11, 12, 13, 14, 15, 16, 10, 54, 
        54, 54, 20, 21, 22, 54, 23, 24, 
        25, 54, 6, 54, 54, 1, 55, 54, 
        54, 54, 54, 54, 54, 54, 54, 54, 
        10, 11, 12, 13, 14, 15, 16, 10, 
        17, 54, 19, 20, 21, 22, 54, 23, 
        24, 25, 54, 1, 57, 3, 54, 54, 
        54, 3, 54, 54, 6, 54, 54, 1, 
        55, 54, 54, 54, 54, 54, 54, 54, 
        54, 54, 10, 11, 12, 13, 14, 15, 
        16, 10, 17, 18, 19, 20, 21, 22, 
        54, 23, 24, 25, 54, 6, 54, 54, 
        1, 55, 54, 54, 54, 54, 54, 54, 
        54, 54, 54, 10, 11, 12, 13, 14, 
        15, 16, 10, 17, 18, 19, 20, 21, 
        22, 54, 23, 24, 25, 54, 59, 58, 
        58, 58, 58, 58, 58, 58, 58, 58, 
        58, 58, 58, 58, 58, 58, 58, 58, 
        58, 58, 59, 60, 58, 59, 60, 58, 
        60, 58, 0
};

static const char _use_syllable_machine_trans_targs[] = {
        1, 26, 2, 3, 1, 23, 1, 43, 
        44, 46, 28, 29, 30, 31, 32, 39, 
        40, 41, 45, 42, 36, 37, 38, 33, 
        34, 35, 1, 1, 1, 1, 4, 5, 
        22, 7, 8, 9, 10, 11, 18, 19, 
        20, 21, 15, 16, 17, 12, 13, 14, 
        6, 1, 1, 24, 25, 1, 1, 0, 
        27, 1, 1, 47, 48
};

static const char _use_syllable_machine_trans_actions[] = {
        1, 2, 0, 0, 5, 0, 6, 0, 
        2, 0, 0, 0, 0, 0, 0, 0, 
        0, 0, 2, 2, 0, 0, 0, 0, 
        0, 0, 7, 8, 9, 10, 0, 0, 
        0, 0, 0, 0, 0, 0, 0, 0, 
        0, 0, 0, 0, 0, 0, 0, 0, 
        0, 11, 12, 0, 0, 13, 14, 0, 
        2, 15, 16, 0, 0
};

static const char _use_syllable_machine_to_state_actions[] = {
        0, 3, 0, 0, 0, 0, 0, 0, 
        0, 0, 0, 0, 0, 0, 0, 0, 
        0, 0, 0, 0, 0, 0, 0, 0, 
        0, 0, 0, 0, 0, 0, 0, 0, 
        0, 0, 0, 0, 0, 0, 0, 0, 
        0, 0, 0, 0, 0, 0, 0, 0, 
        0
};

static const char _use_syllable_machine_from_state_actions[] = {
        0, 4, 0, 0, 0, 0, 0, 0, 
        0, 0, 0, 0, 0, 0, 0, 0, 
        0, 0, 0, 0, 0, 0, 0, 0, 
        0, 0, 0, 0, 0, 0, 0, 0, 
        0, 0, 0, 0, 0, 0, 0, 0, 
        0, 0, 0, 0, 0, 0, 0, 0, 
        0
};

static const short _use_syllable_machine_eof_trans[] = {
        1, 0, 27, 29, 29, 50, 29, 29, 
        29, 29, 29, 29, 29, 29, 29, 29, 
        29, 29, 29, 29, 29, 29, 29, 51, 
        54, 51, 55, 55, 55, 55, 55, 55, 
        55, 55, 55, 55, 55, 55, 55, 55, 
        55, 55, 55, 58, 55, 55, 59, 59, 
        59
};

static const int use_syllable_machine_start = 1;
static const int use_syllable_machine_first_final = 1;
static const int use_syllable_machine_error = -1;

static const int use_syllable_machine_en_main = 1;


#line 38 "hb-ot-shape-complex-use-machine.rl"



#line 138 "hb-ot-shape-complex-use-machine.rl"


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
  
#line 315 "hb-ot-shape-complex-use-machine.hh"
        {
        cs = use_syllable_machine_start;
        ts = 0;
        te = 0;
        act = 0;
        }

#line 159 "hb-ot-shape-complex-use-machine.rl"


  p = 0;
  pe = eof = buffer->len;

  unsigned int last = 0;
  unsigned int syllable_serial = 1;
  
#line 332 "hb-ot-shape-complex-use-machine.hh"
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
#line 346 "hb-ot-shape-complex-use-machine.hh"
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
#line 127 "hb-ot-shape-complex-use-machine.rl"
        {te = p+1;{ found_syllable (independent_cluster); }}
        break;
        case 10:
#line 129 "hb-ot-shape-complex-use-machine.rl"
        {te = p+1;{ found_syllable (standard_cluster); }}
        break;
        case 6:
#line 133 "hb-ot-shape-complex-use-machine.rl"
        {te = p+1;{ found_syllable (broken_cluster); }}
        break;
        case 5:
#line 134 "hb-ot-shape-complex-use-machine.rl"
        {te = p+1;{ found_syllable (non_cluster); }}
        break;
        case 7:
#line 127 "hb-ot-shape-complex-use-machine.rl"
        {te = p;p--;{ found_syllable (independent_cluster); }}
        break;
        case 11:
#line 128 "hb-ot-shape-complex-use-machine.rl"
        {te = p;p--;{ found_syllable (virama_terminated_cluster); }}
        break;
        case 9:
#line 129 "hb-ot-shape-complex-use-machine.rl"
        {te = p;p--;{ found_syllable (standard_cluster); }}
        break;
        case 13:
#line 130 "hb-ot-shape-complex-use-machine.rl"
        {te = p;p--;{ found_syllable (number_joiner_terminated_cluster); }}
        break;
        case 12:
#line 131 "hb-ot-shape-complex-use-machine.rl"
        {te = p;p--;{ found_syllable (numeral_cluster); }}
        break;
        case 16:
#line 132 "hb-ot-shape-complex-use-machine.rl"
        {te = p;p--;{ found_syllable (symbol_cluster); }}
        break;
        case 14:
#line 133 "hb-ot-shape-complex-use-machine.rl"
        {te = p;p--;{ found_syllable (broken_cluster); }}
        break;
        case 15:
#line 134 "hb-ot-shape-complex-use-machine.rl"
        {te = p;p--;{ found_syllable (non_cluster); }}
        break;
        case 1:
#line 133 "hb-ot-shape-complex-use-machine.rl"
        {{p = ((te))-1;}{ found_syllable (broken_cluster); }}
        break;
#line 420 "hb-ot-shape-complex-use-machine.hh"
        }

_again:
        switch ( _use_syllable_machine_to_state_actions[cs] ) {
        case 3:
#line 1 "NONE"
        {ts = 0;}
        break;
#line 429 "hb-ot-shape-complex-use-machine.hh"
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

#line 168 "hb-ot-shape-complex-use-machine.rl"

}

#undef found_syllable

#endif /* HB_OT_SHAPE_COMPLEX_USE_MACHINE_HH */
