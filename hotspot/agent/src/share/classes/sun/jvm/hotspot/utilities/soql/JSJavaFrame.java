/*
 * Copyright (c) 2004, 2007, Oracle and/or its affiliates. All rights reserved.
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

package sun.jvm.hotspot.utilities.soql;

import java.util.*;
import sun.jvm.hotspot.debugger.*;
import sun.jvm.hotspot.oops.*;
import sun.jvm.hotspot.runtime.*;

public class JSJavaFrame extends DefaultScriptObject {
    private static final int FIELD_METHOD      = 0;
    private static final int FIELD_BCI         = 1;
    private static final int FIELD_LINE_NUMBER = 2;
    private static final int FIELD_LOCALS      = 3;
    private static final int FIELD_THIS_OBJECT = 4;
    private static final int FIELD_THREAD      = 5;
    private static final int FIELD_UNDEFINED   = -1;

    public JSJavaFrame(JavaVFrame jvf, JSJavaFactory fac) {
        this.jvf = jvf;
        this.factory = fac;
    }

    public Object get(String name) {
        int fieldID = getFieldID(name);
        switch (fieldID) {
        case FIELD_METHOD:
            return getMethod();
        case FIELD_BCI:
            return new Integer(getBCI());
        case FIELD_LINE_NUMBER:
            return new Integer(getLineNumber());
        case FIELD_LOCALS:
            return getLocals();
        case FIELD_THIS_OBJECT:
            return getThisObject();
        case FIELD_THREAD:
            return getThread();
        case FIELD_UNDEFINED:
        default:
            return super.get(name);
        }
    }

    public Object[] getIds() {
       Object[] fieldNames = fields.keySet().toArray();
       Object[] superFields = super.getIds();
       Object[] res = new Object[fieldNames.length + superFields.length];
       System.arraycopy(fieldNames, 0, res, 0, fieldNames.length);
       System.arraycopy(superFields, 0, res, fieldNames.length, superFields.length);
       return res;
   }

    public boolean has(String name) {
        if (getFieldID(name) != FIELD_UNDEFINED) {
            return true;
        } else {
            return super.has(name);
        }
    }

    public void put(String name, Object value) {
        if (getFieldID(name) == FIELD_UNDEFINED) {
            super.put(name, value);
        }
    }

    public String toString() {
        StringBuffer buf = new StringBuffer();
        buf.append("Frame (method=");
        buf.append(jvf.getMethod().externalNameAndSignature());
        buf.append(", bci=");
        buf.append(getBCI());
        buf.append(", line=");
        buf.append(getLineNumber());
        buf.append(')');
        return buf.toString();
    }

    //-- Internals only below this point
    private static Map fields = new HashMap();
    private static void addField(String name, int fieldId) {
        fields.put(name, new Integer(fieldId));
    }

    private static int getFieldID(String name) {
        Integer res = (Integer) fields.get(name);
        return (res != null)? res.intValue() : FIELD_UNDEFINED;
    }

    static {
        addField("method", FIELD_METHOD);
        addField("bci", FIELD_BCI);
        addField("line", FIELD_LINE_NUMBER);
        addField("locals", FIELD_LOCALS);
        addField("thisObject", FIELD_THIS_OBJECT);
        addField("thread", FIELD_THREAD);
    }

    private JSJavaObject getMethod() {
        return factory.newJSJavaObject(jvf.getMethod());
    }

    private int getBCI() {
        return jvf.getBCI();
    }

    private int getLineNumber() {
        int bci = jvf.getBCI();
        if (bci == -1) {
            return 0;
        } else {
            int lineNum = jvf.getMethod().getLineNumberFromBCI(bci);
            return (lineNum <= 0)? 0 : lineNum;
        }
    }

    private synchronized JSMap getLocals() {
        if (localsCache == null) {
            Map map = new HashMap();
            localsCache = factory.newJSMap(map);
            StackValueCollection values = jvf.getLocals();
            Method method = jvf.getMethod();
            if (method.isNative() || ! method.hasLocalVariableTable() ||
                values == null) {
                return localsCache;
            }

            LocalVariableTableElement[] localVars = method.getLocalVariableTable();
            int bci = getBCI();
            List visibleVars = new ArrayList(0);
            for (int i = 0; i < localVars.length; i++) {
                LocalVariableTableElement cur = localVars[i];
                if (cur.getStartBCI() >= bci && cur.getLength() > 0) {
                    visibleVars.add(cur);
                }
            }

            OopHandle handle = null;
            ObjectHeap heap = VM.getVM().getObjectHeap();
            for (Iterator varItr = visibleVars.iterator(); varItr.hasNext();) {
                LocalVariableTableElement cur = (LocalVariableTableElement) varItr.next();
                String name =  method.getConstants().getSymbolAt(cur.getNameCPIndex()).asString();
                int slot = cur.getSlot();

                String signature = method.getConstants().getSymbolAt(cur.getDescriptorCPIndex()).asString();
                BasicType variableType = BasicType.charToBasicType(signature.charAt(0));
                Object value = null;
                if (variableType == BasicType.T_BOOLEAN) {
                    value = Boolean.valueOf(values.booleanAt(slot));
                } else if (variableType == BasicType.T_CHAR) {
                    value = new Character(values.charAt(slot));
                } else if (variableType == BasicType.T_FLOAT) {
                    value = new Float(values.floatAt(slot));
                } else if (variableType == BasicType.T_DOUBLE) {
                    value = new Double(values.doubleAt(slot));
                } else if (variableType == BasicType.T_BYTE) {
                    value = new Byte(values.byteAt(slot));
                } else if (variableType == BasicType.T_SHORT) {
                    value = new Short(values.shortAt(slot));
                } else if (variableType == BasicType.T_INT) {
                    value = new Integer(values.intAt(slot));
                } else if (variableType == BasicType.T_LONG) {
                    value = new Long(values.longAt(slot));
                } else if (variableType == BasicType.T_OBJECT ||
                       variableType == BasicType.T_ARRAY) {
                    handle = values.oopHandleAt(slot);
                    value = factory.newJSJavaObject(heap.newOop(handle));
                } else {
                    // ignore
                }
                map.put(name, value);
            }
        }
        return localsCache;
    }

    private JSJavaObject getThisObject() {
        Method method = jvf.getMethod();
        if (method.isStatic()) {
            return null;
        }
        StackValueCollection values = jvf.getLocals();
        if (values != null) {
            // 'this' at index 0.
            OopHandle handle = values.oopHandleAt(0);
            ObjectHeap heap = VM.getVM().getObjectHeap();
            return factory.newJSJavaObject(heap.newOop(handle));
        } else {
            // can't get locals, return null.
            return null;
        }
    }

    private JSJavaThread getThread() {
        return factory.newJSJavaThread(jvf.getThread());
    }

    private final JavaVFrame jvf;
    private final JSJavaFactory factory;
    private JSMap localsCache;
}
