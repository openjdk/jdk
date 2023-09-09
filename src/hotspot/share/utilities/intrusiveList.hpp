/*
 * Copyright (c) 2017, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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

#ifndef SHARE_VM_UTILITIES_INTRUSIVELIST_HPP
#define SHARE_VM_UTILITIES_INTRUSIVELIST_HPP

#include "memory/allocation.hpp"
#include "metaprogramming/conditional.hpp"
#include "metaprogramming/enableIf.hpp"
#include "metaprogramming/integralConstant.hpp"
#include "metaprogramming/isSame.hpp"
#include "utilities/align.hpp"
#include "utilities/debug.hpp"
#include "utilities/macros.hpp"

class IntrusiveListEntry;
class IntrusiveListImpl;

/**
 * The IntrusiveList class template provides a doubly-linked list in
 * which the links between elements are embedded directly into objects
 * contained in the list.  As a result, there are no copies involved
 * when inserting objects into the list or referencing list objects,
 * and removing an object from a list need not involve destroying the
 * object.
 *
 * To be used in a IntrusiveList, an object must have a
 * IntrusiveListEntry member.  An IntrusiveList is associated with the
 * class of its elements and the entry member.
 *
 * An object can be in multiple lists at the same time, so long as
 * each list uses a different entry member.  That is, the class of the
 * object must have multiple IntrusiveListEntry members, one for each
 * list the object is simultaneously an element.
 *
 * The API for IntrusiveList is modelled on the containers provided by
 * the C++ standard library.  In particular, iteration over the
 * elements is provided via iterator classes.
 *
 * IntrusiveLists support polymorphic elements.  Because the objects
 * in a list are externally managed, rather than being embedded values
 * in the list, the actual type of such objects may be more specific
 * than the list's element type.
 *
 * \tparam T is the class of the elements in the list.
 *
 * \tparam entry_member is a pointer to class member referring to the
 * IntrusiveListEntry subobject of T used by this list.
 *
 * \tparam has_size determines whether the list has a size()
 * operation, returning the number of elements in the list.  If the
 * operation is present, it has constant-time complexity.  The default
 * is to not provide a constant-time size() operation.
 *
 * \tparam Base is the base class for the list.  This is typically
 * used to specify the allocation class, and defaults to _ValueObj.
 *
 * Usage of IntrusiveList involves defining an element class which
 * contains a IntrusiveListEntry member, and using a corresponding
 * specialization of the IntrusiveList class, e.g.
 *
 * <code>
 * class MyClass {
 * public:
 *   ...
 *   IntrusiveListEntry _entry;
 *   ...
 * };
 *
 *   ...
 *   IntrusiveList<MyClass, &MyClass::_entry> mylist;
 *   ... use mylist ...
 * </code>
 *
 * Alternatively, the scope of the entry member can be limited, with a
 * typedef of a list specialization providing access, e.g.
 *
 * <code>
 * class MyClass {
 *   ...
 *   IntrusiveListEntry _entry;
 *   ...
 * public:
 *   ...
 *   typedef IntrusiveList<MyClass, &_entry> MyList;
 *   ...
 * };
 *
 *   ...
 *   MyClass::MyList mylist;
 *   ... use mylist ...
 * </code>
 */
template<typename T,
         IntrusiveListEntry T::*entry_member,
         bool has_size = false,
         typename Base = _ValueObj>
class IntrusiveList;

/**
 * A class with an IntrusiveListEntry member can be used as an element
 * of a corresponding specialization of IntrusiveList.
 */
class IntrusiveListEntry VALUE_OBJ_CLASS_SPEC {
  friend class IntrusiveListImpl;

public:
  IntrusiveListEntry();
  ~IntrusiveListEntry();

private:
  // _prev and _next are the links between elements / entries in an
  // associated list.  The values of these members are type-erased
  // void*.  The IntrusiveListImpl::IteratorOperations class is used
  // to encode, decode, and manipulate the type-erased values.
  //
  // Members are mutable and we deal exclusively with pointers to
  // const to make const_references and const_iterators easier to use;
  // an object being const doesn't prevent modifying its list state.
  mutable const void* _prev;
  mutable const void* _next;
  // The list containing this entry, if any.
  // Debug-only, for use in validity checks.
  DEBUG_ONLY(mutable IntrusiveListImpl* _list;)

  // Noncopyable.
  IntrusiveListEntry(const IntrusiveListEntry&);
  IntrusiveListEntry& operator=(const IntrusiveListEntry&);
};

class IntrusiveListImpl VALUE_OBJ_CLASS_SPEC {
  typedef IntrusiveListEntry Entry;

  // Nothing for clients to see here, everything is private.  Only
  // the IntrusiveList class template has access, via friendship.
  template<typename T, Entry T::*, bool, typename> friend class IntrusiveList;

  typedef size_t size_type;
  typedef ptrdiff_t difference_type;

  Entry _root;

  IntrusiveListImpl();
  ~IntrusiveListImpl();

  // Noncopyable
  IntrusiveListImpl(const IntrusiveListImpl&);
  IntrusiveListImpl& operator=(const IntrusiveListImpl&);

  // Tag manipulation for encoded void*; see IteratorOperations.
  static const uintptr_t tag_alignment = 2;

  static bool is_tagged_entry(const void* ptr) {
    return !is_aligned(ptr, tag_alignment);
  }

