/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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

#include "nmt/memTag.hpp"
#include "opto/type.hpp"
#include "opto/typejavaptr.hpp"
#include "unittest.hpp"
#include "utilities/debug.hpp"
#include "utilities/globalDefinitions.hpp"
#include "utilities/growableArray.hpp"
#include "utilities/ostream.hpp"
#include <array>
#include <cstddef>

class ciInstanceKlassMirror;
class ciArrayKlassMirror;
class TypeOopPtrMirror;
class TypeInstPtrMirror;
class TypeAryPtrMirror;
class TypeKlassPtrMirror;
class TypeInstKlassPtrMirror;
class TypeAryKlassPtrMirror;

// This file contains unit tests for the implementation of TypeJavaPtrMeetHelper and
// TypeJavaPtrJoinHelper. We use mirror instances that mimic the behaviour of the real objects.
// There are several advantages in using them:
// - It is really hard to create a Type instance, while we can create mirror instances at will.
// - Type::make can be used to create an arbitrary instance, while we can limit the corresponding
//   factories to only return some expected instances. This greatly increases the rigor of the
//   tests.
// - Mirror instances are created at compile time, ensuring the absence of unexpected behaviors.

class InterfaceSet {
public:
  bool _i0;
  bool _i1;

  constexpr InterfaceSet(bool i0, bool i1) : _i0(i0), _i1(i1) {}

  InterfaceSet intersection_with(InterfaceSet other) const {
    return InterfaceSet(_i0 && other._i0, _i1 && other._i1);
  }

  InterfaceSet union_with(InterfaceSet other) const {
    return InterfaceSet(_i0 || other._i0, _i1 || other._i1);
  }

  const InterfaceSet* operator->() const {
    return this;
  }

  constexpr bool operator==(InterfaceSet o) const {
    return _i0 == o._i0 && _i1 == o._i1;
  }

  constexpr bool operator!=(InterfaceSet o) const {
    return !(*this == o);
  }

  bool contains(InterfaceSet sub) const {
    return (_i0 || !sub._i0) && (_i1 || !sub._i1);
  }

  bool eq(const ciInstanceKlassMirror* ci_klass) const;

  void dump_on(outputStream& st) const {
    if (_i0) {
      st.print("x");
    } else {
      st.print("_");
    }
    if (_i1) {
      st.print("x");
    } else {
      st.print("_");
    }
  }
};

class ciKlassMirror {
private:
  // The instance_id of the allocation corresponding to this type, each type has 3 dedicated id,
  // this value is the start of the 3
  int _instance_id;

protected:
  constexpr ciKlassMirror(int instance_id) : _instance_id(instance_id) {}

public:
  static constexpr bool verify_instance_id();

  constexpr int instance_id() const { return _instance_id; }

  virtual bool is_inst() const = 0;
  virtual bool is_ary()  const = 0;
  const ciInstanceKlassMirror* as_inst() const;
  const ciArrayKlassMirror*    as_ary()  const;

  virtual void dump_on(outputStream& st) const = 0;
};

class ciInstanceKlassMirror : public ciKlassMirror {
private:
  friend class ciEnvMirror;

  static constexpr size_t _samples_size = 8;
  int _parent_idx;
  InterfaceSet _interfaces;
  bool _is_loaded;
  bool _is_interface;
  const char* _name;

  constexpr ciInstanceKlassMirror(int parent_idx, InterfaceSet interfaces, bool is_loaded, bool is_interface, const char* name, int instance_id)
    : ciKlassMirror(instance_id), _parent_idx(parent_idx), _interfaces(interfaces), _is_loaded(is_loaded), _is_interface(is_interface), _name(name) {}

public:
  static const std::array<ciInstanceKlassMirror, _samples_size> _samples;

  constexpr InterfaceSet                 interfaces()          const { return _interfaces; }
  constexpr const ciInstanceKlassMirror* as_instance_klass()   const { return this; }
  constexpr bool                         is_loaded()           const { return _is_loaded; }
  constexpr bool                         is_interface()        const { return _is_interface; }
  constexpr bool                         is_java_lang_Object() const { return _parent_idx == -1; }

  constexpr const ciInstanceKlassMirror* parent() const {
    if (is_java_lang_Object()) {
      return nullptr;
    } else {
      return &_samples[_parent_idx];
    }
  }

  constexpr bool is_subtype_of(const ciInstanceKlassMirror* other) const {
    for (auto t = this; t != nullptr; t = t->parent()) {
      if (t == other) {
        return true;
      }
    }
    return false;
  }

  constexpr const ciInstanceKlassMirror* least_common_ancestor(const ciInstanceKlassMirror* other) const {
    for (auto t = this; t != nullptr; t = t->parent()) {
      if (other->is_subtype_of(t)) {
        return t;
      }
    }
    ShouldNotReachHere();
  }

  virtual bool is_inst() const override { return true; }
  virtual bool is_ary()  const override { return false; }

  virtual void dump_on(outputStream& st) const override {
    st.print("%s", _name);
  }
};

constexpr std::array<ciInstanceKlassMirror, ciInstanceKlassMirror::_samples_size> ciInstanceKlassMirror::_samples{
  ciInstanceKlassMirror(-1, InterfaceSet(false, false), true, false, "Object", 1),   // j.l.Object
  ciInstanceKlassMirror(0, InterfaceSet(false, false), false, false, "unloaded", 4), // unloaded
  ciInstanceKlassMirror(0, InterfaceSet(true, false), true, false, "A", 7),          // A extends Object implements I0
  ciInstanceKlassMirror(2, InterfaceSet(true, true), true, false, "B", 10),          // B extends A implements I1
  ciInstanceKlassMirror(2, InterfaceSet(true, false), true, false, "C", 13),         // C extends A
  ciInstanceKlassMirror(0, InterfaceSet(false, true), true, false, "D", 16),         // D extends Object implements I1
  ciInstanceKlassMirror(0, InterfaceSet(true, false), true, true, "I0", 19),         // I0
  ciInstanceKlassMirror(0, InterfaceSet(false, true), true, true, "I1", 22),         // I1
};

bool InterfaceSet::eq(const ciInstanceKlassMirror* ci_klass) const {
  return ci_klass->interfaces() == *this;
}

class ciArrayKlassMirror : public ciKlassMirror {
private:
  static constexpr size_t _samples_size = (ciInstanceKlassMirror::_samples.size() + 3) * 2;
  BasicType _bt;
  bool _elem_is_array;
  int _elem_idx;

  constexpr ciArrayKlassMirror(BasicType bt, bool elem_is_array, int elem_idx, int instance_id)
    : ciKlassMirror(instance_id), _bt(bt), _elem_is_array(elem_is_array), _elem_idx(elem_idx) {}

public:
  static const std::array<ciArrayKlassMirror, _samples_size> _samples;

  static constexpr const ciArrayKlassMirror& find(BasicType bt, const ciKlassMirror* elem) {
    for (auto& sample : _samples) {
      if (sample._bt == bt && sample.elem() == elem) {
        return sample;
      }
    }

    ShouldNotReachHere();
  }

  constexpr BasicType            elem_basic_type() const { return _bt; }

  constexpr const ciKlassMirror* elem()            const {
    if (_elem_idx == -1) {
      return nullptr;
    } else if (_elem_is_array) {
      return &_samples[_elem_idx];
    } else {
      return &ciInstanceKlassMirror::_samples[_elem_idx];
    }
  }

  virtual bool is_inst() const override { return false; }
  virtual bool is_ary()  const override { return true; }

  virtual void dump_on(outputStream& st) const override {
    if (_bt != T_OBJECT) {
      st.print("%s[]", type2name(_bt));
    } else {
      elem()->dump_on(st);
      st.print("[]");
    }
  }
};

// byte[] and int[] shares the same element base when they are expressed as a TypeAryPtr, while
// they have a different base from float[], so these 3 are used as representatives of primitive
// arrays
constexpr std::array<ciArrayKlassMirror, ciArrayKlassMirror::_samples_size> ciArrayKlassMirror::_samples = {
  ciArrayKlassMirror(T_BYTE, false, -1, 25),
  ciArrayKlassMirror(T_INT, false, -1, 28),
  ciArrayKlassMirror(T_FLOAT, false, -1, 31),
  ciArrayKlassMirror(T_OBJECT, false, 0, 34),
  ciArrayKlassMirror(T_OBJECT, false, 1, 37),
  ciArrayKlassMirror(T_OBJECT, false, 2, 40),
  ciArrayKlassMirror(T_OBJECT, false, 3, 43),
  ciArrayKlassMirror(T_OBJECT, false, 4, 46),
  ciArrayKlassMirror(T_OBJECT, false, 5, 49),
  ciArrayKlassMirror(T_OBJECT, false, 6, 52),
  ciArrayKlassMirror(T_OBJECT, false, 7, 55),
  ciArrayKlassMirror(T_OBJECT, true, 0, 58),
  ciArrayKlassMirror(T_OBJECT, true, 1, 61),
  ciArrayKlassMirror(T_OBJECT, true, 2, 64),
  ciArrayKlassMirror(T_OBJECT, true, 3, 67),
  ciArrayKlassMirror(T_OBJECT, true, 4, 70),
  ciArrayKlassMirror(T_OBJECT, true, 5, 73),
  ciArrayKlassMirror(T_OBJECT, true, 6, 76),
  ciArrayKlassMirror(T_OBJECT, true, 7, 79),
  ciArrayKlassMirror(T_OBJECT, true, 8, 82),
  ciArrayKlassMirror(T_OBJECT, true, 9, 85),
  ciArrayKlassMirror(T_OBJECT, true, 10, 88),
};

const ciInstanceKlassMirror* ciKlassMirror::as_inst() const {
  assert(is_inst(), "not an instance");
  return static_cast<const ciInstanceKlassMirror*>(this);
}

const ciArrayKlassMirror* ciKlassMirror::as_ary() const {
  assert(is_ary(), "not an array");
  return static_cast<const ciArrayKlassMirror*>(this);
}

constexpr bool ciKlassMirror::verify_instance_id() {
  int expected = 1;
  for (auto& ci_klass : ciInstanceKlassMirror::_samples) {
    if (ci_klass.instance_id() != expected) {
      return false;
    }
    expected += 3;
  }
  for (auto& ci_klass : ciArrayKlassMirror::_samples) {
    if (ci_klass.instance_id() != expected) {
      return false;
    }
    expected += 3;
  }

  return true;
}

static_assert(ciKlassMirror::verify_instance_id());

class ciEnvMirror {
private:
  static const ciEnvMirror _instance;

public:
  static constexpr const ciEnvMirror* current() { return &_instance; }
  constexpr const ciInstanceKlassMirror* Object_klass() const { return &ciInstanceKlassMirror::_samples[0]; }
};
constexpr ciEnvMirror ciEnvMirror::_instance;

class InstanceMirror {
private:
  const ciKlassMirror* _klass;
  int _idx;

public:
  constexpr InstanceMirror(const ciKlassMirror& klass, int idx) : _klass(&klass), _idx(idx) {}
  constexpr InstanceMirror(std::nullptr_t) : _klass(nullptr), _idx(0) {}

  constexpr int                  idx()   const { return _idx; }
  constexpr const ciKlassMirror* klass() const { return _klass; }

  constexpr int array_length() const {
    // A little sneaky, static_cast to the incorrect type will be rejected in a constexpr
    // evaluation. May change to _klass->is_ary() when C++20 is available.
    assert(_klass != nullptr && static_cast<const ciArrayKlassMirror*>(_klass) != nullptr, "must be an array");
    switch (_idx) {
      case 0:
      case 1:
        return 1;
      case 2:
        return 2;
      case 3:
        return 0;
      case 4:
      case 5:
        return 3;
      default:
        ShouldNotReachHere();
    }
  }

