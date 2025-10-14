/*
 * Copyright (c) 1997, 2025, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_OPTO_TYPE_HPP
#define SHARE_OPTO_TYPE_HPP

#include "opto/adlcVMDeps.hpp"
#include "opto/compile.hpp"
#include "opto/rangeinference.hpp"
#include "runtime/handles.hpp"

// Portions of code courtesy of Clifford Click

// Optimization - Graph Style


// This class defines a Type lattice.  The lattice is used in the constant
// propagation algorithms, and for some type-checking of the iloc code.
// Basic types include RSD's (lower bound, upper bound, stride for integers),
// float & double precision constants, sets of data-labels and code-labels.
// The complete lattice is described below.  Subtypes have no relationship to
// up or down in the lattice; that is entirely determined by the behavior of
// the MEET/JOIN functions.

class Dict;
class Type;
class   TypeD;
class   TypeF;
class   TypeH;
class   TypeInteger;
class     TypeInt;
class     TypeLong;
class   TypeNarrowPtr;
class     TypeNarrowOop;
class     TypeNarrowKlass;
class   TypeAry;
class   TypeTuple;
class   TypeVect;
class     TypeVectA;
class     TypeVectS;
class     TypeVectD;
class     TypeVectX;
class     TypeVectY;
class     TypeVectZ;
class     TypeVectMask;
class   TypePtr;
class     TypeRawPtr;
class     TypeOopPtr;
class       TypeInstPtr;
class       TypeAryPtr;
class     TypeKlassPtr;
class       TypeInstKlassPtr;
class       TypeAryKlassPtr;
class     TypeMetadataPtr;
class VerifyMeet;

template <class T, class U>
class TypeIntPrototype;

//------------------------------Type-------------------------------------------
// Basic Type object, represents a set of primitive Values.
// Types are hash-cons'd into a private class dictionary, so only one of each
// different kind of Type exists.  Types are never modified after creation, so
// all their interesting fields are constant.
class Type {

public:
  enum TYPES {
    Bad=0,                      // Type check
    Control,                    // Control of code (not in lattice)
    Top,                        // Top of the lattice
    Int,                        // Integer range (lo-hi)
    Long,                       // Long integer range (lo-hi)
    Half,                       // Placeholder half of doubleword
    NarrowOop,                  // Compressed oop pointer
    NarrowKlass,                // Compressed klass pointer

    Tuple,                      // Method signature or object layout
    Array,                      // Array types

    Interfaces,                 // Set of implemented interfaces for oop types

    VectorMask,                 // Vector predicate/mask type
    VectorA,                    // (Scalable) Vector types for vector length agnostic
    VectorS,                    //  32bit Vector types
    VectorD,                    //  64bit Vector types
    VectorX,                    // 128bit Vector types
    VectorY,                    // 256bit Vector types
    VectorZ,                    // 512bit Vector types

    AnyPtr,                     // Any old raw, klass, inst, or array pointer
    RawPtr,                     // Raw (non-oop) pointers
    OopPtr,                     // Any and all Java heap entities
    InstPtr,                    // Instance pointers (non-array objects)
    AryPtr,                     // Array pointers
    // (Ptr order matters:  See is_ptr, isa_ptr, is_oopptr, isa_oopptr.)

    MetadataPtr,                // Generic metadata
    KlassPtr,                   // Klass pointers
    InstKlassPtr,
    AryKlassPtr,

    Function,                   // Function signature
    Abio,                       // Abstract I/O
    Return_Address,             // Subroutine return address
    Memory,                     // Abstract store
    HalfFloatTop,               // No float value
    HalfFloatCon,               // Floating point constant
    HalfFloatBot,               // Any float value
    FloatTop,                   // No float value
    FloatCon,                   // Floating point constant
    FloatBot,                   // Any float value
    DoubleTop,                  // No double value
    DoubleCon,                  // Double precision constant
    DoubleBot,                  // Any double value
    Bottom,                     // Bottom of lattice
    lastype                     // Bogus ending type (not in lattice)
  };

  // Signal values for offsets from a base pointer
  enum OFFSET_SIGNALS {
    OffsetTop = -2000000000,    // undefined offset
    OffsetBot = -2000000001     // any possible offset
  };

  // Min and max WIDEN values.
  enum WIDEN {
    WidenMin = 0,
    WidenMax = 3
  };

private:
  typedef struct {
    TYPES                dual_type;
    BasicType            basic_type;
    const char*          msg;
    bool                 isa_oop;
    uint                 ideal_reg;
    relocInfo::relocType reloc;
  } TypeInfo;

  // Dictionary of types shared among compilations.
  static Dict* _shared_type_dict;
  static const TypeInfo _type_info[];

  static int uhash( const Type *const t );
  // Structural equality check.  Assumes that equals() has already compared
  // the _base types and thus knows it can cast 't' appropriately.
  virtual bool eq( const Type *t ) const;

  // Top-level hash-table of types
  static Dict *type_dict() {
    return Compile::current()->type_dict();
  }

  // DUAL operation: reflect around lattice centerline.  Used instead of
  // join to ensure my lattice is symmetric up and down.  Dual is computed
  // lazily, on demand, and cached in _dual.
  const Type *_dual;            // Cached dual value


  const Type *meet_helper(const Type *t, bool include_speculative) const;
  void check_symmetrical(const Type* t, const Type* mt, const VerifyMeet& verify) const NOT_DEBUG_RETURN;

protected:
  // Each class of type is also identified by its base.
  const TYPES _base;            // Enum of Types type

  Type( TYPES t ) : _dual(nullptr),  _base(t) {} // Simple types
  // ~Type();                   // Use fast deallocation
  const Type *hashcons();       // Hash-cons the type
  virtual const Type *filter_helper(const Type *kills, bool include_speculative) const;
  const Type *join_helper(const Type *t, bool include_speculative) const {
    assert_type_verify_empty();
    return dual()->meet_helper(t->dual(), include_speculative)->dual();
  }

  void assert_type_verify_empty() const NOT_DEBUG_RETURN;

public:

  inline void* operator new( size_t x ) throw() {
    Compile* compile = Compile::current();
    compile->set_type_last_size(x);
    return compile->type_arena()->AmallocWords(x);
  }
  inline void operator delete( void* ptr ) {
    Compile* compile = Compile::current();
    compile->type_arena()->Afree(ptr,compile->type_last_size());
  }

  // Initialize the type system for a particular compilation.
  static void Initialize(Compile* compile);

  // Initialize the types shared by all compilations.
  static void Initialize_shared(Compile* compile);

  TYPES base() const {
    assert(_base > Bad && _base < lastype, "sanity");
    return _base;
  }

  // Create a new hash-consd type
  static const Type *make(enum TYPES);
  // Test for equivalence of types
  static bool equals(const Type* t1, const Type* t2);
  // Test for higher or equal in lattice
  // Variant that drops the speculative part of the types
  bool higher_equal(const Type* t) const {
    return equals(meet(t), t->remove_speculative());
  }
  // Variant that keeps the speculative part of the types
  bool higher_equal_speculative(const Type* t) const {
    return equals(meet_speculative(t), t);
  }

  // MEET operation; lower in lattice.
  // Variant that drops the speculative part of the types
  const Type *meet(const Type *t) const {
    return meet_helper(t, false);
  }
  // Variant that keeps the speculative part of the types
  const Type *meet_speculative(const Type *t) const {
    return meet_helper(t, true)->cleanup_speculative();
  }
  // WIDEN: 'widens' for Ints and other range types
  virtual const Type *widen( const Type *old, const Type* limit ) const { return this; }
  // NARROW: complement for widen, used by pessimistic phases
  virtual const Type *narrow( const Type *old ) const { return this; }

  // DUAL operation: reflect around lattice centerline.  Used instead of
  // join to ensure my lattice is symmetric up and down.
  const Type *dual() const { return _dual; }

  // Compute meet dependent on base type
  virtual const Type *xmeet( const Type *t ) const;
  virtual const Type *xdual() const;    // Compute dual right now.

  // JOIN operation; higher in lattice.  Done by finding the dual of the
  // meet of the dual of the 2 inputs.
  // Variant that drops the speculative part of the types
  const Type *join(const Type *t) const {
    return join_helper(t, false);
  }
  // Variant that keeps the speculative part of the types
  const Type *join_speculative(const Type *t) const {
    return join_helper(t, true)->cleanup_speculative();
  }

  // Modified version of JOIN adapted to the needs Node::Value.
  // Normalizes all empty values to TOP.  Does not kill _widen bits.
  // Variant that drops the speculative part of the types
  const Type *filter(const Type *kills) const {
    return filter_helper(kills, false);
  }
  // Variant that keeps the speculative part of the types
  const Type *filter_speculative(const Type *kills) const {
    return filter_helper(kills, true)->cleanup_speculative();
  }

  // Returns true if this pointer points at memory which contains a
  // compressed oop references.
  bool is_ptr_to_narrowoop() const;
  bool is_ptr_to_narrowklass() const;

  // Convenience access
  short geth() const;
  virtual float getf() const;
  double getd() const;

  // This has the same semantics as std::dynamic_cast<TypeClass*>(this)
  template <typename TypeClass>
  const TypeClass* try_cast() const;

  const TypeInt    *is_int() const;
  const TypeInt    *isa_int() const;             // Returns null if not an Int
  const TypeInteger* is_integer(BasicType bt) const;
  const TypeInteger* isa_integer(BasicType bt) const;
  const TypeLong   *is_long() const;
  const TypeLong   *isa_long() const;            // Returns null if not a Long
  const TypeD      *isa_double() const;          // Returns null if not a Double{Top,Con,Bot}
  const TypeD      *is_double_constant() const;  // Asserts it is a DoubleCon
  const TypeD      *isa_double_constant() const; // Returns null if not a DoubleCon
  const TypeH      *isa_half_float() const;          // Returns null if not a HalfFloat{Top,Con,Bot}
  const TypeH      *is_half_float_constant() const;  // Asserts it is a HalfFloatCon
  const TypeH      *isa_half_float_constant() const; // Returns null if not a HalfFloatCon
  const TypeF      *isa_float() const;           // Returns null if not a Float{Top,Con,Bot}
  const TypeF      *is_float_constant() const;   // Asserts it is a FloatCon
  const TypeF      *isa_float_constant() const;  // Returns null if not a FloatCon
  const TypeTuple  *is_tuple() const;            // Collection of fields, NOT a pointer
  const TypeAry    *is_ary() const;              // Array, NOT array pointer
  const TypeAry    *isa_ary() const;             // Returns null of not ary
  const TypeVect   *is_vect() const;             // Vector
  const TypeVect   *isa_vect() const;            // Returns null if not a Vector
  const TypeVectMask *is_vectmask() const;       // Predicate/Mask Vector
  const TypeVectMask *isa_vectmask() const;      // Returns null if not a Vector Predicate/Mask
  const TypePtr    *is_ptr() const;              // Asserts it is a ptr type
  const TypePtr    *isa_ptr() const;             // Returns null if not ptr type
  const TypeRawPtr *isa_rawptr() const;          // NOT Java oop
  const TypeRawPtr *is_rawptr() const;           // Asserts is rawptr
  const TypeNarrowOop  *is_narrowoop() const;    // Java-style GC'd pointer
  const TypeNarrowOop  *isa_narrowoop() const;   // Returns null if not oop ptr type
  const TypeNarrowKlass *is_narrowklass() const; // compressed klass pointer
  const TypeNarrowKlass *isa_narrowklass() const;// Returns null if not oop ptr type
  const TypeOopPtr   *isa_oopptr() const;        // Returns null if not oop ptr type
  const TypeOopPtr   *is_oopptr() const;         // Java-style GC'd pointer
  const TypeInstPtr  *isa_instptr() const;       // Returns null if not InstPtr
  const TypeInstPtr  *is_instptr() const;        // Instance
  const TypeAryPtr   *isa_aryptr() const;        // Returns null if not AryPtr
  const TypeAryPtr   *is_aryptr() const;         // Array oop

  template <typename TypeClass>
  const TypeClass* cast() const;

  const TypeMetadataPtr   *isa_metadataptr() const;   // Returns null if not oop ptr type
  const TypeMetadataPtr   *is_metadataptr() const;    // Java-style GC'd pointer
  const TypeKlassPtr      *isa_klassptr() const;      // Returns null if not KlassPtr
  const TypeKlassPtr      *is_klassptr() const;       // assert if not KlassPtr
  const TypeInstKlassPtr  *isa_instklassptr() const;  // Returns null if not IntKlassPtr
  const TypeInstKlassPtr  *is_instklassptr() const;   // assert if not IntKlassPtr
  const TypeAryKlassPtr   *isa_aryklassptr() const;   // Returns null if not AryKlassPtr
  const TypeAryKlassPtr   *is_aryklassptr() const;    // assert if not AryKlassPtr

  virtual bool      is_finite() const;           // Has a finite value
  virtual bool      is_nan()    const;           // Is not a number (NaN)

  // Returns this ptr type or the equivalent ptr type for this compressed pointer.
  const TypePtr* make_ptr() const;

  // Returns this oopptr type or the equivalent oopptr type for this compressed pointer.
  // Asserts if the underlying type is not an oopptr or narrowoop.
  const TypeOopPtr* make_oopptr() const;

  // Returns this compressed pointer or the equivalent compressed version
  // of this pointer type.
  const TypeNarrowOop* make_narrowoop() const;

  // Returns this compressed klass pointer or the equivalent
  // compressed version of this pointer type.
  const TypeNarrowKlass* make_narrowklass() const;

  // Special test for register pressure heuristic
  bool is_floatingpoint() const;        // True if Float or Double base type

  // Do you have memory, directly or through a tuple?
  bool has_memory( ) const;

  // TRUE if type is a singleton
  virtual bool singleton(void) const;

  // TRUE if type is above the lattice centerline, and is therefore vacuous
  virtual bool empty(void) const;

  // Return a hash for this type.  The hash function is public so ConNode
  // (constants) can hash on their constant, which is represented by a Type.
  virtual uint hash() const;

  // Map ideal registers (machine types) to ideal types
  static const Type *mreg2type[];

  // Printing, statistics
#ifndef PRODUCT
  void         dump_on(outputStream *st) const;
  void         dump() const {
    dump_on(tty);
  }
  virtual void dump2( Dict &d, uint depth, outputStream *st ) const;
  static  void dump_stats();
  // Groups of types, for debugging and visualization only.
  enum class Category {
    Data,
    Memory,
    Mixed,   // Tuples with types of different categories.
    Control,
    Other,   // {Type::Top, Type::Abio, Type::Bottom}.
    Undef    // {Type::Bad, Type::lastype}, for completeness.
  };
  // Return the category of this type.
  Category category() const;
  // Check recursively in tuples.
  bool has_category(Category cat) const;

  static const char* str(const Type* t);
#endif // !PRODUCT
  void typerr(const Type *t) const; // Mixing types error

  // Create basic type
  static const Type* get_const_basic_type(BasicType type) {
    assert((uint)type <= T_CONFLICT && _const_basic_type[type] != nullptr, "bad type");
    return _const_basic_type[type];
  }

  // For two instance arrays of same dimension, return the base element types.
  // Otherwise or if the arrays have different dimensions, return null.
  static void get_arrays_base_elements(const Type *a1, const Type *a2,
                                       const TypeInstPtr **e1, const TypeInstPtr **e2);

  // Mapping to the array element's basic type.
  BasicType array_element_basic_type() const;

  enum InterfaceHandling {
      trust_interfaces,
      ignore_interfaces
  };
  // Create standard type for a ciType:
  static const Type* get_const_type(ciType* type, InterfaceHandling interface_handling = ignore_interfaces);

  // Create standard zero value:
  static const Type* get_zero_type(BasicType type) {
    assert((uint)type <= T_CONFLICT && _zero_type[type] != nullptr, "bad type");
    return _zero_type[type];
  }

  // Report if this is a zero value (not top).
  bool is_zero_type() const {
    BasicType type = basic_type();
    if (type == T_VOID || type >= T_CONFLICT)
      return false;
    else
      return (this == _zero_type[type]);
  }

  // Convenience common pre-built types.
  static const Type *ABIO;
  static const Type *BOTTOM;
  static const Type *CONTROL;
  static const Type *DOUBLE;
  static const Type *FLOAT;
  static const Type *HALF_FLOAT;
  static const Type *HALF;
  static const Type *MEMORY;
  static const Type *MULTI;
  static const Type *RETURN_ADDRESS;
  static const Type *TOP;

  // Mapping from compiler type to VM BasicType
  BasicType basic_type() const       { return _type_info[_base].basic_type; }
  uint ideal_reg() const             { return _type_info[_base].ideal_reg; }
  const char* msg() const            { return _type_info[_base].msg; }
  bool isa_oop_ptr() const           { return _type_info[_base].isa_oop; }
  relocInfo::relocType reloc() const { return _type_info[_base].reloc; }

  // Mapping from CI type system to compiler type:
  static const Type* get_typeflow_type(ciType* type);

  static const Type* make_from_constant(ciConstant constant,
                                        bool require_constant = false,
                                        int stable_dimension = 0,
                                        bool is_narrow = false,
                                        bool is_autobox_cache = false);

  static const Type* make_constant_from_field(ciInstance* holder,
                                              int off,
                                              bool is_unsigned_load,
                                              BasicType loadbt);

  static const Type* make_constant_from_field(ciField* field,
                                              ciInstance* holder,
                                              BasicType loadbt,
                                              bool is_unsigned_load);

  static const Type* make_constant_from_array_element(ciArray* array,
                                                      int off,
                                                      int stable_dimension,
                                                      BasicType loadbt,
                                                      bool is_unsigned_load);

  // Speculative type helper methods. See TypePtr.
  virtual const TypePtr* speculative() const                                  { return nullptr; }
  virtual ciKlass* speculative_type() const                                   { return nullptr; }
  virtual ciKlass* speculative_type_not_null() const                          { return nullptr; }
  virtual bool speculative_maybe_null() const                                 { return true; }
  virtual bool speculative_always_null() const                                { return true; }
  virtual const Type* remove_speculative() const                              { return this; }
  virtual const Type* cleanup_speculative() const                             { return this; }
  virtual bool would_improve_type(ciKlass* exact_kls, int inline_depth) const { return exact_kls != nullptr; }
  virtual bool would_improve_ptr(ProfilePtrKind ptr_kind) const { return ptr_kind == ProfileAlwaysNull || ptr_kind == ProfileNeverNull; }
  const Type* maybe_remove_speculative(bool include_speculative) const;

  virtual bool maybe_null() const { return true; }
  virtual bool is_known_instance() const { return false; }

private:
  // support arrays
  static const Type*        _zero_type[T_CONFLICT+1];
  static const Type* _const_basic_type[T_CONFLICT+1];
};

//------------------------------TypeF------------------------------------------
// Class of Float-Constant Types.
class TypeF : public Type {
  TypeF( float f ) : Type(FloatCon), _f(f) {};
public:
  virtual bool eq( const Type *t ) const;
  virtual uint hash() const;             // Type specific hashing
  virtual bool singleton(void) const;    // TRUE if type is a singleton
  virtual bool empty(void) const;        // TRUE if type is vacuous
public:
  const float _f;               // Float constant

  static const TypeF *make(float f);

  virtual bool        is_finite() const;  // Has a finite value
  virtual bool        is_nan()    const;  // Is not a number (NaN)

  virtual const Type *xmeet( const Type *t ) const;
  virtual const Type *xdual() const;    // Compute dual right now.
  // Convenience common pre-built types.
  static const TypeF *MAX;
  static const TypeF *MIN;
  static const TypeF *ZERO; // positive zero only
  static const TypeF *ONE;
  static const TypeF *POS_INF;
  static const TypeF *NEG_INF;
#ifndef PRODUCT
  virtual void dump2( Dict &d, uint depth, outputStream *st ) const;
#endif
};

// Class of Half Float-Constant Types.
class TypeH : public Type {
  TypeH(short f) : Type(HalfFloatCon), _f(f) {};
public:
  virtual bool eq(const Type* t) const;
  virtual uint hash() const;             // Type specific hashing
  virtual bool singleton(void) const;    // TRUE if type is a singleton
  virtual bool empty(void) const;        // TRUE if type is vacuous
public:
  const short _f;                        // Half Float constant

  static const TypeH* make(float f);
  static const TypeH* make(short f);

  virtual bool is_finite() const;  // Has a finite value
  virtual bool is_nan() const;     // Is not a number (NaN)

  virtual float getf() const;
  virtual const Type* xmeet(const Type* t) const;
  virtual const Type* xdual() const;    // Compute dual right now.
  // Convenience common pre-built types.
  static const TypeH* MAX;
  static const TypeH* MIN;
  static const TypeH* ZERO; // positive zero only
  static const TypeH* ONE;
  static const TypeH* POS_INF;
  static const TypeH* NEG_INF;
#ifndef PRODUCT
  virtual void dump2(Dict &d, uint depth, outputStream* st) const;
#endif
};

//------------------------------TypeD------------------------------------------
// Class of Double-Constant Types.
class TypeD : public Type {
  TypeD( double d ) : Type(DoubleCon), _d(d) {};
public:
  virtual bool eq( const Type *t ) const;
  virtual uint hash() const;             // Type specific hashing
  virtual bool singleton(void) const;    // TRUE if type is a singleton
  virtual bool empty(void) const;        // TRUE if type is vacuous
public:
  const double _d;              // Double constant

  static const TypeD *make(double d);

  virtual bool        is_finite() const;  // Has a finite value
  virtual bool        is_nan()    const;  // Is not a number (NaN)

  virtual const Type *xmeet( const Type *t ) const;
  virtual const Type *xdual() const;    // Compute dual right now.
  // Convenience common pre-built types.
  static const TypeD *MAX;
  static const TypeD *MIN;
  static const TypeD *ZERO; // positive zero only
  static const TypeD *ONE;
  static const TypeD *POS_INF;
  static const TypeD *NEG_INF;
#ifndef PRODUCT
  virtual void dump2( Dict &d, uint depth, outputStream *st ) const;
#endif
};

class TypeInteger : public Type {
protected:
  TypeInteger(TYPES t, int w, bool dual) : Type(t), _is_dual(dual), _widen(w) {}

  // Denote that a set is a dual set.
  // Dual sets are only used to compute the join of 2 sets, and not used
  // outside.
  const bool _is_dual;

public:
  const short _widen;           // Limit on times we widen this sucker

  virtual jlong hi_as_long() const = 0;
  virtual jlong lo_as_long() const = 0;
  jlong get_con_as_long(BasicType bt) const;
  bool is_con() const { return lo_as_long() == hi_as_long(); }
  virtual short widen_limit() const { return _widen; }

  static const TypeInteger* make(jlong lo, jlong hi, int w, BasicType bt);
  static const TypeInteger* make(jlong con, BasicType bt);

  static const TypeInteger* bottom(BasicType type);
  static const TypeInteger* zero(BasicType type);
  static const TypeInteger* one(BasicType type);
  static const TypeInteger* minus_1(BasicType type);
};

/**
 * Definition:
 *
 * A TypeInt represents a set of non-empty jint values. A jint v is an element
 * of a TypeInt iff:
 *
 *   v >= _lo && v <= _hi &&
 *   juint(v) >= _ulo && juint(v) <= _uhi &&
 *   _bits.is_satisfied_by(v)
 *
 * Multiple sets of parameters can represent the same set.
 * E.g: consider 2 TypeInt t1, t2
 *
 * t1._lo = 2, t1._hi = 7, t1._ulo = 0, t1._uhi = 5, t1._bits._zeros = 0x00000000, t1._bits._ones = 0x1
 * t2._lo = 3, t2._hi = 5, t2._ulo = 3, t2._uhi = 5, t2._bits._zeros = 0xFFFFFFF8, t2._bits._ones = 0x1
 *
 * Then, t1 and t2 both represent the set {3, 5}. We can also see that the
 * constraints of t2 are the tightest possible. I.e there exists no TypeInt t3
 * which also represents {3, 5} such that any of these would be true:
 *
 *  1)  t3._lo  > t2._lo
 *  2)  t3._hi  < t2._hi
 *  3)  t3._ulo > t2._ulo
 *  4)  t3._uhi < t2._uhi
 *  5)  (t3._bits._zeros &~ t2._bis._zeros) != 0
 *  6)  (t3._bits._ones  &~ t2._bits._ones) != 0
 *
 * The 5-th condition mean that the subtraction of the bitsets represented by
 * t3._bits._zeros and t2._bits._zeros is not empty, which means that the
 * bits in t3._bits._zeros is not a subset of those in t2._bits._zeros, the
 * same applies to _bits._ones
 *
 * To simplify reasoning about the types in optimizations, we canonicalize
 * every TypeInt to its tightest form, already at construction. E.g a TypeInt
 * t with t._lo < 0 will definitely contain negative values. It also makes it
 * trivial to determine if a TypeInt instance is a subset of another.
 *
 * Lemmas:
 *
 * 1. Since every TypeInt instance is non-empty and canonicalized, all the
 *   bounds must also be elements of such TypeInt. Or else, we can tighten the
 *   bounds by narrowing it by one, which contradicts the assumption of the
 *   TypeInt being canonical.
 *
 * 2.
 *   2.1.  _lo <= jint(_ulo)
 *   2.2.  _lo <= _hi
 *   2.3.  _lo <= jint(_uhi)
 *   2.4.  _ulo <= juint(_lo)
 *   2.5.  _ulo <= juint(_hi)
 *   2.6.  _ulo <= _uhi
 *   2.7.  _hi >= _lo
 *   2.8.  _hi >= jint(_ulo)
 *   2.9.  _hi >= jint(_uhi)
 *   2.10. _uhi >= juint(_lo)
 *   2.11. _uhi >= _ulo
 *   2.12. _uhi >= juint(_hi)
 *
 *   Proof of lemma 2:
 *
 *   2.1. _lo <= jint(_ulo):
 *     According the lemma 1, _ulo is an element of the TypeInt, so in the
 *     signed domain, it must not be less than the smallest element of that
 *     TypeInt, which is _lo. Which means that _lo <= _ulo in the signed
 *     domain, or in a more programmatical way, _lo <= jint(_ulo).
 *   2.2. _lo <= _hi:
 *     According the lemma 1, _hi is an element of the TypeInt, so in the
 *     signed domain, it must not be less than the smallest element of that
 *     TypeInt, which is _lo. Which means that _lo <= _hi.
 *
 *   The other inequalities can be proved in a similar manner.
 *
 * 3. Given 2 jint values x, y where either both >= 0 or both < 0. Then:
 *
 *   x <= y iff juint(x) <= juint(y)
 *   I.e. x <= y in the signed domain iff x <= y in the unsigned domain
 *
 * 4. Either _lo == jint(_ulo) and _hi == jint(_uhi), or each element of a
 *   TypeInt lies in either interval [_lo, jint(_uhi)] or [jint(_ulo), _hi]
 *   (note that these intervals are disjoint in this case).
 *
 *   Proof of lemma 4:
 *
 *   For a TypeInt t, there are 3 possible cases:
 *
 *   a. t._lo >= 0, we have:
 *
 *     0 <= t_lo <= jint(t._ulo)           (lemma 2.1)
 *     juint(t._lo) <= juint(jint(t._ulo)) (lemma 3)
 *                  == t._ulo              (juint(jint(v)) == v with juint v)
 *                  <= juint(t._lo)        (lemma 2.4)
 *
 *     Which means that t._lo == jint(t._ulo).
 *
 *     Furthermore,
 *
 *     0 <= t._lo <= t._hi                 (lemma 2.2)
 *     0 <= t._lo <= jint(t._uhi)          (lemma 2.3)
 *     t._hi >= jint(t._uhi)               (lemma 2.9)
 *
 *     juint(t._hi) >= juint(jint(t._uhi)) (lemma 3)
 *                  == t._uhi              (juint(jint(v)) == v with juint v)
 *                  >= juint(t._hi)        (lemma 2.12)
 *
 *     Which means that t._hi == jint(t._uhi).
 *     In this case, t._lo == jint(t._ulo) and t._hi == jint(t._uhi)
 *
 *   b. t._hi < 0. Similarly, we can conclude that:
 *     t._lo == jint(t._ulo) and t._hi == jint(t._uhi)
 *
 *   c. t._lo < 0, t._hi >= 0.
 *
 *     Since t._ulo <= juint(t._hi) (lemma 2.5), we must have jint(t._ulo) >= 0
 *     because all negative values is larger than all non-negative values in the
 *     unsigned domain.
 *
 *     Since t._uhi >= juint(t._lo) (lemma 2.10), we must have jint(t._uhi) < 0
 *     similar to the reasoning above.
 *
 *     In this case, each element of t belongs to either [t._lo, jint(t._uhi)] or
 *     [jint(t._ulo), t._hi].
 *
 *     Below is an illustration of the TypeInt in this case, the intervals that
 *     the elements can be in are marked using the = symbol. Note how the
 *     negative range in the signed domain wrap around in the unsigned domain.
 *
 *     Signed:
 *     -----lo=========uhi---------0--------ulo==========hi-----
 *     Unsigned:
 *                                 0--------ulo==========hi----------lo=========uhi---------
 *
 *   This property is useful for our analysis of TypeInt values. Additionally,
 *   it can be seen that _lo and jint(_uhi) are both < 0 or both >= 0, and the
 *   same applies to jint(_ulo) and _hi.
 *
 *   We call [_lo, jint(_uhi)] and [jint(_ulo), _hi] "simple intervals". Then,
 *   a TypeInt consists of 2 simple intervals, each of which has its bounds
 *   being both >= 0 or both < 0. If both simple intervals lie in the same half
 *   of the integer domain, they must be the same (i.e _lo == jint(_ulo) and
 *   _hi == jint(_uhi)). Otherwise, [_lo, jint(_uhi)] must lie in the negative
 *   half and [jint(_ulo), _hi] must lie in the non-negative half of the signed
 *   domain (equivalently, [_lo, jint(_uhi)] must lie in the upper half and
 *   [jint(_ulo), _hi] must lie in the lower half of the unsigned domain).
 */