  static const void* tag_entry(const Entry* entry) {
    const void* untagged = entry;
    assert(is_aligned(untagged, tag_alignment), "must be");
    return static_cast<const char*>(untagged) + 1;
  }

  static const Entry* untag_entry(const void* ptr) {
    assert(is_tagged_entry(ptr), "precondition");
    const void* untagged = static_cast<const char*>(ptr) - 1;
    assert(is_aligned(untagged, tag_alignment), "must be");
    return static_cast<const Entry*>(untagged);
  }

  const Entry* root_entry() const { return &_root; }

  static void detach(const Entry* entry) {
    // TODO: Consider making the clearing of next & prev DEBUG_ONLY.
    // Any operation that will care is a precondition violation, which
    // we only check in debug builds.
    entry->_prev = NULL;
    entry->_next = NULL;
    DEBUG_ONLY(entry->_list = NULL;)
  }

  // Support for optional constant-time size() operation.
  // Can't be private: C++03 11.4/2; fixed in C++11.
public:
  template<bool has_size, typename Base> class SizeBase;
private:

  // Conversion from T* to Entry*, along with relevant typedefs.  A
  // corresponding specialization is used directly by IntrusiveList,
  // and by the list's iterators.
  template<typename T, Entry T::*entry_member>
  struct ListTraits : public AllStatic {
    typedef IntrusiveListImpl::size_type size_type;
    typedef IntrusiveListImpl::difference_type difference_type;
    typedef T value_type;
    typedef value_type* pointer;
    typedef const value_type* const_pointer;
    typedef value_type& reference;
    typedef const value_type& const_reference;

    static const Entry* get_entry(const_reference value) {
      return &(value.*entry_member);
    }
  };

  // Stand-in for std::distance.
  template<typename Iterator>
  static difference_type distance(Iterator from, Iterator to) {
    difference_type result = 0;
    for ( ; from != to; ++result, ++from) {}
    return result;
  }

  // Iterator support.  IntrusiveList defines its iterator types as
  // specializations of this class.
  template<typename T,
           Entry T::*entry_member,
           bool is_forward,
           bool is_const>
  class IteratorImpl;

  // Iterator support.  Provides (static) functions for manipulating
  // iterators.  These are used to implement iterators and list
  // operations related to iterators, but are not part of the public
  // API for iterators.
  template<typename Iterator> class IteratorOperations;

  // Metafunction for determining the mutable variant of Iterator.
  template<typename Iterator> struct MutableIterator;

  // Metafunction for determining whether L is an IntrusiveList type
  // compatible with T and entry_member.
  template<typename T, Entry T::*entry_member, typename L>
  struct IsCompatibleList;

  // Used as the ConvertibleFrom for IteratorImpl instantiations that
  // don't support implicit conversions.
  class Inaccessible {};

#ifdef ASSERT
  // Get entry's containing list; NULL if entry not in a list.
  static const IntrusiveListImpl* entry_list(const Entry* entry);
  // Set entry's containing list; list may be NULL.
  static void set_entry_list(const Entry* entry, IntrusiveListImpl* list);
#endif // ASSERT
};

// Base class for IntrusiveList, with specializations either providing
// or not providing constant-time size.  Base is the next base class
// in the superclass list.

template<bool has_size, typename Base>
class IntrusiveListImpl::SizeBase : public Base {
protected:
  SizeBase() {}
  ~SizeBase() {}

  size_type* size_ptr() { return NULL; }
  void adjust_size(difference_type n) {}
};

template<typename Base>
class IntrusiveListImpl::SizeBase<true, Base> : public Base {
public:
  size_type size() const { return _size; }

protected:
  SizeBase() : _size(0) {}
  ~SizeBase() {}

  size_type* size_ptr() { return &_size; }
  void adjust_size(difference_type n) { _size += n; }

private:
  size_type _size;
};

template<typename T,
         IntrusiveListEntry T::*entry_member,
         bool is_forward,
         bool is_const>
struct IntrusiveListImpl::MutableIterator<
  IntrusiveListImpl::IteratorImpl<T, entry_member, is_forward, is_const> >
  : public AllStatic
{
  typedef IteratorImpl<T, entry_member, is_forward, false> type;
};

template<typename T, IntrusiveListEntry T::*entry_member, typename L>
struct IntrusiveListImpl::IsCompatibleList : public FalseType {};

template<typename T,
         IntrusiveListEntry T::*entry_member,
         bool has_size,
         typename Base>
struct IntrusiveListImpl::IsCompatibleList<
  T, entry_member, IntrusiveList<T, entry_member, has_size, Base> >
  : public TrueType
{};

// The IteratorOperations class provides operations for encoding,
// decoding, and manipulating type-erased void* values representing
// objects in a list.  The encoded void* provides a discriminated
// union of the following:
//
// - T*: a pointer to a list element.
// - IntrusiveListEntry*: a pointer to a list's root entry.
// - NULL: a pointer to no object.
//
// IntrusiveListEntry uses such encoded values to refer to the next or
// previous object in a list, e.g. to represent the links between
// objects.
//
// IteratorImpl uses such encoded values to refer to the object that
// represents the iterator.  A singular iterator is represented by an
// encoded NULL.  A dereferenceable iterator is represented by an
// encoded pointer to a list element.  An encoded list root entry is
// used to represent either an end-of-list or before-the-beginning
// iterator, depending on context.
//
// The encoding of these values uses a tagged void pointer scheme.
// NULL represents itself.  A list element (T*) is distinguished from
// a IntrusiveListEntry* via the low address bit.  If the low bit is
// set, the value is a IntrusiveListEntry*; specifically, it is one
// byte past the pointer to the entry.  Otherwise, it is a list
// element.  [This requires all value types and IntrusiveListEntry to
// have an alignment of at least 2.]
//
// IteratorOperations also provides a suite of operations for
// manipulating iterators and list elements, making use of that
// encoding.  This allows the implementation of iterators and lists to
// be written in terms of these higher level operations, without
// needing to deal with the underlying encoding directly.
template<typename Iterator>
class IntrusiveListImpl::IteratorOperations : AllStatic {
  typedef IntrusiveListImpl Impl;
  typedef typename Iterator::ListTraits ListTraits;
  typedef typename ListTraits::const_reference const_reference;

