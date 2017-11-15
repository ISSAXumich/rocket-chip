#include <stdio.h>

int a[] = {5,5,5,5,5};
int b[] = {1,2,3,4,5};
int c[5];

int main() {
    int i;
    for( i = 0; i < 5; i++ ) {
        c[i] = 0;
    }

    for( i = 0; i < 5; i++ ) {
        asm volatile
        (
            "ninst   %[z], %[x], %[y]\n\t"
            : [z] "=r" (c[i])
            : [x] "r" (a[i]), [y] "r" (b[i])
        );
    }

    for ( int i = 0; i < 5; i++ ) {
        if( c[i] != (5 - 1 - i) ) {
             printf("\n[[FAILED]]: i=%d, c[i]=%d\n",i,c[i]);
             return -1;
        }
        printf("i=%d, c[i]=%d\n",i,c[i]);
    }

    printf("\n[[PASSED]]\n");

    return 0;
}
