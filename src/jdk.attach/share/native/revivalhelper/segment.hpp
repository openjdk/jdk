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

#ifndef SEGMENT_H
#define SEGMENT_H

#include <cinttypes>
#include <cstdlib>
#include <cstdio>

#define BUFLEN 2048
void write0(int fd, const char* buf); // revival.cpp

/**
 * A Segment describes a memory range, as required by the process revival mechanism.
 * It may have a name, and may describe from what offset in a file its contents can be read.
 */
class Segment {
    public:
        Segment(char* n, void* v, size_t len) :
            name(n), vaddr(v), length(len), file_offset(0), file_length(0) {}

        Segment(char* n, void* v, size_t len, size_t offset, size_t file_len) :
            name(n), vaddr(v), length(len), file_offset(offset), file_length(file_len) {}

        Segment(void* v, size_t len, size_t offset, size_t file_len) :
            name(nullptr), vaddr(v), length(len), file_offset(offset), file_length(file_len) {}

        Segment(Segment* s) :
            name(s->name), vaddr(s->vaddr), length(s->length), file_offset(s->file_offset), file_length(s->file_length) {}

        Segment() :
            name(nullptr), vaddr(0), length(0), file_offset(0), file_length(0) {}

        char*  name;
        void*  vaddr;
        size_t length;
        size_t file_offset;
        size_t file_length;

        uint64_t start() { return (uint64_t) vaddr; }
        uint64_t end() { return (uint64_t) vaddr + length; }
        void set_end(uint64_t addr) { length = addr - (uint64_t) vaddr; }
        void set_length(uint64_t len) { length = len; file_length = len; }
        void move_start(long dist);

        bool contains(Segment* seg);
        bool contains(uint64_t addr);
        bool is_relevant(); // Is Segment trivially ignorable, e.g. zero-length.

        int write_mapping(int fd, const char* type);
        int toString(char* buf, int len);
};
#endif /* SEGMENT_H */
