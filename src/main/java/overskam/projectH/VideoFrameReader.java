package overskam.projectH;

import javafx.scene.image.Image;
import org.bytedeco.javacv.FFmpegFrameGrabber;
import org.bytedeco.javacv.Frame;
import org.bytedeco.javacv.FrameGrabber;
import org.bytedeco.javacv.JavaFXFrameConverter;
import org.bytedeco.javacv.Java2DFrameConverter;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;

import java.io.File;

public class VideoFrameReader implements AutoCloseable {

    private FFmpegFrameGrabber grabber;
    private final JavaFXFrameConverter converter = new JavaFXFrameConverter();
    private final Java2DFrameConverter imageFileConverter = new Java2DFrameConverter();
    private int currentFrameIndex = -1;

    public Image openVideoAndReadFirstFrame(File videoFile) throws FrameGrabber.Exception {
        close();

        grabber = new FFmpegFrameGrabber(videoFile);
        grabber.start();

        Frame frame = grabber.grabImage();

        if (frame == null)
            throw new FrameGrabber.Exception("Could not read first video frame");

        currentFrameIndex = 0;

        return converter.convert(frame);
    }

    public Image readNextFrame() throws FrameGrabber.Exception {
        if (grabber == null)
            return null;

        Frame frame = grabber.grabImage();

        if (frame == null)
            return null;

        currentFrameIndex++;
        return converter.convert(frame);
    }

    public Image readFrameAt(int frameIndex) throws FrameGrabber.Exception {
        if (grabber == null || frameIndex < 0)
            return null;

        grabber.setVideoFrameNumber(frameIndex);

        Frame frame = grabber.grabImage();

        if (frame == null)
            return null;

        currentFrameIndex = frameIndex;

        return converter.convert(frame);
    }

    public Image readPreviousFrame() throws FrameGrabber.Exception {
        if (currentFrameIndex <= 0) {
            return null;
        }

        return readFrameAt(currentFrameIndex - 1);
    }

    public int getCurrentFrameIndex() {
        return currentFrameIndex;
    }

    public void saveFrameAsJpeg(int frameIndex, File outputFile)
            throws FrameGrabber.Exception, IOException {
        if (grabber == null) {
            return;
        }

        if (frameIndex < 0) {
            return;
        }

        grabber.setVideoFrameNumber(frameIndex);

        Frame frame = grabber.grabImage();

        if (frame == null) {
            return;
        }

        BufferedImage bufferedImage = imageFileConverter.convert(frame);

        if (bufferedImage == null) {
            return;
        }

        ImageIO.write(bufferedImage, "jpg", outputFile);
    }

    public Image readCurrentFrameAgain() throws FrameGrabber.Exception {
        if (grabber == null || currentFrameIndex < 0) {
            return null;
        }

        return readFrameAt(currentFrameIndex);
    }

    @Override
    public void close() throws FrameGrabber.Exception {
        if (grabber != null) {
            grabber.close();
            grabber = null;
        }

        currentFrameIndex = -1;
    }
}