/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 *   - Redistributions of source code must retain the above copyright
 *     notice, this list of conditions and the following disclaimer.
 *
 *   - Redistributions in binary form must reproduce the above copyright
 *     notice, this list of conditions and the following disclaimer in the
 *     documentation and/or other materials provided with the distribution.
 *
 *   - Neither the name of Oracle nor the names of its
 *     contributors may be used to endorse or promote products derived
 *     from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS
 * IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO,
 * THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR
 * PURPOSE ARE DISCLAIMED.  IN NO EVENT SHALL THE COPYRIGHT OWNER OR
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
 * PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR
 * PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
 * LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

// This program computes the parameters and arrays needed by the modified-ziggurat algorithm
// for sampling from either an exponential distribution with mean 1 or a normal distribution
// with mean 0 and standad deviation 1.  The four arrays needed for either kind of sampler are:
//
//    X[i] is the horizontal width of ziggurat layer i
//    Y[i] is f(X[i]), where f is the function for the exponential or normal curve
//    alias_threshold is the table of probability mass thresholds for Walker's alias method,
//       with one entry for the tail of the distributon and one entry for each overhang region
//    alias_map is the table of forwarding indices used for Walker's alias method
//
// The four parameters needed by the exponential sampler are:
//
//    exponential_number_of_layers   the number of layers in the ziggurat
//    exponential_X_0                the width of the box in layer 0 (which is the x-coordinate of the left end of the tail)
//    exponential_convex_margin      the maximum discrepancy between the curve and a certain diagonal line above it
//
// The five parameters needed by the normal sampler are:
//
//    normal_number_of_layers        the number of layers in the ziggurat
//    normal_X_0                     the width of the box in layer 0 (which is the x-coordinate of the left end of the tail)
//    normal_inflection_index        the index of the layer containing the inflection point
//    normal_convex_margin           the maximum discrepancy between the curve and a certain diagonal line above it
//    normal_concave_margin          the maximum discrepancy between the curve and a certain diagonal line below it
//
// After computing the parameters and tables, the program prints (to standard output)
// a complete Java source code file for a class named either FloatZigguratTables or
// DoubleZigguratTables, according to which precision has been requested.

// The only reason this program has been written as C code rather than Java code is that
// most of the calculations need to be performed in long double precision in order to
// be able to calculate double values of sufficient accuracy.  This code relies on
// long double math functions sqrtl, powl, expl, logl, log2l, erfl, ceill, and copysignl.

// The overall modified ziggurat algorithm closely follows the description in:
//
//     Christopher D. McFarland.  2016 (published online 24 Jun 2015).  A modified ziggurat
//     algorithm for generating exponentially and normally distributed pseudorandom numbers.
//     Journal of Statistical Computation and Simulation 86 (7), pages 1281-1294.
//     https://www.tandfonline.com/doi/abs/10.1080/00949655.2015.1060234
//     Also at https://arxiv.org/abs/1403.6870 (26 March 2014).
//
// This paper in turn refers to code available at https://bitbucket.org/cdmcfarland/fast_prng.
// This includes a file create_layers.py of Python code for constructing the tables.
// The C code here loosely follows the organization of that Python code.  However, the Python
// code appears to contain a number of errors and infelicities that have been corrected here:
//
// (1) On line 211, 1 is added to i_inflection, causing the value 205 to be printed when
//     table size is 256.  Adding 1 is not correct; the correct value for printing is 204.
//
// (2) On line 203, 3 values are dropped from the front of the array E when computing iE_max,
//     with no explanation given.  We believe this is incorrect; E[3:] should be simply E.
//
// (3) When the table elements are converted to printable strings using "map(str,data)",
//     precision is lost because the Python str function produces only 12 decimal digits.
//     In this C code, we print table entries using 17 decimal digits (format %23.16e),
//     because 17 decimal digits suffice to preserve the value of any double precision
//     value (and 16 decimal digits do not always suffice).
//
// (4) At lines 215-223, the Python code computes only a single E value for the
//     rectangle containing the inflection point of the normal distribution curve.
//     We believe it is conceptually more correct to compute two such E values,
//     one for the concave part of the curve (to the left of the inflection point)
//     and one for the convex part of the curve (to the right of the inflection point).
//
// We also observe that the McFarland paper asserts that the solver uses Brent's method,
// but the solver in the Python code does not implement Brent's method.  A proper
// implementation of Brent's method (or its predecessor, Dekker's method) alternates
// between use of the Secant Method and use of the Bisection Method according to various
// criteria, but the Python code merely tries the Secant Method for a fixed number of
// iterations and then switches to the Bisection Method for a calculated number of iterations.
// Here we have translated Brent's Method into C from the Algol code in Brent's original paper.

