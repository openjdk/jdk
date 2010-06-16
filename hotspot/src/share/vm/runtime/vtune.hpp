/*
 * Copyright (c) 1998, 2007, Oracle and/or its affiliates. All rights reserved.
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

// Interface to Intel's VTune profiler.

class VTune : AllStatic {
 public:
   static void create_nmethod(nmethod* nm);      // register newly created nmethod
   static void delete_nmethod(nmethod* nm);      // unregister nmethod before discarding it

   static void register_stub(const char* name, address start, address end);
                                                 // register internal VM stub
   static void start_GC();                       // start/end of GC or scavenge
   static void end_GC();

   static void start_class_load();               // start/end of class loading
   static void end_class_load();

   static void exit();                           // VM exit
};


// helper objects
class VTuneGCMarker : StackObj {
 public:
   VTuneGCMarker() { VTune::start_GC(); }
  ~VTuneGCMarker() { VTune::end_GC(); }
};

class VTuneClassLoadMarker : StackObj {
 public:
   VTuneClassLoadMarker() { VTune::start_class_load(); }
  ~VTuneClassLoadMarker() { VTune::end_class_load(); }
};
