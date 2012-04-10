/*
 * Copyright (c) 1998, 2008, Oracle and/or its affiliates. All rights reserved.
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
#include <stdio.h>
#include <string.h>
#include <errno.h>
#include <stdlib.h>
#include <ctype.h>

#include "jdwpTransport.h"
#include "sysSocket.h"

/*
 * The Socket Transport Library.
 *
 * This module is an implementation of the Java Debug Wire Protocol Transport
 * Service Provider Interface - see src/share/javavm/export/jdwpTransport.h.
 */

static int serverSocketFD;
static int socketFD = -1;
static jdwpTransportCallback *callback;
static JavaVM *jvm;
static int tlsIndex;
static jboolean initialized;
static struct jdwpTransportNativeInterface_ interface;
static jdwpTransportEnv single_env = (jdwpTransportEnv)&interface;

#define RETURN_ERROR(err, msg) \
        if (1==1) { \
            setLastError(err, msg); \
            return err; \
        }

#define RETURN_IO_ERROR(msg)    RETURN_ERROR(JDWPTRANSPORT_ERROR_IO_ERROR, msg);

#define RETURN_RECV_ERROR(n) \
        if (n == 0) { \
            RETURN_ERROR(JDWPTRANSPORT_ERROR_IO_ERROR, "premature EOF"); \
        } else { \
            RETURN_IO_ERROR("recv error"); \
        }

#define HEADER_SIZE     11
#define MAX_DATA_SIZE 1000

static jint recv_fully(int, char *, int);
static jint send_fully(int, char *, int);

/*
 * Record the last error for this thread.
 */
static void
setLastError(jdwpTransportError err, char *newmsg) {
    char buf[255];
    char *msg;

    /* get any I/O first in case any system calls override errno */
    if (err == JDWPTRANSPORT_ERROR_IO_ERROR) {
        dbgsysGetLastIOError(buf, sizeof(buf));
    }

    msg = (char *)dbgsysTlsGet(tlsIndex);
    if (msg != NULL) {
        (*callback->free)(msg);
    }

    if (err == JDWPTRANSPORT_ERROR_IO_ERROR) {
        char *join_str = ": ";
        int msg_len = (int)strlen(newmsg) + (int)strlen(join_str) +
                      (int)strlen(buf) + 3;
        msg = (*callback->alloc)(msg_len);
        if (msg != NULL) {
            strcpy(msg, newmsg);
            strcat(msg, join_str);
            strcat(msg, buf);
        }
    } else {
        msg = (*callback->alloc)((int)strlen(newmsg)+1);
        if (msg != NULL) {
            strcpy(msg, newmsg);
        }
    }

    dbgsysTlsPut(tlsIndex, msg);
}

/*
 * Return the last error for this thread (may be NULL)
 */
static char*
getLastError() {
    return (char *)dbgsysTlsGet(tlsIndex);
}

static jdwpTransportError
setOptions(int fd)
{
    jvalue dontcare;
    int err;

    dontcare.i = 0;  /* keep compiler happy */

    err = dbgsysSetSocketOption(fd, SO_REUSEADDR, JNI_TRUE, dontcare);
    if (err < 0) {
        RETURN_IO_ERROR("setsockopt SO_REUSEADDR failed");
    }

    err = dbgsysSetSocketOption(fd, TCP_NODELAY, JNI_TRUE, dontcare);
    if (err < 0) {
        RETURN_IO_ERROR("setsockopt TCPNODELAY failed");
    }

    return JDWPTRANSPORT_ERROR_NONE;
}

