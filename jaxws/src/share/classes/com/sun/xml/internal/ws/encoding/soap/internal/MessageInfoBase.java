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
package com.sun.xml.internal.ws.encoding.soap.internal;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

import com.sun.xml.internal.ws.pept.encoding.Decoder;
import com.sun.xml.internal.ws.pept.encoding.Encoder;
import com.sun.xml.internal.ws.pept.ept.EPTFactory;
import com.sun.xml.internal.ws.pept.ept.MessageInfo;
import com.sun.xml.internal.ws.pept.protocol.MessageDispatcher;
import com.sun.xml.internal.ws.spi.runtime.WSConnection;

/**
 * @author WS Development Team
 */
public class MessageInfoBase implements MessageInfo {

    protected Object[] _data;
    protected Method _method;
    protected Map _metadata;
    protected int _messagePattern;
    protected Object _response;
    protected int _responseType;
    protected EPTFactory _eptFactory;
    protected MessageDispatcher _messageDispatcher;
    protected Encoder _encoder;
    protected Decoder _decoder;
    protected WSConnection _connection;

    public void setData(Object[] data) {
        _data = data;
    }

    public Object[] getData() {
        return _data;
    }

    public void setMethod(Method method) {
        _method = method;
    }

    public Method getMethod() {
        return _method;
    }

    public void setMetaData(Object name, Object value) {
        if (_metadata == null)
           _metadata = new HashMap();
        _metadata.put(name, value);
    }

    public Object getMetaData(Object name) {
        Object value = null;

        if ((name != null) && (_metadata != null)) {
             value = _metadata.get(name);
        }
        return value;
    }

    public int getMEP() {
        return _messagePattern;
    }

    public void setMEP(int messagePattern) {
        _messagePattern = messagePattern;
    }

    public int getResponseType() {
        return _responseType;
    }

    public void setResponseType(int responseType) {
        _responseType = responseType;
    }

    public Object getResponse() {
        return _response;
    }

    public void setResponse(Object response) {
        _response = response;
    }

    public EPTFactory getEPTFactory() {
        return _eptFactory;
    }

    public void setEPTFactory(EPTFactory eptFactory) {
        _eptFactory = eptFactory;
    }

    /*
     * @see MessageInfo#getMessageDispatcher()
     */
    public MessageDispatcher getMessageDispatcher() {
        return _messageDispatcher;
    }

    /*
     * @see MessageInfo#getEncoder()
     */
    public Encoder getEncoder() {
        return _encoder;
    }

    /*
     * @see MessageInfo#getDecoder()
     */
    public Decoder getDecoder() {
        return _decoder;
    }

    /*
     * @see MessageInfo#getConnection()
     */
    public WSConnection getConnection() {
        return _connection;
    }

    /*
     * @see MessageInfo#setMessageDispatcher(MessageDispatcher)
     */
    public void setMessageDispatcher(MessageDispatcher arg0) {
        this._messageDispatcher = arg0;
    }

    /*
     * @see MessageInfo#setEncoder(Encoder)
     */
    public void setEncoder(Encoder encoder) {
        this._encoder = encoder;
    }

    /*
     * @see MessageInfo#setDecoder(Decoder)
     */
    public void setDecoder(Decoder decoder) {
        this._decoder = decoder;
    }

    /*
     * @see MessageInfo#setConnection(Connection)
     */
    public void setConnection(WSConnection connection) {
        this._connection = connection;
    }

    public static MessageInfo copy(MessageInfo mi){
        MessageInfoBase mib = (MessageInfoBase)mi;
        MessageInfoBase newMi = new MessageInfoBase();
        if(newMi._data != null){
            Object[] data = new Object[mib._data.length];
            int i = 0;
            for(Object o : mib._data){
                data[i++] = o;
            }
            newMi._data = data;
        }
        newMi.setConnection(mi.getConnection());
        newMi.setMethod(mi.getMethod());
        newMi.setDecoder(mi.getDecoder());
        newMi.setEncoder(mi.getEncoder());
        newMi.setEPTFactory(mi.getEPTFactory());
        newMi.setMEP(mi.getMEP());
        newMi._messageDispatcher = mib._messageDispatcher;
        newMi._metadata = new HashMap(mib._metadata);
        return (MessageInfo)newMi;
    }
}
