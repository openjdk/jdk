/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 *
 */

/*
 *
 * (C) Copyright IBM Corp. 1998-2013 - All Rights Reserved
 *
 */

#include "LETypes.h"
#include "LEScripts.h"
#include "OpenTypeTables.h"
#include "OpenTypeUtilities.h"
#include "IndicReordering.h"

U_NAMESPACE_BEGIN

// Split matra table indices
#define _x1  (1 << CF_INDEX_SHIFT)
#define _x2  (2 << CF_INDEX_SHIFT)
#define _x3  (3 << CF_INDEX_SHIFT)
#define _x4  (4 << CF_INDEX_SHIFT)
#define _x5  (5 << CF_INDEX_SHIFT)
#define _x6  (6 << CF_INDEX_SHIFT)
#define _x7  (7 << CF_INDEX_SHIFT)
#define _x8  (8 << CF_INDEX_SHIFT)
#define _x9  (9 << CF_INDEX_SHIFT)

// simple classes
#define _xx  (CC_RESERVED)
#define _ma  (CC_VOWEL_MODIFIER | CF_POS_ABOVE)
#define _mp  (CC_VOWEL_MODIFIER | CF_POS_AFTER)
#define _sa  (CC_STRESS_MARK | CF_POS_ABOVE)
#define _sb  (CC_STRESS_MARK | CF_POS_BELOW)
#define _iv  (CC_INDEPENDENT_VOWEL)
#define _i2  (CC_INDEPENDENT_VOWEL_2)
#define _i3  (CC_INDEPENDENT_VOWEL_3)
#define _ct  (CC_CONSONANT | CF_CONSONANT)
#define _cn  (CC_CONSONANT_WITH_NUKTA | CF_CONSONANT)
#define _nu  (CC_NUKTA)
#define _dv  (CC_DEPENDENT_VOWEL)
#define _dl  (_dv | CF_POS_BEFORE)
#define _db  (_dv | CF_POS_BELOW)
#define _da  (_dv | CF_POS_ABOVE)
#define _dr  (_dv | CF_POS_AFTER)
#define _lm  (_dv | CF_LENGTH_MARK)
#define _l1  (CC_SPLIT_VOWEL_PIECE_1 | CF_POS_BEFORE)
#define _a1  (CC_SPLIT_VOWEL_PIECE_1 | CF_POS_ABOVE)
#define _b2  (CC_SPLIT_VOWEL_PIECE_2 | CF_POS_BELOW)
#define _r2  (CC_SPLIT_VOWEL_PIECE_2 | CF_POS_AFTER)
#define _m2  (CC_SPLIT_VOWEL_PIECE_2 | CF_LENGTH_MARK)
#define _m3  (CC_SPLIT_VOWEL_PIECE_3 | CF_LENGTH_MARK)
#define _vr  (CC_VIRAMA)
#define _al  (CC_AL_LAKUNA)

// split matras
#define _s1  (_dv | _x1)
#define _s2  (_dv | _x2)
#define _s3  (_dv | _x3)
#define _s4  (_dv | _x4)
#define _s5  (_dv | _x5)
#define _s6  (_dv | _x6)
#define _s7  (_dv | _x7)
#define _s8  (_dv | _x8)
#define _s9  (_dv | _x9)

// consonants with special forms
// NOTE: this assumes that no consonants with nukta have
// special forms... (Bengali RA?)
#define _bb  (_ct | CF_BELOW_BASE)
#define _pb  (_ct | CF_POST_BASE)
#define _fb  (_ct | CF_PRE_BASE)
#define _vt  (_bb | CF_VATTU)
#define _rv  (_vt | CF_REPH)
#define _rp  (_pb | CF_REPH)
#define _rb  (_bb | CF_REPH)

