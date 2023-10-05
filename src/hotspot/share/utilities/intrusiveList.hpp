/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_UTILITIES_INTRUSIVELIST_HPP
#define SHARE_UTILITIES_INTRUSIVELIST_HPP

#include "memory/allStatic.hpp"
#include "metaprogramming/enableIf.hpp"
#include "metaprogramming/logical.hpp"
#include "utilities/align.hpp"
#include "utilities/debug.hpp"
#include "utilities/macros.hpp"
#include <type_traits>

class IntrusiveListEntry;
class IntrusiveListImpl;

/**
 * The type of a function for accessing the list entry of an IntrusiveList's
 * element type T.  Such a function takes a reference to const T and returns a
 * reference to const IntrusiveListEntry.
 */
template<typename T>
using IntrusiveListEntryAccessor =
  const IntrusiveListEntry& (*)(std::add_const_t<T>&);

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
 * * T is the class of the elements in the list.  Must be a possibly
 * const-qualified class type.
 *
 * * get_entry is a function of type IntrusiveListEntryAccessor<T> used
 * for accessing the IntrusiveListEntry subobject of T used by this list.
 *
 * * has_size determines whether the list has a size()
 * operation, returning the number of elements in the list.  If the
 * operation is present, it has constant-time complexity.  The default
 * is to not provide a constant-time size() operation.
 *
 * * Base is the base class for the list.  This is typically
 * used to specify the allocation class.  The default is void, indicating
 * no allocation class for the list.
 *
 * The value type for a list may be const-qualified.  Such a list provides
 * const iterators and access to const-qualified elements.  A list whose value
 * type is not const-qualified can provide both const and non-const iterators
 * and can provide access to either const or unqualified elements, depending
 * on the operation used.  A list that is itself const only provides const
 * iterators and access to const-qualified elements.  A const object cannot be
 * added to a list whose value type is not const-qualified, as that would be
 * an implicit casting away of the const qualifier.
 *
 * Some operations that remove elements from a list take a disposer argument.
 * This is a function or function object that will be called with one
 * argument, a const reference to a removed element.  This function should
 * "dispose" of the argument object when called, such as by deleting the
 * object.  The result of the call is ignored.
 *
 * Usage of IntrusiveList involves defining an element class which
 * contains a IntrusiveListEntry member, and using a corresponding
 * specialization of the IntrusiveList class, e.g.
 *
 * <code>
 * class MyClass {
 *   ...
 *   IntrusiveListEntry _entry;
 *   ...
 * public:
 *   ...
 *   static const IntrusiveListEntry& get_entry(const MyClass& v) {
 *     return v._entry;
 *   }
 *   ...
 * };
 *
 *   ...
 *   IntrusiveList<MyClass, &MyClass::get_entry> mylist;
 *   ... use mylist ...
 * </code>
 *
 * Alternatively, the scope of the entry accessor can be limited, with a
 * type alias of a list specialization providing access, e.g.
 *
 * <code>
 * class MyClass {
 *   ...
 *   IntrusiveListEntry _entry;
 *   static const IntrusiveListEntry& get_entry(const MyClass& v) {
 *     return v._entry;
 *   }
 *   ...
 * public:
 *   ...
 *   usnig MyList = IntrusiveList<MyClass, &get_entry>;
 *   ...
 * };
 *
 *   ...
 *   MyClass::MyList mylist;
 *   ... use mylist ...
 * </code>
 */
template<typename T,
         IntrusiveListEntryAccessor<T> entry_accessor,
         bool has_size = false,
         typename Base = void>
class IntrusiveList;

/**
 * A class with an IntrusiveListEntry member can be used as an element
 * of a corresponding specialization of IntrusiveList.
 */
class IntrusiveListEntry {
  friend class IntrusiveListImpl;

public:
  /** Make an entry not attached to any list. */
  IntrusiveListEntry()
    : _prev(nullptr),
      _next(nullptr)
      DEBUG_ONLY(COMMA _list(nullptr))
   {}

  /**
   * Destroy the entry.
   *
   * precondition: not an element of a list.
   */
  ~IntrusiveListEntry() NOT_DEBUG(= default);

  NONCOPYABLE(IntrusiveListEntry);

  /** Test whether this entry is attached to some list. */
  bool is_attached() const {
    bool result = (_prev != nullptr);
    assert(result == (_next != nullptr), "inconsistent entry");
    return result;
  }

private:
  // _prev and _next are the links between elements / root entries in
  // an associated list.  The values of these members are type-erased
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
};

