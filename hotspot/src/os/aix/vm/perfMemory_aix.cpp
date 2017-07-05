/*
 * Copyright (c) 2001, 2016, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2012, 2016 SAP SE. All rights reserved.
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
#include "classfile/vmSymbols.hpp"
#include "memory/allocation.inline.hpp"
#include "memory/resourceArea.hpp"
#include "oops/oop.inline.hpp"
#include "os_aix.inline.hpp"
#include "runtime/handles.inline.hpp"
#include "runtime/os.hpp"
#include "runtime/perfMemory.hpp"
#include "services/memTracker.hpp"
#include "utilities/exceptions.hpp"

// put OS-includes here
# include <sys/types.h>
# include <sys/mman.h>
# include <errno.h>
# include <stdio.h>
# include <unistd.h>
# include <sys/stat.h>
# include <signal.h>
# include <pwd.h>

static char* backing_store_file_name = NULL;  // name of the backing store
                                              // file, if successfully created.

// Standard Memory Implementation Details

// create the PerfData memory region in standard memory.
//
static char* create_standard_memory(size_t size) {

  // allocate an aligned chuck of memory
  char* mapAddress = os::reserve_memory(size);

  if (mapAddress == NULL) {
    return NULL;
  }

  // commit memory
  if (!os::commit_memory(mapAddress, size, !ExecMem)) {
    if (PrintMiscellaneous && Verbose) {
      warning("Could not commit PerfData memory\n");
    }
    os::release_memory(mapAddress, size);
    return NULL;
  }

  return mapAddress;
}

// delete the PerfData memory region
//
static void delete_standard_memory(char* addr, size_t size) {

  // there are no persistent external resources to cleanup for standard
  // memory. since DestroyJavaVM does not support unloading of the JVM,
  // cleanup of the memory resource is not performed. The memory will be
  // reclaimed by the OS upon termination of the process.
  //
  return;
}

// save the specified memory region to the given file
//
// Note: this function might be called from signal handler (by os::abort()),
// don't allocate heap memory.
//
static void save_memory_to_file(char* addr, size_t size) {

  const char* destfile = PerfMemory::get_perfdata_file_path();
  assert(destfile[0] != '\0', "invalid PerfData file path");

  int result;

  RESTARTABLE(::open(destfile, O_CREAT|O_WRONLY|O_TRUNC, S_IREAD|S_IWRITE),
              result);;
  if (result == OS_ERR) {
    if (PrintMiscellaneous && Verbose) {
      warning("Could not create Perfdata save file: %s: %s\n",
              destfile, os::strerror(errno));
    }
  } else {
    int fd = result;

    for (size_t remaining = size; remaining > 0;) {

      RESTARTABLE(::write(fd, addr, remaining), result);
      if (result == OS_ERR) {
        if (PrintMiscellaneous && Verbose) {
          warning("Could not write Perfdata save file: %s: %s\n",
                  destfile, os::strerror(errno));
        }
        break;
      }

      remaining -= (size_t)result;
      addr += result;
    }

    result = ::close(fd);
    if (PrintMiscellaneous && Verbose) {
      if (result == OS_ERR) {
        warning("Could not close %s: %s\n", destfile, os::strerror(errno));
      }
    }
  }
  FREE_C_HEAP_ARRAY(char, destfile);
}


// Shared Memory Implementation Details

// Note: the solaris and linux shared memory implementation uses the mmap
// interface with a backing store file to implement named shared memory.
// Using the file system as the name space for shared memory allows a
// common name space to be supported across a variety of platforms. It
// also provides a name space that Java applications can deal with through
// simple file apis.
//
// The solaris and linux implementations store the backing store file in
// a user specific temporary directory located in the /tmp file system,
// which is always a local file system and is sometimes a RAM based file
// system.

// return the user specific temporary directory name.
//
// the caller is expected to free the allocated memory.
//
static char* get_user_tmp_dir(const char* user) {

  const char* tmpdir = os::get_temp_directory();
  const char* perfdir = PERFDATA_NAME;
  size_t nbytes = strlen(tmpdir) + strlen(perfdir) + strlen(user) + 3;
  char* dirname = NEW_C_HEAP_ARRAY(char, nbytes, mtInternal);

  // construct the path name to user specific tmp directory
  snprintf(dirname, nbytes, "%s/%s_%s", tmpdir, perfdir, user);

  return dirname;
}

// convert the given file name into a process id. if the file
// does not meet the file naming constraints, return 0.
//
static pid_t filename_to_pid(const char* filename) {

  // a filename that doesn't begin with a digit is not a
  // candidate for conversion.
  //
  if (!isdigit(*filename)) {
    return 0;
  }

  // check if file name can be converted to an integer without
  // any leftover characters.
  //
  char* remainder = NULL;
  errno = 0;
  pid_t pid = (pid_t)strtol(filename, &remainder, 10);

  if (errno != 0) {
    return 0;
  }

  // check for left over characters. If any, then the filename is
  // not a candidate for conversion.
  //
  if (remainder != NULL && *remainder != '\0') {
    return 0;
  }

  // successful conversion, return the pid
  return pid;
}

// Check if the given statbuf is considered a secure directory for
// the backing store files. Returns true if the directory is considered
// a secure location. Returns false if the statbuf is a symbolic link or
// if an error occurred.
//
static bool is_statbuf_secure(struct stat *statp) {
  if (S_ISLNK(statp->st_mode) || !S_ISDIR(statp->st_mode)) {
    // The path represents a link or some non-directory file type,
    // which is not what we expected. Declare it insecure.
    //
    return false;
  }
  // We have an existing directory, check if the permissions are safe.
  //
  if ((statp->st_mode & (S_IWGRP|S_IWOTH)) != 0) {
    // The directory is open for writing and could be subjected
    // to a symlink or a hard link attack. Declare it insecure.
    //
    return false;
  }
  // If user is not root then see if the uid of the directory matches the effective uid of the process.
  uid_t euid = geteuid();
  if ((euid != 0) && (statp->st_uid != euid)) {
    // The directory was not created by this user, declare it insecure.
    //
    return false;
  }
  return true;
}


// Check if the given path is considered a secure directory for
// the backing store files. Returns true if the directory exists
// and is considered a secure location. Returns false if the path
// is a symbolic link or if an error occurred.
//
static bool is_directory_secure(const char* path) {
  struct stat statbuf;
  int result = 0;

  RESTARTABLE(::lstat(path, &statbuf), result);
  if (result == OS_ERR) {
    return false;
  }

  // The path exists, see if it is secure.
  return is_statbuf_secure(&statbuf);
}

// (Taken over from Solaris to support the O_NOFOLLOW case on AIX.)
// Check if the given directory file descriptor is considered a secure
// directory for the backing store files. Returns true if the directory
// exists and is considered a secure location. Returns false if the path
// is a symbolic link or if an error occurred.
static bool is_dirfd_secure(int dir_fd) {
  struct stat statbuf;
  int result = 0;

  RESTARTABLE(::fstat(dir_fd, &statbuf), result);
  if (result == OS_ERR) {
    return false;
  }

  // The path exists, now check its mode.
  return is_statbuf_secure(&statbuf);
}


// Check to make sure fd1 and fd2 are referencing the same file system object.
static bool is_same_fsobject(int fd1, int fd2) {
  struct stat statbuf1;
  struct stat statbuf2;
  int result = 0;

  RESTARTABLE(::fstat(fd1, &statbuf1), result);
  if (result == OS_ERR) {
    return false;
  }
  RESTARTABLE(::fstat(fd2, &statbuf2), result);
  if (result == OS_ERR) {
    return false;
  }

  if ((statbuf1.st_ino == statbuf2.st_ino) &&
      (statbuf1.st_dev == statbuf2.st_dev)) {
    return true;
  } else {
    return false;
  }
}

// Helper functions for open without O_NOFOLLOW which is not present on AIX 5.3/6.1.
// We use the jdk6 implementation here.
#ifndef O_NOFOLLOW
// The O_NOFOLLOW oflag doesn't exist before solaris 5.10, this is to simulate that behaviour
// was done in jdk 5/6 hotspot by Oracle this way
static int open_o_nofollow_impl(const char* path, int oflag, mode_t mode, bool use_mode) {
  struct stat orig_st;
  struct stat new_st;
  bool create;
  int error;
  int fd;
  int result;

  create = false;

  RESTARTABLE(::lstat(path, &orig_st), result);

  if (result == OS_ERR) {
    if (errno == ENOENT && (oflag & O_CREAT) != 0) {
      // File doesn't exist, but_we want to create it, add O_EXCL flag
      // to make sure no-one creates it (or a symlink) before us
      // This works as we expect with symlinks, from posix man page:
      // 'If O_EXCL  and  O_CREAT  are set, and path names a symbolic
      // link, open() shall fail and set errno to [EEXIST]'.
      oflag |= O_EXCL;
      create = true;
    } else {
      // File doesn't exist, and we are not creating it.
      return OS_ERR;
    }
  } else {
    // lstat success, check if existing file is a link.
    if ((orig_st.st_mode & S_IFMT) == S_IFLNK)  {
      // File is a symlink.
      errno = ELOOP;
      return OS_ERR;
    }
  }

  if (use_mode == true) {
    RESTARTABLE(::open(path, oflag, mode), fd);
  } else {
    RESTARTABLE(::open(path, oflag), fd);
  }

  if (fd == OS_ERR) {
    return fd;
  }

  // Can't do inode checks on before/after if we created the file.
  if (create == false) {
    RESTARTABLE(::fstat(fd, &new_st), result);
    if (result == OS_ERR) {
      // Keep errno from fstat, in case close also fails.
      error = errno;
      ::close(fd);
      errno = error;
      return OS_ERR;
    }

    if (orig_st.st_dev != new_st.st_dev || orig_st.st_ino != new_st.st_ino) {
      // File was tampered with during race window.
      ::close(fd);
      errno = EEXIST;
      if (PrintMiscellaneous && Verbose) {
        warning("possible file tampering attempt detected when opening %s", path);
      }
      return OS_ERR;
    }
  }

  return fd;
}

static int open_o_nofollow(const char* path, int oflag, mode_t mode) {
  return open_o_nofollow_impl(path, oflag, mode, true);
}

static int open_o_nofollow(const char* path, int oflag) {
  return open_o_nofollow_impl(path, oflag, 0, false);
}
#endif

// Open the directory of the given path and validate it.
// Return a DIR * of the open directory.
static DIR *open_directory_secure(const char* dirname) {
  // Open the directory using open() so that it can be verified
  // to be secure by calling is_dirfd_secure(), opendir() and then check
  // to see if they are the same file system object.  This method does not
  // introduce a window of opportunity for the directory to be attacked that
  // calling opendir() and is_directory_secure() does.
  int result;
  DIR *dirp = NULL;

  // No O_NOFOLLOW defined at buildtime, and it is not documented for open;
  // so provide a workaround in this case.
#ifdef O_NOFOLLOW
  RESTARTABLE(::open(dirname, O_RDONLY|O_NOFOLLOW), result);
#else
  // workaround (jdk6 coding)
  result = open_o_nofollow(dirname, O_RDONLY);
#endif

  if (result == OS_ERR) {
    // Directory doesn't exist or is a symlink, so there is nothing to cleanup.
    if (PrintMiscellaneous && Verbose) {
      if (errno == ELOOP) {
        warning("directory %s is a symlink and is not secure\n", dirname);
      } else {
        warning("could not open directory %s: %s\n", dirname, os::strerror(errno));
      }
    }
    return dirp;
  }
  int fd = result;

  // Determine if the open directory is secure.
  if (!is_dirfd_secure(fd)) {
    // The directory is not a secure directory.
    os::close(fd);
    return dirp;
  }

  // Open the directory.
  dirp = ::opendir(dirname);
  if (dirp == NULL) {
    // The directory doesn't exist, close fd and return.
    os::close(fd);
    return dirp;
  }

  // Check to make sure fd and dirp are referencing the same file system object.
  if (!is_same_fsobject(fd, dirp->dd_fd)) {
    // The directory is not secure.
    os::close(fd);
    os::closedir(dirp);
    dirp = NULL;
    return dirp;
  }

  // Close initial open now that we know directory is secure
  os::close(fd);

  return dirp;
}

// NOTE: The code below uses fchdir(), open() and unlink() because
// fdopendir(), openat() and unlinkat() are not supported on all
// versions.  Once the support for fdopendir(), openat() and unlinkat()
// is available on all supported versions the code can be changed
// to use these functions.

// Open the directory of the given path, validate it and set the
// current working directory to it.
// Return a DIR * of the open directory and the saved cwd fd.
//
static DIR *open_directory_secure_cwd(const char* dirname, int *saved_cwd_fd) {

  // Open the directory.
  DIR* dirp = open_directory_secure(dirname);
  if (dirp == NULL) {
    // Directory doesn't exist or is insecure, so there is nothing to cleanup.
    return dirp;
  }
  int fd = dirp->dd_fd;

  // Open a fd to the cwd and save it off.
  int result;
  RESTARTABLE(::open(".", O_RDONLY), result);
  if (result == OS_ERR) {
    *saved_cwd_fd = -1;
  } else {
    *saved_cwd_fd = result;
  }

  // Set the current directory to dirname by using the fd of the directory and
  // handle errors, otherwise shared memory files will be created in cwd.
  result = fchdir(fd);
  if (result == OS_ERR) {
    if (PrintMiscellaneous && Verbose) {
      warning("could not change to directory %s", dirname);
    }
    if (*saved_cwd_fd != -1) {
      ::close(*saved_cwd_fd);
      *saved_cwd_fd = -1;
    }
    // Close the directory.
    os::closedir(dirp);
    return NULL;
  } else {
    return dirp;
  }
}

// Close the directory and restore the current working directory.
//
static void close_directory_secure_cwd(DIR* dirp, int saved_cwd_fd) {

  int result;
  // If we have a saved cwd change back to it and close the fd.
  if (saved_cwd_fd != -1) {
    result = fchdir(saved_cwd_fd);
    ::close(saved_cwd_fd);
  }

  // Close the directory.
  os::closedir(dirp);
}

// Check if the given file descriptor is considered a secure.
static bool is_file_secure(int fd, const char *filename) {

  int result;
  struct stat statbuf;

  // Determine if the file is secure.
  RESTARTABLE(::fstat(fd, &statbuf), result);
  if (result == OS_ERR) {
    if (PrintMiscellaneous && Verbose) {
      warning("fstat failed on %s: %s\n", filename, os::strerror(errno));
    }
    return false;
  }
  if (statbuf.st_nlink > 1) {
    // A file with multiple links is not expected.
    if (PrintMiscellaneous && Verbose) {
      warning("file %s has multiple links\n", filename);
    }
    return false;
  }
  return true;
}

// Return the user name for the given user id.
//
// The caller is expected to free the allocated memory.
static char* get_user_name(uid_t uid) {

  struct passwd pwent;

  // Determine the max pwbuf size from sysconf, and hardcode
  // a default if this not available through sysconf.
  long bufsize = sysconf(_SC_GETPW_R_SIZE_MAX);
  if (bufsize == -1)
    bufsize = 1024;

  char* pwbuf = NEW_C_HEAP_ARRAY(char, bufsize, mtInternal);

  struct passwd* p;
  int result = getpwuid_r(uid, &pwent, pwbuf, (size_t)bufsize, &p);

  if (result != 0 || p == NULL || p->pw_name == NULL || *(p->pw_name) == '\0') {
    if (PrintMiscellaneous && Verbose) {
      if (result != 0) {
        warning("Could not retrieve passwd entry: %s\n",
                os::strerror(result));
      }
      else if (p == NULL) {
        // this check is added to protect against an observed problem
        // with getpwuid_r() on RedHat 9 where getpwuid_r returns 0,
        // indicating success, but has p == NULL. This was observed when
        // inserting a file descriptor exhaustion fault prior to the call
        // getpwuid_r() call. In this case, error is set to the appropriate
        // error condition, but this is undocumented behavior. This check
        // is safe under any condition, but the use of errno in the output
        // message may result in an erroneous message.
        // Bug Id 89052 was opened with RedHat.
        //
        warning("Could not retrieve passwd entry: %s\n",
                os::strerror(errno));
      }
      else {
        warning("Could not determine user name: %s\n",
                p->pw_name == NULL ? "pw_name = NULL" :
                                     "pw_name zero length");
      }
    }
    FREE_C_HEAP_ARRAY(char, pwbuf);
    return NULL;
  }

  char* user_name = NEW_C_HEAP_ARRAY(char, strlen(p->pw_name) + 1, mtInternal);
  strcpy(user_name, p->pw_name);

  FREE_C_HEAP_ARRAY(char, pwbuf);
  return user_name;
}

// return the name of the user that owns the process identified by vmid.
//
// This method uses a slow directory search algorithm to find the backing
// store file for the specified vmid and returns the user name, as determined
// by the user name suffix of the hsperfdata_<username> directory name.
//
// the caller is expected to free the allocated memory.
//
static char* get_user_name_slow(int vmid, TRAPS) {

  // short circuit the directory search if the process doesn't even exist.
  if (kill(vmid, 0) == OS_ERR) {
    if (errno == ESRCH) {
      THROW_MSG_0(vmSymbols::java_lang_IllegalArgumentException(),
                  "Process not found");
    }
    else /* EPERM */ {
      THROW_MSG_0(vmSymbols::java_io_IOException(), os::strerror(errno));
    }
  }

  // directory search
  char* oldest_user = NULL;
  time_t oldest_ctime = 0;

  const char* tmpdirname = os::get_temp_directory();

  DIR* tmpdirp = os::opendir(tmpdirname);

  if (tmpdirp == NULL) {
    return NULL;
  }

  // for each entry in the directory that matches the pattern hsperfdata_*,
  // open the directory and check if the file for the given vmid exists.
  // The file with the expected name and the latest creation date is used
  // to determine the user name for the process id.
  //
  struct dirent* dentry;
  char* tdbuf = NEW_C_HEAP_ARRAY(char, os::readdir_buf_size(tmpdirname), mtInternal);
  errno = 0;
  while ((dentry = os::readdir(tmpdirp, (struct dirent *)tdbuf)) != NULL) {

    // check if the directory entry is a hsperfdata file
    if (strncmp(dentry->d_name, PERFDATA_NAME, strlen(PERFDATA_NAME)) != 0) {
      continue;
    }

    char* usrdir_name = NEW_C_HEAP_ARRAY(char,
                              strlen(tmpdirname) + strlen(dentry->d_name) + 2, mtInternal);
    strcpy(usrdir_name, tmpdirname);
    strcat(usrdir_name, "/");
    strcat(usrdir_name, dentry->d_name);

    // Open the user directory.
    DIR* subdirp = open_directory_secure(usrdir_name);

    if (subdirp == NULL) {
      FREE_C_HEAP_ARRAY(char, usrdir_name);
      continue;
    }

    // Since we don't create the backing store files in directories
    // pointed to by symbolic links, we also don't follow them when
    // looking for the files. We check for a symbolic link after the
    // call to opendir in order to eliminate a small window where the
    // symlink can be exploited.
    //
    if (!is_directory_secure(usrdir_name)) {
      FREE_C_HEAP_ARRAY(char, usrdir_name);
      os::closedir(subdirp);
      continue;
    }

    struct dirent* udentry;
    char* udbuf = NEW_C_HEAP_ARRAY(char, os::readdir_buf_size(usrdir_name), mtInternal);
    errno = 0;
    while ((udentry = os::readdir(subdirp, (struct dirent *)udbuf)) != NULL) {

      if (filename_to_pid(udentry->d_name) == vmid) {
        struct stat statbuf;
        int result;

        char* filename = NEW_C_HEAP_ARRAY(char,
                            strlen(usrdir_name) + strlen(udentry->d_name) + 2, mtInternal);

        strcpy(filename, usrdir_name);
        strcat(filename, "/");
        strcat(filename, udentry->d_name);

        // don't follow symbolic links for the file
        RESTARTABLE(::lstat(filename, &statbuf), result);
        if (result == OS_ERR) {
           FREE_C_HEAP_ARRAY(char, filename);
           continue;
        }

        // skip over files that are not regular files.
        if (!S_ISREG(statbuf.st_mode)) {
          FREE_C_HEAP_ARRAY(char, filename);
          continue;
        }

        // compare and save filename with latest creation time
        if (statbuf.st_size > 0 && statbuf.st_ctime > oldest_ctime) {

          if (statbuf.st_ctime > oldest_ctime) {
            char* user = strchr(dentry->d_name, '_') + 1;

            if (oldest_user != NULL) FREE_C_HEAP_ARRAY(char, oldest_user);
            oldest_user = NEW_C_HEAP_ARRAY(char, strlen(user)+1, mtInternal);

            strcpy(oldest_user, user);
            oldest_ctime = statbuf.st_ctime;
          }
        }

        FREE_C_HEAP_ARRAY(char, filename);
      }
    }
    os::closedir(subdirp);
    FREE_C_HEAP_ARRAY(char, udbuf);
    FREE_C_HEAP_ARRAY(char, usrdir_name);
  }
  os::closedir(tmpdirp);
  FREE_C_HEAP_ARRAY(char, tdbuf);

  return(oldest_user);
}