//
// Character class tables
//
static const IndicClassTable::CharClass devaCharClasses[] =
{
    _xx, _ma, _ma, _mp, _iv, _iv, _iv, _iv, _iv, _iv, _iv, _iv, _iv, _iv, _iv, _iv, // 0900 - 090F
    _iv, _iv, _iv, _iv, _iv, _ct, _ct, _ct, _ct, _ct, _ct, _ct, _ct, _ct, _ct, _ct, // 0910 - 091F
    _ct, _ct, _ct, _ct, _ct, _ct, _ct, _ct, _ct, _cn, _ct, _ct, _ct, _ct, _ct, _ct, // 0920 - 092F
    _rv, _cn, _ct, _ct, _cn, _ct, _ct, _ct, _ct, _ct, _xx, _xx, _nu, _xx, _dr, _dl, // 0930 - 093F
    _dr, _db, _db, _db, _db, _da, _da, _da, _da, _dr, _dr, _dr, _dr, _vr, _xx, _xx, // 0940 - 094F
    _xx, _sa, _sb, _sa, _sa, _xx, _xx, _xx, _cn, _cn, _cn, _cn, _cn, _cn, _cn, _cn, // 0950 - 095F
    _iv, _iv, _db, _db, _xx, _xx, _xx, _xx, _xx, _xx, _xx, _xx, _xx, _xx, _xx, _xx, // 0960 - 096F
    _xx                                                                             // 0970
};

static const IndicClassTable::CharClass bengCharClasses[] =
{
    _xx, _ma, _mp, _mp, _xx, _i2, _iv, _iv, _iv, _iv, _iv, _iv, _iv, _xx, _xx, _i2, // 0980 - 098F
    _iv, _xx, _xx, _iv, _iv, _ct, _ct, _ct, _ct, _ct, _ct, _ct, _ct, _ct, _ct, _ct, // 0990 - 099F
    _ct, _ct, _ct, _ct, _ct, _ct, _ct, _ct, _ct, _xx, _ct, _ct, _bb, _ct, _ct, _pb, // 09A0 - 09AF
    _rv, _xx, _ct, _xx, _xx, _xx, _ct, _ct, _ct, _ct, _xx, _xx, _nu, _xx, _r2, _dl, // 09B0 - 09BF
    _dr, _db, _db, _db, _db, _xx, _xx, _l1, _dl, _xx, _xx, _s1, _s2, _vr, _xx, _xx, // 09C0 - 09CF
    _xx, _xx, _xx, _xx, _xx, _xx, _xx, _m2, _xx, _xx, _xx, _xx, _cn, _cn, _xx, _cn, // 09D0 - 09DF
    _iv, _iv, _dv, _dv, _xx, _xx, _xx, _xx, _xx, _xx, _xx, _xx, _xx, _xx, _xx, _xx, // 09E0 - 09EF
    _rv, _ct, _xx, _xx, _xx, _xx, _xx, _xx, _xx, _xx, _xx                           // 09F0 - 09FA
};

static const IndicClassTable::CharClass punjCharClasses[] =
{
    _xx, _ma, _ma, _mp, _xx, _iv, _iv, _iv, _iv, _iv, _iv, _xx, _xx, _xx, _xx, _iv, // 0A00 - 0A0F
    _iv, _xx, _xx, _i3, _iv, _ct, _ct, _ct, _ct, _ct, _ct, _ct, _ct, _ct, _ct, _ct, // 0A10 - 0A1F
    _ct, _ct, _ct, _ct, _ct, _ct, _ct, _ct, _ct, _xx, _ct, _ct, _ct, _ct, _ct, _bb, // 0A20 - 0A2F
    _vt, _xx, _ct, _cn, _xx, _bb, _cn, _xx, _ct, _bb, _xx, _xx, _nu, _xx, _dr, _dl, // 0A30 - 0A3F
    _dr, _b2, _db, _xx, _xx, _xx, _xx, _da, _da, _xx, _xx, _a1, _da, _vr, _xx, _xx, // 0A40 - 0A4F
    _xx, _xx, _xx, _xx, _xx, _xx, _xx, _xx, _xx, _cn, _cn, _cn, _ct, _xx, _cn, _xx, // 0A50 - 0A5F
    _xx, _xx, _xx, _xx, _xx, _xx, _xx, _xx, _xx, _xx, _xx, _xx, _xx, _xx, _xx, _xx, // 0A60 - 0A6F
    _ma, _ma, _xx, _xx, _xx                                                         // 0A70 - 0A74
};

