/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.jboss.arquillian.extension.screenRecorder;

import java.awt.AWTException;
import java.awt.Rectangle;
import java.awt.Robot;
import java.awt.Toolkit;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import javax.imageio.ImageIO;
import org.apache.commons.io.FileUtils;
import org.jboss.arquillian.config.descriptor.api.ArquillianDescriptor;
import org.jboss.arquillian.config.descriptor.api.ExtensionDef;
import org.jboss.arquillian.container.spi.event.container.AfterDeploy;
import org.jboss.arquillian.container.spi.event.container.AfterUnDeploy;
import org.jboss.arquillian.core.api.Instance;
import org.jboss.arquillian.core.api.annotation.Inject;
import org.jboss.arquillian.core.api.annotation.Observes;
import org.jboss.arquillian.test.spi.TestClass;
import org.jboss.arquillian.test.spi.TestResult;
import org.jboss.arquillian.test.spi.event.suite.After;
import org.jboss.arquillian.test.spi.event.suite.Before;

/**
 *
 * @author pmensik
 */
public class LifecycleObserver {

    @Inject
    private Instance<TestResult> testResult;

    private RecorderConfiguration configuration;
    private ScreenRecorder recorder;

    private boolean shouldTakeScreenshots;
    private boolean shouldRecordVideo;

    public void initConfiguration(@Observes ArquillianDescriptor descriptor) throws IOException {
        configuration = new RecorderConfiguration();
        for (ExtensionDef extension : descriptor.getExtensions()) {
            if (extension.getExtensionName().equalsIgnoreCase(SystemProperties.SCREEN_RECORDER)) {
                configuration.setProperties(extension.getExtensionProperties());
            }
        }
        shouldRecordVideo = configuration.shouldRecordVideo();
        shouldTakeScreenshots = configuration.shouldTakeScreenshots();

        FileUtils.deleteDirectory(configuration.getRootFolder());

        if (shouldRecordVideo) {
            configuration.getVideoFolder().mkdirs();
        }
        if (shouldTakeScreenshots) {
            configuration.getScreenshotFolder().mkdirs();
        }
    }

    public void executeBeforeStart(@Observes AfterDeploy event) {
        if (configuration.getRecordingType().equals(RecordingType.SUITE) && shouldRecordVideo) {
            startRecording();
        }
    }

    public void executeBeforeStop(@Observes AfterUnDeploy event) {
        if (configuration.getRecordingType().equals(RecordingType.SUITE) && shouldRecordVideo) {
            stopRecording(configuration.getVideoFolder(), configuration.getVideoName());
        }
    }

    public void executeBeforeTest(@Observes Before event) throws AWTException, IOException {
        //timer task
        if (configuration.getRecordingType().equals(RecordingType.TEST) || configuration.getRecordingType().equals(RecordingType.FAILURE)) {
            startRecording();
        }
        if (shouldTakeScreenshots) {
            createScreenshotAndSaveFile(event.getTestClass(), event.getTestMethod(), "before");
        }
    }

    public void executeAfterTest(@Observes After event) throws AWTException, IOException {
        if (configuration.getRecordingType().equals(RecordingType.TEST) || (configuration.getRecordingType().equals(RecordingType.FAILURE) && testResult.get().getStatus().equals(TestResult.Status.FAILED))) {
            File testClassDirectory = prepareDirectory(configuration.getVideoFolder(), event.getTestClass());
            stopRecording(testClassDirectory, event.getTestMethod().getName());
        } else if (configuration.getRecordingType().equals(RecordingType.FAILURE)) {
            recorder.stopRecording(null);
        }
        if (shouldTakeScreenshots) {
            createScreenshotAndSaveFile(event.getTestClass(), event.getTestMethod(), "after");
        }
    }

    protected File prepareDirectory(File root, TestClass clazz) {
        String packageName = clazz.getJavaClass().getPackage().getName();
        String className = clazz.getJavaClass().getSimpleName();
        File directory = org.apache.commons.io.FileUtils.getFile(root, packageName, className);
        directory.mkdirs();
        return directory;
    }

    private void startRecording() {
        recorder = new ScreenRecorder(configuration.getFrameRate(), configuration.getVideoFileType());
        recorder.startRecording();
    }

    private void stopRecording(File directory, String fileName) {
        String videoName = fileName + "." + configuration.getVideoFileType();
        File video = FileUtils.getFile(directory, videoName);
        recorder.stopRecording(video);
    }

    private void createScreenshotAndSaveFile(TestClass testClass, Method testMethod, String when) throws AWTException, IOException {
        Rectangle screenSize = new Rectangle(Toolkit.getDefaultToolkit().getScreenSize());
        BufferedImage image = new Robot().createScreenCapture(screenSize);

        String imageName = testMethod.getName() + "_" + when + "." + configuration.getImageFileType();
        File testClassDirectory = prepareDirectory(configuration.getScreenshotFolder(), testClass);
        File outputFile = FileUtils.getFile(testClassDirectory, imageName);
        ImageIO.write(image, configuration.getImageFileType().toString(), outputFile);
    }
}
