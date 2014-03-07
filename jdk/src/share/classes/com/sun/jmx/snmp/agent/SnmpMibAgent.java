/*
 * Copyright (c) 1998, 2013, Oracle and/or its affiliates. All rights reserved.
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


package com.sun.jmx.snmp.agent;



// java imports
//
import java.io.Serializable;
import java.util.Vector;
import java.util.Enumeration;

// jmx imports
//
import javax.management.MBeanServer;
import javax.management.MBeanRegistration;
import javax.management.ObjectName;
import javax.management.InstanceNotFoundException;
import javax.management.ServiceNotFoundException;
import javax.management.ReflectionException;
import javax.management.MBeanException;
import com.sun.jmx.snmp.SnmpVarBind;
import com.sun.jmx.snmp.SnmpDefinitions;
import com.sun.jmx.snmp.SnmpStatusException;
import com.sun.jmx.snmp.SnmpPdu;
import com.sun.jmx.snmp.SnmpOid;
import com.sun.jmx.snmp.SnmpEngine;

/**
 * Abstract class for representing an SNMP agent.
 *
 * The class is used by the SNMP protocol adaptor as the entry point in
 * the SNMP agent to query.
 *
 * <p><b>This API is a Sun Microsystems internal API  and is subject
 * to change without notice.</b></p>
 */

