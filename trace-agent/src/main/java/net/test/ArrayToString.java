package net.test;

import java.lang.reflect.Array;

/**
 * Convenience method for producing a simple textual representation of an array.
 *
 * <p>The format of the returned <code>String</code> is the same as <code>
 * AbstractCollection.toString</code>:
 *
 * <ul>
 *   <li>non-empty array: [blah, blah]
 *   <li>empty array: []
 *   <li>null array: null
 * </ul>
 *
 * @author Jerome Lacoste
 * @author www.javapractices.com
 */
public final class ArrayToString {

  /**
   * <code>aArray</code> is a possibly-null array whose elements are primitives or objects; arrays
   * of arrays are also valid, in which case <code>aArray</code> is rendered in a nested, recursive
   * fashion.
   */
  public static String get(Object array) {
    if (array == null) return NULL;
    checkObjectIsArray(array);

    StringBuilder result = new StringBuilder(START_CHAR);
    int length = Array.getLength(array);
    for (int idx = 0; idx < length; ++idx) {
      Object item = Array.get(array, idx);
      if (isNonNullArray(item)) {
        // recursive call!
        result.append(get(item));
      } else {
        result.append(item);
      }
      if (!isLastItem(idx, length)) {
        result.append(SEPARATOR);
      }
    }
    result.append(END_CHAR);
    return result.toString();
  }

  // PRIVATE
  private static final String START_CHAR = "[";
  private static final String END_CHAR = "]";
  private static final String SEPARATOR = ", ";
  private static final String NULL = "null";

  private static void checkObjectIsArray(Object array) {
    if (!array.getClass().isArray()) {
      throw new IllegalArgumentException("Object is not an array.");
    }
  }

  private static boolean isNonNullArray(Object item) {
    return item != null && item.getClass().isArray();
  }

  private static boolean isLastItem(int idx, int length) {
    return (idx == length - 1);
  }
}
