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

import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Stack;
import java.util.StringTokenizer;

import org.xml.sax.Attributes;
import org.xml.sax.ContentHandler;
import org.xml.sax.Locator;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

/**
 * Runtime Engine for RELAXNGCC execution.
 *
 * This class has the following functionalities:
 *
 * <ol>
 *  <li>Managing a stack of NGCCHandler objects and
 *      switching between them appropriately.
 *
 *  <li>Keep track of all Attributes.
 *
 *  <li>manage mapping between namespace URIs and prefixes.
 *
 *  <li>TODO: provide support for interleaving.
 *
 * @version $Id: NGCCRuntime.java,v 1.16 2003/03/23 02:47:46 okajima Exp $
 * @author Kohsuke Kawaguchi (kk@kohsuke.org)
 */
public class NGCCRuntime implements ContentHandler, NGCCEventSource {

    public NGCCRuntime() {
        reset();
    }

    /**
     * Sets the root handler, which will be used to parse the
     * root element.
     * <p>
     * This method can be called right after the object is created
     * or the reset method is called. You can't replace the root
     * handler while parsing is in progress.
     * <p>
     * Usually a generated class that corresponds to the &lt;start>
     * pattern will be used as the root handler, but any NGCCHandler
     * can be a root handler.
     *
     * @exception IllegalStateException
     *      If this method is called but it doesn't satisfy the
     *      pre-condition stated above.
     */
    public void setRootHandler( NGCCHandler rootHandler ) {
        if(currentHandler!=null)
            throw new IllegalStateException();
        currentHandler = rootHandler;
    }


    /**
     * Cleans up all the data structure so that the object can be reused later.
     * Normally, applications do not need to call this method directly,
     *
     * as the runtime resets itself after the endDocument method.
     */
    public void reset() {
        attStack.clear();
        currentAtts = null;
        currentHandler = null;
        indent=0;
        locator = null;
        namespaces.clear();
        needIndent = true;
        redirect = null;
        redirectionDepth = 0;
        text = new StringBuffer();

        // add a dummy attributes at the bottom as a "centinel."
        attStack.push(new AttributesImpl());
    }

    // current content handler can be acccessed via set/getContentHandler.

    private Locator locator;
    public void setDocumentLocator( Locator _loc ) { this.locator=_loc; }
    /**
     * Gets the source location of the current event.
     *
     * <p>
     * One can call this method from RelaxNGCC handlers to access
     * the line number information. Note that to
     */
    public Locator getLocator() { return locator; }


    /** stack of {@link Attributes}. */
    private final Stack attStack = new Stack();
    /** current attributes set. always equal to attStack.peek() */
    private AttributesImpl currentAtts;

    /**
     * Attributes that belong to the current element.
     * <p>
     * It's generally not recommended for applications to use
     * this method. RelaxNGCC internally removes processed attributes,
     * so this doesn't correctly reflect all the attributes an element
     * carries.
     */
    public Attributes getCurrentAttributes() {
        return currentAtts;
    }

    /** accumulated text. */
    private StringBuffer text = new StringBuffer();




    /** The current NGCCHandler. Always equals to handlerStack.peek() */
    private NGCCEventReceiver currentHandler;

    public int replace( NGCCEventReceiver o, NGCCEventReceiver n ) {
        if(o!=currentHandler)
            throw new IllegalStateException();  // bug of RelaxNGCC
        currentHandler = n;

        return 0;   // we only have one thread.
    }

    /**
     * Processes buffered text.
     *
     * This method will be called by the start/endElement event to process
     * buffered text as a text event.
     *
     * <p>
     * Whitespace handling is a tricky business. Consider the following
     * schema fragment:
     *
     * <xmp>
     * <element name="foo">
     *   <choice>
     *     <element name="bar"><empty/></element>
     *     <text/>
     *   </choice>
     * </element>
     * </xmp>
     *
     * Assume we hit the following instance:
     * <xmp>
     * <foo> <bar/></foo>
     * </xmp>
     *
     * Then this first space needs to be ignored (for otherwise, we will
     * end up treating this space as the match to &lt;text/> and won't
     * be able to process &lt;bar>.)
     *
     * Now assume the following instance:
     * <xmp>
     * <foo/>
     * </xmp>
     *
     * This time, we need to treat this empty string as a text, for
     * otherwise we won't be able to accept this instance.
     *
     * <p>
     * This is very difficult to solve in general, but one seemingly
     * easy solution is to use the type of next event. If a text is
     * followed by a start tag, it follows from the constraint on
     * RELAX NG that that text must be either whitespaces or a match
     * to &lt;text/>.
     *
     * <p>
     * On the contrary, if a text is followed by a end tag, then it
     * cannot be whitespace unless the content model can accept empty,
     * in which case sending a text event will be harmlessly ignored
     * by the NGCCHandler.
     *
     * <p>
     * Thus this method take one parameter, which controls the
     * behavior of this method.
     *
     * <p>
     * TODO: according to the constraint of RELAX NG, if characters
     * follow an end tag, then they must be either whitespaces or
     * must match to &lt;text/>.
     *
     * @param   possiblyWhitespace
     *      True if the buffered character can be ignorabale. False if
     *      it needs to be consumed.
     */
    private void processPendingText(boolean ignorable) throws SAXException {
        if(ignorable && text.toString().trim().length()==0)
            ; // ignore. See the above javadoc comment for the description
        else
            currentHandler.text(text.toString());   // otherwise consume this token

        // truncate StringBuffer, but avoid excessive allocation.
        if(text.length()>1024)  text = new StringBuffer();
        else                    text.setLength(0);
    }

