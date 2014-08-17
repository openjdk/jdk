/*
 * Copyright (c) 2005, 2013, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.xml.internal.stream.buffer.stax;

import com.sun.xml.internal.stream.buffer.AbstractCreator;
import java.util.ArrayList;
import java.util.List;

/**
 * {@link AbstractCreator} with additional convenience code.
 *
 * @author Paul Sandoz
 * @author Venu
 * @author Kohsuke Kawaguchi
 */
abstract class StreamBufferCreator extends AbstractCreator {

    private boolean checkAttributeValue = false;

    protected List<String> attributeValuePrefixes = new ArrayList<String>();

    protected void storeQualifiedName(int item, String prefix, String uri, String localName) {
        if (uri != null && uri.length() > 0) {
            if (prefix != null && prefix.length() > 0) {
                item |= FLAG_PREFIX;
                storeStructureString(prefix);
            }

            item |= FLAG_URI;
            storeStructureString(uri);
        }

        storeStructureString(localName);

        storeStructure(item);
    }

    protected final void storeNamespaceAttribute(String prefix, String uri) {
        int item = T_NAMESPACE_ATTRIBUTE;

        if (prefix != null && prefix.length() > 0) {
            item |= FLAG_PREFIX;
            storeStructureString(prefix);
        }

        if (uri != null && uri.length() > 0) {
            item |= FLAG_URI;
            storeStructureString(uri);
        }

        storeStructure(item);
    }

    protected final void storeAttribute(String prefix, String uri, String localName, String type, String value) {
        storeQualifiedName(T_ATTRIBUTE_LN, prefix, uri, localName);

        storeStructureString(type);
        storeContentString(value);
        if(checkAttributeValue && value.indexOf("://") == -1){  // the condition after && avoids looking inside URIs
            int firstIndex = value.indexOf(":");
            int lastIndex = value.lastIndexOf(":");  // Check last index of : as some SAML namespace have multiple ":"s
            if(firstIndex != -1 && lastIndex == firstIndex){
                String valuePrefix = value.substring(0, firstIndex);
                if(!attributeValuePrefixes.contains(valuePrefix)){
                    attributeValuePrefixes.add(valuePrefix);
                }
            }
        }
    }

    public final List getAttributeValuePrefixes(){
        return attributeValuePrefixes;
    }

    protected final void storeProcessingInstruction(String target, String data) {
        storeStructure(T_PROCESSING_INSTRUCTION);
        storeStructureString(target);
        storeStructureString(data);
    }

    public final boolean isCheckAttributeValue(){
        return checkAttributeValue;
    }

    public final void setCheckAttributeValue(boolean value){
        this.checkAttributeValue = value;
    }
}
