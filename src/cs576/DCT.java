/*
 *
 * Copyright (C) 1994-1998, Thomas G. Lane.
 * This file is part of the Independent JPEG Group's software.
 * For conditions of distribution and use, see the accompanying README file of libjpeg.
 */

/* Modification Copyright 2017 by Jeffrey Ye */

package cs576;

import java.util.Arrays;

public class DCT {

    static final int CENTERJSAMPLE = 128;
    static final int MAXJSAMPLE = 255;
    static final int RANGE_MASK = (MAXJSAMPLE * 4 + 3);

    static int[] range_limit = new int[5 * (MAXJSAMPLE + 1) + CENTERJSAMPLE];

    static final float[] AANscaleFactor =
            {1.0f, 1.387039845f, 1.306562965f, 1.175875602f, 1.0f, 0.785694958f, 0.541196100f, 0.275899379f};

    static {
        prepareRangeLimitTable();
    }

    private static void prepareRangeLimitTable() {
        int offset = 0;
        offset += (MAXJSAMPLE + 1);
          /* First segment of "simple" table: limit[x] = 0 for x < 0 */
        Arrays.fill(range_limit, offset - (MAXJSAMPLE + 1), MAXJSAMPLE + 1, 0);
         /* Main part of "simple" table: limit[x] = x */
        for (int i = 0; i <= MAXJSAMPLE; i++)
            range_limit[offset + i] = i;
        offset += CENTERJSAMPLE;	/* Point to where post-IDCT table starts */
         /* End of simple table, rest of first half of post-IDCT table */
        for (int i = CENTERJSAMPLE; i < 2 * (MAXJSAMPLE + 1); i++)
            range_limit[offset + i] = MAXJSAMPLE;
        /* Second half of post-IDCT table */
        Arrays.fill(range_limit, offset + (2 * (MAXJSAMPLE + 1)),
                offset + (4 * (MAXJSAMPLE + 1) - CENTERJSAMPLE), 0);

        offset += (4 * (MAXJSAMPLE + 1) - CENTERJSAMPLE);
        for (int i = 0; i < CENTERJSAMPLE; i++) {
            range_limit[offset + i] = range_limit[i + (MAXJSAMPLE + 1)];
        }
    }

    /* For float AA&N IDCT method, divisors are equal to quantization
     * coefficients scaled by scalefactor[row]*scalefactor[col], where
     *   scalefactor[0] = 1
     *   scalefactor[k] = cos(k*PI/16) * sqrt(2)    for k=1..7
     * We apply a further scale factor of 8.
     * What's actually stored is 1/divisor so that the inner loop can
     * use a multiplication rather than a division.
     */
    public static float[] scaleQuantizationMatrix(final int[] matrix) {
        float[] output = new float[64];
        for (int y = 0; y < 8; y++) {
            for (int x = 0; x < 8; x++) {
                output[8 * y + x] = 1.0f / (matrix[8 * y + x]
                        * AANscaleFactor[y]
                        * AANscaleFactor[x]
                        * 8f);
            }
        }
        return output;
    }

    public static float[] scaleDequantizationMatrix(final int[] matrix) {
        float[] output = new float[64];
        for (int y = 0; y < 8; y++) {
            for (int x = 0; x < 8; x++) {
                output[8 * y + x] = matrix[8 * y + x] * AANscaleFactor[y]
                        * AANscaleFactor[x];
            }
        }
        return output;
    }


