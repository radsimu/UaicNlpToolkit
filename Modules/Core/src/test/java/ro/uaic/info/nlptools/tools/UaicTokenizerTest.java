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

import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import ro.uaic.info.nlptools.corpus.InmemoryCorpus;

import java.io.FileInputStream;

import static org.testng.Assert.*;

public class UaicTokenizerTest {
    UaicMorphologicalDictionary morphologicalDictionary;

    @BeforeClass
    public void setUp() throws Exception {
        morphologicalDictionary = new UaicMorphologicalDictionary();
        morphologicalDictionary.load(new FileInputStream("TestData/posResources/posDictRoDiacr.txt"));
    }

    @Test
    public void test_SplitAndTokenize_emails_urls_abbrevs_hyphen_dash() throws IllegalAccessException, InstantiationException {
        String input = " dr. Andrei Iată P.D.L. 16,173 într-un 16.173 copac că-ntr-un\n sfârșit și-ncreți-li-s-ar zi iată că-n față se afla cross-over-ul... pritenii ți-s de fapt dușmani, și-ncreți-li-s-ar fruntea când o să afle radu.simionescu@info.uaic.ro https://eu.sfas.ro";

        UaicTokenizer tokenizer = new UaicTokenizer(morphologicalDictionary);
        InmemoryCorpus rez = tokenizer.splitAndTokenize(input);
        assertEquals(rez.getSentenceCount(), 2);
        assertEquals(rez.getSentence(0).getTokenCount(), 28);
        assertEquals(rez.getSentence(1).getToken(15).getWordForm(), "radu.simionescu@info.uaic.ro");
        assertEquals(rez.getSentence(1).getToken(16).getWordForm(), "https://eu.sfas.ro");
    }

    @Test
    public void test_SplitAndTokenize_asterix_doubleSpace_dash() throws IllegalAccessException, InstantiationException {
        String input = "'Neata  În loc de Bunǎ dimineata , Sal'tare  În loc de Salutare , Ascultǎ dom'le  Cin' sǎ-l mai înțeleagǎ  Un' te duci  Umblǎ fǎr' de rost.";

        UaicTokenizer tokenizer = new UaicTokenizer(morphologicalDictionary);
        InmemoryCorpus rez = tokenizer.splitAndTokenize(input);
        assertEquals(rez.getSentence(0).getTokenCount(), 24);
    }

    @Test
    public void test_SplitAndTokenize_NamesWithDash() throws IllegalAccessException, InstantiationException {
        String input = "Ana-Maria \t Vasile-Petronel!";

        UaicTokenizer tokenizer = new UaicTokenizer(morphologicalDictionary);
        InmemoryCorpus rez = tokenizer.splitAndTokenize(input);
        assertEquals(rez.getTokenCount(), 4);
    }

    @Test
    public void test_SplitAndTokenize_collon_elipse() throws IllegalAccessException, InstantiationException {
        String input = "Elena Udrea înainte de a exprima o anumită grijă de-a binelea care-i muncește în aceste zile pe multi dintre pedelisti: ce se-ntâmplă dacă-și ia mâna ... de pe noi?";

        UaicTokenizer tokenizer = new UaicTokenizer(morphologicalDictionary);
        InmemoryCorpus rez = tokenizer.splitAndTokenize(input);
        assertEquals(rez.getTokenCount(), 31);
        assertEquals(rez.getToken(22).getWordForm(), "-ntâmplă");
    }
}