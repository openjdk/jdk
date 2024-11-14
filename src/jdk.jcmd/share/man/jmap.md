---
# Copyright (c) 2004, 2018, Oracle and/or its affiliates. All rights reserved.
# DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
#
# This code is free software; you can redistribute it and/or modify it
# under the terms of the GNU General Public License version 2 only, as
# published by the Free Software Foundation.
#
# This code is distributed in the hope that it will be useful, but WITHOUT
# ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
# FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
# version 2 for more details (a copy is included in the LICENSE file that
# accompanied this code).
#
# You should have received a copy of the GNU General Public License version
# 2 along with this work; if not, write to the Free Software Foundation,
# Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
#
# Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
# or visit www.oracle.com if you need additional information or have any
# questions.
#

title: 'JMAP(1) JDK @@VERSION_SHORT@@ | JDK Commands'
date: @@COPYRIGHT_YEAR@@
lang: en
---

## Name

jmap - print details of a specified process

## Synopsis

**Note:** This command is experimental and unsupported.

`jmap` \[*options*\] *pid*

*options*
:   This represents the `jmap` command-line options. See [Options for the jmap
    Command].

*pid*
:   The process ID for which the information specified by the *options* is to
    be printed. The process must be a Java process. To get a list of Java
    processes running on a machine, use either the `ps` command or, if the JVM
    processes are not running in a separate docker instance, the
    [jps](jps.html) command.

## Description

The `jmap` command prints details of a specified running process.

**Note:**

This command is unsupported and might not be available in future releases of
the JDK. On Windows Systems where the `dbgeng.dll` file isn't present, the
Debugging Tools for Windows must be installed to make these tools work. The
`PATH` environment variable should contain the location of the `jvm.dll` file
that's used by the target process or the location from which the core dump file
was produced.

## Options for the jmap Command

`-clstats` *pid*
:   Connects to a running process and prints class loader statistics of Java
    heap.

`-finalizerinfo` *pid*
:   Connects to a running process and prints information on objects awaiting
    finalization.

`-histo`\[`:live`\] *pid*
:   Connects to a running process and prints a histogram of the Java object
    heap. If the `live` suboption is specified, it then counts only live
    objects.

`-dump:`*dump\_options* *pid*
:   Connects to a running process and dumps the Java heap. The *dump\_options*
    include:

    -   `live` --- When specified, dumps only the live objects; if not
        specified, then dumps all objects in the heap.

    -   `format=b` --- Dumps the Java heap in `hprof` binary format

    -   `file=`*filename* --- Dumps the heap to *filename*

    Example: `jmap -dump:live,format=b,file=heap.bin` *pid*
