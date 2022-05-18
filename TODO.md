# Wren Security Refactor

This document serves as as task list for (near) future.


## Removal of openicf-maven-plugin 

This plugin is based around CPPL licensed content which means we can not modify it. The main 
purpose was to automatically build connector documentation in a standardized form and include
it as Maven Site report. We should offer an alternative to this, preferably based on Asciidoctor's
own Maven plugin - https://docs.asciidoctor.org/maven-tools/latest/site-integration/setup-and-configuration/.

In the meantime there will be no shared documentation functionality provided by this project.

Other functionality provided by the plugin was to create so called *reduced POM*. I am not
sure what was the use case for this feature. Probably used when creating connector bundles
with embedded dependencies, however it feels like the same functionality is offered by
Maven Shade Plugin.


## Rewrite Site Content

We need to decide how to work with site content and create our own to get rid of CC-BY-NC-ND stuff.


## Revise Server Core

Server Core module contains dependencies that does not make sense on a first glance (grizzly).

Server Core has its own bundle activator that seems to duplicate stuff from framework-osgi.


## Validate OSGi Bundles

Make sure OSGi annotations are valid and the project works in OSGi environment.


## Move Package JavaDoc

Move package JavaDoc to `package-info.java` files.


## Upgrade to org.wrensecurity.commons

Blocked by Wrensec Commons release.


## Lower RPC Complexity

Not sure why RPC module is such a complex mess.


## Better Test Harness

Currently tests are based on quite a lot of timeouts which is unstable. We should try to
create test synchronization support components based on latches (not sure if possible).

Reference: https://github.com/WrenSecurity/wrenicf-java-framework/issues/4


## Upgrade Dependencies

This is kind of self explanatory.
