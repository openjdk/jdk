/*
 * Copyright 2006 Sun Microsystems, Inc.  All Rights Reserved.
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

package com.sun.tools.internal.xjc.reader;

import java.util.HashSet;
import java.util.Set;
import java.util.StringTokenizer;

import com.sun.tools.internal.xjc.Options;
import com.sun.tools.internal.xjc.Plugin;

import org.xml.sax.Attributes;
import org.xml.sax.ContentHandler;
import org.xml.sax.ErrorHandler;
import org.xml.sax.Locator;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;
import org.xml.sax.helpers.DefaultHandler;
import org.xml.sax.helpers.NamespaceSupport;
import org.xml.sax.helpers.XMLFilterImpl;

/**
 * This filter checks jaxb:extensionBindingPrefix and
 * pass/filter extension bindings.
 *
 * <p>
 * This filter also remembers enabled extension namespaces
 * and filters out any extension namespaces that doesn't belong
 * to those. The net effect is that disabled customizations
 * will never pass through this filter.
 *
 * <p>
 * Note that we can't just filter out all foreign namespaces,
 * as we need to use user-defined tags in documentations to generate javadoc.
 *
 * <p>
 * The class needs to know the list of extension binding namespaces
 * that the RI recognizes.
 * To add new URI, modify the isSupportedExtension method.
 *

 * @author
 *     Kohsuke Kawaguchi (kohsuke.kawaguchi@sun.com)
 */
public final class ExtensionBindingChecker extends XMLFilterImpl {

    /** Remembers in-scope namespace bindings. */
    private final NamespaceSupport nsSupport = new NamespaceSupport();

    /**
     * Number of the elements encountered. Used to detect the root element.
     */
    private int count=0;

    /**
     * Set of namespace URIs that designates enabled extensions.
     */
    private final Set<String> enabledExtensions = new HashSet<String>();

    private final Set<String> recognizableExtensions = new HashSet<String>();

    private Locator locator;

    /**
     * When we are pruning a sub tree, this field holds the depth of
     * elements that are being cut. Used to resume event forwarding.
     *
     * As long as this value is 0, we will pass through data.
     */
    private int cutDepth=0;

    /**
     * This object will receive SAX events while a sub tree is being
     * pruned.
     */
    private static final ContentHandler stub = new DefaultHandler();

    /**
     * This field remembers the user-specified ContentHandler.
     * So that we can restore it once the sub tree is completely pruned.
     */
    private ContentHandler next;

    /**
     * Namespace URI of the target schema language. Elements in this
     * namespace are always allowed.
     */
    private final String schemaLanguage;

    /**
     * If false, any use of extensions is reported as an error.
     */
    private final boolean allowExtensions;

    private final Options options;

    /**
     * @param handler
     *      This error handler will receive detected errors.
     */
    public ExtensionBindingChecker( String schemaLanguage, Options options, ErrorHandler handler ) {
        this.schemaLanguage = schemaLanguage;
        this.allowExtensions = options.compatibilityMode!=Options.STRICT;
        this.options = options;
        setErrorHandler(handler);

        for (Plugin plugin : options.getAllPlugins())
            recognizableExtensions.addAll(plugin.getCustomizationURIs());
        recognizableExtensions.add(Const.XJC_EXTENSION_URI);
    }

    /**
     * Checks if the given namespace URI is supported as the extension
     * bindings.
     */
    private boolean isSupportedExtension( String namespaceUri ) {
        if(namespaceUri.equals(Const.XJC_EXTENSION_URI))
            return true;
        return options.pluginURIs.contains(namespaceUri);
    }

    /**
     * Checks if the given namespace URI can be potentially recognized
     * by this XJC.
     */
    private boolean isRecognizableExtension( String namespaceUri ) {
        return recognizableExtensions.contains(namespaceUri);
    }

    /**
     * Returns true if the elements with the given namespace URI
     * should be blocked by this filter.
     */
    private boolean needsToBePruned( String uri ) {
        if( uri.equals(schemaLanguage) )
            return false;
        if( uri.equals(Const.JAXB_NSURI) )
            return false;
        if( enabledExtensions.contains(uri) )
            return false;

        // we don't need to prune something unless
        // the rest of the processor recognizes it as something special.
        // this allows us to send the documentation and other harmless
        // foreign XML fragments, which may be picked up as documents.
        return isRecognizableExtension(uri);
    }



    public void startDocument() throws SAXException {
        super.startDocument();

        count=0;
        cutDepth=0;
        nsSupport.reset();
        enabledExtensions.clear();
    }

