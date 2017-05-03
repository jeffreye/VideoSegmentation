package cs576;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionListener;
import java.awt.image.BufferedImage;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Queue;

/**
 * Created by Jeffreye on 4/1/2017.
 */
public class VideoDecoder implements ActionListener, MouseMotionListener {

    private static final int MACROBLOCK_SEARCH = 64;

    class ImagePair {
        private final int width;
        private final int height;
        int[] original;
        int[] quantized;

        public ImagePair(int width, int height) {
            this.width = width;
            this.height = height;
            this.original = new int[3 * height * width];
            this.quantized = new int[3 * height * width];
        }

        public int[] blend(int mouseX, int mouseY) {

            int left = Math.max(0, mouseX - MACROBLOCK_SEARCH / 2);
            int right = Math.min(width, mouseX + MACROBLOCK_SEARCH / 2);

            int top = Math.max(0, mouseY - MACROBLOCK_SEARCH / 2);
            int bottom = Math.min(height, mouseY + MACROBLOCK_SEARCH / 2);

            for (int i = top; i < bottom; i++) {
                for (int j = left; j < right; j++) {
                    quantized[i * width + j] = original[i * width + j];
                    quantized[i * width + j + 1 * height * width] = original[i * width + j + 1 * height * width];
                    quantized[i * width + j + 2 * height * width] = original[i * width + j + 2 * height * width];
                }
            }

            return quantized;
        }
    }

    private final int foregroundQuantizationValue;
    private final int backgroundQuantizationValue;
    private final boolean gazeControl;
    private int width;
    private int height;
    private int pointerX, pointerY;
    private int videostatus;
    private int currentFrame;

    private FileChannel inputStream;
    private ArrayList<Long> framePositions;


    private BufferedImage image;
    private JFrame frame;
    private JLabel lbIm1;
    private JLabel lbText1;
    private JButton playpause;
    private JButton restart;
    private JSlider seekBar;
    private Queue<ImagePair> nextFrames;
    private Queue<ImagePair> imageBuffers;
    private Timer timer;
    private long minUpdateInterval;

    public VideoDecoder(String inputFile, int foregroundQuantizationValue, int backgroundQuantizationValue, boolean gazeControl, int fps) throws IOException {
        this.inputStream = new FileInputStream(inputFile).getChannel();
        this.foregroundQuantizationValue = foregroundQuantizationValue;
        this.backgroundQuantizationValue = backgroundQuantizationValue;
        this.gazeControl = gazeControl;

        this.videostatus = 1;
        this.currentFrame = 0;
        this.pointerX = 0;
        this.pointerY = 0;
        this.nextFrames = new ArrayDeque<>(10);
        this.imageBuffers = new ArrayDeque<>(10);

        ByteBuffer buffer = ByteBuffer.allocateDirect(3 * 4);
        inputStream.read(buffer);
        buffer.flip();

        width = buffer.getInt();
        height = buffer.getInt();
        int frameNumbers = buffer.getInt();

        framePositions = new ArrayList<>(frameNumbers);
        buffer = ByteBuffer.allocateDirect(frameNumbers * 8);
        inputStream.read(buffer);
        buffer.flip();
        for (int i = 0; i < frameNumbers; i++) {
            framePositions.add(buffer.getLong());
        }

        showWindow(fps, frameNumbers);

        // Allocate buffers
        for (int i = 0; i < 32; i++) {
            imageBuffers.add(new ImagePair(width, height));
        }
    }


    public void showWindow(int fps, int frameNumbers) {
        frame = new JFrame();

        GridBagLayout gLayout = new GridBagLayout();
        frame.getContentPane().setLayout(gLayout);
        String result = String.format("Video height: %d, width: %d", height, width);
        lbText1 = new JLabel(result);
        lbText1.setHorizontalAlignment(SwingConstants.CENTER);
        lbIm1 = new JLabel();
        image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        lbIm1.setIcon(new ImageIcon(image));
        lbIm1.addMouseMotionListener(this);

        GridBagConstraints c = new GridBagConstraints();
        c.fill = GridBagConstraints.HORIZONTAL;
        c.anchor = GridBagConstraints.CENTER;
        c.weightx = 0.5;
        c.gridx = 0;
        c.gridy = 0;
        frame.getContentPane().add(lbText1, c);


        c.fill = GridBagConstraints.HORIZONTAL;
        c.anchor = GridBagConstraints.CENTER;
        c.gridx = 0;
        c.gridy = 1;
        frame.getContentPane().add(lbIm1, c);

        playpause = new JButton("Play/Pause");
        playpause.addActionListener(this);

        c.fill = GridBagConstraints.HORIZONTAL;
        c.anchor = GridBagConstraints.CENTER;
        c.gridx = 0;
        c.gridy = 2;
        frame.getContentPane().add(playpause, c);

        restart = new JButton("Restart");
        restart.addActionListener(this);

        c.fill = GridBagConstraints.HORIZONTAL;
        c.anchor = GridBagConstraints.CENTER;
        c.gridx = 0;
        c.gridy = 3;
        frame.getContentPane().add(restart, c);

        seekBar = new JSlider();
        seekBar.setMinimum(0);
        seekBar.setMaximum(frameNumbers - 1);
        seekBar.addChangeListener(e -> {
            if (!seekBar.getValueIsAdjusting())
                return;

            try {
                currentFrame = seekBar.getValue();
                long pos = framePositions.get(currentFrame);
                while (!nextFrames.isEmpty())
                    imageBuffers.add(nextFrames.remove());
                inputStream.position(pos);
            } catch (IOException e1) {
                e1.printStackTrace();
            }
        });

        c.fill = GridBagConstraints.HORIZONTAL;
        c.anchor = GridBagConstraints.CENTER;
        c.gridx = 0;
        c.gridy = 4;
        frame.getContentPane().add(seekBar, c);

        frame.setTitle("Video Player");
        frame.pack();
        frame.setVisible(true);
        frame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
//        frame.setExtendedState(JFrame.MAXIMIZED_BOTH);


        lastUpdate = System.nanoTime();
        minUpdateInterval = (long) Math.floor(1000.0 / fps);
        timer = new Timer(1000 / (fps*2), e -> refreshImage());
        timer.setInitialDelay(0);
        timer.start();
    }

