/*
 * Copyright (c) 2000, 2022, Oracle and/or its affiliates. All rights reserved.
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
#include "gc/shared/blockOffsetTable.hpp"
#include "utilities/globalDefinitions.hpp"

uint BOTConstants::_log_card_size = 0;
uint BOTConstants::_log_card_size_in_words = 0;
uint BOTConstants::_card_size = 0;
uint BOTConstants::_card_size_in_words = 0;

void BOTConstants::initialize_bot_size(uint card_shift) {
  _log_card_size =  card_shift;
  _log_card_size_in_words = _log_card_size - LogHeapWordSize;
  _card_size = 1 << _log_card_size;
  _card_size_in_words = 1 << _log_card_size_in_words;
}
