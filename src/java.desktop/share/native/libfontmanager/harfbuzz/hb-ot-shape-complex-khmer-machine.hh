
#line 1 "hb-ot-shape-complex-khmer-machine.rl"
/*
 * Copyright © 2011,2012  Google, Inc.
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
 * Google Author(s): Behdad Esfahbod
 */

#ifndef HB_OT_SHAPE_COMPLEX_KHMER_MACHINE_HH
#define HB_OT_SHAPE_COMPLEX_KHMER_MACHINE_HH

#include "hb.hh"


#line 36 "hb-ot-shape-complex-khmer-machine.hh"
static const unsigned char _khmer_syllable_machine_trans_keys[] = {
        5u, 26u, 5u, 21u, 5u, 26u, 5u, 21u, 1u, 16u, 5u, 21u, 5u, 26u, 5u, 21u,
        5u, 26u, 5u, 21u, 1u, 16u, 5u, 21u, 5u, 26u, 5u, 21u, 1u, 16u, 5u, 21u,
        5u, 26u, 5u, 21u, 5u, 26u, 5u, 21u, 5u, 26u, 1u, 16u, 1u, 29u, 5u, 29u,
        5u, 29u, 5u, 29u, 22u, 22u, 5u, 22u, 5u, 29u, 5u, 29u, 5u, 29u, 5u, 26u,
        5u, 29u, 5u, 29u, 22u, 22u, 5u, 22u, 5u, 29u, 5u, 29u, 1u, 16u, 5u, 29u,
        5u, 29u, 0
};

static const char _khmer_syllable_machine_key_spans[] = {
        22, 17, 22, 17, 16, 17, 22, 17,
        22, 17, 16, 17, 22, 17, 16, 17,
        22, 17, 22, 17, 22, 16, 29, 25,
        25, 25, 1, 18, 25, 25, 25, 22,
        25, 25, 1, 18, 25, 25, 16, 25,
        25
};

static const short _khmer_syllable_machine_index_offsets[] = {
        0, 23, 41, 64, 82, 99, 117, 140,
        158, 181, 199, 216, 234, 257, 275, 292,
        310, 333, 351, 374, 392, 415, 432, 462,
        488, 514, 540, 542, 561, 587, 613, 639,
        662, 688, 714, 716, 735, 761, 787, 804,
        830
};

