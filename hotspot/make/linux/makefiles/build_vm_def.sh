#!/bin/sh

# If we're cross compiling use that path for nm
if [ "$CROSS_COMPILE_ARCH" != "" ]; then 
NM=$ALT_COMPILER_PATH/nm
else
NM=nm
fi

$NM --defined-only $* \
    | awk '{
              if ($3 ~ /^_ZTV/ || $3 ~ /^gHotSpotVM/) print "\t" $3 ";"
              if ($3 ~ /^UseSharedSpaces$/) print "\t" $3 ";"
              if ($3 ~ /^_ZN9Arguments17SharedArchivePathE$/) print "\t" $3 ";"
          }' \
    | sort -u
