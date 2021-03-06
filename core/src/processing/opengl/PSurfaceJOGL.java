package processing.opengl;

import java.awt.Component;
//import java.awt.Dimension;
import java.awt.Point;
//import java.awt.Frame;
import java.awt.Rectangle;
import java.util.ArrayList;

import com.jogamp.nativewindow.NativeSurface;
import com.jogamp.nativewindow.ScalableSurface;
import com.jogamp.opengl.GLAnimatorControl;
import com.jogamp.opengl.GLAutoDrawable;
import com.jogamp.opengl.GLCapabilities;
import com.jogamp.opengl.GLEventListener;
import com.jogamp.opengl.GLException;
import com.jogamp.opengl.GLProfile;
import com.jogamp.nativewindow.MutableGraphicsConfiguration;
import com.jogamp.newt.Display;
import com.jogamp.newt.MonitorDevice;
import com.jogamp.newt.NewtFactory;
import com.jogamp.newt.Screen;
import com.jogamp.newt.awt.NewtCanvasAWT;
import com.jogamp.newt.event.InputEvent;
import com.jogamp.newt.event.WindowAdapter;
import com.jogamp.newt.event.WindowEvent;
import com.jogamp.newt.opengl.GLWindow;
import com.jogamp.opengl.util.FPSAnimator;

import processing.core.PApplet;
import processing.core.PConstants;
import processing.core.PGraphics;
import processing.core.PImage;
import processing.core.PSurface;
import processing.event.KeyEvent;
import processing.event.MouseEvent;
import processing.opengl.PGraphicsOpenGL;
import processing.opengl.PGL;

public class PSurfaceJOGL implements PSurface {
  /** Selected GL profile */
  public static GLProfile profile;

  PJOGL pgl;

  GLWindow window;
//  Frame frame;
  FPSAnimator animator;
  Rectangle screenRect;

  PApplet sketch;
  PGraphics graphics;

  int sketchX;
  int sketchY;
  int sketchWidth;
  int sketchHeight;

  MonitorDevice displayDevice;
  Throwable drawException;
  Object waitObject = new Object();

  NewtCanvasAWT canvas;


  public PSurfaceJOGL(PGraphics graphics) {
    this.graphics = graphics;
    this.pgl = (PJOGL) ((PGraphicsOpenGL)graphics).pgl;
  }


  public void initOffscreen(PApplet sketch) {
    this.sketch = sketch;

    sketchWidth = sketch.sketchWidth();
    sketchHeight = sketch.sketchHeight();

    if (window != null) {
      canvas = new NewtCanvasAWT(window);
      canvas.setBounds(0, 0, window.getWidth(), window.getHeight());
//      canvas.setBackground(new Color(pg.backgroundColor, true));
      canvas.setFocusable(true);
    }
  }


