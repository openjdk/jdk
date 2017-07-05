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


package com.sun.jmx.snmp.daemon;



// java import
//
import java.util.Vector;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.logging.Level;
import java.io.InterruptedIOException;
import java.net.DatagramSocket;
import java.net.DatagramPacket;
import java.net.SocketException;

// jmx imports
//
import javax.management.MBeanServer;
import javax.management.ObjectName;
import com.sun.jmx.snmp.SnmpMessage;
import com.sun.jmx.snmp.SnmpPduFactory;
import com.sun.jmx.snmp.SnmpPduBulk;
import com.sun.jmx.snmp.SnmpPduPacket;
import com.sun.jmx.snmp.SnmpPduRequest;
import com.sun.jmx.snmp.SnmpPduTrap;
import com.sun.jmx.snmp.SnmpValue;
import com.sun.jmx.snmp.SnmpVarBind;
import com.sun.jmx.snmp.SnmpVarBindList;
import com.sun.jmx.snmp.SnmpDefinitions;
import com.sun.jmx.snmp.SnmpStatusException;
import com.sun.jmx.snmp.SnmpTooBigException;
import com.sun.jmx.snmp.SnmpDataTypeEnums;

// RI imports
//
import static com.sun.jmx.defaults.JmxProperties.SNMP_ADAPTOR_LOGGER;

// SNMP runtime import
//
import com.sun.jmx.snmp.agent.SnmpMibAgent;
import com.sun.jmx.snmp.agent.SnmpUserDataFactory;
//import com.sun.jmx.snmp.IPAcl.IPAcl;
import com.sun.jmx.snmp.InetAddressAcl;


class SnmpRequestHandler extends ClientHandler implements SnmpDefinitions {

    private transient DatagramSocket       socket = null ;
    private transient DatagramPacket       packet = null ;
    private transient Vector<SnmpMibAgent> mibs = null ;

    /**
     * Contains the list of sub-requests associated to the current request.
     */
    private transient Hashtable<SnmpMibAgent, SnmpSubRequestHandler> subs = null;

    /**
     * Reference on the MIBS
     */
    private transient SnmpMibTree root;

    private transient InetAddressAcl      ipacl = null ;
    private transient SnmpPduFactory      pduFactory = null ;
    private transient SnmpUserDataFactory userDataFactory = null ;
    private transient SnmpAdaptorServer adaptor = null;
    /**
     * Full constructor
     */
    public SnmpRequestHandler(SnmpAdaptorServer server, int id,
                              DatagramSocket s, DatagramPacket p,
                              SnmpMibTree tree, Vector<SnmpMibAgent> m,
                              InetAddressAcl a,
                              SnmpPduFactory factory,
                              SnmpUserDataFactory dataFactory,
                              MBeanServer f, ObjectName n)
    {
        super(server, id, f, n);

        // Need a reference on SnmpAdaptorServer for getNext & getBulk,
        // in case of oid equality (mib overlapping).
        //
        adaptor = server;
        socket = s;
        packet = p;
        root= tree;
        mibs = new Vector<>(m);
        subs= new Hashtable<>(mibs.size());
        ipacl = a;
        pduFactory = factory ;
        userDataFactory = dataFactory ;
        //thread.start();
    }

    /**
     * Treat the request available in 'packet' and send the result
     * back to the client.
     * Note: we overwrite 'packet' with the response bytes.
     */
    @Override
    public void doRun() {

        // Trace the input packet
        //
        if (SNMP_ADAPTOR_LOGGER.isLoggable(Level.FINER)) {
            SNMP_ADAPTOR_LOGGER.logp(Level.FINER, dbgTag,
                    "doRun","Packet received:\n" +
                    SnmpMessage.dumpHexBuffer(packet.getData(), 0, packet.getLength()));
        }

        // Let's build the response packet
        //
        DatagramPacket respPacket = makeResponsePacket(packet) ;

        // Trace the output packet
        //
        if ((SNMP_ADAPTOR_LOGGER.isLoggable(Level.FINER)) && (respPacket != null)) {
            SNMP_ADAPTOR_LOGGER.logp(Level.FINER, dbgTag,
                    "doRun","Packet to be sent:\n" +
                    SnmpMessage.dumpHexBuffer(respPacket.getData(), 0, respPacket.getLength()));
        }

        // Send the response packet if any
        //
        if (respPacket != null) {
            try {
                socket.send(respPacket) ;
            } catch (SocketException e) {
                if (SNMP_ADAPTOR_LOGGER.isLoggable(Level.FINEST)) {
                    if (e.getMessage().equals(InterruptSysCallMsg)) {
                        SNMP_ADAPTOR_LOGGER.logp(Level.FINEST, dbgTag,
                            "doRun", "interrupted");
                    } else {
                      SNMP_ADAPTOR_LOGGER.logp(Level.FINEST, dbgTag,
                            "doRun", "I/O exception", e);
                    }
                }
            } catch(InterruptedIOException e) {
                if (SNMP_ADAPTOR_LOGGER.isLoggable(Level.FINEST)) {
                    SNMP_ADAPTOR_LOGGER.logp(Level.FINEST, dbgTag,
                        "doRun", "interrupted");
                }
            } catch(Exception e) {
                if (SNMP_ADAPTOR_LOGGER.isLoggable(Level.FINEST)) {
                    SNMP_ADAPTOR_LOGGER.logp(Level.FINEST, dbgTag,
                        "doRun", "failure when sending response", e);
                }
            }
        }
    }

