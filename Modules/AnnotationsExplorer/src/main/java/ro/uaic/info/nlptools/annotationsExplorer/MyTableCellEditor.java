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

package ro.uaic.info.nlptools.annotationsExplorer;

import ro.uaic.info.nlptools.corpus.InmemorySentence;

import javax.swing.*;
import javax.swing.table.TableCellEditor;
import java.awt.*;
import java.util.LinkedHashMap;
import java.util.Map;

public class MyTableCellEditor extends AbstractCellEditor implements TableCellEditor {

    AnnotationsExplorer main;
    LinkedHashMap<String,Color> sortedAnnotationsWithColors;
    Map<InmemorySentence,SentenceRow> sentsToRows;

    public MyTableCellEditor(AnnotationsExplorer main, LinkedHashMap<String, Color> sortedAnnotationsWithColors, Map<InmemorySentence, SentenceRow> sentsToRows) {
        this.main = main;
        this.sortedAnnotationsWithColors = sortedAnnotationsWithColors;
        this.sentsToRows = sentsToRows;
    }

    @Override
    public Component getTableCellEditorComponent(JTable jTable, Object o, boolean b, int i, int i2) {
        SentenceRow ret = sentsToRows.get((InmemorySentence) o);
        if (ret == null) {
            ret = new SentenceRow((InmemorySentence) o, sortedAnnotationsWithColors, main);
            sentsToRows.put((InmemorySentence) o, ret);
        }
        return ret;
    }

    @Override
    public Object getCellEditorValue() {
        return null;
    }
}
