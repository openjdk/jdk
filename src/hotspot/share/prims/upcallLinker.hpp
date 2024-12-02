/*
 * Copyright (c) 2020, 2022, Oracle and/or its affiliates. All rights reserved.
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
 */

#ifndef SHARE_VM_PRIMS_UPCALLLINKER_HPP
#define SHARE_VM_PRIMS_UPCALLLINKER_HPP

#include "asm/codeBuffer.hpp"
#include "code/codeBlob.hpp"
#include "prims/foreignGlobals.hpp"

class JavaThread;

class UpcallLinker {
private:
  static JavaThread* maybe_attach_and_get_thread();

  static JavaThread* on_entry(UpcallStub::FrameData* context);
  static void on_exit(UpcallStub::FrameData* context);
public:
  static address make_upcall_stub(jobject mh, Symbol* signature,
                                  BasicType* out_sig_bt, int total_out_args,
                                  BasicType ret_type,
                                  jobject jabi, jobject jconv,
                                  bool needs_return_buffer, int ret_buf_size);

  // public for stubGenerator
  static void handle_uncaught_exception(oop exception);
};

#endif // SHARE_VM_PRIMS_UPCALLLINKER_HPP
