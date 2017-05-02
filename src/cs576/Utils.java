package cs576;

import static java.lang.Math.pow;
import static java.lang.Math.round;

/**
 * Created by Jeffreye on 4/15/2017.
 */
public class Utils {
    public static final int DCT_BLOCK_LENGTH = 8;

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


    private static final int[] OneTable = {
            1, 1, 1, 1, 1, 1, 1, 1,
            1, 1, 1, 1, 1, 1, 1, 1,
            1, 1, 1, 1, 1, 1, 1, 1,
            1, 1, 1, 1, 1, 1, 1, 1,
            1, 1, 1, 1, 1, 1, 1, 1,
            1, 1, 1, 1, 1, 1, 1, 1,
            1, 1, 1, 1, 1, 1, 1, 1,
            1, 1, 1, 1, 1, 1, 1, 1,
    };


    private static final float[] QuantizationOneTable =
            DCT.scaleQuantizationMatrix(OneTable);

    private static final float[] DequantizationOneTable =
            DCT.scaleDequantizationMatrix(OneTable);

    static byte clamp(int v) {
        if (v > 255)
            return (byte) 255;
        else if (v < 0)
            return (byte) 0;
        return (byte) v;
    }

    public static void convertToRGB(float[][] imageY, float[][] imageU, float[][] imageV,int[] rawImage) {
        int height = imageY.length;
        int width = imageY[0].length;

        for (int k = 0; k < height; k++) {
            for (int l = 0; l < width; l++) {

                float _y = imageY[k][l];
                float _u = imageU[k][l];
                float _v = imageV[k][l];

                int pixelIndex = k * width + l;
                int r = clamp(round(_y + 1.402f * (_v - 128f)));
                int g = clamp(round(_y - 0.344136f * (_u - 128f) - 0.714136f * (_v - 128f)));
                int b = clamp(round(_y + 1.772f * (_u - 128f)));
                rawImage[pixelIndex] =  0xff000000 | ((r & 0xff) << 16) | ((g & 0xff) << 8) | (b & 0xff);
            }
        }
    }

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
                rawImage[pixelIndex] = clamp(round(_y + 1.402f * (_v - 128f)));
                rawImage[pixelIndex + height * width] = clamp(round(_y - 0.344136f * (_u - 128f) - 0.714136f * (_v - 128f)));
                rawImage[pixelIndex + height * width * 2] = clamp(round(_y + 1.772f * (_u - 128f)));
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



    public static void forwardDCTAndQuantize(float[][] imageY, float[][] imageU, float[][] imageV, int height, int width, byte[][] acValues, int[] dcValues) {
        // Calculate DCT values
        // compute the DCT values for each blocks
        // NO zigzag order
        float[][] temp = new float[DCT_BLOCK_LENGTH][DCT_BLOCK_LENGTH];

        int ind = 0;
        for (int i = 0; i < height; i += DCT_BLOCK_LENGTH) {
            for (int j = 0; j < width; j += DCT_BLOCK_LENGTH) {
                copyToTemp(height, width, true, temp, i, j, imageY);
                dcValues[ind] = calculateDCTBlock(temp, QuantizationLuminanceTable, acValues[ind]);
                ind++;

                copyToTemp(height, width, false, temp, i, j, imageU);
                dcValues[ind] = calculateDCTBlock(temp, QuantizationChrominanceTable, acValues[ind]);
                ind++;

                copyToTemp(height, width, false, temp, i, j, imageV);
                dcValues[ind] = calculateDCTBlock(temp, QuantizationChrominanceTable, acValues[ind]);
                ind++;

            }
        }
    }

    public static void forwardDCT(float[][] imageY, float[][] imageU, float[][] imageV, float[][] dctValues) {
        // Calculate DCT values
        // compute the DCT values for each blocks
        // NO zigzag order
        int height = imageY.length;
        int width = imageY[0].length;

        int ind = 0;
        for (int i = 0; i < height; i += DCT_BLOCK_LENGTH) {
            for (int j = 0; j < width; j += DCT_BLOCK_LENGTH) {
                forwardDCTBlock(imageY,i,j,true, dctValues[ind++]);
                forwardDCTBlock(imageU,i,j,false,dctValues[ind++]);
                forwardDCTBlock(imageV,i,j,false,dctValues[ind++]);
            }
        }
    }

