package com.foursquare.fongo.impl;

import com.mongodb.BasicDBList;
import com.mongodb.BasicDBObject;
import com.mongodb.DBObject;
import com.mongodb.LazyDBObject;
import com.mongodb.FongoDBCollection;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.bson.LazyDBList;

public class Util {

  private Util() {
  }

  public static <T> BasicDBList list(T... ts) {
    return wrap(Arrays.asList(ts));
  }

  /**
   * Can extract field from an object.
   * Handle "field1.field2" in { field1 : {field2 : value2 } }
   *
   * @param object
   * @param field
   * @return null if not found.
   */
  public static <T> T extractField(DBObject object, String field) {
    if (object == null) {
      return null; // NPE ?
    }
    T value;
    int indexDot = field.indexOf('.');
    if (indexDot > 0) {
      String subField = field.substring(indexDot + 1);
      value = extractField((DBObject) object.get(field.substring(0, indexDot)), subField);
    } else {
      value = (T) object.get(field);
    }
    return value;
  }

  /**
   * Say "true" if field is in this object.
   * Handle "field1.field2" in { field1 : {field2 : value2 } }
   *
   * @param object
   * @param field
   * @return true if object contains field.
   */
  public static boolean containsField(DBObject object, String field) {
    if (object == null) {
      return false;
    }
    boolean result;
    int indexDot = field.indexOf('.');
    if (indexDot > 0) {
      String subField = field.substring(indexDot + 1);
      String actualField = field.substring(0, indexDot);
      result = false;
      if (object.containsField(actualField)) {
        Object value = object.get(actualField);
        if (value instanceof DBObject) {
          result = containsField((DBObject) value, subField);
        }
      }
    } else {
      result = object.containsField(field);
    }
    return result;
  }

  /**
   * Put a value in a {@link DBObject} with hierarchy.
   *
   * @param dbObject object to modify
   * @param path     field with dot '.' to match hierarchy.
   * @param value    new value to set.
   */
  public static void putValue(DBObject dbObject, String path, Object value) {
    if (dbObject == null) {
      return; // NPE ?
    }
    int indexDot = path.indexOf('.');
    if (indexDot > 0) {
      String field = path.substring(0, indexDot);
      String nextPath = path.substring(indexDot + 1);

      // Create DBObject if necessary
      if (!dbObject.containsField(field)) {
        dbObject.put(field, new BasicDBObject());
      }
      putValue((DBObject) dbObject.get(field), nextPath, value);
    } else {
      dbObject.put(path, value);
    }
  }

  public static BasicDBList wrap(List otherList) {
    BasicDBList list = new BasicDBList();
    list.addAll(otherList);
    return list;
  }

  public static List<String> split(String key) {
    char dot = '.';
    int index = key.indexOf(dot);
    if (index <= 0) {
      return Collections.singletonList(key);
    } else {
      ArrayList<String> path = new ArrayList<String>(5);
      while (index > 0) {
        path.add(key.substring(0, index));
        key = key.substring(index + 1);
        index = key.indexOf(dot);
      }
      path.add(key);
      return path;
    }
  }

  public static boolean isPositiveInt(String s) {
    for (int i = 0; i < s.length(); i++) {
      char c = s.charAt(i);
      if (c < '0' || c > '9') {
        return false;
      }
    }
    return true;
  }

  public static <T extends DBObject> T clone(T source) {
    if (source == null) {
      return null;
    }

    if (source instanceof BasicDBObject) {
      @SuppressWarnings("unchecked")
      T clone = (T) ((BasicDBObject) source).copy();
      return clone;
    }

    if (source instanceof BasicDBList) {
      @SuppressWarnings("unchecked")
      T clone = (T) ((BasicDBList) source).copy();
      return clone;
    }

    if (source instanceof LazyDBObject) {
        BasicDBObject clone = new BasicDBObject();
        for(Map.Entry<String, Object> entry : ((LazyDBObject)(source)).entrySet()) {
            clone.put(entry.getKey(), entry.getValue());
        }
        return (T) clone;
    }

    throw new IllegalArgumentException("Don't know how to clone: " + source);
  }

  public static <T extends DBObject> T convertLazy(T source) {
    if (source == null) {
      return null;
    }

    if(source instanceof LazyDBObject) {
      BasicDBObject clone = new BasicDBObject();
      for(Map.Entry<String, Object> entry : ((LazyDBObject)(source)).entrySet()) {
        Object val = entry.getValue();
        if ((val instanceof LazyDBObject) || (val instanceof LazyDBList)) {
          clone.put(entry.getKey(), convertLazy((DBObject) val));
        } else {
          clone.put(entry.getKey(), val);
        }
      }
      return (T) clone;
    }

    if(source instanceof LazyDBList) {
        BasicDBList clone = new BasicDBList();
      for(Object item : ((LazyDBList) source)) {
        if (item instanceof DBObject) {
          clone.add(convertLazy((DBObject) item));
        } else {
          clone.add(item);
        }
      }
      return (T) clone;
    }

    return source;
  }

  // When inserting, MongoDB set _id in first place.
  public static DBObject cloneIdFirst(DBObject source) {
    if (source == null) {
      return null;
    }

    // copy field values into new object
    DBObject newobj = new BasicDBObject();
    if (source.containsField(FongoDBCollection.ID_KEY)) {
      newobj.put(FongoDBCollection.ID_KEY, source.get(FongoDBCollection.ID_KEY));
    }

    Set<Map.Entry<String, Object>> entries;
    if (source instanceof LazyDBObject) {
      entries = ((LazyDBObject) source).entrySet();
    } else {
      entries = (Set<Map.Entry<String, Object>>) source.toMap().entrySet();
    }

    // need to clone the sub obj
    for (Map.Entry<String, Object> entry : entries) {
      String field = entry.getKey();
      Object val = entry.getValue();
      if (val instanceof BasicDBObject) {
        newobj.put(field, ((BasicDBObject) val).copy());
      } else if (val instanceof BasicDBList) {
        newobj.put(field, ((BasicDBList) val).copy());
      } else {
        newobj.put(field, val);
      }
    }
    return newobj;
  }
}
