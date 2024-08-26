/*
 * Copyright (c) 2018, 2023, Oracle and/or its affiliates. All rights reserved.
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

package sun.jvm.hotspot.memory;

import java.util.*;
import sun.jvm.hotspot.debugger.Address;
import sun.jvm.hotspot.runtime.VM;
import sun.jvm.hotspot.runtime.VMObject;
import sun.jvm.hotspot.runtime.VMObjectFactory;
import sun.jvm.hotspot.types.*;
import sun.jvm.hotspot.utilities.Observable;
import sun.jvm.hotspot.utilities.Observer;

public class FileMapInfo {
  private static FileMapHeader headerObj;

  // Fields for handling the copied C++ vtables
  private static Address rwRegionBaseAddress;
  private static Address rwRegionEndAddress;
  private static Address vtablesIndex;
  private static Address mapped_base_address;

  // HashMap created by mapping the vTable addresses in the rw region with
  // the corresponding metadata type.
  private static Map<Address, Type> vTableTypeMap;

  private static Type metadataTypeArray[];

  static {
    VM.registerVMInitializedObserver(new Observer() {
        public void update(Observable o, Object data) {
          initialize(VM.getVM().getTypeDataBase());
        }
      });
  }

  static Address getStatic_AddressField(Type type, String fieldName) {
    AddressField field = type.getAddressField(fieldName);
    return field.getValue();
  }

  static Address get_AddressField(Type type, Address instance, String fieldName) {
    AddressField field = type.getAddressField(fieldName);
    return field.getValue(instance);
  }

  static long get_CIntegerField(Type type, Address instance, String fieldName) {
    CIntegerField field = type.getCIntegerField(fieldName);
    return field.getValue(instance);
  }

  // C equivalent:   return &header->_regions[index];
  static Address get_CDSFileMapRegion(Type FileMapHeader_type, Address header, int index) {
    AddressField regionsField = FileMapHeader_type.getAddressField("_regions[0]");

    // size_t offset = offsetof(FileMapHeader, _regions[0]);
    // CDSFileMapRegion* regions_0 = ((char*)header) + offset; // regions_0 = &header->_regions[index];
    // return ((char*)regions_0) + index * sizeof(CDSFileMapRegion);
    long offset = regionsField.getOffset();
    Address regions_0 = header.addOffsetTo(offset);
    return regions_0.addOffsetTo(index * regionsField.getSize());
  }

  private static void initialize(TypeDataBase db) {
    vTableTypeMap = null; // force vTableTypeMap to get re-initialized later

    Type FileMapInfo_type = db.lookupType("FileMapInfo");
    Type FileMapHeader_type = db.lookupType("FileMapHeader");
    Type CDSFileMapRegion_type = db.lookupType("CDSFileMapRegion");

    // FileMapInfo * info = FileMapInfo::_current_info;
    // FileMapHeader* header = info->_header
    Address info = getStatic_AddressField(FileMapInfo_type, "_current_info");
    Address header = get_AddressField(FileMapInfo_type, info, "_header");
    headerObj = VMObjectFactory.newObject(FileMapHeader.class, header);

    // char* mapped_base_address = header->_mapped_base_address
    // size_t cloned_vtable_offset = header->_cloned_vtable_offset
    // CppVtableInfo** vtablesIndex = mapped_base_address + cloned_vtable_offset;
    mapped_base_address = get_AddressField(FileMapHeader_type, header, "_mapped_base_address");
    long cloned_vtable_offset = get_CIntegerField(FileMapHeader_type, header, "_cloned_vtables_offset");
    vtablesIndex = mapped_base_address.addOffsetTo(cloned_vtable_offset);

    // CDSFileMapRegion* rw_region = &header->_region[rw];
    // char* rwRegionBaseAddress = rw_region->_mapped_base;
    // size_t used = rw_region->_used;
    // char* rwRegionEndAddress = rwRegionBaseAddress + used;
    Address rw_region = get_CDSFileMapRegion(FileMapHeader_type, header, 0);
    rwRegionBaseAddress = get_AddressField(CDSFileMapRegion_type, rw_region, "_mapped_base");
    long used = get_CIntegerField(CDSFileMapRegion_type, rw_region, "_used");
    rwRegionEndAddress = rwRegionBaseAddress.addOffsetTo(used);

    populateMetadataTypeArray(db);
  }

  private static void populateMetadataTypeArray(TypeDataBase db) {
    metadataTypeArray = new Type[9];

    metadataTypeArray[0] = db.lookupType("ConstantPool");
    metadataTypeArray[1] = db.lookupType("InstanceKlass");
    metadataTypeArray[2] = db.lookupType("InstanceClassLoaderKlass");
    metadataTypeArray[3] = db.lookupType("InstanceMirrorKlass");
    metadataTypeArray[4] = db.lookupType("InstanceRefKlass");
    metadataTypeArray[5] = db.lookupType("InstanceStackChunkKlass");
    metadataTypeArray[6] = db.lookupType("Method");
    metadataTypeArray[7] = db.lookupType("ObjArrayKlass");
    metadataTypeArray[8] = db.lookupType("TypeArrayKlass");
  }

  public FileMapHeader getHeader() {
    return headerObj;
  }

  public boolean inCopiedVtableSpace(Address vptrAddress) {
    FileMapHeader fmHeader = getHeader();
    return fmHeader.inCopiedVtableSpace(vptrAddress);
  }

  public Type getTypeForVptrAddress(Address vptrAddress) {
    if (vTableTypeMap == null) {
      getHeader().createVtableTypeMapping();
    }
    return vTableTypeMap.get(vptrAddress);
  }


  //------------------------------------------------------------------------------------------

  public static class FileMapHeader extends VMObject {

    public FileMapHeader(Address addr) {
      super(addr);
    }

    public boolean inCopiedVtableSpace(Address vptrAddress) {
      if (vptrAddress == null) {
        return false;
      }
      if (vptrAddress.greaterThan(rwRegionBaseAddress) &&
          vptrAddress.lessThanOrEqual(rwRegionEndAddress)) {
        return true;
      }
      return false;
    }

    public void createVtableTypeMapping() {
      vTableTypeMap = new HashMap<Address, Type>();
      long addressSize = VM.getVM().getAddressSize();

      // vtablesIndex points to to an array like this:
      // long info[] = {
      //   offset of the CppVtableInfo for ConstantPool,
      //   offset of the CppVtableInfo for InstanceKlass,
      //   offset of the CppVtableInfo for InstanceClassLoaderKlass,
      //   ...
      // };
      //
      // class CppVtableInfo {
      //   intptr_t _vtable_size;
      //   intptr_t _cloned_vtable[1];
      //   ...
      // };
      //
      // The loop below computes the following
      //     CppVtableInfo* t_ConstantPool  = mapped_base_address + info[0];
      //     CppVtableInfo* t_InstanceKlass = mapped_base_address + info[1];
      //     ...
      //
      // If we have the following objects
      //     ConstantPool* cp = ....; // an archived constant pool
      //     InstanceKlass* ik = ....;// an archived class
      //
      // then the following holds true:
      //     ((intptr_t**)cp)[0] == &t_ConstantPool->_cloned_vtable[0]  // The vtable for archived ConstantPools
      //     ((intptr_t**)ik)[0] == &t_InstanceKlass->_cloned_vtable[0] // The vtable for archived InstanceKlasses
      //
      // To get an idea what these address look like, do this:
      //
      // $ java -Xlog:cds+vtables=debug -XX:+UnlockDiagnosticVMOptions -XX:ArchiveRelocationMode=0 --version
      // [0.002s][debug][cds,vtables] Copying  14 vtable entries for ConstantPool to 0x800000018
      // [0.002s][debug][cds,vtables] Copying  41 vtable entries for InstanceKlass to 0x800000090
      // [0.002s][debug][cds,vtables] Copying  41 vtable entries for InstanceClassLoaderKlass to 0x8000001e0
      // [0.002s][debug][cds,vtables] Copying  41 vtable entries for InstanceMirrorKlass to 0x800000330
      // [0.002s][debug][cds,vtables] Copying  41 vtable entries for InstanceRefKlass to 0x800000480
      // [0.002s][debug][cds,vtables] Copying  41 vtable entries for InstanceStackChunkKlass to 0x8000005d0
      // [0.002s][debug][cds,vtables] Copying  14 vtable entries for Method to 0x800000720
      // [0.002s][debug][cds,vtables] Copying  42 vtable entries for ObjArrayKlass to 0x800000798
      // [0.002s][debug][cds,vtables] Copying  42 vtable entries for TypeArrayKlass to 0x8000008f0
      // java 23-internal 2024-09-17
      // ...

      for (int i=0; i < metadataTypeArray.length; i++) {
        long vtable_offset = vtablesIndex.getJLongAt(i * addressSize); // long offset = _index[i]

        // CppVtableInfo* t = the address of the CppVtableInfo for the i-th table
        Address vtableInfoAddress = mapped_base_address.addOffsetTo(vtable_offset);

        // vtableAddress = &t->_cloned_vtable[0]
        Address vtableAddress = vtableInfoAddress.addOffsetTo(addressSize);

        vTableTypeMap.put(vtableAddress, metadataTypeArray[i]);
      }
    }
  }
}