  constexpr bool operator==(const InstanceMirror& o) const {
    return _klass == o._klass && _idx == o._idx;
  }

  constexpr bool operator!=(const InstanceMirror& o) const {
    return !(*this == o);
  }

  constexpr bool operator!=(std::nullptr_t) const {
    return _klass != nullptr;
  }
};

template <class PtrType>
class AryElemType {
private:
  static constexpr size_t _1d_samples_size = ciInstanceKlassMirror::_samples.size() + 5;

  BasicType _bt;
  const PtrType* _ptr_type;

public:
  static const AryElemType* const TOP;
  static const AryElemType* const BOTTOM;

  constexpr AryElemType(BasicType bt, const PtrType* ptr_type) : _bt(bt), _ptr_type(ptr_type) {
    assert((bt == T_OBJECT) == (ptr_type != nullptr), "");
  }

  constexpr AryElemType() : AryElemType(T_ILLEGAL, nullptr) {}

  static constexpr const AryElemType* find(BasicType bt, const PtrType* ptr_type) {
    auto res = find_1d(bt, ptr_type);
    if (res != nullptr) {
      return res;
    }

    res = find_2d(bt, ptr_type);
    assert(res != nullptr, "must find an instance");
    return res;
  }

  static constexpr const AryElemType* find_1d(BasicType bt, const PtrType* ptr_type);
  static constexpr const AryElemType* find_2d(BasicType bt, const PtrType* ptr_type);

  constexpr BasicType      basic_type() const { return _bt; }
  constexpr bool           empty()      const { return _bt == T_ILLEGAL; }
  constexpr const PtrType* make_ptr()   const { return _ptr_type; }

  constexpr Type::TYPES base() const {
    if (_bt == T_VOID) {
      return Type::Bottom;
    } else if (_bt == T_ILLEGAL) {
      ShouldNotReachHere();
    } else if (_bt == T_BYTE || _bt == T_INT) {
      return Type::Int;
    } else if (_bt == T_FLOAT) {
      return Type::FloatBot;
    } else {
      assert(_bt == T_OBJECT, "unexpected BasicType %s", type2name(_bt));
      assert(_ptr_type != nullptr, "must have element type info");
      return _ptr_type->base();
    }
  }

  const AryElemType* meet(const AryElemType* other) const { return meet_speculative(other); }
  const AryElemType* join(const AryElemType* other) const { return join_speculative(other); }

  const AryElemType* meet_speculative(const AryElemType* other) const {
    if (_bt != other->_bt) {
      return BOTTOM;
    } else if (_bt != T_OBJECT) {
      return this;
    }

    return find(T_OBJECT, TypeJavaPtrMeetHelper::javaptr_type_xmeet(_ptr_type, other->_ptr_type));
  }

  const AryElemType* join_speculative(const AryElemType* other) const {
    if (_bt != other->_bt) {
      return TOP;
    } else if (_bt != T_OBJECT) {
      return this;
    }

    auto ptr_type = TypeJavaPtrJoinHelper::javaptr_type_xjoin(_ptr_type, other->_ptr_type);
    if (TypePtr::PTR ptr_ptr = ptr_type->ptr(); ptr_ptr == TypePtr::TopPTR || ptr_ptr == TypePtr::Null) {
      return TOP;
    }

    return find(T_OBJECT, static_cast<const PtrType*>(ptr_type));
  }
};

class TypePtrMirror {
private:
  static constexpr size_t _samples_size = 8;
  Type::TYPES _base;
  TypePtr::PTR _ptr;
  Type::Offset _offset;

  static const std::array<TypePtrMirror, _samples_size> _samples;

protected:
  constexpr TypePtrMirror(Type::TYPES base, TypePtr::PTR ptr, Type::Offset offset) : _base(base), _ptr(ptr), _offset(offset) {}

  void dump_ptr(outputStream& st) const {
    if (ptr() == TypePtr::BotPTR) {
      st.print("BotPTR");
    } else if (ptr() == TypePtr::NotNull) {
      st.print("NotNull");
    } else if (ptr() == TypePtr::Constant) {
      st.print("Constant");
    } else if (ptr() == TypePtr::Null) {
      st.print("Null");
    } else {
      assert(ptr() == TypePtr::TopPTR, "unexpected ptr %d", int(ptr()));
      st.print("TopPTR");
    }
  }

  void dump_offset(outputStream& st) const {
    if (offset() == Type::OffsetBot) {
      st.print("offset=bot");
    } else if (offset() == Type::OffsetTop) {
      st.print("offset=top");
    } else {
      st.print("offset=%d", offset());
    }
  }

public:
  using ciEnv = ciEnvMirror;
  using PtrType = TypePtrMirror;

  static const TypePtrMirror* make(Type::TYPES base, TypePtr::PTR ptr, Type::Offset offset, const TypePtrMirror* speculative = nullptr, int inline_depth = 0) {
    assert(base == Type::AnyPtr, "unexpected base %d", int(base));
    assert(speculative == nullptr && inline_depth == 0, "unsupported");
    for (auto& sample : _samples) {
      if (sample.ptr() == ptr && sample.offset() == offset.get()) {
        return &sample;
      }
    }

    ShouldNotReachHere();
  }

  constexpr Type::TYPES          base()          const { return _base; }
  constexpr TypePtr::PTR         ptr()           const { return _ptr; }
  constexpr int                  offset()        const { return _offset.get(); }
  constexpr TypePtr::FlatInArray flat_in_array() const { return TypePtr::NotFlat; }
  constexpr bool                 empty()         const { return ptr() == TypePtr::TopPTR; }

  const TypePtrMirror* meet(const TypePtrMirror* o) const { ShouldNotReachHere(); }
  const TypePtrMirror* join(const TypePtrMirror* o) const { ShouldNotReachHere(); }
  const TypePtrMirror* is_ptr()                     const { ShouldNotReachHere(); }

  virtual const TypeOopPtrMirror*   is_oopptr()   const { ShouldNotReachHere(); }
  virtual const TypeKlassPtrMirror* is_klassptr() const { ShouldNotReachHere(); }

  virtual void dump_on(outputStream& st) const {
    st.print("AnyPtr:");
    dump_ptr(st);
    st.print(" - ");
    dump_offset(st);
  }
};

constexpr std::array<TypePtrMirror, TypePtrMirror::_samples_size> TypePtrMirror::_samples = {
  TypePtrMirror(Type::AnyPtr, TypePtr::TopPTR, Type::Offset::bottom),
  TypePtrMirror(Type::AnyPtr, TypePtr::TopPTR, Type::Offset(0)),
  TypePtrMirror(Type::AnyPtr, TypePtr::TopPTR, Type::Offset(1)),
  TypePtrMirror(Type::AnyPtr, TypePtr::TopPTR, Type::Offset::top),
  TypePtrMirror(Type::AnyPtr, TypePtr::Null, Type::Offset::bottom),
  TypePtrMirror(Type::AnyPtr, TypePtr::Null, Type::Offset(0)),
  TypePtrMirror(Type::AnyPtr, TypePtr::Null, Type::Offset(1)),
  TypePtrMirror(Type::AnyPtr, TypePtr::Null, Type::Offset::top),
};

class TypeOopPtrMirror : public TypePtrMirror {
private:
  InterfaceSet _interfaces;
  int _instance_id;
  InstanceMirror _const_oop;
  bool _klass_is_exact;

protected:
  constexpr TypeOopPtrMirror(Type::TYPES base, TypePtr::PTR ptr, InterfaceSet interfaces, InstanceMirror const_oop, bool klass_is_exact, Type::Offset offset, int instance_id)
    : TypePtrMirror(base, ptr, offset), _interfaces(interfaces), _instance_id(instance_id), _const_oop(const_oop), _klass_is_exact(klass_is_exact) {}

  void dump_instance(outputStream& st) const {
    st.print("const_oop=");
    if (const_oop() == nullptr) {
      st.print("null");
    } else {
      st.print("%d", const_oop().idx());
    }
    st.print(" - instance_id=");
    if (instance_id() == TypeOopPtr::InstanceBot) {
      st.print("bot");
    } else {
      st.print("%d", instance_id());
    }
  }

public:
  static constexpr bool is_oopptr_type = true;
  using InstType = TypeInstPtrMirror;
  using AryType = TypeAryPtrMirror;

  constexpr InterfaceSet         interfaces()     const { return _interfaces; }
  constexpr InstanceMirror       const_oop()      const { return _const_oop; }
  constexpr bool                 klass_is_exact() const { return _klass_is_exact; }
  constexpr int                  instance_id()    const { return _instance_id; }
  constexpr const TypePtrMirror* speculative()    const { return nullptr; }
  constexpr int                  inline_depth()   const { return 0; }

  virtual const TypeOopPtrMirror*  is_oopptr()  const override { return this; }
  virtual const TypeInstPtrMirror* is_instptr() const = 0;
  virtual const TypeAryPtrMirror*  is_aryptr()  const = 0;

  virtual void dump_on(outputStream& st) const override = 0;
};

static constexpr size_t TypeInstPtr_samples_size() {
  size_t res = 0;
  for (auto& ci_klass : ciInstanceKlassMirror::_samples) {
    if (ci_klass.is_interface()) {
      continue;
    }

    // The constant instance
    res += ci_klass.is_loaded() ? 2 : 0;

    // The exact-klass instances, with different instance_id values
    res += ci_klass.is_loaded() ? 4 : 0;

    // The non-exact-klass instances
    if (ci_klass.interfaces()._i0 && ci_klass.interfaces()._i1) {
      res += 2;
    } else if (ci_klass.interfaces()._i0 || ci_klass.interfaces()._i1) {
      res += 4;
    } else {
      res += 8;
    }
  }

  // Different offset values
  return res * 3;
}

class TypeInstPtrMirror : public TypeOopPtrMirror {
private:
  const ciInstanceKlassMirror* _klass;

  constexpr TypeInstPtrMirror(TypePtr::PTR ptr, const ciInstanceKlassMirror& klass, InterfaceSet interfaces, bool klass_is_exact,
                              InstanceMirror const_oop, Type::Offset offset, int instance_id)
    : TypeOopPtrMirror(Type::InstPtr, ptr, interfaces, const_oop, klass_is_exact, offset, instance_id),
      _klass(&klass) {
    assert(!klass.is_interface(), "");
  }

  static constexpr auto generate_samples();

public:
  static const std::array<TypeInstPtrMirror, TypeInstPtr_samples_size()> _samples;

  constexpr TypeInstPtrMirror()
    : TypeOopPtrMirror(Type::Bad, TypePtr::TopPTR, InterfaceSet(false, false), nullptr, false, Type::Offset(0), 0),
      _klass(nullptr) {}

  static constexpr const TypeInstPtrMirror* make(TypePtr::PTR ptr, const ciInstanceKlassMirror* klass, InterfaceSet interfaces, bool klass_is_exact,
                                                 InstanceMirror const_oop = nullptr, Type::Offset offset = Type::Offset(0), TypePtr::FlatInArray flat_in_array = TypePtr::NotFlat,
                                                 int instance_id = TypeOopPtr::InstanceBot, const TypePtrMirror* speculative = nullptr, int inline_depth = 0) {
    assert(flat_in_array == TypePtr::NotFlat && speculative == nullptr && inline_depth == 0, "unsupported");
    for (auto& sample : _samples) {
      if (sample.ptr() == ptr && sample.instance_klass() == klass && sample.interfaces() == interfaces && sample.klass_is_exact() == klass_is_exact &&
          sample.const_oop() == const_oop && sample.offset() == offset.get() && sample.instance_id() == instance_id) {
        return &sample;
      }
    }

    ShouldNotReachHere();
  }

