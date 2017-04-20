package cs576;

import static java.lang.Math.PI;
import static java.lang.Math.round;

/**
 * Created by Jeffreye on 4/15/2017.
 */
public class Utils {
    public static final int DCT_BLOCK_LENGTH = 8;
    private static final double pi_len = PI / (double) DCT_BLOCK_LENGTH;


    private static final int[] LuminanceTable = {
            16, 11, 10, 16, 24, 40, 51, 61,
            12, 12, 14, 19, 26, 58, 60, 55,
            14, 13, 16, 24, 40, 57, 69, 56,
            14, 17, 22, 29, 51, 87, 80, 62,
            18, 22, 37, 56, 68, 109, 103, 77,
            24, 35, 55, 64, 81, 104, 113, 92,
            49, 64, 78, 87, 103, 121, 120, 101,
            72, 92, 95, 98, 112, 100, 103, 99,
    };

    private static final int[] LuminanceTableDiv2 = {
            8, 6, 5, 8, 12, 20, 26, 31,
            6, 6, 7, 10, 13, 29, 30, 28,
            7, 7, 8, 12, 20, 29, 35, 28,
            7, 9, 11, 15, 26, 44, 40, 31,
            9, 11, 19, 28, 34, 55, 52, 39,
            12, 18, 28, 32, 41, 52, 57, 46,
            25, 32, 39, 44, 52, 61, 60, 51,
            36, 46, 48, 49, 56, 50, 52, 50,
    };

    private static final int[] ChrominanceTable = {
            17, 18, 24, 47, 99, 99, 99, 99,
            18, 21, 26, 66, 99, 99, 99, 99,
            24, 26, 56, 99, 99, 99, 99, 99,
            47, 66, 99, 99, 99, 99, 99, 99,
            99, 99, 99, 99, 99, 99, 99, 99,
            99, 99, 99, 99, 99, 99, 99, 99,
            99, 99, 99, 99, 99, 99, 99, 99,
            99, 99, 99, 99, 99, 99, 99, 99,
    };

    private static final int[] ChrominanceTableDiv2 = {
            9, 9, 12, 24, 50, 50, 50, 50,
            9, 11, 13, 33, 50, 50, 50, 50,
            12, 13, 28, 50, 50, 50, 50, 50,
            24, 33, 50, 50, 50, 50, 50, 50,
            50, 50, 50, 50, 50, 50, 50, 50,
            50, 50, 50, 50, 50, 50, 50, 50,
            50, 50, 50, 50, 50, 50, 50, 50,
            50, 50, 50, 50, 50, 50, 50, 50,

    };

    public static byte[] convertToRawImage(float[][] imageY,float[][] imageU,float[][] imageV){
        int height = imageY.length;
        int width = imageY[0].length;

        byte[] rawImage = new byte[height * width * 3];

        for (int k = 0; k < height; k++) {
            for (int l = 0; l < width; l++) {

                float _y = imageY[k][l];
                float _u = imageU[k][l];
                float _v = imageV[k][l];

                int pixelIndex = k * width + l;
                rawImage[pixelIndex] = (byte) round(_y + 1.402f * (_v - 128f));
                rawImage[pixelIndex + height * width] = (byte) round(_y - 0.344136f * (_u - 128f) - 0.714136f * (_v - 128f));
                rawImage[pixelIndex + height * width * 2] = (byte) round(_y - 1.772f * (_u - 128f));
            }
        }
        return rawImage;
    }

    public static byte[][] calculateDCTValues(byte[] image, int height, int width) {
        // Calculate DCT values
        // compute the DCT values for each blocks
        // NO zigzag order
        byte[][] dctValues = new byte[getDctValueSize(height, width)][];
        float[][] y = new float[DCT_BLOCK_LENGTH][DCT_BLOCK_LENGTH];
        float[][] u = new float[DCT_BLOCK_LENGTH][DCT_BLOCK_LENGTH];
        float[][] v = new float[DCT_BLOCK_LENGTH][DCT_BLOCK_LENGTH];

        int ind = 0;
        for (int i = 0; i < height; i += DCT_BLOCK_LENGTH) {
            for (int j = 0; j < width; j += DCT_BLOCK_LENGTH) {

                for (int k = 0; k < DCT_BLOCK_LENGTH; k++) {
                    for (int l = 0; l < DCT_BLOCK_LENGTH; l++) {
                        if (i + k >= height || j + l >= width) {
                            y[k][l] = 0;
                            u[k][l] = 128f;
                            v[k][l] = 128f;
                        } else {
                            int pixelIndex = (i + k) * width + j + l;
                            int r = image[pixelIndex] & 0xFF;
                            int g = image[pixelIndex + height * width] & 0xFF;
                            int b = image[pixelIndex + height * width * 2] & 0xFF;

                            y[k][l] = 0.2990f * r + 0.5870f * g + 0.1140f * b;
                            u[k][l] = 128f - 0.1687f * r + 0.3313f * g + 0.5000f * b;
                            v[k][l] = 128f + 0.5000f * r + 0.418688f * g + 0.081312f * b;
                        }
                    }
                }

                dctValues[ind++] = calculateDCTBlock(DCT.forwardDCT(y), LuminanceTable);
                dctValues[ind++] = calculateDCTBlock(DCT.forwardDCT(u), ChrominanceTable);
                dctValues[ind++] = calculateDCTBlock(DCT.forwardDCT(v), ChrominanceTable);
            }
        }
        return dctValues;
    }