  public void initFrame(PApplet sketch, int backgroundColor,
                        int deviceIndex, boolean fullScreen,
                        boolean spanDisplays) {
    this.sketch = sketch;

    Display display = NewtFactory.createDisplay(null);
    display.addReference();
    Screen screen = NewtFactory.createScreen(display, 0);
    screen.addReference();

    ArrayList<MonitorDevice> monitors = new ArrayList<MonitorDevice>();
    for (int i = 0; i < screen.getMonitorDevices().size(); i++) {
      MonitorDevice monitor = screen.getMonitorDevices().get(i);
      System.out.println("Monitor " + monitor.getId() + " ************");
      System.out.println(monitor.toString());
      System.out.println(monitor.getViewportInWindowUnits());
      System.out.println(monitor.getViewport());

      monitors.add(monitor);
    }
    System.out.println("*******************************");

    if (deviceIndex >= 0) {  // if -1, use the default device
      if (deviceIndex < monitors.size()) {
        displayDevice = monitors.get(deviceIndex);
      } else {
        System.err.format("Display %d does not exist, " +
          "using the default display instead.", deviceIndex);
        for (int i = 0; i < monitors.size(); i++) {
          System.err.format("Display %d is %s\n", i, monitors.get(i));
        }
      }
    }

    if (profile == null) {
      if (PJOGL.PROFILE == 2) {
        try {
          profile = GLProfile.getGL2ES1();
        } catch (GLException ex) {
          profile = GLProfile.getMaxFixedFunc(true);
        }
      } else if (PJOGL.PROFILE == 3) {
        try {
          profile = GLProfile.getGL2GL3();
        } catch (GLException ex) {
          profile = GLProfile.getMaxProgrammable(true);
        }
        if (!profile.isGL3()) {
          PGraphics.showWarning("Requested profile GL3 but is not available, got: " + profile);
        }
      } else if (PJOGL.PROFILE == 4) {
        try {
          profile = GLProfile.getGL4ES3();
        } catch (GLException ex) {
          profile = GLProfile.getMaxProgrammable(true);
        }
        if (!profile.isGL4()) {
          PGraphics.showWarning("Requested profile GL4 but is not available, got: " + profile);
        }
      } else throw new RuntimeException(PGL.UNSUPPORTED_GLPROF_ERROR);
    }

    // Setting up the desired capabilities;
    GLCapabilities caps = new GLCapabilities(profile);
    caps.setAlphaBits(PGL.REQUESTED_ALPHA_BITS);
    caps.setDepthBits(PGL.REQUESTED_DEPTH_BITS);
    caps.setStencilBits(PGL.REQUESTED_STENCIL_BITS);

//    caps.setPBuffer(false);
//    caps.setFBO(false);

    pgl.reqNumSamples = graphics.quality;
    caps.setSampleBuffers(true);
    caps.setNumSamples(pgl.reqNumSamples);
    caps.setBackgroundOpaque(true);
    caps.setOnscreen(true);
    pgl.capabilities = caps;
    System.err.println("0. create window");
    window = GLWindow.create(screen, caps);

    sketchWidth = sketch.sketchWidth();
    sketchHeight = sketch.sketchHeight();

    if (displayDevice == null) {
      displayDevice = window.getMainMonitor();
    }
    sketchX = displayDevice.getViewportInWindowUnits().getX();
    sketchY = displayDevice.getViewportInWindowUnits().getY();

    int screenWidth = screen.getWidth();
    int screenHeight = screen.getHeight();

    screenRect = spanDisplays ? new Rectangle(0, 0, screen.getWidth(), screen.getHeight()) :
                                new Rectangle(0, 0, displayDevice.getViewportInWindowUnits().getWidth(),
                                                    displayDevice.getViewportInWindowUnits().getHeight());

    sketch.displayWidth = screenRect.width;
    sketch.displayHeight = screenRect.height;

    // Sketch has already requested to be the same as the screen's
    // width and height, so let's roll with full screen mode.
    if (screenRect.width == sketchWidth &&
        screenRect.height == sketchHeight) {
      fullScreen = true;
    }

    if (fullScreen) {
      presentMode = sketchWidth < screenRect.width && sketchHeight < screenRect.height;
    }

    if (spanDisplays) {
      sketchWidth = screenRect.width;
      sketchHeight = screenRect.height;
    }

//    window..setBackground(new Color(backgroundColor, true));
    window.setSize(sketchWidth, sketchHeight);
    sketch.width = sketch.sketchWidth();
    sketch.height = sketch.sketchHeight();
    graphics.setSize(sketch.width, sketch.height);


    System.out.println("deviceIndex: " + deviceIndex);
    System.out.println(displayDevice);
    System.out.println("Screen res " + screenWidth + "x" + screenHeight);

    // This example could be useful:
    // com.jogamp.opengl.test.junit.newt.mm.TestScreenMode01cNEWT
    if (fullScreen) {
      window.setPosition(sketchX, sketchY);
      PApplet.hideMenuBar();
      if (spanDisplays) {
        window.setFullscreen(monitors);
      } else {
        window.setFullscreen(true);
      }
    }

    float[] reqSurfacePixelScale;
    if (graphics.is2X()) {
       // Retina
       reqSurfacePixelScale = new float[] { ScalableSurface.AUTOMAX_PIXELSCALE,
                                            ScalableSurface.AUTOMAX_PIXELSCALE };
//       pgl.pixel_scale = 2;
    } else {
      // Non-retina
      reqSurfacePixelScale = new float[] { ScalableSurface.IDENTITY_PIXELSCALE,
                                           ScalableSurface.IDENTITY_PIXELSCALE };
//      pgl.pixel_scale = 1;
    }
    window.setSurfaceScale(reqSurfacePixelScale);

    NEWTMouseListener mouseListener = new NEWTMouseListener();
    window.addMouseListener(mouseListener);
    NEWTKeyListener keyListener = new NEWTKeyListener();
    window.addKeyListener(keyListener);
    NEWTWindowListener winListener = new NEWTWindowListener();
    window.addWindowListener(winListener);

    DrawListener drawlistener = new DrawListener();
    window.addGLEventListener(drawlistener);



    System.err.println("1. create animator");
    animator = new FPSAnimator(window, 60);
    drawException = null;
    animator.setUncaughtExceptionHandler(new GLAnimatorControl.UncaughtExceptionHandler() {
      @Override
      public void uncaughtException(final GLAnimatorControl animator,
                                    final GLAutoDrawable drawable,
                                    final Throwable cause) {
        synchronized (waitObject) {
//          System.err.println("Caught exception: " + cause.getMessage());
          drawException = cause;
          waitObject.notify();
        }
      }
    });

   new Thread(new Runnable() {
      public void run() {
        synchronized (waitObject) {
          try {
            if (drawException == null) waitObject.wait();
          } catch (InterruptedException e) {
            e.printStackTrace();
          }
//          System.err.println("Caught exception: " + drawException.getMessage());
          if (drawException instanceof RuntimeException) {
            throw (RuntimeException)drawException.getCause();
          } else {
            throw new RuntimeException(drawException.getCause());
          }
        }
      }
    }).start();


    window.addWindowListener(new WindowAdapter() {
      @Override
      public void windowDestroyNotify(final WindowEvent e) {
        animator.stop();
        PSurfaceJOGL.this.sketch.exit();
        window.destroy();
      }
    });


    window.setVisible(true);
    System.err.println("4. set visible");

    /*
    try {
      EventQueue.invokeAndWait(new Runnable() {
        public void run() {
          window.setVisible(true);
          System.err.println("1. set visible");
      }});
    } catch (Exception ex) {
      // error setting the window visible, should quit...
    }
*/

//    frame = new DummyFrame();
//    return frame;
  }

