/*
 * Copyright (c) 1998, 2006, Oracle and/or its affiliates. All rights reserved.
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

// java imports
//
import java.util.logging.Level;
import java.util.Vector;

// jmx imports
//
import com.sun.jmx.snmp.SnmpEngine;
import com.sun.jmx.snmp.SnmpPdu;
import com.sun.jmx.snmp.SnmpValue;
import com.sun.jmx.snmp.SnmpVarBind;
import com.sun.jmx.snmp.SnmpVarBindList;
import com.sun.jmx.snmp.SnmpOid;
import com.sun.jmx.snmp.SnmpDefinitions;
import com.sun.jmx.snmp.SnmpStatusException;
// SNMP Runtime import
//
import static com.sun.jmx.defaults.JmxProperties.SNMP_ADAPTOR_LOGGER;
import com.sun.jmx.snmp.agent.SnmpMibAgent;
import com.sun.jmx.snmp.agent.SnmpMibRequest;
import com.sun.jmx.snmp.daemon.SnmpAdaptorServer;
import com.sun.jmx.snmp.internal.SnmpIncomingRequest;

/* NPCTE fix for bugId 4492741, esc 0 */
import com.sun.jmx.snmp.ThreadContext;
/* end of NPCTE fix for bugId 4492741 */

class SnmpSubNextRequestHandler extends SnmpSubRequestHandler {
    private SnmpAdaptorServer server = null;
    /**
     * The constuctor initialize the subrequest with the whole varbind
     * list contained in the original request.
     */
    protected SnmpSubNextRequestHandler(SnmpAdaptorServer server,
                                        SnmpMibAgent agent,
                                        SnmpPdu req) {
        super(agent,req);
        init(req, server);
    }

    protected SnmpSubNextRequestHandler(SnmpEngine engine,
                                        SnmpAdaptorServer server,
                                        SnmpIncomingRequest incRequest,
                                        SnmpMibAgent agent,
                                        SnmpPdu req) {
        super(engine, incRequest, agent, req);
        init(req, server);
        if (SNMP_ADAPTOR_LOGGER.isLoggable(Level.FINEST)) {
            SNMP_ADAPTOR_LOGGER.logp(Level.FINEST, SnmpSubNextRequestHandler.class.getName(),
                "SnmpSubNextRequestHandler", "Constructor : " + this);
        }
    }

    private void init(SnmpPdu req, SnmpAdaptorServer server) {
        this.server = server;

        // The translation table is easy in this case ...
        //
        final int max= translation.length;
        final SnmpVarBind[] list= req.varBindList;
        final NonSyncVector<SnmpVarBind> nonSyncVarBind =
                ((NonSyncVector<SnmpVarBind>)varBind);
        for(int i=0; i < max; i++) {
            translation[i]= i;
            // we need to allocate a new SnmpVarBind. Otherwise the first
            // sub request will modify the list...
            //
            final SnmpVarBind newVarBind =
                new SnmpVarBind(list[i].oid, list[i].value);
            nonSyncVarBind.addNonSyncElement(newVarBind);
        }
    }

    public void run() {

        try {
            /* NPCTE fix for bugId 4492741, esc 0, 16-August-2001 */
            final ThreadContext oldContext =
                ThreadContext.push("SnmpUserData",data);
            try {
                if (SNMP_ADAPTOR_LOGGER.isLoggable(Level.FINER)) {
                    SNMP_ADAPTOR_LOGGER.logp(Level.FINER, SnmpSubRequestHandler.class.getName(),
                        "run", "[" + Thread.currentThread() +
                          "]:getNext operation on " + agent.getMibName());
                }

                // Always call with V2. So the merge of the responses will
                // be easier.
                //
                agent.getNext(createMibRequest(varBind, snmpVersionTwo, data));
            } finally {
                ThreadContext.restore(oldContext);
            }
            /* end of NPCTE fix for bugId 4492741 */


        } catch(SnmpStatusException x) {
            errorStatus = x.getStatus() ;
            errorIndex=  x.getErrorIndex();
            if (SNMP_ADAPTOR_LOGGER.isLoggable(Level.FINEST)) {
                SNMP_ADAPTOR_LOGGER.logp(Level.FINEST, SnmpSubRequestHandler.class.getName(),
                    "run", "[" + Thread.currentThread() +
                      "]:an Snmp error occured during the operation", x);
            }
        }
        catch(Exception x) {
            errorStatus = SnmpDefinitions.snmpRspGenErr ;
            if (SNMP_ADAPTOR_LOGGER.isLoggable(Level.FINEST)) {
                SNMP_ADAPTOR_LOGGER.logp(Level.FINEST, SnmpSubRequestHandler.class.getName(),
                    "run", "[" + Thread.currentThread() +
                      "]:a generic error occured during the operation", x);
            }
        }
        if (SNMP_ADAPTOR_LOGGER.isLoggable(Level.FINER)) {
            SNMP_ADAPTOR_LOGGER.logp(Level.FINER, SnmpSubRequestHandler.class.getName(),
                "run", "[" + Thread.currentThread() +  "]:operation completed");
        }
    }

