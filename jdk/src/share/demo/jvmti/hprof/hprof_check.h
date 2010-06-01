/*
 * Copyright (c) 2005, Oracle and/or its affiliates. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 *   - Redistributions of source code must retain the above copyright
 *     notice, this list of conditions and the following disclaimer.
 *
 *   - Redistributions in binary form must reproduce the above copyright
 *     notice, this list of conditions and the following disclaimer in the
 *     documentation and/or other materials provided with the distribution.
 *
 *   - Neither the name of Oracle nor the names of its
 *     contributors may be used to endorse or promote products derived
 *     from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS
 * IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO,
 * THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR
 * PURPOSE ARE DISCLAIMED.  IN NO EVENT SHALL THE COPYRIGHT OWNER OR
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
 * PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR
 * PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
 * LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

#ifndef HPROF_CHECK_H
#define HPROF_CHECK_H

#define CHECK_FOR_ERROR(condition) \
        ( (condition) ? \
          (void)0 : \
          HPROF_ERROR(JNI_TRUE, #condition) )
#define CHECK_SERIAL_NO(name, sno) \
        CHECK_FOR_ERROR( (sno) >= gdata->name##_serial_number_start  && \
                      (sno) <  gdata->name##_serial_number_counter)
#define CHECK_CLASS_SERIAL_NO(sno) CHECK_SERIAL_NO(class,sno)
#define CHECK_THREAD_SERIAL_NO(sno) CHECK_SERIAL_NO(thread,sno)
#define CHECK_TRACE_SERIAL_NO(sno) CHECK_SERIAL_NO(trace,sno)
#define CHECK_OBJECT_SERIAL_NO(sno) CHECK_SERIAL_NO(object,sno)

void check_binary_file(char *filename);

#endif
