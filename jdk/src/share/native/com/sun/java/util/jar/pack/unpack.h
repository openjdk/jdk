/*
 * Copyright (c) 2002, 2008, Oracle and/or its affiliates. All rights reserved.
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
 */



// Global Structures
struct jar;
struct gunzip;
struct band;
struct cpool;
struct entry;
struct cpindex;
struct inner_class;
struct value_stream;

struct cpindex {
  uint    len;
  entry*  base1;   // base of primary index
  entry** base2;   // base of secondary index
  byte    ixTag;   // type of entries (!= CONSTANT_None), plus 64 if sub-index
  enum { SUB_TAG = 64 };

  entry* get(uint i);

  void init(int len_, entry* base1_, int ixTag_) {
    len = len_;
    base1 = base1_;
    base2 = null;
    ixTag = ixTag_;
  }
  void init(int len_, entry** base2_, int ixTag_) {
    len = len_;
    base1 = null;
    base2 = base2_;
    ixTag = ixTag_;
  }
};

struct cpool {
  uint  nentries;
  entry* entries;
  entry* first_extra_entry;
  uint maxentries;      // total allocated size of entries

  // Position and size of each homogeneous subrange:
  int     tag_count[CONSTANT_Limit];
  int     tag_base[CONSTANT_Limit];
  cpindex tag_index[CONSTANT_Limit];
  ptrlist tag_extras[CONSTANT_Limit];

  cpindex* member_indexes;   // indexed by 2*CONSTANT_Class.inord
  cpindex* getFieldIndex(entry* classRef);
  cpindex* getMethodIndex(entry* classRef);

  inner_class** ic_index;
  inner_class** ic_child_index;
  inner_class* getIC(entry* inner);
  inner_class* getFirstChildIC(entry* outer);
  inner_class* getNextChildIC(inner_class* child);

  int outputIndexLimit;  // index limit after renumbering
  ptrlist outputEntries; // list of entry* needing output idx assigned

  entry** hashTab;
  uint    hashTabLength;
  entry*& hashTabRef(byte tag, bytes& b);
  entry*  ensureUtf8(bytes& b);
  entry*  ensureClass(bytes& b);

  // Well-known Utf8 symbols.
  enum {
    #define SNAME(n,s) s_##s,
    ALL_ATTR_DO(SNAME)
    #undef SNAME
    s_lt_init_gt,  // <init>
    s_LIMIT
  };
  entry* sym[s_LIMIT];

  // read counts from hdr, allocate main arrays
  enum { NUM_COUNTS = 12 };
  void init(unpacker* u, int counts[NUM_COUNTS]);

  // pointer to outer unpacker, for error checks etc.
  unpacker* u;

  int getCount(byte tag) {
    assert((uint)tag < CONSTANT_Limit);
    return tag_count[tag];
  }
  cpindex* getIndex(byte tag) {
    assert((uint)tag < CONSTANT_Limit);
    return &tag_index[tag];
  }
  cpindex* getKQIndex();  // uses cur_descr

  void expandSignatures();
  void initMemberIndexes();

  void computeOutputOrder();
  void computeOutputIndexes();
  void resetOutputIndexes();

  // error handling
  inline void abort(const char* msg);
  inline bool aborting();
};

/*
 * The unpacker provides the entry points to the unpack engine,
 * as well as maintains the state of the engine.
 */
struct unpacker {
  // One element of the resulting JAR.
  struct file {
    const char* name;
    julong      size;
    int         modtime;
    int         options;
    bytes       data[2];
    // Note:  If Sum(data[*].len) < size,
    // remaining bytes must be read directly from the input stream.
    bool deflate_hint() { return ((options & FO_DEFLATE_HINT) != 0); }
  };

  // back pointer to NativeUnpacker obj and Java environment
  void* jniobj;
  void* jnienv;

  // global pointer to self, if not running under JNI (not multi-thread safe)
  static unpacker* non_mt_current;

  // if running Unix-style, here are the inputs and outputs
  FILE* infileptr;  // buffered
  int   infileno;   // unbuffered
  bytes inbytes;    // direct
  gunzip* gzin;     // gunzip filter, if any
  jar*  jarout;     // output JAR file

#ifndef PRODUCT
  int   nowrite;
  int   skipfiles;
  int   verbose_bands;
#endif

  // pointer to self, for U_NEW macro
  unpacker* u;

