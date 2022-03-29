package sanning.http;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class Headers {

   private final List<String> names;
   private final Map<String, List<String>> headerMap;

   Headers() {
      names = new ArrayList<>();
      headerMap = new HashMap<>();
   }

   public List<String> names() { return names; }

   public void addValue(String name, String value) { doSetValue(name, value, false); }
   public void setValue(String name, String value) { doSetValue(name, value, true); }

   @SuppressWarnings("unchecked")
   public List<String> multiValue(String name) {
      List<String> values = headerMap.get(name.toLowerCase());
      return values != null ? values : Collections.EMPTY_LIST;
   }

   public String singleValue(String name) {
      List<String> values = headerMap.get(name.toLowerCase());
      return values != null ? values.get(0) : null;
   }

   private void doSetValue(String name, String value, boolean override) {
      String lname = name.toLowerCase();
      List<String> values = headerMap.get(lname);
      if (values == null) {
         values = new ArrayList<>(1);
         names.add(name);
         headerMap.put(lname, values);
      }
      if (override) {
         values.clear();
      }
      values.add(value);
   }

}
