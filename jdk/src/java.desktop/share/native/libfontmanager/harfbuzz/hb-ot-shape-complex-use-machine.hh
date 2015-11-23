
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
        0u, 0u, 4u, 4u, 1u, 1u, 0u, 39u, 21u, 21u, 8u, 39u, 8u, 39u, 1u, 1u,
        8u, 39u, 8u, 39u, 8u, 39u, 8u, 26u, 8u, 26u, 8u, 26u, 8u, 39u, 8u, 39u,
        8u, 39u, 8u, 39u, 8u, 39u, 8u, 39u, 8u, 39u, 8u, 39u, 8u, 39u, 8u, 39u,
        8u, 39u, 8u, 39u, 8u, 39u, 1u, 1u, 8u, 39u, 8u, 39u, 8u, 26u, 8u, 26u,
        8u, 26u, 8u, 39u, 8u, 39u, 8u, 39u, 8u, 39u, 8u, 39u, 8u, 39u, 8u, 39u,
        8u, 39u, 12u, 21u, 12u, 13u, 8u, 39u, 8u, 39u, 8u, 39u, 8u, 39u, 8u, 26u,
        8u, 26u, 8u, 26u, 8u, 39u, 8u, 39u, 8u, 39u, 8u, 39u, 8u, 39u, 8u, 39u,
        8u, 39u, 8u, 39u, 8u, 39u, 8u, 39u, 1u, 39u, 8u, 39u, 21u, 42u, 41u, 42u,
        42u, 42u, 0
};

static const char _use_syllable_machine_key_spans[] = {
        0, 1, 1, 40, 1, 32, 32, 1,
        32, 32, 32, 19, 19, 19, 32, 32,
        32, 32, 32, 32, 32, 32, 32, 32,
        32, 32, 32, 1, 32, 32, 19, 19,
        19, 32, 32, 32, 32, 32, 32, 32,
        32, 10, 2, 32, 32, 32, 32, 19,
        19, 19, 32, 32, 32, 32, 32, 32,
        32, 32, 32, 32, 39, 32, 22, 2,
        1
};

static const short _use_syllable_machine_index_offsets[] = {
        0, 0, 2, 4, 45, 47, 80, 113,
        115, 148, 181, 214, 234, 254, 274, 307,
        340, 373, 406, 439, 472, 505, 538, 571,
        604, 637, 670, 703, 705, 738, 771, 791,
        811, 831, 864, 897, 930, 963, 996, 1029,
        1062, 1095, 1106, 1109, 1142, 1175, 1208, 1241,
        1261, 1281, 1301, 1334, 1367, 1400, 1433, 1466,
        1499, 1532, 1565, 1598, 1631, 1671, 1704, 1727,
        1730
};

