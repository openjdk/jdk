/*
 * Copyright (c) 2015, 2020, Oracle and/or its affiliates. All rights reserved.
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

#include "precompiled.hpp"
#include "gc/z/zArray.inline.hpp"
#include "gc/z/zBackingPath_linux.hpp"
#include "gc/z/zErrno.hpp"
#include "gc/z/zGlobals.hpp"
#include "gc/z/zLargePages.inline.hpp"
#include "gc/z/zPhysicalMemoryBacking_linux.hpp"
#include "gc/z/zSyscall_linux.hpp"
#include "logging/log.hpp"
#include "runtime/init.hpp"
#include "runtime/os.hpp"
#include "utilities/align.hpp"
#include "utilities/debug.hpp"

#include <fcntl.h>
#include <stdio.h>
#include <sys/mman.h>
#include <sys/stat.h>
#include <sys/statfs.h>
#include <sys/types.h>
#include <unistd.h>

//
// Support for building on older Linux systems
//

// memfd_create(2) flags
#ifndef MFD_CLOEXEC
#define MFD_CLOEXEC                      0x0001U
#endif
#ifndef MFD_HUGETLB
#define MFD_HUGETLB                      0x0004U
#endif

// open(2) flags
#ifndef O_CLOEXEC
#define O_CLOEXEC                        02000000
#endif
#ifndef O_TMPFILE
#define O_TMPFILE                        (020000000 | O_DIRECTORY)
#endif

// fallocate(2) flags
#ifndef FALLOC_FL_KEEP_SIZE
#define FALLOC_FL_KEEP_SIZE              0x01
#endif
#ifndef FALLOC_FL_PUNCH_HOLE
#define FALLOC_FL_PUNCH_HOLE             0x02
#endif

// Filesystem types, see statfs(2)
#ifndef TMPFS_MAGIC
#define TMPFS_MAGIC                      0x01021994
#endif
#ifndef HUGETLBFS_MAGIC
#define HUGETLBFS_MAGIC                  0x958458f6
#endif

// Filesystem names
#define ZFILESYSTEM_TMPFS                "tmpfs"
#define ZFILESYSTEM_HUGETLBFS            "hugetlbfs"

// Proc file entry for max map mount
#define ZFILENAME_PROC_MAX_MAP_COUNT     "/proc/sys/vm/max_map_count"

// Sysfs file for transparent huge page on tmpfs
#define ZFILENAME_SHMEM_ENABLED          "/sys/kernel/mm/transparent_hugepage/shmem_enabled"

// Java heap filename
#define ZFILENAME_HEAP                   "java_heap"

// Preferred tmpfs mount points, ordered by priority
static const char* z_preferred_tmpfs_mountpoints[] = {
  "/dev/shm",
  "/run/shm",
  NULL
};

// Preferred hugetlbfs mount points, ordered by priority
static const char* z_preferred_hugetlbfs_mountpoints[] = {
  "/dev/hugepages",
  "/hugepages",
  NULL
};

static int z_fallocate_hugetlbfs_attempts = 3;
static bool z_fallocate_supported = true;

ZPhysicalMemoryBacking::ZPhysicalMemoryBacking() :
    _fd(-1),
    _size(0),
    _filesystem(0),
    _block_size(0),
    _available(0),
    _initialized(false) {

  // Create backing file
  _fd = create_fd(ZFILENAME_HEAP);
  if (_fd == -1) {
    return;
  }

  // Get filesystem statistics
  struct statfs buf;
  if (fstatfs(_fd, &buf) == -1) {
    ZErrno err;
    log_error(gc)("Failed to determine filesystem type for backing file (%s)", err.to_string());
    return;
  }

  _filesystem = buf.f_type;
  _block_size = buf.f_bsize;
  _available = buf.f_bavail * _block_size;

  // Make sure we're on a supported filesystem
  if (!is_tmpfs() && !is_hugetlbfs()) {
    log_error(gc)("Backing file must be located on a %s or a %s filesystem",
                  ZFILESYSTEM_TMPFS, ZFILESYSTEM_HUGETLBFS);
    return;
  }

  // Make sure the filesystem type matches requested large page type
  if (ZLargePages::is_transparent() && !is_tmpfs()) {
    log_error(gc)("-XX:+UseTransparentHugePages can only be enable when using a %s filesystem",
                  ZFILESYSTEM_TMPFS);
    return;
  }

  if (ZLargePages::is_transparent() && !tmpfs_supports_transparent_huge_pages()) {
    log_error(gc)("-XX:+UseTransparentHugePages on a %s filesystem not supported by kernel",
                  ZFILESYSTEM_TMPFS);
    return;
  }

  if (ZLargePages::is_explicit() && !is_hugetlbfs()) {
    log_error(gc)("-XX:+UseLargePages (without -XX:+UseTransparentHugePages) can only be enabled "
                  "when using a %s filesystem", ZFILESYSTEM_HUGETLBFS);
    return;
  }

  if (!ZLargePages::is_explicit() && is_hugetlbfs()) {
    log_error(gc)("-XX:+UseLargePages must be enabled when using a %s filesystem",
                  ZFILESYSTEM_HUGETLBFS);
    return;
  }

  const size_t expected_block_size = is_tmpfs() ? os::vm_page_size() : os::large_page_size();
  if (expected_block_size != _block_size) {
    log_error(gc)("%s filesystem has unexpected block size " SIZE_FORMAT " (expected " SIZE_FORMAT ")",
                  is_tmpfs() ? ZFILESYSTEM_TMPFS : ZFILESYSTEM_HUGETLBFS, _block_size, expected_block_size);
    return;
  }

  // Successfully initialized
  _initialized = true;
}

int ZPhysicalMemoryBacking::create_mem_fd(const char* name) const {
  // Create file name
  char filename[PATH_MAX];
  snprintf(filename, sizeof(filename), "%s%s", name, ZLargePages::is_explicit() ? ".hugetlb" : "");

  // Create file
  const int extra_flags = ZLargePages::is_explicit() ? MFD_HUGETLB : 0;
  const int fd = ZSyscall::memfd_create(filename, MFD_CLOEXEC | extra_flags);
  if (fd == -1) {
    ZErrno err;
    log_debug(gc, init)("Failed to create memfd file (%s)",
                        ((ZLargePages::is_explicit() && err == EINVAL) ? "Hugepages not supported" : err.to_string()));
    return -1;
  }

  log_info(gc, init)("Heap backed by file: /memfd:%s", filename);

  return fd;
}

int ZPhysicalMemoryBacking::create_file_fd(const char* name) const {
  const char* const filesystem = ZLargePages::is_explicit()
                                 ? ZFILESYSTEM_HUGETLBFS
                                 : ZFILESYSTEM_TMPFS;
  const char** const preferred_mountpoints = ZLargePages::is_explicit()
                                             ? z_preferred_hugetlbfs_mountpoints
                                             : z_preferred_tmpfs_mountpoints;

  // Find mountpoint
  ZBackingPath path(filesystem, preferred_mountpoints);
  if (path.get() == NULL) {
    log_error(gc)("Use -XX:ZPath to specify the path to a %s filesystem", filesystem);
    return -1;
  }

  // Try to create an anonymous file using the O_TMPFILE flag. Note that this
  // flag requires kernel >= 3.11. If this fails we fall back to open/unlink.
  const int fd_anon = os::open(path.get(), O_TMPFILE|O_EXCL|O_RDWR|O_CLOEXEC, S_IRUSR|S_IWUSR);
  if (fd_anon == -1) {
    ZErrno err;
    log_debug(gc, init)("Failed to create anonymous file in %s (%s)", path.get(),
                        (err == EINVAL ? "Not supported" : err.to_string()));
  } else {
    // Get inode number for anonymous file
    struct stat stat_buf;
    if (fstat(fd_anon, &stat_buf) == -1) {
      ZErrno err;
      log_error(gc)("Failed to determine inode number for anonymous file (%s)", err.to_string());
      return -1;
    }

    log_info(gc, init)("Heap backed by file: %s/#" UINT64_FORMAT, path.get(), (uint64_t)stat_buf.st_ino);

    return fd_anon;
  }

  log_debug(gc, init)("Falling back to open/unlink");

  // Create file name
  char filename[PATH_MAX];
  snprintf(filename, sizeof(filename), "%s/%s.%d", path.get(), name, os::current_process_id());

  // Create file
  const int fd = os::open(filename, O_CREAT|O_EXCL|O_RDWR|O_CLOEXEC, S_IRUSR|S_IWUSR);
  if (fd == -1) {
    ZErrno err;
    log_error(gc)("Failed to create file %s (%s)", filename, err.to_string());
    return -1;
  }

  // Unlink file
  if (unlink(filename) == -1) {
    ZErrno err;
    log_error(gc)("Failed to unlink file %s (%s)", filename, err.to_string());
    return -1;
  }

  log_info(gc, init)("Heap backed by file: %s", filename);

  return fd;
}

int ZPhysicalMemoryBacking::create_fd(const char* name) const {
  if (ZPath == NULL) {
    // If the path is not explicitly specified, then we first try to create a memfd file
    // instead of looking for a tmpfd/hugetlbfs mount point. Note that memfd_create() might
    // not be supported at all (requires kernel >= 3.17), or it might not support large
    // pages (requires kernel >= 4.14). If memfd_create() fails, then we try to create a
    // file on an accessible tmpfs or hugetlbfs mount point.
    const int fd = create_mem_fd(name);
    if (fd != -1) {
      return fd;
    }

    log_debug(gc, init)("Falling back to searching for an accessible mount point");
  }

  return create_file_fd(name);
}

bool ZPhysicalMemoryBacking::is_initialized() const {
  return _initialized;
}

void ZPhysicalMemoryBacking::warn_available_space(size_t max) const {
  // Note that the available space on a tmpfs or a hugetlbfs filesystem
  // will be zero if no size limit was specified when it was mounted.
  if (_available == 0) {
    // No size limit set, skip check
    log_info(gc, init)("Available space on backing filesystem: N/A");
    return;
  }

  log_info(gc, init)("Available space on backing filesystem: " SIZE_FORMAT "M", _available / M);

  // Warn if the filesystem doesn't currently have enough space available to hold
  // the max heap size. The max heap size will be capped if we later hit this limit
  // when trying to expand the heap.
  if (_available < max) {
    log_warning(gc)("***** WARNING! INCORRECT SYSTEM CONFIGURATION DETECTED! *****");
    log_warning(gc)("Not enough space available on the backing filesystem to hold the current max Java heap");
    log_warning(gc)("size (" SIZE_FORMAT "M). Please adjust the size of the backing filesystem accordingly "
                    "(available", max / M);
    log_warning(gc)("space is currently " SIZE_FORMAT "M). Continuing execution with the current filesystem "
                    "size could", _available / M);
    log_warning(gc)("lead to a premature OutOfMemoryError being thrown, due to failure to map memory.");
  }
}

void ZPhysicalMemoryBacking::warn_max_map_count(size_t max) const {
  const char* const filename = ZFILENAME_PROC_MAX_MAP_COUNT;
  FILE* const file = fopen(filename, "r");
  if (file == NULL) {
    // Failed to open file, skip check
    log_debug(gc, init)("Failed to open %s", filename);
    return;
  }

  size_t actual_max_map_count = 0;
  const int result = fscanf(file, SIZE_FORMAT, &actual_max_map_count);
  fclose(file);
  if (result != 1) {
    // Failed to read file, skip check
    log_debug(gc, init)("Failed to read %s", filename);
    return;
  }

  // The required max map count is impossible to calculate exactly since subsystems
  // other than ZGC are also creating memory mappings, and we have no control over that.
  // However, ZGC tends to create the most mappings and dominate the total count.
  // In the worst cases, ZGC will map each granule three times, i.e. once per heap view.
  // We speculate that we need another 20% to allow for non-ZGC subsystems to map memory.
  const size_t required_max_map_count = (max / ZGranuleSize) * 3 * 1.2;
  if (actual_max_map_count < required_max_map_count) {
    log_warning(gc)("***** WARNING! INCORRECT SYSTEM CONFIGURATION DETECTED! *****");
    log_warning(gc)("The system limit on number of memory mappings per process might be too low for the given");
    log_warning(gc)("max Java heap size (" SIZE_FORMAT "M). Please adjust %s to allow for at",
                    max / M, filename);
    log_warning(gc)("least " SIZE_FORMAT " mappings (current limit is " SIZE_FORMAT "). Continuing execution "
                    "with the current", required_max_map_count, actual_max_map_count);
    log_warning(gc)("limit could lead to a fatal error, due to failure to map memory.");
  }
}

void ZPhysicalMemoryBacking::warn_commit_limits(size_t max) const {
  // Warn if available space is too low
  warn_available_space(max);

  // Warn if max map count is too low
  warn_max_map_count(max);
}

size_t ZPhysicalMemoryBacking::size() const {
  return _size;
}

bool ZPhysicalMemoryBacking::is_tmpfs() const {
  return _filesystem == TMPFS_MAGIC;
}

bool ZPhysicalMemoryBacking::is_hugetlbfs() const {
  return _filesystem == HUGETLBFS_MAGIC;
}

bool ZPhysicalMemoryBacking::tmpfs_supports_transparent_huge_pages() const {
  // If the shmem_enabled file exists and is readable then we
  // know the kernel supports transparent huge pages for tmpfs.
  return access(ZFILENAME_SHMEM_ENABLED, R_OK) == 0;
}

ZErrno ZPhysicalMemoryBacking::fallocate_compat_ftruncate(size_t size) const {
  while (ftruncate(_fd, size) == -1) {
    if (errno != EINTR) {
      // Failed
      return errno;
    }
  }

  // Success
  return 0;
}

ZErrno ZPhysicalMemoryBacking::fallocate_compat_mmap(size_t offset, size_t length, bool touch) const {
  // On hugetlbfs, mapping a file segment will fail immediately, without
  // the need to touch the mapped pages first, if there aren't enough huge
  // pages available to back the mapping.
  void* const addr = mmap(0, length, PROT_READ|PROT_WRITE, MAP_SHARED, _fd, offset);
  if (addr == MAP_FAILED) {
    // Failed
    return errno;
  }

  // Once mapped, the huge pages are only reserved. We need to touch them
  // to associate them with the file segment. Note that we can not punch
  // hole in file segments which only have reserved pages.
  if (touch) {
    char* const start = (char*)addr;
    char* const end = start + length;
    os::pretouch_memory(start, end, _block_size);
  }

  // Unmap again. From now on, the huge pages that were mapped are allocated
  // to this file. There's no risk in getting SIGBUS when touching them.
  if (munmap(addr, length) == -1) {
    // Failed
    return errno;
  }

  // Success
  return 0;
}

ZErrno ZPhysicalMemoryBacking::fallocate_compat_pwrite(size_t offset, size_t length) const {
  uint8_t data = 0;

  // Allocate backing memory by writing to each block
  for (size_t pos = offset; pos < offset + length; pos += _block_size) {
    if (pwrite(_fd, &data, sizeof(data), pos) == -1) {
      // Failed
      return errno;
    }
  }

  // Success
  return 0;
}

ZErrno ZPhysicalMemoryBacking::fallocate_fill_hole_compat(size_t offset, size_t length) {
  // fallocate(2) is only supported by tmpfs since Linux 3.5, and by hugetlbfs
  // since Linux 4.3. When fallocate(2) is not supported we emulate it using
  // ftruncate/pwrite (for tmpfs) or ftruncate/mmap/munmap (for hugetlbfs).

  const size_t end = offset + length;
  if (end > _size) {
    // Increase file size
    const ZErrno err = fallocate_compat_ftruncate(end);
    if (err) {
      // Failed
      return err;
    }
  }

  // Allocate backing memory
  const ZErrno err = is_hugetlbfs() ? fallocate_compat_mmap(offset, length, false /* touch */)
                                    : fallocate_compat_pwrite(offset, length);
  if (err) {
    if (end > _size) {
      // Restore file size
      fallocate_compat_ftruncate(_size);
    }

    // Failed
    return err;
  }

  if (end > _size) {
    // Record new file size
    _size = end;
  }

  // Success
  return 0;
}