static const IndicClassTable::CharClass gujrCharClasses[] =
{
    _xx, _ma, _ma, _mp, _xx, _iv, _iv, _iv, _iv, _iv, _iv, _iv, _xx, _iv, _xx, _iv, // 0A80 - 0A8F
    _iv, _iv, _xx, _iv, _iv, _ct, _ct, _ct, _ct, _ct, _ct, _ct, _ct, _ct, _ct, _ct, // 0A90 - 0A9F
    _ct, _ct, _ct, _ct, _ct, _ct, _ct, _ct, _ct, _xx, _ct, _ct, _ct, _ct, _ct, _ct, // 0AA0 - 0AAF
    _rv, _xx, _ct, _ct, _xx, _ct, _ct, _ct, _ct, _ct, _xx, _xx, _nu, _xx, _dr, _dl, // 0AB0 - 0ABF
    _dr, _db, _db, _db, _db, _da, _xx, _da, _da, _dr, _xx, _dr, _dr, _vr, _xx, _xx, // 0AC0 - 0ACF
    _xx, _xx, _xx, _xx, _xx, _xx, _xx, _xx, _xx, _xx, _xx, _xx, _xx, _xx, _xx, _xx, // 0AD0 - 0ADF
    _iv, _iv, _db, _db, _xx, _xx, _xx, _xx, _xx, _xx, _xx, _xx, _xx, _xx, _xx, _xx  // 0AE0 - 0AEF
};

#if 1
static const IndicClassTable::CharClass oryaCharClasses[] =
{
    _xx, _ma, _mp, _mp, _xx, _iv, _iv, _iv, _iv, _iv, _iv, _iv, _iv, _xx, _xx, _iv, /* 0B00 - 0B0F */
    _iv, _xx, _xx, _iv, _iv, _bb, _bb, _bb, _bb, _bb, _bb, _bb, _bb, _bb, _ct, _bb, /* 0B10 - 0B1F */
    _bb, _bb, _bb, _bb, _bb, _bb, _bb, _bb, _bb, _xx, _bb, _bb, _bb, _bb, _bb, _pb, /* 0B20 - 0B2F */
    _rb, _xx, _bb, _bb, _xx, _bb, _bb, _bb, _bb, _bb, _xx, _xx, _nu, _xx, _dr, _da, /* 0B30 - 0B3F */
    _dr, _db, _db, _db, _xx, _xx, _xx, _dl, _s1, _xx, _xx, _s2, _s3, _vr, _xx, _xx, /* 0B40 - 0B4F */
    _xx, _xx, _xx, _xx, _xx, _xx, _da, _dr, _xx, _xx, _xx, _xx, _cn, _cn, _xx, _pb, /* 0B50 - 0B5F */
    _iv, _iv, _xx, _xx, _xx, _xx, _xx, _xx, _xx, _xx, _xx, _xx, _xx, _xx, _xx, _xx, /* 0B60 - 0B6F */
    _xx, _bb                                                                        /* 0B70 - 0B71 */
};
#else
static const IndicClassTable::CharClass oryaCharClasses[] =
{
    _xx, _ma, _mp, _mp, _xx, _iv, _iv, _iv, _iv, _iv, _iv, _iv, _iv, _xx, _xx, _iv, // 0B00 - 0B0F
    _iv, _xx, _xx, _iv, _iv, _ct, _ct, _ct, _ct, _ct, _ct, _ct, _ct, _ct, _ct, _ct, // 0B10 - 0B1F
    _ct, _ct, _ct, _ct, _bb, _ct, _ct, _ct, _bb, _xx, _ct, _ct, _bb, _bb, _bb, _pb, // 0B20 - 0B2F
    _rb, _xx, _bb, _bb, _xx, _ct, _ct, _ct, _ct, _ct, _xx, _xx, _nu, _xx, _r2, _da, // 0B30 - 0B3F
    _dr, _db, _db, _db, _xx, _xx, _xx, _l1, _s1, _xx, _xx, _s2, _s3, _vr, _xx, _xx, // 0B40 - 0B4F
    _xx, _xx, _xx, _xx, _xx, _xx, _m2, _m2, _xx, _xx, _xx, _xx, _cn, _cn, _xx, _cn, // 0B50 - 0B5F
    _iv, _iv, _xx, _xx, _xx, _xx, _xx, _xx, _xx, _xx, _xx, _xx, _xx, _xx, _xx, _xx, // 0B60 - 0B6F
    _xx, _ct                                                                        // 0B70 - 0B71
};
#endif

