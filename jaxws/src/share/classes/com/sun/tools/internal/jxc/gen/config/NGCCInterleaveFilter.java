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
package com.sun.tools.internal.jxc.gen.config;

import org.xml.sax.Attributes;
import org.xml.sax.SAXException;

/**
 * Dispatches incoming events into sub handlers appropriately
 * so that the interleaving semantics will be correctly realized.
 *
 * @author Kohsuke Kawaguchi (kk@kohsuke.org)
 */
public abstract class NGCCInterleaveFilter implements NGCCEventSource, NGCCEventReceiver {
    protected NGCCInterleaveFilter( NGCCHandler parent, int cookie ) {
        this._parent = parent;
        this._cookie = cookie;
    }

    protected void setHandlers( NGCCEventReceiver[] receivers ) {
        this._receivers = receivers;
    }

    /** event receiverse. */
    protected NGCCEventReceiver[] _receivers;

    public int replace(NGCCEventReceiver oldHandler, NGCCEventReceiver newHandler) {
        for( int i=0; i<_receivers.length; i++ )
            if( _receivers[i]==oldHandler ) {
                _receivers[i]=newHandler;
                return i;
            }
        throw new InternalError(); // a bug in RelaxNGCC.
    }


    /** Parent handler. */
    private final NGCCHandler _parent;
    /** Cookie given by the parent. */
    private final int _cookie;



//
//
// event handler
//
//
    /**
     * Receiver that is being locked and therefore receives all the events.
     * <pre><xmp>
     * <interleave>
     *   <element name="foo"/>
     *   <element name="bar">
     *     <element name="foo"/>
     *   </element>
     * </interlaeve>
     * </xmp></pre>
     * When processing inside the bar element, this receiver is
     * "locked" so that it can correctly receive its child foo element.
     */
    private int lockedReceiver;
    /**
     * Nest level. Lock will be release when the lockCount becomes 0.
     */
    private int lockCount=0;

    public void enterElement(
        String uri, String localName, String qname,Attributes atts) throws SAXException {

        if(isJoining)   return; // ignore any token if we are joining. See joinByXXXX.

        if(lockCount++==0) {
            lockedReceiver = findReceiverOfElement(uri,localName);
            if(lockedReceiver==-1) {
                // we can't process this token. join.
                joinByEnterElement(null,uri,localName,qname,atts);
                return;
            }
        }

        _receivers[lockedReceiver].enterElement(uri,localName,qname,atts);
    }
    public void leaveElement(String uri, String localName, String qname) throws SAXException {
        if(isJoining)   return; // ignore any token if we are joining. See joinByXXXX.

        if( lockCount-- == 0 )
            joinByLeaveElement(null,uri,localName,qname);
        else
            _receivers[lockedReceiver].leaveElement(uri,localName,qname);
    }
    public void enterAttribute(String uri, String localName, String qname) throws SAXException {
        if(isJoining)   return; // ignore any token if we are joining. See joinByXXXX.

        if(lockCount++==0) {
            lockedReceiver = findReceiverOfAttribute(uri,localName);
            if(lockedReceiver==-1) {
                // we can't process this token. join.
                joinByEnterAttribute(null,uri,localName,qname);
                return;
            }
        }

        _receivers[lockedReceiver].enterAttribute(uri,localName,qname);
    }
    public void leaveAttribute(String uri, String localName, String qname) throws SAXException {
        if(isJoining)   return; // ignore any token if we are joining. See joinByXXXX.

        if( lockCount-- == 0 )
            joinByLeaveAttribute(null,uri,localName,qname);
        else
            _receivers[lockedReceiver].leaveAttribute(uri,localName,qname);
    }
    public void text(String value) throws SAXException {
        if(isJoining)   return; // ignore any token if we are joining. See joinByXXXX.

        if(lockCount!=0)
            _receivers[lockedReceiver].text(value);
        else {
            int receiver = findReceiverOfText();
            if(receiver!=-1)    _receivers[receiver].text(value);
            else                joinByText(null,value);
        }
    }



    /**
     * Implemented by the generated code to determine the handler
     * that can receive the given element.
     *
     * @return
     *      Thread ID of the receiver that can handle this event,
     *      or -1 if none.
     */
    protected abstract int findReceiverOfElement( String uri, String local );

    /**
     * Returns the handler that can receive the given attribute, or null.
     */
    protected abstract int findReceiverOfAttribute( String uri, String local );

    /**
     * Returns the handler that can receive text events, or null.
     */
    protected abstract int findReceiverOfText();




//
//
// join method
//
//


    /**
     * Set to true when this handler is in the process of
     * joining all branches.
     */
    private boolean isJoining = false;

