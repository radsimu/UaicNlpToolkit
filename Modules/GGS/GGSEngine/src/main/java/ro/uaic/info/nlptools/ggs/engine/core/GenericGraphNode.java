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

package ro.uaic.info.nlptools.ggs.engine.core;


import java.util.*;

public class GenericGraphNode<T> {
    public Set<GenericGraphNode<T>> children = new LinkedHashSet<GenericGraphNode<T>>();
    public Set<GenericGraphNode<T>> parents = new LinkedHashSet<GenericGraphNode<T>>();
    public T userObject;
    public List<AbstractMap.SimpleEntry<List<T>, Integer>> utterrances = new ArrayList<AbstractMap.SimpleEntry<List<T>, Integer>>(); // an utterance is represented by the sequence which has been added and the startPositionInSentence of the element from the sequence which has matched this graph node
    public boolean isLeaf;

    public GenericGraphNode() {

    }

    public void AddSequence(List<T> sequence) {
        AddSequenceRec(sequence, 0, new Comparator<T>() {
            @Override
            public int compare(T t, T t2) {
                return t.equals(t2) ? 0 : -1;
            }
        });
    }

    public void AddSequence(List<T> sequence, Comparator<T> comparator) {
        AddSequenceRec(sequence, 0, comparator);
    }

    private void AddSequenceRec(List<T> sequcence, int offset, Comparator<T> comparator) {//this adds the sequence as in a Trie, but if the existent structure is a graph with cicles and all that, it will behave accordingly
        GenericGraphNode<T> foundChild = null;
        for (GenericGraphNode<T> child : children)
            if (comparator.compare(child.userObject, sequcence.get(offset)) == 0) {
                foundChild = child;
                break;
            }
        if (foundChild == null) {
            foundChild = new GenericGraphNode<T>();
            children.add(foundChild);
            foundChild.userObject = sequcence.get(offset);
            foundChild.parents.add(this);
        }
        foundChild.utterrances.add(new AbstractMap.SimpleEntry<List<T>, Integer> (sequcence, offset));
        offset++;
        if (sequcence.size() == offset)
            foundChild.isLeaf = true;
        else
            foundChild.AddSequenceRec(sequcence, offset, comparator);
    }

    public void RemoveChild(GenericGraphNode<T> child) {
        children.remove(child);
        child.parents.remove(this);
    }

    public void RemoveChild(GenericGraphNode<T> child, Comparator<GenericGraphNode<T>> comparator) {
        GenericGraphNode<T> foundChild = null;
        for (GenericGraphNode<T> myChild : children) {
            if (comparator.compare(myChild, child) == 0) {
                foundChild = myChild;
                break;
            }
        }
        if (foundChild != null)
            RemoveChild(foundChild);
    }

    public void RemoveParent(GenericGraphNode<T> parent) {
        parents.remove(parent);
        parent.children.remove(this);
    }

    public void RemoveParent(GenericGraphNode<T> parent, Comparator<GenericGraphNode<T>> comparator) {
        GenericGraphNode<T> foundParent = null;
        for (GenericGraphNode<T> myParent : parents) {
            if (comparator.compare(myParent, parent) == 0) {
                foundParent = myParent;
                break;
            }
        }
        if (foundParent != null)
            RemoveParent(foundParent);
    }

    public void AddChild(GenericGraphNode<T> child) {
        if (!children.contains(child))
            children.add(child);
        if (!child.parents.contains(this))
            child.parents.add(this);
    }

    public void AddParent(GenericGraphNode<T> parent) {
        if (!parents.contains(parent))
            parents.add(parent);
        if (!parent.children.contains(this))
            parent.children.add(this);
    }

    public GenericGraphNode<T> AddParent(GenericGraphNode<T> parent, Comparator<GenericGraphNode<T>> comparator) {
        GenericGraphNode<T> foundParent = null;
        for (GenericGraphNode<T> myParent : parents) {
            if (comparator.compare(myParent, parent) == 0) {
                foundParent = myParent;
                break;
            }
        }
        if (foundParent != null) {
            AddParent(foundParent);
            return foundParent;
        } else {
            AddParent(parent);
            return parent;
        }
    }

    @Override
    public String toString() {
        return "GenericGraphNode: " + userObject;
    }

    @Override
    public GenericGraphNode<T> clone(){
        GenericGraphNode<T> clone = new GenericGraphNode<T>();
        clone.isLeaf = isLeaf;
        clone.userObject = userObject;
        clone.utterrances = utterrances;
        return  clone;
    }
}
