/*
 * Copyright (c) 1997, 2012, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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

#include <windows.h>
#include <winsock2.h>
#include <ctype.h>
#include <stdio.h>
#include <stdlib.h>
#include <malloc.h>
#include <sys/types.h>

#include "java_net_SocketOptions.h"
#include "java_net_TwoStacksPlainSocketImpl.h"
#include "java_net_InetAddress.h"
#include "java_io_FileDescriptor.h"
#include "java_lang_Integer.h"

#include "jvm.h"
#include "net_util.h"
#include "jni_util.h"

/************************************************************************
 * TwoStacksPlainSocketImpl
 */

static jfieldID IO_fd_fdID;

jfieldID psi_fdID;
jfieldID psi_fd1ID;
jfieldID psi_addressID;
jfieldID psi_portID;
jfieldID psi_localportID;
jfieldID psi_timeoutID;
jfieldID psi_trafficClassID;
jfieldID psi_serverSocketID;
jfieldID psi_lastfdID;

/*
 * the level of the TCP protocol for setsockopt and getsockopt
 * we only want to look this up once, from the static initializer
 * of TwoStacksPlainSocketImpl
 */
static int tcp_level = -1;

static int getFD(JNIEnv *env, jobject this) {
    jobject fdObj = (*env)->GetObjectField(env, this, psi_fdID);

    if (fdObj == NULL) {
        return -1;
    }
    return (*env)->GetIntField(env, fdObj, IO_fd_fdID);
}

static int getFD1(JNIEnv *env, jobject this) {
    jobject fdObj = (*env)->GetObjectField(env, this, psi_fd1ID);

    if (fdObj == NULL) {
        return -1;
    }
    return (*env)->GetIntField(env, fdObj, IO_fd_fdID);
}


/*
 * The initProto function is called whenever TwoStacksPlainSocketImpl is
 * loaded, to cache fieldIds for efficiency. This is called everytime
 * the Java class is loaded.
 *
 * Class:     java_net_TwoStacksPlainSocketImpl
 * Method:    initProto

 * Signature: ()V
 */
JNIEXPORT void JNICALL
Java_java_net_TwoStacksPlainSocketImpl_initProto(JNIEnv *env, jclass cls) {

    struct protoent *proto = getprotobyname("TCP");
    tcp_level = (proto == 0 ? IPPROTO_TCP: proto->p_proto);

    psi_fdID = (*env)->GetFieldID(env, cls , "fd", "Ljava/io/FileDescriptor;");
    CHECK_NULL(psi_fdID);
    psi_fd1ID =(*env)->GetFieldID(env, cls , "fd1", "Ljava/io/FileDescriptor;");
    CHECK_NULL(psi_fd1ID);
    psi_addressID = (*env)->GetFieldID(env, cls, "address",
                                          "Ljava/net/InetAddress;");
    CHECK_NULL(psi_addressID);
    psi_portID = (*env)->GetFieldID(env, cls, "port", "I");
    CHECK_NULL(psi_portID);
    psi_lastfdID = (*env)->GetFieldID(env, cls, "lastfd", "I");
    CHECK_NULL(psi_portID);
    psi_localportID = (*env)->GetFieldID(env, cls, "localport", "I");
    CHECK_NULL(psi_localportID);
    psi_timeoutID = (*env)->GetFieldID(env, cls, "timeout", "I");
    CHECK_NULL(psi_timeoutID);
    psi_trafficClassID = (*env)->GetFieldID(env, cls, "trafficClass", "I");
    CHECK_NULL(psi_trafficClassID);
    psi_serverSocketID = (*env)->GetFieldID(env, cls, "serverSocket",
                                            "Ljava/net/ServerSocket;");
    CHECK_NULL(psi_serverSocketID);
    IO_fd_fdID = NET_GetFileDescriptorID(env);
    CHECK_NULL(IO_fd_fdID);
}

/*
 * Class:     java_net_TwoStacksPlainSocketImpl
 * Method:    socketCreate
 * Signature: (Z)V
 */
JNIEXPORT void JNICALL
Java_java_net_TwoStacksPlainSocketImpl_socketCreate(JNIEnv *env, jobject this,
                                           jboolean stream) {
    jobject fdObj, fd1Obj;
    int fd, fd1;

    fdObj = (*env)->GetObjectField(env, this, psi_fdID);

    if (IS_NULL(fdObj)) {
        JNU_ThrowByName(env, JNU_JAVANETPKG "SocketException",
                        "null fd object");
        return;
    }
    fd = socket(AF_INET, (stream ? SOCK_STREAM: SOCK_DGRAM), 0);
    if (fd == -1) {
        NET_ThrowCurrent(env, "create");
        return;
    } else {
        /* Set socket attribute so it is not passed to any child process */
        SetHandleInformation((HANDLE)(UINT_PTR)fd, HANDLE_FLAG_INHERIT, FALSE);
        (*env)->SetIntField(env, fdObj, IO_fd_fdID, (int)fd);
    }
    if (ipv6_available()) {
        fd1Obj = (*env)->GetObjectField(env, this, psi_fd1ID);

        if (IS_NULL(fd1Obj)) {
            JNU_ThrowByName(env, JNU_JAVANETPKG "SocketException",
                            "null fd1 object");
            (*env)->SetIntField(env, fdObj, IO_fd_fdID, -1);
            NET_SocketClose(fd);
            return;
        }
        fd1 = socket(AF_INET6, (stream ? SOCK_STREAM: SOCK_DGRAM), 0);
        if (fd1 == -1) {
            NET_ThrowCurrent(env, "create");
            (*env)->SetIntField(env, fdObj, IO_fd_fdID, -1);
            NET_SocketClose(fd);
            return;
        } else {
            /* Set socket attribute so it is not passed to any child process */
            SetHandleInformation((HANDLE)(UINT_PTR)fd1, HANDLE_FLAG_INHERIT, FALSE);
            (*env)->SetIntField(env, fd1Obj, IO_fd_fdID, fd1);
        }
    } else {
        (*env)->SetObjectField(env, this, psi_fd1ID, NULL);
    }
}

