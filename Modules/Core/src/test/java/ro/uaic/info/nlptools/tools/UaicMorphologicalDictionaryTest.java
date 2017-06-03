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

/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ro.uaic.info.nlptools.tools;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Dictionary;

import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

/**
 * @author Radu
 */
public class UaicMorphologicalDictionaryTest {

    static UaicMorphologicalDictionary dictionary;

    public UaicMorphologicalDictionaryTest() throws FileNotFoundException, IOException {
    }

    @BeforeClass
    public static void setUpClass() throws Exception {
        InputStream is = new FileInputStream("TestData/posResources/posDictRoDiacr.txt");
        dictionary = new UaicMorphologicalDictionary();
        dictionary.load(is);
        is.close();
    }

    /**
     * Test of saveCompact method, of class UaicMorphologicalDictionary.
     */
    @Test
    public void testSave() throws Exception {
        dictionary.save("TestData/generated/morphologicalDictionarySave/posDictRoDiacrSaved.txt");
        UaicMorphologicalDictionary dic1 = new UaicMorphologicalDictionary();
        dic1.load(new FileInputStream("TestData/generated/morphologicalDictionarySave/posDictRoDiacrSaved.txt"));
        assert dic1.getTagset().size() == dictionary.getTagset().size();
        assert dic1.entrySet().size() == dictionary.entrySet().size();
        assert dic1.abbreviations.size() == dictionary.abbreviations.size();
        assert dic1.getLemmas().size() == dictionary.getLemmas().size();
        assert dic1.extra_features.size() == dictionary.extra_features.size();
    }

    @Test
    public void testConvertFromRacaiMsd() throws Exception {
        System.out.println("convertFromSimilarMsd");

        String word = "masă";
        String racaiPos = "Vmis3s";
        String result = dictionary.correctMSDTag(word, racaiPos);
        System.out.println(racaiPos + "=" + result);

        word = "și";
        racaiPos = "Crssp";
        result = dictionary.correctMSDTag(word, racaiPos);
        System.out.println(racaiPos + "=" + result);
    }
}
