# UaicNlpToolkit

This project is a set of NLP tools that I have developped during my Master and PhD studies at UAIC of Iași.
The tools come preparred with resources for processing Romanian with as little effort as possible. 
The built/ready to use versions of the tools are maintained in the "artifacts" folder:
- AnnotationsExplorer - a tool for viewing corpora, handy especialy in the case of stand-off annotations
- GGS (engine and editor) - Graphical Grammar Studio
- Core - Common corpus functionality mostly required by the other tools. As a standalone tool, it will be able to convert, merge and index corpora.


The rest represents source code, configurations, tests, etc. Contributions are welcome.

The toolkit currently contains:
- 
- Graphical Grammar Studio (GGS)
- Annotations Explorer (initially part of GGS, now as a separate tool for browsing corpora)
- A morphological dictionary built for Romanian to enable support for easy switching between "diacritics only", "no diacritics" and "mixed diacritics" modes
- Uaic's hybrid part of speech tagger

This is a work in progress. I intend to include UAIC's Romanian dependency parser in this toolkit soon.

The sources are configured as an Intellij project. The important folders are:
- Modules: this is where all the source code is
	- AnnotationsExplorer - initially part of GGS, now as a separate tool for browsing corpora
	- Core - Basic functionality for loading corpora in different formats that were frequently used in UAIC's NLP group, with limited support for automatic input format detection
	- GGS - Graphical Grammar Studio containing
		- GGSEngine - The GGS engine as a library
		- GGSEditor - The GGS editor for designing and testing ggs networks
		- GGSInferer - An experimental system that builds a GGS network from a set of sequences that need to be matched by it
	- UaicPosTagger
- TestData: *UNZIP* the file in this folder to populate it with the testing resources, which are used by the tests contained in the various modules. Test resources are shared amongst the tests from the various modules.
- TestUserStories: this is an aditional module containing some high level tests.



This source code is licensed under Apache License 2.0

# Included libraries
Apache License 2.0
- jsyntaxpane
- TestNG
- OpenNLP
- Apache Lucene
- Apache Commons Lang

Mozilla Public License 2.0
- Mozilla Rhino