    /**
     * Here we make a response packet from a request packet.
     * We return null if there no response packet to sent.
     */
    private DatagramPacket makeResponsePacket(DatagramPacket reqPacket) {
        DatagramPacket respPacket = null ;

        // Transform the request packet into a request SnmpMessage
        //
        SnmpMessage reqMsg = new SnmpMessage() ;
        try {
            reqMsg.decodeMessage(reqPacket.getData(), reqPacket.getLength()) ;
            reqMsg.address = reqPacket.getAddress() ;
            reqMsg.port = reqPacket.getPort() ;
        }
        catch(SnmpStatusException x) {
            if (SNMP_ADAPTOR_LOGGER.isLoggable(Level.FINEST)) {
                SNMP_ADAPTOR_LOGGER.logp(Level.FINEST, dbgTag,
                    "makeResponsePacket", "packet decoding failed", x);
            }
            reqMsg = null ;
            ((SnmpAdaptorServer)adaptorServer).incSnmpInASNParseErrs(1) ;
        }

        // Make the response SnmpMessage if any
        //
        SnmpMessage respMsg = null ;
        if (reqMsg != null) {
            respMsg = makeResponseMessage(reqMsg) ;
        }

        // Try to transform the response SnmpMessage into response packet.
        // NOTE: we overwrite the request packet.
        //
        if (respMsg != null) {
            try {
                reqPacket.setLength(respMsg.encodeMessage(reqPacket.getData())) ;
                respPacket = reqPacket ;
            }
            catch(SnmpTooBigException x) {
                if (SNMP_ADAPTOR_LOGGER.isLoggable(Level.FINEST)) {
                    SNMP_ADAPTOR_LOGGER.logp(Level.FINEST, dbgTag,
                        "makeResponsePacket", "response message is too big");
                }
                try {
                    respMsg = newTooBigMessage(reqMsg) ;
                    reqPacket.setLength(respMsg.encodeMessage(reqPacket.getData())) ;
                    respPacket = reqPacket ;
                }
                catch(SnmpTooBigException xx) {
                    if (SNMP_ADAPTOR_LOGGER.isLoggable(Level.FINEST)) {
                        SNMP_ADAPTOR_LOGGER.logp(Level.FINEST, dbgTag,
                            "makeResponsePacket", "'too big' is 'too big' !!!");
                    }
                    adaptor.incSnmpSilentDrops(1);
                }
            }
        }

        return respPacket ;
    }

