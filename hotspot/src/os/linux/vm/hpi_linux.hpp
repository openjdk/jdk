/*
 * Copyright (c) 1999, 2005, Oracle and/or its affiliates. All rights reserved.
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

//
// Because the interruptible IO has been dropped for HotSpot/Linux,
// the following HPI interface is very different from HotSparc.
//

#include <unistd.h>
#include <sys/socket.h>
#include <sys/poll.h>
#include <sys/ioctl.h>
#include <netdb.h>

// HPI_FileInterface

inline int hpi::close(int fd) {
  return ::close(fd);
}

inline size_t hpi::read(int fd, void *buf, unsigned int nBytes) {
  size_t res;
  RESTARTABLE( (size_t) ::read(fd, buf, (size_t) nBytes), res);
  return res;
}

inline size_t hpi::write(int fd, const void *buf, unsigned int nBytes) {
  size_t res;
  RESTARTABLE((size_t) ::write(fd, buf, (size_t) nBytes), res);
  return res;
}


// HPI_SocketInterface

inline int hpi::socket_close(int fd) {
  return ::close(fd);
}

inline int hpi::socket(int domain, int type, int protocol) {
  return ::socket(domain, type, protocol);
}

inline int hpi::recv(int fd, char *buf, int nBytes, int flags) {
  RESTARTABLE_RETURN_INT(::recv(fd, buf, nBytes, (unsigned int) flags));
}

inline int hpi::send(int fd, char *buf, int nBytes, int flags) {
  RESTARTABLE_RETURN_INT(::send(fd, buf, nBytes, (unsigned int) flags));
}

inline int hpi::raw_send(int fd, char *buf, int nBytes, int flags) {
  return send(fd, buf, nBytes, flags);
}

inline int hpi::timeout(int fd, long timeout) {
  julong prevtime,newtime;
  struct timeval t;

  gettimeofday(&t, NULL);
  prevtime = ((julong)t.tv_sec * 1000)  +  t.tv_usec / 1000;

  for(;;) {
    struct pollfd pfd;

    pfd.fd = fd;
    pfd.events = POLLIN | POLLERR;

    int res = ::poll(&pfd, 1, timeout);

    if (res == OS_ERR && errno == EINTR) {

      // On Linux any value < 0 means "forever"

      if(timeout >= 0) {
        gettimeofday(&t, NULL);
        newtime = ((julong)t.tv_sec * 1000)  +  t.tv_usec / 1000;
        timeout -= newtime - prevtime;
        if(timeout <= 0)
          return OS_OK;
        prevtime = newtime;
      }
    } else
      return res;
  }
}

inline int hpi::listen(int fd, int count) {
  return ::listen(fd, count);
}

inline int hpi::connect(int fd, struct sockaddr *him, int len) {
  RESTARTABLE_RETURN_INT(::connect(fd, him, len));
}

inline int hpi::accept(int fd, struct sockaddr *him, int *len) {
  // This cast is from int to unsigned int on linux.  Since we
  // only pass the parameter "len" around the vm and don't try to
  // fetch it's value, this cast is safe for now. The java.net group
  // may need and want to change this interface someday if socklen_t goes
  // to 64 bits on some platform that we support.
  // Linux doc says this can't return EINTR, unlike accept() on Solaris

  return ::accept(fd, him, (socklen_t *)len);
}

inline int hpi::recvfrom(int fd, char *buf, int nBytes, int flags,
                         sockaddr *from, int *fromlen) {
  RESTARTABLE_RETURN_INT(::recvfrom(fd, buf, nBytes, (unsigned int) flags, from, (socklen_t *)fromlen));
}

inline int hpi::sendto(int fd, char *buf, int len, int flags,
                        struct sockaddr *to, int tolen) {
  RESTARTABLE_RETURN_INT(::sendto(fd, buf, len, (unsigned int) flags, to, tolen));
}

inline int hpi::socket_available(int fd, jint *pbytes) {
  // Linux doc says EINTR not returned, unlike Solaris
  int ret = ::ioctl(fd, FIONREAD, pbytes);

  //%% note ioctl can return 0 when successful, JVM_SocketAvailable
  // is expected to return 0 on failure and 1 on success to the jdk.
  return (ret < 0) ? 0 : 1;
}


// following methods have been updated to avoid problems in
// hpi's sockets calls based on sys_api_td.c (JDK1.3)

/*
HPIDECL(socket_shutdown, "socket_shutdown", _socket, SocketShutdown,
        int, "%d",
        (int fd, int howto),
        ("fd = %d, howto = %d", fd, howto),
        (fd, howto));
        */
inline int hpi::socket_shutdown(int fd, int howto){
  return ::shutdown(fd, howto);
}

/*
HPIDECL(bind, "bind", _socket, Bind,
        int, "%d",
        (int fd, struct sockaddr *him, int len),
        ("fd = %d, him = %p, len = %d",
         fd, him, len),
        (fd, him, len));
*/
inline int hpi::bind(int fd, struct sockaddr *him, int len){
  return ::bind(fd, him, len);
}

/*
HPIDECL(get_sock_name, "get_sock_name", _socket, GetSocketName,
        int, "%d",
        (int fd, struct sockaddr *him, int *len),
        ("fd = %d, him = %p, len = %p",
         fd, him, len),
        (fd, him, len));
        */
inline int hpi::get_sock_name(int fd, struct sockaddr *him, int *len){
  return ::getsockname(fd, him, (socklen_t *)len);
}

/*
HPIDECL(get_host_name, "get_host_name", _socket, GetHostName, int, "%d",
        (char *hostname, int namelen),
        ("hostname = %p, namelen = %d",
         hostname, namelen),
        (hostname, namelen));
        */
inline int hpi::get_host_name(char* name, int namelen){
  return ::gethostname(name, namelen);
}

/*
HPIDECL(get_sock_opt, "get_sock_opt", _socket, SocketGetOption, int, "%d",
        (int fd, int level, int optname, char *optval, int* optlen),
        ("fd = %d, level = %d, optname = %d, optval = %p, optlen = %p",
         fd, level, optname, optval, optlen),
        (fd, level, optname, optval, optlen));
        */
inline int hpi::get_sock_opt(int fd, int level, int optname,
                             char *optval, int* optlen){
  return ::getsockopt(fd, level, optname, optval, (socklen_t *)optlen);
}

/*
HPIDECL(set_sock_opt, "set_sock_opt", _socket, SocketSetOption, int, "%d",
        (int fd, int level, int optname, const char *optval, int optlen),
        ("fd = %d, level = %d, optname = %d, optval = %p, optlen = %d",
         fd, level, optname, optval, optlen),
        (fd, level, optname, optval, optlen));
        */
inline int hpi::set_sock_opt(int fd, int level, int optname,
                             const char *optval, int optlen){
  return ::setsockopt(fd, level, optname, optval, optlen);
}


// Reconciliation History
// hpi_solaris.hpp      1.9 99/08/30 16:31:23
// End