class TypeInt : public TypeInteger {
private:
  TypeInt(const TypeIntPrototype<jint, juint>& t, int w, bool dual);
  static const Type* make_or_top(const TypeIntPrototype<jint, juint>& t, int widen, bool dual);

  friend class TypeIntHelper;

protected:
  virtual const Type* filter_helper(const Type* kills, bool include_speculative) const;

public:
  typedef jint NativeType;
  virtual bool eq(const Type* t) const;
  virtual uint hash() const;             // Type specific hashing
  virtual bool singleton(void) const;    // TRUE if type is a singleton
  virtual bool empty(void) const;        // TRUE if type is vacuous
  // A value is in the set represented by this TypeInt if it satisfies all
  // the below constraints, see contains(jint)
  const jint _lo, _hi;       // Lower bound, upper bound in the signed domain
  const juint _ulo, _uhi;    // Lower bound, upper bound in the unsigned domain
  const KnownBits<juint> _bits;

  static const TypeInt* make(jint con);
  // must always specify w
  static const TypeInt* make(jint lo, jint hi, int widen);
  static const Type* make_or_top(const TypeIntPrototype<jint, juint>& t, int widen);

  // Check for single integer
  bool is_con() const { return _lo == _hi; }
  bool is_con(jint i) const { return is_con() && _lo == i; }
  jint get_con() const { assert(is_con(), "");  return _lo; }
  // Check if a jint/TypeInt is a subset of this TypeInt (i.e. all elements of the
  // argument are also elements of this type)
  bool contains(jint i) const;
  bool contains(const TypeInt* t) const;

