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
 *
 * THIS FILE WAS MODIFIED BY SUN MICROSYSTEMS, INC.
 */



package com.sun.xml.internal.fastinfoset.sax;

import com.sun.xml.internal.fastinfoset.EncodingConstants;
import com.sun.xml.internal.fastinfoset.QualifiedName;
import com.sun.xml.internal.fastinfoset.algorithm.BuiltInEncodingAlgorithmFactory;
import java.io.IOException;
import java.util.Map;
import com.sun.xml.internal.org.jvnet.fastinfoset.EncodingAlgorithm;
import com.sun.xml.internal.org.jvnet.fastinfoset.EncodingAlgorithmException;
import com.sun.xml.internal.org.jvnet.fastinfoset.EncodingAlgorithmIndexes;
import com.sun.xml.internal.org.jvnet.fastinfoset.FastInfosetException;

import com.sun.xml.internal.org.jvnet.fastinfoset.sax.EncodingAlgorithmAttributes;
import com.sun.xml.internal.fastinfoset.CommonResourceBundle;

public class AttributesHolder implements EncodingAlgorithmAttributes {
    private static final int DEFAULT_CAPACITY = 8;

    private Map _registeredEncodingAlgorithms;

    private int _attributeCount;

    private QualifiedName[] _names;
    private String[] _values;

    private String[] _algorithmURIs;
    private int[] _algorithmIds;
    private Object[] _algorithmData;

    public AttributesHolder() {
        _names = new QualifiedName[DEFAULT_CAPACITY];
        _values = new String[DEFAULT_CAPACITY];

        _algorithmURIs = new String[DEFAULT_CAPACITY];
        _algorithmIds = new int[DEFAULT_CAPACITY];
        _algorithmData = new Object[DEFAULT_CAPACITY];
    }

    public AttributesHolder(Map registeredEncodingAlgorithms) {
        this();
        _registeredEncodingAlgorithms = registeredEncodingAlgorithms;
    }

    // org.xml.sax.Attributes

    public final int getLength() {
        return _attributeCount;
    }

    public final String getLocalName(int index) {
        return _names[index].localName;
    }

    public final String getQName(int index) {
        return _names[index].getQNameString();
    }

    public final String getType(int index) {
        return "CDATA";
    }

    public final String getURI(int index) {
        return _names[index].namespaceName;
    }

    public final String getValue(int index) {
        final String value = _values[index];
        if (value != null) {
            return value;
        }

        if (_algorithmData[index] == null || _registeredEncodingAlgorithms == null) {
            return null;
        }

        try {
            return _values[index] = convertEncodingAlgorithmDataToString(
                    _algorithmIds[index],
                    _algorithmURIs[index],
                    _algorithmData[index]).toString();
        } catch (IOException e) {
            return null;
        } catch (FastInfosetException e) {
            return null;
        }
    }

    public final int getIndex(String qName) {
        int i = qName.indexOf(':');
        String prefix = "";
        String localName = qName;
        if (i >= 0) {
            prefix = qName.substring(0, i);
            localName = qName.substring(i + 1);
        }

        for (i = 0; i < _attributeCount; i++) {
            QualifiedName name = _names[i];
            if (localName.equals(name.localName) &&
                prefix.equals(name.prefix)) {
                return i;
            }
        }
        return -1;
    }

    public final String getType(String qName) {
        int index = getIndex(qName);
        if (index >= 0) {
            return "CDATA";
        } else {
            return null;
        }
    }

    public final String getValue(String qName) {
        int index = getIndex(qName);
        if (index >= 0) {
            return _values[index];
        } else {
            return null;
        }
    }

    public final int getIndex(String uri, String localName) {
        for (int i = 0; i < _attributeCount; i++) {
            QualifiedName name = _names[i];
            if (localName.equals(name.localName) &&
                uri.equals(name.namespaceName)) {
                return i;
            }
        }
        return -1;
    }

    public final String getType(String uri, String localName) {
        int index = getIndex(uri, localName);
        if (index >= 0) {
            return "CDATA";
        } else {
            return null;
        }
    }