/*
 * inetAddress is the address object passed to the socket connect
 * call.
 *
 * Class:     java_net_TwoStacksPlainSocketImpl
 * Method:    socketConnect
 * Signature: (Ljava/net/InetAddress;I)V
 */
JNIEXPORT void JNICALL
Java_java_net_TwoStacksPlainSocketImpl_socketConnect(JNIEnv *env, jobject this,
                                            jobject iaObj, jint port,
                                            jint timeout)
{
    jint localport = (*env)->GetIntField(env, this, psi_localportID);

    /* family and localport are int fields of iaObj */
    int family;
    jint fd, fd1=-1;
    jint len;
    int  ipv6_supported = ipv6_available();

    /* fd initially points to the IPv4 socket and fd1 to the IPv6 socket
     * If we want to connect to IPv6 then we swap the two sockets/objects
     * This way, fd is always the connected socket, and fd1 always gets closed.
     */
    jobject fdObj = (*env)->GetObjectField(env, this, psi_fdID);
    jobject fd1Obj = (*env)->GetObjectField(env, this, psi_fd1ID);

    SOCKETADDRESS him;

    /* The result of the connection */
    int connect_res;

    if (!IS_NULL(fdObj)) {
        fd = (*env)->GetIntField(env, fdObj, IO_fd_fdID);
    }

    if (ipv6_supported && !IS_NULL(fd1Obj)) {
        fd1 = (*env)->GetIntField(env, fd1Obj, IO_fd_fdID);
    }

    if (IS_NULL(iaObj)) {
        JNU_ThrowNullPointerException(env, "inet address argument is null.");
        return;
    }

    if (NET_InetAddressToSockaddr(env, iaObj, port, (struct sockaddr *)&him, &len, JNI_FALSE) != 0) {
      return;
    }

    family = him.him.sa_family;
    if (family == AF_INET6) {
        if (!ipv6_supported) {
            JNU_ThrowByName(env, JNU_JAVANETPKG "SocketException",
                            "Protocol family not supported");
            return;
        } else {
            if (fd1 == -1) {
                JNU_ThrowByName(env, JNU_JAVANETPKG "SocketException",
                                "Destination unreachable");
                return;
            }
            /* close the v4 socket, and set fd to be the v6 socket */
            (*env)->SetObjectField(env, this, psi_fdID, fd1Obj);
            (*env)->SetObjectField(env, this, psi_fd1ID, NULL);
            NET_SocketClose(fd);
            fd = fd1; fdObj = fd1Obj;
        }
    } else {
        if (fd1 != -1) {
            (*env)->SetIntField(env, fd1Obj, IO_fd_fdID, -1);
            NET_SocketClose(fd1);
        }
        if (fd == -1) {
            JNU_ThrowByName(env, JNU_JAVANETPKG "SocketException",
                            "Destination unreachable");
            return;
        }
    }
    (*env)->SetObjectField(env, this, psi_fd1ID, NULL);

    if (timeout <= 0) {
        connect_res = connect(fd, (struct sockaddr *) &him, SOCKETADDRESS_LEN(&him));
        if (connect_res == SOCKET_ERROR) {
            connect_res = WSAGetLastError();
        }
    } else {
        int optval;
        int optlen = sizeof(optval);

        /* make socket non-blocking */
        optval = 1;
        ioctlsocket( fd, FIONBIO, &optval );

        /* initiate the connect */
        connect_res = connect(fd, (struct sockaddr *) &him, SOCKETADDRESS_LEN(&him));
        if (connect_res == SOCKET_ERROR) {
            if (WSAGetLastError() != WSAEWOULDBLOCK) {
                connect_res = WSAGetLastError();
            } else {
                fd_set wr, ex;
                struct timeval t;

                FD_ZERO(&wr);
                FD_ZERO(&ex);
                FD_SET(fd, &wr);
                FD_SET(fd, &ex);
                t.tv_sec = timeout / 1000;
                t.tv_usec = (timeout % 1000) * 1000;

                /*
                 * Wait for timout, connection established or
                 * connection failed.
                 */
                connect_res = select(fd+1, 0, &wr, &ex, &t);

                /*
                 * Timeout before connection is established/failed so
                 * we throw exception and shutdown input/output to prevent
                 * socket from being used.
                 * The socket should be closed immediately by the caller.
                 */
                if (connect_res == 0) {
                    JNU_ThrowByName(env, JNU_JAVANETPKG "SocketTimeoutException",
                                    "connect timed out");
                    shutdown( fd, SD_BOTH );

                     /* make socket blocking again - just in case */
                    optval = 0;
                    ioctlsocket( fd, FIONBIO, &optval );
                    return;
                }

                /*
                 * We must now determine if the connection has been established
                 * or if it has failed. The logic here is designed to work around
                 * bug on Windows NT whereby using getsockopt to obtain the
                 * last error (SO_ERROR) indicates there is no error. The workaround
                 * on NT is to allow winsock to be scheduled and this is done by
                 * yielding and retrying. As yielding is problematic in heavy
                 * load conditions we attempt up to 3 times to get the error reason.
                 */
                if (!FD_ISSET(fd, &ex)) {
                    connect_res = 0;
                } else {
                    int retry;
                    for (retry=0; retry<3; retry++) {
                        NET_GetSockOpt(fd, SOL_SOCKET, SO_ERROR,
                                       (char*)&connect_res, &optlen);
                        if (connect_res) {
                            break;
                        }
                        Sleep(0);
                    }

                    if (connect_res == 0) {
                        JNU_ThrowByName(env, JNU_JAVANETPKG "SocketException",
                                        "Unable to establish connection");
                        return;
                    }
                }
            }
        }

        /* make socket blocking again */
        optval = 0;
        ioctlsocket(fd, FIONBIO, &optval);
    }

    if (connect_res) {
        if (connect_res == WSAEADDRNOTAVAIL) {
            JNU_ThrowByName(env, JNU_JAVANETPKG "ConnectException",
                "connect: Address is invalid on local machine, or port is not valid on remote machine");
        } else {
            NET_ThrowNew(env, connect_res, "connect");
        }
        return;
    }

    (*env)->SetIntField(env, fdObj, IO_fd_fdID, (int)fd);

    /* set the remote peer address and port */
    (*env)->SetObjectField(env, this, psi_addressID, iaObj);
    (*env)->SetIntField(env, this, psi_portID, port);

    /*
     * we need to initialize the local port field if bind was called
     * previously to the connect (by the client) then localport field
     * will already be initialized
     */
    if (localport == 0) {
        /* Now that we're a connected socket, let's extract the port number
         * that the system chose for us and store it in the Socket object.
         */
        u_short port;
        int len = SOCKETADDRESS_LEN(&him);
        if (getsockname(fd, (struct sockaddr *)&him, &len) == -1) {

            if (WSAGetLastError() == WSAENOTSOCK) {
                JNU_ThrowByName(env, JNU_JAVANETPKG "SocketException",
                        "Socket closed");
            } else {
                NET_ThrowCurrent(env, "getsockname failed");
            }
            return;
        }
        port = ntohs ((u_short)GET_PORT(&him));
        (*env)->SetIntField(env, this, psi_localportID, (int) port);
    }
}

