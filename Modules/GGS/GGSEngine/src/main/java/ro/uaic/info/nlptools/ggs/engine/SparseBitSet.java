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

import java.util.*;

public class SparseBitSet {
    public enum SparseBitSetCompressionPolicy {none, always, auto}

    public static SparseBitSetCompressionPolicy compressionPolicy = SparseBitSetCompressionPolicy.auto;
    //SmallEndianRepresentation
    private LinkedList<Integer> runs;
    private int length;
    private int cardinality = 0;
    BitSet backingBitSet;
    public boolean locked = false;

    public SparseBitSet() {
        runs = new LinkedList<Integer>();
        compressOrDecompressIfNecessary();
    }

    private void compressOrDecompressIfNecessary() {
        switch (compressionPolicy){
            case always:
                compress();
                return;
            case none:
                decompress();
                return;
            case auto:
                if (backingBitSet != null && cardinality() < getLength() / 1024){
                    compress();
                }else if (backingBitSet == null && getLength() / 512 < cardinality()){
                    decompress();
                }
                return;
        }
    }

    private void compress() {
        if (backingBitSet != null) {
            runs = getCompressedRuns();
            length = backingBitSet.length();
            cardinality = backingBitSet.cardinality();
            backingBitSet = null;
        }
    }

    private void decompress(){
        backingBitSet = getUncompressed();
    }

    public SparseBitSet(BitSet bitSet) {
        backingBitSet = bitSet;
        compressOrDecompressIfNecessary();
    }

    private void init(List<Integer> runs) {
        this.runs = new LinkedList<Integer>(runs);
        length = 0;
        boolean s = false;
        for (int run : this.runs) {
            length += run;
            if (s)
                cardinality += run;
            s = !s;
        }
        if (this.runs.size() % 2 == 1)
            this.runs.removeLast();
    }

    public SparseBitSet(List<Integer> runs) {
        init(runs);
        compressOrDecompressIfNecessary();
    }

    public SparseBitSet clone() {
        SparseBitSet bs = new SparseBitSet();
        if (backingBitSet != null) {
            bs.backingBitSet = (BitSet) backingBitSet.clone();
            return bs;
        }

        bs.runs = new LinkedList<Integer>(runs);
        bs.cardinality = cardinality;
        bs.length = length;
        return bs;
    }

    @Override
    public String toString() {
        List<Integer> runs = this.runs;
        if (backingBitSet != null) {
            runs = getCompressedRuns();
        }

        StringBuilder sb = new StringBuilder();
        for (Integer run : runs)
            sb.append(run).append(" ");
        return sb.toString();
    }

    public void set(int pos) {
        set(pos, true);
    }

    public void set(int pos, boolean value) {
        set(pos, pos + 1, value);
    }

    private static BitSet shift(BitSet bs, int n) {
        if (n == 0)
            return bs;

        if (n < 0) {
            if (-n >= bs.length())
                return new BitSet();
            return bs.get(-n, bs.length());
        }

        BitSet ret = new BitSet(bs.length());
        if (bs.cardinality() == 0)
            return ret;
        if (bs.cardinality() == bs.length()) {
            ret.set(1, bs.cardinality() + 1);
            return ret;
        }

        for (int i = bs.nextSetBit(0); i != -1; i = bs.nextSetBit(i + 1))
            ret.set(i + n);

        return ret;
    }

    public void Shift(int offset) { // < 0 => la stanga
        if (locked)
            throw new IllegalStateException("bitset is locked");

        compressOrDecompressIfNecessary();

        if (backingBitSet != null) {
            backingBitSet = shift(backingBitSet, offset);
            return;
        }

        if (runs.size() == 0 || offset == 0)
            return;

        if (offset > 0) {
            int current0 = runs.remove();
            runs.add(0, current0 + offset);
        } else {
            boolean sign = runs.size() % 2 == 0;
            int currentOffset = offset;
            while (currentOffset < 0 && runs.size() > 0) {
                int offs = runs.remove();
                currentOffset += offs;
                sign = !sign;
                if (sign)
                    cardinality -= offs;
            }

            if (currentOffset > 0 || runs.size() > 0) {
                runs.add(0, currentOffset);
                if (sign)
                    cardinality += currentOffset;
            }
            if (sign)
                not();
        }

        length += offset;
        if (length < 0)
            length = 0;

        if (runs.size() % 2 == 1)
            not();
    }