// return the name of the user that owns the JVM indicated by the given vmid.
//
static char* get_user_name(int vmid, TRAPS) {
  return get_user_name_slow(vmid, THREAD);
}

// return the file name of the backing store file for the named
// shared memory region for the given user name and vmid.
//
// the caller is expected to free the allocated memory.
//
static char* get_sharedmem_filename(const char* dirname, int vmid) {

  // add 2 for the file separator and a null terminator.
  size_t nbytes = strlen(dirname) + UINT_CHARS + 2;

  char* name = NEW_C_HEAP_ARRAY(char, nbytes, mtInternal);
  snprintf(name, nbytes, "%s/%d", dirname, vmid);

  return name;
}


// remove file
//
// this method removes the file specified by the given path
//
static void remove_file(const char* path) {

  int result;

  // if the file is a directory, the following unlink will fail. since
  // we don't expect to find directories in the user temp directory, we
  // won't try to handle this situation. even if accidentially or
  // maliciously planted, the directory's presence won't hurt anything.
  //
  RESTARTABLE(::unlink(path), result);
  if (PrintMiscellaneous && Verbose && result == OS_ERR) {
    if (errno != ENOENT) {
      warning("Could not unlink shared memory backing"
              " store file %s : %s\n", path, os::strerror(errno));
    }
  }
}

