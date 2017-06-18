package ro.uaic.info.nlptools.dictcompiler;

import java.io.BufferedWriter;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.UnsupportedEncodingException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PrecompileFromDexOnlineDB {

    public static String nounsComFile = "inflNounsCom.txt";
    public static String nounsInvarFile = "invarNounsCom.txt";
    public static String nounsPerFile = "inflNounsPer.txt";
    public static String adjsFile = "inflAdjs.txt";
    public static String adjsInvarFile = "invarAdjs.txt";
    public static String verbsFile = "inflVerbs.txt";
    public static String auxverbsFile = "inflAuxverbs.txt";
    public static String pronDemFile = "inflPronDems.txt";
    public static String pronIndefFile = "inflPronIndef.txt";
    public static String pronPersFile = "inflPronPers.txt";
    public static String pronNegFile = "inflPronNeg.txt";
    public static String pronPosFile = "inflPronPos.txt";
    public static String pronReflFile = "inflPronRefl.txt";
    public static String pronRelFile = "inflPronRel.txt";
    public static String pronIntFile = "inflPronInt.txt";
    public static String numOrdFile = "inflNumOrd.txt";
    public static String numColFile = "inflNumCol.txt";
    public static String numCardFile = "inflNumCard.txt";
    public static String numFracFile = "inflNumFrac.txt";
    public static String detDemFile = "inflDetDems.txt";
    public static String detIndefFile = "inflDetIndef.txt";
    public static String detNegFile = "inflDetNeg.txt";
    public static String detPosFile = "inflDetPos.txt";
    public static String detRelFile = "inflDetRel.txt";
    public static String detIntFile = "inflDetInt.txt";
    public static String articlesFile = "inflArticles.txt";
    public static String conjsFile = "invarConjs.txt";
    public static String advsFile = "invarAdvs.txt";
    public static String prepsFile = "invarPreps.txt";
    public static String interjFile = "invarInterjs.txt";
    public static String lexemPriorityFile = "lexemPriority.total";

    public static void main(String[] args) throws FileNotFoundException, IOException, ClassNotFoundException, SQLException {
        Class.forName("com.mysql.jdbc.Driver");

        Connection con = DriverManager.getConnection("jdbc:mysql://localhost/dexonline?useUnicode=true&characterEncoding=UTF-8", "dexro", "dexro");

        System.out.println("uncomment lines in main!");

        updateVerbs(con);
        updateNounsCommon(con);
        updateNounsPers(con);
        updateAdjs(con);
        updateAdjsInvar(con);
        updateAdvs(con);
        updatePreps(con);
        updateDemPronouns(con);
        updateIndefPronouns(con);
        updatePersPronouns(con);
        updateNegPronouns(con);
        updatePosPronouns(con);
        updateReflPronouns(con);
        updateRelPronouns(con);
        updateDemDets(con);
        updateArticles(con);
        updateConjs(con);
        updateInterjs(con);
        updateNumOrd(con);
        updateNumCard(con);
        updateNumCol(con);
        updateLexemPriority(con);
    }
    static String lastFemLemma;

    public static void updateNounsCommon(Connection con) throws SQLException, IOException {
        MyDictionary dictionary = new MyDictionary();
        BufferedWriter out = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(MyDictionary.folder + "grabedForUse/" + nounsComFile), "UTF8"));
        Statement stmt = con.createStatement();

        //multe substantive sunt reprezentate ca adjective, atunci cand pt ele exista versiuni in ambele genuri.exemplu "adept".
        //multe adjective pot fi substantivizate cu usurinta, altele nu :exemplu "prost" vs "albastru"
        //dar toate aceste cazuri sunt markate in db ca adj. Putem afla daca un adj este si subst daca procesam putin definitia sa.
        Set<String> nounEntries = new HashSet<String>();
        ResultSet rs = stmt.executeQuery("SELECT lexem.formUtf8General FROM lexemdefinitionmap \n"
                + "join lexem on lexem.id = lexemId \n"
                + "join definition on definitionId = definition.id \n"
                + "where htmlRep REGEXP 'title=\"substantiv'");
        while (rs.next()) {
            nounEntries.add(rs.getString(1));
        }

        //find words stored as adjectives which are actually nouns which have versions for each gender. ex: abonat, acordant, acționar, secretar
        //for these words, when processing them as nouns, the lemma of an inflection must agree in gender with the inflected form. By default, adjectives associate all flections with the singular masculine lemma
        //and that is ok for adjectives, but not for nouns. 
        //the db already marks such noun entries as having the MF modelType but there are many examples which are not marked correctly. To determine if an entry is MF we look in the deffinition of the lexem, for a particular char sequence
        Set<String> mfEntries = new HashSet<String>();
        rs = stmt.executeQuery("SELECT lexem.formUtf8General FROM lexemdefinitionmap\n"
                + "join lexem on lexem.id = lexemId\n"
                + "join definition on definitionId = definition.id\n"
                + "where internalRep REGEXP '#s\\\\.[[:space:]]+m\\\\.[[:space:]]*#?[[:space:]]+și[[:space:]]+#f\\\\.'");
        while (rs.next()) {
            mfEntries.add(rs.getString(1));
        }

        long k = 0;
        rs = stmt.executeQuery("SELECT lexem.formUtf8General AS lemma, \n"
                + "lexemmodel.modelType AS POS, \n"
                + "inflectedform.formUtf8General AS inflForm, \n"
                + "inflection.description AS inflPOS, \n"
                + "inflectedform.inflectionId AS inflId \n"
                + "FROM lexem \n"
                + "JOIN lexemmodel ON lexemmodel.lexemid = lexem.id \n"
                + "JOIN inflectedform ON inflectedform.lexemmodelId = lexemmodel.id \n"
                + "LEFT JOIN inflection ON inflection.id = inflectedform.inflectionId \n"
                + "WHERE \n"
                + "lexemmodel.modelType IN ('M', 'F', 'N', 'MF', 'A') \n"
                + "ORDER BY lexem.formUtf8General, inflectedform.inflectionId");
        while (rs.next()) {
            if (!rs.getString("inflForm").toLowerCase().equals(rs.getString("inflForm"))) {
                continue;
            }

            MyDictionaryEntry entry = new MyDictionaryEntry(rs.getString("inflForm"), null, null, null);
            entry.setLemma(rs.getString("lemma"));
            int inflId = rs.getInt("inflId");
            if (inflId == 33) {//singular feminine adj direct 
                lastFemLemma = entry.getWord();
            }
            if (rs.getString("POS").equals("A") && !nounEntries.contains(entry.getLemma())) {
                continue; //pare a fi un adjectiv care nu poate fi substantivizat
            }

            if (mfEntries.contains(entry.getLemma()) && ((inflId >= 33 && inflId <= 40) || inflId >= 95)) {//for feminine adj set a feminine lemma
                entry.setLemma(lastFemLemma);
            }
            //some nouns are stored as adjectives because they have both gender versions... "adept" for instance
            //and some nouns can be nominalized.
            //for this reason we add a noun version for such adjective

            if (inflId >= 25 && inflId < 41) {//this is represented as an adjective, convert it to the corresponding noun inflId
                inflId -= 24;
            }
            if (inflId >= 93)//adjective vocative
            {
                inflId -= 6;
            }

            preprocessEntry(entry, inflId);
            if (!dictionary.contains(entry)) {
                out.write(entry.toString() + "\n");
                dictionary.Add(entry);
                k++;
            }
        }
        rs.close();

        out = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(MyDictionary.folder + "grabedForUse/" + nounsInvarFile), "UTF8"));
        //there are some more nouns which are invariable and are processed below
        rs = stmt.executeQuery(
                "SELECT inflectedform.formUtf8General AS inflForm, \n"
                + "lexem.formUtf8General AS lemma, \n"
                + "inflection.description AS inflPOS, \n"
                + "inflectedform.inflectionId AS inflId \n"
                + "FROM lexem \n"
                + "JOIN lexemmodel ON lexemmodel.lexemId = lexem.id \n"
                + "JOIN inflectedform ON inflectedform.lexemmodelId = lexemmodel.id \n"
                + "JOIN lexemdefinitionmap on lexemdefinitionmap.lexemId=lexem.Id \n"
                + "JOIN definition on definition.id = lexemdefinitionmap.definitionId \n"
                + "LEFT JOIN modeltype ON modeltype.code = lexemmodel.modelType \n"
                + "LEFT JOIN inflection ON inflection.id = inflectedform.inflectionId \n"
                + "WHERE lexemmodel.modelType IN ('I') and definition.internalRep LIKE '%#s. m.%' \n"
                + "ORDER BY lexem.formUtf8General");

        while (rs.next()) {
            if (!rs.getString("inflForm").toLowerCase().equals(rs.getString("inflForm"))) {
                continue;
            }
            MyDictionaryEntry entry = new MyDictionaryEntry(rs.getString("lemma"), null, null, null);
            entry.setLemma(rs.getString("lemma"));
            entry.setMsd("Ncmsrn");
            preprocessEntry(entry, 84);

            if (dictionary.get(entry.getWord()) == null) {
                out.write(entry.toString() + "\n");
                dictionary.Add(entry);
                k++;
            }
        }

        rs = stmt.executeQuery(
                "SELECT inflectedform.formUtf8General AS inflForm, \n"
                + "lexem.formUtf8General AS lemma, \n"
                + "inflection.description AS inflPOS, \n"
                + "inflectedform.inflectionId AS inflId \n"
                + "FROM lexem \n"
                + "JOIN lexemmodel ON lexem.Id = lexemmodel.lexemid \n"
                + "JOIN inflectedform ON inflectedform.lexemmodelId = lexemmodel.id \n"
                + "JOIN lexemdefinitionmap on lexemdefinitionmap.lexemId=lexem.Id \n"
                + "JOIN definition on definition.id = lexemdefinitionmap.definitionId \n"
                + "LEFT JOIN modeltype ON modeltype.code = lexemmodel.modelType \n"
                + "LEFT JOIN inflection ON inflection.id = inflectedform.inflectionId \n"
                + "WHERE lexemmodel.modelType IN ('I') and definition.internalRep LIKE '%#s. f.%' \n"
                + "ORDER BY lexem.formUtf8General");

        while (rs.next()) {
            if (!rs.getString("inflForm").toLowerCase().equals(rs.getString("inflForm"))) {
                continue;
            }
            MyDictionaryEntry entry = new MyDictionaryEntry(
                    rs.getString("lemma"),
                    "Ncfsrn",
                    rs.getString("lemma"),
                    null
            );
            preprocessEntry(entry, 84);

            if (dictionary.get(entry.getWord()) == null) {
                out.write(entry.toString() + "\n");
                dictionary.Add(entry);
                k++;
            }
        }

        stmt.close();
        rs.close();
        out.close();
        System.out.println("Finished common nouns (" + k + " added)");
    }

    public static void updateNounsPers(Connection con) throws SQLException, IOException {
        MyDictionary dictionary = new MyDictionary();
        BufferedWriter out = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(MyDictionary.folder + "grabedForUse/" + nounsPerFile), "UTF8"));
        Statement stmt = con.createStatement();
        long k = 0;
        ResultSet rs = stmt.executeQuery("SELECT lexem.formUtf8General AS lemma, \n"
                + "modeltype.description AS POS, \n"
                + "inflectedform.formUtf8General AS inflForm, \n"
                + "inflection.description AS inflPOS, \n"
                + "inflectedform.inflectionId AS inflId \n"
                + "FROM lexem \n"
                + "JOIN lexemmodel ON lexemmodel.lexemId = lexem.id \n"
                + "JOIN inflectedform ON inflectedform.lexemmodelId = lexemmodel.id \n"
                + "LEFT JOIN modeltype ON modeltype.code = lexemmodel.modelType \n"
                + "LEFT JOIN inflection ON inflection.id = inflectedform.inflectionId \n"
                + "WHERE lexemmodel.modelType \n"
                + "IN ('M', 'F', 'N', 'MF', 'A') \n"
                + "ORDER BY lexem.formUtf8General, inflectedform.inflectionId");
        while (rs.next()) {
            if (rs.getString("inflForm").toLowerCase().equals(rs.getString("inflForm"))) {
                continue;
            }

            MyDictionaryEntry entry = new MyDictionaryEntry(rs.getString("inflForm"), null, null, null);
            entry.setLemma(rs.getString("lemma"));
            int inflId = rs.getInt("inflId");
            if (inflId == 33) {
                lastFemLemma = entry.getWord();
            }
            if (inflId >= 33) {
                entry.setLemma(lastFemLemma);
            }
            if (inflId >= 25) {
                inflId -= 24;
            }
            preprocessEntry(entry, inflId);
            entry.setMsd("Np" + entry.getMsd().substring(2));

            if (!dictionary.contains(entry)) {
                out.write(entry.toString() + "\n");
                dictionary.Add(entry);
                k++;
            }
        }
        stmt.close();
        rs.close();
        out.close();
        System.out.println("Finished propper nouns (" + k + " added)");
    }

    public static void updateAdjs(Connection con) throws SQLException, IOException {
        MyDictionary dictionary = new MyDictionary();
        BufferedWriter out = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(MyDictionary.folder + "grabedForUse/" + adjsFile), "UTF8"));
        Statement stmt = con.createStatement();
        long k = 0;

        Set<String> adjEntries = new HashSet<String>();
        ResultSet rs = stmt.executeQuery("SELECT lexem.formUtf8General "
                + "FROM lexemdefinitionmap\n"
                + "join lexem on lexem.id = lexemId \n"
                + "join definition on definitionId = definition.id\n"
                + "where htmlRep REGEXP 'title=\"adjectiv'");
        while (rs.next()) {
            adjEntries.add(rs.getString(1));
        }

        rs = stmt.executeQuery("SELECT \n"
                + "        lexem.formUtf8General as lemma, \n"
                + "        lexemmodel.modelType as POS, \n"
                + "        inflectedform.formUtf8General as inflForm, \n"
                + "        inflectedform.inflectionId as inflId \n"
                + "FROM lexem \n"
                + "JOIN lexemmodel on lexemmodel.lexemid = lexem.id \n"
                + "LEFT JOIN inflectedform on inflectedform.lexemmodelId = lexemmodel.id \n"
                + "where lexemmodel.modelType in ('A', 'MF') \n"
                + "ORDER BY lexem.formUtf8General");
        while (rs.next()) {
            MyDictionaryEntry entry = new MyDictionaryEntry(rs.getString("inflForm"), null, null, null);
            entry.setLemma(rs.getString("lemma"));
            if (rs.getString("POS").equals("MF") && !adjEntries.contains(entry.getLemma())) {
                continue;
            }

            preprocessEntry(entry, rs.getInt("inflId"));

            if (!dictionary.contains(entry)) {
                out.write(entry.toString() + "\n");
                dictionary.Add(entry);
                k++;
            }
        }

        stmt.close();
        rs.close();
        out.close();
        System.out.println("Finished adjectives (" + k + " added)");
    }

    public static void updateAdjsInvar(Connection con) throws SQLException, IOException {
        MyDictionary dictionary = new MyDictionary();
        BufferedWriter out = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(MyDictionary.folder + "grabedForUse/" + adjsInvarFile), "UTF8"));
        Statement stmt = con.createStatement();
        long k = 0;

        ResultSet rs = stmt.executeQuery("SELECT \n"
                + "		lexem.formUtf8General as lemma,\n"
                + "             definition.internalRep as definition\n"
                + "from lexem \n"
                + "JOIN lexemdefinitionmap on lexemdefinitionmap.lexemId=lexem.Id \n"
                + "JOIN definition on definition.id = lexemdefinitionmap.definitionId \n"
                + "where definition.internalRep REGEXP '#adj\\..?.?.? #invar\\.' \n"
                + "ORDER BY lexem.formUtf8General");

        while (rs.next()) {
            MyDictionaryEntry entry = new MyDictionaryEntry(rs.getString("lemma"), null, null, null);
            entry.setLemma(rs.getString("lemma"));
            entry.setMsd("Afp");

            preprocessEntry(entry, 84);

            if (!dictionary.contains(entry)) {
                out.write(entry.toString() + "\n");
                dictionary.Add(entry);
                k++;
            }
        }
        rs.close();

        stmt.close();
        out.close();
        System.out.println("Finished invariable adjectives (" + k + " added)");
    }

    public static void updateVerbs(Connection con) throws SQLException, IOException {
        MyDictionary dictionary = new MyDictionary();
        BufferedWriter out = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(MyDictionary.folder + "grabedForUse/" + verbsFile), "UTF8"));
        Statement stmt = con.createStatement();
        long k = 0;
        ResultSet rs = stmt.executeQuery("SELECT \n"
                + "        lexem.formUtf8General as lemma, \n"
                + "        modeltype.description as POS, \n"
                + "        inflectedform.formUtf8General as inflForm, \n"
                + "        inflection.description as inflPOS, \n"
                + "        inflectedform.inflectionId as inflId \n"
                + "FROM lexem \n"
                + "LEFT JOIN lexemmodel on lexemmodel.lexemId = lexem.id \n"
                + "LEFT JOIN inflectedform on inflectedform.lexemModelId = LexemModel.id \n"
                + "LEFT JOIN modeltype on modeltype.code = lexemmodel.modelType \n"
                + "LEFT JOIN inflection on inflection.id = inflectedform.inflectionId \n"
                + "where lexemmodel.modelType in ('V', 'VT') \n"
                + "ORDER BY lexem.formUtf8General");
        while (rs.next()) {
            if (rs.getInt("inflId") == 50) {
                continue;
            }
            MyDictionaryEntry entry = new MyDictionaryEntry(rs.getString("inflForm"), null, null, null);
            entry.setLemma(rs.getString("lemma"));
            preprocessEntry(entry, rs.getInt("inflId"));
            if (entry.getMsd() == null) {
                continue;
            }

            if (rs.getString("POS").toLowerCase().equals("verb tranzitiv")) {
                entry.setExtra("tranzitiv");
            } else {
                entry.setExtra("intranzitiv");
            }

            if ((entry.getLemma().equals("avea") || entry.getLemma().equals("vrea")) && rs.getString("POS").equals("Verb")) {
                continue; // astea intra doar la verbe auxiliare
                //aici intra doar ca verbe tranzitive
            }
            if (!dictionary.contains(entry)) {
                out.write(entry.toString() + "\n");
                dictionary.Add(entry);
                k++;
            }

            if (entry.getMsd().startsWith("Vmip2p")) {
                MyDictionaryEntry e = new MyDictionaryEntry(entry.getWord(), null, null, null);
                e.setLemma(entry.getLemma());
                e.setMsd("Vmmp2p");
                e.setExtra(entry.getExtra());
                out.write(e.toString() + "\n");
                dictionary.Add(e);
                k++;
            }
        }
        stmt.close();
        rs.close();
        out.close();
        System.out.println("Finished verbs (" + k + " added)");
    }

    public static void updateAdvs(Connection con) throws SQLException, IOException {
        MyDictionary dictionary = new MyDictionary();
        Statement stmt = con.createStatement();
        long k = 0;
        ResultSet rs = stmt.executeQuery("SELECT \n"
                + "		lexem.formUtf8General as lemma,\n"
                + "             definition.internalRep as definition\n"
                + "from lexem \n"
                + "JOIN lexemdefinitionmap on lexemdefinitionmap.lexemId=lexem.Id \n"
                + "JOIN definition on definition.id = lexemdefinitionmap.definitionId \n"
                + "where definition.internalRep LIKE '%#adv.%' or definition.internalRep LIKE '%Adverbial%' \n"
                + "ORDER BY lexem.formUtf8General");

        BufferedWriter out = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(MyDictionary.folder + "grabedForUse/" + advsFile), "UTF8"));
        Pattern pat = Pattern.compile("\\$([^ ]*),\\$ #adv\\.");
        while (rs.next()) {
            String l = rs.getString("lemma");
            String def = rs.getString("definition");
            Matcher mat = pat.matcher(def);
            if (mat.find()) {
                l = mat.group(1).toLowerCase();
            }

            MyDictionaryEntry entry = new MyDictionaryEntry(l, null, null, null);
            entry.setLemma(l);
            entry.setMsd("Rg");

            preprocessEntry(entry, 84);

            if (!dictionary.contains(entry)) {
                out.write(entry.toString() + "\n");
                dictionary.Add(entry);
                k++;
            }
        }
        stmt.close();
        rs.close();
        out.close();
        System.out.println("Finished adverbs (" + k + " added)");
    }

    public static void updatePreps(Connection con) throws SQLException, IOException {
        MyDictionary dictionary = new MyDictionary();
        Statement stmt = con.createStatement();
        long k = 0;
        ResultSet rs = stmt.executeQuery("SELECT \n"
                + "		lexem.formUtf8General as lemma,\n"
                + "             definition.internalRep as definition\n"
                + "from lexem \n"
                + "JOIN lexemdefinitionmap on lexemdefinitionmap.lexemId=lexem.Id \n"
                + "JOIN definition on definition.id = lexemdefinitionmap.definitionId \n"
                + "where definition.internalRep LIKE '%#prep.%' \n"
                + "ORDER BY lexem.formUtf8General");

        BufferedWriter out = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(MyDictionary.folder + "grabedForUse/" + prepsFile), "UTF8"));

        while (rs.next()) {
            MyDictionaryEntry entry = new MyDictionaryEntry(rs.getString("lemma"), null, null, null);
            entry.setLemma(rs.getString("lemma"));
            entry.setMsd("Sp");
            preprocessEntry(entry, 84);
            if (!dictionary.contains(entry)) {
                out.write(entry.toString() + "\n");
                dictionary.Add(entry);
                k++;
            }
        }
        stmt.close();
        rs.close();
        out.close();
        System.out.println("Finished prepositions (" + k + " added)");
    }

    public static void updateConjs(Connection con) throws SQLException, IOException {
        MyDictionary dictionary = new MyDictionary();
        Statement stmt = con.createStatement();
        long k = 0;
        ResultSet rs = stmt.executeQuery("SELECT \n"
                + "		lexem.formUtf8General as lemma,\n"
                + "             definition.internalRep as definition\n"
                + "from lexem \n"
                + "JOIN lexemdefinitionmap on lexemdefinitionmap.lexemId=lexem.Id \n"
                + "JOIN definition on definition.id = lexemdefinitionmap.definitionId \n"
                + "where definition.internalRep LIKE '%#conj.%' \n"
                + "ORDER BY lexem.formUtf8General");

        BufferedWriter out = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(MyDictionary.folder + "grabedForUse/" + conjsFile), "UTF8"));

        while (rs.next()) {
            MyDictionaryEntry entry = new MyDictionaryEntry(rs.getString("lemma"), null, null, null);
            entry.setLemma(rs.getString("lemma"));
            entry.setMsd("Cs");
            preprocessEntry(entry, 84);
            if (!dictionary.contains(entry)) {
                out.write(entry.toString() + "\n");
                dictionary.Add(entry);
                k++;
            }
        }
        stmt.close();
        rs.close();
        out.close();
        System.out.println("Finished conjunctions (" + k + " added)");
    }

    public static void updateInterjs(Connection con) throws SQLException, IOException {
        MyDictionary dictionary = new MyDictionary();
        Statement stmt = con.createStatement();
        long k = 0;
        ResultSet rs = stmt.executeQuery("SELECT \n"
                + "		lexem.formUtf8General as lemma,\n"
                + "             definition.internalRep as definition\n"
                + "from lexem \n"
                + "JOIN lexemdefinitionmap on lexemdefinitionmap.lexemId=lexem.Id \n"
                + "JOIN definition on definition.id = lexemdefinitionmap.definitionId \n"
                + "where definition.internalRep LIKE '%#interj.%' \n"
                + "ORDER BY lexem.formUtf8General");

        BufferedWriter out = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(MyDictionary.folder + "grabedForUse/" + interjFile), "UTF8"));

        while (rs.next()) {
            MyDictionaryEntry entry = new MyDictionaryEntry(rs.getString("lemma"), null, null, null);
            entry.setLemma(rs.getString("lemma"));
            entry.setMsd("I");
            preprocessEntry(entry, 84);
            if (!dictionary.contains(entry)) {
                out.write(entry.toString() + "\n");
                dictionary.Add(entry);
                k++;
            }
        }
        stmt.close();
        rs.close();
        out.close();
        System.out.println("Finished interjection (" + k + " added)");
    }

    public static void updateDemPronouns(Connection con) throws SQLException, IOException {
        MyDictionary dictionary = new MyDictionary();
        Statement stmt = con.createStatement();
        long k = 0;
        ResultSet rs = stmt.executeQuery("SELECT \n"
                + "             inflectedform.formUtf8General as inflForm, \n"
                + "             inflectedform.inflectionId as inflId, \n"
                + "		lexem.formUtf8General as lemma,\n"
                + "             definition.internalRep as definition\n"
                + "FROM inflectedform \n"
                + "JOIN lexemmodel on inflectedform.lexemmodelId=lexemmodel.id \n"
                + "JOIN lexem on lexemmodel.lexemId=lexem.id \n"
                + "JOIN lexemdefinitionmap on lexemdefinitionmap.lexemId=lexem.Id \n"
                + "JOIN definition on definition.id = lexemdefinitionmap.definitionId \n"
                + "WHERE (inflectedform.inflectionId=84 || (inflectedform.inflectionId>40 && inflectedform.inflectionId<49)) AND definition.internalRep LIKE '% #pron\\. dem\\.%' \n"
                + "ORDER BY inflectedform.formUtf8General");

        BufferedWriter out = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(MyDictionary.folder + "grabedForUse/" + pronDemFile), "UTF8"));
        while (rs.next()) {
            MyDictionaryEntry entry = new MyDictionaryEntry(rs.getString("inflForm"), null, null, null);
            entry.setLemma(rs.getString("lemma"));
            entry.setMsd("Pd");
            preprocessEntry(entry, rs.getInt("inflId"));

            if (!dictionary.contains(entry)) {
                out.write(entry.toString() + "\n");
                dictionary.Add(entry);
                k++;
            }
        }
        stmt.close();
        rs.close();
        out.close();
        System.out.println("Finished demonstrative pronouns (" + k + " added)");
    }

    public static void updateDemDets(Connection con) throws SQLException, IOException {
        MyDictionary dictionary = new MyDictionary();
        Statement stmt = con.createStatement();
        long k = 0;
        ResultSet rs = stmt.executeQuery("SELECT \n"
                + "             inflectedform.formUtf8General as inflForm, \n"
                + "             inflectedform.inflectionId as inflId, \n"
                + "		lexem.formUtf8General as lemma,\n"
                + "             definition.internalRep as definition\n"
                + "FROM inflectedform \n"
                + "JOIN lexemmodel on inflectedform.lexemmodelId=lexemmodel.id \n"
                + "JOIN lexem on lexemmodel.lexemId=lexem.id \n"
                + "JOIN lexemdefinitionmap on lexemdefinitionmap.lexemId=lexem.Id \n"
                + "JOIN definition on definition.id = lexemdefinitionmap.definitionId \n"
                + "WHERE (inflectedform.inflectionId=84 || (inflectedform.inflectionId>40 && inflectedform.inflectionId<49)) AND definition.internalRep LIKE '% #adj\\. dem\\.%' \n"
                + "ORDER BY inflectedform.formUtf8General");

        BufferedWriter out = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(MyDictionary.folder + "grabedForUse/" + detDemFile), "UTF8"));
        while (rs.next()) {
            MyDictionaryEntry entry = new MyDictionaryEntry(rs.getString("inflForm"), null, null, null);
            entry.setLemma(rs.getString("lemma"));
            entry.setMsd("Dd");
            preprocessEntry(entry, rs.getInt("inflId"));

            if (!dictionary.contains(entry)) {
                out.write(entry.toString() + "\n");
                dictionary.Add(entry);
                k++;
            }
        }
        stmt.close();
        rs.close();
        out.close();
        System.out.println("Finished demonstrative determiners (" + k + " added)");
    }

    public static void updateArticles(Connection con) throws SQLException, IOException {
        MyDictionary dictionary = new MyDictionary();
        Statement stmt = con.createStatement();
        long k = 0;
        ResultSet rs = stmt.executeQuery("SELECT \n"
                + "             inflectedform.formUtf8General as inflForm, \n"
                + "             inflectedform.inflectionId as inflId, \n"
                + "		lexem.formUtf8General as lemma,\n"
                + "             definition.internalRep as definition\n"
                + "FROM inflectedform \n"
                + "JOIN lexemmodel on inflectedform.lexemmodelId=lexemmodel.id \n"
                + "JOIN lexem on lexemmodel.lexemId=lexem.id \n"
                + "JOIN lexemdefinitionmap on lexemdefinitionmap.lexemId=lexem.Id \n"
                + "JOIN definition on definition.id = lexemdefinitionmap.definitionId \n"
                + "WHERE (inflectedform.inflectionId=84 || (inflectedform.inflectionId>40 && inflectedform.inflectionId<49)) AND definition.internalRep LIKE '% #art\\.%' \n"
                + "ORDER BY inflectedform.formUtf8General");

        BufferedWriter out = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(MyDictionary.folder + "grabedForUse/" + articlesFile), "UTF8"));
        while (rs.next()) {
            MyDictionaryEntry entry = new MyDictionaryEntry(rs.getString("inflForm"), null, null, null);
            entry.setLemma(rs.getString("lemma"));
            entry.setMsd("Tf");
            preprocessEntry(entry, rs.getInt("inflId"));
            if (entry.getMsd().length() > 3) {
                entry.setMsd(entry.getMsd().substring(0, 2) + entry.getMsd().substring(3));
            }
            if (!dictionary.contains(entry)) {
                out.write(entry.toString() + "\n");
                dictionary.Add(entry);
                k++;
            }
        }
        stmt.close();
        rs.close();
        out.close();
        System.out.println("Finished articles (" + k + " added)");
    }

    public static void updateIndefPronouns(Connection con) throws SQLException, IOException {
        MyDictionary dictionary = new MyDictionary();
        Statement stmt = con.createStatement();
        long k = 0;
        ResultSet rs = stmt.executeQuery("SELECT \n"
                + "             inflectedform.formUtf8General as inflForm, \n"
                + "             inflectedform.inflectionId as inflId, \n"
                + "		lexem.formUtf8General as lemma,\n"
                + "             definition.internalRep as definition\n"
                + "FROM inflectedform \n"
                + "JOIN lexemmodel on inflectedform.lexemmodelId=lexemmodel.id \n"
                + "JOIN lexem on lexemmodel.lexemId=lexem.id \n"
                + "JOIN lexemdefinitionmap on lexemdefinitionmap.lexemId=lexem.Id \n"
                + "JOIN definition on definition.id = lexemdefinitionmap.definitionId \n"
                + "WHERE (inflectedform.inflectionId=84 || (inflectedform.inflectionId>40 && inflectedform.inflectionId<49)) AND definition.internalRep LIKE '% #pron\\. nehot\\.%' \n"
                + "ORDER BY inflectedform.formUtf8General");

        BufferedWriter out = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(MyDictionary.folder + "grabedForUse/" + pronIndefFile), "UTF8"));
        while (rs.next()) {
            MyDictionaryEntry entry = new MyDictionaryEntry(rs.getString("inflForm"), null, null, null);
            entry.setLemma(rs.getString("lemma"));
            entry.setMsd("Pi");
            preprocessEntry(entry, rs.getInt("inflId"));

            if (!dictionary.contains(entry)) {
                out.write(entry.toString() + "\n");
                dictionary.Add(entry);
                k++;
            }
        }
        stmt.close();
        rs.close();
        out.close();
        System.out.println("Finished indefinite pronouns (" + k + " added)");
    }

    public static void updatePersPronouns(Connection con) throws SQLException, IOException {
        MyDictionary dictionary = new MyDictionary();
        Statement stmt = con.createStatement();
        long k = 0;
        ResultSet rs = stmt.executeQuery("SELECT \n"
                + "             inflectedform.formUtf8General as inflForm, \n"
                + "             inflectedform.inflectionId as inflId, \n"
                + "		lexem.formUtf8General as lemma,\n"
                + "             definition.internalRep as definition\n"
                + "FROM inflectedform \n"
                + "JOIN lexemmodel on inflectedform.lexemmodelId=lexemmodel.id \n"
                + "JOIN lexem on lexemmodel.lexemId=lexem.id \n"
                + "JOIN lexemdefinitionmap on lexemdefinitionmap.lexemId=lexem.Id \n"
                + "JOIN definition on definition.id = lexemdefinitionmap.definitionId \n"
                + "WHERE (inflectedform.inflectionId=84 || (inflectedform.inflectionId>40 && inflectedform.inflectionId<49)) AND definition.internalRep LIKE '% #pron\\. pers\\.%' \n"
                + "ORDER BY inflectedform.formUtf8General");

        BufferedWriter out = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(MyDictionary.folder + "grabedForUse/" + pronPersFile), "UTF8"));
        while (rs.next()) {
            MyDictionaryEntry entry = new MyDictionaryEntry(rs.getString("inflForm"), null, null, null);
            entry.setLemma(rs.getString("lemma"));
            entry.setMsd("Pp");
            preprocessEntry(entry, rs.getInt("inflId"));

            if (!dictionary.contains(entry)) {
                out.write(entry.toString() + "\n");
                dictionary.Add(entry);
                k++;
            }
        }
        stmt.close();
        rs.close();
        out.close();
        System.out.println("Finished personal pronouns (" + k + " added)");
    }

    public static void updateNegPronouns(Connection con) throws SQLException, IOException {
        MyDictionary dictionary = new MyDictionary();
        Statement stmt = con.createStatement();
        long k = 0;
        ResultSet rs = stmt.executeQuery("SELECT \n"
                + "             inflectedform.formUtf8General as inflForm, \n"
                + "             inflectedform.inflectionId as inflId, \n"
                + "		lexem.formUtf8General as lemma,\n"
                + "             definition.internalRep as definition\n"
                + "FROM inflectedform \n"
                + "JOIN lexemmodel on inflectedform.lexemmodelId=lexemmodel.id \n"
                + "JOIN lexem on lexemmodel.lexemId=lexem.id \n"
                + "JOIN lexemdefinitionmap on lexemdefinitionmap.lexemId=lexem.Id \n"
                + "JOIN definition on definition.id = lexemdefinitionmap.definitionId \n"
                + "WHERE (inflectedform.inflectionId=84 || (inflectedform.inflectionId>40 && inflectedform.inflectionId<49)) AND definition.internalRep LIKE '% #pron\\. neg\\.%' \n"
                + "ORDER BY inflectedform.formUtf8General");

        BufferedWriter out = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(MyDictionary.folder + "grabedForUse/" + pronNegFile), "UTF8"));
        while (rs.next()) {
            MyDictionaryEntry entry = new MyDictionaryEntry(rs.getString("inflForm"), null, null, null);
            entry.setLemma(rs.getString("lemma"));
            entry.setMsd("Pz");
            preprocessEntry(entry, rs.getInt("inflId"));

            if (!dictionary.contains(entry)) {
                out.write(entry.toString() + "\n");
                dictionary.Add(entry);
                k++;
            }
        }
        stmt.close();
        rs.close();
        out.close();
        System.out.println("Finished negative pronouns (" + k + " added)");
    }

    public static void updatePosPronouns(Connection con) throws SQLException, IOException {
        MyDictionary dictionary = new MyDictionary();
        Statement stmt = con.createStatement();
        long k = 0;
        ResultSet rs = stmt.executeQuery("SELECT \n"
                + "             inflectedform.formUtf8General as inflForm, \n"
                + "             inflectedform.inflectionId as inflId, \n"
                + "		lexem.formUtf8General as lemma,\n"
                + "             definition.internalRep as definition\n"
                + "FROM inflectedform \n"
                + "JOIN lexemmodel on inflectedform.lexemmodelId=lexemmodel.id \n"
                + "JOIN lexem on lexemmodel.lexemId=lexem.id \n"
                + "JOIN lexemdefinitionmap on lexemdefinitionmap.lexemId=lexem.Id \n"
                + "JOIN definition on definition.id = lexemdefinitionmap.definitionId \n"
                + "WHERE (inflectedform.inflectionId=84 || (inflectedform.inflectionId>40 && inflectedform.inflectionId<49)) AND definition.internalRep LIKE '% #pron\\. pos\\.%' \n"
                + "ORDER BY inflectedform.formUtf8General");

        BufferedWriter out = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(MyDictionary.folder + "grabedForUse/" + pronPosFile), "UTF8"));
        while (rs.next()) {
            MyDictionaryEntry entry = new MyDictionaryEntry(rs.getString("inflForm"), null, null, null);
            entry.setLemma(rs.getString("lemma"));
            entry.setMsd("Ps");
            preprocessEntry(entry, rs.getInt("inflId"));

            if (!dictionary.contains(entry)) {
                out.write(entry.toString() + "\n");
                dictionary.Add(entry);
                k++;
            }
        }
        stmt.close();
        rs.close();
        out.close();
        System.out.println("Finished possesive pronouns (" + k + " added)");
    }

    public static void updateReflPronouns(Connection con) throws SQLException, IOException {
        MyDictionary dictionary = new MyDictionary();
        Statement stmt = con.createStatement();
        long k = 0;
        ResultSet rs = stmt.executeQuery("SELECT \n"
                + "             inflectedform.formUtf8General as inflForm, \n"
                + "             inflectedform.inflectionId as inflId, \n"
                + "		lexem.formUtf8General as lemma,\n"
                + "             definition.internalRep as definition\n"
                + "FROM inflectedform \n"
                + "JOIN lexemmodel on inflectedform.lexemmodelId=lexemmodel.id \n"
                + "JOIN lexem on lexemmodel.lexemId=lexem.id \n"
                + "JOIN lexemdefinitionmap on lexemdefinitionmap.lexemId=lexem.Id \n"
                + "JOIN definition on definition.id = lexemdefinitionmap.definitionId \n"
                + "WHERE (inflectedform.inflectionId=84 || (inflectedform.inflectionId>40 && inflectedform.inflectionId<49)) AND definition.internalRep LIKE '% #pron\\. refl\\.%' \n"
                + "ORDER BY inflectedform.formUtf8General");

        BufferedWriter out = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(MyDictionary.folder + "grabedForUse/" + pronReflFile), "UTF8"));
        while (rs.next()) {
            MyDictionaryEntry entry = new MyDictionaryEntry(rs.getString("inflForm"), null, null, null);
            entry.setLemma(rs.getString("lemma"));
            entry.setMsd("Px");
            preprocessEntry(entry, rs.getInt("inflId"));

            if (!dictionary.contains(entry)) {
                out.write(entry.toString() + "\n");
                dictionary.Add(entry);
                k++;
            }
        }
        stmt.close();
        rs.close();
        out.close();
        System.out.println("Finished reflexive pronouns (" + k + " added)");
    }

    public static void updateRelPronouns(Connection con) throws SQLException, IOException {
        MyDictionary dictionary = new MyDictionary();
        Statement stmt = con.createStatement();
        long k = 0;
        ResultSet rs = stmt.executeQuery("SELECT \n"
                + "             inflectedform.formUtf8General as inflForm, \n"
                + "             inflectedform.inflectionId as inflId, \n"
                + "		lexem.formUtf8General as lemma,\n"
                + "             definition.internalRep as definition\n"
                + "FROM inflectedform \n"
                + "JOIN lexemmodel on inflectedform.lexemmodelId=lexemmodel.id \n"
                + "JOIN lexem on lexemmodel.lexemId=lexem.id \n"
                + "JOIN lexemdefinitionmap on lexemdefinitionmap.lexemId=lexem.Id \n"
                + "JOIN definition on definition.id = lexemdefinitionmap.definitionId \n"
                + "WHERE (inflectedform.inflectionId=84 || (inflectedform.inflectionId>40 && inflectedform.inflectionId<49)) AND definition.internalRep REGEXP '#pron\\..?.?#?(interog\\.|rel\\.|int\\.|inter\\.)' \n"
                + "ORDER BY inflectedform.formUtf8General");

        BufferedWriter out = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(MyDictionary.folder + "grabedForUse/" + pronRelFile), "UTF8"));
        while (rs.next()) {
            MyDictionaryEntry entry = new MyDictionaryEntry(rs.getString("inflForm"), null, null, null);
            entry.setLemma(rs.getString("lemma"));
            entry.setMsd("Pw");
            preprocessEntry(entry, rs.getInt("inflId"));

            if (!dictionary.contains(entry)) {
                out.write(entry.toString() + "\n");
                dictionary.Add(entry);
                k++;
            }
        }
        stmt.close();
        rs.close();
        out.close();
        System.out.println("Finished relative pronouns (" + k + " added)");
    }

    public static void updateNumOrd(Connection con) throws SQLException, IOException {
        MyDictionary dictionary = new MyDictionary();
        Statement stmt = con.createStatement();
        long k = 0;
        ResultSet rs = stmt.executeQuery("SELECT \n"
                + "             inflectedform.formUtf8General as inflForm, \n"
                + "             inflectedform.inflectionId as inflId, \n"
                + "		lexem.formUtf8General as lemma,\n"
                + "             definition.internalRep as definition\n"
                + "FROM inflectedform \n"
                + "JOIN lexemmodel on inflectedform.lexemmodelId=lexemmodel.id \n"
                + "JOIN lexem on lexemmodel.lexemId=lexem.id \n"
                + "JOIN lexemdefinitionmap on lexemdefinitionmap.lexemId=lexem.Id \n"
                + "JOIN definition on definition.id = lexemdefinitionmap.definitionId \n"
                + "WHERE definition.internalRep REGEXP '#num\\. ord\\.' \n"
                + "ORDER BY inflectedform.formUtf8General");

        BufferedWriter out = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(MyDictionary.folder + "grabedForUse/" + numOrdFile), "UTF8"));
        while (rs.next()) {
            MyDictionaryEntry entry = new MyDictionaryEntry(rs.getString("inflForm"), null, null, null);
            entry.setLemma(rs.getString("lemma"));
            entry.setMsd("Mo");
            preprocessEntry(entry, rs.getInt("inflId"));
            if (entry.getMsd().length() > 3) {
                entry.setMsd(entry.getMsd().substring(0, 2) + entry.getMsd().substring(3));
            }
            if (!dictionary.contains(entry)) {
                out.write(entry.toString() + "\n");
                dictionary.Add(entry);
                k++;
            }
        }
        stmt.close();
        rs.close();
        out.close();
        System.out.println("Finished ordinal numerals (" + k + " added)");
    }

    public static void updateNumCol(Connection con) throws SQLException, IOException {
        MyDictionary dictionary = new MyDictionary();
        Statement stmt = con.createStatement();
        long k = 0;
        ResultSet rs = stmt.executeQuery("SELECT \n"
                + "             inflectedform.formUtf8General as inflForm, \n"
                + "             inflectedform.inflectionId as inflId, \n"
                + "		lexem.formUtf8General as lemma,\n"
                + "             definition.internalRep as definition\n"
                + "FROM inflectedform \n"
                + "JOIN lexemmodel on inflectedform.lexemmodelId=lexemmodel.id \n"
                + "JOIN lexem on lexemmodel.lexemId=lexem.id \n"
                + "JOIN lexemdefinitionmap on lexemdefinitionmap.lexemId=lexem.Id \n"
                + "JOIN definition on definition.id = lexemdefinitionmap.definitionId \n"
                + "WHERE definition.internalRep REGEXP '#num\\. col\\.' \n"
                + "ORDER BY inflectedform.formUtf8General");

        BufferedWriter out = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(MyDictionary.folder + "grabedForUse/" + numColFile), "UTF8"));
        while (rs.next()) {
            MyDictionaryEntry entry = new MyDictionaryEntry(rs.getString("inflForm"), null, null, null);
            entry.setLemma(rs.getString("lemma"));
            entry.setMsd("Ml");
            preprocessEntry(entry, rs.getInt("inflId"));
            if (entry.getMsd().length() > 3) {
                entry.setMsd(entry.getMsd().substring(0, 2) + entry.getMsd().substring(3));
            }

            if (!dictionary.contains(entry)) {
                out.write(entry.toString() + "\n");
                dictionary.Add(entry);
                k++;
            }
        }
        stmt.close();
        rs.close();
        out.close();
        System.out.println("Finished colective numerals (" + k + " added)");
    }

    public static void updateNumCard(Connection con) throws SQLException, IOException {
        MyDictionary dictionary = new MyDictionary();
        Statement stmt = con.createStatement();
        long k = 0;
        ResultSet rs = stmt.executeQuery("SELECT \n"
                + "             inflectedform.formUtf8General as inflForm, \n"
                + "             inflectedform.inflectionId as inflId, \n"
                + "		lexem.formUtf8General as lemma,\n"
                + "             definition.internalRep as definition\n"
                + "FROM inflectedform \n"
                + "JOIN lexemmodel on inflectedform.lexemmodelId=lexemmodel.id \n"
                + "JOIN lexem on lexemmodel.lexemId=lexem.id \n"
                + "JOIN lexemdefinitionmap on lexemdefinitionmap.lexemId=lexem.Id \n"
                + "JOIN definition on definition.id = lexemdefinitionmap.definitionId \n"
                + "WHERE definition.internalRep REGEXP '#num\\. card\\.' \n"
                + "ORDER BY inflectedform.formUtf8General");

        BufferedWriter out = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(MyDictionary.folder + "grabedForUse/" + numCardFile), "UTF8"));
        while (rs.next()) {
            MyDictionaryEntry entry = new MyDictionaryEntry(rs.getString("inflForm"), null, null, null);
            entry.setLemma(rs.getString("lemma"));
            entry.setMsd("Mc");
            preprocessEntry(entry, rs.getInt("inflId"));
            if (entry.getMsd().length() > 3) {
                entry.setMsd(entry.getMsd().substring(0, 2) + entry.getMsd().substring(3));
            }

            if (!dictionary.contains(entry)) {
                out.write(entry.toString() + "\n");
                dictionary.Add(entry);
                k++;
            }
        }
        stmt.close();
        rs.close();
        out.close();
        System.out.println("Finished cardinal numerals (" + k + " added)");
    }

    public static void preprocessEntry(MyDictionaryEntry entry, int inflId) {
        int infl = inflId;
        if (infl == 17 || infl == 18 || infl == 21 || infl == 22) {
            infl -= 16;
        }
        if (infl == 19 || infl == 20 || infl == 23 || infl == 24) {
            infl -= 8;
        }

        if (infl == 50) {
            infl--;
        }
        char[] msd;

        switch (infl) {
            case 1:
                entry.setMsd("Ncmsrn");
                break;
            case 2:
                entry.setMsd("Ncmson");
                break;
            case 3:
                entry.setMsd("Ncmprn");
                break;
            case 4:
                entry.setMsd("Ncmpon");
                break;
            case 5:
                entry.setMsd("Ncmsry");
                break;
            case 6:
                entry.setMsd("Ncmsoy");
                break;
            case 7:
                entry.setMsd("Ncmpry");
                break;
            case 8:
                entry.setMsd("Ncmpoy");
                break;

            case 9:
                entry.setMsd("Ncfsrn");
                break;
            case 10:
                entry.setMsd("Ncfson");
                break;
            case 11:
                entry.setMsd("Ncfprn");
                break;
            case 12:
                entry.setMsd("Ncfpon");
                break;

            case 13:
                entry.setMsd("Ncfsry");
                break;
            case 14:
                entry.setMsd("Ncfsoy");
                break;
            case 15:
                entry.setMsd("Ncfpry");
                break;
            case 16:
                entry.setMsd("Ncfpoy");
                break;
            //vocative:
            case 87:
            case 91://neutru singular
                entry.setMsd("Ncmsvy");
                break;
            case 88:
                entry.setMsd("Ncmpvy");
                break;
            case 89:
                entry.setMsd("Ncfsvy");
                break;
            case 90:
            case 92://neutru plural
                entry.setMsd("Ncfpvy");
                break;

            //de la 17-24 este subst neutru dar acesta e modificat in masculin si feminin in functie de numar
            //ADVECTIV
            case 25:
                entry.setMsd("Afpmsrn");
                break;
            case 26:
                entry.setMsd("Afpmson");
                break;
            case 27:
                entry.setMsd("Afpmprn");
                break;
            case 28:
                entry.setMsd("Afpmpon");
                break;
            case 29:
                entry.setMsd("Afpmsry");
                break;
            case 30:
                entry.setMsd("Afpmsoy");
                break;
            case 31:
                entry.setMsd("Afpmpry");
                break;
            case 32:
                entry.setMsd("Afpmpoy");
                break;

            case 33:
                entry.setMsd("Afpfsrn");
                break;
            case 34:
                entry.setMsd("Afpfson");
                break;
            case 35:
                entry.setMsd("Afpfprn");
                break;
            case 36:
                entry.setMsd("Afpfpon");
                break;

            case 37:
                entry.setMsd("Afpfsry");
                break;
            case 38:
                entry.setMsd("Afpfsoy");
                break;
            case 39:
                entry.setMsd("Afpfpry");
                break;
            case 40:
                entry.setMsd("Afpfpoy");
                break;
            //vocative
            case 93:
                entry.setMsd("Afpmsvy");
                break;
            case 94:
                entry.setMsd("Afpmpvy");
                break;
            case 95:
                entry.setMsd("Afpfsvy");
                break;
            case 96:
                entry.setMsd("Afpfpvy");
                break;

            //PRONOUN
            case 41:
                msd = entry.getMsd().toCharArray();
                msd = expandArray(msd, 6);
                msd[3] = 'm';
                msd[4] = 's';
                msd[5] = 'r';
                entry.setMsd(new String(msd));
                break;
            case 42:
                msd = entry.getMsd().toCharArray();
                msd = expandArray(msd, 6);
                msd[3] = 'm';
                msd[4] = 's';
                msd[5] = 'o';
                entry.setMsd(new String(msd));
                break;
            case 43:
                msd = entry.getMsd().toCharArray();
                msd = expandArray(msd, 6);
                msd[3] = 'm';
                msd[4] = 'p';
                msd[5] = 'r';
                entry.setMsd(new String(msd));
                break;
            case 44:
                msd = entry.getMsd().toCharArray();
                msd = expandArray(msd, 6);
                msd[3] = 'm';
                msd[4] = 'p';
                msd[5] = 'o';
                entry.setMsd(new String(msd));
                break;
            case 45:
                msd = entry.getMsd().toCharArray();
                msd = expandArray(msd, 6);
                msd[3] = 'f';
                msd[4] = 's';
                msd[5] = 'r';
                entry.setMsd(new String(msd));
                break;
            case 46:
                msd = entry.getMsd().toCharArray();
                msd = expandArray(msd, 6);
                msd[3] = 'f';
                msd[4] = 's';
                msd[5] = 'o';
                entry.setMsd(new String(msd));
                break;
            case 47:
                msd = entry.getMsd().toCharArray();
                msd = expandArray(msd, 6);
                msd[3] = 'f';
                msd[4] = 'p';
                msd[5] = 'r';
                entry.setMsd(new String(msd));
                break;
            case 48:
                msd = entry.getMsd().toCharArray();
                msd = expandArray(msd, 6);
                msd[3] = 'f';
                msd[4] = 'p';
                msd[5] = 'o';
                entry.setMsd(new String(msd));
                break;

            //VERB
            case 49:
                entry.setMsd("Vmn");
                break;
            case 51:
                entry.setMsd("Vmmp2s");
                break;
            case 52:
                entry.setMsd("Vmp");
                break;
            case 53:
                entry.setMsd("Vmg");
                break;

            case 54:
                entry.setMsd("Vmip1s");
                break;
            case 55:
                entry.setMsd("Vmip2s");
                break;
            case 56:
                entry.setMsd("Vmip3s");
                break;
            case 57:
                entry.setMsd("Vmip1p");
                break;
            case 58:
                entry.setMsd("Vmip2p");
                break;
            case 59:
                entry.setMsd("Vmip3p");
                break;

            case 60:
                entry.setMsd("Vmsp1s");
                break;
            case 61:
                entry.setMsd("Vmsp2s");
                break;
            case 62:
                entry.setMsd("Vmsp3");
                break;
            case 63:
                entry.setMsd("Vmsp1p");
                break;
            case 64:
                entry.setMsd("Vmsp2p");
                break;
            case 65:
                entry.setMsd("Vmsp3");
                break;

            case 66:
                entry.setMsd("Vmii1s");
                break;
            case 67:
                entry.setMsd("Vmii2s");
                break;
            case 68:
                entry.setMsd("Vmii3s");
                break;
            case 69:
                entry.setMsd("Vmii1p");
                break;
            case 70:
                entry.setMsd("Vmii2p");
                break;
            case 71:
                entry.setMsd("Vmii3p");
                break;

            case 72:
                entry.setMsd("Vmis1s");
                break;
            case 73:
                entry.setMsd("Vmis2s");
                break;
            case 74:
                entry.setMsd("Vmis3s");
                break;
            case 75:
                entry.setMsd("Vmis1p");
                break;
            case 76:
                entry.setMsd("Vmis2p");
                break;
            case 77:
                entry.setMsd("Vmis3p");
                break;

            case 78:
                entry.setMsd("Vmil1s");
                break;
            case 79:
                entry.setMsd("Vmil2s");
                break;
            case 80:
                entry.setMsd("Vmil3s");
                break;
            case 81:
                entry.setMsd("Vmil1p");
                break;
            case 82:
                entry.setMsd("Vmil2p");
                break;
            case 83:
                entry.setMsd("Vmil3p");
                break;
            case 86:
                entry.setMsd("Vmmp2p");
                break;
        }
    }

    public static char[] expandArray(char[] a, int length) {//tails with - to meet the required length
        if (a.length < length) {
            char[] ret = new char[length];
            for (int i = 0; i < length; i++) {
                if (i < a.length) {
                    ret[i] = a[i];
                } else {
                    ret[i] = '-';
                }
            }
            return ret;
        } else {
            return a;
        }
    }

    private static void updateLexemPriority(Connection con) throws SQLException, FileNotFoundException, UnsupportedEncodingException, IOException {
        Statement stmt = con.createStatement();
        ResultSet rs = stmt.executeQuery("SELECT \n"
                + "		formUtf8General as lemma,\n"
                + "		frequency\n"
                + "FROM lexem \n"
                + "ORDER BY formUtf8General");

        BufferedWriter out = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(MyDictionary.folder + "grabedForUse/" + lexemPriorityFile), "UTF8"));
        while (rs.next()) {
            String lemma = rs.getString("lemma");
            lemma = MyDictionary.getCanonicalWordKeepGrafie(lemma);
            int fr = (int) (100 * rs.getFloat("frequency"));
            out.write(lemma);
            out.write(" ");
            out.write("" + fr);
            out.write("\n");
        }
        stmt.close();
        rs.close();
        out.close();
        System.out.println("Finished lexem priority map ");
    }
}