static const char _use_syllable_machine_indicies[] = {
        1, 0, 3, 2, 4, 5, 6,
        4, 1, 5, 8, 8, 7, 8, 8,
        3, 9, 8, 8, 8, 4, 4, 10,
        11, 8, 8, 12, 13, 14, 15, 16,
        17, 18, 12, 19, 20, 21, 22, 23,
        24, 8, 25, 26, 27, 8, 29, 28,
        31, 30, 30, 32, 33, 30, 30, 30,
        30, 30, 30, 30, 30, 34, 35, 36,
        37, 38, 39, 40, 41, 35, 42, 34,
        43, 44, 45, 46, 30, 47, 48, 49,
        30, 31, 30, 30, 32, 33, 30, 30,
        30, 30, 30, 30, 30, 30, 50, 35,
        36, 37, 38, 39, 40, 41, 35, 42,
        43, 43, 44, 45, 46, 30, 47, 48,
        49, 30, 32, 51, 31, 30, 30, 32,
        33, 30, 30, 30, 30, 30, 30, 30,
        30, 30, 35, 36, 37, 38, 39, 40,
        41, 35, 42, 43, 43, 44, 45, 46,
        30, 47, 48, 49, 30, 31, 30, 30,
        30, 30, 30, 30, 30, 30, 30, 30,
        30, 30, 30, 35, 36, 37, 38, 39,
        30, 30, 30, 30, 30, 30, 44, 45,
        46, 30, 47, 48, 49, 30, 31, 30,
        30, 30, 30, 30, 30, 30, 30, 30,
        30, 30, 30, 30, 30, 36, 37, 38,
        39, 30, 30, 30, 30, 30, 30, 30,
        30, 30, 30, 47, 48, 49, 30, 31,
        30, 30, 30, 30, 30, 30, 30, 30,
        30, 30, 30, 30, 30, 30, 30, 37,
        38, 39, 30, 31, 30, 30, 30, 30,
        30, 30, 30, 30, 30, 30, 30, 30,
        30, 30, 30, 30, 38, 39, 30, 31,
        30, 30, 30, 30, 30, 30, 30, 30,
        30, 30, 30, 30, 30, 30, 30, 30,
        30, 39, 30, 31, 30, 30, 30, 30,
        30, 30, 30, 30, 30, 30, 30, 30,
        30, 30, 30, 37, 38, 39, 30, 30,
        30, 30, 30, 30, 30, 30, 30, 30,
        47, 48, 49, 30, 31, 30, 30, 30,
        30, 30, 30, 30, 30, 30, 30, 30,
        30, 30, 30, 30, 37, 38, 39, 30,
        30, 30, 30, 30, 30, 30, 30, 30,
        30, 30, 48, 49, 30, 31, 30, 30,
        30, 30, 30, 30, 30, 30, 30, 30,
        30, 30, 30, 30, 30, 37, 38, 39,
        30, 30, 30, 30, 30, 30, 30, 30,
        30, 30, 30, 30, 49, 30, 31, 30,
        30, 30, 30, 30, 30, 30, 30, 30,
        30, 30, 30, 30, 30, 36, 37, 38,
        39, 30, 30, 30, 30, 30, 30, 44,
        45, 46, 30, 47, 48, 49, 30, 31,
        30, 30, 30, 30, 30, 30, 30, 30,
        30, 30, 30, 30, 30, 30, 36, 37,
        38, 39, 30, 30, 30, 30, 30, 30,
        30, 45, 46, 30, 47, 48, 49, 30,
        31, 30, 30, 30, 30, 30, 30, 30,
        30, 30, 30, 30, 30, 30, 30, 36,
        37, 38, 39, 30, 30, 30, 30, 30,
        30, 30, 30, 46, 30, 47, 48, 49,
        30, 31, 30, 30, 30, 30, 30, 30,
        30, 30, 30, 30, 30, 30, 30, 35,
        36, 37, 38, 39, 30, 41, 35, 30,
        30, 30, 44, 45, 46, 30, 47, 48,
        49, 30, 31, 30, 30, 30, 30, 30,
        30, 30, 30, 30, 30, 30, 30, 30,
        35, 36, 37, 38, 39, 30, 30, 35,
        30, 30, 30, 44, 45, 46, 30, 47,
        48, 49, 30, 31, 30, 30, 30, 30,
        30, 30, 30, 30, 30, 30, 30, 30,
        30, 35, 36, 37, 38, 39, 40, 41,
        35, 30, 30, 30, 44, 45, 46, 30,
        47, 48, 49, 30, 31, 30, 30, 32,
        33, 30, 30, 30, 30, 30, 30, 30,
        30, 30, 35, 36, 37, 38, 39, 40,
        41, 35, 42, 30, 43, 44, 45, 46,
        30, 47, 48, 49, 30, 31, 30, 30,
        32, 33, 30, 30, 30, 30, 30, 30,
        30, 30, 30, 35, 36, 37, 38, 39,
        40, 41, 35, 42, 34, 43, 44, 45,
        46, 30, 47, 48, 49, 30, 53, 52,
        52, 54, 55, 52, 52, 52, 52, 52,
        52, 52, 52, 56, 52, 57, 58, 59,
        60, 61, 62, 57, 63, 56, 64, 52,
        52, 52, 52, 65, 66, 67, 52, 53,
        52, 52, 54, 55, 52, 52, 52, 52,
        52, 52, 52, 52, 68, 52, 57, 58,
        59, 60, 61, 62, 57, 63, 64, 64,
        52, 52, 52, 52, 65, 66, 67, 52,
        54, 51, 53, 52, 52, 54, 55, 52,
        52, 52, 52, 52, 52, 52, 52, 52,
        52, 57, 58, 59, 60, 61, 62, 57,
        63, 64, 64, 52, 52, 52, 52, 65,
        66, 67, 52, 53, 52, 52, 52, 52,
        52, 52, 52, 52, 52, 52, 52, 52,
        52, 52, 57, 58, 59, 60, 52, 52,
        52, 52, 52, 52, 52, 52, 52, 52,
        65, 66, 67, 52, 53, 52, 52, 52,
        52, 52, 52, 52, 52, 52, 52, 52,
        52, 52, 52, 52, 58, 59, 60, 52,
        53, 52, 52, 52, 52, 52, 52, 52,
        52, 52, 52, 52, 52, 52, 52, 52,
        52, 59, 60, 52, 53, 52, 52, 52,
        52, 52, 52, 52, 52, 52, 52, 52,
        52, 52, 52, 52, 52, 52, 60, 52,
        53, 52, 52, 52, 52, 52, 52, 52,
        52, 52, 52, 52, 52, 52, 52, 52,
        58, 59, 60, 52, 52, 52, 52, 52,
        52, 52, 52, 52, 52, 65, 66, 67,
        52, 53, 52, 52, 52, 52, 52, 52,
        52, 52, 52, 52, 52, 52, 52, 52,
        52, 58, 59, 60, 52, 52, 52, 52,
        52, 52, 52, 52, 52, 52, 52, 66,
        67, 52, 53, 52, 52, 52, 52, 52,
        52, 52, 52, 52, 52, 52, 52, 52,
        52, 52, 58, 59, 60, 52, 52, 52,
        52, 52, 52, 52, 52, 52, 52, 52,
        52, 67, 52, 53, 52, 52, 52, 52,
        52, 52, 52, 52, 52, 52, 52, 52,
        52, 52, 57, 58, 59, 60, 52, 62,
        57, 52, 52, 52, 52, 52, 52, 52,
        65, 66, 67, 52, 53, 52, 52, 52,
        52, 52, 52, 52, 52, 52, 52, 52,
        52, 52, 52, 57, 58, 59, 60, 52,
        52, 57, 52, 52, 52, 52, 52, 52,
        52, 65, 66, 67, 52, 53, 52, 52,
        52, 52, 52, 52, 52, 52, 52, 52,
        52, 52, 52, 52, 57, 58, 59, 60,
        61, 62, 57, 52, 52, 52, 52, 52,
        52, 52, 65, 66, 67, 52, 53, 52,
        52, 54, 55, 52, 52, 52, 52, 52,
        52, 52, 52, 52, 52, 57, 58, 59,
        60, 61, 62, 57, 63, 52, 64, 52,
        52, 52, 52, 65, 66, 67, 52, 53,
        52, 52, 54, 55, 52, 52, 52, 52,
        52, 52, 52, 52, 52, 52, 57, 58,
        59, 60, 61, 62, 57, 63, 56, 64,
        52, 52, 52, 52, 65, 66, 67, 52,
        70, 71, 69, 69, 69, 69, 69, 69,
        69, 72, 69, 70, 71, 69, 7, 73,
        73, 3, 9, 73, 73, 73, 73, 73,
        73, 73, 73, 74, 12, 13, 14, 15,
        16, 17, 18, 12, 19, 21, 21, 22,
        23, 24, 73, 25, 26, 27, 73, 7,
        73, 73, 3, 9, 73, 73, 73, 73,
        73, 73, 73, 73, 73, 12, 13, 14,
        15, 16, 17, 18, 12, 19, 21, 21,
        22, 23, 24, 73, 25, 26, 27, 73,
        7, 73, 73, 73, 73, 73, 73, 73,
        73, 73, 73, 73, 73, 73, 12, 13,
        14, 15, 16, 73, 73, 73, 73, 73,
        73, 22, 23, 24, 73, 25, 26, 27,
        73, 7, 73, 73, 73, 73, 73, 73,
        73, 73, 73, 73, 73, 73, 73, 73,
        13, 14, 15, 16, 73, 73, 73, 73,
        73, 73, 73, 73, 73, 73, 25, 26,
        27, 73, 7, 73, 73, 73, 73, 73,
        73, 73, 73, 73, 73, 73, 73, 73,
        73, 73, 14, 15, 16, 73, 7, 73,
        73, 73, 73, 73, 73, 73, 73, 73,
        73, 73, 73, 73, 73, 73, 73, 15,
        16, 73, 7, 73, 73, 73, 73, 73,
        73, 73, 73, 73, 73, 73, 73, 73,
        73, 73, 73, 73, 16, 73, 7, 73,
        73, 73, 73, 73, 73, 73, 73, 73,
        73, 73, 73, 73, 73, 73, 14, 15,
        16, 73, 73, 73, 73, 73, 73, 73,
        73, 73, 73, 25, 26, 27, 73, 7,
        73, 73, 73, 73, 73, 73, 73, 73,
        73, 73, 73, 73, 73, 73, 73, 14,
        15, 16, 73, 73, 73, 73, 73, 73,
        73, 73, 73, 73, 73, 26, 27, 73,
        7, 73, 73, 73, 73, 73, 73, 73,
        73, 73, 73, 73, 73, 73, 73, 73,
        14, 15, 16, 73, 73, 73, 73, 73,
        73, 73, 73, 73, 73, 73, 73, 27,
        73, 7, 73, 73, 73, 73, 73, 73,
        73, 73, 73, 73, 73, 73, 73, 73,
        13, 14, 15, 16, 73, 73, 73, 73,
        73, 73, 22, 23, 24, 73, 25, 26,
        27, 73, 7, 73, 73, 73, 73, 73,
        73, 73, 73, 73, 73, 73, 73, 73,
        73, 13, 14, 15, 16, 73, 73, 73,
        73, 73, 73, 73, 23, 24, 73, 25,
        26, 27, 73, 7, 73, 73, 73, 73,
        73, 73, 73, 73, 73, 73, 73, 73,
        73, 73, 13, 14, 15, 16, 73, 73,
        73, 73, 73, 73, 73, 73, 24, 73,
        25, 26, 27, 73, 7, 73, 73, 73,
        73, 73, 73, 73, 73, 73, 73, 73,
        73, 73, 12, 13, 14, 15, 16, 73,
        18, 12, 73, 73, 73, 22, 23, 24,
        73, 25, 26, 27, 73, 7, 73, 73,
        73, 73, 73, 73, 73, 73, 73, 73,
        73, 73, 73, 12, 13, 14, 15, 16,
        73, 73, 12, 73, 73, 73, 22, 23,
        24, 73, 25, 26, 27, 73, 7, 73,
        73, 73, 73, 73, 73, 73, 73, 73,
        73, 73, 73, 73, 12, 13, 14, 15,
        16, 17, 18, 12, 73, 73, 73, 22,
        23, 24, 73, 25, 26, 27, 73, 7,
        73, 73, 3, 9, 73, 73, 73, 73,
        73, 73, 73, 73, 73, 12, 13, 14,
        15, 16, 17, 18, 12, 19, 73, 21,
        22, 23, 24, 73, 25, 26, 27, 73,
        5, 6, 73, 73, 5, 73, 73, 7,
        73, 73, 3, 9, 73, 73, 73, 73,
        73, 73, 73, 73, 73, 12, 13, 14,
        15, 16, 17, 18, 12, 19, 20, 21,
        22, 23, 24, 73, 25, 26, 27, 73,
        7, 73, 73, 3, 9, 73, 73, 73,
        73, 73, 73, 73, 73, 73, 12, 13,
        14, 15, 16, 17, 18, 12, 19, 20,
        21, 22, 23, 24, 73, 25, 26, 27,
        73, 76, 75, 75, 75, 75, 75, 75,
        75, 75, 75, 75, 75, 75, 75, 75,
        75, 75, 75, 75, 75, 76, 77, 75,
        76, 77, 75, 77, 75, 0
};