    /**
     * The method updates the varbind list of the subrequest.
     */
    protected  void updateRequest(SnmpVarBind var, int pos) {
        if (SNMP_ADAPTOR_LOGGER.isLoggable(Level.FINEST)) {
            SNMP_ADAPTOR_LOGGER.logp(Level.FINEST, SnmpSubRequestHandler.class.getName(),
                "updateRequest", "Copy :" + var);
        }
        int size= varBind.size();
        translation[size]= pos;
        final SnmpVarBind newVarBind =
            new SnmpVarBind(var.oid, var.value);
        if (SNMP_ADAPTOR_LOGGER.isLoggable(Level.FINEST)) {
            SNMP_ADAPTOR_LOGGER.logp(Level.FINEST, SnmpSubRequestHandler.class.getName(),
                "updateRequest", "Copied :" + newVarBind);
        }

        varBind.addElement(newVarBind);
    }
    /**
     * The method updates a given var bind list with the result of a
     * previsouly invoked operation.
     * Prior to calling the method, one must make sure that the operation was
     * successful. As such the method getErrorIndex or getErrorStatus should be
     * called.
     */
    protected void updateResult(SnmpVarBind[] result) {

        final int max=varBind.size();
        for(int i= 0; i< max ; i++) {
            // May be we should control the position ...
            //
            final int index= translation[i];
            final SnmpVarBind elmt=
                (SnmpVarBind)((NonSyncVector)varBind).elementAtNonSync(i);

            final SnmpVarBind vb= result[index];
            if (vb == null) {
                result[index]= elmt;
                /* NPCTE fix for bugid 4381195 esc 0. <J.C.> < 17-Oct-2000> */
                // if ((elmt != null) &&  (elmt.value == null) &&
                //    (version == snmpVersionTwo))
                //    elmt.value = SnmpVarBind.endOfMibView;
                /* end of NPCTE fix for bugid 4381195 */
                continue;
            }

            final SnmpValue val= vb.value;
            if ((val == null)|| (val == SnmpVarBind.endOfMibView)){
                /* NPCTE fix for bugid 4381195 esc 0. <J.C.> < 17-Oct-2000> */
                if ((elmt != null) &&
                    (elmt.value != SnmpVarBind.endOfMibView))
                    result[index]= elmt;
                // else if ((val == null) && (version == snmpVersionTwo))
                //    vb.value = SnmpVarBind.endOfMibView;
                continue;
                /* end of NPCTE fix for bugid 4381195 */
            }

            /* NPCTE fix for bugid 4381195 esc 0. <J.C.> < 17-Oct-2000> */
            if (elmt == null) continue;
            /* end of NPCTE fix for bugid 4381195 */

            if (elmt.value == SnmpVarBind.endOfMibView) continue;


            // Now we need to take the smallest oid ...
            //
            int comp = elmt.oid.compareTo(vb.oid);
            if (comp < 0) {
              // Take the smallest (lexicographically)
                //
                result[index]= elmt;
            }
            else {
                if(comp == 0) {
                    // Must compare agent used for reply
                    // Take the deeper within the reply
                    if (SNMP_ADAPTOR_LOGGER.isLoggable(Level.FINER)) {
                        SNMP_ADAPTOR_LOGGER.logp(Level.FINER, SnmpSubRequestHandler.class.getName(),
                            "updateResult"," oid overlapping. Oid : " +
                              elmt.oid + "value :" + elmt.value);
                        SNMP_ADAPTOR_LOGGER.logp(Level.FINER, SnmpSubRequestHandler.class.getName(),
                            "updateResult","Already present varBind : " +
                              vb);
                    }

                    SnmpOid oid = vb.oid;
                    SnmpMibAgent deeperAgent = server.getAgentMib(oid);

                    if (SNMP_ADAPTOR_LOGGER.isLoggable(Level.FINER)) {
                        SNMP_ADAPTOR_LOGGER.logp(Level.FINER, SnmpSubRequestHandler.class.getName(),
                            "updateResult","Deeper agent : " + deeperAgent);
                    }
                    if(deeperAgent == agent) {
                        if (SNMP_ADAPTOR_LOGGER.isLoggable(Level.FINER)) {
                            SNMP_ADAPTOR_LOGGER.logp(Level.FINER, SnmpSubRequestHandler.class.getName(),
                                "updateResult","The current agent is the deeper one. Update the value with the current one");
                        }
                        result[index].value = elmt.value;
                    }

                    /*
                      Vector v = new Vector();
                      SnmpMibRequest getReq = createMibRequest(v,
                      version,
                      null);
                      SnmpVarBind realValue = new SnmpVarBind(oid);
                      getReq.addVarBind(realValue);
                      try {
                      deeperAgent.get(getReq);
                      } catch(SnmpStatusException e) {
                      e.printStackTrace();
                      }

                      if(isDebugOn())
                      trace("updateResult", "Biggest priority value is : " +
                      realValue.value);

                      result[index].value = realValue.value;
                    */
                }
            }
        }
    }
}