    public void processList( String str ) throws SAXException {
        StringTokenizer t = new StringTokenizer(str, " \t\r\n");
        while(t.hasMoreTokens())
            currentHandler.text(t.nextToken());
    }

    public void startElement(String uri, String localname, String qname, Attributes atts)
            throws SAXException {

        uri = uri.intern();
        localname = localname.intern();
        qname = qname.intern();

        if(redirect!=null) {
            redirect.startElement(uri,localname,qname,atts);
            redirectionDepth++;
        } else {
            processPendingText(true);
    //        System.out.println("startElement:"+localname+"->"+_attrStack.size());
            currentHandler.enterElement(uri, localname, qname, atts);
        }
    }

    /**
     * Called by the generated handler code when an enter element
     * event is consumed.
     *
     * <p>
     * Pushes a new attribute set.
     *
     * <p>
     * Note that attributes are NOT pushed at the startElement method,
     * because the processing of the enterElement event can trigger
     * other attribute events and etc.
     * <p>
     * This method will be called from one of handlers when it truely
     * consumes the enterElement event.
     */
    public void onEnterElementConsumed(
        String uri, String localName, String qname,Attributes atts) throws SAXException {
        attStack.push(currentAtts=new AttributesImpl(atts));
        nsEffectiveStack.push( new Integer(nsEffectivePtr) );
        nsEffectivePtr = namespaces.size();
    }

    public void onLeaveElementConsumed(String uri, String localName, String qname) throws SAXException {
        attStack.pop();
        if(attStack.isEmpty())
            currentAtts = null;
        else
            currentAtts = (AttributesImpl)attStack.peek();
        nsEffectivePtr = ((Integer)nsEffectiveStack.pop()).intValue();
    }

    public void endElement(String uri, String localname, String qname)
            throws SAXException {

        uri = uri.intern();
        localname = localname.intern();
        qname = qname.intern();

        if(redirect!=null) {
            redirect.endElement(uri,localname,qname);
            redirectionDepth--;

            if(redirectionDepth!=0)
                return;

            // finished redirection.
            for( int i=0; i<namespaces.size(); i+=2 )
                redirect.endPrefixMapping((String)namespaces.get(i));
            redirect.endDocument();

            redirect = null;
            // then process this element normally
        }

        processPendingText(false);

        currentHandler.leaveElement(uri, localname, qname);
//        System.out.println("endElement:"+localname);
    }

    public void characters(char[] ch, int start, int length) throws SAXException {
        if(redirect!=null)
            redirect.characters(ch,start,length);
        else
            text.append(ch,start,length);
    }
    public void ignorableWhitespace(char[] ch, int start, int length) throws SAXException {
        if(redirect!=null)
            redirect.ignorableWhitespace(ch,start,length);
        else
            text.append(ch,start,length);
    }

    public int getAttributeIndex(String uri, String localname) {
        return currentAtts.getIndex(uri, localname);
    }
    public void consumeAttribute(int index) throws SAXException {
        final String uri    = currentAtts.getURI(index).intern();
        final String local  = currentAtts.getLocalName(index).intern();
        final String qname  = currentAtts.getQName(index).intern();
        final String value  = currentAtts.getValue(index);
        currentAtts.removeAttribute(index);

        currentHandler.enterAttribute(uri,local,qname);
        currentHandler.text(value);
        currentHandler.leaveAttribute(uri,local,qname);
    }


    public void startPrefixMapping( String prefix, String uri ) throws SAXException {
        if(redirect!=null)
            redirect.startPrefixMapping(prefix,uri);
        else {
            namespaces.add(prefix);
            namespaces.add(uri);
        }
    }

    public void endPrefixMapping( String prefix ) throws SAXException {
        if(redirect!=null)
            redirect.endPrefixMapping(prefix);
        else {
            namespaces.remove(namespaces.size()-1);
            namespaces.remove(namespaces.size()-1);
        }
    }

    public void skippedEntity( String name ) throws SAXException {
        if(redirect!=null)
            redirect.skippedEntity(name);
    }

    public void processingInstruction( String target, String data ) throws SAXException {
        if(redirect!=null)
            redirect.processingInstruction(target,data);
    }

    /** Impossible token. This value can never be a valid XML name. */
    static final String IMPOSSIBLE = "\u0000";

