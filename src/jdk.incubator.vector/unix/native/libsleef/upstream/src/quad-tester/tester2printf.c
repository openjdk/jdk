//   Copyright Naoki Shibata and contributors 2010 - 2021.
// Distributed under the Boost Software License, Version 1.0.
//    (See accompanying file LICENSE.txt or copy at
//          http://www.boost.org/LICENSE_1_0.txt)

// gcc -O3 -Wno-format tester2printf.c qtesterutil.c -I ../../src/common -I ../../build/include -L ../../build/lib -lsleefquad -lsleef -lquadmath

#include <stdio.h>
#include <stdlib.h>
#include <stdint.h>
#include <string.h>
#include <ctype.h>
#include <time.h>
#include <unistd.h>

#include <quadmath.h>

#include "sleefquad.h"
#include "qtesterutil.h"

void testem_rnd(Sleef_quad val) {
  int prec = xrand() % 25, width = xrand() % 50;
  char *types[] = { "Qe", "Qf", "Qg", "Qa" };
  for(int i=0;i<4;i++) {
    for(int alt=0;alt<2;alt++) {
      for(int zero=0;zero<2;zero++) {
        for(int left=0;left<2;left++) {
          for(int blank=0;blank<2;blank++) {
            for(int sign=0;sign<2;sign++) {
              char fmt[100], corr[100], corr2[100], test[100];
              int lc, lc2, lt;

              // no width, no prec

              snprintf(fmt, 99, "%%%s%s%s%s%s%s",
                       alt ? "#" : "",
                       zero ? "0" : "",
                       left ? "-" : "",
                       blank ? " " : "",
                       sign ? "+" : "",
                       types[i]);
              lc2 = snprintf(corr2, 99, fmt, val);
              lc = snprintf(corr, 99, fmt, strtoflt128(corr2, NULL));
              lt = Sleef_snprintf(test, 99, fmt, val);

              if((lc != lt && lc2 != lt) || (strcmp(test, corr) != 0 && strcmp(test, corr2) != 0)) {
                printf("val=%Qa %s : c=[%s](%d) t=[%s](%d)\n", val, fmt, corr, lc, test, lt);
                return;
              }
              if (strtoflt128(corr, NULL) != Sleef_strtoq(corr, NULL) && strstr(corr, "nan") == NULL) {
                printf("X [%s] : c=[%.40Qg] t=[%.40Qg]\n", corr, strtoflt128(corr, NULL), Sleef_strtoq(corr, NULL));
                return;
              }

              // width

              snprintf(fmt, 99, "%%%s%s%s%s%s%d.%s",
                       alt ? "#" : "",
                       zero ? "0" : "",
                       left ? "-" : "",
                       blank ? " " : "",
                       sign ? "+" : "",
                       width, types[i]);
              lc2 = snprintf(corr2, 99, fmt, val);
              lc = snprintf(corr, 99, fmt, strtoflt128(corr2, NULL));
              lt = Sleef_snprintf(test, 99, fmt, val);

              if((lc != lt && lc2 != lt) || (strcmp(test, corr) != 0 && strcmp(test, corr2) != 0)) {
                printf("val=%Qa %s : c=[%s](%d) t=[%s](%d)\n", val, fmt, corr, lc, test, lt);
                return;
              }
              if (strtoflt128(corr, NULL) != Sleef_strtoq(corr, NULL) && strstr(corr, "nan") == NULL) {
                printf("X [%s] : c=[%.40Qg] t=[%.40Qg]\n", corr, strtoflt128(corr, NULL), Sleef_strtoq(corr, NULL));
                return;
              }

              // prec

              snprintf(fmt, 99, "%%%s%s%s%s%s.%d%s",
                       alt ? "#" : "",
                       zero ? "0" : "",
                       left ? "-" : "",
                       blank ? " " : "",
                       sign ? "+" : "",
                       prec, types[i]);
              lc2 = snprintf(corr2, 99, fmt, val);
              lc = snprintf(corr, 99, fmt, strtoflt128(corr2, NULL));
              lt = Sleef_snprintf(test, 99, fmt, val);

              if((lc != lt && lc2 != lt) || (strcmp(test, corr) != 0 && strcmp(test, corr2) != 0)) {
                printf("val=%Qa %s : c=[%s](%d) t=[%s](%d)\n", val, fmt, corr, lc, test, lt);
                return;
              }
              if (strtoflt128(corr, NULL) != Sleef_strtoq(corr, NULL) && strstr(corr, "nan") == NULL) {
                printf("X [%s] : c=[%.40Qg] t=[%.40Qg]\n", corr, strtoflt128(corr, NULL), Sleef_strtoq(corr, NULL));
                return;
              }

              // both

              snprintf(fmt, 99, "%%%s%s%s%s%s%d.%d%s",
                       alt ? "#" : "",
                       zero ? "0" : "",
                       left ? "-" : "",
                       blank ? " " : "",
                       sign ? "+" : "",
                       width, prec, types[i]);
              lc2 = snprintf(corr2, 99, fmt, val);
              lc = snprintf(corr, 99, fmt, strtoflt128(corr2, NULL));
              lt = Sleef_snprintf(test, 99, fmt, val);

              if((lc != lt && lc2 != lt) || (strcmp(test, corr) != 0 && strcmp(test, corr2) != 0)) {
                printf("val=%Qa %s : c=[%s](%d) t=[%s](%d)\n", val, fmt, corr, lc, test, lt);
                return;
              }
              if (strtoflt128(corr, NULL) != Sleef_strtoq(corr, NULL) && strstr(corr, "nan") == NULL) {
                printf("X [%s] : c=[%.40Qg] t=[%.40Qg]\n", corr, strtoflt128(corr, NULL), Sleef_strtoq(corr, NULL));
                return;
              }
            }
          }
        }
      }
    }
  }
}

