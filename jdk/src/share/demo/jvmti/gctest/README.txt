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

gctest

This agent library can be used to track garbage collection events.

You can use this agent library as follows:

    java -agentlib:gctest ...  
	  
To get help on the available options try:

    java -agentlib:gctest=help

See ${JAVA_HOME}/demo/jvmti/index.html for help running and building agents.

The Events JVMTI_EVENT_GARBAGE_COLLECTION_START,
JVMTI_EVENT_GARBAGE_COLLECTION_FINISH, and JVMTI_EVENT_OBJECT_FREE 
all have limitations as to what can be called directly inside the 
agent callback functions (e.g. no JNI calls are allowed, and limited 
interface calls can be made). However, by using raw monitors and a separate 
watcher thread, this agent demonstrates how these limitations can be 
easily avoided, allowing the watcher thread to do just about anything
after the JVMTI_EVENT_GARBAGE_COLLECTION_FINISH event.

