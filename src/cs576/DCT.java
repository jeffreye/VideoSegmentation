/*
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *  under the License.
 */

/* Modification Copyright 2017 by Jeffrey Ye */

package cs576;

public class DCT {
    /*
     * The book "JPEG still image data compression standard", by Pennebaker and
     * Mitchell, Chapter 4, discusses a number of approaches to the fast DCT.
     * Here's the cost, exluding modified (de)quantization, for transforming an
     * 8x8 block:
     *
     * Algorithm Adds Multiplies RightShifts Total Naive 896 1024 0 1920
     * "Symmetries" 448 224 0 672 Vetterli and 464 208 0 672 Ligtenberg Arai,
     * Agui and 464 80 0 544 Nakajima (AA&N) Feig 8x8 462 54 6 522 Fused mul/add
     * 416 (a pipe dream)
     *
     * IJG's libjpeg, FFmpeg, and a number of others use AA&N.
     *
     * It would appear that Feig does 4-5% less operations, and multiplications
     * are reduced from 80 in AA&N to only 54. But in practice:
     *
     * Benchmarks, Intel Core i3 @ 2.93 GHz in long mode, 4 GB RAM Time taken to
     * do 100 million IDCTs (less is better): Rene' Stckel's Feig, int: 45.07
     * seconds My Feig, floating point: 36.252 seconds AA&N, unrolled loops,
     * double[][] -> double[][]: 25.167 seconds
     *
     * Clearly Feig is hopeless. I suspect the performance killer is simply the
     * weight of the algorithm: massive number of local variables, large code
     * size, and lots of random array accesses.
     *
     * Also, AA&N can be optimized a lot: AA&N, rolled loops, double[][] ->
     * double[][]: 21.162 seconds AA&N, rolled loops, float[][] -> float[][]: no
     * improvement, but at some stage Hotspot might start doing SIMD, so let's
     * use float AA&N, rolled loops, float[] -> float[][]: 19.979 seconds
     * apparently 2D arrays are slow! AA&N, rolled loops, inlined 1D AA&N
     * transform, float[] transformed in-place: 18.5 seconds AA&N, previous
     * version rewritten in C and compiled with "gcc -O3" takes: 8.5 seconds
     * (probably due to heavy use of SIMD)
     *
     * Other brave attempts: AA&N, best float version converted to 16:16 fixed
     * point: 23.923 seconds
     *
     * Anyway the best float version stays. 18.5 seconds = 5.4 million
     * transforms per second per core :-)
     */

    private static final float[] dctScalingFactors = {
            (float) (0.5 / Math.sqrt(2.0)),
            (float) (0.25 / Math.cos(Math.PI / 16.0)),
            (float) (0.25 / Math.cos(2.0 * Math.PI / 16.0)),
            (float) (0.25 / Math.cos(3.0 * Math.PI / 16.0)),
            (float) (0.25 / Math.cos(4.0 * Math.PI / 16.0)),
            (float) (0.25 / Math.cos(5.0 * Math.PI / 16.0)),
            (float) (0.25 / Math.cos(6.0 * Math.PI / 16.0)),
            (float) (0.25 / Math.cos(7.0 * Math.PI / 16.0)),};

    private static final float[] idctScalingFactors = {
            (float) (2.0 * 4.0 / Math.sqrt(2.0) * 0.0625),
            (float) (4.0 * Math.cos(Math.PI / 16.0) * 0.125),
            (float) (4.0 * Math.cos(2.0 * Math.PI / 16.0) * 0.125),
            (float) (4.0 * Math.cos(3.0 * Math.PI / 16.0) * 0.125),
            (float) (4.0 * Math.cos(4.0 * Math.PI / 16.0) * 0.125),
            (float) (4.0 * Math.cos(5.0 * Math.PI / 16.0) * 0.125),
            (float) (4.0 * Math.cos(6.0 * Math.PI / 16.0) * 0.125),
            (float) (4.0 * Math.cos(7.0 * Math.PI / 16.0) * 0.125),};

