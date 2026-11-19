/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_JFR_INSTRUMENTATION_JFRCLASSTRANSFORMER_HPP
#define SHARE_JFR_INSTRUMENTATION_JFRCLASSTRANSFORMER_HPP

#include "memory/allStatic.hpp"
#include "utilities/exceptions.hpp"

class ClassFileParser;
class ClassFileStream;
class InstanceKlass;

/*
 * Contains common functionality used by method and event instrumentation.
 */
class JfrClassTransformer : AllStatic {
 private:
  static InstanceKlass* create_new_instance_klass(InstanceKlass* ik, ClassFileStream* stream, TRAPS);
  static const InstanceKlass* klass_being_redefined(const InstanceKlass* ik, JvmtiThreadState* state);

 public:
  static const InstanceKlass* find_existing_klass(const InstanceKlass* ik, JavaThread* thread);
  static InstanceKlass* create_instance_klass(InstanceKlass*& ik, ClassFileStream* stream, bool is_initial_load, JavaThread* thread);
  static void copy_traceid(const InstanceKlass* ik, const InstanceKlass* new_ik);
  static void transfer_cached_class_file_data(InstanceKlass* ik, InstanceKlass* new_ik, const ClassFileParser& parser, JavaThread* thread);
  static void rewrite_klass_pointer(InstanceKlass*& ik, InstanceKlass* new_ik, ClassFileParser& parser, const JavaThread* thread);
  static void cache_class_file_data(InstanceKlass* new_ik, const ClassFileStream* new_stream, const JavaThread* thread);
};

#endif // SHARE_JFR_INSTRUMENTATION_JFRCLASSTRANSFORMER_HPP