    public void startPrefixMapping(String prefix, String uri) throws SAXException {
        super.startPrefixMapping(prefix, uri);
        nsSupport.pushContext();
        nsSupport.declarePrefix(prefix,uri);
    }

    public void endPrefixMapping(String prefix) throws SAXException {
        super.endPrefixMapping(prefix);
        nsSupport.popContext();
    }

    public void startElement(String namespaceURI, String localName, String qName, Attributes atts)
        throws SAXException {

        if( cutDepth==0 ) {
            String v = atts.getValue(Const.JAXB_NSURI,"extensionBindingPrefixes");
            if(v!=null) {
                if(count!=0)
                    // the binding attribute is allowed only at the root level.
                    error( Messages.ERR_UNEXPECTED_EXTENSION_BINDING_PREFIXES.format() );

                if(!allowExtensions)
                    error( Messages.ERR_VENDOR_EXTENSION_DISALLOWED_IN_STRICT_MODE.format() );

                // then remember the associated namespace URIs.
                StringTokenizer tokens = new StringTokenizer(v);
                while(tokens.hasMoreTokens()) {
                    String prefix = tokens.nextToken();
                    String uri = nsSupport.getURI(prefix);
                    if( uri==null ) {
                        // undeclared prefix
                        error( Messages.ERR_UNDECLARED_PREFIX.format(prefix) );
                    } else {
                        if( !isRecognizableExtension(uri) )
                            // not the namespace URI we know of
                            error( Messages.ERR_UNSUPPORTED_EXTENSION.format(prefix) );
                        else
                        if( !isSupportedExtension(uri) ) {
                            // recognizable but not not supported, meaning
                            // the plug-in isn't enabled

                            // look for plug-in that handles this URI
                            Plugin owner = null;
                            for( Plugin p : options.getAllPlugins() ) {
                                if(p.getCustomizationURIs().contains(uri)) {
                                    owner = p;
                                    break;
                                }
                            }
                            if(owner!=null)
                                // we know the plug-in that supports this namespace, but it's not enabled
                                error( Messages.ERR_PLUGIN_NOT_ENABLED.format(owner.getOptionName(),uri));
                            else {
                                // this shouldn't happen, but be defensive...
                                error( Messages.ERR_UNSUPPORTED_EXTENSION.format(prefix) );
                            }
                        }

                        // as an error recovery enable this namespace URI anyway.
                        enabledExtensions.add(uri);
                    }
                }
            }

            if( needsToBePruned(namespaceURI) ) {
                // start pruning the tree. Call the super class method directly.
                if( isRecognizableExtension(namespaceURI) ) {
                    // but this is a supported customization.
                    // isn't the user forgetting @jaxb:extensionBindingPrefixes?
                    warning( Messages.ERR_SUPPORTED_EXTENSION_IGNORED.format(namespaceURI) );
                }
                super.setContentHandler(stub);
                cutDepth=1;
            }
            else
            if(options.pluginURIs.contains(namespaceURI)) {
                // make sure that this is a valid tag name
                boolean correct = false;
                for( Plugin p : options.activePlugins ) {
                    if(p.isCustomizationTagName(namespaceURI,localName)) {
                        correct = true;
                        break;
                    }
                }
                if(!correct) {
                    error( Messages.ERR_ILLEGAL_CUSTOMIZATION_TAGNAME.format(qName) );
                    super.setContentHandler(stub);
                    cutDepth=1;
                }
            }
        } else
            cutDepth++;

        count++;
        super.startElement(namespaceURI, localName, qName, atts);
    }

    public void endElement(String namespaceURI, String localName, String qName) throws SAXException {
        super.endElement(namespaceURI, localName, qName);

        if( cutDepth!=0 ) {
            cutDepth--;
            if( cutDepth == 0 )
                // pruning completed. restore the user handler
                super.setContentHandler(next);
        }
    }

    public void setDocumentLocator(Locator locator) {
        super.setDocumentLocator(locator);
        this.locator = locator;
    }

    public void setContentHandler(ContentHandler handler) {
        next = handler;
        // changes take effect immediately unless the sub-tree is being pruned
        if(getContentHandler()!=stub)
            super.setContentHandler(handler);
    }


    /**
     * Reports an error and returns the created SAXParseException
     */
    private SAXParseException error( String msg ) throws SAXException {
        SAXParseException spe = new SAXParseException( msg, locator );
        getErrorHandler().error(spe);
        return spe;
    }

    /**
     * Reports a warning.
     */
    private void warning( String msg ) throws SAXException {
        SAXParseException spe = new SAXParseException( msg, locator );
        getErrorHandler().warning(spe);
    }

}
