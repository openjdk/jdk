/*
 * Copyright © 2007,2008,2009,2010  Red Hat, Inc.
 * Copyright © 2012,2018  Google, Inc.
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

#ifndef HB_MACHINERY_HH
#define HB_MACHINERY_HH

#include "hb.hh"
#include "hb-blob.hh"

#include "hb-array.hh"
#include "hb-vector.hh"


/*
 * Casts
 */

/* Cast to struct T, reference to reference */
template<typename Type, typename TObject>
static inline const Type& CastR(const TObject &X)
{ return reinterpret_cast<const Type&> (X); }
template<typename Type, typename TObject>
static inline Type& CastR(TObject &X)
{ return reinterpret_cast<Type&> (X); }

/* Cast to struct T, pointer to pointer */
template<typename Type, typename TObject>
static inline const Type* CastP(const TObject *X)
{ return reinterpret_cast<const Type*> (X); }
template<typename Type, typename TObject>
static inline Type* CastP(TObject *X)
{ return reinterpret_cast<Type*> (X); }

/* StructAtOffset<T>(P,Ofs) returns the struct T& that is placed at memory
 * location pointed to by P plus Ofs bytes. */
template<typename Type>
static inline const Type& StructAtOffset(const void *P, unsigned int offset)
{ return * reinterpret_cast<const Type*> ((const char *) P + offset); }
template<typename Type>
static inline Type& StructAtOffset(void *P, unsigned int offset)
{ return * reinterpret_cast<Type*> ((char *) P + offset); }
template<typename Type>
static inline const Type& StructAtOffsetUnaligned(const void *P, unsigned int offset)
{
#pragma GCC diagnostic push
#pragma GCC diagnostic ignored "-Wcast-align"
  return * reinterpret_cast<Type*> ((char *) P + offset);
#pragma GCC diagnostic pop
}
template<typename Type>
static inline Type& StructAtOffsetUnaligned(void *P, unsigned int offset)
{
#pragma GCC diagnostic push
#pragma GCC diagnostic ignored "-Wcast-align"
  return * reinterpret_cast<Type*> ((char *) P + offset);
#pragma GCC diagnostic pop
}

/* StructAfter<T>(X) returns the struct T& that is placed after X.
 * Works with X of variable size also.  X must implement get_size() */
template<typename Type, typename TObject>
static inline const Type& StructAfter(const TObject &X)
{ return StructAtOffset<Type>(&X, X.get_size()); }
template<typename Type, typename TObject>
static inline Type& StructAfter(TObject &X)
{ return StructAtOffset<Type>(&X, X.get_size()); }


/*
 * Size checking
 */

/* Check _assertion in a method environment */
#define _DEFINE_INSTANCE_ASSERTION1(_line, _assertion) \
  void _instance_assertion_on_line_##_line () const \
  { static_assert ((_assertion), ""); }
# define _DEFINE_INSTANCE_ASSERTION0(_line, _assertion) _DEFINE_INSTANCE_ASSERTION1 (_line, _assertion)
# define DEFINE_INSTANCE_ASSERTION(_assertion) _DEFINE_INSTANCE_ASSERTION0 (__LINE__, _assertion)

/* Check that _code compiles in a method environment */
#define _DEFINE_COMPILES_ASSERTION1(_line, _code) \
  void _compiles_assertion_on_line_##_line () const \
  { _code; }
# define _DEFINE_COMPILES_ASSERTION0(_line, _code) _DEFINE_COMPILES_ASSERTION1 (_line, _code)
# define DEFINE_COMPILES_ASSERTION(_code) _DEFINE_COMPILES_ASSERTION0 (__LINE__, _code)


#define DEFINE_SIZE_STATIC(size) \
  DEFINE_INSTANCE_ASSERTION (sizeof (*this) == (size)) \
  unsigned int get_size () const { return (size); } \
  static constexpr unsigned null_size = (size); \
  static constexpr unsigned min_size = (size); \
  static constexpr unsigned static_size = (size)

#define DEFINE_SIZE_UNION(size, _member) \
  DEFINE_COMPILES_ASSERTION ((void) this->u._member.static_size) \
  DEFINE_INSTANCE_ASSERTION (sizeof(this->u._member) == (size)) \
  static constexpr unsigned null_size = (size); \
  static constexpr unsigned min_size = (size)

