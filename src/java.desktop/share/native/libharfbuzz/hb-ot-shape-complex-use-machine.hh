
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

#include "hb.hh"


#line 38 "hb-ot-shape-complex-use-machine.hh"
static const unsigned char _use_syllable_machine_trans_keys[] = {
        12u, 44u, 1u, 15u, 1u, 1u, 12u, 44u, 0u, 44u, 21u, 21u, 8u, 44u, 8u, 44u,
        1u, 15u, 1u, 1u, 8u, 44u, 8u, 44u, 8u, 39u, 8u, 26u, 8u, 26u, 8u, 26u,
        8u, 39u, 8u, 39u, 8u, 39u, 8u, 44u, 8u, 44u, 8u, 44u, 8u, 44u, 8u, 44u,
        8u, 44u, 8u, 44u, 8u, 44u, 1u, 39u, 8u, 44u, 13u, 21u, 4u, 4u, 13u, 13u,
        8u, 44u, 8u, 44u, 8u, 44u, 8u, 39u, 8u, 26u, 8u, 26u, 8u, 26u, 8u, 39u,
        8u, 39u, 8u, 39u, 8u, 44u, 8u, 44u, 8u, 44u, 8u, 44u, 8u, 44u, 8u, 44u,
        8u, 44u, 8u, 44u, 1u, 39u, 1u, 15u, 12u, 44u, 1u, 44u, 8u, 44u, 21u, 42u,
        41u, 42u, 42u, 42u, 1u, 5u, 0
};

static const char _use_syllable_machine_key_spans[] = {
        33, 15, 1, 33, 45, 1, 37, 37,
        15, 1, 37, 37, 32, 19, 19, 19,
        32, 32, 32, 37, 37, 37, 37, 37,
        37, 37, 37, 39, 37, 9, 1, 1,
        37, 37, 37, 32, 19, 19, 19, 32,
        32, 32, 37, 37, 37, 37, 37, 37,
        37, 37, 39, 15, 33, 44, 37, 22,
        2, 1, 5
};

static const short _use_syllable_machine_index_offsets[] = {
        0, 34, 50, 52, 86, 132, 134, 172,
        210, 226, 228, 266, 304, 337, 357, 377,
        397, 430, 463, 496, 534, 572, 610, 648,
        686, 724, 762, 800, 840, 878, 888, 890,
        892, 930, 968, 1006, 1039, 1059, 1079, 1099,
        1132, 1165, 1198, 1236, 1274, 1312, 1350, 1388,
        1426, 1464, 1502, 1542, 1558, 1592, 1637, 1675,
        1698, 1701, 1703
};

