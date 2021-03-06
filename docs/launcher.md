[![Cognifide logo](cognifide-logo.png)](http://cognifide.com)

<p>
  <img src="logo.png" alt="Gradle AEM Plugin"/>
</p>

# Standalone launcher

* [About](#about)
* [Setting up local instance](#setting-up-local-instance)
* [Tailing logs](#tailing-logs)
* [Copying content between instances](#copying-content-between-instances)

## About

Some of the GAP features could be useful even when not building AEM application.
Moreover, to run GAP, it is needed to have a project which has at least Gradle Wrapper files and minimal Gradle configuration that applies Gradle AEM Plugin.
To eliminate such ceremony, GAP standalone launcher could be used to be able to use its features with minimal effort, anywhere.
Simply, using e.g bash script - download the GAP launcher run it with regular GAP arguments - all tasks and properties are available to be used.

Note that when it is needed to e.g specify GAP properties e.g related with source of AEM instance JAR & license files when running `up` task, 
consider adding argument `--save-props` when running GAP launcher. It will save all other command line properties to `gradle.properties` file.
Thanks to that, when running `down` task next time, all properties related with instance definitions will be no longer needed to be passed as command line arguments.

Alternatively, when technique for credentials passed as command line parameters is considered as not enough safe, it is an option to create file `gap/gradle.properties` 
and specify all required properties there before running the launcher.

## Setting up local instance

To set up and turn on AEM instance(s) by single command, consider running:

```bash
curl -O https://github.com/Cognifide/gradle-aem-plugin/releases/download/13.2.1/gap.jar \
&& java -jar gap.jar --save-props up \
-PlocalInstance.quickstart.jarUrl=http://company-share.com/aem/cq-quickstart-6.5.0.jar \
-PlocalInstance.quickstart.licenseUrl=http://company-share.com/aem/license.properties \
-PfileTransfer.user=foo \
-PfileTransfer.password=pass \
-Pinstance.local-author.httpUrl=http://localhost:4502 \
-Pinstance.local-author.type=local
```

As of previously `--save-props` argument was specified, now to turn off AEM instance(s), simply run (rest of properties could be omitted):

```bash
java -jar gap.jar down
```

## Tailing logs

To interactively monitor logs of any AEM instances using task [`instanceTail`](instance-plugin.md#task-instancetail), consider running command:

```bash
curl -O https://github.com/Cognifide/gradle-aem-plugin/releases/download/13.2.1/gap.jar \
&& java -jar gap.jar --save-props instanceTail \
-Pinstance.staging-author.httpUrl=http://10.11.12.1:4502 \
-Pinstance.staging-publish.httpUrl=http://10.11.12.2:4503
```

## Copying content between instances

To copy JCR content between any AEM instances using task [`instanceRcp`](instance-plugin.md#task-instancercp), consider running command:

```bash
curl -O https://github.com/Cognifide/gradle-aem-plugin/releases/download/13.2.1/gap.jar \
&& java -jar gap.jar instanceRcp \
-Pinstance.rcp.source=http://foo:pass@10.11.12.1:4502 \
-Pinstance.rcp.target=http://foo:pass@10.11.12.2:4503 \
-Pinstance.rcp.paths=[/content/example,/content/dam/example]
```