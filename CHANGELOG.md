# 1.5.3.0

Summary (more details in sections bellow):

* Maven groupId refactored to `org.wrensecurity.wrenicf`.
* Updated artifactIds to keep project structure more concise (see bellow).
* Shared license bundle is now included as Maven submodule instead of being separate project.
* Custom JavadocUpdaterTool Maven plugin was removed as no longer necessary.
* Connector Framework is compatible with Java 8, 11 and 17.

Maven module folder names were updated to the following convention:

* `framework-*` - core connector framework modules
* `server-*` - connector server modules
* `test-*` - test support modules
* `bundle-*` - connector bundle modules

Maven groupId for individual modules was changed to the following:

* `org.wrensecurity.wrenicf` - group solely for root aggregator project and license bundle
* `org.wrensecurity.wrenicf.framework` - framework modules
* `org.wrensecurity.wrenicf.connector` - connector bundles

Major artifact identifier changes are:

* `connector-framework-parent` module was introduced to provide place for common configuration
  without polluting the root aggregator project (`connector-bundle-parent` is no longer sharing
  plugin configuration with the framework)
* `connector-framework` module was renamed to `connector-framework-core`
* `connector-test-common` module was renamed to `connector-framework-test`
* `connector-framework-server` module was renamed to `connector-server-core`
