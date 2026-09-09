#!/usr/bin/env python3
"""Set NetBSD's "disable mprotect restrictions" PaX flag on an ELF file.

NetBSD refuses to make a mapping both writable and executable when
security.pax.mprotect.global is 1, which every JIT needs.  paxctl(8) marks
a binary as exempt, but it only runs on NetBSD; a cross build has no such
tool.  The .note.netbsd.pax section is already there -- the crt files in
the sysroot carry it -- so the flag is one bit in a note that is present
but empty.
"""
import struct, sys

ELF_NOTE_PAX_NOMPROTECT = 0x02

def set_pax_nomprotect(path):
    with open(path, 'r+b') as f:
        d = bytearray(f.read())
        if d[:4] != b'\x7fELF' or d[4] != 2:      # ELFCLASS64 only
            return None
        little = d[5] == 1
        e = '<' if little else '>'
        e_shoff, = struct.unpack_from(e + 'Q', d, 0x28)
        e_shentsize, e_shnum, e_shstrndx = struct.unpack_from(e + 'HHH', d, 0x3a)
        if e_shoff == 0 or e_shnum == 0:
            return None
        str_off, = struct.unpack_from(e + 'Q', d, e_shoff + e_shstrndx * e_shentsize + 0x18)
        for i in range(e_shnum):
            sh = e_shoff + i * e_shentsize
            sh_name, = struct.unpack_from(e + 'I', d, sh)
            end = d.index(b'\0', str_off + sh_name)
            if d[str_off + sh_name:end] != b'.note.netbsd.pax':
                continue
            sh_offset, = struct.unpack_from(e + 'Q', d, sh + 0x18)
            namesz, descsz, _ = struct.unpack_from(e + 'III', d, sh_offset)
            desc = sh_offset + 12 + ((namesz + 3) & ~3)
            if descsz != 4:
                return None
            flags, = struct.unpack_from(e + 'I', d, desc)
            new = flags | ELF_NOTE_PAX_NOMPROTECT
            if new != flags:
                struct.pack_into(e + 'I', d, desc, new)
                f.seek(0); f.write(d); f.truncate()
            return new
        return None

if __name__ == '__main__':
    n = 0
    for p in sys.argv[1:]:
        r = set_pax_nomprotect(p)
        if r is not None:
            n += 1
        else:
            print(f'{p}: no .note.netbsd.pax', file=sys.stderr)
    print(f'{n} file(s) marked')