  constexpr const ciInstanceKlassMirror* instance_klass() const { return _klass; }

  virtual const TypeInstPtrMirror* is_instptr() const override { return this; }
  virtual const TypeAryPtrMirror*  is_aryptr()  const override { ShouldNotReachHere(); }

  virtual void dump_on(outputStream& st) const override {
    st.print("InstPtr:");
    dump_ptr(st);
    st.print(" - ");
    instance_klass()->dump_on(st);
    st.print("(");
    interfaces().dump_on(st);
    st.print(") - klass_is_exact=%d - ", klass_is_exact());
    dump_instance(st);
    st.print(" - ");
    dump_offset(st);
  }
};

constexpr auto TypeInstPtrMirror::generate_samples()  {
  std::array<TypeInstPtrMirror, TypeInstPtr_samples_size()> res;
  size_t sample_idx = 0;
  auto fill_result = [&](TypePtr::PTR ptr, const ciInstanceKlassMirror& ci_klass, InterfaceSet interfaces, bool klass_is_exact, InstanceMirror const_oop, int instance_id) {
    res[sample_idx] = TypeInstPtrMirror(ptr, ci_klass, interfaces, klass_is_exact, const_oop, Type::Offset::bottom, instance_id);
    sample_idx++;
    res[sample_idx] = TypeInstPtrMirror(ptr, ci_klass, interfaces, klass_is_exact, const_oop, Type::Offset(0), instance_id);
    sample_idx++;
    res[sample_idx] = TypeInstPtrMirror(ptr, ci_klass, interfaces, klass_is_exact, const_oop, Type::Offset(1), instance_id);
    sample_idx++;
  };

  for (auto& ci_klass : ciInstanceKlassMirror::_samples) {
    if (ci_klass.is_interface()) {
      continue;
    }

    InterfaceSet interfaces = ci_klass.interfaces();
    if (ci_klass.is_loaded()) {
      fill_result(TypePtr::Constant, ci_klass, interfaces, true, InstanceMirror(ci_klass, 0), TypeOopPtr::InstanceBot);
      fill_result(TypePtr::Constant, ci_klass, interfaces, true, InstanceMirror(ci_klass, 1), TypeOopPtr::InstanceBot);
      fill_result(TypePtr::BotPTR, ci_klass, interfaces, true, nullptr, TypeOopPtr::InstanceBot);
      fill_result(TypePtr::NotNull, ci_klass, interfaces, true, nullptr, TypeOopPtr::InstanceBot);
      fill_result(TypePtr::NotNull, ci_klass, interfaces, true, nullptr, ci_klass.instance_id());
      fill_result(TypePtr::NotNull, ci_klass, interfaces, true, nullptr, ci_klass.instance_id() + 1);
    }

    fill_result(TypePtr::BotPTR, ci_klass, interfaces, false, nullptr, TypeOopPtr::InstanceBot);
    fill_result(TypePtr::NotNull, ci_klass, interfaces, false, nullptr, TypeOopPtr::InstanceBot);
    if (!interfaces._i0) {
      fill_result(TypePtr::BotPTR, ci_klass, InterfaceSet(true, interfaces._i1), false, nullptr, TypeOopPtr::InstanceBot);
      fill_result(TypePtr::NotNull, ci_klass, InterfaceSet(true, interfaces._i1), false, nullptr, TypeOopPtr::InstanceBot);
    }
    if (!interfaces._i1) {
      fill_result(TypePtr::BotPTR, ci_klass, InterfaceSet(interfaces._i0, true), false, nullptr, TypeOopPtr::InstanceBot);
      fill_result(TypePtr::NotNull, ci_klass, InterfaceSet(interfaces._i0, true), false, nullptr, TypeOopPtr::InstanceBot);
    }
    if (!interfaces._i0 && !interfaces._i1) {
      fill_result(TypePtr::BotPTR, ci_klass, InterfaceSet(true, true), false, nullptr, TypeOopPtr::InstanceBot);
      fill_result(TypePtr::NotNull, ci_klass, InterfaceSet(true, true), false, nullptr, TypeOopPtr::InstanceBot);
    }
  }

  assert(sample_idx == res.size(), "");
  return res;
}

constexpr std::array<TypeInstPtrMirror, TypeInstPtr_samples_size()> TypeInstPtrMirror::_samples = generate_samples();

class ArySizeType {
private:
  friend class TypeAryPtrMirror;

  bool _empty;
  int _lo;
  int _hi;

  constexpr ArySizeType(bool empty, int lo, int hi) : _empty(empty), _lo(lo), _hi(hi) {
    assert((!empty && lo <= hi) || (empty && lo == 0 && hi == 0), "invariant");
    assert(lo >= 0 && hi <= 3, "constraint");
  }

public:
  static const ArySizeType BOTTOM;

  ArySizeType meet(ArySizeType other) const {
    assert(!_empty && !other._empty, "must not have empty type here");
    return ArySizeType(false, MIN2(_lo, other._lo), MAX2(_hi, other._hi));
  }

  ArySizeType join(ArySizeType other) const {
    assert(!_empty && !other._empty, "must not have empty type here");
    int lo = MAX2(_lo, other._lo);
    int hi = MIN2(_hi, other._hi);
    if (lo > hi) {
      return ArySizeType(true, 0, 0);
    } else {
      return ArySizeType(false, lo, hi);
    }
  }

  const ArySizeType* operator->() const { return this; }
  const ArySizeType& is_int()     const { assert(!_empty, "must not be empty"); return *this; }
  const ArySizeType& isa_int()    const { return *this; }

  constexpr bool contains(int len) const {
    assert(0 <= len && len <= 3, "unsupported");
    return _lo <= len && len <= _hi;
  }

  constexpr bool operator==(const ArySizeType& other) const {
    return _empty == other._empty && _lo == other._lo && _hi == other._hi;
  }

  constexpr bool operator!=(const ArySizeType& other) const {
    return !(*this == other);
  }

  constexpr bool operator==(std::nullptr_t) const {
    return _empty;
  }

  constexpr bool operator!=(std::nullptr_t) const {
    return !_empty;
  }
};

constexpr ArySizeType ArySizeType::BOTTOM(false, 0, 3);

class TypeAryMirror {
private:
  friend class TypeAryPtrMirror;

  const AryElemType<TypeOopPtrMirror>* _elem;
  ArySizeType _size;

public:
  constexpr TypeAryMirror(const AryElemType<TypeOopPtrMirror>* elem, ArySizeType size) : _elem(elem), _size(size) {
    assert(size != nullptr, "");
  }

  static TypeAryMirror make(const AryElemType<TypeOopPtrMirror>* elem, ArySizeType size, bool is_stable, bool flat, bool not_flat, bool null_free, bool not_null_free, bool atomic) {
    assert(!is_stable && !flat && not_flat && !null_free && not_null_free && atomic, "unsupported");
    return TypeAryMirror(elem, size);
  }
};

static constexpr size_t TypeAryPtr_1d_elem_samples_size() {
  size_t res = 0;
  // top, bot, byte, int, float
  res += 5;
  for (auto& ci_klass : ciInstanceKlassMirror::_samples) {
    if (ci_klass.is_interface()) {
      continue;
    }

    if (ci_klass.interfaces()._i0 && ci_klass.interfaces()._i1) {
      res += 1;
    } else if (ci_klass.interfaces()._i0 || ci_klass.interfaces()._i1) {
      res += 2;
    } else {
      res += 4;
    }
  }

  return res;
}

static constexpr size_t TypeAryPtr_1d_exact_samples_size() {
  size_t res = 0;
  // byte[], int[], float[]
  res += 3;
  for (auto& ci_klass : ciInstanceKlassMirror::_samples) {
    if (ci_klass.is_loaded()) {
      res += 1;
    }
  }

  return res;
}

static constexpr size_t TypeAryPtr_1d_nonexact_samples_size() {
  size_t res = 0;
  // bot[]
  res += 1;
  for (auto& ci_klass : ciInstanceKlassMirror::_samples) {
    if (ci_klass.is_interface()) {
      continue;
    }

    if (ci_klass.interfaces()._i0 && ci_klass.interfaces()._i1) {
      res += 1;
    } else if (ci_klass.interfaces()._i0 || ci_klass.interfaces()._i1) {
      res += 2;
    } else {
      res += 4;
    }
  }

  return res;
}

// All test instances have is_stable() == false, is_auto_box_cache() == false, is_flat() == false,
// is_not_flat() == true, is_null_free() == false, is_not_null_free() == true, is_atomic() == true,
// field_offset() == Offset::bottom. All other parameters are included exhaustively.
class TypeAryPtrMirror : public TypeOopPtrMirror {
private:
  static constexpr size_t _1d_elem_samples_size = TypeAryPtr_1d_elem_samples_size();
  static constexpr size_t _1d_samples_size = (TypeAryPtr_1d_exact_samples_size() * 15 + TypeAryPtr_1d_nonexact_samples_size() * 10) * 3;
  static constexpr size_t _2d_elem_samples_size = TypeAryPtr_1d_nonexact_samples_size() + 3;
  static constexpr size_t _2d_samples_size = _1d_samples_size;

  TypeAryMirror _ary;
  const ciArrayKlassMirror* _klass;

  constexpr TypeAryPtrMirror(TypePtr::PTR ptr, InstanceMirror const_oop, const TypeAryMirror& ary,
                             const ciArrayKlassMirror* klass, bool klass_is_exact, Type::Offset offset, int instance_id)
    : TypeOopPtrMirror(TypePtr::AryPtr, ptr, _array_interfaces, const_oop, klass_is_exact, offset, instance_id),
      _ary(ary), _klass(klass) {
    assert(ary._elem != nullptr, "");
  }

  template <class R>
  static constexpr void fill_samples_helper(R& res, size_t& sample_idx, TypePtr::PTR ptr, InstanceMirror const_oop, const AryElemType<TypeOopPtrMirror>* elem,
                                            const ciArrayKlassMirror* klass, bool klass_is_exact, int instance_id, Type::Offset offset);

  static constexpr auto generate_1d_elem_samples();
  static constexpr auto generate_1d_samples();
  static constexpr auto generate_2d_elem_samples();
  static constexpr auto generate_2d_samples();

public:
  static constexpr InterfaceSet _array_interfaces = InterfaceSet(false, true);
  static const std::array<AryElemType<TypeOopPtrMirror>, _1d_elem_samples_size> _1d_elem_samples;
  static const std::array<TypeAryPtrMirror, _1d_samples_size> _1d_samples;
  static const std::array<AryElemType<TypeOopPtrMirror>, _2d_elem_samples_size> _2d_elem_samples;
  static const std::array<TypeAryPtrMirror, _2d_samples_size> _2d_samples;

  constexpr TypeAryPtrMirror()
    : TypeOopPtrMirror(Type::Bad, TypePtr::TopPTR, _array_interfaces, nullptr, false, Type::Offset(0), 0),
      _ary(nullptr, ArySizeType(false, 0, 0)), _klass(nullptr) {}

  static constexpr const TypeAryPtrMirror* make(TypePtr::PTR ptr, InstanceMirror const_oop, const TypeAryMirror& ary, const ciArrayKlassMirror* klass, bool klass_is_exact,
                                                Type::Offset offset, Type::Offset field_offset, int instance_id, const TypePtrMirror* speculative = nullptr, int inline_depth = 0, bool is_autobox_cache = false) {
    assert(field_offset == Type::Offset::bottom && speculative == nullptr && inline_depth == 0 && !is_autobox_cache, "unsupported");
    auto match = [&](const TypeAryPtrMirror& sample) {
      return sample.ptr() == ptr && sample.const_oop() == const_oop && sample.elem() == ary._elem && sample.size() == ary._size &&
             sample.klass() == klass && sample.klass_is_exact() == klass_is_exact && sample.offset() == offset.get() && sample.instance_id() == instance_id;
    };

    for (auto& sample : _1d_samples) {
      if (match(sample)) {
        return &sample;
      }
    }

    for (auto& sample : _2d_samples) {
      if (match(sample)) {
        return &sample;
      }
    }

    ShouldNotReachHere();
  }

