/*
 * Copyright (c) 1998, 2014, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_VM_UTILITIES_HISTOGRAM_HPP
#define SHARE_VM_UTILITIES_HISTOGRAM_HPP

#include "memory/allocation.hpp"
#include "runtime/os.hpp"
#include "utilities/growableArray.hpp"

// This class provides a framework for collecting various statistics.
// The current implementation is oriented towards counting invocations
// of various types, but that can be easily changed.
//
// To use it, you need to declare a Histogram*, and a subtype of
// HistogramElement:
//
//  HistogramElement* MyHistogram;
//
//  class MyHistogramElement : public HistogramElement {
//    public:
//      MyHistogramElement(char* name);
//  };
//
//  MyHistogramElement::MyHistogramElement(char* elementName) {
//    _name = elementName;
//
//    if(MyHistogram == NULL)
//      MyHistogram = new Histogram("My Call Counts",100);
//
//    MyHistogram->add_element(this);
//  }
//
//  #define MyCountWrapper(arg) static MyHistogramElement* e = new MyHistogramElement(arg); e->increment_count()
//
// This gives you a simple way to count invocations of specfic functions:
//
// void a_function_that_is_being_counted() {
//   MyCountWrapper("FunctionName");
//   ...
// }
//
// To print the results, invoke print() on your Histogram*.

#ifdef ASSERT

class HistogramElement : public CHeapObj<mtInternal> {
 protected:
  jint _count;
  const char* _name;

 public:
  HistogramElement();
  virtual int count();
  virtual const char* name();
  virtual void increment_count();
  void print_on(outputStream* st) const;
  virtual int compare(HistogramElement* e1,HistogramElement* e2);
};

class Histogram : public CHeapObj<mtInternal> {
 protected:
  GrowableArray<HistogramElement*>* _elements;
  GrowableArray<HistogramElement*>* elements() { return _elements; }
  const char* _title;
  const char* title() { return _title; }
  static int sort_helper(HistogramElement** e1,HistogramElement** e2);
  virtual void print_header(outputStream* st);
  virtual void print_elements(outputStream* st);

 public:
  Histogram(const char* title,int estimatedSize);
  virtual void add_element(HistogramElement* element);
  void print_on(outputStream* st) const;
};

#endif

#endif // SHARE_VM_UTILITIES_HISTOGRAM_HPP
