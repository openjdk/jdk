/*
 * Copyright (c) 2000, 2008, Oracle and/or its affiliates. All rights reserved.
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
  public static int ACCESS_FLAGS_OFFSET;
  public static int NAME_INDEX_OFFSET;
  public static int SIGNATURE_INDEX_OFFSET;
  public static int INITVAL_INDEX_OFFSET;
  public static int LOW_OFFSET;
  public static int HIGH_OFFSET;
  public static int GENERIC_SIGNATURE_INDEX_OFFSET;
  public static int NEXT_OFFSET;
  public static int IMPLEMENTORS_LIMIT;

  // ClassState constants
  private static int CLASS_STATE_UNPARSABLE_BY_GC;
  private static int CLASS_STATE_ALLOCATED;
  private static int CLASS_STATE_LOADED;
  private static int CLASS_STATE_LINKED;
  private static int CLASS_STATE_BEING_INITIALIZED;
  private static int CLASS_STATE_FULLY_INITIALIZED;
  private static int CLASS_STATE_INITIALIZATION_ERROR;

  private static synchronized void initialize(TypeDataBase db) throws WrongTypeException {
    Type type            = db.lookupType("instanceKlass");
    arrayKlasses         = new OopField(type.getOopField("_array_klasses"), Oop.getHeaderSize());
    methods              = new OopField(type.getOopField("_methods"), Oop.getHeaderSize());
    methodOrdering       = new OopField(type.getOopField("_method_ordering"), Oop.getHeaderSize());
    localInterfaces      = new OopField(type.getOopField("_local_interfaces"), Oop.getHeaderSize());
    transitiveInterfaces = new OopField(type.getOopField("_transitive_interfaces"), Oop.getHeaderSize());
    nofImplementors      = new CIntField(type.getCIntegerField("_nof_implementors"), Oop.getHeaderSize());
    IMPLEMENTORS_LIMIT   = db.lookupIntConstant("instanceKlass::implementors_limit").intValue();
    implementors         = new OopField[IMPLEMENTORS_LIMIT];
    for (int i = 0; i < IMPLEMENTORS_LIMIT; i++) {
      long arrayOffset = Oop.getHeaderSize() + (i * db.getAddressSize());
      implementors[i]    = new OopField(type.getOopField("_implementors[0]"), arrayOffset);
    }
    fields               = new OopField(type.getOopField("_fields"), Oop.getHeaderSize());
    constants            = new OopField(type.getOopField("_constants"), Oop.getHeaderSize());
    classLoader          = new OopField(type.getOopField("_class_loader"), Oop.getHeaderSize());
    protectionDomain     = new OopField(type.getOopField("_protection_domain"), Oop.getHeaderSize());
    signers              = new OopField(type.getOopField("_signers"), Oop.getHeaderSize());
    sourceFileName       = new OopField(type.getOopField("_source_file_name"), Oop.getHeaderSize());
    sourceDebugExtension = new OopField(type.getOopField("_source_debug_extension"), Oop.getHeaderSize());
    innerClasses         = new OopField(type.getOopField("_inner_classes"), Oop.getHeaderSize());
    nonstaticFieldSize   = new CIntField(type.getCIntegerField("_nonstatic_field_size"), Oop.getHeaderSize());
    staticFieldSize      = new CIntField(type.getCIntegerField("_static_field_size"), Oop.getHeaderSize());
    staticOopFieldSize   = new CIntField(type.getCIntegerField("_static_oop_field_size"), Oop.getHeaderSize());
    nonstaticOopMapSize  = new CIntField(type.getCIntegerField("_nonstatic_oop_map_size"), Oop.getHeaderSize());
    isMarkedDependent    = new CIntField(type.getCIntegerField("_is_marked_dependent"), Oop.getHeaderSize());
    initState            = new CIntField(type.getCIntegerField("_init_state"), Oop.getHeaderSize());
    vtableLen            = new CIntField(type.getCIntegerField("_vtable_len"), Oop.getHeaderSize());
    itableLen            = new CIntField(type.getCIntegerField("_itable_len"), Oop.getHeaderSize());
    breakpoints          = type.getAddressField("_breakpoints");
    genericSignature     = new OopField(type.getOopField("_generic_signature"), Oop.getHeaderSize());
    majorVersion         = new CIntField(type.getCIntegerField("_major_version"), Oop.getHeaderSize());
    minorVersion         = new CIntField(type.getCIntegerField("_minor_version"), Oop.getHeaderSize());
    headerSize           = alignObjectOffset(Oop.getHeaderSize() + type.getSize());

    // read field offset constants
    ACCESS_FLAGS_OFFSET = db.lookupIntConstant("instanceKlass::access_flags_offset").intValue();
    NAME_INDEX_OFFSET = db.lookupIntConstant("instanceKlass::name_index_offset").intValue();
    SIGNATURE_INDEX_OFFSET = db.lookupIntConstant("instanceKlass::signature_index_offset").intValue();
    INITVAL_INDEX_OFFSET = db.lookupIntConstant("instanceKlass::initval_index_offset").intValue();
    LOW_OFFSET = db.lookupIntConstant("instanceKlass::low_offset").intValue();
    HIGH_OFFSET = db.lookupIntConstant("instanceKlass::high_offset").intValue();
    GENERIC_SIGNATURE_INDEX_OFFSET = db.lookupIntConstant("instanceKlass::generic_signature_offset").intValue();
    NEXT_OFFSET = db.lookupIntConstant("instanceKlass::next_offset").intValue();
    // read ClassState constants
    CLASS_STATE_UNPARSABLE_BY_GC = db.lookupIntConstant("instanceKlass::unparsable_by_gc").intValue();
    CLASS_STATE_ALLOCATED = db.lookupIntConstant("instanceKlass::allocated").intValue();
    CLASS_STATE_LOADED = db.lookupIntConstant("instanceKlass::loaded").intValue();
    CLASS_STATE_LINKED = db.lookupIntConstant("instanceKlass::linked").intValue();
    CLASS_STATE_BEING_INITIALIZED = db.lookupIntConstant("instanceKlass::being_initialized").intValue();
    CLASS_STATE_FULLY_INITIALIZED = db.lookupIntConstant("instanceKlass::fully_initialized").intValue();
    CLASS_STATE_INITIALIZATION_ERROR = db.lookupIntConstant("instanceKlass::initialization_error").intValue();

  }

  InstanceKlass(OopHandle handle, ObjectHeap heap) {
    super(handle, heap);
  }

  private static OopField  arrayKlasses;
  private static OopField  methods;
  private static OopField  methodOrdering;
  private static OopField  localInterfaces;
  private static OopField  transitiveInterfaces;
  private static CIntField nofImplementors;
  private static OopField[] implementors;
  private static OopField  fields;
  private static OopField  constants;
  private static OopField  classLoader;
  private static OopField  protectionDomain;
  private static OopField  signers;
  private static OopField  sourceFileName;
  private static OopField  sourceDebugExtension;
  private static OopField  innerClasses;
  private static CIntField nonstaticFieldSize;
  private static CIntField staticFieldSize;
  private static CIntField staticOopFieldSize;
  private static CIntField nonstaticOopMapSize;
  private static CIntField isMarkedDependent;
  private static CIntField initState;
  private static CIntField vtableLen;
  private static CIntField itableLen;
  private static AddressField breakpoints;
  private static OopField  genericSignature;
  private static CIntField majorVersion;
  private static CIntField minorVersion;

  // type safe enum for ClassState from instanceKlass.hpp
  public static class ClassState {
     public static final ClassState UNPARSABLE_BY_GC = new ClassState("unparsable_by_gc");
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

  private int  getInitStateAsInt() { return (int) initState.getValue(this); }
  public ClassState getInitState() {
     int state = getInitStateAsInt();
     if (state == CLASS_STATE_UNPARSABLE_BY_GC) {
        return ClassState.UNPARSABLE_BY_GC;
     } else if (state == CLASS_STATE_ALLOCATED) {
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

  public static long getHeaderSize() { return headerSize; }

  // Accessors for declared fields
  public Klass     getArrayKlasses()        { return (Klass)        arrayKlasses.getValue(this); }
  public ObjArray  getMethods()             { return (ObjArray)     methods.getValue(this); }
  public TypeArray getMethodOrdering()      { return (TypeArray)    methodOrdering.getValue(this); }
  public ObjArray  getLocalInterfaces()     { return (ObjArray)     localInterfaces.getValue(this); }
  public ObjArray  getTransitiveInterfaces() { return (ObjArray)     transitiveInterfaces.getValue(this); }
  public long      nofImplementors()        { return                nofImplementors.getValue(this); }
  public Klass     getImplementor()         { return (Klass)        implementors[0].getValue(this); }
  public Klass     getImplementor(int i)    { return (Klass)        implementors[i].getValue(this); }
  public TypeArray getFields()              { return (TypeArray)    fields.getValue(this); }
  public ConstantPool getConstants()        { return (ConstantPool) constants.getValue(this); }
  public Oop       getClassLoader()         { return                classLoader.getValue(this); }
  public Oop       getProtectionDomain()    { return                protectionDomain.getValue(this); }
  public ObjArray  getSigners()             { return (ObjArray)     signers.getValue(this); }
  public Symbol    getSourceFileName()      { return (Symbol)       sourceFileName.getValue(this); }
  public Symbol    getSourceDebugExtension(){ return (Symbol)       sourceDebugExtension.getValue(this); }
  public TypeArray getInnerClasses()        { return (TypeArray)    innerClasses.getValue(this); }
  public long      getNonstaticFieldSize()  { return                nonstaticFieldSize.getValue(this); }
  public long      getStaticFieldSize()     { return                staticFieldSize.getValue(this); }
  public long      getStaticOopFieldSize()  { return                staticOopFieldSize.getValue(this); }
  public long      getNonstaticOopMapSize() { return                nonstaticOopMapSize.getValue(this); }
  public boolean   getIsMarkedDependent()   { return                isMarkedDependent.getValue(this) != 0; }
  public long      getVtableLen()           { return                vtableLen.getValue(this); }
  public long      getItableLen()           { return                itableLen.getValue(this); }
  public Symbol    getGenericSignature()    { return (Symbol)       genericSignature.getValue(this); }
  public long      majorVersion()           { return                majorVersion.getValue(this); }
  public long      minorVersion()           { return                minorVersion.getValue(this); }

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

  // refer to compute_modifier_flags in VM code.
  public long computeModifierFlags() {
    long access = getAccessFlags();
    // But check if it happens to be member class.
    TypeArray innerClassList = getInnerClasses();
    int length = ( innerClassList == null)? 0 : (int) innerClassList.getLength();
    if (length > 0) {
       if (Assert.ASSERTS_ENABLED) {
          Assert.that(length % InnerClassAttributeOffset.innerClassNextOffset == 0, "just checking");
       }
       for (int i = 0; i < length; i += InnerClassAttributeOffset.innerClassNextOffset) {
          int ioff = innerClassList.getShortAt(i +
                         InnerClassAttributeOffset.innerClassInnerClassInfoOffset);
          // 'ioff' can be zero.
          // refer to JVM spec. section 4.7.5.
          if (ioff != 0) {
             // only look at classes that are already loaded
             // since we are looking for the flags for our self.
             Oop classInfo = getConstants().getObjAt(ioff);
             Symbol name = null;
             if (classInfo instanceof Klass) {
                name = ((Klass) classInfo).getName();
             } else if (classInfo instanceof Symbol) {
                name = (Symbol) classInfo;
             } else {
                throw new RuntimeException("should not reach here");
             }

             if (name.equals(getName())) {
                // This is really a member class
                access = innerClassList.getShortAt(i +
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
    TypeArray innerClassList = getInnerClasses();
    int length = ( innerClassList == null)? 0 : (int) innerClassList.getLength();
    if (length > 0) {
       if (Assert.ASSERTS_ENABLED) {
         Assert.that(length % InnerClassAttributeOffset.innerClassNextOffset == 0, "just checking");
       }
       for (int i = 0; i < length; i += InnerClassAttributeOffset.innerClassNextOffset) {
         int ioff = innerClassList.getShortAt(i +
                        InnerClassAttributeOffset.innerClassInnerClassInfoOffset);
         // 'ioff' can be zero.
         // refer to JVM spec. section 4.7.5.
         if (ioff != 0) {
            Oop iclassInfo = getConstants().getObjAt(ioff);
            Symbol innerName = null;
            if (iclassInfo instanceof Klass) {
               innerName = ((Klass) iclassInfo).getName();
            } else if (iclassInfo instanceof Symbol) {
               innerName = (Symbol) iclassInfo;
            } else {
               throw new RuntimeException("should not reach here");
            }

            Symbol myname = getName();
            int ooff = innerClassList.getShortAt(i +
                        InnerClassAttributeOffset.innerClassOuterClassInfoOffset);
            // for anonymous classes inner_name_index of InnerClasses
            // attribute is zero.
            int innerNameIndex = innerClassList.getShortAt(i +
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
               Oop oclassInfo = getConstants().getObjAt(ooff);
               Symbol outerName = null;
               if (oclassInfo instanceof Klass) {
                  outerName = ((Klass) oclassInfo).getName();
               } else if (oclassInfo instanceof Symbol) {
                  outerName = (Symbol) oclassInfo;
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
    ObjArray interfaces =  getTransitiveInterfaces();
    final int len = (int) interfaces.getLength();
    for (int i = 0; i < len; i++) {
      if (interfaces.getObjAt(i).equals(k)) return true;
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

  public void iterateFields(OopVisitor visitor, boolean doVMFields) {
    super.iterateFields(visitor, doVMFields);
    if (doVMFields) {
      visitor.doOop(arrayKlasses, true);
      visitor.doOop(methods, true);
      visitor.doOop(methodOrdering, true);
      visitor.doOop(localInterfaces, true);
      visitor.doOop(transitiveInterfaces, true);
      visitor.doCInt(nofImplementors, true);
      for (int i = 0; i < IMPLEMENTORS_LIMIT; i++)
        visitor.doOop(implementors[i], true);
      visitor.doOop(fields, true);
      visitor.doOop(constants, true);
      visitor.doOop(classLoader, true);
      visitor.doOop(protectionDomain, true);
      visitor.doOop(signers, true);
      visitor.doOop(sourceFileName, true);
      visitor.doOop(innerClasses, true);
      visitor.doCInt(nonstaticFieldSize, true);
      visitor.doCInt(staticFieldSize, true);
      visitor.doCInt(staticOopFieldSize, true);
      visitor.doCInt(nonstaticOopMapSize, true);
      visitor.doCInt(isMarkedDependent, true);
      visitor.doCInt(initState, true);
      visitor.doCInt(vtableLen, true);
      visitor.doCInt(itableLen, true);
    }

    TypeArray fields = getFields();
    int length = (int) fields.getLength();
    for (int index = 0; index < length; index += NEXT_OFFSET) {
      short accessFlags    = fields.getShortAt(index + ACCESS_FLAGS_OFFSET);
      short signatureIndex = fields.getShortAt(index + SIGNATURE_INDEX_OFFSET);
      FieldType   type   = new FieldType((Symbol) getConstants().getObjAt(signatureIndex));
      AccessFlags access = new AccessFlags(accessFlags);
      if (access.isStatic()) {
        visitField(visitor, type, index);
      }
    }
  }

  public Klass getJavaSuper() {
    return getSuper();
  }

  public void iterateNonStaticFields(OopVisitor visitor) {
    if (getSuper() != null) {
      ((InstanceKlass) getSuper()).iterateNonStaticFields(visitor);
    }
    TypeArray fields = getFields();

    int length = (int) fields.getLength();
    for (int index = 0; index < length; index += NEXT_OFFSET) {
      short accessFlags    = fields.getShortAt(index + ACCESS_FLAGS_OFFSET);
      short signatureIndex = fields.getShortAt(index + SIGNATURE_INDEX_OFFSET);

      FieldType   type   = new FieldType((Symbol) getConstants().getObjAt(signatureIndex));
      AccessFlags access = new AccessFlags(accessFlags);
      if (!access.isStatic()) {
        visitField(visitor, type, index);
      }
    }
  }

  /** Field access by name. */
  public Field findLocalField(Symbol name, Symbol sig) {
    TypeArray fields = getFields();
    int n = (int) fields.getLength();
    ConstantPool cp = getConstants();
    for (int i = 0; i < n; i += NEXT_OFFSET) {
      int nameIndex = fields.getShortAt(i + NAME_INDEX_OFFSET);
      int sigIndex  = fields.getShortAt(i + SIGNATURE_INDEX_OFFSET);
      Symbol f_name = cp.getSymbolAt(nameIndex);
      Symbol f_sig  = cp.getSymbolAt(sigIndex);
      if (name.equals(f_name) && sig.equals(f_sig)) {
        return newField(i);
      }
    }

    return null;
  }

  /** Find field in direct superinterfaces. */
  public Field findInterfaceField(Symbol name, Symbol sig) {
    ObjArray interfaces = getLocalInterfaces();
    int n = (int) interfaces.getLength();
    for (int i = 0; i < n; i++) {
      InstanceKlass intf1 = (InstanceKlass) interfaces.getObjAt(i);
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
  public Field getFieldByIndex(int fieldArrayIndex) {
    return newField(fieldArrayIndex);
  }


    /** Return a List of SA Fields for the fields declared in this class.
        Inherited fields are not included.
        Return an empty list if there are no fields declared in this class.
        Only designed for use in a debugging system. */
    public List getImmediateFields() {
        // A list of Fields for each field declared in this class/interface,
        // not including inherited fields.
        TypeArray fields = getFields();

        int length = (int) fields.getLength();
        List immediateFields = new ArrayList(length / NEXT_OFFSET);
        for (int index = 0; index < length; index += NEXT_OFFSET) {
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

        ObjArray interfaces = getTransitiveInterfaces();
        int n = (int) interfaces.getLength();
        for (int i = 0; i < n; i++) {
            InstanceKlass intf1 = (InstanceKlass) interfaces.getObjAt(i);
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

      ObjArray methods = getMethods();
      int length = (int)methods.getLength();
      Object[] tmp = new Object[length];

      TypeArray methodOrdering = getMethodOrdering();
      if (methodOrdering.getLength() != length) {
         // no ordering info present
         for (int index = 0; index < length; index++) {
            tmp[index] = methods.getObjAt(index);
         }
      } else {
         for (int index = 0; index < length; index++) {
            int originalIndex = getMethodOrdering().getIntAt(index);
            tmp[originalIndex] = methods.getObjAt(index);
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

        ObjArray interfaces = getLocalInterfaces();
        int length = (int) interfaces.getLength();
        List directImplementedInterfaces = new ArrayList(length);

        for (int index = 0; index < length; index ++) {
            directImplementedInterfaces.add(interfaces.getObjAt(index));
        }

        return directImplementedInterfaces;
    }


  public long getObjectSize() {
    long bodySize =    alignObjectOffset(getVtableLen() * getHeap().getOopSize())
                     + alignObjectOffset(getItableLen() * getHeap().getOopSize())
                     + (getStaticFieldSize() + getNonstaticOopMapSize()) * getHeap().getOopSize();
    return alignObjectSize(headerSize + bodySize);
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

  /** Breakpoint support (see methods on methodOop for details) */
  public BreakpointInfo getBreakpoints() {
    Address addr = getHandle().getAddressAt(Oop.getHeaderSize() + breakpoints.getOffset());
    return (BreakpointInfo) VMObjectFactory.newObject(BreakpointInfo.class, addr);
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
    TypeArray fields = getFields();
    short signatureIndex = fields.getShortAt(index + SIGNATURE_INDEX_OFFSET);
    FieldType type = new FieldType((Symbol) getConstants().getObjAt(signatureIndex));
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

  private static Method findMethod(ObjArray methods, Symbol name, Symbol signature) {
    int len = (int) methods.getLength();
    // methods are sorted, so do binary search
    int l = 0;
    int h = len - 1;
    while (l <= h) {
      int mid = (l + h) >> 1;
      Method m = (Method) methods.getObjAt(mid);
      int res = m.getName().fastCompare(name);
      if (res == 0) {
        // found matching name; do linear search to find matching signature
        // first, quick check for common case
        if (m.getSignature().equals(signature)) return m;
        // search downwards through overloaded methods
        int i;
        for (i = mid - 1; i >= l; i--) {
          Method m1 = (Method) methods.getObjAt(i);
          if (!m1.getName().equals(name)) break;
          if (m1.getSignature().equals(signature)) return m1;
        }
        // search upwards
        for (i = mid + 1; i <= h; i++) {
          Method m1 = (Method) methods.getObjAt(i);
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

  private static int linearSearch(ObjArray methods, Symbol name, Symbol signature) {
    int len = (int) methods.getLength();
    for (int index = 0; index < len; index++) {
      Method m = (Method) methods.getObjAt(index);
      if (m.getSignature().equals(signature) && m.getName().equals(name)) {
        return index;
      }
    }
    return -1;
  }
}