  // private abort message string, allocated to PATH_MAX*2
  const char* abort_message;
  ptrlist mallocs;      // list of guys to free when we are all done
  ptrlist tmallocs;     // list of guys to free on next client request
  fillbytes smallbuf;   // supplies small alloc requests
  fillbytes tsmallbuf;  // supplies temporary small alloc requests

  // option management members
  int   verbose;  // verbose level, 0 means no output
  bool  strip_compile;
  bool  strip_debug;
  bool  strip_jcov;
  bool  remove_packfile;
  int   deflate_hint_or_zero;  // ==0 means not set, otherwise -1 or 1
  int   modification_time_or_zero;

  FILE*       errstrm;
  const char* errstrm_name;

  const char* log_file;

  // input stream
  fillbytes input;       // the whole block (size is predicted, has slop too)
  bool      live_input;  // is the data in this block live?
  bool      free_input;  // must the input buffer be freed?
  byte*     rp;          // read pointer (< rplimit <= input.limit())
  byte*     rplimit;     // how much of the input block has been read?
  julong    bytes_read;
  int       unsized_bytes_read;

  // callback to read at least one byte, up to available input
  typedef jlong (*read_input_fn_t)(unpacker* self, void* buf, jlong minlen, jlong maxlen);
  read_input_fn_t read_input_fn;

  // archive header fields
  int      magic, minver, majver;
  size_t   archive_size;
  int      archive_next_count, archive_options, archive_modtime;
  int      band_headers_size;
  int      file_count, attr_definition_count, ic_count, class_count;
  int      default_class_minver, default_class_majver;
  int      default_file_options, suppress_file_options;  // not header fields
  int      default_archive_modtime, default_file_modtime;  // not header fields
  int      code_count;  // not a header field
  int      files_remaining;  // not a header field

  // engine state
  band*        all_bands;   // indexed by band_number
  byte*        meta_rp;     // read-pointer into (copy of) band_headers
  cpool        cp;          // all constant pool information
  inner_class* ics;         // InnerClasses

  // output stream
  bytes    output;      // output block (either classfile head or tail)
  byte*    wp;          // write pointer (< wplimit == output.limit())
  byte*    wpbase;      // write pointer starting address (<= wp)
  byte*    wplimit;     // how much of the output block has been written?

  // output state
  file      cur_file;
  entry*    cur_class;  // CONSTANT_Class entry
  entry*    cur_super;  // CONSTANT_Class entry or null
  entry*    cur_descr;  // CONSTANT_NameandType entry
  int       cur_descr_flags;  // flags corresponding to cur_descr
  int       cur_class_minver, cur_class_majver;
  bool      cur_class_has_local_ics;
  fillbytes cur_classfile_head;
  fillbytes cur_classfile_tail;
  int       files_written;   // also tells which file we're working on
  int       classes_written; // also tells which class we're working on
  julong    bytes_written;
  intlist   bcimap;
  fillbytes class_fixup_type;
  intlist   class_fixup_offset;
  ptrlist   class_fixup_ref;
  fillbytes code_fixup_type;    // which format of branch operand?
  intlist   code_fixup_offset;  // location of operand needing fixup
  intlist   code_fixup_source;  // encoded ID of branch insn
  ptrlist   requested_ics;      // which ics need output?

  // stats pertaining to multiple segments (updated on reset)
  julong    bytes_read_before_reset;
  julong    bytes_written_before_reset;
  int       files_written_before_reset;
  int       classes_written_before_reset;
  int       segments_read_before_reset;

  // attribute state
  struct layout_definition {
    uint          idx;        // index (0..31...) which identifies this layout
    const char*   name;       // name of layout
    entry*        nameEntry;
    const char*   layout;     // string of layout (not yet parsed)
    band**        elems;      // array of top-level layout elems (or callables)

    bool hasCallables()   { return layout[0] == '['; }
    band** bands()        { assert(elems != null); return elems; }
  };
  struct attr_definitions {
    unpacker* u;  // pointer to self, for U_NEW macro
    int     xxx_flags_hi_bn;// locator for flags, count, indexes, calls bands
    int     attrc;          // ATTR_CONTEXT_CLASS, etc.
    uint    flag_limit;     // 32 or 63, depending on archive_options bit
    julong  predef;         // mask of built-in definitions
    julong  redef;          // mask of local flag definitions or redefinitions
    ptrlist layouts;        // local (compressor-defined) defs, in index order
    int     flag_count[X_ATTR_LIMIT_FLAGS_HI];
    intlist overflow_count;
    ptrlist strip_names;    // what attribute names are being stripped?
    ptrlist band_stack;     // Temp., used during layout parsing.
    ptrlist calls_to_link;  //  (ditto)
    int     bands_made;     //  (ditto)

