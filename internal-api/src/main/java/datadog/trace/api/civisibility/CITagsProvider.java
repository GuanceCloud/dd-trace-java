package datadog.trace.api.civisibility;

import java.util.Map;

public interface CITagsProvider {
  Map<String, String> getCiTags(CIProviderInfo ciProviderInfo);
}
