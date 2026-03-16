# 1.5.4.1

Connector bundle Maven groupId changed to `org.wrensecurity.wrenicf.connectors`.


# 1.5.4.0

Significant dependency updates:

* Commons - from 22.2.0 to 23.0.0
* Groovy - from 2.4.21 to 5.0.4
* Grizzly - from 2.3.35 to 4.0.2
* Protobuf - from 3.0.2 to 3.25.8

Change in license file fetching:

* logic moved from maven-remote-resources-plugin to license-maven-plugin:
* the license plugin needs to be defined in connector projects instead of the former one


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
