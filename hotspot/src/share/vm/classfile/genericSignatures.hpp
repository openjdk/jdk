/*
 * Copyright (c) 2012, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_VM_CLASSFILE_GENERICSIGNATURES_HPP
#define SHARE_VM_CLASSFILE_GENERICSIGNATURES_HPP

#include "classfile/symbolTable.hpp"
#include "memory/allocation.hpp"
#include "runtime/signature.hpp"
#include "utilities/growableArray.hpp"
#include "utilities/resourceHash.hpp"

class stringStream;

namespace generic {

class Identifier;
class ClassDescriptor;
class MethodDescriptor;

class TypeParameter; // a formal type parameter declared in generic signatures
class TypeArgument;  // The "type value" passed to fill parameters in supertypes
class TypeVariable;  // A usage of a type parameter as a value
/**
 * Example:
 *
 * <T, V> class Foo extends Bar<String> { int m(V v) {} }
 * ^^^^^^                       ^^^^^^          ^^
 * type parameters            type argument    type variable
 *
 * Note that a type variable could be passed as an argument too:
 * <T, V> class Foo extends Bar<T> { int m(V v) {} }
 *                             ^^^
 *                             type argument's value is a type variable
 */


class Type;
class ClassType;
class ArrayType;
class PrimitiveType;
class Context;
class DescriptorCache;

class DescriptorStream;

class Identifier : public ResourceObj {
 private:
  Symbol* _sym;
  int _begin;
  int _end;

 public:
  Identifier(Symbol* sym, int begin, int end) :
    _sym(sym), _begin(begin), _end(end) {}

  bool equals(Identifier* other);
  bool equals(Symbol* sym);

#ifndef PRODUCT
  void print_on(outputStream* str) const;
#endif // ndef PRODUCT
};

class Descriptor : public ResourceObj {
 protected:
  GrowableArray<TypeParameter*> _type_parameters;
  ClassDescriptor* _outer_class;

  Descriptor(GrowableArray<TypeParameter*>& params,
    ClassDescriptor* outer)
    : _type_parameters(params), _outer_class(outer) {}

 public:

  ClassDescriptor* outer_class() { return _outer_class; }
  void set_outer_class(ClassDescriptor* sig) { _outer_class = sig; }

  virtual ClassDescriptor* as_class_signature() { return NULL; }
  virtual MethodDescriptor* as_method_signature() { return NULL; }

  bool is_class_signature() { return as_class_signature() != NULL; }
  bool is_method_signature() { return as_method_signature() != NULL; }

  GrowableArray<TypeParameter*>& type_parameters() {
    return _type_parameters;
  }

  TypeParameter* find_type_parameter(Identifier* id, int* param_depth);

  virtual void bind_variables_to_parameters() = 0;

#ifndef PRODUCT
  virtual void print_on(outputStream* str) const = 0;
#endif
};

class ClassDescriptor : public Descriptor {
 private:
  ClassType* _super;
  GrowableArray<ClassType*> _interfaces;
  MethodDescriptor* _outer_method;

  ClassDescriptor(GrowableArray<TypeParameter*>& ftp, ClassType* scs,
      GrowableArray<ClassType*>& sis, ClassDescriptor* outer_class = NULL,
      MethodDescriptor* outer_method = NULL)
        : Descriptor(ftp, outer_class), _super(scs), _interfaces(sis),
          _outer_method(outer_method) {}

  static u2 get_outer_class_index(InstanceKlass* k, TRAPS);
  static ClassDescriptor* parse_generic_signature(Klass* k, Symbol* original_name, TRAPS);

 public:

  virtual ClassDescriptor* as_class_signature() { return this; }

  MethodDescriptor* outer_method() { return _outer_method; }
  void set_outer_method(MethodDescriptor* m) { _outer_method = m; }

  ClassType* super() { return _super; }
  ClassType* interface_desc(Symbol* sym);

  static ClassDescriptor* parse_generic_signature(Klass* k, TRAPS);
  static ClassDescriptor* parse_generic_signature(Symbol* sym);

  // For use in superclass chains in positions where this is no generic info
  static ClassDescriptor* placeholder(InstanceKlass* klass);

#ifndef PRODUCT
  void print_on(outputStream* str) const;
#endif

  ClassDescriptor* canonicalize(Context* ctx);

  // Linking sets the position index in any contained TypeVariable type
  // to correspond to the location of that identifier in the formal type
  // parameters.
  void bind_variables_to_parameters();
};

class MethodDescriptor : public Descriptor {
 private:
  GrowableArray<Type*> _parameters;
  Type* _return_type;
  GrowableArray<Type*> _throws;

