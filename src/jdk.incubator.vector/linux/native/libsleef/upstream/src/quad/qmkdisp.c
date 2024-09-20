//   Copyright Naoki Shibata and contributors 2010 - 2021.
// Distributed under the Boost Software License, Version 1.0.
//    (See accompanying file LICENSE.txt or copy at
//          http://www.boost.org/LICENSE_1_0.txt)

#include <stdio.h>
#include <stdlib.h>
#include <stdint.h>
#include <string.h>

#include "qfuncproto.h"

int main(int argc, char **argv) {
  if (argc < 7) {
    fprintf(stderr, "Usage : %s <DP width> <vargquad type> <vdouble type> <vint type> <vint64 type> <vuint64 type> <isa0> [<isa1> ...]\n", argv[0]);
    fprintf(stderr, "\n");
    exit(-1);
  }

  const int wdp = atoi(argv[1]);
  const char *vquadname = argv[2], *vdoublename = argv[3];
  const char *vintname = argv[4], *vint64name = argv[5], *vuint64name = argv[6];
  const int isastart = 7, nisa = argc - isastart;

  for(int i=0;funcList[i].name != NULL;i++) {
    char ulpSuffix0[100] = "", ulpSuffix1[100] = "_";
    if (funcList[i].ulp >= 0) {
      sprintf(ulpSuffix0, "_u%02d", funcList[i].ulp);
      sprintf(ulpSuffix1, "_u%02d", funcList[i].ulp);
    }

    switch(funcList[i].funcType) {
    case 0:
      printf("DISPATCH_vq_vq(%s, Sleef_%sq%d%s, pnt_%sq%d%s, disp_%sq%d%s",
             vquadname,
             funcList[i].name, wdp, ulpSuffix0,
             funcList[i].name, wdp, ulpSuffix0,
             funcList[i].name, wdp, ulpSuffix0);
      for(int j=0;j<nisa;j++) printf(", Sleef_%sq%d%s%s", funcList[i].name, wdp, ulpSuffix1, argv[isastart + j]);
      printf(")\n");
      break;

    case 1:
      printf("DISPATCH_vq_vq_vq(%s, Sleef_%sq%d%s, pnt_%sq%d%s, disp_%sq%d%s",
             vquadname,
             funcList[i].name, wdp, ulpSuffix0,
             funcList[i].name, wdp, ulpSuffix0,
             funcList[i].name, wdp, ulpSuffix0);
      for(int j=0;j<nisa;j++) printf(", Sleef_%sq%d%s%s", funcList[i].name, wdp, ulpSuffix1, argv[isastart + j]);
      printf(")\n");
      break;

    case 3:
      printf("DISPATCH_vq_vq_vx(%s, %s, Sleef_%sq%d%s, pnt_%sq%d%s, disp_%sq%d%s",
             vquadname, vintname,
             funcList[i].name, wdp, ulpSuffix0,
             funcList[i].name, wdp, ulpSuffix0,
             funcList[i].name, wdp, ulpSuffix0);
      for(int j=0;j<nisa;j++) printf(", Sleef_%sq%d%s%s", funcList[i].name, wdp, ulpSuffix1, argv[isastart + j]);
      printf(")\n");
      break;

    case 4:
      printf("DISPATCH_vx_vq(%s, %s, Sleef_%sq%d%s, pnt_%sq%d%s, disp_%sq%d%s",
             vquadname, vintname,
             funcList[i].name, wdp, ulpSuffix0,
             funcList[i].name, wdp, ulpSuffix0,
             funcList[i].name, wdp, ulpSuffix0);
      for(int j=0;j<nisa;j++) printf(", Sleef_%sq%d%s%s", funcList[i].name, wdp, ulpSuffix1, argv[isastart + j]);
      printf(")\n");
      break;

    case 5:
      printf("DISPATCH_vq_vq_vq_vq(%s, Sleef_%sq%d%s, pnt_%sq%d%s, disp_%sq%d%s",
             vquadname,
             funcList[i].name, wdp, ulpSuffix0,
             funcList[i].name, wdp, ulpSuffix0,
             funcList[i].name, wdp, ulpSuffix0);
      for(int j=0;j<nisa;j++) printf(", Sleef_%sq%d%s%s", funcList[i].name, wdp, ulpSuffix1, argv[isastart + j]);
      printf(")\n");
      break;

    case 9:
      printf("DISPATCH_vx_vq_vq(%s, %s, Sleef_%sq%d%s, pnt_%sq%d%s, disp_%sq%d%s",
             vquadname, vintname,
             funcList[i].name, wdp, ulpSuffix0,
             funcList[i].name, wdp, ulpSuffix0,
             funcList[i].name, wdp, ulpSuffix0);
      for(int j=0;j<nisa;j++) printf(", Sleef_%sq%d%s%s", funcList[i].name, wdp, ulpSuffix1, argv[isastart + j]);
      printf(")\n");
      break;

    case 10:
      printf("DISPATCH_vx_vq(%s, %s, Sleef_%sq%d%s, pnt_%sq%d%s, disp_%sq%d%s",
             vquadname, vdoublename,
             funcList[i].name, wdp, ulpSuffix0,
             funcList[i].name, wdp, ulpSuffix0,
             funcList[i].name, wdp, ulpSuffix0);
      for(int j=0;j<nisa;j++) printf(", Sleef_%sq%d%s%s", funcList[i].name, wdp, ulpSuffix1, argv[isastart + j]);
      printf(")\n");
      break;

    case 11:
      printf("DISPATCH_vq_vx(%s, %s, Sleef_%sq%d%s, pnt_%sq%d%s, disp_%sq%d%s",
             vquadname, vdoublename,
             funcList[i].name, wdp, ulpSuffix0,
             funcList[i].name, wdp, ulpSuffix0,
             funcList[i].name, wdp, ulpSuffix0);
      for(int j=0;j<nisa;j++) printf(", Sleef_%sq%d%s%s", funcList[i].name, wdp, ulpSuffix1, argv[isastart + j]);
      printf(")\n");
      break;

    case 12:
      printf("DISPATCH_vq_vx(%s, %s, Sleef_%sq%d%s, pnt_%sq%d%s, disp_%sq%d%s",
             vquadname, "Sleef_quad",
             funcList[i].name, wdp, ulpSuffix0,
             funcList[i].name, wdp, ulpSuffix0,
             funcList[i].name, wdp, ulpSuffix0);
      for(int j=0;j<nisa;j++) printf(", Sleef_%sq%d%s%s", funcList[i].name, wdp, ulpSuffix1, argv[isastart + j]);
      printf(")\n");
      break;

    case 16:
      printf("DISPATCH_q_vq_vx(%s, %s, Sleef_%sq%d%s, pnt_%sq%d%s, disp_%sq%d%s",
             vquadname, "int",
             funcList[i].name, wdp, ulpSuffix0,
             funcList[i].name, wdp, ulpSuffix0,
             funcList[i].name, wdp, ulpSuffix0);
      for(int j=0;j<nisa;j++) printf(", Sleef_%sq%d%s%s", funcList[i].name, wdp, ulpSuffix1, argv[isastart + j]);
      printf(")\n");
      break;

    case 17:
      printf("DISPATCH_vq_vq_vi_q(%s, %s, Sleef_%sq%d%s, pnt_%sq%d%s, disp_%sq%d%s",
             vquadname, "int",
             funcList[i].name, wdp, ulpSuffix0,
             funcList[i].name, wdp, ulpSuffix0,
             funcList[i].name, wdp, ulpSuffix0);
      for(int j=0;j<nisa;j++) printf(", Sleef_%sq%d%s%s", funcList[i].name, wdp, ulpSuffix1, argv[isastart + j]);
      printf(")\n");
      break;

    case 18:
      printf("DISPATCH_vx_vq(%s, %s, Sleef_%sq%d%s, pnt_%sq%d%s, disp_%sq%d%s",
             vquadname, vint64name,
             funcList[i].name, wdp, ulpSuffix0,
             funcList[i].name, wdp, ulpSuffix0,
             funcList[i].name, wdp, ulpSuffix0);
      for(int j=0;j<nisa;j++) printf(", Sleef_%sq%d%s%s", funcList[i].name, wdp, ulpSuffix1, argv[isastart + j]);
      printf(")\n");
      break;

    case 19:
      printf("DISPATCH_vq_vx(%s, %s, Sleef_%sq%d%s, pnt_%sq%d%s, disp_%sq%d%s",
             vquadname, vint64name,
             funcList[i].name, wdp, ulpSuffix0,
             funcList[i].name, wdp, ulpSuffix0,
             funcList[i].name, wdp, ulpSuffix0);
      for(int j=0;j<nisa;j++) printf(", Sleef_%sq%d%s%s", funcList[i].name, wdp, ulpSuffix1, argv[isastart + j]);
      printf(")\n");
      break;

    case 20:
      printf("DISPATCH_vx_vq(%s, %s, Sleef_%sq%d%s, pnt_%sq%d%s, disp_%sq%d%s",
             vquadname, vuint64name,
             funcList[i].name, wdp, ulpSuffix0,
             funcList[i].name, wdp, ulpSuffix0,
             funcList[i].name, wdp, ulpSuffix0);
      for(int j=0;j<nisa;j++) printf(", Sleef_%sq%d%s%s", funcList[i].name, wdp, ulpSuffix1, argv[isastart + j]);
      printf(")\n");
      break;

    case 21:
      printf("DISPATCH_vq_vx(%s, %s, Sleef_%sq%d%s, pnt_%sq%d%s, disp_%sq%d%s",
             vquadname, vuint64name,
             funcList[i].name, wdp, ulpSuffix0,
             funcList[i].name, wdp, ulpSuffix0,
             funcList[i].name, wdp, ulpSuffix0);
      for(int j=0;j<nisa;j++) printf(", Sleef_%sq%d%s%s", funcList[i].name, wdp, ulpSuffix1, argv[isastart + j]);
      printf(")\n");
      break;

    case 22:
      printf("DISPATCH_vq_vq_pvx(%s, %s, Sleef_%sq%d%s, pnt_%sq%d%s, disp_%sq%d%s",
             vquadname, vintname,
             funcList[i].name, wdp, ulpSuffix0,
             funcList[i].name, wdp, ulpSuffix0,
             funcList[i].name, wdp, ulpSuffix0);
      for(int j=0;j<nisa;j++) printf(", Sleef_%sq%d%s%s", funcList[i].name, wdp, ulpSuffix1, argv[isastart + j]);
      printf(")\n");
      break;

    case 23:
      printf("DISPATCH_vq_vq_pvx(%s, %s, Sleef_%sq%d%s, pnt_%sq%d%s, disp_%sq%d%s",
             vquadname, vquadname,
             funcList[i].name, wdp, ulpSuffix0,
             funcList[i].name, wdp, ulpSuffix0,
             funcList[i].name, wdp, ulpSuffix0);
      for(int j=0;j<nisa;j++) printf(", Sleef_%sq%d%s%s", funcList[i].name, wdp, ulpSuffix1, argv[isastart + j]);
      printf(")\n");
      break;
    }
  }

  exit(0);
}