static const char _use_syllable_machine_indicies[] = {
        1, 0, 0, 0, 0, 0, 0, 0,
        0, 0, 0, 0, 0, 0, 0, 0,
        0, 0, 0, 0, 0, 0, 0, 0,
        0, 0, 0, 0, 0, 0, 0, 0,
        1, 0, 3, 2, 2, 2, 2, 2,
        2, 2, 2, 2, 2, 2, 2, 2,
        4, 2, 3, 2, 6, 5, 5, 5,
        5, 5, 5, 5, 5, 5, 5, 5,
        5, 5, 5, 5, 5, 5, 5, 5,
        5, 5, 5, 5, 5, 5, 5, 5,
        5, 5, 5, 5, 6, 5, 7, 8,
        9, 7, 10, 8, 9, 9, 11, 9,
        9, 3, 12, 9, 9, 13, 7, 7,
        14, 15, 9, 9, 16, 17, 18, 19,
        20, 21, 22, 16, 23, 24, 25, 26,
        27, 28, 9, 29, 30, 31, 9, 9,
        9, 32, 33, 9, 35, 34, 37, 36,
        36, 38, 1, 36, 36, 39, 36, 36,
        36, 36, 36, 40, 41, 42, 43, 44,
        45, 46, 47, 41, 48, 40, 49, 50,
        51, 52, 36, 53, 54, 55, 36, 36,
        36, 36, 56, 36, 37, 36, 36, 38,
        1, 36, 36, 39, 36, 36, 36, 36,
        36, 57, 41, 42, 43, 44, 45, 46,
        47, 41, 48, 49, 49, 50, 51, 52,
        36, 53, 54, 55, 36, 36, 36, 36,
        56, 36, 38, 58, 58, 58, 58, 58,
        58, 58, 58, 58, 58, 58, 58, 58,
        59, 58, 38, 58, 37, 36, 36, 38,
        1, 36, 36, 39, 36, 36, 36, 36,
        36, 36, 41, 42, 43, 44, 45, 46,
        47, 41, 48, 49, 49, 50, 51, 52,
        36, 53, 54, 55, 36, 36, 36, 36,
        56, 36, 37, 36, 36, 36, 36, 36,
        36, 36, 36, 36, 36, 36, 36, 36,
        41, 42, 43, 44, 45, 36, 36, 36,
        36, 36, 36, 50, 51, 52, 36, 53,
        54, 55, 36, 36, 36, 36, 42, 36,
        37, 36, 36, 36, 36, 36, 36, 36,
        36, 36, 36, 36, 36, 36, 36, 42,
        43, 44, 45, 36, 36, 36, 36, 36,
        36, 36, 36, 36, 36, 53, 54, 55,
        36, 37, 36, 36, 36, 36, 36, 36,
        36, 36, 36, 36, 36, 36, 36, 36,
        36, 43, 44, 45, 36, 37, 36, 36,
        36, 36, 36, 36, 36, 36, 36, 36,
        36, 36, 36, 36, 36, 36, 44, 45,
        36, 37, 36, 36, 36, 36, 36, 36,
        36, 36, 36, 36, 36, 36, 36, 36,
        36, 36, 36, 45, 36, 37, 36, 36,
        36, 36, 36, 36, 36, 36, 36, 36,
        36, 36, 36, 36, 36, 43, 44, 45,
        36, 36, 36, 36, 36, 36, 36, 36,
        36, 36, 53, 54, 55, 36, 37, 36,
        36, 36, 36, 36, 36, 36, 36, 36,
        36, 36, 36, 36, 36, 36, 43, 44,
        45, 36, 36, 36, 36, 36, 36, 36,
        36, 36, 36, 36, 54, 55, 36, 37,
        36, 36, 36, 36, 36, 36, 36, 36,
        36, 36, 36, 36, 36, 36, 36, 43,
        44, 45, 36, 36, 36, 36, 36, 36,
        36, 36, 36, 36, 36, 36, 55, 36,
        37, 36, 36, 36, 36, 36, 36, 36,
        36, 36, 36, 36, 36, 36, 36, 42,
        43, 44, 45, 36, 36, 36, 36, 36,
        36, 50, 51, 52, 36, 53, 54, 55,
        36, 36, 36, 36, 42, 36, 37, 36,
        36, 36, 36, 36, 36, 36, 36, 36,
        36, 36, 36, 36, 36, 42, 43, 44,
        45, 36, 36, 36, 36, 36, 36, 36,
        51, 52, 36, 53, 54, 55, 36, 36,
        36, 36, 42, 36, 37, 36, 36, 36,
        36, 36, 36, 36, 36, 36, 36, 36,
        36, 36, 36, 42, 43, 44, 45, 36,
        36, 36, 36, 36, 36, 36, 36, 52,
        36, 53, 54, 55, 36, 36, 36, 36,
        42, 36, 37, 36, 36, 36, 36, 36,
        36, 36, 36, 36, 36, 36, 36, 36,
        41, 42, 43, 44, 45, 36, 47, 41,
        36, 36, 36, 50, 51, 52, 36, 53,
        54, 55, 36, 36, 36, 36, 42, 36,
        37, 36, 36, 36, 36, 36, 36, 36,
        36, 36, 36, 36, 36, 36, 41, 42,
        43, 44, 45, 36, 60, 41, 36, 36,
        36, 50, 51, 52, 36, 53, 54, 55,
        36, 36, 36, 36, 42, 36, 37, 36,
        36, 36, 36, 36, 36, 36, 36, 36,
        36, 36, 36, 36, 41, 42, 43, 44,
        45, 36, 36, 41, 36, 36, 36, 50,
        51, 52, 36, 53, 54, 55, 36, 36,
        36, 36, 42, 36, 37, 36, 36, 36,
        36, 36, 36, 36, 36, 36, 36, 36,
        36, 36, 41, 42, 43, 44, 45, 46,
        47, 41, 36, 36, 36, 50, 51, 52,
        36, 53, 54, 55, 36, 36, 36, 36,
        42, 36, 37, 36, 36, 38, 1, 36,
        36, 39, 36, 36, 36, 36, 36, 36,
        41, 42, 43, 44, 45, 46, 47, 41,
        48, 36, 49, 50, 51, 52, 36, 53,
        54, 55, 36, 36, 36, 36, 56, 36,
        38, 58, 58, 58, 58, 58, 58, 37,
        58, 58, 58, 58, 58, 58, 59, 58,
        58, 58, 58, 58, 58, 58, 42, 43,
        44, 45, 58, 58, 58, 58, 58, 58,
        58, 58, 58, 58, 53, 54, 55, 58,
        37, 36, 36, 38, 1, 36, 36, 39,
        36, 36, 36, 36, 36, 36, 41, 42,
        43, 44, 45, 46, 47, 41, 48, 40,
        49, 50, 51, 52, 36, 53, 54, 55,
        36, 36, 36, 36, 56, 36, 62, 61,
        61, 61, 61, 61, 61, 61, 63, 61,
        10, 64, 62, 61, 11, 65, 65, 3,
        6, 65, 65, 66, 65, 65, 65, 65,
        65, 67, 16, 17, 18, 19, 20, 21,
        22, 16, 23, 25, 25, 26, 27, 28,
        65, 29, 30, 31, 65, 65, 65, 65,
        33, 65, 11, 65, 65, 3, 6, 65,
        65, 66, 65, 65, 65, 65, 65, 65,
        16, 17, 18, 19, 20, 21, 22, 16,
        23, 25, 25, 26, 27, 28, 65, 29,
        30, 31, 65, 65, 65, 65, 33, 65,
        11, 65, 65, 65, 65, 65, 65, 65,
        65, 65, 65, 65, 65, 65, 16, 17,
        18, 19, 20, 65, 65, 65, 65, 65,
        65, 26, 27, 28, 65, 29, 30, 31,
        65, 65, 65, 65, 17, 65, 11, 65,
        65, 65, 65, 65, 65, 65, 65, 65,
        65, 65, 65, 65, 65, 17, 18, 19,
        20, 65, 65, 65, 65, 65, 65, 65,
        65, 65, 65, 29, 30, 31, 65, 11,
        65, 65, 65, 65, 65, 65, 65, 65,
        65, 65, 65, 65, 65, 65, 65, 18,
        19, 20, 65, 11, 65, 65, 65, 65,
        65, 65, 65, 65, 65, 65, 65, 65,
        65, 65, 65, 65, 19, 20, 65, 11,
        65, 65, 65, 65, 65, 65, 65, 65,
        65, 65, 65, 65, 65, 65, 65, 65,
        65, 20, 65, 11, 65, 65, 65, 65,
        65, 65, 65, 65, 65, 65, 65, 65,
        65, 65, 65, 18, 19, 20, 65, 65,
        65, 65, 65, 65, 65, 65, 65, 65,
        29, 30, 31, 65, 11, 65, 65, 65,
        65, 65, 65, 65, 65, 65, 65, 65,
        65, 65, 65, 65, 18, 19, 20, 65,
        65, 65, 65, 65, 65, 65, 65, 65,
        65, 65, 30, 31, 65, 11, 65, 65,
        65, 65, 65, 65, 65, 65, 65, 65,
        65, 65, 65, 65, 65, 18, 19, 20,
        65, 65, 65, 65, 65, 65, 65, 65,
        65, 65, 65, 65, 31, 65, 11, 65,
        65, 65, 65, 65, 65, 65, 65, 65,
        65, 65, 65, 65, 65, 17, 18, 19,
        20, 65, 65, 65, 65, 65, 65, 26,
        27, 28, 65, 29, 30, 31, 65, 65,
        65, 65, 17, 65, 11, 65, 65, 65,
        65, 65, 65, 65, 65, 65, 65, 65,
        65, 65, 65, 17, 18, 19, 20, 65,
        65, 65, 65, 65, 65, 65, 27, 28,
        65, 29, 30, 31, 65, 65, 65, 65,
        17, 65, 11, 65, 65, 65, 65, 65,
        65, 65, 65, 65, 65, 65, 65, 65,
        65, 17, 18, 19, 20, 65, 65, 65,
        65, 65, 65, 65, 65, 28, 65, 29,
        30, 31, 65, 65, 65, 65, 17, 65,
        11, 65, 65, 65, 65, 65, 65, 65,
        65, 65, 65, 65, 65, 65, 16, 17,
        18, 19, 20, 65, 22, 16, 65, 65,
        65, 26, 27, 28, 65, 29, 30, 31,
        65, 65, 65, 65, 17, 65, 11, 65,
        65, 65, 65, 65, 65, 65, 65, 65,
        65, 65, 65, 65, 16, 17, 18, 19,
        20, 65, 68, 16, 65, 65, 65, 26,
        27, 28, 65, 29, 30, 31, 65, 65,
        65, 65, 17, 65, 11, 65, 65, 65,
        65, 65, 65, 65, 65, 65, 65, 65,
        65, 65, 16, 17, 18, 19, 20, 65,
        65, 16, 65, 65, 65, 26, 27, 28,
        65, 29, 30, 31, 65, 65, 65, 65,
        17, 65, 11, 65, 65, 65, 65, 65,
        65, 65, 65, 65, 65, 65, 65, 65,
        16, 17, 18, 19, 20, 21, 22, 16,
        65, 65, 65, 26, 27, 28, 65, 29,
        30, 31, 65, 65, 65, 65, 17, 65,
        11, 65, 65, 3, 6, 65, 65, 66,
        65, 65, 65, 65, 65, 65, 16, 17,
        18, 19, 20, 21, 22, 16, 23, 65,
        25, 26, 27, 28, 65, 29, 30, 31,
        65, 65, 65, 65, 33, 65, 3, 65,
        65, 65, 65, 65, 65, 11, 65, 65,
        65, 65, 65, 65, 4, 65, 65, 65,
        65, 65, 65, 65, 17, 18, 19, 20,
        65, 65, 65, 65, 65, 65, 65, 65,
        65, 65, 29, 30, 31, 65, 3, 69,
        69, 69, 69, 69, 69, 69, 69, 69,
        69, 69, 69, 69, 4, 69, 6, 69,
        69, 69, 69, 69, 69, 69, 69, 69,
        69, 69, 69, 69, 69, 69, 69, 69,
        69, 69, 69, 69, 69, 69, 69, 69,
        69, 69, 69, 69, 69, 69, 6, 69,
        8, 65, 65, 65, 8, 65, 65, 11,
        65, 65, 3, 6, 65, 65, 66, 65,
        65, 65, 65, 65, 65, 16, 17, 18,
        19, 20, 21, 22, 16, 23, 24, 25,
        26, 27, 28, 65, 29, 30, 31, 65,
        65, 65, 65, 33, 65, 11, 65, 65,
        3, 6, 65, 65, 66, 65, 65, 65,
        65, 65, 65, 16, 17, 18, 19, 20,
        21, 22, 16, 23, 24, 25, 26, 27,
        28, 65, 29, 30, 31, 65, 65, 65,
        65, 33, 65, 71, 70, 70, 70, 70,
        70, 70, 70, 70, 70, 70, 70, 70,
        70, 70, 70, 70, 70, 70, 70, 71,
        72, 70, 71, 72, 70, 72, 70, 8,
        69, 69, 69, 8, 69, 0
};

