/*
 * Copyright (c) 2000, 2013, Oracle and/or its affiliates. All rights reserved.
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

import java.util.Vector;
import java.util.Hashtable;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Arrays;
import java.util.logging.Level;

import static com.sun.jmx.defaults.JmxProperties.SNMP_ADAPTOR_LOGGER;
import com.sun.jmx.snmp.SnmpVarBind;
import com.sun.jmx.snmp.SnmpStatusException;
import com.sun.jmx.snmp.SnmpDefinitions;
import com.sun.jmx.snmp.SnmpOid;
import com.sun.jmx.snmp.SnmpPdu;
import com.sun.jmx.snmp.SnmpEngine;

//  XXX: things to do: use SnmpOid rather than `instance' for future
//       evolutions.
//  XXX: Maybe use hashlists rather than vectors for entries?
//       => in that case, the key should be SnmpOid.toString()
//
/**
 * This class is used to register varbinds from a SNMP varbind list with
 * the SnmpMibNode responsible for handling the requests concerning that
 * varbind.
 * This class holds a hashtable of Handler nodes, whith the involved
 * SnmpMibNode as a key.
 * When the involved SnmpMibNode is a group, the sublist of varbind is
 * directly stored in the Handler node.
 * When the involved SnmpMibNode is a table, the sublist is stored in a
 * sorted array indexed by the OID of the entry involved.
 */
final class SnmpRequestTree {

    // Constructor:
    // @param  req The SnmpMibRequest that will be segmented in this
    //         tree. It holds the original varbind vector passed
    //         by the SnmpSubRequestHandler to this MIB. This
    //         varbind vector is used to retrieve the "real"
    //         position of a varbind in the vector. There is no other easy
    //         way to do this - since as a result of the segmentation the
    //         original positions will be lost.
    // @param  creationflag indicates whether the operation involved
    //         allows for entry creation (ie: it is a SET request).
    // @param  pdutype indicates the type of the request PDU as defined
    //         in SnmpDefinitions
    //
    SnmpRequestTree(SnmpMibRequest req, boolean creationflag, int pdutype) {
        this.request = req;
        this.version  = req.getVersion();
        this.creationflag = creationflag;
        this.hashtable = new Hashtable<>();
        setPduType(pdutype);
    }

    public static int mapSetException(int errorStatus, int version)
        throws SnmpStatusException {

        final int errorCode = errorStatus;

        if (version == SnmpDefinitions.snmpVersionOne)
            return errorCode;

        int mappedErrorCode = errorCode;

        // Now take care of V2 errorCodes that can be stored
        // in the varbind itself:
        if (errorCode == SnmpStatusException.noSuchObject)
            // noSuchObject => notWritable
            mappedErrorCode = SnmpStatusException.snmpRspNotWritable;

        else if (errorCode == SnmpStatusException.noSuchInstance)
            // noSuchInstance => notWritable
            mappedErrorCode = SnmpStatusException.snmpRspNotWritable;

        return mappedErrorCode;
    }

