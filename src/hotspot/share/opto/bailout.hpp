/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2023, Red Hat Inc. All rights reserved.
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

#ifndef SHARE_OPTO_BAILOUT_HPP
#define SHARE_OPTO_BAILOUT_HPP

#include "utilities/debug.hpp"
#include "utilities/globalDefinitions.hpp"

/*

enum class BailState {
  ok = 0, failed = 1
};

#define BAIL              __bail__
#define BAILARG           BailState& BAIL

#define DO_BAIL           { BAIL = BailState::failed; return; }
#define DO_BAIL_(res)     { BAIL = BailState::failed; return (res); }

#define BAILED            BAIL == BailState::failed
*/

#define CHECK_BAIL        { if (failing()) return; }
#define CHECK_BAIL_(res)  { if (failing()) return (res); }

#define CHECKED(FUNCTIONCALL)          FUNCTIONCALL; CHECK_BAIL;
#define CHECKED_(FUNCTIONCALL, res)    FUNCTIONCALL; CHECK_BAIL_(res);

// This is a noop that just serves as marking
#define CHECKED_DONTRETURN(FUNCTIONCALL)  FUNCTIONCALL

// For convenience, some common variants in short form
#define CHECK_BAIL_true   CHECK_BAIL_(true);
#define CHECK_BAIL_false  CHECK_BAIL_(false);
#define CHECK_BAIL_0      CHECK_BAIL_(0);
#define CHECK_BAIL_neg1   CHECK_BAIL_(-1);
#define CHECK_BAIL_NULL   CHECK_BAIL_(nullptr);

#define CHECKED_true(FUNCTIONCALL)     CHECKED_(FUNCTIONCALL, true)
#define CHECKED_false(FUNCTIONCALL)    CHECKED_(FUNCTIONCALL, false)
#define CHECKED_0(FUNCTIONCALL)        CHECKED_(FUNCTIONCALL, 0)
#define CHECKED_neg1(FUNCTIONCALL)     CHECKED_(FUNCTIONCALL, -1)
#define CHECKED_NULL(FUNCTIONCALL)     CHECKED_(FUNCTIONCALL, nullptr)

#endif // SHARE_OPTO_BAILOUT_HPP
