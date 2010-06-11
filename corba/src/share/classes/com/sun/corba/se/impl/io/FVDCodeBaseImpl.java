/*
 * Copyright (c) 1999, 2003, Oracle and/or its affiliates. All rights reserved.
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
/*
 * Licensed Materials - Property of IBM
 * RMI-IIOP v1.0
 * Copyright IBM Corp. 1998 1999  All Rights Reserved
 *
 */

package com.sun.corba.se.impl.io;

import org.omg.CORBA.ORB;
import java.util.Properties;
import javax.rmi.CORBA.Util;
import javax.rmi.CORBA.ValueHandler;
import java.util.Hashtable;
import java.util.Stack;

import com.sun.org.omg.CORBA.ValueDefPackage.FullValueDescription;
import com.sun.org.omg.SendingContext._CodeBaseImplBase;
import com.sun.org.omg.SendingContext.CodeBase;
import com.sun.org.omg.SendingContext.CodeBaseHelper;
import org.omg.CORBA.CompletionStatus;
import org.omg.CORBA.ORB;

import com.sun.corba.se.impl.logging.OMGSystemException;
import com.sun.corba.se.spi.logging.CORBALogDomains;

/**
 * This class acts as the remote interface to receivers wishing to retrieve
 * the information of a remote Class.
 */
public class FVDCodeBaseImpl extends _CodeBaseImplBase
{
    // Contains rep. ids as keys to FullValueDescriptions
    private static Hashtable fvds = new Hashtable();

    // Private ORBSingleton used when we need an ORB while not
    // having a delegate set.
    private transient ORB orb = null;

    private transient OMGSystemException wrapper = OMGSystemException.get(
        CORBALogDomains.RPC_ENCODING ) ;

    // backward compatability so that appropriate rep-id calculations
    // can take place
    // this needs to be transient to prevent serialization during
    // marshalling/unmarshalling
    private transient ValueHandlerImpl vhandler = null;

    void setValueHandler(ValueHandler vh)
    {
        vhandler = (com.sun.corba.se.impl.io.ValueHandlerImpl) vh;
    }

    // Operation to obtain the IR from the sending context
    public com.sun.org.omg.CORBA.Repository get_ir (){
        return null;
    }

    // Operations to obtain a URL to the implementation code
    public String implementation (String x){
        try{
            // default to using the current ORB version in case the
            // vhandler is not set
            if (vhandler == null) {
                vhandler = new ValueHandlerImpl(false);
            }

            // Util.getCodebase may return null which would
            // cause a BAD_PARAM exception.
            String result = Util.getCodebase(vhandler.getClassFromType(x));
            if (result == null)
                return "";
            else
                return result;
        } catch(ClassNotFoundException cnfe){
            throw wrapper.missingLocalValueImpl( CompletionStatus.COMPLETED_MAYBE,
                cnfe ) ;
        }
    }

    public String[] implementations (String[] x){
        String result[] = new String[x.length];

        for (int i = 0; i < x.length; i++)
            result[i] = implementation(x[i]);

        return result;
    }

    // the same information
    public FullValueDescription meta (String x){
        try{
            FullValueDescription result = (FullValueDescription)fvds.get(x);

            if (result == null) {
                // default to using the current ORB version in case the
                // vhandler is not set
                if (vhandler == null) {
                    vhandler = new ValueHandlerImpl(false);
                }

                try{
                    result = ValueUtility.translate(_orb(),
                        ObjectStreamClass.lookup(vhandler.getAnyClassFromType(x)), vhandler);
                } catch(Throwable t){
                    if (orb == null)
                        orb = ORB.init(); //d11638
                    result = ValueUtility.translate(orb,
                        ObjectStreamClass.lookup(vhandler.getAnyClassFromType(x)), vhandler);
                }

                if (result != null){
                    fvds.put(x, result);
                } else {
                    throw wrapper.missingLocalValueImpl( CompletionStatus.COMPLETED_MAYBE);
                }
            }

            return result;
        } catch(Throwable t){
            throw wrapper.incompatibleValueImpl(CompletionStatus.COMPLETED_MAYBE,t);
        }
    }

    public FullValueDescription[] metas (String[] x){
        FullValueDescription descriptions[] = new FullValueDescription[x.length];

        for (int i = 0; i < x.length; i++)
            descriptions[i] = meta(x[i]);

        return descriptions;
    }

    // information
    public String[] bases (String x){
        try {
            // default to using the current ORB version in case the
            // vhandler is not set
            if (vhandler == null) {
                vhandler = new ValueHandlerImpl(false);
            }

            Stack repIds = new Stack();
            Class parent = ObjectStreamClass.lookup(vhandler.getClassFromType(x)).forClass().getSuperclass();

            while (!parent.equals(java.lang.Object.class)) {
                repIds.push(vhandler.createForAnyType(parent));
                parent = parent.getSuperclass();
            }

            String result[] = new String[repIds.size()];
            for (int i = result.length - 1; i >= 0; i++)
                result[i] = (String)repIds.pop();

            return result;
        } catch (Throwable t) {
            throw wrapper.missingLocalValueImpl( CompletionStatus.COMPLETED_MAYBE, t );
        }
    }
}