    public static int mapGetException(int errorStatus, int version)
        throws SnmpStatusException {

        final int errorCode = errorStatus;
        if (version == SnmpDefinitions.snmpVersionOne)
            return errorCode;

        int mappedErrorCode = errorCode;

        // Now take care of V2 errorCodes that can be stored
        // in the varbind itself:
        if (errorCode ==
            SnmpStatusException.noSuchObject)
            // noSuchObject => noSuchObject
            mappedErrorCode = errorCode;

        else if (errorCode ==
                 SnmpStatusException.noSuchInstance)
            // noSuchInstance => noSuchInstance
            mappedErrorCode = errorCode;

        // Now we're going to try to transform every other
        // global code in either noSuchInstance or noSuchObject,
        // so that the get can return a partial result.
        //
        // Only noSuchInstance or noSuchObject can be stored
        // in the varbind itself.
        //

        // According to RFC 1905: noAccess is emitted when the
        // the access is denied because it is not in the MIB view...
        //
        else if (errorCode ==
                 SnmpStatusException.noAccess)
            // noAccess => noSuchInstance
            mappedErrorCode = SnmpStatusException.noSuchInstance;

        // According to RFC 1905: (my interpretation because it is not
        // really clear) The specified variable name exists - but the
        // variable does not exists and cannot be created under the
        // present circumstances (probably because the request specifies
        // another variable/value which is incompatible, or because the
        // value of some other variable in the MIB prevents the creation)
        //
        // Note that this error should never be raised in a GET context
        // but who knows?
        //
        else if (errorCode == SnmpStatusException.snmpRspInconsistentName)
            // inconsistentName => noSuchInstance
            mappedErrorCode = SnmpStatusException.noSuchInstance;

        // All the errors comprised between snmpRspWrongType and
        // snmpRspInconsistentValue concern values: so we're going
        // to assume the OID was correct, and reply with noSuchInstance.
        //
        // Note that this error should never be raised in a GET context
        // but who knows?
        //
        else if ((errorCode >= SnmpStatusException.snmpRspWrongType) &&
                 (errorCode <= SnmpStatusException.snmpRspInconsistentValue))
            mappedErrorCode = SnmpStatusException.noSuchInstance;

        // We're going to assume the OID was correct, and reply
        // with noSuchInstance.
        //
        else if (errorCode == SnmpStatusException.readOnly)
            mappedErrorCode = SnmpStatusException.noSuchInstance;

        // For all other errors but genErr, we're going to reply with
        // noSuchObject
        //
        else if (errorCode != SnmpStatusException.snmpRspAuthorizationError &&
                 errorCode != SnmpStatusException.snmpRspGenErr)
            mappedErrorCode = SnmpStatusException.noSuchObject;

        // Only genErr will abort the GET and be returned as global
        // error.
        //
        return mappedErrorCode;

    }

    //-------------------------------------------------------------------
    // This class is a package implementation of the enumeration of
    // SnmSubRequest associated with an Handler node.
    //-------------------------------------------------------------------

    static final class Enum implements Enumeration<SnmpMibSubRequest> {
        Enum(SnmpRequestTree hlist,Handler h) {
            handler = h;
            this.hlist = hlist;
            size = h.getSubReqCount();
        }
        private final Handler handler;
        private final SnmpRequestTree hlist;
        private int   entry = 0;
        private int   iter  = 0;
        private int   size  = 0;

        @Override
        public boolean hasMoreElements() {
            return iter < size;
        }

        @Override
        public SnmpMibSubRequest nextElement() throws NoSuchElementException  {
            if (iter == 0) {
                if (handler.sublist != null) {
                    iter++;
                    return hlist.getSubRequest(handler);
                }
            }
            iter ++;
            if (iter > size) throw new NoSuchElementException();
            SnmpMibSubRequest result = hlist.getSubRequest(handler,entry);
            entry++;
            return result;
        }
    }

    //-------------------------------------------------------------------
    // This class is a package implementation of the SnmpMibSubRequest
    // interface. It can only be instantiated by SnmpRequestTree.
    //-------------------------------------------------------------------

    static final class SnmpMibSubRequestImpl implements SnmpMibSubRequest {
        SnmpMibSubRequestImpl(SnmpMibRequest global, Vector<SnmpVarBind> sublist,
                           SnmpOid entryoid, boolean isnew,
                           boolean getnextflag, SnmpVarBind rs) {
            this.global = global;
            varbinds           = sublist;
            this.version       = global.getVersion();
            this.entryoid      = entryoid;
            this.isnew         = isnew;
            this.getnextflag   = getnextflag;
            this.statusvb      = rs;
        }

        final private Vector<SnmpVarBind> varbinds;
        final private SnmpMibRequest global;
        final private int            version;
        final private boolean        isnew;
        final private SnmpOid        entryoid;
        final private boolean        getnextflag;
        final private SnmpVarBind    statusvb;

        // -------------------------------------------------------------
        // Implements the method defined in SnmpMibRequest interface.
        // See SnmpMibRequest for the java doc.
        // -------------------------------------------------------------
        @Override
        public Enumeration<SnmpVarBind> getElements() {
            return varbinds.elements();
        }

        // -------------------------------------------------------------
        // Implements the method defined in SnmpMibRequest interface.
        // See SnmpMibRequest for the java doc.
        // -------------------------------------------------------------
        @Override
        public Vector<SnmpVarBind> getSubList() {
            return varbinds;
        }

        // -------------------------------------------------------------
        // Implements the method defined in SnmpMibRequest interface.
        // See SnmpMibRequest for the java doc.
        // -------------------------------------------------------------
        @Override
        public final int getSize()  {
            if (varbinds == null) return 0;
            return varbinds.size();
        }