    /**
     * Here we make a response message from a request message.
     * We return null if there is no message to reply.
     */
    private SnmpMessage makeResponseMessage(SnmpMessage reqMsg) {
        SnmpMessage respMsg = null ;

        // Transform the request message into a request pdu
        //
        SnmpPduPacket reqPdu;
        Object userData = null;
        try {
            reqPdu = (SnmpPduPacket)pduFactory.decodeSnmpPdu(reqMsg) ;
            if (reqPdu != null && userDataFactory != null)
                userData = userDataFactory.allocateUserData(reqPdu);
        }
        catch(SnmpStatusException x) {
            reqPdu = null ;
            SnmpAdaptorServer snmpServer = (SnmpAdaptorServer)adaptorServer ;
            snmpServer.incSnmpInASNParseErrs(1) ;
            if (x.getStatus()== SnmpDefinitions.snmpWrongSnmpVersion)
                snmpServer.incSnmpInBadVersions(1) ;
            if (SNMP_ADAPTOR_LOGGER.isLoggable(Level.FINEST)) {
                SNMP_ADAPTOR_LOGGER.logp(Level.FINEST, dbgTag,
                    "makeResponseMessage", "message decoding failed", x);
            }
        }

        // Make the response pdu if any
        //
        SnmpPduPacket respPdu = null ;
        if (reqPdu != null) {
            respPdu = makeResponsePdu(reqPdu,userData) ;
            try {
                if (userDataFactory != null)
                    userDataFactory.releaseUserData(userData,respPdu);
            } catch (SnmpStatusException x) {
                respPdu = null;
            }
        }

        // Try to transform the response pdu into a response message if any
        //
        if (respPdu != null) {
            try {
                respMsg = (SnmpMessage)pduFactory.
                    encodeSnmpPdu(respPdu, packet.getData().length) ;
            }
            catch(SnmpStatusException x) {
                respMsg = null ;
                if (SNMP_ADAPTOR_LOGGER.isLoggable(Level.FINEST)) {
                    SNMP_ADAPTOR_LOGGER.logp(Level.FINEST, dbgTag,
                        "makeResponseMessage", "failure when encoding the response message", x);
                }
            }
            catch(SnmpTooBigException x) {
                if (SNMP_ADAPTOR_LOGGER.isLoggable(Level.FINEST)) {
                    SNMP_ADAPTOR_LOGGER.logp(Level.FINEST, dbgTag,
                        "makeResponseMessage", "response message is too big");
                }

                try {
                    // if the PDU is too small, why should we try to do
                    // recovery ?
                    //
                    if (packet.getData().length <=32)
                        throw x;
                    int pos= x.getVarBindCount();
                    if (SNMP_ADAPTOR_LOGGER.isLoggable(Level.FINEST)) {
                        SNMP_ADAPTOR_LOGGER.logp(Level.FINEST, dbgTag,
                            "makeResponseMessage", "fail on element" + pos);
                    }
                    int old;
                    while (true) {
                        try {
                            respPdu = reduceResponsePdu(reqPdu, respPdu, pos) ;
                            respMsg = (SnmpMessage)pduFactory.
                                encodeSnmpPdu(respPdu,
                                              packet.getData().length -32) ;
                            break;
                        } catch (SnmpTooBigException xx) {
                            if (SNMP_ADAPTOR_LOGGER.isLoggable(Level.FINEST)) {
                                SNMP_ADAPTOR_LOGGER.logp(Level.FINEST, dbgTag,
                                    "makeResponseMessage", "response message is still too big");
                            }
                            old= pos;
                            pos= xx.getVarBindCount();
                            if (SNMP_ADAPTOR_LOGGER.isLoggable(Level.FINEST)) {
                                SNMP_ADAPTOR_LOGGER.logp(Level.FINEST, dbgTag,
                                    "makeResponseMessage","fail on element" + pos);
                            }
                            if (pos == old) {
                                // we can not go any further in trying to
                                // reduce the message !
                                //
                                throw xx;
                            }
                        }
                    }// end of loop
                } catch(SnmpStatusException xx) {
                    respMsg = null ;
                    if (SNMP_ADAPTOR_LOGGER.isLoggable(Level.FINEST)) {
                        SNMP_ADAPTOR_LOGGER.logp(Level.FINEST, dbgTag,
                           "makeResponseMessage", "failure when encoding the response message", xx);
                    }
                }
                catch(SnmpTooBigException xx) {
                    try {
                        respPdu = newTooBigPdu(reqPdu) ;
                        respMsg = (SnmpMessage)pduFactory.
                            encodeSnmpPdu(respPdu, packet.getData().length) ;
                    }
                    catch(SnmpTooBigException xxx) {
                        respMsg = null ;
                        if (SNMP_ADAPTOR_LOGGER.isLoggable(Level.FINEST)) {
                            SNMP_ADAPTOR_LOGGER.logp(Level.FINEST, dbgTag,
                               "makeResponseMessage", "'too big' is 'too big' !!!");
                        }
                        adaptor.incSnmpSilentDrops(1);
                    }
                    catch(Exception xxx) {
                        if (SNMP_ADAPTOR_LOGGER.isLoggable(Level.FINEST)) {
                            SNMP_ADAPTOR_LOGGER.logp(Level.FINEST, dbgTag,
                               "makeResponseMessage", "Got unexpected exception", xxx);
                        }
                        respMsg = null ;
                    }
                }
                catch(Exception xx) {
                    if (SNMP_ADAPTOR_LOGGER.isLoggable(Level.FINEST)) {
                        SNMP_ADAPTOR_LOGGER.logp(Level.FINEST, dbgTag,
                           "makeResponseMessage", "Got unexpected exception", xx);
                    }
                    respMsg = null ;
                }
            }
        }
        return respMsg ;
    }

    /**
     * Here we make a response pdu from a request pdu.
     * We return null if there is no pdu to reply.
     */
    private SnmpPduPacket makeResponsePdu(SnmpPduPacket reqPdu,
                                          Object userData) {

        SnmpAdaptorServer snmpServer = (SnmpAdaptorServer)adaptorServer ;
        SnmpPduPacket respPdu = null ;

        snmpServer.updateRequestCounters(reqPdu.type) ;
        if (reqPdu.varBindList != null)
            snmpServer.updateVarCounters(reqPdu.type,
                                         reqPdu.varBindList.length) ;

        if (checkPduType(reqPdu)) {
            respPdu = checkAcl(reqPdu) ;
            if (respPdu == null) { // reqPdu is accepted by ACLs
                if (mibs.size() < 1) {
                    if (SNMP_ADAPTOR_LOGGER.isLoggable(Level.FINER)) {
                        SNMP_ADAPTOR_LOGGER.logp(Level.FINER, dbgTag,
                           "makeResponsePdu", "Request " + reqPdu.requestId +
                           " received but no MIB registered.");
                    }
                    return makeNoMibErrorPdu((SnmpPduRequest)reqPdu, userData);
                }
                switch(reqPdu.type) {
                case SnmpPduPacket.pduGetRequestPdu:
                case SnmpPduPacket.pduGetNextRequestPdu:
                case SnmpPduPacket.pduSetRequestPdu:
                    respPdu = makeGetSetResponsePdu((SnmpPduRequest)reqPdu,
                                                    userData) ;
                    break ;

                case SnmpPduPacket.pduGetBulkRequestPdu:
                    respPdu = makeGetBulkResponsePdu((SnmpPduBulk)reqPdu,
                                                     userData) ;
                    break ;
                }
            }
            else { // reqPdu is rejected by ACLs
                // respPdu contains the error response to be sent.
                // We send this response only if authResEnabled is true.
                if (!snmpServer.getAuthRespEnabled()) { // No response should be sent
                    respPdu = null ;
                }
                if (snmpServer.getAuthTrapEnabled()) { // A trap must be sent
                    try {
                        snmpServer.snmpV1Trap(SnmpPduTrap.
                                              trapAuthenticationFailure, 0,
                                              new SnmpVarBindList()) ;
                    }
                    catch(Exception x) {
                        if (SNMP_ADAPTOR_LOGGER.isLoggable(Level.FINEST)) {
                            SNMP_ADAPTOR_LOGGER.logp(Level.FINEST, dbgTag,
                               "makeResponsePdu", "Failure when sending authentication trap", x);
                        }
                    }
                }
            }
        }
        return respPdu ;
    }

