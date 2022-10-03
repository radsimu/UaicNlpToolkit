/**************************************************************************
 * Copyright © 2017 Radu Simionescu
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *************************************************************************/

package ro.uaic.info.nlptools.tools;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

class UaicSegmenter {

//    public static void main(String[] args) throws UnsupportedEncodingException, FileNotFoundException, IOException, InstantiationException, IllegalAccessException, Exception {
//        BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream("inputuri/test.txt"), "UTF8"));
//        String line = null;
//        StringBuilder sb = new StringBuilder();
//        while ((line = br.readLine()) != null) {
//            if (line.startsWith("\uFEFF")) {
//                line = line.substring(1);
//            }
//            sb.append(line).append("\n");
//        }
//
//        UaicMorphologicalDictionary dic = new UaicMorphologicalDictionary();
//        dic.load(new FileInputStream(args[0]));
//        UaicSegmenter splitter = new UaicSegmenter(dic);
//
//        List<SentenceHunk> phrases = splitter.segment(sb.toString());
//        for (SentenceHunk sentenceStruct : phrases) {
//            System.out.println(sentenceStruct);
//        }
//    }

    public UaicMorphologicalDictionary dic;
    static Pattern PARAGRAPH_REGEXP = Pattern.compile("[ \\t]*(\r\n|\r|\n)(?:[ \\t]*\\1+)+[ \\t]*|(?<=[.?!])\\s*[\n\r\\n]+\\s*", Pattern.MULTILINE);
    static Pattern WORD_REGEXP = Pattern.compile("[ /,\t]+");
    static Pattern PHRASE_REGEXP_SPLIT = Pattern.compile("((?<=[\\s\"\'%)}/][\\p{L}\\p{Nd}_.]{0,25}\\s?[?!.])([\r\n\\s]*)(?=(?:\\p{L}+|''|[(\"„])))  |"
                    + " ((<?=[\\.!]{2}) ([\r\n\\s]*) (?=[\\p{L}\\p{Nd}])) | ((?<=\\.) \\s* (?=\\p{L}))",
            Pattern.COMMENTS);

    public UaicSegmenter(UaicMorphologicalDictionary dic) {
        this.dic = dic;
    }

    static Pattern LINKS_EMAILS_ABBREVS = Pattern.compile("" +
            // email-uri
            "(?:[a-z0-9!$%&'*+/=?^_`{|}~-]+(?:\\.[a-z0-9!$%&'*+/=?^_`{|}~-]+)*@(?:[a-z0-9](?:[a-z0-9-]*[a-z0-9])?\\.)+[a-z0-9](?:[a-z0-9-]*[a-z0-9])?)" +
            "|" +
            //links
            "(?:(?i)\\b((?:[a-z][\\w-]+:(?:/{1,3}|[a-z0-9%])|www\\d{0,3}[.]|[a-z0-9.\\-]+[.][a-z]{2,4}/)(?:[^\\s()<>]+|\\(([^\\s()<>]+|(\\([^\\s()<>]+\\)))*\\))+(?:\\(([^\\s()<>]+|(\\([^\\s()<>]+\\)))*\\)|[^\\s`!()\\[\\]{};:'\".,<>?«»“”‘’])))" +
            "|" +
            //abrevieri cu .
            "(?:(?:\\p{L}\\.){2,})" +
            "|" +
            "(?:([^\\p{Z}\\s\\n\\r]{20,}))" + // orice sir continuu de caractere mai lung de 20 sa nu poata fi spart
            "", Pattern.COMMENTS);

    public Map.Entry<List<SentenceHunk>, List<Integer>> segment(String inputText) throws InstantiationException, IllegalAccessException {

        List<SentenceHunk> sentences = new ArrayList<>();
        if (inputText == null)
            return new AbstractMap.SimpleEntry<>(sentences, null);
        List<Integer> nonbreakableBoundaries = findNonbreakable(inputText); // daca un token sau propozitie contine aceste ofseturi, trebuie sa nu poata fi spart
        Matcher matcher = PARAGRAPH_REGEXP.matcher(inputText);

        int offset = 0;
        String text = inputText;

        int k = 0;
        while (matcher.find()) {
            String currentPara = inputText.substring(offset, matcher.start());
            Map.Entry<Integer, Integer> trimBy = trim(currentPara);
            currentPara = currentPara.substring(trimBy.getKey(), trimBy.getValue());

            offset += trimBy.getKey();

            if (currentPara.length() > 0) {
                TextParagraph textParagraph = new TextParagraph();

                textParagraph.text = currentPara;
                textParagraph.offset = offset;

                splitToPhrases(textParagraph, nonbreakableBoundaries);
                for (SentenceHunk s : textParagraph.phrases) {
                    k++;
                    s.id = "" + k;
                    sentences.add(s);
                }
            }

            offset = matcher.end();
            text = inputText.substring(offset, inputText.length());
        }

        Map.Entry<Integer, Integer> trimBy = trim(text);
        text = text.substring(trimBy.getKey(), trimBy.getValue());
        offset += trimBy.getKey();

        if (text.length() > 0) {
            TextParagraph textParagraph = new TextParagraph();

            textParagraph.text = text;
            textParagraph.offset = offset;

            splitToPhrases(textParagraph, nonbreakableBoundaries);
            for (SentenceHunk s : textParagraph.phrases) {
                k++;
                s.id = "" + k;
                sentences.add(s);
            }
        }

        return new AbstractMap.SimpleEntry<List<SentenceHunk>, List<Integer>>(sentences, nonbreakableBoundaries);
    }

