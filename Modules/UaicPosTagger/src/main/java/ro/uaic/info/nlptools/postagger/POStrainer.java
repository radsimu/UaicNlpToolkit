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

package ro.uaic.info.nlptools.postagger;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.Map;
import opennlp.model.*;
import opennlp.perceptron.SimplePerceptronSequenceTrainer;
import opennlp.tools.cmdline.CmdLineUtil;
import opennlp.tools.cmdline.TerminateToolException;
import opennlp.tools.postag.*;
import opennlp.tools.util.HashSumEventStream;
import opennlp.tools.util.ObjectStream;
import opennlp.tools.util.PlainTextByLineStream;
import opennlp.tools.util.model.*;
import ro.uaic.info.nlptools.tools.UaicMorphologicalDictionary;

public class POStrainer {

    public static void main(String[] args) throws FileNotFoundException, IOException, Exception {

        File trainingDataInFile = new File(args[0]);
        File modelOutFile = new File(args[2]);

        CmdLineUtil.checkOutputFile("pos tagger model", modelOutFile);
        ObjectStream<POSSample> sampleStream = openSampleData("Training", trainingDataInFile, Charset.forName("utf-8"));
        UaicMorphologicalDictionary dic = new UaicMorphologicalDictionary();
        dic.load(new FileInputStream(args[1]));

        POSModel model;
        try {
            model = train(sampleStream, dic, ModelType.MAXENT, 5, 100);
        } catch (IOException e) {
            CmdLineUtil.printTrainingIoError(e);
            throw new TerminateToolException(-1);
        } finally {
            try {
                sampleStream.close();
            } catch (IOException e) {
                // sorry that this can fail
            }
        }

        CmdLineUtil.writeModel("pos tagger", modelOutFile, model);
    }

    public static POSModel train(ObjectStream<POSSample> samples, UaicMorphologicalDictionary dict, ModelType modelType, int cutoff, int iterations) throws IOException {

        POSContextGenerator contextGenerator = new MyPOSContextGenerator(dict);

        AbstractModel posModel = null;

        Map<String, String> manifestInfoEntries = new HashMap<String, String>();
        ModelUtil.addCutoffAndIterations(manifestInfoEntries, cutoff, iterations);

        if (modelType.equals(ModelType.MAXENT)
                || modelType.equals(ModelType.PERCEPTRON)) {
            EventStream es = new POSSampleEventStream(samples, contextGenerator);
            HashSumEventStream hses = new HashSumEventStream(es);

            if (modelType.equals(ModelType.MAXENT)) {
                posModel = opennlp.maxent.GIS.trainModel(iterations,
                        new TwoPassDataIndexer(hses, cutoff));
            } else if (modelType.equals(ModelType.PERCEPTRON)) {
                boolean useAverage = true;

                posModel = new opennlp.perceptron.PerceptronTrainer().trainModel(
                        iterations, new TwoPassDataIndexer(hses,
                        cutoff, false), cutoff, useAverage);
            } else {
                throw new IllegalStateException();
            }

            manifestInfoEntries.put(BaseModel.TRAINING_EVENTHASH_PROPERTY,
                    hses.calculateHashSum().toString(16));
        } else if (modelType.equals(ModelType.PERCEPTRON_SEQUENCE)) {

            POSSampleSequenceStream ss = new POSSampleSequenceStream(samples, contextGenerator);
            boolean useAverage = true;

            posModel = new SimplePerceptronSequenceTrainer().trainModel(iterations, ss, cutoff, useAverage);
        } else {
            throw new IllegalStateException();
        }

        return new POSModel("ro", posModel, null, null, manifestInfoEntries);
    }

    static ObjectStream<POSSample> openSampleData(String sampleDataName, File sampleDataFile, Charset encoding) {
        CmdLineUtil.checkInputFile(sampleDataName + " Data", sampleDataFile);
        FileInputStream sampleDataIn = CmdLineUtil.openInFile(sampleDataFile);
        ObjectStream<String> lineStream = new PlainTextByLineStream(sampleDataIn.getChannel(), encoding);
        return new WordTagSampleStream(lineStream);
    }
}
