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

package com.sun.xml.internal.ws.wsdl.parser;

import com.sun.xml.internal.ws.model.ParameterBinding;
import com.sun.xml.internal.ws.model.Mode;

import javax.xml.namespace.QName;
import javax.xml.ws.soap.SOAPBinding;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class WSDLDocument {
    protected Map<QName, Message> messages;
    protected Map<QName, PortType> portTypes;
    protected Map<QName, Binding> bindings;
    protected Map<QName, Service> services;

    public WSDLDocument() {
        messages = new HashMap<QName, Message>();
        portTypes = new HashMap<QName, PortType>();
        bindings = new HashMap<QName, Binding>();
        services = new LinkedHashMap<QName, Service>();
    }

    public void addMessage(Message msg){
        messages.put(msg.getName(), msg);
    }

    public Message getMessage(QName name){
        return messages.get(name);
    }

    public void addPortType(PortType pt){
        portTypes.put(pt.getName(), pt);
    }

    public PortType getPortType(QName name){
        return portTypes.get(name);
    }

    public void addBinding(Binding binding){
        bindings.put(binding.getName(), binding);
    }

    public Binding getBinding(QName name){
        return bindings.get(name);
    }

    public void addService(Service svc){
        services.put(svc.getName(), svc);
    }

    public Service getService(QName name){
        return services.get(name);
    }

    public Map<QName, Service> getServices(){
        return services;
    }

    /**
     * Returns the first service QName from insertion order
     * @return
     */
    public QName getFirstServiceName(){
        if(services.isEmpty())
            return null;
        return services.values().iterator().next().getName();
    }

    /**
     * Returns first port QName from first service as per the insertion order
     * @return
     */
    public QName getFirstPortName(){
        if(services.isEmpty())
            return null;
        Service service = services.values().iterator().next();
        Iterator<QName> iter = service.keySet().iterator();
        QName port = (iter.hasNext())?iter.next():null;
        return port;
    }

    private Port getFirstPort(){
        if(services.isEmpty())
            return null;
        Service service = services.values().iterator().next();
        Collection<Port> coll = service.values();
        Port port = (coll != null)?((coll.iterator().hasNext())?coll.iterator().next():null):null;
        return port;
    }


    /**
     * Returns biningId of the first port
     * @return
     */
    public String getBindingId(){
        Port port = getFirstPort();
        if(port == null)
            return null;
        Binding binding = bindings.get(port.getBindingName());
        if(binding == null)
            return null;
        return binding.getBindingId();
    }

    /**
     * Gives the binding Id of the given service and port
     * @param service
     * @param port
     * @return
     */
    public String getBindingId(QName service, QName port){
        Service s = services.get(service);
        if(s != null){
            Port p = s.get(port);
            if(p != null){
                Binding b = bindings.get(p.getBindingName());
                if(b != null)
                    return b.getBindingId();
            }

        }
        return null;
    }

     /**
     *
     * @param serviceName non-null service QName
     * @param portName    non-null port QName
     * @return
     *          BindingOperation on success otherwise null. throws NPE if any of the parameters null
     */
    public Binding getBinding(QName serviceName, QName portName){
        Service service = services.get(serviceName);
        if(service != null){
            Port port = service.get(portName);
            if(port != null){
                QName bindingName = port.getBindingName();
                return bindings.get(bindingName);
            }
        }
        return null;
    }

    /**
     * Returns the bindings for the given bindingId
     * @param service  non-null service
     * @param bindingId  non-null binding id
     * @return
     */
    public List<Binding> getBindings(Service service, String bindingId){
        List<Binding> bs = new ArrayList<Binding>();
        Collection<Port> ports = service.values();
        if(ports.isEmpty())
            return bs;
        for(Port port:ports){
            Binding b = bindings.get(port.getName());
            if(b == null)
                return bs;
            if(b.equals(bindingId))
                bs.add(b);
        }
        return bs;
    }

    public QName getPortName(QName serviceName, QName portType){
        Service service = services.get(serviceName);
        for(Port port:service.values()){
            QName bindingName = port.getBindingName();
            assert (bindingName != null);
            Binding binding = bindings.get(bindingName);
            QName ptName = binding.getPortTypeName();
            assert (ptName != null);
            if(ptName.equals(portType))
                return port.getName();
        }
        return null;
    }

    public void finalizeBinding(Binding binding){
        assert(binding != null);
        QName portTypeName = binding.getPortTypeName();
        if(portTypeName == null)
            return;
        PortType pt = portTypes.get(portTypeName);
        if(pt == null)
            return;
        for(String op:binding.keySet()){
            PortTypeOperation pto = pt.get(op);
            if(pto == null)
                return;
            QName inMsgName = pto.getInputMessage();
            if(inMsgName == null)
                continue;
            Message inMsg = messages.get(inMsgName);
            BindingOperation bo = binding.get(op);
            int bodyindex = 0;
            if(inMsg != null){
                for(String name:inMsg){
                    ParameterBinding pb = bo.getInputBinding(name);
                    if(pb.isBody()){
                        bo.addPart(new Part(name, pb, bodyindex++), Mode.IN);
                    }
                }
            }
            bodyindex=0;
            QName outMsgName = pto.getOutputMessage();
            if(outMsgName == null)
                continue;
            Message outMsg = messages.get(outMsgName);
            if(outMsg!= null){
                for(String name:outMsg){
                    ParameterBinding pb = bo.getOutputBinding(name);
                    if(pb.isBody()){
                        bo.addPart(new Part(name, pb, bodyindex++), Mode.OUT);
                    }
                }
            }
        }
    }
}