    /**
     * Joins all the child receivers.
     *
     * <p>
     * This method is called by a child receiver when it sees
     * something that it cannot handle, or by this object itself
     * when it sees an event that it can't process.
     *
     * <p>
     * This method forces children to move to its final state,
     * then revert to the parent.
     *
     * @param source
     *      If this method is called by one of the child receivers,
     *      the receiver object. If this method is called by itself,
     *      null.
     */
    public void joinByEnterElement( NGCCEventReceiver source,
        String uri, String local, String qname, Attributes atts ) throws SAXException {

        if(isJoining)   return; // we are already in the process of joining. ignore.
        isJoining = true;

        // send special token to the rest of the branches.
        // these branches don't understand this token, so they will
        // try to move to a final state and send the token back to us,
        // which this object will ignore (because isJoining==true)
        // Otherwise branches will find an error.
        for( int i=0; i<_receivers.length; i++ )
            if( _receivers[i]!=source )
                _receivers[i].enterElement(uri,local,qname,atts);

        // revert to the parent
        _parent._source.replace(this,_parent);
        _parent.onChildCompleted(null,_cookie,true);
        // send this event to the parent
        _parent.enterElement(uri,local,qname,atts);
    }

    public void joinByLeaveElement( NGCCEventReceiver source,
        String uri, String local, String qname ) throws SAXException {

        if(isJoining)   return; // we are already in the process of joining. ignore.
        isJoining = true;

        // send special token to the rest of the branches.
        // these branches don't understand this token, so they will
        // try to move to a final state and send the token back to us,
        // which this object will ignore (because isJoining==true)
        // Otherwise branches will find an error.
        for( int i=0; i<_receivers.length; i++ )
            if( _receivers[i]!=source )
                _receivers[i].leaveElement(uri,local,qname);

        // revert to the parent
        _parent._source.replace(this,_parent);
        _parent.onChildCompleted(null,_cookie,true);
        // send this event to the parent
        _parent.leaveElement(uri,local,qname);
    }

    public void joinByEnterAttribute( NGCCEventReceiver source,
        String uri, String local, String qname ) throws SAXException {

        if(isJoining)   return; // we are already in the process of joining. ignore.
        isJoining = true;

        // send special token to the rest of the branches.
        // these branches don't understand this token, so they will
        // try to move to a final state and send the token back to us,
        // which this object will ignore (because isJoining==true)
        // Otherwise branches will find an error.
        for( int i=0; i<_receivers.length; i++ )
            if( _receivers[i]!=source )
                _receivers[i].enterAttribute(uri,local,qname);

        // revert to the parent
        _parent._source.replace(this,_parent);
        _parent.onChildCompleted(null,_cookie,true);
        // send this event to the parent
        _parent.enterAttribute(uri,local,qname);
    }

    public void joinByLeaveAttribute( NGCCEventReceiver source,
        String uri, String local, String qname ) throws SAXException {

        if(isJoining)   return; // we are already in the process of joining. ignore.
        isJoining = true;

        // send special token to the rest of the branches.
        // these branches don't understand this token, so they will
        // try to move to a final state and send the token back to us,
        // which this object will ignore (because isJoining==true)
        // Otherwise branches will find an error.
        for( int i=0; i<_receivers.length; i++ )
            if( _receivers[i]!=source )
                _receivers[i].leaveAttribute(uri,local,qname);

        // revert to the parent
        _parent._source.replace(this,_parent);
        _parent.onChildCompleted(null,_cookie,true);
        // send this event to the parent
        _parent.leaveAttribute(uri,local,qname);
    }

    public void joinByText( NGCCEventReceiver source,
        String value ) throws SAXException {

        if(isJoining)   return; // we are already in the process of joining. ignore.
        isJoining = true;

        // send special token to the rest of the branches.
        // these branches don't understand this token, so they will
        // try to move to a final state and send the token back to us,
        // which this object will ignore (because isJoining==true)
        // Otherwise branches will find an error.
        for( int i=0; i<_receivers.length; i++ )
            if( _receivers[i]!=source )
                _receivers[i].text(value);

        // revert to the parent
        _parent._source.replace(this,_parent);
        _parent.onChildCompleted(null,_cookie,true);
        // send this event to the parent
        _parent.text(value);
    }



//
//
// event dispatching methods
//
//

    public void sendEnterAttribute( int threadId,
        String uri, String local, String qname) throws SAXException {

        _receivers[threadId].enterAttribute(uri,local,qname);
    }

    public void sendEnterElement( int threadId,
        String uri, String local, String qname, Attributes atts) throws SAXException {

        _receivers[threadId].enterElement(uri,local,qname,atts);
    }

    public void sendLeaveAttribute( int threadId,
        String uri, String local, String qname) throws SAXException {

        _receivers[threadId].leaveAttribute(uri,local,qname);
    }

    public void sendLeaveElement( int threadId,
        String uri, String local, String qname) throws SAXException {

        _receivers[threadId].leaveElement(uri,local,qname);
    }

    public void sendText(int threadId, String value) throws SAXException {
        _receivers[threadId].text(value);
    }

}