/*
 * Class:     java_net_TwoStacksPlainSocketImpl
 * Method:    socketBind
 * Signature: (Ljava/net/InetAddress;I)V
 */
JNIEXPORT void JNICALL
Java_java_net_TwoStacksPlainSocketImpl_socketBind(JNIEnv *env, jobject this,
                                         jobject iaObj, jint localport,
                                         jboolean exclBind) {

    /* fdObj is the FileDescriptor field on this */
    jobject fdObj, fd1Obj;
    /* fd is an int field on fdObj */
    int fd, fd1, len;
    int ipv6_supported = ipv6_available();

    /* family is an int field of iaObj */
    int family;
    int rv;

    SOCKETADDRESS him;

    fdObj = (*env)->GetObjectField(env, this, psi_fdID);
    fd1Obj = (*env)->GetObjectField(env, this, psi_fd1ID);

    family = getInetAddress_family(env, iaObj);

    if (family == IPv6 && !ipv6_supported) {
        JNU_ThrowByName(env, JNU_JAVANETPKG "SocketException",
                        "Protocol family not supported");
        return;
    }

    if (IS_NULL(fdObj) || (ipv6_supported && IS_NULL(fd1Obj))) {
        JNU_ThrowByName(env, JNU_JAVANETPKG "SocketException",
                        "Socket closed");
        return;
    } else {
        fd = (*env)->GetIntField(env, fdObj, IO_fd_fdID);
        if (ipv6_supported) {
            fd1 = (*env)->GetIntField(env, fd1Obj, IO_fd_fdID);
        }
    }
    if (IS_NULL(iaObj)) {
        JNU_ThrowNullPointerException(env, "inet address argument");
        return;
    }

    if (NET_InetAddressToSockaddr(env, iaObj, localport,
                          (struct sockaddr *)&him, &len, JNI_FALSE) != 0) {
      return;
    }
    if (ipv6_supported) {
        struct ipv6bind v6bind;
        v6bind.addr = &him;
        v6bind.ipv4_fd = fd;
        v6bind.ipv6_fd = fd1;
        rv = NET_BindV6(&v6bind, exclBind);
        if (rv != -1) {
            /* check if the fds have changed */
            if (v6bind.ipv4_fd != fd) {
                fd = v6bind.ipv4_fd;
                if (fd == -1) {
                    /* socket is closed. */
                    (*env)->SetObjectField(env, this, psi_fdID, NULL);
                } else {
                    /* socket was re-created */
                    (*env)->SetIntField(env, fdObj, IO_fd_fdID, fd);
                }
            }
            if (v6bind.ipv6_fd != fd1) {
                fd1 = v6bind.ipv6_fd;
                if (fd1 == -1) {
                    /* socket is closed. */
                    (*env)->SetObjectField(env, this, psi_fd1ID, NULL);
                } else {
                    /* socket was re-created */
                    (*env)->SetIntField(env, fd1Obj, IO_fd_fdID, fd1);
                }
            }
        }
    } else {
        rv = NET_WinBind(fd, (struct sockaddr *)&him, len, exclBind);
    }

    if (rv == -1) {
        NET_ThrowCurrent(env, "JVM_Bind");
        return;
    }

    /* set the address */
    (*env)->SetObjectField(env, this, psi_addressID, iaObj);

    /* intialize the local port */
    if (localport == 0) {
        /* Now that we're a bound socket, let's extract the port number
         * that the system chose for us and store it in the Socket object.
         */
        int len = SOCKETADDRESS_LEN(&him);
        u_short port;
        fd = him.him.sa_family == AF_INET? fd: fd1;

        if (getsockname(fd, (struct sockaddr *)&him, &len) == -1) {
            NET_ThrowCurrent(env, "getsockname in plain socketBind");
            return;
        }
        port = ntohs ((u_short) GET_PORT (&him));

        (*env)->SetIntField(env, this, psi_localportID, (int) port);
    } else {
        (*env)->SetIntField(env, this, psi_localportID, localport);
    }
}

