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

#ifndef SHARE_MEMORY_METASPACE_METASPACECOMMON_HPP_
#define SHARE_MEMORY_METASPACE_METASPACECOMMON_HPP_

#include "utilities/globalDefinitions.hpp"


class outputStream;

namespace metaspace {
namespace internals {

// Print a size, in words, scaled.
void print_scaled_words(outputStream* st, size_t word_size, size_t scale = 0, int width = -1);

// Convenience helper: prints a size value and a percentage.
void print_scaled_words_and_percentage(outputStream* st, size_t word_size, size_t compare_word_size, size_t scale = 0, int width = -1);

// Print a human readable size.
// byte_size: size, in bytes, to be printed.
// scale: one of 1 (byte-wise printing), sizeof(word) (word-size printing), K, M, G (scaled by KB, MB, GB respectively,
//         or 0, which means the best scale is choosen dynamically.
// width: printing width.
void print_human_readable_size(outputStream* st, size_t byte_size, size_t scale = 0, int width = -1);

// Prints a percentage value. Values smaller than 1% but not 0 are displayed as "<1%", values
// larger than 99% but not 100% are displayed as ">100%".
void print_percentage(outputStream* st, size_t total, size_t part);

} // namespace internals
} // namespace metaspace

#endif /* SHARE_MEMORY_METASPACE_METASPACESTATISTICS_HPP_ */
