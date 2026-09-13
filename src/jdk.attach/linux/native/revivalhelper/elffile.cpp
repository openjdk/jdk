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

#include <cassert>
#include <elf.h>
#include <errno.h>
#include <stdio.h>
#include <sys/mman.h>
#include <sys/types.h>

#include "revival.hpp"
#include "elffile.hpp"

/**
 * Return bool for whether a Program Header is obviously unnecessary.
 * We have Segment::is_relevant() but can avoid getting as far as creating a Segment.
 */
bool is_unwanted_phdr(Elf64_Phdr* phdr) {
    return (phdr->p_memsz == 0 || phdr->p_filesz == 0);
}

bool is_inside(Elf64_Addr from, Elf64_Addr x, Elf64_Addr to) {
    return from <= x && x < to;
}

bool is_inside(Elf64_Phdr* phdr, Elf64_Addr start, Elf64_Addr end) {
    return is_inside(start, phdr->p_vaddr, end)
           || is_inside(start, phdr->p_vaddr + phdr->p_memsz, end);
}

bool should_relocate_addend(Elf64_Rela* rela) {
    switch (ELF64_R_TYPE(rela->r_info)) {
#if defined(__aarch64__)
        case R_AARCH64_RELATIVE:
#else
        case R_X86_64_RELATIVE:
#endif
            return true;
        default:
            return false;
    }
}

bool should_relocate_program_header(Elf64_Phdr* phdr) {
    return phdr->p_type != PT_GNU_STACK;
}

bool should_relocate_dynamic_tag(Elf64_Dyn* dyn) {
    switch (dyn->d_tag) {
        case DT_INIT:
        case DT_FINI:
        case DT_HASH:
        case DT_GNU_HASH:
        case DT_STRTAB:
        case DT_SYMTAB:
        case DT_PLTGOT:
        case DT_JMPREL:
        case DT_RELA:
        case DT_VERDEF:
        case DT_VERNEED:
        case DT_VERSYM:
            return true;
        default:
            return false;
    }
}

bool verify_header(Elf64_Ehdr* hdr) {
    return (hdr->e_ident[0] == 0x7f
        && hdr->e_ident[1] == 'E'
        && hdr->e_ident[2] == 'L'
        && hdr->e_ident[3] == 'F');
}

bool verify_file(int fd) {
    Elf64_Ehdr hdr;
    int e = read(fd, &hdr, sizeof(Elf64_Ehdr));
    if (e == sizeof(Elf64_Ehdr)) {
        return verify_header(&hdr);
    } else {
        return false;
    }
}

bool ELFFile::verify() {
    if (!verify_header(hdr)) {
        warn("%s: ELF signature not recognised.", filename);
        return false;
    }
#if defined(__aarch64__)
    if (hdr->e_machine != EM_AARCH64) {
        warn("%s: not an AARCH64 ELF file.", filename);
        return false;
    }
#else
    if (hdr->e_machine != EM_X86_64) {
        warn("%s: not an X86_64 ELF file.", filename);
        return false;
    }
#endif
    if (hdr->e_phnum == PN_XNUM) {
        warn("%s: Too many program headers, handling not implemented (%x)", filename, hdr->e_phnum);
        return false;
    }
    if (hdr->e_type == ET_DYN && hdr->e_shnum == 0) {
        warn("%s: No section headers in shared library.", filename);
        return false;
    }
    return true;
}