  constexpr const TypeAryMirror                  ary()              const { return _ary; }
  constexpr const AryElemType<TypeOopPtrMirror>* elem()             const { return _ary._elem; }
  constexpr ArySizeType                          size()             const { return _ary._size; }
  constexpr bool                                 is_stable()        const { return false; }
  constexpr const ciArrayKlassMirror*            klass()            const { return _klass; }
  constexpr bool                                 is_autobox_cache() const { return false; }

  bool is_flat()          const { return false; }
  bool is_not_flat()      const { return true; }
  bool is_null_free()     const { return false; }
  bool is_not_null_free() const { return true; }
  bool is_atomic()        const { return true; }
  Type::Offset field_offset() const { return Type::Offset::bottom; }

  virtual const TypeInstPtrMirror* is_instptr() const override { ShouldNotReachHere(); }
  virtual const TypeAryPtrMirror*  is_aryptr()  const override { return this; }

  virtual void dump_on(outputStream& st) const override {
    st.print("AryPtr:");
    dump_ptr(st);
    st.print(" - ");
    if (elem()->base() == Type::Bottom) {
      st.print("bot");
    } else if (elem()->basic_type() != T_OBJECT) {
      st.print("%s", type2name(elem()->basic_type()));
    } else {
      st.print("(");
      elem()->make_ptr()->dump_on(st);
      st.print(")");
    }
    st.print("[%d - %d] - klass_is_exact=%d - ", size()._lo, size()._hi, klass_is_exact());
    dump_instance(st);
    st.print(" - ");
    dump_offset(st);
  }
};

template <>
constexpr const AryElemType<TypeOopPtrMirror>* AryElemType<TypeOopPtrMirror>::TOP = &TypeAryPtrMirror::_1d_elem_samples[0];

template <>
constexpr const AryElemType<TypeOopPtrMirror>* AryElemType<TypeOopPtrMirror>::BOTTOM = &TypeAryPtrMirror::_1d_elem_samples[1];

template <>
constexpr const AryElemType<TypeOopPtrMirror>* AryElemType<TypeOopPtrMirror>::find_1d(BasicType bt, const TypeOopPtrMirror* ptr_type) {
  for (auto& sample : TypeAryPtrMirror::_1d_elem_samples) {
    if (sample._bt == bt && sample._ptr_type == ptr_type) {
      return &sample;
    }
  }
  return nullptr;
}

template <>
constexpr const AryElemType<TypeOopPtrMirror>* AryElemType<TypeOopPtrMirror>::find_2d(BasicType bt, const TypeOopPtrMirror* ptr_type) {
  for (auto& sample : TypeAryPtrMirror::_2d_elem_samples) {
    if (sample._bt == bt && sample._ptr_type == ptr_type) {
      return &sample;
    }
  }
  return nullptr;
}

constexpr auto TypeAryPtrMirror::generate_1d_elem_samples() {
  std::array<AryElemType<TypeOopPtrMirror>, _1d_elem_samples_size> res;
  res[0] = AryElemType<TypeOopPtrMirror>(T_ILLEGAL, nullptr);
  res[1] = AryElemType<TypeOopPtrMirror>(T_VOID, nullptr);
  res[2] = AryElemType<TypeOopPtrMirror>(T_BYTE, nullptr);
  res[3] = AryElemType<TypeOopPtrMirror>(T_INT, nullptr);
  res[4] = AryElemType<TypeOopPtrMirror>(T_FLOAT, nullptr);

  size_t sample_idx = 5;
  for (auto& ci_klass : ciInstanceKlassMirror::_samples) {
    if (ci_klass.is_interface()) {
      continue;
    }

    InterfaceSet interfaces = ci_klass.interfaces();
    res[sample_idx] = AryElemType<TypeOopPtrMirror>(T_OBJECT, TypeInstPtrMirror::make(TypePtr::BotPTR, &ci_klass, interfaces, false));
    sample_idx++;
    if (!interfaces._i0) {
      res[sample_idx] = AryElemType<TypeOopPtrMirror>(T_OBJECT, TypeInstPtrMirror::make(TypePtr::BotPTR, &ci_klass, InterfaceSet(true, interfaces._i1), false));
      sample_idx++;
    }
    if (!interfaces._i1) {
      res[sample_idx] = AryElemType<TypeOopPtrMirror>(T_OBJECT, TypeInstPtrMirror::make(TypePtr::BotPTR, &ci_klass, InterfaceSet(interfaces._i0, true), false));
      sample_idx++;
    }
    if (!interfaces._i0 && !interfaces._i1) {
      res[sample_idx] = AryElemType<TypeOopPtrMirror>(T_OBJECT, TypeInstPtrMirror::make(TypePtr::BotPTR, &ci_klass, InterfaceSet(true, true), false));
      sample_idx++;
    }
  }

  assert(sample_idx == res.size(), "");
  return res;
}

constexpr std::array<AryElemType<TypeOopPtrMirror>, TypeAryPtrMirror::_1d_elem_samples_size> TypeAryPtrMirror::_1d_elem_samples = generate_1d_elem_samples();

template <class R>
constexpr void TypeAryPtrMirror::fill_samples_helper(R& res, size_t& sample_idx, TypePtr::PTR ptr, InstanceMirror const_oop, const AryElemType<TypeOopPtrMirror>* elem,
                                                     const ciArrayKlassMirror* klass, bool klass_is_exact, int instance_id, Type::Offset offset) {
  if (const_oop != nullptr) {
    TypeAryMirror ary(elem, ArySizeType(false, const_oop.array_length(), const_oop.array_length()));
    res[sample_idx] = TypeAryPtrMirror(ptr, const_oop, ary, klass, klass_is_exact, offset, instance_id);
    sample_idx++;
  } else if (instance_id != TypeOopPtr::InstanceBot) {
    // Each klass is reserved 3 distinct instance_id, so we try to infer whether which instance this
    // is by finding its remainder modulo 3
    if (instance_id % 3 == 1) {
      TypeAryMirror ary(elem, ArySizeType(false, 0, 1));
      res[sample_idx] = TypeAryPtrMirror(ptr, const_oop, ary, klass, klass_is_exact, offset, instance_id);
      sample_idx++;
      ary = TypeAryMirror(elem, ArySizeType(false, 1, 1));
      res[sample_idx] = TypeAryPtrMirror(ptr, const_oop, ary, klass, klass_is_exact, offset, instance_id);
      sample_idx++;
    } else {
      TypeAryMirror ary(elem, ArySizeType(false, 2, 3));
      res[sample_idx] = TypeAryPtrMirror(ptr, const_oop, ary, klass, klass_is_exact, offset, instance_id);
      sample_idx++;
    }
  } else {
    TypeAryMirror ary(elem, ArySizeType(false, 0, 1));
    res[sample_idx] = TypeAryPtrMirror(ptr, const_oop, ary, klass, klass_is_exact, offset, instance_id);
    sample_idx++;
    ary = TypeAryMirror(elem, ArySizeType(false, 1, 1));
    res[sample_idx] = TypeAryPtrMirror(ptr, const_oop, ary, klass, klass_is_exact, offset, instance_id);
    sample_idx++;

    ary = TypeAryMirror(elem, ArySizeType(false, 0, 3));
    res[sample_idx] = TypeAryPtrMirror(ptr, const_oop, ary, klass, klass_is_exact, offset, instance_id);
    sample_idx++;
    ary = TypeAryMirror(elem, ArySizeType(false, 1, 3));
    res[sample_idx] = TypeAryPtrMirror(ptr, const_oop, ary, klass, klass_is_exact, offset, instance_id);
    sample_idx++;
    ary = TypeAryMirror(elem, ArySizeType(false, 2, 3));
    res[sample_idx] = TypeAryPtrMirror(ptr, const_oop, ary, klass, klass_is_exact, offset, instance_id);
    sample_idx++;
  }
}

