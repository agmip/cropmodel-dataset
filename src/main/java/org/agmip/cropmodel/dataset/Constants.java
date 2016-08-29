
package org.agmip.cropmodel.dataset;

import java.util.regex.Pattern;
import org.joda.time.format.DateTimeFormatter;
import org.joda.time.format.ISODateTimeFormat;

/**
 *
 * @author frostbytten
 */
public class Constants {
  public static final Pattern BATCH_REGEX = Pattern.compile("(\\w+_\\d+)_b\\w+__\\d+");
  public static final Pattern SEASONAL_REGEX = Pattern.compile("(\\w+_\\d+)__\\d+");
  public static final DateTimeFormatter DATE_FORMAT = ISODateTimeFormat.date();
  public static final DateTimeFormatter SHORT_DATE_FORMAT = ISODateTimeFormat.basicDate();
  
  private Constants(){}
  
}
