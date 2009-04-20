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

package com.sun.xml.internal.ws.fault;

import com.sun.xml.internal.bind.marshaller.NamespacePrefixMapper;
import com.sun.xml.internal.ws.developer.ServerSideException;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementWrapper;
import javax.xml.bind.annotation.XmlRootElement;
import java.util.ArrayList;
import java.util.List;

/**
 * JAXB-bound bean that captures the exception and its call stack.
 *
 * <p>
 * This is used to capture the stack trace of the server side error and
 * send that over to the client.
 *
 * @author Kohsuke Kawaguchi
 */
@XmlRootElement(namespace=ExceptionBean.NS,name=ExceptionBean.LOCAL_NAME)
final class ExceptionBean {
    /**
     * Converts the given {@link Throwable} into an XML representation
     * and put that as a DOM tree under the given node.
     */
    public static void marshal( Throwable t, Node parent ) throws JAXBException {
        Marshaller m = JAXB_CONTEXT.createMarshaller();
        m.setProperty("com.sun.xml.internal.bind.namespacePrefixMapper",nsp);
        m.marshal(new ExceptionBean(t), parent );
    }

    /**
     * Does the reverse operation of {@link #marshal(Throwable, Node)}. Constructs an
     * {@link Exception} object from the XML.
     */
    public static ServerSideException unmarshal( Node xml ) throws JAXBException {
        ExceptionBean e = (ExceptionBean) JAXB_CONTEXT.createUnmarshaller().unmarshal(xml);
        return e.toException();
    }

    @XmlAttribute(name="class")
    public String className;
    @XmlElement
    public String message;
    @XmlElementWrapper(namespace=NS,name="stackTrace")
    @XmlElement(namespace=NS,name="frame")
    public List<StackFrame> stackTrace = new ArrayList<StackFrame>();
    @XmlElement(namespace=NS,name="cause")
    public ExceptionBean cause;

    // so that people noticed this fragment can turn it off
    @XmlAttribute
    public String note = "To disable this feature, set "+SOAPFaultBuilder.CAPTURE_STACK_TRACE_PROPERTY+" system property to false";

    ExceptionBean() {// for JAXB
    }

    /**
     * Creates an {@link ExceptionBean} tree that represents the given {@link Throwable}.
     */
    private ExceptionBean(Throwable t) {
        this.className = t.getClass().getName();
        this.message = t.getMessage();

        for (StackTraceElement f : t.getStackTrace()) {
            stackTrace.add(new StackFrame(f));
        }

        Throwable cause = t.getCause();
        if(t!=cause && cause!=null)
            this.cause = new ExceptionBean(cause);
    }

    private ServerSideException toException() {
        ServerSideException e = new ServerSideException(className,message);
        if(stackTrace!=null) {
            StackTraceElement[] ste = new StackTraceElement[stackTrace.size()];
            for( int i=0; i<stackTrace.size(); i++ )
                ste[i] = stackTrace.get(i).toStackTraceElement();
            e.setStackTrace(ste);
        }
        if(cause!=null)
            e.initCause(cause.toException());
        return e;
    }

    /**
     * Captures one stack frame.
     */
    static final class StackFrame {
        @XmlAttribute(name="class")
        public String declaringClass;
        @XmlAttribute(name="method")
        public String methodName;
        @XmlAttribute(name="file")
        public String fileName;
        @XmlAttribute(name="line")
        public String lineNumber;

        StackFrame() {// for JAXB
        }

        public StackFrame(StackTraceElement ste) {
            this.declaringClass = ste.getClassName();
            this.methodName = ste.getMethodName();
            this.fileName = ste.getFileName();
            this.lineNumber = box(ste.getLineNumber());
        }

        private String box(int i) {
            if(i>=0) return String.valueOf(i);
            if(i==-2)   return "native";
            return "unknown";
        }

        private int unbox(String v) {
            try {
                return Integer.parseInt(v);
            } catch (NumberFormatException e) {
                if(v.equals("native"))  return -2;
                return -1;
            }
        }

        private StackTraceElement toStackTraceElement() {
            return new StackTraceElement(declaringClass,methodName,fileName,unbox(lineNumber));
        }
    }

    /**
     * Checks if the given element is the XML representation of {@link ExceptionBean}.
     */
    public static boolean isStackTraceXml(Element n) {
        return n.getLocalName().equals(LOCAL_NAME) && n.getNamespaceURI().equals(NS);
    }

    private static final JAXBContext JAXB_CONTEXT;

    /**
     * Namespace URI.
     */
    /*package*/ static final String NS = "http://jax-ws.dev.java.net/";

    /*package*/ static final String LOCAL_NAME = "exception";

    static {
        try {
            JAXB_CONTEXT = JAXBContext.newInstance(ExceptionBean.class);
        } catch (JAXBException e) {
            // this must be a bug in our code
            throw new Error(e);
        }
    }

    private static final NamespacePrefixMapper nsp = new NamespacePrefixMapper() {
        public String getPreferredPrefix(String namespaceUri, String suggestion, boolean requirePrefix) {
            if(namespaceUri.equals(NS)) return "";
            return suggestion;
        }
    };
}