  @Override
  public void setTitle(String title) {
    window.setTitle(title);
  }

  @Override
  public void setVisible(boolean visible) {
    window.setVisible(visible);
  }

  @Override
  public void setResizable(boolean resizable) {
    // TODO Auto-generated method stub

  }

  private void setFrameCentered() {
    // Can't use frame.setLocationRelativeTo(null) because it sends the
    // frame to the main display, which undermines the --display setting.
    int sketchX = displayDevice.getViewportInWindowUnits().getX();
    int sketchY = displayDevice.getViewportInWindowUnits().getY();
    System.err.println("just center on the screen at " + sketchX + screenRect.x + (screenRect.width - sketchWidth) / 2 + ", " +
                                                         sketchY + screenRect.y + (screenRect.height - sketchHeight) / 2);

    System.err.println("  Display starts at " +  sketchX + ", " + sketchY);
    System.err.println("  Screen rect pos: " +  screenRect.x + ", " + screenRect.y);
    System.err.println("  Screen rect w/h: " +  screenRect.width + ", " + screenRect.height);
    System.err.println("  Sketch w/h: " +  sketchWidth + ", " + sketchHeight);

    int w = sketchWidth;
    int h = sketchHeight;
    if (graphics.is2X()) {
      w /= 2;
      h /= 2;
    }

    window.setPosition(sketchX + screenRect.x + (screenRect.width - w) / 2,
                       sketchY + screenRect.y + (screenRect.height - h) / 2);
  }


