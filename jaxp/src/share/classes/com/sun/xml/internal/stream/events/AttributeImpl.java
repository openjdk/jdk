/*
 * Copyright 2005 Sun Microsystems, Inc.  All Rights Reserved.
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

package com.sun.xml.internal.stream.events;

import javax.xml.namespace.QName;
import javax.xml.stream.events.Attribute;
import java.io.Writer;
import javax.xml.stream.events.XMLEvent;


//xxx: AttributeEvent is not really a first order event. Should we be renaming the class to AttributeImpl for consistent
//naming convention.

/**
 * Implementation of Attribute Event.
 *
 *@author Neeraj Bajaj, Sun Microsystems
 *@author K.Venugopal, Sun Microsystems
 *
 */

public class AttributeImpl extends DummyEvent implements Attribute

{
    //attribute value
    private String fValue;
    private String fNonNormalizedvalue;

    //name of the attribute
    private QName fQName;
    //attribute type
    private String fAttributeType = "CDATA";


    //A flag indicating whether this attribute was actually specified in the start-tag
    //of its element or was defaulted from the schema.
    private boolean fIsSpecified;

    public AttributeImpl(){
        init();
    }
    public AttributeImpl(String name, String value) {
        init();
        fQName = new QName(name);
        fValue = value;
    }

    public AttributeImpl(String prefix, String name, String value) {
        this(prefix, null,name, value, null,null,false );
    }

    public AttributeImpl(String prefix, String uri, String localPart, String value, String type) {
        this(prefix, uri, localPart, value, null, type, false);
    }

    public AttributeImpl(String prefix, String uri, String localPart, String value, String nonNormalizedvalue, String type, boolean isSpecified) {
        this(new QName(uri, localPart, prefix), value, nonNormalizedvalue, type, isSpecified);
    }


    public AttributeImpl(QName qname, String value, String nonNormalizedvalue, String type, boolean isSpecified) {
        init();
        fQName = qname ;
        fValue = value ;
        if(type != null && !type.equals(""))
            fAttributeType = type;

        fNonNormalizedvalue = nonNormalizedvalue;
        fIsSpecified = isSpecified ;

    }

    public String toString() {
        if( fQName.getPrefix() != null && fQName.getPrefix().length() > 0 )
            return fQName.getPrefix() + ":" + fQName.getLocalPart() + "='" + fValue + "'";
        else
            return fQName.getLocalPart() + "='" + fValue + "'";
    }

    public void setName(QName name){
        fQName = name ;
    }

    public QName getName() {
        return fQName;
    }

    public void setValue(String value){
        fValue = value;
    }

    public String getValue() {
        return fValue;
    }

    public void setNonNormalizedValue(String nonNormalizedvalue){
        fNonNormalizedvalue = nonNormalizedvalue;
    }

    public String getNonNormalizedValue(){
        return fNonNormalizedvalue ;
    }

    public void setAttributeType(String attributeType){
        fAttributeType = attributeType ;
    }

    /** Gets the type of this attribute, default is "CDATA   */
    // We dont need to take care of default value.. implementation takes care of it.
    public String getDTDType() {
        return fAttributeType;
    }

    /** is this attribute is specified in the instance document */

    public void setSpecified(boolean isSpecified){
        fIsSpecified = isSpecified ;
    }

    public boolean isSpecified() {
        return fIsSpecified ;
    }

    /** This method will write the XMLEvent as per the XML 1.0 specification as Unicode characters.
     *
     * No indentation or whitespace should be outputted.
     *
     *
     *
     * Any user defined event type SHALL have this method
     *
     * called when being written to on an output stream.
     *
     * Built in Event types MUST implement this method,
     *
     * but implementations MAY choose not call these methods
     *
     * for optimizations reasons when writing out built in
     *
     * Events to an output stream.
     *
     * The output generated MUST be equivalent in terms of the
     *
     * infoset expressed.
     *
     *
     *
     * @param writer The writer that will output the data
     *
     * @throws XMLStreamException if there is a fatal error writing the event
     *
     */

    public void writeAsEncodedUnicode(Writer writer) throws javax.xml.stream.XMLStreamException {

    }

    protected void init(){
        setEventType(XMLEvent.ATTRIBUTE);
    }




}//AttributeImpl
