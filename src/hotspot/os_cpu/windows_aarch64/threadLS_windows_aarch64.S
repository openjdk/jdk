;
; Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
; DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
;
; This code is free software; you can redistribute it and/or modify it
; under the terms of the GNU General Public License version 2 only, as
; published by the Free Software Foundation.
;
; This code is distributed in the hope that it will be useful, but WITHOUT
; ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
; FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
; version 2 for more details (a copy is included in the LICENSE file that
; accompanied this code).
;
; You should have received a copy of the GNU General Public License version
; 2 along with this work; if not, write to the Free Software Foundation,
; Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
;
; Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
; or visit www.oracle.com if you need additional information or have any
; questions.
;

    ; JavaThread::aarch64_get_thread_helper()
    ;
    ; Optimized TLS access to `Thread::_thr_current` on Windows AArch64.
    ; Returns the current thread pointer in x0, clobbers x1, while all other
    ; registers are preserved.

    IMPORT _tls_index
    IMPORT _jvm_thr_current_tls_offset

    AREA threadls_text, CODE, READONLY
    ALIGN 4

    ; MSVC-decorated name for: static Thread* JavaThread::aarch64_get_thread_helper()
    EXPORT |?aarch64_get_thread_helper@JavaThread@@SAPEAVThread@@XZ|

|?aarch64_get_thread_helper@JavaThread@@SAPEAVThread@@XZ| PROC

    ; x18 holds the TEB, 0x58 is a well-defined offset into the TEB on 64-bit
    ; systems, so the following line loads the thread-local storage pointer
    ; inside the TEB
    ldr     x1, [x18, #0x58]

    ; Load `_tls_index` and zero-extend it to 64 bits to occupy x0
    adrp    x0, _tls_index
    ldr     w0, [x0, _tls_index]

    ; `x0` holds the index, `x1` holds the array base address (each entry is 64
    ; bits long), so in the following line, x1 = array_base[_tls_index]
    ldr     x1, [x1, x0, lsl #3]

    ; Load cached TLS offset of `Thread::_thr_current`
    adrp    x0, _jvm_thr_current_tls_offset
    ldr     x0, [x0, _jvm_thr_current_tls_offset]

    ; Load `Thread::_thr_current` value
    ldr     x0, [x1, x0]

    ret

    ENDP
    END
