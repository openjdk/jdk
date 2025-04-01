/*
 * Copyright (c) 2000, 2023, Oracle and/or its affiliates. All rights reserved.
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

package sun.jvm.hotspot.oops;

import java.io.*;

import sun.jvm.hotspot.code.CompressedReadStream;
import sun.jvm.hotspot.runtime.*;
import sun.jvm.hotspot.utilities.*;

// Super class for all fields in an object
public class Field {

  Field(FieldIdentifier id, long offset, boolean isVMField) {
    this.offset    = offset;
    this.id        = id;
    this.isVMField = isVMField;
  }

  /** Constructor for fields that are named in an InstanceKlass's
      fields array (i.e., named, non-VM fields) */
  private Field(InstanceKlass holder, int fieldIndex, FieldInfoValues values) {
    this.holder = holder;
    this.fieldIndex = fieldIndex;
    this.values = values;
    offset = values.offset;

    name = holder.getSymbolFromIndex(values.nameIndex, isInjected());
    signature = holder.getSymbolFromIndex(values.signatureIndex, isInjected());
    id          = new NamedFieldIdentifier(name.asString());
    fieldType   = new FieldType(signature);
    accessFlags = new AccessFlags(values.accessFlags);

    if (isGeneric()) {
      genericSignature = holder.getSymbolFromIndex(values.genericSignatureIndex, isInjected());
    }
  }

  /** Constructor for cloning an existing Field object */
  Field(InstanceKlass holder, int fieldIndex) {
      this(holder, fieldIndex, holder.getField(fieldIndex).values);
  }


  static class FieldInfoValues {
    int nameIndex;
    int signatureIndex;
    int offset;
    int accessFlags;
    int fieldFlags;
    int initialValueIndex;
    int genericSignatureIndex;
    int contendedGroup;
  }

  // The format of the stream, after decompression, is a series of
  // integers organized like this:
  //
  //   FieldInfoStream := j=num_java_fields k=num_injected_fields Field[j+k] End
  //   Field := name sig offset access flags Optionals(flags)
  //   Optionals(i) := initval?[i&is_init]     // ConstantValue attr
  //                   gsig?[i&is_generic]     // signature attr
  //                   group?[i&is_contended]  // Contended anno (group)
  //   End = 0
  //

  static FieldInfoValues readFieldInfoValues(CompressedReadStream crs) {
    FieldInfoValues fieldInfoValues = new FieldInfoValues();
    fieldInfoValues.nameIndex = crs.readInt();                 // read name_index
    fieldInfoValues.signatureIndex = crs.readInt();            // read signature index
    fieldInfoValues.offset = crs.readInt();                    // read offset
    fieldInfoValues.accessFlags = crs.readInt();               // read access flags
    fieldInfoValues.fieldFlags = crs.readInt();                // read field flags
                                                               // Optional reads:
    if (fieldIsInitialized(fieldInfoValues.fieldFlags)) {
        fieldInfoValues.initialValueIndex = crs.readInt();     // read initial value index
    }
    if (fieldIsGeneric(fieldInfoValues.fieldFlags)) {
        fieldInfoValues.genericSignatureIndex = crs.readInt(); // read generic signature index
    }
    if (fieldIsContended(fieldInfoValues.fieldFlags)) {
        fieldInfoValues.contendedGroup = crs.readInt();        // read contended group
    }
    return fieldInfoValues;
  }

  public static Field[] getFields(InstanceKlass kls) {
    CompressedReadStream crs = new CompressedReadStream(kls.getFieldInfoStream().getDataStart());
    int numJavaFields = crs.readInt();     // read num_java_fields
    int numInjectedFields = crs.readInt(); // read num_injected_fields;
    int numFields = numJavaFields + numInjectedFields;
    crs.skipBytes(numJavaFields > 16 ? 4 : 0);
    Field[] fields = new Field[numFields];
    for (int i = 0; i < numFields; i++) {
      FieldInfoValues values = readFieldInfoValues(crs);
      fields[i] = new Field(kls, i, values);
    }
    return fields;
  }

  FieldInfoValues         values;
  private Symbol          name;
  private long            offset;
  private FieldIdentifier id;
  private boolean         isVMField;
  // Java fields only
  private InstanceKlass   holder;
  private FieldType       fieldType;
  private Symbol          signature;
  private Symbol          genericSignature;
  private AccessFlags     accessFlags;
  private int             fieldIndex;

  /** Returns the byte offset of the field within the object or klass */
  public long getOffset() { return offset; }

  /** Returns the identifier of the field */
  public FieldIdentifier getID() { return id; }

  public Symbol getName() { return name; }
  public int getNameIndex() { return values.nameIndex; }

  /** Indicates whether this is a VM field */
  public boolean isVMField() { return isVMField; }

  /** Indicates whether this is a named field */
  public boolean isNamedField() { return (id instanceof NamedFieldIdentifier); }

  public void printOn(PrintStream tty) {
    getID().printOn(tty);
    tty.print(" {" + getOffset() + "} :");
  }

  /** (Named, non-VM fields only) Returns the InstanceKlass containing
      this (static or non-static) field. */
  public InstanceKlass getFieldHolder() {
    return holder;
  }

  /** (Named, non-VM fields only) Returns the index in the fields
      TypeArray for this field. Equivalent to the "index" in the VM's
      fieldDescriptors. */
  public int getFieldIndex() {
    return fieldIndex;
  }

  /** (Named, non-VM fields only) Retrieves the access flags. */
  public long getAccessFlags() { return accessFlags.getValue(); }
  public AccessFlags getAccessFlagsObj() { return accessFlags; }

  /** (Named, non-VM fields only) Returns the type of this field. */
  public FieldType getFieldType() { return fieldType; }

  /** (Named, non-VM fields only) Returns the signature of this
      field. */
  public Symbol getSignature() { return signature; }
  public int getSignatureIndex() { return values.signatureIndex; }
  public Symbol getGenericSignature() { return genericSignature; }
  public int getGenericSignatureIndex() { return values.genericSignatureIndex; }

  public boolean hasInitialValue()           { return holder.getFieldInitialValueIndex(fieldIndex) != 0;    }
  public int getInitialValueIndex()        { return values.initialValueIndex; }

  //
  // Following accessors are for named, non-VM fields only
  //
  public boolean isPublic()                  { return accessFlags.isPublic(); }
  public boolean isPrivate()                 { return accessFlags.isPrivate(); }
  public boolean isProtected()               { return accessFlags.isProtected(); }
  public boolean isPackagePrivate()          { return !isPublic() && !isPrivate() && !isProtected(); }

  public boolean isStatic()                  { return accessFlags.isStatic(); }
  public boolean isFinal()                   { return accessFlags.isFinal(); }
  public boolean isVolatile()                { return accessFlags.isVolatile(); }
  public boolean isTransient()               { return accessFlags.isTransient(); }

  public boolean isSynthetic()               { return accessFlags.isSynthetic(); }
  public boolean isEnumConstant()            { return accessFlags.isEnum();      }

  private static boolean fieldIsInitialized(int flags) { return ((flags >> InstanceKlass.FIELD_FLAG_IS_INITIALIZED) & 1 ) != 0; }
  private static boolean fieldIsInjected(int flags)    { return ((flags >> InstanceKlass.FIELD_FLAG_IS_INJECTED   ) & 1 ) != 0; }
  private static boolean fieldIsGeneric(int flags)     { return ((flags >> InstanceKlass.FIELD_FLAG_IS_GENERIC    ) & 1 ) != 0; }
  private static boolean fieldIsStable(int flags)      { return ((flags >> InstanceKlass.FIELD_FLAG_IS_STABLE     ) & 1 ) != 0; }
  private static boolean fieldIsContended(int flags)   { return ((flags >> InstanceKlass.FIELD_FLAG_IS_CONTENDED  ) & 1 ) != 0; }


  public boolean isInitialized()             { return fieldIsInitialized(values.fieldFlags); }
  public boolean isInjected()                { return fieldIsInjected(values.fieldFlags); }
  public boolean isGeneric()                 { return fieldIsGeneric(values.fieldFlags); }
  public boolean isStable()                  { return fieldIsStable(values.fieldFlags); }
  public boolean isContended()               { return fieldIsContended(values.fieldFlags); }

  public boolean equals(Object obj) {
     if (obj == null) {
        return false;
     }

     if (! (obj instanceof Field)) {
        return false;
     }

     Field other = (Field) obj;
     return this.getFieldHolder().equals(other.getFieldHolder()) &&
            this.getID().equals(other.getID());
  }

  public int hashCode() {
     return getFieldHolder().hashCode() ^ getID().hashCode();
  }
}