class IntrusiveListImpl {
public:
  struct TestSupport;            // For unit tests

private:
  using Entry = IntrusiveListEntry;

  // Nothing for clients to see here, everything is private.  Only
  // the IntrusiveList class template has access, via friendship.
  template<typename T,
           IntrusiveListEntryAccessor<T>,
           bool,
           typename>
  friend class IntrusiveList;

  using size_type = size_t;
  using difference_type = ptrdiff_t;

  Entry _root;

  IntrusiveListImpl();
  ~IntrusiveListImpl() NOT_DEBUG(= default);

  NONCOPYABLE(IntrusiveListImpl);

  // Tag manipulation for encoded void*; see IteratorOperations.
  static const uintptr_t _tag_alignment = 2;

  static bool is_tagged_root_entry(const void* ptr) {
    return !is_aligned(ptr, _tag_alignment);
  }

  static const void* add_tag_to_root_entry(const Entry* entry) {
    assert(is_aligned(entry, _tag_alignment), "must be");
    const void* untagged = entry;
    return static_cast<const char*>(untagged) + 1;
  }

  static const Entry* remove_tag_from_root_entry(const void* ptr) {
    assert(is_tagged_root_entry(ptr), "precondition");
    const void* untagged = static_cast<const char*>(ptr) - 1;
    assert(is_aligned(untagged, _tag_alignment), "must be");
    return static_cast<const Entry*>(untagged);
  }

  const Entry* root_entry() const { return &_root; }

  static void detach(const Entry& entry) {
    entry._prev = nullptr;
    entry._next = nullptr;
    DEBUG_ONLY(entry._list = nullptr;)
  }

  class NoAllocationBase {};
  template<typename Base> struct ResolveBase;

  // Support for optional constant-time size() operation.
  template<bool has_size, typename Base> class SizeBase;

  // Conversion from T* to Entry*, along with relevant type aliases.  A
  // corresponding specialization is used directly by IntrusiveList,
  // and by the list's iterators.
  template<typename T>
  struct ListTraits : public AllStatic {
    static_assert(std::is_class<T>::value, "precondition");
    // May be const, but not volatile.
    static_assert(!std::is_volatile<T>::value, "precondition");

    using size_type = IntrusiveListImpl::size_type;
    using difference_type = IntrusiveListImpl::difference_type;
    using value_type = T;
    using pointer = std::add_pointer_t<value_type>;
    using const_pointer = std::add_pointer_t<std::add_const_t<value_type>>;
    using reference = std::add_lvalue_reference_t<value_type>;
    using const_reference = std::add_lvalue_reference_t<std::add_const_t<value_type>>;
  };

  // Stand-in for std::distance.
  template<typename Iterator1, typename Iterator2>
  static difference_type distance(Iterator1 from, Iterator2 to) {
    difference_type result = 0;
    for ( ; from != to; ++result, ++from) {}
    return result;
  }

  // Iterator support.  IntrusiveList defines its iterator types as
  // specializations of this class.
  template<typename T,
           IntrusiveListEntryAccessor<T> entry_accessor,
           bool is_forward>
  class IteratorImpl;

  // Iterator support.  Provides (static) functions for manipulating
  // iterators.  These are used to implement iterators and list
  // operations related to iterators, but are not part of the public
  // API for iterators.
  template<typename Iterator> class IteratorOperations;

  template<typename Iterator> struct EntryAccess;

  // Predicate metafunction for determining whether T is a non-const
  // IntrusiveList type.
  template<typename T>
  struct IsListType : public std::false_type {};

#ifdef ASSERT
  // Get entry's containing list; null if entry not in a list.
  static const IntrusiveListImpl* entry_list(const Entry& entry);
  // Set entry's containing list; list may be null.
  static void set_entry_list(const Entry& entry, IntrusiveListImpl* list);
#endif // ASSERT
};

// Metafunction for resolving the Base template parameter for
// IntrusiveList, handling the default void type and transforming it
// into the internal NoAllocationBase.

template<typename Base>
struct IntrusiveListImpl::ResolveBase {
  using type = Base;
};

template<>
struct IntrusiveListImpl::ResolveBase<void> {
  using type = NoAllocationBase;
};