#define DEFINE_SIZE_MIN(size) \
  DEFINE_INSTANCE_ASSERTION (sizeof (*this) >= (size)) \
  static constexpr unsigned null_size = (size); \
  static constexpr unsigned min_size = (size)

#define DEFINE_SIZE_UNBOUNDED(size) \
  DEFINE_INSTANCE_ASSERTION (sizeof (*this) >= (size)) \
  static constexpr unsigned min_size = (size)

#define DEFINE_SIZE_ARRAY(size, array) \
  DEFINE_COMPILES_ASSERTION ((void) (array)[0].static_size) \
  DEFINE_INSTANCE_ASSERTION (sizeof (*this) == (size) + VAR * sizeof ((array)[0])) \
  static constexpr unsigned null_size = (size); \
  static constexpr unsigned min_size = (size)

#define DEFINE_SIZE_ARRAY_SIZED(size, array) \
  unsigned int get_size () const { return (size - (array).min_size + (array).get_size ()); } \
  DEFINE_SIZE_ARRAY(size, array)


/*
 * Dispatch
 */

template <typename Context, typename Return, unsigned int MaxDebugDepth>
struct hb_dispatch_context_t
{
  static constexpr unsigned max_debug_depth = MaxDebugDepth;
  typedef Return return_t;
  template <typename T, typename F>
  bool may_dispatch (const T *obj HB_UNUSED, const F *format HB_UNUSED) { return true; }
  static return_t no_dispatch_return_value () { return Context::default_return_value (); }
  static bool stop_sublookup_iteration (const return_t r HB_UNUSED) { return false; }
};


/*
 * Sanitize
 *
 *
 * === Introduction ===
 *
 * The sanitize machinery is at the core of our zero-cost font loading.  We
 * mmap() font file into memory and create a blob out of it.  Font subtables
 * are returned as a readonly sub-blob of the main font blob.  These table
 * blobs are then sanitized before use, to ensure invalid memory access does
 * not happen.  The toplevel sanitize API use is like, eg. to load the 'head'
 * table:
 *
 *   hb_blob_t *head_blob = hb_sanitize_context_t ().reference_table<OT::head> (face);
 *
 * The blob then can be converted to a head table struct with:
 *
 *   const head *head_table = head_blob->as<head> ();
 *
 * What the reference_table does is, to call hb_face_reference_table() to load
 * the table blob, sanitize it and return either the sanitized blob, or empty
 * blob if sanitization failed.  The blob->as() function returns the null
 * object of its template type argument if the blob is empty.  Otherwise, it
 * just casts the blob contents to the desired type.
 *
 * Sanitizing a blob of data with a type T works as follows (with minor
 * simplification):
 *
 *   - Cast blob content to T*, call sanitize() method of it,
 *   - If sanitize succeeded, return blob.
 *   - Otherwise, if blob is not writable, try making it writable,
 *     or copy if cannot be made writable in-place,
 *   - Call sanitize() again.  Return blob if sanitize succeeded.
 *   - Return empty blob otherwise.
 *
 *
 * === The sanitize() contract ===
 *
 * The sanitize() method of each object type shall return true if it's safe to
 * call other methods of the object, and false otherwise.
 *
 * Note that what sanitize() checks for might align with what the specification
 * describes as valid table data, but does not have to be.  In particular, we
 * do NOT want to be pedantic and concern ourselves with validity checks that
 * are irrelevant to our use of the table.  On the contrary, we want to be
 * lenient with error handling and accept invalid data to the extent that it
 * does not impose extra burden on us.
 *
 * Based on the sanitize contract, one can see that what we check for depends
 * on how we use the data in other table methods.  Ie. if other table methods
 * assume that offsets do NOT point out of the table data block, then that's
 * something sanitize() must check for (GSUB/GPOS/GDEF/etc work this way).  On
 * the other hand, if other methods do such checks themselves, then sanitize()
 * does not have to bother with them (glyf/local work this way).  The choice
 * depends on the table structure and sanitize() performance.  For example, to
 * check glyf/loca offsets in sanitize() would cost O(num-glyphs).  We try hard
 * to avoid such costs during font loading.  By postponing such checks to the
 * actual glyph loading, we reduce the sanitize cost to O(1) and total runtime
 * cost to O(used-glyphs).  As such, this is preferred.
 *
 * The same argument can be made re GSUB/GPOS/GDEF, but there, the table
 * structure is so complicated that by checking all offsets at sanitize() time,
 * we make the code much simpler in other methods, as offsets and referenced
 * objects do not need to be validated at each use site.
 */

