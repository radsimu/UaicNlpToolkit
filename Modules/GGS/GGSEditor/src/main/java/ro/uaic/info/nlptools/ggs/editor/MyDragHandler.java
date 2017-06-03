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

package ro.uaic.info.nlptools.ggs.editor;

import javax.swing.*;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.io.File;
import java.util.List;

public class MyDragHandler extends TransferHandler {
    public boolean canImport(JComponent arg0, DataFlavor[] arg1) {
        if (arg1.length != 1)
            return false;
        DataFlavor flavor = arg1[0];
        if (flavor.equals(DataFlavor.javaFileListFlavor)) {
            return true;
        }
        return false;
    }

    public boolean importData(JComponent comp, Transferable t) {
        if (GrammarEditor.theEditor.shouldSave) {
            int answer = JOptionPane.showConfirmDialog(GrammarEditor.theEditor.frame, "Save before opening a new grammar?", null, JOptionPane.YES_NO_CANCEL_OPTION);
            if (answer == JOptionPane.YES_OPTION)
                GrammarEditor.theEditor.save.doClick();
            else if (answer == JOptionPane.CANCEL_OPTION)
                return false;
        }

        File f = null;
        try {
            f = ((List<File>) t.getTransferData(DataFlavor.javaFileListFlavor)).get(0);
            GrammarEditor.theEditor.openGrammar(f);
            return true;
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(GrammarEditor.theEditor.frame, ex.getMessage(), "Can't drag file", JOptionPane.ERROR_MESSAGE);
        }

        return false;
    }
}
