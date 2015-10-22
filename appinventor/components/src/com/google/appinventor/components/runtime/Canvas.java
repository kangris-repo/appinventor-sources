// -*- mode: java; c-basic-offset: 2; -*-
// Copyright 2009-2011 Google, All Rights reserved
// Copyright 2011-2012 MIT, All rights reserved
// Released under the Apache License, Version 2.0
// http://www.apache.org/licenses/LICENSE-2.0

package com.google.appinventor.components.runtime;

import android.net.Uri;
import android.hardware.Camera;
import android.hardware.Camera.CameraInfo;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import com.google.appinventor.components.annotations.DesignerComponent;
import com.google.appinventor.components.annotations.DesignerProperty;
import com.google.appinventor.components.annotations.PropertyCategory;
import com.google.appinventor.components.annotations.SimpleEvent;
import com.google.appinventor.components.annotations.SimpleFunction;
import com.google.appinventor.components.annotations.SimpleObject;
import com.google.appinventor.components.annotations.SimpleProperty;
import com.google.appinventor.components.annotations.UsesPermissions;
import com.google.appinventor.components.common.ComponentCategory;
import com.google.appinventor.components.common.ComponentConstants;
import com.google.appinventor.components.common.PropertyTypeConstants;
import com.google.appinventor.components.common.YaVersion;
import com.google.appinventor.components.runtime.collect.Sets;
import com.google.appinventor.components.runtime.util.BoundingBox;
import com.google.appinventor.components.runtime.util.EclairUtil;
import com.google.appinventor.components.runtime.util.ErrorMessages;
import com.google.appinventor.components.runtime.util.FileUtil;
import com.google.appinventor.components.runtime.util.FroyoUtil;
import com.google.appinventor.components.runtime.util.GingerbreadUtil;
import com.google.appinventor.components.runtime.util.MediaUtil;
import com.google.appinventor.components.runtime.util.PaintUtil;

import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.BitmapDrawable;
import android.text.TextUtils;
import android.util.Log;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import com.google.appinventor.components.runtime.util.SdkLevel;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

/**
 * <p>A two-dimensional touch-sensitive rectangular panel on which drawing can
 * be done and sprites can be moved.</p>
 *
 * <p>Conceptually, a sprite consists of the following layers, from back
 * to front (with items in front being drawn on top):
 * <ul>
 * <li> background color
 * <li> background image
 * <li> the "drawing layer", populated through calls to
 *      {@link #DrawPoint(int,int)}, {@link #DrawCircle(int,int,float)},
 *      {@link #DrawText(String,int,int)}, and
 *      {@link #DrawTextAtAngle(String,int,int,float)}, and
 *      {@link #SetBackgroundPixelColor(int,int,int)}
 * <li> the sprite layer, where sprites with higher Z values are drawn
 *      in front of (after) sprites with lower Z values.
 * </ul>
 * To the user, the first three layers are all the background, in terms
 * of the behavior of {@link #SetBackgroundPixelColor(int,int,int)} and
 * {@link #GetBackgroundPixelColor(int,int)}.  For historical reasons,
 * changing the background color or image clears the drawing layer.
 */
@DesignerComponent(version = YaVersion.CANVAS_COMPONENT_VERSION,
    description = "<p>A two-dimensional touch-sensitive rectangular panel on " +
    "which drawing can be done and sprites can be moved.</p> " +
    "<p>The <code>BackgroundColor</code>, <code>PaintColor</code>, " +
    "<code>BackgroundImage</code>, <code>Width</code>, and " +
    "<code>Height</code> of the Canvas can be set in either the Designer or " +
    "in the Blocks Editor.  The <code>Width</code> and <code>Height</code> " +
    "are measured in pixels and must be positive.</p>" +
    "<p>Any location on the Canvas can be specified as a pair of " +
    "(X, Y) values, where <ul> " +
    "<li>X is the number of pixels away from the left edge of the Canvas</li>" +
    "<li>Y is the number of pixels away from the top edge of the Canvas</li>" +
    "</ul>.</p> " +
    "<p>There are events to tell when and where a Canvas has been touched or " +
    "a <code>Sprite</code> (<code>ImageSprite</code> or <code>Ball</code>) " +
    "has been dragged.  There are also methods for drawing points, lines, " +
    "and circles.</p>",
    category = ComponentCategory.ANIMATION)
@SimpleObject
@UsesPermissions(permissionNames = "android.permission.INTERNET," +
                 "android.permission.CAMERA," +
                 "android.permission.WRITE_EXTERNAL_STORAGE")
public final class Canvas extends AndroidViewComponent implements ComponentContainer, OnStopListener {
  private static final String LOG_TAG = "Canvas";

  private final Activity context;
  private final CanvasView view;

  // Android can't correctly give the width and height of a canvas until
  // something has been drawn on it.
  private boolean drawn;

  private Uri imageFile;

  // Variables behind properties
  private int paintColor;
  private final Paint paint;
  private int backgroundColor;
  private String backgroundImagePath = "";
  private int textAlignment;
  private boolean cameraFeed = false;
  private boolean cameraUseFront = false;
  private int cameraOrientation = 0;

  // Default values
  private static final int MIN_WIDTH_HEIGHT = 1;
  private static final float DEFAULT_LINE_WIDTH = 2;
  private static final int DEFAULT_PAINT_COLOR = Component.COLOR_BLACK;
  private static final int DEFAULT_BACKGROUND_COLOR = Component.COLOR_WHITE;
  private static final int DEFAULT_TEXTALIGNMENT = Component.ALIGNMENT_CENTER;
  private static final int FLING_INTERVAL = 1000;  // ms

  // Keep track of enclosed sprites.  This list should always be
  // sorted by increasing sprite.Z().
  private final List<Sprite> sprites;

  // Handle touches and drags
  private final MotionEventParser motionEventParser;

  // Handle fling events
  private final GestureDetector mGestureDetector;

  //Handle camera feed
  private final CameraController cameraController;

  // The canvas has built-in detectors that trigger on touch, drag, touchDown,
  // TouchUp and Fling gestures.  It also maintains a set of additional gesture detectors
  // that can respond to motion events. These detectors
  // will typically be implemented by extension components that add the detector to this set.

  private final Set<ExtensionGestureDetector> extensionGestureDetectors = Sets.newHashSet();

  // additional gesture detectors must implement this interface
  public interface ExtensionGestureDetector {
    boolean onTouchEvent(MotionEvent event);
  };

  /**
   * Parser for Android {@link android.view.MotionEvent} sequences, which calls
   * the appropriate event handlers.  Specifically:
   * <ul>
   * <li> If a {@link android.view.MotionEvent#ACTION_DOWN} is followed by one
   * or more {@link android.view.MotionEvent#ACTION_MOVE} events, a sequence of
   * {@link Sprite#Dragged(float, float, float, float, float, float)}
   * calls are generated for sprites that were touched, and the final
   * {@link android.view.MotionEvent#ACTION_UP} is ignored.
   *
   * <li> If a {@link android.view.MotionEvent#ACTION_DOWN} is followed by an
   * {@link android.view.MotionEvent#ACTION_UP} event either immediately or
   * after {@link android.view.MotionEvent#ACTION_MOVE} events that take it no
   * further than {@link #TAP_THRESHOLD} pixels horizontally or vertically from
   * the start point, it is interpreted as a touch, and a single call to
   * {@link Sprite#Touched(float, float)} for each touched sprite is
   * generated.
   * </ul>
   *
   * After the {@code Dragged()} or {@code Touched()} methods are called for
   * any applicable sprites, a call is made to
   * {@link Canvas#Dragged(float, float, float, float, float, float, boolean)}
   * or {@link Canvas#Touched(float, float, boolean)}, respectively.  The
   * additional final argument indicates whether it was preceded by one or
   * more calls to a sprite, i.e., whether the locations on the canvas had a
   * sprite on them ({@code true}) or were empty of sprites {@code false}).
   *
   *
   */
  class MotionEventParser {
    /**
     * The number of pixels right, left, up, or down, a sequence of drags must
     * move from the starting point to be considered a drag (instead of a
     * touch).
     */
    // This used to be 30 and people complained that they could not draw small circles.
    // If the threshold is too small, then touches might be misinterpreted as drags,
    // this might require more experimentation.  We might also want to take screen resolution
    // into account and/or try to make a more clever motion parser.
    public static final int TAP_THRESHOLD = 15;

    /**
     * The width of a finger.  This is used in determining whether a sprite is
     * touched.  Specifically, this is used to determine the horizontal extent
     * of a bounding box that is tested for collision with each sprite.  The
     * vertical extent is determined by {@link #FINGER_HEIGHT}.
     */
    public static final int FINGER_WIDTH = 24;

