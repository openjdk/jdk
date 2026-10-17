/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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

#include "pefile.hpp"

PEFile::PEFile(const char* filename) {
    this->filename = filename;
    image = nullptr;
}

PEFile::~PEFile() {
    if (image != nullptr) {
        int e = ImageUnload(image);
        if (e != TRUE) {
            warn("PEFile: ImageUnload %s: error: %d", filename, GetLastError());
        } else {
            logd("PEFile: ImageUnload %s: done", filename);
        }
    }
}

void PEFile::imageLoad() {
    if (image == nullptr) {
        image = ImageLoad(filename, nullptr);
        if (image == nullptr) {
            error("PEFile: %s: ImageLoad error: %d", filename, GetLastError());
        }
    }
}

uint64_t PEFile::file_offset_for_reladdr(uint64_t reladdr) {
    Segment* seg = get_rdata_section();
    uint64_t rdata_vaddr = reladdr - (uint64_t) seg->vaddr;
    if (rdata_vaddr > seg->file_length) {
        warn("PEFile::file_offset_for reladdr: 0x%llx > .rdata size", reladdr);
    }
    uint64_t file_offset = seg->file_offset + rdata_vaddr;
    delete seg;
    return file_offset;
}

// static
bool PEFile::rebase(const char* filename, uint64_t address) {
    ULONG OldImageSize;
    ULONG64 OldImageBase;
    ULONG NewImageSize = (ULONG) address;
    ULONG64 NewImageBase = address;
    BOOL e = ReBaseImage64(filename, nullptr /* SymbolPath */, TRUE /* fReBase */, TRUE /* permit system file */, FALSE /* rebase downwards */,
                           0 /* MaxSize */, &OldImageSize, &OldImageBase, &NewImageSize, &NewImageBase, 0 /* TimeStamp */);
    logv("rebase: OldImageSize 0x%lx  OldImageBase 0x%llx  NewImageSize 0x%lx  NewImageBase 0x%llx",
          OldImageSize, OldImageBase, NewImageSize, NewImageBase);
    if (!e) {
        warn("ReBaseImage64 failed: %d", GetLastError());
        return false;
    }
    if (address == OldImageBase) {
        logv("rebase: Not needed.");
        return true;
    }
    if (NewImageBase == address) {
        logv("rebase: OK");
     } else {
        logv("rebase: reported new base 0x%llx != required 0x%llx (may not mean rebase actually failed)", NewImageBase, address);
    }
    return e;
}

// static
bool PEFile::remove_dynamicbase(const char* filename) {
    // Set DYNAMICBASE:NO in DllCharacteristics of an existing binary.
    HANDLE h = CreateFile(filename, GENERIC_READ | GENERIC_WRITE, 0 /* not shared */, nullptr, OPEN_EXISTING, FILE_ATTRIBUTE_NORMAL, 0);
    if (h == INVALID_HANDLE_VALUE) { error("remove_dynamicbase: CreateFile failure: %d", GetLastError()); }

    HANDLE h2 = CreateFileMapping(h, nullptr, PAGE_READWRITE, 0,0, nullptr);
    if (h == INVALID_HANDLE_VALUE) { error("remove_dynamicbase: CreateFileMapping failure: %d", GetLastError()); }

    LPVOID base = MapViewOfFile(h2, FILE_MAP_READ | FILE_MAP_WRITE, 0,0,0);
    if (base == nullptr) { error("remove_dynamicbase: MapViewOfFile failure: %d", GetLastError()); }

    short magic = *(short*) base;
    if (magic != 0x5a4d /* MZ */) {
        error("remove_dynamicbase: %s: DOS magic not recognized: 0x%x", filename, magic);
    }

    logv("remove_dynamicbase: %s mapped at 0x%llx", filename, (uint64_t) base);
    uint64_t peOffsetAddr = (uint64_t) base + 0x3c;
    ULONG32 peOffset = *(ULONG32*) peOffsetAddr;
    uint64_t peAddr = (uint64_t) base + peOffset;
    logd("remove_dynamicbase: peAddr = 0x%llx", peAddr);

    // At peOffset, is IMAGE_NT_HEADERS32:
    ULONG32 peMagic = *(ULONG32*) peAddr;
    if (peMagic != 0x4550 /* PE */) {
        error("remove_dynamicbase: %s: PE magic not recognized: 0x%x", filename, peMagic);
    }

    PIMAGE_OPTIONAL_HEADER32 optional = (PIMAGE_OPTIONAL_HEADER32) ((uint64_t) peAddr + sizeof(DWORD) + sizeof(IMAGE_FILE_HEADER));
    logd("DllCharacteristics = 0x%llx", optional->DllCharacteristics);
    WORD dllCharacteristics = optional->DllCharacteristics;
    dllCharacteristics = dllCharacteristics & ~IMAGE_DLLCHARACTERISTICS_DYNAMIC_BASE; // Remove bit value of flag.
    logv("remove_dynamicbase: New DllCharacteristics = 0x%llx", dllCharacteristics);
    logd("&optional.DllCharacteristics =  0x%llx", &(optional->DllCharacteristics));
    *(WORD*)(&(optional->DllCharacteristics)) = dllCharacteristics;

    if (!UnmapViewOfFile(base)) {
        error("remove_dynamicbase: UnmapViewOfFile: %d", GetLastError());
    }
    CloseHandle(h2);
    CloseHandle(h);
    return TRUE;
}