    void free() {
      layouts.free();
      overflow_count.free();
      strip_names.free();
      band_stack.free();
      calls_to_link.free();
    }

    // Locate the five fixed bands.
    band& xxx_flags_hi();
    band& xxx_flags_lo();
    band& xxx_attr_count();
    band& xxx_attr_indexes();
    band& xxx_attr_calls();
    band& fixed_band(int e_class_xxx);

    // Register a new layout, and make bands for it.
    layout_definition* defineLayout(int idx, const char* name, const char* layout);
    layout_definition* defineLayout(int idx, entry* nameEntry, const char* layout);
    band** buildBands(layout_definition* lo);

    // Parse a layout string or part of one, recursively if necessary.
    const char* parseLayout(const char* lp,    band** &res, int curCble);
    const char* parseNumeral(const char* lp,   int    &res);
    const char* parseIntLayout(const char* lp, band*  &res, byte le_kind,
                               bool can_be_signed = false);
    band** popBody(int band_stack_base);  // pops a body off band_stack

    // Read data into the bands of the idx-th layout.
    void readBandData(int idx);  // parse layout, make bands, read data
    void readBandData(band** body, uint count);  // recursive helper

    layout_definition* getLayout(uint idx) {
      if (idx >= (uint)layouts.length())  return null;
      return (layout_definition*) layouts.get(idx);
    }

    void setHaveLongFlags(bool z) {
      assert(flag_limit == 0);  // not set up yet
      flag_limit = (z? X_ATTR_LIMIT_FLAGS_HI: X_ATTR_LIMIT_NO_FLAGS_HI);
    }
    bool haveLongFlags() {
     assert(flag_limit == X_ATTR_LIMIT_NO_FLAGS_HI ||
            flag_limit == X_ATTR_LIMIT_FLAGS_HI);
      return flag_limit == X_ATTR_LIMIT_FLAGS_HI;
    }

    // Return flag_count if idx is predef and not redef, else zero.
    int predefCount(uint idx);

    bool isRedefined(uint idx) {
      if (idx >= flag_limit) return false;
      return (bool)((redef >> idx) & 1);
    }
    bool isPredefined(uint idx) {
      if (idx >= flag_limit) return false;
      return (bool)(((predef & ~redef) >> idx) & 1);
    }
    julong flagIndexMask() {
      return (predef | redef);
    }
    bool isIndex(uint idx) {
      assert(flag_limit != 0);  // must be set up already
      if (idx < flag_limit)
        return (bool)(((predef | redef) >> idx) & 1);
      else
        return (idx - flag_limit < (uint)overflow_count.length());
    }
    int& getCount(uint idx) {
      assert(isIndex(idx));
      if (idx < flag_limit)
        return flag_count[idx];
      else
        return overflow_count.get(idx - flag_limit);
    }
    bool aborting()             { return u->aborting(); }
    void abort(const char* msg) { u->abort(msg); }
  };

  attr_definitions attr_defs[ATTR_CONTEXT_LIMIT];

  // Initialization
  void         init(read_input_fn_t input_fn = null);
  // Resets to a known sane state
  void         reset();
  // Deallocates all storage.
  void         free();
  // Deallocates temporary storage (volatile after next client call).
  void         free_temps() { tsmallbuf.init(); tmallocs.freeAll(); }

  // Option management methods
  bool         set_option(const char* option, const char* value);
  const char*  get_option(const char* option);

  void         dump_options();

  // Fetching input.
  bool   ensure_input(jlong more);
  byte*  input_scan()               { return rp; }
  size_t input_remaining()          { return rplimit - rp; }
  size_t input_consumed()           { return rp - input.base(); }

  // Entry points to the unpack engine
  static int   run(int argc, char **argv);   // Unix-style entry point.
  void         check_options();
  void         start(void* packptr = null, size_t len = 0);
  void         redirect_stdio();
  void         write_file_to_jar(file* f);
  void         finish();

  // Public post unpack methods
  int          get_files_remaining()    { return files_remaining; }
  int          get_segments_remaining() { return archive_next_count; }
  file*        get_next_file();  // returns null on last file

