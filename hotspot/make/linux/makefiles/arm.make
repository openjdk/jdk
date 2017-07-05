#
# Copyright (c) 2008, 2011, Oracle and/or its affiliates. All rights reserved.
# ORACLE PROPRIETARY/CONFIDENTIAL. Use is subject to license terms.
#

Obj_Files += linux_arm.o

LIBS += $(EXT_LIBS_PATH)/sflt_glibc.a 

CFLAGS += -DVM_LITTLE_ENDIAN