constexpr auto TypeAryPtrMirror::generate_1d_samples() {
  std::array<TypeAryPtrMirror, _1d_samples_size> res;
  size_t sample_idx = 0;
  auto fill_result = [&](TypePtr::PTR ptr, InstanceMirror const_oop, const AryElemType<TypeOopPtrMirror>* elem,
                                   const ciArrayKlassMirror* klass, bool klass_is_exact, int instance_id) {
    fill_samples_helper(res, sample_idx, ptr, const_oop, elem, klass, klass_is_exact, instance_id, Type::Offset::bottom);
    fill_samples_helper(res, sample_idx, ptr, const_oop, elem, klass, klass_is_exact, instance_id, Type::Offset(0));
    fill_samples_helper(res, sample_idx, ptr, const_oop, elem, klass, klass_is_exact, instance_id, Type::Offset(1));
  };

  auto bot_ary_elem = AryElemType<TypeOopPtrMirror>::find_1d(T_VOID, nullptr);
  fill_result(TypePtr::BotPTR, nullptr, bot_ary_elem, nullptr, false, TypeOopPtr::InstanceBot);
  fill_result(TypePtr::NotNull, nullptr, bot_ary_elem, nullptr, false, TypeOopPtr::InstanceBot);

  auto byte_ary_elem = AryElemType<TypeOopPtrMirror>::find_1d(T_BYTE, nullptr);
  auto& byte_ary_klass = ciArrayKlassMirror::find(T_BYTE, nullptr);
  fill_result(TypePtr::BotPTR, nullptr, byte_ary_elem, &byte_ary_klass, true, TypeOopPtr::InstanceBot);
  fill_result(TypePtr::NotNull, nullptr, byte_ary_elem, &byte_ary_klass, true, TypeOopPtr::InstanceBot);
  fill_result(TypePtr::NotNull, nullptr, byte_ary_elem, &byte_ary_klass, true, byte_ary_klass.instance_id());
  fill_result(TypePtr::NotNull, nullptr, byte_ary_elem, &byte_ary_klass, true, byte_ary_klass.instance_id() + 1);
  fill_result(TypePtr::Constant, InstanceMirror(byte_ary_klass, 0), byte_ary_elem, &byte_ary_klass, true, TypeOopPtr::InstanceBot);
  fill_result(TypePtr::Constant, InstanceMirror(byte_ary_klass, 1), byte_ary_elem, &byte_ary_klass, true, TypeOopPtr::InstanceBot);

  auto int_ary_elem = AryElemType<TypeOopPtrMirror>::find_1d(T_INT, nullptr);
  auto& int_ary_klass = ciArrayKlassMirror::find(T_INT, nullptr);
  fill_result(TypePtr::BotPTR, nullptr, int_ary_elem, &int_ary_klass, true, TypeOopPtr::InstanceBot);
  fill_result(TypePtr::NotNull, nullptr, int_ary_elem, &int_ary_klass, true, TypeOopPtr::InstanceBot);
  fill_result(TypePtr::NotNull, nullptr, int_ary_elem, &int_ary_klass, true, int_ary_klass.instance_id());
  fill_result(TypePtr::NotNull, nullptr, int_ary_elem, &int_ary_klass, true, int_ary_klass.instance_id() + 1);
  fill_result(TypePtr::Constant, InstanceMirror(int_ary_klass, 0), int_ary_elem, &int_ary_klass, true, TypeOopPtr::InstanceBot);
  fill_result(TypePtr::Constant, InstanceMirror(int_ary_klass, 1), int_ary_elem, &int_ary_klass, true, TypeOopPtr::InstanceBot);

  auto float_ary_elem = AryElemType<TypeOopPtrMirror>::find_1d(T_FLOAT, nullptr);
  auto& float_ary_klass = ciArrayKlassMirror::find(T_FLOAT, nullptr);
  fill_result(TypePtr::BotPTR, nullptr, float_ary_elem, nullptr, true, TypeOopPtr::InstanceBot);
  fill_result(TypePtr::NotNull, nullptr, float_ary_elem, nullptr, true, TypeOopPtr::InstanceBot);
  fill_result(TypePtr::NotNull, nullptr, float_ary_elem, nullptr, true, float_ary_klass.instance_id());
  fill_result(TypePtr::NotNull, nullptr, float_ary_elem, nullptr, true, float_ary_klass.instance_id() + 1);
  fill_result(TypePtr::Constant, InstanceMirror(float_ary_klass, 0), float_ary_elem, nullptr, true, TypeOopPtr::InstanceBot);
  fill_result(TypePtr::Constant, InstanceMirror(float_ary_klass, 1), float_ary_elem, nullptr, true, TypeOopPtr::InstanceBot);

  for (auto& ci_klass : ciInstanceKlassMirror::_samples) {
    if (ci_klass.is_loaded()) {
      auto& ci_instance_klass = ci_klass.is_interface() ? *ciEnvMirror::current()->Object_klass() : ci_klass;
      auto elem_ptr_type = TypeInstPtrMirror::make(TypePtr::BotPTR, &ci_instance_klass, ci_klass.interfaces(), false);
      auto elem = AryElemType<TypeOopPtrMirror>::find_1d(T_OBJECT, elem_ptr_type);
      fill_result(TypePtr::BotPTR, nullptr, elem, nullptr, true, TypeOopPtr::InstanceBot);
      fill_result(TypePtr::NotNull, nullptr, elem, nullptr, true, TypeOopPtr::InstanceBot);

      auto& ci_ary_klass = ciArrayKlassMirror::find(T_OBJECT, &ci_klass);
      fill_result(TypePtr::NotNull, nullptr, elem, nullptr, true, ci_ary_klass.instance_id());
      fill_result(TypePtr::NotNull, nullptr, elem, nullptr, true, ci_ary_klass.instance_id() + 1);

      InstanceMirror const_oop_0(ci_ary_klass, 0);
      fill_result(TypePtr::Constant, const_oop_0, elem, nullptr, true, TypeOopPtr::InstanceBot);
      InstanceMirror const_oop_1(ci_ary_klass, 1);
      fill_result(TypePtr::Constant, const_oop_1, elem, nullptr, true, TypeOopPtr::InstanceBot);
    }

    if (ci_klass.is_interface()) {
      continue;
    }

    InterfaceSet interfaces = ci_klass.interfaces();
    {
      auto elem_ptr_type = TypeInstPtrMirror::make(TypePtr::BotPTR, &ci_klass, interfaces, false);
      auto elem = AryElemType<TypeOopPtrMirror>::find_1d(T_OBJECT, elem_ptr_type);
      fill_result(TypePtr::BotPTR, nullptr, elem, nullptr, false, TypeOopPtr::InstanceBot);
      fill_result(TypePtr::NotNull, nullptr, elem, nullptr, false, TypeOopPtr::InstanceBot);
    }

    if (!interfaces._i0) {
      auto elem_ptr_type = TypeInstPtrMirror::make(TypePtr::BotPTR, &ci_klass, InterfaceSet(true, interfaces._i1), false);
      auto elem = AryElemType<TypeOopPtrMirror>::find_1d(T_OBJECT, elem_ptr_type);
      fill_result(TypePtr::BotPTR, nullptr, elem, nullptr, false, TypeOopPtr::InstanceBot);
      fill_result(TypePtr::NotNull, nullptr, elem, nullptr, false, TypeOopPtr::InstanceBot);
    }

    if (!interfaces._i1) {
      auto elem_ptr_type = TypeInstPtrMirror::make(TypePtr::BotPTR, &ci_klass, InterfaceSet(interfaces._i0, true), false);
      auto elem = AryElemType<TypeOopPtrMirror>::find_1d(T_OBJECT, elem_ptr_type);
      fill_result(TypePtr::BotPTR, nullptr, elem, nullptr, false, TypeOopPtr::InstanceBot);
      fill_result(TypePtr::NotNull, nullptr, elem, nullptr, false, TypeOopPtr::InstanceBot);
    }

    if (!interfaces._i0 && !interfaces._i1) {
      auto elem_ptr_type = TypeInstPtrMirror::make(TypePtr::BotPTR, &ci_klass, InterfaceSet(true, true), false);
      auto elem = AryElemType<TypeOopPtrMirror>::find_1d(T_OBJECT, elem_ptr_type);
      fill_result(TypePtr::BotPTR, nullptr, elem, nullptr, false, TypeOopPtr::InstanceBot);
      fill_result(TypePtr::NotNull, nullptr, elem, nullptr, false, TypeOopPtr::InstanceBot);
    }
  }

  assert(sample_idx == res.size(), "");
  return res;
}

constexpr std::array<TypeAryPtrMirror, TypeAryPtrMirror::_1d_samples_size> TypeAryPtrMirror::_1d_samples = generate_1d_samples();

constexpr auto TypeAryPtrMirror::generate_2d_elem_samples() {
  std::array<AryElemType<TypeOopPtrMirror>, _2d_elem_samples_size> res;
  size_t sample_idx = 0;

  for (auto& elem_ptr_type : _1d_samples) {
    if (elem_ptr_type.ptr() != TypePtr::BotPTR || elem_ptr_type.size() != ArySizeType::BOTTOM || elem_ptr_type.offset() != 0) {
      continue;
    }

    bool klass_is_exact = is_java_primitive(elem_ptr_type.elem()->basic_type());
    if (elem_ptr_type.klass_is_exact() != klass_is_exact) {
      continue;
    }

    res[sample_idx] = AryElemType<TypeOopPtrMirror>(T_OBJECT, &elem_ptr_type);
    sample_idx++;
  }

  assert(sample_idx == res.size(), "");
  return res;
}

constexpr std::array<AryElemType<TypeOopPtrMirror>, TypeAryPtrMirror::_2d_elem_samples_size> TypeAryPtrMirror::_2d_elem_samples = generate_2d_elem_samples();

constexpr auto TypeAryPtrMirror::generate_2d_samples() {
  std::array<TypeAryPtrMirror, _2d_samples_size> res;
  size_t sample_idx = 0;
  auto fill_result = [&](TypePtr::PTR ptr, InstanceMirror const_oop, const AryElemType<TypeOopPtrMirror>* elem,
                                   const ciArrayKlassMirror* klass, bool klass_is_exact, int instance_id) {
    fill_samples_helper(res, sample_idx, ptr, const_oop, elem, klass, klass_is_exact, instance_id, Type::Offset::bottom);
    fill_samples_helper(res, sample_idx, ptr, const_oop, elem, klass, klass_is_exact, instance_id, Type::Offset(0));
    fill_samples_helper(res, sample_idx, ptr, const_oop, elem, klass, klass_is_exact, instance_id, Type::Offset(1));
  };

  for (auto& sample : _1d_samples) {
    if (sample.ptr() == TypePtr::Constant && sample.const_oop().idx() == 0 && sample.offset() == 0) {
      bool klass_is_exact = is_java_primitive(sample.elem()->basic_type());
      TypeAryMirror ary(sample.elem(), ArySizeType::BOTTOM);
      auto elem_ptr_type = TypeAryPtrMirror::make(TypePtr::BotPTR, nullptr, ary, sample.klass(), klass_is_exact, Type::Offset(0), Type::Offset::bottom, TypeOopPtr::InstanceBot);
      auto elem = AryElemType<TypeOopPtrMirror>::find_2d(T_OBJECT, elem_ptr_type);
      fill_result(TypePtr::BotPTR, nullptr, elem, nullptr, true, TypeOopPtr::InstanceBot);
      fill_result(TypePtr::NotNull, nullptr, elem, nullptr, true, TypeOopPtr::InstanceBot);

      auto& ci_ary_klass = ciArrayKlassMirror::find(T_OBJECT, sample.const_oop().klass());
      fill_result(TypePtr::NotNull, nullptr, elem, nullptr, true, ci_ary_klass.instance_id());
      fill_result(TypePtr::NotNull, nullptr, elem, nullptr, true, ci_ary_klass.instance_id() + 1);

      InstanceMirror const_oop_0(ci_ary_klass, 0);
      fill_result(TypePtr::Constant, const_oop_0, elem, nullptr, true, TypeOopPtr::InstanceBot);
      InstanceMirror const_oop_1(ci_ary_klass, 1);
      fill_result(TypePtr::Constant, const_oop_1, elem, nullptr, true, TypeOopPtr::InstanceBot);
    }

    if (sample.ptr() == TypePtr::BotPTR && sample.size() == ArySizeType::BOTTOM && !sample.klass_is_exact() && sample.offset() == 0) {
      auto elem = AryElemType<TypeOopPtrMirror>::find_2d(T_OBJECT, &sample);
      fill_result(TypePtr::BotPTR, nullptr, elem, nullptr, false, TypeOopPtr::InstanceBot);
      fill_result(TypePtr::NotNull, nullptr, elem, nullptr, false, TypeOopPtr::InstanceBot);
    }
  }

  assert(sample_idx == res.size(), "");
  return res;
}

constexpr std::array<TypeAryPtrMirror, TypeAryPtrMirror::_2d_samples_size> TypeAryPtrMirror::_2d_samples = generate_2d_samples();

class TypeKlassPtrMirror : public TypePtrMirror {
private:
  InterfaceSet _interfaces;

protected:
  constexpr TypeKlassPtrMirror(Type::TYPES base, TypePtr::PTR ptr, InterfaceSet interfaces, Type::Offset offset)
    : TypePtrMirror(base, ptr, offset), _interfaces(interfaces) {}

public:
  static constexpr bool is_oopptr_type = false;
  using InstType = TypeInstKlassPtrMirror;
  using AryType = TypeAryKlassPtrMirror;

  constexpr InterfaceSet interfaces()     const { return _interfaces; }
  constexpr bool         klass_is_exact() const { return ptr() == TypePtr::Constant; }

  virtual const TypeKlassPtrMirror*     is_klassptr()     const { return this; }
  virtual const TypeInstKlassPtrMirror* is_instklassptr() const = 0;
  virtual const TypeAryKlassPtrMirror*  is_aryklassptr()  const = 0;
};

static constexpr size_t TypeInstKlassPtr_samples_size() {
  size_t res = 0;
  for (auto& ci_klass : ciInstanceKlassMirror::_samples) {
    if (!ci_klass.is_loaded() || ci_klass.is_interface()) {
      continue;
    }

    if (ci_klass.is_java_lang_Object()) {
      // j.l.O has 2 additional constant klass pointers corresponding to the interfaces I0 and I1
      res += 11;
      continue;
    }

    // The constant instance
    res += 1;

    // The non-exact-klass instances
    if (ci_klass.interfaces()._i0 && ci_klass.interfaces()._i1) {
      res += 2;
    } else if (ci_klass.interfaces()._i0 || ci_klass.interfaces()._i1) {
      res += 4;
    } else {
      res += 8;
    }
  }

  // Different offset values
  return res * 3;
}

class TypeInstKlassPtrMirror : public TypeKlassPtrMirror {
private:
  const ciInstanceKlassMirror* _klass;

  static constexpr auto generate_samples();