  virtual bool is_finite() const;  // Has a finite value

  virtual const Type* xmeet(const Type* t) const;
  virtual const Type* xdual() const;    // Compute dual right now.
  virtual const Type* widen(const Type* t, const Type* limit_type) const;
  virtual const Type* narrow(const Type* t) const;

  virtual jlong hi_as_long() const { return _hi; }
  virtual jlong lo_as_long() const { return _lo; }

  // Do not kill _widen bits.
  // Convenience common pre-built types.
  static const TypeInt* MAX;
  static const TypeInt* MIN;
  static const TypeInt* MINUS_1;
  static const TypeInt* ZERO;
  static const TypeInt* ONE;
  static const TypeInt* BOOL;
  static const TypeInt* CC;
  static const TypeInt* CC_LT;  // [-1]  == MINUS_1
  static const TypeInt* CC_GT;  // [1]   == ONE
  static const TypeInt* CC_EQ;  // [0]   == ZERO
  static const TypeInt* CC_NE;  // [-1, 1]
  static const TypeInt* CC_LE;  // [-1,0]
  static const TypeInt* CC_GE;  // [0,1] == BOOL (!)
  static const TypeInt* BYTE;
  static const TypeInt* UBYTE;
  static const TypeInt* CHAR;
  static const TypeInt* SHORT;
  static const TypeInt* NON_ZERO;
  static const TypeInt* POS;
  static const TypeInt* POS1;
  static const TypeInt* INT;
  static const TypeInt* SYMINT; // symmetric range [-max_jint..max_jint]
  static const TypeInt* TYPE_DOMAIN; // alias for TypeInt::INT

  static const TypeInt* as_self(const Type* t) { return t->is_int(); }
#ifndef PRODUCT
  virtual void dump2(Dict& d, uint depth, outputStream* st) const;
  void dump_verbose() const;
#endif
};

// Similar to TypeInt
class TypeLong : public TypeInteger {
private:
  TypeLong(const TypeIntPrototype<jlong, julong>& t, int w, bool dual);
  static const Type* make_or_top(const TypeIntPrototype<jlong, julong>& t, int widen, bool dual);

  friend class TypeIntHelper;

protected:
  // Do not kill _widen bits.
  virtual const Type* filter_helper(const Type* kills, bool include_speculative) const;
public:
  typedef jlong NativeType;
  virtual bool eq( const Type *t ) const;
  virtual uint hash() const;             // Type specific hashing
  virtual bool singleton(void) const;    // TRUE if type is a singleton
  virtual bool empty(void) const;        // TRUE if type is vacuous
public:
  // A value is in the set represented by this TypeLong if it satisfies all
  // the below constraints, see contains(jlong)
  const jlong _lo, _hi;       // Lower bound, upper bound in the signed domain
  const julong _ulo, _uhi;    // Lower bound, upper bound in the unsigned domain
  const KnownBits<julong> _bits;

  static const TypeLong* make(jlong con);
  // must always specify w
  static const TypeLong* make(jlong lo, jlong hi, int widen);
  static const Type* make_or_top(const TypeIntPrototype<jlong, julong>& t, int widen);

  // Check for single integer
  bool is_con() const { return _lo == _hi; }
  bool is_con(jlong i) const { return is_con() && _lo == i; }
  jlong get_con() const { assert(is_con(), "" ); return _lo; }
  // Check if a jlong/TypeLong is a subset of this TypeLong (i.e. all elements of the
  // argument are also elements of this type)
  bool contains(jlong i) const;
  bool contains(const TypeLong* t) const;

  // Check for positive 32-bit value.
  int is_positive_int() const { return _lo >= 0 && _hi <= (jlong)max_jint; }

  virtual bool        is_finite() const;  // Has a finite value

  virtual jlong hi_as_long() const { return _hi; }
  virtual jlong lo_as_long() const { return _lo; }

  virtual const Type* xmeet(const Type* t) const;
  virtual const Type* xdual() const;    // Compute dual right now.
  virtual const Type* widen(const Type* t, const Type* limit_type) const;
  virtual const Type* narrow(const Type* t) const;
  // Convenience common pre-built types.
  static const TypeLong* MAX;
  static const TypeLong* MIN;
  static const TypeLong* MINUS_1;
  static const TypeLong* ZERO;
  static const TypeLong* ONE;
  static const TypeLong* NON_ZERO;
  static const TypeLong* POS;
  static const TypeLong* NEG;
  static const TypeLong* LONG;
  static const TypeLong* INT;    // 32-bit subrange [min_jint..max_jint]
  static const TypeLong* UINT;   // 32-bit unsigned [0..max_juint]
  static const TypeLong* TYPE_DOMAIN; // alias for TypeLong::LONG

  // static convenience methods.
  static const TypeLong* as_self(const Type* t) { return t->is_long(); }

#ifndef PRODUCT
  virtual void dump2(Dict& d, uint, outputStream* st) const;// Specialized per-Type dumping
  void dump_verbose() const;
#endif
};

//------------------------------TypeTuple--------------------------------------
// Class of Tuple Types, essentially type collections for function signatures
// and class layouts.  It happens to also be a fast cache for the HotSpot
// signature types.
class TypeTuple : public Type {
  TypeTuple( uint cnt, const Type **fields ) : Type(Tuple), _cnt(cnt), _fields(fields) { }

  const uint          _cnt;              // Count of fields
  const Type ** const _fields;           // Array of field types

public:
  virtual bool eq( const Type *t ) const;
  virtual uint hash() const;             // Type specific hashing
  virtual bool singleton(void) const;    // TRUE if type is a singleton
  virtual bool empty(void) const;        // TRUE if type is vacuous

  // Accessors:
  uint cnt() const { return _cnt; }
  const Type* field_at(uint i) const {
    assert(i < _cnt, "oob");
    return _fields[i];
  }
  void set_field_at(uint i, const Type* t) {
    assert(i < _cnt, "oob");
    _fields[i] = t;
  }

  static const TypeTuple *make( uint cnt, const Type **fields );
  static const TypeTuple *make_range(ciSignature *sig, InterfaceHandling interface_handling = ignore_interfaces);
  static const TypeTuple *make_domain(ciInstanceKlass* recv, ciSignature *sig, InterfaceHandling interface_handling);

  // Subroutine call type with space allocated for argument types
  // Memory for Control, I_O, Memory, FramePtr, and ReturnAdr is allocated implicitly
  static const Type **fields( uint arg_cnt );

  virtual const Type *xmeet( const Type *t ) const;
  virtual const Type *xdual() const;    // Compute dual right now.
  // Convenience common pre-built types.
  static const TypeTuple *IFBOTH;
  static const TypeTuple *IFFALSE;
  static const TypeTuple *IFTRUE;
  static const TypeTuple *IFNEITHER;
  static const TypeTuple *LOOPBODY;
  static const TypeTuple *MEMBAR;
  static const TypeTuple *STORECONDITIONAL;
  static const TypeTuple *START_I2C;
  static const TypeTuple *INT_PAIR;
  static const TypeTuple *LONG_PAIR;
  static const TypeTuple *INT_CC_PAIR;
  static const TypeTuple *LONG_CC_PAIR;
#ifndef PRODUCT
  virtual void dump2( Dict &d, uint, outputStream *st  ) const; // Specialized per-Type dumping
#endif
};

