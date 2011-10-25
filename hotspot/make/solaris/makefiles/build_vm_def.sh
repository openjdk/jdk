#!/bin/sh

/usr/ccs/bin/nm -p $* \
    | awk '{
          if ($2 == "U") next
          if ($3 ~ /^__1c.*__vtbl_$/ || $3 ~ /^gHotSpotVM/) print "\t" $3 ";"
          if ($3 ~ /^UseSharedSpaces$/) print "\t" $3 ";"
          if ($3 ~ /^__1cJArgumentsRSharedArchivePath_$/) print "\t" $3 ";"
          }' \
    | sort -u