  constexpr TypeInstKlassPtrMirror(TypePtr::PTR ptr, const ciInstanceKlassMirror& klass, InterfaceSet interfaces, Type::Offset offset)
    : TypeKlassPtrMirror(Type::InstKlassPtr, ptr, interfaces, offset), _klass(&klass) {
    assert(klass.is_loaded() && !klass.is_interface(), "");
  }

public:
  static const std::array<TypeInstKlassPtrMirror, TypeInstKlassPtr_samples_size()> _samples;

  constexpr TypeInstKlassPtrMirror() : TypeKlassPtrMirror(Type::Bad, TypePtr::TopPTR, InterfaceSet(false, false), Type::Offset(0)), _klass(nullptr) {}

  static constexpr const TypeInstKlassPtrMirror* make(TypePtr::PTR ptr, const ciInstanceKlassMirror* klass, InterfaceSet interfaces, Type::Offset offset = Type::Offset(0),
                                                      TypePtr::FlatInArray flat_in_array = TypePtr::NotFlat) {
    assert(flat_in_array == TypePtr::NotFlat, "unsupported");
    for (auto& sample : _samples) {
      if (sample.ptr() == ptr && sample.instance_klass() == klass && sample.interfaces() == interfaces && sample.offset() == offset.get()) {
        return &sample;
      }
    }

    ShouldNotReachHere();
  }

  constexpr const ciInstanceKlassMirror* instance_klass() const { return _klass; }

  virtual const TypeInstKlassPtrMirror* is_instklassptr() const override { return this; }
  virtual const TypeAryKlassPtrMirror*  is_aryklassptr()  const override { ShouldNotReachHere(); }

  virtual void dump_on(outputStream& st) const override {
    st.print("InstKlassPtr:");
    dump_ptr(st);
    st.print(" - ");
    instance_klass()->dump_on(st);
    st.print("(");
    interfaces().dump_on(st);
    st.print(")");
    dump_offset(st);
  }
};

constexpr auto TypeInstKlassPtrMirror::generate_samples() {
  std::array<TypeInstKlassPtrMirror, TypeInstKlassPtr_samples_size()> res;
  size_t sample_idx = 0;
  auto fill_result = [&](TypePtr::PTR ptr, const ciInstanceKlassMirror& ci_klass, InterfaceSet interfaces) {
    res[sample_idx] = TypeInstKlassPtrMirror(ptr, ci_klass, interfaces, Type::Offset::bottom);
    sample_idx++;
    res[sample_idx] = TypeInstKlassPtrMirror(ptr, ci_klass, interfaces, Type::Offset(0));
    sample_idx++;
    res[sample_idx] = TypeInstKlassPtrMirror(ptr, ci_klass, interfaces, Type::Offset(1));
    sample_idx++;
  };

  for (auto& ci_klass : ciInstanceKlassMirror::_samples) {
    if (!ci_klass.is_loaded() || ci_klass.is_interface()) {
      continue;
    }

    if (ci_klass.is_java_lang_Object()) {
      fill_result(TypePtr::Constant, ci_klass, InterfaceSet(false, false));
      fill_result(TypePtr::BotPTR, ci_klass, InterfaceSet(false, false));
      fill_result(TypePtr::NotNull, ci_klass, InterfaceSet(false, false));
      fill_result(TypePtr::Constant, ci_klass, InterfaceSet(false, true));
      fill_result(TypePtr::BotPTR, ci_klass, InterfaceSet(false, true));
      fill_result(TypePtr::NotNull, ci_klass, InterfaceSet(false, true));
      fill_result(TypePtr::Constant, ci_klass, InterfaceSet(true, false));
      fill_result(TypePtr::BotPTR, ci_klass, InterfaceSet(true, false));
      fill_result(TypePtr::NotNull, ci_klass, InterfaceSet(true, false));
      fill_result(TypePtr::BotPTR, ci_klass, InterfaceSet(true, true));
      fill_result(TypePtr::NotNull, ci_klass, InterfaceSet(true, true));
      continue;
    }

    InterfaceSet interfaces = ci_klass.interfaces();
    fill_result(TypePtr::Constant, ci_klass, interfaces);
    fill_result(TypePtr::BotPTR, ci_klass, interfaces);
    fill_result(TypePtr::NotNull, ci_klass, interfaces);
    if (!interfaces._i0) {
      fill_result(TypePtr::BotPTR, ci_klass, InterfaceSet(true, interfaces._i1));
      fill_result(TypePtr::NotNull, ci_klass, InterfaceSet(true, interfaces._i1));
    }
    if (!interfaces._i1) {
      fill_result(TypePtr::BotPTR, ci_klass, InterfaceSet(interfaces._i0, true));
      fill_result(TypePtr::NotNull, ci_klass, InterfaceSet(interfaces._i0, true));
    }
    if (!interfaces._i0 && !interfaces._i1) {
      fill_result(TypePtr::BotPTR, ci_klass, InterfaceSet(true, true));
      fill_result(TypePtr::NotNull, ci_klass, InterfaceSet(true, true));
    }
  }

  assert(sample_idx == res.size(), "");
  return res;
}

constexpr std::array<TypeInstKlassPtrMirror, TypeInstKlassPtr_samples_size()> TypeInstKlassPtrMirror::_samples = generate_samples();

static constexpr size_t TypeAryKlassPtr_1d_elem_samples_size() {
  size_t res = 0;
  // Top, Bot, byte, int, float
  res += 5;
  for (auto& ci_klass : ciInstanceKlassMirror::_samples) {
    if (!ci_klass.is_loaded()) {
      continue;
    }

    // The constant instance
    res += 1;
    if (ci_klass.is_interface()) {
      continue;
    }

    InterfaceSet interfaces = ci_klass.interfaces();
    if (interfaces._i0 && interfaces._i1) {
      res += 1;
    } else if (interfaces._i0 || interfaces._i1) {
      res += 2;
    } else {
      res += 4;
    }
  }

  return res;
}

static constexpr size_t TypeAryKlassPtr_1d_samples_constant_size() {
  size_t res = 0;
  // byte[], int[], float[]
  res += 3;

  for (auto& ci_klass : ciInstanceKlassMirror::_samples) {
    if (!ci_klass.is_loaded()) {
      continue;
    }

    res += 1;
  }

  return res;
}

static constexpr size_t TypeAryKlassPtr_1d_samples_notnull_size() {
  size_t res = 0;
  // bot[], byte[], int[], float[]
  res += 4;

  for (auto& ci_klass : ciInstanceKlassMirror::_samples) {
    if (!ci_klass.is_loaded()) {
      continue;
    }

    // The notnull instance with constant elem
    res += 1;

    if (ci_klass.is_interface()) {
      continue;
    }

    InterfaceSet interfaces = ci_klass.interfaces();
    if (interfaces._i0 && interfaces._i1) {
      res += 1;
    } else if (interfaces._i0 || interfaces._i1) {
      res += 2;
    } else {
      res += 4;
    }
  }

  return res;
}

// All test instances have is_flat() == false, is_not_flat() == true, is_null_free() == false,
// is_not_null_free() == true, is_atomic() == true, is_refined_type() == false. All other
// parameters are included exhaustively.
class TypeAryKlassPtrMirror : public TypeKlassPtrMirror {
private:
  static constexpr size_t _1d_elem_samples_size = TypeAryKlassPtr_1d_elem_samples_size();
  static constexpr size_t _1d_samples_size = (TypeAryKlassPtr_1d_samples_constant_size() + TypeAryKlassPtr_1d_samples_notnull_size() * 2) * 3;
  static constexpr size_t _2d_elem_samples_size = TypeAryKlassPtr_1d_samples_constant_size() + TypeAryKlassPtr_1d_samples_notnull_size();
  static constexpr size_t _2d_samples_size = (TypeAryKlassPtr_1d_samples_constant_size() * 3 + TypeAryKlassPtr_1d_samples_notnull_size() * 2) * 3;

  const AryElemType<TypeKlassPtrMirror>* _elem;
  const ciArrayKlassMirror* _klass;

  constexpr TypeAryKlassPtrMirror(TypePtr::PTR ptr, const AryElemType<TypeKlassPtrMirror>* elem, const ciArrayKlassMirror* klass, Type::Offset offset)
    : TypeKlassPtrMirror(TypePtr::AryKlassPtr, ptr, _array_interfaces, offset), _elem(elem), _klass(klass) {
    assert(elem != nullptr, "");
    assert((elem->base() == Type::Int) == (klass != nullptr), "only have klass for int/char/byte/short arrays");
    assert(klass == nullptr || klass->elem_basic_type() == elem->basic_type(), "mismatched klass");
  }

  static constexpr auto generate_1d_elem_samples();
  static constexpr auto generate_1d_samples();
  static constexpr auto generate_2d_elem_samples();
  static constexpr auto generate_2d_samples();

public:
  static constexpr InterfaceSet _array_interfaces = TypeAryPtrMirror::_array_interfaces;
  static const std::array<AryElemType<TypeKlassPtrMirror>, _1d_elem_samples_size> _1d_elem_samples;
  static const std::array<TypeAryKlassPtrMirror, _1d_samples_size> _1d_samples;

  static const std::array<AryElemType<TypeKlassPtrMirror>, _2d_elem_samples_size> _2d_elem_samples;
  static const std::array<TypeAryKlassPtrMirror, _2d_samples_size> _2d_samples;

  constexpr TypeAryKlassPtrMirror() : TypeKlassPtrMirror(Type::Bad, TypePtr::TopPTR, _array_interfaces, Type::Offset(0)), _elem(nullptr), _klass(nullptr) {}

  static const TypeAryKlassPtrMirror* make(TypePtr::PTR ptr, const AryElemType<TypeKlassPtrMirror>* elem, const ciArrayKlassMirror* klass, Type::Offset offset,
                                           bool not_flat, bool not_null_free, bool flat, bool null_free, bool atomic, bool refined) {
    assert(!flat && not_flat && !null_free && not_null_free && atomic && !refined, "unsupported");
    for (auto& sample : _1d_samples) {
      if (sample.ptr() == ptr && sample.elem() == elem && sample.klass() == klass && sample.offset() == offset.get()) {
        return &sample;
      }
    }

    for (auto& sample : _2d_samples) {
      if (sample.ptr() == ptr && sample.elem() == elem && sample.klass() == klass && sample.offset() == offset.get()) {
        return &sample;
      }
    }

    ShouldNotReachHere();
  }

  const AryElemType<TypeKlassPtrMirror>* elem()  const { return _elem; }
  const ciArrayKlassMirror*              klass() const { return _klass; }

  bool is_flat()          const { return false; }
  bool is_not_flat()      const { return true; }
  bool is_null_free()     const { return false; }
  bool is_not_null_free() const { return true; }
  bool is_atomic()        const { return true; }
  bool is_refined_type()  const { return false; }

  virtual const TypeInstKlassPtrMirror* is_instklassptr() const override { ShouldNotReachHere(); }
  virtual const TypeAryKlassPtrMirror*  is_aryklassptr()  const override { return this; }

  virtual void dump_on(outputStream& st) const override {
    st.print("AryKlassPtr:");
    dump_ptr(st);
    st.print(" - ");
    if (elem()->base() == Type::Bottom) {
      st.print("bot[]");
    } else if (BasicType elem_bt = elem()->basic_type(); is_java_primitive(elem_bt)) {
      st.print("%s[]", type2name(elem_bt));
    } else {
      st.print("(");
      elem()->make_ptr()->dump_on(st);
      st.print(")[]");
    }
    dump_offset(st);
  }
};

template <>
constexpr const AryElemType<TypeKlassPtrMirror>* AryElemType<TypeKlassPtrMirror>::TOP = &TypeAryKlassPtrMirror::_1d_elem_samples[0];