ELFFile::ELFFile(const char* filename, const char* libdirs, bool write) {
    logd("ELFFile: %s (write = %d)", filename, write);
    this->filename = filename;
    this->libdirs = libdirs;
    fd = -1;
    length = 0;
    m = nullptr;
    hdr = nullptr;

    // Open for writing if requested, we may be relocating:
    int oflags = write ? O_RDWR : O_RDONLY;
    fd = open(filename, oflags);
    if (fd < 0) {
        warn("Cannot open '%s': %s", filename, strerror(errno));
        return;
    }
    length = file_size(filename);

    int prot = write ? (PROT_READ | PROT_WRITE) : PROT_READ;
    int flags = MAP_SHARED;
    m = mmap(0, length, prot, flags, fd, 0);
    if (m == (void*) -1) {
        warn("ELFFile: mmap of ELF file '%s' failed: %s", filename, strerror(errno));
        m = nullptr;
        return; // invalid ELF file
    }
    hdr = (Elf64_Ehdr*) m;
    if (!verify()) {
        warn("Not an ELF file: %s", filename);
        m = nullptr;
        return; // invalid ELF file
    }

    // Set absolute ph and sh pointers for ease.
    // Careful with ptr arithmetic, do NOT use:
    // = (Elf64_Phdr*) (char*) m + (hdr->e_phoff);
    // Use:
    ph = (Elf64_Phdr*) ((char*) m + (hdr->e_phoff));

    if (hdr->e_shoff > 0) {
        sh = (Elf64_Shdr*) ((char*) m + (hdr->e_shoff));
        Elf64_Shdr* strndx_shdr = section_by_index(hdr->e_shstrndx);
        shdr_strings = (char*) m + strndx_shdr->sh_offset;
    } else {
        sh = nullptr; // cores don't usually have Sections
    }

    logd("ELFFile: %s hdr = %p phoff = 0x%lx sh_off = 0x%lx   ph = %p sh = %p",
         filename, hdr, hdr->e_phoff, hdr->e_shoff, ph, sh);
}

// static
bool ELFFile::is_elf(const char* filename) {
    bool result = false;
    if (file_exists_pd(filename)) {
        int fd = open(filename, O_RDONLY);
        if (fd >= 0) {
            result = verify_file(fd);
            close(fd);
        }
    }
    return result;
}

bool ELFFile::is_valid() {
    return fd >= 0 &&  m != nullptr;
}

bool ELFFile::is_core() {
    return is_valid() && hdr != nullptr && hdr->e_type == ET_CORE;
}

bool ELFFile::is_sharedlib() {
    return is_valid() && hdr != nullptr && hdr->e_type == ET_DYN;
    // To further distinguish from a PIE executable, an executable will have a PT_INTERP PH.
}

ELFFile::~ELFFile() {
    if (fd >= 0) {
        ::close(fd);
    }
    if (m != nullptr) {
        int e = munmap(m, length);
        if (e != 0) {
           warn("ELFFile: munmap failed: 0x%lx: %s", (uint64_t) m, strerror(errno));
        }
        m = nullptr;
    }
    hdr = nullptr;
}

// Section header actual address in mmapped file.
Elf64_Shdr* ELFFile::section_header(unsigned long i) {
    return (Elf64_Shdr*) ((char*) sh + (i * hdr->e_shentsize));
}

// Program header actual address in mmapped file.
Elf64_Phdr* ELFFile::program_header(unsigned long i) {
    return (Elf64_Phdr*) ((char*) ph + (i * hdr->e_phentsize));
}

bool ELFFile::section_name_is(Elf64_Shdr* shdr, const char* name) {
    return (strcmp(name, shdr_strings + shdr->sh_name) == 0);
}

Elf64_Shdr* ELFFile::next_sh(Elf64_Shdr* s) {
    return (Elf64_Shdr*) ((char*) s + hdr->e_shentsize);
}

Elf64_Phdr* ELFFile::next_ph(Elf64_Phdr* p) {
   return (Elf64_Phdr*) ((char*) p + hdr->e_phentsize);
}

Elf64_Shdr* ELFFile::section_by_name(const char* name) {
    Elf64_Shdr* s = sh;
    for (int i = 0; i < hdr->e_shnum; i++) {
        if (section_name_is(s, name)) {
            return s;
        }
        s = next_sh(s);
    }
    warn("Section not found: %s", name);
    return nullptr;
}

Elf64_Shdr* ELFFile::section_by_index(unsigned long index) {
    return (Elf64_Shdr*) ((char*) sh + (index * hdr->e_shentsize));
}

Elf64_Phdr* ELFFile::program_header_by_type(Elf32_Word type) {
    Elf64_Phdr* phdr = ph;
    for (int i = 0; i < hdr->e_phnum; i++) {
        if (phdr->p_type == type) {
            return phdr;
        }
        phdr = next_ph(phdr);
    }
    return nullptr;
}

void ELFFile::relocate_header(long displacement) {
    if (hdr->e_entry != 0) {
        hdr->e_entry += displacement;
    }
}