    /**
     * The width of a finger.  This is used in determining whether a sprite is
     * touched.  Specifically, this is used to determine the vertical extent
     * of a bounding box that is tested for collision with each sprite.  The
     * horizontal extent is determined by {@link #FINGER_WIDTH}.
     */
    public static final int FINGER_HEIGHT = 24;

    private static final int HALF_FINGER_WIDTH = FINGER_WIDTH / 2;
    private static final int HALF_FINGER_HEIGHT = FINGER_HEIGHT / 2;

    /**
     * The set of sprites encountered in a touch or drag sequence.  Checks are
     * only made for sprites at the endpoints of each drag.
     */
    private final List<Sprite> draggedSprites = new ArrayList<Sprite>();

    // startX and startY hold the coordinates of where a touch/drag started
    private static final int UNSET = -1;
    private float startX = UNSET;
    private float startY = UNSET;

    // lastX and lastY hold the coordinates of the previous step of a drag
    private float lastX = UNSET;
    private float lastY = UNSET;

    // Is this sequence of events a drag? I.e., has the touch point moved away
    // from the start point?
    private boolean isDrag = false;

    private boolean drag = false;

    void parse(MotionEvent event) {
      int width = Width();
      int height = Height();

      // Coordinates less than 0 can be returned if a move begins within a
      // view and ends outside of it.  Because negative coordinates would
      // probably confuse the user (as they did me) and would not be useful,
      // we replace any negative values with zero.
      float x = Math.max(0, (int) event.getX() / $form().deviceDensity());
      float y = Math.max(0, (int) event.getY() / $form().deviceDensity());

      // Also make sure that by adding or subtracting a half finger that
      // we don't go out of bounds.
      BoundingBox rect = new BoundingBox(
          Math.max(0, (int) x - HALF_FINGER_HEIGHT),
          Math.max(0, (int) y - HALF_FINGER_WIDTH),
          Math.min(width - 1, (int) x + HALF_FINGER_WIDTH),
          Math.min(height - 1, (int) y + HALF_FINGER_HEIGHT));

      switch (event.getAction()) {
        case MotionEvent.ACTION_DOWN:
          draggedSprites.clear();
          startX = x;
          startY = y;
          lastX = x;
          lastY = y;
          drag = false;
          isDrag = false;
          for (Sprite sprite : sprites) {
            if (sprite.Enabled() && sprite.Visible() && sprite.intersectsWith(rect)) {
              draggedSprites.add(sprite);
              sprite.TouchDown(startX, startY);
            }
          }
          TouchDown(startX, startY);
          break;

        case MotionEvent.ACTION_MOVE:
          // Ensure that this was preceded by an ACTION_DOWN
          if (startX == UNSET || startY == UNSET || lastX == UNSET || lastY == UNSET) {
            Log.w(LOG_TAG, "In Canvas.MotionEventParser.parse(), " +
                "an ACTION_MOVE was passed without a preceding ACTION_DOWN: " + event);
          }

          // If the new point is near the start point, it may just be a tap
          if (!isDrag &&
              (Math.abs(x - startX) < TAP_THRESHOLD && Math.abs(y - startY) < TAP_THRESHOLD)) {
            break;
          }
          // Otherwise, it's a drag.
          isDrag = true;
          drag = true;

          // Update draggedSprites by adding any that are currently being
          // touched.
          for (Sprite sprite : sprites) {
            if (!draggedSprites.contains(sprite)
                && sprite.Enabled() && sprite.Visible()
                && sprite.intersectsWith(rect)) {
              draggedSprites.add(sprite);
            }
          }

          // Raise a Dragged event for any affected sprites
          boolean handled = false;
          for (Sprite sprite : draggedSprites) {
            if (sprite.Enabled() && sprite.Visible()) {
              sprite.Dragged(startX, startY, lastX, lastY, x, y);
              handled = true;
            }
          }

          // Last argument indicates whether a sprite handled the drag
          Dragged(startX, startY, lastX, lastY, x, y, handled);
          lastX = x;
          lastY = y;
          break;

        case MotionEvent.ACTION_UP:
          // If we never strayed far from the start point, it's a tap.  (If we
          // did stray far, we've already handled the movements in the ACTION_MOVE
          // case.)
          if (!drag) {
            // It's a tap
            handled = false;
            for (Sprite sprite : draggedSprites) {
              if (sprite.Enabled() && sprite.Visible()) {
                sprite.Touched(x, y);
                sprite.TouchUp(x, y);
                handled = true;
              }
            }
            // Last argument indicates that one or more sprites handled the tap
            Touched(x, y, handled);
          }
          else {
            for (Sprite sprite : draggedSprites) {
              if (sprite.Enabled() && sprite.Visible()) {
                sprite.Touched(x, y);
                sprite.TouchUp(x, y);
              }
            }
          }
          // This is intentionally outside the if (!drag) block.
          // Even the release of a drag on the canvas should fire
          // a touch-up event.
          TouchUp(x, y);

          // Prepare for next drag
          drag = false;
          startX = UNSET;
          startY = UNSET;
          lastX = UNSET;
          lastY = UNSET;
          break;
      }
    }
  }


  class CameraController {

    private int cameraId = -1;
    private Camera camera = null;
    private boolean previewing = false; // Is CanvasView Previewing the feed from Camera


    public boolean setCameraFeed() {

      if (view == null || !view.surfaceCreated) {
        return false;
      }

      if (camera == null || cameraId == -1) {
        openCamera();
      }

      if (camera == null) {
        return false; // Camera not acquired, cancel operation.
      }

      if (previewing) {
        unsetCameraFeed(false);
      }

      // Set Camera parameters for best supported preview size.

        setCameraParameters();

      // Set Camera Orientation

      try {
        camera.setPreviewDisplay(view.getHolder());
        camera.startPreview();
        previewing = true;

      } catch (IOException exception) {
        Log.d("MIT","Error in setPreviewDisplay " + exception.getMessage());
        //form . dispatchErrorOccurredEvent ?
        unsetCameraFeed(true);
        return previewing;
      }

      return previewing;

    }

    public void unsetCameraFeed(boolean releaseCamera) {
      if (camera == null) {
        return;
      }
      camera.stopPreview();
      previewing = false;
      if (releaseCamera) {
        releaseCamera();
      }
    }

    public boolean resetCameraFeed() {
      if (cameraFeed) {
        setCameraFeed();
      }
      else {
        unsetCameraFeed(true);
      }
      return cameraFeed;
    }

    private void openCamera() {
      if (camera != null) {
        releaseCamera();
      }
      cameraId = getCameraId();
      try {
        if (SdkLevel.getLevel() >= SdkLevel.LEVEL_GINGERBREAD && cameraId > 0) {
          camera = GingerbreadUtil.cameraOpen(cameraId);
        }
        else {
          camera = Camera.open();
        }
      } catch (RuntimeException exception) {
        Log.d("MIT","Error in Opening Camera " + exception.getMessage());
        camera = null;
      }
      if (camera == null) {
        releaseCamera();
      }
    }

    private void releaseCamera() {
      if (camera == null) {
        cameraId = -1;
        previewing = false;
        return;
      }
      if (previewing) {
        unsetCameraFeed(false);
      }
      camera.release();
      camera = null;
      cameraId = -1;
    }

    private int getCameraId() {
      int camId = -1;
      int camCount = getNumberOfCameras();
      if (camCount >= 0) {
        camId = 0;
      }
      if (SdkLevel.getLevel() >= SdkLevel.LEVEL_GINGERBREAD) {
        CameraInfo camInfo = GingerbreadUtil.newCameraInfo();
        for (int cId = 0; cId < camCount; cId++) {
          GingerbreadUtil.getCameraInfo(cId, camInfo);
          if (cameraUseFront && camInfo.facing == CameraInfo.CAMERA_FACING_FRONT) {
            camId = cId;
          } else if (!cameraUseFront && camInfo.facing == CameraInfo.CAMERA_FACING_BACK) {
            camId = cId;
          }
        }
      }
      return camId;
    }

    private int getNumberOfCameras() {
      // No definite way for checking camera feature for devices with API less than 5 (ECLAIR)
      int camNum = 1; //Assume 1 Camera Exists
      if (SdkLevel.getLevel() >= SdkLevel.LEVEL_ECLAIR) {
        camNum = EclairUtil.hasSystemFeature(PackageManager.FEATURE_CAMERA, container.$form()) ? 1 : 0;
      }
      if (SdkLevel.getLevel() >= SdkLevel.LEVEL_GINGERBREAD) {
        camNum = GingerbreadUtil.getNumberOfCameras();
      }
      return camNum;
    }

