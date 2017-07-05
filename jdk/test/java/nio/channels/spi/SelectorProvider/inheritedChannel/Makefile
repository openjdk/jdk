#
#
# Makefile for building libLauncher.so
#
# To build libLauncher.so requires :-
#     JAVA_HOME environment variable 
#     cc (Solaris) or gcc (Linux) on PATH
#
# The library is created in a architecture specific directory :-
#
#     lib/solaris-sparc/libLauncher.so	(Solaris/SPARC)
#     lib/solaris-i586/libLauncher.so	(Solaris/x86)
#     lib/linux-i586/libLauncher.so	(Linux/x86)

ECHO  = echo
MKDIR = mkdir
UNAME = uname

uname := $(shell uname)

ifeq ($(uname), SunOS)
  PLATFORM = solaris
  ISAINFO = isainfo
  ifndef ARCH_DATA_MODEL
    ARCH_DATA_MODEL=32
  endif
  ifeq ($(ARCH_DATA_MODEL), 32)
    archExpr = case "`$(UNAME) -p`" in  \
		i386) \
		    $(ECHO) i586 \
		    ;; \
		sparc*)	 \
		    $(ECHO) sparc \
		    ;; \
		*) \
		    $(UNAME) -p	 \
		    ;; \
	     esac
    ARCH		:= $(shell $(archExpr) )
  else
    ARCH := $(shell $(ISAINFO) -n)
  endif
  CC = cc
  LD = ld
  CFLAGS = -D_REENTRANT -D__solaris__
  LDFLAGS_COMMON = -G
  EXTRA_LIBS = -lc
  ifeq ($(ARCH_DATA_MODEL), 64)
      CC += -xarch=v9
  endif
endif

ifeq ($(uname), Linux)
  PLATFORM = linux
  archExpr = case "`$(UNAME) -m`" in  \
		i[3-6]86) \
                    $(ECHO) i586 \
                    ;; \
		sparc*)	 \
                    $(ECHO) sparc \
                    ;; \
		*) \
                    $(UNAME) -m	 \
                    ;; \
	   esac
  ARCH	    := $(shell $(archExpr) )
  CC = gcc
  CFLAGS = -fno-strict-aliasing -fPIC -W -Wall
  LD = ld
  LDFLAGS_COMMON = -shared
  EXTRA_LIBS = -lc
endif

LIBDIR=lib/$(PLATFORM)-$(ARCH)
LAUNCHERLIB=$(LIBDIR)/libLauncher.so

all: java_home $(LAUNCHERLIB)

$(LAUNCHERLIB) : $(LIBDIR) $(LIBDIR)/Launcher.o
	$(LD) $(LDFLAGS_COMMON) -o $(LAUNCHERLIB) $(LIBDIR)/Launcher.o $(EXTRA_LIBS)

$(LIBDIR):
	@$(MKDIR) -p $(LIBDIR)

$(LIBDIR)/Launcher.o : Launcher.c \
		       Launcher.h
	$(CC) -c $(CFLAGS) -o $(LIBDIR)/Launcher.o \
	    -I$(JAVA_HOME)/include -I$(JAVA_HOME)/include/$(PLATFORM) Launcher.c
	
Launcher.h : Launcher.class
	$(JAVA_HOME)/bin/javah -force Launcher

Launcher.class : Launcher.java
	$(JAVA_HOME)/bin/javac Launcher.java

java_home:
ifndef JAVA_HOME
	@$(ECHO) "ERROR: Your JAVA_HOME environment variable is not set."
	exit 1	
endif

