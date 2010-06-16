/*
 * Copyright (c) 2000, Oracle and/or its affiliates. All rights reserved.
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

#include <iostream>
#include <winsock2.h>

using namespace std;

void
initWinsock()
{
  static int initted = 0;
  WORD wVersionRequested;
  WSADATA wsaData;
  int err;

  if (!initted) {
    wVersionRequested = MAKEWORD( 2, 0 );

    err = WSAStartup( wVersionRequested, &wsaData );
    if ( err != 0 ) {
      {
        /* Tell the user that we couldn't find a usable */
        /* WinSock DLL.                                 */
        cerr << "SocketBase::SocketBase: unable to find usable "
             << "WinSock DLL" << endl;
        exit(1);
      }
    }

    /* Confirm that the WinSock DLL supports 2.0.*/
    /* Note that if the DLL supports versions greater    */
    /* than 2.0 in addition to 2.0, it will still return */
    /* 2.0 in wVersion since that is the version we      */
    /* requested.                                        */

    if ( LOBYTE( wsaData.wVersion ) != 2 ||
         HIBYTE( wsaData.wVersion ) != 0 ) {
      /* Tell the user that we couldn't find a usable */
      /* WinSock DLL.                                  */
      {
        cerr << "Unable to find suitable version of WinSock DLL" << endl;
        WSACleanup( );
        exit(1);
      }
    }

    initted = 1;
  }
}