    private boolean setCameraParameters() {
      if (camera == null) {
        return false;
      }
      Camera.Parameters params = camera.getParameters();
      int camOrientation = getCameraOrientation(cameraId);
      int dispRotation = getDisplayRotation();
      int previewOrientation = getPreviewOrientation(cameraId, camOrientation, dispRotation);
      int imageRotation = getImageRotation(cameraId, camOrientation, dispRotation);
      setPreviewOrientation(previewOrientation);
      setImageRotation(params, imageRotation);

      if (SdkLevel.getLevel() >= SdkLevel.LEVEL_ECLAIR) {
        Camera.Size picSize = getBestPictureSize(EclairUtil.getCameraSupportedPictureSizes(params));

        double targetAspectRatio = (double) picSize.width / (double) picSize.height;
        Camera.Size preSize = getBestPreviewSize(EclairUtil.getCameraSupportedPreviewSizes(params), targetAspectRatio);
        //Camera.Size preSize = getMaxPreviewSize(EclairUtil.getCameraSupportedPreviewSizes(params), targetAspectRatio);

        params.setPreviewSize(preSize.width, preSize.height);
        params.setPictureSize(picSize.width, picSize.height);
      }

      camera.setParameters(params);
      return true;
    }

    /**
     * Gets the Camera's Highest Resolution Capture
     * @param sizes
     * @return
     */
    private Camera.Size getBestPictureSize(List<Camera.Size> sizes) {
      Camera.Size result = null;
      int area = 0;
      for (Camera.Size size : sizes) {
        Log.d("MIT", "SupportedPicture Size ="+size.width+"x"+size.height);
        int newArea = size.width * size.height;
        if (newArea > area) {
          result = size;
          area = newArea;
        }
      }

      Log.d("MIT", "Picture Size Selected = " + result.width + "x" + result.height);

      return result;
    }

    private Camera.Size getBestPreviewSize(List<Camera.Size> sizes, double targetAspectRatio) {

      if (sizes == null) {
        return null;
      }

      final double ASPECT_TOLERANCE = 0.5;
      Camera.Size result = null;
      double minDiff = Double.MAX_VALUE;

      for (Camera.Size size : sizes) {
        Log.d("MIT", "SupportedPreview Size ="+size.width+"x"+size.height);
        double ratio = (double) size.width / (double) size.height;
        if (Math.abs(ratio - targetAspectRatio) > ASPECT_TOLERANCE) {
          continue;
        }
        if (Math.abs(size.height - getView().getHeight()) < minDiff) {
          result = size;
          minDiff = Math.abs(size.height - getView().getHeight());
        }
      }

      // No Preview Size matching Aspect Ratio
      // We will select the largest fitting Preview Size
      if (result == null) {
        Log.d("MIT", "No preview size with target aspect ratio");
        minDiff = Double.MAX_VALUE;
        for (Camera.Size size : sizes) {
          if (Math.abs(size.height - getView().getHeight()) < minDiff) {
            result = size;
            minDiff = Math.abs(size.height - getView().getHeight());
          }
        }
      }

      Log.d("MIT", "Preview Size Selected = " + result.width + "x" + result.height);

      return result;
    }

    private Camera.Size getMaxPreviewSize(List<Camera.Size> sizes, double targetAspectRatio) {
      Camera.Size result = null;
      final double ASPECT_TOLERANCE = 5.0;
      double minDiff = Double.MAX_VALUE;
      for (Camera.Size size : sizes) {
        double ratio = (double) size.width / size.height;
        if (Math.abs(ratio - targetAspectRatio) > ASPECT_TOLERANCE) continue;
        if ((size.height - getView().getHeight()) < minDiff ) {
          result = size;
          minDiff = Math.abs(size.height - getView().getHeight());
        }
      }
      Log.d("MIT", "Max Preview Size Selected = " + result.width + "x" + result.height);
      return result;
    }

    private int getCameraOrientation(int camId) {
      int orientation = 0;
      switch (cameraOrientation) {
        case 0: // Automatic Orientation
          if (SdkLevel.getLevel() >= SdkLevel.LEVEL_GINGERBREAD) {
            CameraInfo camInfo = GingerbreadUtil.newCameraInfo();
            GingerbreadUtil.getCameraInfo(camId, camInfo);
            orientation = camInfo.orientation;
          } else {
            orientation = 90; // This is bad assumption but most manufacturers seem to mount the camera at 90
          }
          break;
        case 1: // Top
          orientation = 0;
          break;
        case 2: // Right
          orientation = 90;
          break;
        case 3: // Bottom
          orientation = 180;
          break;
        case 4: // Left
          orientation = 270;
          break;
      }

      Log.d("MIT", "Camera Orientation =" + orientation);
      return orientation;
      }

    private int getDisplayRotation() {
      int rotation = 0;
      int degrees = 0;

      if (SdkLevel.getLevel() >= SdkLevel.LEVEL_FROYO) {
        rotation = FroyoUtil.getRotation($form().getWindowManager().getDefaultDisplay());
      }
      else {
        rotation = $form().getWindowManager().getDefaultDisplay().getOrientation();
      }
      switch (rotation) {
        case Surface.ROTATION_0:
          degrees = 0;
          break;
        case Surface.ROTATION_90:
          degrees = 90;
          break;
        case Surface.ROTATION_180:
          degrees = 180;
          break;
        case Surface.ROTATION_270:
          degrees = 270;
          break;
      }
      Log.d("MIT", "Display Rotation =" + rotation);
      return degrees;
    }

    private int getPreviewOrientation(int camId, int camOrientation, int dispRotation) {
      int result = 0;
      Log.d("MIT"," :: CamOrientation=" + camOrientation + " rotation=" + dispRotation);
      if (SdkLevel.getLevel() >= SdkLevel.LEVEL_GINGERBREAD) {
        CameraInfo cameraInfo = GingerbreadUtil.newCameraInfo();
        GingerbreadUtil.getCameraInfo(camId, cameraInfo);
        if (cameraInfo.facing == CameraInfo.CAMERA_FACING_FRONT) {
          result = ( camOrientation + dispRotation )  % 360;
          result = ( 360 - result ) % 360;
        }
        else {
          result = ( camOrientation - dispRotation + 360 )  % 360;
        }
      }
      else {
        result = ( camOrientation - dispRotation + 360 )  % 360;
      }
      Log.d("MIT", "Preview Orientation =" + result);
      return result;
    }

    private int getImageRotation(int camId, int camOrientation, int dispRotation) {
      int result = 0;
      Log.d("MIT"," :: CamOrientation=" + camOrientation + " rotation=" + dispRotation);
      if (SdkLevel.getLevel() > SdkLevel.LEVEL_GINGERBREAD) {
        CameraInfo cameraInfo = GingerbreadUtil.newCameraInfo();
        GingerbreadUtil.getCameraInfo(camId, cameraInfo);
        if (cameraInfo.facing == CameraInfo.CAMERA_FACING_FRONT) {
          result = ( camOrientation - dispRotation + 360 )  % 360;
        }
        else {
          result = ( camOrientation + dispRotation )  % 360;
        }
      }
      else {
        result = ( camOrientation + dispRotation )  % 360;
      }
      Log.d("MIT", "Image Rotation =" + result);
      return result;
    }

    private boolean setPreviewOrientation(int previewOrientation) {
      if (camera == null) {
        return false;
      }
      if (SdkLevel.getLevel() >= SdkLevel.LEVEL_FROYO) {
        camera.setDisplayOrientation(previewOrientation);
        return true;
      }
      else {
        return false;
      }
    }

    private boolean setImageRotation(Camera.Parameters params, int imageRotation) {
      if (SdkLevel.getLevel() >= SdkLevel.LEVEL_ECLAIR) {
        EclairUtil.setCameraParameterRotation(params, imageRotation);
        return true;
      }
      else {
        return false;
      }
    }

  }

  /**
   * Panel for drawing and manipulating sprites.
   *
   */
  private final class CanvasView extends SurfaceView implements SurfaceHolder.Callback {
    // Variables to implement View
    private android.graphics.Canvas canvas;
    private Bitmap bitmap;  // Bitmap backing Canvas

    // Support for background images
    private BitmapDrawable backgroundDrawable;

    //Check whether Surface is Ready for Camera Feed
    private boolean surfaceCreated = false;

    // Support for GetBackgroundPixelColor() and GetPixelColor().

    // scaledBackgroundBitmap is a scaled version of backgroundDrawable that
    // is created only if getBackgroundPixelColor() is called.  It is set back
    // to null whenever the canvas size or backgroundDrawable changes.
    private Bitmap scaledBackgroundBitmap;

    // completeCache is created if the user calls getPixelColor().  It is set
    // back to null whenever the view is redrawn.  If available, it is used
    // when the Canvas is saved to a file.
    private Bitmap completeCache;

