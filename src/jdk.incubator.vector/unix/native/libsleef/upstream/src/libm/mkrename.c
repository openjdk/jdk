//   Copyright Naoki Shibata and contributors 2010 - 2021.
// Distributed under the Boost Software License, Version 1.0.
//    (See accompanying file LICENSE.txt or copy at
//          http://www.boost.org/LICENSE_1_0.txt)

#include <stdio.h>
#include <stdlib.h>
#include <stdint.h>
#include <string.h>

#include "funcproto.h"

// In VSX intrinsics, vector data types are like "vector float".
// This function replaces space characters with '_'.
char *escapeSpace(char *str) {
  char *ret = malloc(strlen(str) + 10);
  strcpy(ret, str);
  for(char *p = ret;*p != '\0';p++) if (*p == ' ') *p = '_';
  return ret;
}

int main(int argc, char **argv) {
  if (argc < 4) {
    fprintf(stderr, "Generate a header for renaming functions\n");
    fprintf(stderr, "Usage : %s <atr prefix> <DP width> <SP width> [<isa>]\n", argv[0]);
    fprintf(stderr, "\n");

    fprintf(stderr, "Generate a part of header for library functions\n");
    fprintf(stderr, "Usage : %s <atr prefix> <DP width> <SP width> <vdouble type> <vfloat type> <vint type> <vint2 type> <Macro to enable> [<isa>]\n", argv[0]);
    fprintf(stderr, "\n");

    exit(-1);
  }

  static char *ulpSuffixStr[] = { "", "_u1", "_u05", "_u35", "_u15", "_u3500" };

  if (argc == 4 || argc == 5) {
    char *atrPrefix = strcmp(argv[1], "-") == 0 ? NULL : argv[1];
    char *wdp = argv[2];
    char *wsp = argv[3];
    char *isaname = argc == 4 ? "" : argv[4];
    char *isaub = argc == 5 ? "_" : "";

    //

    printf("#ifndef DETERMINISTIC\n\n");

    for(int i=0;funcList[i].name != NULL;i++) {
      if (funcList[i].ulp >= 0) {
        printf("#define x%s%s Sleef_%s%sd%s_u%02d%s\n",
               funcList[i].name, ulpSuffixStr[funcList[i].ulpSuffix],
               "", funcList[i].name, wdp,
               funcList[i].ulp, isaname);
        if (atrPrefix != NULL) {
          printf("#define y%s%s Sleef_%s%sd%s_u%02d%s\n",
                 funcList[i].name, ulpSuffixStr[funcList[i].ulpSuffix],
                 atrPrefix, funcList[i].name, wdp,
                 funcList[i].ulp, isaname);
        }
      } else {
        printf("#define x%s Sleef_%s%sd%s%s%s\n",
               funcList[i].name,
               "", funcList[i].name, wdp, isaub, isaname);
        if (atrPrefix != NULL) {
          printf("#define y%s Sleef_%s%sd%s%s%s\n",
                 funcList[i].name,
                 atrPrefix, funcList[i].name, wdp, isaub, isaname);
        }
      }
    }

    printf("\n");

    for(int i=0;funcList[i].name != NULL;i++) {
      if (funcList[i].ulp >= 0) {
        printf("#define x%sf%s Sleef_%s%sf%s_u%02d%s\n",
               funcList[i].name, ulpSuffixStr[funcList[i].ulpSuffix],
               "", funcList[i].name, wsp,
               funcList[i].ulp, isaname);
        if (atrPrefix != NULL) {
          printf("#define y%sf%s Sleef_%s%sf%s_u%02d%s\n",
                 funcList[i].name, ulpSuffixStr[funcList[i].ulpSuffix],
                 atrPrefix, funcList[i].name, wsp,
                 funcList[i].ulp, isaname);
        }
      } else {
        printf("#define x%sf Sleef_%s%sf%s%s%s\n",
               funcList[i].name,
               "", funcList[i].name, wsp, isaub, isaname);
        if (atrPrefix != NULL) {
          printf("#define y%sf Sleef_%s%sf%s%s%s\n",
                 funcList[i].name,
                 atrPrefix, funcList[i].name, wsp, isaub, isaname);
        }
      }
    }

    //

    if (atrPrefix != NULL) {
      printf("\n#else //#ifndef DETERMINISTIC\n\n");

      for(int i=0;funcList[i].name != NULL;i++) {
        if (funcList[i].ulp >= 0) {
          printf("#define x%s%s Sleef_%s%sd%s_u%02d%s\n",
                 funcList[i].name, ulpSuffixStr[funcList[i].ulpSuffix],
                 atrPrefix, funcList[i].name, wdp,
                 funcList[i].ulp, isaname);
        } else {
          printf("#define x%s Sleef_%s%sd%s%s%s\n",
                 funcList[i].name,
                 atrPrefix, funcList[i].name, wdp, isaub, isaname);
        }
      }

      printf("\n");

      for(int i=0;funcList[i].name != NULL;i++) {
        if (funcList[i].ulp >= 0) {
          printf("#define x%sf%s Sleef_%s%sf%s_u%02d%s\n",
                 funcList[i].name, ulpSuffixStr[funcList[i].ulpSuffix],
                 atrPrefix, funcList[i].name, wsp,
                 funcList[i].ulp, isaname);
        } else {
          printf("#define x%sf Sleef_%s%sf%s%s%s\n",
                 funcList[i].name,
                 atrPrefix, funcList[i].name, wsp, isaub, isaname);
        }
      }
    }

    printf("\n#endif // #ifndef DETERMINISTIC\n");
  }
  else {
    char *atrPrefix = strcmp(argv[1], "-") == 0 ? NULL : argv[1];
    char *wdp = argv[2];
    char *wsp = argv[3];
    char *vdoublename = argv[4], *vdoublename_escspace = escapeSpace(vdoublename);
    char *vfloatname = argv[5], *vfloatname_escspace = escapeSpace(vfloatname);
    char *vintname = argv[6], *vintname_escspace = escapeSpace(vintname);
    char *vint2name = argv[7], *vint2name_escspace = escapeSpace(vint2name);
    char *architecture = argv[8];
    char *isaname = argc == 10 ? argv[9] : "";
    char *isaub = argc == 10 ? "_" : "";
    char *str_omp_simd_dp = (argc < 10 && strcmp(wdp, "1") == 0) ? "SLEEF_PRAGMA_OMP_SIMD_DP " : "";
    char *str_omp_simd_sp = (argc < 10 && strcmp(wsp, "1") == 0) ? "SLEEF_PRAGMA_OMP_SIMD_SP " : "";

    if (strcmp(isaname, "sve") == 0)
      wdp = wsp = "x";

    char * vectorcc = "";
#ifdef ENABLE_AAVPCS
    if (strcmp(isaname, "advsimd") == 0)
      vectorcc =" __attribute__((aarch64_vector_pcs))";
#endif

    printf("#ifdef %s\n", architecture);

    if (strcmp(vdoublename, "-") != 0) {
      if (strcmp(vdoublename, "double") != 0) {
        printf("\n");
        printf("#ifndef Sleef_%s_2_DEFINED\n", vdoublename_escspace);
        if (strcmp(architecture, "__ARM_FEATURE_SVE") == 0) {
          printf("typedef svfloat64x2_t Sleef_%s_2;\n", vdoublename_escspace);
        } else {
          printf("typedef struct {\n");
          printf("  %s x, y;\n", vdoublename);
          printf("} Sleef_%s_2;\n", vdoublename_escspace);
        }
        printf("#define Sleef_%s_2_DEFINED\n", vdoublename_escspace);
        printf("#endif\n");
        printf("\n");
      } else {
        printf("\n");
        printf("#ifndef Sleef_double_2_DEFINED\n");
        printf("typedef Sleef_double2 Sleef_double_2;\n");
        printf("#define Sleef_double_2_DEFINED\n");
        printf("#endif\n");
        printf("\n");
      }

      for(int i=0;funcList[i].name != NULL;i++) {
        switch(funcList[i].funcType) {
        case 0:
          if (funcList[i].ulp >= 0) {
            printf("%sSLEEF_IMPORT SLEEF_CONST %s Sleef_%sd%s_u%02d%s(%s)%s;\n",
                   str_omp_simd_dp,
                   vdoublename,
                   funcList[i].name, wdp,
                   funcList[i].ulp, isaname,
                   vdoublename,
                   vectorcc);
            if (atrPrefix != NULL) {
              printf("SLEEF_IMPORT SLEEF_CONST %s Sleef_%s%sd%s_u%02d%s(%s)%s;\n",
                     vdoublename,
                     atrPrefix,
                     funcList[i].name, wdp,
                     funcList[i].ulp, isaname,
                     vdoublename,
                     vectorcc);
            }
          } else {
            printf("%sSLEEF_IMPORT SLEEF_CONST %s Sleef_%sd%s%s%s(%s)%s;\n",
                   str_omp_simd_dp,
                   vdoublename,
                   funcList[i].name, wdp,
                   isaub, isaname,
                   vdoublename,
                   vectorcc);
            if (atrPrefix != NULL) {
              printf("SLEEF_IMPORT SLEEF_CONST %s Sleef_%s%sd%s%s%s(%s)%s;\n",
                     vdoublename,
                     atrPrefix,
                     funcList[i].name, wdp,
                     isaub, isaname,
                     vdoublename,
                     vectorcc);
            }
          }
          break;
        case 1:
          if (funcList[i].ulp >= 0) {
            printf("%sSLEEF_IMPORT SLEEF_CONST %s Sleef_%sd%s_u%02d%s(%s, %s)%s;\n",
                   str_omp_simd_dp,
                   vdoublename,
                   funcList[i].name, wdp,
                   funcList[i].ulp, isaname,
                   vdoublename, vdoublename,
                   vectorcc);
            if (atrPrefix != NULL) {
              printf("SLEEF_IMPORT SLEEF_CONST %s Sleef_%s%sd%s_u%02d%s(%s, %s)%s;\n",
                     vdoublename,
                     atrPrefix, funcList[i].name, wdp,
                     funcList[i].ulp, isaname,
                     vdoublename, vdoublename,
                     vectorcc);
            }
          } else {
            printf("%sSLEEF_IMPORT SLEEF_CONST %s Sleef_%sd%s%s%s(%s, %s)%s;\n",
                   str_omp_simd_dp,
                   vdoublename,
                   funcList[i].name, wdp,
                   isaub, isaname,
                   vdoublename, vdoublename,
                   vectorcc);
            if (atrPrefix != NULL) {
              printf("SLEEF_IMPORT SLEEF_CONST %s Sleef_%s%sd%s%s%s(%s, %s)%s;\n",
                     vdoublename,
                     atrPrefix, funcList[i].name, wdp,
                     isaub, isaname,
                     vdoublename, vdoublename,
                     vectorcc);
            }
          }
          break;
        case 2:
        case 6:
          if (funcList[i].ulp >= 0) {
            printf("SLEEF_IMPORT SLEEF_CONST Sleef_%s_2 Sleef_%sd%s_u%02d%s(%s)%s;\n",
                   vdoublename_escspace,
                   funcList[i].name, wdp,
                   funcList[i].ulp, isaname,
                   vdoublename,
                   vectorcc);
            if (atrPrefix != NULL) {
              printf("SLEEF_IMPORT SLEEF_CONST Sleef_%s_2 Sleef_%s%sd%s_u%02d%s(%s)%s;\n",
                     vdoublename_escspace,
                     atrPrefix, funcList[i].name, wdp,
                     funcList[i].ulp, isaname,
                     vdoublename,
                     vectorcc);
            }
          } else {
            printf("SLEEF_IMPORT SLEEF_CONST Sleef_%s_2 Sleef_%sd%s%s%s(%s)%s;\n",
                   vdoublename_escspace,
                   funcList[i].name, wdp,
                   isaub, isaname,
                   vdoublename,
                   vectorcc);
            if (atrPrefix != NULL) {
              printf("SLEEF_IMPORT SLEEF_CONST Sleef_%s_2 Sleef_%s%sd%s%s%s(%s)%s;\n",
                     vdoublename_escspace,
                     atrPrefix, funcList[i].name, wdp,
                     isaub, isaname,
                     vdoublename,
                     vectorcc);
            }
          }
          break;
        case 3:
          if (funcList[i].ulp >= 0) {
            printf("SLEEF_IMPORT SLEEF_CONST %s Sleef_%sd%s_u%02d%s(%s, %s)%s;\n",
                   vdoublename,
                   funcList[i].name, wdp,
                   funcList[i].ulp, isaname,
                   vdoublename, vintname,
                   vectorcc);
            if (atrPrefix != NULL) {
              printf("SLEEF_IMPORT SLEEF_CONST %s Sleef_%s%sd%s_u%02d%s(%s, %s)%s;\n",
                     vdoublename,
                     atrPrefix, funcList[i].name, wdp,
                     funcList[i].ulp, isaname,
                     vdoublename, vintname,
                     vectorcc);
            }
          } else {
            printf("SLEEF_IMPORT SLEEF_CONST %s Sleef_%sd%s%s%s(%s, %s)%s;\n",
                   vdoublename,
                   funcList[i].name, wdp,
                   isaub, isaname,
                   vdoublename, vintname,
                   vectorcc);
            if (atrPrefix != NULL) {
              printf("SLEEF_IMPORT SLEEF_CONST %s Sleef_%s%sd%s%s%s(%s, %s)%s;\n",
                     vdoublename,
                     atrPrefix, funcList[i].name, wdp,
                     isaub, isaname,
                     vdoublename, vintname,
                     vectorcc);
            }
          }
          break;
        case 4:
          if (funcList[i].ulp >= 0) {
            printf("SLEEF_IMPORT SLEEF_CONST %s Sleef_%sd%s_u%02d%s(%s)%s;\n",
                   vintname,
                   funcList[i].name, wdp,
                   funcList[i].ulp, isaname,
                   vdoublename,
                   vectorcc);
            if (atrPrefix != NULL) {
              printf("SLEEF_IMPORT SLEEF_CONST %s Sleef_%s%sd%s_u%02d%s(%s)%s;\n",
                     vintname,
                     atrPrefix, funcList[i].name, wdp,
                     funcList[i].ulp, isaname,
                     vdoublename,
                     vectorcc);
            }
          } else {
            printf("SLEEF_IMPORT SLEEF_CONST %s Sleef_%sd%s%s%s(%s)%s;\n",
                   vintname,
                   funcList[i].name, wdp,
                   isaub, isaname,
                   vdoublename,
                   vectorcc);
            if (atrPrefix != NULL) {
              printf("SLEEF_IMPORT SLEEF_CONST %s Sleef_%s%sd%s%s%s(%s)%s;\n",
                     vintname,
                     atrPrefix, funcList[i].name, wdp,
                     isaub, isaname,
                     vdoublename,
                     vectorcc);
            }
          }
          break;
        case 5:
          if (funcList[i].ulp >= 0) {
            printf("%sSLEEF_IMPORT SLEEF_CONST %s Sleef_%sd%s_u%02d%s(%s, %s, %s)%s;\n",
                   str_omp_simd_dp,
                   vdoublename,
                   funcList[i].name, wdp,
                   funcList[i].ulp, isaname,
                   vdoublename, vdoublename, vdoublename,
                   vectorcc);
            if (atrPrefix != NULL) {
              printf("SLEEF_IMPORT SLEEF_CONST %s Sleef_%s%sd%s_u%02d%s(%s, %s, %s)%s;\n",
                     vdoublename,
                     atrPrefix, funcList[i].name, wdp,
                     funcList[i].ulp, isaname,
                     vdoublename, vdoublename, vdoublename,
                     vectorcc);
            }
          } else {
            printf("SLEEF_IMPORT SLEEF_CONST %s Sleef_%sd%s%s%s(%s, %s, %s)%s;\n",
                   vdoublename,
                   funcList[i].name, wdp,
                   isaub, isaname,
                   vdoublename, vdoublename, vdoublename,
                   vectorcc);
            if (atrPrefix != NULL) {
              printf("SLEEF_IMPORT SLEEF_CONST %s Sleef_%s%sd%s%s%s(%s, %s, %s)%s;\n",
                     vdoublename,
                     atrPrefix, funcList[i].name, wdp,
                     isaub, isaname,
                     vdoublename, vdoublename, vdoublename,
                     vectorcc);
            }
          }
          break;
          // The two cases below should not use vector calling convention.
          // They do not have vector type as argument or return value.
          // Also, the corresponding definition (`getPtr` and `getInt`) in `sleefsimd*.c`
          // are not defined with `VECTOR_CC`. (Same for single precision case below)
        case 7:
          printf("SLEEF_IMPORT SLEEF_CONST int Sleef_%sd%s%s%s(int);\n",
                 funcList[i].name, wdp, isaub, isaname);
          break;
        case 8:
          printf("SLEEF_IMPORT SLEEF_CONST void *Sleef_%sd%s%s%s(int);\n",
                 funcList[i].name, wdp, isaub, isaname);
          break;
        }
      }
    }

    if (strcmp(vfloatname, "float") != 0) {
      printf("\n");
      printf("#ifndef Sleef_%s_2_DEFINED\n", vfloatname_escspace);
      if (strcmp(architecture, "__ARM_FEATURE_SVE") == 0) {
        printf("typedef svfloat32x2_t Sleef_%s_2;\n", vfloatname_escspace);
      } else {
        printf("typedef struct {\n");
        printf("  %s x, y;\n", vfloatname);
        printf("} Sleef_%s_2;\n", vfloatname_escspace);
      }
      printf("#define Sleef_%s_2_DEFINED\n", vfloatname_escspace);
      printf("#endif\n");
      printf("\n");
    } else {
      printf("\n");
      printf("#ifndef Sleef_float_2_DEFINED\n");
      printf("typedef Sleef_float2 Sleef_float_2;\n");
      printf("#define Sleef_float_2_DEFINED\n");
      printf("#endif\n");
      printf("\n");
    }

    //printf("typedef %s vint2_%s;\n", vint2name, isaname);
    //printf("\n");

    for(int i=0;funcList[i].name != NULL;i++) {
      switch(funcList[i].funcType) {
      case 0:
        if (funcList[i].ulp >= 0) {
          printf("%sSLEEF_IMPORT SLEEF_CONST %s Sleef_%sf%s_u%02d%s(%s)%s;\n",
                 str_omp_simd_sp,
                 vfloatname,
                 funcList[i].name, wsp,
                 funcList[i].ulp, isaname,
                 vfloatname,
                 vectorcc);
          if (atrPrefix != NULL) {
            printf("SLEEF_IMPORT SLEEF_CONST %s Sleef_%s%sf%s_u%02d%s(%s)%s;\n",
                   vfloatname,
                   atrPrefix, funcList[i].name, wsp,
                   funcList[i].ulp, isaname,
                   vfloatname,
                   vectorcc);
          }
        } else {
          printf("%sSLEEF_IMPORT SLEEF_CONST %s Sleef_%sf%s%s%s(%s)%s;\n",
                 str_omp_simd_sp,
                 vfloatname,
                 funcList[i].name, wsp,
                 isaub, isaname,
                 vfloatname,
                 vectorcc);
          if (atrPrefix != NULL) {
            printf("SLEEF_IMPORT SLEEF_CONST %s Sleef_%s%sf%s%s%s(%s)%s;\n",
                   vfloatname,
                   atrPrefix, funcList[i].name, wsp,
                   isaub, isaname,
                   vfloatname,
                   vectorcc);
          }
        }
        break;
      case 1:
        if (funcList[i].ulp >= 0) {
          printf("%sSLEEF_IMPORT SLEEF_CONST %s Sleef_%sf%s_u%02d%s(%s, %s)%s;\n",
                 str_omp_simd_sp,
                 vfloatname,
                 funcList[i].name, wsp,
                 funcList[i].ulp, isaname,
                 vfloatname, vfloatname, vectorcc);
          if (atrPrefix != NULL) {
            printf("SLEEF_IMPORT SLEEF_CONST %s Sleef_%s%sf%s_u%02d%s(%s, %s)%s;\n",
                   vfloatname,
                   atrPrefix, funcList[i].name, wsp,
                   funcList[i].ulp, isaname,
                   vfloatname, vfloatname,
                   vectorcc);
          }
        } else {
          printf("%sSLEEF_IMPORT SLEEF_CONST %s Sleef_%sf%s%s%s(%s, %s)%s;\n",
                 str_omp_simd_sp,
                 vfloatname,
                 funcList[i].name, wsp,
                 isaub, isaname,
                 vfloatname, vfloatname,
                 vectorcc);
          if (atrPrefix != NULL) {
            printf("SLEEF_IMPORT SLEEF_CONST %s Sleef_%s%sf%s%s%s(%s, %s)%s;\n",
                   vfloatname,
                   atrPrefix, funcList[i].name, wsp,
                   isaub, isaname,
                   vfloatname, vfloatname,
                   vectorcc);
          }
        }
        break;
      case 2:
      case 6:
        if (funcList[i].ulp >= 0) {
          printf("SLEEF_IMPORT SLEEF_CONST Sleef_%s_2 Sleef_%sf%s_u%02d%s(%s)%s;\n",
                 vfloatname_escspace,
                 funcList[i].name, wsp,
                 funcList[i].ulp, isaname,
                 vfloatname,
                 vectorcc);
          if (atrPrefix != NULL) {
            printf("SLEEF_IMPORT SLEEF_CONST Sleef_%s_2 Sleef_%s%sf%s_u%02d%s(%s)%s;\n",
                   vfloatname_escspace,
                   atrPrefix, funcList[i].name, wsp,
                   funcList[i].ulp, isaname,
                   vfloatname,
                   vectorcc);
          }
        } else {
          printf("SLEEF_IMPORT SLEEF_CONST Sleef_%s_2 Sleef_%sf%s%s%s(%s)%s;\n",
                 vfloatname_escspace,
                 funcList[i].name, wsp,
                 isaub, isaname,
                 vfloatname,
                 vectorcc);
          if (atrPrefix != NULL) {
            printf("SLEEF_IMPORT SLEEF_CONST Sleef_%s_2 Sleef_%s%sf%s%s%s(%s)%s;\n",
                   vfloatname_escspace,
                   atrPrefix, funcList[i].name, wsp,
                   isaub, isaname,
                   vfloatname,
                   vectorcc);
          }
        }
        break;
        /*
          case 3:
          printf("SLEEF_IMPORT SLEEF_CONST %s Sleef_%sf%d_%s(%s, vint2_%s);\n",
          vfloatname,
          funcList[i].name, wsp,
          isaname,
          vfloatname, isaname);
          break;
          case 4:
          printf("SLEEF_IMPORT SLEEF_CONST vint2_%s Sleef_%sf%d_%s(%s);\n",
          isaname,
          funcList[i].name, wsp,
          isaname,
          vfloatname);
          break;
        */
      case 5:
        if (funcList[i].ulp >= 0) {
          printf("%sSLEEF_IMPORT SLEEF_CONST %s Sleef_%sf%s_u%02d%s(%s, %s, %s)%s;\n",
                 str_omp_simd_sp,
                 vfloatname,
                 funcList[i].name, wsp,
                 funcList[i].ulp, isaname,
                 vfloatname, vfloatname, vfloatname,
                 vectorcc);
          if (atrPrefix != NULL) {
            printf("SLEEF_IMPORT SLEEF_CONST %s Sleef_%s%sf%s_u%02d%s(%s, %s, %s)%s;\n",
                   vfloatname,
                   atrPrefix, funcList[i].name, wsp,
                   funcList[i].ulp, isaname,
                   vfloatname, vfloatname, vfloatname,
                   vectorcc);
          }
        } else {
          printf("%sSLEEF_IMPORT SLEEF_CONST %s Sleef_%sf%s%s%s(%s, %s, %s)%s;\n",
                 str_omp_simd_sp,
                 vfloatname,
                 funcList[i].name, wsp,
                 isaub, isaname,
                 vfloatname, vfloatname, vfloatname,
                 vectorcc);
          if (atrPrefix != NULL) {
            printf("SLEEF_IMPORT SLEEF_CONST %s Sleef_%s%sf%s%s%s(%s, %s, %s)%s;\n",
                   vfloatname,
                   atrPrefix, funcList[i].name, wsp,
                   isaub, isaname,
                   vfloatname, vfloatname, vfloatname,
                   vectorcc);
          }
        }
        break;
        // The two cases below should not use vector calling convention.
        // See comments for double precision case above.
      case 7:
        printf("SLEEF_IMPORT SLEEF_CONST int Sleef_%sf%s%s%s(int);\n",
               funcList[i].name, wsp, isaub, isaname);
        if (atrPrefix != NULL) {
          printf("SLEEF_IMPORT SLEEF_CONST int Sleef_%s%sf%s%s%s(int);\n",
                 atrPrefix, funcList[i].name, wsp, isaub, isaname);
        }
        break;
      case 8:
        printf("SLEEF_IMPORT SLEEF_CONST void *Sleef_%sf%s%s%s(int);\n",
               funcList[i].name, wsp, isaub, isaname);
        if (atrPrefix != NULL) {
          printf("SLEEF_IMPORT SLEEF_CONST void *Sleef_%s%sf%s%s%s(int);\n",
                 atrPrefix, funcList[i].name, wsp, isaub, isaname);
        }
        break;
      }
    }

    printf("#endif\n");

    free(vdoublename_escspace);
    free(vfloatname_escspace);
    free(vintname_escspace);
    free(vint2name_escspace);
  }

  exit(0);
}
