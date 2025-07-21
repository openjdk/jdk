/*
 * Copyright (c) 2021, 2023, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_GC_SHARED_STRINGDEDUP_STRINGDEDUPPROCESSOR_HPP
#define SHARE_GC_SHARED_STRINGDEDUP_STRINGDEDUPPROCESSOR_HPP

#include "gc/shared/stringdedup/stringDedup.hpp"
#include "memory/allocation.hpp"
#include "utilities/macros.hpp"

class CollectedHeap;
class JavaThread;
class OopStorage;

// This class performs string deduplication.  There is only one instance of
// this class.  It processes deduplication requests.  It also manages the
// deduplication table, performing resize and cleanup operations as needed.
// This includes managing the OopStorage objects used to hold requests.
//
// Processing periodically checks for and yields at safepoints.  Processing of
// requests is performed in incremental chunks.  The Table provides
// incremental operations for resizing and for removing dead entries, so
// safepoint checks can be performed between steps in those operations.
class StringDedup::Processor : public CHeapObj<mtGC> {
  friend class CollectedHeap;

  Processor();
  ~Processor() = default;

  NONCOPYABLE(Processor);

  static OopStorage* _storages[2];
  static StorageUse* volatile _storage_for_requests;
  static StorageUse* _storage_for_processing;

  JavaThread* _thread;

  // Wait until there are requests to be processed.  The storage for requests
  // and storage for processing are swapped; the former requests storage
  // becomes the current processing storage, and vice versa.
  // precondition: the processing storage is empty.
  void wait_for_requests() const;

  // Yield if requested.
  void yield() const;

  class ProcessRequest;
  void process_requests() const;
  void cleanup_table(bool grow_only, bool force) const;

  void log_statistics();

public:
  static void initialize();

  static void initialize_storage();
  static StorageUse* storage_for_requests();

  // Use thread as the deduplication thread.
  // precondition: thread == Thread::current()
  void run(JavaThread* thread);
};

#endif // SHARE_GC_SHARED_STRINGDEDUP_STRINGDEDUPPROCESSOR_HPP
