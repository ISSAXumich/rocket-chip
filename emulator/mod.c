#include <stdio.h>

int a[] = {11,22,33,44,55};
int b[] = {10,10,10,10,10};
int c[5];

int main() {
    int i;
    for( i = 0; i < 5; i++ ) {
        c[i] = 0;
    }

    for( i = 0; i < 5; i++ ) {
        asm volatile
        (
            "mod   %[z], %[x], %[y]\n\t"
            : [z] "=r" (c[i])
            : [x] "r" (a[i]), [y] "r" (b[i])
        );
    }

    for ( int i = 0; i < 5; i++ ) {
        if( c[i] != i+1 ) {
            printf("\n[[FAILED]]: i=%d, c[i]=%d\n",i,c[i]);
            return -1;
        }
        printf("i=%d, c[i]=%d\n",i,c[i]);
    }

    printf("\n[[PASSED]]\n");

    return 0;
}
