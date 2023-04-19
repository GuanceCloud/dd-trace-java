package datadog.trace.civisibility

import datadog.trace.api.civisibility.CIProviderInfo

class GitLabInfoTest extends CITagsProviderImplTest {

  @Override
  CIProviderInfo instanceProvider() {
    return new GitLabInfo()
  }

  @Override
  String getProviderName() {
    return GitLabInfo.GITLAB_PROVIDER_NAME
  }

  @Override
  Map<String, String> buildRemoteGitInfoEmpty() {
    final Map<String, String> map = new HashMap<>()
    map.put(GitLabInfo.GITLAB, "true")
    map.put(GitLabInfo.GITLAB_WORKSPACE_PATH, localFSGitWorkspace)
    return map
  }

  @Override
  Map<String, String> buildRemoteGitInfoMismatchLocalGit() {
    final Map<String, String> map = new HashMap<>()
    map.put(GitLabInfo.GITLAB, "true")
    map.put(GitLabInfo.GITLAB_WORKSPACE_PATH, localFSGitWorkspace)
    map.put(GitLabInfo.GITLAB_GIT_COMMIT, "0000000000000000000000000000000000000000")
    return map
  }
}
