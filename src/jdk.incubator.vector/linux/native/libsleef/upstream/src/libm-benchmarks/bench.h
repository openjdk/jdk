#define NITER1 100000
#define NITER2 10000
#define NITER (NITER1 * NITER2)

#define callFuncSLEEF1_1(funcName, name, xmin, xmax, ulp, arg, type) ({ \
      printf("%s\n", #funcName);                                        \
      uint64_t t = Sleef_currentTimeMicros();                           \
      for(int j=0;j<NITER2;j++) {                                       \
        type *p = (type *)(arg);                                        \
        for(int i=0;i<NITER1;i++) funcName(*p++);                       \
      }                                                                 \
      fprintf(fp, name ", %.3g, %.3g, %gulps, %g\n",                    \
              (double)xmin, (double)xmax, ulp, (double)(Sleef_currentTimeMicros() - t) / NITER); \
    })

#define callFuncSLEEF1_2(funcName, name, xmin, xmax, ymin, ymax, ulp, arg1, arg2, type) ({ \
      printf("%s\n", #funcName);                                        \
      uint64_t t = Sleef_currentTimeMicros();                           \
      for(int j=0;j<NITER2;j++) {                                       \
        type *p1 = (type *)(arg1), *p2 = (type *)(arg2);                \
        for(int i=0;i<NITER1;i++) funcName(*p1++, *p2++);               \
      }                                                                 \
      fprintf(fp, name ", %.3g, %.3g, %.3g, %.3g, %gulps, %g\n",        \
              (double)xmin, (double)xmax, (double)ymin, (double)ymax, ulp, (double)(Sleef_currentTimeMicros() - t) / NITER); \
    })

#define callFuncSVML1_1(funcName, name, xmin, xmax, arg, type) ({       \
      printf("%s\n", #funcName);                                        \
      uint64_t t = Sleef_currentTimeMicros();                           \
      for(int j=0;j<NITER2;j++) {                                       \
        type *p = (type *)(arg);                                        \
        for(int i=0;i<NITER1;i++) funcName(*p++);                       \
      }                                                                 \
      fprintf(fp, name ", %.3g, %.3g, %gulps, %g\n",                    \
              (double)xmin, (double)xmax, (double)SVMLULP, (double)(Sleef_currentTimeMicros() - t) / NITER); \
    })

#define callFuncSVML2_1(funcName, name, xmin, xmax, arg, type) ({       \
      printf("%s\n", #funcName);                                        \
      uint64_t t = Sleef_currentTimeMicros();                           \
      for(int j=0;j<NITER2;j++) {                                       \
        type *p = (type *)(arg), c;                                     \
        for(int i=0;i<NITER1;i++) funcName(&c, *p++);                   \
      }                                                                 \
      fprintf(fp, name ", %.3g, %.3g, %gulps, %g\n",                    \
              (double)xmin, (double)xmax, (double)SVMLULP, (double)(Sleef_currentTimeMicros() - t) / NITER); \
    })

#define callFuncSVML1_2(funcName, name, xmin, xmax, ymin, ymax, arg1, arg2, type) ({ \
      printf("%s\n", #funcName);                                        \
      uint64_t t = Sleef_currentTimeMicros();                           \
      for(int j=0;j<NITER2;j++) {                                       \
        type *p1 = (type *)(arg1), *p2 = (type *)(arg2);                \
        for(int i=0;i<NITER1;i++) funcName(*p1++, *p2++);               \
      }                                                                 \
      fprintf(fp, name ", %.3g, %.3g, %.3g, %.3g, %gulps, %g\n",        \
              (double)xmin, (double)xmax, (double)ymin, (double)ymax, (double)SVMLULP, (double)(Sleef_currentTimeMicros() - t) / NITER); \
    })