ZErrno ZPhysicalMemoryBacking::fallocate_fill_hole_syscall(size_t offset, size_t length) {
  const int mode = 0; // Allocate
  const int res = ZSyscall::fallocate(_fd, mode, offset, length);
  if (res == -1) {
    // Failed
    return errno;
  }

  const size_t end = offset + length;
  if (end > _size) {
    // Record new file size
    _size = end;
  }

  // Success
  return 0;
}

ZErrno ZPhysicalMemoryBacking::fallocate_fill_hole(size_t offset, size_t length) {
  // Using compat mode is more efficient when allocating space on hugetlbfs.
  // Note that allocating huge pages this way will only reserve them, and not
  // associate them with segments of the file. We must guarantee that we at
  // some point touch these segments, otherwise we can not punch hole in them.
  if (z_fallocate_supported && !is_hugetlbfs()) {
     const ZErrno err = fallocate_fill_hole_syscall(offset, length);
     if (!err) {
       // Success
       return 0;
     }

     if (err != ENOSYS && err != EOPNOTSUPP) {
       // Failed
       return err;
     }

     // Not supported
     log_debug(gc)("Falling back to fallocate() compatibility mode");
     z_fallocate_supported = false;
  }

  return fallocate_fill_hole_compat(offset, length);
}

ZErrno ZPhysicalMemoryBacking::fallocate_punch_hole(size_t offset, size_t length) {
  if (is_hugetlbfs()) {
    // We can only punch hole in pages that have been touched. Non-touched
    // pages are only reserved, and not associated with any specific file
    // segment. We don't know which pages have been previously touched, so
    // we always touch them here to guarantee that we can punch hole.
    const ZErrno err = fallocate_compat_mmap(offset, length, true /* touch */);
    if (err) {
      // Failed
      return err;
    }
  }

  const int mode = FALLOC_FL_PUNCH_HOLE|FALLOC_FL_KEEP_SIZE;
  if (ZSyscall::fallocate(_fd, mode, offset, length) == -1) {
    // Failed
    return errno;
  }

  // Success
  return 0;
}