    public CanvasView(Context context) {
      super(context);
      bitmap = Bitmap.createBitmap(ComponentConstants.CANVAS_PREFERRED_WIDTH,
                                   ComponentConstants.CANVAS_PREFERRED_HEIGHT,
                                   Bitmap.Config.ARGB_8888);
      canvas = new android.graphics.Canvas(bitmap);
    }

    /*
     * Create a bitmap showing the background (image or color) and drawing
     * (points, lines, circles, text) layer of the view but not any sprites.
     */
    private Bitmap buildCache() {
      // First, try building drawing cache.
      setDrawingCacheEnabled(true);
      destroyDrawingCache();      // clear any earlier versions we have requested
      Bitmap cache = getDrawingCache();  // may return null if size is too large

      // If drawing cache can't be built, build a cache manually.
      if (cache == null) {
        int width = getWidth();
        int height = getHeight();
        cache = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        android.graphics.Canvas c = new android.graphics.Canvas(cache);
        layout(0, 0, width, height);
        draw(c);
      }
      return cache;
    }

    @Override
    public void onDraw(android.graphics.Canvas canvas0) {
      completeCache = null;

      // This will draw the background image and color, if present.
      super.onDraw(canvas0);

      // Redraw anything that had been directly drawn on the old Canvas,
      // such as lines and circles but not Sprites.
      canvas0.drawBitmap(bitmap, 0, 0, null);

      // sprites is sorted by Z level, so sprites with low Z values will be
      // drawn first, potentially being hidden by Sprites with higher Z values.
      for (Sprite sprite : sprites) {
        sprite.onDraw(canvas0);
      }
      drawn = true;
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldW, int oldH) {
      int oldBitmapWidth = bitmap.getWidth();
      int oldBitmapHeight = bitmap.getHeight();
      if (w != oldBitmapWidth || h != oldBitmapHeight) {
        Bitmap oldBitmap = bitmap;

        // Create a new bitmap by scaling the old bitmap that contained the
        // drawing layer (points, lines, text, etc.).

        // The documentation for Bitmap.createScaledBitmap doesn't specify whether it creates a
        // mutable or immutable bitmap. Looking at the source code shows that it calls
        // Bitmap.createBitmap(Bitmap, int, int, int, int, Matrix, boolean), which is documented as
        // returning an immutable bitmap. However, it actually returns a mutable bitmap.
        // It's possible that the behavior could change in the future if they "fix" that bug.
        // Try Bitmap.createScaledBitmap, but if it gives us an immutable bitmap, we'll have to
        // create a mutable bitmap and scale the old bitmap using Canvas.drawBitmap.
        try {
          // See comment at the catch below
          Bitmap scaledBitmap = Bitmap.createScaledBitmap(oldBitmap, w, h, false);

          if (scaledBitmap.isMutable()) {
            // scaledBitmap is mutable; we can use it in a canvas.
            bitmap = scaledBitmap;
            // NOTE(lizlooney) - I tried just doing canvas.setBitmap(bitmap), but after that the
            // canvas.drawCircle() method did not work correctly. So, we need to create a whole new
            // canvas.
            canvas = new android.graphics.Canvas(bitmap);

          } else {
            // scaledBitmap is immutable; we can't use it in a canvas.

            bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
            // NOTE(lizlooney) - I tried just doing canvas.setBitmap(bitmap), but after that the
            // canvas.drawCircle() method did not work correctly. So, we need to create a whole new
            // canvas.
            canvas = new android.graphics.Canvas(bitmap);

            // Draw the old bitmap into the new canvas, scaling as necessary.
            Rect src = new Rect(0, 0, oldBitmapWidth, oldBitmapHeight);
            RectF dst = new RectF(0, 0, w, h);
            canvas.drawBitmap(oldBitmap, src, dst, null);
          }

        } catch (IllegalArgumentException ioe) {
          // There's some kind of order of events issue that results in w or h being zero.
          // I'm guessing that this is a result of specifying width or height as FILL_PARRENT on an
          // opening screen.   In any case, w<=0 or h<=0 causes the call to createScaledBitmap
          // to throw an illegal argument.  If this happens we simply don't draw the bitmap
          // (which would be of width or height 0)
          // TODO(hal): Investigate this further to see what is causes the w=0 or h=0 and see if
          // there is a more high-level fix.

          Log.e(LOG_TAG, "Bad values to createScaledBimap w = " + w + ", h = " + h);
        }

        // The following has nothing to do with the scaling in this method.
        // It has to do with scaling the background image for GetColor().
        // Specifically, it says we need to regenerate the bitmap representing
        // the background color/image if a call to GetColor() is made.
        scaledBackgroundBitmap = null;
      }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
      int preferredWidth;
      int preferredHeight;
      if (backgroundDrawable != null) {
        // Drawable.getIntrinsicWidth/Height gives weird values, but Bitmap.getWidth/Height works.
        Bitmap bitmap = backgroundDrawable.getBitmap();
        preferredWidth = bitmap.getWidth();
        preferredHeight = bitmap.getHeight();
      } else {
        preferredWidth = ComponentConstants.CANVAS_PREFERRED_WIDTH;
        preferredHeight = ComponentConstants.CANVAS_PREFERRED_HEIGHT;
      }
      setMeasuredDimension(getSize(widthMeasureSpec, preferredWidth),
          getSize(heightMeasureSpec, preferredHeight));
    }

    private int getSize(int measureSpec, int preferredSize) {
      int result;
      int specMode = MeasureSpec.getMode(measureSpec);
      int specSize = MeasureSpec.getSize(measureSpec);

      if (specMode == MeasureSpec.EXACTLY) {
        // We were told how big to be
        result = specSize;
      } else {
        // Use the preferred size.
        result = preferredSize;
        if (specMode == MeasureSpec.AT_MOST) {
          // Respect AT_MOST value if that was what is called for by measureSpec
          result = Math.min(result, specSize);
        }
      }

      return result;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
      // The following call results in the Form not grabbing our events and
      // handling dragging on its own, which it wants to do to handle scrolling.
      // Its effect only lasts long as the current set of motion events
      // generated during this touch and drag sequence.  Consequently, it needs
      // to be called here, so that it happens for each touch-drag sequence.
      container.$form().dontGrabTouchEventsForComponent();
      motionEventParser.parse(event);
      mGestureDetector.onTouchEvent(event); // handle onFling here
      // let each detector in the custom list handle the event
      for (ExtensionGestureDetector g : extensionGestureDetectors) {
        //  Log.i("Canvas", "Calling detector: " + g.toString());
        //  Log.i("Canvas", "sending motion event " + event.toString());
        g.onTouchEvent(event);
      }
      return true;
    }

    // Methods supporting properties

    // This mutates backgroundImagePath in the outer class
    // and backgroundDrawable in this class.
    //
    // This erases the drawing layer (lines, text, etc.), whether or not
    // a valid image is loaded, to be compatible with earlier versions
    // of App Inventor.
    void setBackgroundImage(String path) {
      backgroundImagePath = (path == null) ? "" : path;
      backgroundDrawable = null;
      scaledBackgroundBitmap = null;

      if (!TextUtils.isEmpty(backgroundImagePath)) {
        try {
          backgroundDrawable = MediaUtil.getBitmapDrawable(container.$form(), backgroundImagePath);
        } catch (IOException ioe) {
          Log.e(LOG_TAG, "Unable to load " + backgroundImagePath);
        }
      }

      setBackgroundDrawable(backgroundDrawable);

      // If the path was null or the empty string, or if IOException was
      // raised, backgroundDrawable will be null.  The only difference
      // from the case of a successful image load is that we must draw
      // in the background color, if present.
      if (backgroundDrawable == null) {
        super.setBackgroundColor(backgroundColor);
      }

      clearDrawingLayer();  // will call invalidate()
    }

    private void clearDrawingLayer() {
      canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR);
      invalidate();
    }

    // This mutates backgroundColor in the outer class.
    // This erases the drawing layer (lines, text, etc.) to be compatible
    // with earlier versions of App Inventor.
    @Override
    public void setBackgroundColor(int color) {
      backgroundColor = color;

      // Only draw the background color if no image.
      if (backgroundDrawable == null) {
        super.setBackgroundColor(color);
      }

      clearDrawingLayer();
    }

    // These methods support SimpleFunctions.
    private void drawTextAtAngle(String text, int x, int y, float angle) {
      canvas.save();
      canvas.rotate(-angle, x, y);
      canvas.drawText(text, x, y, paint);
      canvas.restore();
      invalidate();
    }