    //
    // Generates a response packet, filling the values in the
    // varbindlist with one of endOfMibView, noSuchObject, noSuchInstance
    // according to the value of <code>status</code>
    //
    // @param statusTag should be one of:
    //        <li>SnmpDataTypeEnums.errEndOfMibViewTag</li>
    //        <li>SnmpDataTypeEnums.errNoSuchObjectTag</li>
    //        <li>SnmpDataTypeEnums.errNoSuchInstanceTag</li>
    //
    SnmpPduPacket makeErrorVarbindPdu(SnmpPduPacket req, int statusTag) {

        final SnmpVarBind[] vblist = req.varBindList;
        final int length = vblist.length;

        switch (statusTag) {
        case SnmpDataTypeEnums.errEndOfMibViewTag:
            for (int i=0 ; i<length ; i++)
                vblist[i].value = SnmpVarBind.endOfMibView;
            break;
        case SnmpDataTypeEnums.errNoSuchObjectTag:
            for (int i=0 ; i<length ; i++)
                vblist[i].value = SnmpVarBind.noSuchObject;
            break;
        case SnmpDataTypeEnums.errNoSuchInstanceTag:
            for (int i=0 ; i<length ; i++)
                vblist[i].value = SnmpVarBind.noSuchInstance;
            break;
        default:
            return newErrorResponsePdu(req,snmpRspGenErr,1);
        }
        return newValidResponsePdu(req,vblist);
    }

    // Generates an appropriate response when no mib is registered in
    // the adaptor.
    //
    // <li>If the version is V1:</li>
    // <ul><li>Generates a NoSuchName error V1 response PDU</li></ul>
    // <li>If the version is V2:</li>
    // <ul><li>If the request is a GET, fills the varbind list with
    //         NoSuchObject's</li>
    //     <li>If the request is a GET-NEXT/GET-BULK, fills the varbind
    //         list with EndOfMibView's</li>
    //     <li>If the request is a SET, generates a NoAccess error V2
    //          response PDU</li>
    // </ul>
    //
    //
    SnmpPduPacket makeNoMibErrorPdu(SnmpPduRequest req, Object userData) {
        // There is no agent registered
        //
        if (req.version == SnmpDefinitions.snmpVersionOne) {
            // Version 1: => NoSuchName
            return
                newErrorResponsePdu(req,snmpRspNoSuchName,1);
        } else if (req.version == SnmpDefinitions.snmpVersionTwo) {
            // Version 2: => depends on PDU type
            switch (req.type) {
            case pduSetRequestPdu :
            case pduWalkRequest :
                // SET request => NoAccess
                return
                    newErrorResponsePdu(req,snmpRspNoAccess,1);
            case pduGetRequestPdu :
                // GET request => NoSuchObject
                return
                    makeErrorVarbindPdu(req,SnmpDataTypeEnums.
                                        errNoSuchObjectTag);
            case pduGetNextRequestPdu :
            case pduGetBulkRequestPdu :
                // GET-NEXT or GET-BULK => EndOfMibView
                return
                    makeErrorVarbindPdu(req,SnmpDataTypeEnums.
                                        errEndOfMibViewTag);
            default:
            }
        }
        // Something wrong here: => snmpRspGenErr
        return newErrorResponsePdu(req,snmpRspGenErr,1);
    }

    /**
     * Here we make the response pdu from a get/set request pdu.
     * At this level, the result is never null.
     */
    private SnmpPduPacket makeGetSetResponsePdu(SnmpPduRequest req,
                                                Object userData) {

        // Create the trhead group specific for handling sub-requests
        // associated to the current request. Use the invoke id
        //
        // Nice idea to use a thread group on a request basis.
        // However the impact on performance is terrible !
        // theGroup= new ThreadGroup(thread.getThreadGroup(),
        //                "request " + String.valueOf(req.requestId));

        // Let's build the varBindList for the response pdu
        //

        if (req.varBindList == null) {
            // Good ! Let's make a full response pdu.
            //
            return newValidResponsePdu(req, null) ;
        }

        // First we need to split the request into subrequests
        //
        splitRequest(req);
        int nbSubRequest= subs.size();
        if (nbSubRequest == 1)
            return turboProcessingGetSet(req,userData);


        // Execute all the subrequests resulting from the split of the
        // varbind list.
        //
        SnmpPduPacket result= executeSubRequest(req,userData);
        if (result != null)
            // It means that an error occurred. The error is already
            // formatted by the executeSubRequest
            // method.
            return result;

        // So far so good. So we need to concatenate all the answers.
        //
        if (SNMP_ADAPTOR_LOGGER.isLoggable(Level.FINER)) {
            SNMP_ADAPTOR_LOGGER.logp(Level.FINER, dbgTag,
               "makeGetSetResponsePdu",
               "Build the unified response for request " + req.requestId);
        }
        return mergeResponses(req);
    }