void ELFFile::relocate_program_headers(long displacement) {
    Elf64_Phdr* p = ph;
    for (int i = 0; i < hdr->e_phnum; i++) {
        logd("relocate_program_headers %3d %p", i, p);
        if (should_relocate_program_header(p)) {
            p->p_vaddr += displacement;
            p->p_paddr += displacement;
#ifdef __aarch64__
            p->p_align = 0x1000;
#endif
        }
        p = next_ph(p);
    }
}

bool ELFFile::should_relocate_section_header(Elf64_Shdr* shdr) {
    if (section_name_is(shdr, ".comment")) return false;
    if (section_name_is(shdr, ".note.stapsdt")) return false;
    if (section_name_is(shdr, ".note.gnu.gold-version")) return false;
    if (section_name_is(shdr, ".gnu_debuglink")) return false;
    if (section_name_is(shdr, ".symtab")) return false;
    if (section_name_is(shdr, ".shstrtab")) return false;
    if (section_name_is(shdr, ".strtab")) return false;
    if (shdr->sh_type == SHT_NULL) return false;
    return true;
}

void ELFFile::relocate_section_headers(long displacement) {
    Elf64_Shdr* s = sh;
    for (int i = 0; i < hdr->e_shnum; i++) {
        logd("relocate_section_headers %3d %p", i, s);
        if (should_relocate_section_header(s)) {
            s->sh_addr += displacement;
        }
        s = next_sh(s);
    }
}

void ELFFile::relocate_relocation_table(long displacement, const char* name) {
    Elf64_Shdr* sh_reladyn = section_by_name(name);
    if (sh_reladyn == nullptr) {
        return;
    }
    for (unsigned long o = sh_reladyn->sh_offset; o < (sh_reladyn->sh_offset + sh_reladyn->sh_size); o += sh_reladyn->sh_entsize) {
        Elf64_Rela* rela = (Elf64_Rela*) ((uint64_t) m + o);
        rela->r_offset += displacement;
        if (should_relocate_addend(rela)) {
            rela->r_addend += displacement;
        }
    }
}

uint64_t ELFFile::find_dynamic_value(Elf64_Shdr* s, int tag) {
    for (unsigned long o = s->sh_offset; o < s->sh_offset + s->sh_size; o += s->sh_entsize) {
        Elf64_Dyn* dyn = (Elf64_Dyn*) ((uint64_t) m + o);
        if (dyn->d_tag == DT_NULL) {
            break;
        }
        if (dyn->d_tag == tag) {
            return dyn->d_un.d_val;
        }
    }
    return 0;
}

/**
 * Relocate e.g. INIT_ARRAY contents.
 */
void ELFFile::relocate_dyn_array(long displacement, Elf64_Dyn* dyn, int count) {
    logd("relocate_dyn_array: updating %d", count);
    // Get our mmapped address of the array:
    uint64_t *p = (uint64_t*) ((uint64_t) m + dyn->d_un.d_ptr);
    // Relocate contents:
    for (int i = 0; i < count; i++) {
        if (*p != 0) {
            uint64_t contents = *(uint64_t*) p;
            uint64_t newval = contents + displacement;
            *p = newval;
        }
        p++;
    }
    // Adjust dynamic table entry:
    dyn->d_un.d_ptr += displacement;
}

void ELFFile::relocate_dynamic_table(long displacement) {
    Elf64_Shdr* s = section_by_name(".dynamic");
    if (s == nullptr) {
        return;
    }
    for (unsigned long o = s->sh_offset; o < s->sh_offset + s->sh_size; o += s->sh_entsize) {
        Elf64_Dyn* dyn = (Elf64_Dyn*) ((uint64_t) m + o);
        if (dyn->d_tag == DT_NULL) {
            break;
        }
        // Special-case for the .init array contents:
        if (dyn->d_tag == DT_INIT_ARRAY) {
            int count = find_dynamic_value(s, DT_INIT_ARRAYSZ) / sizeof(uint64_t);
            relocate_dyn_array(displacement, dyn, count);
        } else if (dyn->d_tag == DT_FINI_ARRAY) {
            int count = find_dynamic_value(s, DT_FINI_ARRAYSZ) / sizeof(uint64_t);
            relocate_dyn_array(displacement, dyn, count);
        } else if (should_relocate_dynamic_tag(dyn)) {
           dyn->d_un.d_ptr += displacement;
        }
    }
}