        // -------------------------------------------------------------
        // Implements the method defined in SnmpMibRequest interface.
        // See SnmpMibRequest for the java doc.
        // -------------------------------------------------------------
        @Override
        public void addVarBind(SnmpVarBind varbind) {
            // XXX not sure we must also add the varbind in the global
            //     request? or whether we should raise an exception:
            //     in principle, this method should not be called!
            varbinds.addElement(varbind);
            global.addVarBind(varbind);
        }

        // -------------------------------------------------------------
        // Implements the method defined in SnmpMibSubRequest interface.
        // See SnmpMibSubRequest for the java doc.
        // -------------------------------------------------------------
        @Override
        public boolean isNewEntry() {
            return isnew;
        }

        // -------------------------------------------------------------
        // Implements the method defined in SnmpMibSubRequest interface.
        // See SnmpMibSubRequest for the java doc.
        // -------------------------------------------------------------
        @Override
        public SnmpOid getEntryOid() {
            return entryoid;
        }

        // -------------------------------------------------------------
        // Implements the method defined in SnmpMibRequest interface.
        // See SnmpMibRequest for the java doc.
        // -------------------------------------------------------------
        @Override
        public int getVarIndex(SnmpVarBind varbind) {
            if (varbind == null) return 0;
            return global.getVarIndex(varbind);
        }

        // -------------------------------------------------------------
        // Implements the method defined in SnmpMibRequest interface.
        // See SnmpMibRequest for the java doc.
        // -------------------------------------------------------------
        @Override
        public Object getUserData() { return global.getUserData(); }


        // -------------------------------------------------------------
        // Implements the method defined in SnmpMibSubRequest interface.
        // See SnmpMibSubRequest for the java doc.
        // -------------------------------------------------------------

        @Override
        public void registerGetException(SnmpVarBind var,
                                         SnmpStatusException exception)
            throws SnmpStatusException {
            // The index in the exception must correspond to
            // the SNMP index ...
            //
            if (version == SnmpDefinitions.snmpVersionOne)
                throw new SnmpStatusException(exception, getVarIndex(var)+1);

            if (var == null)
                throw exception;

            // If we're doing a getnext ==> endOfMibView
            if (getnextflag) {
                var.value = SnmpVarBind.endOfMibView;
                return;
            }

            final int errorCode = mapGetException(exception.getStatus(),
                                                  version);

            // Now take care of V2 errorCodes that can be stored
            // in the varbind itself:
            if (errorCode ==
                SnmpStatusException.noSuchObject)
                // noSuchObject => noSuchObject
                var.value= SnmpVarBind.noSuchObject;

            else if (errorCode ==
                     SnmpStatusException.noSuchInstance)
                // noSuchInstance => noSuchInstance
                var.value= SnmpVarBind.noSuchInstance;

            else
                throw new SnmpStatusException(errorCode, getVarIndex(var)+1);

        }

        // -------------------------------------------------------------
        // Implements the method defined in SnmpMibSubRequest interface.
        // See SnmpMibSubRequest for the java doc.
        // -------------------------------------------------------------
        @Override
        public void registerSetException(SnmpVarBind var,
                                         SnmpStatusException exception)
            throws SnmpStatusException {
            // The index in the exception must correspond to
            // the SNMP index ...
            //
            if (version == SnmpDefinitions.snmpVersionOne)
                throw new SnmpStatusException(exception, getVarIndex(var)+1);

            // Although the first pass of check() did not fail,
            // the set() phase could not be carried out correctly.
            // Since we don't know how to make an "undo", and some
            // assignation may already have been performed, we're going
            // to throw an snmpRspUndoFailed.
            //
            throw new SnmpStatusException(SnmpDefinitions.snmpRspUndoFailed,
                                          getVarIndex(var)+1);
        }

        // -------------------------------------------------------------
        // Implements the method defined in SnmpMibSubRequest interface.
        // See SnmpMibSubRequest for the java doc.
        // -------------------------------------------------------------
        @Override
        public void registerCheckException(SnmpVarBind var,
                                           SnmpStatusException exception)
            throws SnmpStatusException {
            // The index in the exception must correspond to
            // the SNMP index ...
            //
            // We throw the exception in order to abort the SET operation
            // in an atomic way.
            final int errorCode = exception.getStatus();
            final int mappedErrorCode = mapSetException(errorCode,
                                                        version);

            if (errorCode != mappedErrorCode)
                throw new
                    SnmpStatusException(mappedErrorCode, getVarIndex(var)+1);
            else
                throw new SnmpStatusException(exception, getVarIndex(var)+1);
        }