// Cleanup stale shared memory resources
//
// This method attempts to remove all stale shared memory files in
// the named user temporary directory. It scans the named directory
// for files matching the pattern ^$[0-9]*$. For each file found, the
// process id is extracted from the file name and a test is run to
// determine if the process is alive. If the process is not alive,
// any stale file resources are removed.
static void cleanup_sharedmem_resources(const char* dirname) {

  int saved_cwd_fd;
  // Open the directory.
  DIR* dirp = open_directory_secure_cwd(dirname, &saved_cwd_fd);
  if (dirp == NULL) {
     // Directory doesn't exist or is insecure, so there is nothing to cleanup.
    return;
  }

  // For each entry in the directory that matches the expected file
  // name pattern, determine if the file resources are stale and if
  // so, remove the file resources. Note, instrumented HotSpot processes
  // for this user may start and/or terminate during this search and
  // remove or create new files in this directory. The behavior of this
  // loop under these conditions is dependent upon the implementation of
  // opendir/readdir.
  struct dirent* entry;
  char* dbuf = NEW_C_HEAP_ARRAY(char, os::readdir_buf_size(dirname), mtInternal);

  errno = 0;
  while ((entry = os::readdir(dirp, (struct dirent *)dbuf)) != NULL) {

    pid_t pid = filename_to_pid(entry->d_name);

    if (pid == 0) {

      if (strcmp(entry->d_name, ".") != 0 && strcmp(entry->d_name, "..") != 0) {

        // Attempt to remove all unexpected files, except "." and "..".
        unlink(entry->d_name);
      }

      errno = 0;
      continue;
    }

    // We now have a file name that converts to a valid integer
    // that could represent a process id . if this process id
    // matches the current process id or the process is not running,
    // then remove the stale file resources.
    //
    // Process liveness is detected by sending signal number 0 to
    // the process id (see kill(2)). if kill determines that the
    // process does not exist, then the file resources are removed.
    // if kill determines that that we don't have permission to
    // signal the process, then the file resources are assumed to
    // be stale and are removed because the resources for such a
    // process should be in a different user specific directory.
    if ((pid == os::current_process_id()) ||
        (kill(pid, 0) == OS_ERR && (errno == ESRCH || errno == EPERM))) {

        unlink(entry->d_name);
    }
    errno = 0;
  }

  // Close the directory and reset the current working directory.
  close_directory_secure_cwd(dirp, saved_cwd_fd);

  FREE_C_HEAP_ARRAY(char, dbuf);
}