    private static final float A1 = (float) (Math.cos(2.0 * Math.PI / 8.0));
    private static final float A2 = (float) (Math.cos(Math.PI / 8.0) - Math
            .cos(3.0 * Math.PI / 8.0));
    private static final float A3 = A1;
    private static final float A4 = (float) (Math.cos(Math.PI / 8.0) + Math
            .cos(3.0 * Math.PI / 8.0));
    private static final float A5 = (float) (Math.cos(3.0 * Math.PI / 8.0));

    private static final float C2 = (float) (2.0 * Math.cos(Math.PI / 8));
    private static final float C4 = (float) (2.0 * Math
            .cos(2 * Math.PI / 8));
    private static final float C6 = (float) (2.0 * Math
            .cos(3 * Math.PI / 8));
    private static final float Q = C2 - C6;
    private static final float R = C2 + C6;

    public static float[]  scaleQuantizationMatrix(final int[] matrix) {
        float[] output = new float[64];
        for (int y = 0; y < 8; y++) {
            for (int x = 0; x < 8; x++) {
                output[8 * y + x] = 1.0f / matrix[8 * y + x]
                        * dctScalingFactors[y]
                        * dctScalingFactors[x];
            }
        }
        return output;
    }

    public static float[]  scaleDequantizationMatrix(final int[] matrix) {
        float[] output = new float[64];
        for (int y = 0; y < 8; y++) {
            for (int x = 0; x < 8; x++) {
                output[8 * y + x] = matrix[8 * y + x] * idctScalingFactors[y]
                        * idctScalingFactors[x];
            }
        }
        return output;
    }

    /**
     * Fast forward Dct using AA&N. Taken from the book
     * "JPEG still image data compression standard", by Pennebaker and Mitchell,
     * chapter 4, figure "4-8".
     */
    public static void forwardDCT8(final float[] vector) {
        final float a00 = vector[0] + vector[7];
        final float a10 = vector[1] + vector[6];
        final float a20 = vector[2] + vector[5];
        final float a30 = vector[3] + vector[4];
        final float a40 = vector[3] - vector[4];
        final float a50 = vector[2] - vector[5];
        final float a60 = vector[1] - vector[6];
        final float a70 = vector[0] - vector[7];

        final float a01 = a00 + a30;
        final float a11 = a10 + a20;
        final float a21 = a10 - a20;
        final float a31 = a00 - a30;
        // Avoid some negations:
        // float a41 = -a40 - a50;
        final float neg_a41 = a40 + a50;
        final float a51 = a50 + a60;
        final float a61 = a60 + a70;

        final float a22 = a21 + a31;

        final float a23 = a22 * A1;
        final float mul5 = (a61 - neg_a41) * A5;
        final float a43 = neg_a41 * A2 - mul5;
        final float a53 = a51 * A3;
        final float a63 = a61 * A4 - mul5;

        final float a54 = a70 + a53;
        final float a74 = a70 - a53;

        vector[0] = a01 + a11;
        vector[4] = a01 - a11;
        vector[2] = a31 + a23;
        vector[6] = a31 - a23;
        vector[5] = a74 + a43;
        vector[1] = a54 + a63;
        vector[7] = a54 - a63;
        vector[3] = a74 - a43;
    }