/* This limits sanitizing time on really broken fonts. */
#ifndef HB_SANITIZE_MAX_EDITS
#define HB_SANITIZE_MAX_EDITS 32
#endif
#ifndef HB_SANITIZE_MAX_OPS_FACTOR
#define HB_SANITIZE_MAX_OPS_FACTOR 8
#endif
#ifndef HB_SANITIZE_MAX_OPS_MIN
#define HB_SANITIZE_MAX_OPS_MIN 16384
#endif
#ifndef HB_SANITIZE_MAX_OPS_MAX
#define HB_SANITIZE_MAX_OPS_MAX 0x3FFFFFFF
#endif

struct hb_sanitize_context_t :
       hb_dispatch_context_t<hb_sanitize_context_t, bool, HB_DEBUG_SANITIZE>
{
  hb_sanitize_context_t () :
        debug_depth (0),
        start (nullptr), end (nullptr),
        max_ops (0),
        writable (false), edit_count (0),
        blob (nullptr),
        num_glyphs (65536),
        num_glyphs_set (false) {}

  const char *get_name () { return "SANITIZE"; }
  template <typename T, typename F>
  bool may_dispatch (const T *obj HB_UNUSED, const F *format)
  { return format->sanitize (this); }
  template <typename T>
  return_t dispatch (const T &obj) { return obj.sanitize (this); }
  static return_t default_return_value () { return true; }
  static return_t no_dispatch_return_value () { return false; }
  bool stop_sublookup_iteration (const return_t r) const { return !r; }

  void init (hb_blob_t *b)
  {
    this->blob = hb_blob_reference (b);
    this->writable = false;
  }

  void set_num_glyphs (unsigned int num_glyphs_)
  {
    num_glyphs = num_glyphs_;
    num_glyphs_set = true;
  }
  unsigned int get_num_glyphs () { return num_glyphs; }

  void set_max_ops (int max_ops_) { max_ops = max_ops_; }

  template <typename T>
  void set_object (const T *obj)
  {
    reset_object ();

    if (!obj) return;

    const char *obj_start = (const char *) obj;
    if (unlikely (obj_start < this->start || this->end <= obj_start))
      this->start = this->end = nullptr;
    else
    {
      this->start = obj_start;
      this->end   = obj_start + MIN<uintptr_t> (this->end - obj_start, obj->get_size ());
    }
  }

  void reset_object ()
  {
    this->start = this->blob->data;
    this->end = this->start + this->blob->length;
    assert (this->start <= this->end); /* Must not overflow. */
  }

  void start_processing ()
  {
    reset_object ();
    this->max_ops = MAX ((unsigned int) (this->end - this->start) * HB_SANITIZE_MAX_OPS_FACTOR,
                         (unsigned) HB_SANITIZE_MAX_OPS_MIN);
    this->edit_count = 0;
    this->debug_depth = 0;

    DEBUG_MSG_LEVEL (SANITIZE, start, 0, +1,
                     "start [%p..%p] (%lu bytes)",
                     this->start, this->end,
                     (unsigned long) (this->end - this->start));
  }

  void end_processing ()
  {
    DEBUG_MSG_LEVEL (SANITIZE, this->start, 0, -1,
                     "end [%p..%p] %u edit requests",
                     this->start, this->end, this->edit_count);

    hb_blob_destroy (this->blob);
    this->blob = nullptr;
    this->start = this->end = nullptr;
  }

  bool check_range (const void *base,
                           unsigned int len) const
  {
    const char *p = (const char *) base;
    bool ok = this->start <= p &&
              p <= this->end &&
              (unsigned int) (this->end - p) >= len &&
              this->max_ops-- > 0;

    DEBUG_MSG_LEVEL (SANITIZE, p, this->debug_depth+1, 0,
       "check_range [%p..%p] (%d bytes) in [%p..%p] -> %s",
       p, p + len, len,
       this->start, this->end,
       ok ? "OK" : "OUT-OF-RANGE");

    return likely (ok);
  }

  template <typename T>
  bool check_range (const T *base,
                           unsigned int a,
                           unsigned int b) const
  {
    return !hb_unsigned_mul_overflows (a, b) &&
           this->check_range (base, a * b);
  }

  template <typename T>
  bool check_range (const T *base,
                           unsigned int a,
                           unsigned int b,
                           unsigned int c) const
  {
    return !hb_unsigned_mul_overflows (a, b) &&
           this->check_range (base, a * b, c);
  }

  template <typename T>
  bool check_array (const T *base, unsigned int len) const
  {
    return this->check_range (base, len, hb_static_size (T));
  }

  template <typename T>
  bool check_array (const T *base,
                    unsigned int a,
                    unsigned int b) const
  {
    return this->check_range (base, a, b, hb_static_size (T));
  }

  template <typename Type>
  bool check_struct (const Type *obj) const
  { return likely (this->check_range (obj, obj->min_size)); }

  bool may_edit (const void *base, unsigned int len)
  {
    if (this->edit_count >= HB_SANITIZE_MAX_EDITS)
      return false;

    const char *p = (const char *) base;
    this->edit_count++;

    DEBUG_MSG_LEVEL (SANITIZE, p, this->debug_depth+1, 0,
       "may_edit(%u) [%p..%p] (%d bytes) in [%p..%p] -> %s",
       this->edit_count,
       p, p + len, len,
       this->start, this->end,
       this->writable ? "GRANTED" : "DENIED");

    return this->writable;
  }

  template <typename Type, typename ValueType>
  bool try_set (const Type *obj, const ValueType &v)
  {
    if (this->may_edit (obj, hb_static_size (Type)))
    {
      hb_assign (* const_cast<Type *> (obj), v);
      return true;
    }
    return false;
  }

  template <typename Type>
  hb_blob_t *sanitize_blob (hb_blob_t *blob)
  {
    bool sane;

    init (blob);

  retry:
    DEBUG_MSG_FUNC (SANITIZE, start, "start");

    start_processing ();

    if (unlikely (!start))
    {
      end_processing ();
      return blob;
    }

    Type *t = CastP<Type> (const_cast<char *> (start));

    sane = t->sanitize (this);
    if (sane)
    {
      if (edit_count)
      {
        DEBUG_MSG_FUNC (SANITIZE, start, "passed first round with %d edits; going for second round", edit_count);

        /* sanitize again to ensure no toe-stepping */
        edit_count = 0;
        sane = t->sanitize (this);
        if (edit_count) {
          DEBUG_MSG_FUNC (SANITIZE, start, "requested %d edits in second round; FAILLING", edit_count);
          sane = false;
        }
      }
    }
    else
    {
      if (edit_count && !writable) {
        start = hb_blob_get_data_writable (blob, nullptr);
        end = start + blob->length;

        if (start)
        {
          writable = true;
          /* ok, we made it writable by relocating.  try again */
          DEBUG_MSG_FUNC (SANITIZE, start, "retry");
          goto retry;
        }
      }
    }

    end_processing ();

    DEBUG_MSG_FUNC (SANITIZE, start, sane ? "PASSED" : "FAILED");
    if (sane)
    {
      hb_blob_make_immutable (blob);
      return blob;
    }
    else
    {
      hb_blob_destroy (blob);
      return hb_blob_get_empty ();
    }
  }

  template <typename Type>
  hb_blob_t *reference_table (const hb_face_t *face, hb_tag_t tableTag = Type::tableTag)
  {
    if (!num_glyphs_set)
      set_num_glyphs (hb_face_get_glyph_count (face));
    return sanitize_blob<Type> (hb_face_reference_table (face, tableTag));
  }

  mutable unsigned int debug_depth;
  const char *start, *end;
  mutable int max_ops;
  private:
  bool writable;
  unsigned int edit_count;
  hb_blob_t *blob;
  unsigned int num_glyphs;
  bool  num_glyphs_set;
};