template <>
constexpr const AryElemType<TypeKlassPtrMirror>* AryElemType<TypeKlassPtrMirror>::BOTTOM = &TypeAryKlassPtrMirror::_1d_elem_samples[1];

template <>
constexpr const AryElemType<TypeKlassPtrMirror>* AryElemType<TypeKlassPtrMirror>::find_1d(BasicType bt, const TypeKlassPtrMirror* ptr_type) {
  for (auto& sample : TypeAryKlassPtrMirror::_1d_elem_samples) {
    if (sample._bt == bt && sample._ptr_type == ptr_type) {
      return &sample;
    }
  }
  return nullptr;
}

template <>
constexpr const AryElemType<TypeKlassPtrMirror>* AryElemType<TypeKlassPtrMirror>::find_2d(BasicType bt, const TypeKlassPtrMirror* ptr_type) {
  for (auto& sample : TypeAryKlassPtrMirror::_2d_elem_samples) {
    if (sample._bt == bt && sample._ptr_type == ptr_type) {
      return &sample;
    }
  }
  return nullptr;
}

constexpr auto TypeAryKlassPtrMirror::generate_1d_elem_samples() {
  std::array<AryElemType<TypeKlassPtrMirror>, _1d_elem_samples_size> res;
  res[0] = AryElemType<TypeKlassPtrMirror>(T_ILLEGAL, nullptr);
  res[1] = AryElemType<TypeKlassPtrMirror>(T_VOID, nullptr);
  res[2] = AryElemType<TypeKlassPtrMirror>(T_BYTE, nullptr);
  res[3] = AryElemType<TypeKlassPtrMirror>(T_INT, nullptr);
  res[4] = AryElemType<TypeKlassPtrMirror>(T_FLOAT, nullptr);

  size_t sample_idx = 5;
  for (auto& ci_klass : ciInstanceKlassMirror::_samples) {
    if (!ci_klass.is_loaded()) {
      continue;
    }

    if (ci_klass.is_interface()) {
      res[sample_idx] = AryElemType<TypeKlassPtrMirror>(T_OBJECT, TypeInstKlassPtrMirror::make(TypePtr::Constant, ciEnvMirror::current()->Object_klass(), ci_klass.interfaces()));
      sample_idx++;
    } else {
      res[sample_idx] = AryElemType<TypeKlassPtrMirror>(T_OBJECT, TypeInstKlassPtrMirror::make(TypePtr::Constant, &ci_klass, ci_klass.interfaces()));
      sample_idx++;
    }
  }

  for (auto& ci_klass : ciInstanceKlassMirror::_samples) {
    if (!ci_klass.is_loaded() || ci_klass.is_interface()) {
      continue;
    }

    InterfaceSet interfaces = ci_klass.interfaces();
    res[sample_idx] = AryElemType<TypeKlassPtrMirror>(T_OBJECT, TypeInstKlassPtrMirror::make(TypePtr::NotNull, &ci_klass, interfaces));
    sample_idx++;
    if (!interfaces._i0) {
      res[sample_idx] = AryElemType<TypeKlassPtrMirror>(T_OBJECT, TypeInstKlassPtrMirror::make(TypePtr::NotNull, &ci_klass, InterfaceSet(true, interfaces._i1)));
      sample_idx++;
    }
    if (!interfaces._i1) {
      res[sample_idx] = AryElemType<TypeKlassPtrMirror>(T_OBJECT, TypeInstKlassPtrMirror::make(TypePtr::NotNull, &ci_klass, InterfaceSet(interfaces._i0, true)));
      sample_idx++;
    }
    if (!interfaces._i0 && !interfaces._i1) {
      res[sample_idx] = AryElemType<TypeKlassPtrMirror>(T_OBJECT, TypeInstKlassPtrMirror::make(TypePtr::NotNull, &ci_klass, InterfaceSet(true, true)));
      sample_idx++;
    }
  }

  assert(sample_idx == res.size(), "");
  return res;
}

constexpr std::array<AryElemType<TypeKlassPtrMirror>, TypeAryKlassPtrMirror::_1d_elem_samples_size> TypeAryKlassPtrMirror::_1d_elem_samples = generate_1d_elem_samples();

constexpr auto TypeAryKlassPtrMirror::generate_1d_samples() {
  std::array<TypeAryKlassPtrMirror, _1d_samples_size> res;
  size_t sample_idx = 0;
  auto fill_result = [&](TypePtr::PTR ptr, const AryElemType<TypeKlassPtrMirror>* elem, const ciArrayKlassMirror* klass) {
    res[sample_idx] = TypeAryKlassPtrMirror(ptr, elem, klass, Type::Offset::bottom);
    sample_idx++;
    res[sample_idx] = TypeAryKlassPtrMirror(ptr, elem, klass, Type::Offset(0));
    sample_idx++;
    res[sample_idx] = TypeAryKlassPtrMirror(ptr, elem, klass, Type::Offset(1));
    sample_idx++;
  };

  fill_result(TypePtr::BotPTR, AryElemType<TypeKlassPtrMirror>::find_1d(T_VOID, nullptr), nullptr);
  fill_result(TypePtr::NotNull, AryElemType<TypeKlassPtrMirror>::find_1d(T_VOID, nullptr), nullptr);

  auto& byte_ary_klass = ciArrayKlassMirror::find(T_BYTE, nullptr);
  fill_result(TypePtr::BotPTR, AryElemType<TypeKlassPtrMirror>::find_1d(T_BYTE, nullptr), &byte_ary_klass);
  fill_result(TypePtr::NotNull, AryElemType<TypeKlassPtrMirror>::find_1d(T_BYTE, nullptr), &byte_ary_klass);
  fill_result(TypePtr::Constant, AryElemType<TypeKlassPtrMirror>::find_1d(T_BYTE, nullptr), &byte_ary_klass);

  auto& int_ary_klass = ciArrayKlassMirror::find(T_INT, nullptr);
  fill_result(TypePtr::BotPTR, AryElemType<TypeKlassPtrMirror>::find_1d(T_INT, nullptr), &int_ary_klass);
  fill_result(TypePtr::NotNull, AryElemType<TypeKlassPtrMirror>::find_1d(T_INT, nullptr), &int_ary_klass);
  fill_result(TypePtr::Constant, AryElemType<TypeKlassPtrMirror>::find_1d(T_INT, nullptr), &int_ary_klass);

  fill_result(TypePtr::BotPTR, AryElemType<TypeKlassPtrMirror>::find_1d(T_FLOAT, nullptr), nullptr);
  fill_result(TypePtr::NotNull, AryElemType<TypeKlassPtrMirror>::find_1d(T_FLOAT, nullptr), nullptr);
  fill_result(TypePtr::Constant, AryElemType<TypeKlassPtrMirror>::find_1d(T_FLOAT, nullptr), nullptr);

  for (auto& elem : _1d_elem_samples) {
    if (elem.make_ptr() == nullptr) {
      continue;
    }

    fill_result(TypePtr::BotPTR, &elem, nullptr);
    fill_result(TypePtr::NotNull, &elem, nullptr);

    if (elem.make_ptr()->ptr() == TypePtr::Constant) {
      fill_result(TypePtr::Constant, &elem, nullptr);
    }
  }

  assert(sample_idx == res.size(), "");
  return res;
}

constexpr std::array<TypeAryKlassPtrMirror, TypeAryKlassPtrMirror::_1d_samples_size> TypeAryKlassPtrMirror::_1d_samples = generate_1d_samples();

constexpr auto TypeAryKlassPtrMirror::generate_2d_elem_samples() {
  std::array<AryElemType<TypeKlassPtrMirror>, _2d_elem_samples_size> res;
  size_t sample_idx = 0;
  for (auto& ptr_type : _1d_samples) {
    if (ptr_type.ptr() == TypePtr::BotPTR || ptr_type.offset() != 0) {
      continue;
    }

    res[sample_idx] = AryElemType<TypeKlassPtrMirror>(T_OBJECT, &ptr_type);
    sample_idx++;
  }

  assert(sample_idx == res.size(), "");
  return res;
}

constexpr std::array<AryElemType<TypeKlassPtrMirror>, TypeAryKlassPtrMirror::_2d_elem_samples_size> TypeAryKlassPtrMirror::_2d_elem_samples = generate_2d_elem_samples();

constexpr auto TypeAryKlassPtrMirror::generate_2d_samples() {
  std::array<TypeAryKlassPtrMirror, _2d_samples_size> res;
  size_t sample_idx = 0;
  for (auto& elem : _2d_elem_samples) {
    res[sample_idx] = TypeAryKlassPtrMirror(TypePtr::BotPTR, &elem, nullptr, Type::Offset::bottom);
    sample_idx++;
    res[sample_idx] = TypeAryKlassPtrMirror(TypePtr::BotPTR, &elem, nullptr, Type::Offset(0));
    sample_idx++;
    res[sample_idx] = TypeAryKlassPtrMirror(TypePtr::BotPTR, &elem, nullptr, Type::Offset(1));
    sample_idx++;
    res[sample_idx] = TypeAryKlassPtrMirror(TypePtr::NotNull, &elem, nullptr, Type::Offset::bottom);
    sample_idx++;
    res[sample_idx] = TypeAryKlassPtrMirror(TypePtr::NotNull, &elem, nullptr, Type::Offset(0));
    sample_idx++;
    res[sample_idx] = TypeAryKlassPtrMirror(TypePtr::NotNull, &elem, nullptr, Type::Offset(1));
    sample_idx++;

    if (elem.make_ptr()->ptr() == TypePtr::Constant) {
      res[sample_idx] = TypeAryKlassPtrMirror(TypePtr::Constant, &elem, nullptr, Type::Offset::bottom);
      sample_idx++;
      res[sample_idx] = TypeAryKlassPtrMirror(TypePtr::Constant, &elem, nullptr, Type::Offset(0));
      sample_idx++;
      res[sample_idx] = TypeAryKlassPtrMirror(TypePtr::Constant, &elem, nullptr, Type::Offset(1));
      sample_idx++;
    }
  }

  assert(sample_idx == res.size(), "");
  return res;
}

constexpr std::array<TypeAryKlassPtrMirror, TypeAryKlassPtrMirror::_2d_samples_size> TypeAryKlassPtrMirror::_2d_samples = generate_2d_samples();

// OopPtrMirror is the mirror of oop
class OopPtrMirror {
private:
  static constexpr size_t _samples_size = (ciInstanceKlassMirror::_samples.size() + ciArrayKlassMirror::_samples.size()) * 18 + 3;
  InstanceMirror _pointee;
  int _instance_id;
  int _offset;

  constexpr OopPtrMirror(InstanceMirror pointee, int instance_id, int offset) : _pointee(pointee), _instance_id(instance_id), _offset(offset) {}

  static constexpr auto generate_samples();

public:
  static const std::array<OopPtrMirror, _samples_size> _samples;

  constexpr OopPtrMirror() : _pointee(nullptr), _instance_id(0), _offset(0) {}

