#
# This makefile must be executed on a system with makedepend, such as Solaris.
# In my copious amount of spare time, I hope to write a Java-based makedepend
# to eliminate this dependency (no pun intended). TB

BUILD_DIR = ../..
TOPDIR   = ../../..

STUBDIR = WindowsSystemHeaderStubs
BUILDSTUBDIR = BuildStubs

SHARE_SRC = $(TOPDIR)/src/share
SUN_SRC = $(SHARE_SRC)/native/sun
COMP_SRC = $(SUN_SRC)/awt/alphacomposite
DEBUG_SRC = $(SUN_SRC)/awt/debug
IMG_SRC = $(SUN_SRC)/awt/image
MEDIA_SRC = $(SUN_SRC)/awt/medialib
J2D_SRC  = $(SUN_SRC)/java2d
J2D_FONT_SRC = $(SUN_SRC)/font
J2D_WINDOWS_SRC = $(WINDOWS_SRC)/native/sun/java2d
LOOP_SRC = $(SUN_SRC)/java2d/loops
PIPE_SRC = $(SUN_SRC)/java2d/pipe
WINDOWS_SRC = $(TOPDIR)/src/windows
SRC = $(WINDOWS_SRC)/native/sun/windows

SRCDIRS = \
	$(COMP_SRC) \
	$(DEBUG_SRC) \
	$(IMG_SRC) \
	$(IMG_SRC)/cvutils \
	$(IMG_SRC)/gif \
	$(MEDIA_SRC) \
	$(J2D_SRC) \
	$(J2D_FONT_SRC) \
	$(J2D_SRC)/opengl \
	$(J2D_WINDOWS_SRC)/windows \
	$(J2D_WINDOWS_SRC)/d3d \
	$(J2D_WINDOWS_SRC)/opengl \
	$(LOOP_SRC) \
	$(PIPE_SRC) \
	$(SRC)

INCLUDES = \
	   -I$(STUBDIR) \
	   -I$(BUILDSTUBDIR) \
	   -I$(SHARE_SRC)/javavm/export \
	   -I$(WINDOWS_SRC)/javavm/export \
	   -I$(SHARE_SRC)/native/common \
	   -I$(WINDOWS_SRC)/native/common \
	   -I$(SUN_SRC)/dc/doe \
	   -I$(SUN_SRC)/dc/path \
	   -I$(COMP_SRC) \
	   -I$(DEBUG_SRC) \
	   -I$(IMG_SRC) \
	   -I$(IMG_SRC)/cvutils \
	   -I$(MEDIA_SRC) \
	   -I$(J2D_SRC) \
	   -I$(J2D_FONT_SRC) \
	   -I$(J2D_SRC)/opengl \
	   -I$(J2D_WINDOWS_SRC) \
	   -I$(J2D_WINDOWS_SRC)/windows \
	   -I$(J2D_WINDOWS_SRC)/d3d \
	   -I$(J2D_WINDOWS_SRC)/opengl \
	   -I$(LOOP_SRC) \
	   -I$(PIPE_SRC) \
	   -I$(SRC)

STUBFILES = \
	$(STUBDIR)/ddraw.h \
	$(STUBDIR)/d3d.h \
	$(STUBDIR)/Ole2.h \
	$(STUBDIR)/Zmouse.h \
	$(STUBDIR)/cderr.h \
	$(STUBDIR)/commdlg.h \
	$(STUBDIR)/direct.h \
	$(STUBDIR)/d3dcom.h \
	$(STUBDIR)/imm.h \
	$(STUBDIR)/ime.h \
	$(STUBDIR)/io.h \
	$(STUBDIR)/mmsystem.h \
	$(STUBDIR)/new.h \
	$(STUBDIR)/ole2.h \
	$(STUBDIR)/richole.h \
	$(STUBDIR)/richedit.h \
	$(STUBDIR)/shellapi.h \
	$(STUBDIR)/shlobj.h \
	$(STUBDIR)/tchar.h \
	$(STUBDIR)/winbase.h \
	$(STUBDIR)/windef.h \
	$(STUBDIR)/windows.h \
	$(STUBDIR)/Windows.h \
	$(STUBDIR)/windowsx.h \
	$(STUBDIR)/winspool.h \
	$(STUBDIR)/winuser.h \
	$(STUBDIR)/wtypes.h \
	$(STUBDIR)/zmouse.h \

EXTRAFILES_c = \
	img_colors.c

default: dependencies

include FILES_c_windows.gmk

dependencies:
	rm -rf make.depend
	rm -rf make.tmp make.tmp2 make.tmp.bak
	rm -rf $(STUBDIR) $(BUILDSTUBDIR) depend.filelist
	for file in $(FILES_c) $(FILES_cpp) $(EXTRAFILES_c); do \
	    for dir in $(SRCDIRS); do \
	    	if [ -f $$dir/$$file ]; then \
		    echo $$dir/$$file >>depend.filelist; \
		fi; \
	    	if [ -f $$dir/$${file}pp ]; then \
		    echo $$dir/$${file}pp >>depend.filelist; \
		fi; \
	    done; \
	done
	touch make.tmp
	mkdir $(STUBDIR)
	touch $(STUBFILES)
	mkdir $(BUILDSTUBDIR)
	gnumake -f Depend.mak classhdrstubs
	touch $(BUILDSTUBDIR)/awt_colors.h
	cat depend.filelist | xargs -n 100 makedepend \
		-DWIN32 -D_X86X -Dx86 -DDEBUG -D_MSC_VER -DMLIB_NO_LIBSUNMATH \
                -DUNICODE -D_UNICODE \
		-a -f make.tmp -o.obj $(INCLUDES)
	fgrep .obj make.tmp | sed -f Depend.sed | sort -f -u | nawk -f CondenseRules.awk > make.depend
	rm -rf make.tmp make.tmp2 make.tmp.bak
	rm -rf $(STUBDIR) $(BUILDSTUBDIR) depend.filelist


include FILES_export_windows.gmk

EXTRAFILES_java = \
	java/lang/Integer.java

FILES_java = $(FILES_export) $(FILES_export2) $(FILES_export3) \
	$(EXTRAFILES_java)

classhdrstubs:
	for file in `echo $(FILES_java) | \
		      tr ' ' '\n' | \
		      sed -e 'y/\//_/' -e 's/\.java/.h/'`; do \
	    echo "#include <jni.h>" > $(BUILDSTUBDIR)/$$file; \
	done