    long lastUpdate;

    private void refreshImage() {
        // Video is buffering
        if (nextFrames.size() == 0)
            return;

        if (seekBar.getValueIsAdjusting())
            return;

        currentFrame++;
        updateSlider();

        // Slider position changed
        if (nextFrames.size() == 0)
            return;



        ImagePair frame = nextFrames.remove();
        int[] buffer = gazeControl ? frame.blend(pointerX, pointerY) : frame.quantized;
        imageBuffers.add(frame);

        image.getRaster().setDataElements(0, 0, width, height, buffer);

        try {
            // End timer
            final long endTime = System.nanoTime();
            final double deltaTime = (endTime - lastUpdate) / 1e6;
            final long waitTime = Math.round(minUpdateInterval - deltaTime);
//            lbText1.setText(String.format("FPS:%d",
//            System.out.println(String.format("FPS:%d",
//                    Math.round(1000f / ((endTime - lastUpdate) / 1e6))));
            lastUpdate = endTime;
            if (waitTime > 10)
                Thread.sleep(waitTime);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }


        lbIm1.repaint();
    }

    public void decode() throws IOException {
        SegmentedFrame lastFrame = new SegmentedFrame(height, width);
        while (true) {
            // Reset to first frame
            if (inputStream.size() == inputStream.position()) {
                inputStream.position(framePositions.get(0));
                currentFrame = 0;
            }

            // Stop buffering
            if (imageBuffers.size() == 0)
                try {
                    Thread.sleep(200);
                    continue;
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }


            ImagePair rawImage = imageBuffers.remove();

            lastFrame.loadFrom(inputStream);

            if (gazeControl) {
                lastFrame.reconstruct();
                lastFrame.getRawImage(rawImage.original);
            }

            lastFrame.reconstruct(foregroundQuantizationValue, backgroundQuantizationValue);
            lastFrame.getRawImage(rawImage.quantized);

            nextFrames.add(rawImage);

        }
    }

    /*=============================================================================
    * ============================= Action Listeners ==============================
    * =============================================================================
    */

    public void playOrPauseVideo() {
        if (videostatus == 1) {
            timer.stop();
            videostatus = 0;
        } else {
            lastUpdate = System.nanoTime();
            timer.start();
            videostatus = 1;
        }
    }


    public void actionPerformed(ActionEvent e) {
        Object src = e.getSource();
        if (src == playpause) {
            playOrPauseVideo();
        } else if (src == restart) {
            seekBar.setValueIsAdjusting(true);
            seekBar.setValue(0);
            seekBar.setValueIsAdjusting(false);
        }
    }

    public void mouseMoved(MouseEvent e) {
        this.pointerX = e.getX();
        this.pointerY = e.getY();
    }

    public void mouseDragged(MouseEvent e) {

    }

    public void updateSlider() {
        seekBar.setValue(currentFrame);
    }


    public static void main(String[] argv) {
        if (argv.length >= 3 && argv[0].endsWith(".cmp")) {
            try {
                String input = argv[0];
                int quantizationForeground = Integer.parseInt(argv[1]);
                int quantizationBackground = Integer.parseInt(argv[2]);
                boolean gazeControl = Integer.parseInt(argv[3]) == 1;
                VideoDecoder decoder = new VideoDecoder(input, quantizationForeground, quantizationBackground, gazeControl, 30);
                decoder.decode();
            } catch (Exception e) {
                e.printStackTrace(System.err);
            }

        } else {
            System.err.println("Invalid arguments");
            System.err.println("Only accepts file name as argument");
        }
    }
}