static const char _khmer_syllable_machine_indicies[] = {
        1, 1, 0, 0, 0, 0, 0, 0,
        0, 0, 0, 0, 0, 0, 0, 2,
        3, 0, 0, 0, 0, 4, 0, 1,
        1, 0, 0, 0, 0, 0, 0, 0,
        0, 0, 0, 0, 0, 0, 0, 3,
        0, 1, 1, 0, 0, 0, 0, 0,
        0, 0, 0, 0, 0, 0, 0, 0,
        0, 3, 0, 0, 0, 0, 4, 0,
        5, 5, 0, 0, 0, 0, 0, 0,
        0, 0, 0, 0, 0, 0, 0, 0,
        4, 0, 6, 6, 0, 0, 0, 0,
        0, 0, 0, 0, 0, 0, 0, 0,
        0, 6, 0, 7, 7, 0, 0, 0,
        0, 0, 0, 0, 0, 0, 0, 0,
        0, 0, 0, 8, 0, 9, 9, 0,
        0, 0, 0, 0, 0, 0, 0, 0,
        0, 0, 0, 0, 0, 10, 0, 0,
        0, 0, 4, 0, 9, 9, 0, 0,
        0, 0, 0, 0, 0, 0, 0, 0,
        0, 0, 0, 0, 10, 0, 11, 11,
        0, 0, 0, 0, 0, 0, 0, 0,
        0, 0, 0, 0, 0, 0, 12, 0,
        0, 0, 0, 4, 0, 11, 11, 0,
        0, 0, 0, 0, 0, 0, 0, 0,
        0, 0, 0, 0, 0, 12, 0, 13,
        13, 0, 0, 0, 0, 0, 0, 0,
        0, 0, 0, 0, 0, 0, 13, 0,
        15, 15, 14, 14, 14, 14, 14, 14,
        14, 14, 14, 14, 14, 14, 14, 14,
        16, 14, 15, 15, 17, 17, 17, 17,
        17, 17, 17, 17, 17, 17, 17, 17,
        17, 17, 16, 17, 17, 17, 17, 18,
        17, 19, 19, 17, 17, 17, 17, 17,
        17, 17, 17, 17, 17, 17, 17, 17,
        17, 18, 17, 20, 20, 17, 17, 17,
        17, 17, 17, 17, 17, 17, 17, 17,
        17, 17, 20, 17, 21, 21, 17, 17,
        17, 17, 17, 17, 17, 17, 17, 17,
        17, 17, 17, 17, 22, 17, 23, 23,
        17, 17, 17, 17, 17, 17, 17, 17,
        17, 17, 17, 17, 17, 17, 24, 17,
        17, 17, 17, 18, 17, 23, 23, 17,
        17, 17, 17, 17, 17, 17, 17, 17,
        17, 17, 17, 17, 17, 24, 17, 25,
        25, 17, 17, 17, 17, 17, 17, 17,
        17, 17, 17, 17, 17, 17, 17, 26,
        17, 17, 17, 17, 18, 17, 25, 25,
        17, 17, 17, 17, 17, 17, 17, 17,
        17, 17, 17, 17, 17, 17, 26, 17,
        15, 15, 17, 17, 17, 17, 17, 17,
        17, 17, 17, 17, 17, 17, 17, 27,
        16, 17, 17, 17, 17, 18, 17, 28,
        28, 17, 17, 17, 17, 17, 17, 17,
        17, 17, 17, 17, 17, 17, 28, 17,
        13, 13, 29, 29, 30, 30, 29, 29,
        29, 29, 2, 2, 29, 31, 29, 13,
        29, 29, 29, 29, 16, 20, 29, 29,
        29, 18, 24, 26, 22, 29, 33, 33,
        32, 32, 32, 32, 32, 32, 32, 34,
        32, 32, 32, 32, 32, 2, 3, 6,
        32, 32, 32, 4, 10, 12, 8, 32,
        35, 35, 32, 32, 32, 32, 32, 32,
        32, 36, 32, 32, 32, 32, 32, 32,
        3, 6, 32, 32, 32, 4, 10, 12,
        8, 32, 5, 5, 32, 32, 32, 32,
        32, 32, 32, 36, 32, 32, 32, 32,
        32, 32, 4, 6, 32, 32, 32, 32,
        32, 32, 8, 32, 6, 32, 7, 7,
        32, 32, 32, 32, 32, 32, 32, 36,
        32, 32, 32, 32, 32, 32, 8, 6,
        32, 37, 37, 32, 32, 32, 32, 32,
        32, 32, 36, 32, 32, 32, 32, 32,
        32, 10, 6, 32, 32, 32, 4, 32,
        32, 8, 32, 38, 38, 32, 32, 32,
        32, 32, 32, 32, 36, 32, 32, 32,
        32, 32, 32, 12, 6, 32, 32, 32,
        4, 10, 32, 8, 32, 35, 35, 32,
        32, 32, 32, 32, 32, 32, 34, 32,
        32, 32, 32, 32, 32, 3, 6, 32,
        32, 32, 4, 10, 12, 8, 32, 15,
        15, 39, 39, 39, 39, 39, 39, 39,
        39, 39, 39, 39, 39, 39, 39, 16,
        39, 39, 39, 39, 18, 39, 41, 41,
        40, 40, 40, 40, 40, 40, 40, 42,
        40, 40, 40, 40, 40, 40, 16, 20,
        40, 40, 40, 18, 24, 26, 22, 40,
        19, 19, 40, 40, 40, 40, 40, 40,
        40, 42, 40, 40, 40, 40, 40, 40,
        18, 20, 40, 40, 40, 40, 40, 40,
        22, 40, 20, 40, 21, 21, 40, 40,
        40, 40, 40, 40, 40, 42, 40, 40,
        40, 40, 40, 40, 22, 20, 40, 43,
        43, 40, 40, 40, 40, 40, 40, 40,
        42, 40, 40, 40, 40, 40, 40, 24,
        20, 40, 40, 40, 18, 40, 40, 22,
        40, 44, 44, 40, 40, 40, 40, 40,
        40, 40, 42, 40, 40, 40, 40, 40,
        40, 26, 20, 40, 40, 40, 18, 24,
        40, 22, 40, 28, 28, 39, 39, 39,
        39, 39, 39, 39, 39, 39, 39, 39,
        39, 39, 28, 39, 45, 45, 40, 40,
        40, 40, 40, 40, 40, 46, 40, 40,
        40, 40, 40, 27, 16, 20, 40, 40,
        40, 18, 24, 26, 22, 40, 41, 41,
        40, 40, 40, 40, 40, 40, 40, 46,
        40, 40, 40, 40, 40, 40, 16, 20,
        40, 40, 40, 18, 24, 26, 22, 40,
        0
};

