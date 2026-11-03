package test.disruptor;

public class EqualEvent {
  @Override
  public boolean equals(Object other) {
    return other instanceof EqualEvent;
  }

  @Override
  public int hashCode() {
    return 1;
  }
}
