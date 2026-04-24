# Eclipse JDT over Javac

This repository contains support ot use Javac backend (instead of ECJ) for JDT features:

It features Javac-based:
* error reporting/reconciling
* build/compilation
* indexing
* code selection (Ctrl + click / hover)
* code completion (requires JDT fork bundle + opt-out flag)
* search/match (requires JDT fork bundle + opt-out flag)

## ❓ Why?

Some background...
* These days, with more frequent and more features Java releases, it's becoming hard for JDT to **cope with new Java features on time** and **facilitate support for upcoming/preview features before Java is released so JDT can participate to consolidation of the spec**. Over recent releases, JDT has failed at providing the features on time. This is mostly because of the difficulty of maintaining the Eclipse compiler: compilers are difficult bits of code to maintain and it takes a lot of time to implement things well in them. There is no clear sign the situation can improve here.
* The Eclipse compiler has always suffered from occasional **inconsistencies with Javac** which end-users fail at understanding. Sometimes, ECJ is right, sometimes Javac is; but for end-users and for the ecosystem, Javac is the reference implementation and it's behavior is what they perceive as the actual specification
* JDT has a very strong ecosystem (JDT-LS, plugins) a tons of nice features, so it seems profitable to **keep relying higher-level JDT APIs, such as model or DOM** to remain compatible with the ecosystem

🎯 The technical proposal here mostly to **allow Javac to be used at the lowest-level of JDT**, under the hood, to populate higher-level models that are used in many operations; named the JDT DOM and IJavaElement models. It is expected that if we can create a good DOM and IJavaElement structure with another strategy (eg using Javac API), then all higher level operations will remain working as well without modification.

## 📥 Install on top of existing JDT in your Eclipse IDE

Assuming you have Eclipse IDE well configured, just click the following link 👇 https://mickaelistria.github.io/redirctToEclipseIDECloneCommand/redirectToMarketplace.html?entryId=6444683

**OR**

Using the _Help > Install New Software_ dialog and pointing to https://ci.eclipse.org/ls/job/eclipse.jdt.javac/job/main/lastSuccessfulBuild/artifact/repository/target/repository/ p2 repository,
install the _Javac backend for JDT_ artifact. Installing it will also tweak your `eclipse.ini` file to add the relevant options.

Note that some required feature switches are not yet available in upstream JDT (see above) and thus will still default to ECJ.

## ⌨️ Development

### 🔛 Enable in your Eclipse-based application

There are several ways to achieve that:

* If your installation relies on **p2**, installing `org.eclipse.jdt.core.javac` should set the VM configuration and system properties
* If any Eclipse-based installation, having `org.eclipse.jdt.core.javac` and `org.eclipse.jdt.core.javac.configurator` available, and the later using
**autoStart and startLevel=3** would allow to use a preference to control the enablement of system properties on startup (setting the `--add-opens ...` is still
necessary on startup command-line or configuration such as eclipse.ini though). The preference value can them be set as usual, for example via some
`plugin_customization.ini`.
* More manually, you need to set the same VM args as seen in https://github.com/eclipse-jdtls/eclipse.jdt.javac/blob/main/org.eclipse.jdt.core.javac/META-INF/p2.inf or,
if using the configurator bundle at the right level, set only the `--add-opens ...` and let the configurator set other properties on early startup:
https://github.com/eclipse-jdtls/eclipse.jdt.javac/blob/main/org.eclipse.jdt.core.javac.configurator/src/org/eclipse/jdt/core/javac/configurator/JavacConfigurationActivator.java#L36


### 🔨 Building from Source

#### Local Development Builds