  MethodDescriptor(GrowableArray<TypeParameter*>& ftp, ClassDescriptor* outer,
      GrowableArray<Type*>& sigs, Type* rt, GrowableArray<Type*>& throws)
      : Descriptor(ftp, outer), _parameters(sigs), _return_type(rt),
        _throws(throws) {}

 public:

  static MethodDescriptor* parse_generic_signature(Method* m, ClassDescriptor* outer);
  static MethodDescriptor* parse_generic_signature(Symbol* sym, ClassDescriptor* outer);

  MethodDescriptor* as_method_signature() { return this; }

  // Performs generic analysis on the method parameters to determine
  // if both methods refer to the same argument types.
  bool covariant_match(MethodDescriptor* other, Context* ctx);

  // Returns a new method descriptor with all generic variables
  // removed and replaced with whatever is indicated using the Context.
  MethodDescriptor* canonicalize(Context* ctx);

  void bind_variables_to_parameters();

#ifndef PRODUCT
  TempNewSymbol reify_signature(Context* ctx, TRAPS);
  void print_on(outputStream* str) const;
#endif
};

class TypeParameter : public ResourceObj {
 private:
  Identifier* _identifier;
  ClassType* _class_bound;
  GrowableArray<ClassType*> _interface_bounds;

  // The position is the ordinal location of the parameter within the
  // formal parameter list (excluding outer classes).  It is only set for
  // formal type parameters that are associated with a class -- method
  // type parameters are left as -1.  When resolving a generic variable to
  // find the actual type, this index is used to access the generic type
  // argument in the provided context object.
  int _position; // Assigned during variable linking

  TypeParameter(Identifier* id, ClassType* class_bound,
    GrowableArray<ClassType*>& interface_bounds) :
      _identifier(id), _class_bound(class_bound),
      _interface_bounds(interface_bounds), _position(-1) {}

 public:
  static TypeParameter* parse_generic_signature(DescriptorStream* str);

  ClassType* bound();
  int position() { return _position; }

  void bind_variables_to_parameters(Descriptor* sig, int position);
  Identifier* identifier() { return _identifier; }

  Type* resolve(Context* ctx, int inner_depth, int ctx_depth);
  TypeParameter* canonicalize(Context* ctx, int ctx_depth);

#ifndef PRODUCT
  void print_on(outputStream* str) const;
#endif
};

class Type : public ResourceObj {
 public:
  static Type* parse_generic_signature(DescriptorStream* str);

  virtual ClassType* as_class() { return NULL; }
  virtual TypeVariable* as_variable() { return NULL; }
  virtual ArrayType* as_array() { return NULL; }
  virtual PrimitiveType* as_primitive() { return NULL; }

  virtual bool covariant_match(Type* gt, Context* ctx) = 0;
  virtual Type* canonicalize(Context* ctx, int ctx_depth) = 0;

  virtual void bind_variables_to_parameters(Descriptor* sig) = 0;

#ifndef PRODUCT
  virtual void reify_signature(stringStream* ss, Context* ctx) = 0;
  virtual void print_on(outputStream* str) const = 0;
#endif
};

class ClassType : public Type {
  friend class ClassDescriptor;
 protected:
  Identifier* _identifier;
  GrowableArray<TypeArgument*> _type_arguments;
  ClassType* _outer_class;

  ClassType(Identifier* identifier,
      GrowableArray<TypeArgument*>& args,
      ClassType* outer)
      : _identifier(identifier), _type_arguments(args), _outer_class(outer) {}

  // Returns true if there are inner classes to read
  static Identifier* parse_generic_signature_simple(
      GrowableArray<TypeArgument*>* args,
      bool* has_inner, DescriptorStream* str);

  static ClassType* parse_generic_signature(ClassType* outer,
      DescriptorStream* str);
  static ClassType* from_symbol(Symbol* sym);

 public:
  ClassType* as_class() { return this; }

  static ClassType* parse_generic_signature(DescriptorStream* str);
  static ClassType* java_lang_Object();

  Identifier* identifier() { return _identifier; }
  int type_arguments_length() { return _type_arguments.length(); }
  TypeArgument* type_argument_at(int i);

  virtual ClassType* outer_class() { return _outer_class; }

  bool covariant_match(Type* gt, Context* ctx);
  ClassType* canonicalize(Context* ctx, int context_depth);

  void bind_variables_to_parameters(Descriptor* sig);

#ifndef PRODUCT
  void reify_signature(stringStream* ss, Context* ctx);
  void print_on(outputStream* str) const;
#endif
};

class TypeVariable : public Type {
 private:
  Identifier* _id;
  TypeParameter* _parameter; // assigned during linking

  // how many steps "out" from inner classes, -1 if method
  int _inner_depth;

  TypeVariable(Identifier* id)
      : _id(id), _parameter(NULL), _inner_depth(0) {}