static const IndicClassTable::CharClass tamlCharClasses[] =
{
    _xx, _xx, _ma, _xx, _xx, _iv, _iv, _iv, _iv, _iv, _iv, _xx, _xx, _xx, _iv, _iv, // 0B80 - 0B8F
    _iv, _xx, _iv, _iv, _iv, _ct, _xx, _xx, _xx, _ct, _ct, _xx, _ct, _xx, _ct, _ct, // 0B90 - 0B9F
    _xx, _xx, _xx, _ct, _ct, _xx, _xx, _xx, _ct, _ct, _ct, _xx, _xx, _xx, _ct, _ct, // 0BA0 - 0BAF
    _ct, _ct, _ct, _ct, _ct, _ct, _ct, _ct, _ct, _ct, _xx, _xx, _xx, _xx, _r2, _dr, // 0BB0 - 0BBF
    _da, _dr, _dr, _xx, _xx, _xx, _l1, _l1, _dl, _xx, _s1, _s2, _s3, _vr, _xx, _xx, // 0BC0 - 0BCF
    _xx, _xx, _xx, _xx, _xx, _xx, _xx, _m2, _xx, _xx, _xx, _xx, _xx, _xx, _xx, _xx, // 0BD0 - 0BDF
    _xx, _xx, _xx, _xx, _xx, _xx, _xx, _xx, _xx, _xx, _xx, _xx, _xx, _xx, _xx, _xx, // 0BE0 - 0BEF
    _xx, _xx, _xx                                                                   // 0BF0 - 0BF2
};

// FIXME: Should some of the bb's be pb's? (KA, NA, MA, YA, VA, etc. (approx 13))
// U+C43 and U+C44 are _lm here not _dr.  Similar to the situation with U+CC3 and
// U+CC4 in Kannada below.
static const IndicClassTable::CharClass teluCharClasses[] =
{
    _xx, _mp, _mp, _mp, _xx, _iv, _iv, _iv, _iv, _iv, _iv, _iv, _iv, _xx, _iv, _iv, // 0C00 - 0C0F
    _iv, _xx, _iv, _iv, _iv, _bb, _bb, _bb, _bb, _bb, _bb, _bb, _bb, _bb, _bb, _bb, // 0C10 - 0C1F
    _bb, _bb, _bb, _bb, _bb, _bb, _bb, _bb, _bb, _xx, _bb, _bb, _bb, _bb, _bb, _bb, // 0C20 - 0C2F
    _bb, _bb, _bb, _bb, _xx, _bb, _bb, _bb, _bb, _bb, _xx, _xx, _xx, _xx, _da, _da, // 0C30 - 0C3F
    _da, _dr, _dr, _lm, _lm, _xx, _a1, _da, _s1, _xx, _da, _da, _da, _vr, _xx, _xx, // 0C40 - 0C4F
    _xx, _xx, _xx, _xx, _xx, _da, _m2, _xx, _xx, _xx, _xx, _xx, _xx, _xx, _xx, _xx, // 0C50 - 0C5F
    _iv, _iv, _xx, _xx, _xx, _xx, _xx, _xx, _xx, _xx, _xx, _xx, _xx, _xx, _xx, _xx  // 0C60 - 0C6F
};

// U+CC3 and U+CC4 are _lm here not _dr since the Kannada rendering
// rules want them below and to the right of the entire cluster
//
// There's some information about this in:
//
//  http://brahmi.sourceforge.net/docs/KannadaComputing.html
static const IndicClassTable::CharClass kndaCharClasses[] =
{
    _xx, _xx, _mp, _mp, _xx, _iv, _iv, _iv, _iv, _iv, _iv, _iv, _iv, _xx, _iv, _iv, // 0C80 - 0C8F
    _iv, _xx, _iv, _iv, _iv, _bb, _bb, _bb, _bb, _bb, _bb, _bb, _bb, _bb, _bb, _bb, // 0C90 - 0C9F
    _bb, _bb, _bb, _bb, _bb, _bb, _bb, _bb, _bb, _xx, _bb, _bb, _bb, _bb, _bb, _bb, // 0CA0 - 0CAF
    _rb, _ct, _bb, _bb, _xx, _bb, _bb, _bb, _bb, _bb, _xx, _xx, _xx, _xx, _dr, _da, // 0CB0 - 0CBF
    _s1, _dr, _r2, _lm, _lm, _xx, _a1, _s2, _s3, _xx, _s4, _s5, _da, _vr, _xx, _xx, // 0CC0 - 0CCF
    _xx, _xx, _xx, _xx, _xx, _m3, _m2, _xx, _xx, _xx, _xx, _xx, _xx, _xx, _ct, _xx, // 0CD0 - 0CDF
    _iv, _iv, _xx, _xx, _xx, _xx, _xx, _xx, _xx, _xx, _xx, _xx, _xx, _xx, _xx, _xx  // 0CE0 - 0CEF
};