struct hb_sanitize_with_object_t
{
  template <typename T>
  hb_sanitize_with_object_t (hb_sanitize_context_t *c,
                                    const T& obj) : c (c)
  { c->set_object (obj); }
  ~hb_sanitize_with_object_t ()
  { c->reset_object (); }

  private:
  hb_sanitize_context_t *c;
};


/*
 * Serialize
 */

struct hb_serialize_context_t
{
  hb_serialize_context_t (void *start_, unsigned int size)
  {
    this->start = (char *) start_;
    this->end = this->start + size;
    reset ();
  }

  bool in_error () const { return !this->successful; }

  void reset ()
  {
    this->successful = true;
    this->head = this->start;
    this->debug_depth = 0;
  }

  bool propagate_error (bool e)
  { return this->successful = this->successful && e; }
  template <typename T> bool propagate_error (const T &obj)
  { return this->successful = this->successful && !obj.in_error (); }
  template <typename T> bool propagate_error (const T *obj)
  { return this->successful = this->successful && !obj->in_error (); }
  template <typename T1, typename T2> bool propagate_error (T1 &o1, T2 &o2)
  { return propagate_error (o1) && propagate_error (o2); }
  template <typename T1, typename T2> bool propagate_error (T1 *o1, T2 *o2)
  { return propagate_error (o1) && propagate_error (o2); }
  template <typename T1, typename T2, typename T3>
  bool propagate_error (T1 &o1, T2 &o2, T3 &o3)
  { return propagate_error (o1) && propagate_error (o2, o3); }
  template <typename T1, typename T2, typename T3>
  bool propagate_error (T1 *o1, T2 *o2, T3 *o3)
  { return propagate_error (o1) && propagate_error (o2, o3); }