/*
 * Class:     java_net_TwoStacksPlainSocketImpl
 * Method:    socketListen
 * Signature: (I)V
 */
JNIEXPORT void JNICALL
Java_java_net_TwoStacksPlainSocketImpl_socketListen (JNIEnv *env, jobject this,
                                            jint count)
{
    /* this FileDescriptor fd field */
    jobject fdObj = (*env)->GetObjectField(env, this, psi_fdID);
    jobject fd1Obj = (*env)->GetObjectField(env, this, psi_fd1ID);
    jobject address;
    /* fdObj's int fd field */
    int fd, fd1;
    SOCKETADDRESS addr; int addrlen;

    if (IS_NULL(fdObj) && IS_NULL(fd1Obj)) {
        JNU_ThrowByName(env, JNU_JAVANETPKG "SocketException",
                        "socket closed");
        return;
    }

    if (!IS_NULL(fdObj)) {
        fd = (*env)->GetIntField(env, fdObj, IO_fd_fdID);
    }
    /* Listen on V4 if address type is v4 or if v6 and address is ::0.
     * Listen on V6 if address type is v6 or if v4 and address is 0.0.0.0.
     * In cases, where we listen on one space only, we close the other socket.
     */
    address = (*env)->GetObjectField(env, this, psi_addressID);
    if (IS_NULL(address)) {
        JNU_ThrowNullPointerException(env, "socket address");
        return;
    }
    if (NET_InetAddressToSockaddr(env, address, 0, (struct sockaddr *)&addr,
                                  &addrlen, JNI_FALSE) != 0) {
      return;
    }

    if (addr.him.sa_family == AF_INET || IN6ADDR_ISANY(&addr.him6)) {
        /* listen on v4 */
        if (listen(fd, count) == -1) {
            NET_ThrowCurrent(env, "listen failed");
        }
    } else {
        NET_SocketClose (fd);
        (*env)->SetObjectField(env, this, psi_fdID, NULL);
    }
    if (ipv6_available() && !IS_NULL(fd1Obj)) {
        fd1 = (*env)->GetIntField(env, fd1Obj, IO_fd_fdID);
        if (addr.him.sa_family == AF_INET6 || addr.him4.sin_addr.s_addr == INADDR_ANY) {
            /* listen on v6 */
            if (listen(fd1, count) == -1) {
                NET_ThrowCurrent(env, "listen failed");
            }
        } else {
            NET_SocketClose (fd1);
            (*env)->SetObjectField(env, this, psi_fd1ID, NULL);
        }
    }
}

/*
 * Class:     java_net_TwoStacksPlainSocketImpl
 * Method:    socketAccept
 * Signature: (Ljava/net/SocketImpl;)V
 */
