/*
 * Copyright (c) 2018, 2020, Oracle and/or its affiliates. All rights reserved.
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

public class FileMapInfo {
  private static FileMapHeader headerObj;

  // Fields for handling the copied C++ vtables
  private static Address mcRegionBaseAddress;
  private static Address mcRegionEndAddress;
  private static Address vtablesStartAddress;

  // HashMap created by mapping the vTable addresses in the mc region with
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

  // C equivalent:   return &header->_space[index];
  static Address get_CDSFileMapRegion(Type FileMapHeader_type, Address header, int index) {
    AddressField spaceField = FileMapHeader_type.getAddressField("_space[0]");

    // size_t offset = offsetof(FileMapHeader, _space[0]);
    // CDSFileMapRegion* space_0 = ((char*)header) + offset; // space_0 = &header->_space[index];
    // return ((char*)space_0) + index * sizeof(CDSFileMapRegion);
    long offset = spaceField.getOffset();
    Address space_0 = header.addOffsetTo(offset);
    return space_0.addOffsetTo(index * spaceField.getSize());
  }

  private static void initialize(TypeDataBase db) {
    Type FileMapInfo_type = db.lookupType("FileMapInfo");
    Type FileMapHeader_type = db.lookupType("FileMapHeader");
    Type CDSFileMapRegion_type = db.lookupType("CDSFileMapRegion");

    // FileMapInfo * info = FileMapInfo::_current_info;
    // FileMapHeader* header = info->_header
    Address info = getStatic_AddressField(FileMapInfo_type, "_current_info");
    Address header = get_AddressField(FileMapInfo_type, info, "_header");
    headerObj = (FileMapHeader) VMObjectFactory.newObject(FileMapInfo.FileMapHeader.class, header);

    // char* mapped_base_address = header->_mapped_base_address
    // size_t cloned_vtable_offset = header->_cloned_vtable_offset
    // char* vtablesStartAddress = mapped_base_address + cloned_vtable_offset;
    Address mapped_base_address = get_AddressField(FileMapHeader_type, header, "_mapped_base_address");
    long cloned_vtable_offset = get_CIntegerField(FileMapHeader_type, header, "_cloned_vtables_offset");
    vtablesStartAddress = mapped_base_address.addOffsetTo(cloned_vtable_offset);

    // CDSFileMapRegion* mc_space = &header->_space[mc];
    // char* mcRegionBaseAddress = mc_space->_mapped_base;
    // size_t used = mc_space->_used;
    // char* mcRegionEndAddress = mcRegionBaseAddress + used;
    Address mc_space = get_CDSFileMapRegion(FileMapHeader_type, header, 0);
    mcRegionBaseAddress = get_AddressField(CDSFileMapRegion_type, mc_space, "_mapped_base");
    long used = get_CIntegerField(CDSFileMapRegion_type, mc_space, "_used");
    mcRegionEndAddress = mcRegionBaseAddress.addOffsetTo(used);

    populateMetadataTypeArray(db);
  }

  private static void populateMetadataTypeArray(TypeDataBase db) {
    metadataTypeArray = new Type[8];

    metadataTypeArray[0] = db.lookupType("ConstantPool");
    metadataTypeArray[1] = db.lookupType("InstanceKlass");
    metadataTypeArray[2] = db.lookupType("InstanceClassLoaderKlass");
    metadataTypeArray[3] = db.lookupType("InstanceMirrorKlass");
    metadataTypeArray[4] = db.lookupType("InstanceRefKlass");
    metadataTypeArray[5] = db.lookupType("Method");
    metadataTypeArray[6] = db.lookupType("ObjArrayKlass");
    metadataTypeArray[7] = db.lookupType("TypeArrayKlass");
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
      if (vptrAddress.greaterThan(mcRegionBaseAddress) &&
          vptrAddress.lessThanOrEqual(mcRegionEndAddress)) {
        return true;
      }
      return false;
    }

    public void createVtableTypeMapping() {
      vTableTypeMap = new HashMap<Address, Type>();
      long metadataVTableSize = 0;
      long addressSize = VM.getVM().getAddressSize();

      Address copiedVtableAddress = vtablesStartAddress;
      for (int i=0; i < metadataTypeArray.length; i++) {
        // The first entry denotes the vtable size.
        metadataVTableSize = copiedVtableAddress.getAddressAt(0).asLongValue();
        vTableTypeMap.put(copiedVtableAddress.addOffsetTo(addressSize), metadataTypeArray[i]);

        // The '+ 1' below is to skip the entry containing the size of this metadata's vtable.
        copiedVtableAddress =
          copiedVtableAddress.addOffsetTo((metadataVTableSize + 1) * addressSize);
      }
    }
  }
}