//------------------------------TypeAry----------------------------------------
// Class of Array Types
class TypeAry : public Type {
  TypeAry(const Type* elem, const TypeInt* size, bool stable) : Type(Array),
      _elem(elem), _size(size), _stable(stable) {}
public:
  virtual bool eq( const Type *t ) const;
  virtual uint hash() const;             // Type specific hashing
  virtual bool singleton(void) const;    // TRUE if type is a singleton
  virtual bool empty(void) const;        // TRUE if type is vacuous

private:
  const Type *_elem;            // Element type of array
  const TypeInt *_size;         // Elements in array
  const bool _stable;           // Are elements @Stable?
  friend class TypeAryPtr;

public:
  static const TypeAry* make(const Type* elem, const TypeInt* size, bool stable = false);

  virtual const Type *xmeet( const Type *t ) const;
  virtual const Type *xdual() const;    // Compute dual right now.
  bool ary_must_be_exact() const;  // true if arrays of such are never generic
  virtual const TypeAry* remove_speculative() const;
  virtual const Type* cleanup_speculative() const;
#ifndef PRODUCT
  virtual void dump2( Dict &d, uint, outputStream *st  ) const; // Specialized per-Type dumping
#endif
};

//------------------------------TypeVect---------------------------------------
// Class of Vector Types
class TypeVect : public Type {
  const BasicType _elem_bt;  // Vector's element type
  const uint _length;  // Elements in vector (power of 2)

protected:
  TypeVect(TYPES t, BasicType elem_bt, uint length) : Type(t),
    _elem_bt(elem_bt), _length(length) {}

public:
  BasicType element_basic_type() const { return _elem_bt; }
  uint length() const { return _length; }
  uint length_in_bytes() const {
    return _length * type2aelembytes(element_basic_type());
  }

  virtual bool eq(const Type* t) const;
  virtual uint hash() const;             // Type specific hashing
  virtual bool singleton(void) const;    // TRUE if type is a singleton
  virtual bool empty(void) const;        // TRUE if type is vacuous

  static const TypeVect* make(const BasicType elem_bt, uint length, bool is_mask = false);
  static const TypeVect* makemask(const BasicType elem_bt, uint length);

  virtual const Type* xmeet( const Type *t) const;
  virtual const Type* xdual() const;     // Compute dual right now.

  static const TypeVect* VECTA;
  static const TypeVect* VECTS;
  static const TypeVect* VECTD;
  static const TypeVect* VECTX;
  static const TypeVect* VECTY;
  static const TypeVect* VECTZ;
  static const TypeVect* VECTMASK;

#ifndef PRODUCT
  virtual void dump2(Dict& d, uint, outputStream* st) const; // Specialized per-Type dumping
#endif
};

class TypeVectA : public TypeVect {
  friend class TypeVect;
  TypeVectA(BasicType elem_bt, uint length) : TypeVect(VectorA, elem_bt, length) {}
};

class TypeVectS : public TypeVect {
  friend class TypeVect;
  TypeVectS(BasicType elem_bt, uint length) : TypeVect(VectorS, elem_bt, length) {}
};

class TypeVectD : public TypeVect {
  friend class TypeVect;
  TypeVectD(BasicType elem_bt, uint length) : TypeVect(VectorD, elem_bt, length) {}
};

class TypeVectX : public TypeVect {
  friend class TypeVect;
  TypeVectX(BasicType elem_bt, uint length) : TypeVect(VectorX, elem_bt, length) {}
};

class TypeVectY : public TypeVect {
  friend class TypeVect;
  TypeVectY(BasicType elem_bt, uint length) : TypeVect(VectorY, elem_bt, length) {}
};

class TypeVectZ : public TypeVect {
  friend class TypeVect;
  TypeVectZ(BasicType elem_bt, uint length) : TypeVect(VectorZ, elem_bt, length) {}
};

class TypeVectMask : public TypeVect {
public:
  friend class TypeVect;
  TypeVectMask(BasicType elem_bt, uint length) : TypeVect(VectorMask, elem_bt, length) {}
  static const TypeVectMask* make(const BasicType elem_bt, uint length);
};

// Set of implemented interfaces. Referenced from TypeOopPtr and TypeKlassPtr.
class TypeInterfaces : public Type {
private:
  GrowableArrayFromArray<ciInstanceKlass*> _interfaces;
  uint _hash;
  ciInstanceKlass* _exact_klass;
  DEBUG_ONLY(bool _initialized;)

  void initialize();

  void verify() const NOT_DEBUG_RETURN;
  void compute_hash();
  void compute_exact_klass();

  TypeInterfaces(ciInstanceKlass** interfaces_base, int nb_interfaces);

  NONCOPYABLE(TypeInterfaces);
public:
  static const TypeInterfaces* make(GrowableArray<ciInstanceKlass*>* interfaces = nullptr);
  bool eq(const Type* other) const;
  bool eq(ciInstanceKlass* k) const;
  uint hash() const;
  const Type *xdual() const;
  void dump(outputStream* st) const;
  const TypeInterfaces* union_with(const TypeInterfaces* other) const;
  const TypeInterfaces* intersection_with(const TypeInterfaces* other) const;
  bool contains(const TypeInterfaces* other) const {
    return intersection_with(other)->eq(other);
  }
  bool empty() const { return _interfaces.length() == 0; }

  ciInstanceKlass* exact_klass() const;
  void verify_is_loaded() const NOT_DEBUG_RETURN;

  static int compare(ciInstanceKlass* const& k1, ciInstanceKlass* const& k2);
  static int compare(ciInstanceKlass** k1, ciInstanceKlass** k2);

  const Type* xmeet(const Type* t) const;

  bool singleton(void) const;
  bool has_non_array_interface() const;
};

//------------------------------TypePtr----------------------------------------
// Class of machine Pointer Types: raw data, instances or arrays.
// If the _base enum is AnyPtr, then this refers to all of the above.
// Otherwise the _base will indicate which subset of pointers is affected,
// and the class will be inherited from.
class TypePtr : public Type {
  friend class TypeNarrowPtr;
  friend class Type;
protected:
  static const TypeInterfaces* interfaces(ciKlass*& k, bool klass, bool interface, bool array, InterfaceHandling interface_handling);

public:
  enum PTR { TopPTR, AnyNull, Constant, Null, NotNull, BotPTR, lastPTR };
protected:
  TypePtr(TYPES t, PTR ptr, int offset,
          const TypePtr* speculative = nullptr,
          int inline_depth = InlineDepthBottom) :
    Type(t), _speculative(speculative), _inline_depth(inline_depth), _offset(offset),
    _ptr(ptr) {}
  static const PTR ptr_meet[lastPTR][lastPTR];
  static const PTR ptr_dual[lastPTR];
  static const char * const ptr_msg[lastPTR];

  enum {
    InlineDepthBottom = INT_MAX,
    InlineDepthTop = -InlineDepthBottom
  };

  // Extra type information profiling gave us. We propagate it the
  // same way the rest of the type info is propagated. If we want to
  // use it, then we have to emit a guard: this part of the type is
  // not something we know but something we speculate about the type.
  const TypePtr*   _speculative;
  // For speculative types, we record at what inlining depth the
  // profiling point that provided the data is. We want to favor
  // profile data coming from outer scopes which are likely better for
  // the current compilation.
  int _inline_depth;

  // utility methods to work on the speculative part of the type
  const TypePtr* dual_speculative() const;
  const TypePtr* xmeet_speculative(const TypePtr* other) const;
  bool eq_speculative(const TypePtr* other) const;
  int hash_speculative() const;
  const TypePtr* add_offset_speculative(intptr_t offset) const;
  const TypePtr* with_offset_speculative(intptr_t offset) const;
#ifndef PRODUCT
  void dump_speculative(outputStream *st) const;
#endif

  // utility methods to work on the inline depth of the type
  int dual_inline_depth() const;
  int meet_inline_depth(int depth) const;
#ifndef PRODUCT
  void dump_inline_depth(outputStream *st) const;
#endif

  // TypeInstPtr (TypeAryPtr resp.) and TypeInstKlassPtr (TypeAryKlassPtr resp.) implement very similar meet logic.
  // The logic for meeting 2 instances (2 arrays resp.) is shared in the 2 utility methods below. However the logic for
  // the oop and klass versions can be slightly different and extra logic may have to be executed depending on what
  // exact case the meet falls into. The MeetResult struct is used by the utility methods to communicate what case was
  // encountered so the right logic specific to klasses or oops can be executed.,
  enum MeetResult {
    QUICK,
    UNLOADED,
    SUBTYPE,
    NOT_SUBTYPE,
    LCA
  };
  template<class T> static TypePtr::MeetResult meet_instptr(PTR& ptr, const TypeInterfaces*& interfaces, const T* this_type,
                                                            const T* other_type, ciKlass*& res_klass, bool& res_xk);

  template<class T> static MeetResult meet_aryptr(PTR& ptr, const Type*& elem, const T* this_ary, const T* other_ary,
                                                  ciKlass*& res_klass, bool& res_xk);

  template <class T1, class T2> static bool is_java_subtype_of_helper_for_instance(const T1* this_one, const T2* other, bool this_exact, bool other_exact);
  template <class T1, class T2> static bool is_same_java_type_as_helper_for_instance(const T1* this_one, const T2* other);
  template <class T1, class T2> static bool maybe_java_subtype_of_helper_for_instance(const T1* this_one, const T2* other, bool this_exact, bool other_exact);
  template <class T1, class T2> static bool is_java_subtype_of_helper_for_array(const T1* this_one, const T2* other, bool this_exact, bool other_exact);
  template <class T1, class T2> static bool is_same_java_type_as_helper_for_array(const T1* this_one, const T2* other);
  template <class T1, class T2> static bool maybe_java_subtype_of_helper_for_array(const T1* this_one, const T2* other, bool this_exact, bool other_exact);
  template <class T1, class T2> static bool is_meet_subtype_of_helper_for_instance(const T1* this_one, const T2* other, bool this_xk, bool other_xk);
  template <class T1, class T2> static bool is_meet_subtype_of_helper_for_array(const T1* this_one, const T2* other, bool this_xk, bool other_xk);
public:
  const int _offset;            // Offset into oop, with TOP & BOT
  const PTR _ptr;               // Pointer equivalence class

  int offset() const { return _offset; }
  PTR ptr()    const { return _ptr; }

  static const TypePtr *make(TYPES t, PTR ptr, int offset,
                             const TypePtr* speculative = nullptr,
                             int inline_depth = InlineDepthBottom);

  // Return a 'ptr' version of this type
  virtual const TypePtr* cast_to_ptr_type(PTR ptr) const;

  virtual intptr_t get_con() const;

  int xadd_offset( intptr_t offset ) const;
  virtual const TypePtr* add_offset(intptr_t offset) const;
  virtual const TypePtr* with_offset(intptr_t offset) const;
  virtual bool eq(const Type *t) const;
  virtual uint hash() const;             // Type specific hashing

  virtual bool singleton(void) const;    // TRUE if type is a singleton
  virtual bool empty(void) const;        // TRUE if type is vacuous
  virtual const Type *xmeet( const Type *t ) const;
  virtual const Type *xmeet_helper( const Type *t ) const;
  int meet_offset( int offset ) const;
  int dual_offset( ) const;
  virtual const Type *xdual() const;    // Compute dual right now.

  // meet, dual and join over pointer equivalence sets
  PTR meet_ptr( const PTR in_ptr ) const { return ptr_meet[in_ptr][ptr()]; }
  PTR dual_ptr()                   const { return ptr_dual[ptr()];      }

  // This is textually confusing unless one recalls that
  // join(t) == dual()->meet(t->dual())->dual().
  PTR join_ptr( const PTR in_ptr ) const {
    return ptr_dual[ ptr_meet[ ptr_dual[in_ptr] ] [ dual_ptr() ] ];
  }

  // Speculative type helper methods.
  virtual const TypePtr* speculative() const { return _speculative; }
  int inline_depth() const                   { return _inline_depth; }
  virtual ciKlass* speculative_type() const;
  virtual ciKlass* speculative_type_not_null() const;
  virtual bool speculative_maybe_null() const;
  virtual bool speculative_always_null() const;
  virtual const TypePtr* remove_speculative() const;
  virtual const Type* cleanup_speculative() const;
  virtual bool would_improve_type(ciKlass* exact_kls, int inline_depth) const;
  virtual bool would_improve_ptr(ProfilePtrKind maybe_null) const;
  virtual const TypePtr* with_inline_depth(int depth) const;

  virtual bool maybe_null() const { return meet_ptr(Null) == ptr(); }

  // Tests for relation to centerline of type lattice:
  static bool above_centerline(PTR ptr) { return (ptr <= AnyNull); }
  static bool below_centerline(PTR ptr) { return (ptr >= NotNull); }
  // Convenience common pre-built types.
  static const TypePtr *NULL_PTR;
  static const TypePtr *NOTNULL;
  static const TypePtr *BOTTOM;
#ifndef PRODUCT
  virtual void dump2( Dict &d, uint depth, outputStream *st  ) const;
#endif
};

