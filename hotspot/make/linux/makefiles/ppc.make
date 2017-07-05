#
# Copyright (c) 2004, 2010, Oracle and/or its affiliates. All rights reserved.
# ORACLE PROPRIETARY/CONFIDENTIAL. Use is subject to license terms.
#

# The copied fdlibm routines in sharedRuntimeTrig.o must not be optimized
OPT_CFLAGS/sharedRuntimeTrig.o = $(OPT_CFLAGS/NOOPT)

# Must also specify if CPU is big endian
CFLAGS += -DVM_BIG_ENDIAN