// FIXME: this is correct for old-style Malayalam (MAL) but not for reformed Malayalam (MLR)
// FIXME: should there be a REPH for old-style Malayalam?
static const IndicClassTable::CharClass mlymCharClasses[] =
{
    _xx, _xx, _mp, _mp, _xx, _iv, _iv, _iv, _iv, _iv, _iv, _iv, _iv, _xx, _iv, _iv, // 0D00 - 0D0F
    _iv, _xx, _iv, _iv, _iv, _ct, _ct, _ct, _ct, _ct, _ct, _ct, _ct, _ct, _ct, _ct, // 0D10 - 0D1F
    _ct, _ct, _ct, _ct, _ct, _ct, _ct, _ct, _ct, _xx, _ct, _ct, _ct, _ct, _ct, _pb, // 0D20 - 0D2F
    _fb, _fb, _bb, _ct, _ct, _pb, _ct, _ct, _ct, _ct, _xx, _xx, _xx, _xx, _r2, _dr, // 0D30 - 0D3F
    _dr, _dr, _dr, _dr, _xx, _xx, _l1, _l1, _dl, _xx, _s1, _s2, _s3, _vr, _xx, _xx, // 0D40 - 0D4F
    _xx, _xx, _xx, _xx, _xx, _xx, _xx, _m2, _xx, _xx, _xx, _xx, _xx, _xx, _xx, _xx, // 0D50 - 0D5F
    _iv, _iv, _xx, _xx, _xx, _xx, _xx, _xx, _xx, _xx, _xx, _xx, _xx, _xx, _xx, _xx  // 0D60 - 0D6F
};

static const IndicClassTable::CharClass sinhCharClasses[] =
{
    _xx, _xx, _mp, _mp, _xx, _iv, _iv, _iv, _iv, _iv, _iv, _iv, _iv, _iv, _iv, _iv, // 0D80 - 0D8F
    _iv, _iv, _iv, _iv, _iv, _iv, _iv, _xx, _xx, _xx, _ct, _ct, _ct, _ct, _ct, _ct, // 0D90 - 0D9F
    _ct, _ct, _ct, _ct, _ct, _ct, _ct, _ct, _ct, _ct, _ct, _ct, _ct, _ct, _ct, _ct, // 0DA0 - 0DAF
    _ct, _ct, _xx, _ct, _ct, _ct, _ct, _ct, _ct, _ct, _ct, _ct, _xx, _ct, _xx, _xx, // 0DB0 - 0DBF
    _ct, _ct, _ct, _ct, _ct, _ct, _ct, _xx, _xx, _xx, _al, _xx, _xx, _xx, _xx, _dr, // 0DC0 - 0DCF
    _dr, _dr, _da, _da, _db, _xx, _db, _xx, _dr, _dl, _s1, _dl, _s2, _s3, _s4, _dr, // 0DD0 - 0DDF
    _xx, _xx, _xx, _xx, _xx, _xx, _xx, _xx, _xx, _xx, _xx, _xx, _xx, _xx, _xx, _xx, // 0DE0 - 0DEF
    _xx, _xx, _dr, _dr, _xx                                                         // 0DF0 - 0DF4
};

//
// Split matra tables
//
static const SplitMatra bengSplitTable[] = {{0x09C7, 0x09BE}, {0x09C7, 0x09D7}};

static const SplitMatra oryaSplitTable[] = {{0x0B47, 0x0B56}, {0x0B47, 0x0B3E}, {0x0B47, 0x0B57}};

static const SplitMatra tamlSplitTable[] = {{0x0BC6, 0x0BBE}, {0x0BC7, 0x0BBE}, {0x0BC6, 0x0BD7}};

static const SplitMatra teluSplitTable[] = {{0x0C46, 0x0C56}};

static const SplitMatra kndaSplitTable[] = {{0x0CBF, 0x0CD5}, {0x0CC6, 0x0CD5}, {0x0CC6, 0x0CD6}, {0x0CC6, 0x0CC2},
                                            {0x0CC6, 0x0CC2, 0x0CD5}};

static const SplitMatra mlymSplitTable[] = {{0x0D46, 0x0D3E}, {0x0D47, 0x0D3E}, {0x0D46, 0x0D57}};


