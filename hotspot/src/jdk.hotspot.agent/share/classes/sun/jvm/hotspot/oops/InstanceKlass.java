/*
 * Copyright (c) 2000, 2016, Oracle and/or its affiliates. All rights reserved.
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
import java.util.*;
import sun.jvm.hotspot.classfile.ClassLoaderData;
import sun.jvm.hotspot.debugger.*;
import sun.jvm.hotspot.memory.*;
import sun.jvm.hotspot.runtime.*;
import sun.jvm.hotspot.types.*;
import sun.jvm.hotspot.utilities.*;

// An InstanceKlass is the VM level representation of a Java class.

public class InstanceKlass extends Klass {
  static {
    VM.registerVMInitializedObserver(new Observer() {
        public void update(Observable o, Object data) {
          initialize(VM.getVM().getTypeDataBase());
        }
      });
  }

  // field offset constants
  private static int ACCESS_FLAGS_OFFSET;
  private static int NAME_INDEX_OFFSET;
  private static int SIGNATURE_INDEX_OFFSET;
  private static int INITVAL_INDEX_OFFSET;
  private static int LOW_OFFSET;
  private static int HIGH_OFFSET;
  private static int FIELD_SLOTS;
  private static short FIELDINFO_TAG_SIZE;
  private static short FIELDINFO_TAG_MASK;
  private static short FIELDINFO_TAG_OFFSET;

  // ClassState constants
  private static int CLASS_STATE_ALLOCATED;
  private static int CLASS_STATE_LOADED;
  private static int CLASS_STATE_LINKED;
  private static int CLASS_STATE_BEING_INITIALIZED;
  private static int CLASS_STATE_FULLY_INITIALIZED;
  private static int CLASS_STATE_INITIALIZATION_ERROR;

  private static synchronized void initialize(TypeDataBase db) throws WrongTypeException {
    Type type            = db.lookupType("InstanceKlass");
    arrayKlasses         = new MetadataField(type.getAddressField("_array_klasses"), 0);
    methods              = type.getAddressField("_methods");
    methodOrdering       = type.getAddressField("_method_ordering");
    localInterfaces      = type.getAddressField("_local_interfaces");
    transitiveInterfaces = type.getAddressField("_transitive_interfaces");
    fields               = type.getAddressField("_fields");
    javaFieldsCount      = new CIntField(type.getCIntegerField("_java_fields_count"), 0);
    constants            = new MetadataField(type.getAddressField("_constants"), 0);
    classLoaderData      = type.getAddressField("_class_loader_data");
    sourceDebugExtension = type.getAddressField("_source_debug_extension");
    innerClasses         = type.getAddressField("_inner_classes");
    sourceFileNameIndex  = new CIntField(type.getCIntegerField("_source_file_name_index"), 0);
    nonstaticFieldSize   = new CIntField(type.getCIntegerField("_nonstatic_field_size"), 0);
    staticFieldSize      = new CIntField(type.getCIntegerField("_static_field_size"), 0);
    staticOopFieldCount  = new CIntField(type.getCIntegerField("_static_oop_field_count"), 0);
    nonstaticOopMapSize  = new CIntField(type.getCIntegerField("_nonstatic_oop_map_size"), 0);
    isMarkedDependent    = new CIntField(type.getCIntegerField("_is_marked_dependent"), 0);
    initState            = new CIntField(type.getCIntegerField("_init_state"), 0);
    vtableLen            = new CIntField(type.getCIntegerField("_vtable_len"), 0);
    itableLen            = new CIntField(type.getCIntegerField("_itable_len"), 0);
    breakpoints          = type.getAddressField("_breakpoints");
    genericSignatureIndex = new CIntField(type.getCIntegerField("_generic_signature_index"), 0);
    majorVersion         = new CIntField(type.getCIntegerField("_major_version"), 0);
    minorVersion         = new CIntField(type.getCIntegerField("_minor_version"), 0);
    headerSize           = type.getSize();

    // read field offset constants
    ACCESS_FLAGS_OFFSET            = db.lookupIntConstant("FieldInfo::access_flags_offset").intValue();
    NAME_INDEX_OFFSET              = db.lookupIntConstant("FieldInfo::name_index_offset").intValue();
    SIGNATURE_INDEX_OFFSET         = db.lookupIntConstant("FieldInfo::signature_index_offset").intValue();
    INITVAL_INDEX_OFFSET           = db.lookupIntConstant("FieldInfo::initval_index_offset").intValue();
    LOW_OFFSET                     = db.lookupIntConstant("FieldInfo::low_packed_offset").intValue();
    HIGH_OFFSET                    = db.lookupIntConstant("FieldInfo::high_packed_offset").intValue();
    FIELD_SLOTS                    = db.lookupIntConstant("FieldInfo::field_slots").intValue();
    FIELDINFO_TAG_SIZE             = db.lookupIntConstant("FIELDINFO_TAG_SIZE").shortValue();
    FIELDINFO_TAG_MASK             = db.lookupIntConstant("FIELDINFO_TAG_MASK").shortValue();
    FIELDINFO_TAG_OFFSET           = db.lookupIntConstant("FIELDINFO_TAG_OFFSET").shortValue();

    // read ClassState constants
    CLASS_STATE_ALLOCATED = db.lookupIntConstant("InstanceKlass::allocated").intValue();
    CLASS_STATE_LOADED = db.lookupIntConstant("InstanceKlass::loaded").intValue();
    CLASS_STATE_LINKED = db.lookupIntConstant("InstanceKlass::linked").intValue();
    CLASS_STATE_BEING_INITIALIZED = db.lookupIntConstant("InstanceKlass::being_initialized").intValue();
    CLASS_STATE_FULLY_INITIALIZED = db.lookupIntConstant("InstanceKlass::fully_initialized").intValue();
    CLASS_STATE_INITIALIZATION_ERROR = db.lookupIntConstant("InstanceKlass::initialization_error").intValue();

  }

  public InstanceKlass(Address addr) {
    super(addr);
    if (getJavaFieldsCount() != getAllFieldsCount()) {
      // Exercise the injected field logic
      for (int i = getJavaFieldsCount(); i < getAllFieldsCount(); i++) {
        getFieldName(i);
        getFieldSignature(i);
      }
    }
  }

  private static MetadataField arrayKlasses;
  private static AddressField  methods;
  private static AddressField  methodOrdering;
  private static AddressField  localInterfaces;
  private static AddressField  transitiveInterfaces;
  private static AddressField fields;
  private static CIntField javaFieldsCount;
  private static MetadataField constants;
  private static AddressField  classLoaderData;
  private static AddressField  sourceDebugExtension;
  private static AddressField  innerClasses;
  private static CIntField sourceFileNameIndex;
  private static CIntField nonstaticFieldSize;
  private static CIntField staticFieldSize;
  private static CIntField staticOopFieldCount;
  private static CIntField nonstaticOopMapSize;
  private static CIntField isMarkedDependent;
  private static CIntField initState;
  private static CIntField vtableLen;
  private static CIntField itableLen;
  private static AddressField breakpoints;
  private static CIntField genericSignatureIndex;
  private static CIntField majorVersion;
  private static CIntField minorVersion;

  // type safe enum for ClassState from instanceKlass.hpp
  public static class ClassState {
     public static final ClassState ALLOCATED    = new ClassState("allocated");
     public static final ClassState LOADED       = new ClassState("loaded");
     public static final ClassState LINKED       = new ClassState("linked");
     public static final ClassState BEING_INITIALIZED      = new ClassState("beingInitialized");
     public static final ClassState FULLY_INITIALIZED    = new ClassState("fullyInitialized");
     public static final ClassState INITIALIZATION_ERROR = new ClassState("initializationError");

     private ClassState(String value) {
        this.value = value;
     }

     public String toString() {
        return value;
     }

     private String value;
  }

  public int  getInitStateAsInt() { return (int) initState.getValue(this); }
  public ClassState getInitState() {
     int state = getInitStateAsInt();
     if (state == CLASS_STATE_ALLOCATED) {
        return ClassState.ALLOCATED;
     } else if (state == CLASS_STATE_LOADED) {
        return ClassState.LOADED;
     } else if (state == CLASS_STATE_LINKED) {
        return ClassState.LINKED;
     } else if (state == CLASS_STATE_BEING_INITIALIZED) {
        return ClassState.BEING_INITIALIZED;
     } else if (state == CLASS_STATE_FULLY_INITIALIZED) {
        return ClassState.FULLY_INITIALIZED;
     } else if (state == CLASS_STATE_INITIALIZATION_ERROR) {
        return ClassState.INITIALIZATION_ERROR;
     } else {
        throw new RuntimeException("should not reach here");
     }
  }

  // initialization state quaries
  public boolean isLoaded() {
     return getInitStateAsInt() >= CLASS_STATE_LOADED;
  }

  public boolean isLinked() {
     return getInitStateAsInt() >= CLASS_STATE_LINKED;
  }

  public boolean isInitialized() {
     return getInitStateAsInt() == CLASS_STATE_FULLY_INITIALIZED;
  }

  public boolean isNotInitialized() {
     return getInitStateAsInt() < CLASS_STATE_BEING_INITIALIZED;
  }

  public boolean isBeingInitialized() {
     return getInitStateAsInt() == CLASS_STATE_BEING_INITIALIZED;
  }

  public boolean isInErrorState() {
     return getInitStateAsInt() == CLASS_STATE_INITIALIZATION_ERROR;
  }

  public int getClassStatus() {
     int result = 0;
     if (isLinked()) {
        result |= JVMDIClassStatus.VERIFIED | JVMDIClassStatus.PREPARED;
     }

     if (isInitialized()) {
        if (Assert.ASSERTS_ENABLED) {
           Assert.that(isLinked(), "Class status is not consistent");
        }
        result |= JVMDIClassStatus.INITIALIZED;
     }

     if (isInErrorState()) {
        result |= JVMDIClassStatus.ERROR;
     }
     return result;
  }

  // Byteside of the header
  private static long headerSize;

  public long getObjectSize(Oop object) {
    return getSizeHelper() * VM.getVM().getAddressSize();
  }

  public long getSize() {
    return Oop.alignObjectSize(getHeaderSize() + getVtableLen() +
                               getItableLen() + getNonstaticOopMapSize());
  }

  public static long getHeaderSize() { return headerSize; }

  public short getFieldAccessFlags(int index) {
    return getFields().at(index * FIELD_SLOTS + ACCESS_FLAGS_OFFSET);
  }

  public short getFieldNameIndex(int index) {
    if (index >= getJavaFieldsCount()) throw new IndexOutOfBoundsException("not a Java field;");
    return getFields().at(index * FIELD_SLOTS + NAME_INDEX_OFFSET);
  }

  public Symbol getFieldName(int index) {
    int nameIndex = getFields().at(index * FIELD_SLOTS + NAME_INDEX_OFFSET);
    if (index < getJavaFieldsCount()) {
      return getConstants().getSymbolAt(nameIndex);
    } else {
      return vmSymbols.symbolAt(nameIndex);
    }
  }

  public short getFieldSignatureIndex(int index) {
    if (index >= getJavaFieldsCount()) throw new IndexOutOfBoundsException("not a Java field;");
    return getFields().at(index * FIELD_SLOTS + SIGNATURE_INDEX_OFFSET);
  }

  public Symbol getFieldSignature(int index) {
    int signatureIndex = getFields().at(index * FIELD_SLOTS + SIGNATURE_INDEX_OFFSET);
    if (index < getJavaFieldsCount()) {
      return getConstants().getSymbolAt(signatureIndex);
    } else {
      return vmSymbols.symbolAt(signatureIndex);
    }
  }

  public short getFieldGenericSignatureIndex(int index) {
    // int len = getFields().length();
    int allFieldsCount = getAllFieldsCount();
    int generic_signature_slot = allFieldsCount * FIELD_SLOTS;
    for (int i = 0; i < allFieldsCount; i++) {
      short flags = getFieldAccessFlags(i);
      AccessFlags access = new AccessFlags(flags);
      if (i == index) {
        if (access.fieldHasGenericSignature()) {
           return getFields().at(generic_signature_slot);
        } else {
          return 0;
        }
      } else {
        if (access.fieldHasGenericSignature()) {
          generic_signature_slot ++;
        }
      }
    }
    return 0;
  }

  public Symbol getFieldGenericSignature(int index) {
    short genericSignatureIndex = getFieldGenericSignatureIndex(index);
    if (genericSignatureIndex != 0)  {
      return getConstants().getSymbolAt(genericSignatureIndex);
    }
    return null;
  }

  public short getFieldInitialValueIndex(int index) {
    if (index >= getJavaFieldsCount()) throw new IndexOutOfBoundsException("not a Java field;");
    return getFields().at(index * FIELD_SLOTS + INITVAL_INDEX_OFFSET);
  }

  public int getFieldOffset(int index) {
    U2Array fields = getFields();
    short lo = fields.at(index * FIELD_SLOTS + LOW_OFFSET);
    short hi = fields.at(index * FIELD_SLOTS + HIGH_OFFSET);
    if ((lo & FIELDINFO_TAG_MASK) == FIELDINFO_TAG_OFFSET) {
      return VM.getVM().buildIntFromShorts(lo, hi) >> FIELDINFO_TAG_SIZE;
    }
    throw new RuntimeException("should not reach here");
  }

  // Accessors for declared fields
  public Klass     getArrayKlasses()        { return (Klass)        arrayKlasses.getValue(this); }
  public MethodArray  getMethods()              { return new MethodArray(methods.getValue(getAddress())); }
  public KlassArray   getLocalInterfaces()      { return new KlassArray(localInterfaces.getValue(getAddress())); }
  public KlassArray   getTransitiveInterfaces() { return new KlassArray(transitiveInterfaces.getValue(getAddress())); }
  public int       getJavaFieldsCount()     { return                (int) javaFieldsCount.getValue(this); }
  public int       getAllFieldsCount()      {
    int len = getFields().length();
    int allFieldsCount = 0;
    for (; allFieldsCount*FIELD_SLOTS < len; allFieldsCount++) {
      short flags = getFieldAccessFlags(allFieldsCount);
      AccessFlags access = new AccessFlags(flags);
      if (access.fieldHasGenericSignature()) {
        len --;
      }
    }
    return allFieldsCount;
  }
  public ConstantPool getConstants()        { return (ConstantPool) constants.getValue(this); }
  public ClassLoaderData getClassLoaderData() { return                ClassLoaderData.instantiateWrapperFor(classLoaderData.getValue(getAddress())); }
  public Oop       getClassLoader()         { return                getClassLoaderData().getClassLoader(); }
  public Symbol    getSourceFileName()      { return                getConstants().getSymbolAt(sourceFileNameIndex.getValue(this)); }
  public String    getSourceDebugExtension(){ return                CStringUtilities.getString(sourceDebugExtension.getValue(getAddress())); }
  public long      getNonstaticFieldSize()  { return                nonstaticFieldSize.getValue(this); }
  public long      getStaticOopFieldCount() { return                staticOopFieldCount.getValue(this); }
  public long      getNonstaticOopMapSize() { return                nonstaticOopMapSize.getValue(this); }
  public boolean   getIsMarkedDependent()   { return                isMarkedDependent.getValue(this) != 0; }
  public long      getVtableLen()           { return                vtableLen.getValue(this); }
  public long      getItableLen()           { return                itableLen.getValue(this); }
  public long      majorVersion()           { return                majorVersion.getValue(this); }
  public long      minorVersion()           { return                minorVersion.getValue(this); }
  public Symbol    getGenericSignature()    {
    long index = genericSignatureIndex.getValue(this);
    if (index != 0) {
      return getConstants().getSymbolAt(index);
    } else {
      return null;
    }
  }

  // "size helper" == instance size in words
  public long getSizeHelper() {
    int lh = getLayoutHelper();
    if (Assert.ASSERTS_ENABLED) {
      Assert.that(lh > 0, "layout helper initialized for instance class");
    }
    return lh / VM.getVM().getAddressSize();
  }

  // same as enum InnerClassAttributeOffset in VM code.
  public static interface InnerClassAttributeOffset {
    // from JVM spec. "InnerClasses" attribute
    public static final int innerClassInnerClassInfoOffset = 0;
    public static final int innerClassOuterClassInfoOffset = 1;
    public static final int innerClassInnerNameOffset = 2;
    public static final int innerClassAccessFlagsOffset = 3;
    public static final int innerClassNextOffset = 4;
  };

  public static interface EnclosingMethodAttributeOffset {
    public static final int enclosing_method_class_index_offset = 0;
    public static final int enclosing_method_method_index_offset = 1;
    public static final int enclosing_method_attribute_size = 2;
  };

  // refer to compute_modifier_flags in VM code.
  public long computeModifierFlags() {
    long access = getAccessFlags();
    // But check if it happens to be member class.
    U2Array innerClassList = getInnerClasses();
    int length = (innerClassList == null)? 0 : (int) innerClassList.length();
    if (length > 0) {
       if (Assert.ASSERTS_ENABLED) {
          Assert.that(length % InnerClassAttributeOffset.innerClassNextOffset == 0 ||
                      length % InnerClassAttributeOffset.innerClassNextOffset == EnclosingMethodAttributeOffset.enclosing_method_attribute_size,
                      "just checking");
       }
       for (int i = 0; i < length; i += InnerClassAttributeOffset.innerClassNextOffset) {
          if (i == length - EnclosingMethodAttributeOffset.enclosing_method_attribute_size) {
              break;
          }
          int ioff = innerClassList.at(i +
                         InnerClassAttributeOffset.innerClassInnerClassInfoOffset);
          // 'ioff' can be zero.
          // refer to JVM spec. section 4.7.5.
          if (ioff != 0) {
             // only look at classes that are already loaded
             // since we are looking for the flags for our self.
             ConstantPool.CPSlot classInfo = getConstants().getSlotAt(ioff);
             Symbol name = null;
             if (classInfo.isResolved()) {
               name = classInfo.getKlass().getName();
             } else if (classInfo.isUnresolved()) {
               name = classInfo.getSymbol();
             } else {
                throw new RuntimeException("should not reach here");
             }

             if (name.equals(getName())) {
                // This is really a member class
                access = innerClassList.at(i +
                        InnerClassAttributeOffset.innerClassAccessFlagsOffset);
                break;
             }
          }
       } // for inner classes
    }

    // Remember to strip ACC_SUPER bit
    return (access & (~JVM_ACC_SUPER)) & JVM_ACC_WRITTEN_FLAGS;
  }


  // whether given Symbol is name of an inner/nested Klass of this Klass?
  // anonymous and local classes are excluded.
  public boolean isInnerClassName(Symbol sym) {
    return isInInnerClasses(sym, false);
  }

  // whether given Symbol is name of an inner/nested Klass of this Klass?
  // anonymous classes excluded, but local classes are included.
  public boolean isInnerOrLocalClassName(Symbol sym) {
    return isInInnerClasses(sym, true);
  }

  private boolean isInInnerClasses(Symbol sym, boolean includeLocals) {
    U2Array innerClassList = getInnerClasses();
    int length = ( innerClassList == null)? 0 : (int) innerClassList.length();
    if (length > 0) {
       if (Assert.ASSERTS_ENABLED) {
         Assert.that(length % InnerClassAttributeOffset.innerClassNextOffset == 0 ||
                     length % InnerClassAttributeOffset.innerClassNextOffset == EnclosingMethodAttributeOffset.enclosing_method_attribute_size,
                     "just checking");
       }
       for (int i = 0; i < length; i += InnerClassAttributeOffset.innerClassNextOffset) {
         if (i == length - EnclosingMethodAttributeOffset.enclosing_method_attribute_size) {
             break;
         }
         int ioff = innerClassList.at(i +
                        InnerClassAttributeOffset.innerClassInnerClassInfoOffset);
         // 'ioff' can be zero.
         // refer to JVM spec. section 4.7.5.
         if (ioff != 0) {
            ConstantPool.CPSlot iclassInfo = getConstants().getSlotAt(ioff);
            Symbol innerName = getConstants().getKlassNameAt(ioff);
            Symbol myname = getName();
            int ooff = innerClassList.at(i +
                        InnerClassAttributeOffset.innerClassOuterClassInfoOffset);
            // for anonymous classes inner_name_index of InnerClasses
            // attribute is zero.
            int innerNameIndex = innerClassList.at(i +
                        InnerClassAttributeOffset.innerClassInnerNameOffset);
            // if this is not a member (anonymous, local etc.), 'ooff' will be zero
            // refer to JVM spec. section 4.7.5.
            if (ooff == 0) {
               if (includeLocals) {
                  // does it looks like my local class?
                  if (innerName.equals(sym) &&
                     innerName.asString().startsWith(myname.asString())) {
                     // exclude anonymous classes.
                     return (innerNameIndex != 0);
                  }
               }
            } else {
               ConstantPool.CPSlot oclassInfo = getConstants().getSlotAt(ooff);
               Symbol outerName = null;
               if (oclassInfo.isResolved()) {
                 outerName = oclassInfo.getKlass().getName();
               } else if (oclassInfo.isUnresolved()) {
                 outerName = oclassInfo.getSymbol();
               } else {
                  throw new RuntimeException("should not reach here");
               }

               // include only if current class is outer class.
               if (outerName.equals(myname) && innerName.equals(sym)) {
                  return true;
               }
           }
         }
       } // for inner classes
       return false;
    } else {
       return false;
    }
  }

  public boolean implementsInterface(Klass k) {
    if (Assert.ASSERTS_ENABLED) {
      Assert.that(k.isInterface(), "should not reach here");
    }
    KlassArray interfaces =  getTransitiveInterfaces();
    final int len = interfaces.length();
    for (int i = 0; i < len; i++) {
      if (interfaces.getAt(i).equals(k)) return true;
    }
    return false;
  }

  boolean computeSubtypeOf(Klass k) {
    if (k.isInterface()) {
      return implementsInterface(k);
    } else {
      return super.computeSubtypeOf(k);
    }
  }

  public void printValueOn(PrintStream tty) {
    tty.print("InstanceKlass for " + getName().asString());
  }

  public void iterateFields(MetadataVisitor visitor) {
    super.iterateFields(visitor);
    visitor.doMetadata(arrayKlasses, true);
    // visitor.doOop(methods, true);
    // visitor.doOop(localInterfaces, true);
    // visitor.doOop(transitiveInterfaces, true);
      visitor.doCInt(nonstaticFieldSize, true);
      visitor.doCInt(staticFieldSize, true);
      visitor.doCInt(staticOopFieldCount, true);
      visitor.doCInt(nonstaticOopMapSize, true);
      visitor.doCInt(isMarkedDependent, true);
      visitor.doCInt(initState, true);
      visitor.doCInt(vtableLen, true);
      visitor.doCInt(itableLen, true);
    }

  /*
   *  Visit the static fields of this InstanceKlass with the obj of
   *  the visitor set to the oop holding the fields, which is
   *  currently the java mirror.
   */
  public void iterateStaticFields(OopVisitor visitor) {
    visitor.setObj(getJavaMirror());
    visitor.prologue();
    iterateStaticFieldsInternal(visitor);
    visitor.epilogue();

  }

  void iterateStaticFieldsInternal(OopVisitor visitor) {
    int length = getJavaFieldsCount();
    for (int index = 0; index < length; index++) {
      short accessFlags    = getFieldAccessFlags(index);
      FieldType   type   = new FieldType(getFieldSignature(index));
      AccessFlags access = new AccessFlags(accessFlags);
      if (access.isStatic()) {
        visitField(visitor, type, index);
      }
    }
  }

  public Klass getJavaSuper() {
    return getSuper();
  }

  public static class StaticField {
    public AccessFlags flags;
    public Field field;

    StaticField(Field field, AccessFlags flags) {
      this.field = field;
      this.flags = flags;
    }
  }

  public Field[] getStaticFields() {
    U2Array fields = getFields();
    int length = getJavaFieldsCount();
    ArrayList result = new ArrayList();
    for (int index = 0; index < length; index++) {
      Field f = newField(index);
      if (f.isStatic()) {
        result.add(f);
      }
    }
    return (Field[])result.toArray(new Field[result.size()]);
  }

  public void iterateNonStaticFields(OopVisitor visitor, Oop obj) {
    if (getSuper() != null) {
      ((InstanceKlass) getSuper()).iterateNonStaticFields(visitor, obj);
    }
    int length = getJavaFieldsCount();
    for (int index = 0; index < length; index++) {
      short accessFlags    = getFieldAccessFlags(index);
      FieldType   type   = new FieldType(getFieldSignature(index));
      AccessFlags access = new AccessFlags(accessFlags);
      if (!access.isStatic()) {
        visitField(visitor, type, index);
      }
    }
  }

  /** Field access by name. */
  public Field findLocalField(Symbol name, Symbol sig) {
    int length = getJavaFieldsCount();
    for (int i = 0; i < length; i++) {
      Symbol f_name = getFieldName(i);
      Symbol f_sig  = getFieldSignature(i);
      if (name.equals(f_name) && sig.equals(f_sig)) {
        return newField(i);
      }
    }

    return null;
  }

  /** Find field in direct superinterfaces. */
  public Field findInterfaceField(Symbol name, Symbol sig) {
    KlassArray interfaces = getLocalInterfaces();
    int n = interfaces.length();
    for (int i = 0; i < n; i++) {
      InstanceKlass intf1 = (InstanceKlass) interfaces.getAt(i);
      if (Assert.ASSERTS_ENABLED) {
        Assert.that(intf1.isInterface(), "just checking type");
      }
      // search for field in current interface
      Field f = intf1.findLocalField(name, sig);
      if (f != null) {
        if (Assert.ASSERTS_ENABLED) {
          Assert.that(f.getAccessFlagsObj().isStatic(), "interface field must be static");
        }
        return f;
      }
      // search for field in direct superinterfaces
      f = intf1.findInterfaceField(name, sig);
      if (f != null) return f;
    }
    // otherwise field lookup fails
    return null;
  }

  /** Find field according to JVM spec 5.4.3.2, returns the klass in
      which the field is defined. */
  public Field findField(Symbol name, Symbol sig) {
    // search order according to newest JVM spec (5.4.3.2, p.167).
    // 1) search for field in current klass
    Field f = findLocalField(name, sig);
    if (f != null) return f;

    // 2) search for field recursively in direct superinterfaces
    f = findInterfaceField(name, sig);
    if (f != null) return f;

    // 3) apply field lookup recursively if superclass exists
    InstanceKlass supr = (InstanceKlass) getSuper();
    if (supr != null) return supr.findField(name, sig);

    // 4) otherwise field lookup fails
    return null;
  }

  /** Find field according to JVM spec 5.4.3.2, returns the klass in
      which the field is defined (convenience routine) */
  public Field findField(String name, String sig) {
    SymbolTable symbols = VM.getVM().getSymbolTable();
    Symbol nameSym = symbols.probe(name);
    Symbol sigSym  = symbols.probe(sig);
    if (nameSym == null || sigSym == null) {
      return null;
    }
    return findField(nameSym, sigSym);
  }

  /** Find field according to JVM spec 5.4.3.2, returns the klass in
      which the field is defined (retained only for backward
      compatibility with jdbx) */
  public Field findFieldDbg(String name, String sig) {
    return findField(name, sig);
  }

  /** Get field by its index in the fields array. Only designed for
      use in a debugging system. */
  public Field getFieldByIndex(int fieldIndex) {
    return newField(fieldIndex);
  }


    /** Return a List of SA Fields for the fields declared in this class.
        Inherited fields are not included.
        Return an empty list if there are no fields declared in this class.
        Only designed for use in a debugging system. */
    public List getImmediateFields() {
        // A list of Fields for each field declared in this class/interface,
        // not including inherited fields.
        int length = getJavaFieldsCount();
        List immediateFields = new ArrayList(length);
        for (int index = 0; index < length; index++) {
            immediateFields.add(getFieldByIndex(index));
        }

        return immediateFields;
    }

    /** Return a List of SA Fields for all the java fields in this class,
        including all inherited fields.  This includes hidden
        fields.  Thus the returned list can contain fields with
        the same name.
        Return an empty list if there are no fields.
        Only designed for use in a debugging system. */
    public List getAllFields() {
        // Contains a Field for each field in this class, including immediate
        // fields and inherited fields.
        List  allFields = getImmediateFields();

        // transitiveInterfaces contains all interfaces implemented
        // by this class and its superclass chain with no duplicates.

        KlassArray interfaces = getTransitiveInterfaces();
        int n = interfaces.length();
        for (int i = 0; i < n; i++) {
            InstanceKlass intf1 = (InstanceKlass) interfaces.getAt(i);
            if (Assert.ASSERTS_ENABLED) {
                Assert.that(intf1.isInterface(), "just checking type");
            }
            allFields.addAll(intf1.getImmediateFields());
        }

        // Get all fields in the superclass, recursively.  But, don't
        // include fields in interfaces implemented by superclasses;
        // we already have all those.
        if (!isInterface()) {
            InstanceKlass supr;
            if  ( (supr = (InstanceKlass) getSuper()) != null) {
                allFields.addAll(supr.getImmediateFields());
            }
        }

        return allFields;
    }


    /** Return a List of SA Methods declared directly in this class/interface.
        Return an empty list if there are none, or if this isn't a class/
        interface.
    */
    public List getImmediateMethods() {
      // Contains a Method for each method declared in this class/interface
      // not including inherited methods.

      MethodArray methods = getMethods();
      int length = methods.length();
      Object[] tmp = new Object[length];

      IntArray methodOrdering = getMethodOrdering();
      if (methodOrdering.length() != length) {
         // no ordering info present
         for (int index = 0; index < length; index++) {
            tmp[index] = methods.at(index);
         }
      } else {
         for (int index = 0; index < length; index++) {
            int originalIndex = methodOrdering.at(index);
            tmp[originalIndex] = methods.at(index);
         }
      }

      return Arrays.asList(tmp);
    }

    /** Return a List containing an SA InstanceKlass for each
        interface named in this class's 'implements' clause.
    */
    public List getDirectImplementedInterfaces() {
        // Contains an InstanceKlass for each interface in this classes
        // 'implements' clause.

        KlassArray interfaces = getLocalInterfaces();
        int length = interfaces.length();
        List directImplementedInterfaces = new ArrayList(length);

        for (int index = 0; index < length; index ++) {
            directImplementedInterfaces.add(interfaces.getAt(index));
        }

        return directImplementedInterfaces;
    }

  public Klass arrayKlassImpl(boolean orNull, int n) {
    // FIXME: in reflective system this would need to change to
    // actually allocate
    if (getArrayKlasses() == null) { return null; }
    ObjArrayKlass oak = (ObjArrayKlass) getArrayKlasses();
    if (orNull) {
      return oak.arrayKlassOrNull(n);
    }
    return oak.arrayKlass(n);
  }

  public Klass arrayKlassImpl(boolean orNull) {
    return arrayKlassImpl(orNull, 1);
  }

  public String signature() {
     return "L" + super.signature() + ";";
  }

  /** Convenience routine taking Strings; lookup is done in
      SymbolTable. */
  public Method findMethod(String name, String sig) {
    SymbolTable syms = VM.getVM().getSymbolTable();
    Symbol nameSym = syms.probe(name);
    Symbol sigSym  = syms.probe(sig);
    if (nameSym == null || sigSym == null) {
      return null;
    }
    return findMethod(nameSym, sigSym);
  }

  /** Find method in vtable. */
  public Method findMethod(Symbol name, Symbol sig) {
    return findMethod(getMethods(), name, sig);
  }

  /** Breakpoint support (see methods on Method* for details) */
  public BreakpointInfo getBreakpoints() {
    Address addr = getAddress().getAddressAt(breakpoints.getOffset());
    return (BreakpointInfo) VMObjectFactory.newObject(BreakpointInfo.class, addr);
  }

  public IntArray  getMethodOrdering() {
    Address addr = getAddress().getAddressAt(methodOrdering.getOffset());
    return (IntArray) VMObjectFactory.newObject(IntArray.class, addr);
  }

  public U2Array getFields() {
    Address addr = getAddress().getAddressAt(fields.getOffset());
    return (U2Array) VMObjectFactory.newObject(U2Array.class, addr);
  }

  public U2Array getInnerClasses() {
    Address addr = getAddress().getAddressAt(innerClasses.getOffset());
    return (U2Array) VMObjectFactory.newObject(U2Array.class, addr);
  }


  //----------------------------------------------------------------------
  // Internals only below this point
  //

  private void visitField(OopVisitor visitor, FieldType type, int index) {
    Field f = newField(index);
    if (type.isOop()) {
      visitor.doOop((OopField) f, false);
      return;
    }
    if (type.isByte()) {
      visitor.doByte((ByteField) f, false);
      return;
    }
    if (type.isChar()) {
      visitor.doChar((CharField) f, false);
      return;
    }
    if (type.isDouble()) {
      visitor.doDouble((DoubleField) f, false);
      return;
    }
    if (type.isFloat()) {
      visitor.doFloat((FloatField) f, false);
      return;
    }
    if (type.isInt()) {
      visitor.doInt((IntField) f, false);
      return;
    }
    if (type.isLong()) {
      visitor.doLong((LongField) f, false);
      return;
    }
    if (type.isShort()) {
      visitor.doShort((ShortField) f, false);
      return;
    }
    if (type.isBoolean()) {
      visitor.doBoolean((BooleanField) f, false);
      return;
    }
  }

  // Creates new field from index in fields TypeArray
  private Field newField(int index) {
    FieldType type = new FieldType(getFieldSignature(index));
    if (type.isOop()) {
     if (VM.getVM().isCompressedOopsEnabled()) {
        return new NarrowOopField(this, index);
     } else {
        return new OopField(this, index);
     }
    }
    if (type.isByte()) {
      return new ByteField(this, index);
    }
    if (type.isChar()) {
      return new CharField(this, index);
    }
    if (type.isDouble()) {
      return new DoubleField(this, index);
    }
    if (type.isFloat()) {
      return new FloatField(this, index);
    }
    if (type.isInt()) {
      return new IntField(this, index);
    }
    if (type.isLong()) {
      return new LongField(this, index);
    }
    if (type.isShort()) {
      return new ShortField(this, index);
    }
    if (type.isBoolean()) {
      return new BooleanField(this, index);
    }
    throw new RuntimeException("Illegal field type at index " + index);
  }

  private static Method findMethod(MethodArray methods, Symbol name, Symbol signature) {
    int len = methods.length();
    // methods are sorted, so do binary search
    int l = 0;
    int h = len - 1;
    while (l <= h) {
      int mid = (l + h) >> 1;
      Method m = methods.at(mid);
      int res = m.getName().fastCompare(name);
      if (res == 0) {
        // found matching name; do linear search to find matching signature
        // first, quick check for common case
        if (m.getSignature().equals(signature)) return m;
        // search downwards through overloaded methods
        int i;
        for (i = mid - 1; i >= l; i--) {
          Method m1 = methods.at(i);
          if (!m1.getName().equals(name)) break;
          if (m1.getSignature().equals(signature)) return m1;
        }
        // search upwards
        for (i = mid + 1; i <= h; i++) {
          Method m1 = methods.at(i);
          if (!m1.getName().equals(name)) break;
          if (m1.getSignature().equals(signature)) return m1;
        }
        // not found
        if (Assert.ASSERTS_ENABLED) {
          int index = linearSearch(methods, name, signature);
          if (index != -1) {
            throw new DebuggerException("binary search bug: should have found entry " + index);
          }
        }
        return null;
      } else if (res < 0) {
        l = mid + 1;
      } else {
        h = mid - 1;
      }
    }
    if (Assert.ASSERTS_ENABLED) {
      int index = linearSearch(methods, name, signature);
      if (index != -1) {
        throw new DebuggerException("binary search bug: should have found entry " + index);
      }
    }
    return null;
  }

  private static int linearSearch(MethodArray methods, Symbol name, Symbol signature) {
    int len = (int) methods.length();
    for (int index = 0; index < len; index++) {
      Method m = methods.at(index);
      if (m.getSignature().equals(signature) && m.getName().equals(name)) {
        return index;
      }
    }
    return -1;
  }

  public void dumpReplayData(PrintStream out) {
    ConstantPool cp = getConstants();

    // Try to record related loaded classes
    Klass sub = getSubklassKlass();
    while (sub != null) {
        if (sub instanceof InstanceKlass) {
            out.println("instanceKlass " + sub.getName().asString());
        }
        sub = sub.getNextSiblingKlass();
    }

    final int length = (int) cp.getLength();
    out.print("ciInstanceKlass " + getName().asString() + " " + (isLinked() ? 1 : 0) + " " + (isInitialized() ? 1 : 0) + " " + length);
    for (int index = 1; index < length; index++) {
      out.print(" " + cp.getTags().at(index));
    }
    out.println();
    if (isInitialized()) {
      Field[] staticFields = getStaticFields();
      for (int i = 0; i < staticFields.length; i++) {
        Field f = staticFields[i];
        Oop mirror = getJavaMirror();
        if (f.isFinal() && !f.hasInitialValue()) {
          out.print("staticfield " + getName().asString() + " " +
                    OopUtilities.escapeString(f.getID().getName()) + " " +
                    f.getFieldType().getSignature().asString() + " ");
          if (f instanceof ByteField) {
            ByteField bf = (ByteField)f;
            out.println(bf.getValue(mirror));
          } else if (f instanceof BooleanField) {
            BooleanField bf = (BooleanField)f;
            out.println(bf.getValue(mirror) ? 1 : 0);
          } else if (f instanceof ShortField) {
            ShortField bf = (ShortField)f;
            out.println(bf.getValue(mirror));
          } else if (f instanceof CharField) {
            CharField bf = (CharField)f;
            out.println(bf.getValue(mirror) & 0xffff);
          } else if (f instanceof IntField) {
            IntField bf = (IntField)f;
            out.println(bf.getValue(mirror));
          } else  if (f instanceof LongField) {
            LongField bf = (LongField)f;
            out.println(bf.getValue(mirror));
          } else if (f instanceof FloatField) {
            FloatField bf = (FloatField)f;
            out.println(Float.floatToRawIntBits(bf.getValue(mirror)));
          } else if (f instanceof DoubleField) {
            DoubleField bf = (DoubleField)f;
            out.println(Double.doubleToRawLongBits(bf.getValue(mirror)));
          } else if (f instanceof OopField) {
            OopField bf = (OopField)f;

            Oop value = bf.getValue(mirror);
            if (value == null) {
              out.println("null");
            } else if (value.isInstance()) {
              Instance inst = (Instance)value;
              if (inst.isA(SystemDictionary.getStringKlass())) {
                out.println("\"" + OopUtilities.stringOopToEscapedString(inst) + "\"");
              } else {
                out.println(inst.getKlass().getName().asString());
              }
            } else if (value.isObjArray()) {
              ObjArray oa = (ObjArray)value;
              Klass ek = (ObjArrayKlass)oa.getKlass();
              out.println(oa.getLength() + " " + ek.getName().asString());
            } else if (value.isTypeArray()) {
              TypeArray ta = (TypeArray)value;
              out.println(ta.getLength());
            } else {
              out.println(value);
            }
          }
        }
      }
    }
  }
}