        // -------------------------------------------------------------
        // Implements the method defined in SnmpMibRequest interface.
        // See SnmpMibRequest for the java doc.
        // -------------------------------------------------------------
        @Override
        public int getVersion() {
            return version;
        }

        @Override
        public SnmpVarBind getRowStatusVarBind() {
            return statusvb;
        }

        @Override
        public SnmpPdu getPdu() {
            return global.getPdu();
        }

        @Override
        public int getRequestPduVersion() {
            return global.getRequestPduVersion();
        }

        @Override
        public SnmpEngine getEngine() {
            return global.getEngine();
        }

        @Override
        public String getPrincipal() {
            return global.getPrincipal();
        }

        @Override
        public int getSecurityLevel() {
            return global.getSecurityLevel();
        }

        @Override
        public int getSecurityModel() {
            return global.getSecurityModel();
        }

        @Override
        public byte[] getContextName() {
            return global.getContextName();
        }

        @Override
        public byte[] getAccessContextName() {
            return global.getAccessContextName();
        }
    }

    //-------------------------------------------------------------------
    // This class implements a node in the SnmpRequestTree.
    // It stores:
    //    o The SnmpMibNode involved (key)
    //    o The sublist of varbind directly handled by this node
    //    o A vector of sublists concerning the entries (existing or not)
    //      of the SnmpMIbNode (when it is a table).
    //-------------------------------------------------------------------

    static final class Handler {
        SnmpMibNode meta;       // The meta  which handles the sublist.
        int         depth;      // The depth of the meta node.
        Vector<SnmpVarBind> sublist; // The sublist of varbinds to be handled.
        // List        entryoids;  // Sorted array of entry oids
        // List        entrylists; // Sorted array of entry lists
        // List        isentrynew; // Sorted array of booleans
        SnmpOid[]     entryoids  = null; // Sorted array of entry oids
        Vector<SnmpVarBind>[] entrylists = null; // Sorted array of entry lists
        boolean[]     isentrynew = null; // Sorted array of booleans
        SnmpVarBind[] rowstatus  = null; // RowStatus varbind, if any
        int entrycount = 0;
        int entrysize  = 0;

        final int type; // request PDU type as defined in SnmpDefinitions
        final private static int Delta = 10;

        public Handler(int pduType) {
            this.type = pduType;
        }

        /**
         * Adds a varbind in this node sublist.
         */
        public void addVarbind(SnmpVarBind varbind) {
            if (sublist == null) sublist = new Vector<>();
            sublist.addElement(varbind);
        }

