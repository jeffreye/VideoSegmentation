package cs576;

import static java.lang.Math.PI;
import static java.lang.Math.sqrt;

/**
 * We shoule use https://commons.apache.org/proper/commons-imaging/jacoco/org.apache.commons.imaging.formats.jpeg.decoder/Dct.java.html#L138
 * instead
 */
public class DCT {

   /*
   * This method preforms forward DCT on a block of image data using the literal
   * method specified for a 2-D Discrete Cosine Transform. It is included as a
   * curiosity and can give you an idea of the difference in the compression
   * result (the resulting image quality) by comparing its output to the output
   * of the AAN method below. It is ridiculously inefficient.
   */


    static final double pi_len = PI / 16;
    static final double alpha = 1 / sqrt(2);

    public static float[][] forwardDCT(float input[][]) {
        float output[][] = new float[8][8];
        int v, u, x, y;
        for (v = 0; v < 8; v++) {
            for (u = 0; u < 8; u++) {

                for (x = 0; x < 8; x++) {
                    for (y = 0; y < 8; y++) {
                        output[v][u] += input[x][y]
                                * Math.cos((2 * x + 1) * u * pi_len)
                                * Math.cos((2 * y + 1) * v * pi_len);
                    }
                }

                output[v][u] *= (0.25) * ((u == 0) ? alpha : 1.0)
                        * ((v == 0) ? alpha : 1.0);
            }
        }
        return output;
    }


    public static float[][] inverseDCT(float input[][]) {
        float output[][] = new float[8][8];
        int v, u, x, y;
        for (v = 0; v < 8; v++) {
            for (u = 0; u < 8; u++) {

                for (x = 0; x < 8; x++) {
                    for (y = 0; y < 8; y++) {
                        output[v][u] += input[x][y]
                                * Math.cos((2 * x + 1) * u * pi_len)
                                * Math.cos((2 * y + 1) * v * pi_len)
                                * (u == 0 ? alpha : 1.0)
                                * (v == 0 ? alpha : 1.0);
                    }
                }

                output[v][u] *= 0.25;
            }
        }
        return output;
    }


}