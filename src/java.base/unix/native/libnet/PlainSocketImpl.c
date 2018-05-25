/*
 * Copyright (c) 1997, 2018, Oracle and/or its affiliates. All rights reserved.
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
#include <errno.h>

#include "jvm.h"
#include "net_util.h"

#include "java_net_SocketOptions.h"
#include "java_net_PlainSocketImpl.h"

/************************************************************************
 * PlainSocketImpl
 */

static jfieldID IO_fd_fdID;

jfieldID psi_fdID;
jfieldID psi_addressID;
jfieldID psi_ipaddressID;
jfieldID psi_portID;
jfieldID psi_localportID;
jfieldID psi_timeoutID;
jfieldID psi_trafficClassID;
jfieldID psi_serverSocketID;
jfieldID psi_fdLockID;
jfieldID psi_closePendingID;

extern void setDefaultScopeID(JNIEnv *env, struct sockaddr *him);

/*
 * file descriptor used for dup2
 */
static int marker_fd = -1;


#define SET_NONBLOCKING(fd) {           \
        int flags = fcntl(fd, F_GETFL); \
        flags |= O_NONBLOCK;            \
        fcntl(fd, F_SETFL, flags);      \
}

#define SET_BLOCKING(fd) {              \
        int flags = fcntl(fd, F_GETFL); \
        flags &= ~O_NONBLOCK;           \
        fcntl(fd, F_SETFL, flags);      \
}

/*
 * Create the marker file descriptor by establishing a loopback connection
 * which we shutdown but do not close the fd. The result is an fd that
 * can be used for read/write.
 */
static int getMarkerFD()
{
    int sv[2];

#ifdef AF_UNIX
    if (socketpair(AF_UNIX, SOCK_STREAM, 0, sv) == -1) {
        return -1;
    }
#else
    return -1;
#endif

    /*
     * Finally shutdown sv[0] (any reads to this fd will get
     * EOF; any writes will get an error).
     */
    shutdown(sv[0], 2);
    close(sv[1]);

    return sv[0];
}

/*
 * Return the file descriptor given a PlainSocketImpl
 */
static int getFD(JNIEnv *env, jobject this) {
    jobject fdObj = (*env)->GetObjectField(env, this, psi_fdID);
    CHECK_NULL_RETURN(fdObj, -1);
    return (*env)->GetIntField(env, fdObj, IO_fd_fdID);
}

/*
 * The initroto function is called whenever PlainSocketImpl is
 * loaded, to cache field IDs for efficiency. This is called every time
 * the Java class is loaded.
 *
 * Class:     java_net_PlainSocketImpl
 * Method:    initProto
 * Signature: ()V
 */
JNIEXPORT void JNICALL
Java_java_net_PlainSocketImpl_initProto(JNIEnv *env, jclass cls) {
    psi_fdID = (*env)->GetFieldID(env, cls , "fd",
                                  "Ljava/io/FileDescriptor;");
    CHECK_NULL(psi_fdID);
    psi_addressID = (*env)->GetFieldID(env, cls, "address",
                                          "Ljava/net/InetAddress;");
    CHECK_NULL(psi_addressID);
    psi_portID = (*env)->GetFieldID(env, cls, "port", "I");
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
    psi_fdLockID = (*env)->GetFieldID(env, cls, "fdLock",
                                      "Ljava/lang/Object;");
    CHECK_NULL(psi_fdLockID);
    psi_closePendingID = (*env)->GetFieldID(env, cls, "closePending", "Z");
    CHECK_NULL(psi_closePendingID);
    IO_fd_fdID = NET_GetFileDescriptorID(env);
    CHECK_NULL(IO_fd_fdID);

    initInetAddressIDs(env);
    JNU_CHECK_EXCEPTION(env);

    /* Create the marker fd used for dup2 */
    marker_fd = getMarkerFD();
}

/* a global reference to the java.net.SocketException class. In
 * socketCreate, we ensure that this is initialized. This is to
 * prevent the problem where socketCreate runs out of file
 * descriptors, and is then unable to load the exception class.
 */