    // This intentionally ignores sprites.
    private int getBackgroundPixelColor(int x, int y) {
      // If the request is out of bounds, return COLOR_NONE.
      if (x < 0 || x >= bitmap.getWidth() ||
          y < 0 || y >= bitmap.getHeight()) {
        return Component.COLOR_NONE;
      }

      try {
        // First check if anything has been drawn on the bitmap
        // (such as by DrawPoint, DrawCircle, etc.).
        int color = bitmap.getPixel(x, y);
        if (color != Color.TRANSPARENT) {
          return color;
        }

        // If nothing has been drawn on the bitmap at that location,
        // check if there is a background image.
        if (backgroundDrawable != null) {
          if (scaledBackgroundBitmap == null) {
            scaledBackgroundBitmap = Bitmap.createScaledBitmap(
                backgroundDrawable.getBitmap(),
                bitmap.getWidth(), bitmap.getHeight(),
                false);  // false argument indicates not to filter
          }
          color = scaledBackgroundBitmap.getPixel(x, y);
          return color;
        }

        // If there is no background image, use the background color.
        if (Color.alpha(backgroundColor) != 0) {
          return backgroundColor;
        }
        return Component.COLOR_NONE;
      } catch (IllegalArgumentException e) {
        // This should never occur, since we have checked bounds.
        Log.e(LOG_TAG,
            String.format("Returning COLOR_NONE (exception) from getBackgroundPixelColor."));
        return Component.COLOR_NONE;
      }
    }

    private int getPixelColor(int x, int y) {
      // If the request is out of bounds, return COLOR_NONE.
      if (x < 0 || x >= bitmap.getWidth() ||
          y < 0 || y >= bitmap.getHeight()) {
        return Component.COLOR_NONE;
      }

      // If the cache isn't available, try to avoid rebuilding it.
      if (completeCache == null) {
        // If there are no visible sprites, just call getBackgroundPixelColor().
        boolean anySpritesVisible = false;
        for (Sprite sprite : sprites) {
          if (sprite.Visible()) {
            anySpritesVisible = true;
            break;
          }
        }
        if (!anySpritesVisible) {
          return getBackgroundPixelColor(x, y);
        }

        // TODO(user): If needed for efficiency, check whether there are any
        // sprites overlapping (x, y).  If not, we can just call getBackgroundPixelColor().
        // If so, maybe we can just draw those sprites instead of building a full
        // cache of the view.

        completeCache = buildCache();
      }

      // Check the complete cache.
      try {
        return completeCache.getPixel(x, y);
      } catch (IllegalArgumentException e) {
        // This should never occur, since we have checked bounds.
        Log.e(LOG_TAG,
            String.format("Returning COLOR_NONE (exception) from getPixelColor."));
        return Component.COLOR_NONE;
      }
    }

    @Override
    public void surfaceCreated(SurfaceHolder surfaceHolder) {
      surfaceCreated = true;
      Log.d("MIT","SurfaceCreated");
    }

    @Override
    public void surfaceChanged(SurfaceHolder surfaceHolder, int format, int width, int height) {
      Log.d("MIT","SurfaceChanged Size " + width + "x" + height);
      Log.d("MIT","Canvas Size " + Width() + "x" + Height());
      Log.d("MIT","CanvasView Size " + view.getWidth() + "x" + view.getHeight());
      cameraController.resetCameraFeed();

    }

