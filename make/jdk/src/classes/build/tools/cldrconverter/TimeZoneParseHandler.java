/*
 * Copyright (c) 2017, 2026, Oracle and/or its affiliates. All rights reserved.
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

package build.tools.cldrconverter;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

/**
 * Handles parsing of timezone.xml and produces a map from short timezone IDs to
 * tz database IDs.
 */

class TimeZoneParseHandler extends AbstractLDMLHandler<Object> {
    private static final String PREF_PREFIX = "preferred:";

    // CLDR aliases to IANA ids map. The initial capacity is estimated
    // from the number of aliases in timezone.xml as of CLDR v48
    private final Map<String, String> ianaAliasMap = HashMap.newHashMap(32);

    @Override
    public InputSource resolveEntity(String publicID, String systemID) throws IOException, SAXException {
        // avoid HTTP traffic to unicode.org
        if (systemID.startsWith(CLDRConverter.BCP47_LDML_DTD_SYSTEM_ID)) {
            return new InputSource((new File(CLDRConverter.LOCAL_BCP47_LDML_DTD)).toURI().toString());
        }
        return null;
    }

    @Override
    public void startElement(String uri, String localName, String qName, Attributes attributes) throws SAXException {
        switch (qName) {
        case "type":
            if (!isIgnored(attributes) &&
                    !attributes.getValue("description").equals("Metazone")) {
                if (attributes.getValue("deprecated").equals("true")) {
                    var preferred = attributes.getValue("preferred");
                    if (preferred != null && !preferred.isEmpty()) {
                        put(attributes.getValue("name"), PREF_PREFIX + preferred);
                    }
                } else {
                    var alias = attributes.getValue("alias");
                    var iana = attributes.getValue("iana");
                    if (iana != null) {
                        for (var a : alias.split("\\s+")) {
                            if (!a.equals(iana)) {
                                ianaAliasMap.put(a, iana);
                            }
                        }
                    }
                    put(attributes.getValue("name"), alias);
                }
            }
            break;
        default:
            // treat anything else as a container
            pushContainer(qName, attributes);
            break;
        }
    }

    @Override
    public void endDocument() throws SAXException {
        var map = getData();
        map.entrySet().stream()
            .filter(e -> e.getValue().toString().startsWith(PREF_PREFIX))
            .forEach(e -> map.put(e.getKey(),
                map.get(e.getValue().toString().substring(PREF_PREFIX.length()))));
    }

    Map<String, String> getIanaAliasMap() {
        return ianaAliasMap;
    }
}