static jdwpTransportError
handshake(int fd, jlong timeout) {
    const char *hello = "JDWP-Handshake";
    char b[16];
    int rv, helloLen, received;

    if (timeout > 0) {
        dbgsysConfigureBlocking(fd, JNI_FALSE);
    }
    helloLen = (int)strlen(hello);
    received = 0;
    while (received < helloLen) {
        int n;
        char *buf;
        if (timeout > 0) {
            rv = dbgsysPoll(fd, JNI_TRUE, JNI_FALSE, (long)timeout);
            if (rv <= 0) {
                setLastError(0, "timeout during handshake");
                return JDWPTRANSPORT_ERROR_IO_ERROR;
            }
        }
        buf = b;
        buf += received;
        n = recv_fully(fd, buf, helloLen-received);
        if (n == 0) {
            setLastError(0, "handshake failed - connection prematurally closed");
            return JDWPTRANSPORT_ERROR_IO_ERROR;
        }
        if (n < 0) {
            RETURN_IO_ERROR("recv failed during handshake");
        }
        received += n;
    }
    if (timeout > 0) {
        dbgsysConfigureBlocking(fd, JNI_TRUE);
    }
    if (strncmp(b, hello, received) != 0) {
        char msg[80+2*16];
        b[received] = '\0';
        /*
         * We should really use snprintf here but it's not available on Windows.
         * We can't use jio_snprintf without linking the transport against the VM.
         */
        sprintf(msg, "handshake failed - received >%s< - expected >%s<", b, hello);
        setLastError(0, msg);
        return JDWPTRANSPORT_ERROR_IO_ERROR;
    }

    if (send_fully(fd, (char*)hello, helloLen) != helloLen) {
        RETURN_IO_ERROR("send failed during handshake");
    }
    return JDWPTRANSPORT_ERROR_NONE;
}

static jdwpTransportError
parseAddress(const char *address, struct sockaddr_in *sa, uint32_t defaultHost) {
    char *colon;

    memset((void *)sa,0,sizeof(struct sockaddr_in));
    sa->sin_family = AF_INET;

    /* check for host:port or port */
    colon = strchr(address, ':');
    if (colon == NULL) {
        u_short port = (u_short)atoi(address);
        sa->sin_port = dbgsysHostToNetworkShort(port);
        sa->sin_addr.s_addr = dbgsysHostToNetworkLong(defaultHost);
    } else {
        char *buf;
        char *hostname;
        u_short port;
        uint32_t addr;

        buf = (*callback->alloc)((int)strlen(address)+1);
        if (buf == NULL) {
            RETURN_ERROR(JDWPTRANSPORT_ERROR_OUT_OF_MEMORY, "out of memory");
        }
        strcpy(buf, address);
        buf[colon - address] = '\0';
        hostname = buf;
        port = atoi(colon + 1);
        sa->sin_port = dbgsysHostToNetworkShort(port);

        /*
         * First see if the host is a literal IP address.
         * If not then try to resolve it.
         */
        addr = dbgsysInetAddr(hostname);
        if (addr == 0xffffffff) {
            struct hostent *hp = dbgsysGetHostByName(hostname);
            if (hp == NULL) {
                /* don't use RETURN_IO_ERROR as unknown host is normal */
                setLastError(0, "gethostbyname: unknown host");
                (*callback->free)(buf);
                return JDWPTRANSPORT_ERROR_IO_ERROR;
            }

            /* lookup was successful */
            memcpy(&(sa->sin_addr), hp->h_addr_list[0], hp->h_length);
        } else {
            sa->sin_addr.s_addr = addr;
        }

        (*callback->free)(buf);
    }

    return JDWPTRANSPORT_ERROR_NONE;
}


static jdwpTransportError JNICALL
socketTransport_getCapabilities(jdwpTransportEnv* env,
        JDWPTransportCapabilities* capabilitiesPtr)
{
    JDWPTransportCapabilities result;

    memset(&result, 0, sizeof(result));
    result.can_timeout_attach = JNI_TRUE;
    result.can_timeout_accept = JNI_TRUE;
    result.can_timeout_handshake = JNI_TRUE;

    *capabilitiesPtr = result;

    return JDWPTRANSPORT_ERROR_NONE;
}