bool PEFile::find_data_segs(void* address, Segment** _data, Segment** _rdata) {
    logv("PEFile::find_data_segs");
    imageLoad();

    logv("find_data_segs image: base address 0x%llx", address);
    if (address == nullptr) {
        error("find_data_segs: null base address");
    }
    Segment* data = nullptr;
    Segment* rdata = nullptr;
    // Create a Segment from a Section, and use the next Section to set its end address.
    for (unsigned int i = 0; i < image->NumberOfSections; i++) {
        IMAGE_SECTION_HEADER section = image->Sections[i];
        logv("find_data_segs: image: %s vaddr 0x%llx size 0x%llx Misc.PhysicalAddress 0x%llx PointerToRawData 0x%llx",
            image->Sections[i].Name, image->Sections[i].VirtualAddress, image->Sections[i].SizeOfRawData,
            section.Misc.PhysicalAddress, section.PointerToRawData);

        if (rdata == nullptr && strncmp((char*) image->Sections[i].Name, ".rdata", 8) == 0) {
            rdata = new Segment((void*) (DWORD_PTR) image->Sections[i].VirtualAddress, (size_t) image->Sections[i].SizeOfRawData, 0, 0);
            continue;
        }

        if (data == nullptr && strncmp((char*) image->Sections[i].Name, ".data", 8) == 0) {
            // Set .rdata end:
            if (rdata != nullptr) {
                rdata->set_length(image->Sections[i].VirtualAddress - rdata->start());
            }
            data = new Segment((void*) (DWORD_PTR) image->Sections[i].VirtualAddress, (size_t) image->Sections[i].SizeOfRawData, 0, 0);
            continue;
        }
        if (data != nullptr) {
            // Already read and set Seg, use this section as the end of that Seg.
            data->set_length(image->Sections[i].VirtualAddress - data->start());
            break;
        }
    }

    // Rebase segs to library address:
    rdata = new Segment((void*) ((uint64_t) address + (uint64_t) rdata->start()), rdata->length, 0, 0);
    data = new Segment((void*) ((uint64_t) address + (uint64_t) data->start()), data->length, 0, 0);
    // Save in output params:
    if (_rdata != nullptr) {
        *_rdata = rdata;
    }
    if (_data != nullptr) {
        *_data = data;
    }
    return true;
}

/**
 * Locate the .data section of a PE file.
 * Return a Segment using relative addresses.
 * Returns a new Segment which caller should delete.
 */
Segment* PEFile::get_rdata_section() {
    imageLoad();
    // Create a Segment from .data, and use the next Section to set its end address.
    Segment* seg = nullptr;
    for (unsigned int i = 0; i < image->NumberOfSections; i++) {
        logv("get_rdata_section: Name: %s vaddr 0x%llx size 0x%llx PointerToRawData 0x%llx",
              image->Sections[i].Name, image->Sections[i].VirtualAddress, image->Sections[i].SizeOfRawData, image->Sections[i].PointerToRawData);

        if (strncmp((char*) image->Sections[i].Name, ".rdata", 8) == 0) {
            seg = new Segment((void*) (DWORD_PTR) image->Sections[i].VirtualAddress, (size_t) image->Sections[i].SizeOfRawData,
                              image->Sections[i].PointerToRawData, 0);
            continue;
        }
        if (seg != nullptr) {
            // Already read and set Seg, use this section as the end of that Seg.
            seg->set_length(image->Sections[i].VirtualAddress - seg->start());
            logd("get_rdata_section seg: 0x%llx - 0x%llx ", seg->start(), seg->end());
            break;
        }
    }
    return seg;
}
