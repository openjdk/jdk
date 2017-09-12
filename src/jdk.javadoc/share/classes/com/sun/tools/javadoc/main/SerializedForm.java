/*
 * Copyright (c) 1998, 2014, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.tools.javadoc.main;

import com.sun.javadoc.*;
import com.sun.tools.javac.code.*;
import com.sun.tools.javac.code.Symbol.ClassSymbol;
import com.sun.tools.javac.code.Symbol.MethodSymbol;
import com.sun.tools.javac.code.Symbol.VarSymbol;
import com.sun.tools.javac.util.*;

import static com.sun.tools.javac.code.Kinds.Kind.*;
import static com.sun.tools.javac.code.Scope.LookupKind.NON_RECURSIVE;

/**
 * The serialized form is the specification of a class' serialization
 * state. <p>
 *
 * It consists of the following information:<p>
 *
 * <pre>
 * 1. Whether class is Serializable or Externalizable.
 * 2. Javadoc for serialization methods.
 *    a. For Serializable, the optional readObject, writeObject,
 *       readResolve and writeReplace.
 *       serialData tag describes, in prose, the sequence and type
 *       of optional data written by writeObject.
 *    b. For Externalizable, writeExternal and readExternal.
 *       serialData tag describes, in prose, the sequence and type
 *       of optional data written by writeExternal.
 * 3. Javadoc for serialization data layout.
 *    a. For Serializable, the name,type and description
 *       of each Serializable fields.
 *    b. For Externalizable, data layout is described by 2(b).
 * </pre>
 *
 *  <p><b>This is NOT part of any supported API.
 *  If you write code that depends on this, you do so at your own risk.
 *  This code and its internal interfaces are subject to change or
 *  deletion without notice.</b>
 *
 * @since 1.2
 * @author Joe Fialli
 * @author Neal Gafter (rewrite but not too proud)
 */
@Deprecated
class SerializedForm {
    ListBuffer<MethodDoc> methods = new ListBuffer<>();

    /* List of FieldDocImpl - Serializable fields.
     * Singleton list if class defines Serializable fields explicitly.
     * Otherwise, list of default serializable fields.
     * 0 length list for Externalizable.
     */
    private final ListBuffer<FieldDocImpl> fields = new ListBuffer<>();

    /* True if class specifies serializable fields explicitly.
     * using special static member, serialPersistentFields.
     */
    private boolean definesSerializableFields = false;

    // Specially treated field/method names defined by Serialization.
    private static final String SERIALIZABLE_FIELDS = "serialPersistentFields";
    private static final String READOBJECT  = "readObject";
    private static final String WRITEOBJECT = "writeObject";
    private static final String READRESOLVE  = "readResolve";
    private static final String WRITEREPLACE = "writeReplace";
    private static final String READOBJECTNODATA = "readObjectNoData";

    /**
     * Constructor.
     *
     * Catalog Serializable fields for Serializable class.
     * Catalog serialization methods for Serializable and
     * Externalizable classes.
     */
    SerializedForm(DocEnv env, ClassSymbol def, ClassDocImpl cd) {
        if (cd.isExternalizable()) {
            /* look up required public accessible methods,
             *   writeExternal and readExternal.
             */
            String[] readExternalParamArr = { "java.io.ObjectInput" };
            String[] writeExternalParamArr = { "java.io.ObjectOutput" };
            MethodDoc md = cd.findMethod("readExternal", readExternalParamArr);
            if (md != null) {
                methods.append(md);
            }
            md = cd.findMethod("writeExternal", writeExternalParamArr);
            if (md != null) {
                methods.append(md);
                Tag tag[] = md.tags("serialData");
            }
        // } else { // isSerializable() //### ???
        } else if (cd.isSerializable()) {

            VarSymbol dsf = getDefinedSerializableFields(def);
            if (dsf != null) {

                /* Define serializable fields with array of ObjectStreamField.
                 * Each ObjectStreamField should be documented by a
                 * serialField tag.
                 */
                definesSerializableFields = true;
                //### No modifier filtering applied here.
                FieldDocImpl dsfDoc = env.getFieldDoc(dsf);
                fields.append(dsfDoc);
                mapSerialFieldTagImplsToFieldDocImpls(dsfDoc, env, def);
            } else {

                /* Calculate default Serializable fields as all
                 * non-transient, non-static fields.
                 * Fields should be documented by serial tag.
                 */
                computeDefaultSerializableFields(env, def, cd);
            }

           /* Check for optional customized readObject, writeObject,
            * readResolve and writeReplace, which can all contain
            * the serialData tag.        */
            addMethodIfExist(env, def, READOBJECT);
            addMethodIfExist(env, def, WRITEOBJECT);
            addMethodIfExist(env, def, READRESOLVE);
            addMethodIfExist(env, def, WRITEREPLACE);
            addMethodIfExist(env, def, READOBJECTNODATA);
        }
    }

