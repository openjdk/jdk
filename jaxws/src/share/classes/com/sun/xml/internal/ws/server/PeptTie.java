/*
 * Portions Copyright 2006 Sun Microsystems, Inc.  All Rights Reserved.
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
package com.sun.xml.internal.ws.server;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import com.sun.xml.internal.ws.pept.ept.MessageInfo;
import com.sun.xml.internal.ws.pept.presentation.MessageStruct;
import com.sun.xml.internal.ws.pept.presentation.Tie;
import com.sun.xml.internal.ws.util.MessageInfoUtil;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Creates a Stateless Tie object so that it is created only once and reused.
 */
public class PeptTie implements Tie {

    private static final Logger logger = Logger.getLogger(
            com.sun.xml.internal.ws.util.Constants.LoggingDomain + ".server.PeptTie");

    public void _setServant(Object servant) {
        throw new UnsupportedOperationException();
    }

    public Object _getServant() {
        throw new UnsupportedOperationException();
    }

    /*
     * @see Tie#_invoke(MessageInfo)
     */
    public void _invoke(MessageInfo messageInfo) {
        Object[] oa = messageInfo.getData();
        Method method = messageInfo.getMethod();
        RuntimeContext rtCtxt = MessageInfoUtil.getRuntimeContext(messageInfo);
        RuntimeEndpointInfo endpointInfo = rtCtxt.getRuntimeEndpointInfo();
        Object servant = endpointInfo.getImplementor();
        try {
            Object ret = method.invoke(servant, oa);
            messageInfo.setResponseType(MessageStruct.NORMAL_RESPONSE);
            messageInfo.setResponse(ret);
        } catch (IllegalArgumentException e) {
            setRuntimeException(messageInfo, e);
        } catch (IllegalAccessException e) {
            setRuntimeException(messageInfo, e);
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            if (cause != null) {
                if (!(cause instanceof RuntimeException) && cause instanceof Exception ) {
                    // Service specific exception
                    messageInfo.setResponseType(
                            MessageStruct.CHECKED_EXCEPTION_RESPONSE);
                    messageInfo.setResponse(cause);
                } else {
                    setRuntimeException(messageInfo, cause);
                }
            } else {
                setRuntimeException(messageInfo, e);
            }
        }
    }

    private void setRuntimeException(MessageInfo messageInfo, Throwable e) {
        logger.log(Level.SEVERE, e.getMessage(), e);
        messageInfo.setResponseType(MessageStruct.UNCHECKED_EXCEPTION_RESPONSE);
        messageInfo.setResponse(e);
    }

}