// Make the user specific temporary directory. Returns true if
// the directory exists and is secure upon return. Returns false
// if the directory exists but is either a symlink, is otherwise
// insecure, or if an error occurred.
static bool make_user_tmp_dir(const char* dirname) {

  // Create the directory with 0755 permissions. note that the directory
  // will be owned by euid::egid, which may not be the same as uid::gid.
  if (mkdir(dirname, S_IRWXU|S_IRGRP|S_IXGRP|S_IROTH|S_IXOTH) == OS_ERR) {
    if (errno == EEXIST) {
      // The directory already exists and was probably created by another
      // JVM instance. However, this could also be the result of a
      // deliberate symlink. Verify that the existing directory is safe.
      if (!is_directory_secure(dirname)) {
        // Directory is not secure.
        if (PrintMiscellaneous && Verbose) {
          warning("%s directory is insecure\n", dirname);
        }
        return false;
      }
    }
    else {
      // we encountered some other failure while attempting
      // to create the directory
      //
      if (PrintMiscellaneous && Verbose) {
        warning("could not create directory %s: %s\n",
                dirname, os::strerror(errno));
      }
      return false;
    }
  }
  return true;
}

// create the shared memory file resources
//
// This method creates the shared memory file with the given size
// This method also creates the user specific temporary directory, if
// it does not yet exist.
//
static int create_sharedmem_resources(const char* dirname, const char* filename, size_t size) {

  // make the user temporary directory
  if (!make_user_tmp_dir(dirname)) {
    // could not make/find the directory or the found directory
    // was not secure
    return -1;
  }

  int saved_cwd_fd;
  // Open the directory and set the current working directory to it.
  DIR* dirp = open_directory_secure_cwd(dirname, &saved_cwd_fd);
  if (dirp == NULL) {
    // Directory doesn't exist or is insecure, so cannot create shared
    // memory file.
    return -1;
  }

  // Open the filename in the current directory.
  // Cannot use O_TRUNC here; truncation of an existing file has to happen
  // after the is_file_secure() check below.
  int result;

  // No O_NOFOLLOW defined at buildtime, and it is not documented for open;
  // so provide a workaround in this case.
#ifdef O_NOFOLLOW
  RESTARTABLE(::open(filename, O_RDWR|O_CREAT|O_NOFOLLOW, S_IREAD|S_IWRITE), result);
#else
  // workaround function (jdk6 code)
  result = open_o_nofollow(filename, O_RDWR|O_CREAT, S_IREAD|S_IWRITE);
#endif

  if (result == OS_ERR) {
    if (PrintMiscellaneous && Verbose) {
      if (errno == ELOOP) {
        warning("file %s is a symlink and is not secure\n", filename);
      } else {
        warning("could not create file %s: %s\n", filename, os::strerror(errno));
      }
    }
    // Close the directory and reset the current working directory.
    close_directory_secure_cwd(dirp, saved_cwd_fd);

    return -1;
  }
  // Close the directory and reset the current working directory.
  close_directory_secure_cwd(dirp, saved_cwd_fd);

  // save the file descriptor
  int fd = result;

  // Check to see if the file is secure.
  if (!is_file_secure(fd, filename)) {
    ::close(fd);
    return -1;
  }

  // Truncate the file to get rid of any existing data.
  RESTARTABLE(::ftruncate(fd, (off_t)0), result);
  if (result == OS_ERR) {
    if (PrintMiscellaneous && Verbose) {
      warning("could not truncate shared memory file: %s\n", os::strerror(errno));
    }
    ::close(fd);
    return -1;
  }
  // set the file size
  RESTARTABLE(::ftruncate(fd, (off_t)size), result);
  if (result == OS_ERR) {
    if (PrintMiscellaneous && Verbose) {
      warning("could not set shared memory file size: %s\n", os::strerror(errno));
    }
    ::close(fd);
    return -1;
  }

  return fd;
}