This project depends on test bundles that are not published to standard Eclipse P2 repositories:
- `org.eclipse.jdt.core.tests.*` bundles - automatically resolved from [CI build artifacts](https://ci.eclipse.org/ls/job/jdt-core-incubator/job/dom-with-javac/)
- `org.eclipse.jdt.ui.tests` from [eclipse-jdt/eclipse.jdt.ui](https://github.com/eclipse-jdt/eclipse.jdt.ui) - must be built manually

**To build locally**, you must first build the JDT UI test dependency:

```bash
# 1. Build JDT UI test bundles
git clone https://github.com/eclipse-jdt/eclipse.jdt.ui.git
cd eclipse.jdt.ui
mvn install -DskipTests -pl org.eclipse.jdt.ui.tests -am

# 2. Now build jdt.javac
cd /path/to/eclipse.jdt.javac
mvn clean install
```

**Note:** JDT Core test bundles (`org.eclipse.jdt.core.tests.compiler`, `org.eclipse.jdt.core.tests.model`, etc.) are automatically resolved from the forked JDT Core CI build P2 repository and do not need to be built manually.

**CI builds** automatically build all dependencies in the Jenkinsfile, so no manual steps are needed in CI.

### ⌨️ Contribute

From a PDE-able IDE using a target platform that is suitable for JDT development (usually default case).

You'll need to import the code of `org.eclipse.jdt.core.javac` from this branch in your Eclipse workspace; and create a Launch Configuration of type "Eclipse Application" which does include the `org.eclipse.jdt.core` bundle. Go to _Arguments_ tab of this launch configuration, and add the following content to the _VM arguments_ list:

```
-DCompilationUnit.DOM_BASED_OPERATIONS=true -DICompletionProvider=org.eclipse.jdt.core.dom.DOMCompletionProvider -DSourceIndexer.DOM_BASED_INDEXER=true -DICompilationUnitResolver=org.eclipse.jdt.core.dom.JavacCompilationUnitResolver -DAbstractImageBuilder.compilerFactory=org.eclipse.jdt.internal.javac.JavacCompilerFactory --add-opens jdk.compiler/com.sun.tools.javac.main=ALL-UNNAMED --add-opens jdk.compiler/com.sun.tools.javac.util=ALL-UNNAMED --add-opens jdk.compiler/com.sun.tools.javac.tree=ALL-UNNAMED --add-opens jdk.compiler/com.sun.tools.javac.api=ALL-UNNAMED --add-opens jdk.compiler/com.sun.tools.javac.file=ALL-UNNAMED --add-opens jdk.compiler/com.sun.tools.javac.parser=ALL-UNNAMED --add-opens jdk.compiler/com.sun.tools.javac.comp=ALL-UNNAMED --add-opens jdk.compiler/com.sun.tools.javac.code=ALL-UNNAMED --add-opens jdk.compiler/com.sun.tools.javac.processing=ALL-UNNAMED --add-opens jdk.compiler/com.sun.tools.javac.jvm=ALL-UNNAMED --add-opens jdk.compiler/com.sun.tools.javac.model=ALL-UNNAMED --add-opens jdk.compiler/com.sun.tools.javac.platform=ALL-UNNAMED --add-opens jdk.compiler/com.sun.tools.javac.platform=ALL-UNNAMED --add-opens jdk.compiler/com.sun.tools.javac.resources=ALL-UNNAMED --add-opens jdk.zipfs/jdk.nio.zipfs=ALL-UNNAMED --add-opens java.compiler/javax.tools=ALL-UNNAMED --add-opens java.base/sun.nio.ch=ALL-UNNAMED
```

Those arguments are automatically added to your eclipse.ini file when installing with p2.

* `CompilationUnit.DOM_BASED_OPERATIONS=true`/`CompilationUnit.codeComplete.DOM_BASED_OPERATIONS` / `SourceIndexer.DOM_BASED_INDEXER=true` system properties enables some operations to use build and DOM instead of ECJ Parser (so if DOM comes from Javac, ECJ parser is not involved at all)
* `--add-opens ...` as visible in the `org.eclipse.jdt.core.javac/META-INF/p2.inf` file to allow to access internal API of the JVM, including Javac ones
* `ICompilationUnitResolver=org.eclipse.jdt.core.dom.JavacCompilationUnitResolver` system property enables using Javac instead of ECJ to create JDT DOM AST.
* `AbstractImageBuilder.compilerFactory=org.eclipse.jdt.internal.javac.JavacCompilerFactory` system property instruct the builder to use Javac instead of ECJ to generate the .class file during build.

Those properties can be set separately, which can useful when developing one particular aspect of this proposal, which property to set depends on what you want to focus on.

### 📈 Progress

* Refactorings in upstream JDT got merged in order to allow alternative (non-ECJ strategies) for
  * reconciling model/errors using DOM ✔DONE
  * generating DOM from alternative parser ✔DONE
  * codeSelect ✔DONE
  * indexing ✔DONE
  * completion ⌨️IN PROGRESS: mostly working, fixing corner cases
  * search/match ⌨️IN PROGRESS: mostly working, fixing corner cases
  * compiler ✔DONE
* Javac backend for
  * producing Javac AST & Symbols ✔DONE (incl. annotation processing)
  * Javac->JDT AST conversion ✔DONE (incl. annotation processing)
  * binding conversion ✔DONE
  * JavaCompiler (generate .class from the IDE) ✔DONE
     * with "proceed on error" to generate code for erroneous files ✔DONE
* Performance: Javac based operations can still be noticibly slower, particularly for big projects.


🤔 What are the potential concerns:
* **Memory cost** of retaining Javac contexts needs to be evaluated (can we get rid of the context earlier? Can we share subparts of the concerns across multiple files in the project?...)
* It seems hard to find reusable parts from the **CompletionEngine**, although many proposals shouldn't really depend on the parser (so should be reusable)

😧 What are the confirmed concerns:
* **Null analysis** and some other **static analysis** are coded deep in ECJ and cannot be used with Javac. A solution can be to leverage another analysis engine (eg SpotBugs, SonarQube) deal with those features.