    /*
     * Check for explicit Serializable fields.
     * Check for a private static array of ObjectStreamField with
     * name SERIALIZABLE_FIELDS.
     */
    private VarSymbol getDefinedSerializableFields(ClassSymbol def) {
        Names names = def.name.table.names;

        /* SERIALIZABLE_FIELDS can be private,
         * so must lookup by ClassSymbol, not by ClassDocImpl.
         */
        for (Symbol sym : def.members().getSymbolsByName(names.fromString(SERIALIZABLE_FIELDS))) {
            if (sym.kind == VAR) {
                VarSymbol f = (VarSymbol)sym;
                if ((f.flags() & Flags.STATIC) != 0 &&
                    (f.flags() & Flags.PRIVATE) != 0) {
                    return f;
                }
            }
        }
        return null;
    }

    /*
     * Compute default Serializable fields from all members of ClassSymbol.
     *
     * Since the fields of ClassDocImpl might not contain private or
     * package accessible fields, must walk over all members of ClassSymbol.
     */
    private void computeDefaultSerializableFields(DocEnv env,
                                                  ClassSymbol def,
                                                  ClassDocImpl cd) {
        for (Symbol sym : def.members().getSymbols(NON_RECURSIVE)) {
            if (sym != null && sym.kind == VAR) {
                VarSymbol f = (VarSymbol)sym;
                if ((f.flags() & Flags.STATIC) == 0 &&
                    (f.flags() & Flags.TRANSIENT) == 0) {
                    //### No modifier filtering applied here.
                    FieldDocImpl fd = env.getFieldDoc(f);
                    //### Add to beginning.
                    //### Preserve order used by old 'javadoc'.
                    fields.prepend(fd);
                }
            }
        }
    }

    /*
     * Catalog Serializable method if it exists in current ClassSymbol.
     * Do not look for method in superclasses.
     *
     * Serialization requires these methods to be non-static.
     *
     * @param method should be an unqualified Serializable method
     *               name either READOBJECT, WRITEOBJECT, READRESOLVE
     *               or WRITEREPLACE.
     * @param visibility the visibility flag for the given method.
     */
    private void addMethodIfExist(DocEnv env, ClassSymbol def, String methodName) {
        Names names = def.name.table.names;

        for (Symbol sym : def.members().getSymbolsByName(names.fromString(methodName))) {
            if (sym.kind == MTH) {
                MethodSymbol md = (MethodSymbol)sym;
                if ((md.flags() & Flags.STATIC) == 0) {
                    /*
                     * WARNING: not robust if unqualifiedMethodName is overloaded
                     *          method. Signature checking could make more robust.
                     * READOBJECT takes a single parameter, java.io.ObjectInputStream.
                     * WRITEOBJECT takes a single parameter, java.io.ObjectOutputStream.
                     */
                    methods.append(env.getMethodDoc(md));
                }
            }
        }
    }

    /*
     * Associate serialField tag fieldName with FieldDocImpl member.
     * Note: A serialField tag does not have to map an existing field
     *       of a class.
     */
    private void mapSerialFieldTagImplsToFieldDocImpls(FieldDocImpl spfDoc,
                                                       DocEnv env,
                                                       ClassSymbol def) {
        Names names = def.name.table.names;
        for (SerialFieldTag tag : spfDoc.serialFieldTags()) {
            if (tag.fieldName() == null || tag.fieldType() == null) // ignore malformed @serialField tags
                continue;

            Name fieldName = names.fromString(tag.fieldName());

            // Look for a FieldDocImpl that is documented by serialFieldTagImpl.
            for (Symbol sym : def.members().getSymbolsByName(fieldName)) {
                if (sym.kind == VAR) {
                    VarSymbol f = (VarSymbol) sym;
                    FieldDocImpl fdi = env.getFieldDoc(f);
                    ((SerialFieldTagImpl) (tag)).mapToFieldDocImpl(fdi);
                    break;
                }
            }
        }
    }

    /**
     * Return serializable fields in class. <p>
     *
     * Returns either a list of default fields documented by serial tag comment or
     *         javadoc comment<p>
     * Or Returns a single FieldDocImpl for serialPersistentField. There is a
     *         serialField tag for each serializable field.<p>
     *
     * @return an array of FieldDocImpl for representing the visible
     *         fields in this class.
     */
    FieldDoc[] fields() {
        return (FieldDoc[])fields.toArray(new FieldDocImpl[fields.length()]);
    }

    /**
     * Return serialization methods in class.
     *
     * @return an array of MethodDocImpl for serialization methods in this class.
     */
    MethodDoc[] methods() {
        return methods.toArray(new MethodDoc[methods.length()]);
    }

    /**
     * Returns true if Serializable fields are defined explicitly using
     * member, serialPersistentFields.
     *
     * @see #fields()
     */
    boolean definesSerializableFields() {
        return definesSerializableFields;
    }
}