static jclass socketExceptionCls;

/*
 * Class:     java_net_PlainSocketImpl
 * Method:    socketCreate
 * Signature: (Z)V */
JNIEXPORT void JNICALL
Java_java_net_PlainSocketImpl_socketCreate(JNIEnv *env, jobject this,
                                           jboolean stream) {
    jobject fdObj, ssObj;
    int fd;
    int type = (stream ? SOCK_STREAM : SOCK_DGRAM);
    int domain = ipv6_available() ? AF_INET6 : AF_INET;

    if (socketExceptionCls == NULL) {
        jclass c = (*env)->FindClass(env, "java/net/SocketException");
        CHECK_NULL(c);
        socketExceptionCls = (jclass)(*env)->NewGlobalRef(env, c);
        CHECK_NULL(socketExceptionCls);
    }
    fdObj = (*env)->GetObjectField(env, this, psi_fdID);

    if (fdObj == NULL) {
        (*env)->ThrowNew(env, socketExceptionCls, "null fd object");
        return;
    }

    if ((fd = socket(domain, type, 0)) == -1) {
        /* note: if you run out of fds, you may not be able to load
         * the exception class, and get a NoClassDefFoundError
         * instead.
         */
        NET_ThrowNew(env, errno, "can't create socket");
        return;
    }

    /* Disable IPV6_V6ONLY to ensure dual-socket support */
    if (domain == AF_INET6) {
        int arg = 0;
        if (setsockopt(fd, IPPROTO_IPV6, IPV6_V6ONLY, (char*)&arg,
                       sizeof(int)) < 0) {
            NET_ThrowNew(env, errno, "cannot set IPPROTO_IPV6");
            close(fd);
            return;
        }
    }

    /*
     * If this is a server socket then enable SO_REUSEADDR
     * automatically and set to non blocking.
     */
    ssObj = (*env)->GetObjectField(env, this, psi_serverSocketID);
    if (ssObj != NULL) {
        int arg = 1;
        SET_NONBLOCKING(fd);
        if (NET_SetSockOpt(fd, SOL_SOCKET, SO_REUSEADDR, (char*)&arg,
                       sizeof(arg)) < 0) {
            NET_ThrowNew(env, errno, "cannot set SO_REUSEADDR");
            close(fd);
            return;
        }
    }

    (*env)->SetIntField(env, fdObj, IO_fd_fdID, fd);
}

/*
 * inetAddress is the address object passed to the socket connect
 * call.
 *
 * Class:     java_net_PlainSocketImpl
 * Method:    socketConnect
 * Signature: (Ljava/net/InetAddress;I)V
 */
