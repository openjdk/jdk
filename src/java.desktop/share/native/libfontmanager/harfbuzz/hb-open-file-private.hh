/*
 * Copyright © 2007,2008,2009  Red Hat, Inc.
 * Copyright © 2012  Google, Inc.
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
 * Red Hat Author(s): Behdad Esfahbod
 * Google Author(s): Behdad Esfahbod
 */

#ifndef HB_OPEN_FILE_PRIVATE_HH
#define HB_OPEN_FILE_PRIVATE_HH

#include "hb-open-type-private.hh"
#include "hb-ot-head-table.hh"


namespace OT {


/*
 *
 * The OpenType Font File
 *
 */


/*
 * Organization of an OpenType Font
 */

struct OpenTypeFontFile;
struct OffsetTable;
struct TTCHeader;


typedef struct TableRecord
{
  int cmp (Tag t) const
  { return -t.cmp (tag); }

  static int cmp (const void *pa, const void *pb)
  {
    const TableRecord *a = (const TableRecord *) pa;
    const TableRecord *b = (const TableRecord *) pb;
    return b->cmp (a->tag);
  }

  inline bool sanitize (hb_sanitize_context_t *c) const
  {
    TRACE_SANITIZE (this);
    return_trace (c->check_struct (this));
  }

  Tag           tag;            /* 4-byte identifier. */
  CheckSum      checkSum;       /* CheckSum for this table. */
  Offset32      offset;         /* Offset from beginning of TrueType font
                                 * file. */
  HBUINT32      length;         /* Length of this table. */
  public:
  DEFINE_SIZE_STATIC (16);
} OpenTypeTable;

typedef struct OffsetTable
{
  friend struct OpenTypeFontFile;

  inline unsigned int get_table_count (void) const
  { return tables.len; }
  inline const TableRecord& get_table (unsigned int i) const
  {
    return tables[i];
  }
  inline unsigned int get_table_tags (unsigned int  start_offset,
                                      unsigned int *table_count, /* IN/OUT */
                                      hb_tag_t     *table_tags /* OUT */) const
  {
    if (table_count)
    {
      if (start_offset >= tables.len)
        *table_count = 0;
      else
        *table_count = MIN<unsigned int> (*table_count, tables.len - start_offset);

      const TableRecord *sub_tables = tables.array + start_offset;
      unsigned int count = *table_count;
      for (unsigned int i = 0; i < count; i++)
        table_tags[i] = sub_tables[i].tag;
    }
    return tables.len;
  }
  inline bool find_table_index (hb_tag_t tag, unsigned int *table_index) const
  {
    Tag t;
    t.set (tag);
    /* Linear-search for small tables to work around fonts with unsorted
     * table list. */
    int i = tables.len < 64 ? tables.lsearch (t) : tables.bsearch (t);
    if (table_index)
      *table_index = i == -1 ? Index::NOT_FOUND_INDEX : (unsigned int) i;
    return i != -1;
  }
  inline const TableRecord& get_table_by_tag (hb_tag_t tag) const
  {
    unsigned int table_index;
    find_table_index (tag, &table_index);
    return get_table (table_index);
  }

  public:

  inline bool serialize (hb_serialize_context_t *c,
                         hb_tag_t sfnt_tag,
                         Supplier<hb_tag_t> &tags,
                         Supplier<hb_blob_t *> &blobs,
                         unsigned int table_count)
  {
    TRACE_SERIALIZE (this);
    /* Alloc 12 for the OTHeader. */
    if (unlikely (!c->extend_min (*this))) return_trace (false);
    /* Write sfntVersion (bytes 0..3). */
    sfnt_version.set (sfnt_tag);
    /* Take space for numTables, searchRange, entrySelector, RangeShift
     * and the TableRecords themselves.  */
    if (unlikely (!tables.serialize (c, table_count))) return_trace (false);

    const char *dir_end = (const char *) c->head;
    HBUINT32 *checksum_adjustment = nullptr;

    /* Write OffsetTables, alloc for and write actual table blobs. */
    for (unsigned int i = 0; i < table_count; i++)
    {
      TableRecord &rec = tables.array[i];
      hb_blob_t *blob = blobs[i];
      rec.tag.set (tags[i]);
      rec.length.set (hb_blob_get_length (blob));
      rec.offset.serialize (c, this);

      /* Allocate room for the table and copy it. */
      char *start = (char *) c->allocate_size<void> (rec.length);
      if (unlikely (!start)) {return false;}

      memcpy (start, hb_blob_get_data (blob, nullptr), rec.length);

      /* 4-byte allignment. */
      if (rec.length % 4)
        c->allocate_size<void> (4 - rec.length % 4);
      const char *end = (const char *) c->head;

      if (tags[i] == HB_OT_TAG_head && end - start >= head::static_size)
      {
        head *h = (head *) start;
        checksum_adjustment = &h->checkSumAdjustment;
        checksum_adjustment->set (0);
      }

      rec.checkSum.set_for_data (start, end - start);
    }
    tags += table_count;
    blobs += table_count;

    tables.qsort ();

    if (checksum_adjustment)
    {
      CheckSum checksum;

      /* The following line is a slower version of the following block. */
      //checksum.set_for_data (this, (const char *) c->head - (const char *) this);
      checksum.set_for_data (this, dir_end - (const char *) this);
      for (unsigned int i = 0; i < table_count; i++)
      {
        TableRecord &rec = tables.array[i];
        checksum.set (checksum + rec.checkSum);
      }

      checksum_adjustment->set (0xB1B0AFBAu - checksum);
    }

    return_trace (true);
  }

  inline bool sanitize (hb_sanitize_context_t *c) const
  {
    TRACE_SANITIZE (this);
    return_trace (c->check_struct (this) && tables.sanitize (c));
  }

  protected:
  Tag           sfnt_version;   /* '\0\001\0\00' if TrueType / 'OTTO' if CFF */
  BinSearchArrayOf<TableRecord>
                tables;
  public:
  DEFINE_SIZE_ARRAY (12, tables);
} OpenTypeFontFace;


/*
 * TrueType Collections
 */

struct TTCHeaderVersion1
{
  friend struct TTCHeader;