//------------------------------TypeRawPtr-------------------------------------
// Class of raw pointers, pointers to things other than Oops.  Examples
// include the stack pointer, top of heap, card-marking area, handles, etc.
class TypeRawPtr : public TypePtr {
protected:
  TypeRawPtr( PTR ptr, address bits ) : TypePtr(RawPtr,ptr,0), _bits(bits){}
public:
  virtual bool eq( const Type *t ) const;
  virtual uint hash() const;    // Type specific hashing

  const address _bits;          // Constant value, if applicable

  static const TypeRawPtr *make( PTR ptr );
  static const TypeRawPtr *make( address bits );

  // Return a 'ptr' version of this type
  virtual const TypeRawPtr* cast_to_ptr_type(PTR ptr) const;

  virtual intptr_t get_con() const;

  virtual const TypePtr* add_offset(intptr_t offset) const;
  virtual const TypeRawPtr* with_offset(intptr_t offset) const { ShouldNotReachHere(); return nullptr;}

  virtual const Type *xmeet( const Type *t ) const;
  virtual const Type *xdual() const;    // Compute dual right now.
  // Convenience common pre-built types.
  static const TypeRawPtr *BOTTOM;
  static const TypeRawPtr *NOTNULL;
#ifndef PRODUCT
  virtual void dump2( Dict &d, uint depth, outputStream *st  ) const;
#endif
};

//------------------------------TypeOopPtr-------------------------------------
// Some kind of oop (Java pointer), either instance or array.
class TypeOopPtr : public TypePtr {
  friend class TypeAry;
  friend class TypePtr;
  friend class TypeInstPtr;
  friend class TypeAryPtr;
protected:
 TypeOopPtr(TYPES t, PTR ptr, ciKlass* k, const TypeInterfaces* interfaces, bool xk, ciObject* o, int offset, int instance_id,
            const TypePtr* speculative, int inline_depth);
public:
  virtual bool eq( const Type *t ) const;
  virtual uint hash() const;             // Type specific hashing
  virtual bool singleton(void) const;    // TRUE if type is a singleton
  enum {
   InstanceTop = -1,   // undefined instance
   InstanceBot = 0     // any possible instance
  };
protected:

  // Oop is null, unless this is a constant oop.
  ciObject*     _const_oop;   // Constant oop
  // If _klass is null, then so is _sig.  This is an unloaded klass.
  ciKlass*      _klass;       // Klass object

  const TypeInterfaces* _interfaces;

  // Does the type exclude subclasses of the klass?  (Inexact == polymorphic.)
  bool          _klass_is_exact;
  bool          _is_ptr_to_narrowoop;
  bool          _is_ptr_to_narrowklass;
  bool          _is_ptr_to_boxed_value;

  // If not InstanceTop or InstanceBot, indicates that this is
  // a particular instance of this type which is distinct.
  // This is the node index of the allocation node creating this instance.
  int           _instance_id;

  static const TypeOopPtr* make_from_klass_common(ciKlass* klass, bool klass_change, bool try_for_exact, InterfaceHandling interface_handling);

  int dual_instance_id() const;
  int meet_instance_id(int uid) const;

  const TypeInterfaces* meet_interfaces(const TypeOopPtr* other) const;

  // Do not allow interface-vs.-noninterface joins to collapse to top.
  virtual const Type *filter_helper(const Type *kills, bool include_speculative) const;

  virtual ciKlass* exact_klass_helper() const { return nullptr; }
  virtual ciKlass* klass() const { return _klass;     }

public:

  bool is_java_subtype_of(const TypeOopPtr* other) const {
    return is_java_subtype_of_helper(other, klass_is_exact(), other->klass_is_exact());
  }

  bool is_same_java_type_as(const TypePtr* other) const {
    return is_same_java_type_as_helper(other->is_oopptr());
  }

  virtual bool is_same_java_type_as_helper(const TypeOopPtr* other) const {
    ShouldNotReachHere(); return false;
  }

  bool maybe_java_subtype_of(const TypeOopPtr* other) const {
    return maybe_java_subtype_of_helper(other, klass_is_exact(), other->klass_is_exact());
  }
  virtual bool is_java_subtype_of_helper(const TypeOopPtr* other, bool this_exact, bool other_exact) const { ShouldNotReachHere(); return false; }
  virtual bool maybe_java_subtype_of_helper(const TypeOopPtr* other, bool this_exact, bool other_exact) const { ShouldNotReachHere(); return false; }


  // Creates a type given a klass. Correctly handles multi-dimensional arrays
  // Respects UseUniqueSubclasses.
  // If the klass is final, the resulting type will be exact.
  static const TypeOopPtr* make_from_klass(ciKlass* klass, InterfaceHandling interface_handling = ignore_interfaces) {
    return make_from_klass_common(klass, true, false, interface_handling);
  }
  // Same as before, but will produce an exact type, even if
  // the klass is not final, as long as it has exactly one implementation.
  static const TypeOopPtr* make_from_klass_unique(ciKlass* klass, InterfaceHandling interface_handling= ignore_interfaces) {
    return make_from_klass_common(klass, true, true, interface_handling);
  }
  // Same as before, but does not respects UseUniqueSubclasses.
  // Use this only for creating array element types.
  static const TypeOopPtr* make_from_klass_raw(ciKlass* klass, InterfaceHandling interface_handling = ignore_interfaces) {
    return make_from_klass_common(klass, false, false, interface_handling);
  }
  // Creates a singleton type given an object.
  // If the object cannot be rendered as a constant,
  // may return a non-singleton type.
  // If require_constant, produce a null if a singleton is not possible.
  static const TypeOopPtr* make_from_constant(ciObject* o,
                                              bool require_constant = false);

  // Make a generic (unclassed) pointer to an oop.
  static const TypeOopPtr* make(PTR ptr, int offset, int instance_id,
                                const TypePtr* speculative = nullptr,
                                int inline_depth = InlineDepthBottom);

  ciObject* const_oop()    const { return _const_oop; }
  // Exact klass, possibly an interface or an array of interface
  ciKlass* exact_klass(bool maybe_null = false) const { assert(klass_is_exact(), ""); ciKlass* k = exact_klass_helper(); assert(k != nullptr || maybe_null, ""); return k;  }
  ciKlass* unloaded_klass() const { assert(!is_loaded(), "only for unloaded types"); return klass(); }

  virtual bool  is_loaded() const { return klass()->is_loaded(); }
  virtual bool klass_is_exact()    const { return _klass_is_exact; }

  // Returns true if this pointer points at memory which contains a
  // compressed oop references.
  bool is_ptr_to_narrowoop_nv() const { return _is_ptr_to_narrowoop; }
  bool is_ptr_to_narrowklass_nv() const { return _is_ptr_to_narrowklass; }
  bool is_ptr_to_boxed_value()   const { return _is_ptr_to_boxed_value; }
  bool is_known_instance()       const { return _instance_id > 0; }
  int  instance_id()             const { return _instance_id; }
  bool is_known_instance_field() const { return is_known_instance() && _offset >= 0; }

  virtual intptr_t get_con() const;

  virtual const TypeOopPtr* cast_to_ptr_type(PTR ptr) const;

  virtual const TypeOopPtr* cast_to_exactness(bool klass_is_exact) const;

  virtual const TypeOopPtr *cast_to_instance_id(int instance_id) const;

  // corresponding pointer to klass, for a given instance
  virtual const TypeKlassPtr* as_klass_type(bool try_for_exact = false) const;

  virtual const TypeOopPtr* with_offset(intptr_t offset) const;
  virtual const TypePtr* add_offset(intptr_t offset) const;

  // Speculative type helper methods.
  virtual const TypeOopPtr* remove_speculative() const;
  virtual const Type* cleanup_speculative() const;
  virtual bool would_improve_type(ciKlass* exact_kls, int inline_depth) const;
  virtual const TypePtr* with_inline_depth(int depth) const;

  virtual const TypePtr* with_instance_id(int instance_id) const;

  virtual const Type *xdual() const;    // Compute dual right now.
  // the core of the computation of the meet for TypeOopPtr and for its subclasses
  virtual const Type *xmeet_helper(const Type *t) const;

  // Convenience common pre-built type.
  static const TypeOopPtr *BOTTOM;
#ifndef PRODUCT
  virtual void dump2( Dict &d, uint depth, outputStream *st ) const;
#endif
private:
  virtual bool is_meet_subtype_of(const TypePtr* other) const {
    return is_meet_subtype_of_helper(other->is_oopptr(), klass_is_exact(), other->is_oopptr()->klass_is_exact());
  }

  virtual bool is_meet_subtype_of_helper(const TypeOopPtr* other, bool this_xk, bool other_xk) const {
    ShouldNotReachHere(); return false;
  }

  virtual const TypeInterfaces* interfaces() const {
    return _interfaces;
  };

  const TypeOopPtr* is_reference_type(const Type* other) const {
    return other->isa_oopptr();
  }

  const TypeAryPtr* is_array_type(const TypeOopPtr* other) const {
    return other->isa_aryptr();
  }

  const TypeInstPtr* is_instance_type(const TypeOopPtr* other) const {
    return other->isa_instptr();
  }
};

//------------------------------TypeInstPtr------------------------------------
// Class of Java object pointers, pointing either to non-array Java instances
// or to a Klass* (including array klasses).
class TypeInstPtr : public TypeOopPtr {
  TypeInstPtr(PTR ptr, ciKlass* k, const TypeInterfaces* interfaces, bool xk, ciObject* o, int off, int instance_id,
              const TypePtr* speculative, int inline_depth);
  virtual bool eq( const Type *t ) const;
  virtual uint hash() const;             // Type specific hashing

  ciKlass* exact_klass_helper() const;

public:

  // Instance klass, ignoring any interface
  ciInstanceKlass* instance_klass() const {
    assert(!(klass()->is_loaded() && klass()->is_interface()), "");
    return klass()->as_instance_klass();
  }

  bool is_same_java_type_as_helper(const TypeOopPtr* other) const;
  bool is_java_subtype_of_helper(const TypeOopPtr* other, bool this_exact, bool other_exact) const;
  bool maybe_java_subtype_of_helper(const TypeOopPtr* other, bool this_exact, bool other_exact) const;

  // Make a pointer to a constant oop.
  static const TypeInstPtr *make(ciObject* o) {
    ciKlass* k = o->klass();
    const TypeInterfaces* interfaces = TypePtr::interfaces(k, true, false, false, ignore_interfaces);
    return make(TypePtr::Constant, k, interfaces, true, o, 0, InstanceBot);
  }
  // Make a pointer to a constant oop with offset.
  static const TypeInstPtr *make(ciObject* o, int offset) {
    ciKlass* k = o->klass();
    const TypeInterfaces* interfaces = TypePtr::interfaces(k, true, false, false, ignore_interfaces);
    return make(TypePtr::Constant, k, interfaces, true, o, offset, InstanceBot);
  }

  // Make a pointer to some value of type klass.
  static const TypeInstPtr *make(PTR ptr, ciKlass* klass, InterfaceHandling interface_handling = ignore_interfaces) {
    const TypeInterfaces* interfaces = TypePtr::interfaces(klass, true, true, false, interface_handling);
    return make(ptr, klass, interfaces, false, nullptr, 0, InstanceBot);
  }

  // Make a pointer to some non-polymorphic value of exactly type klass.
  static const TypeInstPtr *make_exact(PTR ptr, ciKlass* klass) {
    const TypeInterfaces* interfaces = TypePtr::interfaces(klass, true, false, false, ignore_interfaces);
    return make(ptr, klass, interfaces, true, nullptr, 0, InstanceBot);
  }

  // Make a pointer to some value of type klass with offset.
  static const TypeInstPtr *make(PTR ptr, ciKlass* klass, int offset) {
    const TypeInterfaces* interfaces = TypePtr::interfaces(klass, true, false, false, ignore_interfaces);
    return make(ptr, klass, interfaces, false, nullptr, offset, InstanceBot);
  }

  static const TypeInstPtr *make(PTR ptr, ciKlass* k, const TypeInterfaces* interfaces, bool xk, ciObject* o, int offset,
                                 int instance_id = InstanceBot,
                                 const TypePtr* speculative = nullptr,
                                 int inline_depth = InlineDepthBottom);

  static const TypeInstPtr *make(PTR ptr, ciKlass* k, bool xk, ciObject* o, int offset, int instance_id = InstanceBot) {
    const TypeInterfaces* interfaces = TypePtr::interfaces(k, true, false, false, ignore_interfaces);
    return make(ptr, k, interfaces, xk, o, offset, instance_id);
  }

  /** Create constant type for a constant boxed value */
  const Type* get_const_boxed_value() const;

  // If this is a java.lang.Class constant, return the type for it or null.
  // Pass to Type::get_const_type to turn it to a type, which will usually
  // be a TypeInstPtr, but may also be a TypeInt::INT for int.class, etc.
  ciType* java_mirror_type() const;

