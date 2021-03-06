/*******************************************************************************
 * Copyright (c) 2009  Eucalyptus Systems, Inc.
 * 
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, only version 3 of the License.
 * 
 * 
 *  This file is distributed in the hope that it will be useful, but WITHOUT
 *  ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 *  FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 *  for more details.
 * 
 *  You should have received a copy of the GNU General Public License along
 *  with this program.  If not, see <http://www.gnu.org/licenses/>.
 * 
 *  Please contact Eucalyptus Systems, Inc., 130 Castilian
 *  Dr., Goleta, CA 93101 USA or visit <http://www.eucalyptus.com/licenses/>
 *  if you need additional information or have any questions.
 * 
 *  This file may incorporate work covered under the following copyright and
 *  permission notice:
 * 
 *    Software License Agreement (BSD License)
 * 
 *    Copyright (c) 2008, Regents of the University of California
 *    All rights reserved.
 * 
 *    Redistribution and use of this software in source and binary forms, with
 *    or without modification, are permitted provided that the following
 *    conditions are met:
 * 
 *      Redistributions of source code must retain the above copyright notice,
 *      this list of conditions and the following disclaimer.
 * 
 *      Redistributions in binary form must reproduce the above copyright
 *      notice, this list of conditions and the following disclaimer in the
 *      documentation and/or other materials provided with the distribution.
 * 
 *    THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS
 *    IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED
 *    TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A
 *    PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER
 *    OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 *    EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
 *    PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR
 *    PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
 *    LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 *    NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 *    SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE. USERS OF
 *    THIS SOFTWARE ACKNOWLEDGE THE POSSIBLE PRESENCE OF OTHER OPEN SOURCE
 *    LICENSED MATERIAL, COPYRIGHTED MATERIAL OR PATENTED MATERIAL IN THIS
 *    SOFTWARE, AND IF ANY SUCH MATERIAL IS DISCOVERED THE PARTY DISCOVERING
 *    IT MAY INFORM DR. RICH WOLSKI AT THE UNIVERSITY OF CALIFORNIA, SANTA
 *    BARBARA WHO WILL THEN ASCERTAIN THE MOST APPROPRIATE REMEDY, WHICH IN
 *    THE REGENTS' DISCRETION MAY INCLUDE, WITHOUT LIMITATION, REPLACEMENT
 *    OF THE CODE SO IDENTIFIED, LICENSING OF THE CODE SO IDENTIFIED, OR
 *    WITHDRAWAL OF THE CODE CAPABILITY TO THE EXTENT NEEDED TO COMPLY WITH
 *    ANY SUCH LICENSES OR RIGHTS.
 *******************************************************************************
 * @author chris grzegorczyk <grze@eucalyptus.com>
 */

package com.eucalyptus.configurable;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import com.eucalyptus.component.ComponentId;
import com.eucalyptus.configurable.PropertyDirectory.NoopEventListener;
import com.eucalyptus.empyrean.Empyrean;
import com.google.common.base.Function;

/**
 * Low-effort annotations for exposing values as being @Configurable.<br/>
 * 
 * To use it simply mark a {@link Class}, {@link Field}, or {@link Method}:
 * 
 * {@code @Configurable("Human readable name")}
 * 
 * The purpose of this annotation is to be the shortest path to exposing
 * something for modification at runtime. To that end, it only contains (and will only ever contain)
 * a single <i>optional</i> argument ({@link Configurable#value()} which can be provided to give a
 * human readable name for the property.
 * 
 * @note {@link ConfigurableIdentifier} is replaced by {@link Configurables.Identifier}
 * @note {@link ConfigurableField#readonly()} is replaced by checking whether
 *       the field is {@link Modifier#FINAL} or is enforced by a {@link Configurables.Constraint}.
 * @note {@link ConfigurableFieldType} is replaced by {@link Configurables.Restricted}.
 * @note {@link ConfigurableClass#root()} is replaced by {@link Configurables.Namespace}
 * @note {@link ConfigurableClass#singleton()} is replaced by {@link Configurables.Singleton}
 * @note {@link ConfigurableClass#deferred()} is no longer applicable.
 * @see {@link Configurable}
 * @see ConfigurableIdentifier
 * @see ChangeListener
 * @see Constraint
 */
public class Configurables {
  
  @Target( { ElementType.FIELD } )
  @Retention( RetentionPolicy.RUNTIME )
  public @interface DefaultValue {
    String value( ) default "";
  }
  
  @Target( { ElementType.FIELD, ElementType.METHOD } )
  @Retention( RetentionPolicy.RUNTIME )
  public @interface Identifier {}
  
  @Target( { ElementType.TYPE, ElementType.FIELD, ElementType.METHOD } )
  @Retention( RetentionPolicy.RUNTIME )
  public @interface Description {
    String value( );
  }
  
  @Target( { ElementType.TYPE } )
  @Retention( RetentionPolicy.RUNTIME )
  public @interface Namespace {
    String value( );
  }
  
  @Target( { ElementType.TYPE } )
  @Retention( RetentionPolicy.RUNTIME )
  public @interface Singleton {
    Class<? extends ComponentId> value( ) default Empyrean.class;
  }
  
  @Target( { ElementType.TYPE } )
  @Retention( RetentionPolicy.RUNTIME )
  public @interface Restricted {
    boolean internal( ) default false;
    
    boolean hidden( ) default false;
  }
  
  @Target( { ElementType.TYPE } )
  @Retention( RetentionPolicy.RUNTIME )
  public @interface Constraint {
    Class<? extends com.google.common.collect.Constraint<?>> value( );
  }
  
  @Target( { ElementType.TYPE } )
  @Retention( RetentionPolicy.RUNTIME )
  public @interface ChangeListener {
    Class<? extends PropertyChangeListener> value( ) default NoopEventListener.class;
  }
  
  @Target( { ElementType.FIELD, ElementType.METHOD } )
  @Retention( RetentionPolicy.RUNTIME )
  public @interface TypeParser {
    Class<?> type( );
    Class<? extends Function<String, ?>> parser( );
  }
  
}