static jdwpTransportError JNICALL
socketTransport_startListening(jdwpTransportEnv* env, const char* address,
                               char** actualAddress)
{
    struct sockaddr_in sa;
    int err;

    memset((void *)&sa,0,sizeof(struct sockaddr_in));
    sa.sin_family = AF_INET;

    /* no address provided */
    if ((address == NULL) || (address[0] == '\0')) {
        address = "0";
    }

    err = parseAddress(address, &sa, INADDR_ANY);
    if (err != JDWPTRANSPORT_ERROR_NONE) {
        return err;
    }

    serverSocketFD = dbgsysSocket(AF_INET, SOCK_STREAM, 0);
    if (serverSocketFD < 0) {
        RETURN_IO_ERROR("socket creation failed");
    }

    err = setOptions(serverSocketFD);
    if (err) {
        return err;
    }

    err = dbgsysBind(serverSocketFD, (struct sockaddr *)&sa, sizeof(sa));
    if (err < 0) {
        RETURN_IO_ERROR("bind failed");
    }

    err = dbgsysListen(serverSocketFD, 1);
    if (err < 0) {
        RETURN_IO_ERROR("listen failed");
    }

    {
        char buf[20];
        int len = sizeof(sa);
        jint portNum;
        err = dbgsysGetSocketName(serverSocketFD,
                               (struct sockaddr *)&sa, &len);
        portNum = dbgsysNetworkToHostShort(sa.sin_port);
        sprintf(buf, "%d", portNum);
        *actualAddress = (*callback->alloc)((int)strlen(buf) + 1);
        if (*actualAddress == NULL) {
            RETURN_ERROR(JDWPTRANSPORT_ERROR_OUT_OF_MEMORY, "out of memory");
        } else {
            strcpy(*actualAddress, buf);
        }
    }

    return JDWPTRANSPORT_ERROR_NONE;
}

static jdwpTransportError JNICALL
socketTransport_accept(jdwpTransportEnv* env, jlong acceptTimeout, jlong handshakeTimeout)
{
    int socketLen, err;
    struct sockaddr_in socket;
    jlong startTime = (jlong)0;

    /*
     * Use a default handshake timeout if not specified - this avoids an indefinite
     * hang in cases where something other than a debugger connects to our port.
     */
    if (handshakeTimeout == 0) {
        handshakeTimeout = 2000;
    }

    do {
        /*
         * If there is an accept timeout then we put the socket in non-blocking
         * mode and poll for a connection.
         */
        if (acceptTimeout > 0) {
            int rv;
            dbgsysConfigureBlocking(serverSocketFD, JNI_FALSE);
            startTime = dbgsysCurrentTimeMillis();
            rv = dbgsysPoll(serverSocketFD, JNI_TRUE, JNI_FALSE, (long)acceptTimeout);
            if (rv <= 0) {
                /* set the last error here as could be overridden by configureBlocking */
                if (rv == 0) {
                    setLastError(JDWPTRANSPORT_ERROR_IO_ERROR, "poll failed");
                }
                /* restore blocking state */
                dbgsysConfigureBlocking(serverSocketFD, JNI_TRUE);
                if (rv == 0) {
                    RETURN_ERROR(JDWPTRANSPORT_ERROR_TIMEOUT, "timed out waiting for connection");
                } else {
                    return JDWPTRANSPORT_ERROR_IO_ERROR;
                }
            }
        }

        /*
         * Accept the connection
         */
        memset((void *)&socket,0,sizeof(struct sockaddr_in));
        socketLen = sizeof(socket);
        socketFD = dbgsysAccept(serverSocketFD,
                                (struct sockaddr *)&socket,
                                &socketLen);
        /* set the last error here as could be overridden by configureBlocking */
        if (socketFD < 0) {
            setLastError(JDWPTRANSPORT_ERROR_IO_ERROR, "accept failed");
        }
        /*
         * Restore the blocking state - note that the accepted socket may be in
         * blocking or non-blocking mode (platform dependent). However as there
         * is a handshake timeout set then it will go into non-blocking mode
         * anyway for the handshake.
         */
        if (acceptTimeout > 0) {
            dbgsysConfigureBlocking(serverSocketFD, JNI_TRUE);
        }
        if (socketFD < 0) {
            return JDWPTRANSPORT_ERROR_IO_ERROR;
        }

        /* handshake with the debugger */
        err = handshake(socketFD, handshakeTimeout);

        /*
         * If the handshake fails then close the connection. If there if an accept
         * timeout then we must adjust the timeout for the next poll.
         */
        if (err) {
            fprintf(stderr, "Debugger failed to attach: %s\n", getLastError());
            dbgsysSocketClose(socketFD);
            socketFD = -1;
            if (acceptTimeout > 0) {
                long endTime = dbgsysCurrentTimeMillis();
                acceptTimeout -= (endTime - startTime);
                if (acceptTimeout <= 0) {
                    setLastError(JDWPTRANSPORT_ERROR_IO_ERROR,
                        "timeout waiting for debugger to connect");
                    return JDWPTRANSPORT_ERROR_IO_ERROR;
                }
            }
        }
    } while (socketFD < 0);

    return JDWPTRANSPORT_ERROR_NONE;
}

