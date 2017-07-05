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

import com.sun.istack.internal.NotNull;
import com.sun.istack.internal.Nullable;
import com.sun.xml.internal.bind.api.Bridge;
import com.sun.xml.internal.bind.api.JAXBRIContext;
import com.sun.xml.internal.ws.api.SOAPVersion;
import com.sun.xml.internal.ws.api.message.Message;
import com.sun.xml.internal.ws.api.model.CheckedException;
import com.sun.xml.internal.ws.api.model.ExceptionType;
import com.sun.xml.internal.ws.encoding.soap.SOAP12Constants;
import com.sun.xml.internal.ws.encoding.soap.SOAPConstants;
import com.sun.xml.internal.ws.encoding.soap.SerializationException;
import com.sun.xml.internal.ws.message.jaxb.JAXBMessage;
import com.sun.xml.internal.ws.message.FaultMessage;
import com.sun.xml.internal.ws.model.CheckedExceptionImpl;
import com.sun.xml.internal.ws.model.JavaMethodImpl;
import com.sun.xml.internal.ws.util.DOMUtil;
import com.sun.xml.internal.ws.util.StringUtils;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.namespace.QName;
import javax.xml.soap.SOAPFault;
import javax.xml.soap.Detail;
import javax.xml.soap.DetailEntry;
import javax.xml.transform.dom.DOMResult;
import javax.xml.ws.ProtocolException;
import javax.xml.ws.WebServiceException;
import javax.xml.ws.soap.SOAPFaultException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Iterator;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Base class that represents SOAP 1.1 or SOAP 1.2 fault. This class can be used by the invocation handlers to create
 * an Exception from a received messge.
 *
 * @author Vivek Pandey
 */
public abstract class SOAPFaultBuilder {

    /**
     * Gives the {@link DetailType} for a Soap 1.1 or Soap 1.2 message that can be used to create either a checked exception or
     * a protocol specific exception
     */
    abstract DetailType getDetail();

    abstract void setDetail(DetailType detailType);

    public @Nullable QName getFirstDetailEntryName() {
        DetailType dt = getDetail();
        if (dt != null) {
            Node entry = dt.getDetail(0);
            if (entry != null) {
                return new QName(entry.getNamespaceURI(), entry.getLocalName());
            }
        }
        return null;
    }

    /**
     * gives the fault string that can be used to create an {@link Exception}
     */
    abstract String getFaultString();

    /**
     * This should be called from the client side to throw an {@link Exception} for a given soap mesage
     */
    public Throwable createException(Map<QName, CheckedExceptionImpl> exceptions) throws JAXBException {
        DetailType dt = getDetail();
        Node detail = null;
        if(dt != null)  detail = dt.getDetail(0);

        //return ProtocolException if the detail is not present or there is no checked exception
        if(detail == null || exceptions == null){
            // No soap detail, doesnt look like its a checked exception
            // throw a protocol exception
            return attachServerException(getProtocolException());
        }

        //check if the detail is a checked exception, if not throw a ProtocolException
        QName detailName = new QName(detail.getNamespaceURI(), detail.getLocalName());
        CheckedExceptionImpl ce = exceptions.get(detailName);
        if (ce == null) {
            //No Checked exception for the received detail QName, throw a SOAPFault exception
            return attachServerException(getProtocolException());

        }

        if (ce.getExceptionType().equals(ExceptionType.UserDefined)) {
            return attachServerException(createUserDefinedException(ce));

        }
        Class exceptionClass = ce.getExceptionClass();
        try {
            Constructor constructor = exceptionClass.getConstructor(String.class, (Class) ce.getDetailType().type);
            Exception exception = (Exception) constructor.newInstance(getFaultString(), getJAXBObject(detail, ce));
            return attachServerException(exception);
        } catch (Exception e) {
            throw new WebServiceException(e);
        }
    }

