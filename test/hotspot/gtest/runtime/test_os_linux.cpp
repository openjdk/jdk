/*
 * Copyright (c) 2018, 2025, Oracle and/or its affiliates. All rights reserved.
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
 */


#ifdef LINUX

#include "os_linux.hpp"
#include "prims/jniCheck.hpp"
#include "runtime/globals.hpp"
#include "runtime/os.hpp"
#include "utilities/align.hpp"
#include "utilities/decoder.hpp"
#include "concurrentTestRunner.inline.hpp"
#include "testutils.hpp"
#include "unittest.hpp"

#include <sys/mman.h>
#include <sys/prctl.h>

static bool using_explicit_hugepages()  { return UseLargePages && !UseTransparentHugePages; }

namespace {
  static void small_page_write(void* addr, size_t size) {
    size_t page_size = os::vm_page_size();

    char* end = (char*)addr + size;
    for (char* p = (char*)addr; p < end; p += page_size) {
      *p = 1;
    }
  }

  class HugeTlbfsMemory : private ::os::Linux {
    char* const _ptr;
    const size_t _size;
   public:
    static char* reserve_memory_special_huge_tlbfs(size_t bytes, size_t alignment, size_t page_size, char* req_addr, bool exec) {
      return os::reserve_memory_special(bytes, alignment, page_size, req_addr, exec);
    }
    HugeTlbfsMemory(char* const ptr, size_t size) : _ptr(ptr), _size(size) { }
    ~HugeTlbfsMemory() {
      if (_ptr != nullptr) {
        os::release_memory_special(_ptr, _size);
      }
    }
  };

  // have to use these functions, as gtest's _PRED macros don't like is_aligned
  // nor (is_aligned<size_t, size_t>)
  static bool is_size_aligned(size_t size, size_t alignment) {
    return is_aligned(size, alignment);
  }
  static bool is_ptr_aligned(char* ptr, size_t alignment) {
    return is_aligned(ptr, alignment);
  }
}

TEST_VM(os_linux, reserve_memory_special_huge_tlbfs_size_aligned) {
  if (!using_explicit_hugepages()) {
    return;
  }
  size_t lp = os::large_page_size();

  for (size_t size = lp; size <= lp * 10; size += lp) {
    char* addr = HugeTlbfsMemory::reserve_memory_special_huge_tlbfs(size, lp, lp, nullptr, false);

    if (addr != nullptr) {
      HugeTlbfsMemory mr(addr, size);
      small_page_write(addr, size);
    }
  }
}

TEST_VM(os_linux, reserve_memory_special_huge_tlbfs_size_not_aligned_without_addr) {
  if (!using_explicit_hugepages()) {
    return;
  }
  size_t lp = os::large_page_size();
  size_t ag = os::vm_allocation_granularity();

  // sizes to test
  const size_t sizes[] = {
    lp, lp + ag, lp + lp / 2, lp * 2,
    lp * 2 + ag, lp * 2 - ag, lp * 2 + lp / 2,
    lp * 10, lp * 10 + lp / 2
  };
  const int num_sizes = sizeof(sizes) / sizeof(size_t);
  for (int i = 0; i < num_sizes; i++) {
    const size_t size = sizes[i];
    for (size_t alignment = ag; is_size_aligned(size, alignment); alignment *= 2) {
      char* p = HugeTlbfsMemory::reserve_memory_special_huge_tlbfs(size, alignment, lp, nullptr, false);
      if (p != nullptr) {
        HugeTlbfsMemory mr(p, size);
        EXPECT_PRED2(is_ptr_aligned, p, alignment) << " size = " << size;
        small_page_write(p, size);
      }
    }
  }
}