static const SplitMatra sinhSplitTable[] = {{0x0DD9, 0x0DCA}, {0x0DD9, 0x0DCF}, {0x0DD9, 0x0DCF,0x0DCA},
                                            {0x0DD9, 0x0DDF}};
//
// Script Flags
//

// FIXME: post 'GSUB' reordering of MATRA_PRE's for Malayalam and Tamil
// FIXME: reformed Malayalam needs to reorder VATTU to before base glyph...
// FIXME: not sure passing ZWJ/ZWNJ is best way to render Malayalam Cillu...
// FIXME: eyelash RA only for Devanagari??
#define DEVA_SCRIPT_FLAGS (SF_EYELASH_RA | SF_NO_POST_BASE_LIMIT | SF_FILTER_ZERO_WIDTH)
#define BENG_SCRIPT_FLAGS (SF_REPH_AFTER_BELOW | SF_NO_POST_BASE_LIMIT | SF_FILTER_ZERO_WIDTH)
#define PUNJ_SCRIPT_FLAGS (SF_NO_POST_BASE_LIMIT | SF_FILTER_ZERO_WIDTH)
#define GUJR_SCRIPT_FLAGS (SF_NO_POST_BASE_LIMIT | SF_FILTER_ZERO_WIDTH)
#define ORYA_SCRIPT_FLAGS (SF_REPH_AFTER_BELOW | SF_NO_POST_BASE_LIMIT | SF_FILTER_ZERO_WIDTH)
#define TAML_SCRIPT_FLAGS (SF_MPRE_FIXUP | SF_NO_POST_BASE_LIMIT | SF_FILTER_ZERO_WIDTH)
#define TELU_SCRIPT_FLAGS (SF_MATRAS_AFTER_BASE | SF_FILTER_ZERO_WIDTH | 3)
#define KNDA_SCRIPT_FLAGS (SF_MATRAS_AFTER_BASE | SF_FILTER_ZERO_WIDTH | 3)
#define MLYM_SCRIPT_FLAGS (SF_MPRE_FIXUP | SF_NO_POST_BASE_LIMIT /*| SF_FILTER_ZERO_WIDTH*/)
#define SINH_SCRIPT_FLAGS (SF_NO_POST_BASE_LIMIT)

//
// Indic Class Tables
//
static const IndicClassTable devaClassTable = {0x0900, 0x0970, 2, DEVA_SCRIPT_FLAGS, devaCharClasses, NULL};

static const IndicClassTable bengClassTable = {0x0980, 0x09FA, 3, BENG_SCRIPT_FLAGS, bengCharClasses, bengSplitTable};

static const IndicClassTable punjClassTable = {0x0A00, 0x0A74, 2, PUNJ_SCRIPT_FLAGS, punjCharClasses, NULL};

static const IndicClassTable gujrClassTable = {0x0A80, 0x0AEF, 2, GUJR_SCRIPT_FLAGS, gujrCharClasses, NULL};

static const IndicClassTable oryaClassTable = {0x0B00, 0x0B71, 3, ORYA_SCRIPT_FLAGS, oryaCharClasses, oryaSplitTable};

static const IndicClassTable tamlClassTable = {0x0B80, 0x0BF2, 3, TAML_SCRIPT_FLAGS, tamlCharClasses, tamlSplitTable};

static const IndicClassTable teluClassTable = {0x0C00, 0x0C6F, 3, TELU_SCRIPT_FLAGS, teluCharClasses, teluSplitTable};

static const IndicClassTable kndaClassTable = {0x0C80, 0x0CEF, 4, KNDA_SCRIPT_FLAGS, kndaCharClasses, kndaSplitTable};

static const IndicClassTable mlymClassTable = {0x0D00, 0x0D6F, 3, MLYM_SCRIPT_FLAGS, mlymCharClasses, mlymSplitTable};

static const IndicClassTable sinhClassTable = {0x0D80, 0x0DF4, 4, SINH_SCRIPT_FLAGS, sinhCharClasses, sinhSplitTable};

