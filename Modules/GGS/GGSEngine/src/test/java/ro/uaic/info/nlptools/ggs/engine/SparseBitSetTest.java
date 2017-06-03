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

package ro.uaic.info.nlptools.ggs.engine;

import org.testng.annotations.BeforeTest;
import org.testng.annotations.Test;

import java.util.Arrays;

public class SparseBitSetTest {

    @BeforeTest
    public void init() {
        SparseBitSet.compressionPolicy = SparseBitSet.SparseBitSetCompressionPolicy.none;
    }

    @Test
    public void testGetSetBitsCount() throws Exception {
        //TODO implement test
    }

    @Test
    public void testSetBit() throws Exception {
        //TODO implement test
    }

    @Test
    public void testShift() throws Exception {
        SparseBitSet bs;
        bs = new SparseBitSet();
        bs.Shift(-5);
        assert bs.toString().equals("");
        bs.not();
        bs.Shift(-5);
        assert bs.toString().equals("");
        bs.Shift(5);
        assert bs.toString().equals("");

        bs = new SparseBitSet(Arrays.asList(0, 1, 3, 1, 3, 3, 5, 2));
        bs.Shift(4);
        assert bs.toString().equals("4 1 3 1 3 3 5 2 ");
        bs.Shift(-2);
        assert bs.toString().equals("2 1 3 1 3 3 5 2 ");
        bs.Shift(-2);
        assert bs.toString().equals("0 1 3 1 3 3 5 2 ");
        bs.Shift(-9);
        assert bs.toString().equals("0 2 5 2 ");
        bs.Shift(10);
        assert bs.toString().equals("10 2 5 2 ");
        bs.Shift(-18);
        assert bs.toString().equals("0 1 ");

        bs = new SparseBitSet(Arrays.asList(0, 1, 3, 1, 3, 3, 5, 2));
        bs.Shift(-17);
        assert bs.toString().equals("0 1 ");

        bs = new SparseBitSet(Arrays.asList(0, 3, 2));
        bs.Shift(-2);
        assert bs.toString().equals("0 1 ");

        bs = new SparseBitSet(Arrays.asList(0, 3, 2, 4));
        bs.Shift(-3);
        assert bs.toString().equals("2 4 ");
    }

    @Test
    public void testSet() throws Exception {
        SparseBitSet bs;

        bs = new SparseBitSet();
        bs.Operation(new SparseBitSet(), SparseBitSet.boolOpEnum.Or);
        assert bs.toString().isEmpty();
        bs.Operation(new SparseBitSet(), SparseBitSet.boolOpEnum.And);
        assert bs.toString().isEmpty();
        bs.Operation(new SparseBitSet(Arrays.asList(0)), SparseBitSet.boolOpEnum.Or);
        assert bs.toString().isEmpty();
        bs.Operation(new SparseBitSet(Arrays.asList(0)), SparseBitSet.boolOpEnum.Xor);
        assert bs.toString().isEmpty();

        bs = new SparseBitSet();
        bs.set(1, 3, false);
        assert bs.toString().isEmpty();

        bs.set(1, 3, true);
        assert bs.toString().equals("1 2 ");

        bs = new SparseBitSet(Arrays.asList(0, 1, 3, 1, 3, 3, 5, 2));
        bs.set(11, 13, false);
        assert bs.toString().equals("0 1 3 1 3 3 5 2 ");


        bs = new SparseBitSet(Arrays.asList(0, 1, 3, 1, 3, 3, 5, 2));
        bs.set(4, false);
        assert bs.toString().equals("0 1 7 3 5 2 ");

        bs = new SparseBitSet(Arrays.asList(0, 1, 3, 1, 3, 3, 5, 2));
        bs.set(1, 4, true);
        assert bs.toString().equals("0 5 3 3 5 2 ");

        bs = new SparseBitSet(Arrays.asList(0, 1, 3, 1, 3, 3, 5, 2));
        bs.set(3, 6, false);
        assert bs.toString().equals("0 1 7 3 5 2 ");

        bs = new SparseBitSet(Arrays.asList(0, 1, 3, 1, 3, 3, 5, 2));
        bs.set(2, 6, true);
        assert bs.toString().equals("0 1 1 4 2 3 5 2 ");

        bs = new SparseBitSet(Arrays.asList(0, 1, 3, 1, 3, 3, 5, 2));
        bs.set(4, 9, false);
        assert bs.toString().equals("0 1 8 2 5 2 ");

        bs = new SparseBitSet(Arrays.asList(0, 1, 3, 1, 3, 3, 5, 2));
        bs.set(0, 6, false);
        assert bs.toString().equals("8 3 5 2 ");

        bs = new SparseBitSet(Arrays.asList(8, 3, 5, 2));
        bs.set(0, 2, true);
        assert bs.toString().equals("0 2 6 3 5 2 ");

        bs = new SparseBitSet(Arrays.asList(9,1));
        bs.set(4,7, true);
        assert bs.toString().equals("4 3 2 1 ");
    }

    @Test
    public void testGet() throws Exception {
        SparseBitSet bs = new SparseBitSet(Arrays.asList(0, 1, 3, 1, 3, 3, 5, 2));
        assert bs.get(0);
        assert !bs.get(1);
        assert !bs.get(12);
        assert bs.get(16);
        assert !bs.get(15);
        assert !bs.get(15);
        assert bs.get(4);
        assert !bs.get(5);

        bs = new SparseBitSet(Arrays.asList(1, 3, 1, 3, 3, 5, 2));
        assert !bs.get(0);
        assert bs.get(1);
        assert bs.get(12);
        assert !bs.get(16);
        assert bs.get(15);
        assert bs.get(15);
        assert !bs.get(4);
        assert bs.get(5);
    }

    @Test
    public void testGetLastBitSwapOffset() throws Exception {
        //TODO implement
    }
}