bool ELFFile::should_relocate_symbol(Elf64_Sym* sym) {
    if (ELF64_ST_TYPE(sym->st_info) == STT_TLS) return false;
    if (sym->st_shndx == 0) return false;
    if (sym->st_shndx == SHN_ABS) return false;
    return true;
}

void ELFFile::relocate_symbol_table(long displacement, const char* name) {
    Elf64_Shdr* s = section_by_name(name);
    if (s == nullptr) {
        return;
    }
    for (unsigned long o = s->sh_offset; o < s->sh_offset + s->sh_size; o += s->sh_entsize) {
        Elf64_Sym* s = (Elf64_Sym*) ((uint64_t) m + o);
        if (should_relocate_symbol(s)) {
            s->st_value += displacement;
        }
    }
}

void ELFFile::read_bytes_at(unsigned long at, ssize_t bytes, char* buffer) {
    if (lseek(fd, at, SEEK_SET) == -1) {
        error("read_bytes_at: %s", strerror(errno));
    }
    if (read(fd, buffer, bytes) != bytes) {
        error("read_bytes_at: %s", strerror(errno));
    }
}

void ELFFile::read_bytes(ssize_t bytes, char* buffer) {
    if (read(fd, buffer, bytes) != bytes) {
        error("read_bytes: %s", strerror(errno));
    }
}

char* ELFFile::find_note_data(Elf64_Phdr* notes_ph, Elf64_Word type) {
    // Read NOTES.  p_filesz is limit.
    Elf64_Nhdr* nhdr = (Elf64_Nhdr*) ((uint64_t) m + notes_ph->p_offset);
    Elf64_Nhdr* end  = nhdr + notes_ph->p_filesz;

    while (nhdr < end) {
        logd("NOTE at %p type 0x%x namesz %x descsz %x", nhdr, nhdr->n_type, nhdr->n_namesz, nhdr->n_descsz);
        char* pos = (char*) nhdr;
        pos += sizeof(Elf64_Nhdr);
        if (nhdr->n_namesz > 0) {
            char* name = (char*) pos;
            logd("NOTE name='%s'", name);
            pos += nhdr->n_namesz; // n_namesz includes terminator
        }
        // Align. 4 byte alignment, including when 64-bit.
        while (((unsigned long long) pos & 0x3) != 0) {
            pos++;
        }
        // After aligning, pos points at actual NOTE data.
        if (nhdr->n_type == type) {
            return pos;
        }
        pos += nhdr->n_descsz;
        nhdr = (Elf64_Nhdr*) pos;
    }
    return nullptr;
}

// Return the filename to use in our sharedlib list, for a name from NT_FILE info.
// Handle any name filtering, and return nullptr if file should not be included in library list.
// Handle substitution from the libdirs list.
char* file_name_for_nt_file(char* name, const char* libdirs) {
    // Filter on name from the NT_FILE list.
    // Keep all files in NT_FILE list, not just ELF sharedlibs.
    // But not classes.jsa or other ".jsa" file, we need the core file data for that.
    // In a transported core actual files are not available, so cannot check file type.

    char* p = strrchr(name, '.');
    if (p != nullptr && strcmp(p, ".jsa") == 0) {
        return nullptr;
    }
    // Ignore " (deleted)" which can be at the end of a path.
    char* name2 = nullptr;
    if (strlen(name) > 10) {
        p = strrchr(name, ' ');
        if (p != nullptr && strcmp(p, " (deleted)") == 0) {
            int len = strlen(name) - 10;
            name2 = (char*) malloc(len + 1);
            strncpy(name2, name, len);
            name2[len] = 0;
            name = name2;
        }
    }

    if (libdirs != nullptr) {
        char* alt_name = find_filename_in_libdirs(libdirs, name);
        if (alt_name != nullptr) {
            logv("Using from libdirs: '%s'", alt_name);
            name = alt_name; // heap allocated
            if (name2 != nullptr) {
                free(name2);
            }
        }
    }
    return name;
}

/**
 * Read file mapping list from the NT_FILE NOTE in a core file.
 */