    /**
     * The method runs all the sub-requests associated to the current
     * instance of SnmpRequestHandler.
     */
    private SnmpPduPacket executeSubRequest(SnmpPduPacket req,
                                            Object userData) {

        int errorStatus = SnmpDefinitions.snmpRspNoError ;

        int i;
        // If it's a set request, we must first check any varBind
        //
        if (req.type == pduSetRequestPdu) {

            i=0;
            for(Enumeration<SnmpSubRequestHandler> e= subs.elements(); e.hasMoreElements() ; i++) {
                // Indicate to the sub request that a check must be invoked ...
                // OK we should have defined out own tag for that !
                //
                SnmpSubRequestHandler sub= e.nextElement();
                sub.setUserData(userData);
                sub.type= pduWalkRequest;

                sub.run();

                sub.type= pduSetRequestPdu;

                if (sub.getErrorStatus() != SnmpDefinitions.snmpRspNoError) {
                    // No point to go any further.
                    //
                    if (SNMP_ADAPTOR_LOGGER.isLoggable(Level.FINEST)) {
                        SNMP_ADAPTOR_LOGGER.logp(Level.FINEST, dbgTag,
                           "executeSubRequest", "an error occurs");
                    }

                    return newErrorResponsePdu(req, errorStatus,
                                               sub.getErrorIndex() + 1) ;
                }
            }
        }// end processing check operation for a set PDU.

        // Let's start the sub-requests.
        //
        i=0;
        for(Enumeration<SnmpSubRequestHandler> e= subs.elements(); e.hasMoreElements() ;i++) {
            SnmpSubRequestHandler sub= e.nextElement();
        /* NPCTE fix for bugId 4492741, esc 0, 16-August 2001 */
            sub.setUserData(userData);
        /* end of NPCTE fix for bugId 4492741 */

            sub.run();

            if (sub.getErrorStatus() != SnmpDefinitions.snmpRspNoError) {
                // No point to go any further.
                //
                if (SNMP_ADAPTOR_LOGGER.isLoggable(Level.FINEST)) {
                    SNMP_ADAPTOR_LOGGER.logp(Level.FINEST, dbgTag,
                       "executeSubRequest", "an error occurs");
                }

                return newErrorResponsePdu(req, errorStatus,
                                           sub.getErrorIndex() + 1) ;
            }
        }

        // everything is ok
        //
        return null;
    }

    /**
     * Optimize when there is only one sub request
     */
    private SnmpPduPacket turboProcessingGetSet(SnmpPduRequest req,
                                                Object userData) {

        int errorStatus;
        SnmpSubRequestHandler sub = subs.elements().nextElement();
        sub.setUserData(userData);

        // Indicate to the sub request that a check must be invoked ...
        // OK we should have defined out own tag for that !
        //
        if (req.type == SnmpDefinitions.pduSetRequestPdu) {
            sub.type= pduWalkRequest;
            sub.run();
            sub.type= pduSetRequestPdu;

            // Check the error status.
            //
            errorStatus= sub.getErrorStatus();
            if (errorStatus != SnmpDefinitions.snmpRspNoError) {
                // No point to go any further.
                //
                return newErrorResponsePdu(req, errorStatus,
                                           sub.getErrorIndex() + 1) ;
            }
        }

        // process the operation
        //

        sub.run();
        errorStatus= sub.getErrorStatus();
        if (errorStatus != SnmpDefinitions.snmpRspNoError) {
            // No point to go any further.
            //
            if (SNMP_ADAPTOR_LOGGER.isLoggable(Level.FINEST)) {
                SNMP_ADAPTOR_LOGGER.logp(Level.FINEST, dbgTag,
                   "turboProcessingGetSet", "an error occurs");
            }
            int realIndex= sub.getErrorIndex() + 1;
            return newErrorResponsePdu(req, errorStatus, realIndex) ;
        }

        // So far so good. So we need to concatenate all the answers.
        //

        if (SNMP_ADAPTOR_LOGGER.isLoggable(Level.FINER)) {
            SNMP_ADAPTOR_LOGGER.logp(Level.FINER, dbgTag,
               "turboProcessingGetSet",  "build the unified response for request "
                + req.requestId);
        }
        return mergeResponses(req);
    }