  template <class PtrType>
  static bool klass_satisfies(const ciKlassMirror* klass, const PtrType* type, bool type_is_exact) {
    type_is_exact |= type->klass_is_exact();

    if (klass->is_inst()) {
      if (type->base() == Type::AryPtr || type->base() == Type::AryKlassPtr) {
        return false;
      }

      auto inst_klass = klass->as_inst();
      auto inst_interfaces = inst_klass->interfaces();
      if (inst_klass->is_interface()) {
        inst_klass = ciEnvMirror::current()->Object_klass();
      }

      const typename PtrType::InstType* type_inst;
      if constexpr (PtrType::is_oopptr_type) {
        type_inst = type->is_oopptr()->is_instptr();
      } else {
        type_inst = type->is_klassptr()->is_instklassptr();
      }

      if (type_is_exact) {
        return inst_klass == type_inst->instance_klass() &&
               inst_interfaces == type_inst->interfaces();
      } else {
        return inst_klass->is_subtype_of(type_inst->instance_klass()) &&
               inst_interfaces->contains(type_inst->interfaces());
      }
    }

    if (type->base() == Type::InstPtr || type->base() == Type::InstKlassPtr) {
      const typename PtrType::InstType* type_inst;
      if constexpr (PtrType::is_oopptr_type) {
        type_inst = type->is_oopptr()->is_instptr();
      } else {
        type_inst = type->is_klassptr()->is_instklassptr();
      }

      return !type_is_exact && type_inst->instance_klass()->is_java_lang_Object() &&
             TypeAryKlassPtrMirror::_array_interfaces.contains(type_inst->interfaces());
    }

    const typename PtrType::AryType* type_ary;
    if constexpr (PtrType::is_oopptr_type) {
      type_ary = type->is_oopptr()->is_aryptr();
    } else {
      type_ary = type->is_klassptr()->is_aryklassptr();
    }
    auto type_elem = type_ary->elem();
    auto ary_klass = klass->as_ary();
    if (type_elem->base() == Type::Bottom) {
      assert(type->ptr() != TypePtr::Constant, "cannot have an exact bot[]");
      return true;
    }

    if (type_elem->basic_type() != ary_klass->elem_basic_type()) {
      return false;
    }

    if (type_elem->basic_type() != T_OBJECT) {
      return true;
    } else {
      return klass_satisfies<PtrType>(ary_klass->elem(), type_elem->make_ptr(), type_is_exact);
    }
  }

  bool satisfies(const TypePtrMirror* type) const {
    if (type->offset() != Type::OffsetBot && type->offset() != _offset) {
      return false;
    }

    if (_pointee.klass() == nullptr) {
      return type->ptr() == TypePtr::BotPTR || type->ptr() == TypePtr::Null;
    }

    if (type->base() == Type::AnyPtr) {
      assert(type->ptr() == TypePtr::TopPTR || type->ptr() == TypePtr::Null, "must be top or null");
      return false;
    }
    assert(type->ptr() == TypePtr::BotPTR || type->ptr() == TypePtr::NotNull || type->ptr() == TypePtr::Constant, "only AnyPtr can be top or null");

    auto type_oop = type->is_oopptr();
    if (_pointee.klass()->is_ary() && type->base() == Type::AryPtr &&
        !type_oop->is_aryptr()->size()->contains(_pointee.array_length())) {
      return false;
    }

    if (type->ptr() == TypePtr::Constant) {
      auto const_oop = type_oop->const_oop();
      return const_oop == _pointee;
    }

    if (type_oop->instance_id() != TypeOopPtr::InstanceBot && type_oop->instance_id() != _instance_id) {
      return false;
    }

    return klass_satisfies(_pointee.klass(), type_oop, false);
  }

  void dump_on(outputStream& st) const {
    st.print("klassptr_instance:");
    if (_pointee.klass() == nullptr) {
      st.print("null");
    } else {
      _pointee.klass()->dump_on(st);
      st.print(" - idx=%d - instance_id=%d", _pointee.idx(), _instance_id);
    }
    st.print(" - offset=%d", _offset);
  }
};

constexpr auto OopPtrMirror::generate_samples() {
  std::array<OopPtrMirror, _samples_size> res;
  res[0] = OopPtrMirror(InstanceMirror(nullptr), 0, 0);
  res[1] = OopPtrMirror(InstanceMirror(nullptr), 0, 1);
  res[2] = OopPtrMirror(InstanceMirror(nullptr), 0, 2);

  size_t sample_idx = 3;
  auto fill_result_helper = [&](const ciKlassMirror& ci_klass, int idx, int instance_id) {
    res[sample_idx] = OopPtrMirror(InstanceMirror(ci_klass, idx), instance_id, 0);
    sample_idx++;
    res[sample_idx] = OopPtrMirror(InstanceMirror(ci_klass, idx), instance_id, 1);
    sample_idx++;
    res[sample_idx] = OopPtrMirror(InstanceMirror(ci_klass, idx), instance_id, 2);
    sample_idx++;
  };

  auto fill_result = [&](const ciKlassMirror& ci_klass) {
    fill_result_helper(ci_klass, 0, 0);
    fill_result_helper(ci_klass, 1, 0);
    fill_result_helper(ci_klass, 2, 0);
    fill_result_helper(ci_klass, 3, ci_klass.instance_id());
    fill_result_helper(ci_klass, 3, ci_klass.instance_id() + 1);
    fill_result_helper(ci_klass, 3, ci_klass.instance_id() + 2);
  };

  for (auto& ci_klass : ciInstanceKlassMirror::_samples) {
    fill_result(ci_klass);
  }
  for (auto& ci_klass : ciArrayKlassMirror::_samples) {
    fill_result(ci_klass);
  }

  assert(sample_idx == res.size(), "");
  return res;
}

constexpr std::array<OopPtrMirror, OopPtrMirror::_samples_size> OopPtrMirror::_samples = generate_samples();

// KlassPtrMirror is the mirror of Klass*
class KlassPtrMirror {
private:
  static constexpr size_t _samples_size = (ciInstanceKlassMirror::_samples.size() + ciArrayKlassMirror::_samples.size() + 1) * 3;
  const ciKlassMirror* _klass;
  int _offset;

  constexpr KlassPtrMirror(const ciKlassMirror* klass, int offset) : _klass(klass), _offset(offset) {}

  static constexpr auto generate_samples();

public:
  static const std::array<KlassPtrMirror, _samples_size> _samples;

  constexpr KlassPtrMirror() : _klass(nullptr), _offset(0) {}

  bool satisfies(const TypePtrMirror* type) const {
    if (type->offset() != _offset && type->offset() != Type::OffsetBot) {
      return false;
    }

    if (_klass == nullptr) {
      return type->ptr() == TypePtr::BotPTR || type->ptr() == TypePtr::Null;
    }

    if (type->base() == Type::AnyPtr) {
      assert(type->ptr() == TypePtr::TopPTR || type->ptr() == TypePtr::Null, "must be top or null");
      return false;
    }

    assert(type->ptr() == TypePtr::BotPTR || type->ptr() == TypePtr::NotNull || type->ptr() == TypePtr::Constant, "only AnyPtr can be top or null");
    return OopPtrMirror::klass_satisfies(_klass, type->is_klassptr(), false);
  }

  void dump_on(outputStream& st) const {
    st.print("klassptr_instance:");
    if (_klass == nullptr) {
      st.print("null");
    } else {
      _klass->dump_on(st);
    }
    st.print(" - offset=%d", _offset);
  }
};

constexpr auto KlassPtrMirror::generate_samples() {
  std::array<KlassPtrMirror, _samples_size> res;
  res[0] = KlassPtrMirror(nullptr, 0);
  res[1] = KlassPtrMirror(nullptr, 1);
  res[2] = KlassPtrMirror(nullptr, 2);

  size_t sample_idx = 3;
  for (auto& ci_klass : ciInstanceKlassMirror::_samples) {
    res[sample_idx] = KlassPtrMirror(&ci_klass, 0);
    res[sample_idx + 1] = KlassPtrMirror(&ci_klass, 1);
    res[sample_idx + 2] = KlassPtrMirror(&ci_klass, 2);
    sample_idx += 3;
  }
  for (auto& ci_klass : ciArrayKlassMirror::_samples) {
    res[sample_idx] = KlassPtrMirror(&ci_klass, 0);
    res[sample_idx + 1] = KlassPtrMirror(&ci_klass, 1);
    res[sample_idx + 2] = KlassPtrMirror(&ci_klass, 2);
    sample_idx += 3;
  }

  assert(sample_idx == res.size(), "");
  return res;
}

constexpr std::array<KlassPtrMirror, KlassPtrMirror::_samples_size> KlassPtrMirror::_samples = generate_samples();

template <class PtrType, class Ptr>
static void test_meet_join() {
  constexpr size_t samples_size = PtrType::InstType::_samples.size() + PtrType::AryType::_1d_samples.size() + PtrType::AryType::_2d_samples.size();
  if constexpr (PtrType::is_oopptr_type) {
    static_assert(samples_size == 2040);
  } else {
    static_assert(samples_size == 471);
  }

  // Running all instances takes a lot of time, so we only run a fraction of them regularly
  auto sample_hit = [] {
    constexpr double sampling_prob = 0.02;
    return uint(os::random()) < max_juint * sampling_prob;
  };

  GrowableArray<const PtrType*> type_samples(samples_size, MemTag::mtOther);
  for (auto& sample : PtrType::InstType::_samples) {
    if (sample_hit()) {
      type_samples.append(&sample);
    }
  }
  for (auto& sample : PtrType::AryType::_1d_samples) {
    if (sample_hit()) {
      type_samples.append(&sample);
    }
  }
  for (auto& sample : PtrType::AryType::_2d_samples) {
    if (sample_hit()) {
      type_samples.append(&sample);
    }
  }

  for (int i = 0; i < type_samples.length(); i++) {
    const PtrType* t1 = type_samples.at(i);
    for (int j = i; j < type_samples.length(); j++) {
      const PtrType* t2 = type_samples.at(j);
      auto meet = TypeJavaPtrMeetHelper::javaptr_type_xmeet(t1, t2);
      auto join = TypeJavaPtrJoinHelper::javaptr_type_xjoin(t1, t2);

      // Commutativity
      ASSERT_EQ(TypeJavaPtrMeetHelper::javaptr_type_xmeet(t2, t1), meet);
      ASSERT_EQ(TypeJavaPtrJoinHelper::javaptr_type_xjoin(t2, t1), join);

      // Verify that t1 and t2 must be subsets of meet
      ASSERT_EQ(TypeJavaPtrMeetHelper::javaptr_type_xmeet(t1, meet), meet);
      ASSERT_EQ(TypeJavaPtrMeetHelper::javaptr_type_xmeet(t2, meet), meet);
      ASSERT_EQ(TypeJavaPtrJoinHelper::javaptr_type_xjoin(t1, meet), t1);
      ASSERT_EQ(TypeJavaPtrJoinHelper::javaptr_type_xjoin(t2, meet), t2);

      if (join->base() != Type::AnyPtr) {
        const PtrType* typed_join;
        if constexpr (PtrType::is_oopptr_type) {
          typed_join = join->is_oopptr();
        } else {
          typed_join = join->is_klassptr();
        }

        // Verify that join must be a subset of both t1 and t2
        ASSERT_EQ(TypeJavaPtrMeetHelper::javaptr_type_xmeet(t1, typed_join), t1);
        ASSERT_EQ(TypeJavaPtrMeetHelper::javaptr_type_xmeet(t2, typed_join), t2);
        ASSERT_EQ(TypeJavaPtrJoinHelper::javaptr_type_xjoin(t1, typed_join), typed_join);
        ASSERT_EQ(TypeJavaPtrJoinHelper::javaptr_type_xjoin(t2, typed_join), typed_join);
      }

      // Element-wise verification
      for (auto& elem : Ptr::_samples) {
        bool in_t1 = elem.satisfies(t1);
        bool in_t2 = elem.satisfies(t2);
        bool in_meet = elem.satisfies(meet);
        bool in_join = elem.satisfies(join);

        ASSERT_TRUE((!in_t1 && !in_t2) || in_meet);
        ASSERT_EQ(in_t1 && in_t2, in_join);
      }
    }
  }
}

TEST(opto, typejavaptr) {
  test_meet_join<TypeOopPtrMirror, OopPtrMirror>();
  test_meet_join<TypeKlassPtrMirror, KlassPtrMirror>();
}