ZErrno ZPhysicalMemoryBacking::split_and_fallocate(bool punch_hole, size_t offset, size_t length) {
  // Try first half
  const size_t offset0 = offset;
  const size_t length0 = align_up(length / 2, _block_size);
  const ZErrno err0 = fallocate(punch_hole, offset0, length0);
  if (err0) {
    return err0;
  }

  // Try second half
  const size_t offset1 = offset0 + length0;
  const size_t length1 = length - length0;
  const ZErrno err1 = fallocate(punch_hole, offset1, length1);
  if (err1) {
    return err1;
  }

  // Success
  return 0;
}

ZErrno ZPhysicalMemoryBacking::fallocate(bool punch_hole, size_t offset, size_t length) {
  assert(is_aligned(offset, _block_size), "Invalid offset");
  assert(is_aligned(length, _block_size), "Invalid length");

  const ZErrno err = punch_hole ? fallocate_punch_hole(offset, length) : fallocate_fill_hole(offset, length);
  if (err == EINTR && length > _block_size) {
    // Calling fallocate(2) with a large length can take a long time to
    // complete. When running profilers, such as VTune, this syscall will
    // be constantly interrupted by signals. Expanding the file in smaller
    // steps avoids this problem.
    return split_and_fallocate(punch_hole, offset, length);
  }

  return err;
}