//
// IndicClassTable addresses
//
static const IndicClassTable * const indicClassTables[scriptCodeCount] = {
    NULL,            /* 'zyyy' (COMMON) */
    NULL,            /* 'qaai' (INHERITED) */
    NULL,            /* 'arab' (ARABIC) */
    NULL,            /* 'armn' (ARMENIAN) */
    &bengClassTable, /* 'beng' (BENGALI) */
    NULL,            /* 'bopo' (BOPOMOFO) */
    NULL,            /* 'cher' (CHEROKEE) */
    NULL,            /* 'copt' (COPTIC) */
    NULL,            /* 'cyrl' (CYRILLIC) */
    NULL,            /* 'dsrt' (DESERET) */
    &devaClassTable, /* 'deva' (DEVANAGARI) */
    NULL,            /* 'ethi' (ETHIOPIC) */
    NULL,            /* 'geor' (GEORGIAN) */
    NULL,            /* 'goth' (GOTHIC) */
    NULL,            /* 'grek' (GREEK) */
    &gujrClassTable, /* 'gujr' (GUJARATI) */
    &punjClassTable, /* 'guru' (GURMUKHI) */
    NULL,            /* 'hani' (HAN) */
    NULL,            /* 'hang' (HANGUL) */
    NULL,            /* 'hebr' (HEBREW) */
    NULL,            /* 'hira' (HIRAGANA) */
    &kndaClassTable, /* 'knda' (KANNADA) */
    NULL,            /* 'kata' (KATAKANA) */
    NULL,            /* 'khmr' (KHMER) */
    NULL,            /* 'laoo' (LAO) */
    NULL,            /* 'latn' (LATIN) */
    &mlymClassTable, /* 'mlym' (MALAYALAM) */
    NULL,            /* 'mong' (MONGOLIAN) */
    NULL,            /* 'mymr' (MYANMAR) */
    NULL,            /* 'ogam' (OGHAM) */
    NULL,            /* 'ital' (OLD-ITALIC) */
    &oryaClassTable, /* 'orya' (ORIYA) */
    NULL,            /* 'runr' (RUNIC) */
    &sinhClassTable, /* 'sinh' (SINHALA) */
    NULL,            /* 'syrc' (SYRIAC) */
    &tamlClassTable, /* 'taml' (TAMIL) */
    &teluClassTable, /* 'telu' (TELUGU) */
    NULL,            /* 'thaa' (THAANA) */
    NULL,            /* 'thai' (THAI) */
    NULL,            /* 'tibt' (TIBETAN) */
    NULL,            /* 'cans' (CANADIAN-ABORIGINAL) */
    NULL,            /* 'yiii' (YI) */
    NULL,            /* 'tglg' (TAGALOG) */
    NULL,            /* 'hano' (HANUNOO) */
    NULL,            /* 'buhd' (BUHID) */
    NULL,            /* 'tagb' (TAGBANWA) */
    NULL,            /* 'brai' (BRAILLE) */
    NULL,            /* 'cprt' (CYPRIOT) */
    NULL,            /* 'limb' (LIMBU) */
    NULL,            /* 'linb' (LINEAR_B) */
    NULL,            /* 'osma' (OSMANYA) */
    NULL,            /* 'shaw' (SHAVIAN) */
    NULL,            /* 'tale' (TAI_LE) */
    NULL,            /* 'ugar' (UGARITIC) */
    NULL,            /* 'hrkt' (KATAKANA_OR_HIRAGANA) */
    NULL,            /* 'bugi' (BUGINESE) */
    NULL,            /* 'glag' (GLAGOLITIC) */
    NULL,            /* 'khar' (KHAROSHTHI) */
    NULL,            /* 'sylo' (SYLOTI_NAGRI) */
    NULL,            /* 'talu' (NEW_TAI_LUE) */
    NULL,            /* 'tfng' (TIFINAGH) */
    NULL,            /* 'xpeo' (OLD_PERSIAN) */
    NULL,            /* 'bali' (BALINESE) */
    NULL,            /* 'batk' (BATK) */
    NULL,            /* 'blis' (BLIS) */
    NULL,            /* 'brah' (BRAH) */
    NULL,            /* 'cham' (CHAM) */
    NULL,            /* 'cirt' (CIRT) */
    NULL,            /* 'cyrs' (CYRS) */
    NULL,            /* 'egyd' (EGYD) */
    NULL,            /* 'egyh' (EGYH) */
    NULL,            /* 'egyp' (EGYP) */
    NULL,            /* 'geok' (GEOK) */
    NULL,            /* 'hans' (HANS) */
    NULL,            /* 'hant' (HANT) */
    NULL,            /* 'hmng' (HMNG) */
    NULL,            /* 'hung' (HUNG) */
    NULL,            /* 'inds' (INDS) */
    NULL,            /* 'java' (JAVA) */
    NULL,            /* 'kali' (KALI) */
    NULL,            /* 'latf' (LATF) */
    NULL,            /* 'latg' (LATG) */
    NULL,            /* 'lepc' (LEPC) */
    NULL,            /* 'lina' (LINA) */
    NULL,            /* 'mand' (MAND) */
    NULL,            /* 'maya' (MAYA) */
    NULL,            /* 'mero' (MERO) */
    NULL,            /* 'nko ' (NKO) */
    NULL,            /* 'orkh' (ORKH) */
    NULL,            /* 'perm' (PERM) */
    NULL,            /* 'phag' (PHAGS_PA) */
    NULL,            /* 'phnx' (PHOENICIAN) */
    NULL,            /* 'plrd' (PLRD) */
    NULL,            /* 'roro' (RORO) */
    NULL,            /* 'sara' (SARA) */
    NULL,            /* 'syre' (SYRE) */
    NULL,            /* 'syrj' (SYRJ) */
    NULL,            /* 'syrn' (SYRN) */
    NULL,            /* 'teng' (TENG) */
    NULL,            /* 'vai ' (VAII) */
    NULL,            /* 'visp' (VISP) */
    NULL,            /* 'xsux' (CUNEIFORM) */
    NULL,            /* 'zxxx' (ZXXX) */
    NULL,            /* 'zzzz' (UNKNOWN) */
    NULL,            /* 'cari' (CARI) */
    NULL,            /* 'jpan' (JPAN) */
    NULL,            /* 'lana' (LANA) */
    NULL,            /* 'lyci' (LYCI) */
    NULL,            /* 'lydi' (LYDI) */
    NULL,            /* 'olck' (OLCK) */
    NULL,            /* 'rjng' (RJNG) */
    NULL,            /* 'saur' (SAUR) */
    NULL,            /* 'sgnw' (SGNW) */
    NULL,            /* 'sund' (SUND) */
    NULL,            /* 'moon' (MOON) */
    NULL,            /* 'mtei' (MTEI) */
    NULL,            /* 'armi' (ARMI) */
    NULL,            /* 'avst' (AVST) */
    NULL,            /* 'cakm' (CAKM) */
    NULL,            /* 'kore' (KORE) */
    NULL,            /* 'kthi' (KTHI) */
    NULL,            /* 'mani' (MANI) */
    NULL,            /* 'phli' (PHLI) */
    NULL,            /* 'phlp' (PHLP) */
    NULL,            /* 'phlv' (PHLV) */
    NULL,            /* 'prti' (PRTI) */
    NULL,            /* 'samr' (SAMR) */
    NULL,            /* 'tavt' (TAVT) */
    NULL,            /* 'zmth' (ZMTH) */
    NULL,            /* 'zsym' (ZSYM) */
    NULL,            /* 'bamu' (BAMUM) */
    NULL,            /* 'lisu' (LISU) */
    NULL,            /* 'nkgb' (NKGB) */
    NULL             /* 'sarb' (OLD_SOUTH_ARABIAN) */
};

