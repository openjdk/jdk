/*
 * Copyright (c) 1997, 2023, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_CLASSFILE_JAVACLASSESIMPL_HPP
#define SHARE_CLASSFILE_JAVACLASSESIMPL_HPP

#include "cds/serializeClosure.hpp"
#include "classfile/javaClasses.hpp"
#include "runtime/continuationJavaClasses.hpp"
#include "utilities/macros.hpp"

#define ALL_INJECTED_FIELDS(macro)          \
  STRING_INJECTED_FIELDS(macro)             \
  CLASS_INJECTED_FIELDS(macro)              \
  CLASSLOADER_INJECTED_FIELDS(macro)        \
  RESOLVEDMETHOD_INJECTED_FIELDS(macro)     \
  MEMBERNAME_INJECTED_FIELDS(macro)         \
  CALLSITECONTEXT_INJECTED_FIELDS(macro)    \
  STACKFRAMEINFO_INJECTED_FIELDS(macro)     \
  MODULE_INJECTED_FIELDS(macro)             \
  THREAD_INJECTED_FIELDS(macro)             \
  VTHREAD_INJECTED_FIELDS(macro)            \
  INTERNALERROR_INJECTED_FIELDS(macro)      \
  STACKCHUNK_INJECTED_FIELDS(macro)

#define INJECTED_FIELD_COMPUTE_OFFSET(klass, name, signature, may_be_java) \
  klass::_##name##_offset = JavaClasses::compute_injected_offset(InjectedFieldID::klass##_##name##_enum);

#if INCLUDE_CDS
#define INJECTED_FIELD_SERIALIZE_OFFSET(klass, name, signature, may_be_java) \
  f->do_int(&_##name##_offset);
#endif

#if INCLUDE_CDS
#define FIELD_SERIALIZE_OFFSET(offset, klass, name, signature, is_static) \
  f->do_int(&offset)
#endif

#define FIELD_COMPUTE_OFFSET(offset, klass, name, signature, is_static) \
  JavaClasses::compute_offset(offset, klass, name, vmSymbols::signature(), is_static)


#define DECLARE_INJECTED_FIELD_ENUM(klass, name, signature, may_be_java) \
  klass##_##name##_enum,

enum class InjectedFieldID : int {
  ALL_INJECTED_FIELDS(DECLARE_INJECTED_FIELD_ENUM)
  MAX_enum
};

#undef DECLARE_INJECTED_FIELD_ENUM

#endif // SHARE_CLASSFILE_JAVACLASSESIMPL_HPP
