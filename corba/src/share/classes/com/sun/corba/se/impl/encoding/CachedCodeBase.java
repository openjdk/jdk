/*
 * Copyright (c) 2000, 2004, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.corba.se.impl.encoding;

import java.util.Hashtable;
import com.sun.org.omg.CORBA.ValueDefPackage.FullValueDescription;
import com.sun.org.omg.SendingContext.CodeBase;
import com.sun.org.omg.SendingContext.CodeBaseHelper;
import com.sun.org.omg.SendingContext._CodeBaseImplBase;
import com.sun.org.omg.SendingContext._CodeBaseStub;
import com.sun.corba.se.spi.transport.CorbaConnection;

/**
 * Provides the reading side with a per connection cache of
 * info obtained via calls to the remote CodeBase.
 *
 * Previously, most of this was in IIOPConnection.
 *
 * Features:
 *    Delays cache creation unless used
 *    Postpones remote calls until necessary
 *    Handles creating obj ref from IOR
 *    Maintains caches for the following maps:
 *         CodeBase IOR to obj ref (global)
 *         RepId to implementation URL(s)
 *         RepId to remote FVD
 *         RepId to superclass type list
 *
 * Needs cache management.
 */
// REVISIT: revert to package protected after framework merge.
public class CachedCodeBase extends _CodeBaseImplBase
{
    private Hashtable implementations, fvds, bases;
    private CodeBase delegate;
    private CorbaConnection conn;

    private static Hashtable iorToCodeBaseObjMap = new Hashtable();

    public CachedCodeBase(CorbaConnection connection) {
        conn = connection;
    }

    public com.sun.org.omg.CORBA.Repository get_ir () {
        return null;
    }

    public String implementation (String repId) {
        String urlResult = null;

        if (implementations == null)
            implementations = new Hashtable();
        else
            urlResult = (String)implementations.get(repId);

        if (urlResult == null && connectedCodeBase()) {
            urlResult = delegate.implementation(repId);

            if (urlResult != null)
                implementations.put(repId, urlResult);
        }

        return urlResult;
    }

    public String[] implementations (String[] repIds) {
        String[] urlResults = new String[repIds.length];

        for (int i = 0; i < urlResults.length; i++)
            urlResults[i] = implementation(repIds[i]);

        return urlResults;
    }

    public FullValueDescription meta (String repId) {
        FullValueDescription result = null;

        if (fvds == null)
            fvds = new Hashtable();
        else
            result = (FullValueDescription)fvds.get(repId);

        if (result == null && connectedCodeBase()) {
            result = delegate.meta(repId);

            if (result != null)
                fvds.put(repId, result);
        }

        return result;
    }

    public FullValueDescription[] metas (String[] repIds) {
        FullValueDescription[] results
            = new FullValueDescription[repIds.length];

        for (int i = 0; i < results.length; i++)
            results[i] = meta(repIds[i]);

        return results;
    }

    public String[] bases (String repId) {

        String[] results = null;

        if (bases == null)
            bases = new Hashtable();
        else
            results = (String[])bases.get(repId);

        if (results == null && connectedCodeBase()) {
            results = delegate.bases(repId);

            if (results != null)
                bases.put(repId, results);
        }

        return results;
    }

    // Ensures that we've used the connection's IOR to create
    // a valid CodeBase delegate.  If this returns false, then
    // it is not valid to access the delegate.
    private boolean connectedCodeBase() {
        if (delegate != null)
            return true;

        // The delegate was null, so see if the connection's
        // IOR was set.  If so, then we just need to connect
        // it.  Otherwise, there is no hope of checking the
        // remote code base.  That could be bug if the
        // service context processing didn't occur, or it
        // could be that we're talking to a foreign ORB which
        // doesn't include this optional service context.
        if (conn.getCodeBaseIOR() == null) {
            // REVISIT.  Use Merlin logging service to report that
            // codebase functionality was requested but unavailable.
            if (conn.getBroker().transportDebugFlag)
                conn.dprint("CodeBase unavailable on connection: " + conn);

            return false;
        }

        synchronized(this) {

            // Recheck the condition to make sure another
            // thread didn't already do this while we waited
            if (delegate != null)
                return true;

            // Do we have a reference initialized by another connection?
            delegate = (CodeBase)CachedCodeBase.iorToCodeBaseObjMap.get(conn.getCodeBaseIOR());
            if (delegate != null)
                return true;

            // Connect the delegate and update the cache
            delegate = CodeBaseHelper.narrow(getObjectFromIOR());

            // Save it for the benefit of other connections
            CachedCodeBase.iorToCodeBaseObjMap.put(conn.getCodeBaseIOR(),
                                                   delegate);
        }

        // It's now safe to use the delegate
        return true;
    }

    private final org.omg.CORBA.Object getObjectFromIOR() {
        return CDRInputStream_1_0.internalIORToObject(
            conn.getCodeBaseIOR(), null /*stubFactory*/, conn.getBroker());
    }
}

// End of file.
