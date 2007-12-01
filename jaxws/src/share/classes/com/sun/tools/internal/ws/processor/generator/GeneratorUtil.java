/*
 * Portions Copyright 2006 Sun Microsystems, Inc.  All Rights Reserved.
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
 */

package com.sun.tools.internal.ws.processor.generator;

import java.io.IOException;
import java.util.Comparator;

import javax.xml.namespace.QName;

import com.sun.tools.internal.ws.processor.model.Block;
import com.sun.tools.internal.ws.processor.model.Fault;
import com.sun.tools.internal.ws.processor.model.Operation;
import com.sun.tools.internal.ws.processor.model.java.JavaStructureType;
import com.sun.tools.internal.ws.processor.util.IndentingWriter;
import com.sun.tools.internal.ws.processor.util.ProcessorEnvironment;


/**
 *
 * @author WS Development Team
 */
public class GeneratorUtil implements GeneratorConstants {

    public static void writeNewQName(IndentingWriter p, QName name)
        throws IOException {
        p.p(
            "new QName(\""
                + name.getNamespaceURI()
                + "\", \""
                + name.getLocalPart()
                + "\")");
    }


    public static void writeBlockQNameDeclaration(
        IndentingWriter p,
        Operation operation,
        Block block,
        Names names)
        throws IOException {
        String qname = names.getBlockQNameName(operation, block);
        p.p("private static final QName ");
        p.p(qname + " = ");
        writeNewQName(p, block.getName());
        p.pln(";");
    }

    public static void writeQNameDeclaration(
        IndentingWriter p,
        QName name,
        Names names)
        throws IOException {
        String qname = names.getQNameName(name);
        p.p("private static final QName ");
        p.p(qname + " = ");
        writeNewQName(p, name);
        p.pln(";");
    }

    public static void writeQNameTypeDeclaration(
        IndentingWriter p,
        QName name,
        Names names)
        throws IOException {
        String qname = names.getTypeQName(name);
        p.p("private static final QName ");
        p.p(qname + " = ");
        writeNewQName(p, name);
        p.pln(";");
    }

    public static boolean classExists(
        ProcessorEnvironment env,
        String className) {
        try {
            // Takes care of inner classes.
            getLoadableClassName(className, env.getClassLoader());
            return true;
        } catch(ClassNotFoundException ce) {
        }
        return false;
    }

    public static String getLoadableClassName(
        String className,
        ClassLoader classLoader)
        throws ClassNotFoundException {

        try {
            Class.forName(className, true, classLoader);
        } catch (ClassNotFoundException e) {
            int idx = className.lastIndexOf(DOTC);
            if (idx > -1) {
                String tmp = className.substring(0, idx) + SIG_INNERCLASS;
                tmp += className.substring(idx + 1);
                return getLoadableClassName(tmp, classLoader);
            }
            throw e;
        }
        return className;
    }

    public static class FaultComparator implements Comparator {
        private boolean sortName = false;
        public FaultComparator() {
        }
        public FaultComparator(boolean sortName) {
            this.sortName = sortName;
        }

        public int compare(Object o1, Object o2) {
            if (sortName) {
                QName name1 = ((Fault) o1).getBlock().getName();
                QName name2 = ((Fault) o2).getBlock().getName();
                // Faults that are processed by name first, then type
                if (!name1.equals(name2)) {
                    return name1.toString().compareTo(name2.toString());
                }
            }
            JavaStructureType type1 = ((Fault) o1).getJavaException();
            JavaStructureType type2 = ((Fault) o2).getJavaException();
            int result = sort(type1, type2);
            return result;
        }

        protected int sort(JavaStructureType type1, JavaStructureType type2) {
            if (type1.getName().equals(type2.getName())) {
                return 0;
            }
            JavaStructureType superType;
            superType = type1.getSuperclass();
            while (superType != null) {
                if (superType.equals(type2)) {
                    return -1;
                }
                superType = superType.getSuperclass();
            }
            superType = type2.getSuperclass();
            while (superType != null) {
                if (superType.equals(type1)) {
                    return 1;
                }
                superType = superType.getSuperclass();
            }
            if (type1.getSubclasses() == null && type2.getSubclasses() != null)
                return -1;
            if (type1.getSubclasses() != null && type2.getSubclasses() == null)
                return 1;
            if (type1.getSuperclass() != null
                && type2.getSuperclass() == null) {
                return 1;
            }
            if (type1.getSuperclass() == null
                && type2.getSuperclass() != null) {
                return -1;
            }
            return type1.getName().compareTo(type2.getName());
        }
    }
}
