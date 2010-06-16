/*
 * Copyright (c) 1999, 2007, Oracle and/or its affiliates. All rights reserved.
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

#include "incls/_precompiled.incl"
#include "incls/_vtune_linux.cpp.incl"

// empty implementation

void VTune::start_GC() {}
void VTune::end_GC() {}
void VTune::start_class_load() {}
void VTune::end_class_load() {}
void VTune::exit() {}
void VTune::register_stub(const char* name, address start, address end) {}

void VTune::create_nmethod(nmethod* nm) {}
void VTune::delete_nmethod(nmethod* nm) {}

void vtune_init() {}


// Reconciliation History
// vtune_solaris.cpp    1.8 99/07/12 23:54:21
// End
