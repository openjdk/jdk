/*
 * Copyright (C) 2012 Grigori Goronzy <greg@kinoho.net>
 *
 * Permission to use, copy, modify, and/or distribute this software for any
 * purpose with or without fee is hereby granted, provided that the above
 * copyright notice and this permission notice appear in all copies.
 *
 * THE SOFTWARE IS PROVIDED "AS IS" AND THE AUTHOR DISCLAIMS ALL WARRANTIES
 * WITH REGARD TO THIS SOFTWARE INCLUDING ALL IMPLIED WARRANTIES OF
 * MERCHANTABILITY AND FITNESS. IN NO EVENT SHALL THE AUTHOR BE LIABLE FOR
 * ANY SPECIAL, DIRECT, INDIRECT, OR CONSEQUENTIAL DAMAGES OR ANY DAMAGES
 * WHATSOEVER RESULTING FROM LOSS OF USE, DATA OR PROFITS, WHETHER IN AN
 * ACTION OF CONTRACT, NEGLIGENCE OR OTHER TORTIOUS ACTION, ARISING OUT OF
 * OR IN CONNECTION WITH THE USE OR PERFORMANCE OF THIS SOFTWARE.
 */

#include <stdio.h>
#include <stdlib.h>
#include "ucdn.h"

typedef struct {
    unsigned char category;
    unsigned char combining;
    unsigned char bidi_class;
    unsigned char mirrored;
    unsigned char east_asian_width;
    unsigned char normalization_check;
    unsigned char script;
} UCDRecord;

typedef struct {
    unsigned short from, to;
} MirrorPair;

typedef struct {
    unsigned int start;
    short count, index;
} Reindex;

#include "unicodedata_db.h"

/* constants required for Hangul (de)composition */
#define SBASE 0xAC00
#define LBASE 0x1100
#define VBASE 0x1161
#define TBASE 0x11A7
#define SCOUNT 11172
#define LCOUNT 19
#define VCOUNT 21
#define TCOUNT 28
#define NCOUNT (VCOUNT * TCOUNT)

static const UCDRecord *get_ucd_record(uint32_t code)
{
    int index, offset;

    if (code >= 0x110000)
        index = 0;
    else {
        index  = index0[code >> (SHIFT1+SHIFT2)] << SHIFT1;
        offset = (code >> SHIFT2) & ((1<<SHIFT1) - 1);
        index  = index1[index + offset] << SHIFT2;
        offset = code & ((1<<SHIFT2) - 1);
        index  = index2[index + offset];
    }

    return &ucd_records[index];
}

static const unsigned short *get_decomp_record(uint32_t code)
{
    int index, offset;

    if (code >= 0x110000)
        index = 0;
    else {
        index  = decomp_index0[code >> (DECOMP_SHIFT1+DECOMP_SHIFT2)]
            << DECOMP_SHIFT1;
        offset = (code >> DECOMP_SHIFT2) & ((1<<DECOMP_SHIFT1) - 1);
        index  = decomp_index1[index + offset] << DECOMP_SHIFT2;
        offset = code & ((1<<DECOMP_SHIFT2) - 1);
        index  = decomp_index2[index + offset];
    }

    return &decomp_data[index];
}

static int get_comp_index(uint32_t code, const Reindex *idx)
{
    int i;

    for (i = 0; idx[i].start; i++) {
        const Reindex *cur = &idx[i];
        if (code < cur->start)
            return -1;
        if (code <= cur->start + cur->count) {
            return cur->index + (code - cur->start);
        }
    }

    return -1;
}

static int compare_mp(const void *a, const void *b)
{
    MirrorPair *mpa = (MirrorPair *)a;
    MirrorPair *mpb = (MirrorPair *)b;
    return mpa->from - mpb->from;
}

static int hangul_pair_decompose(uint32_t code, uint32_t *a, uint32_t *b)
{
    int si = code - SBASE;

    if (si < 0 || si >= SCOUNT)
        return 0;

    if (si % TCOUNT) {
        /* LV,T */
        *a = SBASE + (si / TCOUNT) * TCOUNT;
        *b = TBASE + (si % TCOUNT);
        return 3;
    } else {
        /* L,V */
        *a = LBASE + (si / NCOUNT);
        *b = VBASE + (si % NCOUNT) / TCOUNT;
        return 2;
    }
}