  static const bool is_forward = Iterator::is_forward;

  static const void* make_raw_value(const_reference value) {
    return &value;
  }

  static const void* make_raw_value(const Entry* entry) {
    return tag_entry(entry);
  }

  static const Entry* resolve_to_entry(Iterator i) {
    assert_not_singular(i);
    return is_entry(i) ? untag_entry(raw_value(i)) : ListTraits::get_entry(dereference(i));
  }

  static Iterator next(Iterator i) {
    return Iterator(resolve_to_entry(i)->_next);
  }

  static Iterator prev(Iterator i) {
    return Iterator(resolve_to_entry(i)->_prev);
  }

  static Iterator next(const_reference value) {
    return Iterator(ListTraits::get_entry(value)->_next);
  }

  static Iterator prev(const_reference value) {
    return Iterator(ListTraits::get_entry(value)->_prev);
  }

  static void attach_impl(const_reference prev, Iterator next) {
    ListTraits::get_entry(prev)->_next = raw_value(next);
    resolve_to_entry(next)->_prev = make_raw_value(prev);
  }

  static void attach_impl(Iterator prev, const_reference next) {
    resolve_to_entry(prev)->_next = make_raw_value(next);
    ListTraits::get_entry(next)->_prev = raw_value(prev);
  }

  static void iter_attach_impl(Iterator prev, Iterator next) {
    resolve_to_entry(prev)->_next = raw_value(next);
    resolve_to_entry(next)->_prev = raw_value(prev);
  }

public:
  static const void* raw_value(Iterator i) { return i._raw_value; }

  static bool is_singular(Iterator i) { return raw_value(i) == NULL; }
  static bool is_entry(Iterator i) { return is_tagged_entry(raw_value(i)); }

  static const_reference dereference(Iterator i) {
    assert_not_singular(i);
    assert(!is_entry(i), "dereference end-of-list iterator");
    return *static_cast<typename ListTraits::const_pointer>(raw_value(i));
  }

  // Get the predecessor / successor (according to the iterator's
  // direction) of the argument.  Reference arguments are preferred;
  // the iterator form should only be used when the iterator is not
  // already known to be dereferenceable.  (The iterator form of
  // successor is not provided; for an iterator to have a successor,
  // the iterator must be dereferenceable.)

  static Iterator successor(const_reference value) {
    return is_forward ? next(value) : prev(value);
  }

  static Iterator predecessor(const_reference value) {
    return is_forward ? prev(value) : next(value);
  }

  static Iterator iter_predecessor(Iterator i) {
    return is_forward ? prev(i) : next(i);
  }

  // Attach pred to succ such that, after the operation,
  // predecessor(succ) == pred.  A reference argument is required when
  // it is not already in the list, since iterator_to is invalid in
  // that situation.  Reference arguments are preferred; an iterator
  // argument should only be used when it is not already known to be
  // dereferenceable.  That is, the first argument should only be an
  // iterator if it might be a before-the-beginning (pseudo)iterator.
  // Similarly, the second argument should only be an iterator if it
  // might be an end-of-list iterator.  (The two-reference case is not
  // provided because that form is never needed.)

  // Mixed reference / iterator attachment.
  // C++11: PredType&& and SuccType&& ?
  template<typename PredType, typename SuccType>
  static void attach(const PredType& pred, const SuccType& succ) {
    is_forward ? attach_impl(pred, succ) : attach_impl(succ, pred);
  }

  // Iterator to iterator attachment.
  static void iter_attach(Iterator pred, Iterator succ) {
    is_forward ? iter_attach_impl(pred, succ) : iter_attach_impl(succ, pred);
  }

  template<typename Iterator2>
  static Iterator make_iterator(Iterator2 i) {
    return Iterator(IteratorOperations<Iterator2>::raw_value(i));
  }

  static Iterator make_iterator_to(const_reference value) {
    return Iterator(make_raw_value(value));
  }

  static Iterator make_begin_iterator(const Impl& impl) {
    const Entry* entry = impl.root_entry();
    return Iterator(is_forward ? entry->_next : entry->_prev);
  }

  static Iterator make_end_iterator(const Impl& impl) {
    return Iterator(make_raw_value(impl.root_entry()));
  }

  // Support for iterator copy conversion and copy assignment from
  // ConvertibleFrom.  The Inaccessible overload is only declared; it
  // can never be called.  If ConvertibleFrom is *not* Inaccessible,
  // then the template overload is the best (and only) match.

  static const void* raw_value_for_conversion(const Inaccessible&);

  template<typename Iterator2>
  static const void* raw_value_for_conversion(Iterator2 i) {
    return IteratorOperations<Iterator2>::raw_value(i);
  }

