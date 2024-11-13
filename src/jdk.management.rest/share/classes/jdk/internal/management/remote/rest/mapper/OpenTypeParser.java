/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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

package jdk.internal.management.remote.rest.mapper;

import jdk.internal.management.remote.rest.json.JSONArray;
import jdk.internal.management.remote.rest.json.JSONElement;
import jdk.internal.management.remote.rest.json.JSONObject;
import jdk.internal.management.remote.rest.json.JSONPrimitive;

import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;
import javax.management.openmbean.*;
import java.lang.reflect.Array;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 */
public final class OpenTypeParser {

    protected String s;
    protected int pos;
    protected int consumed;

    public OpenTypeParser(String s) {
        set(s);
    }

    private void set(String s) {
        this.s = s;
        pos = 0;
        consumed = 0;
    }

    private void reset(String s) {
        this.s = s;
    }

    protected String error(String msg) throws ParseException {
        throw new ParseException(msg + " at: '" + s + "'", 0);
    }

    protected String errorString(String msg) {
        return msg + " at: '" + s + "'";
    }

    protected boolean consume(String wanted) {
        if (s.startsWith(wanted)) {
            s = s.substring(wanted.length());
            consumed += wanted.length();
            return true;
        } else {
            return false;
        } 
    }

    protected String consumeTo(char c) throws ParseException {
        int pos = s.indexOf(c);
        if (pos < 0) {
            error("missing " + c); 
        }
        String result = s.substring(0, pos);
        consume(pos);
        return result;
    }

    protected boolean consume(int n) {
        if (s.length() < n) {
            return false;
        }
        s = s.substring(n);
        consumed += n;
        return true;
    }

    public String getRemainder() {
        return s;
    }

    public char peek() {
        return s.charAt(0);
    }

    public char getChar() {
        char c = s.charAt(0);
        consume(1);
        return c;
    }

    public OpenType<?> parse() throws ParseException {
        // ArrayType, CompositeType, SimpleType, TabularType
        OpenType<?> type = null;
        if (consume("javax.management.openmbean.SimpleType(name=")) {
            type = parseSimpleType();
        } else if (consume("javax.management.openmbean.TabularType(")) {
            type = parseTabularType();
        } else if (consume("javax.management.openmbean.CompositeType(")) {
            type = parseCompositeType();
        } else if (consume("javax.management.openmbean.ArrayType(")) {
            type = parseArrayType();
        } else {
//            System.err.println("OpenTypeParser: '" + s + "': unknown");
            //error("OpenTypeParser: '" + s + "': unknown");
        }
        if (consume(")")) {
            // ok.
        } 
        return type;
    }

    public OpenType<?> parseSimpleType() throws ParseException {
        // javax.management.openmbean.SimpleType(name=java.lang.String)
        // has been consumed including name= so e.g.
        // "java.lang.String)" is next.
        OpenType<?> type = null;

        // If we have a closing bracket ) read up to that.
        // But do not fail if missing, so this method can be used to parse
        // simply a SimpleType name.
        String n = null;
        int pos = s.indexOf(")");
        if (pos >= 0) {
            if (pos < 14) {
                error("parseSimpleType bad");
            } else {
                n = s.substring(0, pos);
            }
        } else {
            n = s;
        }

            // Map lookup better?
            switch (n) {
            // See OpenType's ALLOWED_CLASSNAMES
            case "java.lang.Boolean": {
                consume(17);
                type = SimpleType.BOOLEAN;
                break;
            }
            case "java.lang.BigDecimal": {
                consume(20);
                type = SimpleType.BIGDECIMAL;
                break;
            }
            case "java.lang.BigInteger": {
                consume(20);
                type = SimpleType.BIGINTEGER;
                break;
            }
            case "java.lang.Byte": {
                consume(14);
                type = SimpleType.BYTE;
                break;
            }
            case "java.lang.Character": {
                consume(19);
                type = SimpleType.CHARACTER;
                break;
            }
            case "java.lang.Double": {
                consume(16);
                type = SimpleType.DOUBLE;
                break;
            }
            case "java.lang.Date": {
                consume(14);
                type = SimpleType.DATE;
                break;
            }
            case "java.lang.Float": {
                consume(15);
                type = SimpleType.FLOAT;
                break;
            }
            case "java.lang.Integer": {
                consume(17);
                type = SimpleType.INTEGER;
                break;
            }
            case "java.lang.Long": {
                consume(14);
                type = SimpleType.LONG;
                break;
            }
            case "java.lang.Short": {
                consume(15);
                type = SimpleType.SHORT;
                break;
            }
            case "java.lang.String": {
                consume(16);
                type = SimpleType.STRING;
                break;
            }
            case "java.lang.Void": {
                consume(14);
                type = SimpleType.VOID;
                break;
            }
            case "javax.management.ObjectName": {
                consume(27);
                type = SimpleType.OBJECTNAME;
                break;
            }
            default: {
                System.err.println("parseSimpleType unknown: '" + s + "'");
            }
        }
        // or consume(pos) and remove all consume calls above
//        System.err.println(" XXXX read type = " + type);
        return type;
    }