    /**
     * Perform the forward DCT on one block of samples.
     */
    public static float[][] forwardDCT(float[][] data) {
        float tmp0, tmp1, tmp2, tmp3, tmp4, tmp5, tmp6, tmp7;
        float tmp10, tmp11, tmp12, tmp13;
        float z1, z2, z3, z4, z5, z11, z13;

  /* Pass 1: process rows. */

        for (int i = 0; i < 8; i++) {
            tmp0 = data[i][0] + data[i][7];
            tmp7 = data[i][0] - data[i][7];
            tmp1 = data[i][1] + data[i][6];
            tmp6 = data[i][1] - data[i][6];
            tmp2 = data[i][2] + data[i][5];
            tmp5 = data[i][2] - data[i][5];
            tmp3 = data[i][3] + data[i][4];
            tmp4 = data[i][3] - data[i][4];
    
    /* Even part */

            tmp10 = tmp0 + tmp3;	/* phase 2 */
            tmp13 = tmp0 - tmp3;
            tmp11 = tmp1 + tmp2;
            tmp12 = tmp1 - tmp2;

            data[i][0] = tmp10 + tmp11; /* phase 3 */
            data[i][4] = tmp10 - tmp11;

            z1 = (tmp12 + tmp13) * ((float) 0.707106781); /* c4 */
            data[i][2] = tmp13 + z1;	/* phase 5 */
            data[i][6] = tmp13 - z1;
    
    /* Odd part */

            tmp10 = tmp4 + tmp5;	/* phase 2 */
            tmp11 = tmp5 + tmp6;
            tmp12 = tmp6 + tmp7;

    /* The rotator is modified from fig 4-8 to avoid extra negations. */
            z5 = (tmp10 - tmp12) * ((float) 0.382683433); /* c6 */
            z2 = ((float) 0.541196100) * tmp10 + z5; /* c2-c6 */
            z4 = ((float) 1.306562965) * tmp12 + z5; /* c2+c6 */
            z3 = tmp11 * ((float) 0.707106781); /* c4 */

            z11 = tmp7 + z3;		/* phase 5 */
            z13 = tmp7 - z3;

            data[i][5] = z13 + z2;	/* phase 6 */
            data[i][3] = z13 - z2;
            data[i][1] = z11 + z4;
            data[i][7] = z11 - z4;
        }

  /* Pass 2: process columns. */

        for (int i = 0; i < 8; i++) {
            tmp0 = data[0][i] + data[7][i];
            tmp7 = data[0][i] - data[7][i];
            tmp1 = data[1][i] + data[6][i];
            tmp6 = data[1][i] - data[6][i];
            tmp2 = data[2][i] + data[5][i];
            tmp5 = data[2][i] - data[5][i];
            tmp3 = data[3][i] + data[4][i];
            tmp4 = data[3][i] - data[4][i];
    
    /* Even part */

            tmp10 = tmp0 + tmp3;	/* phase 2 */
            tmp13 = tmp0 - tmp3;
            tmp11 = tmp1 + tmp2;
            tmp12 = tmp1 - tmp2;

            data[0][i] = tmp10 + tmp11; /* phase 3 */
            data[4][i] = tmp10 - tmp11;

            z1 = (tmp12 + tmp13) * ((float) 0.707106781); /* c4 */
            data[2][i] = tmp13 + z1; /* phase 5 */
            data[6][i] = tmp13 - z1;
    
    /* Odd part */

            tmp10 = tmp4 + tmp5;	/* phase 2 */
            tmp11 = tmp5 + tmp6;
            tmp12 = tmp6 + tmp7;

    /* The rotator is modified from fig 4-8 to avoid extra negations. */
            z5 = (tmp10 - tmp12) * ((float) 0.382683433); /* c6 */
            z2 = ((float) 0.541196100) * tmp10 + z5; /* c2-c6 */
            z4 = ((float) 1.306562965) * tmp12 + z5; /* c2+c6 */
            z3 = tmp11 * ((float) 0.707106781); /* c4 */

            z11 = tmp7 + z3;		/* phase 5 */
            z13 = tmp7 - z3;

            data[5][i] = z13 + z2; /* phase 6 */
            data[3][i] = z13 - z2;
            data[1][i] = z11 + z4;
            data[7][i] = z11 - z4;
        }

        return data;
    }