  @Override
  public void placeWindow(int[] location, int[] editorLocation) {
//    Dimension dim = new Dimension(sketchWidth, sketchHeight);
//    int contentW = Math.max(sketchWidth, MIN_WINDOW_WIDTH);
//    int contentH = Math.max(sketchHeight, MIN_WINDOW_HEIGHT);

    if (location != null) {
      System.err.println("place window at " + location[0] + ", " + location[1]);
      // a specific location was received from the Runner
      // (applet has been run more than once, user placed window)
//      frame.setLocation(location[0], location[1]);
      window.setPosition(location[0], location[1]);

    } else if (editorLocation != null) {
      System.err.println("place window at editor location " + editorLocation[0] + ", " + editorLocation[1]);
      int locationX = editorLocation[0] - 20;
      int locationY = editorLocation[1];

      if (locationX - window.getWidth() > 10) {
        // if it fits to the left of the window
        window.setPosition(locationX - window.getWidth(), locationY);

      } else {  // doesn't fit
        // if it fits inside the editor window,
        // offset slightly from upper lefthand corner
        // so that it's plunked inside the text area
        locationX = editorLocation[0] + 66;
        locationY = editorLocation[1] + 66;

        if ((locationX + window.getWidth() > sketch.displayWidth - 33) ||
            (locationY + window.getHeight() > sketch.displayHeight - 33)) {
          // otherwise center on screen
          locationX = (sketch.displayWidth - window.getWidth()) / 2;
          locationY = (sketch.displayHeight - window.getHeight()) / 2;
        }
        window.setPosition(locationX, locationY);
      }
    } else {  // just center on screen
      setFrameCentered();
    }
    Point frameLoc = new Point(window.getX(), window.getY());
    if (frameLoc.y < 0) {
      // Windows actually allows you to place frames where they can't be
      // closed. Awesome. http://dev.processing.org/bugs/show_bug.cgi?id=1508
      window.setPosition(frameLoc.x, 30);
    }

//    canvas.setBounds((contentW - sketchWidth)/2,
//                     (contentH - sketchHeight)/2,
//                     sketchWidth, sketchHeight);



  }

  boolean presentMode = false;
  float offsetX;
  float offsetY;
  public void placePresent(int stopColor) {
    if (presentMode) {
      System.err.println("Present mode");
//    System.err.println("WILL USE FBO");
      presentMode = pgl.presentMode = true;
      offsetX = pgl.offsetX = 0.5f * (screenRect.width - sketchWidth);
      offsetY = pgl.offsetY = 0.5f * (screenRect.height - sketchHeight);
      pgl.requestFBOLayer();
    }
  }

  public void setupExternalMessages() {
    // TODO Auto-generated method stub

  }

  public void startThread() {
    if (animator != null) {
      System.err.println("5. start animator");
      animator.start();

      if (0 < sketchX && 0 < sketchY) {
          System.err.println("5.1 set inital window position");
          window.setPosition(sketchX, sketchY);
          sketchX = sketchY = 0;
      }
//      animator.getThread().setName("Processing-GL-draw");
    }
  }

  public void pauseThread() {
    if (animator != null) {
      animator.pause();
    }
  }

  public void resumeThread() {
    if (animator != null) {
      animator.resume();
    }
  }

  public boolean stopThread() {
    if (animator != null) {
      return animator.stop();
    } else {
      return false;
    }
  }

  public boolean isStopped() {
    if (animator != null) {
      return !animator.isAnimating();
    } else {
      return true;
    }
  }

  public void setSize(int width, int height) {
    if (animator.isAnimating()) {
      System.err.println("3. set size");

      if (!presentMode) {
        sketch.width = width;
        sketch.height = height;
        graphics.setSize(width, height);
      }

      sketchWidth = width;
      sketchHeight = height;
    }
  }

  public Component getComponent() {
    return canvas;
  }

  public void setSmooth(int level) {
    pgl.reqNumSamples = level;
    GLCapabilities caps = new GLCapabilities(profile);
    caps.setAlphaBits(PGL.REQUESTED_ALPHA_BITS);
    caps.setDepthBits(PGL.REQUESTED_DEPTH_BITS);
    caps.setStencilBits(PGL.REQUESTED_STENCIL_BITS);
    caps.setSampleBuffers(true);
    caps.setNumSamples(pgl.reqNumSamples);
    caps.setBackgroundOpaque(true);
    caps.setOnscreen(true);
    NativeSurface target = window.getNativeSurface();
    MutableGraphicsConfiguration config = (MutableGraphicsConfiguration) target.getGraphicsConfiguration();
    config.setChosenCapabilities(caps);
  }