static const char _use_syllable_machine_trans_targs[] = {
        3, 41, 3, 43, 4, 5, 25, 3,
        0, 2, 60, 62, 45, 46, 47, 48,
        49, 56, 57, 58, 61, 59, 53, 54,
        55, 50, 51, 52, 3, 3, 3, 3,
        6, 7, 24, 9, 10, 11, 12, 13,
        20, 21, 22, 23, 17, 18, 19, 14,
        15, 16, 8, 3, 3, 3, 26, 27,
        40, 29, 30, 31, 32, 36, 37, 38,
        39, 33, 34, 35, 28, 3, 3, 1,
        42, 3, 44, 3, 63, 64
};

static const char _use_syllable_machine_trans_actions[] = {
        1, 2, 3, 4, 0, 0, 0, 7,
        0, 0, 4, 0, 0, 0, 0, 0,
        0, 0, 0, 0, 4, 4, 0, 0,
        0, 0, 0, 0, 8, 9, 10, 11,
        0, 0, 0, 0, 0, 0, 0, 0,
        0, 0, 0, 0, 0, 0, 0, 0,
        0, 0, 0, 12, 13, 14, 0, 0,
        0, 0, 0, 0, 0, 0, 0, 0,
        0, 0, 0, 0, 0, 15, 16, 0,
        2, 17, 4, 18, 0, 0
};