bool ZPhysicalMemoryBacking::commit_inner(size_t offset, size_t length) {
  log_trace(gc, heap)("Committing memory: " SIZE_FORMAT "M-" SIZE_FORMAT "M (" SIZE_FORMAT "M)",
                      offset / M, (offset + length) / M, length / M);

retry:
  const ZErrno err = fallocate(false /* punch_hole */, offset, length);
  if (err) {
    if (err == ENOSPC && !is_init_completed() && is_hugetlbfs() && z_fallocate_hugetlbfs_attempts-- > 0) {
      // If we fail to allocate during initialization, due to lack of space on
      // the hugetlbfs filesystem, then we wait and retry a few times before
      // giving up. Otherwise there is a risk that running JVMs back-to-back
      // will fail, since there is a delay between process termination and the
      // huge pages owned by that process being returned to the huge page pool
      // and made available for new allocations.
      log_debug(gc, init)("Failed to commit memory (%s), retrying", err.to_string());

      // Wait and retry in one second, in the hope that huge pages will be
      // available by then.
      sleep(1);
      goto retry;
    }

    // Failed
    log_error(gc)("Failed to commit memory (%s)", err.to_string());
    return false;
  }

  // Success
  return true;
}

size_t ZPhysicalMemoryBacking::commit(size_t offset, size_t length) {
  // Try to commit the whole region
  if (commit_inner(offset, length)) {
    // Success
    return length;
  }

  // Failed, try to commit as much as possible
  size_t start = offset;
  size_t end = offset + length;

  for (;;) {
    length = align_down((end - start) / 2, ZGranuleSize);
    if (length < ZGranuleSize) {
      // Done, don't commit more
      return start - offset;
    }

    if (commit_inner(start, length)) {
      // Success, try commit more
      start += length;
    } else {
      // Failed, try commit less
      end -= length;
    }
  }
}

