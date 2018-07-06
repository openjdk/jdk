/*
 * Copyright © 2007,2008,2009  Red Hat, Inc.
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
 * Red Hat Author(s): Behdad Esfahbod
 * Google Author(s): Behdad Esfahbod
 */

#ifndef HB_PRIVATE_HH
#define HB_PRIVATE_HH

#define _GNU_SOURCE 1

#ifdef HAVE_CONFIG_H
#include "config.h"
#endif

#include "hb.h"
#define HB_H_IN
#ifdef HAVE_OT
#include "hb-ot.h"
#define HB_OT_H_IN
#endif

#include <math.h>
#include <stdlib.h>
#include <stddef.h>
#include <string.h>
#include <assert.h>
#include <errno.h>
#include <stdio.h>
#include <stdarg.h>

#if (defined(_MSC_VER) && _MSC_VER >= 1500) || defined(__MINGW32__)
#include <intrin.h>
#endif

#define HB_PASTE1(a,b) a##b
#define HB_PASTE(a,b) HB_PASTE1(a,b)

/* Compile-time custom allocator support. */

#if defined(hb_malloc_impl) \
 && defined(hb_calloc_impl) \
 && defined(hb_realloc_impl) \
 && defined(hb_free_impl)
extern "C" void* hb_malloc_impl(size_t size);
extern "C" void* hb_calloc_impl(size_t nmemb, size_t size);
extern "C" void* hb_realloc_impl(void *ptr, size_t size);
extern "C" void  hb_free_impl(void *ptr);
#define malloc hb_malloc_impl
#define calloc hb_calloc_impl
#define realloc hb_realloc_impl
#define free hb_free_impl
#endif


/* Compiler attributes */


#if __cplusplus < 201103L

#ifndef nullptr
#define nullptr NULL
#endif

// Static assertions
#ifndef static_assert
#define static_assert(e, msg) \
        HB_UNUSED typedef int HB_PASTE(static_assertion_failed_at_line_, __LINE__) [(e) ? 1 : -1]
#endif // static_assert

#ifdef __GNUC__
#if (__GNUC__ < 4 || (__GNUC__ == 4 && __GNUC_MINOR__ < 8))
#define thread_local __thread
#endif
#else
#define thread_local
#endif

#endif // __cplusplus < 201103L

#if (defined(__GNUC__) || defined(__clang__)) && defined(__OPTIMIZE__)
#define likely(expr) (__builtin_expect (!!(expr), 1))
#define unlikely(expr) (__builtin_expect (!!(expr), 0))
#else
#define likely(expr) (expr)
#define unlikely(expr) (expr)
#endif

#if !defined(__GNUC__) && !defined(__clang__)
#undef __attribute__
#define __attribute__(x)
#endif

#if __GNUC__ >= 3
#define HB_PURE_FUNC    __attribute__((pure))
#define HB_CONST_FUNC   __attribute__((const))
#define HB_PRINTF_FUNC(format_idx, arg_idx) __attribute__((__format__ (__printf__, format_idx, arg_idx)))
#else
#define HB_PURE_FUNC
#define HB_CONST_FUNC
#define HB_PRINTF_FUNC(format_idx, arg_idx)
#endif
#if __GNUC__ >= 4
#define HB_UNUSED       __attribute__((unused))
#elif defined(_MSC_VER) /* https://github.com/harfbuzz/harfbuzz/issues/635 */
#define HB_UNUSED __pragma(warning(suppress: 4100 4101))
#else
#define HB_UNUSED
#endif

#ifndef HB_INTERNAL
# if !defined(HB_NO_VISIBILITY) && !defined(__MINGW32__) && !defined(__CYGWIN__) && !defined(_MSC_VER) && !defined(__SUNPRO_CC)
#  define HB_INTERNAL __attribute__((__visibility__("hidden")))
# else
#  define HB_INTERNAL
#  define HB_NO_VISIBILITY 1
# endif
#endif

#if __GNUC__ >= 3
#define HB_FUNC __PRETTY_FUNCTION__
#elif defined(_MSC_VER)
#define HB_FUNC __FUNCSIG__
#else
#define HB_FUNC __func__
#endif

#if defined(__SUNPRO_CC) && (__SUNPRO_CC < 0x5140)
/* https://github.com/harfbuzz/harfbuzz/issues/630 */
#define __restrict
#endif

/*
 * Borrowed from https://bugzilla.mozilla.org/show_bug.cgi?id=1215411
 * HB_FALLTHROUGH is an annotation to suppress compiler warnings about switch
 * cases that fall through without a break or return statement. HB_FALLTHROUGH
 * is only needed on cases that have code:
 *
 * switch (foo) {
 *   case 1: // These cases have no code. No fallthrough annotations are needed.
 *   case 2:
 *   case 3:
 *     foo = 4; // This case has code, so a fallthrough annotation is needed:
 *     HB_FALLTHROUGH;
 *   default:
 *     return foo;
 * }
 */
#if defined(__clang__) && __cplusplus >= 201103L
   /* clang's fallthrough annotations are only available starting in C++11. */