    @Override
    public void surfaceDestroyed(SurfaceHolder surfaceHolder) {
      if (cameraController.previewing) {
        cameraController.unsetCameraFeed(true);
      }
      surfaceCreated = false;
    }


  }

  public Canvas(ComponentContainer container) {
    super(container);
    context = container.$context();

    // Create view and add it to its designated container.
    view = new CanvasView(context);
    // To know when the SurfaceView is ready for Camera Feed
    view.getHolder().addCallback(view);
    // This method is deprecated for API 11 and above, it is ignored and set automatically
    view.getHolder().setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);


    container.$add(this);
    $form().registerForOnStop(this);

    paint = new Paint();
    paint.setFlags(Paint.ANTI_ALIAS_FLAG);

    // Set default properties.
    paint.setStrokeWidth(DEFAULT_LINE_WIDTH);
    PaintColor(DEFAULT_PAINT_COLOR);
    BackgroundColor(DEFAULT_BACKGROUND_COLOR);
    TextAlignment(DEFAULT_TEXTALIGNMENT);
    FontSize(Component.FONT_DEFAULT_SIZE);

    sprites = new LinkedList<Sprite>();
    motionEventParser = new MotionEventParser();
    mGestureDetector = new GestureDetector(context, new FlingGestureListener());
    cameraController = new CameraController();

    Log.d("MIT", "Canvas Component Constructed Size " + Width() + " " + Height());

  }

  @Override
  public View getView() {
    return view;
  }

  public Activity getContext() {
    return context;
  }

  // add a new custom gesture detector, typically by means of a component extension
  public void registerCustomGestureDetector(ExtensionGestureDetector detector) {
    // Log.i("Canvas", "Adding custom detector " + detector.toString());
    extensionGestureDetectors.add(detector);
  }

  public void removeCustomGestureDetector(Object detector) {
    extensionGestureDetectors.remove(detector);
  }


  // Methods related to getting the dimensions of this Canvas

  /**
   * Returns whether the layout associated with this view has been computed.
   * If so, {@link #Width()} and {@link #Height()} will be properly initialized.
   *
   * @return {@code true} if it is safe to call {@link #Width()} and {@link
   * #Height()}, {@code false} otherwise
   */
  public boolean ready() {
    return drawn;
  }

  // Implementation of container methods

  /**
   * Adds a sprite to this Canvas by placing it in {@link #sprites},
   * which it ensures remains sorted.
   *
   * @param sprite the sprite to add
   */
  void addSprite(Sprite sprite) {
    // Add before first element with greater Z value.
    // This ensures not only that items are in increasing Z value
    // but that sprites whose Z values are always equal are
    // ordered by creation time.  While we don't wish to guarantee
    // this behavior going forward, it does provide consistency
    // with how things worked before Z layering was added.
    for (int i = 0; i < sprites.size(); i++) {
      if (sprites.get(i).Z() > sprite.Z()) {
        sprites.add(i, sprite);
        return;
      }
    }

    // Add to end if it has the highest Z value.
    sprites.add(sprite);
  }

  /**
   * Removes a sprite from this Canvas.
   *
   * @param sprite the sprite to remove
   */
  void removeSprite(Sprite sprite) {
    sprites.remove(sprite);
  }

  /**
   * Updates the sorted set of Sprites and the screen when a Sprite's Z
   * property is changed.
   *
   * @param sprite the Sprite whose Z property has changed
   */
  void changeSpriteLayer(Sprite sprite) {
    removeSprite(sprite);
    addSprite(sprite);
    view.invalidate();
  }

  @Override
  public Activity $context() {
    return context;
  }

  @Override
  public Form $form() {
    return container.$form();
  }

  @Override
  public void $add(AndroidViewComponent component) {
    throw new UnsupportedOperationException("Canvas.$add() called");
  }

  @Override
  public void setChildWidth(AndroidViewComponent component, int width) {
    throw new UnsupportedOperationException("Canvas.setChildWidth() called");
  }

  @Override
  public void setChildHeight(AndroidViewComponent component, int height) {
    throw new UnsupportedOperationException("Canvas.setChildHeight() called");
  }

  // Methods executed when a child sprite has changed its location or appearance

  /**
   * Indicates that a sprite has changed, triggering invalidation of the view
   * and a check for collisions.
   *
   * @param sprite the sprite whose location, size, or appearance has changed
   */
  void registerChange(Sprite sprite) {
    view.invalidate();
    findSpriteCollisions(sprite);
  }


  // Methods for detecting collisions

  /**
   * Checks if the given sprite now overlaps with or abuts any other sprite
   * or has ceased to do so.  If there is a sprite that is newly in collision
   * with it, {@link Sprite#CollidedWith(Sprite)} is called for each sprite
   * with the other sprite as an argument.  If two sprites that had been in
   * collision are no longer colliding,
   * {@link Sprite#NoLongerCollidingWith(Sprite)} is called for each sprite
   * with the other as an argument.   Collisions are only recognized between
   * sprites that are both
   * {@link com.google.appinventor.components.runtime.Sprite#Visible()}
   * and
   * {@link com.google.appinventor.components.runtime.Sprite#Enabled()}.
   *
   * @param movedSprite the sprite that has just changed position
   */
  protected void findSpriteCollisions(Sprite movedSprite) {
    for (Sprite sprite : sprites) {
      if (sprite != movedSprite) {
        // Check whether we already raised an event for their collision.
        if (movedSprite.CollidingWith(sprite)) {
          // If they no longer conflict, note that.
          if (!movedSprite.Visible() || !movedSprite.Enabled() ||
              !sprite.Visible() || !sprite.Enabled() ||
              !Sprite.colliding(sprite, movedSprite)) {
            movedSprite.NoLongerCollidingWith(sprite);
            sprite.NoLongerCollidingWith(movedSprite);
          } else {
            // If they still conflict, do nothing.
          }
        } else {
          // Check if they now conflict.
          if (movedSprite.Visible() && movedSprite.Enabled() &&
              sprite.Visible() && sprite.Enabled() &&
              Sprite.colliding(sprite, movedSprite)) {
            // If so, raise two CollidedWith events.
            movedSprite.CollidedWith(sprite);
            sprite.CollidedWith(movedSprite);
          } else {
            // If they still don't conflict, do nothing.
          }
        }
      }
    }
  }


  // Properties

 /**
  * Set the canvas width
  *
  * The width can only be set to >0 or -1 (automatic) or -2 (fill parent)
  * or to a value less then or equal to LENGTH_PERCENT_TAG (which is later
  * converted to pixels.
  *
  * @param width
  */
  @Override
  @SimpleProperty
  // the bitmap routines will crash if the width is set to 0
  public void Width(int width) {
    if ((width > 0) || (width==LENGTH_FILL_PARENT) || (width==LENGTH_PREFERRED) ||
        (width <= LENGTH_PERCENT_TAG)) {
      super.Width(width);
    }
    else {
      container.$form().dispatchErrorOccurredEvent(this, "Width",
          ErrorMessages.ERROR_CANVAS_WIDTH_ERROR);
    }
  }

  /**
   * Set the canvas height
   *
   * The height can only be set to >0 or -1 (automatic) or -2 (fill parent) or
   * to a value less then or equal to LENGTH_PERCENT_TAG (which is later
   * converted to pixels.
   *
   * @param height
   */
  @Override
  @SimpleProperty
  // the bitmap routines will crash if the height is set to 0
   public void Height(int height) {
     if ((height > 0) || (height==LENGTH_FILL_PARENT) || (height==LENGTH_PREFERRED) ||
         (height <= LENGTH_PERCENT_TAG)) {
       super.Height(height);
     }
     else {
       container.$form().dispatchErrorOccurredEvent(this, "Height",
            ErrorMessages.ERROR_CANVAS_HEIGHT_ERROR);
    }
   }


  /**
   * Returns the button's background color as an alpha-red-green-blue
   * integer, i.e., {@code 0xAARRGGBB}.  An alpha of {@code 00}
   * indicates fully transparent and {@code FF} means opaque.
   *
   * @return background color in the format 0xAARRGGBB, which includes
   * alpha, red, green, and blue components
   */
  @SimpleProperty(
      description = "The color of the canvas background.",
      category = PropertyCategory.APPEARANCE)
  public int BackgroundColor() {
    return backgroundColor;
  }

  /**
   * Specifies the Canvas's background color as an alpha-red-green-blue
   * integer, i.e., {@code 0xAARRGGBB}.  An alpha of {@code 00}
   * indicates fully transparent and {@code FF} means opaque.
   * The background color only shows if there is no background image.
   *
   * @param argb background color in the format 0xAARRGGBB, which
   * includes alpha, red, green, and blue components
   */
  @DesignerProperty(editorType = PropertyTypeConstants.PROPERTY_TYPE_COLOR,
      defaultValue = Component.DEFAULT_VALUE_COLOR_WHITE)
  @SimpleProperty
  public void BackgroundColor(int argb) {
    view.setBackgroundColor(argb);
  }

  /**
   * Returns the path of the canvas background image.
   *
   * @return  the path of the canvas background image
   */
  @SimpleProperty(
      description = "The name of a file containing the background image for the canvas",
      category = PropertyCategory.APPEARANCE)
  public String BackgroundImage() {
    return backgroundImagePath;
  }

  /**
   * Specifies the path of the canvas background image.
   *
   * <p/>See {@link MediaUtil#determineMediaSource} for information about what
   * a path can be.
   *
   * @param path  the path of the canvas background image
   */
  @DesignerProperty(editorType = PropertyTypeConstants.PROPERTY_TYPE_ASSET,
      defaultValue = "")
  @SimpleProperty
  public void BackgroundImage(String path) {
    view.setBackgroundImage(path);
  }

  /**
   * Returns the currently specified paint color as an alpha-red-green-blue
   * integer, i.e., {@code 0xAARRGGBB}.  An alpha of {@code 00}
   * indicates fully transparent and {@code FF} means opaque.
   *
   * @return paint color in the format 0xAARRGGBB, which includes alpha,
   * red, green, and blue components
   */
  @SimpleProperty(
      description = "The color in which lines are drawn",
      category = PropertyCategory.APPEARANCE)
  public int PaintColor() {
    return paintColor;
  }

  /**
   * Specifies the paint color as an alpha-red-green-blue integer,
   * i.e., {@code 0xAARRGGBB}.  An alpha of {@code 00} indicates fully
   * transparent and {@code FF} means opaque.
   *
   * @param argb paint color in the format 0xAARRGGBB, which includes
   * alpha, red, green, and blue components
   */
  @DesignerProperty(editorType = PropertyTypeConstants.PROPERTY_TYPE_COLOR,
      defaultValue = Component.DEFAULT_VALUE_COLOR_BLACK)
  @SimpleProperty
  public void PaintColor(int argb) {
    paintColor = argb;
    changePaint(paint, argb);
  }

  private void changePaint(Paint paint, int argb) {
    if (argb == Component.COLOR_DEFAULT) {
      // The default paint color is black.
      PaintUtil.changePaint(paint, Component.COLOR_BLACK);
    } else if (argb == Component.COLOR_NONE) {
      PaintUtil.changePaintTransparent(paint);
    } else {
      PaintUtil.changePaint(paint, argb);
    }
  }

  @SimpleProperty(
      description = "The font size of text drawn on the canvas.",
      category = PropertyCategory.APPEARANCE)
  public float FontSize() {
    float scale = $form().deviceDensity();
    return paint.getTextSize() / scale;
  }

  @DesignerProperty(editorType = PropertyTypeConstants.PROPERTY_TYPE_NON_NEGATIVE_FLOAT,
      defaultValue = Component.FONT_DEFAULT_SIZE + "")
  @SimpleProperty
  public void FontSize(float size) {
    float scale = $form().deviceDensity();
    paint.setTextSize(size * scale);
  }

  /**
   * Returns the currently specified stroke width
   * @return width
   */
  @SimpleProperty(
      description = "The width of lines drawn on the canvas.",
      category = PropertyCategory.APPEARANCE)
  public float LineWidth() {
    return paint.getStrokeWidth() / $form().deviceDensity();
  }

  /**
   * Specifies the stroke width
   *
   * @param width
   */
  @DesignerProperty(editorType = PropertyTypeConstants.PROPERTY_TYPE_NON_NEGATIVE_FLOAT,
      defaultValue = DEFAULT_LINE_WIDTH + "")
  @SimpleProperty
  public void LineWidth(float width) {
    paint.setStrokeWidth(width * $form().deviceDensity());
  }

  /**
   * Returns the alignment of the canvas's text: center, normal
   * (starting at the specified point in drawText()), or opposite
   * (ending at the specified point in drawText()).
   *
   * @return  one of {@link Component#ALIGNMENT_NORMAL},
   *          {@link Component#ALIGNMENT_CENTER} or
   *          {@link Component#ALIGNMENT_OPPOSITE}
   */
  @SimpleProperty(description = "Determines the alignment of the " +
      "text drawn by DrawText() or DrawAngle() with respect to the " +
      "point specified by that command.",
      category = PropertyCategory.APPEARANCE,
      userVisible = true)
  public int TextAlignment() {
    return textAlignment;
  }

  /**
   * Specifies the alignment of the canvas's text: center, normal
   * (starting at the specified point in DrawText() or DrawAngle()),
   * or opposite (ending at the specified point in DrawText() or
   * DrawAngle()).
   *
   * @param alignment  one of {@link Component#ALIGNMENT_NORMAL},
   *                   {@link Component#ALIGNMENT_CENTER} or
   *                   {@link Component#ALIGNMENT_OPPOSITE}
   */
  @DesignerProperty(editorType = PropertyTypeConstants.PROPERTY_TYPE_TEXTALIGNMENT,
                    defaultValue = DEFAULT_TEXTALIGNMENT + "")
  @SimpleProperty(userVisible = true)
  public void TextAlignment(int alignment) {
    this.textAlignment = alignment;
    switch (alignment) {
      case Component.ALIGNMENT_NORMAL:
        paint.setTextAlign(Paint.Align.LEFT);
        break;
      case Component.ALIGNMENT_CENTER:
        paint.setTextAlign(Paint.Align.CENTER);
        break;
      case Component.ALIGNMENT_OPPOSITE:
        paint.setTextAlign(Paint.Align.RIGHT);
        break;
    }
  }

  /**
   *
   * @return true if Camera Live Feed is Enabled
   */
  @SimpleProperty(
      description = "Specifies whether to use the Canvas for Camera Preview"
  )
  public boolean CameraFeed() {
    return cameraFeed;
  }
  /**
   * Specify whether to enable or disable Live Feed from Camera
   * @param camFeed true to enable
   */
  @DesignerProperty(editorType = PropertyTypeConstants.PROPERTY_TYPE_BOOLEAN,
      defaultValue = "False")
  @SimpleProperty
  public void CameraFeed(boolean camFeed) {
    cameraFeed = camFeed;
  }

  /**
   * Returns true if the front-facing camera is to be used (when available)
   *
   * @return {@code true} indicates front-facing is to be used, {@code false} will open default
   */
  @SimpleProperty(description = "Specifies whether the front-facing camera should be used (when available). "
      + "If the device does not have a front-facing camera, this option will be ignored "
      + "and the camera will open normally.")
  public boolean CameraUseFront(){
    return cameraUseFront;
  }

  /**
   * Specifies whether the front-facing camera should be used (when available)
   *
   * @param front
   *          {@code true} for front-facing camera, {@code false} for default
   */
  @DesignerProperty(editorType = PropertyTypeConstants.PROPERTY_TYPE_BOOLEAN, defaultValue = "False")
  @SimpleProperty
  public void CameraUseFront(boolean useFront) {
    cameraUseFront = useFront;
  }

  @SimpleProperty(description = "This represents the angle the camera image needs to be rotated clockwise "
      + "to show correctly on the display in its natural orientation. This should be almost always Automatic. "
      + "This property is device specific. This should only be used to correct wrong orientation in older devices."
      + "The valid values should be 0 = Automatic,  1 = Top (0), 2 = Right(90), 3 = Bottom(180), 4 = Left(270)")
  public int CameraOrientation() {
    return cameraOrientation;
  }

  @DesignerProperty(editorType = PropertyTypeConstants.PROPERTY_TYPE_CAMERA_ORIENTATION,
      defaultValue = Component.CAMERA_ORIENTATION_AUTOMATIC + "")
  @SimpleProperty
  public void CameraOrientation(int orientation){
    if(orientation >= 0 && orientation < 5  ){
      cameraOrientation = orientation;
    }
    else {
      $form().dispatchErrorOccurredEvent(this, "Camera Orientation",
          ErrorMessages.ERROR_CANVAS_CAMERA_ORIENTATION_ERROR, cameraOrientation);
    }
  }

  // Methods supporting event handling

  /**
   * When the user touches the canvas and then immediately lifts finger: provides
   * the (x,y) position of the touch, relative to the upper left of the canvas.  TouchedAnySprite
   * is true if the same touch also touched a sprite, and false otherwise.
   *
   * @param x  x-coordinate of the point that was touched
   * @param y  y-coordinate of the point that was touched
   * @param touchedAnySprite {@code true} if a sprite was touched, {@code false}
   *        otherwise
   */
  @SimpleEvent
  public void Touched(float x, float y, boolean touchedAnySprite) {
    EventDispatcher.dispatchEvent(this, "Touched", x, y, touchedAnySprite);
  }

  /**
   * When the user begins touching the canvas (places finger on canvas and
   * leaves it there): provides the (x,y) position of the touch, relative
   * to the upper left of the canvas
   *
   * @param x  x-coordinate of the point that was touched
   * @param y  y-coordinate of the point that was touched
   */
  @SimpleEvent
  public void TouchDown(float x, float y) {
    EventDispatcher.dispatchEvent(this, "TouchDown", x, y);
  }

  /**
   * When the user stops touching the canvas (lifts finger after a
   * TouchDown event): provides the (x,y) position of the touch, relative
   * to the upper left of the canvas
   *
   * @param x  x-coordinate of the point that was touched
   * @param y  y-coordinate of the point that was touched
   */
  @SimpleEvent
  public void TouchUp(float x, float y) {
    EventDispatcher.dispatchEvent(this, "TouchUp", x, y);
  }

  /**
   * When a fling gesture (quick swipe) is made on the canvas: provides
   * the (x,y) position of the start of the fling, relative to the upper
   * left of the canvas. Also provides the speed (pixels per millisecond) and heading
   * (0-360 degrees) of the fling, as well as the x velocity and y velocity
   * components of the fling's vector. The value "flungSprite" is true if a sprite
   * was located near the the starting point of the fling gesture.
   *
   * @param x  x-coordinate of touched point
   * @param y  y-coordinate of touched point
   * @param speed  the speed of the fling sqrt(xspeed^2 + yspeed^2)
   * @param heading  the heading of the fling
   * @param xvel  the speed in x-direction of the fling
   * @param yvel  the speed in y-direction of the fling
   * @param flungSprite  {@code true} if a sprite was flung,
   *        {@code false} otherwise
   *
   */
  @SimpleEvent
  public void Flung(float x, float y, float speed, float heading,float xvel, float yvel,
      boolean flungSprite) {
    EventDispatcher.dispatchEvent(this, "Flung", x, y, speed, heading, xvel, yvel, flungSprite);
  }