TEST_VM(os_linux, reserve_memory_special_huge_tlbfs_size_not_aligned_with_good_req_addr) {
  if (!using_explicit_hugepages()) {
    return;
  }
  size_t lp = os::large_page_size();
  size_t ag = os::vm_allocation_granularity();

  // sizes to test
  const size_t sizes[] = {
    lp, lp + ag, lp + lp / 2, lp * 2,
    lp * 2 + ag, lp * 2 - ag, lp * 2 + lp / 2,
    lp * 10, lp * 10 + lp / 2
  };
  const int num_sizes = sizeof(sizes) / sizeof(size_t);

  // Pre-allocate an area as large as the largest allocation
  // and aligned to the largest alignment we will be testing.
  const size_t mapping_size = sizes[num_sizes - 1] * 2;
  char* const mapping = (char*) ::mmap(nullptr, mapping_size,
      PROT_NONE, MAP_PRIVATE|MAP_ANONYMOUS|MAP_NORESERVE,
      -1, 0);
  ASSERT_TRUE(mapping != MAP_FAILED) << " mmap failed, mapping_size = " << mapping_size;
  // Unmap the mapping, it will serve as a value for a "good" req_addr
  ::munmap(mapping, mapping_size);

  for (int i = 0; i < num_sizes; i++) {
    const size_t size = sizes[i];
    for (size_t alignment = ag; is_size_aligned(size, alignment); alignment *= 2) {
      // req_addr must be at least large page aligned.
      char* const req_addr = align_up(mapping, MAX2(alignment, lp));
      char* p = HugeTlbfsMemory::reserve_memory_special_huge_tlbfs(size, alignment, lp, req_addr, false);
      if (p != nullptr) {
        HugeTlbfsMemory mr(p, size);
        ASSERT_EQ(req_addr, p) << " size = " << size << ", alignment = " << alignment;
        small_page_write(p, size);
      }
    }
  }
}


TEST_VM(os_linux, reserve_memory_special_huge_tlbfs_size_not_aligned_with_bad_req_addr) {
  if (!using_explicit_hugepages()) {
    return;
  }
  size_t lp = os::large_page_size();
  size_t ag = os::vm_allocation_granularity();

  // sizes to test
  const size_t sizes[] = {
    lp, lp + ag, lp + lp / 2, lp * 2,
    lp * 2 + ag, lp * 2 - ag, lp * 2 + lp / 2,
    lp * 10, lp * 10 + lp / 2
  };
  const int num_sizes = sizeof(sizes) / sizeof(size_t);

  // Pre-allocate an area as large as the largest allocation
  // and aligned to the largest alignment we will be testing.
  const size_t mapping_size = sizes[num_sizes - 1] * 2;
  char* const mapping = (char*) ::mmap(nullptr, mapping_size,
      PROT_NONE, MAP_PRIVATE|MAP_ANONYMOUS|MAP_NORESERVE,
      -1, 0);
  ASSERT_TRUE(mapping != MAP_FAILED) << " mmap failed, mapping_size = " << mapping_size;
  // Leave the mapping intact, it will server as "bad" req_addr

  class MappingHolder {
    char* const _mapping;
    size_t _size;
   public:
    MappingHolder(char* mapping, size_t size) : _mapping(mapping), _size(size) { }
    ~MappingHolder() {
      ::munmap(_mapping, _size);
    }
  } holder(mapping, mapping_size);

  for (int i = 0; i < num_sizes; i++) {
    const size_t size = sizes[i];
    for (size_t alignment = ag; is_size_aligned(size, alignment); alignment *= 2) {
      // req_addr must be at least large page aligned.
      char* const req_addr = align_up(mapping, MAX2(alignment, lp));
      char* p = HugeTlbfsMemory::reserve_memory_special_huge_tlbfs(size, alignment, lp, req_addr, false);
      HugeTlbfsMemory mr(p, size);
      // as the area around req_addr contains already existing mappings, the API should always
      // return null (as per contract, it cannot return another address)
      EXPECT_TRUE(p == nullptr) << " size = " << size
                             << ", alignment = " << alignment
                             << ", req_addr = " << req_addr
                             << ", p = " << p;
    }
  }
}

