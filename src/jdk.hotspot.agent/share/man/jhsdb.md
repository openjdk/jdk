---
# Copyright (c) 2019, 2024, Oracle and/or its affiliates. All rights reserved.
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

title: 'JHSDB(1) JDK @@VERSION_SHORT@@ | JDK Commands'
date: @@COPYRIGHT_YEAR@@
lang: en
---

## Name

jhsdb - attach to a Java process or launch a postmortem debugger to analyze
the content of a core dump from a crashed Java Virtual Machine (JVM)

## Synopsis

 **WARNING:** The `debugd` subcommand and `--connect` options are deprecated.  They will be removed in a future release.

`jhsdb` `clhsdb` \[`--pid` *pid* \| `--exe` *executable* `--core` *coredump*\]

`jhsdb` `hsdb` \[`--pid` *pid* \| `--exe` *executable* `--core` *coredump*\]

`jhsdb` `debugd` (`--pid` *pid* \| `--exe` *executable* `--core` *coredump*)
\[*options*\]

`jhsdb` `jstack` (`--pid` *pid* \| `--exe` *executable* `--core` *coredump* \|
`--connect` *\[server-id@\]debugd-host*) \[*options*\]

`jhsdb` `jmap` (`--pid` *pid* \| `--exe` *executable* `--core` *coredump* \|
`--connect` *\[server-id@\]debugd-host*) \[*options*\]

`jhsdb` `jinfo` (`--pid` *pid* \| `--exe` *executable* `--core` *coredump* \|
`--connect` *\[server-id@\]debugd-host*) \[*options*\]

`jhsdb` `jsnap` (`--pid` *pid* \| `--exe` *executable* `--core` *coredump* \|
`--connect` *\[server-id@\]debugd-host*) \[*options*\]

*pid*
:   The process ID to which the `jhsdb` tool should attach. The process must be
    a Java process. To get a list of Java processes running on a machine, use
    the `ps` command or, if the JVM processes are not running in a separate
    docker instance, the [jps](jps.html) command.

*executable*
:   The Java executable file from which the core dump was produced.

*coredump*
:   The core file to which the `jhsdb` tool should attach.

*\[server-id@\]debugd-host*
:   An optional server ID and the address of the remote debug server (debugd).

*options*
:   The command-line options for a `jhsdb` mode. See [Options for the debugd Mode],
    [Options for the jstack Mode], [Options for the jmap Mode],
    [Options for the jinfo Mode], and [Options for the jsnap Mode].

**Note:**

Either the *pid* or the pair of *executable* and *core* files or
the *\[server-id@\]debugd-host* must be provided for `debugd`, `jstack`, `jmap`,
`jinfo` and `jsnap` modes.

## Description

You can use the `jhsdb` tool to attach to a Java process or to launch a
postmortem debugger to analyze the content of a core-dump from a crashed Java
Virtual Machine (JVM). This command is experimental and unsupported.

**Note:**

Attaching the `jhsdb` tool to a live process will cause the process to hang and
the process will probably crash when the debugger detaches.

The `jhsdb` tool can be launched in any one of the following modes:

`jhsdb clhsdb`
:   Starts the interactive command-line debugger.

`jhsdb hsdb`
:   Starts the interactive GUI debugger.

`jhsdb debugd`
:   Starts the remote debug server.

`jhsdb jstack`
:   Prints stack and locks information.

`jhsdb jmap`
:   Prints heap information.

`jhsdb jinfo`
:   Prints basic JVM information.

`jhsdb jsnap`
:   Prints performance counter information.

`jhsdb` *command* `--help`
:   Displays the options available for the *command*.

## Options for the debugd Mode

`--serverid` *server-id*
:   An optional unique ID for this debug server. This is required if multiple
    debug servers are run on the same machine.

`--rmiport` *port*
:   Sets the port number to which the RMI connector is bound. If not specified
    a random available port is used.

`--registryport` *port*
:   Sets the RMI registry port. This option overrides the system property
    'sun.jvm.hotspot.rmi.port'. If not specified, the system property is used.
    If the system property is not set, the default port 1099 is used.

`--hostname` *hostname*
:   Sets the hostname the RMI connector is bound. The value could be a hostname
    or an IPv4/IPv6 address. This option overrides the system property
    'java.rmi.server.hostname'. If not specified, the system property is used.
    If the system property is not set, a system hostname is used.

## Options for the jinfo Mode

`--flags`
:   Prints the VM flags.

`--sysprops`
:   Prints the Java system properties.

no option
:   Prints the VM flags and the Java system properties.

## Options for the jmap Mode

no option
:   Prints the same information as Solaris `pmap`.

`--heap`
:   Prints the `java` heap summary.

`--binaryheap`
:   Dumps the `java` heap in `hprof` binary format.

`--dumpfile` *name*
:   The name of the dumpfile.

`--histo`
:   Prints the histogram of `java` object heap.

`--clstats`
:   Prints the class loader statistics.

`--finalizerinfo`
:   Prints the information on objects awaiting finalization.

## Options for the jstack Mode

`--locks`
:   Prints the `java.util.concurrent` locks information.

`--mixed`
:   Attempts to print both `java` and native frames if the platform allows it.

## Options for the jsnap Mode

`--all`
:   Prints all performance counters.
