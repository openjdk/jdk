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
  if (argc < 7) {
    fprintf(stderr, "Usage : %s <DP width> <SP width> <vdouble type> <vfloat type> <vint type> <isa0> [<isa1> ...]\n", argv[0]);
    fprintf(stderr, "\n");
    exit(-1);
  }

  const int wdp = atoi(argv[1]), wsp = atoi(argv[2]);
  const char *vdoublename = argv[3], *vfloatname = argv[4], *vintname = argv[5];
  const int isastart = 6, nisa = argc - isastart;

  for(int i=0;funcList[i].name != NULL;i++) {
    char ulpSuffix0[100] = "", ulpSuffix1[100] = "_";
    if (funcList[i].ulp >= 0) {
      sprintf(ulpSuffix0, "_u%02d", funcList[i].ulp);
      sprintf(ulpSuffix1, "_u%02d", funcList[i].ulp);
    }

    switch(funcList[i].funcType) {
    case 0:
      if ((funcList[i].flags & 2) == 0) {
        printf("DISPATCH_vf_vf(%s, %d, Sleef_%s%s, Sleef_%sd1%s, Sleef_%sd%d%s, pnt_%sd%d%s, disp_%sd%d%s",
               vdoublename,
               wdp, funcList[i].name, ulpSuffix0,
               funcList[i].name, ulpSuffix0,
               funcList[i].name, wdp, ulpSuffix0,
               funcList[i].name, wdp, ulpSuffix0,
               funcList[i].name, wdp, ulpSuffix0);
        for(int j=0;j<nisa;j++) printf(", Sleef_%sd%d%s%s", funcList[i].name, wdp, ulpSuffix1, argv[isastart + j]);
        printf(")\n");
      }

      printf("DISPATCH_vf_vf(%s, %d, Sleef_%sf%s, Sleef_%sf1%s, Sleef_%sf%d%s, pnt_%sf%d%s, disp_%sf%d%s",
             vfloatname,
             wsp, funcList[i].name, ulpSuffix0,
             funcList[i].name, ulpSuffix0,
             funcList[i].name, wsp, ulpSuffix0,
             funcList[i].name, wsp, ulpSuffix0,
             funcList[i].name, wsp, ulpSuffix0);
      for(int j=0;j<nisa;j++) printf(", Sleef_%sf%d%s%s", funcList[i].name, wsp, ulpSuffix1, argv[isastart + j]);
      printf(")\n");

      break;
    case 1:
      if ((funcList[i].flags & 2) == 0) {
        printf("DISPATCH_vf_vf_vf(%s, %d, Sleef_%s%s, Sleef_%sd1%s, Sleef_%sd%d%s, pnt_%sd%d%s, disp_%sd%d%s",
               vdoublename,
               wdp, funcList[i].name, ulpSuffix0,
               funcList[i].name, ulpSuffix0,
               funcList[i].name, wdp, ulpSuffix0,
               funcList[i].name, wdp, ulpSuffix0,
               funcList[i].name, wdp, ulpSuffix0);
        for(int j=0;j<nisa;j++) printf(", Sleef_%sd%d%s%s", funcList[i].name, wdp, ulpSuffix1, argv[isastart + j]);
        printf(")\n");
      }

      printf("DISPATCH_vf_vf_vf(%s, %d, Sleef_%sf%s, Sleef_%sf1%s, Sleef_%sf%d%s, pnt_%sf%d%s, disp_%sf%d%s",
             vfloatname,
             wsp, funcList[i].name, ulpSuffix0,
             funcList[i].name, ulpSuffix0,
             funcList[i].name, wsp, ulpSuffix0,
             funcList[i].name, wsp, ulpSuffix0,
             funcList[i].name, wsp, ulpSuffix0);
      for(int j=0;j<nisa;j++) printf(", Sleef_%sf%d%s%s", funcList[i].name, wsp, ulpSuffix1, argv[isastart + j]);
      printf(")\n");

      break;
    case 2:
    case 6:
      if ((funcList[i].flags & 2) == 0) {
        printf("DISPATCH_vf2_vf(%s, Sleef_%s_2, %d, Sleef_%s%s, Sleef_%sd1%s, Sleef_%sd%d%s, pnt_%sd%d%s, disp_%sd%d%s",
               vdoublename, vdoublename,
               wdp, funcList[i].name, ulpSuffix0,
               funcList[i].name, ulpSuffix0,
               funcList[i].name, wdp, ulpSuffix0,
               funcList[i].name, wdp, ulpSuffix0,
               funcList[i].name, wdp, ulpSuffix0);
        for(int j=0;j<nisa;j++) printf(", Sleef_%sd%d%s%s", funcList[i].name, wdp, ulpSuffix1, argv[isastart + j]);
        printf(")\n");
      }

      printf("DISPATCH_vf2_vf(%s, Sleef_%s_2, %d, Sleef_%sf%s, Sleef_%sf1%s, Sleef_%sf%d%s, pnt_%sf%d%s, disp_%sf%d%s",
             vfloatname, vfloatname,
             wsp, funcList[i].name, ulpSuffix0,
             funcList[i].name, ulpSuffix0,
             funcList[i].name, wsp, ulpSuffix0,
             funcList[i].name, wsp, ulpSuffix0,
             funcList[i].name, wsp, ulpSuffix0);
      for(int j=0;j<nisa;j++) printf(", Sleef_%sf%d%s%s", funcList[i].name, wsp, ulpSuffix1, argv[isastart + j]);
      printf(")\n");

      break;
    case 3:
      if ((funcList[i].flags & 2) == 0) {
        printf("DISPATCH_vf_vf_vi(%s, %s, %d, Sleef_%s%s, Sleef_%sd1%s, Sleef_%sd%d%s, pnt_%sd%d%s, disp_%sd%d%s",
               vdoublename, vintname,
               wdp, funcList[i].name, ulpSuffix0,
               funcList[i].name, ulpSuffix0,
               funcList[i].name, wdp, ulpSuffix0,
               funcList[i].name, wdp, ulpSuffix0,
               funcList[i].name, wdp, ulpSuffix0);
        for(int j=0;j<nisa;j++) printf(", Sleef_%sd%d%s%s", funcList[i].name, wdp, ulpSuffix1, argv[isastart + j]);
        printf(")\n");
      }
      break;
    case 4:
      if ((funcList[i].flags & 2) == 0) {
        printf("DISPATCH_vi_vf(%s, %s, %d, Sleef_%s%s, Sleef_%sd1%s, Sleef_%sd%d%s, pnt_%sd%d%s, disp_%sd%d%s",
               vdoublename, vintname,
               wsp, funcList[i].name, ulpSuffix0,
               funcList[i].name, ulpSuffix0,
               funcList[i].name, wdp, ulpSuffix0,
               funcList[i].name, wdp, ulpSuffix0,
               funcList[i].name, wdp, ulpSuffix0);
        for(int j=0;j<nisa;j++) printf(", Sleef_%sd%d%s%s", funcList[i].name, wdp, ulpSuffix1, argv[isastart + j]);
        printf(")\n");
      }
      break;
    case 5:
      if ((funcList[i].flags & 2) == 0) {
        printf("DISPATCH_vf_vf_vf_vf(%s, %d, Sleef_%s%s, Sleef_%sd1%s, Sleef_%sd%d%s, pnt_%sd%d%s, disp_%sd%d%s",
               vdoublename,
               wdp, funcList[i].name, ulpSuffix0,
               funcList[i].name, ulpSuffix0,
               funcList[i].name, wdp, ulpSuffix0,
               funcList[i].name, wdp, ulpSuffix0,
               funcList[i].name, wdp, ulpSuffix0);
        for(int j=0;j<nisa;j++) printf(", Sleef_%sd%d%s%s", funcList[i].name, wdp, ulpSuffix1, argv[isastart + j]);
        printf(")\n");
      }

      printf("DISPATCH_vf_vf_vf_vf(%s, %d, Sleef_%sf%s, Sleef_%sf1%s, Sleef_%sf%d%s, pnt_%sf%d%s, disp_%sf%d%s",
             vfloatname,
             wsp, funcList[i].name, ulpSuffix0,
             funcList[i].name, ulpSuffix0,
             funcList[i].name, wsp, ulpSuffix0,
             funcList[i].name, wsp, ulpSuffix0,
             funcList[i].name, wsp, ulpSuffix0);
      for(int j=0;j<nisa;j++) printf(", Sleef_%sf%d%s%s", funcList[i].name, wsp, ulpSuffix1, argv[isastart + j]);
      printf(")\n");

      break;
    case 7:
      printf("DISPATCH_i_i(%d, Sleef_%sf, Sleef_%sf1, Sleef_%sf%d, pnt_%sf%d, disp_%sf%d",
             wsp, funcList[i].name,
             funcList[i].name,
             funcList[i].name, wsp,
             funcList[i].name, wsp,
             funcList[i].name, wsp);
      for(int j=0;j<nisa;j++) printf(", Sleef_%sf%d_%s", funcList[i].name, wsp, argv[isastart + j]);
      printf(")\n");

      if ((funcList[i].flags & 2) == 0) {
        printf("DISPATCH_i_i(%d, Sleef_%s, Sleef_%sd1, Sleef_%sd%d, pnt_%sd%d, disp_%sd%d",
               wdp, funcList[i].name,
               funcList[i].name,
               funcList[i].name, wdp,
               funcList[i].name, wdp,
               funcList[i].name, wdp);
        for(int j=0;j<nisa;j++) printf(", Sleef_%sd%d_%s", funcList[i].name, wdp, argv[isastart + j]);
        printf(")\n");
      }
      break;
    case 8:
      printf("DISPATCH_p_i(%d, Sleef_%sf, Sleef_%sf1, Sleef_%sf%d, pnt_%sf%d, disp_%sf%d",
             wsp, funcList[i].name,
             funcList[i].name,
             funcList[i].name, wsp,
             funcList[i].name, wsp,
             funcList[i].name, wsp);
      for(int j=0;j<nisa;j++) printf(", Sleef_%sf%d_%s", funcList[i].name, wsp, argv[isastart + j]);
      printf(")\n");

      if ((funcList[i].flags & 2) == 0) {
        printf("DISPATCH_p_i(%d, Sleef_%s, Sleef_%sd1, Sleef_%sd%d, pnt_%sd%d, disp_%sd%d",
               wdp, funcList[i].name,
               funcList[i].name,
               funcList[i].name, wdp,
               funcList[i].name, wdp,
               funcList[i].name, wdp);
        for(int j=0;j<nisa;j++) printf(", Sleef_%sd%d_%s", funcList[i].name, wdp, argv[isastart + j]);
        printf(")\n");
      }
      break;
    }
  }

  exit(0);
}