    public void endDocument() throws SAXException {
        // consume the special "end document" token so that all the handlers
        // currently at the stack will revert to their respective parents.
        //
        // this is necessary to handle a grammar like
        // <start><ref name="X"/></start>
        // <define name="X">
        //   <element name="root"><empty/></element>
        // </define>
        //
        // With this grammar, when the endElement event is consumed, two handlers
        // are on the stack (because a child object won't revert to its parent
        // unless it sees a next event.)

        // pass around an "impossible" token.
        currentHandler.leaveElement(IMPOSSIBLE,IMPOSSIBLE,IMPOSSIBLE);

        reset();
    }
    public void startDocument() throws SAXException {}




//
//
// event dispatching methods
//
//

    public void sendEnterAttribute( int threadId,
        String uri, String local, String qname) throws SAXException {

        currentHandler.enterAttribute(uri,local,qname);
    }

    public void sendEnterElement( int threadId,
        String uri, String local, String qname, Attributes atts) throws SAXException {

        currentHandler.enterElement(uri,local,qname,atts);
    }

    public void sendLeaveAttribute( int threadId,
        String uri, String local, String qname) throws SAXException {

        currentHandler.leaveAttribute(uri,local,qname);
    }

    public void sendLeaveElement( int threadId,
        String uri, String local, String qname) throws SAXException {

        currentHandler.leaveElement(uri,local,qname);
    }

    public void sendText(int threadId, String value) throws SAXException {
        currentHandler.text(value);
    }


//
//
// redirection of SAX2 events.
//
//
    /** When redirecting a sub-tree, this value will be non-null. */
    private ContentHandler redirect = null;

    /**
     * Counts the depth of the elements when we are re-directing
     * a sub-tree to another ContentHandler.
     */
    private int redirectionDepth = 0;

    /**
     * This method can be called only from the enterElement handler.
     * The sub-tree rooted at the new element will be redirected
     * to the specified ContentHandler.
     *
     * <p>
     * Currently active NGCCHandler will only receive the leaveElement
     * event of the newly started element.
     *
     * @param   uri,local,qname
     *      Parameters passed to the enter element event. Used to
     *      simulate the startElement event for the new ContentHandler.
     */
    public void redirectSubtree( ContentHandler child,
        String uri, String local, String qname ) throws SAXException {

        redirect = child;
        redirect.setDocumentLocator(locator);
        redirect.startDocument();

        // TODO: when a prefix is re-bound to something else,
        // the following code is potentially dangerous. It should be
        // modified to report active bindings only.
        for( int i=0; i<namespaces.size(); i+=2 )
            redirect.startPrefixMapping(
                (String)namespaces.get(i),
                (String)namespaces.get(i+1)
            );

        redirect.startElement(uri,local,qname,currentAtts);
        redirectionDepth=1;
    }

//
//
// validation context implementation
//
//
    /** in-scope namespace mapping.
     * namespaces[2n  ] := prefix
     * namespaces[2n+1] := namespace URI */
    private final ArrayList namespaces = new ArrayList();
    /**
     * Index on the namespaces array, which points to
     * the top of the effective bindings. Because of the
     * timing difference between the startPrefixMapping method
     * and the execution of the corresponding actions,
     * this value can be different from <code>namespaces.size()</code>.
     * <p>
     * For example, consider the following schema:
     * <pre><xmp>
     *  <oneOrMore>
     *   <element name="foo"><empty/></element>
     *  </oneOrMore>
     *  code fragment X
     *  <element name="bob"/>
     * </xmp></pre>
     * Code fragment X is executed after we see a startElement event,
     * but at this time the namespaces variable already include new
     * namespace bindings declared on "bob".
     */
    private int nsEffectivePtr=0;

    /**
     * Stack to preserve old nsEffectivePtr values.
     */
    private final Stack nsEffectiveStack = new Stack();

    public String resolveNamespacePrefix( String prefix ) {
        for( int i = nsEffectivePtr-2; i>=0; i-=2 )
            if( namespaces.get(i).equals(prefix) )
                return (String)namespaces.get(i+1);

        // no binding was found.
        if(prefix.equals(""))   return "";  // return the default no-namespace
        if(prefix.equals("xml"))    // pre-defined xml prefix
            return "http://www.w3.org/XML/1998/namespace";
        else    return null;    // prefix undefined
    }


// error reporting
    protected void unexpectedX(String token) throws SAXException {
        throw new SAXParseException(MessageFormat.format(
            "Unexpected {0} appears at line {1} column {2}",
            new Object[]{
                token,
                new Integer(getLocator().getLineNumber()),
                new Integer(getLocator().getColumnNumber()) }),
            getLocator());
    }




//
//
// trace functions
//
//
    private int indent=0;
    private boolean needIndent=true;
    private void printIndent() {
        for( int i=0; i<indent; i++ )
            System.out.print("  ");
    }
    public void trace( String s ) {
        if(needIndent) {
            needIndent=false;
            printIndent();
        }
        System.out.print(s);
    }
    public void traceln( String s ) {
        trace(s);
        trace("\n");
        needIndent=true;
    }
}