  static void assert_not_singular(Iterator i) {
    assert(!is_singular(i), "singular iterator");
  }

  static void assert_is_in_some_list(Iterator i) {
    assert_not_singular(i);
    assert(list_ptr(i) != NULL,
           "Invalid iterator " PTR_FORMAT, p2i(raw_value(i)));
  }

#ifdef ASSERT

  static const Impl* list_ptr(Iterator i) {
    return entry_list(resolve_to_entry(i));
  }

#endif // ASSERT
};

/**
 * Bi-directional constant (e.g. not output) iterator for iterating
 * over the elements of an IntrusiveList.  The IntrusiveList class
 * uses specializations of this class as its iterator types.
 */
template<typename T,
         IntrusiveListEntry T::*entry_member,
         bool is_forward_,
         bool is_const_>
class IntrusiveListImpl::IteratorImpl VALUE_OBJ_CLASS_SPEC {
  friend class IntrusiveListImpl;

  static const bool is_forward = is_forward_;
  static const bool is_const = is_const_;

  typedef IntrusiveListImpl Impl;
  typedef Impl::ListTraits<T, entry_member> ListTraits;
  typedef Impl::IteratorOperations<IteratorImpl> IOps;

  // A const iterator type supports implicit conversion from the
  // corresponding non-const iterator type.  For non-const iterators,
  // use Inaccessible as the ConvertibleFrom type, to simplify
  // overload definitions.
  typedef typename Conditional<is_const,
                               IteratorImpl<T, entry_member, is_forward, false>,
                               Impl::Inaccessible>::type ConvertibleFrom;

public:
  /** Type of an iterator's value. */
  typedef typename ListTraits::value_type value_type;

  /** Type of a reference to an iterator's value. */
  typedef typename Conditional<is_const,
                               typename ListTraits::const_reference,
                               typename ListTraits::reference>::type reference;

  /** Type of a pointer to an iterator's value. */
  typedef typename Conditional<is_const,
                               typename ListTraits::const_pointer,
                               typename ListTraits::pointer>::type pointer;

  /** Type for distance between iterators. */
  typedef typename ListTraits::difference_type difference_type;

  // TODO: We don't have access to <iterator>, so we can't provide the
  // iterator_category type.  Maybe someday...
  // typedef std::bidirectional_iterator_tag iterator_category;

  /** Construct a singular iterator. */
  IteratorImpl() : _raw_value(NULL) {}

  // Default destructor, copy constructor, copy assign.

  // Implicit conversion from ConvertibleFrom.
  // If ConvertibleFrom is Inaccessible, this can't actually be called.
  IteratorImpl(const ConvertibleFrom& other) :
    _raw_value(IOps::raw_value_for_conversion(other))
  {}

  /**
   * Return a reference to the iterator's value.
   *
   * precondition: this is dereferenceable.
   * complexity: constant.
   */
  reference operator*() const {
    return const_cast<reference>(IOps::dereference(*this));
  }

  /**
   * Return a pointer to the iterator's value.
   *
   * precondition: this is dereferenceable.
   * complexity: constant.
   */
  pointer operator->() const {
    return &this->operator*();
  }

  /**
   * Change this iterator to refer to the successor element (per the
   * iterator's direction) in the list, or to the end of the list.
   * Return a reference to this iterator.
   *
   * precondition: this is dereferenceable.
   * postcondition: this is dereferenceable or end-of-list.
   * complexity: constant.
   */
  IteratorImpl& operator++() {
    IOps::assert_is_in_some_list(*this);
    *this = IOps::successor(this->operator*());
    return *this;
  }

  /**
   * Make a copy of this iterator, then change this iterator to refer
   * to the successor element (per the iterator's direction) in the
   * list, or to the end of the list.  Return the copy.
   *
   * precondition: this is dereferenceable.
   * postcondition: this is dereferenceable or end-of-list.
   * complexity: constant.
   */
  IteratorImpl operator++(int) {
    IteratorImpl result = *this;
    this->operator++();
    return result;
  }

  /**
   * Change this iterator to refer to the preceeding element (per the
   * iterator's direction) in the list.  Return a reference to this
   * iterator.
   *
   * precondition: There exists an iterator i such that ++i equals this.
   * postcondition: this is dereferenceable.
   * complexity: constant.
   */
  IteratorImpl& operator--() {
    IOps::assert_is_in_some_list(*this);
    *this = IOps::iter_predecessor(*this);
    // Must not have been (r)begin iterator.
    assert(!IOps::is_entry(*this), "iterator decrement underflow");
    return *this;
  }

  /**
   * Make a copy of this iterator, then change this iterator to refer
   * to the preceeding element (per the iterator's direction) in the
   * list.  Return the copy.
   *
   * precondition: There exists an iterator i such that ++i equals this.
   * postcondition: this is dereferenceable.
   * complexity: constant.
   */
  IteratorImpl operator--(int) {
    IteratorImpl result = *this;
    this->operator--();
    return result;
  }

  /**
   * Return true if this and other refer to the same element of a list,
   * or both refer to end-of-list.
   *
   * precondition: this and other are both dereferenceable or end-of-list.
   * precondition: this and other must be iterators for the same list.
   * complexity: constant.
   */
  bool operator==(const IteratorImpl& other) const {
    IOps::assert_is_in_some_list(*this);
    IOps::assert_is_in_some_list(other);
    assert(IOps::list_ptr(*this) == IOps::list_ptr(other),
           "comparing iterators from different lists");
    return IOps::raw_value(*this) == IOps::raw_value(other);
  }