static jdwpTransportError JNICALL
socketTransport_stopListening(jdwpTransportEnv *env)
{
    if (serverSocketFD < 0) {
        RETURN_ERROR(JDWPTRANSPORT_ERROR_ILLEGAL_STATE, "connection not open");
    }
    if (dbgsysSocketClose(serverSocketFD) < 0) {
        RETURN_IO_ERROR("close failed");
    }
    serverSocketFD = -1;
    return JDWPTRANSPORT_ERROR_NONE;
}

static jdwpTransportError JNICALL
socketTransport_attach(jdwpTransportEnv* env, const char* addressString, jlong attachTimeout,
                       jlong handshakeTimeout)
{
    struct sockaddr_in sa;
    int err;

    if (addressString == NULL || addressString[0] == '\0') {
        RETURN_ERROR(JDWPTRANSPORT_ERROR_ILLEGAL_ARGUMENT, "address is missing");
    }

    err = parseAddress(addressString, &sa, 0x7f000001);
    if (err != JDWPTRANSPORT_ERROR_NONE) {
        return err;
    }

    socketFD = dbgsysSocket(AF_INET, SOCK_STREAM, 0);
    if (socketFD < 0) {
        RETURN_IO_ERROR("unable to create socket");
    }

    err = setOptions(socketFD);
    if (err) {
        return err;
    }

    /*
     * To do a timed connect we make the socket non-blocking
     * and poll with a timeout;
     */
    if (attachTimeout > 0) {
        dbgsysConfigureBlocking(socketFD, JNI_FALSE);
    }

    err = dbgsysConnect(socketFD, (struct sockaddr *)&sa, sizeof(sa));
    if (err == DBG_EINPROGRESS && attachTimeout > 0) {
        err = dbgsysFinishConnect(socketFD, (long)attachTimeout);

        if (err == DBG_ETIMEOUT) {
            dbgsysConfigureBlocking(socketFD, JNI_TRUE);
            RETURN_ERROR(JDWPTRANSPORT_ERROR_TIMEOUT, "connect timed out");
        }
    }

    if (err < 0) {
        RETURN_IO_ERROR("connect failed");
    }

    if (attachTimeout > 0) {
        dbgsysConfigureBlocking(socketFD, JNI_TRUE);
    }

    err = handshake(socketFD, handshakeTimeout);
    if (err) {
        dbgsysSocketClose(socketFD);
        socketFD = -1;
        return err;
    }

    return JDWPTRANSPORT_ERROR_NONE;
}

static jboolean JNICALL
socketTransport_isOpen(jdwpTransportEnv* env)
{
    if (socketFD >= 0) {
        return JNI_TRUE;
    } else {
        return JNI_FALSE;
    }
}

static jdwpTransportError JNICALL
socketTransport_close(jdwpTransportEnv* env)
{
    int fd = socketFD;
    socketFD = -1;
    if (fd < 0) {
        return JDWPTRANSPORT_ERROR_NONE;
    }
    if (dbgsysSocketClose(fd) < 0) {
        /*
         * close failed - it's pointless to restore socketFD here because
         * any subsequent close will likely fail aswell.
         */
        RETURN_IO_ERROR("close failed");
    }
    return JDWPTRANSPORT_ERROR_NONE;
}

