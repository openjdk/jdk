/*
 * Copyright (c) 1999, 2006, Oracle and/or its affiliates. All rights reserved.
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


package com.sun.jmx.snmp.daemon;



// java import
//
import java.io.*;
import java.util.logging.Level;

// jmx import
//
import javax.management.MBeanServer;
import javax.management.ObjectName;

// jmx RI import
//
import static com.sun.jmx.defaults.JmxProperties.SNMP_ADAPTOR_LOGGER;

/**
 * The <CODE>ClientHandler</CODE> class is the base class of each
 * adaptor.<p>
 */

abstract class ClientHandler implements Runnable {

    public ClientHandler(CommunicatorServer server, int id, MBeanServer f, ObjectName n) {
        adaptorServer = server ;
        requestId = id ;
        mbs = f ;
        objectName = n ;
        interruptCalled = false ;
        dbgTag = makeDebugTag() ;
        //if (mbs == null ){
        //thread = new Thread (this) ;
        thread =  createThread(this);

        //} else {
        //thread = mbs.getThreadAllocatorSrvIf().obtainThread(objectName,this) ;
        //}
        // Note: the thread will be started by the subclass.
    }

    // thread service
    Thread createThread(Runnable r) {
        return new Thread(this);
    }

    public void interrupt() {
        SNMP_ADAPTOR_LOGGER.entering(dbgTag, "interrupt");
        interruptCalled = true ;
        if (thread != null) {
            thread.interrupt() ;
        }
        SNMP_ADAPTOR_LOGGER.exiting(dbgTag, "interrupt");
    }


    public void join() {
        if (thread != null) {
        try {
            thread.join() ;
        }
        catch(InterruptedException x) {
        }
        }
    }

    public void run() {

        try {
            //
            // Notify the server we are now active
            //
            adaptorServer.notifyClientHandlerCreated(this) ;

            //
            // Call protocol specific sequence
            //
            doRun() ;
        }
        finally {
            //
            // Now notify the adaptor server that the handler is terminating.
            // This is important because the server may be blocked waiting for
            // a handler to terminate.
            //
            adaptorServer.notifyClientHandlerDeleted(this) ;
        }
    }

    //
    // The protocol-dependent part of the request
    //
    public abstract void doRun() ;

    protected CommunicatorServer adaptorServer = null ;
    protected int requestId = -1 ;
    protected MBeanServer mbs = null ;
    protected ObjectName objectName = null ;
    protected Thread thread = null ;
    protected boolean interruptCalled = false ;
    protected String dbgTag = null ;

    protected String makeDebugTag() {
        return "ClientHandler[" + adaptorServer.getProtocol() + ":" + adaptorServer.getPort() + "][" + requestId + "]";
    }
}