 public:
  TypeVariable* as_variable() { return this; }

  static TypeVariable* parse_generic_signature(DescriptorStream* str);

  Identifier* identifier() { return _id; }
  TypeParameter* parameter() { return _parameter; }
  int inner_depth() { return _inner_depth; }

  void bind_variables_to_parameters(Descriptor* sig);

  Type* resolve(Context* ctx, int ctx_depth);
  bool covariant_match(Type* gt, Context* ctx);
  Type* canonicalize(Context* ctx, int ctx_depth);

#ifndef PRODUCT
  void reify_signature(stringStream* ss, Context* ctx);
  void print_on(outputStream* str) const;
#endif
};

class ArrayType : public Type {
 private:
  Type* _base;

  ArrayType(Type* base) : _base(base) {}

 public:
  ArrayType* as_array() { return this; }

  static ArrayType* parse_generic_signature(DescriptorStream* str);

  bool covariant_match(Type* gt, Context* ctx);
  ArrayType* canonicalize(Context* ctx, int ctx_depth);

  void bind_variables_to_parameters(Descriptor* sig);

#ifndef PRODUCT
  void reify_signature(stringStream* ss, Context* ctx);
  void print_on(outputStream* str) const;
#endif
};

class PrimitiveType : public Type {
  friend class Type;
 private:
  char _type; // includes V for void

  PrimitiveType(char& type) : _type(type) {}

 public:
  PrimitiveType* as_primitive() { return this; }

  bool covariant_match(Type* gt, Context* ctx);
  PrimitiveType* canonicalize(Context* ctx, int ctx_depth);

  void bind_variables_to_parameters(Descriptor* sig);

#ifndef PRODUCT
  void reify_signature(stringStream* ss, Context* ctx);
  void print_on(outputStream* str) const;
#endif
};

class TypeArgument : public ResourceObj {
 private:
  Type* _lower_bound;
  Type* _upper_bound; // may be null or == _lower_bound

  TypeArgument(Type* lower_bound, Type* upper_bound)
      : _lower_bound(lower_bound), _upper_bound(upper_bound) {}

 public:

  static TypeArgument* parse_generic_signature(DescriptorStream* str);

  Type* lower_bound() { return _lower_bound; }
  Type* upper_bound() { return _upper_bound; }

  void bind_variables_to_parameters(Descriptor* sig);
  TypeArgument* canonicalize(Context* ctx, int ctx_depth);

  bool covariant_match(TypeArgument* a, Context* ctx);

#ifndef PRODUCT
  void print_on(outputStream* str) const;
#endif
};


class Context : public ResourceObj {
 private:
  DescriptorCache* _cache;
  GrowableArray<ClassType*> _type_arguments;

  void reset_to_mark(int size);

 public:
  // When this object goes out of scope or 'destroy' is
  // called, then the application of the type to the
  // context is wound-back (unless it's been deactivated).
  class Mark : public StackObj {
   private:
    mutable Context* _context;
    int _marked_size;

    bool is_active() const { return _context != NULL; }
    void deactivate() const { _context = NULL; }

   public:
    Mark() : _context(NULL), _marked_size(0) {}
    Mark(Context* ctx, int sz) : _context(ctx), _marked_size(sz) {}
    Mark(const Mark& m) : _context(m._context), _marked_size(m._marked_size) {
      m.deactivate(); // Ownership is transferred
    }

    Mark& operator=(const Mark& cm) {
      destroy();
      _context = cm._context;
      _marked_size = cm._marked_size;
      cm.deactivate();
      return *this;
    }

    void destroy();
    ~Mark() { destroy(); }
  };

  Context(DescriptorCache* cache) : _cache(cache) {}

  Mark mark() { return Mark(this, _type_arguments.length()); }
  void apply_type_arguments(InstanceKlass* current, InstanceKlass* super,TRAPS);

  ClassType* at_depth(int i) const;

#ifndef PRODUCT
  void print_on(outputStream* str) const;
#endif
};

/**
 * Contains a cache of descriptors for classes and methods so they can be
 * looked-up instead of reparsing each time they are needed.
 */
class DescriptorCache : public ResourceObj {
 private:
  ResourceHashtable<InstanceKlass*, ClassDescriptor*> _class_descriptors;
  ResourceHashtable<Method*, MethodDescriptor*> _method_descriptors;

 public:
  ClassDescriptor* descriptor_for(InstanceKlass* ikh, TRAPS);

  MethodDescriptor* descriptor_for(Method* mh, ClassDescriptor* cd, TRAPS);
  // Class descriptor derived from method holder
  MethodDescriptor* descriptor_for(Method* mh, TRAPS);
};

} // namespace generic

#endif // SHARE_VM_CLASSFILE_GENERICSIGNATURES_HPP