    public OpenType<?> parseTabularType() throws ParseException {
        // "javax.management.openmbean.TabularType("
        if (!consume("name=")) {
            error("missing name=");
        }
        // Type name or actual type?
        String typeName = parseTypeName(); // leaves a , or )
        if (typeName == null) {
            error("missing type name");
        }
//        System.err.println("XXX parseTabularType: typeName = '" + typeName + "'");

        if (!consume(",")) {
            error("missing ,"); 
        } 
        if (!consume("rowType=")) {
            error("missing rowType=");
        }

        //OpenTypeParser parser = new OpenTypeParser(s);
        //OpenType<?> rowType = parser.parse();
        CompositeType rowType = (CompositeType) this.parse();
//        OpenType<?> rowType = this.parse();

        if (rowType == null) {
            error("cannot parse rowType");
        }

        if (!consume(",")) {
            error("missing ,");
        } 

        if (!consume("indexNames=(")) {
            error("missing indexNames=");
        }
        int pos = s.indexOf(")");
        if (pos < 0) {
            error("missing )");
        }
        String indexNames = s.substring(0, pos);
        consume(pos);
        // Build final type:
        try {
        TabularType type = new TabularType(typeName, "from JSON", rowType, indexNames.split(","));
//        System.err.println("CREATED: " + type);
        return type;
        } catch (OpenDataException ode) {
            ode.printStackTrace(System.err);
            return null;
        }
    }

    public OpenType<?> parseCompositeType() throws ParseException {
        if (!consume("name=")) {
            error("missing name=");
        }
        String typeName = parseTypeName(); // leaves a , or )
        if (typeName == null) {
            error("missing type name");
        }
        if (!consume(",")) {
            error("missing ,");
        } 
        if (!consume("items=(")) {
            error("missing items=(");
        }
        // (itemName=key,itemType=javax.management.openmbean.SimpleType(name=java.lang.String))
        // followed by , for more, 
        List<String> itemNames = new ArrayList<>();
        List<String> itemDescriptions = new ArrayList<>();
        List<OpenType<?>> itemTypes= new ArrayList<>();

        while (true) {
            if (!consume("(")) {
                error("missing ("); 
            } 
            if (!consume("itemName=")) {
                error("missing itemName=");
            }
            String itemName = consumeTo(',');
            if (itemName == null) {
                error("missing itemName");
            }
            if (!consume(",itemType=")) {
                error("missing ,itemType=");
            }

//            System.err.println("ZZZ parseCompositeType: will parse itemType at: " + getRemainder());
            OpenType<?> itemType = this.parse();
            if (itemType == null) {
                error("cannot parse itemType");
            }
//            System.err.println(itemNames.size() + ": itemType = " + itemType);
//            System.err.println("ZZZ remainder: " + getRemainder());

            if (!consume(")")) {
                error("missing )"); 
            } 
            itemNames.add(itemName);
            itemDescriptions.add("from JSON");
            itemTypes.add(itemType);

            if (consume(")")) {
                break;
            } else if (!consume(",")) {
                error("missing , ");
            } 
        }
        try {
        CompositeType type = new CompositeType(typeName, "from JSON",
            itemNames.toArray(new String[0]),
            itemDescriptions.toArray(new String[0]),
            itemTypes.toArray(new OpenType<?>[0])
            );
//        System.err.println("CREATED: " + type);
        return type;
        } catch (OpenDataException ode) {
            ode.printStackTrace(System.err);
            return null;
        }
    }

    public OpenType<?> parseArrayType() throws ParseException {
        if (!consume("name=")) {
            error("missing name=");
        }
        String typeName = parseTypeName(); // leaves a , or )
        if (typeName == null) {
            error("missing type name");
        }
        if (!consume(",")) {
            error("missing ,");
        }
        if (!consume("dimension=")) {
            error("missing dimension=");
        }
        String d = consumeTo(',');
        int dim = 1;
        try {
            dim = Integer.parseInt(d);
        } catch (Exception e) {
            error(e.getMessage());
        }
        consume(",");
        if (!consume("elementType=")) {
            error("missing elementType=");
        }
        OpenType<?> elementType = parse();
//        System.err.println("ZZZZZZZZZZZZZZZZZZZZZZZ parseArrayType: elementType = " + elementType);
        OpenType<?> type = null;
        if (!consume(",")) {
            error("missing ,");
        }
        if (!consume("primitiveArray=")) {
            error("missing primitiveArray=");
        }
        if (consume("false")) {
            try {
                type = new ArrayType<OpenType<?>>(dim, elementType);
    //        System.err.println("ZZZZZZZZZZZZZZZZZZZZZZZ parseArrayType: arrayType = " + type);
            } catch (OpenDataException ode) {
                ode.printStackTrace(System.err);
            }
        } else if (consume("true")) {

        } else {
            error("primitiveArray specification problem?");
        }

        // Caller parses )
        return type;
    }

    protected String parseTypeName() throws ParseException {
//        System.err.println("XXX parseTypeName '" + s + "'");
        // tabular:
        // name=java.util.Map<java.lang.String, java.lang.String>,
        // composite:
        // javax.management.openmbean.CompositeType(name=java.lang.management.MemoryUsage,
        if (consume("java.util.Map<java.lang.String, java.lang.String>")) {
            return "java.util.Map<java.lang.String, java.lang.String>";
        }
        // Simple case:
        if (!s.contains("<")) {
            return consumeTo(',');
        }
        // Parse to , but don't count commas inside < > for a Map type.
        StringBuilder name = new StringBuilder();
        int brackets = 0;
        while (s.length() > 0) {
            char c = peek();
            if (c == ',' && brackets == 0) {
                break;
            }
            c = getChar();
            if (c == '<') {
                brackets++;
            } else if (c == '>') {
                brackets--;
            }
            name.append(c);
        }
//        System.err.println("XXX typeName = '" + name.toString() + "'");
        return name.toString();
    }
}
