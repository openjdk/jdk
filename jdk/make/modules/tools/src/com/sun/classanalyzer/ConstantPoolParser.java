/*
 * Copyright (c) 2009, Oracle and/or its affiliates. All rights reserved.
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
package com.sun.classanalyzer;

import com.sun.tools.classfile.*;
import com.sun.tools.classfile.ConstantPool.*;
import static com.sun.tools.classfile.ConstantPool.*;

/**
 *
 * @author Mandy Chung
 */
public class ConstantPoolParser {

    private final ClassFileParser cfparser;
    private final StringValueVisitor visitor;
    private final ConstantPool cpool;

    ConstantPoolParser(ClassFileParser parser) {
        this.cfparser = parser;
        this.cpool = cfparser.classfile.constant_pool;
        this.visitor = new StringValueVisitor();
    }

    public String stringValue(CPInfo cpInfo) {
        return visitor.visit(cpInfo);
    }

    public String stringValue(int constant_pool_index) {
        try {
            return stringValue(cpool.get(constant_pool_index));
        } catch (ConstantPool.InvalidIndex e) {
            throw new RuntimeException(e);
        }
    }

    public void parseDependency() {
        ConstantPool.Visitor<Integer, Void> v = new ConstantPool.Visitor<Integer, Void>() {

            public Integer visitClass(CONSTANT_Class_info info, Void p) {
                try {
                    String classname = cfparser.checkClassName(info.getName());
                    if (classname.isEmpty()) {
                        return 1;
                    }

                    Klass from = cfparser.this_klass;
                    Klass to = Klass.getKlass(classname);
                    ResolutionInfo resInfo = ResolutionInfo.resolvedConstantPool(from, to, info.name_index);

                    from.addDep(to, resInfo);
                    to.addReferrer(from, resInfo);
                } catch (ConstantPoolException ex) {
                    throw new RuntimeException(ex);
                }
                return 1;
            }

            public Integer visitDouble(CONSTANT_Double_info info, Void p) {
                // skip
                return 2;
            }

            public Integer visitFieldref(CONSTANT_Fieldref_info info, Void p) {
                // skip
                return 1;
            }

            public Integer visitFloat(CONSTANT_Float_info info, Void p) {
                // skip
                return 1;
            }

            public Integer visitInteger(CONSTANT_Integer_info info, Void p) {
                // skip
                return 1;
            }

            public Integer visitInterfaceMethodref(CONSTANT_InterfaceMethodref_info info, Void p) {
                // skip
                return 1;
            }

            public Integer visitLong(CONSTANT_Long_info info, Void p) {
                // skip
                return 2;
            }

            public Integer visitNameAndType(CONSTANT_NameAndType_info info, Void p) {
                // skip
                return 1;
            }

            public Integer visitMethodref(CONSTANT_Methodref_info info, Void p) {
                // skip
                return 1;
            }

            public Integer visitString(CONSTANT_String_info info, Void p) {
                // skip
                return 1;
            }

            public Integer visitUtf8(CONSTANT_Utf8_info info, Void p) {
                // skip
                return 1;
            }
        };
        int cpx = 1;
        while (cpx < cpool.size()) {
            try {
                CPInfo cpInfo = cpool.get(cpx);
                cpx += cpInfo.accept(v, null);
            } catch (ConstantPool.InvalidIndex ex) {
                throw new RuntimeException(ex);
            }
        }
    }

    int getTag(int index) {
        try {
            return cpool.get(index).getTag();
        } catch (ConstantPoolException ex) {
            throw new RuntimeException(ex);
        }
    }

    String getDescriptor(int index) {
        CPInfo cpInfo;
        try {
            cpInfo = cpool.get(index);
        } catch (ConstantPoolException ex) {
            throw new RuntimeException(ex);
        }

        int tag = cpInfo.getTag();
        switch (tag) {
            case CONSTANT_Methodref:
            case CONSTANT_InterfaceMethodref:
            case CONSTANT_Fieldref:
                // simplify references within this class
                CPRefInfo ref = (CPRefInfo) cpInfo;
                try {
                    return ref.getNameAndTypeInfo().getType();
                } catch (ConstantPoolException ex) {
                }
        }
        return stringValue(cpInfo);
    }

    String getMethodName(int index) {
        try {
            CPInfo cpInfo = cpool.get(index);
            if (cpInfo.getTag() == CONSTANT_Methodref ||
                    cpInfo.getTag() == CONSTANT_InterfaceMethodref) {

                // simplify references within this class
                CPRefInfo ref = (CPRefInfo) cpInfo;
                String classname;
                if (ref.class_index == cfparser.classfile.this_class) {
                    classname = cfparser.this_klass.getClassName();
                } else {
                    classname = cfparser.checkClassName(ref.getClassName()).replace('/', '.');
                }
                String methodname = ref.getNameAndTypeInfo().getName();
                return classname + "." + methodname;
            } else {
                return null;
            }
        } catch (InvalidIndex ex) {
            throw new RuntimeException(ex);
        } catch (ConstantPoolException ex) {
            throw new RuntimeException(ex);
        }

    }