  /**
   * Return true if this and other are not ==.
   *
   * precondition: this and other are both dereferenceable or end-of-list.
   * precondition: this and other must be iterators for the same list.
   * complexity: constant.
   */
  bool operator!=(const IteratorImpl& other) const {
    return !(*this == other);
  }

  // Comparisons with Inaccessible, to simplify the definitions below
  // for ConvertibleFrom OP IteratorImpl.  Only declared, since can't
  // be called.
  bool operator==(const Impl::Inaccessible&) const;
  bool operator!=(const Impl::Inaccessible&) const;

  // Add ConvertibleFrom OP IteratorImpl overloads, because these are
  // not handled by the corresponding member function plus implicit
  // conversions.  For example, const_iterator == iterator is handled
  // by const_iterator::operator==(const_iterator) plus implicit
  // conversion of iterator to const_iterator.  But we need an
  // additional overload to handle iterator == const_iterator.

  friend bool operator==(const ConvertibleFrom& lhs, const IteratorImpl& rhs) {
    return rhs == lhs;
  }

  friend bool operator!=(const ConvertibleFrom& lhs, const IteratorImpl& rhs) {
    return rhs != lhs;
  }

private:
  // An iterator refers to either an object in the list, the root
  // entry of the list, or NULL if singular.  See IteratorOperations
  // for details of the encoding.
  const void* _raw_value;

  // Allow explicit construction from an encoded const void* raw
  // value.  But require exactly that type, disallowing any implicit
  // conversions.  Without that restriction, certain kinds of usage
  // errors become both more likely and harder to diagnose the
  // resulting compilation errors.  [The remaining diagnostic
  // difficulties could be eliminated by making RawValue a non-public
  // class for carrying the encoded void* to iterator construction.]
  template<typename RawValue>
  explicit IteratorImpl(RawValue raw_value,
                        typename EnableIf<IsSame<RawValue, const void*>::value>::type* = NULL)
    : _raw_value(raw_value)
  {}
};

template<typename T,
         IntrusiveListEntry T::*entry_member,
         bool has_size_,
         typename Base>
class IntrusiveList : public IntrusiveListImpl::SizeBase<has_size_, Base> {
  // Give access to other instantiations, for splice().
  template<typename U, IntrusiveListEntry U::*, bool, typename>
  friend class IntrusiveList;

  typedef IntrusiveListEntry Entry;
  typedef IntrusiveListImpl Impl;
  typedef Impl::ListTraits<T, entry_member> ListTraits;
  typedef Impl::SizeBase<has_size_, Base> Super;

public:
  /** Flag indicating presence of a constant-time size() operation. */
  static const bool has_size = has_size_;

  /** Type of the size of the list. */
  typedef typename ListTraits::size_type size_type;

  /** The difference type for iterators. */
  typedef typename ListTraits::difference_type difference_type;

  /** Type of list elements. */
  typedef typename ListTraits::value_type value_type;

  /** Type of a pointer to a list element. */
  typedef typename ListTraits::pointer pointer;

  /** Type of a const pointer to a list element. */
  typedef typename ListTraits::const_pointer const_pointer;

  /** Type of a reference to a list element. */
  typedef typename ListTraits::reference reference;

  /** Type of a const reference to a list element. */
  typedef typename ListTraits::const_reference const_reference;

  /** Forward iterator type allowing modification of elements. */
  typedef Impl::IteratorImpl<T,                 // element type
                             entry_member,      // pointer to entry member of T
                             true,              // is_forward
                             false>             // is_const
  iterator;

  /** Forward iterator type disallowing modification of elements. */
  typedef Impl::IteratorImpl<T,                 // element type
                             entry_member,      // pointer to entry member of T
                             true,              // is_forward
                             true>              // is_const
  const_iterator;

  /** Reverse iterator type allowing modification of elements. */
  typedef Impl::IteratorImpl<T,                 // element type
                             entry_member,      // pointer to entry member of T
                             false,             // is_forward
                             false>             // is_const
  reverse_iterator;

  /** Reverse iterator type disallowing modification of elements. */
  typedef Impl::IteratorImpl<T,                 // element type
                             entry_member,      // pointer to entry member of T
                             false,             // is_forward
                             true>              // is_const
  const_reverse_iterator;

  /**
   * Inserts value at the front of the list.  Does not affect the
   * validity of iterators or element references for this list.
   *
   * precondition: value must not already be in a list using the same entry.
   * complexity: constant.
   */
  void push_front(const_reference value) {
    insert(begin(), value);
  }

  /**
   * Inserts value at the back of the list.  Does not affect the
   * validity of iterators or element references for this list.
   *
   * precondition: value must not already be in a list using the same entry.
   * complexity: constant.
   */
  void push_back(const_reference value) {
    insert(end(), value);
  }

  /**
   * Removes the front element from the list, and applies the
   * disposer, if any, to the removed element.  The list may not be in
   * a consistent state when the disposer is called.  Invalidates
   * iterators for the removed element.
   *
   * precondition: !empty()
   * complexity: constant.
   */
  void pop_front() {
    pop_front_and_dispose(NopDisposer());
  }

  template<typename Disposer>
  void pop_front_and_dispose(Disposer disposer) {
    erase_and_dispose(begin(), disposer);
  }

