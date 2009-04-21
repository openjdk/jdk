/*
 * Copyright 1998-2006 Sun Microsystems, Inc.  All Rights Reserved.
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
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 *
 */

//
// C++ wrapper to HPI.
//

class hpi : AllStatic {

private:
  static GetInterfaceFunc       _get_interface;
  static HPI_FileInterface*     _file;
  static HPI_SocketInterface*   _socket;
  static HPI_LibraryInterface*  _library;
  static HPI_SystemInterface*   _system;

private:
  static void initialize_get_interface(vm_calls_t *callbacks);

public:
  // Load and initialize everything except sockets.
  static jint initialize();

  // Socket library needs to be lazy intialized because eagerly
  // loading Winsock is known to cause "connect to your ISP"
  // dialog to show up.  Or so goes the legend.
  static jint initialize_socket_library();

  // HPI_FileInterface
  static inline char*  native_path(char *path);
  static inline int    file_type(const char *path);
  static inline int    open(const char *name, int mode, int perm);
  static inline int    close(int fd);
  static inline jlong  lseek(int fd, jlong off, int whence);
  static inline int    ftruncate(int fd, jlong length);
  static inline int    fsync(int fd);
  static inline int    available(int fd, jlong *bytes);
  static inline size_t read(int fd, void *buf, unsigned int nBytes);
  static inline size_t write(int fd, const void *buf, unsigned int nBytes);
  static inline int    fsize(int fd, jlong *size);

  // HPI_SocketInterface
  static inline int    socket(int domain, int type, int protocol);
  static inline int    socket_close(int fd);
  static inline int    socket_shutdown(int fd, int howto);
  static inline int    recv(int fd, char *buf, int nBytes, int flags);
  static inline int    send(int fd, char *buf, int nBytes, int flags);
  // Variant of send that doesn't support interruptible I/O
  static inline int    raw_send(int fd, char *buf, int nBytes, int flags);
  static inline int    timeout(int fd, long timeout);
  static inline int    listen(int fd, int count);
  static inline int    connect(int fd, struct sockaddr *him, int len);
  static inline int    bind(int fd, struct sockaddr *him, int len);
  static inline int    accept(int fd, struct sockaddr *him, int *len);
  static inline int    recvfrom(int fd, char *buf, int nbytes, int flags,
                                struct sockaddr *from, int *fromlen);
  static inline int    get_sock_name(int fd, struct sockaddr *him, int *len);
  static inline int    sendto(int fd, char *buf, int len, int flags,
                              struct sockaddr *to, int tolen);
  static inline int    socket_available(int fd, jint *pbytes);

  static inline int    get_sock_opt(int fd, int level, int optname,
                              char *optval, int* optlen);
  static inline int    set_sock_opt(int fd, int level, int optname,
                              const char *optval, int optlen);
  static inline int    get_host_name(char* name, int namelen);
  static inline struct hostent*  get_host_by_addr(const char* name, int len, int type);
  static inline struct hostent*  get_host_by_name(char* name);
  static inline struct protoent* get_proto_by_name(char* name);

  // HPI_LibraryInterface
  static inline void   dll_build_name(char *buf, int buf_len, const char* path,
                                      const char *name);
  static inline void*  dll_load(const char *name, char *ebuf, int ebuflen);
  static inline void   dll_unload(void *lib);
  static inline void*  dll_lookup(void *lib, const char *name);

  // HPI_SystemInterface
  static inline int    lasterror(char *buf, int len);
};

//
// Macros that provide inline bodies for the functions.
//

#define HPIDECL(name, names, intf, func, ret_type, ret_fmt, arg_type, arg_print, arg)   \
  inline ret_type hpi::name arg_type {        \
    if (TraceHPI) {                           \
      tty->print("hpi::" names "(");          \
      tty->print arg_print ;                  \
      tty->print(") = ");                     \
    }                                         \
    ret_type result = (*intf->func) arg ;     \
    if (TraceHPI) {                           \
      tty->print_cr(ret_fmt, result);         \
    }                                         \
    return result;                            \
  }