    /**
     * To be called to convert a  {@link ProtocolException} and faultcode for a given {@link SOAPVersion} in to a {@link Message}.
     *
     * @param soapVersion {@link SOAPVersion#SOAP_11} or {@link SOAPVersion#SOAP_12}
     * @param ex a ProtocolException
     * @param faultcode soap faultcode. Its ignored if the {@link ProtocolException} instance is {@link SOAPFaultException} and it has a
     * faultcode present in the underlying {@link SOAPFault}.
     * @return {@link Message} representing SOAP fault
     */
    public static @NotNull Message createSOAPFaultMessage(@NotNull SOAPVersion soapVersion, @NotNull ProtocolException ex, @Nullable QName faultcode){
        Object detail = getFaultDetail(null, ex);
        if(soapVersion == SOAPVersion.SOAP_12)
            return createSOAP12Fault(soapVersion, ex, detail, null, faultcode);
        return createSOAP11Fault(soapVersion, ex, detail, null, faultcode);
    }

    /**
     * To be called by the server runtime in the situations when there is an Exception that needs to be transformed in
     * to a soapenv:Fault payload.
     *
     * @param ceModel     {@link CheckedExceptionImpl} model that provides useful informations such as the detail tagname
     *                    and the Exception associated with it. Caller of this constructor should get the CheckedException
     *                    model by calling {@link JavaMethodImpl#getCheckedException(Class)}, where
     *                    Class is t.getClass().
     *                    <p>
     *                    If its null then this is not a checked exception  and in that case the soap fault will be
     *                    serialized only from the exception as described below.
     * @param ex          Exception that needs to be translated into soapenv:Fault, always non-null.
     *                    <ul>
     *                    <li>If t is instance of {@link SOAPFaultException} then its serilaized as protocol exception.
     *                    <li>If t.getCause() is instance of {@link SOAPFaultException} and t is a checked exception then
     *                    the soap fault detail is serilaized from t and the fault actor/string/role is taken from t.getCause().
     *                    </ul>
     * @param soapVersion non-null
     */
    public static Message createSOAPFaultMessage(SOAPVersion soapVersion, CheckedExceptionImpl ceModel, Throwable ex) {
        Object detail = getFaultDetail(ceModel, ex);
        if(soapVersion == SOAPVersion.SOAP_12)
            return createSOAP12Fault(soapVersion, ex, detail, ceModel, null);
        return createSOAP11Fault(soapVersion, ex, detail, ceModel, null);
    }

    /**
     * Server runtime will call this when there is some internal error not resulting from an exception.
     *
     * @param soapVersion {@link SOAPVersion#SOAP_11} or {@link SOAPVersion#SOAP_12}
     * @param faultString must be non-null
     * @param faultCode   For SOAP 1.1, it must be one of
     *                    <ul>
     *                    <li>{@link SOAPVersion#faultCodeClient}
     *                    <li>{@link SOAPVersion#faultCodeServer}
     *                    <li>{@link SOAPConstants#FAULT_CODE_MUST_UNDERSTAND}
     *                    <li>{@link SOAPConstants#FAULT_CODE_VERSION_MISMATCH}
     *                    </ul>
     *
     *                    For SOAP 1.2
     *                    <ul>
     *                    <li>{@link SOAPVersion#faultCodeClient}
     *                    <li>{@link SOAPVersion#faultCodeServer}
     *                    <li>{@link SOAP12Constants#FAULT_CODE_MUST_UNDERSTAND}
     *                    <li>{@link SOAP12Constants#FAULT_CODE_VERSION_MISMATCH}
     *                    <li>{@link SOAP12Constants#FAULT_CODE_DATA_ENCODING_UNKNOWN}
     *                    </ul>
     * @return non-null {@link Message}
     */
    public static Message createSOAPFaultMessage(SOAPVersion soapVersion, String faultString, QName faultCode) {
        if (faultCode == null)
            faultCode = getDefaultFaultCode(soapVersion);
        return createSOAPFaultMessage(soapVersion, faultString, faultCode, null);
    }