// open the shared memory file for the given user and vmid. returns
// the file descriptor for the open file or -1 if the file could not
// be opened.
//
static int open_sharedmem_file(const char* filename, int oflags, TRAPS) {

  // open the file
  int result;
  // provide a workaround in case no O_NOFOLLOW is defined at buildtime
#ifdef O_NOFOLLOW
  RESTARTABLE(::open(filename, oflags), result);
#else
  result = open_o_nofollow(filename, oflags);
#endif
  if (result == OS_ERR) {
    if (errno == ENOENT) {
      THROW_MSG_(vmSymbols::java_lang_IllegalArgumentException(),
                 "Process not found", OS_ERR);
    }
    else if (errno == EACCES) {
      THROW_MSG_(vmSymbols::java_lang_IllegalArgumentException(),
                 "Permission denied", OS_ERR);
    }
    else {
      THROW_MSG_(vmSymbols::java_io_IOException(),
                 os::strerror(errno), OS_ERR);
    }
  }
  int fd = result;

  // Check to see if the file is secure.
  if (!is_file_secure(fd, filename)) {
    ::close(fd);
    return -1;
  }

  return fd;
}

// create a named shared memory region. returns the address of the
// memory region on success or NULL on failure. A return value of
// NULL will ultimately disable the shared memory feature.
//
// On AIX, the name space for shared memory objects
// is the file system name space.
//
// A monitoring application attaching to a JVM does not need to know
// the file system name of the shared memory object. However, it may
// be convenient for applications to discover the existence of newly
// created and terminating JVMs by watching the file system name space
// for files being created or removed.
//
static char* mmap_create_shared(size_t size) {

  int result;
  int fd;
  char* mapAddress;

  int vmid = os::current_process_id();

  char* user_name = get_user_name(geteuid());

  if (user_name == NULL)
    return NULL;

  char* dirname = get_user_tmp_dir(user_name);
  char* filename = get_sharedmem_filename(dirname, vmid);

  // get the short filename.
  char* short_filename = strrchr(filename, '/');
  if (short_filename == NULL) {
    short_filename = filename;
  } else {
    short_filename++;
  }

  // cleanup any stale shared memory files
  cleanup_sharedmem_resources(dirname);

  assert(((size > 0) && (size % os::vm_page_size() == 0)),
         "unexpected PerfMemory region size");

  fd = create_sharedmem_resources(dirname, short_filename, size);

  FREE_C_HEAP_ARRAY(char, user_name);
  FREE_C_HEAP_ARRAY(char, dirname);

  if (fd == -1) {
    FREE_C_HEAP_ARRAY(char, filename);
    return NULL;
  }

  mapAddress = (char*)::mmap((char*)0, size, PROT_READ|PROT_WRITE, MAP_SHARED, fd, 0);

  result = ::close(fd);
  assert(result != OS_ERR, "could not close file");

  if (mapAddress == MAP_FAILED) {
    if (PrintMiscellaneous && Verbose) {
      warning("mmap failed -  %s\n", os::strerror(errno));
    }
    remove_file(filename);
    FREE_C_HEAP_ARRAY(char, filename);
    return NULL;
  }

  // save the file name for use in delete_shared_memory()
  backing_store_file_name = filename;

  // clear the shared memory region
  (void)::memset((void*) mapAddress, 0, size);

  // It does not go through os api, the operation has to record from here.
  MemTracker::record_virtual_memory_reserve((address)mapAddress, size, CURRENT_PC, mtInternal);

  return mapAddress;
}