    /**
     * Here we make the response pdu for a bulk request.
     * At this level, the result is never null.
     */
    private SnmpPduPacket makeGetBulkResponsePdu(SnmpPduBulk req,
                                                 Object userData) {

        SnmpVarBind[] respVarBindList;

        // RFC 1905, Section 4.2.3, p14
        int L = req.varBindList.length ;
        int N = Math.max(Math.min(req.nonRepeaters, L), 0) ;
        int M = Math.max(req.maxRepetitions, 0) ;
        int R = L - N ;

        if (req.varBindList == null) {
            // Good ! Let's make a full response pdu.
            //
            return newValidResponsePdu(req, null) ;
        }

        // Split the request into subrequests.
        //
        splitBulkRequest(req, N, M, R);
        SnmpPduPacket result= executeSubRequest(req,userData);
        if (result != null)
            return result;

        respVarBindList= mergeBulkResponses(N + (M * R));

        // Now we remove useless trailing endOfMibView.
        //
        int m2 ; // respVarBindList[m2] item and next are going to be removed
        int t = respVarBindList.length ;
        while ((t > N) && (respVarBindList[t-1].
                           value.equals(SnmpVarBind.endOfMibView))) {
            t-- ;
        }
        if (t == N)
            m2 = N + R ;
        else
            m2 = N + ((t -1 -N) / R + 2) * R ; // Trivial, of course...
        if (m2 < respVarBindList.length) {
            SnmpVarBind[] truncatedList = new SnmpVarBind[m2] ;
            for (int i = 0 ; i < m2 ; i++) {
                truncatedList[i] = respVarBindList[i] ;
            }
            respVarBindList = truncatedList ;
        }

        // Good ! Let's make a full response pdu.
        //
        return newValidResponsePdu(req, respVarBindList) ;
    }

    /**
     * Check the type of the pdu: only the get/set/bulk request
     * are accepted.
     */
    private boolean checkPduType(SnmpPduPacket pdu) {

        boolean result;

        switch(pdu.type) {

        case SnmpDefinitions.pduGetRequestPdu:
        case SnmpDefinitions.pduGetNextRequestPdu:
        case SnmpDefinitions.pduSetRequestPdu:
        case SnmpDefinitions.pduGetBulkRequestPdu:
            result = true ;
            break;

        default:
            if (SNMP_ADAPTOR_LOGGER.isLoggable(Level.FINEST)) {
                SNMP_ADAPTOR_LOGGER.logp(Level.FINEST, dbgTag,
                   "checkPduType", "cannot respond to this kind of PDU");
            }
            result = false ;
            break;
        }

        return result ;
    }

    /**
     * Check if the specified pdu is conform to the ACL.
     * This method returns null if the pdu is ok. If not, it returns
     * the response pdu to be replied.
     */
    private SnmpPduPacket checkAcl(SnmpPduPacket pdu) {
        SnmpPduPacket response = null ;
        String community = new String(pdu.community) ;

        // We check the pdu type and create an error response if
        // the check failed.
        //
        if (ipacl != null) {
            if (pdu.type == SnmpDefinitions.pduSetRequestPdu) {
                if (!ipacl.checkWritePermission(pdu.address, community)) {
                    if (SNMP_ADAPTOR_LOGGER.isLoggable(Level.FINER)) {
                        SNMP_ADAPTOR_LOGGER.logp(Level.FINER, dbgTag,
                           "checkAcl", "sender is " + pdu.address +
                              " with " + community +". Sender has no write permission");
                    }
                    int err = SnmpSubRequestHandler.
                        mapErrorStatus(SnmpDefinitions.
                                       snmpRspAuthorizationError,
                                       pdu.version, pdu.type);
                    response = newErrorResponsePdu(pdu, err, 0) ;
                }
                else {
                    if (SNMP_ADAPTOR_LOGGER.isLoggable(Level.FINER)) {
                        SNMP_ADAPTOR_LOGGER.logp(Level.FINER, dbgTag,
                           "checkAcl", "sender is " + pdu.address +
                              " with " + community +". Sender has write permission");
                    }
                }
            }
            else {
                if (!ipacl.checkReadPermission(pdu.address, community)) {
                    if (SNMP_ADAPTOR_LOGGER.isLoggable(Level.FINER)) {
                        SNMP_ADAPTOR_LOGGER.logp(Level.FINER, dbgTag,
                           "checkAcl", "sender is " + pdu.address +
                              " with " + community +". Sender has no read permission");
                    }
                    int err = SnmpSubRequestHandler.
                        mapErrorStatus(SnmpDefinitions.
                                       snmpRspAuthorizationError,
                                       pdu.version, pdu.type);
                    response = newErrorResponsePdu(pdu,
                                                   err,
                                                   0);
                    SnmpAdaptorServer snmpServer =
                        (SnmpAdaptorServer)adaptorServer;
                    snmpServer.updateErrorCounters(SnmpDefinitions.
                                                   snmpRspNoSuchName);
                }
                else {
                    if (SNMP_ADAPTOR_LOGGER.isLoggable(Level.FINER)) {
                        SNMP_ADAPTOR_LOGGER.logp(Level.FINER, dbgTag,
                           "checkAcl", "sender is " + pdu.address +
                              " with " + community +". Sender has read permission");
                    }
                }
            }
        }

        // If the response is not null, this means the pdu is rejected.
        // So let's update the statistics.
        //
        if (response != null) {
            SnmpAdaptorServer snmpServer = (SnmpAdaptorServer)adaptorServer ;
            snmpServer.incSnmpInBadCommunityUses(1) ;
            if (ipacl.checkCommunity(community) == false)
                snmpServer.incSnmpInBadCommunityNames(1) ;
        }

        return response ;
    }