JNIEXPORT void JNICALL
Java_java_net_PlainSocketImpl_socketConnect(JNIEnv *env, jobject this,
                                            jobject iaObj, jint port,
                                            jint timeout)
{
    jint localport = (*env)->GetIntField(env, this, psi_localportID);
    int len = 0;
    /* fdObj is the FileDescriptor field on this */
    jobject fdObj = (*env)->GetObjectField(env, this, psi_fdID);

    jclass clazz = (*env)->GetObjectClass(env, this);

    jobject fdLock;

    jint trafficClass = (*env)->GetIntField(env, this, psi_trafficClassID);

    /* fd is an int field on iaObj */
    jint fd;

    SOCKETADDRESS sa;
    /* The result of the connection */
    int connect_rv = -1;

    if (IS_NULL(fdObj)) {
        JNU_ThrowByName(env, JNU_JAVANETPKG "SocketException", "Socket closed");
        return;
    } else {
        fd = (*env)->GetIntField(env, fdObj, IO_fd_fdID);
    }
    if (IS_NULL(iaObj)) {
        JNU_ThrowNullPointerException(env, "inet address argument null.");
        return;
    }

    /* connect */
    if (NET_InetAddressToSockaddr(env, iaObj, port, &sa, &len,
                                  JNI_TRUE) != 0) {
        return;
    }
    setDefaultScopeID(env, &sa.sa);

    if (trafficClass != 0 && ipv6_available()) {
        NET_SetTrafficClass(&sa, trafficClass);
    }

    if (timeout <= 0) {
        connect_rv = NET_Connect(fd, &sa.sa, len);
#ifdef __solaris__
        if (connect_rv == -1 && errno == EINPROGRESS ) {

            /* This can happen if a blocking connect is interrupted by a signal.
             * See 6343810.
             */
            while (1) {
                struct pollfd pfd;
                pfd.fd = fd;
                pfd.events = POLLOUT;

                connect_rv = NET_Poll(&pfd, 1, -1);

                if (connect_rv == -1) {
                    if (errno == EINTR) {
                        continue;
                    } else {
                        break;
                    }
                }
                if (connect_rv > 0) {
                    socklen_t optlen;
                    /* has connection been established */
                    optlen = sizeof(connect_rv);
                    if (getsockopt(fd, SOL_SOCKET, SO_ERROR,
                                   (void*)&connect_rv, &optlen) <0) {
                        connect_rv = errno;
                    }

                    if (connect_rv != 0) {
                        /* restore errno */
                        errno = connect_rv;
                        connect_rv = -1;
                    }
                    break;
                }
            }
        }
#endif
    } else {
        /*
         * A timeout was specified. We put the socket into non-blocking
         * mode, connect, and then wait for the connection to be
         * established, fail, or timeout.
         */
        SET_NONBLOCKING(fd);

        /* no need to use NET_Connect as non-blocking */
        connect_rv = connect(fd, &sa.sa, len);

        /* connection not established immediately */
        if (connect_rv != 0) {
            socklen_t optlen;
            jlong nanoTimeout = (jlong) timeout * NET_NSEC_PER_MSEC;
            jlong prevNanoTime = JVM_NanoTime(env, 0);

            if (errno != EINPROGRESS) {
                NET_ThrowByNameWithLastError(env, JNU_JAVANETPKG "ConnectException",
                             "connect failed");
                SET_BLOCKING(fd);
                return;
            }

            /*
             * Wait for the connection to be established or a
             * timeout occurs. poll needs to handle EINTR in
             * case lwp sig handler redirects any process signals to
             * this thread.
             */
            while (1) {
                jlong newNanoTime;
                struct pollfd pfd;
                pfd.fd = fd;
                pfd.events = POLLOUT;

                errno = 0;
                connect_rv = NET_Poll(&pfd, 1, nanoTimeout / NET_NSEC_PER_MSEC);

                if (connect_rv >= 0) {
                    break;
                }
                if (errno != EINTR) {
                    break;
                }

                /*
                 * The poll was interrupted so adjust timeout and
                 * restart
                 */
                newNanoTime = JVM_NanoTime(env, 0);
                nanoTimeout -= (newNanoTime - prevNanoTime);
                if (nanoTimeout < NET_NSEC_PER_MSEC) {
                    connect_rv = 0;
                    break;
                }
                prevNanoTime = newNanoTime;

            } /* while */

            if (connect_rv == 0) {
                JNU_ThrowByName(env, JNU_JAVANETPKG "SocketTimeoutException",
                            "connect timed out");

                /*
                 * Timeout out but connection may still be established.
                 * At the high level it should be closed immediately but
                 * just in case we make the socket blocking again and
                 * shutdown input & output.
                 */
                SET_BLOCKING(fd);
                shutdown(fd, 2);
                return;
            }

            /* has connection been established */
            optlen = sizeof(connect_rv);
            if (getsockopt(fd, SOL_SOCKET, SO_ERROR, (void*)&connect_rv,
                           &optlen) <0) {
                connect_rv = errno;
            }
        }

        /* make socket blocking again */
        SET_BLOCKING(fd);

        /* restore errno */
        if (connect_rv != 0) {
            errno = connect_rv;
            connect_rv = -1;
        }
    }

    /* report the appropriate exception */
    if (connect_rv < 0) {

#ifdef __linux__
        /*
         * Linux/GNU distribution setup /etc/hosts so that
         * InetAddress.getLocalHost gets back the loopback address
         * rather than the host address. Thus a socket can be
         * bound to the loopback address and the connect will
         * fail with EADDRNOTAVAIL. In addition the Linux kernel
         * returns the wrong error in this case - it returns EINVAL
         * instead of EADDRNOTAVAIL. We handle this here so that
         * a more descriptive exception text is used.
         */
        if (connect_rv == -1 && errno == EINVAL) {
            JNU_ThrowByName(env, JNU_JAVANETPKG "SocketException",
                "Invalid argument or cannot assign requested address");
            return;
        }
#endif
#if defined(EPROTO)
        if (errno == EPROTO) {
            NET_ThrowByNameWithLastError(env, JNU_JAVANETPKG "ProtocolException",
                           "Protocol error");
            return;
        }
#endif
        if (errno == ECONNREFUSED) {
            NET_ThrowByNameWithLastError(env, JNU_JAVANETPKG "ConnectException",
                           "Connection refused");
        } else if (errno == ETIMEDOUT) {
            NET_ThrowByNameWithLastError(env, JNU_JAVANETPKG "ConnectException",
                           "Connection timed out");
        } else if (errno == EHOSTUNREACH) {
            NET_ThrowByNameWithLastError(env, JNU_JAVANETPKG "NoRouteToHostException",
                           "Host unreachable");
        } else if (errno == EADDRNOTAVAIL) {
            NET_ThrowByNameWithLastError(env, JNU_JAVANETPKG "NoRouteToHostException",
                             "Address not available");
        } else if ((errno == EISCONN) || (errno == EBADF)) {
            JNU_ThrowByName(env, JNU_JAVANETPKG "SocketException",
                            "Socket closed");
        } else {
            JNU_ThrowByNameWithMessageAndLastError
                (env, JNU_JAVANETPKG "SocketException", "connect failed");
        }
        return;
    }

    (*env)->SetIntField(env, fdObj, IO_fd_fdID, fd);

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
        socklen_t slen = sizeof(SOCKETADDRESS);
        if (getsockname(fd, &sa.sa, &slen) == -1) {
            JNU_ThrowByNameWithMessageAndLastError
                (env, JNU_JAVANETPKG "SocketException", "Error getting socket name");
        } else {
            localport = NET_GetPortFromSockaddr(&sa);
            (*env)->SetIntField(env, this, psi_localportID, localport);
        }
    }
}

