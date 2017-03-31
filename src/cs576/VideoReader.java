package cs576;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;


public class VideoReader {

    JFrame frame;
    JLabel lbIm1;
    BufferedImage img;
    Timer timer;

    File file;
    InputStream is;
    int width;
    int height;

    byte[] bytes;

    public VideoReader(String filename, int width, int height, int fps) throws FileNotFoundException {
        this.width = width;
        this.height = height;

        bytes = new byte[width * height * 3];

        file = new File(filename);
        is = new FileInputStream(file);
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
            if (is.available() == 0) {
                is.close();
                is = new FileInputStream(file);
            }


            int offset = 0;
            int numRead = 0;
            while (offset < bytes.length && (numRead = is.read(bytes, offset, bytes.length - offset)) >= 0) {
                offset += numRead;
            }

            int ind = 0;
            for (int y = 0; y < height; y++) {

                for (int x = 0; x < width; x++) {

                    byte a = 0;
                    byte r = bytes[ind];
                    byte g = bytes[ind + height * width];
                    byte b = bytes[ind + height * width * 2];

                    int pix = 0xff000000 | ((r & 0xff) << 16) | ((g & 0xff) << 8) | (b & 0xff);
                    img.setRGB(x, y, pix);
                    ind++;
                }
            }

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