  virtual const TypeInstPtr* cast_to_ptr_type(PTR ptr) const;

  virtual const TypeInstPtr* cast_to_exactness(bool klass_is_exact) const;

  virtual const TypeInstPtr* cast_to_instance_id(int instance_id) const;

  virtual const TypePtr* add_offset(intptr_t offset) const;
  virtual const TypeInstPtr* with_offset(intptr_t offset) const;

  // Speculative type helper methods.
  virtual const TypeInstPtr* remove_speculative() const;
  const TypeInstPtr* with_speculative(const TypePtr* speculative) const;
  virtual const TypePtr* with_inline_depth(int depth) const;
  virtual const TypePtr* with_instance_id(int instance_id) const;

  // the core of the computation of the meet of 2 types
  virtual const Type *xmeet_helper(const Type *t) const;
  virtual const TypeInstPtr *xmeet_unloaded(const TypeInstPtr *tinst, const TypeInterfaces* interfaces) const;
  virtual const Type *xdual() const;    // Compute dual right now.

  const TypeKlassPtr* as_klass_type(bool try_for_exact = false) const;

  // Convenience common pre-built types.
  static const TypeInstPtr *NOTNULL;
  static const TypeInstPtr *BOTTOM;
  static const TypeInstPtr *MIRROR;
  static const TypeInstPtr *MARK;
  static const TypeInstPtr *KLASS;
#ifndef PRODUCT
  virtual void dump2( Dict &d, uint depth, outputStream *st ) const; // Specialized per-Type dumping
#endif

private:
  virtual bool is_meet_subtype_of_helper(const TypeOopPtr* other, bool this_xk, bool other_xk) const;

  virtual bool is_meet_same_type_as(const TypePtr* other) const {
    return _klass->equals(other->is_instptr()->_klass) && _interfaces->eq(other->is_instptr()->_interfaces);
  }

};

//------------------------------TypeAryPtr-------------------------------------
// Class of Java array pointers
class TypeAryPtr : public TypeOopPtr {
  friend class Type;
  friend class TypePtr;
  friend class TypeInterfaces;

  TypeAryPtr( PTR ptr, ciObject* o, const TypeAry *ary, ciKlass* k, bool xk,
              int offset, int instance_id, bool is_autobox_cache,
              const TypePtr* speculative, int inline_depth)
    : TypeOopPtr(AryPtr,ptr,k,_array_interfaces,xk,o,offset, instance_id, speculative, inline_depth),
    _ary(ary),
    _is_autobox_cache(is_autobox_cache)
 {
    int dummy;
    bool top_or_bottom = (base_element_type(dummy) == Type::TOP || base_element_type(dummy) == Type::BOTTOM);

    if (UseCompressedOops && (elem()->make_oopptr() != nullptr && !top_or_bottom) &&
        _offset != 0 && _offset != arrayOopDesc::length_offset_in_bytes() &&
        _offset != arrayOopDesc::klass_offset_in_bytes()) {
      _is_ptr_to_narrowoop = true;
    }

  }
  virtual bool eq( const Type *t ) const;
  virtual uint hash() const;    // Type specific hashing
  const TypeAry *_ary;          // Array we point into
  const bool     _is_autobox_cache;

  ciKlass* compute_klass() const;

  // A pointer to delay allocation to Type::Initialize_shared()

  static const TypeInterfaces* _array_interfaces;
  ciKlass* exact_klass_helper() const;
  // Only guaranteed non null for array of basic types
  ciKlass* klass() const;

public:

  bool is_same_java_type_as_helper(const TypeOopPtr* other) const;
  bool is_java_subtype_of_helper(const TypeOopPtr* other, bool this_exact, bool other_exact) const;
  bool maybe_java_subtype_of_helper(const TypeOopPtr* other, bool this_exact, bool other_exact) const;

  // returns base element type, an instance klass (and not interface) for object arrays
  const Type* base_element_type(int& dims) const;

  // Accessors
  bool  is_loaded() const { return (_ary->_elem->make_oopptr() ? _ary->_elem->make_oopptr()->is_loaded() : true); }

  const TypeAry* ary() const  { return _ary; }
  const Type*    elem() const { return _ary->_elem; }
  const TypeInt* size() const { return _ary->_size; }
  bool      is_stable() const { return _ary->_stable; }

  bool is_autobox_cache() const { return _is_autobox_cache; }

  static const TypeAryPtr *make(PTR ptr, const TypeAry *ary, ciKlass* k, bool xk, int offset,
                                int instance_id = InstanceBot,
                                const TypePtr* speculative = nullptr,
                                int inline_depth = InlineDepthBottom);
  // Constant pointer to array
  static const TypeAryPtr *make(PTR ptr, ciObject* o, const TypeAry *ary, ciKlass* k, bool xk, int offset,
                                int instance_id = InstanceBot,
                                const TypePtr* speculative = nullptr,
                                int inline_depth = InlineDepthBottom, bool is_autobox_cache = false);

  // Return a 'ptr' version of this type
  virtual const TypeAryPtr* cast_to_ptr_type(PTR ptr) const;

  virtual const TypeAryPtr* cast_to_exactness(bool klass_is_exact) const;

  virtual const TypeAryPtr* cast_to_instance_id(int instance_id) const;

  virtual const TypeAryPtr* cast_to_size(const TypeInt* size) const;
  virtual const TypeInt* narrow_size_type(const TypeInt* size) const;

  virtual bool empty(void) const;        // TRUE if type is vacuous
  virtual const TypePtr *add_offset( intptr_t offset ) const;
  virtual const TypeAryPtr *with_offset( intptr_t offset ) const;
  const TypeAryPtr* with_ary(const TypeAry* ary) const;

  // Speculative type helper methods.
  virtual const TypeAryPtr* remove_speculative() const;
  virtual const TypePtr* with_inline_depth(int depth) const;
  virtual const TypePtr* with_instance_id(int instance_id) const;

  // the core of the computation of the meet of 2 types
  virtual const Type *xmeet_helper(const Type *t) const;
  virtual const Type *xdual() const;    // Compute dual right now.

  const TypeAryPtr* cast_to_stable(bool stable, int stable_dimension = 1) const;
  int stable_dimension() const;

  const TypeAryPtr* cast_to_autobox_cache() const;

  static jint max_array_length(BasicType etype) ;
  virtual const TypeKlassPtr* as_klass_type(bool try_for_exact = false) const;

  // Convenience common pre-built types.
  static const TypeAryPtr* BOTTOM;
  static const TypeAryPtr* RANGE;
  static const TypeAryPtr* OOPS;
  static const TypeAryPtr* NARROWOOPS;
  static const TypeAryPtr* BYTES;
  static const TypeAryPtr* SHORTS;
  static const TypeAryPtr* CHARS;
  static const TypeAryPtr* INTS;
  static const TypeAryPtr* LONGS;
  static const TypeAryPtr* FLOATS;
  static const TypeAryPtr* DOUBLES;
  // selects one of the above:
  static const TypeAryPtr *get_array_body_type(BasicType elem) {
    assert((uint)elem <= T_CONFLICT && _array_body_type[elem] != nullptr, "bad elem type");
    return _array_body_type[elem];
  }
  static const TypeAryPtr *_array_body_type[T_CONFLICT+1];
  // sharpen the type of an int which is used as an array size
#ifndef PRODUCT
  virtual void dump2( Dict &d, uint depth, outputStream *st ) const; // Specialized per-Type dumping
#endif
private:
  virtual bool is_meet_subtype_of_helper(const TypeOopPtr* other, bool this_xk, bool other_xk) const;
};

//------------------------------TypeMetadataPtr-------------------------------------
// Some kind of metadata, either Method*, MethodData* or CPCacheOop
class TypeMetadataPtr : public TypePtr {
protected:
  TypeMetadataPtr(PTR ptr, ciMetadata* metadata, int offset);
  // Do not allow interface-vs.-noninterface joins to collapse to top.
  virtual const Type *filter_helper(const Type *kills, bool include_speculative) const;
public:
  virtual bool eq( const Type *t ) const;
  virtual uint hash() const;             // Type specific hashing
  virtual bool singleton(void) const;    // TRUE if type is a singleton

private:
  ciMetadata*   _metadata;

public:
  static const TypeMetadataPtr* make(PTR ptr, ciMetadata* m, int offset);

  static const TypeMetadataPtr* make(ciMethod* m);
  static const TypeMetadataPtr* make(ciMethodData* m);

  ciMetadata* metadata() const { return _metadata; }

  virtual const TypeMetadataPtr* cast_to_ptr_type(PTR ptr) const;

  virtual const TypePtr *add_offset( intptr_t offset ) const;

  virtual const Type *xmeet( const Type *t ) const;
  virtual const Type *xdual() const;    // Compute dual right now.

  virtual intptr_t get_con() const;

  // Convenience common pre-built types.
  static const TypeMetadataPtr *BOTTOM;

#ifndef PRODUCT
  virtual void dump2( Dict &d, uint depth, outputStream *st ) const;
#endif
};

//------------------------------TypeKlassPtr-----------------------------------
// Class of Java Klass pointers
class TypeKlassPtr : public TypePtr {
  friend class TypeInstKlassPtr;
  friend class TypeAryKlassPtr;
  friend class TypePtr;
protected:
  TypeKlassPtr(TYPES t, PTR ptr, ciKlass* klass, const TypeInterfaces* interfaces, int offset);

  virtual const Type *filter_helper(const Type *kills, bool include_speculative) const;

public:
  virtual bool eq( const Type *t ) const;
  virtual uint hash() const;
  virtual bool singleton(void) const;    // TRUE if type is a singleton

protected:

  ciKlass* _klass;
  const TypeInterfaces* _interfaces;
  const TypeInterfaces* meet_interfaces(const TypeKlassPtr* other) const;
  virtual bool must_be_exact() const { ShouldNotReachHere(); return false; }
  virtual ciKlass* exact_klass_helper() const;
  virtual ciKlass* klass() const { return  _klass; }

public:

  bool is_java_subtype_of(const TypeKlassPtr* other) const {
    return is_java_subtype_of_helper(other, klass_is_exact(), other->klass_is_exact());
  }
  bool is_same_java_type_as(const TypePtr* other) const {
    return is_same_java_type_as_helper(other->is_klassptr());
  }

  bool maybe_java_subtype_of(const TypeKlassPtr* other) const {
    return maybe_java_subtype_of_helper(other, klass_is_exact(), other->klass_is_exact());
  }
  virtual bool is_same_java_type_as_helper(const TypeKlassPtr* other) const { ShouldNotReachHere(); return false; }
  virtual bool is_java_subtype_of_helper(const TypeKlassPtr* other, bool this_exact, bool other_exact) const { ShouldNotReachHere(); return false; }
  virtual bool maybe_java_subtype_of_helper(const TypeKlassPtr* other, bool this_exact, bool other_exact) const { ShouldNotReachHere(); return false; }

  // Exact klass, possibly an interface or an array of interface
  ciKlass* exact_klass(bool maybe_null = false) const { assert(klass_is_exact(), ""); ciKlass* k = exact_klass_helper(); assert(k != nullptr || maybe_null, ""); return k;  }
  virtual bool klass_is_exact()    const { return _ptr == Constant; }

  static const TypeKlassPtr* make(ciKlass* klass, InterfaceHandling interface_handling = ignore_interfaces);
  static const TypeKlassPtr *make(PTR ptr, ciKlass* klass, int offset, InterfaceHandling interface_handling = ignore_interfaces);

  virtual bool  is_loaded() const { return _klass->is_loaded(); }

  virtual const TypeKlassPtr* cast_to_ptr_type(PTR ptr) const { ShouldNotReachHere(); return nullptr; }

  virtual const TypeKlassPtr *cast_to_exactness(bool klass_is_exact) const { ShouldNotReachHere(); return nullptr; }

  // corresponding pointer to instance, for a given class
  virtual const TypeOopPtr* as_instance_type(bool klass_change = true) const { ShouldNotReachHere(); return nullptr; }

  virtual const TypePtr *add_offset( intptr_t offset ) const { ShouldNotReachHere(); return nullptr; }
  virtual const Type    *xmeet( const Type *t ) const { ShouldNotReachHere(); return nullptr; }
  virtual const Type    *xdual() const { ShouldNotReachHere(); return nullptr; }

  virtual intptr_t get_con() const;

  virtual const TypeKlassPtr* with_offset(intptr_t offset) const { ShouldNotReachHere(); return nullptr; }

  virtual const TypeKlassPtr* try_improve() const { return this; }

#ifndef PRODUCT
  virtual void dump2( Dict &d, uint depth, outputStream *st ) const; // Specialized per-Type dumping
#endif
private:
  virtual bool is_meet_subtype_of(const TypePtr* other) const {
    return is_meet_subtype_of_helper(other->is_klassptr(), klass_is_exact(), other->is_klassptr()->klass_is_exact());
  }

  virtual bool is_meet_subtype_of_helper(const TypeKlassPtr* other, bool this_xk, bool other_xk) const {
    ShouldNotReachHere(); return false;
  }

