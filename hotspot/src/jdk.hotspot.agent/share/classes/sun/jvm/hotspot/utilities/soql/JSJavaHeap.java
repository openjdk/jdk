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
import javax.script.ScriptException;
import sun.jvm.hotspot.debugger.*;
import sun.jvm.hotspot.memory.*;
import sun.jvm.hotspot.oops.*;
import sun.jvm.hotspot.runtime.*;
import sun.jvm.hotspot.utilities.*;
import java.lang.reflect.Method;

public class JSJavaHeap extends DefaultScriptObject {
    private static final int FIELD_CAPACITY = 0;
    private static final int FIELD_USED = 1;
    private static final int FIELD_FOR_EACH_OBJECT = 2;
    private static final int FIELD_FOR_EACH_CLASS = 3;

    private static final int FIELD_UNDEFINED = -1;

    public JSJavaHeap(JSJavaFactory fac) {
        this.factory = fac;
    }

    public Object get(String name) {
        int fieldID = getFieldID(name);
        switch (fieldID) {
        case FIELD_CAPACITY:
            return new Long(getCapacity());
        case FIELD_USED:
            return new Long(getUsed());
        case FIELD_FOR_EACH_OBJECT:
            return new MethodCallable(this, forEachObjectMethod);
        case FIELD_FOR_EACH_CLASS:
            return new MethodCallable(this, forEachClassMethod);
        case FIELD_UNDEFINED:
        default:
            return super.get(name);
        }
    }

    public Object[] getIds() {
        Object[] superIds = super.getIds();
        Object[] tmp = fields.keySet().toArray();
        Object[] res = new Object[superIds.length + tmp.length];
        System.arraycopy(tmp, 0, res, 0, tmp.length);
        System.arraycopy(superIds, 0, res, tmp.length, superIds.length);
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

    public void forEachObject(Object[] args) {
        boolean subtypes = true;
        Klass kls = null;
        Callable func = null;
        switch (args.length) {
        case 3: {
            Object b = args[2];
            if (b != null && b instanceof Boolean) {
                subtypes = ((Boolean)b).booleanValue();
            }
        }
        case 2: {
            Object k = args[1];
            if (k == null) return;
            if (k instanceof JSJavaKlass) {
                kls = ((JSJavaKlass)k).getKlass();
            } else if (k instanceof String) {
                kls = SystemDictionaryHelper.findInstanceKlass((String)k);
                if (kls == null) return;
            }
        }
        case 1: {
            Object f = args[0];
            if (f != null && f instanceof Callable) {
                func = (Callable) f;
            } else {
                // unknown target - just return
                return ;
            }
        }
        break;

        default:
            return;
        }

        final Callable finalFunc = func;
      HeapVisitor visitor = new DefaultHeapVisitor() {
                public boolean doObj(Oop oop) {
                    JSJavaObject jo = factory.newJSJavaObject(oop);
                    if (jo != null) {
                  try {
                    finalFunc.call(new Object[] { jo });
                  } catch (ScriptException exp) {
                    throw new RuntimeException(exp);
                  }
                    }
                return false;
                }
            };
        ObjectHeap heap = VM.getVM().getObjectHeap();
        if (kls == null) {
            kls = SystemDictionaryHelper.findInstanceKlass("java.lang.Object");
        }
        heap.iterateObjectsOfKlass(visitor, kls, subtypes);
    }

    public void forEachClass(Object[] args) {
        boolean withLoader = false;
        Callable func = null;
        switch (args.length) {
        case 2: {
            Object b = args[1];
            if (b instanceof Boolean) {
                withLoader = ((Boolean)b).booleanValue();
            }
        }
        case 1: {
            Object f = args[0];
            if (f instanceof Callable) {
                func = (Callable) f;
            } else {
                return;
            }
        }
        break;
        default:
            return;
        }

      final Callable finalFunc = func;
        SystemDictionary sysDict = VM.getVM().getSystemDictionary();
        if (withLoader) {
            sysDict.classesDo(new SystemDictionary.ClassAndLoaderVisitor() {
                    public void visit(Klass kls, Oop loader) {
                        JSJavaKlass  jk = factory.newJSJavaKlass(kls);
                        if (jk == null) {
                            return;
                        }
                        JSJavaObject k = jk.getJSJavaClass();
                        JSJavaObject l = factory.newJSJavaObject(loader);
                        if (k != null) {
                         if (k != null) {
                       try {
                               finalFunc.call(new Object[] { k, l });
                       } catch (ScriptException exp) {
                         throw new RuntimeException(exp);
                       }
                           }
                        }
                    }
                });

        } else {
            sysDict.classesDo(new SystemDictionary.ClassVisitor() {
                    public void visit(Klass kls) {
                        JSJavaKlass jk = factory.newJSJavaKlass(kls);
                        if (jk == null) {
                            return;
                        }
                        JSJavaClass k = jk.getJSJavaClass();
                        if (k != null) {
                            if (k != null) {
                        try {
                                  finalFunc.call(new Object[] { k });
                        } catch (ScriptException exp) {
                          throw new RuntimeException(exp);
                        }
                            }
                        }
                    }
                });
        }
    }

    public String toString() {
        StringBuffer buf = new StringBuffer();
        buf.append("Java Heap (capacity=");
        buf.append(getCapacity());
        buf.append(", used=");
        buf.append(getUsed());
        buf.append(")");
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
        addField("capacity", FIELD_CAPACITY);
        addField("used", FIELD_USED);
        addField("forEachObject", FIELD_FOR_EACH_OBJECT);
        addField("forEachClass", FIELD_FOR_EACH_CLASS);
      try {
          Class myClass = JSJavaHeap.class;
          forEachObjectMethod = myClass.getMethod("forEachObject",
                                new Class[] { Object[].class });
          forEachClassMethod = myClass.getMethod("forEachClass",
                                new Class[] {Object[].class });
      } catch (RuntimeException re) {
          throw re;
      } catch (Exception exp) {
          throw new RuntimeException(exp);
      }
    }

    private long getCapacity() {
        return VM.getVM().getUniverse().heap().capacity();
    }

    private long getUsed() {
        return VM.getVM().getUniverse().heap().used();
    }

    private final JSJavaFactory factory;
    private static Method forEachObjectMethod;
    private static Method forEachClassMethod;
}