        /**
         * register an entry for the given oid at the given position with
         * the given sublist.
         */
        @SuppressWarnings("unchecked")
        // We need this because of new Vector[n] instead of
        // new Vector<SnmpVarBind>[n], which is illegal.
        void add(int pos,SnmpOid oid, Vector<SnmpVarBind> v, boolean isnew,
                 SnmpVarBind statusvb) {

            if (entryoids == null) {
                // Vectors are null: Allocate new vectors

                entryoids  = new SnmpOid[Delta];
                entrylists = (Vector<SnmpVarBind>[])new Vector<?>[Delta];
                isentrynew = new boolean[Delta];
                rowstatus  = new SnmpVarBind[Delta];
                entrysize  = Delta;
                pos = 0;

            } else if (pos >= entrysize || entrycount == entrysize) {
                // Vectors must be enlarged

                // Save old vectors
                SnmpOid[]     olde = entryoids;
                Vector[]      oldl = entrylists;
                boolean[]     oldn = isentrynew;
                SnmpVarBind[] oldr = rowstatus;

                // Allocate larger vectors
                entrysize += Delta;
                entryoids =  new SnmpOid[entrysize];
                entrylists = (Vector<SnmpVarBind>[])new Vector<?>[entrysize];
                isentrynew = new boolean[entrysize];
                rowstatus  = new SnmpVarBind[entrysize];

                // Check pos validity
                if (pos > entrycount) pos = entrycount;
                if (pos < 0) pos = 0;

                final int l1 = pos;
                final int l2 = entrycount - pos;

                // Copy original vectors up to `pos'
                if (l1 > 0) {
                    java.lang.System.arraycopy(olde,0,entryoids,
                                               0,l1);
                    java.lang.System.arraycopy(oldl,0,entrylists,
                                               0,l1);
                    java.lang.System.arraycopy(oldn,0,isentrynew,
                                               0,l1);
                    java.lang.System.arraycopy(oldr,0,rowstatus,
                                               0,l1);
                }

                // Copy original vectors from `pos' to end, leaving
                // an empty room at `pos' in the new vectors.
                if (l2 > 0) {
                    final int l3 = l1+1;
                    java.lang.System.arraycopy(olde,l1,entryoids,
                                               l3,l2);
                    java.lang.System.arraycopy(oldl,l1,entrylists,
                                               l3,l2);
                    java.lang.System.arraycopy(oldn,l1,isentrynew,
                                               l3,l2);
                    java.lang.System.arraycopy(oldr,l1,rowstatus,
                                               l3,l2);
                }


            } else if (pos < entrycount) {
                // Vectors are large enough to accommodate one additional
                // entry.
                //
                // Shift vectors, making an empty room at `pos'
                final int l1 = pos+1;
                final int l2 = entrycount - pos;

                java.lang.System.arraycopy(entryoids,pos,entryoids,
                                           l1,l2);
                java.lang.System.arraycopy(entrylists,pos,entrylists,
                                           l1,l2);
                java.lang.System.arraycopy(isentrynew,pos,isentrynew,
                                           l1,l2);
                java.lang.System.arraycopy(rowstatus,pos,rowstatus,
                                           l1,l2);
            }

            // Fill the gap at `pos'
            entryoids[pos]  = oid;
            entrylists[pos] = v;
            isentrynew[pos] = isnew;
            rowstatus[pos]  = statusvb;
            entrycount++;
        }

        public void addVarbind(SnmpVarBind varbind, SnmpOid entryoid,
                               boolean isnew, SnmpVarBind statusvb)
            throws SnmpStatusException {
            Vector<SnmpVarBind> v = null;
            SnmpVarBind rs = statusvb;

            if (entryoids == null) {
//              entryoids = new ArrayList();
//              entrylists = new ArrayList();
//              isentrynew = new ArrayList();
                v = new Vector<>();
//              entryoids.add(entryoid);
//              entrylists.add(v);
//              isentrynew.add(new Boolean(isnew));
                add(0,entryoid,v,isnew,rs);
            } else {
                // int pos = findOid(entryoids,entryoid);
                // int pos = findOid(entryoids,entrycount,entryoid);
                final int pos =
                    getInsertionPoint(entryoids,entrycount,entryoid);
                if (pos > -1 && pos < entrycount &&
                    entryoid.compareTo(entryoids[pos]) == 0) {
                    v  = entrylists[pos];
                    rs = rowstatus[pos];
                } else {
                    // if (pos == -1 || pos >= entryoids.size() ) {
                    // if (pos == -1 || pos >= entrycount ) {
                    // pos = getInsertionPoint(entryoids,entryoid);
                    // pos = getInsertionPoint(entryoids,entrycount,entryoid);
                    v = new Vector<>();
//                  entryoids.add(pos,entryoid);
//                  entrylists.add(pos,v);
//                  isentrynew.add(pos,new Boolean(isnew));
                    add(pos,entryoid,v,isnew,rs);
                }
//              } else v = (Vector) entrylists.get(pos);
                    // } else v = entrylists[pos];
                if (statusvb != null) {
                    if ((rs != null) && (rs != statusvb) &&
                        ((type == SnmpDefinitions.pduWalkRequest) ||
                         (type == SnmpDefinitions.pduSetRequestPdu))) {
                        throw new SnmpStatusException(
                              SnmpStatusException.snmpRspInconsistentValue);
                    }
                    rowstatus[pos] = statusvb;
                }
            }

            // We do not include the status variable in the varbind,
            // because we're going to set it separately...
            //
            if (statusvb != varbind)
                v.addElement(varbind);
        }

        public int getSubReqCount() {
            int count = 0;
            if (sublist != null) count++;
//          if (entryoids != null) count += entryoids.size();
            if (entryoids != null) count += entrycount;
            return count;
        }

