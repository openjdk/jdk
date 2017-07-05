/*
 * Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.
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
 */

package jdk.tools.jaotc.jnilibelf.test;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;

import jdk.tools.jaotc.jnilibelf.JNIELFContainer;
import jdk.tools.jaotc.jnilibelf.JNILibELFAPI.ELF;
import jdk.tools.jaotc.jnilibelf.JNILibELFAPI.LibELF.Elf_Cmd;
import jdk.tools.jaotc.jnilibelf.JNILibELFAPI.LibELF.Elf_Type;

public class JNILibELFTest {

    public static void main(String[] args) {
        // if (args.length != 2) {
        // System.out.println("Please provide file-name as argument");
        // return;
        // }
        createSharedLib();
    }

    private static boolean createSharedLib() {

        int numProgHdrs = 1;
        JNIELFContainer elfContainer = new JNIELFContainer("ELF");

        // Allocate ELF Header
        elfContainer.createELFHeader(ELF.ET_DYN);

        // Allocate 'numProgHdrs' program headers

        if (!elfContainer.createProgramHeader(numProgHdrs)) {
            System.out.println("Failed to create Program Headers");
            return false;
        }

        // Hash table content
        int[] bhashWords = {0x01234567, 0x89abcdef, 0xdeadc0de};
        // int[] data = { 100, 200, 300, 400 };

        ByteBuffer byteBuffer = ByteBuffer.allocate(bhashWords.length * 4);
        IntBuffer intBuffer = byteBuffer.asIntBuffer();
        intBuffer.put(bhashWords);

        // byte[] int_hash_array = byteBuffer.array();

        // Hash Table content
        // ByteBuffer hash_words = ByteBuffer.allocate(14).putInt(0x01234567);
        // hash_words.putInt(0x89abcdef);
        // hash_words.putInt(0xdeadc0de);

        // Create a hash section
        // Setting sh_link as 0 since this is just a demo - the value should actually be the section
        // header index
        // of the symbol table to which the hash table applies.
        int index = elfContainer.createSection(".hash", byteBuffer.array(), Elf_Type.ELF_T_WORD, 4, ELF.SHT_HASH, ELF.SHF_ALLOC, 0, 0);
        if (index == 0) {
            System.out.println("Failed to create hash section");
            return false;
        }

        elfContainer.createSection(".strtab", elfContainer.getStrTabContent().getBytes(), Elf_Type.ELF_T_BYTE, 1, ELF.SHT_STRTAB, (ELF.SHF_STRINGS | ELF.SHF_ALLOC), ELF.SHN_UNDEF, 0);
        // Now, finally, after creating all sections, create shstrtab section
        elfContainer.createSection(".shstrtab", elfContainer.getShStrTabContent().getBytes(), Elf_Type.ELF_T_BYTE, 1, ELF.SHT_STRTAB, 0, ELF.SHN_UNDEF, 0);
        // Run elf_update
        elfContainer.elfUpdate(Elf_Cmd.ELF_C_NULL);

        // Set program header type to self
        elfContainer.setProgHdrTypeToSelf();
        // Setting pheader to self type also sets it to be dirty. So run elfUpdate again
        // to write it out.
        elfContainer.elfUpdate(Elf_Cmd.ELF_C_WRITE);
        // Finish ELF processing
        elfContainer.elfEnd();
        return true;
    }
}