static jdwpTransportError JNICALL
socketTransport_writePacket(jdwpTransportEnv* env, const jdwpPacket *packet)
{
    jint len, data_len, id;
    /*
     * room for header and up to MAX_DATA_SIZE data bytes
     */
    char header[HEADER_SIZE + MAX_DATA_SIZE];
    jbyte *data;

    /* packet can't be null */
    if (packet == NULL) {
        RETURN_ERROR(JDWPTRANSPORT_ERROR_ILLEGAL_ARGUMENT, "packet is NULL");
    }

    len = packet->type.cmd.len;         /* includes header */
    data_len = len - HEADER_SIZE;

    /* bad packet */
    if (data_len < 0) {
        RETURN_ERROR(JDWPTRANSPORT_ERROR_ILLEGAL_ARGUMENT, "invalid length");
    }

    /* prepare the header for transmission */
    len = (jint)dbgsysHostToNetworkLong(len);
    id = (jint)dbgsysHostToNetworkLong(packet->type.cmd.id);

    memcpy(header + 0, &len, 4);
    memcpy(header + 4, &id, 4);
    header[8] = packet->type.cmd.flags;
    if (packet->type.cmd.flags & JDWPTRANSPORT_FLAGS_REPLY) {
        jshort errorCode =
            dbgsysHostToNetworkShort(packet->type.reply.errorCode);
        memcpy(header + 9, &errorCode, 2);
    } else {
        header[9] = packet->type.cmd.cmdSet;
        header[10] = packet->type.cmd.cmd;
    }

    data = packet->type.cmd.data;
    /* Do one send for short packets, two for longer ones */
    if (data_len <= MAX_DATA_SIZE) {
        memcpy(header + HEADER_SIZE, data, data_len);
        if (send_fully(socketFD, (char *)&header, HEADER_SIZE + data_len) !=
            HEADER_SIZE + data_len) {
            RETURN_IO_ERROR("send failed");
        }
    } else {
        memcpy(header + HEADER_SIZE, data, MAX_DATA_SIZE);
        if (send_fully(socketFD, (char *)&header, HEADER_SIZE + MAX_DATA_SIZE) !=
            HEADER_SIZE + MAX_DATA_SIZE) {
            RETURN_IO_ERROR("send failed");
        }
        /* Send the remaining data bytes right out of the data area. */
        if (send_fully(socketFD, (char *)data + MAX_DATA_SIZE,
                       data_len - MAX_DATA_SIZE) != data_len - MAX_DATA_SIZE) {
            RETURN_IO_ERROR("send failed");
        }
    }

    return JDWPTRANSPORT_ERROR_NONE;
}

static jint
recv_fully(int f, char *buf, int len)
{
    int nbytes = 0;
    while (nbytes < len) {
        int res = dbgsysRecv(f, buf + nbytes, len - nbytes, 0);
        if (res < 0) {
            return res;
        } else if (res == 0) {
            break; /* eof, return nbytes which is less than len */
        }
        nbytes += res;
    }
    return nbytes;
}

jint
send_fully(int f, char *buf, int len)
{
    int nbytes = 0;
    while (nbytes < len) {
        int res = dbgsysSend(f, buf + nbytes, len - nbytes, 0);
        if (res < 0) {
            return res;
        } else if (res == 0) {
            break; /* eof, return nbytes which is less than len */
        }
        nbytes += res;
    }
    return nbytes;
}