static const char _khmer_syllable_machine_trans_targs[] = {
        22, 1, 30, 24, 25, 3, 26, 5,
        27, 7, 28, 9, 29, 23, 22, 11,
        32, 22, 33, 13, 34, 15, 35, 17,
        36, 19, 37, 40, 39, 22, 31, 38,
        22, 0, 10, 2, 4, 6, 8, 22,
        22, 12, 14, 16, 18, 20, 21
};

static const char _khmer_syllable_machine_trans_actions[] = {
        1, 0, 2, 2, 2, 0, 0, 0,
        2, 0, 2, 0, 2, 2, 3, 0,
        4, 5, 2, 0, 0, 0, 2, 0,
        2, 0, 2, 4, 4, 8, 9, 0,
        10, 0, 0, 0, 0, 0, 0, 11,
        12, 0, 0, 0, 0, 0, 0
};

static const char _khmer_syllable_machine_to_state_actions[] = {
        0, 0, 0, 0, 0, 0, 0, 0,
        0, 0, 0, 0, 0, 0, 0, 0,
        0, 0, 0, 0, 0, 0, 6, 0,
        0, 0, 0, 0, 0, 0, 0, 0,
        0, 0, 0, 0, 0, 0, 0, 0,
        0
};

static const char _khmer_syllable_machine_from_state_actions[] = {
        0, 0, 0, 0, 0, 0, 0, 0,
        0, 0, 0, 0, 0, 0, 0, 0,
        0, 0, 0, 0, 0, 0, 7, 0,
        0, 0, 0, 0, 0, 0, 0, 0,
        0, 0, 0, 0, 0, 0, 0, 0,
        0
};

static const unsigned char _khmer_syllable_machine_eof_trans[] = {
        1, 1, 1, 1, 1, 1, 1, 1,
        1, 1, 1, 15, 18, 18, 18, 18,
        18, 18, 18, 18, 18, 18, 0, 33,
        33, 33, 33, 33, 33, 33, 33, 40,
        41, 41, 41, 41, 41, 41, 40, 41,
        41
};

static const int khmer_syllable_machine_start = 22;
static const int khmer_syllable_machine_first_final = 22;
static const int khmer_syllable_machine_error = -1;

static const int khmer_syllable_machine_en_main = 22;


#line 36 "hb-ot-shape-complex-khmer-machine.rl"



#line 80 "hb-ot-shape-complex-khmer-machine.rl"