class TestReserveMemorySpecial : AllStatic {
 public:
  static void small_page_write(void* addr, size_t size) {
    size_t page_size = os::vm_page_size();

    char* end = (char*)addr + size;
    for (char* p = (char*)addr; p < end; p += page_size) {
      *p = 1;
    }
  }

  static void test_reserve_memory_special_huge_tlbfs_size_aligned(size_t size, size_t alignment, size_t page_size) {
    if (!using_explicit_hugepages()) {
      return;
    }
    char* addr = os::reserve_memory_special(size, alignment, page_size, nullptr, false);
    if (addr != nullptr) {
      small_page_write(addr, size);
      os::release_memory_special(addr, size);
    }
  }

  static void test_reserve_memory_special_huge_tlbfs_size_aligned() {
    if (!using_explicit_hugepages()) {
      return;
    }
    size_t lp = os::large_page_size();
    for (size_t size = lp; size <= lp * 10; size += lp) {
      test_reserve_memory_special_huge_tlbfs_size_aligned(size, lp, lp);
    }
  }

  static void test_reserve_memory_special_huge_tlbfs_size_not_aligned() {
    size_t lp = os::large_page_size();
    size_t ag = os::vm_allocation_granularity();

    // sizes to test
    const size_t sizes[] = {
      lp, lp + ag, lp + lp / 2, lp * 2,
      lp * 2 + ag, lp * 2 - ag, lp * 2 + lp / 2,
      lp * 10, lp * 10 + lp / 2
    };
    const int num_sizes = sizeof(sizes) / sizeof(size_t);

    // For each size/alignment combination, we test three scenarios:
    // 1) with req_addr == nullptr
    // 2) with a non-null req_addr at which we expect to successfully allocate
    // 3) with a non-null req_addr which contains a pre-existing mapping, at which we
    //    expect the allocation to either fail or to ignore req_addr

    // Pre-allocate two areas; they shall be as large as the largest allocation
    //  and aligned to the largest alignment we will be testing.
    const size_t mapping_size = sizes[num_sizes - 1] * 2;
    char* const mapping1 = (char*) ::mmap(nullptr, mapping_size,
      PROT_NONE, MAP_PRIVATE|MAP_ANONYMOUS|MAP_NORESERVE,
      -1, 0);
    EXPECT_NE(mapping1, MAP_FAILED);

    char* const mapping2 = (char*) ::mmap(nullptr, mapping_size,
      PROT_NONE, MAP_PRIVATE|MAP_ANONYMOUS|MAP_NORESERVE,
      -1, 0);
    EXPECT_NE(mapping2, MAP_FAILED);

    // Unmap the first mapping, but leave the second mapping intact: the first
    // mapping will serve as a value for a "good" req_addr (case 2). The second
    // mapping, still intact, as "bad" req_addr (case 3).
    ::munmap(mapping1, mapping_size);

    // Case 1
    for (int i = 0; i < num_sizes; i++) {
      const size_t size = sizes[i];
      for (size_t alignment = ag; is_aligned(size, alignment); alignment *= 2) {
        char* p = os::reserve_memory_special(size, alignment, lp, nullptr, false);
        if (p != nullptr) {
          EXPECT_TRUE(is_aligned(p, alignment));
          small_page_write(p, size);
          os::release_memory_special(p, size);
        }
      }
    }

    // Case 2
    for (int i = 0; i < num_sizes; i++) {
      const size_t size = sizes[i];
      for (size_t alignment = ag; is_aligned(size, alignment); alignment *= 2) {
        // req_addr must be at least large page aligned.
        char* const req_addr = align_up(mapping1, MAX2(alignment, lp));
        char* p = os::reserve_memory_special(size, alignment, lp, req_addr, false);
        if (p != nullptr) {
          EXPECT_EQ(p, req_addr);
          small_page_write(p, size);
          os::release_memory_special(p, size);
        }
      }
    }

    // Case 3
    for (int i = 0; i < num_sizes; i++) {
      const size_t size = sizes[i];
      for (size_t alignment = ag; is_aligned(size, alignment); alignment *= 2) {
        // req_addr must be at least large page aligned.
        char* const req_addr = align_up(mapping2, MAX2(alignment, lp));
        char* p = os::reserve_memory_special(size, alignment, lp, req_addr, false);
        // as the area around req_addr contains already existing mappings, the API should always
        // return nullptr (as per contract, it cannot return another address)
        EXPECT_TRUE(p == nullptr);
      }
    }

    ::munmap(mapping2, mapping_size);

  }

