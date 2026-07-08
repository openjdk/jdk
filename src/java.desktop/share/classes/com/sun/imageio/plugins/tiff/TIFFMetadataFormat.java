/*
 * Copyright (c) 2005, 2025, Oracle and/or its affiliates. All rights reserved.
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
package com.sun.imageio.plugins.tiff;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.MissingResourceException;
import java.util.ResourceBundle;
import javax.imageio.metadata.IIOMetadataFormat;

public abstract class TIFFMetadataFormat implements IIOMetadataFormat {

    protected Map<String,TIFFElementInfo> elementInfoMap = new HashMap<String,TIFFElementInfo>();
    protected Map<String,TIFFAttrInfo> attrInfoMap = new HashMap<String,TIFFAttrInfo>();

    protected String resourceBaseName;
    protected String rootName;

    @Override
    public String getRootName() {
        return rootName;
    }

    private String getResource(String key, Locale locale) {
        if (locale == null) {
            locale = Locale.getDefault();
        }
        try {
            ResourceBundle bundle =
                ResourceBundle.getBundle(resourceBaseName, locale,
                                         this.getClass().getModule());
            return bundle.getString(key);
        } catch (MissingResourceException e) {
            return null;
        }
    }

    private TIFFElementInfo getElementInfo(String elementName) {
        if (elementName == null) {
            throw new NullPointerException("elementName == null!");
        }
        TIFFElementInfo info =
            elementInfoMap.get(elementName);
        if (info == null) {
            throw new IllegalArgumentException("No such element: " +
                                               elementName);
        }
        return info;
    }

    private TIFFAttrInfo getAttrInfo(String elementName, String attrName) {
        if (elementName == null) {
            throw new NullPointerException("elementName == null!");
        }
        if (attrName == null) {
            throw new NullPointerException("attrName == null!");
        }
        String key = elementName + "/" + attrName;
        TIFFAttrInfo info = attrInfoMap.get(key);
        if (info == null) {
            throw new IllegalArgumentException("No such attribute: " + key);
        }
        return info;
    }

    @Override
    public int getElementMinChildren(String elementName) {
        TIFFElementInfo info = getElementInfo(elementName);
        return info.minChildren;
    }

    @Override
    public int getElementMaxChildren(String elementName) {
        TIFFElementInfo info = getElementInfo(elementName);
        return info.maxChildren;
    }

    @Override
    public String getElementDescription(String elementName, Locale locale) {
        if (!elementInfoMap.containsKey(elementName)) {
            throw new IllegalArgumentException("No such element: " +
                                               elementName);
        }
        return getResource(elementName, locale);
    }

    @Override
    public int getChildPolicy(String elementName) {
        TIFFElementInfo info = getElementInfo(elementName);
        return info.childPolicy;
    }

    @Override
    public String[] getChildNames(String elementName) {
        TIFFElementInfo info = getElementInfo(elementName);
        return info.childNames;
    }

    @Override
    public String[] getAttributeNames(String elementName) {
        TIFFElementInfo info = getElementInfo(elementName);
        return info.attributeNames;
    }

    @Override
    public int getAttributeValueType(String elementName, String attrName) {
        TIFFAttrInfo info = getAttrInfo(elementName, attrName);
        return info.valueType;
    }

    @Override
    public int getAttributeDataType(String elementName, String attrName) {
        TIFFAttrInfo info = getAttrInfo(elementName, attrName);
        return info.dataType;
    }

    @Override
    public boolean isAttributeRequired(String elementName, String attrName) {
        TIFFAttrInfo info = getAttrInfo(elementName, attrName);
        return info.isRequired;
    }

    @Override
    public String getAttributeDefaultValue(String elementName,
                                           String attrName) {
        return null;
    }

    @Override
    public String[] getAttributeEnumerations(String elementName,
                                             String attrName) {
        throw new IllegalArgumentException("The attribute is not an enumeration.");
    }

    @Override
    public String getAttributeMinValue(String elementName, String attrName) {
        throw new IllegalArgumentException("The attribute is not a range.");
    }

    @Override
    public String getAttributeMaxValue(String elementName, String attrName) {
        throw new IllegalArgumentException("The attribute is not a range.");
    }

    @Override
    public int getAttributeListMinLength(String elementName, String attrName) {
        TIFFAttrInfo info = getAttrInfo(elementName, attrName);
        return info.listMinLength;
    }

    @Override
    public int getAttributeListMaxLength(String elementName, String attrName) {
        TIFFAttrInfo info = getAttrInfo(elementName, attrName);
        return info.listMaxLength;
    }

    @Override
    public String getAttributeDescription(String elementName, String attrName,
                                          Locale locale) {
        String key = elementName + "/" + attrName;
        if (!attrInfoMap.containsKey(key)) {
            throw new IllegalArgumentException("No such attribute: " + key);
        }
        return getResource(key, locale);
    }

    @Override
    public int getObjectValueType(String elementName) {
        TIFFElementInfo info = getElementInfo(elementName);
        return info.objectValueType;
    }

    @Override
    public Class<?> getObjectClass(String elementName) {
        TIFFElementInfo info = getElementInfo(elementName);
        if (info.objectValueType == VALUE_NONE) {
            throw new IllegalArgumentException(
                     "Element cannot contain an object value: " + elementName);
        }
        return info.objectClass;
    }

    @Override
    public Object getObjectDefaultValue(String elementName) {
        TIFFElementInfo info = getElementInfo(elementName);
        if (info.objectValueType == VALUE_NONE) {
            throw new IllegalArgumentException(
                     "Element cannot contain an object value: " + elementName);
        }
        return info.objectDefaultValue;
    }

    @Override
    public Object[] getObjectEnumerations(String elementName) {
        TIFFElementInfo info = getElementInfo(elementName);
        if (info.objectValueType == VALUE_NONE) {
            throw new IllegalArgumentException(
                     "Element cannot contain an object value: " + elementName);
        }
        return info.objectEnumerations;
    }

    @Override
    public Comparable<Object> getObjectMinValue(String elementName) {
        TIFFElementInfo info = getElementInfo(elementName);
        if (info.objectValueType == VALUE_NONE) {
            throw new IllegalArgumentException(
                     "Element cannot contain an object value: " + elementName);
        }
        return info.objectMinValue;
    }

    @Override
    public Comparable<Object> getObjectMaxValue(String elementName) {
        TIFFElementInfo info = getElementInfo(elementName);
        if (info.objectValueType == VALUE_NONE) {
            throw new IllegalArgumentException(
                     "Element cannot contain an object value: " + elementName);
        }
        return info.objectMaxValue;
    }

    @Override
    public int getObjectArrayMinLength(String elementName) {
        TIFFElementInfo info = getElementInfo(elementName);
        if (info.objectValueType == VALUE_NONE) {
            throw new IllegalArgumentException(
                     "Element cannot contain an object value: " + elementName);
        }
        return info.objectArrayMinLength;
    }

    @Override
    public int getObjectArrayMaxLength(String elementName) {
        TIFFElementInfo info = getElementInfo(elementName);
        if (info.objectValueType == VALUE_NONE) {
            throw new IllegalArgumentException(
                     "Element cannot contain an object value: " + elementName);
        }
        return info.objectArrayMaxLength;
    }

    public TIFFMetadataFormat() {}
}