        public Vector<SnmpVarBind> getSubList() {
            return sublist;
        }

        public int getEntryPos(SnmpOid entryoid) {
            // return findOid(entryoids,entryoid);
            return findOid(entryoids,entrycount,entryoid);
        }

        public SnmpOid getEntryOid(int pos) {
            if (entryoids == null) return null;
            // if (pos == -1 || pos >= entryoids.size() ) return null;
            if (pos == -1 || pos >= entrycount ) return null;
            // return (SnmpOid) entryoids.get(pos);
            return entryoids[pos];
        }

        public boolean isNewEntry(int pos) {
            if (entryoids == null) return false;
            // if (pos == -1 || pos >= entryoids.size() ) return false;
            if (pos == -1 || pos >= entrycount ) return false;
            // return ((Boolean)isentrynew.get(pos)).booleanValue();
            return isentrynew[pos];
        }

        public SnmpVarBind getRowStatusVarBind(int pos) {
            if (entryoids == null) return null;
            // if (pos == -1 || pos >= entryoids.size() ) return false;
            if (pos == -1 || pos >= entrycount ) return null;
            // return ((Boolean)isentrynew.get(pos)).booleanValue();
            return rowstatus[pos];
        }

        public Vector<SnmpVarBind> getEntrySubList(int pos) {
            if (entrylists == null) return null;
            // if (pos == -1 || pos >= entrylists.size() ) return null;
            if (pos == -1 || pos >= entrycount ) return null;
            // return (Vector) entrylists.get(pos);
            return entrylists[pos];
        }

        public Iterator<SnmpOid> getEntryOids() {
            if (entryoids == null) return null;
            // return entryoids.iterator();
            return Arrays.asList(entryoids).iterator();
        }

        public int getEntryCount() {
            if (entryoids == null) return 0;
            // return entryoids.size();
            return entrycount;
        }

    }


    //-------------------------------------------------------------------
    //-------------------------------------------------------------------
    // Public interface
    //-------------------------------------------------------------------
    //-------------------------------------------------------------------

    //-------------------------------------------------------------------
    // Returns the contextual object containing user-data allocated
    // through the SnmpUserDataFactory for this request.
    //-------------------------------------------------------------------

    public Object getUserData() { return request.getUserData(); }

    //-------------------------------------------------------------------
    // Tells whether creation of new entries is allowed with respect
    // to the operation involved (GET=>false/SET=>true)
    //-------------------------------------------------------------------

    public boolean isCreationAllowed() {
        return creationflag;
    }

    //-------------------------------------------------------------------
    // Tells whether we are currently processing a SET request (check/set)
    //-------------------------------------------------------------------

    public boolean isSetRequest() {
        return setreqflag;
    }

    //-------------------------------------------------------------------
    // Returns the protocol version in which the original request is
    // evaluated.
    //-------------------------------------------------------------------

    public int getVersion() {
        return version;
    }

    //-------------------------------------------------------------------
    // Returns the actual protocol version of the request PDU.
    //-------------------------------------------------------------------

    public int getRequestPduVersion() {
        return request.getRequestPduVersion();
    }

    //-------------------------------------------------------------------
    // Returns the SnmpMibNode associated with the given handler
    //-------------------------------------------------------------------

    public SnmpMibNode getMetaNode(Handler handler) {
        return handler.meta;
    }

    //-------------------------------------------------------------------
    // Indicates the depth of the arc in the OID that identifies the
    // SnmpMibNode associated with the given handler
    //-------------------------------------------------------------------

    public int getOidDepth(Handler handler) {
        return handler.depth;
    }

    //-------------------------------------------------------------------
    // returns an enumeration of the SnmpMibSubRequest's to be invoked on
    // the SnmpMibNode associated with a given Handler node.
    // If this node is a group, there will be a single subrequest.
    // If it is a table, there will be one subrequest per entry involved.
    //-------------------------------------------------------------------

    public Enumeration<SnmpMibSubRequest> getSubRequests(Handler handler) {
        return new Enum(this,handler);
    }

    //-------------------------------------------------------------------
    // returns an enumeration of the Handlers stored in the Hashtable.
    //-------------------------------------------------------------------

    public Enumeration<Handler> getHandlers() {
        return hashtable.elements();
    }