int testem(Sleef_quad val) {
  int ret = 0;
  char *types[] = { "Qe", "Qf", "Qg", "Qa" };
  for(int i=0;i<4;i++) {
    for(int alt=0;alt<2;alt++) {
      for(int zero=0;zero<2;zero++) {
        for(int left=0;left<2;left++) {
          for(int blank=0;blank<2;blank++) {
            for(int sign=0;sign<2;sign++) {
              char fmt[100], corr[100], corr2[100], test[100];
              int lc, lc2, lt;

              snprintf(fmt, 99, "%%%s%s%s%s%s%s",
                       alt ? "#" : "",
                       zero ? "0" : "",
                       left ? "-" : "",
                       blank ? " " : "",
                       sign ? "+" : "",
                       types[i]);
              lc2 = snprintf(corr2, 99, fmt, val);
              lc = snprintf(corr, 99, fmt, strtoflt128(corr2, NULL));
              lt = Sleef_snprintf(test, 99, fmt, val);

              if((lc != lt && lc2 != lt) || (strcmp(test, corr) != 0 && strcmp(test, corr2) != 0)) {
                printf("val=%Qa %s : c=[%s](%d) t=[%s](%d)\n", val, fmt, corr, lc, test, lt);
                ret = 1;
              }
              if (strtoflt128(corr, NULL) != Sleef_strtoq(corr, NULL) && strstr(corr, "nan") == NULL) {
                printf("X [%s] : c=[%.40Qg] t=[%.40Qg]\n", corr, strtoflt128(corr, NULL), Sleef_strtoq(corr, NULL));
                ret = 1;
              }

              int prec = xrand() % 30, width = xrand() % 30;

              for(int width=6;width<=16;width += 2) {
                snprintf(fmt, 99, "%%%s%s%s%s%s%d.%s",
                         alt ? "#" : "",
                         zero ? "0" : "",
                         left ? "-" : "",
                         blank ? " " : "",
                         sign ? "+" : "",
                         width, types[i]);
                lc2 = snprintf(corr2, 99, fmt, val);
                lc = snprintf(corr, 99, fmt, strtoflt128(corr2, NULL));
                lt = Sleef_snprintf(test, 99, fmt, val);

                if((lc != lt && lc2 != lt) || (strcmp(test, corr) != 0 && strcmp(test, corr2) != 0)) {
                  printf("val=%Qa %s : c=[%s](%d) t=[%s](%d)\n", val, fmt, corr, lc, test, lt);
                  ret = 1;
                }
                if (strtoflt128(corr, NULL) != Sleef_strtoq(corr, NULL) && strstr(corr, "nan") == NULL) {
                  printf("X [%s] : c=[%.40Qg] t=[%.40Qg]\n", corr, strtoflt128(corr, NULL), Sleef_strtoq(corr, NULL));
                  ret = 1;
                }
              }

              for(int prec=4;prec<=12;prec += 2) {
                for(int width=6;width<=16;width += 2) {
                  snprintf(fmt, 99, "%%%s%s%s%s%s%d.%d%s",
                           alt ? "#" : "",
                           zero ? "0" : "",
                           left ? "-" : "",
                           blank ? " " : "",
                           sign ? "+" : "",
                           width, prec, types[i]);
                  lc2 = snprintf(corr2, 99, fmt, val);
                  lc = snprintf(corr, 99, fmt, strtoflt128(corr2, NULL));
                  lt = Sleef_snprintf(test, 99, fmt, val);

                  if((lc != lt && lc2 != lt) || (strcmp(test, corr) != 0 && strcmp(test, corr2) != 0)) {
                    printf("val=%Qa %s : c=[%s](%d) t=[%s](%d)\n", val, fmt, corr, lc, test, lt);
                    ret = 1;
                  }
                  if (strtoflt128(corr, NULL) != Sleef_strtoq(corr, NULL) && strstr(corr, "nan") == NULL) {
                    printf("X [%s] : c=[%.40Qg] t=[%.40Qg]\n", corr, strtoflt128(corr, NULL), Sleef_strtoq(corr, NULL));
                    ret = 1;
                  }
                }

                snprintf(fmt, 99, "%%%s%s%s%s%s.%d%s",
                         alt ? "#" : "",
                         zero ? "0" : "",
                         left ? "-" : "",
                         blank ? " " : "",
                         sign ? "+" : "",
                         prec, types[i]);
                lc2 = snprintf(corr2, 99, fmt, val);
                lc = snprintf(corr, 99, fmt, strtoflt128(corr2, NULL));
                lt = Sleef_snprintf(test, 99, fmt, val);

                if((lc != lt && lc2 != lt) || (strcmp(test, corr) != 0 && strcmp(test, corr2) != 0)) {
                  printf("val=%Qa %s : c=[%s](%d) t=[%s](%d)\n", val, fmt, corr, lc, test, lt);
                  ret = 1;
                  }
                if (strtoflt128(corr, NULL) != Sleef_strtoq(corr, NULL) && strstr(corr, "nan") == NULL) {
                  printf("X [%s] : c=[%.40Qg] t=[%.40Qg]\n", corr, strtoflt128(corr, NULL), Sleef_strtoq(corr, NULL));
                  ret = 1;
                }
              }
            }
          }
        }
      }
    }
  }
  return ret;
}