  inline unsigned int get_face_count (void) const { return table.len; }
  inline const OpenTypeFontFace& get_face (unsigned int i) const { return this+table[i]; }

  inline bool sanitize (hb_sanitize_context_t *c) const
  {
    TRACE_SANITIZE (this);
    return_trace (table.sanitize (c, this));
  }

  protected:
  Tag           ttcTag;         /* TrueType Collection ID string: 'ttcf' */
  FixedVersion<>version;        /* Version of the TTC Header (1.0),
                                 * 0x00010000u */
  ArrayOf<LOffsetTo<OffsetTable>, HBUINT32>
                table;          /* Array of offsets to the OffsetTable for each font
                                 * from the beginning of the file */
  public:
  DEFINE_SIZE_ARRAY (12, table);
};

struct TTCHeader
{
  friend struct OpenTypeFontFile;

  private:

  inline unsigned int get_face_count (void) const
  {
    switch (u.header.version.major) {
    case 2: /* version 2 is compatible with version 1 */
    case 1: return u.version1.get_face_count ();
    default:return 0;
    }
  }
  inline const OpenTypeFontFace& get_face (unsigned int i) const
  {
    switch (u.header.version.major) {
    case 2: /* version 2 is compatible with version 1 */
    case 1: return u.version1.get_face (i);
    default:return Null(OpenTypeFontFace);
    }
  }

  inline bool sanitize (hb_sanitize_context_t *c) const
  {
    TRACE_SANITIZE (this);
    if (unlikely (!u.header.version.sanitize (c))) return_trace (false);
    switch (u.header.version.major) {
    case 2: /* version 2 is compatible with version 1 */
    case 1: return_trace (u.version1.sanitize (c));
    default:return_trace (true);
    }
  }

  protected:
  union {
  struct {
  Tag           ttcTag;         /* TrueType Collection ID string: 'ttcf' */
  FixedVersion<>version;        /* Version of the TTC Header (1.0 or 2.0),
                                 * 0x00010000u or 0x00020000u */
  }                     header;
  TTCHeaderVersion1     version1;
  } u;
};


/*
 * OpenType Font File
 */

struct OpenTypeFontFile
{
  static const hb_tag_t tableTag        = HB_TAG ('_','_','_','_'); /* Sanitizer needs this. */

  static const hb_tag_t CFFTag          = HB_TAG ('O','T','T','O'); /* OpenType with Postscript outlines */
  static const hb_tag_t TrueTypeTag     = HB_TAG ( 0 , 1 , 0 , 0 ); /* OpenType with TrueType outlines */
  static const hb_tag_t TTCTag          = HB_TAG ('t','t','c','f'); /* TrueType Collection */
  static const hb_tag_t TrueTag         = HB_TAG ('t','r','u','e'); /* Obsolete Apple TrueType */
  static const hb_tag_t Typ1Tag         = HB_TAG ('t','y','p','1'); /* Obsolete Apple Type1 font in SFNT container */

  inline hb_tag_t get_tag (void) const { return u.tag; }

  inline unsigned int get_face_count (void) const
  {
    switch (u.tag) {
    case CFFTag:        /* All the non-collection tags */
    case TrueTag:
    case Typ1Tag:
    case TrueTypeTag:   return 1;
    case TTCTag:        return u.ttcHeader.get_face_count ();
    default:            return 0;
    }
  }
  inline const OpenTypeFontFace& get_face (unsigned int i) const
  {
    switch (u.tag) {
    /* Note: for non-collection SFNT data we ignore index.  This is because
     * Apple dfont container is a container of SFNT's.  So each SFNT is a
     * non-TTC, but the index is more than zero. */
    case CFFTag:        /* All the non-collection tags */
    case TrueTag:
    case Typ1Tag:
    case TrueTypeTag:   return u.fontFace;
    case TTCTag:        return u.ttcHeader.get_face (i);
    default:            return Null(OpenTypeFontFace);
    }
  }

  inline bool serialize_single (hb_serialize_context_t *c,
                                hb_tag_t sfnt_tag,
                                Supplier<hb_tag_t> &tags,
                                Supplier<hb_blob_t *> &blobs,
                                unsigned int table_count)
  {
    TRACE_SERIALIZE (this);
    assert (sfnt_tag != TTCTag);
    if (unlikely (!c->extend_min (*this))) return_trace (false);
    return_trace (u.fontFace.serialize (c, sfnt_tag, tags, blobs, table_count));
  }

  inline bool sanitize (hb_sanitize_context_t *c) const
  {
    TRACE_SANITIZE (this);
    if (unlikely (!u.tag.sanitize (c))) return_trace (false);
    switch (u.tag) {
    case CFFTag:        /* All the non-collection tags */
    case TrueTag:
    case Typ1Tag:
    case TrueTypeTag:   return_trace (u.fontFace.sanitize (c));
    case TTCTag:        return_trace (u.ttcHeader.sanitize (c));
    default:            return_trace (true);
    }
  }

  protected:
  union {
  Tag                   tag;            /* 4-byte identifier. */
  OpenTypeFontFace      fontFace;
  TTCHeader             ttcHeader;
  } u;
  public:
  DEFINE_SIZE_UNION (4, tag);
};


} /* namespace OT */


#endif /* HB_OPEN_FILE_PRIVATE_HH */