#include <float.h>
#include <math.h>
#include <stdio.h>
#include <assert.h>
#include <stdlib.h>
#include <string.h>

// The SIZE may be any power of 2 not greater than 2048; 128, 256, 512, and 1024
// are all plausible choices, but 256 probably makes the best space/time tradeoff.
// The number of layers in the constructed ziggurat will be slightly smaller than this.
#ifndef SIZE
#define SIZE 256
#endif

// Set USE_DOUBLE to 1 for Java routines that compute results of type double, or to 0 for float.
#ifndef USE_DOUBLE
#define USE_DOUBLE 1
#endif


#if USE_DOUBLE

typedef int64_t int_type;
typedef uint64_t uint_type;
typedef double float_type;
#define int_bits 64
#define max_int 0x7fffffffffffffff
#define max_uint 0xffffffffffffffff
#define java_int_type "long"
#define java_float_type "double"
#define java_capitalized_float_type "Double"

#else

typedef int32_t int_type;
typedef uint32_t uint_type;
typedef float float_type;
#define int_bits 32
#define max_int 0x7fffffff
#define max_uint 0xffffffff
#define java_int_type "int"
#define java_float_type "float"
#define java_capitalized_float_type "Float"

#endif

// We set SOLVER_TOLERANCE quite tight
#define SOLVER_TOLERANCE 1.0e-19L

#define PI (3.1415926535897932384626433832795L)

// Assert that two long double values are equal to within double (not long double) precision
#define check_equal(x, y) do assert(((x)>=(y) ? (x)-(y) : (y)-(x)) < DBL_EPSILON); while (0)

// The best way to compute the absolute value of a long double.
long double absl(long double x) {
  return copysignl(x, 1.0);
}

// The type of a function that accepts one long double argument and returns a long double result.
typedef long double (*longdoublefn)(long double);

// The functions we will traffic in for solving need an argument but also two
// or three parameters, of which the first is a longdoublefn and the others are
// long double values.  Because vanilla C doesn't have closures, we just arrange
// for the solver to accept three parameters and pass them in each time.
typedef long double (*solverfn)(long double, longdoublefn, long double, long double);


// The solver: find a root of function g (which has f, p1, and p2 as parameters).
// Returns a value x within bounds [a, b] such that g(x) is (close to) zero.
// Returns NaN if either a >= b or g(a) and g(b) have the same sign;
// this information can help the caller to adjust the bounds and try again.
//
// This solver uses Brent's Method, as it appears in:
//
//    R. P. Brent.  1971.  An algorithm with guaranteed convergence for finding a zero of a function.
//    The Computer Journal, Volume 14, Issue 4, 422–425.  https://doi.org/10.1093/comjnl/14.4.422
//
// We assume that LDBL_EPSILON is the correct value to use for "macheps" as used in the Algol code.

