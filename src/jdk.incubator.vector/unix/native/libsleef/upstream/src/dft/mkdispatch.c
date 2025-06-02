//   Copyright Naoki Shibata and contributors 2010 - 2021.
// Distributed under the Boost Software License, Version 1.0.
//    (See accompanying file LICENSE.txt or copy at
//          http://www.boost.org/LICENSE_1_0.txt)

#include <stdio.h>
#include <stdlib.h>
#include <stdint.h>
#include <string.h>

#ifndef ENABLE_STREAM
#error ENABLE_STREAM not defined
#endif

int main(int argc, char **argv) {
  if (argc < 3) {
    fprintf(stderr, "Usage : %s <basetype> <unrollmax> <unrollmax2> <maxbutwidth> <isa> ...\n", argv[0]);
    exit(-1);
  }

  const char *basetype = argv[1];
  const int maxbutwidth = atoi(argv[2]);
  const int isastart = 3;
  const int isamax = argc - isastart;

#if ENABLE_STREAM == 1
  const int enable_stream = 1;
#else
  const int enable_stream = 0;
#endif

  printf("#define MAXBUTWIDTH %d\n", maxbutwidth);
  printf("\n");

  if (strcmp(basetype, "paramonly") == 0) exit(0);

  printf("#define ISAMAX %d\n", isamax);
  printf("#define CONFIGMAX 4\n");

  for(int k=isastart;k<argc;k++) {
    for(int config=0;config<4;config++) {
#if ENABLE_STREAM == 0
      if ((config & 1) != 0) continue;
#endif
      for(int j=1;j<=maxbutwidth;j++) {
        printf("void dft%df_%d_%s(real *, const real *, const int);\n", 1 << j, config, argv[k]);
        printf("void dft%db_%d_%s(real *, const real *, const int);\n", 1 << j, config, argv[k]);
        printf("void tbut%df_%d_%s(real *, uint32_t *, const real *, const int, const real *, const int);\n", 1 << j, config, argv[k]);
        printf("void tbut%db_%d_%s(real *, uint32_t *, const real *, const int, const real *, const int);\n", 1 << j, config, argv[k]);
        printf("void but%df_%d_%s(real *, uint32_t *, const int, const real *, const int, const real *, const int);\n", 1 << j, config, argv[k]);
        printf("void but%db_%d_%s(real *, uint32_t *, const int, const real *, const int, const real *, const int);\n", 1 << j, config, argv[k]);
      }
    }
    printf("void realSub0_%s(real *, const real *, const int, const real *, const real *);\n", argv[k]);
    printf("void realSub1_%s(real *, const real *, const int, const real *, const real *, const int);\n", argv[k]);
    printf("int getInt_%s(int);\n", argv[k]);
    printf("const void *getPtr_%s(int);\n", argv[k]);
  }

  printf("\n");

  printf("void (*dftf_%s[CONFIGMAX][ISAMAX][MAXBUTWIDTH+1])(real *, const real *, const int) = {\n", basetype);
  for(int config=0;config<4;config++) {
    printf("  {\n");
    for(int k=isastart;k<argc;k++) {
      printf("    {NULL, ");
      for(int i=1;i<=maxbutwidth;i++) {
        if (enable_stream || (config & 1) == 0) {
          printf("dft%df_%d_%s, ", 1 << i, config, argv[k]);
        } else {
          printf("NULL, ");
        }
      }
      printf("},\n");
    }
    printf("},\n");
  }
  printf("};\n\n");

  printf("void (*dftb_%s[CONFIGMAX][ISAMAX][MAXBUTWIDTH+1])(real *, const real *, const int) = {\n", basetype);
  for(int config=0;config<4;config++) {
    printf("  {\n");
    for(int k=isastart;k<argc;k++) {
      printf("    {NULL, ");
      for(int i=1;i<=maxbutwidth;i++) {
        if (enable_stream || (config & 1) == 0) {
          if (i == 1) {
            printf("dft%df_%d_%s, ", 1 << i, config, argv[k]);
          } else {
            printf("dft%db_%d_%s, ", 1 << i, config, argv[k]);
          }
        } else {
          printf("NULL, ");
        }
      }
      printf("},\n");
    }
    printf("},\n");
  }
  printf("};\n\n");

  printf("void (*tbutf_%s[CONFIGMAX][ISAMAX][MAXBUTWIDTH+1])(real *, uint32_t *, const real *, const int, const real *, const int) = {\n", basetype);
  for(int config=0;config<4;config++) {
    printf("  {\n");
    for(int k=isastart;k<argc;k++) {
      printf("    {NULL, ");
      for(int i=1;i<=maxbutwidth;i++) {
        if (enable_stream || (config & 1) == 0) {
          printf("tbut%df_%d_%s, ", 1 << i, config, argv[k]);
        } else {
          printf("NULL, ");
        }
      }
      printf("},\n");
    }
    printf("},\n");
  }
  printf("};\n\n");

  printf("void (*tbutb_%s[CONFIGMAX][ISAMAX][MAXBUTWIDTH+1])(real *, uint32_t *, const real *, const int, const real *, const int) = {\n", basetype);
  for(int config=0;config<4;config++) {
    printf("  {\n");
    for(int k=isastart;k<argc;k++) {
      printf("    {NULL, ");
      for(int i=1;i<=maxbutwidth;i++) {
        if (enable_stream || (config & 1) == 0) {
          printf("tbut%db_%d_%s, ", 1 << i, config, argv[k]);
        } else {
          printf("NULL, ");
        }
      }
      printf("},\n");
    }
    printf("},\n");
  }
  printf("};\n\n");

  printf("void (*butf_%s[CONFIGMAX][ISAMAX][MAXBUTWIDTH+1])(real *, uint32_t *, const int, const real *, const int, const real *, const int) = {\n", basetype);
  for(int config=0;config<4;config++) {
    printf("  {\n");
    for(int k=isastart;k<argc;k++) {
      printf("    {NULL, ");
      for(int i=1;i<=maxbutwidth;i++) {
        if (enable_stream || (config & 1) == 0) {
          printf("but%df_%d_%s, ", 1 << i, config, argv[k]);
        } else {
          printf("NULL, ");
        }
      }
      printf("},\n");
    }
    printf("},\n");
  }
  printf("};\n\n");

  printf("void (*butb_%s[CONFIGMAX][ISAMAX][MAXBUTWIDTH+1])(real *, uint32_t *, const int, const real *, const int, const real *, const int) = {\n", basetype);
  for(int config=0;config<4;config++) {
    printf("  {\n");
    for(int k=isastart;k<argc;k++) {
      printf("    {NULL, ");
      for(int i=1;i<=maxbutwidth;i++) {
        if (enable_stream || (config & 1) == 0) {
          printf("but%db_%d_%s, ", 1 << i, config, argv[k]);
        } else {
          printf("NULL, ");
        }
      }
      printf("},\n");
    }
    printf("},\n");
  }
  printf("};\n\n");

  //

  printf("void (*realSub0_%s[ISAMAX])(real *, const real *, const int, const real *, const real *) = {\n  ", basetype);
  for(int k=isastart;k<argc;k++) printf("realSub0_%s, ", argv[k]);
  printf("\n};\n\n");

  printf("void (*realSub1_%s[ISAMAX])(real *, const real *, const int, const real *, const real *, const int) = {\n  ", basetype);
  for(int k=isastart;k<argc;k++) printf("realSub1_%s, ", argv[k]);
  printf("\n};\n\n");

  printf("int (*getInt_%s[16])(int) = {\n  ", basetype);
  for(int k=isastart;k<argc;k++) printf("getInt_%s, ", argv[k]);
  for(int k=0;k<16-(argc-isastart);k++) printf("NULL, ");
  printf("\n};\n\n");

  printf("const void *(*getPtr_%s[16])(int) = {\n  ", basetype);
  for(int k=isastart;k<argc;k++) printf("getPtr_%s, ", argv[k]);
  for(int k=0;k<16-(argc-isastart);k++) printf("NULL, ");
  printf("\n};\n\n");
}
