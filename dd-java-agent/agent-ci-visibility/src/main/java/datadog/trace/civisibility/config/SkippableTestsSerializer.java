package datadog.trace.civisibility.config;

import datadog.trace.api.civisibility.config.SkippableTest;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;

public abstract class SkippableTestsSerializer {

  private static final Charset CHARSET = StandardCharsets.UTF_8;

  public static ByteBuffer serialize(Collection<SkippableTest> skippableTests) {
    if (skippableTests == null || skippableTests.isEmpty()) {
      return ByteBuffer.allocate(0);
    }

    int length =
        Integer.BYTES // skippableTests.size()
            // suite, name, parameters length() for each test
            + skippableTests.size() * 3 * Integer.BYTES;
    for (SkippableTest test : skippableTests) {
      String suite = test.getSuite();
      String name = test.getName();
      String parameters = test.getParameters();

      length += suite.getBytes(CHARSET).length;
      length += name.getBytes(CHARSET).length;
      if (parameters != null) {
        length += parameters.getBytes(CHARSET).length;
      }
    }

    ByteBuffer buffer = ByteBuffer.allocate(length);
    buffer.putInt(skippableTests.size());

    for (SkippableTest test : skippableTests) {
      String suite = test.getSuite();
      String name = test.getName();
      String parameters = test.getParameters();

      byte[] suiteBytes = suite.getBytes(CHARSET);
      buffer.putInt(suiteBytes.length);
      buffer.put(suiteBytes);

      byte[] nameBytes = name.getBytes(CHARSET);
      buffer.putInt(nameBytes.length);
      buffer.put(nameBytes);

      if (parameters != null) {
        byte[] parametersBytes = parameters.getBytes(CHARSET);
        buffer.putInt(parametersBytes.length);
        buffer.put(parametersBytes);
      } else {
        buffer.putInt(-1);
      }
    }

    buffer.flip();
    return buffer;
  }

  public static Collection<SkippableTest> deserialize(ByteBuffer buffer) {
    if (buffer.remaining() == 0) {
      return Collections.emptyList();
    }

    int count = buffer.getInt();
    Collection<SkippableTest> tests = new ArrayList<>(count * 4 / 3);
    while (count-- > 0) {
      int suiteLength = buffer.getInt();
      byte[] suiteBytes = new byte[suiteLength];
      buffer.get(suiteBytes);
      String suite = new String(suiteBytes, CHARSET);

      int nameLength = buffer.getInt();
      byte[] nameBytes = new byte[nameLength];
      buffer.get(nameBytes);
      String name = new String(nameBytes, CHARSET);

      String parameters;
      int parametersLength = buffer.getInt();
      if (parametersLength >= 0) {
        byte[] parametersBytes = new byte[parametersLength];
        buffer.get(parametersBytes);
        parameters = new String(parametersBytes, CHARSET);
      } else {
        parameters = null;
      }

      tests.add(new SkippableTest(suite, name, parameters, null));
    }
    return tests;
  }
}