int main(int argc, char **argv) {
  xsrand(time(NULL) + (((int)getpid()) << 12));

  strtoflt128("1", NULL); // This is for registering hook

  Sleef_quad vals[] = {
    1.2345678912345678912345e+0Q,
    1.2345678912345678912345e+1Q,
    1.2345678912345678912345e-1Q,
    1.2345678912345678912345e+2Q,
    1.2345678912345678912345e-2Q,
    1.2345678912345678912345e+3Q,
    1.2345678912345678912345e-3Q,
    1.2345678912345678912345e+4Q,
    1.2345678912345678912345e-4Q,
    1.2345678912345678912345e+5Q,
    1.2345678912345678912345e-5Q,
    1.2345678912345678912345e+10Q,
    1.2345678912345678912345e-10Q,
    1.2345678912345678912345e+15Q,
    1.2345678912345678912345e-15Q,
    1.2345678912345678912345e+30Q,
    1.2345678912345678912345e-30Q,
    //1.2345678912345678912345e+1000Q,
    1.2345678912345678912345e-1000Q,
    1.2345678912345678912345e-4950Q,
    //1.2345678912345678912345e+4920Q,
    3.36210314311209350626267781732175260e-4932Q,
    //1.18973149535723176508575932662800702e+4932Q,
    6.475175119438025110924438958227646552e-4966Q,
    0.0Q, 1.0Q,
    1e+1Q, 1e+2Q, 1e+3Q, 1e+4Q, 1e+5Q, 1e+6Q,
    1e-1Q, 1e-2Q, 1e-3Q, 1e-4Q, 1e-5Q, 1e-6Q,
    1e+300*1e+300, 1e+300*1e+300 - 1e+300*1e+300
  };

  for(int i=0;i<sizeof(vals)/sizeof(Sleef_quad);i++) {
    if (testem(+vals[i])) exit(-1);
    if (testem(-vals[i])) exit(-1);
  }

  for(;;) {
    Sleef_quad q;
    memrand(&q, sizeof(q));
    if (fabsq(q) > 1e+25) continue;
    testem_rnd(q);
  }
}