  /**
   * Removes the back element from the list, and applies the disposer,
   * if any, to the removed element.  The list may not be in a
   * consistent state when the disposer is called.  Invalidates
   * iterators for the removed element.
   *
   * precondition: !empty()
   * complexity: constant.
   */
  void pop_back() {
    pop_back_and_dispose(NopDisposer());
  }

  template<typename Disposer>
  void pop_back_and_dispose(Disposer disposer) {
    erase_and_dispose(rbegin(), disposer);
  }

  /**
   * Returns a [const_]reference to the front element of the list.
   *
   * precondition: !empty()
   * complexity: constant.
   */
  reference front() { return *begin(); }
  const_reference front() const { return *begin(); }

  /**
   * Returns a [const_]reference to the back element of the list.
   *
   * precondition: !empty()
   * complexity: constant.
   */
  reference back() { return *rbegin(); }
  const_reference back() const { return *rbegin(); }

  /**
   * Returns a [const_]iterator referring to the first element of the
   * list, or end-of-list if the list is empty.
   *
   * complexity: constant.
   */
  iterator begin() {
    return Impl::IteratorOperations<iterator>::make_begin_iterator(_impl);
  }

  const_iterator begin() const {
    return cbegin();
  }

  const_iterator cbegin() const {
    return Impl::IteratorOperations<const_iterator>::make_begin_iterator(_impl);
  }

  /**
   * Returns a [const_]iterator referring to the end-of-list.
   *
   * complexity: constant.
   */
  iterator end() {
    return Impl::IteratorOperations<iterator>::make_end_iterator(_impl);
  }

  const_iterator end() const {
    return cend();
  }

  const_iterator cend() const {
    return Impl::IteratorOperations<const_iterator>::make_end_iterator(_impl);
  }

  /**
   * Returns a [const_]reverse_iterator referring to the last element
   * of the list, or end-of-reversed-list if the list is empty.
   *
   * complexity: constant.
   */
  reverse_iterator rbegin() {
    return Impl::IteratorOperations<reverse_iterator>::make_begin_iterator(_impl);
  }

  const_reverse_iterator rbegin() const {
    return crbegin();
  }

  const_reverse_iterator crbegin() const {
    return Impl::IteratorOperations<const_reverse_iterator>::make_begin_iterator(_impl);
  }

  /**
   * Returns a [const_]reverse_iterator referring to the
   * end-of-reversed-list.
   *
   * complexity: constant.
   */
  reverse_iterator rend() {
    return Impl::IteratorOperations<reverse_iterator>::make_end_iterator(_impl);
  }

  const_reverse_iterator rend() const {
    return crend();
  }

  const_reverse_iterator crend() const {
    return Impl::IteratorOperations<const_reverse_iterator>::make_end_iterator(_impl);
  }

  /**
   * Returns true if list contains no elements.
   *
   * complexity: constant.
   */
  bool empty() const {
    return cbegin() == cend();
  }

  /**
   * Returns the number of elements in the list.
   *
   * complexity: O(length())
   */
  size_type length() const {
    return Impl::distance(cbegin(), cend());
  }

  /**
   * Removes the element referred to by i from the list, then applies
   * the disposer, if any, to the removed element.  The list may not
   * be in a consistent state when the disposer is called.  Returns an
   * iterator for the successor of i.  Invalidates iterators referring
   * to the removed element.
   *
   * precondition: i must be a valid iterator for the list.
   * precondition: i is not end-of-list.
   * complexity: constant.
   */
  iterator erase(const_iterator i) {
    return erase_and_dispose(i, NopDisposer());
  }

  reverse_iterator erase(const_reverse_iterator i) {
    return erase_and_dispose(i, NopDisposer());
  }

  template<typename Disposer>
  iterator erase_and_dispose(const_iterator i, Disposer disposer) {
    return erase_one_and_dispose(i, disposer);
  }

  template<typename Disposer>
  reverse_iterator erase_and_dispose(const_reverse_iterator i, Disposer disposer) {
    return erase_one_and_dispose(i, disposer);
  }

private:

  // C++11: typename Result = MutableIterator...
  template<typename Iterator, typename Disposer>
  typename Impl::MutableIterator<Iterator>::type
  erase_one_and_dispose(Iterator i, Disposer disposer) {
    typedef Impl::IteratorOperations<Iterator> IOps;
    assert_is_iterator(i);
    const_reference value = *i++;
    IOps::iter_attach(IOps::predecessor(value), i);
    detach(value);
    disposer(value);
    return make_iterator<typename Impl::MutableIterator<Iterator>::type>(i);
  }

public:

  /**
   * Removes the elements in the range designated by from and to.
   * Applies the disposer, if any, to each removed element.  The list
   * may not be in a consistent state when the disposer is called.
   * Returns an iterator referring to the end of the removed range.
   * Invalidates iterators referring to the removed elements.
   *
   * precondition: from and to must be valid iterators for the list.
   * precondition: from does not follow to in the list.
   * complexity: O(number of elements removed)
   */
  iterator erase(const_iterator from, const_iterator to) {
    return erase_and_dispose(from, to, NopDisposer());
  }

  reverse_iterator erase(const_reverse_iterator from, const_reverse_iterator to) {
    return erase_and_dispose(from, to, NopDisposer());
  }

