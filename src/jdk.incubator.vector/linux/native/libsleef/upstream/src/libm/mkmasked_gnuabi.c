//   Copyright Naoki Shibata and contributors 2010 - 2021.
// Distributed under the Boost Software License, Version 1.0.
//    (See accompanying file LICENSE.txt or copy at
//          http://www.boost.org/LICENSE_1_0.txt)

#include <stdio.h>
#include <stdlib.h>
#include <stdint.h>
#include <string.h>
#include <assert.h>

#include "funcproto.h"

int main(int argc, char **argv) {
  if (argc < 4) {
    fprintf(stderr, "\nUsage : %s <isa> <Mangled ISA> <Vector width>\n\n", argv[0]);
    fprintf(stderr, "This program generates an include file defining masked functions.\n");
    exit(-1);
  }

  //

  const char *isaname = argv[1];
  const char *mangledisa = argv[2];
  const int vw = atoi(argv[3]);
  int fptype = 0;

  // Remove the "-" sign in the SP <Vector width> value
  const char *cvw = (vw < 0)  ? argv[3] + 1 : argv[3];

  if (vw < 0) {
    fptype = 1;
  }

  // VLA SVE does not set the vector length in the mangled names.
  if (strcmp(isaname, "sve") == 0)
    cvw = "x";

  //

#define LEN0 16
#define LEN1 256

  static char *vfpname[] = { "vdouble", "vfloat" };
  static char *vintname[] = { "vint", "vint2" };
  static int sizeoffp[] = { 8, 4 };

  static char vparameterStr[7][LEN0] = { "v", "vv", "vl8l8", "vv", "v", "vvv", "vl8" };
  static char *typeSpecS[] = { "", "f" };
  static char funcname[4][LEN1];

  snprintf(vparameterStr[2], LEN0, "vl%dl%d", sizeoffp[fptype], sizeoffp[fptype]);
  snprintf(vparameterStr[6], LEN0, "vl%d", sizeoffp[fptype]);

  //

  for(int i=0;funcList[i].name != NULL;i++) {
    if ((funcList[i].flags & 1) != 0) continue;
    if (fptype == 0 && (funcList[i].flags & 2) != 0) continue;
    if (funcList[i].ulp < 20) {
      snprintf(funcname[0], LEN1, "_ZGV%sN%s%s_%s%s",
               mangledisa, cvw, vparameterStr[funcList[i].funcType], funcList[i].name, typeSpecS[fptype]);
      snprintf(funcname[1], LEN1, "_ZGV%sM%s%s_%s%s",
               mangledisa, cvw, vparameterStr[funcList[i].funcType], funcList[i].name, typeSpecS[fptype]);
    } else {
      snprintf(funcname[0], LEN1, "_ZGV%sN%s%s_%s%s_u%d",
               mangledisa, cvw, vparameterStr[funcList[i].funcType], funcList[i].name, typeSpecS[fptype], funcList[i].ulp);
      snprintf(funcname[1], LEN1, "_ZGV%sM%s%s_%s%s_u%d",
               mangledisa, cvw, vparameterStr[funcList[i].funcType], funcList[i].name, typeSpecS[fptype], funcList[i].ulp);
    }

    snprintf(funcname[2], LEN1, "_ZGV%sN%s%s___%s%s_finite",
             mangledisa, cvw, vparameterStr[funcList[i].funcType], funcList[i].name, typeSpecS[fptype]);
    snprintf(funcname[3], LEN1, "_ZGV%sM%s%s___%s%s_finite",
             mangledisa, cvw, vparameterStr[funcList[i].funcType], funcList[i].name, typeSpecS[fptype]);

    switch(funcList[i].funcType) {
    case 0: {
      printf("EXPORT CONST %s %s(%s a0, vopmask m) { return %s(a0); }\n",
             vfpname[fptype], funcname[1], vfpname[fptype], funcname[0]);

      if (funcList[i].ulp < 20)
        printf("EXPORT CONST %s %s(%s, vopmask) __attribute__((weak, alias(\"%s\")));\n",
               vfpname[fptype], funcname[3], vfpname[fptype], funcname[1]);
      else
        printf("EXPORT CONST %s %s_u%d(%s, vopmask) __attribute__((weak, alias(\"%s\")));\n",
               vfpname[fptype], funcname[3],funcList[i].ulp, vfpname[fptype], funcname[1]);
      break;
    }
    case 1: {
      printf("EXPORT CONST %s %s(%s a0, %s a1, vopmask m) { return %s(a0, a1); }\n",
             vfpname[fptype], funcname[1], vfpname[fptype], vfpname[fptype], funcname[0]);
      if (funcList[i].ulp < 20)
        printf("EXPORT CONST %s %s(%s, %s, vopmask) __attribute__((weak, alias(\"%s\")));\n",
               vfpname[fptype], funcname[3], vfpname[fptype], vfpname[fptype], funcname[1]);
      else
        printf("EXPORT CONST %s %s_u%d(%s, %s, vopmask) __attribute__((weak, alias(\"%s\")));\n",
               vfpname[fptype], funcname[3],funcList[i].ulp, vfpname[fptype], vfpname[fptype], funcname[1]);
      break;
    }
    case 2:
      if (sizeoffp[fptype] == sizeof(double)) {
        printf("#ifndef ENABLE_SVE\n");
        printf("EXPORT void %s(vdouble a0, double *a1, double *a2, vopmask m) {\n", funcname[1]);
        printf("  double s[VECTLENDP], c[VECTLENDP];\n");
        printf("  int32_t mbuf[VECTLENSP];\n");
        printf("  %s(a0, s, c);\n", funcname[0]);
        printf("  vstoreu_v_p_vi2(mbuf, vcast_vi2_vm(vand_vm_vo64_vm(m, vcast_vm_i_i(-1, -1))));\n");
        printf("  for(int i=0;i<VECTLENDP;i++) {\n");
        printf("    if (mbuf[i*2]) { *a1++ = s[i]; *a2++ = c[i]; }\n");
        printf("  }\n");
        printf("}\n");
        printf("#else /* ENABLE_SVE */\n");
        const char * function = "EXPORT void %s(vdouble a0, double *a1, double *a2, vopmask m) { "
          "  return %s(a0, a1, a2); "
          "}\n";
        printf(function, funcname[1], funcname[0]);
        printf("#endif/* ENABLE_SVE */\n");
      } else if (sizeoffp[fptype] == sizeof(float)) {
        printf("#ifndef ENABLE_SVE\n");
        printf("EXPORT void %s(vfloat a0, float *a1, float *a2, vopmask m) {\n", funcname[1]);
        printf("  float s[VECTLENSP], c[VECTLENSP];\n");
        printf("  int32_t mbuf[VECTLENSP];\n");
        printf("  %s(a0, s, c);\n", funcname[0]);
        printf("  vstoreu_v_p_vi2(mbuf, vcast_vi2_vm(vand_vm_vo32_vm(m, vcast_vm_i_i(-1, -1))));\n");
        printf("  for(int i=0;i<VECTLENSP;i++) {\n");
        printf("    if (mbuf[i]) { *a1++ = s[i]; *a2++ = c[i]; }\n");
        printf("  }\n");
        printf("}\n");
        printf("#else /* ENABLE_SVE */\n");
        const char * function = "EXPORT void %s(vfloat a0, float *a1, float *a2, vopmask m) { "
          "  return %s(a0, a1, a2); "
          "}\n";
        printf(function, funcname[1], funcname[0]);
        printf("#endif/* ENABLE_SVE */\n");
      } else {
        assert(0 && "Invalid size of FP data");
      }
      break;
    case 3:
      printf("EXPORT CONST %s %s(%s a0, %s a1, vopmask m) { return %s(a0, a1); }\n",
             vfpname[fptype], funcname[1], vfpname[fptype], vintname[fptype], funcname[0]);
      break;
    case 4:
      printf("EXPORT CONST %s %s(%s a0, vopmask m) { return %s(a0); }\n",
             vintname[fptype], funcname[1], vfpname[fptype], funcname[0]);
      break;
    case 5:
      printf("EXPORT CONST %s %s(%s a0, %s a1, %s a2, vopmask m) { return %s(a0, a1, a2); }\n",
             vfpname[fptype], funcname[1], vfpname[fptype], vfpname[fptype], vfpname[fptype],
             funcname[0]);
      break;
    case 6: {
      char *ptr_type = NULL;

      if (sizeoffp[fptype] == sizeof(double))
        ptr_type = "double *";
      if (sizeoffp[fptype] == sizeof(float))
        ptr_type = "float *";

      printf("EXPORT CONST %s %s(%s a0, %s a1, vopmask m) { return %s(a0, a1); }\n",
             vfpname[fptype], funcname[1], vfpname[fptype], ptr_type, funcname[0]);

      if (funcList[i].ulp < 20)
        printf("EXPORT CONST %s %s(%s, %s, vopmask) __attribute__((weak, alias(\"%s\")));\n",
               vfpname[fptype], funcname[3], vfpname[fptype], ptr_type, funcname[1]);
      else
        printf("EXPORT CONST %s %s_u%d(%s, %s, vopmask) __attribute__((weak, alias(\"%s\")));\n",
               vfpname[fptype], funcname[3],funcList[i].ulp, vfpname[fptype], ptr_type, funcname[1]);
    }
      break;
    }
  }

  exit(0);
}