/*
 * Class:     java_net_PlainSocketImpl
 * Method:    socketBind
 * Signature: (Ljava/net/InetAddress;I)V
 */
JNIEXPORT void JNICALL
Java_java_net_PlainSocketImpl_socketBind(JNIEnv *env, jobject this,
                                         jobject iaObj, jint localport) {

    /* fdObj is the FileDescriptor field on this */
    jobject fdObj = (*env)->GetObjectField(env, this, psi_fdID);
    /* fd is an int field on fdObj */
    int fd;
    int len = 0;
    SOCKETADDRESS sa;

    if (IS_NULL(fdObj)) {
        JNU_ThrowByName(env, JNU_JAVANETPKG "SocketException",
                        "Socket closed");
        return;
    } else {
        fd = (*env)->GetIntField(env, fdObj, IO_fd_fdID);
    }
    if (IS_NULL(iaObj)) {
        JNU_ThrowNullPointerException(env, "iaObj is null.");
        return;
    }

    /* bind */
    if (NET_InetAddressToSockaddr(env, iaObj, localport, &sa,
                                  &len, JNI_TRUE) != 0) {
        return;
    }
    setDefaultScopeID(env, &sa.sa);

    if (NET_Bind(fd, &sa, len) < 0) {
        if (errno == EADDRINUSE || errno == EADDRNOTAVAIL ||
            errno == EPERM || errno == EACCES) {
            NET_ThrowByNameWithLastError(env, JNU_JAVANETPKG "BindException",
                           "Bind failed");
        } else {
            JNU_ThrowByNameWithMessageAndLastError
                (env, JNU_JAVANETPKG "SocketException", "Bind failed");
        }
        return;
    }

    /* set the address */
    (*env)->SetObjectField(env, this, psi_addressID, iaObj);

    /* initialize the local port */
    if (localport == 0) {
        socklen_t slen = sizeof(SOCKETADDRESS);
        /* Now that we're a connected socket, let's extract the port number
         * that the system chose for us and store it in the Socket object.
         */
        if (getsockname(fd, &sa.sa, &slen) == -1) {
            JNU_ThrowByNameWithMessageAndLastError
                (env, JNU_JAVANETPKG "SocketException", "Error getting socket name");
            return;
        }
        localport = NET_GetPortFromSockaddr(&sa);
        (*env)->SetIntField(env, this, psi_localportID, localport);
    } else {
        (*env)->SetIntField(env, this, psi_localportID, localport);
    }
}