size_t ZPhysicalMemoryBacking::uncommit(size_t offset, size_t length) {
  log_trace(gc, heap)("Uncommitting memory: " SIZE_FORMAT "M-" SIZE_FORMAT "M (" SIZE_FORMAT "M)",
                      offset / M, (offset + length) / M, length / M);

  const ZErrno err = fallocate(true /* punch_hole */, offset, length);
  if (err) {
    log_error(gc)("Failed to uncommit memory (%s)", err.to_string());
    return 0;
  }

  return length;
}

void ZPhysicalMemoryBacking::map(uintptr_t addr, size_t size, uintptr_t offset) const {
  const void* const res = mmap((void*)addr, size, PROT_READ|PROT_WRITE, MAP_FIXED|MAP_SHARED, _fd, offset);
  if (res == MAP_FAILED) {
    ZErrno err;
    fatal("Failed to map memory (%s)", err.to_string());
  }
}

void ZPhysicalMemoryBacking::unmap(uintptr_t addr, size_t size) const {
  // Note that we must keep the address space reservation intact and just detach
  // the backing memory. For this reason we map a new anonymous, non-accessible
  // and non-reserved page over the mapping instead of actually unmapping.
  const void* const res = mmap((void*)addr, size, PROT_NONE, MAP_FIXED | MAP_ANONYMOUS | MAP_PRIVATE | MAP_NORESERVE, -1, 0);
  if (res == MAP_FAILED) {
    ZErrno err;
    fatal("Failed to map memory (%s)", err.to_string());
  }
}