void ELFFile::read_file_mappings() {
    if (!is_core()) {
        error("read_file_mappings: Not a core file: %s", filename);
    }
    if (file_mappings.size() != 0) return;

    // Look for Program Header PT_NOTE:
    Elf64_Phdr* notes_ph = program_header_by_type(PT_NOTE);
    if (notes_ph == nullptr) {
        error("read_file_mappings: Cannot locate PT_NOTE in %s", filename);
    }
    // Look for NT_FILE note:
    char* note_nt_file = find_note_data(notes_ph, 0x46494c45 /* NT_FILE */);
    if (note_nt_file == nullptr) {
        error("read_file_mappings: Cannot locate NOTE NT_FILE in %s", filename);
    }
    logv("NT_FILE note data at %p", note_nt_file);

    // Read NT_FILE content:
    int nt_file_count;
    nt_file_count = *(long*) note_nt_file;
    note_nt_file += 8;
    long pagesize = *(long*) note_nt_file;
    note_nt_file += 8;
    logd("NT_FILE count %d pagesize 0x%lx", nt_file_count, pagesize);
    if (nt_file_count > 65536) {
        // Arbitrary number but have a sanity check and warn if data looks strange.
        warn("NT_FILE extreme file mapping count %d pagesize 0x%lx", nt_file_count, pagesize);
    }
    // Read numerical data, then library names.
    // NT_FILE lists can contain multiple entries for the same filename.
    Segment* files = (Segment*) malloc(sizeof(Segment) * nt_file_count);
    for (int i = 0; i < nt_file_count; i++) {
        files[i].vaddr= (void*) *(long*) note_nt_file;
        note_nt_file += 8;
        uint64_t end = *(long*) note_nt_file;
        files[i].length = end - (uint64_t) files[i].vaddr;
        note_nt_file += 8;
        note_nt_file += 8; // skip offset
    }
    for (int i = 0; i < nt_file_count; i++) {
        files[i].name = note_nt_file;
        note_nt_file += strlen(files[i].name);
        note_nt_file++; // terminator
    }

    // Reread that info to build final library list. Use libdirs if set, to rewrite paths.
    // Do not skip duplicate names, as would need to coalesce entries/ranges for same filename.
    // Lookups will get first match, and want to find base address, so all good.
    for (int i = 0; i < nt_file_count; i++) {
        logd("NT_FILE: 0x%lx - 0x%lx %s", (uint64_t) files[i].vaddr, (uint64_t) files[i].end(), files[i].name);
        Segment lib = files[i];
        char* name = file_name_for_nt_file(lib.name, libdirs);
        if (name != nullptr) {
            if (name != lib.name) {
                // name changed, is now heap allocated
            }
            Segment seg(name, lib.vaddr, lib.length);
            file_mappings.push_back(seg);
        }
    }

    logd("file_mappings size = %ld", file_mappings.size());
    free(files);
}

uint64_t ELFFile::file_offset_for_vaddr(uint64_t addr) {
    // Locate PT_LOAD for this address.
    Elf64_Phdr* phdr = ph;
    for (int i = 0; i < hdr->e_phnum; i++) {
        if (phdr->p_type == PT_LOAD) {
            if (addr >= phdr->p_vaddr && addr < (phdr->p_vaddr + phdr->p_memsz)) {
                return phdr->p_offset + (addr - phdr->p_vaddr);
            }
        }
        phdr = next_ph(phdr);
    }
    return (uint64_t) -1;
}

char* ELFFile::readstring_at_address(uint64_t addr) {
    uint64_t offset = this->file_offset_for_vaddr(addr);
    if (offset == (uint64_t) -1) {
        return nullptr;
    } else {
        return readstring_at_offset_pd(filename, offset);
    }
}

void ELFFile::relocate(long displacement) {
    if (is_core()) {
        error("%s: ELFFile::relocate cannot be called on a core file", filename);
    }
    if (hdr->e_type != ET_DYN) {
        error("%s: ELFFile::relocate needs to be on a ET_DYN file", filename);
    }
    if (sh == nullptr) {
        error("%s: ELFFile::relocate expects Sections", filename);
    }
    if (shdr_strings == 0) {
        error("%s: ELFFile::relocate expects shdr_strings", filename);
    }
    // Check first Program Header has zero vaddr?

    relocate_header(displacement);
    relocate_program_headers(displacement);
    relocate_section_headers(displacement);
    relocate_relocation_table(displacement, ".rela.dyn");
    relocate_relocation_table(displacement, ".rela.plt");
    relocate_dynamic_table(displacement);
    relocate_symbol_table(displacement, ".dynsym");
    relocate_symbol_table(displacement, ".symtab");
}

