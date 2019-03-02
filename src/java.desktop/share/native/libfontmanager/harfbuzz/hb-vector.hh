/*
 * Copyright © 2017,2018  Google, Inc.
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

#ifndef HB_VECTOR_HH
#define HB_VECTOR_HH

#include "hb.hh"
#include "hb-array.hh"
#include "hb-null.hh"


template <typename Type>
struct hb_vector_t
{
  typedef Type item_t;
  static constexpr unsigned item_size = hb_static_size (Type);

  HB_NO_COPY_ASSIGN_TEMPLATE (hb_vector_t, Type);
  hb_vector_t ()  { init (); }
  ~hb_vector_t () { fini (); }

  unsigned int length;
  private:
  int allocated; /* == -1 means allocation failed. */
  Type *arrayZ_;
  public:

  void init ()
  {
    allocated = length = 0;
    arrayZ_ = nullptr;
  }

  void fini ()
  {
    if (arrayZ_)
      free (arrayZ_);
    init ();
  }
  void fini_deep ()
  {
    Type *array = arrayZ();
    unsigned int count = length;
    for (unsigned int i = 0; i < count; i++)
      array[i].fini ();
    fini ();
  }

  const Type * arrayZ () const { return arrayZ_; }
        Type * arrayZ ()       { return arrayZ_; }

  Type& operator [] (int i_)
  {
    unsigned int i = (unsigned int) i_;
    if (unlikely (i >= length))
      return Crap (Type);
    return arrayZ()[i];
  }
  const Type& operator [] (int i_) const
  {
    unsigned int i = (unsigned int) i_;
    if (unlikely (i >= length))
      return Null(Type);
    return arrayZ()[i];
  }

  explicit_operator bool () const { return length; }

  hb_array_t<Type> as_array ()
  { return hb_array (arrayZ(), length); }
  hb_array_t<const Type> as_array () const
  { return hb_array (arrayZ(), length); }

  hb_array_t<const Type> sub_array (unsigned int start_offset, unsigned int count) const
  { return as_array ().sub_array (start_offset, count);}
  hb_array_t<const Type> sub_array (unsigned int start_offset, unsigned int *count = nullptr /* IN/OUT */) const
  { return as_array ().sub_array (start_offset, count);}
  hb_array_t<Type> sub_array (unsigned int start_offset, unsigned int count)
  { return as_array ().sub_array (start_offset, count);}
  hb_array_t<Type> sub_array (unsigned int start_offset, unsigned int *count = nullptr /* IN/OUT */)
  { return as_array ().sub_array (start_offset, count);}

  hb_sorted_array_t<Type> as_sorted_array ()
  { return hb_sorted_array (arrayZ(), length); }
  hb_sorted_array_t<const Type> as_sorted_array () const
  { return hb_sorted_array (arrayZ(), length); }

  hb_array_t<const Type> sorted_sub_array (unsigned int start_offset, unsigned int count) const
  { return as_sorted_array ().sorted_sub_array (start_offset, count);}
  hb_array_t<const Type> sorted_sub_array (unsigned int start_offset, unsigned int *count = nullptr /* IN/OUT */) const
  { return as_sorted_array ().sorted_sub_array (start_offset, count);}
  hb_array_t<Type> sorted_sub_array (unsigned int start_offset, unsigned int count)
  { return as_sorted_array ().sorted_sub_array (start_offset, count);}
  hb_array_t<Type> sorted_sub_array (unsigned int start_offset, unsigned int *count = nullptr /* IN/OUT */)
  { return as_sorted_array ().sorted_sub_array (start_offset, count);}

  template <typename T> explicit_operator T * () { return arrayZ(); }
  template <typename T> explicit_operator const T * () const { return arrayZ(); }
  operator hb_array_t<Type> ()             { return as_array (); }
  operator hb_array_t<const Type> () const { return as_array (); }

  Type * operator  + (unsigned int i) { return arrayZ() + i; }
  const Type * operator  + (unsigned int i) const { return arrayZ() + i; }

  Type *push ()
  {
    if (unlikely (!resize (length + 1)))
      return &Crap(Type);
    return &arrayZ()[length - 1];
  }
  Type *push (const Type& v)
  {
    Type *p = push ();
    *p = v;
    return p;
  }

  bool in_error () const { return allocated < 0; }

  /* Allocate for size but don't adjust length. */
  bool alloc (unsigned int size)
  {
    if (unlikely (allocated < 0))
      return false;

    if (likely (size <= (unsigned) allocated))
      return true;

    /* Reallocate */

    unsigned int new_allocated = allocated;
    while (size >= new_allocated)
      new_allocated += (new_allocated >> 1) + 8;

    Type *new_array = nullptr;
    bool overflows =
      (int) new_allocated < 0 ||
      (new_allocated < (unsigned) allocated) ||
      hb_unsigned_mul_overflows (new_allocated, sizeof (Type));
    if (likely (!overflows))
      new_array = (Type *) realloc (arrayZ_, new_allocated * sizeof (Type));

    if (unlikely (!new_array))
    {
      allocated = -1;
      return false;
    }

    arrayZ_ = new_array;
    allocated = new_allocated;

    return true;
  }

  bool resize (int size_)
  {
    unsigned int size = size_ < 0 ? 0u : (unsigned int) size_;
    if (!alloc (size))
      return false;

    if (size > length)
      memset (arrayZ() + length, 0, (size - length) * sizeof (*arrayZ()));

    length = size;
    return true;
  }

  void pop ()
  {
    if (!length) return;
    length--;
  }

  void remove (unsigned int i)
  {
    if (unlikely (i >= length))
      return;
    Type *array = arrayZ();
    memmove (static_cast<void *> (&array[i]),
             static_cast<void *> (&array[i + 1]),
             (length - i - 1) * sizeof (Type));
    length--;
  }

  void shrink (int size_)
  {
    unsigned int size = size_ < 0 ? 0u : (unsigned int) size_;
     if (size < length)
       length = size;
  }

  template <typename T>
  Type *find (T v)
  {
    Type *array = arrayZ();
    for (unsigned int i = 0; i < length; i++)
      if (array[i] == v)
        return &array[i];
    return nullptr;
  }
  template <typename T>
  const Type *find (T v) const
  {
    const Type *array = arrayZ();
    for (unsigned int i = 0; i < length; i++)
      if (array[i] == v)
        return &array[i];
    return nullptr;
  }

  void qsort (int (*cmp)(const void*, const void*))
  { as_array ().qsort (cmp); }
  void qsort (unsigned int start = 0, unsigned int end = (unsigned int) -1)
  { as_array ().qsort (start, end); }

  template <typename T>
  Type *lsearch (const T &x, Type *not_found = nullptr)
  { return as_array ().lsearch (x, not_found); }
  template <typename T>
  const Type *lsearch (const T &x, const Type *not_found = nullptr) const
  { return as_array ().lsearch (x, not_found); }

  template <typename T>
  Type *bsearch (const T &x, Type *not_found = nullptr)
  { return as_sorted_array ().bsearch (x, not_found); }
  template <typename T>
  const Type *bsearch (const T &x, const Type *not_found = nullptr) const
  { return as_sorted_array ().bsearch (x, not_found); }
  template <typename T>
  bool bfind (const T &x, unsigned int *i = nullptr,
                     hb_bfind_not_found_t not_found = HB_BFIND_NOT_FOUND_DONT_STORE,
                     unsigned int to_store = (unsigned int) -1) const
  { return as_sorted_array ().bfind (x, i, not_found, to_store); }
};


#endif /* HB_VECTOR_HH */