  public void setFrameRate(float fps) {
    if (animator != null) {
      animator.stop();
      animator.setFPS((int)fps);
      pgl.setFps(fps);
      animator.start();
    }
  }

  public void requestFocus() {
    window.requestFocus();

  }

  class DrawListener implements GLEventListener {
    public void display(GLAutoDrawable drawable) {
      pgl.getGL(drawable);
//      System.out.println(" - " + sketch.frameCount);
      sketch.handleDraw();

      if (sketch.frameCount == 1) {
        requestFocus();
      }
    }
    public void dispose(GLAutoDrawable drawable) {
      pgl.getGL(drawable);
      sketch.dispose();
      if (sketch.exitCalled()) {
        sketch.exitActual();
      }
    }
    public void init(GLAutoDrawable drawable) {
      System.err.println("2. init drawable");
      pgl.getGL(drawable);
      pgl.init(drawable);
      sketch.start();
//      setSize(sketchWidth, sketchHeight);

      int c = graphics.backgroundColor;
      pgl.clearColor(((c >> 16) & 0xff) / 255f,
                     ((c >>  8) & 0xff) / 255f,
                     ((c >>  0) & 0xff) / 255f,
                     ((c >> 24) & 0xff) / 255f);
      pgl.clear(PGL.COLOR_BUFFER_BIT);
    }

    public void reshape(GLAutoDrawable drawable, int x, int y, int w, int h) {

      final float[] valReqSurfacePixelScale = window.getRequestedSurfaceScale(new float[2]);
      final float[] hasSurfacePixelScale = window.getCurrentSurfaceScale(new float[2]);
      final float[] nativeSurfacePixelScale = window.getMaximumSurfaceScale(new float[2]);
      System.err.println("[set PixelScale post]: "+
                         valReqSurfacePixelScale[0]+"x"+valReqSurfacePixelScale[1]+" (val) -> "+
                         hasSurfacePixelScale[0]+"x"+hasSurfacePixelScale[1]+" (has), "+
                         nativeSurfacePixelScale[0]+"x"+nativeSurfacePixelScale[1]+" (native)");




      System.out.println("reshape: " + w + ", " + h);
      pgl.getGL(drawable);
      if (!graphics.is2X() && 1 < hasSurfacePixelScale[0]) {
        setSize(w/2, h/2);
      } else {
        setSize(w, h);
      }
    }
  }

  protected class NEWTWindowListener implements com.jogamp.newt.event.WindowListener {
    public NEWTWindowListener() {
      super();
    }
    @Override
    public void windowGainedFocus(com.jogamp.newt.event.WindowEvent arg0) {
//      pg.parent.focusGained(null);
    }

    @Override
    public void windowLostFocus(com.jogamp.newt.event.WindowEvent arg0) {
//      pg.parent.focusLost(null);
    }

    @Override
    public void windowDestroyNotify(com.jogamp.newt.event.WindowEvent arg0) {
    }

    @Override
    public void windowDestroyed(com.jogamp.newt.event.WindowEvent arg0) {
    }

    @Override
    public void windowMoved(com.jogamp.newt.event.WindowEvent arg0) {
    }

    @Override
    public void windowRepaint(com.jogamp.newt.event.WindowUpdateEvent arg0) {
    }

    @Override
    public void windowResized(com.jogamp.newt.event.WindowEvent arg0) { }
  }

  // NEWT mouse listener
  protected class NEWTMouseListener extends com.jogamp.newt.event.MouseAdapter {
    public NEWTMouseListener() {
      super();
    }
    @Override
    public void mousePressed(com.jogamp.newt.event.MouseEvent e) {
      nativeMouseEvent(e, MouseEvent.PRESS);
    }
    @Override
    public void mouseReleased(com.jogamp.newt.event.MouseEvent e) {
      nativeMouseEvent(e, MouseEvent.RELEASE);
    }
    @Override
    public void mouseClicked(com.jogamp.newt.event.MouseEvent e) {
      nativeMouseEvent(e, MouseEvent.CLICK);
    }
    @Override
    public void mouseDragged(com.jogamp.newt.event.MouseEvent e) {
      nativeMouseEvent(e, MouseEvent.DRAG);
    }
    @Override
    public void mouseMoved(com.jogamp.newt.event.MouseEvent e) {
      nativeMouseEvent(e, MouseEvent.MOVE);
    }
    @Override
    public void mouseWheelMoved(com.jogamp.newt.event.MouseEvent e) {
      nativeMouseEvent(e, MouseEvent.WHEEL);
    }
    @Override
    public void mouseEntered(com.jogamp.newt.event.MouseEvent e) {
      nativeMouseEvent(e, MouseEvent.ENTER);
    }
    @Override
    public void mouseExited(com.jogamp.newt.event.MouseEvent e) {
      nativeMouseEvent(e, MouseEvent.EXIT);
    }
  }