    void Operation(SparseBitSet sb, boolOpEnum op) {
        if (locked)
            throw new IllegalStateException("bitset is locked");

        if (backingBitSet != null) {
            switch (op) {
                case And:
                    backingBitSet.and(sb.getUncompressed());
                    return;
                case AndNot:
                    backingBitSet.andNot(sb.getUncompressed());
                    return;
                case Or:
                    backingBitSet.or(sb.getUncompressed());
                    return;
                case Xor:
                    backingBitSet.xor(sb.getUncompressed());
                    return;
            }
        }

        //x = 0
        if (isEmpty())
            switch (op) {
                case Or:
                case Xor:
                    cloneFrom(sb);
                    return;
                case And:
                case AndNot:
                    return;
            }
        //y = 0
        if (sb.isEmpty())
            switch (op) {
                case And:
                    clear();
                    return;
                case Or:
                case Xor:
                case AndNot:
                    return;
            }
        //x = 1
        if (cardinality() == getLength() && getLength() >= sb.getLength())
            switch (op) {
                case Xor:
                case AndNot:
                    cloneFrom(sb);
                    not();
                    return;
                case And:
                    cloneFrom(sb);
                    return;
                case Or:
                    return;
            }
        //y = 1
        if (sb.cardinality() == sb.getLength() && sb.getLength() >= getLength())
            switch (op) {
                case Xor:
                    not();
                    return;
                case And:
                    return;
                case AndNot:
                    clear();
                    return;
                case Or:
                    cloneFrom(sb);
                    return;
            }

        Iterator<Integer> i0 = runs.iterator();
        Iterator<Integer> i1 = sb.getCompressedRuns().iterator();

        boolean rezSign = false;
        int rezOffset = 0;
        LinkedList<Integer> rez = new LinkedList<Integer>();

        boolean sign0 = true;
        int offset0 = 0;
        int prevOffset0 = 0;

        boolean sign1 = true;
        int prevOffset1 = 0;
        int offset1 = 0;

        boolean incr0 = true;
        boolean incr1 = true;

        cardinality = 0;
        length = 0;

        while (offset0 != -1 || offset1 != -1) {
            if (incr0) {
                prevOffset0 = offset0;
                if (i0.hasNext())
                    offset0 += i0.next();
                else
                    offset0 = -1;
                sign0 = !sign0;
            }

            if (incr1) {
                prevOffset1 = offset1;
                if (i1.hasNext())
                    offset1 += i1.next();
                else
                    offset1 = -1;
                sign1 = !sign1;
            }

            incr0 = (offset1 >= offset0 || offset1 == -1) && offset0 != -1;
            incr1 = (offset0 >= offset1 || offset0 == -1) && offset1 != -1;

            if (boolOp(sign0, sign1, op) != rezSign) {
                int newRezOffset = Math.max(prevOffset0, prevOffset1);
                if (newRezOffset == -1)
                    newRezOffset = offset0 + offset1 + 1;
                rez.add(newRezOffset - rezOffset);
                if (rezSign)
                    cardinality += newRezOffset - rezOffset;
                rezSign = !rezSign;
                rezOffset = newRezOffset;
            }
        }
        length = rezOffset;
        if (rez.size() % 2 == 1) {
            if (cardinality() > 0) {
                length += rez.getLast();
                rez.add(0);
            } else
                rez.clear();
        }

        if (runs.size() != rez.size() || !runs.toString().equals(rez.toString()))
            runs = rez;
    }

    private void cloneFrom(SparseBitSet bs) {
        if (bs.backingBitSet != null) {
            backingBitSet = (BitSet) bs.backingBitSet.clone();
            return;
        }

        runs = new LinkedList<Integer>(bs.runs);
        length = bs.length;
        cardinality = bs.cardinality;
    }

    public void not() {
        if (locked)
            throw new IllegalStateException("bitset is locked");
        if (backingBitSet != null){
            backingBitSet.flip(0, backingBitSet.length());
            return;
        }

        if (runs.size() > 0 && runs.getFirst() == 0)
            runs.removeFirst();
        else
            runs.addFirst(0);

        if (runs.size() % 2 == 1) {
            if (length > 0)
                runs.add(0);
            else
                runs.remove();
        }

        cardinality = length - cardinality;
    }

    public void set(int from, int to) {
        set(from, to, true);
    }

    public void set(int from, int to, boolean sign) {
        if (locked)
            throw new IllegalStateException("bitset is locked");
        if (backingBitSet != null) {
            backingBitSet.set(from, to, sign);
            return;
        }

        List<Integer> runs = new ArrayList<Integer>();
        runs.add(from);
        runs.add(to - from);

        SparseBitSet mask = new SparseBitSet(runs);
        if (sign)
            Operation(mask, boolOpEnum.Or);
        else {
            Operation(mask, boolOpEnum.AndNot);
        }
    }