    public static Message createSOAPFaultMessage(SOAPVersion soapVersion, SOAPFault fault) {
        switch (soapVersion) {
            case SOAP_11:
                return JAXBMessage.create(JAXB_CONTEXT, new SOAP11Fault(fault), soapVersion);
            case SOAP_12:
                return JAXBMessage.create(JAXB_CONTEXT, new SOAP12Fault(fault), soapVersion);
            default:
                throw new AssertionError();
        }
    }

    private static Message createSOAPFaultMessage(SOAPVersion soapVersion, String faultString, QName faultCode, Element detail) {
        switch (soapVersion) {
            case SOAP_11:
                return JAXBMessage.create(JAXB_CONTEXT, new SOAP11Fault(faultCode, faultString, null, detail), soapVersion);
            case SOAP_12:
                return JAXBMessage.create(JAXB_CONTEXT, new SOAP12Fault(faultCode, faultString, detail), soapVersion);
            default:
                throw new AssertionError();
        }
    }

    /**
     * Creates a DOM node that represents the complete stack trace of the exception,
     * and attach that to {@link DetailType}.
     */
    final void captureStackTrace(@Nullable Throwable t) {
        if(t==null)     return;
        if(!captureStackTrace)  return;     // feature disabled

        try {
            Document d = DOMUtil.createDom();
            ExceptionBean.marshal(t,d);

            DetailType detail = getDetail();
            if(detail==null)
            setDetail(detail=new DetailType());

            detail.getDetails().add(d.getDocumentElement());
        } catch (JAXBException e) {
            // this should never happen
            logger.log(Level.WARNING, "Unable to capture the stack trace into XML",e);
        }
    }

    /**
     * Initialize the cause of this exception by attaching the server side exception.
     */
    private <T extends Throwable> T attachServerException(T t) {
        DetailType detail = getDetail();
        if(detail==null)        return t;   // no details

        for (Element n : detail.getDetails()) {
            if(ExceptionBean.isStackTraceXml(n)) {
                try {
                    t.initCause(ExceptionBean.unmarshal(n));
                } catch (JAXBException e) {
                    // perhaps incorrectly formatted XML.
                    logger.log(Level.WARNING, "Unable to read the capture stack trace in the fault",e);
                }
                return t;
            }
        }

        return t;
    }

    abstract protected Throwable getProtocolException();

    private Object getJAXBObject(Node jaxbBean, CheckedException ce) throws JAXBException {
        Bridge bridge = ce.getBridge();
        return bridge.unmarshal(jaxbBean);
    }

    private Exception createUserDefinedException(CheckedExceptionImpl ce) {
        Class exceptionClass = ce.getExceptionClass();
        Class detailBean = ce.getDetailBean();
        try{
            Node detailNode = getDetail().getDetails().get(0);
            Object jaxbDetail = getJAXBObject(detailNode, ce);
            Constructor exConstructor;
            try{
                exConstructor = exceptionClass.getConstructor(String.class, detailBean);
                return (Exception) exConstructor.newInstance(getFaultString(), jaxbDetail);
            }catch(NoSuchMethodException e){
                exConstructor = exceptionClass.getConstructor(String.class);
                return (Exception) exConstructor.newInstance(getFaultString());
            }
        } catch (Exception e) {
            throw new WebServiceException(e);
        }
    }

    private static String getWriteMethod(Field f) {
        return "set" + StringUtils.capitalize(f.getName());
    }

    private static Object getFaultDetail(CheckedExceptionImpl ce, Throwable exception) {
        if (ce == null)
            return null;
        if (ce.getExceptionType().equals(ExceptionType.UserDefined)) {
            return createDetailFromUserDefinedException(ce, exception);
        }
        try {
            Method m = exception.getClass().getMethod("getFaultInfo");
            return m.invoke(exception);
        } catch (Exception e) {
            throw new SerializationException(e);
        }
    }

