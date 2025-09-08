//   Copyright Naoki Shibata and contributors 2010 - 2021.
// Distributed under the Boost Software License, Version 1.0.
//    (See accompanying file LICENSE.txt or copy at
//          http://www.boost.org/LICENSE_1_0.txt)

#include <stdio.h>
#include <stdlib.h>
#include <stdint.h>
#include <string.h>

#include "funcproto.h"

int main(int argc, char **argv) {
  if (argc < 5) {
    fprintf(stderr, "Usage : %s <isa> <Mangled ISA> <DP width> <SP width>\n", argv[0]);
    exit(-1);
  }

  char *isaname = argv[1];
  char *mangledisa = argv[2];
  char *wdp = argv[3];
  char *wsp = argv[4];

  // VLA SVE does not set the vector length in the mangled names.
  if (strcmp(isaname, "sve") == 0)
    wdp = wsp = "x";

  static char *ulpSuffixStr[] = { "", "_u1", "_u05", "_u35", "_u15", "_u3500" };
  static char *vparameterStrDP[] = { "v", "vv", "vl8l8", "vv", "v", "vvv", "vl8" };
  static char *vparameterStrSP[] = { "v", "vv", "vl4l4", "vv", "v", "vvv", "vl4" };

  for(int i=0;funcList[i].name != NULL;i++) {
    if ((funcList[i].flags & 1) != 0) continue;
    if ((funcList[i].flags & 2) != 0) continue;
    if (funcList[i].ulp < 0) {
      printf("#define x%s _ZGV%sN%s%s_%s\n", funcList[i].name,
             mangledisa, wdp, vparameterStrDP[funcList[i].funcType], funcList[i].name);
      printf("#define str_x%s \"_ZGV%sN%s%s_%s\"\n", funcList[i].name,
             mangledisa, wdp, vparameterStrDP[funcList[i].funcType], funcList[i].name);
      printf("#define __%s_finite _ZGV%sN%s%s___%s_finite\n", funcList[i].name,
             mangledisa, wdp, vparameterStrDP[funcList[i].funcType], funcList[i].name);
    } else if (funcList[i].ulp < 20) {
      printf("#define x%s%s _ZGV%sN%s%s_%s\n",
             funcList[i].name, ulpSuffixStr[funcList[i].ulpSuffix],
             mangledisa, wdp, vparameterStrDP[funcList[i].funcType], funcList[i].name);
      printf("#define str_x%s%s \"_ZGV%sN%s%s_%s\"\n",
             funcList[i].name, ulpSuffixStr[funcList[i].ulpSuffix],
             mangledisa, wdp, vparameterStrDP[funcList[i].funcType], funcList[i].name);
      printf("#define __%s%s_finite _ZGV%sN%s%s___%s_finite\n",
             funcList[i].name, ulpSuffixStr[funcList[i].ulpSuffix],
             mangledisa, wdp, vparameterStrDP[funcList[i].funcType], funcList[i].name);
    } else {
      printf("#define x%s%s _ZGV%sN%s%s_%s_u%d\n",
             funcList[i].name, ulpSuffixStr[funcList[i].ulpSuffix],
             mangledisa, wdp, vparameterStrDP[funcList[i].funcType], funcList[i].name, funcList[i].ulp);
      printf("#define str_x%s%s \"_ZGV%sN%s%s_%s_u%d\"\n",
             funcList[i].name, ulpSuffixStr[funcList[i].ulpSuffix],
             mangledisa, wdp, vparameterStrDP[funcList[i].funcType], funcList[i].name, funcList[i].ulp);
      printf("#define __%s%s_finite _ZGV%sN%s%s___%s_finite\n",
             funcList[i].name, ulpSuffixStr[funcList[i].ulpSuffix],
             mangledisa, wdp, vparameterStrDP[funcList[i].funcType], funcList[i].name);
    }
  }

  printf("\n");

  for(int i=0;funcList[i].name != NULL;i++) {
    if ((funcList[i].flags & 1) != 0) continue;
    if (funcList[i].ulp < 0) {
      printf("#define x%sf _ZGV%sN%s%s_%sf\n", funcList[i].name,
             mangledisa, wsp, vparameterStrSP[funcList[i].funcType], funcList[i].name);
      printf("#define str_x%sf \"_ZGV%sN%s%s_%sf\"\n", funcList[i].name,
             mangledisa, wsp, vparameterStrSP[funcList[i].funcType], funcList[i].name);
      printf("#define __%sf_finite _ZGV%sN%s%s___%sf_finite\n", funcList[i].name,
             mangledisa, wsp, vparameterStrSP[funcList[i].funcType], funcList[i].name);
    } else if (funcList[i].ulp < 20) {
      printf("#define x%sf%s _ZGV%sN%s%s_%sf\n",
             funcList[i].name, ulpSuffixStr[funcList[i].ulpSuffix],
             mangledisa, wsp, vparameterStrSP[funcList[i].funcType], funcList[i].name);
      printf("#define str_x%sf%s \"_ZGV%sN%s%s_%sf\"\n",
             funcList[i].name, ulpSuffixStr[funcList[i].ulpSuffix],
             mangledisa, wsp, vparameterStrSP[funcList[i].funcType], funcList[i].name);
      printf("#define __%sf%s_finite _ZGV%sN%s%s___%sf_finite\n",
             funcList[i].name, ulpSuffixStr[funcList[i].ulpSuffix],
             mangledisa, wsp, vparameterStrSP[funcList[i].funcType], funcList[i].name);
    } else {
      printf("#define x%sf%s _ZGV%sN%s%s_%sf_u%d\n",
             funcList[i].name, ulpSuffixStr[funcList[i].ulpSuffix],
             mangledisa, wsp, vparameterStrSP[funcList[i].funcType], funcList[i].name, funcList[i].ulp);
      printf("#define str_x%sf%s \"_ZGV%sN%s%s_%sf_u%d\"\n",
             funcList[i].name, ulpSuffixStr[funcList[i].ulpSuffix],
             mangledisa, wsp, vparameterStrSP[funcList[i].funcType], funcList[i].name, funcList[i].ulp);
      printf("#define __%sf%s_finite _ZGV%sN%s%s___%sf_finite\n",
             funcList[i].name, ulpSuffixStr[funcList[i].ulpSuffix],
             mangledisa, wsp, vparameterStrSP[funcList[i].funcType], funcList[i].name);
    }
  }

  exit(0);
}
