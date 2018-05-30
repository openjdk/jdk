/*
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
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
#include "classfile/classLoaderData.inline.hpp"
#include "classfile/javaClasses.hpp"
#include "memory/metaspace/printCLDMetaspaceInfoClosure.hpp"
#include "memory/resourceArea.hpp"
#include "runtime/safepoint.hpp"
#include "utilities/globalDefinitions.hpp"
#include "utilities/ostream.hpp"


namespace metaspace {

PrintCLDMetaspaceInfoClosure::PrintCLDMetaspaceInfoClosure(outputStream* out, size_t scale, bool do_print, bool break_down_by_chunktype)
: _out(out), _scale(scale), _do_print(do_print), _break_down_by_chunktype(break_down_by_chunktype)
, _num_loaders(0)
{
  memset(_num_loaders_by_spacetype, 0, sizeof(_num_loaders_by_spacetype));
}

void PrintCLDMetaspaceInfoClosure::do_cld(ClassLoaderData* cld) {

  assert(SafepointSynchronize::is_at_safepoint(), "Must be at a safepoint");

  ClassLoaderMetaspace* msp = cld->metaspace_or_null();
  if (msp == NULL) {
    return;
  }

  // Collect statistics for this class loader metaspace
  ClassLoaderMetaspaceStatistics this_cld_stat;
  msp->add_to_statistics(&this_cld_stat);

  // And add it to the running totals
  _stats_total.add(this_cld_stat);
  _num_loaders ++;
  _stats_by_spacetype[msp->space_type()].add(this_cld_stat);
  _num_loaders_by_spacetype[msp->space_type()] ++;

  // Optionally, print.
  if (_do_print) {

    _out->print(UINTX_FORMAT_W(4) ": ", _num_loaders);

    // Print "CLD for [<loader name>,] instance of <loader class name>"
    // or    "CLD for <anonymous class>, loaded by [<loader name>,] instance of <loader class name>"

    ResourceMark rm;
    const char* name = NULL;
    const char* class_name = NULL;

    // Note: this should also work if unloading:
    Klass* k = cld->class_loader_klass();
    if (k != NULL) {
      class_name = k->external_name();
      Symbol* s = cld->class_loader_name();
      if (s != NULL) {
        name = s->as_C_string();
      }
    } else {
      name = "<bootstrap>";
    }

    // Print
    _out->print("CLD " PTR_FORMAT, p2i(cld));
    if (cld->is_unloading()) {
      _out->print(" (unloading)");
    }
    _out->print(":");
    if (cld->is_anonymous()) {
      _out->print(" <anonymous class>, loaded by");
    }
    if (name != NULL) {
      _out->print(" \"%s\"", name);
    }
    if (class_name != NULL) {
      _out->print(" instance of %s", class_name);
    }

    _out->cr();

    // Print statistics
    this_cld_stat.print_on(_out, _scale, _break_down_by_chunktype);
    _out->cr();

  }

}

} // namespace metaspace