    public static void quantize(float[] values, int quantizationStep){
        for (int i = 0; i < values.length; i++) {
            values[i] = round(values[i] / quantizationStep);
        }
    }

    public static void dequantize(float[] values, int quantizationStep){
        for (int i = 0; i < values.length; i++) {
            values[i] *= quantizationStep;
        }
    }

    static final  float[][] y = new float[DCT_BLOCK_LENGTH][DCT_BLOCK_LENGTH];
    static final  float[][] u = new float[DCT_BLOCK_LENGTH][DCT_BLOCK_LENGTH];
    static final  float[][] v = new float[DCT_BLOCK_LENGTH][DCT_BLOCK_LENGTH];
    public static void inverseDCT(float[][] dctValues, float[][] imageY, float[][] imageU, float[][] imageV) {
        // Calculate IDCT values
        int height = imageY.length;
        int width = imageY[0].length;

        int ind = 0;
        for (int i = 0; i < height; i += DCT_BLOCK_LENGTH) {
            for (int j = 0; j < width; j += DCT_BLOCK_LENGTH) {

                calculateImageBlock(dctValues[ind++], DequantizationOneTable,y);
                calculateImageBlock(dctValues[ind++], DequantizationOneTable,u);
                calculateImageBlock(dctValues[ind++], DequantizationOneTable,v);


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

//    static final float[][] temp = new float[DCT_BLOCK_LENGTH][DCT_BLOCK_LENGTH];
    private static void forwardDCTBlock(float[][] image, int x, int y, boolean isLuminance,float[] output) {
        int height = image.length;
        int width = image[0].length;

        float[][] temp = new float[DCT_BLOCK_LENGTH][DCT_BLOCK_LENGTH];
        copyToTemp(height, width, isLuminance, temp, x, y, image);
        DCT.forwardDCT(temp);

        int index = 0;
        for (int i = 0; i < DCT_BLOCK_LENGTH; i++) {
            for (int j = 0; j < DCT_BLOCK_LENGTH; j++) {
                output[index] = temp[i][j] * QuantizationOneTable[index];
                index++;
            }
        }
    }

    private static float[][] inverseDCTBlock(float[] dct, float[] quantizeTable) {
        float[][] input = new float[DCT_BLOCK_LENGTH][DCT_BLOCK_LENGTH];
        int index = 0;
        for (int i = 0; i < DCT_BLOCK_LENGTH; i++) {
            for (int j = 0; j < DCT_BLOCK_LENGTH; j++) {
                input[i][j] = dct[index] * quantizeTable[index];
                index++;
            }
        }
        return DCT.inverseDCT(input);
    }

    private static float[][] calculateImageBlock(float[] dct, float[] quantizeTable,float[][] output) {
        int index = 0;
        for (int i = 0; i < DCT_BLOCK_LENGTH; i++) {
            for (int j = 0; j < DCT_BLOCK_LENGTH; j++) {
                output[i][j] =  dct[index] * quantizeTable[index];
                index++;
            }
        }

        DCT.inverseDCT(output);

//        index = 0;
//        for (int i = 0; i < DCT_BLOCK_LENGTH; i++) {
//            for (int j = 0; j < DCT_BLOCK_LENGTH; j++) {
//                output[i][j] *= quantizeTable[index];
//                index++;
//            }
//        }
        return output;
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



    public static void inverseDCTAndDequantize(byte[][] acValues, int[] dcValues, int height, int width, float[][] imageY, float[][] imageU, float[][] imageV) {
        // Calculate IDCT values
        int ind = 0;
        for (int i = 0; i < height; i += DCT_BLOCK_LENGTH) {
            for (int j = 0; j < width; j += DCT_BLOCK_LENGTH) {

                float[][] y = calculateImageBlock(acValues[ind], DequantizationLuminanceTable, dcValues[ind]);
                ind++;
                float[][] u = calculateImageBlock(acValues[ind], DequantizationChrominanceTable, dcValues[ind]);
                ind++;
                float[][] v = calculateImageBlock(acValues[ind], DequantizationChrominanceTable, dcValues[ind]);
                ind++;


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