JNIEXPORT void JNICALL
Java_java_net_TwoStacksPlainSocketImpl_socketAccept(JNIEnv *env, jobject this,
                                           jobject socket)
{
    /* fields on this */
    jint port;
    jint scope;
    jint timeout = (*env)->GetIntField(env, this, psi_timeoutID);
    jobject fdObj = (*env)->GetObjectField(env, this, psi_fdID);
    jobject fd1Obj = (*env)->GetObjectField(env, this, psi_fd1ID);

    /* the FileDescriptor field on socket */
    jobject socketFdObj;

    /* cache the Inet4/6Address classes */
    static jclass inet4Cls;
    static jclass inet6Cls;

    /* the InetAddress field on socket */
    jobject socketAddressObj;

    /* the fd int field on fdObj */
    jint fd=-1, fd1=-1;

    SOCKETADDRESS him;
    jint len;

    if (IS_NULL(fdObj) && IS_NULL(fd1Obj)) {
        JNU_ThrowByName(env, JNU_JAVANETPKG "SocketException",
                        "Socket closed");
        return;
    }
    if (!IS_NULL(fdObj)) {
        fd = (*env)->GetIntField(env, fdObj, IO_fd_fdID);
    }
    if (!IS_NULL(fd1Obj)) {
        fd1 = (*env)->GetIntField(env, fd1Obj, IO_fd_fdID);
    }
    if (IS_NULL(socket)) {
        JNU_ThrowNullPointerException(env, "socket is null");
        return;
    } else {
        socketFdObj = (*env)->GetObjectField(env, socket, psi_fdID);
        socketAddressObj = (*env)->GetObjectField(env, socket, psi_addressID);
    }
    if ((IS_NULL(socketAddressObj)) || (IS_NULL(socketFdObj))) {
        JNU_ThrowNullPointerException(env, "socket address or fd obj");
        return;
    }
    if (fd != -1 && fd1 != -1) {
        fd_set rfds;
        struct timeval t, *tP=&t;
        int lastfd, res, fd2;
        FD_ZERO(&rfds);
        FD_SET(fd,&rfds);
        FD_SET(fd1,&rfds);
        if (timeout) {
            t.tv_sec = timeout/1000;
            t.tv_usec = (timeout%1000)*1000;
        } else {
            tP = NULL;
        }
        res = select (fd, &rfds, NULL, NULL, tP);
        if (res == 0) {
            JNU_ThrowByName(env, JNU_JAVANETPKG "SocketTimeoutException",
                            "Accept timed out");
            return;
        } else if (res == 1) {
            fd2 = FD_ISSET(fd, &rfds)? fd: fd1;
        } else if (res == 2) {
            /* avoid starvation */
            lastfd = (*env)->GetIntField(env, this, psi_lastfdID);
            if (lastfd != -1) {
                fd2 = lastfd==fd? fd1: fd;
            } else {
                fd2 = fd;
            }
            (*env)->SetIntField(env, this, psi_lastfdID, fd2);
        } else {
            JNU_ThrowByName(env, JNU_JAVANETPKG "SocketException",
                            "select failed");
            return;
        }
        if (fd2 == fd) { /* v4 */
            len = sizeof (struct sockaddr_in);
        } else {
            len = sizeof (struct SOCKADDR_IN6);
        }
        fd = fd2;
    } else {
        int ret;
        if (fd1 != -1) {
            fd = fd1;
            len = sizeof (struct SOCKADDR_IN6);
        } else {
            len = sizeof (struct sockaddr_in);
        }
        if (timeout) {
            ret = NET_Timeout(fd, timeout);
            if (ret == 0) {
                JNU_ThrowByName(env, JNU_JAVANETPKG "SocketTimeoutException",
                                "Accept timed out");
                return;
            } else if (ret == -1) {
                JNU_ThrowByName(env, JNU_JAVANETPKG "SocketException", "socket closed");
            /* REMIND: SOCKET CLOSED PROBLEM */
    /*        NET_ThrowCurrent(env, "Accept failed"); */
                return;
            } else if (ret == -2) {
                JNU_ThrowByName(env, JNU_JAVAIOPKG "InterruptedIOException",
                                "operation interrupted");
                return;
            }
        }
    }
    fd = accept(fd, (struct sockaddr *)&him, &len);
    if (fd < 0) {
        /* REMIND: SOCKET CLOSED PROBLEM */
        if (fd == -2) {
            JNU_ThrowByName(env, JNU_JAVAIOPKG "InterruptedIOException",
                            "operation interrupted");
        } else {
            JNU_ThrowByName(env, JNU_JAVANETPKG "SocketException",
                            "socket closed");
        }
        return;
    }
    (*env)->SetIntField(env, socketFdObj, IO_fd_fdID, fd);

    if (him.him.sa_family == AF_INET) {
        if (inet4Cls == NULL) {
            jclass c = (*env)->FindClass(env, "java/net/Inet4Address");
            if (c != NULL) {
                inet4Cls = (*env)->NewGlobalRef(env, c);
                (*env)->DeleteLocalRef(env, c);
            }
        }

        /*
         * fill up the remote peer port and address in the new socket structure
         */
        if (inet4Cls != NULL) {
            socketAddressObj = (*env)->NewObject(env, inet4Cls, ia4_ctrID);
        } else {
            socketAddressObj = NULL;
        }
        if (socketAddressObj == NULL) {
            /*
             * FindClass or NewObject failed so close connection and
             * exist (there will be a pending exception).
             */
            NET_SocketClose(fd);
            return;
        }

        setInetAddress_addr(env, socketAddressObj, ntohl(him.him4.sin_addr.s_addr));
        setInetAddress_family(env, socketAddressObj, IPv4);
        (*env)->SetObjectField(env, socket, psi_addressID, socketAddressObj);
    } else {
        jbyteArray addr;
        /* AF_INET6 -> Inet6Address */
        if (inet6Cls == 0) {
            jclass c = (*env)->FindClass(env, "java/net/Inet6Address");
            if (c != NULL) {
                inet6Cls = (*env)->NewGlobalRef(env, c);
                (*env)->DeleteLocalRef(env, c);
            }
        }

        if (inet6Cls != NULL) {
            socketAddressObj = (*env)->NewObject(env, inet6Cls, ia6_ctrID);
        } else {
            socketAddressObj = NULL;
        }
        if (socketAddressObj == NULL) {
            /*
             * FindClass or NewObject failed so close connection and
             * exist (there will be a pending exception).
             */
            NET_SocketClose(fd);
            return;
        }
        addr = (*env)->GetObjectField (env, socketAddressObj, ia6_ipaddressID);
        (*env)->SetByteArrayRegion (env, addr, 0, 16, (const char *)&him.him6.sin6_addr);
        setInetAddress_family(env, socketAddressObj, IPv6);
        scope = him.him6.sin6_scope_id;
        (*env)->SetIntField(env, socketAddressObj, ia6_scopeidID, scope);
        if(scope>0) {
            (*env)->SetBooleanField(env, socketAddressObj, ia6_scopeidsetID, JNI_TRUE);
        }
    }
    /* fields common to AF_INET and AF_INET6 */

    port = ntohs ((u_short) GET_PORT (&him));
    (*env)->SetIntField(env, socket, psi_portID, (int)port);
    port = (*env)->GetIntField(env, this, psi_localportID);
    (*env)->SetIntField(env, socket, psi_localportID, port);
    (*env)->SetObjectField(env, socket, psi_addressID, socketAddressObj);
}