long double fsolve(solverfn g, longdoublefn f, long double p1, long double p2,
          long double a, long double b) {
  // Check the required conditions on the arguments.
  if (a >= b) return NAN;
  long double ga = g(a, f, p1, p2), gb = g(b, f, p1, p2);
  if (copysignl(1.0, ga) == copysignl(1.0, gb)) return NAN;
  // Here is Brent's Method, translated from Algol to C.  We have replaced the uses
  // of "goto" with "for" loops and have scoped the variable declarations more tightly.
  for (;;) {   // label "int:" in the Algol code
    long double c = a, gc = ga;
    long double e = b - a;
    long double d = e;
    for (;;) {   // label "ext:" in the Algol code
      if (absl(gc) < absl(gb)) {
   a = b; b = c; c = a;
   ga = gb; gb = gc; gc = ga;
      }
      long double tol = 2 * LDBL_EPSILON * absl(b) + SOLVER_TOLERANCE;
      long double m = (c - b)/2.0L;
      if (absl(m) < tol || gb == 0.0L) return b;
      // See if a bisection is forced
      if (absl(e) < tol || absl(ga) <= absl(gb)) {
   d = e = m;   // Yes, it is
      } else {
   long double s = gb/ga;
   long double p, q;
   if (a == c) {
     // Linear interpolation
     p = 2.0L * m * s;
     q = 1.0L - s;
   } else {
     // Inverse quadratic interpolation
     long double z = ga/gc, r = gb/gc;
     p = s * (2.0L*m*z*(z-r) - (b - a)*(r - 1.0L));
     q = (z - 1.0L) * (r - 1.0L) * (s - 1.0L);
   }
   if (p > 0.0L) { q = -q; } else { p = -p; }
   s = e; e = d;
   if ((2.0L*p < 3.0L*m*q - absl(tol*q)) && (p < absl(0.5L*s*q))) {
     d = p/q;
   } else {
     d = e = m;
   }
      }
      a = b; ga = gb;
      b = b + (absl(d) > tol ? d : (m > 0.0L ? tol : -tol));
      gb = g(b, f, p1, p2);
      if ((gb > 0.0L) == (gc > 0.0L)) break;  // That is, goto "int:"
      // Otherwise, goto "ext:"
    }
  }
}

// This routine accepts a discrete probability mass function P (represented as an array),
// a second array A, and an integer N that indicates their common length.
// It computes two outputs: a table of probability thresholds (returned in P) and
// a table of forwarding indices (returned in A).
// These tables are suitable for use with Walker's alias algorithm for sampling from
// the originally specified discrete probability mass function.
// For the original description of Walker's alias method, see:
//    Alastair J. Walker.  1977.  An efficient method for generating discrete random
//    variables with general distributions. ACM Trans. Math. Software 3, 3
//    (September 1977), 253-256. DOI: https://doi.org/10.1145/355744.355749
// However, his original version of the routine for building the tables runs in O(N**2) time.
// Following McFarland, we use a variant technique that is O(N), as described by Smith:
//    Warren D. Smith.  2002.  How to sample from a probability distribution.
//    Unpublished.  http://scorevoting.net/WarrenSmithPages/homepage/sampling.ps

void build_sampler(long double *P, int *A, int N) {
  long double *X = malloc((N+1)*sizeof(long double));
  int *B = malloc((N+1)*sizeof(int));
  // First step: normalize the given probability distribution and scale by N.
  long double sum = 0.0L;
  for (int k = 0; k < N; k++) sum += P[k];
  for (int k = 0; k < N; k++) P[k] = (P[k] / sum) * N;
  // Copy P into X, and add a sentinel value.
  for (int k = 0; k < N; k++) X[k] = P[k];
  X[N] = 2.0L;  // sentinel value
  // A will become the table of forwarding indices.
  // B will eventually describe a permutation on X such that every element less than 1.0
  // has a lower index than any element that is not less than 1.0.
  // Initally each is the identity map (element k contains the value k).
  for (int k = 0; k < N; k++) A[k] = k;
  for (int k = 0; k < N+1; k++) B[k] = k;
  // This next step is reminiscent of a Quicksort partition: i and j are two fingers
  // moving toward each other from opposite ends of X, and when i lands on an element
  // not less than 1.0 and j lands on an element less than 1.0, they are _logically_
  // swapped, not by updating X, but by updating the permutation in B.
  int i = 0;
  int j = N;
  for (;;) {
    while (X[B[i]] < 1.0L) i += 1;
    while (X[B[j]] >= 1.0L) j -= 1;
    if (i >= j) break;
    int temp = B[i]; B[i] = B[j]; B[j] = temp;
  }
  i = j;
  j += 1;
  // At this point, X[B[k]] < 1.0L for all k <= i, and X[B[k]] >= 1.0L for all k >= j == i+1.
  // This invariant will be maintained by the next loop, which moves i back out to the left
  // and j back out to the right.
  while (i >= 0) {
    while (X[B[j]] <= 1.0L) j += 1;
    if (j >= N) break;
    // At this point, X[B[i]] is "overfunded" and X[B[j]] is "underfunded".
    // During the sampling process, if the randomly chosen value in [0,1) is not
    // less than X[B[i]], it will be construed as a choice of B[j] rather than of j.
    // This is indicated by storing B[j] in A[B[i]].  In addition, X[B[j]] is updated
    // to reflect that fact that X[B[i]] has "funded" 1-X[B[i]] of its desired
    // probability mass.
    A[B[i]] = B[j];
    X[B[j]] -= (1.0L - X[B[i]]);
    // It may be that the "generosity" of X[B[i]] has caused X[B[j]] to become overfunded.
    // In that case, the two can be swapped (and j is incremented so that the former X[B[i]],
    // now become X[B[j]], will not be further examined).  Otherwise, i is decremented.
    // In either case, i will then indicate a new overfunded slot to be considered.
    if (X[B[j]] < 1.0L) {
      int temp = B[i]; B[i] = B[j]; B[j] = temp;
      j += 1;
    } else {
      i -= 1;
    }
  }
  // All done!  Now a sanity check.
  long double *Q = malloc(N*sizeof(long double));
  for (int k = 0; k < N; k++) Q[k] = X[k];
  for (int k = 0; k < N; k++) Q[A[k]] += (1.0L - X[k]);
  for (int k = 0; k < N; k++) check_equal(Q[k], P[k]);
  // Copy the result table in X back out into the argument P.
  for (int k = 0; k < N; k++) P[k] = X[k];
  free(Q); free(B); free(X);
}

