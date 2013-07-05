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

#include "precompiled.hpp"

#include "classfile/genericSignatures.hpp"
#include "classfile/symbolTable.hpp"
#include "classfile/systemDictionary.hpp"
#include "memory/resourceArea.hpp"

namespace generic {

// Helper class for parsing the generic signature Symbol in klass and methods
class DescriptorStream : public ResourceObj {
 private:
  Symbol* _symbol;
  int _offset;
  int _mark;
  const char* _parse_error;

  void set_parse_error(const char* error) {
    assert(error != NULL, "Can't set NULL error string");
    _parse_error = error;
  }

 public:
  DescriptorStream(Symbol* sym)
      : _symbol(sym), _offset(0), _mark(-1), _parse_error(NULL) {}

  const char* parse_error() const {
    return _parse_error;
  }

  bool at_end() { return _offset >= _symbol->utf8_length(); }

  char peek() {
    if (at_end()) {
      set_parse_error("Peeking past end of signature");
      return '\0';
    } else {
      return _symbol->byte_at(_offset);
    }
  }

  char read() {
    if (at_end()) {
      set_parse_error("Reading past end of signature");
      return '\0';
    } else {
      return _symbol->byte_at(_offset++);
    }
  }

  void read(char expected) {
    char c = read();
    assert_char(c, expected, 0);
  }

  void assert_char(char c, char expected, int pos = -1) {
    if (c != expected) {
      const char* fmt = "Parse error at %d: expected %c but got %c";
      size_t len = strlen(fmt) + 5;
      char* buffer = NEW_RESOURCE_ARRAY(char, len);
      jio_snprintf(buffer, len, fmt, _offset + pos, expected, c);
      set_parse_error(buffer);
    }
  }

  void push(char c) {
    assert(c == _symbol->byte_at(_offset - 1), "Pushing back wrong value");
    --_offset;
  }

  void expect_end() {
    if (!at_end()) {
      set_parse_error("Unexpected data trailing signature");
    }
  }

  bool has_mark() { return _mark != -1; }

  void set_mark() {
    _mark = _offset;
  }