static const char _use_syllable_machine_to_state_actions[] = {
        0, 0, 0, 5, 0, 0, 0, 0,
        0, 0, 0, 0, 0, 0, 0, 0,
        0, 0, 0, 0, 0, 0, 0, 0,
        0, 0, 0, 0, 0, 0, 0, 0,
        0, 0, 0, 0, 0, 0, 0, 0,
        0, 0, 0, 0, 0, 0, 0, 0,
        0, 0, 0, 0, 0, 0, 0, 0,
        0, 0, 0, 0, 0, 0, 0, 0,
        0
};

static const char _use_syllable_machine_from_state_actions[] = {
        0, 0, 0, 6, 0, 0, 0, 0,
        0, 0, 0, 0, 0, 0, 0, 0,
        0, 0, 0, 0, 0, 0, 0, 0,
        0, 0, 0, 0, 0, 0, 0, 0,
        0, 0, 0, 0, 0, 0, 0, 0,
        0, 0, 0, 0, 0, 0, 0, 0,
        0, 0, 0, 0, 0, 0, 0, 0,
        0, 0, 0, 0, 0, 0, 0, 0,
        0
};

static const short _use_syllable_machine_eof_trans[] = {
        0, 1, 3, 0, 29, 31, 31, 52,
        31, 31, 31, 31, 31, 31, 31, 31,
        31, 31, 31, 31, 31, 31, 31, 31,
        31, 53, 53, 52, 53, 53, 53, 53,
        53, 53, 53, 53, 53, 53, 53, 53,
        53, 70, 70, 74, 74, 74, 74, 74,
        74, 74, 74, 74, 74, 74, 74, 74,
        74, 74, 74, 74, 74, 74, 76, 76,
        76
};