// The function that describes the exponential distribution with mean 1.
// See https://en.wikipedia.org/wiki/Exponential_distribution
long double exponential_f(long double x) {
  return expl(-x);
}

// The cumulative distribution function for the exponential distribution with mean 1.
long double exponential_cdf(long double x) {
  return 1.0L - expl(-x);
}

// The function that describes the normal distribution with mean 0 and standard deviation 1, scaled by sqrtl(0.5L*PI).
// See https://en.wikipedia.org/wiki/Normal_distribution
long double normal_f(long double x) {
  return expl(-0.5L*x*x);
}

// The cumulative distribution function for the (right half of the) normal distribution with mean 0 and standard deviation 1.
long double normal_cdf(long double x) {
  return sqrtl(0.5L*PI) * erfl(sqrtl(0.5L)*x);
}

// A function that will be zero at an x such that the new box will have area box_area.
long double box_g(long double x, longdoublefn f, long double last_Y_i, long double box_area) {
  return x*(f(x) - last_Y_i) - box_area;
}

// A function that will be zero at an x such that, if f is normal_f, the tangent at point (x, f(x)) has slope m.
long double normal_tangent_g(long double x, longdoublefn f, long double m, long double unused) {
  return x*f(x) - m;
}

// This routine generates all the parameters and tables for one kind of sampler.
void generate_tables(char *kind) {
  // kind may be "normal" or "exponential"
  assert(!strcmp(kind, "exponential") || !strcmp(kind, "normal"));

  // SIZE must be a power of 2 (the code for Walker's alias method depends on it)
  assert((SIZE & -SIZE) == SIZE);
  // We require that SIZE <= 2048 because one place in the algorithm uses the
  // high 53 bits of a randomly chosen 64-bit integer to make a floating-point
  // (double) value after having already used the low bits to choose an integer
  // in the range [0,SIZE), and it is important that these two values be independent.
  // One consequence is that a value less than SIZE will certainly fit in a short
  // (and we will use a byte instead if SIZE <= 256).
  assert(SIZE <= 2048);

  // A number of parameters need to be declared and then filled in according to the kind.
  // The total area under the probability curve for x >= 0:
  long double total_area_under_curve;
  // The function for the probability curve and also its cumulative distribution function:
  longdoublefn f, cdf;
  // Heuristic initial bounds for using the solver to calculate the X values:
  long double initial_lower_bound, initial_upper_bound;
  if (!strcmp(kind, "exponential")) {
    printf("    // Implementation support for modified-ziggurat implementation of nextExponential()\n\n");
    total_area_under_curve = 1.0L;
    f = exponential_f; cdf = exponential_cdf;
    initial_lower_bound = 1.0L; initial_upper_bound = 10.0L;
  } else if (!strcmp(kind, "normal")) {
    printf("    // Implementation support for modified-ziggurat implementation of nextGaussian()\n\n");
    // The "total area under curve" is for x >= 0 only, so we divide sqrtl(2.0L*PI) by 2.
    total_area_under_curve = sqrtl(2.0L*PI)/2.0L;
    f = normal_f; cdf = normal_cdf;
    initial_lower_bound = 1.0L; initial_upper_bound = 4.0L;
  }
  // Make sure the claimed area under the curve is correct
  // (or think of it as a sanity check on the cdf).
  check_equal(total_area_under_curve, cdf(INFINITY) - cdf(0.0L));

  // The first task is to compute the boxes of the modified ziggurat.
  // The X values are found by an iterative solving process; after that the Y values are easy.
  long double X[SIZE], Y[SIZE];
  long double box_area = total_area_under_curve / ((long double)SIZE);
  long double lower_bound = initial_lower_bound;
  long double upper_bound = initial_upper_bound;
  long double last_Y_i = 0.0L;
  int i = 0;
  while(lower_bound * f(0.0L) > box_area) {
    // There are two solutions for X_i (a tall-skinny box and a long-flat box).
    // We want the latter, so lower_bound is reduced gradually to avoid solving
    // for the tall-skinny box.  The two values of 0.9L are purely heuristic.
    X[i] = fsolve(box_g, f, last_Y_i, box_area, lower_bound, upper_bound);
    if (isnan(X[i])) {
      lower_bound *= 0.9L;
    } else {
      last_Y_i = f(X[i]);
      upper_bound = X[i];
      lower_bound = 0.9L*X[i];
      ++i;
    }
  }
  int number_of_layers = i;
  // One _could_ think of there being an extra layer at the top with a box of width 0.
  // However, to be consistent with McFarland's description, we will not call that a layer.
  // Also, what McFarland calls an "overhanging box", we will call a "rectangle";
  // each rectangle contains part of the curve, and the rest of the curve is above the tail.
  // So there are number_of_layers boxes, numbered from 0 through (number_of_layers - 1);
  // number_of_layers rectangles (one of which, the topmost, has no box to its left),
  // numbered from 1 through number_of_layers; and a tail (which is to the right of box 0).
  // For 1 <= k < number_of_layers, rectangle i is to the right of box i.
  X[i] = 0.0L;
  // We have all the X values; nocompute the corresponding Y values.
  for (int k = 0; k < number_of_layers + 1; k++) Y[k] = f(X[k]);
  // Now we have (number_of_layers + 1) X values and (number_of_layers + 1) Y values.
  // For each i, 0 <= i <= number_of_layers, the point (X[i], Y[i]) lies on the curve.

  // The next step is to compute the differences dX and dY.
  long double dX[SIZE], dY[SIZE];
  // Note that dX is calculated one way and dY the other way;
  // that way all the difference values are positive.
  for (int k = 0; k < number_of_layers; k++) dX[k] = X[k] - X[k+1];
  for (int k = 0; k < number_of_layers; k++) dY[k] = Y[k+1] - Y[k];
  // Sanity check to ensure all the boxes have the correct area
  check_equal(X[0]*Y[0], box_area);
  for (int k = 0; k < number_of_layers - 1; k++) check_equal(X[k+1]*dY[k], box_area);
  // Now we can say that box i (0 <= i <= (number_of_layers - 1)) has width X[i] and height dY[i],
  // and rectangle i (1 <= i <= number_of_layers) has width dX[i-1] and height dY[i-1].

  // The next step is to construct a discrete probability distribution V
  // that encompasses the tail and all the overhang areas (in the rectangles).
  long double V[SIZE];
  V[0] = cdf(INFINITY) - cdf(X[0]);
  for (int k = 0; k < number_of_layers; k++) {
    V[k+1] = (cdf(X[k]) - cdf(X[k+1])) - Y[k]*dX[k];
  }
  for (int k = number_of_layers + 1; k < SIZE; k++) V[k] = 0.0L;
  // Now V[0] is the area of the tail, and V[i] (1 <= i <= number_of_layers)
  // is the area within rectangle i that lies under the curve.
  // Remaining entries are zero.  (The only reason for this zero padding
  // is to make the length of V be a power of 2, which allows generation
  // of a randomly chosen index into V to be faster, using a masking operation
  // rather than a modulus operator.)

  // Sanity check that all area under the curve is accounted for.
  long double V_sum = 0.0L;
  for (int k = 0; k < number_of_layers + 1; k++) V_sum += V[k];
  check_equal((double long)(SIZE - number_of_layers), V_sum/box_area);
  // Report some interesting statistics.
  printf("    // Fraction of the area under the curve that lies outside the layer boxes: %.4f\n", (double)(SIZE - number_of_layers)/(double)SIZE);
  printf("    // Fraction of non-box area that lies in the tail of the distribution: %.4f\n", (double)(V[0]/V_sum));
  printf("\n");

  // Use V to construct tables called "alias_threshold" and "alias_map" for use with
  // Walker's alias method for sampling a discrete distribution efficiently.
  long double alias_threshold[SIZE];
  int alias_map[SIZE];
  // Routine build_sampler normalizes V and then turns it into thresholds,
  // and also constructs the alias_map table.
  build_sampler(V, alias_map, SIZE);
  // Now produce the alias_threshold table from V by scaling it and converting to integer values.
  // This is a trick that allows direct comparison with randomly chosen integer values,
  // rather than requiring generation of a randomly chosen floating-point value.
  for (int k = 0; k < SIZE; k++) {
    if (V[k] >= 1.0L) {
      // This "shouldn't happen", but rounding errors are possible, so we defend against it
      alias_threshold[k] = max_int;
    } else {
      alias_threshold[k] = (int_type)(V[k] * max_uint - max_int);
    }
  }

  // Here each m[k] is computed as a positive value, which is therefore the negative of the
  // true slope of the diagonal line (within rectangle k+1) whose endpoints lie on the curve.
  long double m[SIZE];
  for (int k = 0; k < number_of_layers; k++) m[k] = dY[k]/dX[k];

  // Now it is time to compute and output all the parameters.
  // It is really important that each parameter be declared "final"; it allows
  // a huge speed improvement because the Java compiler can then inline the constants.
  printf("    static final int %sNumberOfLayers = %d;\n", kind, number_of_layers);
  printf("    static final int %sLayerMask = 0x%x;\n", kind, SIZE-1);
  printf("    static final int %sAliasMask = 0x%x;\n", kind, SIZE-1);
  printf("    static final int %sSignCorrectionMask = 0x%x;\n", kind, (SIZE == 256) ? 0xff : 0xffffffff);
  printf("    static final %s %sX0 = %19.17f;\n", java_float_type, kind, (double)(float_type)X[0]);
  if (!strcmp(kind, "exponential")) {
    // Within each rectangle, we want to find a point on the curve where the tangent
    // is parallel to the diagonal line of the rectangle whose slope is m.

    // The first derivative of the exponential function exp(-x) is -exp(-x), whose value
    // at X[k] is -exp(-X[k]) which is -Y[k].  So we can compare m values and Y values directly.
    // Sanity check: we expect Y[k+1] > m > Y[k].
    for (int k = 0; k < number_of_layers; k++) {
      assert(m[k] > Y[k]);
      assert(Y[k+1] > m[k]);
    }
    // Now for some math.  Within rectangle k+1, the point on the curve where the
    // tangent is parallel to that diagonal must have coordinates (-log(m[k]), m[k]).
    // The point on the diagonal directly above it (with the same x-coordinate) is
    // (-log(m[k]), Y[k+1]-m[k]*(-log(m[k])-X[k+1])).  The vertical distance between
    // them is therefore Y[k+1] - m[k]*(-log(m[k])-X[k+1]) - m[k].  We can then divide
    // this by dY[k] to normalize it to a fraction of the height of the rectangle.
    // We could have a table of all these fractions, so that we would have just the
    // right fraction for use with each rectangle; but it saves space (and loses very
    // little time) to just compute the maximum such fraction over all rectangles,
    // and then use that maximum fraction whenever processing any rectangle.
    long double convex_margin = -INFINITY;
    for (int k = 0; k < number_of_layers; k++) {
      long double X_tangent = -logl(m[k]);
      long double E = (Y[k+1] - m[k]*(X_tangent - X[k+1]) - m[k]) / dY[k];
      convex_margin = (convex_margin > E) ? convex_margin : E;
    }
    int_type scaled_convex_margin = (int_type)(convex_margin * (long double)max_int);
    printf("    static final %s %sConvexMargin = %lldL;   // unscaled convex margin = %.4f\n",
      java_int_type, kind, (long long)scaled_convex_margin, (double)convex_margin);
  } else if (!strcmp(kind, "normal")) {
    // Within each rectangle, we want to find a point on the curve where the tangent
    // is parallel to the diagonal line of the rectangle whose slope is m.

    long double inflection_point_x = 1.0L;
    int normal_inflection_index = 0;
    for (int k = 0; k < number_of_layers + 1; k++) {
      if (X[k] > inflection_point_x) ++normal_inflection_index;
    }
    // The inflection point lies within rectangle normal_inflection_index.
    // The x-coordinate of the inflection point lies between
    // X[normal_inflection_index] and X[normal_inflection_index - 1].

    // In principle we could have trouble if the inflection point lies exactly
    // on corner of a box (but it doesn't happen in practice).
    assert(X[normal_inflection_index] < inflection_point_x);
    printf("    static final int normalInflectionIndex = %d;\n", normal_inflection_index);

    // Now for some math.  The first derivative of the normal curve function exp(-x*x/2)
    // at X[k] is -X[k]*exp(-X[k]*X[k]/2) which is -X[k]*f(X[k]).  We use the function
    // normal_tangent_g with the solver to find the x-coordinate of a point on the
    // curve within rectangle k+1 where the tangent has slope m[k].  The rectangle that
    // contains the inflection point will have two such points, so that rectangle gets
    // special processing.
    // For each such tangent point, the idea is to compute the vertical distance between
    // that point and the diagonal, then divide by the height of the rectangle to normalize.
    // We could have a table of all these fractions, so that we would have just the
    // right fraction(s) for use with each rectangle; but it saves space (and loses very
    // little time) to just compute the maximum such fraction over a set of rectangles,
    // and then conservatively use that maximum fraction whenever processing any rectangle.
    // Instead of taking the maximum fraction over all rectangles (as we do for the
    // exponential function) we compute two separate maxima: one over all tangent points
    // below the diagonal (where the curve is convex) and one over all tangent points
    // above the diagonal (where the curve is concave).  Note that the rectangle containing
    // the inflection point has one of each.
    long double convex_margin = -INFINITY, concave_margin = -INFINITY;
    for (int k = 0; k < number_of_layers; k++) {
      // Process rectangle k+1
      if ((k+1) <= normal_inflection_index) {
   // The rectangle has a convex portion of the curve
   long double lower_bound = ((k+1) == normal_inflection_index) ? inflection_point_x : X[k+1];
   long double X_tangent = fsolve(normal_tangent_g, f, m[k], 0.0, lower_bound, X[k]);
   long double E = (Y[k+1] - m[k]*(X_tangent - X[k+1]) - f(X_tangent)) / dY[k];
       convex_margin = (convex_margin > E) ? convex_margin : E;
      }
      if ((k+1) >= normal_inflection_index) {
   // The rectangle has a concave portion of the curve
   long double upper_bound = ((k+1) == normal_inflection_index) ? inflection_point_x : X[k];
   long double X_tangent = fsolve(normal_tangent_g, f, m[k], 0.0, X[k+1], upper_bound);
   long double E = - (Y[k+1] - m[k]*(X_tangent - X[k+1]) - f(X_tangent)) / dY[k];
       concave_margin = (concave_margin > E) ? concave_margin : E;
      }
    }
    int_type scaled_convex_margin = (int_type)(convex_margin * (long double)max_int);
    int_type scaled_concave_margin = (int_type)(concave_margin * (long double)max_int);
    printf("    static final %s %sConvexMargin = %lldL;   // unscaled convex margin = %.4f\n",
      java_int_type, kind, (long long)scaled_convex_margin, (double)convex_margin);
    printf("    static final %s %sConcaveMargin = %lldL;   // unscaled concave margin = %.4f\n",
      java_int_type, kind, (long long)scaled_concave_margin, (double)concave_margin);
  }
  printf("\n");

  // Output the X array
  printf("    // %s_X[i] = length of ziggurat layer i for %s distribution, scaled by 2**(-%d)\n", kind, kind, int_bits-1);
  printf("    static final %s[] %sX = {      // %d entries, which is %s_number_of_layers+1\n", java_float_type, kind, number_of_layers+1, kind);
  for (int k = 0; k < number_of_layers+1; k++) {
    if ((k & 0x3) == 0) printf("        ");
    printf("%23.16e", (float_type)X[k] / (float_type)max_int);
    if (k < number_of_layers) {
      printf(",");
      if ((k & 0x3) < 3) printf(" ");
      else printf("\n");
    } else {
      printf(" };\n");
    }
  }
  printf("\n");

  // Output the Y array
  printf("    // %s_Y[i] = value of the %s distribution function at %s_X[i], scaled by 2**(-%d)\n", kind, kind, kind, int_bits-1);
  printf("    static final %s[] %sY = {      // %d entries, which is %s_number_of_layers+1\n", java_float_type, kind, number_of_layers+1, kind);
  for (int k = 0; k < number_of_layers+1; k++) {
    if ((k & 0x3) == 0) printf("        ");
    printf("%23.16e", (float_type)Y[k] / (float_type)max_int);
    if (k < number_of_layers) {
      printf(",");
      if ((k & 0x3) < 3) printf(" ");
      else printf("\n");
    } else {
      printf(" };\n");
    }
  }
  printf("\n");

  // Output the alias_threshold array
  printf("    // alias_threshold[j] is a threshold for the probability mass function that has been\n");
  printf("    // scaled by (2**%d - 1), translated by -(2**%d), and represented as a %s value;\n", int_bits, int_bits-1, java_int_type);
  printf("    // in this way it can be directly compared to a randomly chosen %s value.\n", java_int_type);
  printf("    static final long[] %sAliasThreshold = {    // %d entries\n", kind, SIZE);
  for (int k = 0; k < SIZE; k++) {
    if ((k & 0x3) == 0) printf("        ");
    printf("%20lldL", (long long)alias_threshold[k]);
    if (k < (SIZE - 1)) {
      printf(",");
      if ((k & 0x3) < 3) printf(" ");
      else printf("\n");
    } else {
      printf(" };\n");
    }
  }
  printf("\n");

  // Output the alias_map array
  char *small_int_type = (SIZE <= 256) ? "byte" : "short";
  int map_items_per_line = (SIZE == 256) ? 8 : 16;
  printf("    static final %s[] %sAliasMap = {    // %d entries\n", small_int_type, kind, SIZE);
  for (int k = 0; k < SIZE; k++) {
    if ((k % map_items_per_line) == 0) printf("        ");
    if (SIZE == 256) printf("(byte)");
    printf("%3d", alias_map[k]);
    if (k < (SIZE - 1)) {
      printf(",");
      if ((k % map_items_per_line) < (map_items_per_line - 1)) printf(" ");
      else printf("\n");
    } else {
      printf(" };\n");
    }
  }
  printf("\n");
}