#  define HB_FALLTHROUGH [[clang::fallthrough]]
#elif __GNUC__ >= 7
   /* GNU fallthrough attribute is available from GCC7 */
#  define HB_FALLTHROUGH __attribute__((fallthrough))
#elif defined(_MSC_VER)
   /*
    * MSVC's __fallthrough annotations are checked by /analyze (Code Analysis):
    * https://msdn.microsoft.com/en-us/library/ms235402%28VS.80%29.aspx
    */
#  include <sal.h>
#  define HB_FALLTHROUGH __fallthrough
#else
#  define HB_FALLTHROUGH /* FALLTHROUGH */
#endif

#if defined(_WIN32) || defined(__CYGWIN__)
   /* We need Windows Vista for both Uniscribe backend and for
    * MemoryBarrier.  We don't support compiling on Windows XP,
    * though we run on it fine. */
#  if defined(_WIN32_WINNT) && _WIN32_WINNT < 0x0600
#    undef _WIN32_WINNT
#  endif
#  ifndef _WIN32_WINNT
#    define _WIN32_WINNT 0x0600
#  endif
#  ifndef WIN32_LEAN_AND_MEAN
#    define WIN32_LEAN_AND_MEAN 1
#  endif
#  ifndef STRICT
#    define STRICT 1
#  endif

#  if defined(_WIN32_WCE)
     /* Some things not defined on Windows CE. */
#    define vsnprintf _vsnprintf
#    define getenv(Name) nullptr
#    if _WIN32_WCE < 0x800
#      define setlocale(Category, Locale) "C"
static int errno = 0; /* Use something better? */
#    endif
#  elif defined(WINAPI_FAMILY) && (WINAPI_FAMILY==WINAPI_FAMILY_PC_APP || WINAPI_FAMILY==WINAPI_FAMILY_PHONE_APP)
#    define getenv(Name) nullptr
#  endif
#  if defined(_MSC_VER) && _MSC_VER < 1900
#    define snprintf _snprintf
#  endif
#endif

#if HAVE_ATEXIT
/* atexit() is only safe to be called from shared libraries on certain
 * platforms.  Whitelist.
 * https://bugs.freedesktop.org/show_bug.cgi?id=82246 */
#  if defined(__linux) && defined(__GLIBC_PREREQ)
#    if __GLIBC_PREREQ(2,3)
/* From atexit() manpage, it's safe with glibc 2.2.3 on Linux. */
#      define HB_USE_ATEXIT 1
#    endif
#  elif defined(_MSC_VER) || defined(__MINGW32__)
/* For MSVC:
 * https://msdn.microsoft.com/en-us/library/tze57ck3.aspx
 * https://msdn.microsoft.com/en-us/library/zk17ww08.aspx
 * mingw32 headers say atexit is safe to use in shared libraries.
 */
#    define HB_USE_ATEXIT 1
#  elif defined(__ANDROID__)
/* This is available since Android NKD r8 or r8b:
 * https://issuetracker.google.com/code/p/android/issues/detail?id=6455
 */
#    define HB_USE_ATEXIT 1
#  elif defined(__APPLE__)
/* For macOS and related platforms, the atexit man page indicates
 * that it will be invoked when the library is unloaded, not only
 * at application exit.
 */
#    define HB_USE_ATEXIT 1
#  endif
#endif
#ifdef HB_NO_ATEXIT
#  undef HB_USE_ATEXIT
#endif

/* Basics */

#undef MIN
template <typename Type>
static inline Type MIN (const Type &a, const Type &b) { return a < b ? a : b; }

#undef MAX
template <typename Type>
static inline Type MAX (const Type &a, const Type &b) { return a > b ? a : b; }

static inline unsigned int DIV_CEIL (const unsigned int a, unsigned int b)
{ return (a + (b - 1)) / b; }


#undef  ARRAY_LENGTH
template <typename Type, unsigned int n>
static inline unsigned int ARRAY_LENGTH (const Type (&)[n]) { return n; }
/* A const version, but does not detect erratically being called on pointers. */
#define ARRAY_LENGTH_CONST(__array) ((signed int) (sizeof (__array) / sizeof (__array[0])))

#define HB_STMT_START do
#define HB_STMT_END   while (0)

template <unsigned int cond> class hb_assert_constant_t;
template <> class hb_assert_constant_t<1> {};

#define ASSERT_STATIC_EXPR_ZERO(_cond) (0 * (unsigned int) sizeof (hb_assert_constant_t<_cond>))

/* Lets assert int types.  Saves trouble down the road. */

static_assert ((sizeof (int8_t) == 1), "");
static_assert ((sizeof (uint8_t) == 1), "");
static_assert ((sizeof (int16_t) == 2), "");
static_assert ((sizeof (uint16_t) == 2), "");
static_assert ((sizeof (int32_t) == 4), "");
static_assert ((sizeof (uint32_t) == 4), "");
static_assert ((sizeof (int64_t) == 8), "");
static_assert ((sizeof (uint64_t) == 8), "");

