
package Quiz1;
import java.awt.AlphaComposite;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Composite;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.EventQueue;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Frame;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.Image;
import java.awt.Point;
import java.awt.RenderingHints;
import java.awt.Toolkit;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionListener;
import java.awt.event.WindowEvent;
import java.awt.event.WindowListener;
import java.awt.image.BufferedImage;
import java.awt.image.PixelGrabber;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.lang.Exception;
import java.lang.Integer;
import java.lang.InterruptedException;
import java.lang.Math;
import java.lang.Object;
import java.lang.OutOfMemoryError;
import java.lang.SecurityException;
import java.lang.String;
import java.lang.System;
import java.lang.Thread;
import java.net.URL;
import java.net.NoRouteToHostException;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;
import java.util.Vector;

import javax.imageio.ImageIO;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JColorChooser;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSlider;
import javax.swing.KeyStroke;
import javax.swing.SwingConstants;
import javax.swing.Timer;
import javax.swing.UIManager;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.event.MouseInputListener;
import javax.swing.filechooser.FileFilter;

public final class DrawingPanel extends FileFilter
    implements ActionListener, MouseMotionListener, Runnable, WindowListener {
    // inner class to represent one frame of an animated GIF
    private static class ImageFrame {
        public Image image;
        public int delay;
        
        public ImageFrame(Image image, int delay) {
            this.image = image;
            this.delay = delay / 10;   // strangely, gif stores delay as sec/100
        }
    }
    
    // class constants
    public static final String ANIMATED_PROPERTY   = "drawingpanel.animated";
    public static final String AUTO_ENABLE_ANIMATION_ON_SLEEP_PROPERTY = "drawingpanel.animateonsleep";
    public static final String DIFF_PROPERTY       = "drawingpanel.diff";
    public static final String HEADLESS_PROPERTY   = "drawingpanel.headless";
    public static final String MULTIPLE_PROPERTY   = "drawingpanel.multiple";
    public static final String SAVE_PROPERTY       = "drawingpanel.save";
    public static final String ANIMATION_FILE_NAME = "_drawingpanel_animation_save.txt";
    private static final String TITLE              = "Drawing Panel";
    private static final String COURSE_WEB_SITE = "http://courses.cs.washington.edu/courses/cse142/CurrentQtr/drawingpanel.txt";
    private static final Color GRID_LINE_COLOR     = new Color(64, 64, 64, 128);
    private static final int GRID_SIZE             = 10;      // 10px between grid lines
    private static final int DELAY                 = 100;     // delay between repaints in millis
    private static final int MAX_FRAMES            = 100;     // max animation frames
    private static final int MAX_SIZE              = 10000;   // max width/height
    private static final boolean DEBUG             = false;
    private static final boolean SAVE_SCALED_IMAGES = true;   // if true, when panel is zoomed, saves images at that zoom factor
    private static int instances = 0;
    private static Thread shutdownThread = null;
    
    private static void checkAnimationSettings() {
        try {
            File settingsFile = new File(ANIMATION_FILE_NAME);
            if (settingsFile.exists()) {
                Scanner input = new Scanner(settingsFile);
                String animationSaveFileName = input.nextLine();
                input.close();
                // *** TODO: delete the file
                System.out.println("***");
                System.out.println("*** DrawingPanel saving animated GIF: " + 
                        new File(animationSaveFileName).getName());
                System.out.println("***");
                settingsFile.delete();

                System.setProperty(ANIMATED_PROPERTY, "1");
                System.setProperty(SAVE_PROPERTY, animationSaveFileName);
            }
        } catch (Exception e) {
            if (DEBUG) {
                System.out.println("error checking animation settings: " + e);
            }
        }
    }
    
    private static boolean hasProperty(String name) {
        try {
            return System.getProperty(name) != null;
        } catch (SecurityException e) {
            if (DEBUG) System.out.println("Security exception when trying to read " + name);
            return false;
        }
    }
    
    private static boolean propertyIsTrue(String name) {
        try {
            String prop = System.getProperty(name);
            return prop != null && (prop.equalsIgnoreCase("true") || prop.equalsIgnoreCase("yes") || prop.equalsIgnoreCase("1"));
        } catch (SecurityException e) {
            if (DEBUG) System.out.println("Security exception when trying to read " + name);
            return false;
        }
    }
    
    /*
    private static boolean propertyIsFalse(String name) {
        try {
            String prop = System.getProperty(name);
            return prop != null && (prop.equalsIgnoreCase("false") || prop.equalsIgnoreCase("no") || prop.equalsIgnoreCase("0"));
        } catch (SecurityException e) {
            if (DEBUG) System.out.println("Security exception when trying to read " + name);
            return false;
        }
    }
    */
    
    // Returns whether the 'main' thread is still running.
    private static boolean mainIsActive() {
        ThreadGroup group = Thread.currentThread().getThreadGroup();
        int activeCount = group.activeCount();
        
        // look for the main thread in the current thread group
        Thread[] threads = new Thread[activeCount];
        group.enumerate(threads);
        for (int i = 0; i < threads.length; i++) {
            Thread thread = threads[i];
            String name = ("" + thread.getName()).toLowerCase();
            if (name.indexOf("main") >= 0 || 
                name.indexOf("testrunner-assignmentrunner") >= 0) {
                // found main thread!
                // (TestRunnerApplet's main runner also counts as "main" thread)
                return thread.isAlive();
            }
        }
        
        // didn't find a running main thread; guess that main is done running
        return false;
    }
    
    private static boolean usingDrJava() {
        try {
            return System.getProperty("drjava.debug.port") != null ||
                System.getProperty("java.class.path").toLowerCase().indexOf("drjava") >= 0;
        } catch (SecurityException e) {
            // running as an applet, or something
            return false;
        }
    }
    
    private class ImagePanel extends JPanel {
        private static final long serialVersionUID = 0;
        private Image image;
        
        public ImagePanel(Image image) {
            setImage(image);
            setBackground(Color.WHITE);
            setPreferredSize(new Dimension(image.getWidth(this), image.getHeight(this)));
            setAlignmentX(0.0f);
        }
        
        public void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g;
            if (currentZoom != 1) {
                g2.scale(currentZoom, currentZoom);
            }
            g2.drawImage(image, 0, 0, this);
            
            // possibly draw grid lines for debugging
            if (gridLines) {
                g2.setPaint(GRID_LINE_COLOR);
                for (int row = 1; row <= getHeight() / GRID_SIZE; row++) {
                    g2.drawLine(0, row * GRID_SIZE, getWidth(), row * GRID_SIZE);
                }
                for (int col = 1; col <= getWidth() / GRID_SIZE; col++) {
                    g2.drawLine(col * GRID_SIZE, 0, col * GRID_SIZE, getHeight());
                }
            }
        }
        
        public void setImage(Image image) {
            this.image = image;
            repaint();
        }
    }

    // fields
    private int width, height;             // dimensions of window frame
    private JFrame frame;                  // overall window frame
    private JPanel panel;                  // overall drawing surface
    private ImagePanel imagePanel;         // real drawing surface
    private BufferedImage image;           // remembers drawing commands
    private Graphics2D g2;                 // graphics context for painting
    private JLabel statusBar;              // status bar showing mouse position
    private JFileChooser chooser;          // file chooser to save files
    private long createTime;               // time at which DrawingPanel was constructed
    private Timer timer;                   // animation timer
    private ArrayList<ImageFrame> frames;  // stores frames of animation to save
    private Gif89Encoder encoder;
    // private FileOutputStream stream;
    private Color backgroundColor = Color.WHITE;
    private String callingClassName;       // name of class that constructed this panel
    private boolean animated = false;      // changes to true if sleep() is called
    private boolean PRETTY = true;         // true to anti-alias
    private boolean gridLines = false;
    private int instanceNumber;
    private int currentZoom = 1;
    private int initialPixel;              // initial value in each pixel, for clear()
    
    // construct a drawing panel of given width and height enclosed in a window
    public DrawingPanel(int width, int height) {
        if (width < 0 || width > MAX_SIZE || height < 0 || height > MAX_SIZE) {
            throw new IllegalArgumentException("Illegal width/height: " + width + " x " + height);
        }
        
        checkAnimationSettings();
        
        synchronized (getClass()) {
            instances++;
            instanceNumber = instances;  // each DrawingPanel stores its own int number
            
            if (shutdownThread == null && !usingDrJava()) {
                shutdownThread = new Thread(new Runnable() {
                    // Runnable implementation; used for shutdown thread.
                    public void run() {
                        try {
                            while (true) {
                                // maybe shut down the program, if no more DrawingPanels are onscreen
                                // and main has finished executing
                                if ((instances == 0 || shouldSave()) && !mainIsActive()) {
                                    try {
                                        System.exit(0);
                                    } catch (SecurityException sex) {}
                                }

                                Thread.sleep(250);
                            }
                        } catch (Exception e) {}
                    }
                });
                shutdownThread.setPriority(Thread.MIN_PRIORITY);
                shutdownThread.start();
            }
        }
        this.width = width;
        this.height = height;
        
        if (DEBUG) System.out.println("w=" + width + ",h=" + height + ",anim=" + isAnimated() + ",graph=" + isGraphical() + ",save=" + shouldSave());
        
        if (isAnimated() && shouldSave()) {
            // image must be no more than 256 colors
            image = new BufferedImage(width, height, BufferedImage.TYPE_BYTE_INDEXED);
            // image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
            PRETTY = false;   // turn off anti-aliasing to save palette colors
            
            // initially fill the entire frame with the background color,
            // because it won't show through via transparency like with a full ARGB image
            Graphics g = image.getGraphics();
            g.setColor(backgroundColor);
            g.fillRect(0, 0, width + 1, height + 1);
        } else {
            image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        }
        initialPixel = image.getRGB(0, 0);
        
        g2 = (Graphics2D) image.getGraphics();
        g2.setColor(Color.BLACK);
        if (PRETTY) {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        }
        
        if (isAnimated()) {
            initializeAnimation();
        }
        
        if (isGraphical()) {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception e) {}
            
            statusBar = new JLabel(" ");
            statusBar.setBorder(BorderFactory.createLineBorder(Color.BLACK));
            
            panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
            panel.setBackground(backgroundColor);
            panel.setPreferredSize(new Dimension(width, height));
            imagePanel = new ImagePanel(image);
            imagePanel.setBackground(backgroundColor);
            panel.add(imagePanel);
            
            // listen to mouse movement
            panel.addMouseMotionListener(this);
            
            // main window frame
            frame = new JFrame(TITLE);
            // frame.setResizable(false);
            frame.addWindowListener(this);
            // JPanel center = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 0));
            JScrollPane center = new JScrollPane(panel);
            // center.add(panel);
            frame.getContentPane().add(center);
            frame.getContentPane().add(statusBar, "South");
            frame.setBackground(Color.DARK_GRAY);

            // menu bar
            setupMenuBar();
            
            frame.pack();
            center(frame);
            frame.setVisible(true);
            if (!shouldSave()) {
                toFront(frame);
            }
            
            // repaint timer so that the screen will update
            createTime = System.currentTimeMillis();
            timer = new Timer(DELAY, this);
            timer.start();
        } else if (shouldSave()) {
            // headless mode; just set a hook on shutdown to save the image
            callingClassName = getCallingClassName();
            try {
                Runtime.getRuntime().addShutdownHook(new Thread(this));
            } catch (Exception e) {
                if (DEBUG) System.out.println("unable to add shutdown hook: " + e);
            }
        }
    }
    
    // method of FileFilter interface
    public boolean accept(File file) {
        return file.isDirectory() ||
            (file.getName().toLowerCase().endsWith(".png") || 
             file.getName().toLowerCase().endsWith(".gif"));
    }
    
    // used for an internal timer that keeps repainting
    public void actionPerformed(ActionEvent e) {
        if (e.getSource() instanceof Timer) {
            // redraw the screen at regular intervals to catch all paint operations
            panel.repaint();
            if (shouldDiff() &&
                System.currentTimeMillis() > createTime + 4 * DELAY) {
                String expected = System.getProperty(DIFF_PROPERTY);
                try {
                    String actual = saveToTempFile();
                    DiffImage diff = new DiffImage(expected, actual);
                    diff.frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
                } catch (IOException ioe) {
                    System.err.println("Error diffing image: " + ioe);
                }
                timer.stop();
            } else if (shouldSave() && readyToClose()) {
                // auto-save-and-close if desired
                try {
                    if (isAnimated()) {
                        saveAnimated(System.getProperty(SAVE_PROPERTY));
                    } else {
                        save(System.getProperty(SAVE_PROPERTY));
                    }
                } catch (IOException ioe) {
                    System.err.println("Error saving image: " + ioe);
                }
                exit();
            }
        } else if (e.getActionCommand().equals("Exit")) {
            exit();
        } else if (e.getActionCommand().equals("Compare to File...")) {
            compareToFile();
        } else if (e.getActionCommand().equals("Compare to Web File...")) {
            new Thread(new Runnable() {
                public void run() {
                    compareToURL();
                }
            }).start();
        } else if (e.getActionCommand().equals("Save As...")) {
            saveAs();
        } else if (e.getActionCommand().equals("Save Animated GIF...")) {
            saveAsAnimated();
        } else if (e.getActionCommand().equals("Zoom In")) {
            zoom(currentZoom + 1);
        } else if (e.getActionCommand().equals("Zoom Out")) {
            zoom(currentZoom - 1);
        } else if (e.getActionCommand().equals("Zoom Normal (100%)")) {
            zoom(1);
        } else if (e.getActionCommand().equals("Grid Lines")) {
            setGridLines(((JCheckBoxMenuItem) e.getSource()).isSelected());
        } else if (e.getActionCommand().equals("About...")) {
            JOptionPane.showMessageDialog(frame,
                    "DrawingPanel\n" + 
                    "Graphical library class to support Building Java Programs textbook\n" +
                    "written by Marty Stepp and Stuart Reges\n" +
                    "University of Washington\n\n" +
                    "please visit our web site at:\n" +
                    "http://www.buildingjavaprograms.com/",
                    
                    "About DrawingPanel",
                    JOptionPane.INFORMATION_MESSAGE);
        }
    }
    
    public void addMouseListener(MouseInputListener listener) {
        panel.addMouseListener(listener);
        if (listener instanceof MouseMotionListener) {
            panel.addMouseMotionListener((MouseMotionListener) listener);
        }
    }
    
    // erases all drawn shapes/lines/colors from the panel
    public void clear() {
        int[] pixels = new int[width * height];
        for (int i = 0; i < pixels.length; i++) {
            pixels[i] = initialPixel;
        }
        image.setRGB(0, 0, width, height, pixels, 0, 1);
    }
    
    // method of FileFilter interface
    public String getDescription() {
        return "Image files (*.png; *.gif)";
    }
    
    // obtain the Graphics object to draw on the panel
    public Graphics2D getGraphics() {
        return g2;
    }
    
    // returns the drawing panel's width in pixels
    public int getHeight() {
        return height;
    }
     
    // returns the drawing panel's pixel size (width, height) as a Dimension object
    public Dimension getSize() {
        return new Dimension(width, height);
    }
    
    // returns the drawing panel's width in pixels
    public int getWidth() {
        return width;
    }
    
    // returns panel's current zoom factor
    public int getZoom() {
        return currentZoom;
    }
    
    // listens to mouse dragging
    public void mouseDragged(MouseEvent e) {}
    
    // listens to mouse movement
    public void mouseMoved(MouseEvent e) {
        int x = e.getX() / currentZoom;
        int y = e.getY() / currentZoom;
        setStatusBarText("(" + x + ", " + y + ")");
    }
    
    // run on shutdown to save the image
    public void run() {
        if (DEBUG) System.out.println("Running shutdown hook");
        try {
            String filename = System.getProperty(SAVE_PROPERTY);
            if (filename == null) {
                filename = callingClassName + ".png";
            }
            
            if (isAnimated()) {
                saveAnimated(filename);
            } else {
                save(filename);
            }
        } catch (SecurityException e) {
        } catch (IOException e) {
            System.err.println("Error saving image: " + e);
        }
    }

    // take the current contents of the panel and write them to a file
    public void save(String filename) throws IOException {
        BufferedImage image2 = getImage();
        
        // if zoomed, scale image before saving it
        if (SAVE_SCALED_IMAGES && currentZoom != 1) {
            BufferedImage zoomedImage = new BufferedImage(width * currentZoom, height * currentZoom, image.getType());
            Graphics2D g = (Graphics2D) zoomedImage.getGraphics();
            g.setColor(Color.BLACK);
            if (PRETTY) {
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            }
            g.scale(currentZoom, currentZoom);
            g.drawImage(image2, 0, 0, imagePanel);
            image2 = zoomedImage;
        }
        
        // if saving multiple panels, append number
        // (e.g. output_*.png becomes output_1.png, output_2.png, etc.)
        if (isMultiple()) {
            filename = filename.replaceAll("\\*", String.valueOf(instanceNumber));
        }

        int lastDot = filename.lastIndexOf(".");
        String extension = filename.substring(lastDot + 1);
        
        // write file
        // TODO: doesn't save background color I don't think
        ImageIO.write(image2, extension, new File(filename));
    }
    
    // take the current contents of the panel and write them to a file
    public void saveAnimated(String filename) throws IOException {
        // add one more final frame
        if (DEBUG) System.out.println("saveAnimated(" + filename + ")");
        frames.add(new ImageFrame(getImage(), 5000));
        // encoder.continueEncoding(stream, getImage(), 5000);
        
        // Gif89Encoder gifenc = new Gif89Encoder();
        
        // add each frame of animation to the encoder
        try {
            for (int i = 0; i < frames.size(); i++) {
                ImageFrame imageFrame = frames.get(i);
                encoder.addFrame(imageFrame.image);
                encoder.getFrameAt(i).setDelay(imageFrame.delay);
                imageFrame.image.flush();
                frames.set(i, null);
            }
        } catch (OutOfMemoryError e) {
            System.out.println("Out of memory when saving");
        }
        
        
        encoder.setLoopCount(0);
        encoder.encode(new FileOutputStream(filename));
    }
    
    // set the background color of the drawing panel
    public void setBackground(Color c) {
        Color oldBackgroundColor = backgroundColor;
        backgroundColor = c;
        if (isGraphical()) {
            panel.setBackground(c);
            imagePanel.setBackground(c);
        }
        
        // with animated images, need to palette-swap the old bg color for the new
        // because there's no notion of transparency in a palettized 8-bit image
        if (isAnimated()) {
            replaceColor(image, oldBackgroundColor, c);
        }
    }
    
    // Enables or disables the drawing of grid lines on top of the image to help
    // with debugging sizes and coordinates.
    public void setGridLines(boolean gridLines) {
        this.gridLines = gridLines;
        imagePanel.repaint();
    }
    
    // sets the drawing panel's height in pixels to the given value
    // After calling this method, the client must call getGraphics() again
    // to get the new graphics context of the newly enlarged image buffer.
    public void setHeight(int height) {
        setSize(getWidth(), height);
    }
     
    // sets the drawing panel's pixel size (width, height) to the given values
    // After calling this method, the client must call getGraphics() again
    // to get the new graphics context of the newly enlarged image buffer.
    public void setSize(int width, int height) {
        // replace the image buffer for drawing
        BufferedImage newImage = new BufferedImage(width, height, image.getType());
        imagePanel.setImage(newImage);
        newImage.getGraphics().drawImage(image, 0, 0, imagePanel);

        this.width = width;
        this.height = height;
        image = newImage;
        g2 = (Graphics2D) newImage.getGraphics();
        g2.setColor(Color.BLACK);
        if (PRETTY) {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        }
        zoom(currentZoom);
        if (isGraphical()) {
            frame.pack();
        }
    }
    
    // show or hide the drawing panel on the screen
    public void setVisible(boolean visible) {
        if (isGraphical()) {
            frame.setVisible(visible);
        }
    }
    
    // sets the drawing panel's width in pixels to the given value
    // After calling this method, the client must call getGraphics() again
    // to get the new graphics context of the newly enlarged image buffer.
    public void setWidth(int width) {
        setSize(width, getHeight());
    }
     
    // makes the program pause for the given amount of time,
    // allowing for animation
    public void sleep(int millis) {
        if (isGraphical() && frame.isVisible()) {
            // if not even displaying, we don't actually need to sleep
            if (millis > 0) {
                try {
                    Thread.sleep(millis);
                    panel.repaint();
                    toFront(frame);
                } catch (Exception e) {}
            }
        }
        
        // manually enable animation if necessary
        if (!isAnimated() && !isMultiple() && autoEnableAnimationOnSleep()) {
            animated = true;
            initializeAnimation();
        }
        
        // capture a frame of animation
        if (isAnimated() && shouldSave() && !isMultiple()) {
            try {
                if (frames.size() < MAX_FRAMES) {
                    frames.add(new ImageFrame(getImage(), millis));
                }
                
                // reset creation timer so that we won't save/close just yet
                createTime = System.currentTimeMillis();
            } catch (OutOfMemoryError e) {
                System.out.println("Out of memory after capturing " + frames.size() + " frames");
            }
        }
    }
    
    // moves window on top of other windows
    public void toFront() {
        toFront(frame);
    }
    
    // called when DrawingPanel closes, to potentially exit the program
    public void windowClosing(WindowEvent event) {
        frame.setVisible(false);
        synchronized (getClass()) {
            instances--;
        }
        frame.dispose();
    }
    
    // methods required by WindowListener interface
    public void windowActivated(WindowEvent event) {}
    public void windowClosed(WindowEvent event) {}
    public void windowDeactivated(WindowEvent event) {}
    public void windowDeiconified(WindowEvent event) {}
    public void windowIconified(WindowEvent event) {}
    public void windowOpened(WindowEvent event) {}

    // zooms the drawing panel in/out to the given factor
    // factor should be >= 1
    public void zoom(int zoomFactor) {
        currentZoom = Math.max(1, zoomFactor);
        if (isGraphical()) {
            Dimension size = new Dimension(width * currentZoom, height * currentZoom);
            imagePanel.setPreferredSize(size);
            panel.setPreferredSize(size);
            imagePanel.validate();
            imagePanel.revalidate();
            panel.validate();
            panel.revalidate();
            // imagePanel.setSize(size);
            frame.getContentPane().validate();
            imagePanel.repaint();
            setStatusBarText(" ");
            
            // resize frame if any more space for it exists or it's the wrong size
            Dimension screen = Toolkit.getDefaultToolkit().getScreenSize();
            if (size.width <= screen.width || size.height <= screen.height) {
                frame.pack();
            }
            
            // if (size.width <= screen.width && size.height <= screen.height) {
            //     frame.pack();
            //     center(frame);
            // }
        }
    }
    

    // moves given jframe to center of screen
    private void center(Window frame) {
        Toolkit tk = Toolkit.getDefaultToolkit();
        Dimension screen = tk.getScreenSize();
        
        int x = Math.max(0, (screen.width - frame.getWidth()) / 2);
        int y = Math.max(0, (screen.height - frame.getHeight()) / 2);
        frame.setLocation(x, y);
    }
    
    // constructs and initializes JFileChooser object if necessary
    private void checkChooser() {
        if (chooser == null) {
            // TODO: fix security on applet mode
            chooser = new JFileChooser(System.getProperty("user.dir"));
            chooser.setMultiSelectionEnabled(false);
            chooser.setFileFilter(this);
        }
    }
    
    // compares current DrawingPanel image to an image file on disk
    private void compareToFile() {
        // save current image to a temp file
        try {
            String tempFile = saveToTempFile();
            
            // use file chooser dialog to find image to compare against
            checkChooser();
            if (chooser.showOpenDialog(frame) != JFileChooser.APPROVE_OPTION) {
                return;
            }
            
            // user chose a file; let's diff it
            new DiffImage(chooser.getSelectedFile().toString(), tempFile);
        } catch (IOException ioe) {
            JOptionPane.showMessageDialog(frame,
                                          "Unable to compare images: \n" + ioe);
        }
    }
    
    // compares current DrawingPanel image to an image file on disk
    public void compareToFilePublic() {
        try {
            String tempFile = saveToTempFile();
            
            // use file chooser dialog to find image to compare against
            new DiffImage("correct-image.png", tempFile);
        } catch (IOException ioe) {
            JOptionPane.showMessageDialog(frame,
                                          "Unable to compare images: \n" + ioe);
        }
    }

    
    // compares current DrawingPanel image to an image file on the web
    private void compareToURL() {
        // save current image to a temp file
        try {
            String tempFile = saveToTempFile();
            
            // get list of images to compare against from web site
            URL url = new URL(COURSE_WEB_SITE);
            Scanner input = new Scanner(url.openStream());
            List<String> lines = new ArrayList<String>();
            List<String> filenames = new ArrayList<String>();
            while (input.hasNextLine()) {
                String line = input.nextLine().trim();
                if (line.length() == 0) { continue; }
                
                if (line.startsWith("#")) {
					// a comment
					if (line.endsWith(":")) {
						// category label
						lines.add(line);
						line = line.replaceAll("#\\s*", "");
						filenames.add(line);
					}
				} else {
                    lines.add(line);
                    
                    // get filename
                    int lastSlash = line.lastIndexOf('/');
                    if (lastSlash >= 0) {
                        line = line.substring(lastSlash + 1);
                    }
                    
                    // remove extension
                    int dot = line.lastIndexOf('.');
                    if (dot >= 0) {
                        line = line.substring(0, dot);
                    }
                    
                    filenames.add(line);
                }
            }
            
            if (filenames.isEmpty()) {
                JOptionPane.showMessageDialog(frame,
                    "No valid web files found to compare against.",
                    "Error: no web files found",
                    JOptionPane.ERROR_MESSAGE);
                return;
            } else {
                String fileURL = null;
                if (filenames.size() == 1) {
                    // only one choice; take it
                    fileURL = lines.get(0);
                } else {
                    // user chooses file to compare against
                    int choice = showOptionDialog(frame, "File to compare against?",
                            "Choose File", filenames.toArray(new String[0]));
                    if (choice < 0) {
                        return;
                    }

                    // user chose a file; let's diff it
                    fileURL = lines.get(choice);
                }
                if (DEBUG) System.out.println(fileURL);
                new DiffImage(fileURL, tempFile);
            }
        } catch (NoRouteToHostException nrthe) {
            JOptionPane.showMessageDialog(frame, "You do not appear to have a working internet connection.\nPlease check your internet settings and try again.\n\n" + nrthe);
        } catch (UnknownHostException uhe) {
            JOptionPane.showMessageDialog(frame, "Internet connection error: \n" + uhe);
        } catch (SocketException se) {
            JOptionPane.showMessageDialog(frame, "Internet connection error: \n" + se);
        } catch (IOException ioe) {
            JOptionPane.showMessageDialog(frame, "Unable to compare images: \n" + ioe);
        }
    }
    
    // closes the frame and exits the program
    private void exit() {
        if (isGraphical()) {
            frame.setVisible(false);
            frame.dispose();
        }
        try {
            System.exit(0);
        } catch (SecurityException e) {
            // if we're running in an applet or something, can't do System.exit
        }
    }
    
    // returns a best guess about the name of the class that constructed this panel
    private String getCallingClassName() {
        StackTraceElement[] stack = new RuntimeException().getStackTrace();
        String className = this.getClass().getName();
        for (StackTraceElement element : stack) {
            String cl = element.getClassName();
            if (!className.equals(cl)) {
                className = cl;
                break;
            }
        }
        
        return className;
    }
    
    private BufferedImage getImage() {
        // create second image so we get the background color
        BufferedImage image2;
        if (isAnimated()) {
            image2 = new BufferedImage(width, height, BufferedImage.TYPE_BYTE_INDEXED);
        } else {
            image2 = new BufferedImage(width, height, image.getType());
        }
        Graphics g = image2.getGraphics();
        if (DEBUG) System.out.println("getImage setting background to " + backgroundColor);
        g.setColor(backgroundColor);
        g.fillRect(0, 0, width, height);
        g.drawImage(image, 0, 0, panel);
        return image2;
    }
    
    private void initializeAnimation() {
        frames = new ArrayList<ImageFrame>();
        encoder = new Gif89Encoder();
        /*
        try {
            if (hasProperty(SAVE_PROPERTY)) {
                stream = new FileOutputStream(System.getProperty(SAVE_PROPERTY));
            }
            // encoder.startEncoding(stream);
        } catch (IOException e) {
            System.out.println(e);
        }
        */
    }
    
    private boolean autoEnableAnimationOnSleep() {
        return propertyIsTrue(AUTO_ENABLE_ANIMATION_ON_SLEEP_PROPERTY);
    }
    
    private boolean isAnimated() {
        return animated || propertyIsTrue(ANIMATED_PROPERTY);
    }
    
    private boolean isGraphical() {
        return !hasProperty(SAVE_PROPERTY) && !hasProperty(HEADLESS_PROPERTY);
    }
    
    private boolean isMultiple() {
        return propertyIsTrue(MULTIPLE_PROPERTY);
    }
    
    private boolean readyToClose() {
/*
        if (isAnimated()) {
            // wait a little longer, in case animation is sleeping
            return System.currentTimeMillis() > createTime + 5 * DELAY;
        } else {
            return System.currentTimeMillis() > createTime + 4 * DELAY;
        }
*/
        return (instances == 0 || shouldSave()) && !mainIsActive();
    }
    
    private void replaceColor(BufferedImage image, Color oldColor, Color newColor) {
        int oldRGB = oldColor.getRGB();
        int newRGB = newColor.getRGB();
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                if (image.getRGB(x, y) == oldRGB) {
                    image.setRGB(x, y, newRGB);
                }
            }
        }
    }
    
    // called when user presses "Save As" menu item
    private void saveAs() {
        String filename = saveAsHelper("png");
        if (filename != null) {
            try {
                save(filename);  // save the file
            } catch (IOException ex) {
                JOptionPane.showMessageDialog(frame, "Unable to save image:\n" + ex);
            }
        }
    }
    
    private void saveAsAnimated() {
        String filename = saveAsHelper("gif");
        if (filename != null) {
            try {
                // record that the file should be saved next time
                PrintStream out = new PrintStream(new File(ANIMATION_FILE_NAME));
                out.println(filename);
                out.close();
                
                JOptionPane.showMessageDialog(frame, 
                    "Due to constraints about how DrawingPanel works, you'll need to\n" +
                    "re-run your program.  When you run it the next time, DrawingPanel will \n" +
                    "automatically save your animated image as: " + new File(filename).getName()
                );
            } catch (IOException ex) {
                JOptionPane.showMessageDialog(frame, "Unable to store animation settings:\n" + ex);
            }
        }
    }
    
    private String saveAsHelper(String extension) {
        // use file chooser dialog to get filename to save into
        checkChooser();
        if (chooser.showSaveDialog(frame) != JFileChooser.APPROVE_OPTION) {
            return null;
        }
        
        File selectedFile = chooser.getSelectedFile();
        String filename = selectedFile.toString();
        if (!filename.toLowerCase().endsWith(extension)) {
            // Windows is dumb about extensions with file choosers
            filename += "." + extension;
        }

        // confirm overwrite of file
        if (new File(filename).exists() && JOptionPane.showConfirmDialog(
                frame, "File exists.  Overwrite?", "Overwrite?",
                JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION) {
            return null;
        }

        return filename;
    }
    
    // saves DrawingPanel image to a temporary file and returns file's name
    private String saveToTempFile() throws IOException {
        File currentImageFile = File.createTempFile("current_image", ".png");
        save(currentImageFile.toString());
        return currentImageFile.toString();
    }
    
    // sets the text that will appear in the bottom status bar
    private void setStatusBarText(String text) {
        if (currentZoom != 1) {
            text += " (current zoom: " + currentZoom + "x" + ")";
        }
        statusBar.setText(text);
    }
    
    // initializes DrawingPanel's menu bar items
    private void setupMenuBar() {
        // abort compare if we're running as an applet or in a secure environment
        boolean secure = (System.getSecurityManager() != null);
        
        JMenuItem saveAs = new JMenuItem("Save As...", 'A');
        saveAs.addActionListener(this);
        saveAs.setAccelerator(KeyStroke.getKeyStroke("ctrl S"));
        saveAs.setEnabled(!secure);
        
        JMenuItem saveAnimated = new JMenuItem("Save Animated GIF...", 'G');
        saveAnimated.addActionListener(this);
        saveAnimated.setAccelerator(KeyStroke.getKeyStroke("ctrl A"));
        saveAnimated.setEnabled(!secure);
        
        JMenuItem compare = new JMenuItem("Compare to File...", 'C');
        compare.addActionListener(this);
        // compare.setAccelerator(KeyStroke.getKeyStroke("ctrl C"));
        compare.setEnabled(!secure);
        
        JMenuItem compareURL = new JMenuItem("Compare to Web File...", 'U');
        compareURL.addActionListener(this);
        compareURL.setAccelerator(KeyStroke.getKeyStroke("ctrl U"));
        compareURL.setEnabled(!secure);
        
        JMenuItem zoomIn = new JMenuItem("Zoom In", 'I');
        zoomIn.addActionListener(this);
        zoomIn.setAccelerator(KeyStroke.getKeyStroke("ctrl EQUALS"));
        
        JMenuItem zoomOut = new JMenuItem("Zoom Out", 'O');
        zoomOut.addActionListener(this);
        zoomOut.setAccelerator(KeyStroke.getKeyStroke("ctrl MINUS"));
        
        JMenuItem zoomNormal = new JMenuItem("Zoom Normal (100%)", 'N');
        zoomNormal.addActionListener(this);
        zoomNormal.setAccelerator(KeyStroke.getKeyStroke("ctrl 0"));
        
        JMenuItem gridLinesItem = new JCheckBoxMenuItem("Grid Lines");
        gridLinesItem.setMnemonic('G');
        gridLinesItem.addActionListener(this);
        gridLinesItem.setAccelerator(KeyStroke.getKeyStroke("ctrl G"));
        
        JMenuItem exit = new JMenuItem("Exit", 'x');
        exit.addActionListener(this);
        
        JMenuItem about = new JMenuItem("About...", 'A');
        about.addActionListener(this);
        
        JMenu file = new JMenu("File");
        file.setMnemonic('F');
        file.add(compareURL);
        file.add(compare);
        file.addSeparator();
        file.add(saveAs);
        file.add(saveAnimated);
        file.addSeparator();
        file.add(exit);
        
        JMenu view = new JMenu("View");
        view.setMnemonic('V');
        view.add(zoomIn);
        view.add(zoomOut);
        view.add(zoomNormal);
        view.addSeparator();
        view.add(gridLinesItem);
        
        JMenu help = new JMenu("Help");
        help.setMnemonic('H');
        help.add(about);
        
        JMenuBar bar = new JMenuBar();
        bar.add(file);
        bar.add(view);
        bar.add(help);
        frame.setJMenuBar(bar);
    }
    
    private boolean shouldDiff() {
        return hasProperty(DIFF_PROPERTY);
    }
    
    private boolean shouldSave() {
        return hasProperty(SAVE_PROPERTY);
    }
    
    // show dialog box with given choices; return index chosen (-1 == cancel)
    private int showOptionDialog(Frame parent, String title, 
            String message, final String[] names) {
        final JDialog dialog = new JDialog(parent, title, true);
        JPanel center = new JPanel(new GridLayout(0, 1));
        
        // just a hack to make the return value a mutable reference to an int
        final int[] hack = {-1};
        
        for (int i = 0; i < names.length; i++) {
            if (names[i].endsWith(":")) {
				center.add(new JLabel("<html><b>" + names[i] + "</b></html>"));
			} else {
				final JButton button = new JButton(names[i]);
				button.setActionCommand(String.valueOf(i));
				button.addActionListener(new ActionListener() {
					public void actionPerformed(ActionEvent e) {
						hack[0] = Integer.parseInt(button.getActionCommand());
						dialog.setVisible(false);
					}
				});
				center.add(button);
			}
        }
        
        JPanel south = new JPanel();
        JButton cancel = new JButton("Cancel");
        cancel.setMnemonic('C');
        cancel.requestFocus();
        cancel.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                dialog.setVisible(false);
            }
        });
        south.add(cancel);
        
        dialog.setDefaultCloseOperation(JDialog.DO_NOTHING_ON_CLOSE);
        dialog.getContentPane().setLayout(new BorderLayout(10, 5));
        // ((JComponent) dialog.getContentPane()).setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        
        if (message != null) {
            JLabel messageLabel = new JLabel(message);
            dialog.add(messageLabel, BorderLayout.NORTH);
        }
        dialog.add(center);
        dialog.add(south, BorderLayout.SOUTH);
        dialog.pack();
        dialog.setResizable(false);
        center(dialog);
        cancel.requestFocus();
        dialog.setVisible(true);
        cancel.requestFocus();
        
        return hack[0];
    }
    
    // brings the given window to the front of the z-ordering
    private void toFront(final Window window) {
        EventQueue.invokeLater(new Runnable() {
            public void run() {
                if (window != null) {
                    window.toFront();
                    window.repaint();
                }
            }
        });
    }

    
    
    // Reports the differences between two images.
    private class DiffImage extends JPanel implements ActionListener,
        ChangeListener {
        private static final long serialVersionUID = 0;
        
        private BufferedImage image1;
        private BufferedImage image2;
        private String image1name;
        private int numDiffPixels;
        private int opacity = 50;
        private String label1Text = "Expected";
        private String label2Text = "Actual";
        private boolean highlightDiffs = false;
        
        private Color highlightColor = new Color(224, 0, 224);
        private JLabel image1Label;
        private JLabel image2Label;
        private JLabel diffPixelsLabel;
        private JSlider slider;
        private JCheckBox box;
        private JMenuItem saveAsItem;
        private JMenuItem setImage1Item;
        private JMenuItem setImage2Item;
        private JFrame frame;
        private JButton colorButton;
        
        public DiffImage(String file1, String file2) throws IOException {
            setImage1(file1);
            setImage2(file2);
            display();
        }
        
        public void actionPerformed(ActionEvent e) {
            Object source = e.getSource();
            if (source == box) {
                highlightDiffs = box.isSelected();
                repaint();
            } else if (source == colorButton) {
                Color color = JColorChooser.showDialog(frame,
                                                       "Choose highlight color", highlightColor);
                if (color != null) {
                    highlightColor = color;
                    colorButton.setBackground(color);
                    colorButton.setForeground(color);
                    repaint();
                }
            } else if (source == saveAsItem) {
                saveAs();
            } else if (source == setImage1Item) {
                setImage1();
            } else if (source == setImage2Item) {
                setImage2();
            }
        }
        
        // Counts number of pixels that differ between the two images.
        public void countDiffPixels() {
            if (image1 == null || image2 == null) {
                return;
            }
            
            int w1 = image1.getWidth();
            int h1 = image1.getHeight();
            int w2 = image2.getWidth();
            int h2 = image2.getHeight();
            int wmax = Math.max(w1, w2);
            int hmax = Math.max(h1, h2);
            
            // check each pair of pixels
            numDiffPixels = 0;
            for (int y = 0; y < hmax; y++) {
                for (int x = 0; x < wmax; x++) {
                    int pixel1 = (x < w1 && y < h1) ? image1.getRGB(x, y) : 0;
                    int pixel2 = (x < w2 && y < h2) ? image2.getRGB(x, y) : 0;
                    if (pixel1 != pixel2) {
                        numDiffPixels++;
                    }
                }
            }
        }
        
        // initializes diffimage panel
        public void display() {
            countDiffPixels();
            
            setupComponents();
            setupEvents();
            setupLayout();
            
            frame.pack();
            center(frame);
            
            frame.setVisible(true);
            toFront(frame);
        }
        
        // draws the given image onto the given graphics context
        public void drawImageFull(Graphics2D g2, BufferedImage image) {
            int iw = image.getWidth();
            int ih = image.getHeight();
            int w = getWidth();
            int h = getHeight();
            int dw = w - iw;
            int dh = h - ih;
            
            if (dw > 0) {
                g2.fillRect(iw, 0, dw, ih);
            }
            if (dh > 0) {
                g2.fillRect(0, ih, iw, dh);
            }
            if (dw > 0 && dh > 0) {
                g2.fillRect(iw, ih, dw, dh);
            }
            g2.drawImage(image, 0, 0, this);
        }
        
        // paints the DiffImage panel
        public void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g;
            
            // draw the expected output (image 1)
            if (image1 != null) {
                drawImageFull(g2, image1);
            }
            
            // draw the actual output (image 2)
            if (image2 != null) {
                Composite oldComposite = g2.getComposite();
                g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_ATOP, ((float) opacity) / 100));
                drawImageFull(g2, image2);
                g2.setComposite(oldComposite);
            }
            g2.setColor(Color.BLACK);
            
            // draw the highlighted diffs (if so desired)
            if (highlightDiffs && image1 != null && image2 != null) {
                int w1 = image1.getWidth();
                int h1 = image1.getHeight();
                int w2 = image2.getWidth();
                int h2 = image2.getHeight();
                
                int wmax = Math.max(w1, w2);
                int hmax = Math.max(h1, h2);
                
                // check each pair of pixels
                g2.setColor(highlightColor);
                for (int y = 0; y < hmax; y++) {
                    for (int x = 0; x < wmax; x++) {
                        int pixel1 = (x < w1 && y < h1) ? image1.getRGB(x, y) : 0;
                        int pixel2 = (x < w2 && y < h2) ? image2.getRGB(x, y) : 0;
                        if (pixel1 != pixel2) {
                            g2.fillRect(x, y, 1, 1);
                        }
                    }
                }
            }
        }
        
        public void save(File file) throws IOException {
            // String extension = filename.substring(filename.lastIndexOf(".") + 1);
            // ImageIO.write(diffImage, extension, new File(filename));
            String filename = file.getName();
            String extension = filename.substring(filename.lastIndexOf(".") + 1);
            BufferedImage img = new BufferedImage(getPreferredSize().width, getPreferredSize().height, BufferedImage.TYPE_INT_ARGB);
            img.getGraphics().setColor(getBackground());
            img.getGraphics().fillRect(0, 0, img.getWidth(), img.getHeight());
            paintComponent(img.getGraphics());
            ImageIO.write(img, extension, file);
        }
        
        public void save(String filename) throws IOException {
            save(new File(filename));
        }
        
        // Called when "Save As" menu item is clicked
        public void saveAs() {
            checkChooser();
            if (chooser.showSaveDialog(frame) != JFileChooser.APPROVE_OPTION) {
                return;
            }
            
            File selectedFile = chooser.getSelectedFile();
            try {
                save(selectedFile.toString());
            } catch (IOException ex) {
                JOptionPane.showMessageDialog(frame, "Unable to save image:\n" + ex);
            }
        }
        
        // called when "Set Image 1" menu item is clicked
        public void setImage1() {
            checkChooser();
            if (chooser.showSaveDialog(frame) != JFileChooser.APPROVE_OPTION) {
                return;
            }
            
            File selectedFile = chooser.getSelectedFile();
            try {
                setImage1(selectedFile.toString());
                countDiffPixels();
                diffPixelsLabel.setText("(" + numDiffPixels + " pixels differ)");
                image1Label.setText(selectedFile.getName());
                frame.pack();
            } catch (IOException ex) {
                JOptionPane.showMessageDialog(frame, "Unable to set image 1:\n" + ex);
            }
        }
        
        // sets image 1 to be the given image
        public void setImage1(BufferedImage image) {
            if (image == null) {
                throw new NullPointerException();
            }
            
            image1 = image;
            setPreferredSize(new Dimension(
                                           Math.max(getPreferredSize().width, image.getWidth()),
                                           Math.max(getPreferredSize().height, image.getHeight()))
                                 );
            if (frame != null) {
                frame.pack();
            }
            repaint();
        }
        
        // loads image 1 from the given filename or URL
        public void setImage1(String filename) throws IOException {
            image1name = new File(filename).getName();
            if (filename.startsWith("http")) {
                setImage1(ImageIO.read(new URL(filename)));
            } else {
                setImage1(ImageIO.read(new File(filename)));
            }
        }
        
        // called when "Set Image 2" menu item is clicked
        public void setImage2() {
            checkChooser();
            if (chooser.showSaveDialog(frame) != JFileChooser.APPROVE_OPTION) {
                return;
            }
            
            File selectedFile = chooser.getSelectedFile();
            try {
                setImage2(selectedFile.toString());
                countDiffPixels();
                diffPixelsLabel.setText("(" + numDiffPixels + " pixels differ)");
                image2Label.setText(selectedFile.getName());
                frame.pack();
            } catch (IOException ex) {
                JOptionPane.showMessageDialog(frame, "Unable to set image 2:\n" + ex);
            }
        }
        
        // sets image 2 to be the given image
        public void setImage2(BufferedImage image) {
            if (image == null) {
                throw new NullPointerException();
            }
            
            image2 = image;
            setPreferredSize(new Dimension(
                                           Math.max(getPreferredSize().width, image.getWidth()),
                                           Math.max(getPreferredSize().height, image.getHeight()))
                                 );
            if (frame != null) {
                frame.pack();
            }
            repaint();
        }
        
        // loads image 2 from the given filename
        public void setImage2(String filename) throws IOException {
            if (filename.startsWith("http")) {
                setImage2(ImageIO.read(new URL(filename)));
            } else {
                setImage2(ImageIO.read(new File(filename)));
            }

        }
        
        private void setupComponents() {
            String title = "DiffImage";
            if (image1name != null) {
                title = "Compare to " + image1name;
            }
            frame = new JFrame(title);
            frame.setResizable(false);
            // frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            
            slider = new JSlider();
            slider.setPaintLabels(false);
            slider.setPaintTicks(true);
            slider.setSnapToTicks(true);
            slider.setMajorTickSpacing(25);
            slider.setMinorTickSpacing(5);
            
            box = new JCheckBox("Highlight diffs in color: ", highlightDiffs);
            
            colorButton = new JButton();
            colorButton.setBackground(highlightColor);
            colorButton.setForeground(highlightColor);
            colorButton.setPreferredSize(new Dimension(24, 24));
            
            diffPixelsLabel = new JLabel("(" + numDiffPixels + " pixels differ)");
            diffPixelsLabel.setFont(diffPixelsLabel.getFont().deriveFont(Font.BOLD));
            image1Label = new JLabel(label1Text);
            image2Label = new JLabel(label2Text);
            
            setupMenuBar();
        }
        
        // initializes layout of components
        private void setupLayout() {
            JPanel southPanel1 = new JPanel();
            southPanel1.setBorder(BorderFactory.createLineBorder(Color.DARK_GRAY));
            southPanel1.add(image1Label);
            southPanel1.add(slider);
            southPanel1.add(image2Label);
            southPanel1.add(Box.createHorizontalStrut(20));
            
            JPanel southPanel2 = new JPanel();
            southPanel2.setBorder(BorderFactory.createLineBorder(Color.DARK_GRAY));
            southPanel2.add(diffPixelsLabel);
            southPanel2.add(Box.createHorizontalStrut(20));
            southPanel2.add(box);
            southPanel2.add(colorButton);
            
            Container southPanel = javax.swing.Box.createVerticalBox();
            southPanel.add(southPanel1);
            southPanel.add(southPanel2);
            
            frame.add(this, BorderLayout.CENTER);
            frame.add(southPanel, BorderLayout.SOUTH);
        }
        
        // initializes main menu bar
        private void setupMenuBar() {
            saveAsItem = new JMenuItem("Save As...", 'A');
            saveAsItem.setAccelerator(KeyStroke.getKeyStroke("ctrl S"));
            setImage1Item = new JMenuItem("Set Image 1...", '1');
            setImage1Item.setAccelerator(KeyStroke.getKeyStroke("ctrl 1"));
            setImage2Item = new JMenuItem("Set Image 2...", '2');
            setImage2Item.setAccelerator(KeyStroke.getKeyStroke("ctrl 2"));
            
            JMenu file = new JMenu("File");
            file.setMnemonic('F');
            file.add(setImage1Item);
            file.add(setImage2Item);
            file.addSeparator();
            file.add(saveAsItem);
            
            JMenuBar bar = new JMenuBar();
            bar.add(file);
            
            // disabling menu bar to simplify code
            // frame.setJMenuBar(bar);
        }
        
        // method of ChangeListener interface
        public void stateChanged(ChangeEvent e) {
            opacity = slider.getValue();
            repaint();
        }
        
        // adds event listeners to various components
        private void setupEvents() {
            slider.addChangeListener(this);
            box.addActionListener(this);
            colorButton.addActionListener(this);
            saveAsItem.addActionListener(this);
            this.setImage1Item.addActionListener(this);
            this.setImage2Item.addActionListener(this);
        }
    }
    
    

    
    class DirectGif89Frame extends Gif89Frame {
    
      private int[] argbPixels;
    
     
      public DirectGif89Frame(Image img) throws IOException 
      { 
        PixelGrabber pg = new PixelGrabber(img, 0, 0, -1, -1, true);
    
        String errmsg = null;
        try {   
          if (!pg.grabPixels())
            errmsg = "can't grab pixels from image";     
        } catch (InterruptedException e) {
          errmsg = "interrupted grabbing pixels from image";
        }
    
        if (errmsg != null)
          throw new IOException(errmsg + " (" + getClass().getName() + ")");
        
        theWidth = pg.getWidth(); 
        theHeight = pg.getHeight();
        argbPixels = (int[]) pg.getPixels();
        ciPixels = new byte[argbPixels.length];
        
        // flush to conserve resources
        img.flush();
      }
    
      
      public DirectGif89Frame(int width, int height, int argb_pixels[])
      {
        theWidth = width;
        theHeight = height;
        argbPixels = new int[theWidth * theHeight];
        System.arraycopy(argb_pixels, 0, argbPixels, 0, argbPixels.length);
        ciPixels = new byte[argbPixels.length];
      }
    
      //----------------------------------------------------------------------------
      Object getPixelSource() { return argbPixels; }
    }
    
    
    
    
    class Gif89Encoder {
        private static final boolean DEBUG = false;
      private Dimension  dispDim = new Dimension(0, 0);
      private GifColorTable colorTable;
      private int          bgIndex = 0;
      private int          loopCount = 1;
      private String        theComments;
      private Vector<Gif89Frame> vFrames = new Vector<Gif89Frame>();
    
      //----------------------------------------------------------------------------
      /** Use this default constructor if you'll be adding multiple frames
       *  constructed from RGB data (i.e., AWT Image objects or ARGB-pixel arrays).
       */
      public Gif89Encoder()
      {
        // empty color table puts us into "palette autodetect" mode
        colorTable = new GifColorTable();  
      }
      
     
      public Gif89Encoder(Image static_image) throws IOException
      {
        this();
        addFrame(static_image);
      }
    
      
      public Gif89Encoder(Color[] colors)
      {
        colorTable = new GifColorTable(colors); 
      }
    
     
      public Gif89Encoder(Color[] colors, int width, int height, byte ci_pixels[])
      throws IOException
      {
        this(colors);
        addFrame(width, height, ci_pixels);
      }  
    
      //----------------------------------------------------------------------------
      /** Get the number of frames that have been added so far.
       *
       * @return
       *  Number of frame items.
       */
      public int getFrameCount() { return vFrames.size(); }
    
     
      public Gif89Frame getFrameAt(int index)
      {
        return isOk(index) ? vFrames.elementAt(index) : null;
      }
     
      
      public void addFrame(Gif89Frame gf) throws IOException
      {
        accommodateFrame(gf);
        vFrames.addElement(gf);
      }
    
      //----------------------------------------------------------------------------
      /** Convenience version of addFrame() that takes a Java Image, internally
       *  constructing the requisite DirectGif89Frame.
       *
       * @param image
       *   Any Image object that supports pixel-grabbing.
       * @exception IOException
       *   If either (1) pixel-grabbing fails, (2) the aggregate cross-frame RGB
       *   color count exceeds 256, or (3) this encoder object was constructed with
       *   an explicit color table.  
       */
      public void addFrame(Image image) throws IOException
      {
        DirectGif89Frame frame = new DirectGif89Frame(image);
        addFrame(frame);
      }
    
      
      public void addFrame(int width, int height, byte ci_pixels[])
      throws IOException
      {
        addFrame(new IndexGif89Frame(width, height, ci_pixels));
      }   
    
     
      public void insertFrame(int index, Gif89Frame gf) throws IOException
      {
        accommodateFrame(gf);
        vFrames.insertElementAt(gf, index);
      }
    
      //----------------------------------------------------------------------------
      /** Set the color table index for the transparent color, if any.
       *
       * @param index
       *   Index of the color that should be rendered as transparent, if any.
       *   A value of -1 turns off transparency.  (Default: -1)
       */  
      public void setTransparentIndex(int index)
      {
        colorTable.setTransparent(index);
      }
       
      //----------------------------------------------------------------------------
      /** Sets attributes of the multi-image display area, if applicable.
       *
       * @param dim
       *   Width/height of display.  (Default: largest detected frame size)
       * @param background
       *   Color table index of background color.  (Default: 0)
       * @see Gif89Frame#setPosition
       */
      public void setLogicalDisplay(Dimension dim, int background)   
      {
        dispDim = new Dimension(dim);
        bgIndex = background;
      }
     
      //----------------------------------------------------------------------------
      /** Set animation looping parameter, if applicable.
       *
       * @param count
       *   Number of times to play sequence.  Special value of 0 specifies
       *   indefinite looping.  (Default: 1)  
       */   
      public void setLoopCount(int count)
      {
        loopCount = count;
      }
    
      //----------------------------------------------------------------------------
      /** Specify some textual comments to be embedded in GIF.
       *
       * @param comments
       *   String containing ASCII comments.
       */ 
      public void setComments(String comments)
      {
        theComments = comments;
      }
    
    
      public void setUniformDelay(int interval)
      {
        for (int i = 0; i < vFrames.size(); ++i)
          vFrames.elementAt(i).setDelay(interval);  
      } 
    
      
      public void encode(OutputStream out) throws IOException
      {
        int  nframes = getFrameCount();
        boolean is_sequence = nframes > 1;
    
        // N.B. must be called before writing screen descriptor
        colorTable.closePixelProcessing(); 
    
        // write GIF HEADER  
        putAscii("GIF89a", out);
    
        // write global blocks
        writeLogicalScreenDescriptor(out);  
        colorTable.encode(out);
        if (is_sequence && loopCount != 1)
          writeNetscapeExtension(out);
        if (theComments != null && theComments.length() > 0)  
          writeCommentExtension(out);
    
        // write out the control and rendering data for each frame
        for (int i = 0; i < nframes; ++i) {
          DirectGif89Frame frame = (DirectGif89Frame) vFrames.elementAt(i);
          frame.encode(out, is_sequence, colorTable.getDepth(), colorTable.getTransparent());
          vFrames.set(i, null);   // for GC's sake
          System.gc();
        }
    
        // write GIF TRAILER
        out.write((int) ';');
        
        out.flush();
      }
      
      public boolean hasStarted = false;
    
    
      public void startEncoding(OutputStream out, Image image, int delay) throws IOException
      {
        hasStarted = true;
        boolean is_sequence = true;
        Gif89Frame gf = new DirectGif89Frame(image);
        accommodateFrame(gf);
    
        // N.B. must be called before writing screen descriptor
        colorTable.closePixelProcessing(); 
    
        // write GIF HEADER  
        putAscii("GIF89a", out);
    
        // write global blocks
        writeLogicalScreenDescriptor(out);  
        colorTable.encode(out);
        if (is_sequence && loopCount != 1)
          writeNetscapeExtension(out);
        if (theComments != null && theComments.length() > 0)  
          writeCommentExtension(out);
      }
      
      public void continueEncoding(OutputStream out, Image image, int delay) throws IOException {
        // write out the control and rendering data for each frame
        Gif89Frame gf = new DirectGif89Frame(image);
        accommodateFrame(gf);
        gf.encode(out, true, colorTable.getDepth(), colorTable.getTransparent());
        out.flush();
        image.flush();
      }
    
      public void endEncoding(OutputStream out) throws IOException {
        // write GIF TRAILER
        out.write((int) ';');
        
        out.flush();
      }
      
      public void setBackground(Color color) {
          bgIndex = colorTable.indexOf(color);
          if (bgIndex < 0) {
              try {
                  BufferedImage img = new BufferedImage(1, 1, BufferedImage.TYPE_BYTE_INDEXED);
                  Graphics g = img.getGraphics();
                  g.setColor(color);
                  g.fillRect(0, 0, 2, 2);
                  DirectGif89Frame frame = new DirectGif89Frame(img);
                  accommodateFrame(frame);
                  bgIndex = colorTable.indexOf(color);
              } catch (IOException e) {
                  if (DEBUG) System.out.println("Error while setting background color: " + e);
              }
          }
          if (DEBUG) System.out.println("Setting bg index to " + bgIndex);
      }
    
      //----------------------------------------------------------------------------
      private void accommodateFrame(Gif89Frame gf) throws IOException
      {
        dispDim.width = Math.max(dispDim.width, gf.getWidth());
        dispDim.height = Math.max(dispDim.height, gf.getHeight());
        colorTable.processPixels(gf);
      }  
    
      //----------------------------------------------------------------------------
      private void writeLogicalScreenDescriptor(OutputStream os) throws IOException
      {
        putShort(dispDim.width, os);
        putShort(dispDim.height, os);
    
        // write 4 fields, packed into a byte  (bitfieldsize:value)
        //   global color map present?       (1:1)
        //   bits per primary color less 1   (3:7)
        //   sorted color table?               (1:0)
        //   bits per pixel less 1           (3:varies)
        os.write(0xf0 | colorTable.getDepth() - 1);
    
        // write background color index 
        os.write(bgIndex);
    
     
        os.write(0);
      }
    
      //----------------------------------------------------------------------------
      private void writeNetscapeExtension(OutputStream os) throws IOException
      {
        // n.b. most software seems to interpret the count as a repeat count
        // (i.e., interations beyond 1) rather than as an iteration count
        // (thus, to avoid repeating we have to omit the whole extension)
        
        os.write((int) '!');           // GIF Extension Introducer
        os.write(0xff);             // Application Extension Label
        
        os.write(11);                 // application ID block size
        putAscii("NETSCAPE2.0", os);  // application ID data
        
        os.write(3);                   // data sub-block size
        os.write(1);                   // a looping flag? dunno
    
        // we finally write the relevent data
        putShort(loopCount > 1 ? loopCount - 1 : 0, os); 
        
        os.write(0);                   // block terminator
      }
    
      //----------------------------------------------------------------------------
      private void writeCommentExtension(OutputStream os) throws IOException
      {
        os.write((int) '!');     // GIF Extension Introducer
        os.write(0xfe);       // Comment Extension Label
    
        int remainder = theComments.length() % 255;
        int nsubblocks_full = theComments.length() / 255;
        int nsubblocks = nsubblocks_full + (remainder > 0 ? 1 : 0);
        int ibyte = 0;
        for (int isb = 0; isb < nsubblocks; ++isb)
        {
          int size = isb < nsubblocks_full ? 255 : remainder;
          
          os.write(size);
          putAscii(theComments.substring(ibyte, ibyte + size), os);
          ibyte += size;
        }
    
        os.write(0);    // block terminator
      }
    
      //----------------------------------------------------------------------------
      private boolean isOk(int frame_index)
      {
        return frame_index >= 0 && frame_index < vFrames.size(); 
      }
    }
    
    //==============================================================================
    class GifColorTable {
    
      // the palette of ARGB colors, packed as returned by Color.getRGB()
      private int[] theColors = new int[256];
      
      // other basic attributes
      private int   colorDepth;
      private int   transparentIndex = -1;
    
      // these fields track color-index info across frames
      private int            ciCount = 0; // count of distinct color indices
      private ReverseColorMap ciLookup; // cumulative rgb-to-ci lookup table
    
      //----------------------------------------------------------------------------
      GifColorTable()
      {
        ciLookup = new ReverseColorMap();  // puts us into "auto-detect mode"
      }
      
      //----------------------------------------------------------------------------
      GifColorTable(Color[] colors)
      {
        int n2copy = Math.min(theColors.length, colors.length);
        for (int i = 0; i < n2copy; ++i)
          theColors[i] = colors[i].getRGB();
      }
      
      int indexOf(Color color) {
          int rgb = color.getRGB();
          for (int i = 0; i < theColors.length; i++) {
              if (rgb == theColors[i]) {
                  return i;
              }
          }
          return -1;
      }
    
      //----------------------------------------------------------------------------
      int getDepth() { return colorDepth; }  
    
      //----------------------------------------------------------------------------
      int getTransparent() { return transparentIndex; }  
     
      //----------------------------------------------------------------------------
      // default: -1 (no transparency)
      void setTransparent(int color_index)
      {
        transparentIndex = color_index;
      }
    
      //----------------------------------------------------------------------------
      void processPixels(Gif89Frame gf) throws IOException
      {
        if (gf instanceof DirectGif89Frame)
          filterPixels((DirectGif89Frame) gf);
        else 
          trackPixelUsage((IndexGif89Frame) gf);
      }   
     
      //----------------------------------------------------------------------------
      void closePixelProcessing()  // must be called before encode()
      {
        colorDepth = computeColorDepth(ciCount);
      }
    
      //----------------------------------------------------------------------------
      void encode(OutputStream os) throws IOException
      {
        // size of palette written is the smallest power of 2 that can accomdate
        // the number of RGB colors detected (or largest color index, in case of
        // index pixels)
        int palette_size = 1 << colorDepth; 
        for (int i = 0; i < palette_size; ++i)
        {
          os.write(theColors[i] >> 16 & 0xff);
          os.write(theColors[i] >>  8 & 0xff);
          os.write(theColors[i] & 0xff);
        }
      }
    
      
      private void filterPixels(DirectGif89Frame dgf) throws IOException
      {
        if (ciLookup == null)
          throw new IOException("RGB frames require palette autodetection");
        
        int[]  argb_pixels = (int[]) dgf.getPixelSource(); 
        byte[] ci_pixels = dgf.getPixelSink();   
        int npixels = argb_pixels.length;
        for (int i = 0; i < npixels; ++i)
        {
          int argb = argb_pixels[i];
    
          // handle transparency
          if ((argb >>> 24) < 0x80)     // transparent pixel?
            if (transparentIndex == -1) // first transparent color encountered?
              transparentIndex = ciCount;  // record its index
            else if (argb != theColors[transparentIndex]) // different pixel value?
            {
              // collapse all transparent pixels into one color index
              ci_pixels[i] = (byte) transparentIndex; 
              continue;  // CONTINUE - index already in table
            }  
    
          // try to look up the index in our "reverse" color table
          int color_index = ciLookup.getPaletteIndex(argb & 0xffffff);
        
          if (color_index == -1)  // if it isn't in there yet
          {
            if (ciCount == 256)
              throw new IOException("can't encode as GIF (> 256 colors)");
    
            // store color in our accumulating palette
            theColors[ciCount] = argb;  
    
            // store index in reverse color table  
            ciLookup.put(argb & 0xffffff, ciCount);
    
            // send color index to our output array
            ci_pixels[i] = (byte) ciCount;
    
            // increment count of distinct color indices
            ++ciCount;
          }
          else  // we've already snagged color into our palette
            ci_pixels[i] = (byte) color_index;  // just send filtered pixel   
        }
      }  
    
      //----------------------------------------------------------------------------
      private void trackPixelUsage(IndexGif89Frame igf) throws IOException
      {   
        byte[] ci_pixels = (byte[]) igf.getPixelSource(); 
        int npixels = ci_pixels.length;   
        for (int i = 0; i < npixels; ++i)
          if (ci_pixels[i] >= ciCount)
            ciCount = ci_pixels[i] + 1;
      }
    
      //----------------------------------------------------------------------------
      private int computeColorDepth(int colorcount)
      {
        // color depth = log-base-2 of maximum number of simultaneous colors, i.e.
        // bits per color-index pixel   
        if (colorcount <= 2)
          return 1;   
        if (colorcount <= 4)
          return 2;   
        if (colorcount <= 16)
          return 4;
        return 8;   
      } 
    }
    
    
    class ReverseColorMap {
    
      private class ColorRecord {
        int rgb;
        int ipalette;
        ColorRecord(int rgb, int ipalette)
        {
          this.rgb = rgb;
          this.ipalette = ipalette;
        }
      }
    
      
      private static final int HCAPACITY = 2053;  // a nice prime number
    
      // our hash table proper
      private ColorRecord[] hTable = new ColorRecord[HCAPACITY];
    
      
      int getPaletteIndex(int rgb) 
      {   
        ColorRecord rec;
    
        for ( int itable = rgb % hTable.length; 
              (rec = hTable[itable]) != null && rec.rgb != rgb;
              itable = ++itable % hTable.length
            ) 
          ;
    
        if (rec != null)
          return rec.ipalette;
          
        return -1;  
      }
    
      //----------------------------------------------------------------------------
      // Assert: (1) same as above; (2) rgb key not already present
      //----------------------------------------------------------------------------
      void put(int rgb, int ipalette) 
      {
        int itable;
        
        for ( itable = rgb % hTable.length; 
              hTable[itable] != null;
              itable = ++itable % hTable.length
            ) 
          ; 
    
        hTable[itable] = new ColorRecord(rgb, ipalette);   
      }
    }
    
    
    
   
    abstract class Gif89Frame {
    
      //// Public "Disposal Mode" constants ////
    
      /** The animated GIF renderer shall decide how to dispose of this Gif89Frame's
       *  display area.
       * @see Gif89Frame#setDisposalMode
       */
      public static final int DM_UNDEFINED = 0;
      
      /** The animated GIF renderer shall take no display-disposal action.
       * @see Gif89Frame#setDisposalMode
       */  
      public static final int DM_LEAVE   = 1;
      
      /** The animated GIF renderer shall replace this Gif89Frame's area with the
       *  background color.
       * @see Gif89Frame#setDisposalMode
       */  
      public static final int DM_BGCOLOR   = 2;
      
      /** The animated GIF renderer shall replace this Gif89Frame's area with the
       *  previous frame's bitmap.
       * @see Gif89Frame#setDisposalMode
       */   
      public static final int DM_REVERT = 3;
    
      //// Bitmap variables set in package subclass constructors ////
      int   theWidth = -1;
      int   theHeight = -1;
      byte[] ciPixels;
    
      //// GIF graphic frame control options ////
      private Point   thePosition = new Point(0, 0);
      private boolean isInterlaced;
      private int    csecsDelay;
      private int    disposalCode = DM_LEAVE;
    
      
      public void setPosition(Point p)
      {
        thePosition = new Point(p);
      }   
    
      //----------------------------------------------------------------------------
      /** Set or clear the interlace flag.
       *
       * @param b
       *   true if you want interlacing.  (Default: false)
       */  
      public void setInterlaced(boolean b)
      {
        isInterlaced = b;
      }
     
      //----------------------------------------------------------------------------
      /** Set the between-frame interval.
       *
       * @param interval
       *   Centiseconds to wait before displaying the subsequent frame.
       *   (Default: 0)
       */   
      public void setDelay(int interval)
      {
        csecsDelay = interval;
      }
    
      
      public void setDisposalMode(int code)
      {
        disposalCode = code;
      }
    
      //----------------------------------------------------------------------------
      Gif89Frame() {}  // package-visible default constructor
    
      //----------------------------------------------------------------------------
      abstract Object getPixelSource();  
    
      //----------------------------------------------------------------------------
      int getWidth() { return theWidth; }
    
      //----------------------------------------------------------------------------
      int getHeight() { return theHeight; }
    
      //----------------------------------------------------------------------------
      byte[] getPixelSink() { return ciPixels; } 
    
      //----------------------------------------------------------------------------
      void encode(OutputStream os, boolean epluribus, int color_depth,
                  int transparent_index) throws IOException
      {
        writeGraphicControlExtension(os, epluribus, transparent_index);
        writeImageDescriptor(os);
        new GifPixelsEncoder(
          theWidth, theHeight, ciPixels, isInterlaced, color_depth
        ).encode(os);
      }
    
      //----------------------------------------------------------------------------
      private void writeGraphicControlExtension(OutputStream os, boolean epluribus,
                                                int itransparent) throws IOException
      {
        int transflag = itransparent == -1 ? 0 : 1;
        if (transflag == 1 || epluribus)   // using transparency or animating ?
        {
          os.write((int) '!');           // GIF Extension Introducer
          os.write(0xf9);                 // Graphic Control Label
          os.write(4);                   // subsequent data block size
          os.write((disposalCode << 2) | transflag); // packed fields (1 byte)
          putShort(csecsDelay, os);  // delay field (2 bytes)
          os.write(itransparent);         // transparent index field
          os.write(0);                   // block terminator
        }  
      }
    
      //----------------------------------------------------------------------------
      private void writeImageDescriptor(OutputStream os) throws IOException
      {
        os.write((int) ',');                // Image Separator
        putShort(thePosition.x, os);
        putShort(thePosition.y, os);
        putShort(theWidth, os);
        putShort(theHeight, os);
        os.write(isInterlaced ? 0x40 : 0);  // packed fields (1 byte)
      }
    }
    
    //==============================================================================
    class GifPixelsEncoder {
    
      private static final int EOF = -1;
    
      private int    imgW, imgH;
      private byte[]  pixAry;
      private boolean wantInterlaced;
      private int    initCodeSize;
    
      // raster data navigators
      private int    countDown;
      private int    xCur, yCur; 
      private int    curPass;  
    
      //----------------------------------------------------------------------------
      GifPixelsEncoder(int width, int height, byte[] pixels, boolean interlaced,
                       int color_depth)
      {
        imgW = width;
        imgH = height;
        pixAry = pixels;
        wantInterlaced = interlaced;
        initCodeSize = Math.max(2, color_depth);
      }
     
      //----------------------------------------------------------------------------
      void encode(OutputStream os) throws IOException
      {
        os.write(initCodeSize);      // write "initial code size" byte
      
        countDown = imgW * imgH;        // reset navigation variables
        xCur = yCur = curPass = 0;
        
        compress(initCodeSize + 1, os); // compress and write the pixel data
        
        os.write(0);                    // write block terminator
      }
    
     
      private void bumpPosition()
      {
        // Bump the current X position
        ++xCur;
    
        // If we are at the end of a scan line, set xCur back to the beginning
        // If we are interlaced, bump the yCur to the appropriate spot,
        // otherwise, just increment it.
        if (xCur == imgW)
        {
          xCur = 0;
    
          if (!wantInterlaced)
            ++yCur;
          else
            switch (curPass)
            {
              case 0:
                yCur += 8;
                if (yCur >= imgH)
                {
                  ++curPass;
                  yCur = 4;
                }
                break;
              case 1:
                yCur += 8;
                if (yCur >= imgH)
                {
                  ++curPass;
                  yCur = 2;
                }
                break;
              case 2:
                yCur += 4;
                if (yCur >= imgH)
                {
                  ++curPass;
                  yCur = 1;
                }
                break;
              case 3:
                yCur += 2;
                break;
            }
        }
      }
    
      //----------------------------------------------------------------------------
      // Return the next pixel from the image
      //----------------------------------------------------------------------------
      private int nextPixel()
      {
        if (countDown == 0)
          return EOF;
    
        --countDown;
    
        byte pix = pixAry[yCur * imgW + xCur];
    
        bumpPosition();
    
        return pix & 0xff;
      }
    
      
    
      static final int BITS = 12;
    
      static final int HSIZE = 5003;                // 80% occupancy
    
     
    
      int n_bits;                               // number of bits/code
      int maxbits = BITS;                       // user settable max # bits/code
      int maxcode;                      // maximum code, given n_bits
      int maxmaxcode = 1 << BITS; // should NEVER generate this code
    
      final int MAXCODE( int n_bits )
          {
          return ( 1 << n_bits ) - 1;
          }
    
      int[] htab = new int[HSIZE];
      int[] codetab = new int[HSIZE];
    
      int hsize = HSIZE;                // for dynamic table sizing
    
      int free_ent = 0;                     // first unused entry
    
      // block compression parameters -- after all codes are used up,
      // and compression rate changes, start over.
      boolean clear_flg = false;
    
      
    
      int g_init_bits;
    
      int ClearCode;
      int EOFCode;
    
      void compress( int init_bits, OutputStream outs ) throws IOException
          {
          int fcode;
          int i /* = 0 */;
          int c;
          int ent;
          int disp;
          int hsize_reg;
          int hshift;
    
          // Set up the globals:  g_init_bits - initial number of bits
          g_init_bits = init_bits;
    
          // Set up the necessary values
          clear_flg = false;
          n_bits = g_init_bits;
          maxcode = MAXCODE( n_bits );
    
          ClearCode = 1 << ( init_bits - 1 );
          EOFCode = ClearCode + 1;
          free_ent = ClearCode + 2;
    
          char_init();
    
          ent = nextPixel();
    
          hshift = 0;
          for ( fcode = hsize; fcode < 65536; fcode *= 2 )
              ++hshift;
          hshift = 8 - hshift;                      // set hash code range bound
    
          hsize_reg = hsize;
          cl_hash( hsize_reg );     // clear hash table
    
          output( ClearCode, outs );
    
          outer_loop:
          while ( (c = nextPixel()) != EOF )
              {
              fcode = ( c << maxbits ) + ent;
              i = ( c << hshift ) ^ ent;                // xor hashing
    
              if ( htab[i] == fcode )
                  {
                  ent = codetab[i];
                  continue;
                  }
              else if ( htab[i] >= 0 )      // non-empty slot
                  {
                  disp = hsize_reg - i;     // secondary hash (after G. Knott)
                  if ( i == 0 )
                      disp = 1;
                  do
                      {
                      if ( (i -= disp) < 0 )
                          i += hsize_reg;
    
                      if ( htab[i] == fcode )
                          {
                          ent = codetab[i];
                          continue outer_loop;
                          }
                      }
                  while ( htab[i] >= 0 );
                  }
              output( ent, outs );
              ent = c;
              if ( free_ent < maxmaxcode )
                  {
                  codetab[i] = free_ent++;      // code -> hashtable
                  htab[i] = fcode;
                  }
              else
                  cl_block( outs );
              }
          // Put out the final code.
          output( ent, outs );
          output( EOFCode, outs );
          }
    
      
    
      int cur_accum = 0;
      int cur_bits = 0;
    
      int masks[] = { 0x0000, 0x0001, 0x0003, 0x0007, 0x000F,
                      0x001F, 0x003F, 0x007F, 0x00FF,
                      0x01FF, 0x03FF, 0x07FF, 0x0FFF,
                      0x1FFF, 0x3FFF, 0x7FFF, 0xFFFF };
    
      void output( int code, OutputStream outs ) throws IOException
          {
          cur_accum &= masks[cur_bits];
    
          if ( cur_bits > 0 )
              cur_accum |= ( code << cur_bits );
          else
              cur_accum = code;
    
          cur_bits += n_bits;
    
          while ( cur_bits >= 8 )
              {
              char_out( (byte) ( cur_accum & 0xff ), outs );
              cur_accum >>= 8;
              cur_bits -= 8;
              }
    
          // If the next entry is going to be too big for the code size,
          // then increase it, if possible.
         if ( free_ent > maxcode || clear_flg )
              {
              if ( clear_flg )
                  {
                  maxcode = MAXCODE(n_bits = g_init_bits);
                  clear_flg = false;
                  }
              else
                  {
                  ++n_bits;
                  if ( n_bits == maxbits )
                      maxcode = maxmaxcode;
                  else
                      maxcode = MAXCODE(n_bits);
                  }
              }
    
          if ( code == EOFCode )
              {
              // At EOF, write the rest of the buffer.
              while ( cur_bits > 0 )
                  {
                  char_out( (byte) ( cur_accum & 0xff ), outs );
                  cur_accum >>= 8;
                  cur_bits -= 8;
                  }
    
              flush_char( outs );
              }
          }
    
      // Clear out the hash table
    
      // table clear for block compress
      void cl_block( OutputStream outs ) throws IOException
          {
          cl_hash( hsize );
          free_ent = ClearCode + 2;
          clear_flg = true;
    
          output( ClearCode, outs );
          }
    
      // reset code table
      void cl_hash( int hsize )
          {
          for ( int i = 0; i < hsize; ++i )
              htab[i] = -1;
          }
    
      // GIF Specific routines
    
      // Number of characters so far in this 'packet'
      int a_count;
    
      // Set up the 'byte output' routine
      void char_init()
          {
          a_count = 0;
          }
    
      // Define the storage for the packet accumulator
      byte[] accum = new byte[256];
    
      // Add a character to the end of the current packet, and if it is 254
      // characters, flush the packet to disk.
      void char_out( byte c, OutputStream outs ) throws IOException
          {
          accum[a_count++] = c;
          if ( a_count >= 254 )
              flush_char( outs );
          }
    
      // Flush the packet to disk, and reset the accumulator
      void flush_char( OutputStream outs ) throws IOException
          {
          if ( a_count > 0 )
              {
              outs.write( a_count );
              outs.write( accum, 0, a_count );
              a_count = 0;
              }
          }     
    }
    
    
    
    class IndexGif89Frame extends Gif89Frame {
    
      //----------------------------------------------------------------------------
      
      public IndexGif89Frame(int width, int height, byte ci_pixels[])
      {
        theWidth = width;
        theHeight = height;
        ciPixels = new byte[theWidth * theHeight];
        System.arraycopy(ci_pixels, 0, ciPixels, 0, ciPixels.length);
      }
    
      //----------------------------------------------------------------------------
      Object getPixelSource() { return ciPixels; }  
    }
    
    
    
    
    public static void putAscii(String s, OutputStream os) throws IOException
    {
        byte[] bytes = new byte[s.length()];
        for (int i = 0; i < bytes.length; ++i) {
            bytes[i] = (byte) s.charAt(i);  // discard the high byte     
        }
        os.write(bytes); 
    }

    
    public static void putShort(int i16, OutputStream os) throws IOException
    {
        os.write(i16 & 0xff);
        os.write(i16 >> 8 & 0xff);
    }
}

   