// release a named shared memory region
//
static void unmap_shared(char* addr, size_t bytes) {
  // Do not rely on os::reserve_memory/os::release_memory to use mmap.
  // Use os::reserve_memory/os::release_memory for PerfDisableSharedMem=1, mmap/munmap for PerfDisableSharedMem=0
  if (::munmap(addr, bytes) == -1) {
    warning("perfmemory: munmap failed (%d)\n", errno);
  }
}

// create the PerfData memory region in shared memory.
//
static char* create_shared_memory(size_t size) {

  // create the shared memory region.
  return mmap_create_shared(size);
}

// delete the shared PerfData memory region
//
static void delete_shared_memory(char* addr, size_t size) {

  // cleanup the persistent shared memory resources. since DestroyJavaVM does
  // not support unloading of the JVM, unmapping of the memory resource is
  // not performed. The memory will be reclaimed by the OS upon termination of
  // the process. The backing store file is deleted from the file system.

  assert(!PerfDisableSharedMem, "shouldn't be here");

  if (backing_store_file_name != NULL) {
    remove_file(backing_store_file_name);
    // Don't.. Free heap memory could deadlock os::abort() if it is called
    // from signal handler. OS will reclaim the heap memory.
    // FREE_C_HEAP_ARRAY(char, backing_store_file_name);
    backing_store_file_name = NULL;
  }
}