    //-------------------------------------------------------------------
    // adds a varbind to a handler node sublist
    //-------------------------------------------------------------------

    public void add(SnmpMibNode meta, int depth, SnmpVarBind varbind)
        throws SnmpStatusException {
        registerNode(meta,depth,null,varbind,false,null);
    }

    //-------------------------------------------------------------------
    // adds an entry varbind to a handler node sublist
    //-------------------------------------------------------------------

    public void add(SnmpMibNode meta, int depth, SnmpOid entryoid,
                    SnmpVarBind varbind, boolean isnew)
        throws SnmpStatusException {
        registerNode(meta,depth,entryoid,varbind,isnew,null);
    }

    //-------------------------------------------------------------------
    // adds an entry varbind to a handler node sublist - specifying the
    // varbind which holds the row status
    //-------------------------------------------------------------------

    public void add(SnmpMibNode meta, int depth, SnmpOid entryoid,
                    SnmpVarBind varbind, boolean isnew,
                    SnmpVarBind statusvb)
        throws SnmpStatusException {
        registerNode(meta,depth,entryoid,varbind,isnew,statusvb);
    }


    //-------------------------------------------------------------------
    //-------------------------------------------------------------------
    // Protected interface
    //-------------------------------------------------------------------
    //-------------------------------------------------------------------

    //-------------------------------------------------------------------
    // Type of the request (see SnmpDefinitions)
    //-------------------------------------------------------------------

    void setPduType(int pduType) {
        type = pduType;
        setreqflag = ((pduType == SnmpDefinitions.pduWalkRequest) ||
            (pduType == SnmpDefinitions.pduSetRequestPdu));
    }

    //-------------------------------------------------------------------
    // We deal with a GET-NEXT request
    //-------------------------------------------------------------------

    void setGetNextFlag() {
        getnextflag = true;
    }

    //-------------------------------------------------------------------
    // Tell whether creation is allowed.
    //-------------------------------------------------------------------
    void switchCreationFlag(boolean flag) {
        creationflag = flag;
    }


    //-------------------------------------------------------------------
    // Returns the subrequest handled by the SnmpMibNode itself
    // (in principle, only for Groups)
    //-------------------------------------------------------------------

    SnmpMibSubRequest getSubRequest(Handler handler) {
        if (handler == null) return null;
        return new SnmpMibSubRequestImpl(request,handler.getSubList(),
                                      null,false,getnextflag,null);
    }

    //-------------------------------------------------------------------
    // Returns the subrequest associated with the entry identified by
    // the given entry (only for tables)
    //-------------------------------------------------------------------

    SnmpMibSubRequest getSubRequest(Handler handler, SnmpOid oid) {
        if (handler == null) return null;
        final int pos = handler.getEntryPos(oid);
        if (pos == -1) return null;
        return new SnmpMibSubRequestImpl(request,
                                         handler.getEntrySubList(pos),
                                         handler.getEntryOid(pos),
                                         handler.isNewEntry(pos),
                                         getnextflag,
                                         handler.getRowStatusVarBind(pos));
    }

    //-------------------------------------------------------------------
    // Returns the subrequest associated with the entry identified by
    // the given entry (only for tables). The `entry' parameter is an
    // index relative to the position of the entry in the handler sublist.
    //-------------------------------------------------------------------

    SnmpMibSubRequest getSubRequest(Handler handler, int entry) {
        if (handler == null) return null;
        return new
            SnmpMibSubRequestImpl(request,handler.getEntrySubList(entry),
                                  handler.getEntryOid(entry),
                                  handler.isNewEntry(entry),getnextflag,
                                  handler.getRowStatusVarBind(entry));
    }

    //-------------------------------------------------------------------
    //-------------------------------------------------------------------
    // Private section
    //-------------------------------------------------------------------
    //-------------------------------------------------------------------


    //-------------------------------------------------------------------
    // stores a handler node in the Hashtable
    //-------------------------------------------------------------------

    private void put(Object key, Handler handler) {
        if (handler == null) return;
        if (key == null) return;
        if (hashtable == null) hashtable = new Hashtable<Object, Handler>();
        hashtable.put(key,handler);
    }

    //-------------------------------------------------------------------
    // finds a handler node in the Hashtable
    //-------------------------------------------------------------------