    class StringValueVisitor implements ConstantPool.Visitor<String, Void> {

        public StringValueVisitor() {
        }

        public String visit(CPInfo info) {
            return info.accept(this, null);
        }

        public String visitClass(CONSTANT_Class_info info, Void p) {
            return getCheckedName(info);
        }

        String getCheckedName(CONSTANT_Class_info info) {
            try {
                return checkName(info.getName());
            } catch (ConstantPoolException e) {
                throw new RuntimeException(e);
            }
        }

        public String visitDouble(CONSTANT_Double_info info, Void p) {
            return info.value + "d";
        }

        public String visitFieldref(CONSTANT_Fieldref_info info, Void p) {
            return visitRef(info, p);
        }

        public String visitFloat(CONSTANT_Float_info info, Void p) {
            return info.value + "f";
        }

        public String visitInteger(CONSTANT_Integer_info info, Void p) {
            return String.valueOf(info.value);
        }

        public String visitInterfaceMethodref(CONSTANT_InterfaceMethodref_info info, Void p) {
            return visitRef(info, p);
        }

        public String visitLong(CONSTANT_Long_info info, Void p) {
            return info.value + "l";
        }

        public String visitNameAndType(CONSTANT_NameAndType_info info, Void p) {
            return getCheckedName(info) + ":" + getType(info);
        }

        String getCheckedName(CONSTANT_NameAndType_info info) {
            try {
                return checkName(info.getName());
            } catch (ConstantPoolException e) {
                throw new RuntimeException(e);
            }
        }

        String getType(CONSTANT_NameAndType_info info) {
            try {
                return info.getType();
            } catch (ConstantPoolException e) {
                throw new RuntimeException(e);
            }
        }

        public String visitMethodref(CONSTANT_Methodref_info info, Void p) {
            return visitRef(info, p);
        }

        public String visitString(CONSTANT_String_info info, Void p) {
            try {
                int string_index = info.string_index;
                return cpool.getUTF8Info(string_index).accept(this, p);
            } catch (ConstantPoolException e) {
                throw new RuntimeException(e);
            }
        }

        public String visitUtf8(CONSTANT_Utf8_info info, Void p) {
            String s = info.value;
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < s.length(); i++) {
                char c = s.charAt(i);
                switch (c) {
                    case '\t':
                        sb.append('\\').append('t');
                        break;
                    case '\n':
                        sb.append('\\').append('n');
                        break;
                    case '\r':
                        sb.append('\\').append('r');
                        break;
                    case '\"':
                        sb.append('\\').append('\"');
                        break;
                    default:
                        sb.append(c);
                }
            }
            return sb.toString();
        }

        String visitRef(CPRefInfo info, Void p) {
            String cn = getCheckedClassName(info);
            String nat;
            try {
                nat = info.getNameAndTypeInfo().accept(this, p);
            } catch (ConstantPoolException e) {
                nat = e.getMessage();
            }
            return cn + "." + nat;
        }

        String getCheckedClassName(CPRefInfo info) {
            try {
                return checkName(info.getClassName());
            } catch (ConstantPoolException e) {
                throw new RuntimeException(e);
            }
        }
    }
    /* If name is a valid binary name, return it; otherwise quote it. */

    private static String checkName(String name) {
        if (name == null) {
            return "null";
        }

        int len = name.length();
        if (len == 0) {
            return "\"\"";
        }

        int cc = '/';
        int cp;
        for (int k = 0; k < len; k += Character.charCount(cp)) {
            cp = name.codePointAt(k);
            if ((cc == '/' && !Character.isJavaIdentifierStart(cp)) || (cp != '/' && !Character.isJavaIdentifierPart(cp))) {
                return "\"" + name + "\"";
            }
            cc = cp;
        }
        return name;
    }

    String tagName(int index) {
        try {
            int tag = cpool.get(index).getTag();
            switch (tag) {
                case CONSTANT_Utf8:
                    return "Utf8";
                case CONSTANT_Integer:
                    return "int";
                case CONSTANT_Float:
                    return "float";
                case CONSTANT_Long:
                    return "long";
                case CONSTANT_Double:
                    return "double";
                case CONSTANT_Class:
                    return "class";
                case CONSTANT_String:
                    return "String";
                case CONSTANT_Fieldref:
                    return "Field";
                case CONSTANT_Methodref:
                    return "Method";
                case CONSTANT_InterfaceMethodref:
                    return "InterfaceMethod";
                case CONSTANT_NameAndType:
                    return "NameAndType";
                default:
                    return "(unknown tag)";
            }
        } catch (InvalidIndex e) {
            throw new RuntimeException(e);
        }
    }
}
