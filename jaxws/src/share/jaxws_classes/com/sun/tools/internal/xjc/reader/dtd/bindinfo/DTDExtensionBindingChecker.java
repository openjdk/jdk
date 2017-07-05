/*
 * Copyright (c) 1997, 2011, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

package com.sun.tools.internal.xjc.reader.dtd.bindinfo;

import com.sun.tools.internal.xjc.Options;
import com.sun.tools.internal.xjc.reader.AbstractExtensionBindingChecker;
import com.sun.tools.internal.xjc.reader.Const;

import org.xml.sax.Attributes;
import org.xml.sax.ErrorHandler;
import org.xml.sax.SAXException;
import org.xml.sax.XMLFilter;

/**
 * {@link XMLFilter} that checks the use of extension namespace URIs
 * (to see if they have corresponding plugins), and otherwise report an error.
 *
 * <p>
 * This code also masks the recognized extensions from the validator that
 * will be plugged as the next component to this.
 *
 * @author Kohsuke Kawaguchi
 */
final class DTDExtensionBindingChecker extends AbstractExtensionBindingChecker {
    public DTDExtensionBindingChecker(String schemaLanguage, Options options, ErrorHandler handler) {
        super(schemaLanguage, options, handler);
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
        if( uri.equals(Const.XJC_EXTENSION_URI) )
            return false;
        // we don't want validator to see extensions that we understand ,
        // because they will complain.
        // OTOH, if  this is an extension that we didn't understand,
        // we want the validator to report an error
        return enabledExtensions.contains(uri);
    }



    public void startElement(String uri, String localName, String qName, Attributes atts)
        throws SAXException {

        if( !isCutting() ) {
            if(!uri.equals("")) {
                // "" is the standard namespace
                checkAndEnable(uri);

                verifyTagName(uri, localName, qName);

                if(needsToBePruned(uri))
                    startCutting();
            }
        }

        super.startElement(uri, localName, qName, atts);
    }
}