    private Handler get(Object key) {
        if (key == null) return null;
        if (hashtable == null) return null;
        return hashtable.get(key);
    }

    //-------------------------------------------------------------------
    // Search for the given oid in `oids'. If none is found, returns -1
    // otherwise, returns the index at which the oid is located.
    //-------------------------------------------------------------------

    private static int findOid(SnmpOid[] oids, int count, SnmpOid oid) {
        final int size = count;
        int low= 0;
        int max= size - 1;
        int curr= low + (max-low)/2;
        //System.out.println("Try to retrieve: " + oid.toString());
        while (low <= max) {

            final SnmpOid pos = oids[curr];

            //System.out.println("Compare with" + pos.toString());
            // never know ...we might find something ...
            //
            final int comp = oid.compareTo(pos);
            if (comp == 0)
                return curr;

            if (oid.equals(pos)) {
                return curr;
            }
            if (comp > 0) {
                low = curr + 1;
            } else {
                max = curr - 1;
            }
            curr = low + (max-low)/2;
        }
        return -1;
    }

    //-------------------------------------------------------------------
    // Return the index at which the given oid should be inserted in the
    // `oids' array.
    //-------------------------------------------------------------------

    private static int getInsertionPoint(SnmpOid[] oids, int count,
                                         SnmpOid oid) {
        final SnmpOid[] localoids = oids;
        final int size = count;
        int low= 0;
        int max= size - 1;
        int curr= low + (max-low)/2;


        while (low <= max) {

            final SnmpOid pos = localoids[curr];

            // never know ...we might find something ...
            //
            final int comp= oid.compareTo(pos);

            // In the calling method we will have to check for this case...
            //    if (comp == 0)
            //       return -1;
            // Returning curr instead of -1 avoids having to call
            // findOid() first and getInsertionPoint() afterwards.
            // We can simply call getInsertionPoint() and then checks whether
            // there's an OID at the returned position which equals the
            // given OID.
            if (comp == 0)
                return curr;

            if (comp>0) {
                low= curr +1;
            } else {
                max= curr -1;
            }
            curr= low + (max-low)/2;
        }
        return curr;
    }

    //-------------------------------------------------------------------
    // adds a varbind in a handler node sublist
    //-------------------------------------------------------------------

    private void registerNode(SnmpMibNode meta, int depth, SnmpOid entryoid,
                              SnmpVarBind varbind, boolean isnew,
                              SnmpVarBind statusvb)
        throws SnmpStatusException {
        if (meta == null) {
            SNMP_ADAPTOR_LOGGER.logp(Level.FINEST,
                    SnmpRequestTree.class.getName(),
                    "registerNode", "meta-node is null!");
            return;
        }
        if (varbind == null) {
            SNMP_ADAPTOR_LOGGER.logp(Level.FINEST,
                    SnmpRequestTree.class.getName(),
                    "registerNode", "varbind is null!");
            return ;
        }

        final Object key = meta;

        // retrieve the handler node associated with the given meta,
        // if any
        Handler handler = get(key);

        // If no handler node was found for that meta, create one.
        if (handler == null) {
            // if (isDebugOn())
            //    debug("registerNode", "adding node for " +
            //          varbind.oid.toString());
            handler = new Handler(type);
            handler.meta  = meta;
            handler.depth = depth;
            put(key,handler);
        }
        // else {
        //   if (isDebugOn())
        //      debug("registerNode","found node for " +
        //            varbind.oid.toString());
        // }

        // Adds the varbind in the handler node's sublist.
        if (entryoid == null)
            handler.addVarbind(varbind);
        else
            handler.addVarbind(varbind,entryoid,isnew,statusvb);
    }


    //-------------------------------------------------------------------
    // private variables
    //-------------------------------------------------------------------

    private Hashtable<Object, Handler> hashtable = null;
                                             // Hashtable of Handler objects
    private SnmpMibRequest request = null;   // The original list of varbinds
    private int       version      = 0;      // The protocol version
    private boolean   creationflag = false;  // Does the operation allow
                                             // creation of entries
    private boolean   getnextflag  = false;  // Does the operation allow
                                             // creation of entries
    private int       type         = 0;      // Request PDU type as defined
                                             // in SnmpDefinitions
    private boolean   setreqflag   = false;  // True if we're processing a
                                             // SET request (check/set).
}
