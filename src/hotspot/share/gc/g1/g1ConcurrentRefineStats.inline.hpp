/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_GC_G1_G1CONCURRENTREFINESTATS_INLINE_HPP
#define SHARE_GC_G1_G1CONCURRENTREFINESTATS_INLINE_HPP

#include "gc/g1/g1ConcurrentRefineStats.hpp"

inline jlong G1ConcurrentRefineStats::sweep_duration() const {
  return _sweep_duration.load_relaxed() - yield_during_sweep_duration();
}

inline jlong G1ConcurrentRefineStats::yield_during_sweep_duration() const {
  return _yield_during_sweep_duration.load_relaxed();
}

inline jlong G1ConcurrentRefineStats::refine_duration() const {
  return _refine_duration.load_relaxed();
}

inline size_t G1ConcurrentRefineStats::refined_cards() const {
  return cards_not_clean();
}

inline size_t G1ConcurrentRefineStats::cards_scanned() const {
  return _cards_scanned.load_relaxed();
}

inline size_t G1ConcurrentRefineStats::cards_clean() const {
  return _cards_clean.load_relaxed();
}

inline size_t G1ConcurrentRefineStats::cards_not_clean() const {
  return cards_scanned() - cards_clean();
}

inline size_t G1ConcurrentRefineStats::cards_not_parsable() const {
  return _cards_not_parsable.load_relaxed();
}

inline size_t G1ConcurrentRefineStats::cards_already_refer_to_cset() const {
  return _cards_already_refer_to_cset.load_relaxed();
}

inline size_t G1ConcurrentRefineStats::cards_refer_to_cset() const {
  return _cards_refer_to_cset.load_relaxed();
}

inline size_t G1ConcurrentRefineStats::cards_no_cross_region() const {
  return _cards_no_cross_region.load_relaxed();
}

inline size_t G1ConcurrentRefineStats::cards_pending() const {
  return cards_not_clean() - cards_already_refer_to_cset();
}

inline size_t G1ConcurrentRefineStats::cards_to_cset() const {
  return cards_already_refer_to_cset() + cards_refer_to_cset();
}

inline void G1ConcurrentRefineStats::inc_sweep_time(jlong t) {
  _sweep_duration.store_relaxed(_sweep_duration.load_relaxed() + t);
}

inline void G1ConcurrentRefineStats::inc_yield_during_sweep_duration(jlong t) {
  _yield_during_sweep_duration.store_relaxed(yield_during_sweep_duration() + t);
}

inline void G1ConcurrentRefineStats::inc_refine_duration(jlong t) {
  _refine_duration.store_relaxed(refine_duration() + t);
}

inline void G1ConcurrentRefineStats::inc_cards_scanned(size_t increment) {
  _cards_scanned.store_relaxed(cards_scanned() + increment);
}

inline void G1ConcurrentRefineStats::inc_cards_clean(size_t increment) {
  _cards_clean.store_relaxed(cards_clean() + increment);
}

inline void G1ConcurrentRefineStats::inc_cards_not_parsable() {
  _cards_not_parsable.store_relaxed(cards_not_parsable() + 1);
}

inline void G1ConcurrentRefineStats::inc_cards_already_refer_to_cset() {
  _cards_already_refer_to_cset.store_relaxed(cards_already_refer_to_cset() + 1);
}

inline void G1ConcurrentRefineStats::inc_cards_refer_to_cset() {
  _cards_refer_to_cset.store_relaxed(cards_refer_to_cset() + 1);
}

inline void G1ConcurrentRefineStats::inc_cards_no_cross_region() {
  _cards_no_cross_region.store_relaxed(cards_no_cross_region() + 1);
}

#endif // SHARE_GC_G1_G1CONCURRENTREFINESTATS_INLINE_HPP