  template<typename Disposer>
  iterator erase_and_dispose(const_iterator from, const_iterator to, Disposer disposer) {
    return erase_range_and_dispose(from, to, disposer);
  }

  template<typename Disposer>
  reverse_iterator erase_and_dispose(const_reverse_iterator from,
                                     const_reverse_iterator to,
                                     Disposer disposer) {
    return erase_range_and_dispose(from, to, disposer);
  }

private:

  // C++11: typename Result = MutableIterator...
  template<typename Iterator, typename Disposer>
  typename Impl::MutableIterator<Iterator>::type
  erase_range_and_dispose(Iterator from, Iterator to, Disposer disposer) {
    typedef Impl::IteratorOperations<Iterator> IOps;
    assert_is_iterator(from);
    assert_is_iterator(to);
    if (from != to) {
      IOps::iter_attach(IOps::predecessor(*from), to);
      do {
        const_reference value = *from++;
        detach(value);
        disposer(value);
      } while (from != to);
    }
    return make_iterator<typename Impl::MutableIterator<Iterator>::type>(to);
  }

public:

  /**
   * Removes all of the elements from the list.  Applies the disposer,
   * if any, to each element as it is removed.  The list may not be in
   * a consistent state when the disposer is called.  Invalidates all
   * non-end-of-list iterators for this list.
   *
   * postcondition: empty()
   * complexity: O(length())
   */
  void clear() {
    erase(begin(), end());
  }

  template<typename Disposer>
  void clear_and_dispose(Disposer disposer) {
    erase_and_dispose(begin(), end(), disposer);
  }

  /**
   * Inserts value into the list before pos.  Returns an iterator
   * referring to the newly inserted value.  Does not invalidate any
   * iterators.
   *
   * precondition: pos must be a valid iterator for the list.
   * precondition: value must not already be in a list using the same entry.
   * postcondition: ++result == pos
   * complexity: constant.
   */
  iterator insert(const_iterator pos, const_reference value) {
    return insert_impl(pos, value);
  }

  reverse_iterator insert(const_reverse_iterator pos, const_reference value) {
    return insert_impl(pos, value);
  }

private:

  // C++11: typename Result = MutableIterator...
  template<typename Iterator>
  typename Impl::MutableIterator<Iterator>::type
  insert_impl(Iterator pos, const_reference value) {
    assert(Impl::entry_list(ListTraits::get_entry(value)) == NULL, "precondition");
    assert_is_iterator(pos);
    typedef Impl::IteratorOperations<Iterator> IOps;
    IOps::attach(IOps::iter_predecessor(pos), value);
    IOps::attach(value, pos);
    DEBUG_ONLY(set_list(value, &_impl);)
    adjust_size(1);
    return make_iterator_to<typename Impl::MutableIterator<Iterator>::type>(value);
  }

public:

  /**
   * Transfers the elements of from_list in the range designated by
   * from and to to this list, inserted before pos.  Returns an
   * iterator referring to the head of the spliced in range.  Does
   * not invalidate any iterators.
   *
   * precondition: pos must be a valid iterator for this list.
   * precondition: from and to must be valid iterators for from_list.
   * precondition: from does not follow to.
   * precondition: pos is not in the range to transfer, i.e. if
   * this == from_list then either from follows pos or to does not
   * follow pos.
   *
   * postcondition: iterators referring to elements in the transferred range
   * are valid iterators for this list rather than from_list.
   *
   * complexity: constant if either (a) this == &from_list, (b) neither
   * this nor from_list has a constant-time size() operation, or (c)
   * from_list has a constant-time size() operation and is being
   * transferred in its entirety; otherwise O(number of elements
   * transferred).
   */
  template<typename FromList>
  typename EnableIf<Impl::IsCompatibleList<T, entry_member, FromList>::value,
                    iterator>::type
  splice(const_iterator pos,
         FromList& from_list,
         const_iterator from,
         const_iterator to) {
    // preparation for supporting reverse iterators too.
    typedef const_iterator Iterator;
    typedef typename Impl::MutableIterator<Iterator>::type Result;
    typedef Impl::IteratorOperations<Iterator> IOps;

    assert_is_iterator(pos);
    from_list.assert_is_iterator(from);
    from_list.assert_is_iterator(to);

    if (from == to) {
      // Done if empty range.
      return make_iterator<Result>(pos);
    } else if (is_same_list(from_list) && (pos == to)) {
      // Done if already in desired position.
      return make_iterator_to<Result>(*from);
    }

    // Adjust sizes if needed.  Only need adjustment if different
    // lists and at least one of the lists has a constant-time size.
    if ((has_size || from_list.has_size) && !is_same_list(from_list)) {
      difference_type transferring;
      if (from_list.has_size &&
          (from == from_list.cbegin()) &&
          (to == from_list.cend())) {
        // If from_list has constant-time size() and we're transferring
        // all of it, we can use that size value to avoid counting the
        // number of elements being transferred.
        transferring = *from_list.size_ptr();
      } else {
        // Count the number of elements being transferred.
        transferring = Impl::distance(from, to);
      }
      from_list.adjust_size(-transferring);
      adjust_size(transferring);
    }

#ifdef ASSERT
    // Transfer elements to this list, and verify pos not in [from, to).
    if (is_same_list(from_list)) {
      for (Iterator i = from; i != to; ++i) {
        assert(i != pos, "splice range includes destination");
      }
    } else {
      for (Iterator i = from; i != to; ++i) {
        set_list(*i, &_impl);
      }
    }
#endif // ASSERT

    Iterator to_pred = --Iterator(to); // Fetch before clobbered
    IOps::iter_attach(IOps::predecessor(*from), to);
    IOps::attach(IOps::iter_predecessor(pos), *from);
    // to is end of non-empty range, so to's predecessor is dereferenceable.
    IOps::attach(*to_pred, pos);
    return make_iterator_to<Result>(*from);
  }