/*
 * Class:     java_net_TwoStacksPlainSocketImpl
 * Method:    socketAvailable
 * Signature: ()I
 */
JNIEXPORT jint JNICALL
Java_java_net_TwoStacksPlainSocketImpl_socketAvailable(JNIEnv *env, jobject this) {

    jint available = -1;
    jint res;
    jobject fdObj = (*env)->GetObjectField(env, this, psi_fdID);
    jint fd;

    if (IS_NULL(fdObj)) {
        JNU_ThrowByName(env, JNU_JAVANETPKG "SocketException", "Socket closed");
        return -1;
    } else {
        fd = (*env)->GetIntField(env, fdObj, IO_fd_fdID);
    }
    res = ioctlsocket(fd, FIONREAD, &available);
    /* if result isn't 0, it means an error */
    if (res != 0) {
        NET_ThrowNew(env, res, "socket available");
    }
    return available;
}

/*
 * Class:     java_net_TwoStacksPlainSocketImpl
 * Method:    socketClose
 * Signature: ()V
 */
JNIEXPORT void JNICALL
Java_java_net_TwoStacksPlainSocketImpl_socketClose0(JNIEnv *env, jobject this,
                                           jboolean useDeferredClose) {

    jobject fdObj = (*env)->GetObjectField(env, this, psi_fdID);
    jobject fd1Obj = (*env)->GetObjectField(env, this, psi_fd1ID);
    jint fd=-1, fd1=-1;

    if (IS_NULL(fdObj) && IS_NULL(fd1Obj)) {
        JNU_ThrowByName(env, JNU_JAVANETPKG "SocketException",
                        "socket already closed");
        return;
    }
    if (!IS_NULL(fdObj)) {
        fd = (*env)->GetIntField(env, fdObj, IO_fd_fdID);
    }
    if (!IS_NULL(fd1Obj)) {
        fd1 = (*env)->GetIntField(env, fd1Obj, IO_fd_fdID);
    }
    if (fd != -1) {
        (*env)->SetIntField(env, fdObj, IO_fd_fdID, -1);
        NET_SocketClose(fd);
    }
    if (fd1 != -1) {
        (*env)->SetIntField(env, fd1Obj, IO_fd_fdID, -1);
        NET_SocketClose(fd1);
    }
}

/*
 * Socket options for plainsocketImpl
 *
 *
 * Class:     java_net_TwoStacksPlainSocketImpl
 * Method:    socketNativeSetOption
 * Signature: (IZLjava/lang/Object;)V
 */