  static void test() {
    if (!using_explicit_hugepages()) {
      return;
    }
    test_reserve_memory_special_huge_tlbfs_size_aligned();
    test_reserve_memory_special_huge_tlbfs_size_not_aligned();
  }
};

TEST_VM(os_linux, reserve_memory_special) {
  TestReserveMemorySpecial::test();
}

class ReserveMemorySpecialRunnable : public TestRunnable {
public:
  void runUnitTest() const {
    TestReserveMemorySpecial::test();
  }
};

TEST_VM(os_linux, reserve_memory_special_concurrent) {
  if (UseLargePages) {
    ReserveMemorySpecialRunnable runnable;
    ConcurrentTestRunner testRunner(&runnable, 5, 3000);
    testRunner.run();
  }
}

TEST_VM(os_linux, pretouch_thp_and_use_concurrent) {
  // Explicitly enable thp to test cocurrent system calls.
  const size_t size = 1 * G;
  const bool useThp = UseTransparentHugePages;
  UseTransparentHugePages = true;
  char* const heap = os::reserve_memory(size, mtTest);
  EXPECT_NE(heap, nullptr);
  EXPECT_TRUE(os::commit_memory(heap, size, false));

  {
    auto pretouch = [&](Thread*, int) {
      os::pretouch_memory(heap, heap + size, os::vm_page_size());
    };
    auto useMemory = [&](Thread*, int) {
      int* iptr = reinterpret_cast<int*>(heap);
      for (int i = 0; i < 1000; i++) *iptr++ = i;
    };
    TestThreadGroup<decltype(pretouch)> pretouchThreads{pretouch, 4};
    TestThreadGroup<decltype(useMemory)> useMemoryThreads{useMemory, 4};
    useMemoryThreads.doit();
    pretouchThreads.doit();
    useMemoryThreads.join();
    pretouchThreads.join();
  }

  int* iptr = reinterpret_cast<int*>(heap);
  for (int i = 0; i < 1000; i++)
    EXPECT_EQ(*iptr++, i);

  os::uncommit_memory(heap, size, false);
  os::release_memory(heap, size);
  UseTransparentHugePages = useThp;
}

// Check that method JNI_CreateJavaVM is found.
TEST(os_linux, addr_to_function_valid) {
  char buf[128] = "";
  int offset = -1;
  address valid_function_pointer = (address)JNI_CreateJavaVM;
  ASSERT_TRUE(os::dll_address_to_function_name(valid_function_pointer, buf, sizeof(buf), &offset, true));
  ASSERT_THAT(buf, testing::HasSubstr("JNI_CreateJavaVM"));
  ASSERT_TRUE(offset >= 0);
}

#if !defined(__clang_major__) || (__clang_major__ >= 5) // DWARF does not support Clang versions older than 5.0.
// Test valid address of method ReportJNIFatalError in jniCheck.hpp. We should get "jniCheck.hpp" in the buffer and a valid line number.
TEST_VM(os_linux, decoder_get_source_info_valid) {
  char buf[128] = "";
  int line = -1;
  address valid_function_pointer = (address)ReportJNIFatalError;
  ASSERT_TRUE(Decoder::get_source_info(valid_function_pointer, buf, sizeof(buf), &line));
  EXPECT_STREQ(buf, "jniCheck.hpp");
  ASSERT_TRUE(line > 0);
}

