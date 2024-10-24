#include "export.h"

EXPORT void noop_params0() {}
EXPORT void noop_params1(void *param0) {}
EXPORT void noop_params2(void *param0, void *param1) {}
EXPORT void noop_params3(void *param0, void *param1, void *param2) {}
EXPORT void noop_params4(void *param0, void *param1, void *param2, void *param3) {}
EXPORT void noop_params5(int param0, int param1, void *param2, void *param3, void *param4) {}
EXPORT void noop_params10(int param0, int param1, void *param2, void *param3, void *param4,
                          int param5, int param6, void *param7, void *param8, void *param9) {}