// Macro to facilitate moving HPI functionality into the vm.
// See bug 6348631.  The only difference between this macro and
// HPIDECL is that we call a vm method rather than use the HPI
// transfer vector.  Ultimately, we'll replace HPIDECL with
// VM_HPIDECL for all hpi methods.
#define VM_HPIDECL(name, names, func, ret_type, ret_fmt, arg_type,arg_print, arg)   \
  inline ret_type hpi::name arg_type {        \
    if (TraceHPI) {                           \
      tty->print("hpi::" names "(");          \
      tty->print arg_print ;                  \
      tty->print(") = ");                     \
    }                                         \
    ret_type result = func arg ;              \
    if (TraceHPI) {                           \
      tty->print_cr(ret_fmt, result);         \
    }                                         \
    return result;                            \
  }

#define VM_HPIDECL_VOID(name, names, func, arg_type, arg_print, arg)   \
  inline void  hpi::name arg_type {           \
    if (TraceHPI) {                           \
      tty->print("hpi::" names "(");          \
      tty->print arg_print;                   \
      tty->print(") = ");                     \
    }                                         \
    func arg;                                 \
  }

#define HPIDECL_VOID(name, names, intf, func, arg_type, arg_print, arg) \
  inline void hpi::name arg_type {            \
    if (TraceHPI) {                           \
      tty->print("hpi::" names "(");          \
      tty->print arg_print ;                  \
      tty->print_cr(") = void");              \
    }                                         \
    (*intf->func) arg ;                       \
  }


// The macro calls below realize into
//          inline char * hpi::native_path(...) {  inlined_body; }
// etc.

// HPI_FileInterface

HPIDECL(native_path, "native_path", _file, NativePath, char *, "%s",
        (char *path),
        ("path = %s", path),
        (path));

HPIDECL(file_type, "file_type", _file, FileType, int, "%d",
        (const char *path),
        ("path = %s", path),
        (path));

HPIDECL(open, "open", _file, Open, int, "%d",
        (const char *name, int mode, int perm),
        ("name = %s, mode = %d, perm = %d", name, mode, perm),
        (name, mode, perm));

HPIDECL(lseek, "seek", _file, Seek, jlong, "(a jlong)",
        (int fd, jlong off, int whence),
        ("fd = %d, off = (a jlong), whence = %d", fd, /* off, */ whence),
        (fd, off, whence));

HPIDECL(ftruncate, "ftruncate", _file, SetLength, int, "%d",
        (int fd, jlong length),
        ("fd = %d, length = (a jlong)", fd /*, length */),
        (fd, length));

HPIDECL(fsync, "fsync", _file, Sync, int, "%d",
        (int fd),
        ("fd = %d", fd),
        (fd));

HPIDECL(available, "available", _file, Available, int, "%d",
        (int fd, jlong *bytes),
        ("fd = %d, bytes = %p", fd, bytes),
        (fd, bytes));

HPIDECL(fsize, "fsize", _file, FileSizeFD, int, "%d",
        (int fd, jlong *size),
        ("fd = %d, size = %p", fd, size),
        (fd, size));

// HPI_LibraryInterface
VM_HPIDECL_VOID(dll_build_name, "dll_build_name", os::dll_build_name,
               (char *buf, int buf_len, const char *path, const char *name),
               ("buf = %p, buflen = %d, path = %s, name = %s",
                buf, buf_len, path, name),
               (buf, buf_len, path, name));

VM_HPIDECL(dll_load, "dll_load", os::dll_load,
        void *, "(void *)%p",
        (const char *name, char *ebuf, int ebuflen),
        ("name = %s, ebuf = %p, ebuflen = %d", name, ebuf, ebuflen),
        (name, ebuf, ebuflen));

HPIDECL_VOID(dll_unload, "dll_unload", _library, UnloadLibrary,
             (void *lib),
             ("lib = %p", lib),
             (lib));

HPIDECL(dll_lookup, "dll_lookup", _library, FindLibraryEntry, void *, "%p",
        (void *lib, const char *name),
        ("lib = %p, name = %s", lib, name),
        (lib, name));

// HPI_SystemInterface
HPIDECL(lasterror, "lasterror", _system, GetLastErrorString, int, "%d",
        (char *buf, int len),
        ("buf = %p, len = %d", buf, len),
        (buf, len));
