/*
 * Copyright (c) 1998, 2018, Oracle and/or its affiliates. All rights reserved.
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

#include "jni.h"
#include "jdwpTransport.h"
#include "sysSocket.h"

#ifdef _WIN32
 #include <winsock2.h>
 #include <ws2tcpip.h>
#else
 #include <arpa/inet.h>
 #include <sys/socket.h>
#endif

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

#define MAX_DATA_SIZE 1000

static jint recv_fully(int, char *, int);
static jint send_fully(int, char *, int);

/* version >= JDWPTRANSPORT_VERSION_1_1 */
typedef struct {
    uint32_t subnet;
    uint32_t netmask;
} AllowedPeerInfo;

#define STR(x) #x
#define MAX_PEER_ENTRIES 32
#define MAX_PEERS_STR STR(MAX_PEER_ENTRIES)
static AllowedPeerInfo _peers[MAX_PEER_ENTRIES];
static int _peers_cnt = 0;


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

/* Set options common to client and server sides */
static jdwpTransportError
setOptionsCommon(int fd)
{
    jvalue dontcare;
    int err;

    dontcare.i = 0;  /* keep compiler happy */

    err = dbgsysSetSocketOption(fd, TCP_NODELAY, JNI_TRUE, dontcare);
    if (err < 0) {
        RETURN_IO_ERROR("setsockopt TCPNODELAY failed");
    }

    return JDWPTRANSPORT_ERROR_NONE;
}