// return the size of the file for the given file descriptor
// or 0 if it is not a valid size for a shared memory file
//
static size_t sharedmem_filesize(int fd, TRAPS) {

  struct stat statbuf;
  int result;

  RESTARTABLE(::fstat(fd, &statbuf), result);
  if (result == OS_ERR) {
    if (PrintMiscellaneous && Verbose) {
      warning("fstat failed: %s\n", os::strerror(errno));
    }
    THROW_MSG_0(vmSymbols::java_io_IOException(),
                "Could not determine PerfMemory size");
  }

  if ((statbuf.st_size == 0) ||
     ((size_t)statbuf.st_size % os::vm_page_size() != 0)) {
    THROW_MSG_0(vmSymbols::java_lang_Exception(),
                "Invalid PerfMemory size");
  }

  return (size_t)statbuf.st_size;
}

// attach to a named shared memory region.
//
static void mmap_attach_shared(const char* user, int vmid, PerfMemory::PerfMemoryMode mode, char** addr, size_t* sizep, TRAPS) {

  char* mapAddress;
  int result;
  int fd;
  size_t size = 0;
  const char* luser = NULL;

  int mmap_prot;
  int file_flags;

  ResourceMark rm;

  // map the high level access mode to the appropriate permission
  // constructs for the file and the shared memory mapping.
  if (mode == PerfMemory::PERF_MODE_RO) {
    mmap_prot = PROT_READ;
  // No O_NOFOLLOW defined at buildtime, and it is not documented for open.
#ifdef O_NOFOLLOW
    file_flags = O_RDONLY | O_NOFOLLOW;
#else
    file_flags = O_RDONLY;
#endif
  }
  else if (mode == PerfMemory::PERF_MODE_RW) {
#ifdef LATER
    mmap_prot = PROT_READ | PROT_WRITE;
    file_flags = O_RDWR | O_NOFOLLOW;
#else
    THROW_MSG(vmSymbols::java_lang_IllegalArgumentException(),
              "Unsupported access mode");
#endif
  }
  else {
    THROW_MSG(vmSymbols::java_lang_IllegalArgumentException(),
              "Illegal access mode");
  }

  if (user == NULL || strlen(user) == 0) {
    luser = get_user_name(vmid, CHECK);
  }
  else {
    luser = user;
  }

  if (luser == NULL) {
    THROW_MSG(vmSymbols::java_lang_IllegalArgumentException(),
              "Could not map vmid to user Name");
  }

  char* dirname = get_user_tmp_dir(luser);

  // since we don't follow symbolic links when creating the backing
  // store file, we don't follow them when attaching either.
  //
  if (!is_directory_secure(dirname)) {
    FREE_C_HEAP_ARRAY(char, dirname);
    if (luser != user) {
      FREE_C_HEAP_ARRAY(char, luser);
    }
    THROW_MSG(vmSymbols::java_lang_IllegalArgumentException(),
              "Process not found");
  }

  char* filename = get_sharedmem_filename(dirname, vmid);

  // copy heap memory to resource memory. the open_sharedmem_file
  // method below need to use the filename, but could throw an
  // exception. using a resource array prevents the leak that
  // would otherwise occur.
  char* rfilename = NEW_RESOURCE_ARRAY(char, strlen(filename) + 1);
  strcpy(rfilename, filename);

  // free the c heap resources that are no longer needed
  if (luser != user) FREE_C_HEAP_ARRAY(char, luser);
  FREE_C_HEAP_ARRAY(char, dirname);
  FREE_C_HEAP_ARRAY(char, filename);

  // open the shared memory file for the give vmid
  fd = open_sharedmem_file(rfilename, file_flags, THREAD);

  if (fd == OS_ERR) {
    return;
  }

  if (HAS_PENDING_EXCEPTION) {
    ::close(fd);
    return;
  }

  if (*sizep == 0) {
    size = sharedmem_filesize(fd, CHECK);
  } else {
    size = *sizep;
  }

  assert(size > 0, "unexpected size <= 0");

  mapAddress = (char*)::mmap((char*)0, size, mmap_prot, MAP_SHARED, fd, 0);

  result = ::close(fd);
  assert(result != OS_ERR, "could not close file");

  if (mapAddress == MAP_FAILED) {
    if (PrintMiscellaneous && Verbose) {
      warning("mmap failed: %s\n", os::strerror(errno));
    }
    THROW_MSG(vmSymbols::java_lang_OutOfMemoryError(),
              "Could not map PerfMemory");
  }

  // it does not go through os api, the operation has to record from here.
  MemTracker::record_virtual_memory_reserve((address)mapAddress, size, CURRENT_PC, mtInternal);

  *addr = mapAddress;
  *sizep = size;

  if (PerfTraceMemOps) {
    tty->print("mapped " SIZE_FORMAT " bytes for vmid %d at "
               INTPTR_FORMAT "\n", size, vmid, p2i((void*)mapAddress));
  }
}




