#include <stdio.h>
#include <stdlib.h>
#include <dlfcn.h>

int main(int argc, char** argv)
{
    void *handle;

    if (argc != 2) {
        fprintf(stderr, "Usage: %s <lib_filename_or_full_path>\n", argv[0]);
        return EXIT_FAILURE;
    }

    printf("Attempting to load library '%s'...\n", argv[1]);

    handle = dlopen(argv[1], RTLD_LAZY);

    if (handle == NULL) {
       fprintf(stderr, "Unable to load library!\n");
       return EXIT_FAILURE;
    }

    printf("Library successfully loaded!\n");

    return dlclose(handle);
} 
