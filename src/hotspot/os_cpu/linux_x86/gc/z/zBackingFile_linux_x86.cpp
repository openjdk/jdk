/*
 * Copyright (c) 2015, 2018, Oracle and/or its affiliates. All rights reserved.
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
#include "gc/z/zBackingFile_linux_x86.hpp"
#include "gc/z/zBackingPath_linux_x86.hpp"
#include "gc/z/zErrno.hpp"
#include "gc/z/zLargePages.inline.hpp"
#include "logging/log.hpp"
#include "runtime/os.hpp"
#include "utilities/align.hpp"
#include "utilities/debug.hpp"

#include <fcntl.h>
#include <sys/mman.h>
#include <sys/stat.h>
#include <sys/statfs.h>
#include <sys/types.h>
#include <unistd.h>

// Filesystem names
#define ZFILESYSTEM_TMPFS                "tmpfs"
#define ZFILESYSTEM_HUGETLBFS            "hugetlbfs"

// Sysfs file for transparent huge page on tmpfs
#define ZFILENAME_SHMEM_ENABLED          "/sys/kernel/mm/transparent_hugepage/shmem_enabled"

// Java heap filename
#define ZFILENAME_HEAP                   "java_heap"

// Support for building on older Linux systems
#ifndef __NR_memfd_create
#define __NR_memfd_create                319
#endif
#ifndef MFD_CLOEXEC
#define MFD_CLOEXEC                      0x0001U
#endif
#ifndef MFD_HUGETLB
#define MFD_HUGETLB                      0x0004U
#endif
#ifndef O_CLOEXEC
#define O_CLOEXEC                        02000000
#endif
#ifndef O_TMPFILE
#define O_TMPFILE                        (020000000 | O_DIRECTORY)
#endif

// Filesystem types, see statfs(2)
#ifndef TMPFS_MAGIC
#define TMPFS_MAGIC                      0x01021994
#endif
#ifndef HUGETLBFS_MAGIC
#define HUGETLBFS_MAGIC                  0x958458f6
#endif

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

static int z_memfd_create(const char *name, unsigned int flags) {
  return syscall(__NR_memfd_create, name, flags);
}

bool ZBackingFile::_hugetlbfs_mmap_retry = true;

ZBackingFile::ZBackingFile() :
    _fd(-1),
    _filesystem(0),
    _available(0),
    _initialized(false) {

  // Create backing file
  _fd = create_fd(ZFILENAME_HEAP);
  if (_fd == -1) {
    return;
  }

  // Get filesystem statistics
  struct statfs statfs_buf;
  if (fstatfs(_fd, &statfs_buf) == -1) {
    ZErrno err;
    log_error(gc, init)("Failed to determine filesystem type for backing file (%s)",
                        err.to_string());
    return;
  }

  _filesystem = statfs_buf.f_type;
  _available = statfs_buf.f_bavail * statfs_buf.f_bsize;

  // Make sure we're on a supported filesystem
  if (!is_tmpfs() && !is_hugetlbfs()) {
    log_error(gc, init)("Backing file must be located on a %s or a %s filesystem",
                        ZFILESYSTEM_TMPFS, ZFILESYSTEM_HUGETLBFS);
    return;
  }

  // Make sure the filesystem type matches requested large page type
  if (ZLargePages::is_transparent() && !is_tmpfs()) {
    log_error(gc, init)("-XX:+UseTransparentHugePages can only be enable when using a %s filesystem",
                        ZFILESYSTEM_TMPFS);
    return;
  }

  if (ZLargePages::is_transparent() && !tmpfs_supports_transparent_huge_pages()) {
    log_error(gc, init)("-XX:+UseTransparentHugePages on a %s filesystem not supported by kernel",
                        ZFILESYSTEM_TMPFS);
    return;
  }

  if (ZLargePages::is_explicit() && !is_hugetlbfs()) {
    log_error(gc, init)("-XX:+UseLargePages (without -XX:+UseTransparentHugePages) can only be enabled when using a %s filesystem",
                        ZFILESYSTEM_HUGETLBFS);
    return;
  }

  if (!ZLargePages::is_explicit() && is_hugetlbfs()) {
    log_error(gc, init)("-XX:+UseLargePages must be enabled when using a %s filesystem",
                        ZFILESYSTEM_HUGETLBFS);
    return;
  }

  // Successfully initialized
  _initialized = true;
}

int ZBackingFile::create_mem_fd(const char* name) const {
  // Create file name
  char filename[PATH_MAX];
  snprintf(filename, sizeof(filename), "%s%s", name, ZLargePages::is_explicit() ? ".hugetlb" : "");

  // Create file
  const int extra_flags = ZLargePages::is_explicit() ? MFD_HUGETLB : 0;
  const int fd = z_memfd_create(filename, MFD_CLOEXEC | extra_flags);
  if (fd == -1) {
    ZErrno err;
    log_debug(gc, init)("Failed to create memfd file (%s)",
                        ((UseLargePages && err == EINVAL) ? "Hugepages not supported" : err.to_string()));
    return -1;
  }

  log_info(gc, init)("Heap backed by file: /memfd:%s", filename);

  return fd;
}

int ZBackingFile::create_file_fd(const char* name) const {
  const char* const filesystem = ZLargePages::is_explicit()
                                 ? ZFILESYSTEM_HUGETLBFS
                                 : ZFILESYSTEM_TMPFS;
  const char** const preferred_mountpoints = ZLargePages::is_explicit()
                                             ? z_preferred_hugetlbfs_mountpoints
                                             : z_preferred_tmpfs_mountpoints;

  // Find mountpoint
  ZBackingPath path(filesystem, preferred_mountpoints);
  if (path.get() == NULL) {
    log_error(gc, init)("Use -XX:ZPath to specify the path to a %s filesystem", filesystem);
    return -1;
  }

  // Try to create an anonymous file using the O_TMPFILE flag. Note that this
  // flag requires kernel >= 3.11. If this fails we fall back to open/unlink.
  const int fd_anon = open(path.get(), O_TMPFILE|O_EXCL|O_RDWR|O_CLOEXEC, S_IRUSR|S_IWUSR);
  if (fd_anon == -1) {
    ZErrno err;
    log_debug(gc, init)("Failed to create anonymous file in %s (%s)", path.get(),
                        (err == EINVAL ? "Not supported" : err.to_string()));
  } else {
    // Get inode number for anonymous file
    struct stat stat_buf;
    if (fstat(fd_anon, &stat_buf) == -1) {
      ZErrno err;
      log_error(gc, init)("Failed to determine inode number for anonymous file (%s)", err.to_string());
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
  const int fd = open(filename, O_CREAT|O_EXCL|O_RDWR|O_CLOEXEC, S_IRUSR|S_IWUSR);
  if (fd == -1) {
    ZErrno err;
    log_error(gc, init)("Failed to create file %s (%s)", filename, err.to_string());
    return -1;
  }

  // Unlink file
  if (unlink(filename) == -1) {
    ZErrno err;
    log_error(gc, init)("Failed to unlink file %s (%s)", filename, err.to_string());
    return -1;
  }

  log_info(gc, init)("Heap backed by file: %s", filename);

  return fd;
}

int ZBackingFile::create_fd(const char* name) const {
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

bool ZBackingFile::is_initialized() const {
  return _initialized;
}

int ZBackingFile::fd() const {
  return _fd;
}

size_t ZBackingFile::available() const {
  return _available;
}

bool ZBackingFile::is_tmpfs() const {
  return _filesystem == TMPFS_MAGIC;
}

bool ZBackingFile::is_hugetlbfs() const {
  return _filesystem == HUGETLBFS_MAGIC;
}

bool ZBackingFile::tmpfs_supports_transparent_huge_pages() const {
  // If the shmem_enabled file exists and is readable then we
  // know the kernel supports transparent huge pages for tmpfs.
  return access(ZFILENAME_SHMEM_ENABLED, R_OK) == 0;
}

bool ZBackingFile::try_split_and_expand_tmpfs(size_t offset, size_t length, size_t alignment) const {
  // Try first smaller part.
  const size_t offset0 = offset;
  const size_t length0 = align_up(length / 2, alignment);
  if (!try_expand_tmpfs(offset0, length0, alignment)) {
    return false;
  }

  // Try second smaller part.
  const size_t offset1 = offset0 + length0;
  const size_t length1 = length - length0;
  if (!try_expand_tmpfs(offset1, length1, alignment)) {
    return false;
  }

  return true;
}

bool ZBackingFile::try_expand_tmpfs(size_t offset, size_t length, size_t alignment) const {
  assert(length > 0, "Invalid length");
  assert(is_aligned(length, alignment), "Invalid length");

  ZErrno err = posix_fallocate(_fd, offset, length);

  if (err == EINTR && length > alignment) {
    // Calling posix_fallocate() with a large length can take a long
    // time to complete. When running profilers, such as VTune, this
    // syscall will be constantly interrupted by signals. Expanding
    // the file in smaller steps avoids this problem.
    return try_split_and_expand_tmpfs(offset, length, alignment);
  }

  if (err) {
    log_error(gc)("Failed to allocate backing file (%s)", err.to_string());
    return false;
  }

  return true;
}

bool ZBackingFile::try_expand_tmpfs(size_t offset, size_t length) const {
  assert(is_tmpfs(), "Wrong filesystem");
  return try_expand_tmpfs(offset, length, os::vm_page_size());
}

bool ZBackingFile::try_expand_hugetlbfs(size_t offset, size_t length) const {
  assert(is_hugetlbfs(), "Wrong filesystem");

  // Prior to kernel 4.3, hugetlbfs did not support posix_fallocate().
  // Instead of posix_fallocate() we can use a well-known workaround,
  // which involves truncating the file to requested size and then try
  // to map it to verify that there are enough huge pages available to
  // back it.
  while (ftruncate(_fd, offset + length) == -1) {
    ZErrno err;
    if (err != EINTR) {
      log_error(gc)("Failed to truncate backing file (%s)", err.to_string());
      return false;
    }
  }

  // If we fail mapping during initialization, i.e. when we are pre-mapping
  // the heap, then we wait and retry a few times before giving up. Otherwise
  // there is a risk that running JVMs back-to-back will fail, since there
  // is a delay between process termination and the huge pages owned by that
  // process being returned to the huge page pool and made available for new
  // allocations.
  void* addr = MAP_FAILED;
  const int max_attempts = 5;
  for (int attempt = 1; attempt <= max_attempts; attempt++) {
    addr = mmap(0, length, PROT_READ|PROT_WRITE, MAP_SHARED, _fd, offset);
    if (addr != MAP_FAILED || !_hugetlbfs_mmap_retry) {
      // Mapping was successful or mmap retry is disabled
      break;
    }

    ZErrno err;
    log_debug(gc)("Failed to map backing file (%s), attempt %d of %d",
                  err.to_string(), attempt, max_attempts);

    // Wait and retry in one second, in the hope that
    // huge pages will be available by then.
    sleep(1);
  }

  // Disable mmap retry from now on
  if (_hugetlbfs_mmap_retry) {
    _hugetlbfs_mmap_retry = false;
  }

  if (addr == MAP_FAILED) {
    // Not enough huge pages left
    ZErrno err;
    log_error(gc)("Failed to map backing file (%s)", err.to_string());
    return false;
  }

  // Successful mapping, unmap again. From now on the pages we mapped
  // will be reserved for this file.
  if (munmap(addr, length) == -1) {
    ZErrno err;
    log_error(gc)("Failed to unmap backing file (%s)", err.to_string());
    return false;
  }

  return true;
}

bool ZBackingFile::try_expand_tmpfs_or_hugetlbfs(size_t offset, size_t length, size_t alignment) const {
  assert(is_aligned(offset, alignment), "Invalid offset");
  assert(is_aligned(length, alignment), "Invalid length");

  log_debug(gc)("Expanding heap from " SIZE_FORMAT "M to " SIZE_FORMAT "M", offset / M, (offset + length) / M);

  return is_hugetlbfs() ? try_expand_hugetlbfs(offset, length) : try_expand_tmpfs(offset, length);
}

size_t ZBackingFile::try_expand(size_t offset, size_t length, size_t alignment) const {
  size_t start = offset;
  size_t end = offset + length;

  // Try to expand
  if (try_expand_tmpfs_or_hugetlbfs(start, length, alignment)) {
    // Success
    return end;
  }

  // Failed, try to expand as much as possible
  for (;;) {
    length = align_down((end - start) / 2, alignment);
    if (length < alignment) {
      // Done, don't expand more
      return start;
    }

    if (try_expand_tmpfs_or_hugetlbfs(start, length, alignment)) {
      // Success, try expand more
      start += length;
    } else {
      // Failed, try expand less
      end -= length;
    }
  }
}
