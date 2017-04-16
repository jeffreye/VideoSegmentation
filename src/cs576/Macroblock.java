package cs576;

import static java.lang.Math.*;

/**
 * Created by Jeffreye on 4/2/2017.
 */
public class Macroblock {

    private static final int blockcells = VideoEncoder.blocksize * VideoEncoder.blocksize;

    private byte[] image;
    private int layer;
    private int x;
    private int y;
    private int index;

    private byte[] Ys;

    public Macroblock(int x, int y,int index) {
        this.image = new byte[blockcells * 3];
        this.Ys = new byte[blockcells];
        this.x = x;
        this.y = y;
        this.index = index;
    }

    public void setPixel(int x, int y, byte r, byte g, byte b) {
        image[y * VideoEncoder.blocksize + x] = r;
        image[y * VideoEncoder.blocksize + x + blockcells] = g;
        image[y * VideoEncoder.blocksize + x + blockcells * 2] = b;

        Ys[y * VideoEncoder.blocksize + x] = (byte) Utils.convertToY(r, g, b);
    }

    public int getLayer() {
        return layer;
    }

    public void setLayer(int layer) {
        this.layer = layer;
    }

    public byte[] getComponentY() {
        return Ys;
    }

    public int getX() {
        return x * VideoEncoder.blocksize;
    }

    public int getY() {
        return y * VideoEncoder.blocksize;
    }

    public int getBlockIndex(){
        return index;
    }

    public void calculateDCT(byte[] outputBuffer) {
        int size = VideoEncoder.blocksize;
        for (int u = 0; u < size; u++) {
            for (int v = 0; v < size; v++) {

                double r = 0, g = 0, b = 0;
                for (int i = 0; i < size; i++) {
                    for (int j = 0; j < size; j++) {
                        r += image[i * size + j] * cos(PI / (double) size * (i + 0.5) * (double) u) * cos(PI / (double) size * ((double) j + 0.5) * (double) v);
                        g += image[i * size + j + size * size] * cos(PI / (double) size * (i + 0.5) * (double) u) * cos(PI / (double) size * ((double) j + 0.5) * (double) v);
                        b += image[i * size + j + size * size * 2] * cos(PI / (double) size * (i + 0.5) * (double) u) * cos(PI / (double) size * ((double) j + 0.5) * (double) v);
                    }
                }

                outputBuffer[u * size + v] = (byte) round(r);
                outputBuffer[u * size + v + size * size] = (byte) round(g);
                outputBuffer[u * size + v + size * size * 2] = (byte) round(b);
            }
        }
    }
}
