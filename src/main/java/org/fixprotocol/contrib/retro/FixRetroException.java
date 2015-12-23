//
// FixRetroException.java - FIX Retroerter Exception
//
// AK, 04 Apr 2011, initial version
//

package org.fixprotocol.contrib.retro;

//...simports:0:
import java.lang.Throwable;
import java.lang.Exception;
//...e

public class FixRetroException extends Exception
  {
  public FixRetroException(String message)
    { super(message); }
  public FixRetroException(String message, Throwable cause)
    { super(message, cause); }
  }