/*
 * Class:     java_net_PlainSocketImpl
 * Method:    socketListen
 * Signature: (I)V
 */
JNIEXPORT void JNICALL
Java_java_net_PlainSocketImpl_socketListen(JNIEnv *env, jobject this,
                                           jint count)
{
    /* this FileDescriptor fd field */
    jobject fdObj = (*env)->GetObjectField(env, this, psi_fdID);
    /* fdObj's int fd field */
    int fd;

    if (IS_NULL(fdObj)) {
        JNU_ThrowByName(env, JNU_JAVANETPKG "SocketException",
                        "Socket closed");
        return;
    } else {
        fd = (*env)->GetIntField(env, fdObj, IO_fd_fdID);
    }

    /*
     * Workaround for bugid 4101691 in Solaris 2.6. See 4106600.
     * If listen backlog is Integer.MAX_VALUE then subtract 1.
     */
    if (count == 0x7fffffff)
        count -= 1;

    if (listen(fd, count) == -1) {
        JNU_ThrowByNameWithMessageAndLastError
            (env, JNU_JAVANETPKG "SocketException", "Listen failed");
    }
}

/*
 * Class:     java_net_PlainSocketImpl
 * Method:    socketAccept
 * Signature: (Ljava/net/SocketImpl;)V
 */