public abstract class SnmpMibAgent
    implements SnmpMibAgentMBean, MBeanRegistration, Serializable {

    /**
     * Default constructor.
     */
    public SnmpMibAgent() {
    }

    // ---------------------------------------------------------------------
    // PUBLIC METHODS
    //----------------------------------------------------------------------

    /**
     * Initializes the MIB (with no registration of the MBeans into the
     * MBean server).
     *
     * @exception IllegalAccessException The MIB can not be initialized.
     */
    public abstract void init() throws IllegalAccessException;

    /**
     * Initializes the MIB but each single MBean representing the MIB
     * is inserted into the MBean server.
     *
     * @param server The MBean server to register the service with.
     * @param name The object name.
     *
     * @return The name of the SNMP MIB registered.
     *
     * @exception java.lang.Exception
     */
    @Override
    public abstract ObjectName preRegister(MBeanServer server,
                                           ObjectName name)
        throws java.lang.Exception;

    /**
     * Not used in this context.
     */
    @Override
    public void postRegister (Boolean registrationDone) {
    }

    /**
     * Not used in this context.
     */
    @Override
    public void preDeregister() throws java.lang.Exception {
    }

    /**
     * Not used in this context.
     */
    @Override
    public void postDeregister() {
    }

    /**
     * Processes a <CODE>get</CODE> operation.
     * This method must update the SnmpVarBinds contained in the
     * <var>{@link SnmpMibRequest} req</var> parameter.
     *
     * @param req The SnmpMibRequest object holding the list of variable to
     *            be retrieved. This list is composed of
     *            <CODE>SnmpVarBind</CODE> objects.
     *
     * @exception SnmpStatusException An error occurred during the operation.
     */
    @Override
    public abstract void get(SnmpMibRequest req)
        throws SnmpStatusException;

    /**
     * Processes a <CODE>getNext</CODE> operation.
     * This method must update the SnmpVarBinds contained in the
     * <var>{@link SnmpMibRequest} req</var> parameter.
     *
     * @param req The SnmpMibRequest object holding the list of
     *            OIDs from which the next variables should be retrieved.
     *            This list is composed of <CODE>SnmpVarBind</CODE> objects.
     *
     * @exception SnmpStatusException An error occurred during the operation.
     */
    @Override
    public abstract void getNext(SnmpMibRequest req)
        throws SnmpStatusException;

    /**
     * Processes a <CODE>getBulk</CODE> operation.
     * This method must update the SnmpVarBinds contained in the
     * <var>{@link SnmpMibRequest} req</var> parameter.
     *
     * @param req The SnmpMibRequest object holding the list of variable to
     *            be retrieved. This list is composed of
     *            <CODE>SnmpVarBind</CODE> objects.
     *
     * @param nonRepeat The number of variables, starting with the first
     *    variable in the variable-bindings, for which a single
     *    lexicographic successor is requested.
     *
     * @param maxRepeat The number of lexicographic successors requested
     *    for each of the last R variables. R is the number of variables
     *    following the first <CODE>nonRepeat</CODE> variables for which
     *    multiple lexicographic successors are requested.
     *
     * @exception SnmpStatusException An error occurred during the operation.
     */
    @Override
    public abstract void getBulk(SnmpMibRequest req, int nonRepeat,
                                 int maxRepeat)
        throws SnmpStatusException;

    /**
     * Processes a <CODE>set</CODE> operation.
     * This method must update the SnmpVarBinds contained in the
     * <var>{@link SnmpMibRequest} req</var> parameter.
     * This method is called during the second phase of the SET two-phase
     * commit.
     *
     * @param req The SnmpMibRequest object holding the list of variable to
     *            be set. This list is composed of
     *            <CODE>SnmpVarBind</CODE> objects.
     *
     * @exception SnmpStatusException An error occurred during the operation.
     *            Throwing an exception in this method will break the
     *            atomicity of the SET operation. Care must be taken so that
     *            the exception is thrown in the {@link #check(SnmpMibRequest)}
     *            method instead.
     */
    @Override
    public abstract void set(SnmpMibRequest req)
        throws SnmpStatusException;


    /**
     * Checks if a <CODE>set</CODE> operation can be performed.
     * If the operation can not be performed, the method should throw an
     * <CODE>SnmpStatusException</CODE>.
     * This method is called during the first phase of the SET two-phase
     * commit.
     *
     * @param req The SnmpMibRequest object holding the list of variable to
     *            be set. This list is composed of
     *            <CODE>SnmpVarBind</CODE> objects.
     *
     * @exception SnmpStatusException The <CODE>set</CODE> operation
     *    cannot be performed.
     */
    @Override
    public abstract void check(SnmpMibRequest req)
        throws SnmpStatusException;

    /**
     * Gets the root object identifier of the MIB.
     * <P>The root object identifier is the object identifier uniquely
     * identifying the MIB.
     *
     * @return The root object identifier.
     */
    public abstract long[] getRootOid();

    // ---------------------------------------------------------------------
    // GETTERS AND SETTERS
    // ---------------------------------------------------------------------

    /**
     * Gets the reference to the MBean server in which the SNMP MIB is
     * registered.
     *
     * @return The MBean server or null if the MIB is not registered in any
     *     MBean server.
     */
    @Override
    public MBeanServer getMBeanServer() {
        return server;
    }

    /**
     * Gets the reference to the SNMP protocol adaptor to which the MIB is
     * bound.
     *
     * @return The SNMP MIB handler.
     */
    @Override
    public SnmpMibHandler getSnmpAdaptor() {
        return adaptor;
    }

    /**
     * Sets the reference to the SNMP protocol adaptor through which the MIB
     * will be SNMP accessible and add this new MIB in the SNMP MIB handler.
     *
     * @param stack The SNMP MIB handler.
     */
    @Override
    public void setSnmpAdaptor(SnmpMibHandler stack) {
        if (adaptor != null) {
            adaptor.removeMib(this);
        }
        adaptor = stack;
        if (adaptor != null) {
            adaptor.addMib(this);
        }
    }

     /**
     * Sets the reference to the SNMP protocol adaptor through which the MIB
     * will be SNMP accessible and add this new MIB in the SNMP MIB handler.
     * This method is to be called to set a specific agent to a specific OID. This can be useful when dealing with MIB overlapping.
     * Some OID can be implemented in more than one MIB. In this case, the OID nearest the agent will be used on SNMP operations.
     * @param stack The SNMP MIB handler.
     * @param oids The set of OIDs this agent implements.
     *
     * @since 1.5
     */
    @Override
    public void setSnmpAdaptor(SnmpMibHandler stack, SnmpOid[] oids) {
        if (adaptor != null) {
            adaptor.removeMib(this);
        }
        adaptor = stack;
        if (adaptor != null) {
            adaptor.addMib(this, oids);
        }
    }

    /**
     * Sets the reference to the SNMP protocol adaptor through which the MIB
     * will be SNMP accessible and adds this new MIB in the SNMP MIB handler.
     * Adds a new contextualized MIB in the SNMP MIB handler.
     *
     * @param stack The SNMP MIB handler.
     * @param contextName The MIB context name. If null is passed, will be registered in the default context.
     *
     * @exception IllegalArgumentException If the parameter is null.
     *
     * @since 1.5
     */
    @Override
    public void setSnmpAdaptor(SnmpMibHandler stack, String contextName) {
        if (adaptor != null) {
            adaptor.removeMib(this, contextName);
        }
        adaptor = stack;
        if (adaptor != null) {
            adaptor.addMib(this, contextName);
        }
    }
    /**
     * Sets the reference to the SNMP protocol adaptor through which the MIB
     * will be SNMP accessible and adds this new MIB in the SNMP MIB handler.
     * Adds a new contextualized MIB in the SNMP MIB handler.
     *
     * @param stack The SNMP MIB handler.
     * @param contextName The MIB context name. If null is passed, will be registered in the default context.
     * @param oids The set of OIDs this agent implements.
     * @exception IllegalArgumentException If the parameter is null.
     *
     * @since 1.5
     */
    @Override
    public void setSnmpAdaptor(SnmpMibHandler stack,
                               String contextName,
                               SnmpOid[] oids) {
        if (adaptor != null) {
            adaptor.removeMib(this, contextName);
        }
        adaptor = stack;
        if (adaptor != null) {
            adaptor.addMib(this, contextName, oids);
        }
    }

    /**
     * Gets the object name of the SNMP protocol adaptor to which the MIB
     * is bound.
     *
     * @return The name of the SNMP protocol adaptor.
     */
    @Override
    public ObjectName getSnmpAdaptorName() {
        return adaptorName;
    }

    /**
     * Sets the reference to the SNMP protocol adaptor through which the MIB
     * will be SNMP accessible and add this new MIB in the SNMP MIB handler
     * associated to the specified <CODE>name</CODE>.
     *
     * @param name The name of the SNMP protocol adaptor.
     *
     * @exception InstanceNotFoundException The SNMP protocol adaptor does
     *     not exist in the MBean server.
     *
     * @exception ServiceNotFoundException This SNMP MIB is not registered
     *     in the MBean server or the requested service is not supported.
     */
    @Override
    public void setSnmpAdaptorName(ObjectName name)
        throws InstanceNotFoundException, ServiceNotFoundException {

        if (server == null) {
            throw new ServiceNotFoundException(mibName + " is not registered in the MBean server");
        }
        // First remove the reference on the old adaptor server.
        //
        if (adaptor != null) {
            adaptor.removeMib(this);
        }

        // Then update the reference to the new adaptor server.
        //
        Object[] params = {this};
        String[] signature = {"com.sun.jmx.snmp.agent.SnmpMibAgent"};
        try {
            adaptor = (SnmpMibHandler)(server.invoke(name, "addMib", params,
                                                     signature));
        } catch (InstanceNotFoundException e) {
            throw new InstanceNotFoundException(name.toString());
        } catch (ReflectionException e) {
            throw new ServiceNotFoundException(name.toString());
        } catch (MBeanException e) {
            // Should never occur...
        }

        adaptorName = name;
    }
    /**
     * Sets the reference to the SNMP protocol adaptor through which the MIB
     * will be SNMP accessible and add this new MIB in the SNMP MIB handler
     * associated to the specified <CODE>name</CODE>.
     * This method is to be called to set a specific agent to a specific OID. This can be useful when dealing with MIB overlapping.
     * Some OID can be implemented in more than one MIB. In this case, the OID nearer agent will be used on SNMP operations.
     * @param name The name of the SNMP protocol adaptor.
     * @param oids The set of OIDs this agent implements.
     * @exception InstanceNotFoundException The SNMP protocol adaptor does
     *     not exist in the MBean server.
     *
     * @exception ServiceNotFoundException This SNMP MIB is not registered
     *     in the MBean server or the requested service is not supported.
     *
     * @since 1.5
     */
    @Override
    public void setSnmpAdaptorName(ObjectName name, SnmpOid[] oids)
        throws InstanceNotFoundException, ServiceNotFoundException {

        if (server == null) {
            throw new ServiceNotFoundException(mibName + " is not registered in the MBean server");
        }
        // First remove the reference on the old adaptor server.
        //
        if (adaptor != null) {
            adaptor.removeMib(this);
        }

        // Then update the reference to the new adaptor server.
        //
        Object[] params = {this, oids};
        String[] signature = {"com.sun.jmx.snmp.agent.SnmpMibAgent",
        oids.getClass().getName()};
        try {
            adaptor = (SnmpMibHandler)(server.invoke(name, "addMib", params,
                                                     signature));
        } catch (InstanceNotFoundException e) {
            throw new InstanceNotFoundException(name.toString());
        } catch (ReflectionException e) {
            throw new ServiceNotFoundException(name.toString());
        } catch (MBeanException e) {
            // Should never occur...
        }

        adaptorName = name;
    }
    /**
     * Sets the reference to the SNMP protocol adaptor through which the MIB
     * will be SNMP accessible and add this new MIB in the SNMP MIB handler
     * associated to the specified <CODE>name</CODE>.
     *
     * @param name The name of the SNMP protocol adaptor.
     * @param contextName The MIB context name. If null is passed, will be registered in the default context.
     * @exception InstanceNotFoundException The SNMP protocol adaptor does
     *     not exist in the MBean server.
     *
     * @exception ServiceNotFoundException This SNMP MIB is not registered
     *     in the MBean server or the requested service is not supported.
     *
     * @since 1.5
     */
    @Override
    public void setSnmpAdaptorName(ObjectName name, String contextName)
        throws InstanceNotFoundException, ServiceNotFoundException {

        if (server == null) {
            throw new ServiceNotFoundException(mibName + " is not registered in the MBean server");
        }

        // First remove the reference on the old adaptor server.
        //
        if (adaptor != null) {
            adaptor.removeMib(this, contextName);
        }

        // Then update the reference to the new adaptor server.
        //
        Object[] params = {this, contextName};
        String[] signature = {"com.sun.jmx.snmp.agent.SnmpMibAgent", "java.lang.String"};
        try {
            adaptor = (SnmpMibHandler)(server.invoke(name, "addMib", params,
                                                     signature));
        } catch (InstanceNotFoundException e) {
            throw new InstanceNotFoundException(name.toString());
        } catch (ReflectionException e) {
            throw new ServiceNotFoundException(name.toString());
        } catch (MBeanException e) {
            // Should never occur...
        }

        adaptorName = name;
    }

    /**
     * Sets the reference to the SNMP protocol adaptor through which the MIB
     * will be SNMP accessible and add this new MIB in the SNMP MIB handler
     * associated to the specified <CODE>name</CODE>.
     *
     * @param name The name of the SNMP protocol adaptor.
     * @param contextName The MIB context name. If null is passed, will be registered in the default context.
     * @param oids The set of OIDs this agent implements.
     * @exception InstanceNotFoundException The SNMP protocol adaptor does
     *     not exist in the MBean server.
     *
     * @exception ServiceNotFoundException This SNMP MIB is not registered
     *     in the MBean server or the requested service is not supported.
     *
     * @since 1.5
     */
    @Override
    public void setSnmpAdaptorName(ObjectName name,
                                   String contextName, SnmpOid[] oids)
        throws InstanceNotFoundException, ServiceNotFoundException {

        if (server == null) {
            throw new ServiceNotFoundException(mibName + " is not registered in the MBean server");
        }

        // First remove the reference on the old adaptor server.
        //
        if (adaptor != null) {
            adaptor.removeMib(this, contextName);
        }

        // Then update the reference to the new adaptor server.
        //
        Object[] params = {this, contextName, oids};
        String[] signature = {"com.sun.jmx.snmp.agent.SnmpMibAgent", "java.lang.String", oids.getClass().getName()};
        try {
            adaptor = (SnmpMibHandler)(server.invoke(name, "addMib", params,
                                                     signature));
        } catch (InstanceNotFoundException e) {
            throw new InstanceNotFoundException(name.toString());
        } catch (ReflectionException e) {
            throw new ServiceNotFoundException(name.toString());
        } catch (MBeanException e) {
            // Should never occur...
        }

        adaptorName = name;
    }

    /**
     * Indicates whether or not the MIB module is bound to a SNMP protocol
     * adaptor.
     * As a reminder, only bound MIBs can be accessed through SNMP protocol
     * adaptor.
     *
     * @return <CODE>true</CODE> if the MIB module is bound,
     *         <CODE>false</CODE> otherwise.
     */
    @Override
    public boolean getBindingState() {
        if (adaptor == null)
            return false;
        else
            return true;
    }

    /**
     * Gets the MIB name.
     *
     * @return The MIB name.
     */
    @Override
    public String getMibName() {
        return mibName;
    }

    /**
     * This is a factory method for creating new SnmpMibRequest objects.
     * @param reqPdu The received PDU.
     * @param vblist   The vector of SnmpVarBind objects in which the
     *        MIB concerned by this request is involved.
     * @param version  The protocol version of the SNMP request.
     * @param userData User allocated contextual data.
     *
     * @return A new SnmpMibRequest object.
     *
     * @since 1.5
     **/
    public static SnmpMibRequest newMibRequest(SnmpPdu reqPdu,
                                               Vector<SnmpVarBind> vblist,
                                               int version,
                                               Object userData)
    {
        return new SnmpMibRequestImpl(null,
                                      reqPdu,
                                      vblist,
                                      version,
                                      userData,
                                      null,
                                      SnmpDefinitions.noAuthNoPriv,
                                      getSecurityModel(version),
                                      null,null);
    }
    /**
     * This is a factory method for creating new SnmpMibRequest objects.
     * @param engine The local engine.
     * @param reqPdu The received pdu.
     * @param vblist The vector of SnmpVarBind objects in which the
     *        MIB concerned by this request is involved.
     * @param version The protocol version of the SNMP request.
     * @param userData User allocated contextual data.
     *
     * @return A new SnmpMibRequest object.
     *
     * @since 1.5
     **/
    public static SnmpMibRequest newMibRequest(SnmpEngine engine,
                                               SnmpPdu reqPdu,
                                               Vector<SnmpVarBind> vblist,
                                               int version,
                                               Object userData,
                                               String principal,
                                               int securityLevel,
                                               int securityModel,
                                               byte[] contextName,
                                               byte[] accessContextName) {
        return new SnmpMibRequestImpl(engine,
                                      reqPdu,
                                      vblist,
                                      version,
                                      userData,
                                      principal,
                                      securityLevel,
                                      securityModel,
                                      contextName,
                                      accessContextName);
    }
    // ---------------------------------------------------------------------
    // PACKAGE METHODS
    // ---------------------------------------------------------------------

    /**
     * Processes a <CODE>getBulk</CODE> operation using call to
     * <CODE>getNext</CODE>.
     * The method implements the <CODE>getBulk</CODE> operation by calling
     * appropriately the <CODE>getNext</CODE> method.
     *
     * @param req The SnmpMibRequest containing the variable list to be
     *        retrieved.
     *
     * @param nonRepeat The number of variables, starting with the first
     *    variable in the variable-bindings, for which a single lexicographic
     *    successor is requested.
     *
     * @param maxRepeat The number of lexicographic successors
     *    requested for each of the last R variables. R is the number of
     *    variables following the first nonRepeat variables for which
     *    multiple lexicographic successors are requested.
     *
     * @return The variable list containing returned values.
     *
     * @exception SnmpStatusException An error occurred during the operation.
     */
    void getBulkWithGetNext(SnmpMibRequest req, int nonRepeat, int maxRepeat)
        throws SnmpStatusException {
        final Vector<SnmpVarBind> list = req.getSubList();

        // RFC 1905, Section 4.2.3, p14
        final int L = list.size() ;
        final int N = Math.max(Math.min(nonRepeat, L), 0) ;
        final int M = Math.max(maxRepeat, 0) ;
        final int R = L - N ;

        // Let's build the varBindList for the response pdu
        //
        // int errorStatus = SnmpDefinitions.snmpRspNoError ;
        // int errorIndex = 0 ;
        if (L != 0) {

            // Non-repeaters and first row of repeaters
            //
            getNext(req);

            // Now the remaining repeaters
            //
            Vector<SnmpVarBind> repeaters= splitFrom(list, N);
            SnmpMibRequestImpl repeatedReq =
                new SnmpMibRequestImpl(req.getEngine(),
                                       req.getPdu(),
                                       repeaters,
                                       SnmpDefinitions.snmpVersionTwo,
                                       req.getUserData(),
                                       req.getPrincipal(),
                                       req.getSecurityLevel(),
                                       req.getSecurityModel(),
                                       req.getContextName(),
                                       req.getAccessContextName());
            for (int i = 2 ; i <= M ; i++) {
                getNext(repeatedReq);
                concatVector(req, repeaters);
            }
        }
    }


    // ---------------------------------------------------------------------
    // PRIVATE METHODS
    // ---------------------------------------------------------------------

    /**
     * This method creates a new Vector which does not contain the first
     * element up to the specified limit.
     *
     * @param original The original vector.
     * @param limit The limit.
     */
    private Vector<SnmpVarBind> splitFrom(Vector<SnmpVarBind> original, int limit) {

        int max= original.size();
        Vector<SnmpVarBind> result= new Vector<>(max - limit);
        int i= limit;

        // Ok the loop looks a bit strange. But in order to improve the
        // perf, we try to avoid reference to the limit variable from
        // within the loop ...
        //
        for(Enumeration<SnmpVarBind> e= original.elements(); e.hasMoreElements(); --i) {
            SnmpVarBind var= e.nextElement();
            if (i >0)
                continue;
            result.addElement(new SnmpVarBind(var.oid, var.value));
        }
        return result;
    }

    private void concatVector(SnmpMibRequest req, Vector<SnmpVarBind> source) {
        for(Enumeration<SnmpVarBind> e= source.elements(); e.hasMoreElements(); ) {
            SnmpVarBind var= e.nextElement();
            // We need to duplicate the SnmpVarBind otherwise it is going
            // to be overloaded by the next get Next ...
            req.addVarBind(new SnmpVarBind(var.oid, var.value));
        }
    }

    private static int getSecurityModel(int version) {
        switch(version) {
        case SnmpDefinitions.snmpVersionOne:
            return SnmpDefinitions.snmpV1SecurityModel;
        default:
            return SnmpDefinitions.snmpV2SecurityModel;
        }
    }

    // ---------------------------------------------------------------------
    // PROTECTED VARIABLES
    // ---------------------------------------------------------------------

    /**
     * The object name of the MIB.
     * @serial
     */
    protected String mibName;

    /**
     * The reference to the MBean server.
     * @serial
     */
    protected MBeanServer server;

    // ---------------------------------------------------------------------
    // PRIVATE VARIABLES
    // ---------------------------------------------------------------------

    /**
     * The object name of the SNMP protocol adaptor.
     * @serial
     */
    private ObjectName adaptorName;

    /**
     * The reference to the SNMP stack.
     */
    private transient SnmpMibHandler adaptor;
}
