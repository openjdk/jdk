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
#include "runtime/arguments.hpp"
#include "runtime/commandLineFlagConstraintsCompiler.hpp"
#include "runtime/globals.hpp"
#include "utilities/defaultStream.hpp"

Flag::Error AliasLevelConstraintFunc(bool verbose, intx* value) {
  if (CommandLineFlags::finishedInitializing() == true) {
    if ((*value <= 1) && (Arguments::mode() == Arguments::_comp)) {
      if (verbose == true) {
        jio_fprintf(defaultStream::error_stream(),
                  "AliasLevel (" INTX_FORMAT ") is not compatible "
                  "with -Xcomp \n",
                  *value);
      }
      return Flag::VIOLATES_CONSTRAINT;
    }
  }
  return Flag::SUCCESS;
}
