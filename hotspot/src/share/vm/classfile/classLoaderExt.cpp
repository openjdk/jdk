/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
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
#include "classfile/classListParser.hpp"
#include "classfile/classLoaderExt.hpp"
#include "classfile/symbolTable.hpp"
#include "classfile/systemDictionary.hpp"


Klass* ClassLoaderExt::load_one_class(ClassListParser* parser, TRAPS) {
  TempNewSymbol class_name_symbol = SymbolTable::new_symbol(parser->current_class_name(), THREAD);
  guarantee(!HAS_PENDING_EXCEPTION, "Exception creating a symbol.");
  return SystemDictionary::resolve_or_null(class_name_symbol, THREAD);
}