static const char _use_syllable_machine_trans_targs[] = {
        4, 8, 4, 32, 2, 4, 1, 5,
        6, 4, 29, 4, 51, 52, 53, 55,
        34, 35, 36, 37, 38, 45, 46, 48,
        54, 49, 42, 43, 44, 39, 40, 41,
        58, 50, 4, 4, 4, 4, 7, 0,
        28, 11, 12, 13, 14, 15, 22, 23,
        25, 26, 19, 20, 21, 16, 17, 18,
        27, 10, 4, 9, 24, 4, 30, 31,
        4, 4, 3, 33, 47, 4, 4, 56,
        57
};

static const char _use_syllable_machine_trans_actions[] = {
        1, 0, 2, 3, 0, 4, 0, 0,
        7, 8, 0, 9, 10, 10, 3, 0,
        0, 0, 0, 0, 0, 0, 0, 0,
        3, 3, 0, 0, 0, 0, 0, 0,
        0, 3, 11, 12, 13, 14, 7, 0,
        7, 0, 0, 0, 0, 0, 0, 0,
        0, 7, 0, 0, 0, 0, 0, 0,
        0, 7, 15, 0, 0, 16, 0, 0,
        17, 18, 0, 3, 0, 19, 20, 0,
        0
};

static const char _use_syllable_machine_to_state_actions[] = {
        0, 0, 0, 0, 5, 0, 0, 0,
        0, 0, 0, 0, 0, 0, 0, 0,
        0, 0, 0, 0, 0, 0, 0, 0,
        0, 0, 0, 0, 0, 0, 0, 0,
        0, 0, 0, 0, 0, 0, 0, 0,
        0, 0, 0, 0, 0, 0, 0, 0,
        0, 0, 0, 0, 0, 0, 0, 0,
        0, 0, 0
};