  /* To be called around main operation. */
  template <typename Type>
  Type *start_serialize ()
  {
    DEBUG_MSG_LEVEL (SERIALIZE, this->start, 0, +1,
                     "start [%p..%p] (%lu bytes)",
                     this->start, this->end,
                     (unsigned long) (this->end - this->start));

    return start_embed<Type> ();
  }
  void end_serialize ()
  {
    DEBUG_MSG_LEVEL (SERIALIZE, this->start, 0, -1,
                     "end [%p..%p] serialized %d bytes; %s",
                     this->start, this->end,
                     (int) (this->head - this->start),
                     this->successful ? "successful" : "UNSUCCESSFUL");
  }

  unsigned int length () const { return this->head - this->start; }

  void align (unsigned int alignment)
  {
    unsigned int l = length () % alignment;
    if (l)
      allocate_size<void> (alignment - l);
  }

  template <typename Type>
  Type *start_embed (const Type *_ HB_UNUSED = nullptr) const
  {
    Type *ret = reinterpret_cast<Type *> (this->head);
    return ret;
  }

  template <typename Type>
  Type *allocate_size (unsigned int size)
  {
    if (unlikely (!this->successful || this->end - this->head < ptrdiff_t (size))) {
      this->successful = false;
      return nullptr;
    }
    memset (this->head, 0, size);
    char *ret = this->head;
    this->head += size;
    return reinterpret_cast<Type *> (ret);
  }

  template <typename Type>
  Type *allocate_min ()
  {
    return this->allocate_size<Type> (Type::min_size);
  }

  template <typename Type>
  Type *embed (const Type &obj)
  {
    unsigned int size = obj.get_size ();
    Type *ret = this->allocate_size<Type> (size);
    if (unlikely (!ret)) return nullptr;
    memcpy (ret, &obj, size);
    return ret;
  }
  template <typename Type>
  hb_serialize_context_t &operator << (const Type &obj) { embed (obj); return *this; }

  template <typename Type>
  Type *extend_size (Type &obj, unsigned int size)
  {
    assert (this->start <= (char *) &obj);
    assert ((char *) &obj <= this->head);
    assert ((char *) &obj + size >= this->head);
    if (unlikely (!this->allocate_size<Type> (((char *) &obj) + size - this->head))) return nullptr;
    return reinterpret_cast<Type *> (&obj);
  }

  template <typename Type>
  Type *extend_min (Type &obj) { return extend_size (obj, obj.min_size); }

  template <typename Type>
  Type *extend (Type &obj) { return extend_size (obj, obj.get_size ()); }