static_assert ((sizeof (hb_codepoint_t) == 4), "");
static_assert ((sizeof (hb_position_t) == 4), "");
static_assert ((sizeof (hb_mask_t) == 4), "");
static_assert ((sizeof (hb_var_int_t) == 4), "");


/* We like our types POD */

#define _ASSERT_TYPE_POD1(_line, _type) union _type_##_type##_on_line_##_line##_is_not_POD { _type instance; }
#define _ASSERT_TYPE_POD0(_line, _type) _ASSERT_TYPE_POD1 (_line, _type)
#define ASSERT_TYPE_POD(_type)          _ASSERT_TYPE_POD0 (__LINE__, _type)

#ifdef __GNUC__
# define _ASSERT_INSTANCE_POD1(_line, _instance) \
        HB_STMT_START { \
                typedef __typeof__(_instance) _type_##_line; \
                _ASSERT_TYPE_POD1 (_line, _type_##_line); \
        } HB_STMT_END
#else
# define _ASSERT_INSTANCE_POD1(_line, _instance)        typedef int _assertion_on_line_##_line##_not_tested
#endif
# define _ASSERT_INSTANCE_POD0(_line, _instance)        _ASSERT_INSTANCE_POD1 (_line, _instance)
# define ASSERT_INSTANCE_POD(_instance)                 _ASSERT_INSTANCE_POD0 (__LINE__, _instance)

/* Check _assertion in a method environment */
#define _ASSERT_POD1(_line) \
        HB_UNUSED inline void _static_assertion_on_line_##_line (void) const \
        { _ASSERT_INSTANCE_POD1 (_line, *this); /* Make sure it's POD. */ }
# define _ASSERT_POD0(_line)    _ASSERT_POD1 (_line)
# define ASSERT_POD()           _ASSERT_POD0 (__LINE__)



/* Tiny functions */

/*
 * Void!
 */
typedef const struct _hb_void_t *hb_void_t;
#define HB_VOID ((const _hb_void_t *) nullptr)

/* Return the number of 1 bits in v. */
template <typename T>
static inline HB_CONST_FUNC unsigned int
_hb_popcount (T v)
{
#if (__GNUC__ > 3 || (__GNUC__ == 3 && __GNUC_MINOR__ >= 4)) && defined(__OPTIMIZE__)
  if (sizeof (T) <= sizeof (unsigned int))
    return __builtin_popcount (v);

  if (sizeof (T) <= sizeof (unsigned long))
    return __builtin_popcountl (v);

  if (sizeof (T) <= sizeof (unsigned long long))
    return __builtin_popcountll (v);
#endif

  if (sizeof (T) <= 4)
  {
    /* "HACKMEM 169" */
    uint32_t y;
    y = (v >> 1) &033333333333;
    y = v - y - ((y >>1) & 033333333333);
    return (((y + (y >> 3)) & 030707070707) % 077);
  }

  if (sizeof (T) == 8)
  {
    unsigned int shift = 32;
    return _hb_popcount<uint32_t> ((uint32_t) v) + _hb_popcount ((uint32_t) (v >> shift));
  }

  if (sizeof (T) == 16)
  {
    unsigned int shift = 64;
    return _hb_popcount<uint64_t> ((uint64_t) v) + _hb_popcount ((uint64_t) (v >> shift));
  }

  assert (0);
  return 0; /* Shut up stupid compiler. */
}

/* Returns the number of bits needed to store number */
template <typename T>
static inline HB_CONST_FUNC unsigned int
_hb_bit_storage (T v)
{
  if (unlikely (!v)) return 0;

#if defined(__GNUC__) && (__GNUC__ >= 4) && defined(__OPTIMIZE__)
  if (sizeof (T) <= sizeof (unsigned int))
    return sizeof (unsigned int) * 8 - __builtin_clz (v);

  if (sizeof (T) <= sizeof (unsigned long))
    return sizeof (unsigned long) * 8 - __builtin_clzl (v);

  if (sizeof (T) <= sizeof (unsigned long long))
    return sizeof (unsigned long long) * 8 - __builtin_clzll (v);
#endif

#if (defined(_MSC_VER) && _MSC_VER >= 1500) || defined(__MINGW32__)
  if (sizeof (T) <= sizeof (unsigned int))
  {
    unsigned long where;
    _BitScanReverse (&where, v);
    return 1 + where;
  }
# if _WIN64
  if (sizeof (T) <= 8)
  {
    unsigned long where;
    _BitScanReverse64 (&where, v);
    return 1 + where;
  }
# endif
#endif

  if (sizeof (T) <= 4)
  {
    /* "bithacks" */
    const unsigned int b[] = {0x2, 0xC, 0xF0, 0xFF00, 0xFFFF0000};
    const unsigned int S[] = {1, 2, 4, 8, 16};
    unsigned int r = 0;
    for (int i = 4; i >= 0; i--)
      if (v & b[i])
      {
        v >>= S[i];
        r |= S[i];
      }
    return r + 1;
  }
  if (sizeof (T) <= 8)
  {
    /* "bithacks" */
    const uint64_t b[] = {0x2ULL, 0xCULL, 0xF0ULL, 0xFF00ULL, 0xFFFF0000ULL, 0xFFFFFFFF00000000ULL};
    const unsigned int S[] = {1, 2, 4, 8, 16, 32};
    unsigned int r = 0;
    for (int i = 5; i >= 0; i--)
      if (v & b[i])
      {
        v >>= S[i];
        r |= S[i];
      }
    return r + 1;
  }
  if (sizeof (T) == 16)
  {
    unsigned int shift = 64;
    return (v >> shift) ? _hb_bit_storage<uint64_t> ((uint64_t) (v >> shift)) + shift :
                          _hb_bit_storage<uint64_t> ((uint64_t) v);
  }

  assert (0);
  return 0; /* Shut up stupid compiler. */
}

/* Returns the number of zero bits in the least significant side of v */
template <typename T>
static inline HB_CONST_FUNC unsigned int
_hb_ctz (T v)
{
  if (unlikely (!v)) return 0;

#if defined(__GNUC__) && (__GNUC__ >= 4) && defined(__OPTIMIZE__)
  if (sizeof (T) <= sizeof (unsigned int))
    return __builtin_ctz (v);

  if (sizeof (T) <= sizeof (unsigned long))
    return __builtin_ctzl (v);

  if (sizeof (T) <= sizeof (unsigned long long))
    return __builtin_ctzll (v);
#endif

#if (defined(_MSC_VER) && _MSC_VER >= 1500) || defined(__MINGW32__)
  if (sizeof (T) <= sizeof (unsigned int))
  {
    unsigned long where;
    _BitScanForward (&where, v);
    return where;
  }
# if _WIN64
  if (sizeof (T) <= 8)
  {
    unsigned long where;
    _BitScanForward64 (&where, v);
    return where;
  }
# endif
#endif

  if (sizeof (T) <= 4)
  {
    /* "bithacks" */
    unsigned int c = 32;
    v &= - (int32_t) v;
    if (v) c--;
    if (v & 0x0000FFFF) c -= 16;
    if (v & 0x00FF00FF) c -= 8;
    if (v & 0x0F0F0F0F) c -= 4;
    if (v & 0x33333333) c -= 2;
    if (v & 0x55555555) c -= 1;
    return c;
  }
  if (sizeof (T) <= 8)
  {
    /* "bithacks" */
    unsigned int c = 64;
    v &= - (int64_t) (v);
    if (v) c--;
    if (v & 0x00000000FFFFFFFFULL) c -= 32;
    if (v & 0x0000FFFF0000FFFFULL) c -= 16;
    if (v & 0x00FF00FF00FF00FFULL) c -= 8;
    if (v & 0x0F0F0F0F0F0F0F0FULL) c -= 4;
    if (v & 0x3333333333333333ULL) c -= 2;
    if (v & 0x5555555555555555ULL) c -= 1;
    return c;
  }
  if (sizeof (T) == 16)
  {
    unsigned int shift = 64;
    return (uint64_t) v ? _hb_bit_storage<uint64_t> ((uint64_t) v) :
                          _hb_bit_storage<uint64_t> ((uint64_t) v >> shift) + shift;
  }

  assert (0);
  return 0; /* Shut up stupid compiler. */
}

static inline bool
_hb_unsigned_int_mul_overflows (unsigned int count, unsigned int size)
{
  return (size > 0) && (count >= ((unsigned int) -1) / size);
}

static inline unsigned int
_hb_ceil_to_4 (unsigned int v)
{
  return ((v - 1) | 3) + 1;
}



/*
 *
 * Utility types
 *
 */

#define HB_DISALLOW_COPY_AND_ASSIGN(TypeName) \
  TypeName(const TypeName&); \
  void operator=(const TypeName&)

/*
 * Static pools
 */

/* Global nul-content Null pool.  Enlarge as necessary. */

#define HB_NULL_POOL_SIZE 264
static_assert (HB_NULL_POOL_SIZE % sizeof (void *) == 0, "Align HB_NULL_POOL_SIZE.");

#ifdef HB_NO_VISIBILITY
static
#else
extern HB_INTERNAL
#endif
void * const _hb_NullPool[HB_NULL_POOL_SIZE / sizeof (void *)]
#ifdef HB_NO_VISIBILITY
= {}
#endif
;
/* Generic nul-content Null objects. */
template <typename Type>
static inline Type const & Null (void) {
  static_assert (sizeof (Type) <= HB_NULL_POOL_SIZE, "Increase HB_NULL_POOL_SIZE.");
  return *reinterpret_cast<Type const *> (_hb_NullPool);
}
#define Null(Type) Null<Type>()

/* Specializaiton for arbitrary-content arbitrary-sized Null objects. */
#define DEFINE_NULL_DATA(Namespace, Type, data) \
} /* Close namespace. */ \
static const char _Null##Type[sizeof (Namespace::Type) + 1] = data; /* +1 is for nul-termination in data */ \
template <> \
/*static*/ inline const Namespace::Type& Null<Namespace::Type> (void) { \
  return *reinterpret_cast<const Namespace::Type *> (_Null##Type); \
} \
namespace Namespace { \
/* The following line really exists such that we end in a place needing semicolon */ \
static_assert (Namespace::Type::min_size + 1 <= sizeof (_Null##Type), "Null pool too small.  Enlarge.")


/* Global writable pool.  Enlarge as necessary. */

/* To be fully correct, CrapPool must be thread_local. However, we do not rely on CrapPool
 * for correct operation. It only exist to catch and divert program logic bugs instead of
 * causing bad memory access. So, races there are not actually introducing incorrectness
 * in the code. Has ~12kb binary size overhead to have it, also clang build fails with it. */
#ifdef HB_NO_VISIBILITY
static
#else
extern HB_INTERNAL
#endif
/*thread_local*/ void * _hb_CrapPool[HB_NULL_POOL_SIZE / sizeof (void *)]
#ifdef HB_NO_VISIBILITY
= {}
#endif
;
/* CRAP pool: Common Region for Access Protection. */
template <typename Type>
static inline Type& Crap (void) {
  static_assert (sizeof (Type) <= HB_NULL_POOL_SIZE, "Increase HB_NULL_POOL_SIZE.");
  Type *obj = reinterpret_cast<Type *> (_hb_CrapPool);
  *obj = Null(Type);
  return *obj;
}
#define Crap(Type) Crap<Type>()

template <typename Type>
struct CrapOrNull {
  static inline Type & get (void) { return Crap(Type); }
};
template <typename Type>
struct CrapOrNull<const Type> {
  static inline Type const & get (void) { return Null(Type); }
};
#define CrapOrNull(Type) CrapOrNull<Type>::get ()



/* arrays and maps */


#define HB_PREALLOCED_ARRAY_INIT {0, 0, nullptr}
template <typename Type, unsigned int StaticSize=8>
struct hb_vector_t
{
  unsigned int len;
  unsigned int allocated;
  bool successful;
  Type *arrayZ;
  Type static_array[StaticSize];

  void init (void)
  {
    len = 0;
    allocated = ARRAY_LENGTH (static_array);
    successful = true;
    arrayZ = static_array;
  }

  inline Type& operator [] (unsigned int i)
  {
    if (unlikely (i >= len))
      return Crap (Type);
    return arrayZ[i];
  }
  inline const Type& operator [] (unsigned int i) const
  {
    if (unlikely (i >= len))
      return Null(Type);
    return arrayZ[i];
  }

  inline Type *push (void)
  {
    if (unlikely (!resize (len + 1)))
      return &Crap(Type);
    return &arrayZ[len - 1];
  }
  inline Type *push (const Type& v)
  {
    Type *p = push ();
    *p = v;
    return p;
  }

  /* Allocate for size but don't adjust len. */
  inline bool alloc (unsigned int size)
  {
    if (unlikely (!successful))
      return false;

    if (likely (size <= allocated))
      return true;

    /* Reallocate */

    unsigned int new_allocated = allocated;
    while (size >= new_allocated)
      new_allocated += (new_allocated >> 1) + 8;

    Type *new_array = nullptr;

    if (arrayZ == static_array)
    {
      new_array = (Type *) calloc (new_allocated, sizeof (Type));
      if (new_array)
        memcpy (new_array, arrayZ, len * sizeof (Type));
    }
    else
    {
      bool overflows = (new_allocated < allocated) || _hb_unsigned_int_mul_overflows (new_allocated, sizeof (Type));
      if (likely (!overflows))
        new_array = (Type *) realloc (arrayZ, new_allocated * sizeof (Type));
    }

    if (unlikely (!new_array))
    {
      successful = false;
      return false;
    }

    arrayZ = new_array;
    allocated = new_allocated;

    return true;
  }

  inline bool resize (int size_)
  {
    unsigned int size = size_ < 0 ? 0u : (unsigned int) size_;
    if (!alloc (size))
      return false;

    if (size > len)
      memset (arrayZ + len, 0, (size - len) * sizeof (*arrayZ));

    len = size;
    return true;
  }

  inline void pop (void)
  {
    if (!len) return;
    len--;
  }

  inline void remove (unsigned int i)
  {
     if (unlikely (i >= len))
       return;
     memmove (static_cast<void *> (&arrayZ[i]),
              static_cast<void *> (&arrayZ[i + 1]),
              (len - i - 1) * sizeof (Type));
     len--;
  }

  inline void shrink (int size_)
  {
    unsigned int size = size_ < 0 ? 0u : (unsigned int) size_;
     if (size < len)
       len = size;
  }

  template <typename T>
  inline Type *find (T v) {
    for (unsigned int i = 0; i < len; i++)
      if (arrayZ[i] == v)
        return &arrayZ[i];
    return nullptr;
  }
  template <typename T>
  inline const Type *find (T v) const {
    for (unsigned int i = 0; i < len; i++)
      if (arrayZ[i] == v)
        return &arrayZ[i];
    return nullptr;
  }

  inline void qsort (int (*cmp)(const void*, const void*))
  {
    ::qsort (arrayZ, len, sizeof (Type), cmp);
  }

  inline void qsort (void)
  {
    ::qsort (arrayZ, len, sizeof (Type), Type::cmp);
  }

  inline void qsort (unsigned int start, unsigned int end)
  {
    ::qsort (arrayZ + start, end - start, sizeof (Type), Type::cmp);
  }

  template <typename T>
  inline Type *lsearch (const T &x)
  {
    for (unsigned int i = 0; i < len; i++)
      if (0 == this->arrayZ[i].cmp (&x))
        return &arrayZ[i];
    return nullptr;
  }

  template <typename T>
  inline Type *bsearch (const T &x)
  {
    unsigned int i;
    return bfind (x, &i) ? &arrayZ[i] : nullptr;
  }
  template <typename T>
  inline const Type *bsearch (const T &x) const
  {
    unsigned int i;
    return bfind (x, &i) ? &arrayZ[i] : nullptr;
  }
  template <typename T>
  inline bool bfind (const T &x, unsigned int *i) const
  {
    int min = 0, max = (int) this->len - 1;
    while (min <= max)
    {
      int mid = (min + max) / 2;
      int c = this->arrayZ[mid].cmp (&x);
      if (c < 0)
        max = mid - 1;
      else if (c > 0)
        min = mid + 1;
      else
      {
        *i = mid;
        return true;
      }
    }
    if (max < 0 || (max < (int) this->len && this->arrayZ[max].cmp (&x) > 0))
      max++;
    *i = max;
    return false;
  }

  inline void fini (void)
  {
    if (arrayZ != static_array)
      free (arrayZ);
    arrayZ = nullptr;
    allocated = len = 0;
  }
};

template <typename Type>
struct hb_auto_t : Type
{
  hb_auto_t (void) { Type::init (); }
  ~hb_auto_t (void) { Type::fini (); }
  private: /* Hide */
  void init (void) {}
  void fini (void) {}
};
template <typename Type>
struct hb_auto_array_t : hb_auto_t <hb_vector_t <Type> > {};


#define HB_LOCKABLE_SET_INIT {HB_PREALLOCED_ARRAY_INIT}
template <typename item_t, typename lock_t>
struct hb_lockable_set_t
{
  hb_vector_t <item_t, 1> items;

  inline void init (void) { items.init (); }

  template <typename T>
  inline item_t *replace_or_insert (T v, lock_t &l, bool replace)
  {
    l.lock ();
    item_t *item = items.find (v);
    if (item) {
      if (replace) {
        item_t old = *item;
        *item = v;
        l.unlock ();
        old.fini ();
      }
      else {
        item = nullptr;
        l.unlock ();
      }
    } else {
      item = items.push (v);
      l.unlock ();
    }
    return item;
  }

  template <typename T>
  inline void remove (T v, lock_t &l)
  {
    l.lock ();
    item_t *item = items.find (v);
    if (item) {
      item_t old = *item;
      *item = items[items.len - 1];
      items.pop ();
      l.unlock ();
      old.fini ();
    } else {
      l.unlock ();
    }
  }

  template <typename T>
  inline bool find (T v, item_t *i, lock_t &l)
  {
    l.lock ();
    item_t *item = items.find (v);
    if (item)
      *i = *item;
    l.unlock ();
    return !!item;
  }

  template <typename T>
  inline item_t *find_or_insert (T v, lock_t &l)
  {
    l.lock ();
    item_t *item = items.find (v);
    if (!item) {
      item = items.push (v);
    }
    l.unlock ();
    return item;
  }

  inline void fini (lock_t &l)
  {
    if (!items.len) {
      /* No need for locking. */
      items.fini ();
      return;
    }
    l.lock ();
    while (items.len) {
      item_t old = items[items.len - 1];
        items.pop ();
        l.unlock ();
        old.fini ();
        l.lock ();
    }
    items.fini ();
    l.unlock ();
  }

};


/* ASCII tag/character handling */

static inline bool ISALPHA (unsigned char c)
{ return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z'); }
static inline bool ISALNUM (unsigned char c)
{ return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9'); }
static inline bool ISSPACE (unsigned char c)
{ return c == ' ' || c =='\f'|| c =='\n'|| c =='\r'|| c =='\t'|| c =='\v'; }
static inline unsigned char TOUPPER (unsigned char c)
{ return (c >= 'a' && c <= 'z') ? c - 'a' + 'A' : c; }
static inline unsigned char TOLOWER (unsigned char c)
{ return (c >= 'A' && c <= 'Z') ? c - 'A' + 'a' : c; }


/* HB_NDEBUG disables some sanity checks that are very safe to disable and
 * should be disabled in production systems.  If NDEBUG is defined, enable
 * HB_NDEBUG; but if it's desirable that normal assert()s (which are very
 * light-weight) to be enabled, then HB_DEBUG can be defined to disable
 * the costlier checks. */
#ifdef NDEBUG
#define HB_NDEBUG 1
#endif


/* Misc */

template <typename T> class hb_assert_unsigned_t;
template <> class hb_assert_unsigned_t<unsigned char> {};
template <> class hb_assert_unsigned_t<unsigned short> {};
template <> class hb_assert_unsigned_t<unsigned int> {};
template <> class hb_assert_unsigned_t<unsigned long> {};

template <typename T> static inline bool
hb_in_range (T u, T lo, T hi)
{
  /* The sizeof() is here to force template instantiation.
   * I'm sure there are better ways to do this but can't think of
   * one right now.  Declaring a variable won't work as HB_UNUSED
   * is unusable on some platforms and unused types are less likely
   * to generate a warning than unused variables. */
  static_assert ((sizeof (hb_assert_unsigned_t<T>) >= 0), "");

  /* The casts below are important as if T is smaller than int,
   * the subtract results will become a signed int! */
  return (T)(u - lo) <= (T)(hi - lo);
}

template <typename T> static inline bool
hb_in_ranges (T u, T lo1, T hi1, T lo2, T hi2)
{
  return hb_in_range (u, lo1, hi1) || hb_in_range (u, lo2, hi2);
}

template <typename T> static inline bool
hb_in_ranges (T u, T lo1, T hi1, T lo2, T hi2, T lo3, T hi3)
{
  return hb_in_range (u, lo1, hi1) || hb_in_range (u, lo2, hi2) || hb_in_range (u, lo3, hi3);
}


/* Enable bitwise ops on enums marked as flags_t */
/* To my surprise, looks like the function resolver is happy to silently cast
 * one enum to another...  So this doesn't provide the type-checking that I
 * originally had in mind... :(.
 *
 * For MSVC warnings, see: https://github.com/harfbuzz/harfbuzz/pull/163
 */
#ifdef _MSC_VER
# pragma warning(disable:4200)
# pragma warning(disable:4800)
#endif
#define HB_MARK_AS_FLAG_T(T) \
        extern "C++" { \
          static inline T operator | (T l, T r) { return T ((unsigned) l | (unsigned) r); } \
          static inline T operator & (T l, T r) { return T ((unsigned) l & (unsigned) r); } \
          static inline T operator ^ (T l, T r) { return T ((unsigned) l ^ (unsigned) r); } \
          static inline T operator ~ (T r) { return T (~(unsigned int) r); } \
          static inline T& operator |= (T &l, T r) { l = l | r; return l; } \
          static inline T& operator &= (T& l, T r) { l = l & r; return l; } \
          static inline T& operator ^= (T& l, T r) { l = l ^ r; return l; } \
        }


/* Useful for set-operations on small enums.
 * For example, for testing "x ∈ {x1, x2, x3}" use:
 * (FLAG_UNSAFE(x) & (FLAG(x1) | FLAG(x2) | FLAG(x3)))
 */
#define FLAG(x) (ASSERT_STATIC_EXPR_ZERO ((unsigned int)(x) < 32) + (1U << (unsigned int)(x)))
#define FLAG_UNSAFE(x) ((unsigned int)(x) < 32 ? (1U << (unsigned int)(x)) : 0)
#define FLAG_RANGE(x,y) (ASSERT_STATIC_EXPR_ZERO ((x) < (y)) + FLAG(y+1) - FLAG(x))


template <typename T, typename T2> static inline void
hb_stable_sort (T *array, unsigned int len, int(*compar)(const T *, const T *), T2 *array2)
{
  for (unsigned int i = 1; i < len; i++)
  {
    unsigned int j = i;
    while (j && compar (&array[j - 1], &array[i]) > 0)
      j--;
    if (i == j)
      continue;
    /* Move item i to occupy place for item j, shift what's in between. */
    {
      T t = array[i];
      memmove (&array[j + 1], &array[j], (i - j) * sizeof (T));
      array[j] = t;
    }
    if (array2)
    {
      T2 t = array2[i];
      memmove (&array2[j + 1], &array2[j], (i - j) * sizeof (T2));
      array2[j] = t;
    }
  }
}

template <typename T> static inline void
hb_stable_sort (T *array, unsigned int len, int(*compar)(const T *, const T *))
{
  hb_stable_sort (array, len, compar, (int *) nullptr);
}

static inline hb_bool_t
hb_codepoint_parse (const char *s, unsigned int len, int base, hb_codepoint_t *out)
{
  /* Pain because we don't know whether s is nul-terminated. */
  char buf[64];
  len = MIN (ARRAY_LENGTH (buf) - 1, len);
  strncpy (buf, s, len);
  buf[len] = '\0';

  char *end;
  errno = 0;
  unsigned long v = strtoul (buf, &end, base);
  if (errno) return false;
  if (*end) return false;
  *out = v;
  return true;
}


/* Vectorization */

struct HbOpOr
{
  static const bool passthru_left = true;
  static const bool passthru_right = true;
  template <typename T> static void process (T &o, const T &a, const T &b) { o = a | b; }
};
struct HbOpAnd
{
  static const bool passthru_left = false;
  static const bool passthru_right = false;
  template <typename T> static void process (T &o, const T &a, const T &b) { o = a & b; }
};
struct HbOpMinus
{
  static const bool passthru_left = true;
  static const bool passthru_right = false;
  template <typename T> static void process (T &o, const T &a, const T &b) { o = a & ~b; }
};
struct HbOpXor
{
  static const bool passthru_left = true;
  static const bool passthru_right = true;
  template <typename T> static void process (T &o, const T &a, const T &b) { o = a ^ b; }
};


/* Compiler-assisted vectorization. */

/* The `vector_size' attribute was introduced in gcc 3.1. */
#if defined( __GNUC__ ) && ( __GNUC__ >= 4 )
#define HB_VECTOR_SIZE 128
#elif !defined(HB_VECTOR_SIZE)
#define HB_VECTOR_SIZE 0
#endif

/* Type behaving similar to vectorized vars defined using __attribute__((vector_size(...))). */
template <typename elt_t, unsigned int byte_size>
struct hb_vector_size_t
{
  elt_t& operator [] (unsigned int i) { return u.v[i]; }
  const elt_t& operator [] (unsigned int i) const { return u.v[i]; }

  template <class Op>
  inline hb_vector_size_t process (const hb_vector_size_t &o) const
  {
    hb_vector_size_t r;
#if HB_VECTOR_SIZE
    if (HB_VECTOR_SIZE && 0 == (byte_size * 8) % HB_VECTOR_SIZE)
      for (unsigned int i = 0; i < ARRAY_LENGTH (u.vec); i++)
        Op::process (r.u.vec[i], u.vec[i], o.u.vec[i]);
    else
#endif
      for (unsigned int i = 0; i < ARRAY_LENGTH (u.v); i++)
        Op::process (r.u.v[i], u.v[i], o.u.v[i]);
    return r;
  }
  inline hb_vector_size_t operator | (const hb_vector_size_t &o) const
  { return process<HbOpOr> (o); }
  inline hb_vector_size_t operator & (const hb_vector_size_t &o) const
  { return process<HbOpAnd> (o); }
  inline hb_vector_size_t operator ^ (const hb_vector_size_t &o) const
  { return process<HbOpXor> (o); }
  inline hb_vector_size_t operator ~ () const
  {
    hb_vector_size_t r;
#if HB_VECTOR_SIZE && 0
    if (HB_VECTOR_SIZE && 0 == (byte_size * 8) % HB_VECTOR_SIZE)
      for (unsigned int i = 0; i < ARRAY_LENGTH (u.vec); i++)
        r.u.vec[i] = ~u.vec[i];
    else
#endif
    for (unsigned int i = 0; i < ARRAY_LENGTH (u.v); i++)
      r.u.v[i] = ~u.v[i];
    return r;
  }

  private:
  static_assert (byte_size / sizeof (elt_t) * sizeof (elt_t) == byte_size, "");
  union {
    elt_t v[byte_size / sizeof (elt_t)];
#if HB_VECTOR_SIZE
    typedef unsigned long vec_t __attribute__((vector_size (HB_VECTOR_SIZE / 8)));
    vec_t vec[byte_size / sizeof (vec_t)];
#endif
  } u;
};


/* Global runtime options. */

struct hb_options_t
{
  unsigned int initialized : 1;
  unsigned int uniscribe_bug_compatible : 1;
};

union hb_options_union_t {
  unsigned int i;
  hb_options_t opts;
};
static_assert ((sizeof (int) == sizeof (hb_options_union_t)), "");

HB_INTERNAL void
_hb_options_init (void);

extern HB_INTERNAL hb_options_union_t _hb_options;

static inline hb_options_t
hb_options (void)
{
  if (unlikely (!_hb_options.i))
    _hb_options_init ();

  return _hb_options.opts;
}

/* Size signifying variable-sized array */
#define VAR 1


/* String type. */

struct hb_bytes_t
{
  inline hb_bytes_t (void) : bytes (nullptr), len (0) {}
  inline hb_bytes_t (const char *bytes_, unsigned int len_) : bytes (bytes_), len (len_) {}

  inline int cmp (const hb_bytes_t &a) const
  {
    if (len != a.len)
      return (int) a.len - (int) len;

    return memcmp (a.bytes, bytes, len);
  }
  static inline int cmp (const void *pa, const void *pb)
  {
    hb_bytes_t *a = (hb_bytes_t *) pa;
    hb_bytes_t *b = (hb_bytes_t *) pb;
    return b->cmp (*a);
  }

  const char *bytes;
  unsigned int len;
};


/* fallback for round() */
#if !defined (HAVE_ROUND) && !defined (HAVE_DECL_ROUND)
static inline double
_hb_round (double x)
{
  if (x >= 0)
    return floor (x + 0.5);
  else
    return ceil (x - 0.5);
}
#define round(x) _hb_round(x)
#endif


#endif /* HB_PRIVATE_HH */