    public static float[][] forwardDCT(final float[][] matrix) {
        float a00, a10, a20, a30, a40, a50, a60, a70;
        float a01, a11, a21, a31, neg_a41, a51, a61;
        float a22, a23, mul5, a43, a53, a63;
        float a54, a74;

        for (int i = 0; i < 8; i++) {
            a00 = matrix[i][0] + matrix[i][7];
            a10 = matrix[i][1] + matrix[i][6];
            a20 = matrix[i][2] + matrix[i][5];
            a30 = matrix[i][3] + matrix[i][4];
            a40 = matrix[i][3] - matrix[i][4];
            a50 = matrix[i][2] - matrix[i][5];
            a60 = matrix[i][1] - matrix[i][6];
            a70 = matrix[i][0] - matrix[i][7];
            a01 = a00 + a30;
            a11 = a10 + a20;
            a21 = a10 - a20;
            a31 = a00 - a30;
            neg_a41 = a40 + a50;
            a51 = a50 + a60;
            a61 = a60 + a70;
            a22 = a21 + a31;
            a23 = a22 * A1;
            mul5 = (a61 - neg_a41) * A5;
            a43 = neg_a41 * A2 - mul5;
            a53 = a51 * A3;
            a63 = a61 * A4 - mul5;
            a54 = a70 + a53;
            a74 = a70 - a53;
            matrix[i][0] = a01 + a11;
            matrix[i][4] = a01 - a11;
            matrix[i][2] = a31 + a23;
            matrix[i][6] = a31 - a23;
            matrix[i][5] = a74 + a43;
            matrix[i][1] = a54 + a63;
            matrix[i][7] = a54 - a63;
            matrix[i][3] = a74 - a43;
        }

        for (int i = 0; i < 8; i++) {
            a00 = matrix[0][i] + matrix[7][i];
            a10 = matrix[1][i] + matrix[6][i];
            a20 = matrix[2][i] + matrix[5][i];
            a30 = matrix[3][i] + matrix[4][i];
            a40 = matrix[3][i] - matrix[4][i];
            a50 = matrix[2][i] - matrix[5][i];
            a60 = matrix[1][i] - matrix[6][i];
            a70 = matrix[0][i] - matrix[7][i];
            a01 = a00 + a30;
            a11 = a10 + a20;
            a21 = a10 - a20;
            a31 = a00 - a30;
            neg_a41 = a40 + a50;
            a51 = a50 + a60;
            a61 = a60 + a70;
            a22 = a21 + a31;
            a23 = a22 * A1;
            mul5 = (a61 - neg_a41) * A5;
            a43 = neg_a41 * A2 - mul5;
            a53 = a51 * A3;
            a63 = a61 * A4 - mul5;
            a54 = a70 + a53;
            a74 = a70 - a53;
            matrix[0][i] = a01 + a11;
            matrix[4][i] = a01 - a11;
            matrix[2][i] = a31 + a23;
            matrix[6][i] = a31 - a23;
            matrix[5][i] = a74 + a43;
            matrix[1][i] = a54 + a63;
            matrix[7][i] = a54 - a63;
            matrix[3][i] = a74 - a43;
        }
        return matrix;
    }

    /**
     * Fast inverse Dct using AA&N. This is taken from the beautiful
     * http://vsr.informatik.tu-chemnitz.de/~jan/MPEG/HTML/IDCT.html which gives
     * easy equations and properly explains constants and scaling factors. Terms
     * have been inlined and the negation optimized out of existence.
     */
    public static void inverseDCT(final float[] vector) {
        // B1
        final float a2 = vector[2] - vector[6];
        final float a3 = vector[2] + vector[6];
        final float a4 = vector[5] - vector[3];
        final float tmp1 = vector[1] + vector[7];
        final float tmp2 = vector[3] + vector[5];
        final float a5 = tmp1 - tmp2;
        final float a6 = vector[1] - vector[7];
        final float a7 = tmp1 + tmp2;

        // M
        final float tmp4 = C6 * (a4 + a6);
        // Eliminate the negative:
        // float b4 = -Q*a4 - tmp4;
        final float neg_b4 = Q * a4 + tmp4;
        final float b6 = R * a6 - tmp4;
        final float b2 = a2 * C4;
        final float b5 = a5 * C4;

        // A1
        final float tmp3 = b6 - a7;
        final float n0 = tmp3 - b5;
        final float n1 = vector[0] - vector[4];
        final float n2 = b2 - a3;
        final float n3 = vector[0] + vector[4];
        final float neg_n5 = neg_b4;

        // A2
        final float m3 = n1 + n2;
        final float m4 = n3 + a3;
        final float m5 = n1 - n2;
        final float m6 = n3 - a3;
        // float m7 = n5 - n0;
        final float neg_m7 = neg_n5 + n0;

        // A3
        vector[0] = m4 + a7;
        vector[1] = m3 + tmp3;
        vector[2] = m5 - n0;
        vector[3] = m6 + neg_m7;
        vector[4] = m6 - neg_m7;
        vector[5] = m5 + n0;
        vector[6] = m3 - tmp3;
        vector[7] = m4 - a7;
    }

