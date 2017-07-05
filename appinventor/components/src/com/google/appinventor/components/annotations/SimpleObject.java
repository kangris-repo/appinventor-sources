// -*- mode: java; c-basic-offset: 2; -*-
// Copyright 2009-2011 Google, All Rights reserved
// Copyright 2011-2012 MIT, All rights reserved
// Released under the Apache License, Version 2.0
// http://www.apache.org/licenses/LICENSE-2.0

package com.google.appinventor.components.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation to mark Simple objects.
 *
 * <p>Note that the Simple compiler will only recognize Java classes marked
 * with this annotation. All other classes will be ignored.
 *
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface SimpleObject {

    /**
     * True if this component works on Screen context
     */
    boolean screenCompatible() default true;

    /**
     * True if this component works on Task context
     */
    boolean taskCompatible() default true;

    /**
     * True if this component is an external component.
     * Setting to True is mandatory for packing Extensions (aix)
     */
    boolean external() default false;

}
