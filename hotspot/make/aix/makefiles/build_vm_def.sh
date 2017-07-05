#!/bin/sh

# If we're cross compiling use that path for nm
if [ "$CROSS_COMPILE_ARCH" != "" ]; then 
NM=$ALT_COMPILER_PATH/nm
else
# On AIX we have to prevent that we pick up the 'nm' version from the GNU binutils
# which may be installed under /opt/freeware/bin. So better use an absolute path here! 
NM=/usr/bin/nm
fi

$NM -X64 -B -C $* \
    | awk '{
              if (($2="d" || $2="D") && ($3 ~ /^__vft/ || $3 ~ /^gHotSpotVM/)) print "\t" $3 ";"
              if ($3 ~ /^UseSharedSpaces$/) print "\t" $3 ";"
              if ($3 ~ /^SharedArchivePath__9Arguments$/) print "\t" $3 ";"
          }' \
    | sort -u
