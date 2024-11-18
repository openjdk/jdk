/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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
package jdk.jpackage.internal.util;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import javax.xml.stream.XMLStreamWriter;

final class PrettyPrintHandler implements InvocationHandler {

    public PrettyPrintHandler(XMLStreamWriter target) {
        this.target = target;
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        switch (method.getName()) {
            case "writeStartElement":
                // update state of parent node
                if (depth > 0) {
                    hasChildElement.put(depth - 1, true);
                }
                // reset state of current node
                hasChildElement.put(depth, false);
                // indent for current depth
                target.writeCharacters(EOL);
                target.writeCharacters(repeat(depth, INDENT));
                depth++;
                break;
            case "writeEndElement":
                depth--;
                if (hasChildElement.get(depth) == true) {
                    target.writeCharacters(EOL);
                    target.writeCharacters(repeat(depth, INDENT));
                }
                break;
            case "writeProcessingInstruction":
            case "writeEmptyElement":
                // update state of parent node
                if (depth > 0) {
                    hasChildElement.put(depth - 1, true);
                }
                // indent for current depth
                target.writeCharacters(EOL);
                target.writeCharacters(repeat(depth, INDENT));
                break;
            default:
                break;
        }
        method.invoke(target, args);
        return null;
    }

    private static String repeat(int d, String s) {
        StringBuilder sb = new StringBuilder();
        while (d-- > 0) {
            sb.append(s);
        }
        return sb.toString();
    }

    private final XMLStreamWriter target;
    private int depth = 0;
    private final Map<Integer, Boolean> hasChildElement = new HashMap<>();
    private static final String INDENT = "  ";
    private static final String EOL = "\n";
}
