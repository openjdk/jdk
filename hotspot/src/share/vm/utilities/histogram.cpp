/*
 * Copyright (c) 1998, 2004, Oracle and/or its affiliates. All rights reserved.
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

# include "incls/_precompiled.incl"
# include "incls/_histogram.cpp.incl"

#ifdef ASSERT

////////////////// HistogramElement ////////////////////////

HistogramElement::HistogramElement() {
  _count = 0;
}

int HistogramElement::count() {
  return _count;
}

const char* HistogramElement::name() {
  return _name;
}

void HistogramElement::increment_count() {
  // We can't use the accessor :-(.
  Atomic::inc(&_count);
}

int HistogramElement::compare(HistogramElement* e1,HistogramElement* e2) {
  if(e1->count() > e2->count()) {
    return -1;
  } else if(e1->count() < e2->count()) {
    return 1;
  }
  return 0;
}

void HistogramElement::print_on(outputStream* st) const {
  st->print("%10d   ",((HistogramElement*)this)->count());
  st->print_cr("%s",((HistogramElement*)this)->name());
}

////////////////// Histogram ////////////////////////

int Histogram::sort_helper(HistogramElement** e1, HistogramElement** e2) {
  return (*e1)->compare(*e1,*e2);
}

Histogram::Histogram(const char* title,int estimatedCount) {
  _title = title;
  _elements = new (ResourceObj::C_HEAP) GrowableArray<HistogramElement*>(estimatedCount,true);
}

void Histogram::add_element(HistogramElement* element) {
  // Note, we need to add locking !
  elements()->append(element);
}

void Histogram::print_header(outputStream* st) {
  st->print_cr("%s",title());
  st->print_cr("--------------------------------------------------");
}

void Histogram::print_elements(outputStream* st) {
  elements()->sort(Histogram::sort_helper);
  jint total = 0;
  for(int i=0; i < elements()->length(); i++) {
    elements()->at(i)->print();
    total += elements()->at(i)->count();
  }
  st->print("%10d   ", total);
  st->print_cr("Total");
}

void Histogram::print_on(outputStream* st) const {
  ((Histogram*)this)->print_header(st);
  ((Histogram*)this)->print_elements(st);
}

#endif
