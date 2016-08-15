// -*- mode: java; c-basic-offset: 2; -*-
// Copyright 2009-2011 Google, All Rights reserved
// Copyright 2011-2012 MIT, All rights reserved
// Released under the Apache License, Version 2.0
// http://www.apache.org/licenses/LICENSE-2.0

package com.google.appinventor.components.runtime;

import android.app.Activity;
import android.content.Context;

/**
 * Components that can contain other components need to implement this
 * interface.
 *
 */
public interface ComponentContainer {
  /**
   * Returns the application context .
   *
   * @return  activity context
   */
  Context $context();

  /**
   * Returns the form that ultimately contains this container.
   *
   * @return  form
   */
  Form $form();

  /**
   * Returns the task that ultimately contains this container.
   *
   * @return  task
   */
  Task $task();

  /**
   * @return  Returns true only if the container is a context.
   */
  boolean isContext();

  /**
   * @return  Returns true only if the container is a form.
   */
  boolean isForm();

  /**
   * @return  Returns true only if the container is a task.
   */
  boolean isTask();

  /**
   * @return  Returns true only if the container is a form or  inside a form.
   */
  boolean inForm();

  /**
   * @return  Returns true only if the container is a task inside a task.
   */
  boolean inTask();

  /**
   * Adds a component to a container.
   *
   * <p/>After this method is finished executing, the given component's view
   * must have LayoutParams, even if the component cannot be added to the
   * container until later.
   *
   * @param component  component associated with view
   */
  void $add(AndroidViewComponent component);

  void setChildWidth(AndroidViewComponent component, int width);

  void setChildHeight(AndroidViewComponent component, int height);

  int Width();

  int Height();


  void dispatchErrorOccurredEvent(final Component component, final String functionName,
           final int errorNumber, final Object... messageArgs);

}