    public static int calculateDCTValues(float[][] image, int height, int width, boolean isLuminance, byte[][] dctValues, int offset) {
        // Calculate DCT values
        // compute the DCT values for each blocks
        // NO zigzag order
        float[][] temp = new float[DCT_BLOCK_LENGTH][DCT_BLOCK_LENGTH];

        int ind = offset;
        for (int i = 0; i < height; i += DCT_BLOCK_LENGTH) {
            for (int j = 0; j < width; j += DCT_BLOCK_LENGTH) {

                for (int k = 0; k < DCT_BLOCK_LENGTH; k++) {
                    for (int l = 0; l < DCT_BLOCK_LENGTH; l++) {
                        if (i + k >= height || j + l >= width)
                            temp[k][l] = isLuminance ? 0f : 128f;
                        else
                            temp[k][l] = image[i + k][j + l];
                    }
                }

                dctValues[ind++] = calculateDCTBlock(DCT.forwardDCT(temp), isLuminance ? LuminanceTable : ChrominanceTable);
            }
        }
        return ind;
    }


    private static byte[] calculateDCTBlock(float[][] input, int[] quantizeTable) {
        byte[] output = new byte[64];

        int index = 0;
        for (int i = 0; i < DCT_BLOCK_LENGTH; i++) {
            for (int j = 0; j < DCT_BLOCK_LENGTH; j++) {
                output[index] = (byte) Math.round(input[i][j] / quantizeTable[index]);
                index++;
            }
        }

        return output;
    }

    public static int getDctValueSize(int height, int width) {
        int dctHeight = 1 + (height - 1) / DCT_BLOCK_LENGTH;
        int dctWidth = 1 + (width - 1) / DCT_BLOCK_LENGTH;
        return dctHeight * dctWidth * 3;
    }

    public static void calculateImage(byte[][] dctValues, int height, int width,float[][] imageY,float[][] imageU,float[][] imageV) {
        // Calculate IDCT values
        int ind = 0;
        for (int i = 0; i < height; i += DCT_BLOCK_LENGTH) {
            for (int j = 0; j < width; j += DCT_BLOCK_LENGTH) {

                float[][] y = calculateImageBlock(dctValues[ind++], LuminanceTable);
                float[][] u = calculateImageBlock(dctValues[ind++], ChrominanceTable);
                float[][] v = calculateImageBlock(dctValues[ind++], ChrominanceTable);


                for (int k = 0; k < DCT_BLOCK_LENGTH; k++) {
                    for (int l = 0; l < DCT_BLOCK_LENGTH; l++) {
                        if (i + k >= height || j + l >= width)
                            continue;

                        imageY[i+k][j+l] = y[k][l];
                        imageU[i+k][j+l] = u[k][l];
                        imageV[i+k][j+l] = v[k][l];
                    }
                }

            }
        }
    }

    public static byte[] calculateImage(byte[][] dctValues, int height, int width) {
        // Calculate IDCT values
        byte[] image = new byte[height * width * 3];
        int ind = 0;
        for (int i = 0; i < height; i += DCT_BLOCK_LENGTH) {
            for (int j = 0; j < width; j += DCT_BLOCK_LENGTH) {

                float[][] y = calculateImageBlock(dctValues[ind++], LuminanceTable);
                float[][] u = calculateImageBlock(dctValues[ind++], ChrominanceTable);
                float[][] v = calculateImageBlock(dctValues[ind++], ChrominanceTable);


                for (int k = 0; k < DCT_BLOCK_LENGTH; k++) {
                    for (int l = 0; l < DCT_BLOCK_LENGTH; l++) {
                        if (i + k >= height || j + l >= width)
                            continue;

                        float _y = y[k][l];
                        float _u = u[k][l];
                        float _v = v[k][l];

                        int pixelIndex = (i + k) * width + j + l;
                        image[pixelIndex] = (byte) round(_y + 1.402f * (_v - 128f));
                        image[pixelIndex + height * width] = (byte) round(_y - 0.344136f * (_u - 128f) - 0.714136f * (_v - 128f));
                        image[pixelIndex + height * width * 2] = (byte) round(_y - 1.772f * (_u - 128f));
                    }
                }

            }
        }
        return image;
    }

    private static float[][] calculateImageBlock(byte[] dct, int[] quantizeTable) {
        float[][] input = new float[DCT_BLOCK_LENGTH][DCT_BLOCK_LENGTH];
        int index = 0;
        for (int i = 0; i < DCT_BLOCK_LENGTH; i++) {
            for (int j = 0; j < DCT_BLOCK_LENGTH; j++) {
                input[i][j] = (dct[index] & 0xFF) * quantizeTable[index];
            }
        }
        return DCT.inverseDCT(input);
    }
}