int main(int argc, char *argv[]) {
  printf("// This Java source file is generated automatically by the program `create_ziggurat_tables.c`.\n");
  printf("\n");
  printf("/*\n");
  printf(" * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.\n");
  printf(" * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.\n");
  printf(" *\n");
  printf(" * This code is free software; you can redistribute it and/or modify it\n");
  printf(" * under the terms of the GNU General Public License version 2 only, as\n");
  printf(" * published by the Free Software Foundation.  Oracle designates this\n");
  printf(" * particular file as subject to the \"Classpath\" exception as provided\n");
  printf(" * by Oracle in the LICENSE file that accompanied this code.\n");
  printf(" *\n");
  printf(" * This code is distributed in the hope that it will be useful, but WITHOUT\n");
  printf(" * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or\n");
  printf(" * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License\n");
  printf(" * version 2 for more details (a copy is included in the LICENSE file that\n");
  printf(" * accompanied this code).\n");
  printf(" *\n");
  printf(" * You should have received a copy of the GNU General Public License version\n");
  printf(" * 2 along with this work; if not, write to the Free Software Foundation,\n");
  printf(" * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.\n");
  printf(" *\n");
  printf(" * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA\n");
  printf(" * or visit www.oracle.com if you need additional information or have any\n");
  printf(" * questions.\n");
  printf(" */\n");
  printf("package java.util;\n");
  printf("\n");
  printf("class %sZigguratTables {\n", java_capitalized_float_type);
  printf("\n");
  generate_tables("exponential");
  generate_tables("normal");
  printf("}\n");
}
