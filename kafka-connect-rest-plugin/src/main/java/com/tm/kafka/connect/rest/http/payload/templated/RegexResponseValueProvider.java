package com.tm.kafka.connect.rest.http.payload.templated;


import com.tm.kafka.connect.rest.RestSourceTask;
import com.tm.kafka.connect.rest.http.Request;
import com.tm.kafka.connect.rest.http.Response;
import org.apache.kafka.common.Configurable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Lookup values used to populate dynamic payloads.
 * These values will be substituted into the payload template.
 *
 * This implementation looks up values in the System properties and then in environment variables.
 */
public class RegexResponseValueProvider extends EnvironmentValueProvider implements Configurable {

  private static Logger log = LoggerFactory.getLogger(RegexResponseValueProvider.class);

  public static final String MULTI_VALUE_SEPARATOR = ",";

  private Map<String, Pattern> patterns;
  private Map<String, String> values;


  @Override
  public void configure(Map<String, ?> props) {
    final RegexResponseValueProviderConfig config = new RegexResponseValueProviderConfig(props);

    patterns = new HashMap<>(config.getResponseVariableNames().size());
    config.getResponseVariableRegexs().forEach((k,v) -> patterns.put(k, Pattern.compile(v)));

    values = new HashMap<>(patterns.size());
  }

  /**
   * Extract values from the response using the regexs
   *
   * @param request The last request made.
   * @param response The last response received.
   */
  @Override
  void extractValues(Request request, Response response) {
    String resp = response.getPayload();
    patterns.forEach((key, pat) -> values.put(key, extractValue(key, resp, pat)));
  }

  /**
   * Returns the value of the given key, which will be looked up first in the System properties and then
   * in environment variables.
   *
   * @return The defined value or null if te key is undefined.
   */
  String getValue(String key) {
    String value = values.get(key);
    return value != null ? value : super.getValue(key);
  }

  private String extractValue(String key, String resp, Pattern pattern) {
    Matcher matcher = pattern.matcher(resp);
    StringBuilder values = new StringBuilder();
    // Iterate over each place where the regex matches
    while(matcher.find()) {
      if(values.length() > 0) {
        values.append(MULTI_VALUE_SEPARATOR);
      }
      if(matcher.groupCount() == 0) {
        // if the regex has no groups then the whole thing is the value
        values.append(matcher.group());
      } else {
        // If the regex has one or more groups then append them in order
        for(int g = 1; g <= matcher.groupCount(); g++) {
          if(values.length() > 0) {
            values.append(MULTI_VALUE_SEPARATOR);
          }
          values.append(matcher.group(g));
        }
      }
    }

    String value = (values.length() != 0) ? values.toString() : null;

    log.info("Variable {} was assigned the value {}", key, value);

    return value;
  }
}
