/*
 * Copyright 2000-2005 Sun Microsystems, Inc.  All Rights Reserved.
 * Copyright 2007, 2008 Red Hat, Inc.
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
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 *
 */

//
// Set the default values for platform dependent flags used by the
// runtime system.  See globals.hpp for details of what they do.
//

define_pd_global(bool,  DontYieldALot,           false);
#ifdef _LP64
define_pd_global(intx,  ThreadStackSize,         1536);
define_pd_global(intx,  VMThreadStackSize,       1024);
#else
define_pd_global(intx,  ThreadStackSize,         1024);
define_pd_global(intx,  VMThreadStackSize,       512);
#endif // _LP64
define_pd_global(intx,  SurvivorRatio,           8);
define_pd_global(intx,  CompilerThreadStackSize, 0);
define_pd_global(uintx, JVMInvokeMethodSlack,    8192);

define_pd_global(bool,  UseVectoredExceptions,   false);
// Only used on 64 bit platforms
define_pd_global(uintx, HeapBaseMinAddress,      2*G);
