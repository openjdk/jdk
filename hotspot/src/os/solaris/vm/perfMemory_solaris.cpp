/*
 * Copyright (c) 2001, 2007, Oracle and/or its affiliates. All rights reserved.
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
# include "incls/_perfMemory_solaris.cpp.incl"

// put OS-includes here
# include <sys/types.h>
# include <sys/mman.h>
# include <errno.h>
# include <stdio.h>
# include <unistd.h>
# include <sys/stat.h>
# include <signal.h>
# include <pwd.h>
# include <procfs.h>


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
  if (!os::commit_memory(mapAddress, size)) {
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
              destfile, strerror(errno));
    }
  } else {

    int fd = result;

    for (size_t remaining = size; remaining > 0;) {

      RESTARTABLE(::write(fd, addr, remaining), result);
      if (result == OS_ERR) {
        if (PrintMiscellaneous && Verbose) {
          warning("Could not write Perfdata save file: %s: %s\n",
                  destfile, strerror(errno));
        }
        break;
      }
      remaining -= (size_t)result;
      addr += result;
    }

    RESTARTABLE(::close(fd), result);
    if (PrintMiscellaneous && Verbose) {
      if (result == OS_ERR) {
        warning("Could not close %s: %s\n", destfile, strerror(errno));
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
  char* dirname = NEW_C_HEAP_ARRAY(char, nbytes);

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


// check if the given path is considered a secure directory for
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

  // the path exists, now check it's mode
  if (S_ISLNK(statbuf.st_mode) || !S_ISDIR(statbuf.st_mode)) {
    // the path represents a link or some non-directory file type,
    // which is not what we expected. declare it insecure.
    //
    return false;
  }
  else {
    // we have an existing directory, check if the permissions are safe.
    //
    if ((statbuf.st_mode & (S_IWGRP|S_IWOTH)) != 0) {
      // the directory is open for writing and could be subjected
      // to a symlnk attack. declare it insecure.
      //
      return false;
    }
  }
  return true;
}


// return the user name for the given user id
//
// the caller is expected to free the allocated memory.
//
static char* get_user_name(uid_t uid) {

  struct passwd pwent;

  // determine the max pwbuf size from sysconf, and hardcode
  // a default if this not available through sysconf.
  //
  long bufsize = sysconf(_SC_GETPW_R_SIZE_MAX);
  if (bufsize == -1)
    bufsize = 1024;

  char* pwbuf = NEW_C_HEAP_ARRAY(char, bufsize);

#ifdef _GNU_SOURCE
  struct passwd* p = NULL;
  int result = getpwuid_r(uid, &pwent, pwbuf, (size_t)bufsize, &p);
#else  // _GNU_SOURCE
  struct passwd* p = getpwuid_r(uid, &pwent, pwbuf, (int)bufsize);
#endif // _GNU_SOURCE

  if (p == NULL || p->pw_name == NULL || *(p->pw_name) == '\0') {
    if (PrintMiscellaneous && Verbose) {
      if (p == NULL) {
        warning("Could not retrieve passwd entry: %s\n",
                strerror(errno));
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

  char* user_name = NEW_C_HEAP_ARRAY(char, strlen(p->pw_name) + 1);
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
      THROW_MSG_0(vmSymbols::java_io_IOException(), strerror(errno));
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
  char* tdbuf = NEW_C_HEAP_ARRAY(char, os::readdir_buf_size(tmpdirname));
  errno = 0;
  while ((dentry = os::readdir(tmpdirp, (struct dirent *)tdbuf)) != NULL) {

    // check if the directory entry is a hsperfdata file
    if (strncmp(dentry->d_name, PERFDATA_NAME, strlen(PERFDATA_NAME)) != 0) {
      continue;
    }

    char* usrdir_name = NEW_C_HEAP_ARRAY(char,
                              strlen(tmpdirname) + strlen(dentry->d_name) + 2);
    strcpy(usrdir_name, tmpdirname);
    strcat(usrdir_name, "/");
    strcat(usrdir_name, dentry->d_name);

    DIR* subdirp = os::opendir(usrdir_name);

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
    char* udbuf = NEW_C_HEAP_ARRAY(char, os::readdir_buf_size(usrdir_name));
    errno = 0;
    while ((udentry = os::readdir(subdirp, (struct dirent *)udbuf)) != NULL) {

      if (filename_to_pid(udentry->d_name) == vmid) {
        struct stat statbuf;
        int result;

        char* filename = NEW_C_HEAP_ARRAY(char,
                            strlen(usrdir_name) + strlen(udentry->d_name) + 2);

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
            oldest_user = NEW_C_HEAP_ARRAY(char, strlen(user)+1);

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

  char psinfo_name[PATH_MAX];
  int result;

  snprintf(psinfo_name, PATH_MAX, "/proc/%d/psinfo", vmid);

  RESTARTABLE(::open(psinfo_name, O_RDONLY), result);

  if (result != OS_ERR) {
    int fd = result;

    psinfo_t psinfo;
    char* addr = (char*)&psinfo;

    for (size_t remaining = sizeof(psinfo_t); remaining > 0;) {

      RESTARTABLE(::read(fd, addr, remaining), result);
      if (result == OS_ERR) {
        THROW_MSG_0(vmSymbols::java_io_IOException(), "Read error");
      }
      remaining-=result;
      addr+=result;
    }

    RESTARTABLE(::close(fd), result);

    // get the user name for the effective user id of the process
    char* user_name = get_user_name(psinfo.pr_euid);

    return user_name;
  }

  if (result == OS_ERR && errno == EACCES) {

    // In this case, the psinfo file for the process id existed,
    // but we didn't have permission to access it.
    THROW_MSG_0(vmSymbols::java_lang_IllegalArgumentException(),
                strerror(errno));
  }

  // at this point, we don't know if the process id itself doesn't
  // exist or if the psinfo file doesn't exit. If the psinfo file
  // doesn't exist, then we are running on Solaris 2.5.1 or earlier.
  // since the structured procfs and old procfs interfaces can't be
  // mixed, we attempt to find the file through a directory search.

  return get_user_name_slow(vmid, CHECK_NULL);
}

// return the file name of the backing store file for the named
// shared memory region for the given user name and vmid.
//
// the caller is expected to free the allocated memory.
//
static char* get_sharedmem_filename(const char* dirname, int vmid) {

  // add 2 for the file separator and a NULL terminator.
  size_t nbytes = strlen(dirname) + UINT_CHARS + 2;

  char* name = NEW_C_HEAP_ARRAY(char, nbytes);
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
              " store file %s : %s\n", path, strerror(errno));
    }
  }
}


// remove file
//
// this method removes the file with the given file name in the
// named directory.
//
static void remove_file(const char* dirname, const char* filename) {

  size_t nbytes = strlen(dirname) + strlen(filename) + 2;
  char* path = NEW_C_HEAP_ARRAY(char, nbytes);

  strcpy(path, dirname);
  strcat(path, "/");
  strcat(path, filename);

  remove_file(path);

  FREE_C_HEAP_ARRAY(char, path);
}


// cleanup stale shared memory resources
//
// This method attempts to remove all stale shared memory files in
// the named user temporary directory. It scans the named directory
// for files matching the pattern ^$[0-9]*$. For each file found, the
// process id is extracted from the file name and a test is run to
// determine if the process is alive. If the process is not alive,
// any stale file resources are removed.
//
static void cleanup_sharedmem_resources(const char* dirname) {

  // open the user temp directory
  DIR* dirp = os::opendir(dirname);

  if (dirp == NULL) {
    // directory doesn't exist, so there is nothing to cleanup
    return;
  }

  if (!is_directory_secure(dirname)) {
    // the directory is not a secure directory
    return;
  }

  // for each entry in the directory that matches the expected file
  // name pattern, determine if the file resources are stale and if
  // so, remove the file resources. Note, instrumented HotSpot processes
  // for this user may start and/or terminate during this search and
  // remove or create new files in this directory. The behavior of this
  // loop under these conditions is dependent upon the implementation of
  // opendir/readdir.
  //
  struct dirent* entry;
  char* dbuf = NEW_C_HEAP_ARRAY(char, os::readdir_buf_size(dirname));
  errno = 0;
  while ((entry = os::readdir(dirp, (struct dirent *)dbuf)) != NULL) {

    pid_t pid = filename_to_pid(entry->d_name);

    if (pid == 0) {

      if (strcmp(entry->d_name, ".") != 0 && strcmp(entry->d_name, "..") != 0) {

        // attempt to remove all unexpected files, except "." and ".."
        remove_file(dirname, entry->d_name);
      }

      errno = 0;
      continue;
    }

    // we now have a file name that converts to a valid integer
    // that could represent a process id . if this process id
    // matches the current process id or the process is not running,
    // then remove the stale file resources.
    //
    // process liveness is detected by sending signal number 0 to
    // the process id (see kill(2)). if kill determines that the
    // process does not exist, then the file resources are removed.
    // if kill determines that that we don't have permission to
    // signal the process, then the file resources are assumed to
    // be stale and are removed because the resources for such a
    // process should be in a different user specific directory.
    //
    if ((pid == os::current_process_id()) ||
        (kill(pid, 0) == OS_ERR && (errno == ESRCH || errno == EPERM))) {

        remove_file(dirname, entry->d_name);
    }
    errno = 0;
  }
  os::closedir(dirp);
  FREE_C_HEAP_ARRAY(char, dbuf);
}

// make the user specific temporary directory. Returns true if
// the directory exists and is secure upon return. Returns false
// if the directory exists but is either a symlink, is otherwise
// insecure, or if an error occurred.
//
static bool make_user_tmp_dir(const char* dirname) {

  // create the directory with 0755 permissions. note that the directory
  // will be owned by euid::egid, which may not be the same as uid::gid.
  //
  if (mkdir(dirname, S_IRWXU|S_IRGRP|S_IXGRP|S_IROTH|S_IXOTH) == OS_ERR) {
    if (errno == EEXIST) {
      // The directory already exists and was probably created by another
      // JVM instance. However, this could also be the result of a
      // deliberate symlink. Verify that the existing directory is safe.
      //
      if (!is_directory_secure(dirname)) {
        // directory is not secure
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
                dirname, strerror(errno));
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

  int result;

  RESTARTABLE(::open(filename, O_RDWR|O_CREAT|O_TRUNC, S_IREAD|S_IWRITE), result);
  if (result == OS_ERR) {
    if (PrintMiscellaneous && Verbose) {
      warning("could not create file %s: %s\n", filename, strerror(errno));
    }
    return -1;
  }

  // save the file descriptor
  int fd = result;

  // set the file size
  RESTARTABLE(::ftruncate(fd, (off_t)size), result);
  if (result == OS_ERR) {
    if (PrintMiscellaneous && Verbose) {
      warning("could not set shared memory file size: %s\n", strerror(errno));
    }
    RESTARTABLE(::close(fd), result);
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
  RESTARTABLE(::open(filename, oflags), result);
  if (result == OS_ERR) {
    if (errno == ENOENT) {
      THROW_MSG_0(vmSymbols::java_lang_IllegalArgumentException(),
                  "Process not found");
    }
    else if (errno == EACCES) {
      THROW_MSG_0(vmSymbols::java_lang_IllegalArgumentException(),
                  "Permission denied");
    }
    else {
      THROW_MSG_0(vmSymbols::java_io_IOException(), strerror(errno));
    }
  }

  return result;
}

// create a named shared memory region. returns the address of the
// memory region on success or NULL on failure. A return value of
// NULL will ultimately disable the shared memory feature.
//
// On Solaris and Linux, the name space for shared memory objects
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

  // cleanup any stale shared memory files
  cleanup_sharedmem_resources(dirname);

  assert(((size > 0) && (size % os::vm_page_size() == 0)),
         "unexpected PerfMemory region size");

  fd = create_sharedmem_resources(dirname, filename, size);

  FREE_C_HEAP_ARRAY(char, user_name);
  FREE_C_HEAP_ARRAY(char, dirname);

  if (fd == -1) {
    FREE_C_HEAP_ARRAY(char, filename);
    return NULL;
  }

  mapAddress = (char*)::mmap((char*)0, size, PROT_READ|PROT_WRITE, MAP_SHARED, fd, 0);

  // attempt to close the file - restart it if it was interrupted,
  // but ignore other failures
  RESTARTABLE(::close(fd), result);
  assert(result != OS_ERR, "could not close file");

  if (mapAddress == MAP_FAILED) {
    if (PrintMiscellaneous && Verbose) {
      warning("mmap failed -  %s\n", strerror(errno));
    }
    remove_file(filename);
    FREE_C_HEAP_ARRAY(char, filename);
    return NULL;
  }

  // save the file name for use in delete_shared_memory()
  backing_store_file_name = filename;

  // clear the shared memory region
  (void)::memset((void*) mapAddress, 0, size);

  return mapAddress;
}

// release a named shared memory region
//
static void unmap_shared(char* addr, size_t bytes) {
  os::release_memory(addr, bytes);
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
      warning("fstat failed: %s\n", strerror(errno));
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
  size_t size;
  const char* luser = NULL;

  int mmap_prot;
  int file_flags;

  ResourceMark rm;

  // map the high level access mode to the appropriate permission
  // constructs for the file and the shared memory mapping.
  if (mode == PerfMemory::PERF_MODE_RO) {
    mmap_prot = PROT_READ;
    file_flags = O_RDONLY;
  }
  else if (mode == PerfMemory::PERF_MODE_RW) {
#ifdef LATER
    mmap_prot = PROT_READ | PROT_WRITE;
    file_flags = O_RDWR;
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
  fd = open_sharedmem_file(rfilename, file_flags, CHECK);
  assert(fd != OS_ERR, "unexpected value");

  if (*sizep == 0) {
    size = sharedmem_filesize(fd, CHECK);
    assert(size != 0, "unexpected size");
  }

  mapAddress = (char*)::mmap((char*)0, size, mmap_prot, MAP_SHARED, fd, 0);

  // attempt to close the file - restart if it gets interrupted,
  // but ignore other failures
  RESTARTABLE(::close(fd), result);
  assert(result != OS_ERR, "could not close file");

  if (mapAddress == MAP_FAILED) {
    if (PrintMiscellaneous && Verbose) {
      warning("mmap failed: %s\n", strerror(errno));
    }
    THROW_MSG(vmSymbols::java_lang_OutOfMemoryError(),
              "Could not map PerfMemory");
  }

  *addr = mapAddress;
  *sizep = size;

  if (PerfTraceMemOps) {
    tty->print("mapped " SIZE_FORMAT " bytes for vmid %d at "
               INTPTR_FORMAT "\n", size, vmid, (void*)mapAddress);
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

char* PerfMemory::backing_store_filename() {
  return backing_store_file_name;
}