  virtual const TypeInterfaces* interfaces() const {
    return _interfaces;
  };

  const TypeKlassPtr* is_reference_type(const Type* other) const {
    return other->isa_klassptr();
  }

  const TypeAryKlassPtr* is_array_type(const TypeKlassPtr* other) const {
    return other->isa_aryklassptr();
  }

  const TypeInstKlassPtr* is_instance_type(const TypeKlassPtr* other) const {
    return other->isa_instklassptr();
  }
};

// Instance klass pointer, mirrors TypeInstPtr
class TypeInstKlassPtr : public TypeKlassPtr {

  TypeInstKlassPtr(PTR ptr, ciKlass* klass, const TypeInterfaces* interfaces, int offset)
    : TypeKlassPtr(InstKlassPtr, ptr, klass, interfaces, offset) {
    assert(klass->is_instance_klass() && (!klass->is_loaded() || !klass->is_interface()), "");
  }

  virtual bool must_be_exact() const;

public:
  // Instance klass ignoring any interface
  ciInstanceKlass* instance_klass() const {
    assert(!klass()->is_interface(), "");
    return klass()->as_instance_klass();
  }

  bool might_be_an_array() const;

  bool is_same_java_type_as_helper(const TypeKlassPtr* other) const;
  bool is_java_subtype_of_helper(const TypeKlassPtr* other, bool this_exact, bool other_exact) const;
  bool maybe_java_subtype_of_helper(const TypeKlassPtr* other, bool this_exact, bool other_exact) const;

  static const TypeInstKlassPtr *make(ciKlass* k, InterfaceHandling interface_handling) {
    const TypeInterfaces* interfaces = TypePtr::interfaces(k, true, true, false, interface_handling);
    return make(TypePtr::Constant, k, interfaces, 0);
  }
  static const TypeInstKlassPtr* make(PTR ptr, ciKlass* k, const TypeInterfaces* interfaces, int offset);

  static const TypeInstKlassPtr* make(PTR ptr, ciKlass* k, int offset) {
    const TypeInterfaces* interfaces = TypePtr::interfaces(k, true, false, false, ignore_interfaces);
    return make(ptr, k, interfaces, offset);
  }

  virtual const TypeInstKlassPtr* cast_to_ptr_type(PTR ptr) const;

  virtual const TypeKlassPtr *cast_to_exactness(bool klass_is_exact) const;

  // corresponding pointer to instance, for a given class
  virtual const TypeOopPtr* as_instance_type(bool klass_change = true) const;
  virtual uint hash() const;
  virtual bool eq(const Type *t) const;

  virtual const TypePtr *add_offset( intptr_t offset ) const;
  virtual const Type    *xmeet( const Type *t ) const;
  virtual const Type    *xdual() const;
  virtual const TypeInstKlassPtr* with_offset(intptr_t offset) const;

  virtual const TypeKlassPtr* try_improve() const;

  // Convenience common pre-built types.
  static const TypeInstKlassPtr* OBJECT; // Not-null object klass or below
  static const TypeInstKlassPtr* OBJECT_OR_NULL; // Maybe-null version of same
private:
  virtual bool is_meet_subtype_of_helper(const TypeKlassPtr* other, bool this_xk, bool other_xk) const;
};

// Array klass pointer, mirrors TypeAryPtr
class TypeAryKlassPtr : public TypeKlassPtr {
  friend class TypeInstKlassPtr;
  friend class Type;
  friend class TypePtr;

  const Type *_elem;

  static const TypeInterfaces* _array_interfaces;
  TypeAryKlassPtr(PTR ptr, const Type *elem, ciKlass* klass, int offset)
    : TypeKlassPtr(AryKlassPtr, ptr, klass, _array_interfaces, offset), _elem(elem) {
    assert(klass == nullptr || klass->is_type_array_klass() || !klass->as_obj_array_klass()->base_element_klass()->is_interface(), "");
  }

  virtual ciKlass* exact_klass_helper() const;
  // Only guaranteed non null for array of basic types
  virtual ciKlass* klass() const;

  virtual bool must_be_exact() const;

public:

  // returns base element type, an instance klass (and not interface) for object arrays
  const Type* base_element_type(int& dims) const;

  static const TypeAryKlassPtr *make(PTR ptr, ciKlass* k, int offset, InterfaceHandling interface_handling);

  bool is_same_java_type_as_helper(const TypeKlassPtr* other) const;
  bool is_java_subtype_of_helper(const TypeKlassPtr* other, bool this_exact, bool other_exact) const;
  bool maybe_java_subtype_of_helper(const TypeKlassPtr* other, bool this_exact, bool other_exact) const;

  bool  is_loaded() const { return (_elem->isa_klassptr() ? _elem->is_klassptr()->is_loaded() : true); }

  static const TypeAryKlassPtr *make(PTR ptr, const Type *elem, ciKlass* k, int offset);
  static const TypeAryKlassPtr* make(ciKlass* klass, InterfaceHandling interface_handling);

  const Type *elem() const { return _elem; }

  virtual bool eq(const Type *t) const;
  virtual uint hash() const;             // Type specific hashing

  virtual const TypeAryKlassPtr* cast_to_ptr_type(PTR ptr) const;

  virtual const TypeKlassPtr *cast_to_exactness(bool klass_is_exact) const;

  // corresponding pointer to instance, for a given class
  virtual const TypeOopPtr* as_instance_type(bool klass_change = true) const;

  virtual const TypePtr *add_offset( intptr_t offset ) const;
  virtual const Type    *xmeet( const Type *t ) const;
  virtual const Type    *xdual() const;      // Compute dual right now.

  virtual const TypeAryKlassPtr* with_offset(intptr_t offset) const;

  virtual bool empty(void) const {
    return TypeKlassPtr::empty() || _elem->empty();
  }

#ifndef PRODUCT
  virtual void dump2( Dict &d, uint depth, outputStream *st ) const; // Specialized per-Type dumping
#endif
private:
  virtual bool is_meet_subtype_of_helper(const TypeKlassPtr* other, bool this_xk, bool other_xk) const;
};

class TypeNarrowPtr : public Type {
protected:
  const TypePtr* _ptrtype; // Could be TypePtr::NULL_PTR

  TypeNarrowPtr(TYPES t, const TypePtr* ptrtype): Type(t),
                                                  _ptrtype(ptrtype) {
    assert(ptrtype->offset() == 0 ||
           ptrtype->offset() == OffsetBot ||
           ptrtype->offset() == OffsetTop, "no real offsets");
  }

  virtual const TypeNarrowPtr *isa_same_narrowptr(const Type *t) const = 0;
  virtual const TypeNarrowPtr *is_same_narrowptr(const Type *t) const = 0;
  virtual const TypeNarrowPtr *make_same_narrowptr(const TypePtr *t) const = 0;
  virtual const TypeNarrowPtr *make_hash_same_narrowptr(const TypePtr *t) const = 0;
  // Do not allow interface-vs.-noninterface joins to collapse to top.
  virtual const Type *filter_helper(const Type *kills, bool include_speculative) const;
public:
  virtual bool eq( const Type *t ) const;
  virtual uint hash() const;             // Type specific hashing
  virtual bool singleton(void) const;    // TRUE if type is a singleton

  virtual const Type *xmeet( const Type *t ) const;
  virtual const Type *xdual() const;    // Compute dual right now.

  virtual intptr_t get_con() const;

  virtual bool empty(void) const;        // TRUE if type is vacuous

  // returns the equivalent ptr type for this compressed pointer
  const TypePtr *get_ptrtype() const {
    return _ptrtype;
  }

  bool is_known_instance() const {
    return _ptrtype->is_known_instance();
  }

#ifndef PRODUCT
  virtual void dump2( Dict &d, uint depth, outputStream *st ) const;
#endif
};

//------------------------------TypeNarrowOop----------------------------------
// A compressed reference to some kind of Oop.  This type wraps around
// a preexisting TypeOopPtr and forwards most of it's operations to
// the underlying type.  It's only real purpose is to track the
// oopness of the compressed oop value when we expose the conversion
// between the normal and the compressed form.
class TypeNarrowOop : public TypeNarrowPtr {
protected:
  TypeNarrowOop( const TypePtr* ptrtype): TypeNarrowPtr(NarrowOop, ptrtype) {
  }

  virtual const TypeNarrowPtr *isa_same_narrowptr(const Type *t) const {
    return t->isa_narrowoop();
  }

  virtual const TypeNarrowPtr *is_same_narrowptr(const Type *t) const {
    return t->is_narrowoop();
  }

  virtual const TypeNarrowPtr *make_same_narrowptr(const TypePtr *t) const {
    return new TypeNarrowOop(t);
  }

  virtual const TypeNarrowPtr *make_hash_same_narrowptr(const TypePtr *t) const {
    return (const TypeNarrowPtr*)((new TypeNarrowOop(t))->hashcons());
  }

public:

  static const TypeNarrowOop *make( const TypePtr* type);

  static const TypeNarrowOop* make_from_constant(ciObject* con, bool require_constant = false) {
    return make(TypeOopPtr::make_from_constant(con, require_constant));
  }

  static const TypeNarrowOop *BOTTOM;
  static const TypeNarrowOop *NULL_PTR;

  virtual const TypeNarrowOop* remove_speculative() const;
  virtual const Type* cleanup_speculative() const;

#ifndef PRODUCT
  virtual void dump2( Dict &d, uint depth, outputStream *st ) const;
#endif
};

//------------------------------TypeNarrowKlass----------------------------------
// A compressed reference to klass pointer.  This type wraps around a
// preexisting TypeKlassPtr and forwards most of it's operations to
// the underlying type.
class TypeNarrowKlass : public TypeNarrowPtr {
protected:
  TypeNarrowKlass( const TypePtr* ptrtype): TypeNarrowPtr(NarrowKlass, ptrtype) {
  }

  virtual const TypeNarrowPtr *isa_same_narrowptr(const Type *t) const {
    return t->isa_narrowklass();
  }

  virtual const TypeNarrowPtr *is_same_narrowptr(const Type *t) const {
    return t->is_narrowklass();
  }

  virtual const TypeNarrowPtr *make_same_narrowptr(const TypePtr *t) const {
    return new TypeNarrowKlass(t);
  }

  virtual const TypeNarrowPtr *make_hash_same_narrowptr(const TypePtr *t) const {
    return (const TypeNarrowPtr*)((new TypeNarrowKlass(t))->hashcons());
  }

public:
  static const TypeNarrowKlass *make( const TypePtr* type);

  // static const TypeNarrowKlass *BOTTOM;
  static const TypeNarrowKlass *NULL_PTR;

#ifndef PRODUCT
  virtual void dump2( Dict &d, uint depth, outputStream *st ) const;
#endif
};

//------------------------------TypeFunc---------------------------------------
// Class of Array Types
class TypeFunc : public Type {
  TypeFunc( const TypeTuple *domain, const TypeTuple *range ) : Type(Function),  _domain(domain), _range(range) {}
  virtual bool eq( const Type *t ) const;
  virtual uint hash() const;             // Type specific hashing
  virtual bool singleton(void) const;    // TRUE if type is a singleton
  virtual bool empty(void) const;        // TRUE if type is vacuous

  const TypeTuple* const _domain;     // Domain of inputs
  const TypeTuple* const _range;      // Range of results

public:
  // Constants are shared among ADLC and VM
  enum { Control    = AdlcVMDeps::Control,
         I_O        = AdlcVMDeps::I_O,
         Memory     = AdlcVMDeps::Memory,
         FramePtr   = AdlcVMDeps::FramePtr,
         ReturnAdr  = AdlcVMDeps::ReturnAdr,
         Parms      = AdlcVMDeps::Parms
  };


  // Accessors:
  const TypeTuple* domain() const { return _domain; }
  const TypeTuple* range()  const { return _range; }

  static const TypeFunc *make(ciMethod* method);
  static const TypeFunc *make(ciSignature signature, const Type* extra);
  static const TypeFunc *make(const TypeTuple* domain, const TypeTuple* range);

  virtual const Type *xmeet( const Type *t ) const;
  virtual const Type *xdual() const;    // Compute dual right now.

  BasicType return_type() const;

#ifndef PRODUCT
  virtual void dump2( Dict &d, uint depth, outputStream *st ) const; // Specialized per-Type dumping
#endif
  // Convenience common pre-built types.
};

//------------------------------accessors--------------------------------------
inline bool Type::is_ptr_to_narrowoop() const {
#ifdef _LP64
  return (isa_oopptr() != nullptr && is_oopptr()->is_ptr_to_narrowoop_nv());
#else
  return false;
#endif
}

inline bool Type::is_ptr_to_narrowklass() const {
#ifdef _LP64
  return (isa_oopptr() != nullptr && is_oopptr()->is_ptr_to_narrowklass_nv());
#else
  return false;
#endif
}

inline float Type::getf() const {
  assert( _base == FloatCon, "Not a FloatCon" );
  return ((TypeF*)this)->_f;
}

inline short Type::geth() const {
  assert(_base == HalfFloatCon, "Not a HalfFloatCon");
  return ((TypeH*)this)->_f;
}