// Base class for IntrusiveList, with specializations either providing
// or not providing constant-time size.  Base is the corresponding template
// parameter from IntrusiveList.

template<bool has_size, typename Base>
class IntrusiveListImpl::SizeBase : public ResolveBase<Base>::type {
protected:
  SizeBase() = default;
  ~SizeBase() = default;

  size_type* size_ptr() { return nullptr; }
  void adjust_size(difference_type n) {}
};

template<typename Base>
class IntrusiveListImpl::SizeBase<true, Base> : public ResolveBase<Base>::type {
public:
  size_type size() const { return _size; }

protected:
  SizeBase() : _size(0) {}
  ~SizeBase() = default;

  size_type* size_ptr() { return &_size; }
  void adjust_size(difference_type n) { _size += n; }

private:
  size_type _size;
};

// Destructure iterator type to be provide calling the entry accessor.
template<typename T,
         IntrusiveListEntryAccessor<T> accessor,
         bool is_forward>
struct IntrusiveListImpl::EntryAccess<
  IntrusiveListImpl::IteratorImpl<T, accessor, is_forward>>
{
  using const_reference = std::add_lvalue_reference_t<std::add_const_t<T>>;
  static const Entry& get_entry(const_reference v) {
    return accessor(v);
  }
};

template<typename T,
         IntrusiveListEntryAccessor<T> accessor,
         bool has_size,
         typename Base>
struct IntrusiveListImpl::IsListType<IntrusiveList<T, accessor, has_size, Base>>
  : public std::true_type
{};

// The IteratorOperations class provides operations for encoding,
// decoding, and manipulating type-erased void* values representing
// objects in a list.  The encoded void* provides a discriminated
// union of the following:
//
// - T*: a pointer to a list element.
// - IntrusiveListEntry*: a pointer to a list's root entry.
// - nullptr: a pointer to no object.
//
// IntrusiveListEntry uses such encoded values to refer to the next or
// previous object in a list, e.g. to represent the links between
// objects.
//
// IteratorImpl uses such encoded values to refer to the object that
// represents the iterator.  A singular iterator is represented by an
// encoded null.  A dereferenceable iterator is represented by an
// encoded pointer to a list element.  An encoded list root entry is
// used to represent either an end-of-list or before-the-beginning
// iterator, depending on context.
//
// The encoding of these values uses a tagged void pointer scheme.
// null represents itself.  A list element (T*) is distinguished from
// a IntrusiveListEntry* via the low address bit.  If the low bit is
// set, the value is a IntrusiveListEntry*; specifically, it is one
// byte past the pointer to the entry.  Otherwise, it is a list
// element.  [This requires all value types and IntrusiveListEntry to
// have an alignment of at least 2.]
//
// This encoding leads to minimal cost for typical correct iteration patterns.
// Dereferencing an iterator referring to a list element consists of just
// reinterpreting the type of the iterator's internal value.  And for
// iteration over a range denoted by a pair of iterators, until the iteration
// reaches the end of the range the current iterator always refers to a list
// element.  Similarly, incrementing an iterator consists of just a load from
// the iterator's internal value plus a constant offset.
//
// IteratorOperations also provides a suite of operations for
// manipulating iterators and list elements, making use of that
// encoding.  This allows the implementation of iterators and lists to
// be written in terms of these higher level operations, without
// needing to deal with the underlying encoding directly.
//
// Note that various functions provided by this class take a const_reference
// argument.  This means some of these functions may break the rule against
// putting const elements in lists with non-const elements.  It is up to
// callers to ensure that doesn't really happen and result in implicitly
// casting away const of the passed argument.  That is, if the list has
// non-const elements then the actual argument must be non-const, even though
// the function parameter is const_reference.  We do it this way because
// having the overloads for both, with one being conditional, would
// significantly expand the code surface and complexity here.  Instead we
// expect the list API to enforce the invariant, which has the added benefit
// of having improper usage fail to compile at that level rather than deep in
// the implementation.  See splice() for example.
template<typename Iterator>
class IntrusiveListImpl::IteratorOperations : AllStatic {
  using Impl = IntrusiveListImpl;
  using ListTraits = typename Iterator::ListTraits;
  using const_reference = typename ListTraits::const_reference;

  static const bool _is_forward = Iterator::_is_forward;

  static const void* make_encoded_value(const_reference value) {
    return &value;
  }

  static const void* make_encoded_value(const Entry* entry) {
    return add_tag_to_root_entry(entry);
  }

