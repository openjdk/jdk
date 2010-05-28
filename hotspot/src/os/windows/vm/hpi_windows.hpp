/*
 * Copyright (c) 1998, 2005, Oracle and/or its affiliates. All rights reserved.
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

// Win32 delegates these to the HPI.  Solaris provides its own
// implementation without using the HPI (for Interrupitble I/O).

// HPI_FileInterface

HPIDECL(close, "close", _file, Close, int, "%d",
        (int fd),
        ("fd = %d", fd),
        (fd));

HPIDECL(read, "read", _file, Read, size_t, "%ld",
        (int fd, void *buf, unsigned int nBytes),
        ("fd = %d, buf = %p, nBytes = %u", fd, buf, nBytes),
        (fd, buf, nBytes));

HPIDECL(write, "write", _file, Write, size_t, "%ld",
        (int fd, const void *buf, unsigned int nBytes),
        ("fd = %d, buf = %p, nBytes = %u", fd, buf, nBytes),
        (fd, buf, nBytes));


// HPI_SocketInterface

HPIDECL(socket_close, "socket_close", _socket, Close, int, "%d",
        (int fd),
        ("fd = %d", fd),
        (fd));

HPIDECL(socket_available, "socket_available", _socket, Available,
        int, "%d",
        (int fd, jint *pbytes),
        ("fd = %d, pbytes = %p", fd, pbytes),
        (fd, pbytes));

HPIDECL(socket, "socket", _socket, Socket, int, "%d",
        (int domain, int type, int protocol),
        ("domain = %d, type = %d, protocol = %d", domain, type, protocol),
        (domain, type, protocol));

HPIDECL(listen, "listen", _socket, Listen, int, "%d",
        (int fd, int count),
        ("fd = %d, count = %d", fd, count),
        (fd, count));

HPIDECL(connect, "connect", _socket, Connect, int, "%d",
        (int fd, struct sockaddr *him, int len),
        ("fd = %d, him = %p, len = %d", fd, him, len),
        (fd, him, len));

HPIDECL(accept, "accept", _socket, Accept, int, "%d",
        (int fd, struct sockaddr *him, int *len),
        ("fd = %d, him = %p, len = %p", fd, him, len),
        (fd, him, len));

HPIDECL(sendto, "sendto", _socket, SendTo, int, "%d",
        (int fd, char *buf, int len, int flags,
         struct sockaddr *to, int tolen),
        ("fd = %d, buf = %p, len = %d, flags = %d, to = %p, tolen = %d",
         fd, buf, len, flags, to, tolen),
        (fd, buf, len, flags, to, tolen));

HPIDECL(recvfrom, "recvfrom", _socket, RecvFrom, int, "%d",
        (int fd, char *buf, int nbytes, int flags,
         struct sockaddr *from, int *fromlen),
        ("fd = %d, buf = %p, len = %d, flags = %d, frm = %p, frmlen = %d",
         fd, buf, nbytes, flags, from, fromlen),
        (fd, buf, nbytes, flags, from, fromlen));

HPIDECL(recv, "recv", _socket, Recv, int, "%d",
        (int fd, char *buf, int nBytes, int flags),
        ("fd = %d, buf = %p, nBytes = %d, flags = %d",
         fd, buf, nBytes, flags),
        (fd, buf, nBytes, flags));

HPIDECL(send, "send", _socket, Send, int, "%d",
        (int fd, char *buf, int nBytes, int flags),
        ("fd = %d, buf = %p, nBytes = %d, flags = %d",
         fd, buf, nBytes, flags),
        (fd, buf, nBytes, flags));

inline int hpi::raw_send(int fd, char *buf, int nBytes, int flags) {
  return send(fd, buf, nBytes, flags);
}

HPIDECL(timeout, "timeout", _socket, Timeout, int, "%d",
        (int fd, long timeout),
        ("fd = %d, timeout = %ld", fd, timeout),
        (fd, timeout));

HPIDECL(get_host_by_name, "get_host_by_name", _socket, GetHostByName,
        struct hostent *, "(struct hostent *)%p",
        (char *name),
        ("%s", name),
        (name));

HPIDECL(socket_shutdown, "socket_shutdown", _socket, SocketShutdown,
        int, "%d",
        (int fd, int howto),
        ("fd = %d, howto = %d", fd, howto),
        (fd, howto));

HPIDECL(bind, "bind", _socket, Bind,
        int, "%d",
        (int fd, struct sockaddr *him, int len),
        ("fd = %d, him = %p, len = %d",
         fd, him, len),
        (fd, him, len));

HPIDECL(get_sock_name, "get_sock_name", _socket, GetSocketName,
        int, "%d",
        (int fd, struct sockaddr *him, int *len),
        ("fd = %d, him = %p, len = %p",
         fd, him, len),
        (fd, him, len));

HPIDECL(get_host_name, "get_host_name", _socket, GetHostName, int, "%d",
        (char *hostname, int namelen),
        ("hostname = %p, namelen = %d",
         hostname, namelen),
        (hostname, namelen));

HPIDECL(get_host_by_addr, "get_host_by_addr", _socket, GetHostByAddr,
        struct hostent *, "(struct hostent *)%p",
        (const char* name, int len, int type),
        ("name = %p, len = %d, type = %d",
         name, len, type),
        (name, len, type));

HPIDECL(get_sock_opt, "get_sock_opt", _socket, SocketGetOption, int, "%d",
        (int fd, int level, int optname, char *optval, int* optlen),
        ("fd = %d, level = %d, optname = %d, optval = %p, optlen = %p",
         fd, level, optname, optval, optlen),
        (fd, level, optname, optval, optlen));

HPIDECL(set_sock_opt, "set_sock_opt", _socket, SocketSetOption, int, "%d",
        (int fd, int level, int optname, const char *optval, int optlen),
        ("fd = %d, level = %d, optname = %d, optval = %p, optlen = %d",
         fd, level, optname, optval, optlen),
        (fd, level, optname, optval, optlen));

HPIDECL(get_proto_by_name, "get_proto_by_name", _socket, GetProtoByName,
        struct protoent *, "(struct protoent *)%p",
        (char* name),
        ("name = %p",
         name),
        (name));