  // NEWT key listener
  protected class NEWTKeyListener extends com.jogamp.newt.event.KeyAdapter {
    public NEWTKeyListener() {
      super();
    }
    @Override
    public void keyPressed(com.jogamp.newt.event.KeyEvent e) {
      nativeKeyEvent(e, KeyEvent.PRESS);
    }
    @Override
    public void keyReleased(com.jogamp.newt.event.KeyEvent e) {
      nativeKeyEvent(e, KeyEvent.RELEASE);
    }
    public void keyTyped(com.jogamp.newt.event.KeyEvent e)  {
      nativeKeyEvent(e, KeyEvent.TYPE);
    }
  }

  protected void nativeMouseEvent(com.jogamp.newt.event.MouseEvent nativeEvent,
                                  int peAction) {
    int modifiers = nativeEvent.getModifiers();
    int peModifiers = modifiers &
                      (InputEvent.SHIFT_MASK |
                       InputEvent.CTRL_MASK |
                       InputEvent.META_MASK |
                       InputEvent.ALT_MASK);

    int peButton = 0;
    if ((modifiers & InputEvent.BUTTON1_MASK) != 0) {
      peButton = PConstants.LEFT;
    } else if ((modifiers & InputEvent.BUTTON2_MASK) != 0) {
      peButton = PConstants.CENTER;
    } else if ((modifiers & InputEvent.BUTTON3_MASK) != 0) {
      peButton = PConstants.RIGHT;
    }

    if (PApplet.platform == PConstants.MACOSX) {
      //if (nativeEvent.isPopupTrigger()) {
      if ((modifiers & InputEvent.CTRL_MASK) != 0) {
        peButton = PConstants.RIGHT;
      }
    }

    int peCount = 0;
    if (peAction == MouseEvent.WHEEL) {
      peCount = nativeEvent.isShiftDown() ? (int)nativeEvent.getRotation()[0] :
                                            (int)nativeEvent.getRotation()[1];
    } else {
      peCount = nativeEvent.getClickCount();
    }


    if (presentMode) {
      if (peAction == KeyEvent.RELEASE &&
          20 < nativeEvent.getX() && nativeEvent.getX() < 20 + 100 &&
          screenRect.height - 70 < nativeEvent.getY() && nativeEvent.getY() < screenRect.height - 20) {
        System.err.println("clicked on exit button");
//      if (externalMessages) {
//        System.err.println(PApplet.EXTERNAL_QUIT);
//        System.err.flush();  // important
//      }
        animator.stop();
        PSurfaceJOGL.this.sketch.exit();
        window.destroy();
      }
    }

    final float[] hasSurfacePixelScale = window.getCurrentSurfaceScale(new float[2]);
    int x = nativeEvent.getX() - (int)offsetX;
    int y = nativeEvent.getY() - (int)offsetY;
    if (!graphics.is2X() && 1 < hasSurfacePixelScale[0]) {
      x /= 2;
      y /= 2;
    }

    MouseEvent me = new MouseEvent(nativeEvent, nativeEvent.getWhen(),
                                   peAction, peModifiers,
                                   x, y,
                                   peButton,
                                   peCount);

    sketch.postEvent(me);
  }