// Test invalid addresses. Should not cause harm and output buffer and line must contain "" and -1, respectively.
TEST_VM(os_linux, decoder_get_source_info_invalid) {
  char buf[128] = "";
  int line = -1;
  address invalid_function_pointers[] = { nullptr, (address)1, (address)&line };

  for (address addr : invalid_function_pointers) {
    strcpy(buf, "somestring");
    line = 12;
    // We should return false but do not crash or fail in any way.
    ASSERT_FALSE(Decoder::get_source_info(addr, buf, sizeof(buf), &line));
    ASSERT_TRUE(buf[0] == '\0'); // Should contain "" on error
    ASSERT_TRUE(line == -1); // Should contain -1 on error
  }
}

// Test with valid address but a too small buffer to store the entire filename. Should find generic <OVERFLOW> message
// and a valid line number.
TEST_VM(os_linux, decoder_get_source_info_valid_overflow) {
  char buf[11] = "";
  int line = -1;
  address valid_function_pointer = (address)ReportJNIFatalError;
  ASSERT_TRUE(Decoder::get_source_info(valid_function_pointer, buf, 11, &line));
  EXPECT_STREQ(buf, "<OVERFLOW>");
  ASSERT_TRUE(line > 0);
}

// Test with valid address but a too small buffer that can neither store the entire filename nor the generic <OVERFLOW>
// message. We should find "L" as filename and a valid line number.
TEST_VM(os_linux, decoder_get_source_info_valid_overflow_minimal) {
  char buf[2] = "";
  int line = -1;
  address valid_function_pointer = (address)ReportJNIFatalError;
  ASSERT_TRUE(Decoder::get_source_info(valid_function_pointer, buf, 2, &line));
  EXPECT_STREQ(buf, "L"); // Overflow message does not fit, so we fall back to "L:line_number"
  ASSERT_TRUE(line > 0); // Line should correctly be found and returned
}
#endif // clang

#ifdef __GLIBC__
#ifndef ADDRESS_SANITIZER
TEST_VM(os_linux, glibc_mallinfo_wrapper) {
  // Very basic test. Call it. That proves that resolution and invocation works.
  os::Linux::glibc_mallinfo mi;
  bool did_wrap = false;

  void* p = os::malloc(2 * K, mtTest);
  ASSERT_NOT_NULL(p);

  os::Linux::get_mallinfo(&mi, &did_wrap);

  // We should see total allocation values > 0
  ASSERT_GE((mi.uordblks + mi.hblkhd), 2 * K);

  // These values also should less than some reasonable size.
  ASSERT_LT(mi.fordblks, 2 * G);
  ASSERT_LT(mi.uordblks, 2 * G);
  ASSERT_LT(mi.hblkhd, 2 * G);

  os::free(p);
}
#endif // ADDRESS_SANITIZER
#endif // __GLIBC__

static void test_set_thread_name(const char* name, const char* expected) {
  os::set_native_thread_name(name);
  char buf[16];
  int rc = prctl(PR_GET_NAME, buf);
  ASSERT_EQ(0, rc);
  ASSERT_STREQ(buf, expected);
}

TEST_VM(os_linux, set_thread_name) {
  char buf[16];
  // retrieve current name
  int rc = prctl(PR_GET_NAME, buf);
  ASSERT_EQ(0, rc);

  test_set_thread_name("shortname", "shortname");
  test_set_thread_name("012345678901234",  "012345678901234");
  test_set_thread_name("0123456789012345", "0123456..012345");
  test_set_thread_name("MyAllocationWorkerThread22", "MyAlloc..read22");

  // restore current name
  test_set_thread_name(buf, buf);
}

#endif // LINUX