    /**
     * Perform dequantization and inverse DCT on one block of coefficients.
     *
     * @param data
     * @return
     */
    public static float[][] inverseDCT(float[][] data) {
        float tmp0, tmp1, tmp2, tmp3, tmp4, tmp5, tmp6, tmp7;
        float tmp10, tmp11, tmp12, tmp13;
        float z5, z10, z11, z12, z13;

  /* Pass 1: process columns from input, store into work array. */

        for (int i = 0; i < 8; i++) {
    /* Due to quantization, we will usually find that many of the input
     * coefficients are zero, especially the AC terms.  We can exploit this
     * by short-circuiting the IDCT calculation for any column in which all
     * the AC terms are zero.  In that case each output is equal to the
     * DC coefficient (with scale factor as needed).
     * With typical images and quantization tables, half or more of the
     * column DCT calculations can be simplified this way.
     */

            if (data[1][i] == 0 && data[2][i] == 0 &&
                    data[3][i] == 0 && data[4][i] == 0 &&
                    data[5][i] == 0 && data[6][i] == 0 &&
                    data[7][i] == 0) {
      /* AC terms all zero */
                float dcval = data[0][i];

                data[0][i] = dcval;
                data[1][i] = dcval;
                data[2][i] = dcval;
                data[3][i] = dcval;
                data[4][i] = dcval;
                data[5][i] = dcval;
                data[6][i] = dcval;
                data[7][i] = dcval;
                continue;
            }

    /* Even part */

            tmp0 = data[0][i];
            tmp1 = data[2][i];
            tmp2 = data[4][i];
            tmp3 = data[6][i];

            tmp10 = tmp0 + tmp2;	/* phase 3 */
            tmp11 = tmp0 - tmp2;

            tmp13 = tmp1 + tmp3;	/* phases 5-3 */
            tmp12 = (tmp1 - tmp3) * ((float) 1.414213562) - tmp13; /* 2*c4 */

            tmp0 = tmp10 + tmp13;	/* phase 2 */
            tmp3 = tmp10 - tmp13;
            tmp1 = tmp11 + tmp12;
            tmp2 = tmp11 - tmp12;

    /* Odd part */

            tmp4 = data[1][i];
            tmp5 = data[3][i];
            tmp6 = data[5][i];
            tmp7 = data[7][i];

            z13 = tmp6 + tmp5;		/* phase 6 */
            z10 = tmp6 - tmp5;
            z11 = tmp4 + tmp7;
            z12 = tmp4 - tmp7;

            tmp7 = z11 + z13;		/* phase 5 */
            tmp11 = (z11 - z13) * ((float) 1.414213562); /* 2*c4 */

            z5 = (z10 + z12) * ((float) 1.847759065); /* 2*c2 */
            tmp10 = ((float) 1.082392200) * z12 - z5; /* 2*(c2-c6) */
            tmp12 = ((float) -2.613125930) * z10 + z5; /* -2*(c2+c6) */

            tmp6 = tmp12 - tmp7;	/* phase 2 */
            tmp5 = tmp11 - tmp6;
            tmp4 = tmp10 + tmp5;

            data[0][i] = tmp0 + tmp7;
            data[7][i] = tmp0 - tmp7;
            data[1][i] = tmp1 + tmp6;
            data[6][i] = tmp1 - tmp6;
            data[2][i] = tmp2 + tmp5;
            data[5][i] = tmp2 - tmp5;
            data[4][i] = tmp3 + tmp4;
            data[3][i] = tmp3 - tmp4;
        }

  /* Pass 2: process rows from work array, store into output array. */
  /* Note that we must descale the results by a factor of 8 == 2**3. */


        for (int i = 0; i < 8; i++) {
    /* Rows of zeroes can be exploited in the same way as we did with columns.
     * However, the column calculation has created many nonzero AC terms, so
     * the simplification applies less often (typically 5% to 10% of the time).
     * And testing floats for zero is relatively expensive, so we don't bother.
     */

    /* Even part */

            tmp10 = data[i][0] + data[i][4];
            tmp11 = data[i][0] - data[i][4];

            tmp13 = data[i][2] + data[i][6];
            tmp12 = (data[i][2] - data[i][6]) * ((float) 1.414213562) - tmp13;

            tmp0 = tmp10 + tmp13;
            tmp3 = tmp10 - tmp13;
            tmp1 = tmp11 + tmp12;
            tmp2 = tmp11 - tmp12;

    /* Odd part */

            z13 = data[i][5] + data[i][3];
            z10 = data[i][5] - data[i][3];
            z11 = data[i][1] + data[i][7];
            z12 = data[i][1] - data[i][7];

            tmp7 = z11 + z13;
            tmp11 = (z11 - z13) * ((float) 1.414213562);

            z5 = (z10 + z12) * ((float) 1.847759065); /* 2*c2 */
            tmp10 = ((float) 1.082392200) * z12 - z5; /* 2*(c2-c6) */
            tmp12 = ((float) -2.613125930) * z10 + z5; /* -2*(c2+c6) */

            tmp6 = tmp12 - tmp7;
            tmp5 = tmp11 - tmp6;
            tmp4 = tmp10 + tmp5;

    /* Final output stage: scale down by a factor of 8 and range-limit */

            data[i][0] = range_limit[(MAXJSAMPLE + 1) + rightShift((int) (tmp0 + tmp7), 3) & RANGE_MASK];
            data[i][7] = range_limit[(MAXJSAMPLE + 1) + rightShift((int) (tmp0 - tmp7), 3) & RANGE_MASK];
            data[i][1] = range_limit[(MAXJSAMPLE + 1) + rightShift((int) (tmp1 + tmp6), 3) & RANGE_MASK];
            data[i][6] = range_limit[(MAXJSAMPLE + 1) + rightShift((int) (tmp1 - tmp6), 3) & RANGE_MASK];
            data[i][2] = range_limit[(MAXJSAMPLE + 1) + rightShift((int) (tmp2 + tmp5), 3) & RANGE_MASK];
            data[i][5] = range_limit[(MAXJSAMPLE + 1) + rightShift((int) (tmp2 - tmp5), 3) & RANGE_MASK];
            data[i][4] = range_limit[(MAXJSAMPLE + 1) + rightShift((int) (tmp3 + tmp4), 3) & RANGE_MASK];
            data[i][3] = range_limit[(MAXJSAMPLE + 1) + rightShift((int) (tmp3 - tmp4), 3) & RANGE_MASK];
        }
        return data;
    }

    private static int rightShift(int val, int shift) {
        return (val < 0 ?
                (val >> (shift)) | ((~(0)) << (32 - (shift))) :
                (val >> (shift)));
    }
}