    private static Object createDetailFromUserDefinedException(CheckedExceptionImpl ce, Object exception) {
        Class detailBean = ce.getDetailBean();
        Field[] fields = detailBean.getDeclaredFields();
        try {
            Object detail = detailBean.newInstance();
            for (Field f : fields) {
                Method em = exception.getClass().getMethod(getReadMethod(f));
                try {
                    Method sm = detailBean.getMethod(getWriteMethod(f), em.getReturnType());
                    sm.invoke(detail, em.invoke(exception));
                } catch(NoSuchMethodException ne) {
                    // Try to use exception bean's public field to populate the value.
                    Field sf = detailBean.getField(f.getName());
                    sf.set(detail, em.invoke(exception));
                }
            }
            return detail;
        } catch (Exception e) {
            throw new SerializationException(e);
        }
    }

    private static String getReadMethod(Field f) {
        if (f.getType().isAssignableFrom(boolean.class))
            return "is" + StringUtils.capitalize(f.getName());
        return "get" + StringUtils.capitalize(f.getName());
    }

    private static Message createSOAP11Fault(SOAPVersion soapVersion, Throwable e, Object detail, CheckedExceptionImpl ce, QName faultCode) {
        SOAPFaultException soapFaultException = null;
        String faultString = null;
        String faultActor = null;
        Throwable cause = e.getCause();
        if (e instanceof SOAPFaultException) {
            soapFaultException = (SOAPFaultException) e;
        } else if (cause != null && cause instanceof SOAPFaultException) {
            soapFaultException = (SOAPFaultException) e.getCause();
        }
        if (soapFaultException != null) {
            QName soapFaultCode = soapFaultException.getFault().getFaultCodeAsQName();
            if(soapFaultCode != null)
                faultCode = soapFaultCode;

            faultString = soapFaultException.getFault().getFaultString();
            faultActor = soapFaultException.getFault().getFaultActor();
        }

        if (faultCode == null) {
            faultCode = getDefaultFaultCode(soapVersion);
        }

        if (faultString == null) {
            faultString = e.getMessage();
            if (faultString == null) {
                faultString = e.toString();
            }
        }
        Element detailNode = null;
        QName firstEntry = null;
        if (detail == null && soapFaultException != null) {
            detailNode = soapFaultException.getFault().getDetail();
            firstEntry = getFirstDetailEntryName((Detail)detailNode);
        } else if(ce != null){
            try {
                DOMResult dr = new DOMResult();
                ce.getBridge().marshal(detail,dr);
                detailNode = (Element)dr.getNode().getFirstChild();
                firstEntry = getFirstDetailEntryName(detailNode);
            } catch (JAXBException e1) {
                //Should we throw Internal Server Error???
                faultString = e.getMessage();
                faultCode = getDefaultFaultCode(soapVersion);
            }
        }
        SOAP11Fault soap11Fault = new SOAP11Fault(faultCode, faultString, faultActor, detailNode);
        soap11Fault.captureStackTrace(e);

        Message msg = JAXBMessage.create(JAXB_CONTEXT, soap11Fault, soapVersion);
        return new FaultMessage(msg, firstEntry);
    }

    private static @Nullable QName getFirstDetailEntryName(@Nullable Detail detail) {
        if (detail != null) {
            Iterator<DetailEntry> it = detail.getDetailEntries();
            if (it.hasNext()) {
                DetailEntry entry = it.next();
                return getFirstDetailEntryName(entry);
            }
        }
        return null;
    }

    private static @NotNull QName getFirstDetailEntryName(@NotNull Element entry) {
        return new QName(entry.getNamespaceURI(), entry.getLocalName());
    }