  Identifier* identifier_from_mark() {
    assert(has_mark(), "Mark should be set");
    if (!has_mark()) {
      set_parse_error("Expected mark to be set");
      return NULL;
    } else {
      Identifier* id = new Identifier(_symbol, _mark, _offset - 1);
      _mark = -1;
      return id;
    }
  }
};


#define CHECK_FOR_PARSE_ERROR()         \
  if (STREAM->parse_error() != NULL) {   \
    if (VerifyGenericSignatures) {      \
      fatal(STREAM->parse_error());      \
    }                                   \
    return NULL;                        \
  } (void)0

#define READ() STREAM->read(); CHECK_FOR_PARSE_ERROR()
#define PEEK() STREAM->peek(); CHECK_FOR_PARSE_ERROR()
#define PUSH(c) STREAM->push(c)
#define EXPECT(c) STREAM->read(c); CHECK_FOR_PARSE_ERROR()
#define EXPECTED(c, ch) STREAM->assert_char(c, ch); CHECK_FOR_PARSE_ERROR()
#define EXPECT_END() STREAM->expect_end(); CHECK_FOR_PARSE_ERROR()

#define CHECK_STREAM STREAM); CHECK_FOR_PARSE_ERROR(); ((void)0

#ifndef PRODUCT
void Identifier::print_on(outputStream* str) const {
  for (int i = _begin; i < _end; ++i) {
    str->print("%c", (char)_sym->byte_at(i));
  }
}
#endif // ndef PRODUCT

bool Identifier::equals(Identifier* other) {
  if (_sym == other->_sym && _begin == other->_begin && _end == other->_end) {
    return true;
  } else if (_end - _begin != other->_end - other->_begin) {
    return false;
  } else {
    size_t len = _end - _begin;
    char* addr = ((char*)_sym->bytes()) + _begin;
    char* oaddr = ((char*)other->_sym->bytes()) + other->_begin;
    return strncmp(addr, oaddr, len) == 0;
  }
}

bool Identifier::equals(Symbol* sym) {
  Identifier id(sym, 0, sym->utf8_length());
  return equals(&id);
}

/**
 * A formal type parameter may be found in the the enclosing class, but it could
 * also come from an enclosing method or outer class, in the case of inner-outer
 * classes or anonymous classes.  For example:
 *
 * class Outer<T,V> {
 *   class Inner<W> {
 *     void m(T t, V v, W w);
 *   }
 * }
 *
 * In this case, the type variables in m()'s signature are not all found in the
 * immediate enclosing class (Inner).  class Inner has only type parameter W,
 * but it's outer_class field will reference Outer's descriptor which contains
 * T & V (no outer_method in this case).
 *
 * If you have an anonymous class, it has both an enclosing method *and* an
 * enclosing class where type parameters can be declared:
 *
 * class MOuter<T> {
 *   <V> void bar(V v) {
 *     Runnable r = new Runnable() {
 *       public void run() {}
 *       public void foo(T t, V v) { ... }
 *     };
 *   }
 * }
 *
 * In this case, foo will be a member of some class, Runnable$1, which has no
 * formal parameters itself, but has an outer_method (bar()) which provides
 * type parameter V, and an outer class MOuter with type parameter T.
 *
 * It is also possible that the outer class is itself an inner class to some
 * other class (or an anonymous class with an enclosing method), so we need to
 * follow the outer_class/outer_method chain to it's end when looking for a
 * type parameter.
 */
TypeParameter* Descriptor::find_type_parameter(Identifier* id, int* depth) {

  int current_depth = 0;

  MethodDescriptor* outer_method = as_method_signature();
  ClassDescriptor* outer_class = as_class_signature();

  if (outer_class == NULL) { // 'this' is a method signature; use the holder
    outer_class = outer_method->outer_class();
  }

  while (outer_method != NULL || outer_class != NULL) {
    if (outer_method != NULL) {
      for (int i = 0; i < outer_method->type_parameters().length(); ++i) {
        TypeParameter* p = outer_method->type_parameters().at(i);
        if (p->identifier()->equals(id)) {
          *depth = -1; // indicates this this is a method parameter
          return p;
        }
      }
    }
    if (outer_class != NULL) {
      for (int i = 0; i < outer_class->type_parameters().length(); ++i) {
        TypeParameter* p = outer_class->type_parameters().at(i);
        if (p->identifier()->equals(id)) {
          *depth = current_depth;
          return p;
        }
      }
      outer_method = outer_class->outer_method();
      outer_class = outer_class->outer_class();
      ++current_depth;
    }
  }

  if (VerifyGenericSignatures) {
    fatal("Could not resolve identifier");
  }

  return NULL;
}

ClassDescriptor* ClassDescriptor::parse_generic_signature(Klass* klass, TRAPS) {
  return parse_generic_signature(klass, NULL, CHECK_NULL);
}

ClassDescriptor* ClassDescriptor::parse_generic_signature(
      Klass* klass, Symbol* original_name, TRAPS) {

  InstanceKlass* ik = InstanceKlass::cast(klass);
  Symbol* sym = ik->generic_signature();

  ClassDescriptor* spec;

  if (sym == NULL || (spec = ClassDescriptor::parse_generic_signature(sym)) == NULL) {
    spec = ClassDescriptor::placeholder(ik);
  }

  u2 outer_index = get_outer_class_index(ik, CHECK_NULL);
  if (outer_index != 0) {
    if (original_name == NULL) {
      original_name = ik->name();
    }
    Handle class_loader = Handle(THREAD, ik->class_loader());
    Handle protection_domain = Handle(THREAD, ik->protection_domain());

    Symbol* outer_name = ik->constants()->klass_name_at(outer_index);
    Klass* outer = SystemDictionary::find(
        outer_name, class_loader, protection_domain, CHECK_NULL);
    if (outer == NULL && !THREAD->is_Compiler_thread()) {
      if (outer_name == ik->super()->name()) {
        outer = SystemDictionary::resolve_super_or_fail(original_name, outer_name,
                                                        class_loader, protection_domain,
                                                        false, CHECK_NULL);
      }
      else {
        outer = SystemDictionary::resolve_or_fail(outer_name, class_loader,
                                                  protection_domain, false, CHECK_NULL);
      }
    }

    InstanceKlass* outer_ik;
    ClassDescriptor* outer_spec = NULL;
    if (outer == NULL) {
      outer_spec = ClassDescriptor::placeholder(ik);
      assert(false, "Outer class not loaded and not loadable from here");
    } else {
      outer_ik = InstanceKlass::cast(outer);
      outer_spec = parse_generic_signature(outer, original_name, CHECK_NULL);
    }
    spec->set_outer_class(outer_spec);

    u2 encl_method_idx = ik->enclosing_method_method_index();
    if (encl_method_idx != 0 && outer_ik != NULL) {
      ConstantPool* cp = ik->constants();
      u2 name_index = cp->name_ref_index_at(encl_method_idx);
      u2 sig_index = cp->signature_ref_index_at(encl_method_idx);
      Symbol* name = cp->symbol_at(name_index);
      Symbol* sig = cp->symbol_at(sig_index);
      Method* m = outer_ik->find_method(name, sig);
      if (m != NULL) {
        Symbol* gsig = m->generic_signature();
        if (gsig != NULL) {
          MethodDescriptor* gms = MethodDescriptor::parse_generic_signature(gsig, outer_spec);
          spec->set_outer_method(gms);
        }
      } else if (VerifyGenericSignatures) {
        ResourceMark rm;
        stringStream ss;
        ss.print("Could not find method %s %s in class %s",
          name->as_C_string(), sig->as_C_string(), outer_name->as_C_string());
        fatal(ss.as_string());
      }
    }
  }

  spec->bind_variables_to_parameters();
  return spec;
}

ClassDescriptor* ClassDescriptor::placeholder(InstanceKlass* klass) {
  GrowableArray<TypeParameter*> formals;
  GrowableArray<ClassType*> interfaces;
  ClassType* super_type = NULL;

  Klass* super_klass = klass->super();
  if (super_klass != NULL) {
    InstanceKlass* super = InstanceKlass::cast(super_klass);
    super_type = ClassType::from_symbol(super->name());
  }

  for (int i = 0; i < klass->local_interfaces()->length(); ++i) {
    InstanceKlass* iface = InstanceKlass::cast(klass->local_interfaces()->at(i));
    interfaces.append(ClassType::from_symbol(iface->name()));
  }
  return new ClassDescriptor(formals, super_type, interfaces);
}

ClassDescriptor* ClassDescriptor::parse_generic_signature(Symbol* sym) {

  DescriptorStream ds(sym);
  DescriptorStream* STREAM = &ds;

  GrowableArray<TypeParameter*> parameters(8);
  char c = READ();
  if (c == '<') {
    c = READ();
    while (c != '>') {
      PUSH(c);
      TypeParameter* ftp = TypeParameter::parse_generic_signature(CHECK_STREAM);
      parameters.append(ftp);
      c = READ();
    }
  } else {
    PUSH(c);
  }

  EXPECT('L');
  ClassType* super = ClassType::parse_generic_signature(CHECK_STREAM);

  GrowableArray<ClassType*> signatures(2);
  while (!STREAM->at_end()) {
    EXPECT('L');
    ClassType* iface = ClassType::parse_generic_signature(CHECK_STREAM);
    signatures.append(iface);
  }

  EXPECT_END();

  return new ClassDescriptor(parameters, super, signatures);
}

#ifndef PRODUCT
void ClassDescriptor::print_on(outputStream* str) const {
  str->indent().print_cr("ClassDescriptor {");
  {
    streamIndentor si(str);
    if (_type_parameters.length() > 0) {
      str->indent().print_cr("Formals {");
      {
        streamIndentor si(str);
        for (int i = 0; i < _type_parameters.length(); ++i) {
          _type_parameters.at(i)->print_on(str);
        }
      }
      str->indent().print_cr("}");
    }
    if (_super != NULL) {
      str->indent().print_cr("Superclass: ");
      {
        streamIndentor si(str);
        _super->print_on(str);
      }
    }
    if (_interfaces.length() > 0) {
      str->indent().print_cr("SuperInterfaces: {");
      {
        streamIndentor si(str);
        for (int i = 0; i < _interfaces.length(); ++i) {
          _interfaces.at(i)->print_on(str);
        }
      }
      str->indent().print_cr("}");
    }
    if (_outer_method != NULL) {
      str->indent().print_cr("Outer Method: {");
      {
        streamIndentor si(str);
        _outer_method->print_on(str);
      }
      str->indent().print_cr("}");
    }
    if (_outer_class != NULL) {
      str->indent().print_cr("Outer Class: {");
      {
        streamIndentor si(str);
        _outer_class->print_on(str);
      }
      str->indent().print_cr("}");
    }
  }
  str->indent().print_cr("}");
}
#endif // ndef PRODUCT

ClassType* ClassDescriptor::interface_desc(Symbol* sym) {
  for (int i = 0; i < _interfaces.length(); ++i) {
    if (_interfaces.at(i)->identifier()->equals(sym)) {
      return _interfaces.at(i);
    }
  }
  if (VerifyGenericSignatures) {
    fatal("Did not find expected interface");
  }
  return NULL;
}

void ClassDescriptor::bind_variables_to_parameters() {
  if (_outer_class != NULL) {
    _outer_class->bind_variables_to_parameters();
  }
  if (_outer_method != NULL) {
    _outer_method->bind_variables_to_parameters();
  }
  for (int i = 0; i < _type_parameters.length(); ++i) {
    _type_parameters.at(i)->bind_variables_to_parameters(this, i);
  }
  if (_super != NULL) {
    _super->bind_variables_to_parameters(this);
  }
  for (int i = 0; i < _interfaces.length(); ++i) {
    _interfaces.at(i)->bind_variables_to_parameters(this);
  }
}

ClassDescriptor* ClassDescriptor::canonicalize(Context* ctx) {

  GrowableArray<TypeParameter*> type_params(_type_parameters.length());
  for (int i = 0; i < _type_parameters.length(); ++i) {
    type_params.append(_type_parameters.at(i)->canonicalize(ctx, 0));
  }

  ClassDescriptor* outer = _outer_class == NULL ? NULL :
      _outer_class->canonicalize(ctx);

  ClassType* super = _super == NULL ? NULL : _super->canonicalize(ctx, 0);

  GrowableArray<ClassType*> interfaces(_interfaces.length());
  for (int i = 0; i < _interfaces.length(); ++i) {
    interfaces.append(_interfaces.at(i)->canonicalize(ctx, 0));
  }

  MethodDescriptor* md = _outer_method == NULL ? NULL :
      _outer_method->canonicalize(ctx);

  return new ClassDescriptor(type_params, super, interfaces, outer, md);
}

u2 ClassDescriptor::get_outer_class_index(InstanceKlass* klass, TRAPS) {
  int inner_index = InstanceKlass::inner_class_inner_class_info_offset;
  int outer_index = InstanceKlass::inner_class_outer_class_info_offset;
  int name_offset = InstanceKlass::inner_class_inner_name_offset;
  int next_offset = InstanceKlass::inner_class_next_offset;

  if (klass->inner_classes() == NULL || klass->inner_classes()->length() == 0) {
    // No inner class info => no declaring class
    return 0;
  }

  Array<u2>* i_icls = klass->inner_classes();
  ConstantPool* i_cp = klass->constants();
  int i_length = i_icls->length();

  // Find inner_klass attribute
  for (int i = 0; i + next_offset < i_length; i += next_offset) {
    u2 ioff = i_icls->at(i + inner_index);
    u2 ooff = i_icls->at(i + outer_index);
    u2 noff = i_icls->at(i + name_offset);
    if (ioff != 0) {
      // Check to see if the name matches the class we're looking for
      // before attempting to find the class.
      if (i_cp->klass_name_at_matches(klass, ioff) && ooff != 0) {
        return ooff;
      }
    }
  }

  // It may be anonymous; try for that.
  u2 encl_method_class_idx = klass->enclosing_method_class_index();
  if (encl_method_class_idx != 0) {
    return encl_method_class_idx;
  }

  return 0;
}

MethodDescriptor* MethodDescriptor::parse_generic_signature(Method* m, ClassDescriptor* outer) {
  Symbol* generic_sig = m->generic_signature();
  MethodDescriptor* md = NULL;
  if (generic_sig == NULL || (md = parse_generic_signature(generic_sig, outer)) == NULL) {
    md = parse_generic_signature(m->signature(), outer);
  }
  assert(md != NULL, "Could not parse method signature");
  md->bind_variables_to_parameters();
  return md;
}

MethodDescriptor* MethodDescriptor::parse_generic_signature(Symbol* sym, ClassDescriptor* outer) {

  DescriptorStream ds(sym);
  DescriptorStream* STREAM = &ds;

  GrowableArray<TypeParameter*> params(8);
  char c = READ();
  if (c == '<') {
    c = READ();
    while (c != '>') {
      PUSH(c);
      TypeParameter* ftp = TypeParameter::parse_generic_signature(CHECK_STREAM);
      params.append(ftp);
      c = READ();
    }
  } else {
    PUSH(c);
  }

  EXPECT('(');

  GrowableArray<Type*> parameters(8);
  c = READ();
  while (c != ')') {
    PUSH(c);
    Type* arg = Type::parse_generic_signature(CHECK_STREAM);
    parameters.append(arg);
    c = READ();
  }

  Type* rt = Type::parse_generic_signature(CHECK_STREAM);

  GrowableArray<Type*> throws;
  while (!STREAM->at_end()) {
    EXPECT('^');
    Type* spec = Type::parse_generic_signature(CHECK_STREAM);
    throws.append(spec);
  }

  return new MethodDescriptor(params, outer, parameters, rt, throws);
}

void MethodDescriptor::bind_variables_to_parameters() {
  for (int i = 0; i < _type_parameters.length(); ++i) {
    _type_parameters.at(i)->bind_variables_to_parameters(this, i);
  }
  for (int i = 0; i < _parameters.length(); ++i) {
    _parameters.at(i)->bind_variables_to_parameters(this);
  }
  _return_type->bind_variables_to_parameters(this);
  for (int i = 0; i < _throws.length(); ++i) {
    _throws.at(i)->bind_variables_to_parameters(this);
  }
}

bool MethodDescriptor::covariant_match(MethodDescriptor* other, Context* ctx) {

  if (_parameters.length() == other->_parameters.length()) {
    for (int i = 0; i < _parameters.length(); ++i) {
      if (!_parameters.at(i)->covariant_match(other->_parameters.at(i), ctx)) {
        return false;
      }
    }

    if (_return_type->as_primitive() != NULL) {
      return _return_type->covariant_match(other->_return_type, ctx);
    } else {
      // return type is a reference
      return other->_return_type->as_class() != NULL ||
             other->_return_type->as_variable() != NULL ||
             other->_return_type->as_array() != NULL;
    }
  } else {
    return false;
  }
}

MethodDescriptor* MethodDescriptor::canonicalize(Context* ctx) {

  GrowableArray<TypeParameter*> type_params(_type_parameters.length());
  for (int i = 0; i < _type_parameters.length(); ++i) {
    type_params.append(_type_parameters.at(i)->canonicalize(ctx, 0));
  }

  ClassDescriptor* outer = _outer_class == NULL ? NULL :
      _outer_class->canonicalize(ctx);

  GrowableArray<Type*> params(_parameters.length());
  for (int i = 0; i < _parameters.length(); ++i) {
    params.append(_parameters.at(i)->canonicalize(ctx, 0));
  }

  Type* rt = _return_type->canonicalize(ctx, 0);

  GrowableArray<Type*> throws(_throws.length());
  for (int i = 0; i < _throws.length(); ++i) {
    throws.append(_throws.at(i)->canonicalize(ctx, 0));
  }

  return new MethodDescriptor(type_params, outer, params, rt, throws);
}

#ifndef PRODUCT
TempNewSymbol MethodDescriptor::reify_signature(Context* ctx, TRAPS) {
  stringStream ss(256);

  ss.print("(");
  for (int i = 0; i < _parameters.length(); ++i) {
    _parameters.at(i)->reify_signature(&ss, ctx);
  }
  ss.print(")");
  _return_type->reify_signature(&ss, ctx);
  return SymbolTable::new_symbol(ss.base(), (int)ss.size(), THREAD);
}

void MethodDescriptor::print_on(outputStream* str) const {
  str->indent().print_cr("MethodDescriptor {");
  {
    streamIndentor si(str);
    if (_type_parameters.length() > 0) {
      str->indent().print_cr("Formals: {");
      {
        streamIndentor si(str);
        for (int i = 0; i < _type_parameters.length(); ++i) {
          _type_parameters.at(i)->print_on(str);
        }
      }
      str->indent().print_cr("}");
    }
    str->indent().print_cr("Parameters: {");
    {
      streamIndentor si(str);
      for (int i = 0; i < _parameters.length(); ++i) {
        _parameters.at(i)->print_on(str);
      }
    }
    str->indent().print_cr("}");
    str->indent().print_cr("Return Type: ");
    {
      streamIndentor si(str);
      _return_type->print_on(str);
    }

    if (_throws.length() > 0) {
      str->indent().print_cr("Throws: {");
      {
        streamIndentor si(str);
        for (int i = 0; i < _throws.length(); ++i) {
          _throws.at(i)->print_on(str);
        }
      }
      str->indent().print_cr("}");
    }
  }
  str->indent().print_cr("}");
}
#endif // ndef PRODUCT

TypeParameter* TypeParameter::parse_generic_signature(DescriptorStream* STREAM) {
  STREAM->set_mark();
  char c = READ();
  while (c != ':') {
    c = READ();
  }

  Identifier* id = STREAM->identifier_from_mark();

  ClassType* class_bound = NULL;
  GrowableArray<ClassType*> interface_bounds(8);

  c = READ();
  if (c != '>') {
    if (c != ':') {
      EXPECTED(c, 'L');
      class_bound = ClassType::parse_generic_signature(CHECK_STREAM);
      c = READ();
    }

    while (c == ':') {
      EXPECT('L');
      ClassType* fts = ClassType::parse_generic_signature(CHECK_STREAM);
      interface_bounds.append(fts);
      c = READ();
    }
  }
  PUSH(c);

  return new TypeParameter(id, class_bound, interface_bounds);
}

void TypeParameter::bind_variables_to_parameters(Descriptor* sig, int position) {
  if (_class_bound != NULL) {
    _class_bound->bind_variables_to_parameters(sig);
  }
  for (int i = 0; i < _interface_bounds.length(); ++i) {
    _interface_bounds.at(i)->bind_variables_to_parameters(sig);
  }
  _position = position;
}

Type* TypeParameter::resolve(
    Context* ctx, int inner_depth, int ctx_depth) {

  if (inner_depth == -1) {
    // This indicates that the parameter is a method type parameter, which
    // isn't resolveable using the class hierarchy context
    return bound();
  }

  ClassType* provider = ctx->at_depth(ctx_depth);
  if (provider != NULL) {
    for (int i = 0; i < inner_depth && provider != NULL; ++i) {
      provider = provider->outer_class();
    }
    if (provider != NULL) {
      TypeArgument* arg = provider->type_argument_at(_position);
      if (arg != NULL) {
        Type* value = arg->lower_bound();
        return value->canonicalize(ctx, ctx_depth + 1);
      }
    }
  }

  return bound();
}

TypeParameter* TypeParameter::canonicalize(Context* ctx, int ctx_depth) {
  ClassType* bound = _class_bound == NULL ? NULL :
     _class_bound->canonicalize(ctx, ctx_depth);

  GrowableArray<ClassType*> ifaces(_interface_bounds.length());
  for (int i = 0; i < _interface_bounds.length(); ++i) {
    ifaces.append(_interface_bounds.at(i)->canonicalize(ctx, ctx_depth));
  }

  TypeParameter* ret = new TypeParameter(_identifier, bound, ifaces);
  ret->_position = _position;
  return ret;
}

ClassType* TypeParameter::bound() {
  if (_class_bound != NULL) {
    return _class_bound;
  }

  if (_interface_bounds.length() == 1) {
    return _interface_bounds.at(0);
  }

  return ClassType::java_lang_Object(); // TODO: investigate this case
}

#ifndef PRODUCT
void TypeParameter::print_on(outputStream* str) const {
  str->indent().print_cr("Formal: {");
  {
    streamIndentor si(str);

    str->indent().print("Identifier: ");
    _identifier->print_on(str);
    str->print_cr("");
    if (_class_bound != NULL) {
      str->indent().print_cr("Class Bound: ");
      streamIndentor si(str);
      _class_bound->print_on(str);
    }
    if (_interface_bounds.length() > 0) {
      str->indent().print_cr("Interface Bounds: {");
      {
        streamIndentor si(str);
        for (int i = 0; i < _interface_bounds.length(); ++i) {
          _interface_bounds.at(i)->print_on(str);
        }
      }
      str->indent().print_cr("}");
    }
    str->indent().print_cr("Ordinal Position: %d", _position);
  }
  str->indent().print_cr("}");
}
#endif // ndef PRODUCT

Type* Type::parse_generic_signature(DescriptorStream* STREAM) {
  char c = READ();
  switch (c) {
    case 'L':
      return ClassType::parse_generic_signature(CHECK_STREAM);
    case 'T':
      return TypeVariable::parse_generic_signature(CHECK_STREAM);
    case '[':
      return ArrayType::parse_generic_signature(CHECK_STREAM);
    default:
      return new PrimitiveType(c);
  }
}

Identifier* ClassType::parse_generic_signature_simple(GrowableArray<TypeArgument*>* args,
    bool* has_inner, DescriptorStream* STREAM) {
  STREAM->set_mark();

  char c = READ();
  while (c != ';' && c != '.' && c != '<') { c = READ(); }
  Identifier* id = STREAM->identifier_from_mark();

  if (c == '<') {
    c = READ();
    while (c != '>') {
      PUSH(c);
      TypeArgument* arg = TypeArgument::parse_generic_signature(CHECK_STREAM);
      args->append(arg);
      c = READ();
    }
    c = READ();
  }

  *has_inner = (c == '.');
  if (!(*has_inner)) {
    EXPECTED(c, ';');
  }

  return id;
}

ClassType* ClassType::parse_generic_signature(DescriptorStream* STREAM) {
  return parse_generic_signature(NULL, CHECK_STREAM);
}

ClassType* ClassType::parse_generic_signature(ClassType* outer, DescriptorStream* STREAM) {
  GrowableArray<TypeArgument*> args;
  ClassType* gct = NULL;
  bool has_inner = false;

  Identifier* id = parse_generic_signature_simple(&args, &has_inner, STREAM);
  if (id != NULL) {
    gct = new ClassType(id, args, outer);

    if (has_inner) {
      gct = parse_generic_signature(gct, CHECK_STREAM);
    }
  }
  return gct;
}

ClassType* ClassType::from_symbol(Symbol* sym) {
  assert(sym != NULL, "Must not be null");
  GrowableArray<TypeArgument*> args;
  Identifier* id = new Identifier(sym, 0, sym->utf8_length());
  return new ClassType(id, args, NULL);
}

ClassType* ClassType::java_lang_Object() {
  return from_symbol(vmSymbols::java_lang_Object());
}

void ClassType::bind_variables_to_parameters(Descriptor* sig) {
  for (int i = 0; i < _type_arguments.length(); ++i) {
    _type_arguments.at(i)->bind_variables_to_parameters(sig);
  }
  if (_outer_class != NULL) {
    _outer_class->bind_variables_to_parameters(sig);
  }
}

TypeArgument* ClassType::type_argument_at(int i) {
  if (i >= 0 && i < _type_arguments.length()) {
    return _type_arguments.at(i);
  } else {
    return NULL;
  }
}

#ifndef PRODUCT
void ClassType::reify_signature(stringStream* ss, Context* ctx) {
  ss->print("L");
  _identifier->print_on(ss);
  ss->print(";");
}

void ClassType::print_on(outputStream* str) const {
  str->indent().print_cr("Class {");
  {
    streamIndentor si(str);
    str->indent().print("Name: ");
    _identifier->print_on(str);
    str->print_cr("");
    if (_type_arguments.length() != 0) {
      str->indent().print_cr("Type Arguments: {");
      {
        streamIndentor si(str);
        for (int j = 0; j < _type_arguments.length(); ++j) {
          _type_arguments.at(j)->print_on(str);
        }
      }
      str->indent().print_cr("}");
    }
    if (_outer_class != NULL) {
      str->indent().print_cr("Outer Class: ");
      streamIndentor sir(str);
      _outer_class->print_on(str);
    }
  }
  str->indent().print_cr("}");
}
#endif // ndef PRODUCT

bool ClassType::covariant_match(Type* other, Context* ctx) {

  if (other == this) {
    return true;
  }

  TypeVariable* variable = other->as_variable();
  if (variable != NULL) {
    other = variable->resolve(ctx, 0);
  }

  ClassType* outer = outer_class();
  ClassType* other_class = other->as_class();

  if (other_class == NULL ||
      (outer == NULL) != (other_class->outer_class() == NULL)) {
    return false;
  }

  if (!_identifier->equals(other_class->_identifier)) {
    return false;
  }

  if (outer != NULL && !outer->covariant_match(other_class->outer_class(), ctx)) {
    return false;
  }

  return true;
}

ClassType* ClassType::canonicalize(Context* ctx, int ctx_depth) {

  GrowableArray<TypeArgument*> args(_type_arguments.length());
  for (int i = 0; i < _type_arguments.length(); ++i) {
    args.append(_type_arguments.at(i)->canonicalize(ctx, ctx_depth));
  }

  ClassType* outer = _outer_class == NULL ? NULL :
      _outer_class->canonicalize(ctx, ctx_depth);

  return new ClassType(_identifier, args, outer);
}

TypeVariable* TypeVariable::parse_generic_signature(DescriptorStream* STREAM) {
  STREAM->set_mark();
  char c = READ();
  while (c != ';') {
    c = READ();
  }
  Identifier* id = STREAM->identifier_from_mark();

  return new TypeVariable(id);
}

void TypeVariable::bind_variables_to_parameters(Descriptor* sig) {
  _parameter = sig->find_type_parameter(_id, &_inner_depth);
  if (VerifyGenericSignatures && _parameter == NULL) {
    fatal("Could not find formal parameter");
  }
}

Type* TypeVariable::resolve(Context* ctx, int ctx_depth) {
  if (parameter() != NULL) {
    return parameter()->resolve(ctx, inner_depth(), ctx_depth);
  } else {
    if (VerifyGenericSignatures) {
      fatal("Type variable matches no parameter");
    }
    return NULL;
  }
}

bool TypeVariable::covariant_match(Type* other, Context* ctx) {

  if (other == this) {
    return true;
  }

  Context my_context(NULL); // empty, results in erasure
  Type* my_type = resolve(&my_context, 0);
  if (my_type == NULL) {
    return false;
  }

  return my_type->covariant_match(other, ctx);
}

Type* TypeVariable::canonicalize(Context* ctx, int ctx_depth) {
  return resolve(ctx, ctx_depth);
}

#ifndef PRODUCT
void TypeVariable::reify_signature(stringStream* ss, Context* ctx) {
  Type* type = resolve(ctx, 0);
  if (type != NULL) {
    type->reify_signature(ss, ctx);
  }
}

void TypeVariable::print_on(outputStream* str) const {
  str->indent().print_cr("Type Variable {");
  {
    streamIndentor si(str);
    str->indent().print("Name: ");
    _id->print_on(str);
    str->print_cr("");
    str->indent().print_cr("Inner depth: %d", _inner_depth);
  }
  str->indent().print_cr("}");
}
#endif // ndef PRODUCT

ArrayType* ArrayType::parse_generic_signature(DescriptorStream* STREAM) {
  Type* base = Type::parse_generic_signature(CHECK_STREAM);
  return new ArrayType(base);
}

void ArrayType::bind_variables_to_parameters(Descriptor* sig) {
  assert(_base != NULL, "Invalid base");
  _base->bind_variables_to_parameters(sig);
}

bool ArrayType::covariant_match(Type* other, Context* ctx) {
  assert(_base != NULL, "Invalid base");

  if (other == this) {
    return true;
  }

  ArrayType* other_array = other->as_array();
  return (other_array != NULL && _base->covariant_match(other_array->_base, ctx));
}

ArrayType* ArrayType::canonicalize(Context* ctx, int ctx_depth) {
  assert(_base != NULL, "Invalid base");
  return new ArrayType(_base->canonicalize(ctx, ctx_depth));
}

#ifndef PRODUCT
void ArrayType::reify_signature(stringStream* ss, Context* ctx) {
  assert(_base != NULL, "Invalid base");
  ss->print("[");
  _base->reify_signature(ss, ctx);
}

void ArrayType::print_on(outputStream* str) const {
  str->indent().print_cr("Array {");
  {
    streamIndentor si(str);
    _base->print_on(str);
  }
  str->indent().print_cr("}");
}
#endif // ndef PRODUCT

bool PrimitiveType::covariant_match(Type* other, Context* ctx) {

  PrimitiveType* other_prim = other->as_primitive();
  return (other_prim != NULL && _type == other_prim->_type);
}

PrimitiveType* PrimitiveType::canonicalize(Context* ctx, int ctxd) {
  return this;
}

#ifndef PRODUCT
void PrimitiveType::reify_signature(stringStream* ss, Context* ctx) {
  ss->print("%c", _type);
}

void PrimitiveType::print_on(outputStream* str) const {
  str->indent().print_cr("Primitive: '%c'", _type);
}
#endif // ndef PRODUCT

void PrimitiveType::bind_variables_to_parameters(Descriptor* sig) {
}

TypeArgument* TypeArgument::parse_generic_signature(DescriptorStream* STREAM) {
  char c = READ();
  Type* type = NULL;

  switch (c) {
    case '*':
      return new TypeArgument(ClassType::java_lang_Object(), NULL);
      break;
    default:
      PUSH(c);
      // fall-through
    case '+':
    case '-':
      type = Type::parse_generic_signature(CHECK_STREAM);
      if (c == '+') {
        return new TypeArgument(type, NULL);
      } else if (c == '-') {
        return new TypeArgument(ClassType::java_lang_Object(), type);
      } else {
        return new TypeArgument(type, type);
      }
  }
}

void TypeArgument::bind_variables_to_parameters(Descriptor* sig) {
  assert(_lower_bound != NULL, "Invalid lower bound");
  _lower_bound->bind_variables_to_parameters(sig);
  if (_upper_bound != NULL && _upper_bound != _lower_bound) {
    _upper_bound->bind_variables_to_parameters(sig);
  }
}

bool TypeArgument::covariant_match(TypeArgument* other, Context* ctx) {
  assert(_lower_bound != NULL, "Invalid lower bound");

  if (other == this) {
    return true;
  }

  if (!_lower_bound->covariant_match(other->lower_bound(), ctx)) {
    return false;
  }
  return true;
}

TypeArgument* TypeArgument::canonicalize(Context* ctx, int ctx_depth) {
  assert(_lower_bound != NULL, "Invalid lower bound");
  Type* lower = _lower_bound->canonicalize(ctx, ctx_depth);
  Type* upper = NULL;

  if (_upper_bound == _lower_bound) {
    upper = lower;
  } else if (_upper_bound != NULL) {
    upper = _upper_bound->canonicalize(ctx, ctx_depth);
  }

  return new TypeArgument(lower, upper);
}

#ifndef PRODUCT
void TypeArgument::print_on(outputStream* str) const {
  str->indent().print_cr("TypeArgument {");
  {
    streamIndentor si(str);
    if (_lower_bound != NULL) {
      str->indent().print("Lower bound: ");
      _lower_bound->print_on(str);
    }
    if (_upper_bound != NULL) {
      str->indent().print("Upper bound: ");
      _upper_bound->print_on(str);
    }
  }
  str->indent().print_cr("}");
}
#endif // ndef PRODUCT

void Context::Mark::destroy() {
  if (is_active()) {
    _context->reset_to_mark(_marked_size);
  }
  deactivate();
}

void Context::apply_type_arguments(
    InstanceKlass* current, InstanceKlass* super, TRAPS) {
  assert(_cache != NULL, "Cannot use an empty context");
  ClassType* spec = NULL;
  if (current != NULL) {
    ClassDescriptor* descriptor = _cache->descriptor_for(current, CHECK);
    if (super == current->super()) {
      spec = descriptor->super();
    } else {
      spec = descriptor->interface_desc(super->name());
    }
    if (spec != NULL) {
      _type_arguments.push(spec);
    }
  }
}

void Context::reset_to_mark(int size) {
  _type_arguments.trunc_to(size);
}

ClassType* Context::at_depth(int i) const {
  if (i < _type_arguments.length()) {
    return _type_arguments.at(_type_arguments.length() - 1 - i);
  }
  return NULL;
}

#ifndef PRODUCT
void Context::print_on(outputStream* str) const {
  str->indent().print_cr("Context {");
  for (int i = 0; i < _type_arguments.length(); ++i) {
    streamIndentor si(str);
    str->indent().print("leval %d: ", i);
    ClassType* ct = at_depth(i);
    if (ct == NULL) {
      str->print_cr("<empty>");
      continue;
    } else {
      str->print_cr("{");
    }

    for (int j = 0; j < ct->type_arguments_length(); ++j) {
      streamIndentor si(str);
      TypeArgument* ta = ct->type_argument_at(j);
      Type* bound = ta->lower_bound();
      bound->print_on(str);
    }
    str->indent().print_cr("}");
  }
  str->indent().print_cr("}");
}
#endif // ndef PRODUCT

ClassDescriptor* DescriptorCache::descriptor_for(InstanceKlass* ik, TRAPS) {

  ClassDescriptor** existing = _class_descriptors.get(ik);
  if (existing == NULL) {
    ClassDescriptor* cd = ClassDescriptor::parse_generic_signature(ik, CHECK_NULL);
    _class_descriptors.put(ik, cd);
    return cd;
  } else {
    return *existing;
  }
}

MethodDescriptor* DescriptorCache::descriptor_for(
    Method* mh, ClassDescriptor* cd, TRAPS) {
  assert(mh != NULL && cd != NULL, "Should not be NULL");
  MethodDescriptor** existing = _method_descriptors.get(mh);
  if (existing == NULL) {
    MethodDescriptor* md = MethodDescriptor::parse_generic_signature(mh, cd);
    _method_descriptors.put(mh, md);
    return md;
  } else {
    return *existing;
  }
}
MethodDescriptor* DescriptorCache::descriptor_for(Method* mh, TRAPS) {
  ClassDescriptor* cd = descriptor_for(
      InstanceKlass::cast(mh->method_holder()), CHECK_NULL);
  return descriptor_for(mh, cd, THREAD);
}

} // namespace generic