inline double Type::getd() const {
  assert( _base == DoubleCon, "Not a DoubleCon" );
  return ((TypeD*)this)->_d;
}

inline const TypeInteger *Type::is_integer(BasicType bt) const {
  assert((bt == T_INT && _base == Int) || (bt == T_LONG && _base == Long), "Not an Int");
  return (TypeInteger*)this;
}

inline const TypeInteger *Type::isa_integer(BasicType bt) const {
  return (((bt == T_INT && _base == Int) || (bt == T_LONG && _base == Long)) ? (TypeInteger*)this : nullptr);
}

inline const TypeInt *Type::is_int() const {
  assert( _base == Int, "Not an Int" );
  return (TypeInt*)this;
}

inline const TypeInt *Type::isa_int() const {
  return ( _base == Int ? (TypeInt*)this : nullptr);
}

inline const TypeLong *Type::is_long() const {
  assert( _base == Long, "Not a Long" );
  return (TypeLong*)this;
}

inline const TypeLong *Type::isa_long() const {
  return ( _base == Long ? (TypeLong*)this : nullptr);
}

inline const TypeH* Type::isa_half_float() const {
  return ((_base == HalfFloatTop ||
           _base == HalfFloatCon ||
           _base == HalfFloatBot) ? (TypeH*)this : nullptr);
}

inline const TypeH* Type::is_half_float_constant() const {
  assert( _base == HalfFloatCon, "Not a HalfFloat" );
  return (TypeH*)this;
}

inline const TypeH* Type::isa_half_float_constant() const {
  return (_base == HalfFloatCon ? (TypeH*)this : nullptr);
}

inline const TypeF *Type::isa_float() const {
  return ((_base == FloatTop ||
           _base == FloatCon ||
           _base == FloatBot) ? (TypeF*)this : nullptr);
}

inline const TypeF *Type::is_float_constant() const {
  assert( _base == FloatCon, "Not a Float" );
  return (TypeF*)this;
}

inline const TypeF *Type::isa_float_constant() const {
  return ( _base == FloatCon ? (TypeF*)this : nullptr);
}

inline const TypeD *Type::isa_double() const {
  return ((_base == DoubleTop ||
           _base == DoubleCon ||
           _base == DoubleBot) ? (TypeD*)this : nullptr);
}

inline const TypeD *Type::is_double_constant() const {
  assert( _base == DoubleCon, "Not a Double" );
  return (TypeD*)this;
}

inline const TypeD *Type::isa_double_constant() const {
  return ( _base == DoubleCon ? (TypeD*)this : nullptr);
}

inline const TypeTuple *Type::is_tuple() const {
  assert( _base == Tuple, "Not a Tuple" );
  return (TypeTuple*)this;
}

inline const TypeAry *Type::is_ary() const {
  assert( _base == Array , "Not an Array" );
  return (TypeAry*)this;
}

inline const TypeAry *Type::isa_ary() const {
  return ((_base == Array) ? (TypeAry*)this : nullptr);
}

inline const TypeVectMask *Type::is_vectmask() const {
  assert( _base == VectorMask, "Not a Vector Mask" );
  return (TypeVectMask*)this;
}

inline const TypeVectMask *Type::isa_vectmask() const {
  return (_base == VectorMask) ? (TypeVectMask*)this : nullptr;
}

inline const TypeVect *Type::is_vect() const {
  assert( _base >= VectorMask && _base <= VectorZ, "Not a Vector" );
  return (TypeVect*)this;
}

inline const TypeVect *Type::isa_vect() const {
  return (_base >= VectorMask && _base <= VectorZ) ? (TypeVect*)this : nullptr;
}

inline const TypePtr *Type::is_ptr() const {
  // AnyPtr is the first Ptr and KlassPtr the last, with no non-ptrs between.
  assert(_base >= AnyPtr && _base <= AryKlassPtr, "Not a pointer");
  return (TypePtr*)this;
}

inline const TypePtr *Type::isa_ptr() const {
  // AnyPtr is the first Ptr and KlassPtr the last, with no non-ptrs between.
  return (_base >= AnyPtr && _base <= AryKlassPtr) ? (TypePtr*)this : nullptr;
}

inline const TypeOopPtr *Type::is_oopptr() const {
  // OopPtr is the first and KlassPtr the last, with no non-oops between.
  assert(_base >= OopPtr && _base <= AryPtr, "Not a Java pointer" ) ;
  return (TypeOopPtr*)this;
}

inline const TypeOopPtr *Type::isa_oopptr() const {
  // OopPtr is the first and KlassPtr the last, with no non-oops between.
  return (_base >= OopPtr && _base <= AryPtr) ? (TypeOopPtr*)this : nullptr;
}

inline const TypeRawPtr *Type::isa_rawptr() const {
  return (_base == RawPtr) ? (TypeRawPtr*)this : nullptr;
}

inline const TypeRawPtr *Type::is_rawptr() const {
  assert( _base == RawPtr, "Not a raw pointer" );
  return (TypeRawPtr*)this;
}

inline const TypeInstPtr *Type::isa_instptr() const {
  return (_base == InstPtr) ? (TypeInstPtr*)this : nullptr;
}

inline const TypeInstPtr *Type::is_instptr() const {
  assert( _base == InstPtr, "Not an object pointer" );
  return (TypeInstPtr*)this;
}

inline const TypeAryPtr *Type::isa_aryptr() const {
  return (_base == AryPtr) ? (TypeAryPtr*)this : nullptr;
}

inline const TypeAryPtr *Type::is_aryptr() const {
  assert( _base == AryPtr, "Not an array pointer" );
  return (TypeAryPtr*)this;
}

inline const TypeNarrowOop *Type::is_narrowoop() const {
  // OopPtr is the first and KlassPtr the last, with no non-oops between.
  assert(_base == NarrowOop, "Not a narrow oop" ) ;
  return (TypeNarrowOop*)this;
}

inline const TypeNarrowOop *Type::isa_narrowoop() const {
  // OopPtr is the first and KlassPtr the last, with no non-oops between.
  return (_base == NarrowOop) ? (TypeNarrowOop*)this : nullptr;
}

inline const TypeNarrowKlass *Type::is_narrowklass() const {
  assert(_base == NarrowKlass, "Not a narrow oop" ) ;
  return (TypeNarrowKlass*)this;
}

inline const TypeNarrowKlass *Type::isa_narrowklass() const {
  return (_base == NarrowKlass) ? (TypeNarrowKlass*)this : nullptr;
}

inline const TypeMetadataPtr *Type::is_metadataptr() const {
  // MetadataPtr is the first and CPCachePtr the last
  assert(_base == MetadataPtr, "Not a metadata pointer" ) ;
  return (TypeMetadataPtr*)this;
}

inline const TypeMetadataPtr *Type::isa_metadataptr() const {
  return (_base == MetadataPtr) ? (TypeMetadataPtr*)this : nullptr;
}

inline const TypeKlassPtr *Type::isa_klassptr() const {
  return (_base >= KlassPtr && _base <= AryKlassPtr ) ? (TypeKlassPtr*)this : nullptr;
}

inline const TypeKlassPtr *Type::is_klassptr() const {
  assert(_base >= KlassPtr && _base <= AryKlassPtr, "Not a klass pointer");
  return (TypeKlassPtr*)this;
}

inline const TypeInstKlassPtr *Type::isa_instklassptr() const {
  return (_base == InstKlassPtr) ? (TypeInstKlassPtr*)this : nullptr;
}

inline const TypeInstKlassPtr *Type::is_instklassptr() const {
  assert(_base == InstKlassPtr, "Not a klass pointer");
  return (TypeInstKlassPtr*)this;
}

inline const TypeAryKlassPtr *Type::isa_aryklassptr() const {
  return (_base == AryKlassPtr) ? (TypeAryKlassPtr*)this : nullptr;
}

inline const TypeAryKlassPtr *Type::is_aryklassptr() const {
  assert(_base == AryKlassPtr, "Not a klass pointer");
  return (TypeAryKlassPtr*)this;
}

inline const TypePtr* Type::make_ptr() const {
  return (_base == NarrowOop) ? is_narrowoop()->get_ptrtype() :
                              ((_base == NarrowKlass) ? is_narrowklass()->get_ptrtype() :
                                                       isa_ptr());
}

inline const TypeOopPtr* Type::make_oopptr() const {
  return (_base == NarrowOop) ? is_narrowoop()->get_ptrtype()->isa_oopptr() : isa_oopptr();
}

inline const TypeNarrowOop* Type::make_narrowoop() const {
  return (_base == NarrowOop) ? is_narrowoop() :
                                (isa_ptr() ? TypeNarrowOop::make(is_ptr()) : nullptr);
}

inline const TypeNarrowKlass* Type::make_narrowklass() const {
  return (_base == NarrowKlass) ? is_narrowklass() :
                                  (isa_ptr() ? TypeNarrowKlass::make(is_ptr()) : nullptr);
}

inline bool Type::is_floatingpoint() const {
  if( (_base == HalfFloatCon)  || (_base == HalfFloatBot) ||
      (_base == FloatCon)  || (_base == FloatBot) ||
      (_base == DoubleCon) || (_base == DoubleBot) )
    return true;
  return false;
}

template <>
inline const TypeInt* Type::cast<TypeInt>() const {
  return is_int();
}

template <>
inline const TypeLong* Type::cast<TypeLong>() const {
  return is_long();
}

template <>
inline const TypeInt* Type::try_cast<TypeInt>() const {
  return isa_int();
}

template <>
inline const TypeLong* Type::try_cast<TypeLong>() const {
  return isa_long();
}

// ===============================================================
// Things that need to be 64-bits in the 64-bit build but
// 32-bits in the 32-bit build.  Done this way to get full
// optimization AND strong typing.
#ifdef _LP64

// For type queries and asserts
#define is_intptr_t  is_long
#define isa_intptr_t isa_long
#define find_intptr_t_type find_long_type
#define find_intptr_t_con  find_long_con
#define TypeX        TypeLong
#define Type_X       Type::Long
#define TypeX_X      TypeLong::LONG
#define TypeX_ZERO   TypeLong::ZERO
// For 'ideal_reg' machine registers
#define Op_RegX      Op_RegL
// For phase->intcon variants
#define MakeConX     longcon
#define ConXNode     ConLNode
// For array index arithmetic
#define MulXNode     MulLNode
#define AndXNode     AndLNode
#define OrXNode      OrLNode
#define CmpXNode     CmpLNode
#define SubXNode     SubLNode
#define LShiftXNode  LShiftLNode
// For object size computation:
#define AddXNode     AddLNode
#define RShiftXNode  RShiftLNode
// For card marks and hashcodes
#define URShiftXNode URShiftLNode
// For shenandoahSupport
#define LoadXNode    LoadLNode
#define StoreXNode   StoreLNode
// Opcodes
#define Op_LShiftX   Op_LShiftL
#define Op_AndX      Op_AndL
#define Op_AddX      Op_AddL
#define Op_SubX      Op_SubL
#define Op_XorX      Op_XorL
#define Op_URShiftX  Op_URShiftL
#define Op_LoadX     Op_LoadL
// conversions
#define ConvI2X(x)   ConvI2L(x)
#define ConvL2X(x)   (x)
#define ConvX2I(x)   ConvL2I(x)
#define ConvX2L(x)   (x)
#define ConvX2UL(x)  (x)

#else

// For type queries and asserts
#define is_intptr_t  is_int
#define isa_intptr_t isa_int
#define find_intptr_t_type find_int_type
#define find_intptr_t_con  find_int_con
#define TypeX        TypeInt
#define Type_X       Type::Int
#define TypeX_X      TypeInt::INT
#define TypeX_ZERO   TypeInt::ZERO
// For 'ideal_reg' machine registers
#define Op_RegX      Op_RegI
// For phase->intcon variants
#define MakeConX     intcon
#define ConXNode     ConINode
// For array index arithmetic
#define MulXNode     MulINode
#define AndXNode     AndINode
#define OrXNode      OrINode
#define CmpXNode     CmpINode
#define SubXNode     SubINode
#define LShiftXNode  LShiftINode
// For object size computation:
#define AddXNode     AddINode
#define RShiftXNode  RShiftINode
// For card marks and hashcodes
#define URShiftXNode URShiftINode
// For shenandoahSupport
#define LoadXNode    LoadINode
#define StoreXNode   StoreINode
// Opcodes
#define Op_LShiftX   Op_LShiftI
#define Op_AndX      Op_AndI
#define Op_AddX      Op_AddI
#define Op_SubX      Op_SubI
#define Op_XorX      Op_XorI
#define Op_URShiftX  Op_URShiftI
#define Op_LoadX     Op_LoadI
// conversions
#define ConvI2X(x)   (x)
#define ConvL2X(x)   ConvL2I(x)
#define ConvX2I(x)   (x)
#define ConvX2L(x)   ConvI2L(x)
#define ConvX2UL(x)  ConvI2UL(x)

#endif

#endif // SHARE_OPTO_TYPE_HPP