    /**
     * Make a response pdu with the specified error status and index.
     * NOTE: the response pdu share its varBindList with the request pdu.
     */
    private SnmpPduRequest newValidResponsePdu(SnmpPduPacket reqPdu,
                                               SnmpVarBind[] varBindList) {
        SnmpPduRequest result = new SnmpPduRequest() ;

        result.address = reqPdu.address ;
        result.port = reqPdu.port ;
        result.version = reqPdu.version ;
        result.community = reqPdu.community ;
        result.type = SnmpPduRequest.pduGetResponsePdu ;
        result.requestId = reqPdu.requestId ;
        result.errorStatus = SnmpDefinitions.snmpRspNoError ;
        result.errorIndex = 0 ;
        result.varBindList = varBindList ;

        ((SnmpAdaptorServer)adaptorServer).
            updateErrorCounters(result.errorStatus) ;

        return result ;
    }

    /**
     * Make a response pdu with the specified error status and index.
     * NOTE: the response pdu share its varBindList with the request pdu.
     */
    private SnmpPduRequest newErrorResponsePdu(SnmpPduPacket req,int s,int i) {
        SnmpPduRequest result = newValidResponsePdu(req, null) ;
        result.errorStatus = s ;
        result.errorIndex = i ;
        result.varBindList = req.varBindList ;

        ((SnmpAdaptorServer)adaptorServer).
            updateErrorCounters(result.errorStatus) ;

        return result ;
    }

    private SnmpMessage newTooBigMessage(SnmpMessage reqMsg)
        throws SnmpTooBigException {
        SnmpMessage result = null ;
        SnmpPduPacket reqPdu;

        try {
            reqPdu = (SnmpPduPacket)pduFactory.decodeSnmpPdu(reqMsg) ;
            if (reqPdu != null) {
                SnmpPduPacket respPdu = newTooBigPdu(reqPdu) ;
                result = (SnmpMessage)pduFactory.
                    encodeSnmpPdu(respPdu, packet.getData().length) ;
            }
        }
        catch(SnmpStatusException x) {
            // This should not occur because decodeIncomingRequest has normally
            // been successfully called before.
            if (SNMP_ADAPTOR_LOGGER.isLoggable(Level.FINEST)) {
                SNMP_ADAPTOR_LOGGER.logp(Level.FINEST, dbgTag,
                   "newTooBigMessage", "Internal error", x);
            }
            throw new InternalError(x) ;
        }

        return result ;
    }

    private SnmpPduPacket newTooBigPdu(SnmpPduPacket req) {
        SnmpPduRequest result =
            newErrorResponsePdu(req, SnmpDefinitions.snmpRspTooBig, 0) ;
        result.varBindList = null ;
        return result ;
    }

    private SnmpPduPacket reduceResponsePdu(SnmpPduPacket req,
                                            SnmpPduPacket resp,
                                            int acceptedVbCount)
        throws SnmpTooBigException {

        // Reduction can be attempted only on bulk response
        //
        if (req.type != SnmpPduPacket.pduGetBulkRequestPdu) {
            if (SNMP_ADAPTOR_LOGGER.isLoggable(Level.FINEST)) {
                SNMP_ADAPTOR_LOGGER.logp(Level.FINEST, dbgTag,
                   "reduceResponsePdu", "cannot remove anything");
            }
            throw new SnmpTooBigException(acceptedVbCount) ;
        }

        // We're going to reduce the varbind list.
        // First determine which items should be removed.
        // Next duplicate and replace the existing list by the reduced one.
        //
        // acceptedVbCount is the number of varbind which have been
        // successfully encoded before reaching bufferSize:
        //   * when it is >= 2, we split the varbindlist at this
        //     position (-1 to be safe),
        //   * when it is 1, we only put one (big?) item in the varbindlist
        //   * when it is 0 (in fact, acceptedVbCount is not available),
        //     we split the varbindlist by 2.
        //
        int vbCount;
        if (acceptedVbCount >= 3)
            vbCount = Math.min(acceptedVbCount - 1, resp.varBindList.length) ;
        else if (acceptedVbCount == 1)
            vbCount = 1 ;
        else // acceptedCount == 0 ie it is unknown
            vbCount = resp.varBindList.length / 2 ;

        if (vbCount < 1) {
            if (SNMP_ADAPTOR_LOGGER.isLoggable(Level.FINEST)) {
                SNMP_ADAPTOR_LOGGER.logp(Level.FINEST, dbgTag,
                   "reduceResponsePdu", "cannot remove anything");
            }
            throw new SnmpTooBigException(acceptedVbCount) ;
        }
        else {
            SnmpVarBind[] newVbList = new SnmpVarBind[vbCount] ;
            for (int i = 0 ; i < vbCount ; i++) {
                newVbList[i] = resp.varBindList[i] ;
            }
            if (SNMP_ADAPTOR_LOGGER.isLoggable(Level.FINEST)) {
                SNMP_ADAPTOR_LOGGER.logp(Level.FINEST, dbgTag,
                   "reduceResponsePdu", (resp.varBindList.length - newVbList.length) +
                    " items have been removed");
            }
            resp.varBindList = newVbList ;
        }

        return resp ;
    }