IndicClassTable::CharClass IndicClassTable::getCharClass(LEUnicode ch) const
{
    if (ch == C_SIGN_ZWJ) {
        return CF_CONSONANT | CC_ZERO_WIDTH_MARK;
    }

    if (ch == C_SIGN_ZWNJ) {
        return CC_ZERO_WIDTH_MARK;
    }

    if (ch < firstChar || ch > lastChar) {
        return CC_RESERVED;
    }

    return classTable[ch - firstChar];
}

const IndicClassTable *IndicClassTable::getScriptClassTable(le_int32 scriptCode)
{
    if (scriptCode < 0 || scriptCode >= scriptCodeCount) {
        return NULL;
    }

    return indicClassTables[scriptCode];
}

le_int32 IndicReordering::getWorstCaseExpansion(le_int32 scriptCode)
{
    const IndicClassTable *classTable = IndicClassTable::getScriptClassTable(scriptCode);

    if (classTable == NULL) {
        return 1;
    }

    return classTable->getWorstCaseExpansion();
}

le_bool IndicReordering::getFilterZeroWidth(le_int32 scriptCode)
{
    const IndicClassTable *classTable = IndicClassTable::getScriptClassTable(scriptCode);

    if (classTable == NULL) {
        return TRUE;
    }

    return classTable->getFilterZeroWidth();
}

U_NAMESPACE_END
