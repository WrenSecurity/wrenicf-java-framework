# Wren:ICF Java Framework

<p align="center">
  <picture>
    <source media="(prefers-color-scheme: dark)" srcset="https://github.com/user-attachments/assets/9b877fb8-28ec-457e-9281-1de449e54e77">
    <source media="(prefers-color-scheme: light)" srcset="https://github.com/user-attachments/assets/e20cf90c-4593-495e-bfa7-f26ad31f3719">
    <img alt="Wren:ICF logo" src="https://github.com/user-attachments/assets/e20cf90c-4593-495e-bfa7-f26ad31f3719" width="70%">
  </picture>
</p>

[![License](https://img.shields.io/badge/license-CDDL-blue.svg)](https://github.com/WrenSecurity/wrenicf-java-framework/blob/main/LICENSE)
[![Gitter](https://badges.gitter.im/Join%20Chat.svg)](https://gitter.im/WrenSecurity)

Wren:ICF Java Framework is a set of components for building and running _identity connectors_ in
Java. It provides the core API, SPI and runtime needed to develop, deploy and manage connectors
that integrate external identity resources with your application.

Wren:ICF is one of the projects in the Wren Security Suite, a community initiative that adopted
open-source projects formerly developed by ForgeRock, which has its own roots in Sun Microsystems'
products.


## What is an Identity Connector?

An identity connector is a component that provides uniform access to an external identity resource
such as an LDAP directory, relational database, REST service or a flat file. The connector translates
generic operations defined by the framework (create, read, update, delete, search, sync, etc.) into
resource-specific calls, hiding the communication details behind a common interface
([ConnectorFacade](https://github.com/WrenSecurity/wrenicf-java-framework/blob/main/framework-core/src/main/java/org/identityconnectors/framework/api/ConnectorFacade.java)).

Connector developers implement the SPI interfaces (e.g. `CreateOp`, `SearchOp`, `SyncOp`) while
applications consume connectors through the corresponding API operations (e.g. `CreateApiOp`,
`SearchApiOp`, `SyncApiOp`).


## What is a Connector Bundle?

A connector bundle is a JAR containing one or more connector implementations together with their metadata
and internal dependencies (bundled JARs). The bundle manifest includes special JAR Manifest headers
(`ConnectorBundle-FrameworkVersion`, `ConnectorBundle-Name`, `ConnectorBundle-Version`) that allow the framework
to discover and load connectors at runtime. Bundles can be deployed locally or served remotely through the connector
server.


## Contributions

[![Contributing Guide](https://img.shields.io/badge/Contributions-guide-green.svg?style=flat)][contribute]
[![Contributors](https://img.shields.io/github/contributors/WrenSecurity/wrenicf-java-framework)][contribute]
[![Pull Requests](https://img.shields.io/github/issues-pr/WrenSecurity/wrenicf-java-framework)][contribute]
[![Last commit](https://img.shields.io/github/last-commit/WrenSecurity/wrenicf-java-framework.svg)](https://github.com/WrenSecurity/wrenicf-java-framework/commits/main)


## Getting the Wren:ICF

You can get Wren:ICF Java Framework in a couple of ways:


### Maven dependency

Wren:ICF artifacts are published to the Wren Security Maven repository. Add the repository and
dependency to your project:

```xml
<repositories>
    <repository>
        <id>wrensecurity-releases</id>
        <url>https://wrensecurity.jfrog.io/wrensecurity/releases</url>
    </repository>
</repositories>

<dependency>
    <groupId>org.wrensecurity.wrenicf</groupId>
    <artifactId>connector-framework-core</artifactId>
    <version>1.5.4.0-M1</version>
</dependency>
```


### Build the source code

In order to build the project from the command line follow these steps:

**Prepare your Environment**

Following software is needed to build the project:

| Software  | Required Version |
| --------- | ---------------- |
| OpenJDK   | 17 and above     |
| Git       | 2.0 and above    |
| Maven     | 3.0 and above    |

**Build the source code**

All project dependencies are hosted in JFrog repository and managed by Maven, so to build the
project simply execute Maven *verify* goal.

```
$ cd wrenicf-java-framework
$ mvn clean verify
```

## Acknowledgments

Large portions of the source code are based on the open-source projects previously released by:

* Sun Microsystems
* ForgeRock

We'd like to thank them for supporting the idea of open-source software.


## Disclaimer

Please note that the acknowledged parties are not affiliated with this project. Their trade names,
product names and trademarks should not be used to refer to the Wren Security products, as it might
be considered an unfair commercial practice.

Wren Security is open source and always will be.

[contribute]: https://github.com/WrenSecurity/wrensec-docs/wiki/Contributor-Guidelines