static int hangul_pair_compose(uint32_t *code, uint32_t a, uint32_t b)
{
    if (b < VBASE || b >= (TBASE + TCOUNT))
        return 0;

    if ((a < LBASE || a >= (LBASE + LCOUNT))
            && (a < SBASE || a >= (SBASE + SCOUNT)))
        return 0;

    if (a >= SBASE) {
        /* LV,T */
        *code = a + (b - TBASE);
        return 3;
    } else {
        /* L,V */
        int li = a - LBASE;
        int vi = b - VBASE;
        *code = SBASE + li * NCOUNT + vi * TCOUNT;
        return 2;
    }
}

static uint32_t decode_utf16(const unsigned short **code_ptr)
{
    const unsigned short *code = *code_ptr;

    if ((code[0] & 0xd800) != 0xd800) {
        *code_ptr += 1;
        return (uint32_t)code[0];
    } else {
        *code_ptr += 2;
        return 0x10000 + ((uint32_t)code[1] - 0xdc00) +
            (((uint32_t)code[0] - 0xd800) << 10);
    }
}

const char *ucdn_get_unicode_version(void)
{
    return UNIDATA_VERSION;
}

int ucdn_get_combining_class(uint32_t code)
{
    return get_ucd_record(code)->combining;
}

int ucdn_get_east_asian_width(uint32_t code)
{
    return get_ucd_record(code)->east_asian_width;
}

int ucdn_get_general_category(uint32_t code)
{
    return get_ucd_record(code)->category;
}

int ucdn_get_bidi_class(uint32_t code)
{
    return get_ucd_record(code)->bidi_class;
}

int ucdn_get_mirrored(uint32_t code)
{
    return get_ucd_record(code)->mirrored;
}

int ucdn_get_script(uint32_t code)
{
    return get_ucd_record(code)->script;
}

uint32_t ucdn_mirror(uint32_t code)
{
    MirrorPair mp = {0};
    MirrorPair *res;

    if (get_ucd_record(code)->mirrored == 0)
        return code;

    mp.from = code;
    res = bsearch(&mp, mirror_pairs, BIDI_MIRROR_LEN, sizeof(MirrorPair),
            compare_mp);

    if (res == NULL)
        return code;
    else
        return res->to;
}

int ucdn_decompose(uint32_t code, uint32_t *a, uint32_t *b)
{
    const unsigned short *rec;
    int len;

    if (hangul_pair_decompose(code, a, b))
        return 1;

    rec = get_decomp_record(code);
    len = rec[0] >> 8;

    if ((rec[0] & 0xff) != 0 || len == 0)
        return 0;

    rec++;
    *a = decode_utf16(&rec);
    if (len > 1)
        *b = decode_utf16(&rec);
    else
        *b = 0;

    return 1;
}

int ucdn_compose(uint32_t *code, uint32_t a, uint32_t b)
{
    int l, r, index, indexi, offset;

    if (hangul_pair_compose(code, a, b))
        return 1;

    l = get_comp_index(a, nfc_first);
    r = get_comp_index(b, nfc_last);

    if (l < 0 || r < 0)
        return 0;

    indexi = l * TOTAL_LAST + r;
    index  = comp_index0[indexi >> (COMP_SHIFT1+COMP_SHIFT2)] << COMP_SHIFT1;
    offset = (indexi >> COMP_SHIFT2) & ((1<<COMP_SHIFT1) - 1);
    index  = comp_index1[index + offset] << COMP_SHIFT2;
    offset = indexi & ((1<<COMP_SHIFT2) - 1);
    *code  = comp_data[index + offset];

    return *code != 0;
}

int ucdn_compat_decompose(uint32_t code, uint32_t *decomposed)
{
    int i, len;
    const unsigned short *rec = get_decomp_record(code);
    len = rec[0] >> 8;

    if (len == 0)
        return 0;

    rec++;
    for (i = 0; i < len; i++)
        decomposed[i] = decode_utf16(&rec);

    return len;
}