  /* Output routines. */
  template <typename Type>
  Type *copy () const
  {
    assert (this->successful);
    unsigned int len = this->head - this->start;
    void *p = malloc (len);
    if (p)
      memcpy (p, this->start, len);
    return reinterpret_cast<Type *> (p);
  }
  hb_bytes_t copy_bytes () const
  {
    assert (this->successful);
    unsigned int len = this->head - this->start;
    void *p = malloc (len);
    if (p)
      memcpy (p, this->start, len);
    else
      return hb_bytes_t ();
    return hb_bytes_t ((char *) p, len);
  }
  hb_blob_t *copy_blob () const
  {
    assert (this->successful);
    return hb_blob_create (this->start,
                           this->head - this->start,
                           HB_MEMORY_MODE_DUPLICATE,
                           nullptr, nullptr);
  }

  public:
  unsigned int debug_depth;
  char *start, *end, *head;
  bool successful;
};



/*
 * Big-endian integers.
 */

template <typename Type, int Bytes> struct BEInt;

template <typename Type>
struct BEInt<Type, 1>
{
  public:
  void set (Type V)      { v = V; }
  operator Type () const { return v; }
  private: uint8_t v;
};
template <typename Type>
struct BEInt<Type, 2>
{
  public:
  void set (Type V)
  {
    v[0] = (V >>  8) & 0xFF;
    v[1] = (V      ) & 0xFF;
  }
  operator Type () const
  {
#if ((defined(__GNUC__) && __GNUC__ >= 5) || defined(__clang__)) && \
    defined(__BYTE_ORDER) && \
    (__BYTE_ORDER == __LITTLE_ENDIAN || __BYTE_ORDER == __BIG_ENDIAN)
    /* Spoon-feed the compiler a big-endian integer with alignment 1.
     * https://github.com/harfbuzz/harfbuzz/pull/1398 */
    struct __attribute__((packed)) packed_uint16_t { uint16_t v; };
#if __BYTE_ORDER == __LITTLE_ENDIAN
    return __builtin_bswap16 (((packed_uint16_t *) this)->v);
#else /* __BYTE_ORDER == __BIG_ENDIAN */
    return ((packed_uint16_t *) this)->v;
#endif
#endif
    return (v[0] <<  8)
         + (v[1]      );
  }
  private: uint8_t v[2];
};
template <typename Type>
struct BEInt<Type, 3>
{
  public:
  void set (Type V)
  {
    v[0] = (V >> 16) & 0xFF;
    v[1] = (V >>  8) & 0xFF;
    v[2] = (V      ) & 0xFF;
  }
  operator Type () const
  {
    return (v[0] << 16)
         + (v[1] <<  8)
         + (v[2]      );
  }
  private: uint8_t v[3];
};
template <typename Type>
struct BEInt<Type, 4>
{
  public:
  typedef Type type;
  void set (Type V)
  {
    v[0] = (V >> 24) & 0xFF;
    v[1] = (V >> 16) & 0xFF;
    v[2] = (V >>  8) & 0xFF;
    v[3] = (V      ) & 0xFF;
  }
  operator Type () const
  {
    return (v[0] << 24)
         + (v[1] << 16)
         + (v[2] <<  8)
         + (v[3]      );
  }
  private: uint8_t v[4];
};


/*
 * Lazy loaders.
 */

template <typename Data, unsigned int WheresData>
struct hb_data_wrapper_t
{
  static_assert (WheresData > 0, "");

  Data * get_data () const
  { return *(((Data **) (void *) this) - WheresData); }

  bool is_inert () const { return !get_data (); }

  template <typename Stored, typename Subclass>
  Stored * call_create () const { return Subclass::create (get_data ()); }
};
template <>
struct hb_data_wrapper_t<void, 0>
{
  bool is_inert () const { return false; }

  template <typename Stored, typename Funcs>
  Stored * call_create () const { return Funcs::create (); }
};

template <typename T1, typename T2> struct hb_non_void_t { typedef T1 value; };
template <typename T2> struct hb_non_void_t<void, T2> { typedef T2 value; };

template <typename Returned,
          typename Subclass = void,
          typename Data = void,
          unsigned int WheresData = 0,
          typename Stored = Returned>
struct hb_lazy_loader_t : hb_data_wrapper_t<Data, WheresData>
{
  typedef typename hb_non_void_t<Subclass,
                                 hb_lazy_loader_t<Returned,Subclass,Data,WheresData,Stored>
                                >::value Funcs;

  void init0 () {} /* Init, when memory is already set to 0. No-op for us. */
  void init ()  { instance.set_relaxed (nullptr); }
  void fini ()  { do_destroy (instance.get ()); }