JNIEXPORT void JNICALL
Java_java_net_TwoStacksPlainSocketImpl_socketNativeSetOption(JNIEnv *env,
                                              jobject this,
                                              jint cmd, jboolean on,
                                              jobject value) {
    int fd, fd1;
    int level, optname, optlen;
    union {
        int i;
        struct linger ling;
    } optval;

    /*
     * Get SOCKET and check that it hasn't been closed
     */
    fd = getFD(env, this);
    fd1 = getFD1(env, this);
    if (fd < 0 && fd1 < 0) {
        JNU_ThrowByName(env, JNU_JAVANETPKG "SocketException", "Socket closed");
        return;
    }

    /*
     * SO_TIMEOUT is the socket option used to specify the timeout
     * for ServerSocket.accept and Socket.getInputStream().read.
     * It does not typically map to a native level socket option.
     * For Windows we special-case this and use the SOL_SOCKET/SO_RCVTIMEO
     * socket option to specify a receive timeout on the socket. This
     * receive timeout is applicable to Socket only and the socket
     * option should not be set on ServerSocket.
     */
    if (cmd == java_net_SocketOptions_SO_TIMEOUT) {

        /*
         * Don't enable the socket option on ServerSocket as it's
         * meaningless (we don't receive on a ServerSocket).
         */
        jobject ssObj = (*env)->GetObjectField(env, this, psi_serverSocketID);
        if (ssObj != NULL) {
            return;
        }

        /*
         * SO_RCVTIMEO is only supported on Microsoft's implementation
         * of Windows Sockets so if WSAENOPROTOOPT returned then
         * reset flag and timeout will be implemented using
         * select() -- see SocketInputStream.socketRead.
         */
        if (isRcvTimeoutSupported) {
            jclass iCls = (*env)->FindClass(env, "java/lang/Integer");
            jfieldID i_valueID;
            jint timeout;

            CHECK_NULL(iCls);
            i_valueID = (*env)->GetFieldID(env, iCls, "value", "I");
            CHECK_NULL(i_valueID);
            timeout = (*env)->GetIntField(env, value, i_valueID);

            /*
             * Disable SO_RCVTIMEO if timeout is <= 5 second.
             */
            if (timeout <= 5000) {
                timeout = 0;
            }

            if (setsockopt(fd, SOL_SOCKET, SO_RCVTIMEO, (char *)&timeout,
                sizeof(timeout)) < 0) {
                if (WSAGetLastError() == WSAENOPROTOOPT) {
                    isRcvTimeoutSupported = JNI_FALSE;
                } else {
                    NET_ThrowCurrent(env, "setsockopt SO_RCVTIMEO");
                }
            }
            if (fd1 != -1) {
                if (setsockopt(fd1, SOL_SOCKET, SO_RCVTIMEO, (char *)&timeout,
                                        sizeof(timeout)) < 0) {
                    NET_ThrowCurrent(env, "setsockopt SO_RCVTIMEO");
                }
            }
        }
        return;
    }

    /*
     * Map the Java level socket option to the platform specific
     * level
     */
    if (NET_MapSocketOption(cmd, &level, &optname)) {
        JNU_ThrowByName(env, JNU_JAVANETPKG "SocketException",
                        "Invalid option");
        return;
    }

    switch (cmd) {

        case java_net_SocketOptions_TCP_NODELAY :
        case java_net_SocketOptions_SO_OOBINLINE :
        case java_net_SocketOptions_SO_KEEPALIVE :
        case java_net_SocketOptions_SO_REUSEADDR :
            optval.i = (on ? 1 : 0);
            optlen = sizeof(optval.i);
            break;

        case java_net_SocketOptions_SO_SNDBUF :
        case java_net_SocketOptions_SO_RCVBUF :
        case java_net_SocketOptions_IP_TOS :
            {
                jclass cls;
                jfieldID fid;

                cls = (*env)->FindClass(env, "java/lang/Integer");
                CHECK_NULL(cls);
                fid = (*env)->GetFieldID(env, cls, "value", "I");
                CHECK_NULL(fid);

                optval.i = (*env)->GetIntField(env, value, fid);
                optlen = sizeof(optval.i);
            }
            break;

        case java_net_SocketOptions_SO_LINGER :
            {
                jclass cls;
                jfieldID fid;

                cls = (*env)->FindClass(env, "java/lang/Integer");
                CHECK_NULL(cls);
                fid = (*env)->GetFieldID(env, cls, "value", "I");
                CHECK_NULL(fid);

                if (on) {
                    optval.ling.l_onoff = 1;
                    optval.ling.l_linger =
                        (unsigned short)(*env)->GetIntField(env, value, fid);
                } else {
                    optval.ling.l_onoff = 0;
                    optval.ling.l_linger = 0;
                }
                optlen = sizeof(optval.ling);
            }
            break;

        default: /* shouldn't get here */
            JNU_ThrowByName(env, JNU_JAVANETPKG "SocketException",
                "Option not supported by TwoStacksPlainSocketImpl");
            return;
    }

    if (fd != -1) {
        if (NET_SetSockOpt(fd, level, optname, (void *)&optval, optlen) < 0) {
            NET_ThrowCurrent(env, "setsockopt");
        }
    }

    if (fd1 != -1) {
        if (NET_SetSockOpt(fd1, level, optname, (void *)&optval, optlen) < 0) {
            NET_ThrowCurrent(env, "setsockopt");
        }
    }
}


/*
 * Class:     java_net_TwoStacksPlainSocketImpl
 * Method:    socketGetOption
 * Signature: (I)I
 */