static const char _use_syllable_machine_from_state_actions[] = {
        0, 0, 0, 0, 6, 0, 0, 0,
        0, 0, 0, 0, 0, 0, 0, 0,
        0, 0, 0, 0, 0, 0, 0, 0,
        0, 0, 0, 0, 0, 0, 0, 0,
        0, 0, 0, 0, 0, 0, 0, 0,
        0, 0, 0, 0, 0, 0, 0, 0,
        0, 0, 0, 0, 0, 0, 0, 0,
        0, 0, 0
};

static const short _use_syllable_machine_eof_trans[] = {
        1, 3, 3, 6, 0, 35, 37, 37,
        59, 59, 37, 37, 37, 37, 37, 37,
        37, 37, 37, 37, 37, 37, 37, 37,
        37, 37, 37, 59, 37, 62, 65, 62,
        66, 66, 66, 66, 66, 66, 66, 66,
        66, 66, 66, 66, 66, 66, 66, 66,
        66, 66, 66, 70, 70, 66, 66, 71,
        71, 71, 70
};

static const int use_syllable_machine_start = 4;
static const int use_syllable_machine_first_final = 4;
static const int use_syllable_machine_error = -1;

static const int use_syllable_machine_en_main = 4;


#line 38 "hb-ot-shape-complex-use-machine.rl"