  // General purpose methods
  void*        alloc(size_t size) { return alloc_heap(size, true); }
  void*        temp_alloc(size_t size) { return alloc_heap(size, true, true); }
  void*        alloc_heap(size_t size, bool smallOK = false, bool temp = false);
  void         saveTo(bytes& b, const char* str) { saveTo(b, (byte*)str, strlen(str)); }
  void         saveTo(bytes& b, bytes& data) { saveTo(b, data.ptr, data.len); }
  void         saveTo(bytes& b, byte* ptr, size_t len); //{ b.ptr = U_NEW...}
  const char*  saveStr(const char* str) { bytes buf; saveTo(buf, str); return buf.strval(); }
  const char*  saveIntStr(int num) { char buf[30]; sprintf(buf, "%d", num); return saveStr(buf); }
#ifndef PRODUCT
  int printcr_if_verbose(int level, const char* fmt,...);
#endif
  const char*  get_abort_message();
  void         abort(const char* s = null);
  bool         aborting() { return abort_message != null; }
  static unpacker* current();  // find current instance

  // Output management
  void set_output(fillbytes* which) {
    assert(wp == null);
    which->ensureSize(1 << 12);  // covers the average classfile
    wpbase  = which->base();
    wp      = which->limit();
    wplimit = which->end();
  }
  fillbytes* close_output(fillbytes* which = null);  // inverse of set_output

  // These take an implicit parameter of wp/wplimit, and resize as necessary:
  byte*  put_space(size_t len);  // allocates space at wp, returns pointer
  size_t put_empty(size_t s)    { byte* p = put_space(s); return p - wpbase; }
  void   ensure_put_space(size_t len);
  void   put_bytes(bytes& b)    { b.writeTo(put_space(b.len)); }
  void   putu1(int n)           { putu1_at(put_space(1), n); }
  void   putu1_fast(int n)      { putu1_at(wp++,         n); }
  void   putu2(int n);       // { putu2_at(put_space(2), n); }
  void   putu4(int n);       // { putu4_at(put_space(4), n); }
  void   putu8(jlong n);     // { putu8_at(put_space(8), n); }
  void   putref(entry* e);   // { putu2_at(put_space(2), putref_index(e, 2)); }
  void   putu1ref(entry* e); // { putu1_at(put_space(1), putref_index(e, 1)); }
  int    putref_index(entry* e, int size);  // size in [1..2]
  void   put_label(int curIP, int size);    // size in {2,4}
  void   putlayout(band** body);
  void   put_stackmap_type();

  size_t wpoffset() { return (size_t)(wp - wpbase); }  // (unvariant across overflow)
  byte*  wp_at(size_t offset) { return wpbase + offset; }
  uint to_bci(uint bii);
  void get_code_header(int& max_stack,
                       int& max_na_locals,
                       int& handler_count,
                       int& cflags);
  band* ref_band_for_self_op(int bc, bool& isAloadVar, int& origBCVar);
  band* ref_band_for_op(int bc);

  // Definitions of standard classfile int formats:
  static void putu1_at(byte* wp, int n) { assert(n == (n & 0xFF)); wp[0] = n; }
  static void putu2_at(byte* wp, int n);
  static void putu4_at(byte* wp, int n);
  static void putu8_at(byte* wp, jlong n);

  // Private stuff
  void reset_cur_classfile();
  void write_classfile_tail();
  void write_classfile_head();
  void write_code();
  void write_bc_ops();
  void write_members(int num, int attrc);  // attrc=ATTR_CONTEXT_FIELD/METHOD
  int  write_attrs(int attrc, julong indexBits);

  // The readers
  void read_bands();
  void read_file_header();
  void read_cp();
  void read_cp_counts(value_stream& hdr);
  void read_attr_defs();
  void read_ics();
  void read_attrs(int attrc, int obj_count);
  void read_classes();
  void read_code_headers();
  void read_bcs();
  void read_bc_ops();
  void read_files();
  void read_Utf8_values(entry* cpMap, int len);
  void read_single_words(band& cp_band, entry* cpMap, int len);
  void read_double_words(band& cp_bands, entry* cpMap, int len);
  void read_single_refs(band& cp_band, byte refTag, entry* cpMap, int len);
  void read_double_refs(band& cp_band, byte ref1Tag, byte ref2Tag, entry* cpMap, int len);
  void read_signature_values(entry* cpMap, int len);
};

inline void cpool::abort(const char* msg) { u->abort(msg); }
inline bool cpool::aborting()             { return u->aborting(); }
