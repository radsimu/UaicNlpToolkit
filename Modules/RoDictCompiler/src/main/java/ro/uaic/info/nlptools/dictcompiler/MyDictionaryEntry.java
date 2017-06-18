package ro.uaic.info.nlptools.dictcompiler;

import ro.uaic.info.nlptools.tools.UaicMorphologicalAnnotation;

public class MyDictionaryEntry extends UaicMorphologicalAnnotation {

    public MyDictionaryEntry(String word, String msd, String lemma, String extra){
        super(word,msd,lemma, extra);
    }
    
    public MyDictionaryEntry(String str) {
        int aux = str.indexOf("#");
        if (aux >= 0) {
            str = str.substring(0, aux).trim();
        }

        String[] feats = str.split("\t");

        for (int i = 0; i < feats.length; i++) {
            feats[i] = feats[i].trim();
            assert !feats[i].isEmpty();
            if (i == 0) {
                setWord(feats[i]);
                continue;
            }

            aux = feats[i].indexOf("=");

            if (aux > 0) {
                String name = feats[i].substring(0, aux).trim();
                String value = feats[i].substring(aux + 1).trim();
                assert !value.isEmpty();
                if (name.equals("LEMMA")) {
                    setLemma (value.trim());
                } else if (name.equals("MSD")) {
                    setMsd(value.trim());
                }
            } else {
                if (!feats[i].trim().isEmpty()) {
                    setExtra(feats[i].trim());
                }
            }
        }
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(getWord());
        sb.append("\tLEMMA=").append(getLemma());
        sb.append("\tMSD=").append(getMsd());
        if (getExtra() != null) {
            sb.append("\t").append(getExtra());
        }
        return sb.toString();
    }
}
