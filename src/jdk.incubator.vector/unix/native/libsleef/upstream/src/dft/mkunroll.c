//   Copyright Naoki Shibata and contributors 2010 - 2025.
// Distributed under the Boost Software License, Version 1.0.
//    (See accompanying file LICENSE.txt or copy at
//          http://www.boost.org/LICENSE_1_0.txt)

#include <stdio.h>
#include <stdlib.h>
#include <stdint.h>
#include <string.h>

#define CONFIGMAX 4

char *replaceAll(const char *in, const char *pat, const char *replace) {
  const int replaceLen = (int)strlen(replace);
  const int patLen = (int)strlen(pat);

  char *str = malloc(strlen(in)+1);
  strcpy(str, in);

  for(;;) {
    char *p = strstr(str, pat);
    if (p == NULL) return str;

    int replace_pos = (int)(p - str);
    int tail_len = (int)strlen(p + patLen);

    char *newstr = malloc(strlen(str) + (replaceLen - patLen) + 1);

    memcpy(newstr, str, replace_pos);
    memcpy(newstr + replace_pos, replace, replaceLen);
    memcpy(newstr + replace_pos + replaceLen, str + replace_pos + patLen, tail_len+1);

    free(str);
    str = newstr;
  }

  return str;
}

#define LEN 1024
char line[LEN+10];

int main(int argc, char **argv) {
  if (argc < 5) {
    fprintf(stderr, "Usage : %s <file name> <Base type> <Base type ID> <shift> <ISA> ...\n", argv[0]);
    exit(-1);
  }

  const char *fn = argv[1];
  const char *baseTypeID = argv[3];
  int shift = atoi(argv[4]);
  const int isastart = 5;
  int mode = 1;
  if (strcmp(argv[4], "-") == 0) {
    mode = 0;
  } else if (shift <= 0) {
    mode = 2;
    shift = -shift;
  }

  char shiftstr[21];
  snprintf(shiftstr, 20, "%d", shift);

  for(int config=0;config<CONFIGMAX;config++) {
#if ENABLE_STREAM == 0
    if ((config & 1) != 0) continue;
#endif
    for(int isa=isastart;isa<argc;isa++) {
      char *isaString = argv[isa];
      char configString[100];
      sprintf(configString, "%d", config);

      FILE *fpin = fopen(fn, "r");

      switch(mode) {
      case 0:
        sprintf(line, "unroll_%d_%s.cpp", config, isaString);
        break;
      case 1:
        sprintf(line, "unroll_%d_%s_%d.cpp", config, isaString, shift);
        break;
      case 2:
        sprintf(line, "unroll2_%d_%s_%d.cpp", config, isaString, shift);
        break;
      }

      FILE *fpout = fopen(line, "w");
      fputs("#include \"vectortype.hpp\"\n\n", fpout);

      for(;;) {
        if (fgets(line, LEN, fpin) == NULL) break;
        char *s;
        if ((config & 1) == 0) {
          char *s0 = replaceAll(line, "%ISA%", isaString);
          s = replaceAll(s0, "%CONFIG%", configString);
          free(s0);
        } else {
          char *s0 = replaceAll(line, "%ISA%", isaString);
          char *s1 = replaceAll(s0, "%CONFIG%", configString);
          char *s2 = replaceAll(s1, "store(", "stream(");
          s = replaceAll(s2, "scatter(", "scstream(");
          free(s0); free(s1); free(s2);
        }

        if ((config & 2) == 0) {
          char *s0 = replaceAll(s, "#pragma", "//pragma");
          char *s1 = replaceAll(s0, "%DEFINE%", "//#define");
          free(s); free(s0);
          s = s1;
        } else {
          char *s0 = replaceAll(s, "%DEFINE%", "#define");
          free(s);
          s = s0;
        }

        if (config == 0) {
          char *s0 = replaceAll(s, "#undef EMITREALSUB", "#define EMITREALSUB");
          free(s);
          s = s0;
        }

        {
          char *s0 = replaceAll(s, "%TYPEID%", baseTypeID);
          free(s);
          s = s0;
        }

        {
          char *s0 = replaceAll(s, "%SHIFT%", shiftstr);
          free(s);
          s = s0;
        }

        fputs(s, fpout);
        free(s);
      }

      fclose(fpin);
      fclose(fpout);
    }
  }
}
