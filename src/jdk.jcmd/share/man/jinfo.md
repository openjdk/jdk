---
# Copyright (c) 2004, 2024, Oracle and/or its affiliates. All rights reserved.
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

title: 'JINFO(1) JDK @@VERSION_SHORT@@ | JDK Commands'
date: @@COPYRIGHT_YEAR@@
lang: en
---

## Name

jinfo - generate Java configuration information for a specified Java process

## Synopsis

**Note:** This command is experimental and unsupported.

`jinfo` \[*option*\] *pid*

*option*
:   This represents the `jinfo` command-line options. See [Options for the
    jinfo Command].

*pid*
:   The process ID for which the configuration information is to be printed.
    The process must be a Java process. To get a list of Java processes running
    on a machine, use either the `ps` command or, if the JVM processes are not
    running in a separate docker instance, the [jps](jps.html) command.

## Description

The `jinfo` command prints Java configuration information for a specified Java
process. The configuration information includes Java system properties and JVM
command-line flags. If the specified process is running on a 64-bit JVM, then
you might need to specify the `-J-d64` option, for example:

>   `jinfo -J-d64 -sysprops` *pid*

This command is unsupported and might not be available in future releases of
the JDK. In Windows Systems where `dbgeng.dll` is not present, the Debugging
Tools for Windows must be installed to have these tools work. The `PATH`
environment variable should contain the location of the `jvm.dll` that's used
by the target process or the location from which the core dump file was
produced.

## Options for the jinfo Command

**Note:**

If none of the following options are used, both the command-line flags and the
system property name-value pairs are printed.

`-flag` *name*
:   Prints the name and value of the specified command-line flag.

`-flag` \[`+`\|`-`\]*name*
:   Enables or disables the specified Boolean command-line flag.

`-flag` *name*`=`*value*
:   Sets the specified command-line flag to the specified value.

`-flags`
:   Prints command-line flags passed to the JVM.

`-sysprops`
:   Prints Java system properties as name-value pairs.

`-h` or `-help`
:   Prints a help message.