static const int use_syllable_machine_start = 3;
static const int use_syllable_machine_first_final = 3;
static const int use_syllable_machine_error = 0;

static const int use_syllable_machine_en_main = 3;


#line 38 "hb-ot-shape-complex-use-machine.rl"



#line 145 "hb-ot-shape-complex-use-machine.rl"


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

#line 388 "hb-ot-shape-complex-use-machine.hh"
        {
        cs = use_syllable_machine_start;
        ts = 0;
        te = 0;
        act = 0;
        }

#line 166 "hb-ot-shape-complex-use-machine.rl"


  p = 0;
  pe = eof = buffer->len;

  unsigned int last = 0;
  unsigned int syllable_serial = 1;

#line 405 "hb-ot-shape-complex-use-machine.hh"
        {
        int _slen;
        int _trans;
        const unsigned char *_keys;
        const char *_inds;
        if ( p == pe )
                goto _test_eof;
        if ( cs == 0 )
                goto _out;
_resume:
        switch ( _use_syllable_machine_from_state_actions[cs] ) {
        case 6:
#line 1 "NONE"
        {ts = p;}
        break;
#line 421 "hb-ot-shape-complex-use-machine.hh"
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
        case 9:
#line 134 "hb-ot-shape-complex-use-machine.rl"
        {te = p+1;{ found_syllable (independent_cluster); }}
        break;
        case 11:
#line 136 "hb-ot-shape-complex-use-machine.rl"
        {te = p+1;{ found_syllable (consonant_cluster); }}
        break;
        case 14:
#line 137 "hb-ot-shape-complex-use-machine.rl"
        {te = p+1;{ found_syllable (vowel_cluster); }}
        break;
        case 16:
#line 138 "hb-ot-shape-complex-use-machine.rl"
        {te = p+1;{ found_syllable (number_joiner_terminated_cluster); }}
        break;
        case 7:
#line 141 "hb-ot-shape-complex-use-machine.rl"
        {te = p+1;{ found_syllable (broken_cluster); }}
        break;
        case 8:
#line 134 "hb-ot-shape-complex-use-machine.rl"
        {te = p;p--;{ found_syllable (independent_cluster); }}
        break;
        case 12:
#line 135 "hb-ot-shape-complex-use-machine.rl"
        {te = p;p--;{ found_syllable (virama_terminated_cluster); }}
        break;
        case 10:
#line 136 "hb-ot-shape-complex-use-machine.rl"
        {te = p;p--;{ found_syllable (consonant_cluster); }}
        break;
        case 13:
#line 137 "hb-ot-shape-complex-use-machine.rl"
        {te = p;p--;{ found_syllable (vowel_cluster); }}
        break;
        case 15:
#line 139 "hb-ot-shape-complex-use-machine.rl"
        {te = p;p--;{ found_syllable (numeral_cluster); }}
        break;
        case 18:
#line 140 "hb-ot-shape-complex-use-machine.rl"
        {te = p;p--;{ found_syllable (symbol_cluster); }}
        break;
        case 17:
#line 141 "hb-ot-shape-complex-use-machine.rl"
        {te = p;p--;{ found_syllable (broken_cluster); }}
        break;
        case 1:
#line 139 "hb-ot-shape-complex-use-machine.rl"
        {{p = ((te))-1;}{ found_syllable (numeral_cluster); }}
        break;
        case 3:
#line 1 "NONE"
        {       switch( act ) {
        case 0:
        {{cs = 0; goto _again;}}
        break;
        case 8:
        {{p = ((te))-1;} found_syllable (broken_cluster); }
        break;
        }
        }
        break;
        case 4:
#line 1 "NONE"
        {te = p+1;}
#line 141 "hb-ot-shape-complex-use-machine.rl"
        {act = 8;}
        break;
#line 513 "hb-ot-shape-complex-use-machine.hh"
        }

_again:
        switch ( _use_syllable_machine_to_state_actions[cs] ) {
        case 5:
#line 1 "NONE"
        {ts = 0;}
#line 1 "NONE"
        {act = 0;}
        break;
#line 524 "hb-ot-shape-complex-use-machine.hh"
        }

        if ( cs == 0 )
                goto _out;
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

        _out: {}
        }

#line 175 "hb-ot-shape-complex-use-machine.rl"

}

#undef found_syllable

#endif /* HB_OT_SHAPE_COMPLEX_USE_MACHINE_HH */