    public final String getValue(String uri, String localName) {
        int index = getIndex(uri, localName);
        if (index >= 0) {
            return _values[index];
        } else {
            return null;
        }
    }

    public final void clear() {
        for (int i = 0; i < _attributeCount; i++) {
            _values[i] = null;
            _algorithmData[i] = null;
        }
        _attributeCount = 0;
    }

    // EncodingAlgorithmAttributes

    public final String getAlgorithmURI(int index) {
        return _algorithmURIs[index];
    }

    public final int getAlgorithmIndex(int index) {
        return _algorithmIds[index];
    }

    public final Object getAlgorithmData(int index) {
        return _algorithmData[index];
    }

    public String getAlpababet(int index) {
        return null;
    }

    public boolean getToIndex(int index) {
        return false;
    }

    // -----

    public final void addAttribute(QualifiedName name, String value) {
        if (_attributeCount == _names.length) {
            resize();
        }
        _names[_attributeCount] = name;
        _values[_attributeCount++] = value;
    }

    public final void addAttributeWithAlgorithmData(QualifiedName name, String URI, int id, Object data) {
        if (_attributeCount == _names.length) {
            resize();
        }
        _names[_attributeCount] = name;
        _values[_attributeCount] = null;

        _algorithmURIs[_attributeCount] = URI;
        _algorithmIds[_attributeCount] = id;
        _algorithmData[_attributeCount++] = data;
    }

    public final QualifiedName getQualifiedName(int index) {
        return _names[index];
    }

    public final String getPrefix(int index) {
        return _names[index].prefix;
    }

    private final void resize() {
        final int newLength = _attributeCount * 3 / 2 + 1;

        QualifiedName[] names = new QualifiedName[newLength];
        String[] values = new String[newLength];

        String[] algorithmURIs = new String[newLength];
        int[] algorithmIds = new int[newLength];
        Object[] algorithmData = new Object[newLength];

        System.arraycopy(_names, 0, names, 0, _attributeCount);
        System.arraycopy(_values, 0, values, 0, _attributeCount);

        System.arraycopy(_algorithmURIs, 0, algorithmURIs, 0, _attributeCount);
        System.arraycopy(_algorithmIds, 0, algorithmIds, 0, _attributeCount);
        System.arraycopy(_algorithmData, 0, algorithmData, 0, _attributeCount);

        _names = names;
        _values = values;

        _algorithmURIs = algorithmURIs;
        _algorithmIds = algorithmIds;
        _algorithmData = algorithmData;
    }

    private final StringBuffer convertEncodingAlgorithmDataToString(int identifier, String URI, Object data) throws FastInfosetException, IOException {
        EncodingAlgorithm ea = null;
        if (identifier < EncodingConstants.ENCODING_ALGORITHM_BUILTIN_END) {
            ea = BuiltInEncodingAlgorithmFactory.table[identifier];
        } else if (identifier == EncodingAlgorithmIndexes.CDATA) {
            throw new EncodingAlgorithmException(CommonResourceBundle.getInstance().getString("message.CDATAAlgorithmNotSupported"));
        } else if (identifier >= EncodingConstants.ENCODING_ALGORITHM_APPLICATION_START) {
            if (URI == null) {
                throw new EncodingAlgorithmException(CommonResourceBundle.getInstance().getString("message.URINotPresent") + identifier);
            }

            ea = (EncodingAlgorithm)_registeredEncodingAlgorithms.get(URI);
            if (ea == null) {
                throw new EncodingAlgorithmException(CommonResourceBundle.getInstance().getString("message.algorithmNotRegistered") + URI);
            }
        } else {
            // Reserved built-in algorithms for future use
            // TODO should use sax property to decide if event will be
            // reported, allows for support through handler if required.
            throw new EncodingAlgorithmException(CommonResourceBundle.getInstance().getString("message.identifiers10to31Reserved"));
        }

        final StringBuffer sb = new StringBuffer();
        ea.convertToCharacters(data, sb);
        return sb;
    }

}
