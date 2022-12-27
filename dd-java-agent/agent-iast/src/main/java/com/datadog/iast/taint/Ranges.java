package com.datadog.iast.taint;

import com.datadog.iast.model.Range;
import com.datadog.iast.model.Source;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** Utilities to work with {@link Range} instances. */
public final class Ranges {

  public static final Range[] EMPTY = new Range[0];
  public static final RangesProvider<?> EMPTY_PROVIDER =
      new RangesProvider<Object>() {
        @Override
        public int rangeCount() {
          return 0;
        }

        @Override
        public int size() {
          return 0;
        }

        @Override
        public Object value(final int index) {
          throw new UnsupportedOperationException("empty provider");
        }

        @Override
        public Range[] ranges(final Object value) {
          throw new UnsupportedOperationException("empty provider");
        }
      };

  private Ranges() {}

  public static Range[] forString(final @Nonnull String obj, final @Nonnull Source source) {
    return new Range[] {new Range(0, obj.length(), source)};
  }

  public static void copyShift(
      final @Nonnull Range[] src, final @Nonnull Range[] dst, final int dstPos, final int shift) {
    if (shift == 0) {
      System.arraycopy(src, 0, dst, dstPos, src.length);
    } else {
      for (int iSrc = 0, iDst = dstPos; iSrc < src.length; iSrc++, iDst++) {
        dst[iDst] = src[iSrc].shift(shift);
      }
    }
  }

  public static Range[] mergeRanges(
      final int offset, @Nonnull final Range[] rangesLeft, @Nonnull final Range[] rangesRight) {
    final int nRanges = rangesLeft.length + rangesRight.length;
    final Range[] ranges = new Range[nRanges];
    if (rangesLeft.length > 0) {
      System.arraycopy(rangesLeft, 0, ranges, 0, rangesLeft.length);
    }
    if (rangesRight.length > 0) {
      Ranges.copyShift(rangesRight, ranges, rangesLeft.length, offset);
    }
    return ranges;
  }

  @SuppressWarnings("unchecked")
  public static <E> RangesProvider<E> rangesProviderFor(
      @Nonnull final TaintedObjects to, @Nullable final E[] items) {
    if (items == null || items.length == 0) {
      return (RangesProvider<E>) EMPTY_PROVIDER;
    }
    return new ArrayProvider<>(items, to);
  }

  @SuppressWarnings("unchecked")
  public static <E> RangesProvider<E> rangesProviderFor(
      @Nonnull final TaintedObjects to, @Nullable final List<E> items) {
    if (items == null || items.isEmpty()) {
      return (RangesProvider<E>) EMPTY_PROVIDER;
    }
    return new ListProvider<>(items, to);
  }

  public interface RangesProvider<E> {
    int rangeCount();

    int size();

    E value(final int index);

    Range[] ranges(final E value);
  }

  private abstract static class IterableProvider<E, LIST> implements RangesProvider<E> {
    private final LIST items;
    private final Map<E, Range[]> ranges;
    private final int rangeCount;

    private IterableProvider(@Nonnull final LIST items, @Nonnull final TaintedObjects to) {
      this.items = items;
      final int length = size(items);
      Map<E, Range[]> ranges = null;
      int rangeCount = 0;
      for (int i = 0; i < length; i++) {
        final E item = item(items, i);
        if (item != null) {
          final TaintedObject tainted = to.get(item);
          if (tainted != null) {
            final Range[] taintedRanges = tainted.getRanges();
            rangeCount += taintedRanges.length;
            if (ranges == null) {
              ranges = new HashMap<>(length);
            }
            ranges.put(item, taintedRanges);
          }
        }
      }
      this.ranges = ranges;
      this.rangeCount = rangeCount;
    }

    @Override
    public int rangeCount() {
      return rangeCount;
    }

    @Override
    public E value(final int index) {
      return item(items, index);
    }

    @Override
    public Range[] ranges(final E value) {
      return ranges == null ? null : ranges.get(value);
    }

    @Override
    public int size() {
      return size(items);
    }

    protected abstract int size(@Nonnull final LIST items);

    protected abstract E item(@Nonnull final LIST items, final int index);
  }

  private static class ArrayProvider<E> extends IterableProvider<E, E[]> {

    private ArrayProvider(@Nonnull final E[] items, @Nonnull final TaintedObjects to) {
      super(items, to);
    }

    @Override
    protected int size(@Nonnull final E[] items) {
      return items.length;
    }

    @Override
    protected E item(@Nonnull final E[] items, final int index) {
      return items[index];
    }
  }

  private static class ListProvider<E> extends IterableProvider<E, List<E>> {

    private ListProvider(@Nonnull final List<E> items, @Nonnull final TaintedObjects to) {
      super(items, to);
    }

    @Override
    protected int size(@Nonnull final List<E> items) {
      return items.size();
    }

    @Override
    protected E item(@Nonnull final List<E> items, final int index) {
      return items.get(index);
    }
  }
}