  static const Entry& resolve_to_entry(Iterator i) {
    assert_not_singular(i);
    const void* encoded = encoded_value(i);
    if (is_tagged_root_entry(encoded)) {
      return *(remove_tag_from_root_entry(encoded));
    } else {
      return get_entry(dereference_element_ptr(encoded));
    }
  }

  // Get the list element from an encoded pointer to list element.
  static const_reference dereference_element_ptr(const void* encoded_ptr) {
    return *static_cast<typename ListTraits::const_pointer>(encoded_ptr);
  }

  static Iterator next(Iterator i) {
    return Iterator(resolve_to_entry(i)._next);
  }

  static Iterator prev(Iterator i) {
    return Iterator(resolve_to_entry(i)._prev);
  }

  static Iterator next(const_reference value) {
    return Iterator(get_entry(value)._next);
  }

  static Iterator prev(const_reference value) {
    return Iterator(get_entry(value)._prev);
  }

  static void attach_impl(const_reference prev, Iterator next) {
    get_entry(prev)._next = encoded_value(next);
    resolve_to_entry(next)._prev = make_encoded_value(prev);
  }

  static void attach_impl(Iterator prev, const_reference next) {
    resolve_to_entry(prev)._next = make_encoded_value(next);
    get_entry(next)._prev = encoded_value(prev);
  }

  static void iter_attach_impl(Iterator prev, Iterator next) {
    resolve_to_entry(prev)._next = encoded_value(next);
    resolve_to_entry(next)._prev = encoded_value(prev);
  }

public:
  static const void* encoded_value(Iterator i) { return i._encoded_value; }

  static bool is_singular(Iterator i) {
    return encoded_value(i) == nullptr;
  }

  static bool is_root_entry(Iterator i) {
    return is_tagged_root_entry(encoded_value(i));
  }

  // Corresponding is_element is not used, so not provided.

  static const Entry& get_entry(const_reference v) {
    return EntryAccess<Iterator>::get_entry(v);
  }

  static const_reference dereference(Iterator i) {
    assert_not_singular(i);
    assert(!is_root_entry(i), "dereference end-of-list iterator");
    return dereference_element_ptr(encoded_value(i));
  }

  // Get the predecessor / successor (according to the iterator's
  // direction) of the argument.  Reference arguments are preferred;
  // the iterator form should only be used when the iterator is not
  // already known to be dereferenceable.  (The iterator form of
  // successor is not provided; for an iterator to have a successor,
  // the iterator must be dereferenceable.)

  static Iterator successor(const_reference value) {
    return _is_forward ? next(value) : prev(value);
  }

  static Iterator predecessor(const_reference value) {
    return _is_forward ? prev(value) : next(value);
  }

