---
# Copyright (c) 1997, 2018, Oracle and/or its affiliates. All rights reserved.
#

title: 'RMIREGISTRY(1) JDK @@VERSION_SHORT@@ | JDK Commands'
date: @@COPYRIGHT_YEAR@@
lang: en
---

## Name

rmiregistry - create and start a remote object registry on the specified port
on the current host

## Synopsis

`rmiregistry` \[*options*\] \[*port*\]

*options*
:   This represents the option for the `rmiregistry` command. See
    [Options]

*port*
:   The number of a port on the current host at which to start the remote
    object registry.

## Description

The `rmiregistry` command creates and starts a remote object registry on the
specified port on the current host. If the port is omitted, then the registry
is started on port 1099. The `rmiregistry` command produces no output and is
typically run in the background, for example:

>   `rmiregistry &`

A remote object registry is a bootstrap naming service that's used by RMI
servers on the same host to bind remote objects to names. Clients on local and
remote hosts can then look up remote objects and make remote method
invocations.

The registry is typically used to locate the first remote object on which an
application needs to call methods. That object then provides
application-specific support for finding other objects.

The methods of the `java.rmi.registry.LocateRegistry` class are used to get a
registry operating on the local host or local host and port.

The URL-based methods of the `java.rmi.Naming` class operate on a registry and
can be used to:

-   Bind the specified name to a remote object

-   Return an array of the names bound in the registry

-   Return a reference, a stub, for the remote object associated with the
    specified name

-   Rebind the specified name to a new remote object

-   Destroy the binding for the specified name that's associated with a remote
    object

## Options

`-J`*option*
:   Used with any Java option to pass the *option* following the `-J` (no
    spaces between the `-J` and the option) to the Java interpreter.