/* Set the SO_REUSEADDR option */
static jdwpTransportError
setReuseAddrOption(int fd)
{
    jvalue dontcare;
    int err;

    dontcare.i = 0;  /* keep compiler happy */

    err = dbgsysSetSocketOption(fd, SO_REUSEADDR, JNI_TRUE, dontcare);
    if (err < 0) {
        RETURN_IO_ERROR("setsockopt SO_REUSEADDR failed");
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

static uint32_t
getLocalHostAddress() {
    // Simple routine to guess localhost address.
    // it looks up "localhost" and returns 127.0.0.1 if lookup
    // fails.
    struct addrinfo hints, *res = NULL;
    uint32_t addr;
    int err;

    // Use portable way to initialize the structure
    memset((void *)&hints, 0, sizeof(hints));
    hints.ai_family = AF_INET;

    err = getaddrinfo("localhost", NULL, &hints, &res);
    if (err < 0 || res == NULL) {
        return dbgsysHostToNetworkLong(INADDR_LOOPBACK);
    }

    // getaddrinfo might return more than one address
    // but we are using first one only
    addr = ((struct sockaddr_in *)(res->ai_addr))->sin_addr.s_addr;
    freeaddrinfo(res);
    return addr;
}

static int
getPortNumber(const char *s_port) {
    u_long n;
    char *eptr;

    if (*s_port == 0) {
        // bad address - colon with no port number in parameters
        return -1;
    }

    n = strtoul(s_port, &eptr, 10);
    if (eptr != s_port + strlen(s_port)) {
        // incomplete conversion - port number contains non-digit
        return -1;
    }

    if (n > (u_short) -1) {
        // check that value supplied by user is less than
        // maximum possible u_short value (65535) and
        // will not be truncated later.
        return -1;
    }

    return n;
}

static jdwpTransportError
parseAddress(const char *address, struct sockaddr_in *sa) {
    char *colon;
    int port;

    memset((void *)sa, 0, sizeof(struct sockaddr_in));
    sa->sin_family = AF_INET;

    /* check for host:port or port */
    colon = strchr(address, ':');
    port = getPortNumber((colon == NULL) ? address : colon +1);
    if (port < 0) {
        RETURN_ERROR(JDWPTRANSPORT_ERROR_ILLEGAL_ARGUMENT, "invalid port number specified");
    }
    sa->sin_port = dbgsysHostToNetworkShort((u_short)port);

    if (colon == NULL) {
        // bind to localhost only if no address specified
        sa->sin_addr.s_addr = getLocalHostAddress();
    } else if (strncmp(address, "localhost:", 10) == 0) {
        // optimize for common case
        sa->sin_addr.s_addr = getLocalHostAddress();
    } else if (*address == '*' && *(address+1) == ':') {
        // we are explicitly asked to bind server to all available IP addresses
        // has no meaning for client.
        sa->sin_addr.s_addr = dbgsysHostToNetworkLong(INADDR_ANY);
     } else {
        char *buf;
        char *hostname;
        uint32_t addr;
        int ai;
        buf = (*callback->alloc)((int)strlen(address) + 1);
        if (buf == NULL) {
            RETURN_ERROR(JDWPTRANSPORT_ERROR_OUT_OF_MEMORY, "out of memory");
        }
        strcpy(buf, address);
        buf[colon - address] = '\0';
        hostname = buf;

        /*
         * First see if the host is a literal IP address.
         * If not then try to resolve it.
         */
        addr = dbgsysInetAddr(hostname);
        if (addr == 0xffffffff) {
            struct addrinfo hints;
            struct addrinfo *results = NULL;
            memset (&hints, 0, sizeof(hints));
            hints.ai_family = AF_INET;
            hints.ai_socktype = SOCK_STREAM;
            hints.ai_protocol = IPPROTO_TCP;

            ai = dbgsysGetAddrInfo(hostname, NULL, &hints, &results);

            if (ai != 0) {
                /* don't use RETURN_IO_ERROR as unknown host is normal */
                setLastError(0, "getaddrinfo: unknown host");
                (*callback->free)(buf);
                return JDWPTRANSPORT_ERROR_IO_ERROR;
            }

            /* lookup was successful */
            sa->sin_addr =  ((struct sockaddr_in *)results->ai_addr)->sin_addr;
            freeaddrinfo(results);
        } else {
            sa->sin_addr.s_addr = addr;
        }

        (*callback->free)(buf);
    }

    return JDWPTRANSPORT_ERROR_NONE;
}

static const char *
ip_s2u(const char *instr, uint32_t *ip) {
    // Convert string representation of ip to integer
    // in network byte order (big-endian)
    char t[4] = { 0, 0, 0, 0 };
    const char *s = instr;
    int i = 0;

    while (1) {
        if (*s == '.') {
            ++i;
            ++s;
            continue;
        }
        if (*s == 0 || *s == '+' || *s == '/') {
            break;
        }
        if (*s < '0' || *s > '9') {
            return instr;
        }
        t[i] = (t[i] * 10) + (*s - '0');
        ++s;
    }

    *ip = *(uint32_t*)(t);
    return s;
}

static const char *
mask_s2u(const char *instr, uint32_t *mask) {
    // Convert the number of bits to a netmask
    // in network byte order (big-endian)
    unsigned char m = 0;
    const char *s = instr;

    while (1) {
        if (*s == 0 || *s == '+') {
            break;
        }
        if (*s < '0' || *s > '9') {
            return instr;
        }
        m = (m * 10) + (*s - '0');
        ++s;
    }

    if (m == 0 || m > 32) {
       // Drop invalid input
       return instr;
    }

    *mask = htonl((uint32_t)(~0) << (32 - m));
    return s;
}

static int
ip_in_subnet(uint32_t subnet, uint32_t mask, uint32_t ipaddr) {
    return (ipaddr & mask) == subnet;
}

static jdwpTransportError
parseAllowedPeers(const char *allowed_peers) {
    // Build a list of allowed peers from char string
    // of format 192.168.0.10+192.168.0.0/24
    const char *s = NULL;
    const char *p = allowed_peers;
    uint32_t   ip = 0;
    uint32_t mask = 0xFFFFFFFF;

    while (1) {
        s = ip_s2u(p, &ip);
        if (s == p) {
            _peers_cnt = 0;
            fprintf(stderr, "Error in allow option: '%s'\n", s);
            RETURN_ERROR(JDWPTRANSPORT_ERROR_ILLEGAL_ARGUMENT,
                         "invalid IP address in allow option");
        }

        if (*s == '/') {
            // netmask specified
            s = mask_s2u(s + 1, &mask);
            if (*(s - 1) == '/') {
                // Input is not consumed, something bad happened
                _peers_cnt = 0;
                fprintf(stderr, "Error in allow option: '%s'\n", s);
                RETURN_ERROR(JDWPTRANSPORT_ERROR_ILLEGAL_ARGUMENT,
                             "invalid netmask in allow option");
            }
        } else {
            // reset netmask
            mask = 0xFFFFFFFF;
        }

        if (*s == '+' || *s == 0) {
            if (_peers_cnt >= MAX_PEER_ENTRIES) {
                fprintf(stderr, "Error in allow option: '%s'\n", allowed_peers);
                RETURN_ERROR(JDWPTRANSPORT_ERROR_ILLEGAL_ARGUMENT,
                             "exceeded max number of allowed peers: " MAX_PEERS_STR);
            }
            _peers[_peers_cnt].subnet = ip;
            _peers[_peers_cnt].netmask = mask;
            _peers_cnt++;
            if (*s == 0) {
                // end of options
                break;
            }
            // advance to next IP block
            p = s + 1;
        }
    }
    return JDWPTRANSPORT_ERROR_NONE;
}

static int
isPeerAllowed(struct sockaddr_in *peer) {
    int i;
    for (i = 0; i < _peers_cnt; ++i) {
        int peer_ip = peer->sin_addr.s_addr;
        if (ip_in_subnet(_peers[i].subnet, _peers[i].netmask, peer_ip)) {
            return 1;
        }
    }

    return 0;
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

    err = parseAddress(address, &sa);
    if (err != JDWPTRANSPORT_ERROR_NONE) {
        return err;
    }

    serverSocketFD = dbgsysSocket(AF_INET, SOCK_STREAM, 0);
    if (serverSocketFD < 0) {
        RETURN_IO_ERROR("socket creation failed");
    }

    err = setOptionsCommon(serverSocketFD);
    if (err) {
        return err;
    }
    if (sa.sin_port != 0) {
        /*
         * Only need SO_REUSEADDR if we're using a fixed port. If we
         * start seeing EADDRINUSE due to collisions in free ports
         * then we should retry the dbgsysBind() a few times.
         */
        err = setReuseAddrOption(serverSocketFD);
        if (err) {
            return err;
        }
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
        socklen_t len = sizeof(sa);
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
    socklen_t socketLen;
    int err = JDWPTRANSPORT_ERROR_NONE;
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

        /*
         * version >= JDWPTRANSPORT_VERSION_1_1:
         * Verify that peer is allowed to connect.
         */
        if (_peers_cnt > 0) {
            if (!isPeerAllowed(&socket)) {
                char ebuf[64] = { 0 };
                char buf[INET_ADDRSTRLEN] = { 0 };
                const char* addr_str = inet_ntop(AF_INET, &(socket.sin_addr), buf, INET_ADDRSTRLEN);
                sprintf(ebuf, "ERROR: Peer not allowed to connect: %s\n",
                        (addr_str == NULL) ? "<bad address>" : addr_str);
                dbgsysSocketClose(socketFD);
                socketFD = -1;
                err = JDWPTRANSPORT_ERROR_ILLEGAL_ARGUMENT;
                setLastError(err, ebuf);
            }
        }

        if (socketFD > 0) {
          /* handshake with the debugger */
          err = handshake(socketFD, handshakeTimeout);
        }

        /*
         * If the handshake fails then close the connection. If there if an accept
         * timeout then we must adjust the timeout for the next poll.
         */
        if (err != JDWPTRANSPORT_ERROR_NONE) {
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

    err = parseAddress(addressString, &sa);
    if (err != JDWPTRANSPORT_ERROR_NONE) {
        return err;
    }

    socketFD = dbgsysSocket(AF_INET, SOCK_STREAM, 0);
    if (socketFD < 0) {
        RETURN_IO_ERROR("unable to create socket");
    }

    err = setOptionsCommon(socketFD);
    if (err) {
        return err;
    }

    /*
     * We don't call setReuseAddrOption() for the non-server socket
     * case. If we start seeing EADDRINUSE due to collisions in free
     * ports then we should retry the dbgsysConnect() a few times.
     */

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
#ifdef _AIX
    /*
      AIX needs a workaround for I/O cancellation, see:
      http://publib.boulder.ibm.com/infocenter/pseries/v5r3/index.jsp?topic=/com.ibm.aix.basetechref/doc/basetrf1/close.htm
      ...
      The close subroutine is blocked until all subroutines which use the file
      descriptor return to usr space. For example, when a thread is calling close
      and another thread is calling select with the same file descriptor, the
      close subroutine does not return until the select call returns.
      ...
    */
    shutdown(fd, 2);
#endif
    if (dbgsysSocketClose(fd) < 0) {
        /*
         * close failed - it's pointless to restore socketFD here because
         * any subsequent close will likely fail as well.
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
    char header[JDWP_HEADER_SIZE + MAX_DATA_SIZE];
    jbyte *data;

    /* packet can't be null */
    if (packet == NULL) {
        RETURN_ERROR(JDWPTRANSPORT_ERROR_ILLEGAL_ARGUMENT, "packet is NULL");
    }

    len = packet->type.cmd.len;         /* includes header */
    data_len = len - JDWP_HEADER_SIZE;

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
        memcpy(header + JDWP_HEADER_SIZE, data, data_len);
        if (send_fully(socketFD, (char *)&header, JDWP_HEADER_SIZE + data_len) !=
            JDWP_HEADER_SIZE + data_len) {
            RETURN_IO_ERROR("send failed");
        }
    } else {
        memcpy(header + JDWP_HEADER_SIZE, data, MAX_DATA_SIZE);
        if (send_fully(socketFD, (char *)&header, JDWP_HEADER_SIZE + MAX_DATA_SIZE) !=
            JDWP_HEADER_SIZE + MAX_DATA_SIZE) {
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


    n = recv_fully(socketFD,(char *)&(packet->type.cmd.id), sizeof(jint));
    if (n < (int)sizeof(jint)) {
        RETURN_RECV_ERROR(n);
    }

    packet->type.cmd.id = (jint)dbgsysNetworkToHostLong(packet->type.cmd.id);

    n = recv_fully(socketFD,(char *)&(packet->type.cmd.flags), sizeof(jbyte));
    if (n < (int)sizeof(jbyte)) {
        RETURN_RECV_ERROR(n);
    }

    if (packet->type.cmd.flags & JDWPTRANSPORT_FLAGS_REPLY) {
        n = recv_fully(socketFD,(char *)&(packet->type.reply.errorCode), sizeof(jbyte));
        if (n < (int)sizeof(jshort)) {
            RETURN_RECV_ERROR(n);
        }

        /* FIXME - should the error be converted to host order?? */


    } else {
        n = recv_fully(socketFD,(char *)&(packet->type.cmd.cmdSet), sizeof(jbyte));
        if (n < (int)sizeof(jbyte)) {
            RETURN_RECV_ERROR(n);
        }

        n = recv_fully(socketFD,(char *)&(packet->type.cmd.cmd), sizeof(jbyte));
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

static jdwpTransportError JNICALL
socketTransport_setConfiguration(jdwpTransportEnv* env, jdwpTransportConfiguration* cfg) {
    const char* allowed_peers = NULL;

    if (cfg == NULL) {
        RETURN_ERROR(JDWPTRANSPORT_ERROR_ILLEGAL_ARGUMENT,
                     "NULL pointer to transport configuration is invalid");
    }
    allowed_peers = cfg->allowed_peers;
    _peers_cnt = 0;
    if (allowed_peers != NULL) {
        size_t len = strlen(allowed_peers);
        if (len == 0) { /* Impossible: parseOptions() would reject it */
            fprintf(stderr, "Error in allow option: '%s'\n", allowed_peers);
            RETURN_ERROR(JDWPTRANSPORT_ERROR_ILLEGAL_ARGUMENT,
                         "allow option should not be empty");
        } else if (*allowed_peers == '*') {
            if (len != 1) {
                fprintf(stderr, "Error in allow option: '%s'\n", allowed_peers);
                RETURN_ERROR(JDWPTRANSPORT_ERROR_ILLEGAL_ARGUMENT,
                             "allow option '*' cannot be expanded");
            }
        } else {
            int err = parseAllowedPeers(allowed_peers);
            if (err != JDWPTRANSPORT_ERROR_NONE) {
                return err;
            }
        }
    }
    return JDWPTRANSPORT_ERROR_NONE;
}

JNIEXPORT jint JNICALL
jdwpTransport_OnLoad(JavaVM *vm, jdwpTransportCallback* cbTablePtr,
                     jint version, jdwpTransportEnv** env)
{
    if (version < JDWPTRANSPORT_VERSION_1_0 ||
        version > JDWPTRANSPORT_VERSION_1_1) {
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
    if (version >= JDWPTRANSPORT_VERSION_1_1) {
        interface.SetTransportConfiguration = &socketTransport_setConfiguration;
    }
    *env = &single_env;

    /* initialized TLS */
    tlsIndex = dbgsysTlsAlloc();
    return JNI_OK;
}