  /**
   * Transfers all elements of from_list to this list, inserted before
   * pos.  Returns an iterator referring to the head of the spliced in
   * range.  Does not invalidate any iterators.
   *
   * precondition: pos must be a valid iterator for this list.
   * precondition: this != &from_list.
   *
   * postcondition: iterators referring to elements that were in
   * from_list are valid iterators for this list rather than
   * from_list.
   *
   * Complexity: constant if either (a) this does not have a
   * constant-time size() operation, or (b) from_list has a
   * constant-time size() operation; otherwise O(number of elements
   * transferred).
   */
  template<typename FromList>
  iterator splice(const_iterator pos, FromList& from_list) {
    assert(!is_same_list(from_list), "precondition");
    return splice(pos, from_list, from_list.cbegin(), from_list.cend());
  }

  /**
   * Transfers the element of from_list referred to by from to this
   * list, inserted before pos.  Returns an iterator referring to the
   * inserted element.  Does not invalidate any iterators.
   *
   * precondition: pos must be a valid iterator for this list.
   * precondition: from must be a dereferenceable iterator of from_list.
   * precondition: pos is not in the range to transfer, i.e. if
   * this == &from_list then pos != from.
   *
   * postcondition: iterators referring to the transferred element are
   * valid iterators for this list rather than from_list.
   *
   * complexity: constant.
   */
  template<typename FromList>
  typename EnableIf<Impl::IsCompatibleList<T, entry_member, FromList>::value,
                    iterator>::type
  splice(const_iterator pos, FromList& from_list, const_iterator from) {
    // preparation for supporting reverse iterators too.
    typedef const_iterator Iterator;
    typedef typename Impl::MutableIterator<Iterator>::type Result;
    typedef Impl::IteratorOperations<Iterator> IOps;

    assert_is_iterator(pos);
    from_list.assert_is_iterator(from);

    from_list.adjust_size(-1);
    adjust_size(1);

#ifdef ASSERT
    // Transfer element to this list, or verify pos not in [from, to).
    if (is_same_list(from_list)) {
      assert(from != pos, "Splice range includes destination");
    } else {
      set_list(*from, &_impl);
    }
#endif // ASSERT

    IOps::iter_attach(IOps::predecessor(*from), IOps::successor(*from));
    IOps::attach(IOps::iter_predecessor(pos), *from);
    IOps::attach(*from, pos);
    return make_iterator_to<Result>(*from);
  }

  /**
   * Returns a [const_][reverse_]iterator referring to value.
   *
   * precondition: value must be an element of the list.
   * complexity: constant.
   */
  iterator iterator_to(reference value) {
    return make_iterator_to<iterator>(value);
  }

  const_iterator iterator_to(const_reference value) const {
    return const_iterator_to(value);
  }

  const_iterator const_iterator_to(const_reference value) const {
    return make_iterator_to<const_iterator>(value);
  }

  reverse_iterator reverse_iterator_to(reference value) {
    return make_iterator_to<reverse_iterator>(value);
  }

  const_reverse_iterator reverse_iterator_to(const_reference value) const {
    return const_reverse_iterator_to(value);
  }

  const_reverse_iterator const_reverse_iterator_to(const_reference value) const {
    return make_iterator_to<const_reverse_iterator>(value);
  }

private:
  Impl _impl;

  size_type* size_ptr() {
    return Super::size_ptr();
  }

  void adjust_size(difference_type value) {
    Super::adjust_size(value);
  }

  template<typename OtherList>
  bool is_same_list(const OtherList& other) const {
    return &_impl == &other._impl;
  }

  template<typename Iterator>
  void assert_is_iterator(const Iterator& i) const {
    typedef Impl::IteratorOperations<Iterator> IOps;
    assert(IOps::list_ptr(i) == &_impl,
           "Iterator " PTR_FORMAT " not for this list " PTR_FORMAT,
           p2i(IOps::raw_value(i)), p2i(this));
  }

  void assert_is_element(const_reference value) const {
    assert(Impl::entry_list(ListTraits::get_entry(value)) == &_impl,
           "Value " PTR_FORMAT " not in this list " PTR_FORMAT,
           p2i(&value), p2i(this));
  }

#ifdef ASSERT
  void set_list(const_reference value, Impl* list) {
    Impl::set_entry_list(ListTraits::get_entry(value), list);
  }
#endif

  template<typename Result, typename From>
  Result make_iterator(From i) const {
    assert_is_iterator(i);
    return Impl::IteratorOperations<Result>::make_iterator(i);
  }

  template<typename Iterator>
  Iterator make_iterator_to(const_reference value) const {
    assert_is_element(value);
    return Impl::IteratorOperations<Iterator>::make_iterator_to(value);
  }

  struct NopDisposer VALUE_OBJ_CLASS_SPEC {
    void operator()(const_reference) const {}
  };

  void detach(const_reference value) {
    assert_is_element(value);
    Impl::detach(ListTraits::get_entry(value));
    adjust_size(-1);
  }
};

#endif // include guard