// create the PerfData memory region
//
// This method creates the memory region used to store performance
// data for the JVM. The memory may be created in standard or
// shared memory.
//
void PerfMemory::create_memory_region(size_t size) {

  if (PerfDisableSharedMem) {
    // do not share the memory for the performance data.
    _start = create_standard_memory(size);
  }
  else {
    _start = create_shared_memory(size);
    if (_start == NULL) {

      // creation of the shared memory region failed, attempt
      // to create a contiguous, non-shared memory region instead.
      //
      if (PrintMiscellaneous && Verbose) {
        warning("Reverting to non-shared PerfMemory region.\n");
      }
      PerfDisableSharedMem = true;
      _start = create_standard_memory(size);
    }
  }

  if (_start != NULL) _capacity = size;

}

// delete the PerfData memory region
//
// This method deletes the memory region used to store performance
// data for the JVM. The memory region indicated by the <address, size>
// tuple will be inaccessible after a call to this method.
//
void PerfMemory::delete_memory_region() {

  assert((start() != NULL && capacity() > 0), "verify proper state");

  // If user specifies PerfDataSaveFile, it will save the performance data
  // to the specified file name no matter whether PerfDataSaveToFile is specified
  // or not. In other word, -XX:PerfDataSaveFile=.. overrides flag
  // -XX:+PerfDataSaveToFile.
  if (PerfDataSaveToFile || PerfDataSaveFile != NULL) {
    save_memory_to_file(start(), capacity());
  }

  if (PerfDisableSharedMem) {
    delete_standard_memory(start(), capacity());
  }
  else {
    delete_shared_memory(start(), capacity());
  }
}

// attach to the PerfData memory region for another JVM
//
// This method returns an <address, size> tuple that points to
// a memory buffer that is kept reasonably synchronized with
// the PerfData memory region for the indicated JVM. This
// buffer may be kept in synchronization via shared memory
// or some other mechanism that keeps the buffer updated.
//
// If the JVM chooses not to support the attachability feature,
// this method should throw an UnsupportedOperation exception.
//
// This implementation utilizes named shared memory to map
// the indicated process's PerfData memory region into this JVMs
// address space.
//
void PerfMemory::attach(const char* user, int vmid, PerfMemoryMode mode, char** addrp, size_t* sizep, TRAPS) {

  if (vmid == 0 || vmid == os::current_process_id()) {
     *addrp = start();
     *sizep = capacity();
     return;
  }

  mmap_attach_shared(user, vmid, mode, addrp, sizep, CHECK);
}

// detach from the PerfData memory region of another JVM
//
// This method detaches the PerfData memory region of another
// JVM, specified as an <address, size> tuple of a buffer
// in this process's address space. This method may perform
// arbitrary actions to accomplish the detachment. The memory
// region specified by <address, size> will be inaccessible after
// a call to this method.
//
// If the JVM chooses not to support the attachability feature,
// this method should throw an UnsupportedOperation exception.
//
// This implementation utilizes named shared memory to detach
// the indicated process's PerfData memory region from this
// process's address space.
//
void PerfMemory::detach(char* addr, size_t bytes, TRAPS) {

  assert(addr != 0, "address sanity check");
  assert(bytes > 0, "capacity sanity check");

  if (PerfMemory::contains(addr) || PerfMemory::contains(addr + bytes - 1)) {
    // prevent accidental detachment of this process's PerfMemory region
    return;
  }

  unmap_shared(addr, bytes);
}