JNIEXPORT void JNICALL
Java_java_net_PlainSocketImpl_socketAccept(JNIEnv *env, jobject this,
                                           jobject socket)
{
    /* fields on this */
    int port;
    jint timeout = (*env)->GetIntField(env, this, psi_timeoutID);
    jlong prevNanoTime = 0;
    jlong nanoTimeout = (jlong) timeout * NET_NSEC_PER_MSEC;
    jobject fdObj = (*env)->GetObjectField(env, this, psi_fdID);

    /* the FileDescriptor field on socket */
    jobject socketFdObj;
    /* the InetAddress field on socket */
    jobject socketAddressObj;

    /* the ServerSocket fd int field on fdObj */
    jint fd;

    /* accepted fd */
    jint newfd;

    SOCKETADDRESS sa;
    socklen_t slen = sizeof(SOCKETADDRESS);

    if (IS_NULL(fdObj)) {
        JNU_ThrowByName(env, JNU_JAVANETPKG "SocketException",
                        "Socket closed");
        return;
    } else {
        fd = (*env)->GetIntField(env, fdObj, IO_fd_fdID);
    }
    if (IS_NULL(socket)) {
        JNU_ThrowNullPointerException(env, "socket is null");
        return;
    }

    /*
     * accept connection but ignore ECONNABORTED indicating that
     * connection was eagerly accepted by the OS but was reset
     * before accept() was called.
     *
     * If accept timeout in place and timeout is adjusted with
     * each ECONNABORTED or EWOULDBLOCK or EAGAIN to ensure that
     * semantics of timeout are preserved.
     */
    for (;;) {
        int ret;
        jlong currNanoTime;

        /* first usage pick up current time */
        if (prevNanoTime == 0 && nanoTimeout > 0) {
            prevNanoTime = JVM_NanoTime(env, 0);
        }

        /* passing a timeout of 0 to poll will return immediately,
           but in the case of ServerSocket 0 means infinite. */
        if (timeout <= 0) {
            ret = NET_Timeout(env, fd, -1, 0);
        } else {
            ret = NET_Timeout(env, fd, nanoTimeout / NET_NSEC_PER_MSEC, prevNanoTime);
        }
        if (ret == 0) {
            JNU_ThrowByName(env, JNU_JAVANETPKG "SocketTimeoutException",
                            "Accept timed out");
            return;
        } else if (ret == -1) {
            if (errno == EBADF) {
               JNU_ThrowByName(env, JNU_JAVANETPKG "SocketException", "Socket closed");
            } else if (errno == ENOMEM) {
               JNU_ThrowOutOfMemoryError(env, "NET_Timeout native heap allocation failed");
            } else {
               JNU_ThrowByNameWithMessageAndLastError
                   (env, JNU_JAVANETPKG "SocketException", "Accept failed");
            }
            return;
        }

        newfd = NET_Accept(fd, &sa.sa, &slen);

        /* connection accepted */
        if (newfd >= 0) {
            SET_BLOCKING(newfd);
            break;
        }

        /* non (ECONNABORTED or EWOULDBLOCK or EAGAIN) error */
        if (!(errno == ECONNABORTED || errno == EWOULDBLOCK || errno == EAGAIN)) {
            break;
        }

        /* ECONNABORTED or EWOULDBLOCK or EAGAIN error so adjust timeout if there is one. */
        if (nanoTimeout >= NET_NSEC_PER_MSEC) {
            currNanoTime = JVM_NanoTime(env, 0);
            nanoTimeout -= (currNanoTime - prevNanoTime);
            if (nanoTimeout < NET_NSEC_PER_MSEC) {
                JNU_ThrowByName(env, JNU_JAVANETPKG "SocketTimeoutException",
                        "Accept timed out");
                return;
            }
            prevNanoTime = currNanoTime;
        }
    }

    if (newfd < 0) {
        if (newfd == -2) {
            JNU_ThrowByName(env, JNU_JAVAIOPKG "InterruptedIOException",
                            "operation interrupted");
        } else {
            if (errno == EINVAL) {
                errno = EBADF;
            }
            if (errno == EBADF) {
                JNU_ThrowByName(env, JNU_JAVANETPKG "SocketException", "Socket closed");
            } else {
                JNU_ThrowByNameWithMessageAndLastError
                    (env, JNU_JAVANETPKG "SocketException", "Accept failed");
            }
        }
        return;
    }

    /*
     * fill up the remote peer port and address in the new socket structure.
     */
    socketAddressObj = NET_SockaddrToInetAddress(env, &sa, &port);
    if (socketAddressObj == NULL) {
        /* should be pending exception */
        close(newfd);
        return;
    }

    /*
     * Populate SocketImpl.fd.fd
     */
    socketFdObj = (*env)->GetObjectField(env, socket, psi_fdID);
    (*env)->SetIntField(env, socketFdObj, IO_fd_fdID, newfd);

    (*env)->SetObjectField(env, socket, psi_addressID, socketAddressObj);
    (*env)->SetIntField(env, socket, psi_portID, port);
    /* also fill up the local port information */
     port = (*env)->GetIntField(env, this, psi_localportID);
    (*env)->SetIntField(env, socket, psi_localportID, port);
}


/*
 * Class:     java_net_PlainSocketImpl
 * Method:    socketAvailable
 * Signature: ()I
 */
JNIEXPORT jint JNICALL
Java_java_net_PlainSocketImpl_socketAvailable(JNIEnv *env, jobject this) {

    jint ret = -1;
    jobject fdObj = (*env)->GetObjectField(env, this, psi_fdID);
    jint fd;

    if (IS_NULL(fdObj)) {
        JNU_ThrowByName(env, JNU_JAVANETPKG "SocketException",
                        "Socket closed");
        return -1;
    } else {
        fd = (*env)->GetIntField(env, fdObj, IO_fd_fdID);
    }
    /* NET_SocketAvailable returns 0 for failure, 1 for success */
    if (NET_SocketAvailable(fd, &ret) == 0){
        if (errno == ECONNRESET) {
            JNU_ThrowByName(env, "sun/net/ConnectionResetException", "");
        } else {
            JNU_ThrowByNameWithMessageAndLastError
                (env, JNU_JAVANETPKG "SocketException", "ioctl FIONREAD failed");
        }
    }
    return ret;
}

