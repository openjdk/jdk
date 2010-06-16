/*
 * Copyright (c) 2002, 2003, Oracle and/or its affiliates. All rights reserved.
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
 *
 */

package sun.jvm.hotspot.jdi;

import java.util.List;
import java.util.ArrayList;

public class JNITypeParser {

    static final char SIGNATURE_ENDCLASS = ';';
    static final char SIGNATURE_FUNC = '(';
    static final char SIGNATURE_ENDFUNC = ')';

    private String signature;
    private List typeNameList;
    private List signatureList;
    private int currentIndex;

    JNITypeParser(String signature) {
        this.signature = signature;
    }

    static String typeNameToSignature(String signature) {
        StringBuffer buffer = new StringBuffer();
        int firstIndex = signature.indexOf('[');
        int index = firstIndex;
        while (index != -1) {
            buffer.append('[');
            index = signature.indexOf('[', index + 1);
        }

        if (firstIndex != -1) {
            signature = signature.substring(0, firstIndex);
        }

        if (signature.equals("boolean")) {
            buffer.append('Z');
        } else if (signature.equals("byte")) {
            buffer.append('B');
        } else if (signature.equals("char")) {
            buffer.append('C');
        } else if (signature.equals("short")) {
            buffer.append('S');
        } else if (signature.equals("int")) {
            buffer.append('I');
        } else if (signature.equals("long")) {
            buffer.append('J');
        } else if (signature.equals("float")) {
            buffer.append('F');
        } else if (signature.equals("double")) {
            buffer.append('D');
        } else {
            buffer.append('L');
            buffer.append(signature.replace('.', '/'));
            buffer.append(';');
        }

        return buffer.toString();
    }

    String typeName() {
        return (String)typeNameList().get(typeNameList().size()-1);
    }

    List argumentTypeNames() {
        return typeNameList().subList(0, typeNameList().size() - 1);
    }

    String signature() {
        return (String)signatureList().get(signatureList().size()-1);
    }

    List argumentSignatures() {
        return signatureList().subList(0, signatureList().size() - 1);
    }

    int dimensionCount() {
        int count = 0;
        String signature = signature();
        while (signature.charAt(count) == '[') {
            count++;
        }
        return count;
    }

    String componentSignature(int level) {
        return signature().substring(level);
    }

    private synchronized List signatureList() {
        if (signatureList == null) {
            signatureList = new ArrayList(10);
            String elem;

            currentIndex = 0;

            while(currentIndex < signature.length()) {
                elem = nextSignature();
                signatureList.add(elem);
            }
            if (signatureList.size() == 0) {
                throw new IllegalArgumentException("Invalid JNI signature '" +
                                                   signature + "'");
            }
        }
        return signatureList;
    }

    private synchronized List typeNameList() {
        if (typeNameList == null) {
            typeNameList = new ArrayList(10);
            String elem;

            currentIndex = 0;

            while(currentIndex < signature.length()) {
                elem = nextTypeName();
                typeNameList.add(elem);
            }
            if (typeNameList.size() == 0) {
                throw new IllegalArgumentException("Invalid JNI signature '" +
                                                   signature + "'");
            }
        }
        return typeNameList;
    }

    private String nextSignature() {
        char key = signature.charAt(currentIndex++);

        switch(key) {
            case '[':
                return  key + nextSignature();

            case 'L':
                int endClass = signature.indexOf(SIGNATURE_ENDCLASS,
                                                 currentIndex);
                String retVal = signature.substring(currentIndex - 1,
                                                    endClass + 1);
                currentIndex = endClass + 1;
                return retVal;

            case 'V':
            case 'Z':
            case 'B':
            case 'C':
            case 'S':
            case 'I':
            case 'J':
            case 'F':
            case 'D':
                return String.valueOf(key);

            case SIGNATURE_FUNC:
            case SIGNATURE_ENDFUNC:
                return nextSignature();

            default:
                throw new IllegalArgumentException(
                    "Invalid JNI signature character '" + key + "'");

        }
    }

    private String nextTypeName() {
        char key = signature.charAt(currentIndex++);

        switch(key) {
            case '[':
                return  nextTypeName() + "[]";

            case 'B':
                return "byte";

            case 'C':
                return "char";

            case 'L':
                int endClass = signature.indexOf(SIGNATURE_ENDCLASS,
                                                 currentIndex);
                String retVal = signature.substring(currentIndex,
                                                    endClass);
                retVal = retVal.replace('/','.');
                currentIndex = endClass + 1;
                return retVal;

            case 'F':
                return "float";

            case 'D':
                return "double";

            case 'I':
                return "int";

            case 'J':
                return "long";

            case 'S':
                return "short";

            case 'V':
                return "void";

            case 'Z':
                return "boolean";

            case SIGNATURE_ENDFUNC:
            case SIGNATURE_FUNC:
                return nextTypeName();

            default:
                throw new IllegalArgumentException(
                    "Invalid JNI signature character '" + key + "'");

        }
    }
}