JNIEXPORT jint JNICALL
Java_java_net_TwoStacksPlainSocketImpl_socketGetOption(JNIEnv *env, jobject this,
                                              jint opt, jobject iaContainerObj) {

    int fd, fd1;
    int level, optname, optlen;
    union {
        int i;
        struct linger ling;
    } optval;

    /*
     * Get SOCKET and check it hasn't been closed
     */
    fd = getFD(env, this);
    fd1 = getFD1(env, this);

    if (fd < 0 && fd1 < 0) {
        JNU_ThrowByName(env, JNU_JAVANETPKG "SocketException", "Socket closed");
        return -1;
    }
    if (fd < 0) {
        fd = fd1;
    }

    /* For IPv6, we assume both sockets have the same setting always */

    /*
     * SO_BINDADDR isn't a socket option
     */
    if (opt == java_net_SocketOptions_SO_BINDADDR) {
        SOCKETADDRESS him;
        int len;
        int port;
        jobject iaObj;
        jclass iaCntrClass;
        jfieldID iaFieldID;

        len = sizeof(him);

        if (fd == -1) {
            /* must be an IPV6 only socket. Case where both sockets are != -1
             * is handled in java
             */
            fd = getFD1 (env, this);
        }

        if (getsockname(fd, (struct sockaddr *)&him, &len) < 0) {
            NET_ThrowByNameWithLastError(env, JNU_JAVANETPKG "SocketException",
                             "Error getting socket name");
            return -1;
        }
        iaObj = NET_SockaddrToInetAddress(env, (struct sockaddr *)&him, &port);
        CHECK_NULL_RETURN(iaObj, -1);

        iaCntrClass = (*env)->GetObjectClass(env, iaContainerObj);
        iaFieldID = (*env)->GetFieldID(env, iaCntrClass, "addr", "Ljava/net/InetAddress;");
        CHECK_NULL_RETURN(iaFieldID, -1);
        (*env)->SetObjectField(env, iaContainerObj, iaFieldID, iaObj);
        return 0; /* notice change from before */
    }

    /*
     * Map the Java level socket option to the platform specific
     * level and option name.
     */
    if (NET_MapSocketOption(opt, &level, &optname)) {
        JNU_ThrowByName(env, JNU_JAVANETPKG "SocketException", "Invalid option");
        return -1;
    }

    /*
     * Args are int except for SO_LINGER
     */
    if (opt == java_net_SocketOptions_SO_LINGER) {
        optlen = sizeof(optval.ling);
    } else {
        optlen = sizeof(optval.i);
        optval.i = 0;
    }

    if (NET_GetSockOpt(fd, level, optname, (void *)&optval, &optlen) < 0) {
        NET_ThrowCurrent(env, "getsockopt");
        return -1;
    }

    switch (opt) {
        case java_net_SocketOptions_SO_LINGER:
            return (optval.ling.l_onoff ? optval.ling.l_linger: -1);

        case java_net_SocketOptions_SO_SNDBUF:
        case java_net_SocketOptions_SO_RCVBUF:
        case java_net_SocketOptions_IP_TOS:
            return optval.i;

        case java_net_SocketOptions_TCP_NODELAY :
        case java_net_SocketOptions_SO_OOBINLINE :
        case java_net_SocketOptions_SO_KEEPALIVE :
        case java_net_SocketOptions_SO_REUSEADDR :
            return (optval.i == 0) ? -1 : 1;

        default: /* shouldn't get here */
            JNU_ThrowByName(env, JNU_JAVANETPKG "SocketException",
                "Option not supported by TwoStacksPlainSocketImpl");
            return -1;
    }
}

/*
 * Class:     java_net_TwoStacksPlainSocketImpl
 * Method:    socketShutdown
 * Signature: (I)V
 */
JNIEXPORT void JNICALL
Java_java_net_TwoStacksPlainSocketImpl_socketShutdown(JNIEnv *env, jobject this,
                                             jint howto)
{

    jobject fdObj = (*env)->GetObjectField(env, this, psi_fdID);
    jint fd;

    /*
     * WARNING: THIS NEEDS LOCKING. ALSO: SHOULD WE CHECK for fd being
     * -1 already?
     */
    if (IS_NULL(fdObj)) {
        JNU_ThrowByName(env, JNU_JAVANETPKG "SocketException",
                        "socket already closed");
        return;
    } else {
        fd = (*env)->GetIntField(env, fdObj, IO_fd_fdID);
    }
    shutdown(fd, howto);
}

/*
 * Class:     java_net_TwoStacksPlainSocketImpl
 * Method:    socketSendUrgentData
 * Signature: (B)V
 */
JNIEXPORT void JNICALL
Java_java_net_TwoStacksPlainSocketImpl_socketSendUrgentData(JNIEnv *env, jobject this,
                                             jint data) {
    /* The fd field */
    jobject fdObj = (*env)->GetObjectField(env, this, psi_fdID);
    int n, fd;
    unsigned char d = data & 0xff;

    if (IS_NULL(fdObj)) {
        JNU_ThrowByName(env, "java/net/SocketException", "Socket closed");
        return;
    } else {
        fd = (*env)->GetIntField(env, fdObj, IO_fd_fdID);
        /* Bug 4086704 - If the Socket associated with this file descriptor
         * was closed (sysCloseFD), the the file descriptor is set to -1.
         */
        if (fd == -1) {
            JNU_ThrowByName(env, "java/net/SocketException", "Socket closed");
            return;
        }

    }
    n = send(fd, (char *)&data, 1, MSG_OOB);
    if (n == JVM_IO_ERR) {
        NET_ThrowCurrent(env, "send");
        return;
    }
    if (n == JVM_IO_INTR) {
        JNU_ThrowByName(env, "java/io/InterruptedIOException", 0);
        return;
    }
}