    public boolean get(int pos) {
        if (backingBitSet != null)
            return backingBitSet.get(pos);

        if (pos > getLength())
            return runs.size() % 2 == 1;

        int offset = 0;
        int index = 0;
        for (int offsetBucket : runs) {
            offset += offsetBucket;
            if (offset > pos) {
                return index % 2 == 1;
            }
            index++;
        }
        return index % 2 == 1;
    }

    Iterator<Integer> nextSetBitIter;
    int lastNextSetBitIterOffset = 0;
    int nextSetBitIterOffset = 0;

    public int nextSetBit() {
        if (backingBitSet != null) {
            int rez = backingBitSet.nextSetBit(nextSetBitIterOffset);
            nextSetBitIterOffset = rez + 1;
            if (rez == -1)
                nextSetBitIterOffset = 0;
            return rez;
        }

        if (nextSetBitIter == null)
            nextSetBitIter = runs.iterator();

        if (lastNextSetBitIterOffset == 0) {
            if (!nextSetBitIter.hasNext()) {
                nextSetBitIter = null;
                nextSetBitIterOffset = 0;
                lastNextSetBitIterOffset = 0;
                return -1;
            }
            nextSetBitIterOffset += nextSetBitIter.next();

            if (!nextSetBitIter.hasNext()) {
                if (lastNextSetBitIterOffset == 0) {
                    nextSetBitIter = null;
                    nextSetBitIterOffset = 0;
                    lastNextSetBitIterOffset = 0;
                    return -1;
                }
            }
            lastNextSetBitIterOffset = nextSetBitIter.next();
        }

        if (lastNextSetBitIterOffset == 0) {
            return nextSetBit();
        }

        nextSetBitIterOffset++;
        lastNextSetBitIterOffset--;

        return nextSetBitIterOffset - 1;
    }

    public int getLength() {
        if (backingBitSet != null)
            return backingBitSet.length();
        return length;
    }

    LinkedList<Integer> getCompressedRuns() {
        if (backingBitSet == null)
            return runs;

        LinkedList<Integer> l = new LinkedList<Integer>();
        int offset = 0;
        for (int i = backingBitSet.nextSetBit(0); i != -1; i = backingBitSet.nextSetBit(i + 1)) {
            if (offset != i || i == 0) {
                l.add(i - offset);
                l.add(1);
            } else
                l.set(l.size() - 1, l.get(l.size() - 1) + 1);
            offset = i + 1;
        }
        return l;
    }

    protected BitSet getUncompressed() {
        if (backingBitSet != null)
            return backingBitSet;
        BitSet bs = new BitSet(getLength());
        boolean sign = false;
        int offset = 0;
        for (int run : runs) {
            if (sign)
                bs.set(offset, offset + run, true);
            sign = !sign;
            offset += run;
        }

        return bs;
    }

    public void xor(SparseBitSet bs) {
        Operation(bs, boolOpEnum.Xor);
    }

    public void and(SparseBitSet bs) {
        Operation(bs, boolOpEnum.And);
    }

    public void andNot(SparseBitSet bs) {
        Operation(bs, boolOpEnum.AndNot);
    }

    public void or(SparseBitSet bs) {
        Operation(bs, boolOpEnum.Or);
    }

    public int cardinality() {
        if (backingBitSet != null)
            return backingBitSet.cardinality();
        return cardinality;
    }

    public void clear() {
        if (locked)
            throw new IllegalStateException("bitset is locked");
        if (backingBitSet != null)
            backingBitSet.clear();
        runs.clear();
        length = 0;
        cardinality = 0;
    }

    public boolean isEmpty() {
        if (backingBitSet != null)
            return backingBitSet.isEmpty();
        return cardinality() == 0;
    }

    enum boolOpEnum {And, Or, Xor, AndNot}

    private static boolean boolOp(boolean b1, boolean b2, boolOpEnum op) {
        switch (op) {
            case And:
                return b1 && b2;
            case Or:
                return b1 || b2;
            case Xor:
                return b1 ^ b2;
            case AndNot:
                return b1 && !b2;
        }
        return b1;
    }

    public boolean equals(SparseBitSet bs) {
        if (this == bs)
            return true;
        if (backingBitSet != null)
            return backingBitSet.equals(bs.getUncompressed());

        Iterator<Integer> i1 = bs.getCompressedRuns().iterator();

        if (runs.size() != bs.runs.size())
            return false;

        if (getLength() != bs.getLength())
            return false;

        Iterator<Integer> i0 = runs.iterator();


        while (i0.hasNext()) {
            if (i0.next() != i1.next())
                return false;
        }

        return true;
    }
}