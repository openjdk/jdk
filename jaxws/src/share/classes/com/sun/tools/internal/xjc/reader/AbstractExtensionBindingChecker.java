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
package com.sun.tools.internal.xjc.reader;

import java.util.Set;
import java.util.HashSet;

import com.sun.tools.internal.xjc.util.SubtreeCutter;
import com.sun.tools.internal.xjc.Options;
import com.sun.tools.internal.xjc.Plugin;
import com.sun.xml.internal.bind.v2.util.EditDistance;

import org.xml.sax.helpers.NamespaceSupport;
import org.xml.sax.Locator;
import org.xml.sax.ErrorHandler;
import org.xml.sax.SAXParseException;
import org.xml.sax.SAXException;

/**
 * Common code between {@code DTDExtensionBindingChecker} and {@link ExtensionBindingChecker}.
 *
 * @author Kohsuke Kawaguchi
 */
public abstract class AbstractExtensionBindingChecker extends SubtreeCutter {
    /** Remembers in-scope namespace bindings. */
    protected final NamespaceSupport nsSupport = new NamespaceSupport();

    /**
     * Set of namespace URIs that designates enabled extensions.
     */
    protected final Set<String> enabledExtensions = new HashSet<String>();

    private final Set<String> recognizableExtensions = new HashSet<String>();

    private Locator locator;

    /**
     * Namespace URI of the target schema language. Elements in this
     * namespace are always allowed.
     */
    protected final String schemaLanguage;

    /**
     * If false, any use of extensions is reported as an error.
     */
    protected final boolean allowExtensions;

    private final Options options;

    /**
     * @param handler
     *      This error handler will receive detected errors.
     */
    public AbstractExtensionBindingChecker( String schemaLanguage, Options options, ErrorHandler handler ) {
        this.schemaLanguage = schemaLanguage;
        this.allowExtensions = options.compatibilityMode!=Options.STRICT;
        this.options = options;
        setErrorHandler(handler);

        for (Plugin plugin : options.getAllPlugins())
            recognizableExtensions.addAll(plugin.getCustomizationURIs());
        recognizableExtensions.add(Const.XJC_EXTENSION_URI);
    }

    /**
     * Verify that the given URI is indeed a valid extension namespace URI,
     * and if so enable it.
     * <p>
     * This method does all the error handling.
     */
    protected final void checkAndEnable(String uri) throws SAXException {
        if( !isRecognizableExtension(uri) ) {
            String nearest = EditDistance.findNearest(uri, recognizableExtensions);
            // not the namespace URI we know of
            error( Messages.ERR_UNSUPPORTED_EXTENSION.format(uri,nearest) );
        } else
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
                error( Messages.ERR_UNSUPPORTED_EXTENSION.format(uri) );
            }
        }

        // as an error recovery enable this namespace URI anyway.
        enabledExtensions.add(uri);
    }

    /**
     * If the tag name belongs to a plugin namespace-wise, check its local name
     * to make sure it's correct.
     */
    protected final void verifyTagName(String namespaceURI, String localName, String qName) throws SAXException {
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
                startCutting();
            }
        }
    }

    /**
     * Checks if the given namespace URI is supported as the extension
     * bindings.
     */
    protected final boolean isSupportedExtension( String namespaceUri ) {
        return namespaceUri.equals(Const.XJC_EXTENSION_URI) || options.pluginURIs.contains(namespaceUri);
    }

    /**
     * Checks if the given namespace URI can be potentially recognized
     * by this XJC.
     */
    protected final boolean isRecognizableExtension( String namespaceUri ) {
        return recognizableExtensions.contains(namespaceUri);
    }


    public void setDocumentLocator(Locator locator) {
        super.setDocumentLocator(locator);
        this.locator = locator;
    }

    public void startDocument() throws SAXException {
        super.startDocument();

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


    /**
     * Reports an error and returns the created SAXParseException
     */
    protected final SAXParseException error( String msg ) throws SAXException {
        SAXParseException spe = new SAXParseException( msg, locator );
        getErrorHandler().error(spe);
        return spe;
    }

    /**
     * Reports a warning.
     */
    protected final void warning( String msg ) throws SAXException {
        SAXParseException spe = new SAXParseException( msg, locator );
        getErrorHandler().warning(spe);
    }
}
