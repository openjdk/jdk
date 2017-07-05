/*
 * Copyright 2001-2003 Sun Microsystems, Inc.  All Rights Reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Sun designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Sun in the LICENSE file that accompanied this code.
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
 */

package com.sun.corba.se.impl.activation;

import java.util.*;
import com.sun.corba.se.impl.orbutil.ORBConstants;

/** ProcessMonitorThread is started when ServerManager is instantiated. The
  * thread wakes up every minute (This can be changed by setting sleepTime) and
  * makes sure that all the processes (Servers) registered with the ServerTool
  * are healthy. If not the state in ServerTableEntry will be changed to
  * De-Activated.
  * Note: This thread can be killed from the main thread by calling
  *       interrupThread()
  */
public class ProcessMonitorThread extends java.lang.Thread {
    private HashMap serverTable;
    private int sleepTime;
    private static ProcessMonitorThread instance = null;

    private ProcessMonitorThread( HashMap ServerTable, int SleepTime ) {
        serverTable = ServerTable;
        sleepTime = SleepTime;
    }

    public void run( ) {
        while( true ) {
            try {
                // Sleep's for a specified time, before checking
                // the Servers health. This will repeat as long as
                // the ServerManager (ORBD) is up and running.
                Thread.sleep( sleepTime );
            } catch( java.lang.InterruptedException e ) {
                break;
            }
            Iterator serverList;
            synchronized ( serverTable ) {
                // Check each ServerTableEntry to make sure that they
                // are in the right state.
                serverList = serverTable.values().iterator();
            }
            try {
                checkServerHealth( serverList );
            } catch( ConcurrentModificationException e ) {
                break;
            }
        }
    }

    private void checkServerHealth( Iterator serverList ) {
        if( serverList == null ) return;
        while (serverList.hasNext( ) ) {
            ServerTableEntry entry = (ServerTableEntry) serverList.next();
            entry.checkProcessHealth( );
        }
    }

    static void start( HashMap serverTable ) {
        int sleepTime = ORBConstants.DEFAULT_SERVER_POLLING_TIME;

        String pollingTime = System.getProperties().getProperty(
            ORBConstants.SERVER_POLLING_TIME );

        if ( pollingTime != null ) {
            try {
                sleepTime = Integer.parseInt( pollingTime );
            } catch (Exception e ) {
                // Too late to complain, Just use the default
                // sleepTime
            }
        }

        instance = new ProcessMonitorThread( serverTable,
            sleepTime );
        instance.setDaemon( true );
        instance.start();
    }

    static void interruptThread( ) {
        instance.interrupt();
    }
}