void ELFFile::write_symbols(int symbols_fd, const char* symbols[], int count) {
    Elf64_Shdr* strtab = section_by_name(".strtab");
    Elf64_Shdr* symtab = section_by_name(".symtab");
    if (strtab == nullptr || symtab == nullptr) {
        return;
    }
    char* SYMTAB_BUFFER = (char*) m + strtab->sh_offset;
    for (long unsigned int i = 0; i < symtab->sh_size / symtab->sh_entsize; i++) {
        Elf64_Sym* sym = (Elf64_Sym*) ((uint64_t) m + (symtab->sh_offset + i * symtab->sh_entsize));

        for (int j = 0; j < count; j++) {
            int ret = strcmp(symbols[j], SYMTAB_BUFFER + sym->st_name);
            if (ret == 0) {
                char buf[BUFLEN];
                int len = snprintf(buf, BUFLEN, "%s %llx\n",
                        SYMTAB_BUFFER + sym->st_name,
                        (unsigned long long) sym->st_value);
                int e = write(symbols_fd, buf, len);
                if (e != len) {
                    warn("write_symbols: write error: %s", strerror(errno));
                }
            }
        }
    }
}

Segment* ELFFile::get_file_mapping(const char* filename) {
    read_file_mappings();
    for (std::list<Segment>::iterator iter = file_mappings.begin(); iter != file_mappings.end(); iter++) {
        if ((char*) iter->name == nullptr) {
            continue; // no name
        }
        if (strstr((char*) iter->name, filename)) {
            // Return it only if it is an ELF file.
            if (ELFFile::is_elf(iter->name)) {
                return new Segment(*iter);
            } else {
                break;
            }
        }
    }
    return nullptr;
}

std::list<Segment> ELFFile::get_file_mappings() {
    return file_mappings;
}

std::list<Segment> ELFFile::get_sharedlib_mappings() {
    if (libs.size() == 0) {
        char* previous = nullptr;
        std::list<Segment>::iterator iter;
        for (iter = file_mappings.begin(); iter != file_mappings.end(); iter++) {
            // Skip duplicate names (uses NT_FILE info which contains duplicate names).
            if (previous == nullptr || strcmp(previous, iter->name) != 0) {
                libs.push_back(*iter);
            }
            previous = iter->name;
        }
    }
    return libs;
}

void ELFFile::write_mem_mappings(int mappings_fd) {
    // For each Program Header, create a Segment, call Segment::write_mapping(int fd) to write an "M" entry.
    // Skip if PH is trivial, i.e. filesize or memsize is zero.
    // Skip if PH is non-writable, and clashes with a library mapping (i.e. we only want library data).
    logv("write_mem_mappings");
    if (!is_core()) {
        warn("write_mem_mappings: Not writing mappings for non-core file: %s", filename);
        return;
    }
    read_file_mappings();

    int n_skipped = 0;
    Elf64_Phdr* phdr = ph;
    for (int i = 0; i < hdr->e_phnum; i++) {
        if (is_unwanted_phdr(phdr)) {
            n_skipped++;
            phdr = next_ph(phdr);
            continue;
        }
        if (phdr->p_vaddr >= max_user_vaddr_pd()) {
            break; // Kernel mapping?  Not something we can map in.  Phdrs are in ascending address order.
        }

        bool skip = false;
        if (!(phdr->p_flags & PF_W)) {
            std::list<Segment>::iterator iter;
            for (iter = file_mappings.begin(); iter != file_mappings.end(); iter++) {
                Segment lib = *iter;
                if (is_inside(phdr, lib.start(), lib.end())) {
                    logv("Skipping core PH 0x%lx - 0x%lx due to overlap with %s", phdr->p_vaddr, phdr->p_vaddr + phdr->p_memsz, lib.name);
                    skip = true;
                    break;
                }
            }
            if (skip) {
                n_skipped++;
                phdr = next_ph(phdr);
                continue;
            }
        }
        Segment s((void*) phdr->p_vaddr, phdr->p_memsz, phdr->p_offset, phdr->p_filesz);
        s.write_mapping(mappings_fd, "M");
        phdr = next_ph(phdr);
    }
    logv("write_mem_mappings done.  Skipped = %i", n_skipped);
}