#line 143 "hb-ot-shape-complex-use-machine.rl"


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
  unsigned int p, pe, eof, ts, te, act;
  int cs;
  hb_glyph_info_t *info = buffer->info;

#line 378 "hb-ot-shape-complex-use-machine.hh"
        {
        cs = use_syllable_machine_start;
        ts = 0;
        te = 0;
        act = 0;
        }

#line 163 "hb-ot-shape-complex-use-machine.rl"


  p = 0;
  pe = eof = buffer->len;

  unsigned int syllable_serial = 1;

#line 394 "hb-ot-shape-complex-use-machine.hh"
        {
        int _slen;
        int _trans;
        const unsigned char *_keys;
        const char *_inds;
        if ( p == pe )
                goto _test_eof;
_resume:
        switch ( _use_syllable_machine_from_state_actions[cs] ) {
        case 6:
#line 1 "NONE"
        {ts = p;}
        break;
#line 408 "hb-ot-shape-complex-use-machine.hh"
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
        case 7:
#line 1 "NONE"
        {te = p+1;}
        break;
        case 12:
#line 132 "hb-ot-shape-complex-use-machine.rl"
        {te = p+1;{ found_syllable (independent_cluster); }}
        break;
        case 14:
#line 134 "hb-ot-shape-complex-use-machine.rl"
        {te = p+1;{ found_syllable (standard_cluster); }}
        break;
        case 9:
#line 138 "hb-ot-shape-complex-use-machine.rl"
        {te = p+1;{ found_syllable (broken_cluster); }}
        break;
        case 8:
#line 139 "hb-ot-shape-complex-use-machine.rl"
        {te = p+1;{ found_syllable (non_cluster); }}
        break;
        case 11:
#line 132 "hb-ot-shape-complex-use-machine.rl"
        {te = p;p--;{ found_syllable (independent_cluster); }}
        break;
        case 15:
#line 133 "hb-ot-shape-complex-use-machine.rl"
        {te = p;p--;{ found_syllable (virama_terminated_cluster); }}
        break;
        case 13:
#line 134 "hb-ot-shape-complex-use-machine.rl"
        {te = p;p--;{ found_syllable (standard_cluster); }}
        break;
        case 17:
#line 135 "hb-ot-shape-complex-use-machine.rl"
        {te = p;p--;{ found_syllable (number_joiner_terminated_cluster); }}
        break;
        case 16:
#line 136 "hb-ot-shape-complex-use-machine.rl"
        {te = p;p--;{ found_syllable (numeral_cluster); }}
        break;
        case 20:
#line 137 "hb-ot-shape-complex-use-machine.rl"
        {te = p;p--;{ found_syllable (symbol_cluster); }}
        break;
        case 18:
#line 138 "hb-ot-shape-complex-use-machine.rl"
        {te = p;p--;{ found_syllable (broken_cluster); }}
        break;
        case 19:
#line 139 "hb-ot-shape-complex-use-machine.rl"
        {te = p;p--;{ found_syllable (non_cluster); }}
        break;
        case 1:
#line 134 "hb-ot-shape-complex-use-machine.rl"
        {{p = ((te))-1;}{ found_syllable (standard_cluster); }}
        break;
        case 4:
#line 138 "hb-ot-shape-complex-use-machine.rl"
        {{p = ((te))-1;}{ found_syllable (broken_cluster); }}
        break;
        case 2:
#line 1 "NONE"
        {       switch( act ) {
        case 7:
        {{p = ((te))-1;} found_syllable (broken_cluster); }
        break;
        case 8:
        {{p = ((te))-1;} found_syllable (non_cluster); }
        break;
        }
        }
        break;
        case 3:
#line 1 "NONE"
        {te = p+1;}
#line 138 "hb-ot-shape-complex-use-machine.rl"
        {act = 7;}
        break;
        case 10:
#line 1 "NONE"
        {te = p+1;}
#line 139 "hb-ot-shape-complex-use-machine.rl"
        {act = 8;}
        break;
#line 510 "hb-ot-shape-complex-use-machine.hh"
        }

_again:
        switch ( _use_syllable_machine_to_state_actions[cs] ) {
        case 5:
#line 1 "NONE"
        {ts = 0;}
        break;
#line 519 "hb-ot-shape-complex-use-machine.hh"
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

#line 171 "hb-ot-shape-complex-use-machine.rl"

}

#undef found_syllable

#endif /* HB_OT_SHAPE_COMPLEX_USE_MACHINE_HH */