  static Iterator iter_predecessor(Iterator i) {
    return _is_forward ? prev(i) : next(i);
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
  // C++11: use rvalue references and perfect forwarding.
  template<typename PredType, typename SuccType>
  static void attach(const PredType& pred, const SuccType& succ) {
    _is_forward ? attach_impl(pred, succ) : attach_impl(succ, pred);
  }

  // Iterator to iterator attachment.
  static void iter_attach(Iterator pred, Iterator succ) {
    _is_forward ? iter_attach_impl(pred, succ) : iter_attach_impl(succ, pred);
  }

  template<typename Iterator2>
  static Iterator make_iterator(Iterator2 i) {
    return Iterator(IteratorOperations<Iterator2>::encoded_value(i));
  }

  static Iterator make_iterator_to(const_reference value) {
    return Iterator(make_encoded_value(value));
  }

  static Iterator make_begin_iterator(const Impl& impl) {
    const Entry* entry = impl.root_entry();
    return Iterator(_is_forward ? entry->_next : entry->_prev);
  }

  static Iterator make_end_iterator(const Impl& impl) {
    return Iterator(make_encoded_value(impl.root_entry()));
  }

  static void assert_not_singular(Iterator i) {
    assert(!is_singular(i), "singular iterator");
  }

  static void assert_is_in_some_list(Iterator i) {
    assert_not_singular(i);
    assert(list_ptr(i) != nullptr,
           "Invalid iterator " PTR_FORMAT, p2i(encoded_value(i)));
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
 *
 * An iterator may be either const or non-const.  The value type of a const
 * iterator is const-qualified, and a const iterator only provides access to
 * const-qualified elements.  Similarly, a non-const iterator provides access
 * to unqualified elements.  A non-const iterator can be implicitly converted
 * to a const iterator, but not vice versa.
 */
template<typename T,
         IntrusiveListEntryAccessor<T> get_entry,
         bool is_forward>
class IntrusiveListImpl::IteratorImpl {
  friend class IntrusiveListImpl;

  static const bool _is_forward = is_forward;
  static const bool _is_const = std::is_const<T>::value;

  using Impl = IntrusiveListImpl;
  using ListTraits = Impl::ListTraits<T>;
  using IOps = Impl::IteratorOperations<IteratorImpl>;

  // Test whether From is an iterator type different from this type that can
  // be implicitly converted to this iterator type.  A const iterator type
  // supports implicit conversion from the corresponding non-const iterator
  // type.
  template<typename From>
  static constexpr bool is_convertible_iterator() {
    using NonConst = IteratorImpl<std::remove_const_t<T>, get_entry, _is_forward>;
    return _is_const && std::is_same<From, NonConst>::value;
  }

public:
  /** Type of an iterator's value. */
  using value_type = typename ListTraits::value_type;

  /** Type of a reference to an iterator's value. */
  using reference = typename ListTraits::reference;

  /** Type of a pointer to an iterator's value. */
  using pointer = typename ListTraits::pointer;

  /** Type for distance between iterators. */
  using difference_type = typename ListTraits::difference_type;

  // TODO: We don't have access to <iterator>, so we can't provide the
  // iterator_category type.  Maybe someday...
  // using iterator_category = std::bidirectional_iterator_tag;

  /** Construct a singular iterator. */
  IteratorImpl() : _encoded_value(nullptr) {}

  ~IteratorImpl() = default;
  IteratorImpl(const IteratorImpl&) = default;
  IteratorImpl& operator=(const IteratorImpl&) = default;

  // Implicit conversion from non-const to const element type.
  template<typename From, ENABLE_IF(is_convertible_iterator<From>())>
  IteratorImpl(const From& other)
    : _encoded_value(IteratorOperations<From>::encoded_value(other))
  {}

  template<typename From, ENABLE_IF(is_convertible_iterator<From>())>
  IteratorImpl& operator=(const From& other) {
    return *this = IteratorImpl(other);
  }

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
    assert(!IOps::is_root_entry(*this), "iterator decrement underflow");
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
   * complexity: constant.
   */
  bool operator==(const IteratorImpl& other) const {
    IOps::assert_is_in_some_list(*this);
    IOps::assert_is_in_some_list(other);
    return IOps::encoded_value(*this) == IOps::encoded_value(other);
  }

  /**
   * Return true if this and other are not ==.
   *
   * precondition: this and other are both dereferenceable or end-of-list.
   * complexity: constant.
   */
  bool operator!=(const IteratorImpl& other) const {
    return !(*this == other);
  }

  // Add ConvertibleFrom OP IteratorImpl overloads, because these are
  // not handled by the corresponding member function plus implicit
  // conversions.  For example, const_iterator == iterator is handled
  // by const_iterator::operator==(const_iterator) plus implicit
  // conversion of iterator to const_iterator.  But we need an
  // additional overload to handle iterator == const_iterator.

  template<typename From, ENABLE_IF(is_convertible_iterator<From>())>
  friend bool operator==(const From& lhs, const IteratorImpl& rhs) {
    return rhs == lhs;
  }

  template<typename From, ENABLE_IF(is_convertible_iterator<From>())>
  friend bool operator!=(const From& lhs, const IteratorImpl& rhs) {
    return rhs != lhs;
  }

private:
  // An iterator refers to either an object in the list, the root
  // entry of the list, or null if singular.  See IteratorOperations
  // for details of the encoding.
  const void* _encoded_value;

  // Allow explicit construction from an encoded const void*
  // value.  But require exactly that type, disallowing any implicit
  // conversions.  Without that restriction, certain kinds of usage
  // errors become both more likely and harder to diagnose the
  // resulting compilation errors.  [The remaining diagnostic
  // difficulties could be eliminated by making EncodedValue a non-public
  // class for carrying the encoded void* to iterator construction.]
  template<typename EncodedValue,
           ENABLE_IF(std::is_same<EncodedValue, const void*>::value)>
  explicit IteratorImpl(EncodedValue encoded_value)
    : _encoded_value(encoded_value)
  {}
};

template<typename T,
         IntrusiveListEntryAccessor<T> get_entry,
         bool has_size,
         typename Base>
class IntrusiveList : public IntrusiveListImpl::SizeBase<has_size, Base> {
  // Give access to other instantiations, for splice().
  template<typename U, IntrusiveListEntryAccessor<U>, bool, typename>
  friend class IntrusiveList;

  // Give access for unit testing.
  friend struct IntrusiveListImpl::TestSupport;

  using Entry = IntrusiveListEntry;
  using Impl = IntrusiveListImpl;
  using ListTraits = Impl::ListTraits<T>;
  using Super = Impl::SizeBase<has_size, Base>;

  // A subsequence of one list can be transferred to another list via splice
  // if the lists have the same (ignoring const qualifiers) element type, use
  // the same entry member, and either the receiver's element type is const or
  // neither element type is const.  A const element of a list cannot be
  // transferred to a list with non-const elements.  That would effectively be
  // a quiet casting away of const.  Assuming Other is a List, these
  // constraints are equivalent to the constraints on conversion of
  // Other::iterator -> iterator.  The presence or absence of constant-time
  // size support and the base types of the lists don't affect whether
  // splicing is permitted.
  template<typename Other>
  static constexpr bool can_splice_from() {
    return Conjunction<Impl::IsListType<Other>,
                       std::is_convertible<typename Other::iterator, iterator>>::value;
  }

  template<typename Other>
  static constexpr bool can_swap() {
    return Conjunction<Impl::IsListType<Other>,
                       std::is_same<typename Other::iterator, iterator>>::value;
  }

public:
  /** Flag indicating presence of a constant-time size() operation. */
  static const bool _has_size = has_size;

  /** Type of the size of the list. */
  using size_type = typename ListTraits::size_type;

  /** The difference type for iterators. */
  using difference_type = typename ListTraits::difference_type;

  /** Type of list elements. */
  using value_type = typename ListTraits::value_type;

  /** Type of a pointer to a list element. */
  using pointer = typename ListTraits::pointer;

  /** Type of a const pointer to a list element. */
  using const_pointer = typename ListTraits::const_pointer;

  /** Type of a reference to a list element. */
  using reference = typename ListTraits::reference;

  /** Type of a const reference to a list element. */
  using const_reference = typename ListTraits::const_reference;

  /** Forward iterator type allowing modification of elements. */
  using iterator =
    Impl::IteratorImpl<T, get_entry, true>;

  /** Forward iterator type disallowing modification of elements. */
  using const_iterator =
    Impl::IteratorImpl<std::add_const_t<T>, get_entry, true>;

  /** Reverse iterator type allowing modification of elements. */
  using reverse_iterator =
    Impl::IteratorImpl<T, get_entry, false>;

  /** Reverse iterator type disallowing modification of elements. */
  using const_reverse_iterator =
    Impl::IteratorImpl<std::add_const_t<T>, get_entry, false>;

  /** Make an empty list. */
  IntrusiveList() : _impl() {}

  /**
   * Destroy the list.
   *
   * precondition: empty()
   */
  ~IntrusiveList() = default;

  NONCOPYABLE(IntrusiveList);

  /**
   * Inserts value at the front of the list.  Does not affect the
   * validity of iterators or element references for this list.
   *
   * precondition: value must not already be in a list using the same entry.
   * complexity: constant.
   */
  void push_front(reference value) {
    insert(begin(), value);
  }

  /**
   * Inserts value at the back of the list.  Does not affect the
   * validity of iterators or element references for this list.
   *
   * precondition: value must not already be in a list using the same entry.
   * complexity: constant.
   */
  void push_back(reference value) {
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
   * Returns a [const_]reference to the n'th element of the list.
   *
   * precondition: n < length()
   * complexity: O(length())
   */
  reference operator[](size_type n) {
    return nth_element(begin(), end(), n);
  }

  const_reference operator[](size_type n) const {
    return nth_element(cbegin(), cend(), n);
  }

private:

  // Implementation of operator[].
  template<typename Iterator>
  static typename Iterator::reference
  nth_element(Iterator it, Iterator end, size_type n) {
    for (size_type index = 0; true; ++it, ++index) {
      assert(it != end, "index out of bounds: %ju", uintmax_t(n));
      if (index == n) return *it;
    }
  }

public:

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
    return static_cast<size_type>(Impl::distance(cbegin(), cend()));
  }

  /**
   * Removes the element referred to by i from the list, then applies
   * the disposer, if any, to the removed element.  The list may not
   * be in a consistent state when the disposer is called.  Returns an
   * iterator for the successor of i.  Invalidates iterators referring
   * to the removed element.
   *
   * precondition: i must be a dereferenceable iterator for the list.
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
    return erase_one_and_dispose<iterator>(i, disposer);
  }

  template<typename Disposer>
  reverse_iterator erase_and_dispose(const_reverse_iterator i, Disposer disposer) {
    return erase_one_and_dispose<reverse_iterator>(i, disposer);
  }

private:

  template<typename Result, typename Iterator, typename Disposer>
  Result erase_one_and_dispose(Iterator i, Disposer disposer) {
    using IOps = Impl::IteratorOperations<Iterator>;
    assert_is_iterator(i);
    const_reference value = *i++;
    IOps::iter_attach(IOps::predecessor(value), i);
    detach(value);
    disposer(value);
    return make_iterator<Result>(i);
  }

public:

  /**
   * Removes the elements in the range designated by from and to.
   * Applies the disposer, if any, to each removed element.  The list
   * may not be in a consistent state when the disposer is called.
   * Returns an iterator referring to the end of the removed range.
   * Invalidates iterators referring to the removed elements.
   *
   * precondition: from and to must form a valid range for the list.
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
    return erase_range_and_dispose<iterator>(from, to, disposer);
  }

  template<typename Disposer>
  reverse_iterator erase_and_dispose(const_reverse_iterator from,
                                     const_reverse_iterator to,
                                     Disposer disposer) {
    return erase_range_and_dispose<reverse_iterator>(from, to, disposer);
  }

private:

  template<typename Result, typename Iterator, typename Disposer>
  Result erase_range_and_dispose(Iterator from, Iterator to, Disposer disposer) {
    using IOps = Impl::IteratorOperations<Iterator>;
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
    return make_iterator<Result>(to);
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
  iterator insert(const_iterator pos, reference value) {
    return insert_impl<iterator>(pos, value);
  }

  reverse_iterator insert(const_reverse_iterator pos, reference value) {
    return insert_impl<reverse_iterator>(pos, value);
  }

private:

  template<typename Result, typename Iterator>
  Result insert_impl(Iterator pos, reference value) {
    assert(Impl::entry_list(get_entry(value)) == nullptr, "precondition");
    assert_is_iterator(pos);
    using IOps = Impl::IteratorOperations<Iterator>;
    IOps::attach(IOps::iter_predecessor(pos), value);
    IOps::attach(value, pos);
    DEBUG_ONLY(set_list(value, &_impl);)
    adjust_size(1);
    return make_iterator_to<Result>(value);
  }

public:

  /**
   * Transfers the elements of from_list in the range designated by
   * from and to to this list, inserted before pos.  Returns an
   * iterator referring to the head of the spliced in range.  Does
   * not invalidate any iterators.
   *
   * precondition: pos must be a valid iterator for this list.
   * precondition: from and to must form a valid range for from_list.
   * precondition: pos is not in the range to transfer, i.e. either
   * - this != &from_list, or
   * - pos is reachable from to, or
   * - pos is not reachable from from.
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
  template<typename FromList, ENABLE_IF(can_splice_from<FromList>())>
  iterator splice(const_iterator pos,
                  FromList& from_list,
                  typename FromList::iterator from,
                  typename FromList::const_iterator to) {
    using Iterator = const_iterator;
    using Result = iterator;
    using IOps = Impl::IteratorOperations<Iterator>;

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
    if ((_has_size || from_list._has_size) && !is_same_list(from_list)) {
      difference_type transferring;
      if (from_list._has_size &&
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

    // to is end of non-empty range, so has a dereferenceable predecessor.
    Iterator to_pred = --Iterator(to); // Fetch before clobbered
    // from is dereferenceable since it neither follows nor equals to.
    const_reference from_value = *from;
    IOps::iter_attach(IOps::predecessor(from_value), to);
    IOps::attach(IOps::iter_predecessor(pos), from_value);
    IOps::attach(*to_pred, pos);
    return make_iterator_to<Result>(from_value);
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
  template<typename FromList, ENABLE_IF(Impl::IsListType<FromList>::value)>
  iterator splice(const_iterator pos, FromList& from_list) {
    assert(!is_same_list(from_list), "precondition");
    return splice(pos, from_list, from_list.begin(), from_list.end());
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
  template<typename FromList, ENABLE_IF(can_splice_from<FromList>())>
  iterator splice(const_iterator pos,
                  FromList& from_list,
                  typename FromList::iterator from) {
    using IOps = Impl::IteratorOperations<const_iterator>;

    assert_is_iterator(pos);
    from_list.assert_is_iterator(from);

#ifdef ASSERT
    // Transfer element to this list, or verify pos not in [from, to).
    if (is_same_list(from_list)) {
      assert(from != pos, "Splice range includes destination");
    } else {
      set_list(*from, &_impl);
    }
#endif // ASSERT

    const_reference from_value = *from;

    // Remove from_value from from_list.
    IOps::iter_attach(IOps::predecessor(from_value), IOps::successor(from_value));
    from_list.adjust_size(-1);

    // Add from_value to this list before pos.
    IOps::attach(IOps::iter_predecessor(pos), from_value);
    IOps::attach(from_value, pos);
    adjust_size(1);

    return make_iterator_to<iterator>(from_value);
  }

  /**
   * Exchange the elements of this list and other, maintaining the order of
   * the elements.  Does not invalidate any iterators.
   *
   * precondition: this and other are different lists.
   *
   * postcondition: iterators referring to elements in this list become valid
   * iterators for other, and vice versa.
   *
   * complexity: if one of the lists has constant-time size and the other does
   * not, then O(number of elements in the list without constant-time size);
   * otherwise constant (when neither or both lists have constant-time size).
   */
  template<typename OtherList, ENABLE_IF(can_swap<OtherList>())>
  void swap(OtherList& other) {
    assert(!is_same_list(other), "self-swap");
    if (!_has_size) {
      // This list does not have constant-time size. First, transfer other's
      // elements to the front of this list (a constant-time operation).  Then
      // transfer this list's original elements to other (linear time if other
      // has constant-time size, constant-time if it doesn't).
      iterator old_begin = begin();
      splice(old_begin, other);
      other.splice(other.begin(), *this, old_begin, end());
    } else if (!OtherList::_has_size) {
      // This list has constant-time size but other doesn't.  First,
      // transfer all of this list's elements to other (a constant-time
      // operation).  Then transfer other's original elements to this list
      // (linear time).
      typename OtherList::iterator other_begin = other.begin();
      other.splice(other_begin, *this);
      splice(begin(), other, other_begin, other.end());
    } else {
      // Both lists have constant-time sizes that need to be managed.  Use an
      // intermediate temporary so all transfers are of entire lists.  This
      // stays within the constant-time domain for all of the transfers.
      IntrusiveList temp{};
      temp.splice(temp.begin(), other);
      other.splice(other.begin(), *this);
      splice(begin(), temp);
    }
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
    using IOps = Impl::IteratorOperations<Iterator>;
    assert(IOps::list_ptr(i) == &_impl,
           "Iterator " PTR_FORMAT " not for this list " PTR_FORMAT,
           p2i(IOps::encoded_value(i)), p2i(this));
  }

  void assert_is_element(const_reference value) const {
    assert(Impl::entry_list(get_entry(value)) == &_impl,
           "Value " PTR_FORMAT " not in this list " PTR_FORMAT,
           p2i(&value), p2i(this));
  }

#ifdef ASSERT
  void set_list(const_reference value, Impl* list) {
    Impl::set_entry_list(get_entry(value), list);
  }
#endif

  template<typename Result, typename From>
  Result make_iterator(From i) const {
    assert_is_iterator(i);
    return Impl::IteratorOperations<Result>::make_iterator(i);
  }

  // This can break the rules about putting const elements in non-const
  // iterators or lists.  It is up to callers to ensure that doesn't happen
  // and result in implicitly casting away const of the passed argument.
  template<typename Iterator>
  Iterator make_iterator_to(const_reference value) const {
    assert_is_element(value);
    return Impl::IteratorOperations<Iterator>::make_iterator_to(value);
  }

  struct NopDisposer {
    void operator()(const_reference) const {}
  };

  void detach(const_reference value) {
    assert_is_element(value);
    Impl::detach(get_entry(value));
    adjust_size(-1);
  }
};

#endif // SHARE_UTILITIES_INTRUSIVELIST_HPP
