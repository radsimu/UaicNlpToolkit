# RoDictCompiler

This module is used for compiling Romanian morphological dictionaries that can be loaded using the UaicMorphologicalDictionary class. It provides
- PrecompileFromDexOnlineDB - as a script that extracts morphological entries from a DexOnline database dump as a set of
dictionary precompilation resources. At this phase, some word forms are being generated automatically
(ex long infinitive, some vocatives, or dash replacement for any word starting with "î")
- PrecompileFromCorpus - a script that extracts morphological entries from a gold corpus
- CompileUaicMorphologicalDictionary - a script that compiles the precompilation resources as one file that can be used by
various tools in this toolkit through UaicMorphologicalDictionary instances. One can also manually add/adjust the precompilation resources.

This module is configured to work with the files in "root/ResourcesData/RoDictCompiler".
PrecompileFromDexOnlineDB connects to a local DexOnline db dump, but notice that the db dump itself is not part of this project.
So if you want to use this functionality, you will have to set up your own Dexonline db instance.

The files in "root/ResourcesData/RoDictCompiler/precompilation entries" are the ones used for compiling the resulting dictionary
and are currently a mixture of manually created files, files extracted from an extended (and non-public) version of DexOnline, Romanian wikipedia
proper nouns and some entries extracted from gold corpora. These resources directly impact the quality of tools that make use of
the Uaic's Romanian morphological dictionary and for this reason they are versioned. Because some of the file sizes exceed 10 mb,
the entire folder is versioned in zip format. If anyone ever wants to play around with these resources and share on git, please consider
splitting all resources in files less than 10 mb so that zipping is not necessary anymore and diffs become possible.

The file "lexemPriority.total" is treated differently. It contains frequency scores for lemmas and it is extracted also from Dexonline.
This information gets included in the compiled resource and is used in case of lemma resolution ambiguity: when there are more possible lemmas
for a given word form and its part of speech.

The compiled resource is saved as "root/ResourcesData/RoDictCompiler/result/posDictRo.txt" along with other files in the same folder, as follows:
- tagsetFromDict.txt - self explanatory name
- invalidTagsReport.txt - based on a comparison with a given tagset that must be present in the same folder, with name "tagsetManual.txt"

# License

This source code is licensed under Apache License 2.0

Third party libraries
-
Apache License 2.0
- jsyntaxpane
- TestNG
- OpenNLP
- Apache Lucene
- Apache Commons Lang

Mozilla Public License 2.0
- Mozilla Rhino