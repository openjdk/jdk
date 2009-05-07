/*
 * Copyright 2005-2006 Sun Microsystems, Inc.  All Rights Reserved.
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

package com.sun.xml.internal.ws.model.wsdl;

import com.sun.istack.internal.NotNull;
import com.sun.xml.internal.ws.api.model.ParameterBinding;
import com.sun.xml.internal.ws.api.model.wsdl.WSDLBoundPortType;
import com.sun.xml.internal.ws.api.model.wsdl.WSDLMessage;
import com.sun.xml.internal.ws.api.model.wsdl.WSDLModel;
import com.sun.xml.internal.ws.api.model.wsdl.WSDLOperation;
import com.sun.xml.internal.ws.api.model.wsdl.WSDLPort;
import com.sun.xml.internal.ws.api.model.wsdl.WSDLPortType;
import com.sun.xml.internal.ws.api.model.wsdl.WSDLService;

import javax.jws.WebParam.Mode;
import javax.xml.namespace.QName;
import java.net.URL;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Implementation of {@link WSDLModel}
 *
 * @author Vivek Pandey
 */
public final class WSDLModelImpl extends AbstractExtensibleImpl implements WSDLModel {
    private final Map<QName, WSDLMessageImpl> messages = new HashMap<QName, WSDLMessageImpl>();
    private final Map<QName, WSDLPortTypeImpl> portTypes = new HashMap<QName, WSDLPortTypeImpl>();
    private final Map<QName, WSDLBoundPortTypeImpl> bindings = new HashMap<QName, WSDLBoundPortTypeImpl>();
    private final Map<QName, WSDLServiceImpl> services = new LinkedHashMap<QName, WSDLServiceImpl>();

    private final Map<QName,WSDLBoundPortType> unmBindings
        = Collections.<QName,WSDLBoundPortType>unmodifiableMap(bindings);


    public WSDLModelImpl(@NotNull String systemId) {
        super(systemId,-1);
    }

    /**
     * To create {@link WSDLModelImpl} from WSDL that doesn't have a system ID.
     */
    public WSDLModelImpl() {
        super(null,-1);
    }

    public void addMessage(WSDLMessageImpl msg){
        messages.put(msg.getName(), msg);
    }

    public WSDLMessageImpl getMessage(QName name){
        return messages.get(name);
    }

    public void addPortType(WSDLPortTypeImpl pt){
        portTypes.put(pt.getName(), pt);
    }

    public WSDLPortTypeImpl getPortType(QName name){
        return portTypes.get(name);
    }

    public void addBinding(WSDLBoundPortTypeImpl boundPortType){
        assert !bindings.containsValue(boundPortType);
        bindings.put(boundPortType.getName(), boundPortType);
    }

    public WSDLBoundPortTypeImpl getBinding(QName name){
        return bindings.get(name);
    }

    public void addService(WSDLServiceImpl svc){
        services.put(svc.getName(), svc);
    }

    public WSDLServiceImpl getService(QName name){
        return services.get(name);
    }

    public Map<QName, WSDLMessageImpl> getMessages() {
        return messages;
    }

    public @NotNull Map<QName, WSDLPortTypeImpl> getPortTypes() {
        return portTypes;
    }

    public @NotNull Map<QName, WSDLBoundPortType> getBindings() {
        return unmBindings;
    }

    public @NotNull Map<QName, WSDLServiceImpl> getServices(){
        return services;
    }

    /**
     * Returns the first service QName from insertion order
     */
    public QName getFirstServiceName(){
        if(services.isEmpty())
            return null;
        return services.values().iterator().next().getName();
    }

    /**
     * Returns first port QName from first service as per the insertion order
     */
    public QName getFirstPortName(){
        WSDLPort fp = getFirstPort();
        if(fp==null)
            return null;
        else
            return fp.getName();
    }

    private WSDLPort getFirstPort(){
        if(services.isEmpty())
            return null;
        WSDLService service = services.values().iterator().next();
        Iterator<? extends WSDLPort> iter = service.getPorts().iterator();
        WSDLPort port = iter.hasNext()?iter.next():null;
        return port;
    }

    /**
    * gets the first port in the wsdl which matches the serviceName and portType
    */
    public WSDLPortImpl getMatchingPort(QName serviceName, QName portType){
        return getService(serviceName).getMatchingPort(portType);
    }

    /**
     *
     * @param serviceName non-null service QName
     * @param portName    non-null port QName
     * @return
     *          WSDLBoundOperation on success otherwise null. throws NPE if any of the parameters null
     */
    public WSDLBoundPortTypeImpl getBinding(QName serviceName, QName portName){
        WSDLServiceImpl service = services.get(serviceName);
        if(service != null){
            WSDLPortImpl port = service.get(portName);
            if(port != null)
                return port.getBinding();
        }
        return null;
    }

    void finalizeRpcLitBinding(WSDLBoundPortTypeImpl boundPortType){
        assert(boundPortType != null);
        QName portTypeName = boundPortType.getPortTypeName();
        if(portTypeName == null)
            return;
        WSDLPortType pt = portTypes.get(portTypeName);
        if(pt == null)
            return;
        for (WSDLBoundOperationImpl bop : boundPortType.getBindingOperations()) {
            WSDLOperation pto = pt.get(bop.getName().getLocalPart());
            WSDLMessage inMsgName = pto.getInput().getMessage();
            if(inMsgName == null)
                continue;
            WSDLMessageImpl inMsg = messages.get(inMsgName.getName());
            int bodyindex = 0;
            if(inMsg != null){
                for(WSDLPartImpl part:inMsg.parts()){
                    String name = part.getName();
                    ParameterBinding pb = bop.getInputBinding(name);
                    if(pb.isBody()){
                        part.setIndex(bodyindex++);
                        part.setBinding(pb);
                        bop.addPart(part, Mode.IN);
                    }
                }
            }
            bodyindex=0;
            if(pto.isOneWay())
                continue;
            WSDLMessage outMsgName = pto.getOutput().getMessage();
            if(outMsgName == null)
                continue;
            WSDLMessageImpl outMsg = messages.get(outMsgName.getName());
            if(outMsg!= null){
                for(WSDLPartImpl part:outMsg.parts()){
                    String name = part.getName();
                    ParameterBinding pb = bop.getOutputBinding(name);
                    if(pb.isBody()){
                        part.setIndex(bodyindex++);
                        part.setBinding(pb);
                        bop.addPart(part, Mode.OUT);
                    }
                }
            }
        }
    }

    /**
     * Invoked at the end of the model construction to fix up references, etc.
     */
    public void freeze() {
        for (WSDLServiceImpl service : services.values()) {
            service.freeze(this);
        }
        for (WSDLBoundPortTypeImpl bp : bindings.values()) {
            bp.freeze();
        }
    }
}