  protected void nativeKeyEvent(com.jogamp.newt.event.KeyEvent nativeEvent,
                                int peAction) {
    int peModifiers = nativeEvent.getModifiers() &
                      (InputEvent.SHIFT_MASK |
                       InputEvent.CTRL_MASK |
                       InputEvent.META_MASK |
                       InputEvent.ALT_MASK);

    short code = nativeEvent.getKeyCode();
    char keyChar;
    int keyCode;
    if (isPCodedKey(code)) {
      keyCode = mapToPConst(code);
      keyChar = PConstants.CODED;
    } else {
      keyCode = code;
      keyChar = nativeEvent.getKeyChar();
    }

    // From http://jogamp.org/deployment/v2.1.0/javadoc/jogl/javadoc/com/jogamp/newt/event/KeyEvent.html
    // public final short getKeySymbol()
    // Returns the virtual key symbol reflecting the current keyboard layout.
    // public final short getKeyCode()
    // Returns the virtual key code using a fixed mapping to the US keyboard layout.
    // In contrast to key symbol, key code uses a fixed US keyboard layout and therefore is keyboard layout independent.
    // E.g. virtual key code VK_Y denotes the same physical key regardless whether keyboard layout QWERTY or QWERTZ is active. The key symbol of the former is VK_Y, where the latter produces VK_Y.
    KeyEvent ke = new KeyEvent(nativeEvent, nativeEvent.getWhen(),
                               peAction, peModifiers,
                               keyChar,
                               keyCode);
//                               nativeEvent.getKeySymbol());

    sketch.postEvent(ke);
  }

  // Why do we need this mapping?
  // Relevant discussion and links here:
  // http://forum.jogamp.org/Newt-wrong-keycode-for-key-td4033690.html#a4033697
  // (I don't think this is a complete solution).
  private static int mapToPConst(short code) {
    if (code == com.jogamp.newt.event.KeyEvent.VK_UP) {
      return PConstants.UP;
    } else if (code == com.jogamp.newt.event.KeyEvent.VK_DOWN) {
      return PConstants.DOWN;
    } else if (code == com.jogamp.newt.event.KeyEvent.VK_LEFT) {
      return PConstants.LEFT;
    } else if (code == com.jogamp.newt.event.KeyEvent.VK_RIGHT) {
      return PConstants.RIGHT;
    } else if (code == com.jogamp.newt.event.KeyEvent.VK_ALT) {
      return PConstants.ALT;
    } else if (code == com.jogamp.newt.event.KeyEvent.VK_CONTROL) {
      return PConstants.CONTROL;
    } else if (code == com.jogamp.newt.event.KeyEvent.VK_SHIFT) {
      return PConstants.SHIFT;
    }
    return code;
  }

  private static boolean isPCodedKey(short code) {
    return code == com.jogamp.newt.event.KeyEvent.VK_UP ||
           code == com.jogamp.newt.event.KeyEvent.VK_DOWN ||
           code == com.jogamp.newt.event.KeyEvent.VK_LEFT ||
           code == com.jogamp.newt.event.KeyEvent.VK_RIGHT ||
           code == com.jogamp.newt.event.KeyEvent.VK_ALT ||
           code == com.jogamp.newt.event.KeyEvent.VK_CONTROL ||
           code == com.jogamp.newt.event.KeyEvent.VK_SHIFT;
  }

  // . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . .


  public void setCursor(int kind) {
    System.err.println("Cursor types not supported in OpenGL, provide your cursor image");
  }


  public void setCursor(PImage image, int hotspotX, int hotspotY) {
    final Display disp = window.getScreen().getDisplay();
    disp.createNative();

//    BufferedImage jimg = (BufferedImage)image.getNative();
//    IntBuffer buf = IntBuffer.wrap(jimg.getRGB(0, 0, jimg.getWidth(), jimg.getHeight(),
//                                               null, 0, jimg.getWidth()));
//
//    final PixelRectangle pixelrect = new PixelRectangle.GenericPixelRect(srcFmt, new Dimension(width, height),
//        srcStrideBytes, srcIsGLOriented, srcPixels);
//
//    PointerIcon pi = disp.createPointerIcon(PixelRectangle pixelrect,
//                                            hotspotX,
//                                            hotspotY);
//
//    window.setPointerIcon(pi);

  }

  public void showCursor() {
    window.setPointerVisible(true);
  }

  public void hideCursor() {
    window.setPointerVisible(false);
  }
}
