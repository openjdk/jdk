/*
 * Copyright (c) 1999, 2007, Oracle and/or its affiliates. All rights reserved.
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



/**
 * Defines generic behaviour for the server
 * part of a connector or an adaptor. Most connectors or adaptors extend <CODE>CommunicatorServer</CODE>
 * and inherit this behaviour. Connectors or adaptors that do not fit into this model do not extend
 * <CODE>CommunicatorServer</CODE>.
 * <p>
 * An <CODE>CommunicatorServer</CODE> is an active object, it listens for client requests
 * and processes them in its own thread. When necessary, a <CODE>CommunicatorServer</CODE>
 * creates other threads to process multiple requests concurrently.
 * <p>
 * A <CODE>CommunicatorServer</CODE> object can be stopped by calling the <CODE>stop</CODE>
 * method. When it is stopped, the <CODE>CommunicatorServer</CODE> no longer listens to client
 * requests and no longer holds any thread or communication resources.
 * It can be started again by calling the <CODE>start</CODE> method.
 * <p>
 * A <CODE>CommunicatorServer</CODE> has a <CODE>state</CODE> property which reflects its
 * activity.
 * <p>
 * <TABLE>
 * <TR><TH>CommunicatorServer</TH>            <TH>State</TH></TR>
 * <TR><TD><CODE>stopped</CODE></TD>          <TD><CODE>OFFLINE</CODE></TD></TR>
 * <TR><TD><CODE>starting</CODE></TD>         <TD><CODE>STARTING</CODE></TD></TR>
 * <TR><TD><CODE>running</CODE></TD>          <TD><CODE>ONLINE</CODE></TD></TR>
 * <TR><TD><CODE>stopping</CODE></TD>         <TD><CODE>STOPPING</CODE></TD></TR>
 * </TABLE>
 * <p>
 * The <CODE>STARTING</CODE> state marks the transition from <CODE>OFFLINE</CODE> to
 * <CODE>ONLINE</CODE>.
 * <p>
 * The <CODE>STOPPING</CODE> state marks the transition from <CODE>ONLINE</CODE> to
 * <CODE>OFFLINE</CODE>. This occurs when the <CODE>CommunicatorServer</CODE> is
 * finishing or interrupting active requests.
 * <p>
 * A <CODE>CommunicatorServer</CODE> may serve several clients concurrently. The
 * number of concurrent clients can be limited using the property
 * <CODE>maxActiveClientCount</CODE>. The default value of this property is
 * defined by the subclasses.
 * <p>
 * When a <CODE>CommunicatorServer</CODE> is unregistered from the MBeanServer,
 * it is stopped automatically.
 *
 * <p><b>This API is a Sun Microsystems internal API  and is subject
 * to change without notice.</b></p>
 */

public interface CommunicatorServerMBean {

    /**
     * Starts this <CODE>CommunicatorServer</CODE>.
     * <p>
     * Has no effect if this <CODE>CommunicatorServer</CODE> is <CODE>ONLINE</CODE> or
     * <CODE>STOPPING</CODE>.
     */
    public void start() ;

    /**
     * Stops this <CODE>CommunicatorServer</CODE>.
     * <p>
     * Has no effect if this <CODE>CommunicatorServer</CODE> is <CODE>OFFLINE</CODE> or
     * <CODE>STOPPING</CODE>.
     */
    public void stop() ;

    /**
     * Tests if the <CODE>CommunicatorServer</CODE> is active.
     *
     * @return True if connector is <CODE>ONLINE</CODE>; false otherwise.
     */
    public boolean isActive() ;

    /**
     * Waits untill either the State attribute of this MBean equals the specified <VAR>state</VAR> parameter,
     * or the specified  <VAR>timeOut</VAR> has elapsed. The method <CODE>waitState</CODE> returns with a boolean value indicating whether
     * the specified <VAR>state</VAR> parameter equals the value of this MBean's State attribute at the time the method terminates.
     *
     * Two special cases for the <VAR>timeOut</VAR> parameter value are:
     * <UL><LI> if <VAR>timeOut</VAR> is negative then <CODE>waitState</CODE> returns immediately (i.e. does not wait at all),</LI>
     * <LI> if <VAR>timeOut</VAR> equals zero then <CODE>waitState</CODE> waits untill the value of this MBean's State attribute
     * is the same as the <VAR>state</VAR> parameter (i.e. will wait indefinitely if this condition is never met).</LI></UL>
     *
     * @param state The value of this MBean's State attribute
     *        to wait for. <VAR>state</VAR> can be one of:
     * <ul>
     * <li><CODE>CommunicatorServer.OFFLINE</CODE>,</li>
     * <li><CODE>CommunicatorServer.ONLINE</CODE>,</li>
     * <li><CODE>CommunicatorServer.STARTING</CODE>,</li>
     * <li><CODE>CommunicatorServer.STOPPING</CODE>.</li>
     * </ul>
     * @param timeOut The maximum time to wait for, in
     *        milliseconds, if positive.
     * Infinite time out if 0, or no waiting at all if negative.
     *
     * @return true if the value of this MBean's State attribute is the
     *  same as the <VAR>state</VAR> parameter; false otherwise.
     */
    public boolean waitState(int state , long timeOut) ;

    /**
     * Gets the state of this <CODE>CommunicatorServer</CODE> as an integer.
     *
     * @return <CODE>ONLINE</CODE>, <CODE>OFFLINE</CODE>, <CODE>STARTING</CODE> or <CODE>STOPPING</CODE>.
     */
    public int getState() ;

    /**
     * Gets the state of this <CODE>CommunicatorServer</CODE> as a string.
     *
     * @return One of the strings "ONLINE", "OFFLINE", "STARTING" or "STOPPING".
     */
    public String getStateString() ;

    /**
     * Gets the host name used by this <CODE>CommunicatorServer</CODE>.
     *
     * @return The host name used by this <CODE>CommunicatorServer</CODE>.
     */
    public String getHost() ;

    /**
     * Gets the port number used by this <CODE>CommunicatorServer</CODE>.
     *
     * @return The port number used by this <CODE>CommunicatorServer</CODE>.
     */
    public int getPort() ;

    /**
     * Sets the port number used by this <CODE>CommunicatorServer</CODE>.
     *
     * @param port The port number used by this <CODE>CommunicatorServer</CODE>.
     *
     * @exception java.lang.IllegalStateException This method has been invoked
     * while the communicator was ONLINE or STARTING.
     */
    public void setPort(int port) throws java.lang.IllegalStateException ;

    /**
     * Gets the protocol being used by this <CODE>CommunicatorServer</CODE>.
     * @return The protocol as a string.
     */
    public abstract String getProtocol() ;
}
