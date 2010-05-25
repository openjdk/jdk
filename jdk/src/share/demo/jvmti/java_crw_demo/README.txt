#
# Copyright (c) 2004, 2007, Oracle and/or its affiliates. All rights reserved.
#
# Redistribution and use in source and binary forms, with or without
# modification, are permitted provided that the following conditions
# are met:
#
#   - Redistributions of source code must retain the above copyright
#     notice, this list of conditions and the following disclaimer.
#
#   - Redistributions in binary form must reproduce the above copyright
#     notice, this list of conditions and the following disclaimer in the
#     documentation and/or other materials provided with the distribution.
#
#   - Neither the name of Oracle nor the names of its
#     contributors may be used to endorse or promote products derived
#     from this software without specific prior written permission.
#
# THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS
# IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO,
# THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR
# PURPOSE ARE DISCLAIMED.  IN NO EVENT SHALL THE COPYRIGHT OWNER OR
# CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
# EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
# PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR
# PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
# LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
# NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
# SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
#

java_crw_demo Library

The library java_crw_demo is a small C library that is used by HPROF
and other agent libraries to do some very basic bytecode 
insertion (BCI) of class files.  This is not an agent 
library but a general purpose library that can be used to do some 
very limited bytecode insertion.

In the demo sources, look for the use of java_crw_demo.h and
the C function java_crw_demo().  The java_crw_demo library is provided 
as part of the JRE.

The basic BCI that this library does includes:

    * On entry to the java.lang.Object init method (signature "()V"), 
      a invokestatic call to tclass.obj_init_method(object); is inserted. 

    * On any newarray type opcode, immediately following it, the array 
      object is duplicated on the stack and an invokestatic call to
      tclass.newarray_method(object); is inserted. 

    * On entry to all methods, a invokestatic call to 
      tclass.call_method(cnum,mnum); is inserted. The agent can map the 
      two integers (cnum,mnum) to a method in a class, the cnum is the 
      number provided to the java_crw_demo library when the classfile was 
      modified.

    * On return from any method (any return opcode), a invokestatic call to
      tclass.return_method(cnum,mnum); is inserted.  

Some methods are not modified at all, init methods and finalize methods 
whose length is 1 will not be modified.  Classes that are designated 
"system" will not have their clinit methods modified. In addition, the 
method java.lang.Thread.currentThread() is not modified.

No methods or fields will be added to any class, however new constant 
pool entries will be added at the end of the original constant pool table.
The exception, line, and local variable tables for each method is adjusted 
for the modification. The bytecodes are compressed to use smaller offsets 
and the fewest 'wide' opcodes. 

All attempts are made to minimize the number of bytecodes at each insertion 
site, however, classes with N return opcodes or N newarray opcodes will get 
N insertions.  And only the necessary modification dictated by the input 
arguments to java_crw_demo are actually made.