/*
 * Class:     java_net_PlainSocketImpl
 * Method:    socketClose0
 * Signature: (Z)V
 */
JNIEXPORT void JNICALL
Java_java_net_PlainSocketImpl_socketClose0(JNIEnv *env, jobject this,
                                          jboolean useDeferredClose) {

    jobject fdObj = (*env)->GetObjectField(env, this, psi_fdID);
    jint fd;

    if (IS_NULL(fdObj)) {
        JNU_ThrowByName(env, JNU_JAVANETPKG "SocketException",
                        "socket already closed");
        return;
    } else {
        fd = (*env)->GetIntField(env, fdObj, IO_fd_fdID);
    }
    if (fd != -1) {
        if (useDeferredClose && marker_fd >= 0) {
            NET_Dup2(marker_fd, fd);
        } else {
            (*env)->SetIntField(env, fdObj, IO_fd_fdID, -1);
            NET_SocketClose(fd);
        }
    }
}

/*
 * Class:     java_net_PlainSocketImpl
 * Method:    socketShutdown
 * Signature: (I)V
 */
JNIEXPORT void JNICALL
Java_java_net_PlainSocketImpl_socketShutdown(JNIEnv *env, jobject this,
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
 * Class:     java_net_PlainSocketImpl
 * Method:    socketSetOption0
 * Signature: (IZLjava/lang/Object;)V
 */
JNIEXPORT void JNICALL
Java_java_net_PlainSocketImpl_socketSetOption0
  (JNIEnv *env, jobject this, jint cmd, jboolean on, jobject value)
{
    int fd;
    int level, optname, optlen;
    union {
        int i;
        struct linger ling;
    } optval;

    /*
     * Check that socket hasn't been closed
     */
    fd = getFD(env, this);
    if (fd < 0) {
        JNU_ThrowByName(env, JNU_JAVANETPKG "SocketException",
                        "Socket closed");
        return;
    }

    /*
     * SO_TIMEOUT is a NOOP on Solaris/Linux
     */
    if (cmd == java_net_SocketOptions_SO_TIMEOUT) {
        return;
    }

    /*
     * Map the Java level socket option to the platform specific
     * level and option name.
     */
    if (NET_MapSocketOption(cmd, &level, &optname)) {
        JNU_ThrowByName(env, "java/net/SocketException", "Invalid option");
        return;
    }

    switch (cmd) {
        case java_net_SocketOptions_SO_SNDBUF :
        case java_net_SocketOptions_SO_RCVBUF :
        case java_net_SocketOptions_SO_LINGER :
        case java_net_SocketOptions_IP_TOS :
            {
                jclass cls;
                jfieldID fid;

                cls = (*env)->FindClass(env, "java/lang/Integer");
                CHECK_NULL(cls);
                fid = (*env)->GetFieldID(env, cls, "value", "I");
                CHECK_NULL(fid);

                if (cmd == java_net_SocketOptions_SO_LINGER) {
                    if (on) {
                        optval.ling.l_onoff = 1;
                        optval.ling.l_linger = (*env)->GetIntField(env, value, fid);
                    } else {
                        optval.ling.l_onoff = 0;
                        optval.ling.l_linger = 0;
                    }
                    optlen = sizeof(optval.ling);
                } else {
                    optval.i = (*env)->GetIntField(env, value, fid);
                    optlen = sizeof(optval.i);
                }

                break;
            }

        /* Boolean -> int */
        default :
            optval.i = (on ? 1 : 0);
            optlen = sizeof(optval.i);

    }

    if (NET_SetSockOpt(fd, level, optname, (const void *)&optval, optlen) < 0) {
#if defined(__solaris__) || defined(_AIX)
        if (errno == EINVAL) {
            // On Solaris setsockopt will set errno to EINVAL if the socket
            // is closed. The default error message is then confusing
            char fullMsg[128];
            jio_snprintf(fullMsg, sizeof(fullMsg), "Invalid option or socket reset by remote peer");
            JNU_ThrowByName(env, JNU_JAVANETPKG "SocketException", fullMsg);
            return;
        }
#endif /* __solaris__ */
        JNU_ThrowByNameWithMessageAndLastError
            (env, JNU_JAVANETPKG "SocketException", "Error setting socket option");
    }
}

/*
 * Class:     java_net_PlainSocketImpl
 * Method:    socketGetOption
 * Signature: (ILjava/lang/Object;)I
 */
JNIEXPORT jint JNICALL
Java_java_net_PlainSocketImpl_socketGetOption
  (JNIEnv *env, jobject this, jint cmd, jobject iaContainerObj)
{
    int fd;
    int level, optname, optlen;
    union {
        int i;
        struct linger ling;
    } optval;

    /*
     * Check that socket hasn't been closed
     */
    fd = getFD(env, this);
    if (fd < 0) {
        JNU_ThrowByName(env, JNU_JAVANETPKG "SocketException",
                        "Socket closed");
        return -1;
    }

    /*
     * SO_BINDADDR isn't a socket option
     */
    if (cmd == java_net_SocketOptions_SO_BINDADDR) {
        SOCKETADDRESS sa;
        socklen_t len = sizeof(SOCKETADDRESS);
        int port;
        jobject iaObj;
        jclass iaCntrClass;
        jfieldID iaFieldID;

        if (getsockname(fd, &sa.sa, &len) < 0) {
            JNU_ThrowByNameWithMessageAndLastError
                (env, JNU_JAVANETPKG "SocketException", "Error getting socket name");
            return -1;
        }
        iaObj = NET_SockaddrToInetAddress(env, &sa, &port);
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
    if (NET_MapSocketOption(cmd, &level, &optname)) {
        JNU_ThrowByName(env, "java/net/SocketException", "Invalid option");
        return -1;
    }

    /*
     * Args are int except for SO_LINGER
     */
    if (cmd == java_net_SocketOptions_SO_LINGER) {
        optlen = sizeof(optval.ling);
    } else {
        optlen = sizeof(optval.i);
    }

    if (NET_GetSockOpt(fd, level, optname, (void *)&optval, &optlen) < 0) {
        JNU_ThrowByNameWithMessageAndLastError
            (env, JNU_JAVANETPKG "SocketException", "Error getting socket option");
        return -1;
    }

    switch (cmd) {
        case java_net_SocketOptions_SO_LINGER:
            return (optval.ling.l_onoff ? optval.ling.l_linger: -1);

        case java_net_SocketOptions_SO_SNDBUF:
        case java_net_SocketOptions_SO_RCVBUF:
        case java_net_SocketOptions_IP_TOS:
            return optval.i;

        default :
            return (optval.i == 0) ? -1 : 1;
    }
}


/*
 * Class:     java_net_PlainSocketImpl
 * Method:    socketSendUrgentData
 * Signature: (B)V
 */
JNIEXPORT void JNICALL
Java_java_net_PlainSocketImpl_socketSendUrgentData(JNIEnv *env, jobject this,
                                             jint data) {
    /* The fd field */
    jobject fdObj = (*env)->GetObjectField(env, this, psi_fdID);
    int n, fd;
    unsigned char d = data & 0xFF;

    if (IS_NULL(fdObj)) {
        JNU_ThrowByName(env, "java/net/SocketException", "Socket closed");
        return;
    } else {
        fd = (*env)->GetIntField(env, fdObj, IO_fd_fdID);
        /* Bug 4086704 - If the Socket associated with this file descriptor
         * was closed (sysCloseFD), the file descriptor is set to -1.
         */
        if (fd == -1) {
            JNU_ThrowByName(env, "java/net/SocketException", "Socket closed");
            return;
        }

    }
    n = NET_Send(fd, (char *)&d, 1, MSG_OOB);
    if (n == -1) {
        JNU_ThrowIOExceptionWithLastError(env, "Write failed");
    }
}