    private static Message createSOAP12Fault(SOAPVersion soapVersion, Throwable e, Object detail, CheckedExceptionImpl ce, QName faultCode) {
        SOAPFaultException soapFaultException = null;
        CodeType code = null;
        String faultString = null;
        String faultRole = null;
        Throwable cause = e.getCause();
        if (e instanceof SOAPFaultException) {
            soapFaultException = (SOAPFaultException) e;
        } else if (cause != null && cause instanceof SOAPFaultException) {
            soapFaultException = (SOAPFaultException) e.getCause();
        }
        if (soapFaultException != null) {
            SOAPFault fault = soapFaultException.getFault();
            QName soapFaultCode = fault.getFaultCodeAsQName();
            if(soapFaultCode != null){
                faultCode = soapFaultCode;
                code = new CodeType(faultCode);
                Iterator iter = fault.getFaultSubcodes();
                boolean first = true;
                SubcodeType subcode = null;
                while(iter.hasNext()){
                    QName value = (QName)iter.next();
                    if(first){
                        SubcodeType sct = new SubcodeType(value);
                        code.setSubcode(sct);
                        subcode = sct;
                        first = false;
                        continue;
                    }
                    subcode = fillSubcodes(subcode, value);
                }
            }
            faultString = soapFaultException.getFault().getFaultString();
            faultRole = soapFaultException.getFault().getFaultActor();
        }

        if (faultCode == null) {
            faultCode = getDefaultFaultCode(soapVersion);
            code = new CodeType(faultCode);
        }else if(code == null){
            code = new CodeType(faultCode);
        }

        if (faultString == null) {
            faultString = e.getMessage();
            if (faultString == null) {
                faultString = e.toString();
            }
        }

        ReasonType reason = new ReasonType(faultString);
        Element detailNode = null;
        QName firstEntry = null;
        if (detail == null && soapFaultException != null) {
            detailNode = soapFaultException.getFault().getDetail();
            firstEntry = getFirstDetailEntryName((Detail)detailNode);
        } else if(detail != null){
            try {
                DOMResult dr = new DOMResult();
                ce.getBridge().marshal(detail, dr);
                detailNode = (Element)dr.getNode().getFirstChild();
                firstEntry = getFirstDetailEntryName(detailNode);
            } catch (JAXBException e1) {
                //Should we throw Internal Server Error???
                faultString = e.getMessage();
                faultCode = getDefaultFaultCode(soapVersion);
            }
        }

        SOAP12Fault soap12Fault = new SOAP12Fault(code, reason, null, faultRole, detailNode);
        soap12Fault.captureStackTrace(e);

        Message msg = JAXBMessage.create(JAXB_CONTEXT, soap12Fault, soapVersion);
        return new FaultMessage(msg, firstEntry);
    }

    private static SubcodeType fillSubcodes(SubcodeType parent, QName value){
        SubcodeType newCode = new SubcodeType(value);
        parent.setSubcode(newCode);
        return newCode;
    }

    private static QName getDefaultFaultCode(SOAPVersion soapVersion) {
        return soapVersion.faultCodeServer;
    }

    /**
     * Parses a fault {@link Message} and returns it as a {@link SOAPFaultBuilder}.
     *
     * @return always non-null valid object.
     * @throws JAXBException if the parsing fails.
     */
    public static SOAPFaultBuilder create(Message msg) throws JAXBException {
        return msg.readPayloadAsJAXB(JAXB_CONTEXT.createUnmarshaller());
    }

    /**
     * This {@link JAXBContext} can handle SOAP 1.1/1.2 faults.
     */
    private static final JAXBRIContext JAXB_CONTEXT;

    private static final Logger logger = Logger.getLogger(SOAPFaultBuilder.class.getName());

    /**
     * Set to false if you don't want the generated faults to have stack trace in it.
     */
    public static boolean captureStackTrace;

    /*package*/ static final String CAPTURE_STACK_TRACE_PROPERTY = SOAPFaultBuilder.class.getName()+".disableCaptureStackTrace";

    static {
        try {
            captureStackTrace = System.getProperty(CAPTURE_STACK_TRACE_PROPERTY)==null;
        } catch (SecurityException e) {
            // ignore
        }

        try {
            JAXB_CONTEXT = (JAXBRIContext)JAXBContext.newInstance(SOAP11Fault.class, SOAP12Fault.class);
        } catch (JAXBException e) {
            throw new Error(e); // this must be a bug in our code
        }
    }
}