static jdwpTransportError JNICALL
socketTransport_readPacket(jdwpTransportEnv* env, jdwpPacket* packet) {
    jint length, data_len;
    jint n;

    /* packet can't be null */
    if (packet == NULL) {
        RETURN_ERROR(JDWPTRANSPORT_ERROR_ILLEGAL_ARGUMENT, "packet is null");
    }

    /* read the length field */
    n = recv_fully(socketFD, (char *)&length, sizeof(jint));

    /* check for EOF */
    if (n == 0) {
        packet->type.cmd.len = 0;
        return JDWPTRANSPORT_ERROR_NONE;
    }
    if (n != sizeof(jint)) {
        RETURN_RECV_ERROR(n);
    }

    length = (jint)dbgsysNetworkToHostLong(length);
    packet->type.cmd.len = length;


    n = recv_fully(socketFD,(char *)&(packet->type.cmd.id),sizeof(jint));
    if (n < (int)sizeof(jint)) {
        RETURN_RECV_ERROR(n);
    }

    packet->type.cmd.id = (jint)dbgsysNetworkToHostLong(packet->type.cmd.id);

    n = recv_fully(socketFD,(char *)&(packet->type.cmd.flags),sizeof(jbyte));
    if (n < (int)sizeof(jbyte)) {
        RETURN_RECV_ERROR(n);
    }

    if (packet->type.cmd.flags & JDWPTRANSPORT_FLAGS_REPLY) {
        n = recv_fully(socketFD,(char *)&(packet->type.reply.errorCode),sizeof(jbyte));
        if (n < (int)sizeof(jshort)) {
            RETURN_RECV_ERROR(n);
        }

        /* FIXME - should the error be converted to host order?? */


    } else {
        n = recv_fully(socketFD,(char *)&(packet->type.cmd.cmdSet),sizeof(jbyte));
        if (n < (int)sizeof(jbyte)) {
            RETURN_RECV_ERROR(n);
        }

        n = recv_fully(socketFD,(char *)&(packet->type.cmd.cmd),sizeof(jbyte));
        if (n < (int)sizeof(jbyte)) {
            RETURN_RECV_ERROR(n);
        }
    }

    data_len = length - ((sizeof(jint) * 2) + (sizeof(jbyte) * 3));

    if (data_len < 0) {
        setLastError(0, "Badly formed packet received - invalid length");
        return JDWPTRANSPORT_ERROR_IO_ERROR;
    } else if (data_len == 0) {
        packet->type.cmd.data = NULL;
    } else {
        packet->type.cmd.data= (*callback->alloc)(data_len);

        if (packet->type.cmd.data == NULL) {
            RETURN_ERROR(JDWPTRANSPORT_ERROR_OUT_OF_MEMORY, "out of memory");
        }

        n = recv_fully(socketFD,(char *)packet->type.cmd.data, data_len);
        if (n < data_len) {
            (*callback->free)(packet->type.cmd.data);
            RETURN_RECV_ERROR(n);
        }
    }

    return JDWPTRANSPORT_ERROR_NONE;
}

static jdwpTransportError JNICALL
socketTransport_getLastError(jdwpTransportEnv* env, char** msgP) {
    char *msg = (char *)dbgsysTlsGet(tlsIndex);
    if (msg == NULL) {
        return JDWPTRANSPORT_ERROR_MSG_NOT_AVAILABLE;
    }
    *msgP = (*callback->alloc)((int)strlen(msg)+1);
    if (*msgP == NULL) {
        return JDWPTRANSPORT_ERROR_OUT_OF_MEMORY;
    }
    strcpy(*msgP, msg);
    return JDWPTRANSPORT_ERROR_NONE;
}

JNIEXPORT jint JNICALL
jdwpTransport_OnLoad(JavaVM *vm, jdwpTransportCallback* cbTablePtr,
                     jint version, jdwpTransportEnv** result)
{
    if (version != JDWPTRANSPORT_VERSION_1_0) {
        return JNI_EVERSION;
    }
    if (initialized) {
        /*
         * This library doesn't support multiple environments (yet)
         */
        return JNI_EEXIST;
    }
    initialized = JNI_TRUE;
    jvm = vm;
    callback = cbTablePtr;

    /* initialize interface table */
    interface.GetCapabilities = &socketTransport_getCapabilities;
    interface.Attach = &socketTransport_attach;
    interface.StartListening = &socketTransport_startListening;
    interface.StopListening = &socketTransport_stopListening;
    interface.Accept = &socketTransport_accept;
    interface.IsOpen = &socketTransport_isOpen;
    interface.Close = &socketTransport_close;
    interface.ReadPacket = &socketTransport_readPacket;
    interface.WritePacket = &socketTransport_writePacket;
    interface.GetLastError = &socketTransport_getLastError;
    *result = &single_env;

    /* initialized TLS */
    tlsIndex = dbgsysTlsAlloc();
    return JNI_OK;
}
