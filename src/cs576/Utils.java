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

    private static final float[] QuantizationLuminanceTable =
            DCT.scaleQuantizationMatrix(LuminanceTable);

    private static final float[] DequantizationLuminanceTable =
            DCT.scaleDequantizationMatrix(LuminanceTable);

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


    private static final float[] QuantizationChrominanceTable =
            DCT.scaleQuantizationMatrix(ChrominanceTable);

    private static final float[] DequantizationChrominanceTable =
            DCT.scaleDequantizationMatrix(ChrominanceTable);


    public static byte[] convertToRGB(float[][] imageY, float[][] imageU, float[][] imageV) {
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

    public static void convertToYUV(byte[] imageBuffer, int height, int width, float[][] imageY, float[][] imageU, float[][] imageV) {
        for (int k = 0; k < height; k++) {
            for (int l = 0; l < width; l++) {
                int pixelIndex = (k) * width + l;
                int r = imageBuffer[pixelIndex] & 0xFF;
                int g = imageBuffer[pixelIndex + height * width] & 0xFF;
                int b = imageBuffer[pixelIndex + height * width * 2] & 0xFF;

                imageY[k][l] = 0.2990f * r + 0.5870f * g + 0.1140f * b;
                imageU[k][l] = 128f - 0.1687f * r - 0.3313f * g + 0.5000f * b;
                imageV[k][l] = 128f + 0.5000f * r - 0.418688f * g - 0.081312f * b;
            }
        }
    }

    public static void calculateDCTValues(float[][] imageY, float[][] imageU, float[][] imageV, int height, int width, byte[][] acValues, int[] dcValues) {
        // Calculate DCT values
        // compute the DCT values for each blocks
        // NO zigzag order
        float[][] temp = new float[DCT_BLOCK_LENGTH][DCT_BLOCK_LENGTH];

        int ind = 0;
        for (int i = 0; i < height; i += DCT_BLOCK_LENGTH) {
            for (int j = 0; j < width; j += DCT_BLOCK_LENGTH) {
                copyToTemp(height, width, true, temp, i, j, imageY);
                dcValues[ind] = calculateDCTBlock(temp, QuantizationLuminanceTable, acValues[ind] = new byte[64]);
                ind++;

                copyToTemp(height, width, false, temp, i, j, imageU);
                dcValues[ind] = calculateDCTBlock(temp, QuantizationChrominanceTable, acValues[ind] = new byte[64]);
                ind++;

                copyToTemp(height, width, false, temp, i, j, imageV);
                dcValues[ind] = calculateDCTBlock(temp, QuantizationChrominanceTable, acValues[ind] = new byte[64]);
                ind++;

            }
        }
    }

    private static void copyToTemp(int height, int width, boolean isLuminance, float[][] temp, int i, int j, float[][] image) {
        for (int k = 0; k < DCT_BLOCK_LENGTH; k++) {
            for (int l = 0; l < DCT_BLOCK_LENGTH; l++) {
                if (i + k >= height || j + l >= width)
                    temp[k][l] = isLuminance ? 0f : 128f;
                else
                    temp[k][l] = image[i + k][j + l];
            }
        }
    }


    private static int calculateDCTBlock(float[][] input, float[] quantizeTable, byte[] output) {
        int index = 0;
        DCT.forwardDCT(input);
        int ac = round(input[0][0] * quantizeTable[index]);
        for (int i = 0; i < DCT_BLOCK_LENGTH; i++) {
            for (int j = 0; j < DCT_BLOCK_LENGTH; j++) {
                output[index] = (byte) Math.round(input[i][j] * quantizeTable[index]);
                index++;
            }
        }

        return ac;
    }

    public static int getDctValueSize(int height, int width) {
        int dctHeight = 1 + (height - 1) / DCT_BLOCK_LENGTH;
        int dctWidth = 1 + (width - 1) / DCT_BLOCK_LENGTH;
        return dctHeight * dctWidth * 3;
    }

    public static void calculateImage(byte[][] acValues, int[] dcValues, int height, int width, float[][] imageY, float[][] imageU, float[][] imageV) {
        // Calculate IDCT values
        int ind = 0;
        for (int i = 0; i < height; i += DCT_BLOCK_LENGTH) {
            for (int j = 0; j < width; j += DCT_BLOCK_LENGTH) {

                float[][] y = calculateImageBlock(acValues[ind], DequantizationLuminanceTable, dcValues[ind]);ind++;
                float[][] u = calculateImageBlock(acValues[ind], DequantizationChrominanceTable, dcValues[ind]);ind++;
                float[][] v = calculateImageBlock(acValues[ind], DequantizationChrominanceTable, dcValues[ind]);ind++;


                for (int k = 0; k < DCT_BLOCK_LENGTH; k++) {
                    for (int l = 0; l < DCT_BLOCK_LENGTH; l++) {
                        if (i + k >= height || j + l >= width)
                            continue;

                        imageY[i + k][j + l] = y[k][l];
                        imageU[i + k][j + l] = u[k][l];
                        imageV[i + k][j + l] = v[k][l];
                    }
                }

            }
        }
    }

    private static float[][] calculateImageBlock(byte[] dct, float[] quantizeTable, int dc) {
        float[][] input = new float[DCT_BLOCK_LENGTH][DCT_BLOCK_LENGTH];
        int index = 0;
        for (int i = 0; i < DCT_BLOCK_LENGTH; i++) {
            for (int j = 0; j < DCT_BLOCK_LENGTH; j++) {
                input[i][j] = ((float) dct[index]) * quantizeTable[index];
                index++;
            }
        }
        input[0][0] = ((float) dc) * quantizeTable[0];
        return DCT.inverseDCT(input);
    }
}
