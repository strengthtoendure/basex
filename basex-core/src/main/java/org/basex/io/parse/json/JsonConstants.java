package org.basex.io.parse.json;

import static org.basex.util.Token.*;

/**
 * This class contains constants used for parsing and serializing JSON.
 *
 * @author BaseX Team 2005-13, BSD License
 * @author Christian Gruen
 */
public interface JsonConstants {
  /** Token: json. */
  byte[] JSON = token("json");
  /** Token: type. */
  byte[] TYPE = token("type");
  /** Token: item. */
  byte[] ITEM = token("item");

  /** Token: string. */
  byte[] STRING = token("string");
  /** Token: number. */
  byte[] NUMBER = token("number");
  /** Token: boolean. */
  byte[] BOOLEAN = token("boolean");
  /** Token: array. */
  byte[] ARRAY = token("array");
  /** Token: object. */
  byte[] OBJECT = token("object");

  /** Token: pair. */
  byte[] PAIR = token("pair");
  /** Token: name. */
  byte[] NAME = token("name");
  /** Token: array value. */
  byte[] VALUE = token("_");

  /** Supported data types. */
  byte[][] TYPES = { OBJECT, ARRAY, STRING, NUMBER, BOOLEAN, NULL };
  /** Plural. */
  byte[] S = { 's' };
  /** Global data type attributes. */
  byte[][] ATTRS = {
    concat(OBJECT, S), concat(ARRAY, S), concat(STRING, S),
    concat(NUMBER, S), concat(BOOLEAN, S), concat(NULL, S) };
}
