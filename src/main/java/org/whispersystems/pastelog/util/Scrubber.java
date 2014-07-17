package org.whispersystems.pastelog.util;

import android.util.Log;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Scrub data for possibly sensitive information
 */
public class Scrubber {
  private static final String TAG = Scrubber.class.getSimpleName();
  private static final Pattern E164_PATTERN = Pattern.compile("\\+\\d{10,15}");

  private static final Pattern[] DEFAULTS = new Pattern[] {
      E164_PATTERN
  };

  private final Pattern[] patterns;
  public Scrubber(Pattern... patterns) {
    this.patterns = patterns;
  }

  public Scrubber() {
    this(DEFAULTS);
  }

  public String scrub(final String in) {
    Log.d(TAG, "scrubbing input");
    String out = in;
    for (Pattern pattern : patterns) {
      Matcher matcher = pattern.matcher(out);
      while (matcher.find()) {

        StringBuilder builder = new StringBuilder(out.substring(0, matcher.start()));
        final String censored = matcher.group().substring(0,1)                                      +
                                new String(new char[matcher.group().length()-3]).replace("\0", "*") +
                                matcher.group().substring(matcher.group().length()-2);
        builder.append(censored);
        builder.append(out.substring(matcher.end()));
        Log.i(TAG, "replacing a match on /" + pattern.toString() + "/ => " + censored);
        out = builder.toString();
      }
    }
    return out;
  }
}
