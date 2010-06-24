/*
 * Copyright (c) 1998, 2005, Oracle and/or its affiliates. All rights reserved.
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

static int binary_search(int key, LookupswitchPair* array, int n);

static address iload (JavaThread* thread);
static address aload (JavaThread* thread);
static address istore(JavaThread* thread);
static address astore(JavaThread* thread);
static address iinc  (JavaThread* thread);



// native method calls

class SignatureHandlerGenerator: public NativeSignatureIterator {
 private:
  MacroAssembler* _masm;

  void pass_word(int size_of_arg, int offset_in_arg);
  void pass_int()    { pass_word(1, 0); }
  void pass_long();
  void pass_double();
  void pass_float();
  void pass_object();

 public:
  // Creation
  SignatureHandlerGenerator(methodHandle method, CodeBuffer* buffer) : NativeSignatureIterator(method) {
    _masm = new MacroAssembler(buffer);
  }

  // Code generation
  void generate( uint64_t fingerprint );
};