/**
   * When the user does a drag from one point (prevX, prevY) to
   * another (x, y).  The pair (startX, startY) indicates where the
   * user first touched the screen, and "draggedAnySprite" indicates whether a
   * sprite is being dragged.
   *
   * @param startX the starting x-coordinate
   * @param startY the starting y-coordinate
   * @param prevX the previous x-coordinate (possibly equal to startX)
   * @param prevY the previous y-coordinate (possibly equal to startY)
   * @param currentX the current x-coordinate
   * @param currentY the current y-coordinate
   * @param draggedAnySprite {@code true} if
   *        {@link Sprite#Dragged(float, float, float, float, float, float)}
   *        was called for one or more sprites for this segment, {@code false}
   *        otherwise
   */
  @SimpleEvent
  public void Dragged(float startX, float startY, float prevX, float prevY,
                      float currentX, float currentY, boolean draggedAnySprite) {
    EventDispatcher.dispatchEvent(this, "Dragged", startX, startY,
                                  prevX, prevY, currentX, currentY, draggedAnySprite);
  }

  // Functions

  /**
   * Clears the canvas, without removing the background image, if one
   * was provided.
   */
  @SimpleFunction(description = "Clears anything drawn on this Canvas but " +
      "not any background color or image.")
  public void Clear() {
    view.clearDrawingLayer();
  }

  /**
   * Draws a point at the given coordinates on the canvas.
   *
   * @param x  x coordinate
   * @param y  y coordinate
   */
  @SimpleFunction
  public void DrawPoint(int x, int y) {
    float correctedX = x * $form().deviceDensity();
    float correctedY = y * $form().deviceDensity();
    view.canvas.drawPoint(correctedX, correctedY, paint);
    view.invalidate();
  }

 /**
   * Draws a circle (filled in) with the given radius centered at the given coordinates on the canvas
   *
   * @param centerX  x-coordinate of the center of the circle
   * @param centerY  y-coordinate of the center of the circle
   * @param radius  radius of the circle
   * @param fill  true for filled circle; false for circle outline
   */
  @SimpleFunction
  public void DrawCircle(int centerX, int centerY, float radius, boolean fill) {
    float correctedX = centerX * $form().deviceDensity();
    float correctedY = centerY * $form().deviceDensity();
    float correctedR = radius * $form().deviceDensity();
    Paint p = new Paint(paint);
    p.setStyle(fill ? Paint.Style.FILL : Paint.Style.STROKE);
    view.canvas.drawCircle(correctedX, correctedY, correctedR, p);
    view.invalidate();
  }

  /**
   * Draws a line between the given coordinates on the canvas.
   *
   * @param x1  x coordinate of first point
   * @param y1  y coordinate of first point
   * @param x2  x coordinate of second point
   * @param y2  y coordinate of second point
   */
  @SimpleFunction
  public void DrawLine(int x1, int y1, int x2, int y2) {
    float correctedX1 = x1 * $form().deviceDensity();
    float correctedY1 = y1 * $form().deviceDensity();
    float correctedX2 = x2 * $form().deviceDensity();
    float correctedY2 = y2 * $form().deviceDensity();
    view.canvas.drawLine(correctedX1, correctedY1, correctedX2, correctedY2, paint);
    view.invalidate();
  }

  /**
   * Draws the specified text relative to the specified coordinates
   * using the values of the {@link #FontSize(float)} and
   * {@link #TextAlignment(int)} properties.
   *
   * @param text the text to draw
   * @param x the x-coordinate of the origin
   * @param y the y-coordinate of the origin
   */
  @SimpleFunction(description = "Draws the specified text relative to the specified coordinates "
      + "using the values of the FontSize and TextAlignment properties.")
  public void DrawText(String text, int x, int y) {
    float fontScalingFactor = $form().deviceDensity();
    float correctedX = x * fontScalingFactor;
    float correctedY = y * fontScalingFactor;
    view.canvas.drawText(text, correctedX, correctedY, paint);
    view.invalidate();
  }

  /**
   * Draws the specified text starting at the specified coordinates
   * at the specified angle using the values of the {@link #FontSize(float)} and
   * {@link #TextAlignment(int)} properties.
   *
   * @param text the text to draw
   * @param x the x-coordinate of the origin
   * @param y the y-coordinate of the origin
   * @param angle the angle (in degrees) at which to draw the text
   */
  @SimpleFunction(description = "Draws the specified text starting at the specified coordinates "
      + "at the specified angle using the values of the FontSize and TextAlignment properties.")
  public void DrawTextAtAngle(String text, int x, int y, float angle) {
    int correctedX = (int) (x * $form().deviceDensity());
    int correctedY = (int) (y * $form().deviceDensity());
    view.drawTextAtAngle(text, correctedX, correctedY, angle);
  }

  /**
   * <p>Gets the color of the given pixel, ignoring sprites.</p>
   *
   * @param x the x-coordinate
   * @param y the y-coordinate
   * @return the color at that location as an alpha-red-blue-green integer,
   *         or {@link Component#COLOR_NONE} if that point is not on this Canvas
   */
  @SimpleFunction(description = "Gets the color of the specified point. "
      + "This includes the background and any drawn points, lines, or "
      + "circles but not sprites.")
  public int GetBackgroundPixelColor(int x, int y) {
    int correctedX = (int) (x * $form().deviceDensity());
    int correctedY = (int) (y * $form().deviceDensity());
    return view.getBackgroundPixelColor(correctedX, correctedY);
  }

  /**
   * <p>Sets the color of the given pixel.  This has no effect if the
   * coordinates are out of bounds.</p>
   *
   * @param x the x-coordinate
   * @param y the y-coordinate
   * @param color the color as an alpha-red-blue-green integer
   */
  @SimpleFunction(description = "Sets the color of the specified point. "
      + "This differs from DrawPoint by having an argument for color.")
  public void SetBackgroundPixelColor(int x, int y, int color) {
    Paint pixelPaint = new Paint();
    PaintUtil.changePaint(pixelPaint, color);
    int correctedX = (int) (x * $form().deviceDensity());
    int correctedY = (int) (y * $form().deviceDensity());
    view.canvas.drawPoint(correctedX, correctedY, pixelPaint);
    view.invalidate();
  }

  /**
   * <p>Gets the color of the given pixel, including sprites.</p>
   *
   * @param x the x-coordinate
   * @param y the y-coordinate
   * @return the color at that location as an alpha-red-blue-green integer,
   *         or {@link Component#COLOR_NONE} if that point is not on this Canvas
   */
  @SimpleFunction(description = "Gets the color of the specified point.")
  public int GetPixelColor(int x, int y) {
    int correctedX = (int) (x * $form().deviceDensity());
    int correctedY = (int) (y * $form().deviceDensity());
    return view.getPixelColor(correctedX, correctedY);
  }

  /**
   * Saves a picture of this Canvas to the device's external storage.
   * If an error occurs, the Screen's ErrorOccurred event will be called.
   *
   * @return the full path name of the saved file, or the empty string if the
   *         save failed
   */
    @SimpleFunction(description = "Saves a picture of this Canvas to the " +
       "device's external storage. If an error occurs, the Screen's ErrorOccurred " +
       "event will be called.")
  public String Save() {
    try {
      File file = FileUtil.getPictureFile("png");
      return saveFile(file, Bitmap.CompressFormat.PNG, "Save");
    } catch (IOException e) {
      container.$form().dispatchErrorOccurredEvent(this, "Save",
          ErrorMessages.ERROR_MEDIA_FILE_ERROR, e.getMessage());
    } catch (FileUtil.FileException e) {
      container.$form().dispatchErrorOccurredEvent(this, "Save",
          e.getErrorMessageNumber());
    }
    return "";
  }

  /**
   * Saves a picture of this Canvas to the device's external storage in the file
   * named fileName. fileName must end with one of ".jpg", ".jpeg", or ".png"
   * (which determines the file type: JPEG, or PNG).
   *
   * @return the full path name of the saved file, or the empty string if the
   *         save failed
   */
  @SimpleFunction(description =  "Saves a picture of this Canvas to the device's " +
   "external storage in the file " +
   "named fileName. fileName must end with one of .jpg, .jpeg, or .png, " +
   "which determines the file type.")
  public String SaveAs(String fileName) {
    // Figure out desired file format
    Bitmap.CompressFormat format;
    if (fileName.endsWith(".jpg") || fileName.endsWith(".jpeg")) {
      format = Bitmap.CompressFormat.JPEG;
    } else if (fileName.endsWith(".png")) {
      format = Bitmap.CompressFormat.PNG;
    } else if (!fileName.contains(".")) {  // make PNG the default to match Save behavior
      fileName = fileName + ".png";
      format = Bitmap.CompressFormat.PNG;
    } else {
      container.$form().dispatchErrorOccurredEvent(this, "SaveAs",
          ErrorMessages.ERROR_MEDIA_IMAGE_FILE_FORMAT);
      return "";
    }
    try {
      File file = FileUtil.getExternalFile(fileName);
      return saveFile(file, format, "SaveAs");
    } catch (IOException e) {
      container.$form().dispatchErrorOccurredEvent(this, "SaveAs",
          ErrorMessages.ERROR_MEDIA_FILE_ERROR, e.getMessage());
    } catch (FileUtil.FileException e) {
      container.$form().dispatchErrorOccurredEvent(this, "SaveAs",
          e.getErrorMessageNumber());
    }
    return "";
  }

  // Helper method for Save and SaveAs
  private String saveFile(File file, Bitmap.CompressFormat format, String method) {
    try {
      boolean success = false;
      FileOutputStream fos = new FileOutputStream(file);
      // Don't cache, in order to save memory.  It seems unlikely to be used again soon.
      Bitmap bitmap = (view.completeCache == null ? view.buildCache() : view.completeCache);
      try {
        success = bitmap.compress(format,
            100,  // quality: ignored for png
            fos);
      } finally {
        fos.close();
      }
      if (success) {
        return file.getAbsolutePath();
      } else {
        container.$form().dispatchErrorOccurredEvent(this, method,
            ErrorMessages.ERROR_CANVAS_BITMAP_ERROR);
      }
    } catch (FileNotFoundException e) {
      container.$form().dispatchErrorOccurredEvent(this, method,
          ErrorMessages.ERROR_MEDIA_CANNOT_OPEN, file.getAbsolutePath());
    } catch (IOException e) {
      container.$form().dispatchErrorOccurredEvent(this, method,
          ErrorMessages.ERROR_MEDIA_FILE_ERROR, e.getMessage());
    }
    return "";
  }

  class FlingGestureListener extends GestureDetector.SimpleOnGestureListener {
    @Override
    public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX,
        float velocityY) {
      float x = Math.max(0, (int)(e1.getX() / $form().deviceDensity())); // set to zero if negative
      float y = Math.max(0, (int)(e1.getY() / $form().deviceDensity())); // set to zero if negative

      // Normalize the velocity: Change from pixels/sec to pixels/ms
      float vx = velocityX / FLING_INTERVAL;
      float vy = velocityY / FLING_INTERVAL;

      float speed = (float) Math.sqrt(vx * vx + vy * vy);
      float heading = (float) -Math.toDegrees(Math.atan2(vy, vx));

      int width = Width();
      int height = Height();

      // Also make sure that by adding or subtracting a half finger that
      // we don't go out of bounds.
      BoundingBox rect = new BoundingBox(
          Math.max(0, (int) x - MotionEventParser.HALF_FINGER_HEIGHT),
          Math.max(0, (int) y - MotionEventParser.HALF_FINGER_WIDTH),
          Math.min(width - 1, (int) x + MotionEventParser.HALF_FINGER_WIDTH),
          Math.min(height - 1, (int) y + MotionEventParser.HALF_FINGER_HEIGHT));

      boolean spriteHandledFling = false;

      for (Sprite sprite : sprites) {
        if (sprite.Enabled() && sprite.Visible() &&
            sprite.intersectsWith(rect)) {
          sprite.Flung(x, y, speed, heading, vx, vy);
          spriteHandledFling = true;
        }
      }
      Flung(x, y, speed, heading, vx, vy, spriteHandledFling);
      return true;
    }
  }

  @Override
  public void onStop() {
    if(cameraController.previewing) {
      cameraController.unsetCameraFeed(true);
      cameraController.previewing = false;
    }
  }
}