    public static float[][] inverseDCT(final float[][] matrix) {
        float a2, a3, a4, tmp1, tmp2, a5, a6, a7;
        float tmp4, neg_b4, b6, b2, b5;
        float tmp3, n0, n1, n2, n3, neg_n5;
        float m3, m4, m5, m6, neg_m7;

        for (int i = 0; i < 8; i++) {
            a2 = matrix[i][2] - matrix[i][6];
            a3 = matrix[i][2] + matrix[i][6];
            a4 = matrix[i][5] - matrix[i][3];
            tmp1 = matrix[i][1] + matrix[i][7];
            tmp2 = matrix[i][3] + matrix[i][5];
            a5 = tmp1 - tmp2;
            a6 = matrix[i][1] - matrix[i][7];
            a7 = tmp1 + tmp2;
            tmp4 = C6 * (a4 + a6);
            neg_b4 = Q * a4 + tmp4;
            b6 = R * a6 - tmp4;
            b2 = a2 * C4;
            b5 = a5 * C4;
            tmp3 = b6 - a7;
            n0 = tmp3 - b5;
            n1 = matrix[i][0] - matrix[i][4];
            n2 = b2 - a3;
            n3 = matrix[i][0] + matrix[i][4];
            neg_n5 = neg_b4;
            m3 = n1 + n2;
            m4 = n3 + a3;
            m5 = n1 - n2;
            m6 = n3 - a3;
            neg_m7 = neg_n5 + n0;
            matrix[i][0] = m4 + a7;
            matrix[i][1] = m3 + tmp3;
            matrix[i][2] = m5 - n0;
            matrix[i][3] = m6 + neg_m7;
            matrix[i][4] = m6 - neg_m7;
            matrix[i][5] = m5 + n0;
            matrix[i][6] = m3 - tmp3;
            matrix[i][7] = m4 - a7;
        }

        for (int i = 0; i < 8; i++) {
            a2 = matrix[2][i] - matrix[6][i];
            a3 = matrix[2][i] + matrix[6][i];
            a4 = matrix[5][i] - matrix[3][i];
            tmp1 = matrix[1][i] + matrix[7][i];
            tmp2 = matrix[3][i] + matrix[5][i];
            a5 = tmp1 - tmp2;
            a6 = matrix[2][i] - matrix[7][i];
            a7 = tmp1 + tmp2;
            tmp4 = C6 * (a4 + a6);
            neg_b4 = Q * a4 + tmp4;
            b6 = R * a6 - tmp4;
            b2 = a2 * C4;
            b5 = a5 * C4;
            tmp3 = b6 - a7;
            n0 = tmp3 - b5;
            n1 = matrix[0][i] - matrix[4][i];
            n2 = b2 - a3;
            n3 = matrix[0][i] + matrix[4][i];
            neg_n5 = neg_b4;
            m3 = n1 + n2;
            m4 = n3 + a3;
            m5 = n1 - n2;
            m6 = n3 - a3;
            neg_m7 = neg_n5 + n0;
            matrix[0][i] = m4 + a7;
            matrix[1][i] = m3 + tmp3;
            matrix[2][i] = m5 - n0;
            matrix[3][i] = m6 + neg_m7;
            matrix[4][i] = m6 - neg_m7;
            matrix[5][i] = m5 + n0;
            matrix[6][i] = m3 - tmp3;
            matrix[7][i] = m4 - a7;
        }

        return matrix;
    }
}