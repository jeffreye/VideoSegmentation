package cs576;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;


public class VideoReader {

    JFrame frame;
    JLabel lbIm1;
    BufferedImage img;
    Timer timer;

    File file;
    FileChannel is;
    int width;
    int height;

    ByteBuffer bytes;

    public VideoReader(String filename, int width, int height, int fps) throws FileNotFoundException {
        this.width = width;
        this.height = height;

        bytes = ByteBuffer.allocate(width * height * 3);

        file = new File(filename);
        is = new FileInputStream(file).getChannel();
        img = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);

        timer = new Timer(1000 / fps, e -> readImage());
        timer.setInitialDelay(0);

    }

    public void start(){
        frame = new JFrame();
        GridBagLayout gLayout = new GridBagLayout();
        frame.getContentPane().setLayout(gLayout);
        String result = String.format("Video height: %d, width: %d", height, width);
        JLabel lbText1 = new JLabel(result);
        lbText1.setHorizontalAlignment(SwingConstants.CENTER);

        lbIm1 = new JLabel(new ImageIcon(img));

        GridBagConstraints c = new GridBagConstraints();
        c.fill = GridBagConstraints.HORIZONTAL;
        c.anchor = GridBagConstraints.CENTER;
        c.weightx = 0.5;
        c.gridx = 0;
        c.gridy = 0;
        frame.getContentPane().add(lbText1, c);


        c.fill = GridBagConstraints.HORIZONTAL;
        c.gridx = 0;
        c.gridy = 1;
        frame.getContentPane().add(lbIm1, c);

        frame.setTitle("Video Player");
        frame.pack();
        frame.setVisible(true);
        frame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);

        timer.start();
    }


    void readImage() {
        try {
            if (is.position() == is.size()) {
                is.position(0);
            }


            bytes.clear();
            while (bytes.hasRemaining() && is.read(bytes) >= 0) {
            }

            // Test for DCT/IDCT
            float[][] imageY = new float[height][width];
            float[][] imageU = new float[height][width];
            float[][] imageV = new float[height][width];
            float[][] dctValues = new float[Utils.getDctValueSize(height,width)][64];

            Utils.convertToYUV(bytes.array(),height,width,imageY,imageU,imageV);
            Utils.forwardDCT(imageY,imageU,imageV,dctValues);
            for (int i = 0; i < dctValues.length; i++) {
                Utils.quantize(dctValues[i],1);
                Utils.dequantize(dctValues[i],1);
            }
            Utils.inverseDCT(dctValues,imageY,imageU,imageV);
            int[] rawImage = new int[height*width];
            Utils.convertToRGB(imageY,imageU,imageV,rawImage);

            img.getRaster().setDataElements(0,0,width,height,rawImage);

            // Use labels to display the images
            lbIm1.setIcon(new ImageIcon(img));

        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    public static void main(String[] args) {
        try {
            String filename = args[0];
            int width = Integer.parseInt(args[1]);
            int height = Integer.parseInt(args[2]);
            int fps = Integer.parseInt(args[3]);
            VideoReader ren = new VideoReader(filename, width, height, fps);
            ren.start();
        } catch (FileNotFoundException e) {
            System.err.print("Cannot find the file");
        } catch (ArrayIndexOutOfBoundsException e) {
            System.err.print("Incorrect argument");
        }
    }

}
