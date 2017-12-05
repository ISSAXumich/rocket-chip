#include <stdio.h>
#include <math.h>

int main() {
    float a[3] = {3.3, 1.1, 2.2};
    float b[3] = {1.1, 2.2, 3.3};

    float x1 = 1.1;
    float y1 = 2.2;
    float z1 = 3.3;
    float x2 = 1.1;
    float y2 = 2.2;
    float z2 = 3.3;
    float res = 0;
    // int arr_offset = 0;


    float match = sqrt((((a[0] - b[0]) * (a[0] - b[0])) + ((a[1] - b[1]) * (a[1] - b[1]))) + (((a[2] - b[2]) * (a[2] - b[2]))));

    printf("\n[[fdist MATCH]]: res=%f\n", match);

    // Load the values from array "a"
    asm volatile
    (
        "flw   fa0, -64(s0)\n\t"
    );
    asm volatile
    (
        "flw   fa1, -60(s0)\n\t"
    );
    asm volatile
    (
        "flw   fa2, -56(s0)\n\t"
    );

    // Load the values from array "b"
    asm volatile
    (
        "flw   fa3, -80(s0)\n\t"
    );
    asm volatile
    (
        "flw   fa4, -76(s0)\n\t"
    );
    asm volatile
    (
        "flw   fa5, -72(s0)\n\t"
    );

    // Calculate Euclidean distance using fa0-fa5
    asm volatile
    (
        "fdist.s   %[z], fa0, fa3\n\t"
        : [z] "=fr" (res)
    );

    // asm volatile
    // (
    //     "fdist.s   %[z], %[x], %[y]\n\t"
    //     : [z] "=fr" (res)
    //     : [x] "fr" (b[0]), [y] "fr" (b[0])
    // );
    
    if(res != match) {
        printf("\n[[fdist FAILED]]: res=%f\n", res);
        return -1;
    } else {
        printf("\n[[fdist PASSED]]: res=%f\n", res);
    }

    printf("\n[[PASSED]]\n");

    return 0;
}