  void free_instance ()
  {
  retry:
    Stored *p = instance.get ();
    if (unlikely (p && !cmpexch (p, nullptr)))
      goto retry;
    do_destroy (p);
  }

  static void do_destroy (Stored *p)
  {
    if (p && p != const_cast<Stored *> (Funcs::get_null ()))
      Funcs::destroy (p);
  }

  const Returned * operator -> () const { return get (); }
  const Returned & operator * () const  { return *get (); }
  explicit_operator bool () const
  { return get_stored () != Funcs::get_null (); }
  template <typename C> operator const C * () const { return get (); }

  Stored * get_stored () const
  {
  retry:
    Stored *p = this->instance.get ();
    if (unlikely (!p))
    {
      if (unlikely (this->is_inert ()))
        return const_cast<Stored *> (Funcs::get_null ());

      p = this->template call_create<Stored, Funcs> ();
      if (unlikely (!p))
        p = const_cast<Stored *> (Funcs::get_null ());

      if (unlikely (!cmpexch (nullptr, p)))
      {
        do_destroy (p);
        goto retry;
      }
    }
    return p;
  }
  Stored * get_stored_relaxed () const
  {
    return this->instance.get_relaxed ();
  }

  bool cmpexch (Stored *current, Stored *value) const
  {
    /* This *must* be called when there are no other threads accessing. */
    return this->instance.cmpexch (current, value);
  }

  const Returned * get () const { return Funcs::convert (get_stored ()); }
  const Returned * get_relaxed () const { return Funcs::convert (get_stored_relaxed ()); }
  Returned * get_unconst () const { return const_cast<Returned *> (Funcs::convert (get_stored ())); }

  /* To be possibly overloaded by subclasses. */
  static Returned* convert (Stored *p) { return p; }

  /* By default null/init/fini the object. */
  static const Stored* get_null () { return &Null(Stored); }
  static Stored *create (Data *data)
  {
    Stored *p = (Stored *) calloc (1, sizeof (Stored));
    if (likely (p))
      p->init (data);
    return p;
  }
  static Stored *create ()
  {
    Stored *p = (Stored *) calloc (1, sizeof (Stored));
    if (likely (p))
      p->init ();
    return p;
  }
  static void destroy (Stored *p)
  {
    p->fini ();
    free (p);
  }

//  private:
  /* Must only have one pointer. */
  hb_atomic_ptr_t<Stored *> instance;
};

/* Specializations. */

template <typename T, unsigned int WheresFace>
struct hb_face_lazy_loader_t : hb_lazy_loader_t<T,
                                                hb_face_lazy_loader_t<T, WheresFace>,
                                                hb_face_t, WheresFace> {};

template <typename T, unsigned int WheresFace>
struct hb_table_lazy_loader_t : hb_lazy_loader_t<T,
                                                 hb_table_lazy_loader_t<T, WheresFace>,
                                                 hb_face_t, WheresFace,
                                                 hb_blob_t>
{
  static hb_blob_t *create (hb_face_t *face)
  { return hb_sanitize_context_t ().reference_table<T> (face); }
  static void destroy (hb_blob_t *p) { hb_blob_destroy (p); }

  static const hb_blob_t *get_null ()
  { return hb_blob_get_empty (); }

  static const T* convert (const hb_blob_t *blob)
  { return blob->as<T> (); }

  hb_blob_t* get_blob () const { return this->get_stored (); }
};

template <typename Subclass>
struct hb_font_funcs_lazy_loader_t : hb_lazy_loader_t<hb_font_funcs_t, Subclass>
{
  static void destroy (hb_font_funcs_t *p)
  { hb_font_funcs_destroy (p); }
  static const hb_font_funcs_t *get_null ()
  { return hb_font_funcs_get_empty (); }
};
template <typename Subclass>
struct hb_unicode_funcs_lazy_loader_t : hb_lazy_loader_t<hb_unicode_funcs_t, Subclass>
{
  static void destroy (hb_unicode_funcs_t *p)
  { hb_unicode_funcs_destroy (p); }
  static const hb_unicode_funcs_t *get_null ()
  { return hb_unicode_funcs_get_empty (); }
};


#endif /* HB_MACHINERY_HH */
