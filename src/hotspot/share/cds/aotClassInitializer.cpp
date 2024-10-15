/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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
#include "cds/aotClassInitializer.hpp"
#include "cds/archiveBuilder.hpp"
#include "cds/cdsConfig.hpp"
#include "oops/instanceKlass.inline.hpp"

class AOTClassInitializer::AllowedSpec {
  const char* _class_name;
  bool _is_prefix;
  int _len;
public:
  AllowedSpec(const char* class_name, bool is_prefix = false)
    : _class_name(class_name), _is_prefix(is_prefix)
  {
    _len = (class_name == nullptr) ? 0 : (int)strlen(class_name);
  }
  const char* class_name() { return _class_name; }

  bool matches(Symbol* name, int len) {
    if (_is_prefix) {
      return len >= _len && name->starts_with(_class_name);
    } else {
      return len == _len && name->equals(_class_name);
    }
  }
};


bool AOTClassInitializer::is_allowed(AllowedSpec* specs, InstanceKlass* ik) {
  Symbol* name = ik->name();
  int len = name->utf8_length();
  for (AllowedSpec* s = specs; s->class_name() != nullptr; s++) {
    if (s->matches(name, len)) {
      // If a type is included in the tables inside can_archive_initialized_mirror(), we require that
      //   - all super classes must be included
      //   - all super interfaces that have <clinit> must be included.
      // This ensures that in the production run, we don't run the <clinit> of a supertype but skips
      // ik's <clinit>.
      if (ik->java_super() != nullptr) {
        DEBUG_ONLY(ResourceMark rm);
        assert(AOTClassInitializer::can_archive_initialized_mirror(ik->java_super()),
               "super class %s of %s must be aot-initialized", ik->java_super()->external_name(),
               ik->external_name());
      }

      Array<InstanceKlass*>* interfaces = ik->local_interfaces();
      int len = interfaces->length();
      for (int i = 0; i < len; i++) {
        InstanceKlass* intf = interfaces->at(i);
        if (intf->class_initializer() != nullptr) {
          assert(AOTClassInitializer::can_archive_initialized_mirror(intf),
                 "super interface %s (which has <clinit>) of %s must be aot-initialized", intf->external_name(),
                 ik->external_name());
        }
      }

      return true;
    }
  }
  return false;
}


bool AOTClassInitializer::can_archive_initialized_mirror(InstanceKlass* ik) {
  assert(!ArchiveBuilder::current()->is_in_buffer_space(ik), "must be source klass");
  if (!CDSConfig::is_initing_classes_at_dump_time()) {
    return false;
  }

  if (!ik->is_initialized()) {
    return false;
  }

  if (ik->java_super() == vmClasses::Enum_klass()) {
    return true;
  }

  const bool IS_PREFIX = true;

  {
    static AllowedSpec specs[] = {
      {"java/lang/Object"},
      {"java/lang/Enum"},
     {nullptr}
    };
    if (is_allowed(specs, ik)) {
      return true;
    }
  }

  {
    // These classes are special cases needed to support the aot-caching of
    // java.lang.invoke.MethodType instances:
    // - MethodType points to sun.invoke.util.Wrapper enums
    // - The Wrapper enums point to static final fields in these classes.
    //   E.g., ConstantDescs.CD_Boolean.
    // - If we re-run the <clinit> of these classes again during the production
    //   run, ConstantDescs.CD_Boolean will get a new value that has a different
    //   object identity than the value referenced by the the Wrapper enums.
    // - However, Wrapper requires object identity (it allows the use of == to
    //   test the equality of ClassDesc, etc).
    // Therefore, we must preserve the static fields of these classes from
    // the assembly phase.
    static AllowedSpec specs[] = {
      {"java/lang/constant/DynamicConstantDesc"},
      {"jdk/internal/constant/PrimitiveClassDescImpl"},
      {"jdk/internal/constant/ReferenceClassDescImpl"},
      {"java/lang/constant/ConstantDescs"},
      {nullptr}
    };
    if (is_allowed(specs, ik)) {
      return true;
    }
  }

  return false;
}

#ifdef ASSERT
void AOTClassInitializer::assert_no_clinit_will_run_for_aot_init_class(InstanceKlass* ik) {
  assert(ik->has_aot_initialized_mirror(), "must be");

  InstanceKlass* s = ik->java_super();
  if (s != nullptr) {
    DEBUG_ONLY(ResourceMark rm);
    assert(s->is_initialized(), "super class %s of aot-inited class %s must have been initialized",
           s->external_name(), ik->external_name());
    AOTClassInitializer::assert_no_clinit_will_run_for_aot_init_class(s);
  }

  Array<InstanceKlass*>* interfaces = ik->local_interfaces();
  int len = interfaces->length();
  for (int i = 0; i < len; i++) {
    InstanceKlass* intf = interfaces->at(i);
    if (!intf->is_initialized()) {
      // Note: interfaces with no <clinit> are not marked as is_initialized().
      assert(intf->class_initializer() == nullptr, "uninitialized super interface %s of aot-inited class %s must not have <clinit>",
             intf->external_name(), ik->external_name());
    }
  }
}
#endif