    private Map.Entry<Integer, Integer> trim(String substring) {

        char[] chars = substring.toCharArray();

        int start = 0;
        while (start < chars.length
                && (chars[start] == ' ' || chars[start] == '\t' || chars[start] == '\r' || chars[start] == '\n' || chars[start] == '\u00a0')) {
            start++;
        }

        int end = chars.length - 1;
        while (end > start
                && (chars[end] == ' ' || chars[end] == '\t' || chars[end] == '\r' || chars[end] == '\n' || chars[end] == '\u00a0')) {
            end--;
        }

        return new AbstractMap.SimpleEntry<Integer, Integer>(start, end + 1);
    }

    public void splitToPhrases(TextParagraph para, List<Integer> nonbreakableBoundaries) {

        List<SentenceHunk> phrases = new ArrayList<SentenceHunk>();

        String inputText = para.text;
        Matcher matcher = PHRASE_REGEXP_SPLIT.matcher(inputText);

        int offset = 0;

        while (matcher.find()) {
            boolean doNotSplit = false;
            for (int i = 0; i < nonbreakableBoundaries.size(); i += 2) {
                if (nonbreakableBoundaries.get(i) > matcher.end() + para.offset) {
                    break;
                }
                if ((matcher.start() + para.offset > nonbreakableBoundaries.get(i) && matcher.start() + para.offset < nonbreakableBoundaries.get(i + 1)) || (matcher.end() + para.offset > nonbreakableBoundaries.get(i) && matcher.end() + para.offset < nonbreakableBoundaries.get(i + 1))) {
                    doNotSplit = true;
                    break;
                }
            }
            if (doNotSplit) {
                continue;
            }

            String currentText = inputText.substring(offset, matcher.start());

            // if the end of proposition is a strange case (ending with .) we will check against known abbreviations.
            if (currentText.charAt(currentText.length() - 1) == '.') {

                String[] words = WORD_REGEXP.split(currentText);
                String word = words[words.length - 1];

                if (oneUpperCaseLetter(word) || isAcronym(word) || endsWithPunctuation(word) || (dic != null && dic.isAbbreviation(word))) {
                    if (word.endsWith(".")) {
                        word = word.substring(0, word.length() - 1);
                    }
                    Set<UaicMorphologicalAnnotation> annots = dic.get(word);
                    boolean couldBeNonAbrev = false;
                    if (annots != null) {
                        for (UaicMorphologicalAnnotation annot : annots) {
                            if (!annot.getMsd().equals("Y")) {
                                couldBeNonAbrev = true;
                                break;
                            }
                        }
                    }
                    if (!couldBeNonAbrev) {//don't do the split
                        continue;
                    }
                }
                String next = inputText.substring(matcher.start());
                int aux = next.indexOf(".");
                if (aux > 0 && dic.isAbbreviation(next.substring(0, aux + 1))) {
                    continue;
                }
            }

            SentenceHunk textPhrase = new SentenceHunk();
            textPhrase.parentParagraph = para;
            textPhrase.offset = offset + para.offset;
            textPhrase.text = currentText;
            textPhrase.id = "" + phrases.size();
            phrases.add(textPhrase);
            offset = matcher.end();
        }

        String lastPhraseText = inputText.substring(offset, inputText.length());
        SentenceHunk lastTextPhrase = new SentenceHunk();
        lastTextPhrase.parentParagraph = para;
        lastTextPhrase.offset = offset + para.offset;
        lastTextPhrase.text = lastPhraseText;
        lastTextPhrase.id = "" + phrases.size();

        phrases.add(lastTextPhrase);
        para.setPhrases(phrases);
    }

    private boolean oneUpperCaseLetter(String word) {
        return word.matches("\\p{Lu}\\.");
    }

    private boolean endsWithPunctuation(String word) {
        return word.matches("[^\\p{L}\\p{Nd} \\t\\.]$") || word.matches("\\.{2,}");
    }

    private boolean isAcronym(String word) {
        return word.matches("\\p{Lu}+\\.(\\p{Lu}+\\.)+");
    }

    protected static List<Integer> findNonbreakable(String text) {
        List<Integer> nonbreakableBoundaries = new ArrayList<Integer>();

        Matcher matcher = LINKS_EMAILS_ABBREVS.matcher(text);
        //identify emails links & abbreviations
        while (matcher.find()) {
            nonbreakableBoundaries.add(matcher.start());
            nonbreakableBoundaries.add(matcher.end());
        }
        return nonbreakableBoundaries;
    }
}