#define found_syllable(syllable_type) \
  HB_STMT_START { \
    if (0) fprintf (stderr, "syllable %d..%d %s\n", ts, te, #syllable_type); \
    for (unsigned int i = ts; i < te; i++) \
      info[i].syllable() = (syllable_serial << 4) | syllable_type; \
    syllable_serial++; \
    if (unlikely (syllable_serial == 16)) syllable_serial = 1; \
  } HB_STMT_END

static void
find_syllables (hb_buffer_t *buffer)
{
  unsigned int p, pe, eof, ts, te, act HB_UNUSED;
  int cs;
  hb_glyph_info_t *info = buffer->info;

#line 250 "hb-ot-shape-complex-khmer-machine.hh"
        {
        cs = khmer_syllable_machine_start;
        ts = 0;
        te = 0;
        act = 0;
        }

#line 100 "hb-ot-shape-complex-khmer-machine.rl"


  p = 0;
  pe = eof = buffer->len;

  unsigned int syllable_serial = 1;

#line 266 "hb-ot-shape-complex-khmer-machine.hh"
        {
        int _slen;
        int _trans;
        const unsigned char *_keys;
        const char *_inds;
        if ( p == pe )
                goto _test_eof;
_resume:
        switch ( _khmer_syllable_machine_from_state_actions[cs] ) {
        case 7:
#line 1 "NONE"
        {ts = p;}
        break;
#line 280 "hb-ot-shape-complex-khmer-machine.hh"
        }

        _keys = _khmer_syllable_machine_trans_keys + (cs<<1);
        _inds = _khmer_syllable_machine_indicies + _khmer_syllable_machine_index_offsets[cs];

        _slen = _khmer_syllable_machine_key_spans[cs];
        _trans = _inds[ _slen > 0 && _keys[0] <=( info[p].khmer_category()) &&
                ( info[p].khmer_category()) <= _keys[1] ?
                ( info[p].khmer_category()) - _keys[0] : _slen ];

_eof_trans:
        cs = _khmer_syllable_machine_trans_targs[_trans];

        if ( _khmer_syllable_machine_trans_actions[_trans] == 0 )
                goto _again;

        switch ( _khmer_syllable_machine_trans_actions[_trans] ) {
        case 2:
#line 1 "NONE"
        {te = p+1;}
        break;
        case 8:
#line 76 "hb-ot-shape-complex-khmer-machine.rl"
        {te = p+1;{ found_syllable (non_khmer_cluster); }}
        break;
        case 10:
#line 74 "hb-ot-shape-complex-khmer-machine.rl"
        {te = p;p--;{ found_syllable (consonant_syllable); }}
        break;
        case 12:
#line 75 "hb-ot-shape-complex-khmer-machine.rl"
        {te = p;p--;{ found_syllable (broken_cluster); }}
        break;
        case 11:
#line 76 "hb-ot-shape-complex-khmer-machine.rl"
        {te = p;p--;{ found_syllable (non_khmer_cluster); }}
        break;
        case 1:
#line 74 "hb-ot-shape-complex-khmer-machine.rl"
        {{p = ((te))-1;}{ found_syllable (consonant_syllable); }}
        break;
        case 5:
#line 75 "hb-ot-shape-complex-khmer-machine.rl"
        {{p = ((te))-1;}{ found_syllable (broken_cluster); }}
        break;
        case 3:
#line 1 "NONE"
        {       switch( act ) {
        case 2:
        {{p = ((te))-1;} found_syllable (broken_cluster); }
        break;
        case 3:
        {{p = ((te))-1;} found_syllable (non_khmer_cluster); }
        break;
        }
        }
        break;
        case 4:
#line 1 "NONE"
        {te = p+1;}
#line 75 "hb-ot-shape-complex-khmer-machine.rl"
        {act = 2;}
        break;
        case 9:
#line 1 "NONE"
        {te = p+1;}
#line 76 "hb-ot-shape-complex-khmer-machine.rl"
        {act = 3;}
        break;
#line 350 "hb-ot-shape-complex-khmer-machine.hh"
        }

_again:
        switch ( _khmer_syllable_machine_to_state_actions[cs] ) {
        case 6:
#line 1 "NONE"
        {ts = 0;}
        break;
#line 359 "hb-ot-shape-complex-khmer-machine.hh"
        }

        if ( ++p != pe )
                goto _resume;
        _test_eof: {}
        if ( p == eof )
        {
        if ( _khmer_syllable_machine_eof_trans[cs] > 0 ) {
                _trans = _khmer_syllable_machine_eof_trans[cs] - 1;
                goto _eof_trans;
        }
        }

        }

#line 108 "hb-ot-shape-complex-khmer-machine.rl"

}

#endif /* HB_OT_SHAPE_COMPLEX_KHMER_MACHINE_HH */