    /**
     * The method takes the incoming requests and split it into subrequests.
     */
    private void splitRequest(SnmpPduRequest req) {

        int nbAgents= mibs.size();
        SnmpMibAgent agent = mibs.firstElement();
        if (nbAgents == 1) {
            // Take all the oids contained in the request and
            //
            subs.put(agent, new SnmpSubRequestHandler(agent, req, true));
            return;
        }

        // For the get next operation we are going to send the varbind list
        // to all agents
        //
        if (req.type == pduGetNextRequestPdu) {
            for(Enumeration<SnmpMibAgent> e= mibs.elements(); e.hasMoreElements(); ) {
                final SnmpMibAgent ag= e.nextElement();
                subs.put(ag, new SnmpSubNextRequestHandler(adaptor, ag, req));
            }
            return;
        }

        int nbReqs= req.varBindList.length;
        SnmpVarBind[] vars= req.varBindList;
        SnmpSubRequestHandler sub;
        for(int i=0; i < nbReqs; i++) {
            agent= root.getAgentMib(vars[i].oid);
            sub= subs.get(agent);
            if (sub == null) {
                // We need to create the sub request handler and update
                // the hashtable
                //
                sub= new SnmpSubRequestHandler(agent, req);
                subs.put(agent, sub);
            }

            // Update the translation table within the subrequest
            //
            sub.updateRequest(vars[i], i);
        }
    }

    /**
     * The method takes the incoming get bulk requests and split it into
     * subrequests.
     */
    private void splitBulkRequest(SnmpPduBulk req,
                                  int nonRepeaters,
                                  int maxRepetitions,
                                  int R) {
        // Send the getBulk to all agents
        //
        for(Enumeration<SnmpMibAgent> e= mibs.elements(); e.hasMoreElements(); ) {
            final SnmpMibAgent agent = e.nextElement();

            if (SNMP_ADAPTOR_LOGGER.isLoggable(Level.FINER)) {
                SNMP_ADAPTOR_LOGGER.logp(Level.FINER, dbgTag,
                   "splitBulkRequest", "Create a sub with : " + agent + " " + nonRepeaters
                   + " " + maxRepetitions + " " + R);
            }

            subs.put(agent,
                     new SnmpSubBulkRequestHandler(adaptor,
                                                   agent,
                                                   req,
                                                   nonRepeaters,
                                                   maxRepetitions,
                                                   R));
        }
    }

    private SnmpPduPacket mergeResponses(SnmpPduRequest req) {

        if (req.type == pduGetNextRequestPdu) {
            return mergeNextResponses(req);
        }

        SnmpVarBind[] result= req.varBindList;

        // Go through the list of subrequests and concatenate.
        // Hopefully, by now all the sub-requests should be finished
        //
        for(Enumeration<SnmpSubRequestHandler> e= subs.elements(); e.hasMoreElements();) {
            SnmpSubRequestHandler sub= e.nextElement();
            sub.updateResult(result);
        }
        return newValidResponsePdu(req,result);
    }

    private SnmpPduPacket mergeNextResponses(SnmpPduRequest req) {
        int max= req.varBindList.length;
        SnmpVarBind[] result= new SnmpVarBind[max];

        // Go through the list of subrequests and concatenate.
        // Hopefully, by now all the sub-requests should be finished
        //
        for(Enumeration<SnmpSubRequestHandler> e= subs.elements(); e.hasMoreElements();) {
            SnmpSubRequestHandler sub= e.nextElement();
            sub.updateResult(result);
        }

        if (req.version == snmpVersionTwo) {
            return newValidResponsePdu(req,result);
        }

        // In v1 make sure there is no endOfMibView ...
        //
        for(int i=0; i < max; i++) {
            SnmpValue val= result[i].value;
            if (val == SnmpVarBind.endOfMibView)
                return newErrorResponsePdu(req,
                                   SnmpDefinitions.snmpRspNoSuchName, i+1);
        }

        // So far so good ...
        //
        return newValidResponsePdu(req,result);
    }

    private SnmpVarBind[] mergeBulkResponses(int size) {
        // Let's allocate the array for storing the result
        //
        SnmpVarBind[] result= new SnmpVarBind[size];
        for(int i= size-1; i >=0; --i) {
            result[i]= new SnmpVarBind();
            result[i].value= SnmpVarBind.endOfMibView;
        }

        // Go through the list of subrequests and concatenate.
        // Hopefully, by now all the sub-requests should be finished
        //
        for(Enumeration<SnmpSubRequestHandler> e= subs.elements(); e.hasMoreElements();) {
            SnmpSubRequestHandler sub= e.nextElement();
            sub.updateResult(result);
        }

        return result;
    }

    @Override
    protected String makeDebugTag() {
        return "SnmpRequestHandler[" + adaptorServer.getProtocol() + ":" +
            adaptorServer.getPort() + "]";
    }

    @Override
    Thread createThread(Runnable r) {
        return null;
    }

    static final private String InterruptSysCallMsg =
        "Interrupted system call";
}
